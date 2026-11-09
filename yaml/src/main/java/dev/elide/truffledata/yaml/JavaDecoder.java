package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.HeapTape;
import dev.elide.truffledata.tape.TapeBuilder;
import dev.elide.truffledata.yaml.ScalarResolver.Kind;
import dev.elide.truffledata.yaml.ScalarResolver.Scalar;
import dev.elide.truffledata.yaml.ScalarResolver.Tag;
import dev.elide.truffledata.yaml.events.YamlEventSource;
import dev.elide.truffledata.yaml.events.YamlEvents;
import dev.elide.truffledata.yaml.events.YamlMark;
import dev.elide.truffledata.yaml.events.YamlScalarStyle;
import dev.elide.truffledata.yaml.events.YamlSyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The Java kernel's single-document decoder: TruffleData's YAML semantics over parser-neutral
 * events, the counterpart of {@code yaml/native/decoder.h} and {@code merge.h}. Events build a small
 * node tree (aliases are references, never expanded), policies run on it as the native handler runs
 * them on its tape (keys compared after resolution, merge keys at mapping close, recursive aliases
 * rejected, every limit charged as the native writer charges it), and the finished tree is written
 * once into the shared compact tape with the same {@code yaml_meta} words and side tables.
 */
final class JavaDecoder implements YamlEvents {
  // yaml_meta layout (decoder.h).
  static final int STYLE_MASK = 7, ANCHORED = 1 << 3, ALIAS = 1 << 4, KEY_KIND_SHIFT = 5, TAG_SHIFT = 8;
  static final int STYLE_BLOCK = 5, STYLE_FLOW = 6;
  private static final int META_LIMIT = 1 << 24;
  private static final int MAX_TAG_DIRECTIVES = 4; // rapidyaml's RYML_MAX_TAG_DIRECTIVES
  private static final String TIMESTAMP = "tag:yaml.org,2002:timestamp";

  /** A decoded document. {@code present} is false when the input held only comments. */
  record Document(HeapTape tape, YamlMetadata metadata, boolean present, boolean schema11) {}

  private static final class Node {
    static final int SCALAR = 0, MAP = 1, SEQ = 2, ALIAS = 3;
    final int type;
    int meta;
    // Scalars and keys.
    Kind kind = Kind.NULL;
    long bits;
    byte @Nullable [] text;
    long span;
    boolean inputRef, key;
    // Containers: maps alternate key and value nodes.
    @Nullable ArrayList<Node> children;
    // Aliases.
    @Nullable Node target;
    int index = -1;
    boolean dead;

    Node(int type) {
      this.type = type;
      if (type == MAP || type == SEQ) children = new ArrayList<>();
    }

    ArrayList<Node> children() {
      return java.util.Objects.requireNonNull(children);
    }
  }

  private static final class Frame {
    final Node node;
    final boolean map;
    final Tag tag;
    final int anchor;
    final HashSet<String> keys = new HashSet<>();
    // Offset of the first content byte (past properties): `{` for a flow mapping written with
    // braces; a single-pair mapping inside a flow sequence starts at its key instead.
    final int content;
    // A collection in key position whose rejection waits for its end (see open()).
    final boolean complex;
    @Nullable Node merge;
    boolean value;

    Frame(Node node, boolean map, Tag tag, int anchor, int content, boolean complex) {
      this.node = node;
      this.map = map;
      this.tag = tag;
      this.anchor = anchor;
      this.content = content;
      this.complex = complex;
    }
  }

  private final byte[] input;
  private final YamlOptions options;
  private final YamlLimits limits;
  private final boolean retain;
  private final boolean lastWins;
  private YamlSchema schema;

