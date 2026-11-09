package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.TapeSink;
import dev.elide.truffledata.tape.TapeText;
import java.io.Flushable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Streaming YAML writer. As a {@link TapeSink} it accepts any tape export ({@code
 * value.export(writer)}, JSON or YAML); explicit methods add what a sink lacks: anchors and
 * aliases by name, tags, per-node style hints, mapping key kinds and document boundaries.
 *
 * <p>Every scalar is written so that it reads back as the same value under {@link
 * YamlEmitOptions#schema()}: a string is plain only when {@link ScalarResolver#plainIsString}
 * holds and the spelling is syntactically safe in its context; otherwise it is single or double
 * quoted, or a literal block scalar when multi-line. Numbers, booleans and nulls are checked
 * against the resolver and receive a core tag when the schema has no plain spelling for them.
 * Unrepresentable input (an unpaired surrogate, a tag that contradicts the value) throws {@link
 * IllegalArgumentException}; misuse of the event protocol throws {@link IllegalStateException}.
 *
 * <p>Output is written to the {@link Appendable} as it is produced, except that {@link
 * YamlEmitOptions#sortKeys()} buffers each outermost mapping until it ends, and a block
 * collection is held until its first child (an empty one is written {@code {}} / {@code []}).
 * Lines are never folded; comments are not supported. The writer is not thread-safe.
 *
 * <p>Documents: the first value starts a document; a second root value starts another, preceded
 * by {@code ---}. {@link #beginDocument()} forces an explicit {@code ---}, {@link #endDocument()}
 * ends one ({@code ...} with {@link YamlEmitOptions#explicitEnd()}), and {@link #close()} ends
 * the last. Anchors are scoped to a document. Anchor names are kept unless a name is redefined
 * within a document, in which case later definitions receive a fresh suffix; tape anchor ids
 * ({@link #anchor(int)}) are named {@code a<id>}.
 */
public final class YamlWriter implements TapeSink, AutoCloseable {
  private static final class Frame {
    boolean map;
    boolean flow;
    boolean opened;
    /** The next block entry or key continues the current line (compact form after "- "). */
    boolean inline;
    boolean expectKey;
    /** The current member's key used the explicit "? " form. */
    boolean explicitValue;
    /** The previous member's value was a block sequence at this mapping's indentation. */
    boolean indentless;
    long count;
    /** Block: column of keys or "-" entries. */
    int indent;
    String props = "";
  }

  private final Appendable out;
  private final YamlEmitOptions options;
  private Frame[] frames = new Frame[16];
  private int depth;

  private boolean atLineStart = true;
  private boolean docOpen;
  private boolean rootDone;
  private boolean markerOnLine;
  private long documents;

  private @Nullable Object pendingToken;
  private @Nullable String pendingAnchor;
  private @Nullable String pendingTag;
  private @Nullable Style pendingStyle;

  /** Anchor tokens written in this document, by their output names. */
  private final Map<Object, String> tokenNames = new HashMap<>();
  private final Set<String> usedNames = new HashSet<>();
  private final Map<String, Integer> nextSuffix = new HashMap<>();
  /** Explicit anchor names to their latest definition's token. */
  private final Map<String, Object> latest = new HashMap<>();

  private @Nullable Recorder recorder;

  public YamlWriter(Appendable out) {
    this(out, YamlEmitOptions.DEFAULT);
  }

  public YamlWriter(Appendable out, YamlEmitOptions options) {
    this.out = Objects.requireNonNull(out);
    this.options = Objects.requireNonNull(options);
  }

  public YamlEmitOptions options() {
    return options;
  }

  // ---- public event API --------------------------------------------------------------------

  /** Starts a block or flow mapping; the member count is a hint and may be negative (unknown). */
  @Override
  public void beginObject(long members) {
    begin(true);
  }

  @Override
  public void beginArray(long elements) {
    begin(false);
  }

  public void beginMapping() {
    begin(true);
  }

  public void beginSequence() {
    begin(false);
  }

  private void begin(boolean map) {
    Recorder r = recorder;
    if (r != null) {
      r.add(new Event(map ? Event.MAP : Event.SEQ));
      r.depth++;
      return;
    }
    if (map && options.sortKeys()) {
      recorder = r = new Recorder();
      r.add(new Event(Event.MAP));
      r.depth = 1;
      return;
    }
    coreBegin(map);
  }

  @Override
  public void end() {
    Recorder r = recorder;
    if (r != null) {
      r.add(new Event(Event.END));
      if (--r.depth == 0) {
        recorder = null;
        r.replay(this);
      }
      return;
    }
    coreEnd();
  }

  @Override
  public void key(TapeText key) {
    key(key.toJavaString(), null);
  }

  public void key(String key) {
    key(key, null);
  }

  /**
   * A mapping key that originally resolved as {@code kind}: when a plain spelling reads back
   * with that kind and this canonical text it is written plain, otherwise as a string.
   */
  public void key(String key, @Nullable KeyKind kind) {
    YamlScalars.checkText(key);
    Recorder r = recorder;
    if (r != null) {
      Event e = new Event(Event.KEY);
      e.text = key;
      e.kind = kind;
      r.add(e);
      return;
    }
    coreKey(key, kind);
  }

  @Override
  public void nullValue() {
    scalar(Event.NULL, 0, null);
  }

  @Override
  public void booleanValue(boolean value) {
    scalar(Event.BOOL, value ? 1 : 0, null);
  }

  @Override
  public void longValue(long value) {
    scalar(Event.I64, value, null);
  }

  @Override
  public void doubleValue(double value) {
    scalar(Event.F64, Double.doubleToRawLongBits(value), null);
  }

  @Override
  public void unsignedValue(long bits) {
    scalar(Event.U64, bits, null);
  }

  @Override
  public void decimalValue(TapeText token) {
    decimalValue(token.toJavaString());
  }

  /** A number token beyond the 64-bit and binary64 ranges, written with its exact digits. */
  public void decimalValue(String token) {
    scalar(Event.DECIMAL, 0, Objects.requireNonNull(token));
  }

  @Override
  public void stringValue(TapeText text) {
    value(text.toJavaString());
  }

  public void value(String text) {
    YamlScalars.checkText(text);
    scalar(Event.STRING, 0, text);
  }

  public void value(long value) {
    longValue(value);
  }

  public void value(double value) {
    doubleValue(value);
  }

  public void value(boolean value) {
    booleanValue(value);
  }

  private void scalar(int op, long bits, @Nullable String text) {
    Recorder r = recorder;
    if (r != null) {
      Event e = new Event(op);
      e.bits = bits;
      e.text = text;
      r.add(e);
      return;
    }
    coreScalar(op, bits, text);
  }

  /** Tag of the next node, as a resolved URI ({@code tag:yaml.org,2002:str}, {@code !local}). */
  public void tag(String uri) {
    YamlScalars.tag(uri); // validates
    Recorder r = recorder;
    if (r != null) {
      Event e = new Event(Event.TAG);
      e.text = uri;
      r.add(e);
      return;
    }
    roll();
    if (pendingTag != null) throw new IllegalStateException("YAML node already has a tag");
    pendingTag = uri;
  }

  /** Honored only with {@link YamlEmitOptions#preserveMetadata()}. */
  @Override
  public void tag(TapeText uri) {
    if (options.preserveMetadata()) tag(uri.toJavaString());
  }

  /** Anchors the next node as {@code name}; a later {@link #alias(String)} refers to it. */
  public void anchor(String name) {
    YamlScalars.checkAnchor(name);
    if (recorder == null) roll();
    Object token = new Object();
    latest.put(name, token);
    anchorToken(token, name);
  }

  /** Honored only with {@link YamlEmitOptions#preserveMetadata()}. */
  @Override
  public void anchor(int id) {
    if (options.preserveMetadata()) anchorToken(id, "a" + id);
  }

  private void anchorToken(Object token, String name) {
    Recorder r = recorder;
    if (r != null) {
      Event e = new Event(Event.ANCHOR);
      e.token = token;
      e.text = name;
      r.add(e);
      r.defined.add(token);
      return;
    }
    roll();
    if (pendingToken != null) throw new IllegalStateException("YAML node already has an anchor");
    pendingToken = token;
    pendingAnchor = name;
  }

  /** Whether {@code name} is anchored earlier in the current document. */
  public boolean hasAnchor(String name) {
    return latest.containsKey(name);
  }

  /** A reference to the latest node anchored as {@code name} in this document. */
  public void alias(String name) {
    if (recorder == null) roll();
    Object token = latest.get(name);
    if (token == null) throw new IllegalArgumentException("Undefined YAML anchor: " + name);
    aliasToken(token);
  }

  /**
   * With {@link YamlEmitOptions#preserveMetadata()}, writes an alias to an anchor this writer has
   * seen in the current document and returns true; otherwise returns false so the exporter
   * expands the target.
   */
  @Override
  public boolean alias(int id) {
    if (!options.preserveMetadata()) return false;
    Recorder r = recorder;
    if (!tokenNames.containsKey(id) && (r == null || !r.defined.contains(id))) return false;
    aliasToken(id);
    return true;
  }

  private void aliasToken(Object token) {
    Recorder r = recorder;
    if (r != null) {
      Event e = new Event(Event.ALIAS);
      e.token = token;
      r.add(e);
      return;
    }
    coreAlias(token);
  }

  /**
   * Presentation hint for the next node or key: PLAIN, SINGLE, DOUBLE, LITERAL or FOLDED for
   * scalars (FOLDED is written literal), BLOCK or FLOW for collections. A hint that cannot
   * represent the value exactly, or does not fit the context, falls back to the default choice.
   */
  public void style(Style style) {
    Objects.requireNonNull(style);
    Recorder r = recorder;
    if (r != null) {
      Event e = new Event(Event.STYLE);
      e.style = style;
      r.add(e);
      return;
    }
    pendingStyle = style;
  }

  /** Starts a document with an explicit {@code ---}, ending the previous one if complete. */
  public void beginDocument() {
    checkIdle();
    if (docOpen) {
      if (!rootDone) throw new IllegalStateException("YAML document has no root value yet");
      finishDocument();
    }
    openDocument(true);
  }

  /** Ends the current document; its root value must be complete. */
  public void endDocument() {
    checkIdle();
    if (!docOpen || !rootDone) throw new IllegalStateException("No complete YAML document");
    finishDocument();
  }

  /** A root value after a complete one starts the next document. */
  private void roll() {
    if (depth == 0 && docOpen && rootDone) finishDocument();
  }

  private void finishDocument() {
    newline();
    if (options.explicitEnd()) write("...\n");
    documents++;
    docOpen = false;
    rootDone = false;
    markerOnLine = false;
    tokenNames.clear();
    usedNames.clear();
    nextSuffix.clear();
    latest.clear();
  }

  /** Ends the last document and flushes a {@link Flushable} target; does not close it. */
  @Override
  public void close() {
    checkIdle();
    if (docOpen) {
      if (!rootDone) throw new IllegalStateException("YAML document has no root value");
      finishDocument();
    }
    if (out instanceof Flushable flushable) {
      try {
        flushable.flush();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private void checkIdle() {
    if (depth != 0 || recorder != null)
      throw new IllegalStateException("YAML collection is still open");
    if (pendingToken != null || pendingTag != null)
      throw new IllegalStateException("YAML anchor or tag without a node");
  }

  // ---- core emission ---------------------------------------------------------------------

  private void write(String s) {
    if (s.isEmpty()) return;
    try {
      out.append(s);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    atLineStart = s.charAt(s.length() - 1) == '\n';
    markerOnLine = false;
  }

  private void newline() {
    if (!atLineStart) write("\n");
  }

  private void spaces(int n) {
    if (n > 0) write(" ".repeat(n));
  }

  private @Nullable Frame top() {
    return depth == 0 ? null : frames[depth - 1];
  }

  /** The innermost collection, with a held block opening written. */
  private @Nullable Frame current() {
    Frame f = top();
    if (f != null && !f.opened) open(f, depth - 2);
    return f;
  }

  private void openDocument(boolean marker) {
    docOpen = true;
    rootDone = false;
    if (documents > 0 && !atLineStart) newline();
    if (marker) {
      write("---");
      markerOnLine = true;
    }
  }

  /** The value slot is free: root before its value, a sequence, or a mapping after its key. */
  private @Nullable Frame valueSlot() {
    if (depth == 0) {
      roll();
      return null;
    }
    Frame p = current();
    if (p != null && p.map && p.expectKey)
      throw new IllegalStateException("YAML mapping expects a key");
    return p;
  }

  private String takeProps() {
    String props = "";
    Object token = pendingToken;
    if (token != null) {
      String name = Objects.requireNonNull(pendingAnchor);
      // Sorting can reorder a redefined name around its aliases: each definition gets its own.
      // The next suffix is remembered per name, so redefinitions stay linear overall.
      if (options.sortKeys() && usedNames.contains(name)) {
        String base = name;
        int n = nextSuffix.getOrDefault(base, 2);
        while (usedNames.contains(base + "_" + n)) n++;
        nextSuffix.put(base, n + 1);
        name = base + "_" + n;
      }
      usedNames.add(name);
      tokenNames.put(token, name);
      props = "&" + name;
      pendingToken = null;
      pendingAnchor = null;
    }
    String tag = pendingTag;
    if (tag != null) {
      props = props.isEmpty() ? YamlScalars.tag(tag) : props + " " + YamlScalars.tag(tag);
      pendingTag = null;
    }
    return props;
  }

  /**
   * Writes what precedes a node in parent {@code p} (null for the root), then its properties.
   * An inline node continues the line; a block collection's children follow on new lines (or,
   * compactly, on this one after "- ").
   */
  private void prefix(@Nullable Frame p, boolean block, String props) {
    boolean sameLine = !block || !props.isEmpty();
    if (p == null) {
      // Properties of a root block collection need a line of their own after "---".
      if (!docOpen)
        openDocument(options.explicitStart() || documents > 0 || (block && !props.isEmpty()));
      if (markerOnLine && sameLine) write(" ");
    } else if (p.flow) {
      if (p.map) write(" ");
      else if (p.count++ > 0) write(", ");
    } else if (p.map) {
      if (sameLine) write(" ");
    } else {
      if (!p.inline) {
        newline();
        spaces(p.indent);
      }
      p.inline = false;
      p.count++;
      write("- ");
    }
    if (!props.isEmpty()) {
      write(props);
      if (!block) write(" ");
    }
  }

  private void afterValue() {
    Frame p = top();
    if (p == null) {
      rootDone = true;
    } else if (p.map) {
      p.expectKey = true;
      p.explicitValue = false;
    }
  }

  private void coreBegin(boolean map) {
    Frame p = valueSlot();
    if (depth == frames.length) frames = Arrays.copyOf(frames, depth * 2);
    Frame f = frames[depth];
    if (f == null) frames[depth] = f = new Frame();
    Style hint = pendingStyle;
    pendingStyle = null;
    f.map = map;
    f.flow =
        (p != null && p.flow)
            || (hint == Style.FLOW)
            || (hint != Style.BLOCK && depth >= options.flowDepth());
    f.opened = false;
    f.inline = false;
    f.expectKey = map;
    f.explicitValue = false;
    f.indentless = false;
    f.count = 0;
    f.props = takeProps();
    depth++;
    if (f.flow) open(f, depth - 2);
  }

  /** Writes a held opening of {@code f}, whose parent is {@code frames[parent]} (or the root). */
  private void open(Frame f, int parent) {
    Frame p = parent < 0 ? null : frames[parent];
    f.opened = true;
    if (f.flow) {
      prefix(p, false, f.props);
      write(f.map ? "{" : "[");
      return;
    }
    prefix(p, true, f.props);
    boolean props = !f.props.isEmpty();
    if (p == null) {
      f.indent = 0;
    } else if (p.map) {
      boolean indented = f.map || options.indentSequences() || props || p.explicitValue;
      f.indent = p.indent + (indented ? options.indent() : 0);
      p.indentless = !indented;
    } else {
      f.indent = p.indent + 2;
      f.inline = !props;
    }
  }

  private void coreEnd() {
    if (depth == 0) throw new IllegalStateException("No YAML collection to end");
    Frame f = frames[depth - 1];
    if (f.map && !f.expectKey) throw new IllegalStateException("YAML key has no value");
    if (pendingToken != null || pendingTag != null)
      throw new IllegalStateException("YAML anchor or tag without a node");
    pendingStyle = null;
    depth--;
    if (!f.opened) {
      // An empty block collection is written in flow form in its parent's slot.
      prefix(top(), false, f.props);
      write(f.map ? "{}" : "[]");
    } else if (f.flow) {
      write(f.map ? "}" : "]");
    }
    afterValue();
  }

  private void coreKey(String key, @Nullable KeyKind kind) {
    Frame f = top();
    if (f == null || !f.map || !f.expectKey)
      throw new IllegalStateException("YAML key outside a mapping key position");
    Style hint = pendingStyle;
    pendingStyle = null;
    String props = takeProps();
    String text = keyText(key, kind, hint, f.flow);
    if (!props.isEmpty()) text = props + " " + text;
    if (!f.opened && !f.flow && !f.props.isEmpty() && depth >= 2 && !frames[depth - 2].map
        && text.codePointCount(0, text.length()) > YamlScalars.IMPLICIT_KEY_LIMIT) {
      // rapidyaml gives a sequence entry's "- &a" to an explicit first key on the next line,
      // not to the mapping: such a mapping is written in flow style.
      f.flow = true;
      text = keyText(key, kind, hint, true);
      if (!props.isEmpty()) text = props + " " + text;
    }
    current();
    // rapidyaml rejects or misreads an implicit key starting with an indicator or '.' right
    // after an indentless sequence ("-x", ".5"); the explicit form reads back exactly.
    boolean explicit =
        text.codePointCount(0, text.length()) > YamlScalars.IMPLICIT_KEY_LIMIT
            || (f.indentless && !safeAfterIndentless(text.charAt(0)));
    f.indentless = false;
    if (f.flow) {
      if (f.count > 0) write(", ");
      write(explicit ? "? " + text + ":" : text + ":");
    } else {
      if (!f.inline) {
        newline();
        spaces(f.indent);
      }
      f.inline = false;
      if (explicit) {
        write("? " + text);
        newline();
        spaces(f.indent);
        write(":");
      } else {
        write(text + ":");
      }
    }
    f.count++;
    f.expectKey = false;
    f.explicitValue = explicit;
  }

  private static boolean safeAfterIndentless(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '"'
        || c == '\'' || c == '_' || c >= 0x80;
  }

  private String keyText(String key, @Nullable KeyKind kind, @Nullable Style hint, boolean flow) {
    YamlSchema schema = options.schema();
    if (kind != null && kind != KeyKind.STRING && schema != YamlSchema.FAILSAFE) {
      String candidate =
          kind == KeyKind.NULL ? (options.tildeNull() && schema != YamlSchema.JSON ? "~" : "null")
              : key;
      if (YamlScalars.plainSyntax(candidate, schema, flow)) {
        try {
          ScalarResolver.Scalar r = ScalarResolver.resolvePlain(candidate, schema);
          if (r.key() == kind && ScalarResolver.keyText(r).equals(key)) return candidate;
        } catch (ScalarResolver.ResolveException ignored) {
          // not representable plain under this schema: written as a string
        }
      }
    }
    boolean plain = YamlScalars.plain(key, schema, flow);
    if (plain && hint != Style.SINGLE && hint != Style.DOUBLE) return key;
    return quoted(key, hint == Style.DOUBLE ? Style.DOUBLE : hint == Style.SINGLE ? Style.SINGLE
        : options.quote());
  }

  private static String quoted(String s, Style preferred) {
    return preferred == Style.SINGLE && YamlScalars.singleQuotable(s)
        ? YamlScalars.singleQuoted(s)
        : YamlScalars.doubleQuoted(s);
  }

  private void coreAlias(Object token) {
    String name = tokenNames.get(token);
    if (name == null) throw new IllegalArgumentException("YAML alias to an anchor not yet written");
    if (pendingToken != null || pendingTag != null)
      throw new IllegalStateException("YAML aliases cannot carry anchors or tags");
    pendingStyle = null;
    Frame p = valueSlot();
    prefix(p, false, "");
    write("*" + name);
    afterValue();
  }

  private void coreScalar(int op, long bits, @Nullable String text) {
    Frame p = valueSlot();
    Style hint = pendingStyle;
    pendingStyle = null;
    if (op == Event.STRING) {
      stringScalar(p, Objects.requireNonNull(text), hint);
    } else {
      String spelling = typed(op, bits, text);
      prefix(p, false, takeProps());
      write(spelling);
    }
    afterValue();
  }

  // ---- strings -------------------------------------------------------------------------------

  private void stringScalar(@Nullable Frame p, String s, @Nullable Style hint) {
    boolean flow = p != null && p.flow;
    YamlSchema schema = options.schema();
    boolean literalWanted =
        hint == Style.LITERAL
            || hint == Style.FOLDED
            || (hint == null || hint == Style.PLAIN) && options.blockScalars() && s.indexOf('\n') >= 0;
    if (literalWanted && !flow && YamlScalars.literal(s)
        && (p != null || !YamlScalars.needsIndentIndicator(s))) {
      literal(p, s);
      return;
    }
    String text;
    if (hint != Style.SINGLE && hint != Style.DOUBLE
        && (YamlScalars.plain(s, schema, flow)
            || (stringTag(pendingTag) && YamlScalars.plainSyntax(s, schema, flow)))) {
      text = s;
    } else {
      text = quoted(s, hint == Style.DOUBLE || hint == Style.SINGLE ? hint : options.quote());
    }
    prefix(p, false, takeProps());
    write(text);
  }

  /** A tag under which any scalar spelling, plain included, is a string. */
  private static boolean stringTag(@Nullable String uri) {
    if (uri == null) return false;
    ScalarResolver.Tag tag = ScalarResolver.classify(uri);
    return tag == ScalarResolver.Tag.STR || tag == ScalarResolver.Tag.OTHER
        || tag == ScalarResolver.Tag.NONSPECIFIC;
  }

  private void literal(@Nullable Frame p, String s) {
    int base = p == null ? 0 : p.indent;
    int column = base + options.indent();
    StringBuilder header = new StringBuilder("|");
    if (YamlScalars.needsIndentIndicator(s)) header.append((char) ('0' + options.indent()));
    char chomp = YamlScalars.chomping(s);
    if (chomp != 0) header.append(chomp);
    prefix(p, false, takeProps());
    write(header.toString());
    int end = s.length();
    while (end > 0 && s.charAt(end - 1) == '\n') end--;
    int trailing = s.length() - end;
    String indent = " ".repeat(column);
    StringBuilder body = new StringBuilder(end + 16);
    int start = 0;
    while (true) {
      int nl = s.indexOf('\n', start);
      if (nl < 0 || nl >= end) nl = end;
      body.append('\n');
      if (nl > start) body.append(indent).append(s, start, nl);
      if (nl == end) break;
      start = nl + 1;
    }
    // Kept trailing breaks: the content's final break, then one blank line per extra break.
    if (chomp == '+') body.append("\n".repeat(trailing));
    write(body.toString());
  }

  // ---- typed scalars ---------------------------------------------------------------------

  private static final String CORE = ScalarResolver.CORE_PREFIX;

  /**
   * The spelling of a non-string scalar, setting a core tag when the schema needs one; throws
   * when no spelling reads back as the same value.
   */
  private String typed(int op, long bits, @Nullable String text) {
    YamlSchema schema = options.schema();
    String spelling;
    String type;
    switch (op) {
      case Event.NULL -> {
        spelling = options.tildeNull() && schema != YamlSchema.JSON ? "~" : "null";
        type = "null";
      }
      case Event.BOOL -> {
        spelling = bits != 0 ? "true" : "false";
        type = "bool";
      }
      case Event.I64 -> {
        spelling = Long.toString(bits);
        type = "int";
      }
      case Event.U64 -> {
        spelling = Long.toUnsignedString(bits);
        type = "int";
      }
      case Event.F64 -> {
        spelling = YamlScalars.floatSpelling(Double.longBitsToDouble(bits), schema);
        type = "float";
      }
      default -> {
        spelling = Objects.requireNonNull(text);
        type = integral(spelling) ? "int" : "float";
      }
    }
    String tag = pendingTag;
    if (tag != null) {
      if (!matches(op, bits, text, tagged(spelling, ScalarResolver.classify(tag), schema)))
        throw new IllegalArgumentException(
            "YAML " + YamlScalars.tag(tag) + " cannot carry " + spelling);
      return spelling;
    }
    if (schema != YamlSchema.FAILSAFE && YamlScalars.plainSyntax(spelling, schema, false)) {
      ScalarResolver.Scalar r;
      try {
        r = ScalarResolver.resolvePlain(spelling, schema);
      } catch (ScalarResolver.ResolveException e) {
        r = null;
      }
      if (r != null && matches(op, bits, text, r)) return spelling;
    }
    if (matches(op, bits, text, tagged(spelling, ScalarResolver.classify(CORE + type), schema))) {
      pendingTag = CORE + type;
      return spelling;
    }
    throw new IllegalArgumentException(
        "YAML " + schema + " schema cannot represent " + spelling + " exactly");
  }

  private static boolean integral(String token) {
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (c == '.' || c == 'e' || c == 'E' || c == 'i' || c == 'n') return false;
    }
    return true;
  }

  private static ScalarResolver.@Nullable Scalar tagged(
      String spelling, ScalarResolver.Tag tag, YamlSchema schema) {
    try {
      return ScalarResolver.resolveTagged(spelling, tag, schema);
    } catch (ScalarResolver.ResolveException e) {
      return null;
    }
  }

  private static boolean matches(
      int op, long bits, @Nullable String text, ScalarResolver.@Nullable Scalar r) {
    if (r == null) return false;
    return switch (op) {
      case Event.NULL -> r.kind() == ScalarResolver.Kind.NULL;
      case Event.BOOL -> r.kind() == ScalarResolver.Kind.BOOL && r.bits() == bits;
      case Event.I64 -> r.kind() == ScalarResolver.Kind.I64 && r.bits() == bits;
      case Event.U64 -> r.kind() == ScalarResolver.Kind.U64 && r.bits() == bits;
      case Event.F64 -> {
        if (r.kind() != ScalarResolver.Kind.F64) yield false;
        double want = Double.longBitsToDouble(bits);
        yield Double.isNaN(want) ? Double.isNaN(r.doubleValue()) : r.bits() == bits;
      }
      default -> r.kind() == ScalarResolver.Kind.DECIMAL && Objects.equals(r.text(), text);
    };
  }

  // ---- key sorting: buffered events --------------------------------------------------------

  private static final class Event {
    static final int MAP = 0, SEQ = 1, END = 2, KEY = 3, NULL = 4, BOOL = 5, I64 = 6, U64 = 7,
        F64 = 8, DECIMAL = 9, STRING = 10, TAG = 11, ANCHOR = 12, ALIAS = 13, STYLE = 14;
    final int op;
    long bits;
    @Nullable String text;
    @Nullable Object token;
    @Nullable Style style;
    @Nullable KeyKind kind;

    Event(int op) {
      this.op = op;
    }
  }

  /** A buffered node: properties, then a scalar, alias or collection. */
  private static final class Node {
    final List<Event> props = new ArrayList<>(0);
    @Nullable Event value;
    @Nullable Object anchor;
    final List<Node> children = new ArrayList<>(0);
    /** For mapping members: the key event and the style, tag and anchor events before it. */
    @Nullable Event key;
    List<Event> keyProps = List.of();
    boolean map;
  }

  private static final class Recorder {
    final List<Event> events = new ArrayList<>();
    final Set<Object> defined = new HashSet<>();
    int depth;
    private int at;
    private final Map<Object, Node> definitions = new HashMap<>();

    void add(Event e) {
      events.add(e);
    }

    void replay(YamlWriter w) {
      at = 0;
      Node root = node();
      w.replay(root, definitions);
    }

    private Node node() {
      Node n = new Node();
      while (true) {
        Event e = events.get(at++);
        switch (e.op) {
          case Event.TAG, Event.STYLE -> n.props.add(e);
          case Event.ANCHOR -> {
            n.props.add(e);
            n.anchor = e.token;
            definitions.put(Objects.requireNonNull(e.token), n);
          }
          case Event.MAP, Event.SEQ -> {
            n.value = e;
            n.map = e.op == Event.MAP;
            while (events.get(at).op != Event.END) {
              if (n.map) {
                List<Event> keyProps = new ArrayList<>(0);
                List<Event> keyTexts = new ArrayList<>(0);
                Event k = events.get(at++);
                while (k.op != Event.KEY) {
                  keyProps.add(k);
                  if (k.op == Event.ANCHOR) {
                    // An alias sorted before an anchored key expands to the key's text there.
                    Node text = new Node();
                    text.props.add(k);
                    text.anchor = k.token;
                    text.value = new Event(Event.STRING);
                    keyTexts.add(text.value);
                    definitions.put(Objects.requireNonNull(k.token), text);
                  }
                  k = events.get(at++);
                }
                for (Event text : keyTexts) text.text = k.text;
                Node child = node();
                child.key = k;
                child.keyProps = keyProps;
                n.children.add(child);
              } else {
                n.children.add(node());
              }
            }
            at++;
            return n;
          }
          default -> {
            n.value = e;
            return n;
          }
        }
      }
    }
  }

  /**
   * Buffered properties become pending again. A key's anchor already written (an alias sorted
   * before the key expanded it) is dropped: keys cannot be aliases.
   */
  private void apply(List<Event> props, boolean key) {
    for (Event e : props) {
      switch (e.op) {
        case Event.TAG -> pendingTag = e.text;
        case Event.STYLE -> pendingStyle = e.style;
        default -> {
          if (key && tokenNames.containsKey(Objects.requireNonNull(e.token))) continue;
          pendingToken = e.token;
          pendingAnchor = e.text;
        }
      }
    }
  }

  private static final Comparator<Node> BY_KEY =
      Comparator.comparing(n -> Objects.requireNonNull(Objects.requireNonNull(n.key).text));

  /**
   * Emits a buffered node with sorted mappings. Sorting can move an alias before its anchor; the
   * anchored node is then written (with its anchor) at the first position reached, and its
   * original position becomes an alias.
   */
  private void replay(Node n, Map<Object, Node> definitions) {
    Object anchor = n.anchor;
    if (anchor != null && tokenNames.containsKey(anchor)) {
      coreAlias(anchor);
      return;
    }
    Event v = Objects.requireNonNull(n.value);
    if (v.op == Event.ALIAS) {
      Object token = Objects.requireNonNull(v.token);
      Node target = definitions.get(token);
      if (target != null && !tokenNames.containsKey(token)) replay(target, definitions);
      else coreAlias(token);
      return;
    }
    apply(n.props, false);
    if (v.op == Event.MAP || v.op == Event.SEQ) {
      coreBegin(n.map);
      List<Node> children = n.children;
      if (n.map) {
        children = new ArrayList<>(children);
        children.sort(BY_KEY);
      }
      for (Node child : children) {
        Event key = child.key;
        if (key != null) {
          apply(child.keyProps, true);
          coreKey(Objects.requireNonNull(key.text), key.kind);
        }
        replay(child, definitions);
      }
      coreEnd();
    } else {
      coreScalar(v.op, v.bits, v.text);
    }
  }
}