  private int documents;
  private int version;
  private final Map<String, String> directives = new LinkedHashMap<>();
  private final ArrayList<String> tags = new ArrayList<>();
  private final HashMap<String, Integer> tagIndex = new HashMap<>();
  private final ArrayList<Node> anchorNodes = new ArrayList<>();
  private final ArrayList<String> anchorNames = new ArrayList<>();
  private final ArrayList<Boolean> anchorOpen = new ArrayList<>();
  private final HashMap<String, Integer> bindings = new HashMap<>();
  private final ArrayList<Node> aliasTargets = new ArrayList<>();
  private final ArrayDeque<Frame> frames = new ArrayDeque<>();
  private @Nullable Node root;
  private long values, textBytes;
  private YamlMark position = YamlMark.UNKNOWN;

  private JavaDecoder(byte[] input, YamlOptions options, boolean retain, boolean lastWins) {
    this.input = input;
    this.options = options;
    this.limits = options.limits();
    this.retain = retain;
    this.lastWins = lastWins;
    this.schema = options.schema();
  }

  /**
   * Decodes {@code input} (prepared UTF-8) as at most one document. {@code lastWins} is the
   * duplicate policy (typed plans always reject). Throws {@link JavaFailure}.
   */
  static Document decode(
      YamlEventSource source, byte[] input, YamlOptions options, boolean retain, boolean lastWins) {
    JavaDecoder decoder = new JavaDecoder(input, options, retain, lastWins);
    try {
      source.parse(input, input.length, decoder);
    } catch (YamlSyntaxException failure) {
      throw new JavaFailure(
          JavaFailure.SYNTAX, failure.detail(), failure.mark().line(), failure.mark().column(), true);
    } catch (ScalarResolver.ResolveException failure) {
      throw decoder.resolveFailure(failure);
    } catch (StackOverflowError failure) {
      throw decoder.limit("depth");
    }
    if (!decoder.frames.isEmpty()) throw JavaFailure.internal("YAML event stream ended inside a collection");
    return decoder.finish();
  }

  // ---- errors -----------------------------------------------------------------------------------

  private JavaFailure fail(String message) {
    return JavaFailure.syntax(message, position.line(), position.column());
  }

  private JavaFailure limit(String which) {
    return JavaFailure.limit(which, position.line(), position.column());
  }

  private JavaFailure resolveFailure(ScalarResolver.ResolveException failure) {
    String message = String.valueOf(failure.getMessage());
    return failure.limit
        ? new JavaFailure(JavaFailure.LIMIT, message, position.line(), position.column(), false)
        : fail(message);
  }

  // ---- documents --------------------------------------------------------------------------------

  @Override
  public void documentStart(boolean explicit, int version, Map<String, String> tags, YamlMark mark) {
    position = mark;
    if (++documents > 1)
      throw new JavaFailure(
          JavaFailure.MULTIPLE,
          "YAML input holds more than one document; decode it as a stream",
          mark.line(),
          mark.column(),
          false);
    if (tags.size() > MAX_TAG_DIRECTIVES) throw fail("too many YAML %TAG directives");
    directives.putAll(tags);
    if (version != 0) {
      if (version >>> 16 != 1) throw fail("unsupported YAML version (only 1.x)");
      this.version = version;
      // VersionDirective.HONOR: a 1.1 document resolves with the 1.1 schema (and merge keys).
      if (options.versionDirective() == VersionDirective.HONOR && (version & 0xffff) == 1)
        schema = YamlSchema.YAML_1_1;
    }
  }

  @Override
  public void documentEnd(boolean explicit, YamlMark mark) {
    position = mark;
  }

  private boolean merges() {
    return options.mergeKeys() || schema == YamlSchema.YAML_1_1;
  }

  // ---- tags and anchors ---------------------------------------------------------------------------

  private int intern(String uri) {
    Integer found = tagIndex.get(uri);
    if (found != null) return found;
    if (tags.size() + 1 >= META_LIMIT) throw limit("tags");
    tags.add(uri);
    tagIndex.put(uri, tags.size());
    return tags.size();
  }

  /** The tag's kind, rejecting unknown tags under UnknownTags.REJECT. */
  private Tag classify(@Nullable String uri) {
    Tag kind = ScalarResolver.classify(uri);
    if (kind == Tag.OTHER && options.unknownTags() == UnknownTags.REJECT) throw fail("YAML unknown tag");
    return kind;
  }

  private int define(@Nullable String anchor, Node node, boolean open) {
    if (anchor == null) return -1;
    int id = anchorNodes.size();
    anchorNodes.add(node);
    anchorNames.add(anchor);
    anchorOpen.add(open);
    bindings.put(anchor, id);
    return id;
  }

  private Node resolveAlias(String name) {
    if (options.aliases() == Aliases.REJECT) throw fail("YAML aliases are rejected");
    Integer id = bindings.get(name);
    if (id == null) throw fail("undefined YAML alias");
    if (anchorOpen.get(id)) throw fail("recursive YAML alias");
    if (aliasTargets.size() >= limits.maxAliases() || aliasTargets.size() + 1 >= META_LIMIT)
      throw limit("aliases");
    return anchorNodes.get(id);
  }

  private int addAliasTarget(Node target) {
    aliasTargets.add(target);
    return aliasTargets.size() - 1;
  }

  // ---- charging ---------------------------------------------------------------------------------

  private void value() {
    if (values >= limits.maxNodes()) throw limit("tape value limit");
    values++;
  }

  private void charge(int length) {
    if (length > TapeBuilder.MAX_LENGTH) throw limit("tape text token limit");
    if (length > limits.maxTextBytes() - textBytes) throw limit("tape text limit");
    textBytes += length;
  }

  private static long pack(long offset, long length) {
    return offset << 32 | length;
  }

  /** The native decoder's raw spans: always single-line plain tokens, others with the source kept. */
  private long lexical(YamlScalarStyle style, boolean empty, int start, int end) {
    if (empty || end <= start) return 0;
    switch (style) {
      case PLAIN:
        for (int i = start; i < end; i++) if (input[i] == '\n') return retain ? pack(start, end - start) : 0;
        return pack(start, end - start);
      case SINGLE_QUOTED:
      case DOUBLE_QUOTED:
        return retain ? pack(start, end - start) : 0;
      default:
        // From the indicator to the last content byte; a header with no line after it (`--- |1-`
        // at the end of input) has none, as the native header search needs a content line.
        if (!retain) return 0;
        boolean line = false;
        for (int i = start; i < end && !line; i++) line = input[i] == '\n';
        if (!line) return 0;
        int last = end;
        while (last > start + 1 && whitespace(input[last - 1])) last--;
        return pack(start, last - start);
    }
  }

  private static boolean whitespace(byte c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
  }

  // ---- scalars ----------------------------------------------------------------------------------

  /** Resolves a scalar with its tag: 1.1 type tags are validated and stored as strings. */
  private Scalar resolve(String text, YamlScalarStyle style, Tag tag) {
    try {
      switch (tag) {
        case BINARY:
          if (!ScalarResolver.validBase64(text)) throw fail("invalid base64 in a YAML !!binary scalar");
          return ScalarResolver.resolve(text, style == YamlScalarStyle.PLAIN, schema, Tag.STR);
        case TIMESTAMP:
          if (!ScalarResolver.validTimestamp(text)) throw fail("invalid YAML !!timestamp scalar");
          return ScalarResolver.resolve(text, style == YamlScalarStyle.PLAIN, schema, Tag.STR);
        case MERGE:
          if (!text.equals("<<")) throw fail("YAML !!merge tag on a scalar other than <<");
          return ScalarResolver.resolve(text, style == YamlScalarStyle.PLAIN, schema, Tag.STR);
        case VALUE:
          if (!text.equals("=")) throw fail("YAML !!value tag on a scalar other than =");
          return ScalarResolver.resolve(text, style == YamlScalarStyle.PLAIN, schema, Tag.STR);
        case SET, OMAP, PAIRS, SEQ, MAP:
          throw fail("YAML collection tag on a scalar");
        default:
          return ScalarResolver.resolve(text, style == YamlScalarStyle.PLAIN, schema, tag);
      }
    } catch (ScalarResolver.ResolveException failure) {
      throw resolveFailure(failure);
    }
  }

  private static boolean ascii(byte[] text) {
    for (byte b : text) if (b < 0) return false;
    return true;
  }

  /** A text node whose decoded bytes are its plain source token references the input. */
  private boolean sameAsSource(byte[] text, long span) {
    if (span == 0 || (int) span != text.length) return false;
    int offset = (int) (span >>> 32);
    for (int i = 0; i < text.length; i++) if (input[offset + i] != text[i]) return false;
    return true;
  }

  private Node scalarNode(Scalar r, long span, boolean plain) {
    Node node = new Node(Node.SCALAR);
    node.kind = r.kind();
    node.span = span;
    switch (r.kind()) {
      case NULL, BOOL, I64, U64, F64 -> {
        if ((int) span > TapeBuilder.MAX_LENGTH || (span & 0xffffffffL) > TapeBuilder.MAX_LENGTH)
          throw limit("tape text token limit");
        node.bits = r.bits();
      }
      default -> {
        byte[] text = java.util.Objects.requireNonNull(r.text()).getBytes(StandardCharsets.UTF_8);
        charge(text.length);
        node.text = text;
        node.inputRef = plain && r.kind() == Kind.STRING && sameAsSource(text, span);
      }
    }
    value();
    return node;
  }

  @Override
  public void scalar(
      String text,
      YamlScalarStyle style,
      boolean empty,
      @Nullable String anchor,
      @Nullable String tag,
      YamlMark start,
      int end) {
    position = start;
    Frame f = frames.peek();
    if (style == YamlScalarStyle.DOUBLE_QUOTED) checkSurrogates(text);
    Tag kind = classify(tag);
    int index = tag == null ? 0 : intern(tag);
    long span = lexical(style, empty, start.offset(), end);
    Scalar r = resolve(text, style, kind);
    if (r.timestamp() && index == 0) index = intern(TIMESTAMP);
    if (f != null && f.map && !f.value) {
      // `<<` (plain and untagged, or tagged !!merge) is a merge key when merges are on.
      boolean merge =
          merges()
              && ((kind == Tag.NONE && style == YamlScalarStyle.PLAIN && text.equals("<<"))
                  || kind == Tag.MERGE);
      int meta = style.ordinal() | r.key().ordinal() << KEY_KIND_SHIFT | index << TAG_SHIFT;
      placeKey(f, ScalarResolver.keyText(r), span, meta, merge, anchor);
      return;
    }
    if (f == null && root != null) throw JavaFailure.internal("YAML second root value");
    Node node = scalarNode(r, span, style == YamlScalarStyle.PLAIN);
    node.meta = style.ordinal() | index << TAG_SHIFT;
    if (define(anchor, node, false) >= 0) node.meta |= ANCHORED;
    attach(f, node);
    childDone(f);
  }

  /** Escapes may spell UTF-16 surrogates; only paired ones form Unicode scalars. */
  private void checkSurrogates(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) i++;
      else if (Character.isSurrogate(c)) throw fail("unpaired surrogate in a YAML quoted scalar");
    }
  }

  @Override
  public void alias(String name, YamlMark mark) {
    position = mark;
    Node target = resolveAlias(name);
    Frame f = frames.peek();
    int ordinal = addAliasTarget(target);
    if (f != null && f.map && !f.value) {
      // An alias used as a key: the target scalar's key text, kind and the alias bits.
      if (target.type == Node.MAP || target.type == Node.SEQ) throw fail("YAML complex key");
      int[] keyKind = {0};
      String text = keyText(target, keyKind);
      placeKey(f, text, 0, ALIAS | ordinal << TAG_SHIFT | keyKind[0] << KEY_KIND_SHIFT, false, null);
      return;
    }
    if (f == null && root != null) throw JavaFailure.internal("YAML second root value");
    Node node = new Node(Node.ALIAS);
    node.target = target;
    node.meta = ALIAS | ordinal << TAG_SHIFT;
    value();
    attach(f, node);
    childDone(f);
  }

  /** Key text and kind of an existing scalar node (an alias target), as node_key_text. */
  private static String keyText(Node target, int[] kind) {
    byte[] text = target.text;
    switch (target.kind) {
      case STRING -> {
        kind[0] = (target.meta >>> KEY_KIND_SHIFT) & 7;
        return new String(java.util.Objects.requireNonNull(text), StandardCharsets.UTF_8);
      }
      case DECIMAL -> {
        String digits = new String(java.util.Objects.requireNonNull(text), StandardCharsets.UTF_8);
        kind[0] = digits.indexOf('.') < 0 && digits.indexOf('e') < 0 && digits.indexOf('E') < 0
            ? KeyKind.INT.ordinal() : KeyKind.FLOAT.ordinal();
        return digits;
      }
      case NULL -> kind[0] = KeyKind.NULL.ordinal();
      case BOOL -> kind[0] = KeyKind.BOOL.ordinal();
      case F64 -> kind[0] = KeyKind.FLOAT.ordinal();
      default -> kind[0] = KeyKind.INT.ordinal();
    }
    return ScalarResolver.keyText(new Scalar(target.kind, KeyKind.STRING, target.bits, null, false));
  }

  /** Writes a scalar key into mapping {@code f}, applying the duplicate-key and merge policies. */
  private void placeKey(Frame f, String canonical, long span, int meta, boolean merge, @Nullable String anchor) {
    if (merge) {
      Node previous = f.merge;
      if (previous != null) {
        if (!lastWins) throw duplicate("<<");
        f.merge = null;
        erase(f, previous);
      }
    } else if (!f.keys.add(canonical)) {
      if (!lastWins) throw duplicate(canonical);
      erase(f, findKey(f, canonical));
    }
    byte[] text = canonical.getBytes(StandardCharsets.UTF_8);
    charge(text.length);
    Node key = new Node(Node.SCALAR);
    key.kind = Kind.STRING;
    key.key = true;
    key.text = text;
    key.span = span;
    key.inputRef = (meta & STYLE_MASK) == 0 && sameAsSource(text, span);
    key.meta = meta;
    value();
    if (define(anchor, key, false) >= 0) key.meta |= ANCHORED;
    f.node.children().add(key);
    if (merge) f.merge = key;
    f.value = true;
  }

  /** Reported at the repeated key, naming its canonical spelling (#40). */
  private JavaFailure duplicate(String key) {
    return fail("duplicate YAML key: " + key);
  }

  private static Node findKey(Frame f, String canonical) {
    byte[] text = canonical.getBytes(StandardCharsets.UTF_8);
    List<Node> children = f.node.children();
    for (int i = 0; i + 1 < children.size(); i += 2) {
      Node key = children.get(i);
      if (key != f.merge && java.util.Arrays.equals(key.text, text)) return key;
    }
    throw JavaFailure.internal("YAML duplicate key not found");
  }

  /** DuplicateKeys.LAST_WINS: removes the entry of {@code key}; it may hold no anchor or alias target. */
  private void erase(Frame f, Node key) {
    List<Node> children = f.node.children();
    int at = children.indexOf(key);
    Node value = children.get(at + 1);
    mark(key);
    mark(value);
    forbidRemoved("YAML duplicate key removes an anchored node");
    values -= count(key) + count(value);
    children.remove(at + 1);
    children.remove(at);
  }

  private static void mark(Node node) {
    node.dead = true;
    if (node.children != null) for (Node child : node.children) mark(child);
  }

  private static long count(Node node) {
    long n = 1;
    if (node.children != null) for (Node child : node.children) n += count(child);
    return n;
  }

  private void forbidRemoved(String why) {
    for (Node anchored : anchorNodes) if (anchored.dead) throw fail(why);
    for (Node target : aliasTargets) if (target.dead) throw fail(why);
  }

  // ---- collections ------------------------------------------------------------------------------

  @Override
  public void mappingStart(@Nullable String anchor, @Nullable String tag, boolean flow, YamlMark mark) {
    open(true, anchor, tag, flow, mark);
  }

  @Override
  public void sequenceStart(@Nullable String anchor, @Nullable String tag, boolean flow, YamlMark mark) {
    open(false, anchor, tag, flow, mark);
  }

  private void open(boolean map, @Nullable String anchor, @Nullable String tag, boolean flow, YamlMark mark) {
    position = mark;
    Frame parent = frames.peek();
    // A collection used as a key is a complex key, outside the scalar-key tape contract. Like the
    // native handler, an explicit key (`? [a]`, or any key of a `{...}` mapping) fails at once,
    // while an implicit one (`[a]: b`, `[[a]: b]`) is only known to be a key once it was read, so
    // its own content is decoded (and may fail first) and the rejection comes at its end.
    boolean complex = parent != null && parent.map && !parent.value;
    int content = contentAt(mark.offset());
    if (complex && (braces(parent, content) || explicitKey(mark.offset()))) throw fail("YAML complex key");
    if (parent == null && root != null) throw JavaFailure.internal("YAML second root value");
    Tag kind = classify(tag);
    int index = tag == null ? 0 : intern(tag);
    switch (kind) {
      case NONE, NONSPECIFIC, OTHER -> {}
      case MAP, SET -> {
        if (!map) throw fail("YAML mapping tag on a sequence");
      }
      case SEQ, OMAP, PAIRS -> {
        if (map) throw fail("YAML sequence tag on a mapping");
      }
      default -> throw fail("YAML scalar tag on a collection");
    }
    if (frames.size() >= limits.maxDepth()) throw limit("tape depth limit");
    value();
    Node node = new Node(map ? Node.MAP : Node.SEQ);
    node.meta = (flow ? STYLE_FLOW : STYLE_BLOCK) | index << TAG_SHIFT;
    int id = define(anchor, node, true);
    if (id >= 0) node.meta |= ANCHORED;
    if (!complex) attach(parent, node);
    frames.push(new Frame(node, map, kind, id, content, complex));
  }

  /** Whether {@code parent} is a `{...}` mapping, not a flow pair starting at {@code content}. */
  private boolean braces(Frame parent, int content) {
    return parent.content < input.length && input[parent.content] == '{' && parent.content != content;
  }

  /** Whether the node starting at {@code offset} follows a `?` explicit-key indicator. */
  private boolean explicitKey(int offset) {
    int at = Math.min(offset, input.length);
    while (at > 0 && whitespace(input[at - 1])) at--;
    return at > 0 && input[at - 1] == '?';
  }

  /** Offset of the first content byte of a node at {@code offset}, past its properties. */
  private int contentAt(int offset) {
    int at = offset;
    for (;;) {
      while (at < input.length && whitespace(input[at])) at++;
      if (at >= input.length || (input[at] != '&' && input[at] != '!')) return at;
      while (at < input.length && !whitespace(input[at])) at++;
    }
  }

  @Override
  public void mappingEnd(YamlMark mark) {
    close(mark);
  }

  @Override
  public void sequenceEnd(YamlMark mark) {
    close(mark);
  }

  private void close(YamlMark mark) {
    position = mark;
    Frame f = frames.pop();
    if (f.complex) throw fail("YAML complex key");
    if (f.map && f.merge != null) merge(f);
    validateShape(f);
    if (f.anchor >= 0) anchorOpen.set(f.anchor, false);
    childDone(frames.peek());
  }

  private void attach(@Nullable Frame f, Node node) {
    if (f == null) root = node;
    else f.node.children().add(node);
  }

  private static void childDone(@Nullable Frame f) {
    if (f != null && f.map) f.value = false;
  }

  /** !!set: a mapping of nulls (or empty plain scalars); !!omap, !!pairs: single-pair mappings. */
  private void validateShape(Frame f) {
    List<Node> children = f.node.children();
    if (f.tag == Tag.SET) {
      for (int i = 1; i < children.size(); i += 2) {
        Node v = children.get(i);
        boolean empty = v.type == Node.SCALAR && v.kind == Kind.STRING
            && java.util.Objects.requireNonNull(v.text).length == 0 && v.meta == 0;
        boolean isNull = v.type == Node.SCALAR && v.kind == Kind.NULL;
        if ((!isNull && !empty) || (v.meta & ALIAS) != 0) throw fail("YAML !!set with a non-null value");
      }
    } else if (f.tag == Tag.OMAP || f.tag == Tag.PAIRS) {
      for (Node e : children)
        if (e.type != Node.MAP || e.children().size() != 2)
          throw fail(f.tag == Tag.OMAP ? "YAML !!omap entry is not a single-pair mapping"
              : "YAML !!pairs entry is not a single-pair mapping");
    }
  }

  // ---- merge keys (merge.h) ---------------------------------------------------------------------

  /**
   * Resolves {@code <<} when its mapping closes: merged entries take its position, sources in
   * order and each source's entries in order; explicit keys win wherever they appear and earlier
   * sources win. Aliased sources contribute key copies plus references to their values (never deep
   * copies); inline sources move their entries. The {@code <<} entry, inline source nodes, their
   * keys and overridden entries disappear, and an anchor or alias target among them is an error.
   */
  private void merge(Frame f) {
    Node mergeKey = java.util.Objects.requireNonNull(f.merge);
    f.merge = null;
    List<Node> children = f.node.children();
    int at = children.indexOf(mergeKey);
    Node mergeValue = children.get(at + 1);
    List<Node> sources = new ArrayList<>();
    Set<Node> inline = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    if (mergeValue.type == Node.SEQ) {
      for (Node element : mergeValue.children()) addSource(element, sources, inline);
    } else {
      addSource(mergeValue, sources, inline);
    }
    long before = 0;
    for (Node child : children) before += count(child);
    ArrayList<Node> rewritten = new ArrayList<>(children.size());
    long entries = 0;
    for (int i = 0; i < children.size(); i += 2) {
      Node key = children.get(i);
      if (key != mergeKey) {
        rewritten.add(key);
        rewritten.add(children.get(i + 1));
        entries++;
        continue;
      }
      mergeKey.dead = true;
      mergeValue.dead = true;
      if (mergeValue.type == Node.SEQ) for (Node element : mergeValue.children()) element.dead = true;
      for (Node source : sources) {
        boolean moved = inline.contains(source);
        source.dead |= moved;
        List<Node> entriesOf = source.children();
        for (int j = 0; j < entriesOf.size(); j += 2) {
          Node kj = entriesOf.get(j), vj = entriesOf.get(j + 1);
          String text = new String(java.util.Objects.requireNonNull(kj.text), StandardCharsets.UTF_8);
          // An inline source's key node is replaced by its copy; its value moves when merged.
          if (moved) kj.dead = true;
          if (!f.keys.add(text)) {
            if (moved) mark(vj);
            continue;
          }
          Node copy = new Node(Node.SCALAR);
          copy.kind = kj.kind;
          copy.key = true;
          copy.text = kj.text;
          copy.span = kj.span;
          copy.inputRef = kj.inputRef;
          copy.meta = kj.meta & ~ANCHORED;
          rewritten.add(copy);
          if (moved) {
            rewritten.add(vj);
          } else {
            Node target = (vj.meta & ALIAS) != 0 && vj.type == Node.ALIAS
                ? java.util.Objects.requireNonNull(vj.target) : vj;
            if (aliasTargets.size() >= limits.maxAliases() || aliasTargets.size() + 1 >= META_LIMIT)
              throw limit("aliases");
            Node ref = new Node(Node.ALIAS);
            ref.target = target;
            ref.meta = ALIAS | addAliasTarget(target) << TAG_SHIFT;
            rewritten.add(ref);
          }
          entries++;
        }
      }
    }
    forbidRemoved("YAML merge removes an anchored node");
    long after = 0;
    for (Node child : rewritten) after += count(child);
    values -= before;
    if (after > limits.maxNodes() - values) throw limit("tape value limit");
    values += after;
    children.clear();
    children.addAll(rewritten);
    if (entries * 2 != children.size()) throw JavaFailure.internal("YAML merge entry count");
  }

  private void addSource(Node node, List<Node> sources, Set<Node> inline) {
    if (node.type == Node.ALIAS) {
      Node target = java.util.Objects.requireNonNull(node.target);
      if (target.type != Node.MAP) throw fail("YAML merge of a non-mapping");
      sources.add(target);
    } else if (node.type == Node.MAP) {
      sources.add(node);
      inline.add(node);
    } else {
      throw fail("YAML merge of a non-mapping");
    }
  }

  // ---- publication ------------------------------------------------------------------------------

  private Document finish() {
    Node top = root;
    boolean present = documents > 0;
    if (top == null) {
      top = new Node(Node.SCALAR);
      top.kind = Kind.NULL;
    }
    TapeBuilder builder = new TapeBuilder(input);
    write(builder, top);
    HeapTape tape = builder.build(TapeBuilder.HAS_META | TapeBuilder.YAML);
    // maxIndexBytes bounds the native session arena, which also holds rapidyaml's stack, presized
    // reservations and scratch; the kernel charges what it publishes (words, metadata, pool and
    // directories), a lower bound of the native figure.
    long index = tape.nodeCount() * 12 + tape.poolSize() + tape.directorySize() * 8;
    if (index > limits.maxIndexBytes()) throw limit("index allocation limit");
    int[] words = new int[Math.toIntExact(tape.nodeCount())];
    for (int i = 0; i < words.length; i++) words[i] = tape.metadata(i);
    long[] anchors = new long[anchorNodes.size()];
    for (int i = 0; i < anchors.length; i++) anchors[i] = anchorNodes.get(i).index;
    YamlMetadata metadata =
        new YamlMetadata(
            words,
            tags.toArray(new String[0]),
            anchors,
            anchorNames.toArray(new String[0]),
            new YamlDirectives(version, directives),
            limits.maxAliasExpansion());
    return new Document(tape, metadata, present, schema == YamlSchema.YAML_1_1);
  }

  private static void write(TapeBuilder out, Node node) {
    switch (node.type) {
      case Node.MAP, Node.SEQ -> {
        int at = out.begin(node.type == Node.MAP, node.meta);
        node.index = at;
        for (Node child : node.children()) write(out, child);
        out.end(at);
      }
      case Node.ALIAS -> {
        Node target = java.util.Objects.requireNonNull(node.target);
        if (target.index < 0) throw JavaFailure.internal("YAML alias target follows its alias");
        node.index = out.ref(target.index, node.meta);
      }
      default -> node.index = scalar(out, node);
    }
  }

  private static int scalar(TapeBuilder out, Node node) {
    long span = node.span;
    return switch (node.kind) {
      case NULL -> out.nullValue(span, node.meta);
      case BOOL -> out.booleanValue(node.bits != 0, span, node.meta);
      case I64 -> out.longValue(node.bits, span, node.meta);
      case U64 -> out.unsignedValue(node.bits, span, node.meta);
      case F64 -> out.doubleValue(Double.longBitsToDouble(node.bits), span, node.meta);
      case DECIMAL -> out.decimalValue(java.util.Objects.requireNonNull(node.text), span, node.meta);
      case STRING -> {
        byte[] text = java.util.Objects.requireNonNull(node.text);
        yield node.inputRef
            ? out.stringRef(span, ascii(text), node.key, node.meta)
            : out.string(text, span, ascii(text), node.key, node.meta);
      }
    };
  }
}
