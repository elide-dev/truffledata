package dev.elide.truffledata.yaml;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.tape.TapeSink;
import dev.elide.truffledata.tape.TapeText;
import dev.elide.truffledata.tape.TapeValue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The YAML writer: formatting and quoting decisions, and exact round trips through the native
 * decoder (emit, re-parse, compare exported events) for every schema and option combination.
 */
final class YamlEmitterTest {
  // ---- native sessions --------------------------------------------------------------------

  private static final Map<YamlSchema, YamlSession> SESSIONS = new EnumMap<>(YamlSchema.class);

  static YamlOptions parseOptions(YamlSchema schema) {
    return switch (schema) {
      case CORE -> YamlOptions.DEFAULT;
      case JSON -> YamlOptions.json();
      case FAILSAFE -> YamlOptions.DEFAULT.withSchema(YamlSchema.FAILSAFE);
      case YAML_1_1 -> YamlOptions.yaml11();
    };
  }

  @BeforeAll
  static void open() {
    for (YamlSchema schema : YamlSchema.values())
      SESSIONS.put(schema, YamlJvm.openSession(ScalarResolverTest.library(), parseOptions(schema)));
  }

  @AfterAll
  static void close() {
    for (YamlSession session : SESSIONS.values()) session.close();
    SESSIONS.clear();
  }

  /** Exported events, one string each; NaN payloads are not part of the contract. */
  static final class Events implements TapeSink {
    final List<String> out = new ArrayList<>();
    final boolean metadata;

    Events(boolean metadata) {
      this.metadata = metadata;
    }

    @Override public void beginObject(long n) { out.add("{" + n); }
    @Override public void beginArray(long n) { out.add("[" + n); }
    @Override public void end() { out.add("end"); }
    @Override public void key(TapeText key) { out.add("key:" + key.toJavaString()); }
    @Override public void nullValue() { out.add("null"); }
    @Override public void booleanValue(boolean value) { out.add("bool:" + value); }
    @Override public void longValue(long value) { out.add("i64:" + value); }
    @Override public void unsignedValue(long value) { out.add("u64:" + Long.toUnsignedString(value)); }
    @Override public void doubleValue(double value) { out.add(f64(value)); }
    @Override public void decimalValue(TapeText value) { out.add("decimal:" + value.toJavaString()); }
    @Override public void stringValue(TapeText value) { out.add("string:" + value.toJavaString()); }

    @Override
    public void tag(TapeText uri) {
      if (metadata) out.add("tag:" + uri.toJavaString());
    }

    @Override
    public void anchor(int id) {
      if (metadata) out.add("&" + id);
    }
  }

  static String f64(double value) {
    return Double.isNaN(value) ? "f64:nan" : "f64:" + Long.toHexString(Double.doubleToRawLongBits(value));
  }

  /** Native events of every document in {@code yaml}; fails with the text on a parse error. */
  static List<String> reparse(YamlSchema schema, String yaml, boolean metadata) {
    Events events = new Events(metadata);
    try (YamlStream stream = SESSIONS.get(schema).documents(yaml.getBytes(StandardCharsets.UTF_8))) {
      while (true) {
        int before = events.out.size();
        events.out.add("doc");
        if (!stream.next(events)) {
          events.out.remove(before);
          break;
        }
      }
    } catch (RuntimeException failure) {
      throw new AssertionError("Native " + schema + " rejected emitted YAML (" + failure.getMessage()
          + "):\n" + yaml, failure);
    }
    return events.out;
  }

  // ---- a tree model the generator and the writer share -------------------------------------

  sealed interface N permits S, Sq, Mp, Anc, Ali {}

  static final int NULL = 0, BOOL = 1, I64 = 2, U64 = 3, F64 = 4, DEC = 5, STR = 6;

  record S(int op, long bits, @Nullable String text) implements N {}

  record Sq(List<N> items) implements N {}

  record Mp(List<String> keys, List<N> values) implements N {}

  record Anc(String name, N node) implements N {}

  record Ali(String name, N target) implements N {}

  static S str(String s) {
    return new S(STR, 0, s);
  }

  static void feed(N n, YamlWriter w) {
    switch (n) {
      case S s -> {
        switch (s.op()) {
          case NULL -> w.nullValue();
          case BOOL -> w.booleanValue(s.bits() != 0);
          case I64 -> w.longValue(s.bits());
          case U64 -> w.unsignedValue(s.bits());
          case F64 -> w.doubleValue(Double.longBitsToDouble(s.bits()));
          case DEC -> w.decimalValue(java.util.Objects.requireNonNull(s.text()));
          default -> w.value(java.util.Objects.requireNonNull(s.text()));
        }
      }
      case Sq q -> {
        w.beginArray(q.items().size());
        for (N item : q.items()) feed(item, w);
        w.end();
      }
      case Mp m -> {
        w.beginObject(m.keys().size());
        for (int i = 0; i < m.keys().size(); i++) {
          w.key(m.keys().get(i));
          feed(m.values().get(i), w);
        }
        w.end();
      }
      case Anc a -> {
        w.anchor(a.name());
        feed(a.node(), w);
      }
      case Ali a -> w.alias(a.name());
    }
  }

  /** Expected export events (aliases expanded) of {@code n}. */
  static void expected(N n, boolean sort, List<String> out) {
    switch (n) {
      case S s -> out.add(switch (s.op()) {
        case NULL -> "null";
        case BOOL -> "bool:" + (s.bits() != 0);
        case I64 -> "i64:" + s.bits();
        case U64 -> "u64:" + Long.toUnsignedString(s.bits());
        case F64 -> f64(Double.longBitsToDouble(s.bits()));
        case DEC -> "decimal:" + s.text();
        default -> "string:" + s.text();
      });
      case Sq q -> {
        out.add("[" + q.items().size());
        for (N item : q.items()) expected(item, sort, out);
        out.add("end");
      }
      case Mp m -> {
        out.add("{" + m.keys().size());
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < m.keys().size(); i++) order.add(i);
        if (sort) order.sort(Comparator.comparing(m.keys()::get));
        for (int i : order) {
          out.add("key:" + m.keys().get(i));
          expected(m.values().get(i), sort, out);
        }
        out.add("end");
      }
      case Anc a -> expected(a.node(), sort, out);
      case Ali a -> expected(a.target(), sort, out);
    }
  }

  static String emit(YamlEmitOptions options, N... documents) {
    StringBuilder out = new StringBuilder();
    try (YamlWriter w = new YamlWriter(out, options)) {
      for (N document : documents) feed(document, w);
    }
    return out.toString();
  }

  static void roundTrip(YamlEmitOptions options, N document) {
    String yaml = emit(options, document);
    List<String> want = new ArrayList<>();
    want.add("doc");
    expected(document, options.sortKeys(), want);
    List<String> got = reparse(options.schema(), yaml, false);
    if (!want.equals(got)) fail("Round trip mismatch under " + options + "\n" + diff(yaml, want, got));
  }

  /** {@code events} with every mapping's members in key order, as sortKeys writes them. */
  static List<String> sortEvents(List<String> events) {
    List<String> out = new ArrayList<>();
    int[] at = {0};
    while (at[0] < events.size()) {
      if (events.get(at[0]).equals("doc")) out.add(events.get(at[0]++));
      else sortedNode(events, at, out);
    }
    return out;
  }

  private static void sortedNode(List<String> events, int[] at, List<String> out) {
    String e = events.get(at[0]++);
    out.add(e);
    if (e.startsWith("{")) {
      List<Map.Entry<String, List<String>>> members = new ArrayList<>();
      while (!events.get(at[0]).equals("end")) {
        String key = events.get(at[0]++);
        List<String> value = new ArrayList<>();
        sortedNode(events, at, value);
        members.add(Map.entry(key, value));
      }
      at[0]++;
      members.sort(Comparator.comparing(m -> m.getKey().substring(4)));
      for (Map.Entry<String, List<String>> m : members) {
        out.add(m.getKey());
        out.addAll(m.getValue());
      }
      out.add("end");
    } else if (e.startsWith("[")) {
      while (!events.get(at[0]).equals("end")) sortedNode(events, at, out);
      at[0]++;
      out.add("end");
    }
  }

  static String clip(List<String> events, int from) {
    StringBuilder b = new StringBuilder();
    for (int i = Math.max(0, from - 3); i < Math.min(events.size(), from + 4); i++) {
      String e = events.get(i);
      b.append(i == from ? " >>" : " ").append(e.length() > 120 ? e.substring(0, 120) + "..." : e);
    }
    return b.toString().replace("\n", "\\n");
  }

  static String diff(String yaml, List<String> want, List<String> got) {
    String dump = System.getProperty("yaml.emit.dump");
    if (dump != null) {
      try {
        Files.writeString(Path.of(dump), yaml);
      } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    }
    int i = 0;
    while (i < want.size() && i < got.size() && want.get(i).equals(got.get(i))) i++;
    return "--- emitted:\n" + (yaml.length() > 4000 ? yaml.substring(0, 4000) + "..." : yaml)
        + "\n--- want:" + clip(want, i) + "\n--- got: " + clip(got, i);
  }

  // ---- hazards ----------------------------------------------------------------------------

  static List<String> hazards() {
    Set<String> out = new LinkedHashSet<>(ScalarResolverTest.corpus().stream().limit(400).toList());
    String indicators = "-?:,[]{}#&*!|>'\"%@`";
    for (char c : indicators.toCharArray()) {
      out.add(String.valueOf(c));
      out.add(c + " ");
      out.add(c + " x");
      out.add(c + "x");
      out.add("x" + c);
      out.add("x" + c + "y");
      out.add("x " + c);
      out.add("x " + c + " y");
    }
    for (String s :
        new String[] {
          "a: b", "a:", "a :b", "a #b", "a#b", " a", "a ", "\ta", "a\t", "a\tb", "a  b", "a \tb",
          "---", "...", "--- a", "... a", "---a", "...a", "-- -", "a\nb", "a\n", "a\n\n", "\n",
          "\n\n", "\na", "\n\na\n", " a\nb", "  \nfoo", "\tx\ny", "a\r\nb", "\r", "a\rb", "\u0085",
          "a\u0085b", "\u2028", "\u2029", "\uFEFF", "\uFEFFa", "a\uFEFF", "\u00A0", "\u00A0a\u00A0",
          "\u007F", "\u0080", "\u009F", "\uFFFE", "\uFFFF", "\uFFFD", "\uD83D\uDE00",
          "a\uD83D\uDE00b", "\uD800\uDC00", "\uDBFF\uDFFF", "é", "中文", "<<", "=", "~", "null", "Null",
          "true", "yes", "No", "on", "OFF", "y", "n", "2024-01-01", "2001-12-14t21:59:43.10-05:00",
          "1:20", "190:20:30", "0o7", "0x1F", "0b101", "1_000", ".inf", "-.inf", ".nan", "+1", "1e3",
          "1.", ".5", "-0", "it's", "say \"hi\"", "a\\b", "\\", "%TAG", "%YAML 1.2", "@x", "`x",
          "a,b", "[a]", "{a}", "a]", "a}", "x: y: z", "? x", "- x", "-x", "?x", ":x", "a:b",
          "http://x.y/z?q=1#f", "#", "a #", "&a", "*a", "!a", "!!str", "|", ">", "|-", ">+", "'",
          "\"", "a'b", "a\"b", "a\n b", "a\n\tb", "trailing \n", "x\n  indented\nback",
          "lines\n\n\nwith gaps\n", "a\n \n", "a\n ", " \n", "  lead\n  both", "#c\n#d",
          "- item\n- item", "key: value\nk2: v2", "---\nx", "x\n---\ny", "x\n...\ny",
          "\u0000", "\u0007", "\u0008", "\u000B", "\u000C", "\u001B", "\u001F", "a\u0000b",
          "x".repeat(2000), "word ".repeat(300).trim(), "line\n".repeat(50), "\t", "\t\n\t"
        }) out.add(s);
    for (int c = 0; c < 0x20; c++) out.add("c" + (char) c + "c");
    return new ArrayList<>(out);
  }

  static List<YamlEmitOptions> optionCombos(YamlSchema schema) {
    YamlEmitOptions base = YamlEmitOptions.DEFAULT.withSchema(schema);
    return List.of(
        base,
        base.flow(),
        base.withFlowDepth(2).withQuote(Style.SINGLE),
        base.withIndentSequences(false).withSortKeys(true),
        base.withQuote(Style.SINGLE).withBlockScalars(false),
        base.withIndent(4).withExplicitStart(true).withExplicitEnd(true).withTildeNull(true),
        base.withIndent(1).withIndentSequences(false),
        base.withFlowDepth(1).withSortKeys(true).withIndent(3));
  }

  /** Every hazard in every position: root, sequence entry, map value, key, flow, nested. */
  static N hazardDocument(String s) {
    String longKey = "k".repeat(1100) + s;
    return new Mp(
        List.of(s, "v", "seq", "nested", longKey, "flowish"),
        List.of(
            str(s),
            new Sq(List.of(str(s), new Sq(List.of(str(s), str(s))), new Mp(List.of(s), List.of(str(s))))),
            new Sq(List.of(str(s))),
            new Mp(List.of("a", s + "2"), List.of(new Mp(List.of("b"), List.of(str(s))), str(s))),
            str(s),
            new Sq(List.of(new Mp(List.of(s, s + "\u00e9"), List.of(new Sq(List.of(str(s))), str(s)))))));
  }

  @Test
  void hazardCorpusRoundTripsUnderEverySchemaAndStyle() {
    List<String> hazards = hazards();
    int checked = 0;
    for (YamlSchema schema : YamlSchema.values()) {
      for (YamlEmitOptions options : optionCombos(schema)) {
        for (String s : hazards) {
          if (Set.of("v", "seq", "nested", "flowish").contains(s)) continue;
          roundTrip(options, str(s));
          roundTrip(options, hazardDocument(s));
          checked += 2;
        }
      }
    }
    // Tagged strings: under !!str and local tags plain needs only syntactic safety.
    for (YamlSchema schema : YamlSchema.values()) {
      for (String s : hazards) {
        StringBuilder out = new StringBuilder();
        try (YamlWriter w = new YamlWriter(out, YamlEmitOptions.DEFAULT.withSchema(schema))) {
          w.beginSequence();
          w.tag("tag:yaml.org,2002:str");
          w.value(s);
          if (schema != YamlSchema.JSON) {
            w.tag("!local");
            w.value(s);
          }
          w.end();
        }
        List<String> want = new ArrayList<>(List.of("doc", schema == YamlSchema.JSON ? "[1" : "[2",
            "tag:tag:yaml.org,2002:str", "string:" + s));
        if (schema != YamlSchema.JSON) want.addAll(List.of("tag:!local", "string:" + s));
        want.add("end");
        List<String> got = reparse(schema, out.toString(), true);
        if (!want.equals(got)) fail("tagged " + schema + "\n" + diff(out.toString(), want, got));
        checked++;
      }
    }
    assertTrue(checked > 20_000, "checked " + checked);
    System.out.println("YAML emitter hazard round trips: " + checked + " (" + hazards.size() + " hazards)");
  }

  // ---- random documents --------------------------------------------------------------------

  static final class Gen {
    final Random random;
    final YamlSchema schema;
    final boolean anchors;
    final List<String> hazards;
    final List<Anc> defined = new ArrayList<>();

    Gen(long seed, YamlSchema schema, boolean anchors, List<String> hazards) {
      this.random = new Random(seed);
      this.schema = schema;
      this.anchors = anchors;
      this.hazards = hazards;
    }

    String text() {
      return switch (random.nextInt(6)) {
        case 0, 1 -> hazards.get(random.nextInt(hazards.size()));
        case 2 -> {
          StringBuilder b = new StringBuilder();
          String alphabet = "ab -:#,[]{}'\"\\\n\t?!&*|>%@`.~0123456789eExo_+";
          int n = random.nextInt(12);
          for (int i = 0; i < n; i++) b.append(alphabet.charAt(random.nextInt(alphabet.length())));
          yield b.toString();
        }
        case 3 -> {
          StringBuilder b = new StringBuilder();
          int n = random.nextInt(8);
          for (int i = 0; i < n; i++) {
            int cp;
            do {
              cp = random.nextInt(4) == 0 ? 0x10000 + random.nextInt(0x100000) : random.nextInt(0x10000);
            } while (cp >= 0xD800 && cp <= 0xDFFF);
            b.appendCodePoint(cp);
          }
          yield b.toString();
        }
        case 4 -> {
          StringBuilder b = new StringBuilder();
          int lines = 1 + random.nextInt(4);
          for (int i = 0; i < lines; i++) {
            if (random.nextBoolean()) b.append(" ".repeat(random.nextInt(3)));
            b.append("line").append(i);
            b.append("\n".repeat(random.nextInt(3)));
          }
          yield b.toString();
        }
        default -> "w" + random.nextInt(1000);
      };
    }

    S scalar() {
      switch (random.nextInt(9)) {
        case 0:
          return new S(NULL, 0, null);
        case 1:
          return new S(BOOL, random.nextInt(2), null);
        case 2: {
          long[] picks = {0, 1, -1, Long.MAX_VALUE, Long.MIN_VALUE, random.nextLong(), random.nextInt()};
          return new S(I64, picks[random.nextInt(picks.length)], null);
        }
        case 3:
          return new S(U64, random.nextLong() | Long.MIN_VALUE, null);
        case 4: {
          double[] picks = {
            0.0, -0.0, 1.0, -1.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, 1e15, 1e16, 1e-4, 1e-5, 0.1,
            123456789.123456789, 1e21, 2.5e-300
          };
          double d = random.nextBoolean() ? picks[random.nextInt(picks.length)]
              : Double.longBitsToDouble(random.nextLong());
          return new S(F64, Double.doubleToRawLongBits(d), null);
        }
        case 5: {
          String digits = "1" + Long.toUnsignedString(random.nextLong() | Long.MIN_VALUE) + random.nextInt(1000);
          String token = switch (random.nextInt(4)) {
            case 0 -> digits;
            case 1 -> "-" + digits;
            case 2 -> "2.5e+" + (400 + random.nextInt(600));
            default -> schema == YamlSchema.YAML_1_1 ? "-1.0e+999" : (random.nextBoolean() ? "1e400" : "-7e+1000");
          };
          return new S(DEC, 0, token);
        }
        default:
          return str(text());
      }
    }

    N node(int depth) {
      if (anchors && !defined.isEmpty() && random.nextInt(8) == 0) {
        Anc target = defined.get(random.nextInt(defined.size()));
        return new Ali(target.name(), target.node());
      }
      // The anchor is announced before its subtree: an alias inside it to the same name would
      // be recursive, and earlier definitions of that name are shadowed.
      String anchor = anchors && random.nextInt(5) == 0
          ? new String[] {"a", "b", "node", "x_1", "\u00e9t\u00e9"}[random.nextInt(5)] : null;
      if (anchor != null) {
        String name = anchor;
        defined.removeIf(d -> d.name().equals(name));
      }
      N n;
      int pick = depth >= 5 ? 0 : random.nextInt(5);
      if (pick <= 1) {
        n = scalar();
      } else if (pick == 2) {
        int size = random.nextInt(5);
        List<N> items = new ArrayList<>();
        for (int i = 0; i < size; i++) items.add(node(depth + 1));
        n = new Sq(items);
      } else {
        int size = random.nextInt(5);
        Set<String> keys = new LinkedHashSet<>();
        while (keys.size() < size) keys.add(random.nextInt(10) == 0 ? "k".repeat(1030) + keys.size() : text());
        List<N> values = new ArrayList<>();
        for (int i = 0; i < size; i++) values.add(node(depth + 1));
        n = new Mp(new ArrayList<>(keys), values);
      }
      if (anchor != null) {
        // Reused names exercise redefinition. An alias refers to the definition that appears
        // last in the text: a redefinition nested inside this subtree comes after this one.
        Anc a = new Anc(anchor, n);
        if (defined.stream().noneMatch(d -> d.name().equals(anchor))) defined.add(a);
        return a;
      }
      return n;
    }
  }

  @Test
  void randomDocumentsRoundTripUnderEverySchemaAndOptionSet() {
    List<String> hazards = hazards();
    int checked = 0;
    for (YamlSchema schema : YamlSchema.values()) {
      List<YamlEmitOptions> combos = optionCombos(schema);
      for (int i = 0; i < 2400; i++) {
        YamlEmitOptions options = combos.get(i % combos.size());
        boolean anchors = schema != YamlSchema.JSON && i % 3 == 0;
        N document = new Gen(0x59414d4c45L + i, schema, anchors, hazards).node(0);
        roundTrip(options, document);
        checked++;
      }
    }
    assertEquals(9600, checked);
  }

  @Test
  void multipleDocumentsRoundTrip() {
    for (YamlSchema schema : YamlSchema.values()) {
      for (YamlEmitOptions options : optionCombos(schema)) {
        N[] docs = new N[12];
        for (int i = 0; i < docs.length; i++)
          docs[i] = new Gen(i * 31L, schema, false, hazards()).node(i % 3 == 0 ? 5 : 0);
        String yaml = emit(options, docs);
        List<String> want = new ArrayList<>();
        for (N doc : docs) {
          want.add("doc");
          expected(doc, options.sortKeys(), want);
        }
        assertEquals(want, reparse(schema, yaml, false), yaml);
      }
    }
  }

  // ---- tape values: suite and fixture re-emission ------------------------------------------

  private static Path suite() {
    String override = System.getProperty("yaml.suite");
    return override != null ? Path.of(override)
        : Path.of(System.getenv("TEST_SRCDIR"), "+http_archive+yaml_test_suite");
  }

  private static Path workspace(String path) {
    String root = System.getenv("TEST_SRCDIR");
    return root == null ? Path.of(path) : Path.of(root, System.getenv("TEST_WORKSPACE"), path);
  }

  /** Decoded events of {@code source}, or null when the decoder rejects it. */
  private static @Nullable List<String> decodeEvents(YamlSchema schema, String source, boolean metadata) {
    Events events = new Events(metadata);
    try (YamlStream stream = SESSIONS.get(schema).documents(source.getBytes(StandardCharsets.UTF_8))) {
      while (true) {
        int before = events.out.size();
        events.out.add("doc");
        if (!stream.next(events)) {
          events.out.remove(before);
          break;
        }
      }
      return events.out;
    } catch (RuntimeException rejected) {
      return null;
    }
  }

  /** Re-emits every document of {@code source} through {@link YamlEmitter#write}. */
  private static String reemit(YamlSchema schema, String source, YamlEmitOptions options) {
    StringBuilder out = new StringBuilder();
    try (YamlStream stream = SESSIONS.get(schema).documents(source.getBytes(StandardCharsets.UTF_8));
        YamlWriter writer = new YamlWriter(out, options)) {
      for (TapeValue value; (value = stream.next()) != null; ) YamlEmitter.write(value, writer);
    }
    return out.toString();
  }

  /** Metadata a preserving re-emission must keep, per node in source order. */
  private static void metadata(TapeValue v, List<String> out) {
    boolean alias = Yaml.isAlias(v);
    if (alias) {
      out.add("*" + v.targetAnchorName());
      return;
    }
    out.add("&" + Yaml.anchor(v) + " " + Yaml.tag(v) + " " + style(Yaml.style(v)));
    switch (v.kind()) {
      case OBJECT -> {
        long i = 0;
        for (Map.Entry<TapeValue, TapeValue> member : v.members()) {
          TapeValue key = member.getKey();
          out.add("key " + key.stringValue() + " " + Yaml.keyKind(v, i++) + " &" + Yaml.anchor(key)
              + " " + Yaml.tag(key));
          metadata(member.getValue(), out);
        }
      }
      case ARRAY -> {
        for (TapeValue element : v.elements()) metadata(element, out);
      }
      default -> {}
    }
  }

  /**
   * Collection styles are kept; scalar styles are hints that change when the source spelling is
   * not one the writer produces (folded, multi-line quoted, plain forms it quotes).
   */
  private static String style(Style style) {
    return style == Style.BLOCK || style == Style.FLOW ? style.name() : "scalar";
  }

  private static @Nullable List<String> metadataOf(YamlSchema schema, String source) {
    List<String> out = new ArrayList<>();
    try (YamlStream stream = SESSIONS.get(schema).documents(source.getBytes(StandardCharsets.UTF_8))) {
      for (TapeValue value; (value = stream.next()) != null; ) {
        out.add("doc");
        metadata(value, out);
      }
      return out;
    } catch (RuntimeException rejected) {
      return null;
    }
  }

  /** Returns true when {@code source} was decodable and re-emitted exactly. */
  private static boolean reemitChecks(String name, String source, List<String> failures) {
    List<String> original = decodeEvents(YamlSchema.CORE, source, true);
    if (original == null) return false;
    List<String> plainEvents = decodeEvents(YamlSchema.CORE, source, false);
    for (YamlEmitOptions options :
        List.of(
            YamlEmitOptions.DEFAULT,
            YamlEmitOptions.DEFAULT.flow(),
            YamlEmitOptions.DEFAULT.withIndentSequences(false).withQuote(Style.SINGLE).withIndent(3),
            YamlEmitOptions.DEFAULT.withSortKeys(true).withFlowDepth(2))) {
      try {
        String yaml = reemit(YamlSchema.CORE, source, options);
        List<String> got = reparse(YamlSchema.CORE, yaml, false);
        List<String> want = java.util.Objects.requireNonNull(plainEvents);
        if (options.sortKeys()) want = sortEvents(want);
        if (!got.equals(want))
          failures.add(name + " " + options + "\n" + diff(yaml, want, got));
      } catch (RuntimeException | AssertionError e) {
        failures.add(name + " " + options + ": " + e);
      }
    }
    // Preserving: tags, anchors, aliases, styles and key kinds survive (aliases kept as aliases).
    for (YamlEmitOptions options :
        List.of(
            YamlEmitOptions.DEFAULT.withPreserveMetadata(true),
            YamlEmitOptions.DEFAULT.withPreserveMetadata(true).withQuote(Style.SINGLE).withIndent(4))) {
      try {
        String yaml = reemit(YamlSchema.CORE, source, options);
        List<String> got = reparse(YamlSchema.CORE, yaml, true);
        if (!got.equals(original))
          failures.add(name + " preserving\n" + diff(yaml, original, got));
        List<String> wantMeta = metadataOf(YamlSchema.CORE, source);
        List<String> gotMeta = metadataOf(YamlSchema.CORE, yaml);
        if (!java.util.Objects.equals(wantMeta, gotMeta))
          failures.add(name + " metadata\n" + diff(yaml, java.util.Objects.requireNonNull(wantMeta),
              gotMeta == null ? List.of("rejected") : gotMeta));
      } catch (RuntimeException | AssertionError e) {
        failures.add(name + " preserving: " + e);
      }
    }
    return true;
  }

  @Test
  void reemitsFixturesAndThePinnedSuite() throws Exception {
    List<String> failures = new ArrayList<>();
    int fixtures = 0;
    try (var files = Files.list(workspace("yaml/native/testdata"))) {
      for (Path input : files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList()) {
        assertTrue(reemitChecks(input.toString(), Files.readString(input), failures), input.toString());
        fixtures++;
      }
    }
    assertEquals(5, fixtures);
    Path root = suite().toRealPath();
    int decoded = 0;
    try (var files = Files.walk(root)) {
      for (Path input : files.filter(p -> p.getFileName().toString().equals("in.yaml")).sorted().toList()) {
        String name = root.relativize(input.getParent()).toString();
        if (name.startsWith("name/") || name.startsWith("tags/")) continue;
        if (reemitChecks(name, Files.readString(input), failures)) decoded++;
      }
    }
    assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n\n", failures));
    assertTrue(decoded > 250, "decoded suite cases: " + decoded);
    System.out.println("YAML emitter re-emitted " + fixtures + " fixtures and " + decoded + " suite cases");
  }

  @Test
  void tapeValuesAndSnapshotsEmit() {
    String source = "a: &x {b: [1, 2.5, -0.0, .nan, 18446744073709551615, 123456789012345678901234567890]}\n"
        + "c: *x\n'1': !!binary SGVsbG8=\n2: \"two\"\nnull: ~\n";
    YamlSession session = SESSIONS.get(YamlSchema.CORE);
    try (YamlMessage message = session.decode(source.getBytes(StandardCharsets.UTF_8))) {
      TapeValue root = message.root();
      String plain = YamlEmitter.emit(root);
      assertEquals(decodeEvents(YamlSchema.CORE, source, false), reparse(YamlSchema.CORE, plain, false));
      String kept = YamlEmitter.emit(root, YamlEmitOptions.DEFAULT.withPreserveMetadata(true));
      assertEquals(
          "a: &x {b: [1, 2.5, -0.0, .nan, 18446744073709551615, 123456789012345678901234567890]}\n"
              + "c: *x\n'1': !!binary SGVsbG8=\n2: \"two\"\nnull: null\n",
          kept);
      // A subtree whose alias target lies outside it expands the target, which takes the anchor.
      String sub = YamlEmitter.emit(root.member("c"), YamlEmitOptions.DEFAULT.withPreserveMetadata(true));
      assertEquals("--- &x\nb: [1, 2.5, -0.0, .nan, 18446744073709551615, 123456789012345678901234567890]\n",
          sub);
      var snapshot = message.snapshot();
      assertEquals(kept, YamlEmitter.emit(snapshot.root(), YamlEmitOptions.DEFAULT.withPreserveMetadata(true)));
    }
  }

  /** JSON tapes export through the same sink: their events read back from YAML exactly. */
  @Test
  void jsonDocumentsEmit() {
    try (dev.elide.truffledata.json.JsonDecoder decoder =
        dev.elide.truffledata.json.JsonJvm.open(workspace("json/native/json_shared.so"))) {
      for (int i = 0; i < 300; i++) {
        byte[] input = dev.elide.truffledata.comparison.JsonFixture.document(i);
        try (dev.elide.truffledata.json.JsonMessage message = decoder.decode(input)) {
          Events events = new Events(false);
          events.out.add("doc");
          message.export(events);
          for (YamlSchema schema : new YamlSchema[] {YamlSchema.CORE, YamlSchema.JSON, YamlSchema.FAILSAFE}) {
            YamlEmitOptions options = optionCombos(schema).get(i % 8);
            String yaml = YamlEmitter.emit(message.root(), options);
            List<String> want = options.sortKeys() ? sortEvents(events.out) : events.out;
            List<String> got = reparse(schema, yaml, false);
            if (!want.equals(got)) fail("JSON fixture " + i + " " + options + "\n" + diff(yaml, want, got));
          }
        }
      }
    }
  }

  /** The sink path alone (value.export(writer)) keeps tags and aliases, with generated names. */
  @Test
  void sinkExportPreservesTagsAndAliases() {
    String source = "base: &b {x: 1, y: !!str 2}\nuse: [*b, *b]\nbin: !!binary SGVsbG8=\n";
    try (YamlMessage message = SESSIONS.get(YamlSchema.CORE).decode(source.getBytes(StandardCharsets.UTF_8))) {
      StringBuilder out = new StringBuilder();
      try (YamlWriter writer = new YamlWriter(out, YamlEmitOptions.DEFAULT.withPreserveMetadata(true))) {
        message.root().export(writer);
      }
      assertEquals("base: &a0\n  x: 1\n  y: !!str 2\nuse:\n  - *a0\n  - *a0\nbin: !!binary SGVsbG8=\n",
          out.toString());
      assertEquals(decodeEvents(YamlSchema.CORE, source, true), reparse(YamlSchema.CORE, out.toString(), true));
      // Without preservation the same export drops tags and expands aliases.
      StringBuilder plain = new StringBuilder();
      try (YamlWriter writer = new YamlWriter(plain)) {
        message.root().export(writer);
      }
      assertEquals("base:\n  x: 1\n  y: \"2\"\nuse:\n  - x: 1\n    y: \"2\"\n  - x: 1\n    y: \"2\"\n"
          + "bin: SGVsbG8=\n", plain.toString());
    }
  }

  /** Explicit documents, typed value overloads, UTF-8 output and the metadata-free forwarder. */
  @Test
  void documentsValuesAndForwarding() {
    StringBuilder out = new StringBuilder();
    try (YamlWriter w = new YamlWriter(out, YamlEmitOptions.DEFAULT.withExplicitEnd(true))) {
      w.beginDocument();
      w.beginSequence();
      w.value(1L);
      w.value(2.5);
      w.value(true);
      w.value("\u00e9");
      w.end();
      w.endDocument();
      w.beginDocument();
      w.value(-0.0);
      w.beginDocument();
      w.value(false);
    }
    String yaml = out.toString();
    assertEquals("---\n- 1\n- 2.5\n- true\n- \u00e9\n...\n--- -0.0\n...\n--- false\n...\n", yaml);
    assertEquals(List.of("doc", "[4", "i64:1", f64(2.5), "bool:true", "string:\u00e9", "end", "doc",
        f64(-0.0), "doc", "bool:false"), reparse(YamlSchema.CORE, yaml, false));
    String source = "a: &x [1, {b: null, c: true, d: \"s\"}]\ne: *x\nf: !!str 7\n";
    try (YamlMessage message = SESSIONS.get(YamlSchema.CORE).decode(source.getBytes(StandardCharsets.UTF_8))) {
      byte[] utf8 = YamlEmitter.emitUtf8(message.root(), YamlEmitOptions.DEFAULT.withPreserveMetadata(true));
      assertEquals(YamlEmitter.emit(message.root(), YamlEmitOptions.DEFAULT.withPreserveMetadata(true)),
          new String(utf8, StandardCharsets.UTF_8));
      // The forwarder drops tags and anchors even when the writer would keep them.
      StringBuilder forwarded = new StringBuilder();
      try (YamlWriter w = new YamlWriter(forwarded, YamlEmitOptions.DEFAULT.withPreserveMetadata(true))) {
        message.root().export(new YamlEmitter.WithoutMetadata(w));
      }
      assertEquals(YamlEmitter.emit(message.root()), forwarded.toString());
      assertEquals(decodeEvents(YamlSchema.CORE, source, false),
          reparse(YamlSchema.CORE, forwarded.toString(), false));
    }
  }

  @Test
  void optionsAreValues() {
    YamlEmitOptions flow = YamlEmitOptions.DEFAULT.flow();
    assertEquals(YamlEmitOptions.DEFAULT, flow.block());
    assertEquals(YamlEmitOptions.DEFAULT.hashCode(), flow.block().hashCode());
    assertNotEquals(YamlEmitOptions.DEFAULT, flow);
    assertTrue(flow.toString().contains("flowDepth=0"), flow.toString());
    // Percent escapes already in a tag URI are kept; other characters are escaped.
    assertEquals("!<tag:example.com,2000:a%20b%25>", YamlScalars.tag("tag:example.com,2000:a%20b%"));
    assertEquals("!<tag:example.com,2000:%25zz>", YamlScalars.tag("tag:example.com,2000:%zz"));
  }

  // ---- formatting -----------------------------------------------------------------------------

  static Mp map(Object... kv) {
    List<String> keys = new ArrayList<>();
    List<N> values = new ArrayList<>();
    for (int i = 0; i < kv.length; i += 2) {
      keys.add((String) kv[i]);
      values.add(kv[i + 1] instanceof N n ? n : str((String) kv[i + 1]));
    }
    return new Mp(keys, values);
  }

  static Sq seq(Object... items) {
    List<N> out = new ArrayList<>();
    for (Object item : items) out.add(item instanceof N n ? n : str((String) item));
    return new Sq(out);
  }

  static S i64(long v) {
    return new S(I64, v, null);
  }

  @Test
  void blockLayout() {
    Mp doc = map("a", i64(1), "b", seq("x", "y"), "c", map("d", "true"), "e", seq(), "f", map());
    assertEquals("a: 1\nb:\n  - x\n  - y\nc:\n  d: \"true\"\ne: []\nf: {}\n",
        emit(YamlEmitOptions.DEFAULT, doc));
    assertEquals("a: 1\nb:\n- x\n- y\nc:\n  d: 'true'\ne: []\nf: {}\n",
        emit(YamlEmitOptions.DEFAULT.withIndentSequences(false).withQuote(Style.SINGLE), doc));
    assertEquals("{a: 1, b: [x, y], c: {d: \"true\"}, e: [], f: {}}\n",
        emit(YamlEmitOptions.DEFAULT.flow(), doc));
    assertEquals("a: 1\nb: [x, y]\nc: {d: \"true\"}\ne: []\nf: {}\n",
        emit(YamlEmitOptions.DEFAULT.withFlowDepth(1), doc));
    assertEquals("a: 1\nb:\n    - x\n    - y\nc:\n    d: \"true\"\ne: []\nf: {}\n",
        emit(YamlEmitOptions.DEFAULT.withIndent(4), doc));
    assertEquals("- a: 1\n  b: 2\n- - 1\n  - - 2\n    - 3\n- s\n- {}\n",
        emit(YamlEmitOptions.DEFAULT,
            seq(map("a", i64(1), "b", i64(2)), seq(i64(1), seq(i64(2), i64(3))), "s", map())));
    assertEquals("{}\n", emit(YamlEmitOptions.DEFAULT, map()));
    assertEquals("[]\n", emit(YamlEmitOptions.DEFAULT, seq()));
    assertEquals("x\n", emit(YamlEmitOptions.DEFAULT, str("x")));
    assertEquals("--- x\n...\n", emit(YamlEmitOptions.DEFAULT.withExplicitStart(true).withExplicitEnd(true), str("x")));
    assertEquals("---\na: b\n", emit(YamlEmitOptions.DEFAULT.withExplicitStart(true), map("a", "b")));
    assertEquals("a\n--- b\n---\nc: d\n", emit(YamlEmitOptions.DEFAULT, str("a"), str("b"), map("c", "d")));
  }

  @Test
  void literalBlockScalars() {
    assertEquals("s: |-\n  line1\n  line2\n", emit(YamlEmitOptions.DEFAULT, map("s", "line1\nline2")));
    assertEquals("s: |\n  a\n", emit(YamlEmitOptions.DEFAULT, map("s", "a\n")));
    assertEquals("s: |+\n  a\n\n", emit(YamlEmitOptions.DEFAULT, map("s", "a\n\n")));
    assertEquals("s: |+\n  a\n\nt: x\n", emit(YamlEmitOptions.DEFAULT, map("s", "a\n\n", "t", "x")));
    assertEquals("s: |2-\n   lead\n  x\n", emit(YamlEmitOptions.DEFAULT, map("s", " lead\nx")));
    assertEquals("s: |2\n\n  a\n", emit(YamlEmitOptions.DEFAULT, map("s", "\na\n")));
    assertEquals("- |-\n  a\n\n  b\n", emit(YamlEmitOptions.DEFAULT, seq("a\n\nb")));
    assertEquals("|-\n  a\n  b\n", emit(YamlEmitOptions.DEFAULT, str("a\nb")));
    // The root has no parent indentation to anchor an indicator to: double quotes instead.
    assertEquals("\" a\\nb\"\n", emit(YamlEmitOptions.DEFAULT, str(" a\nb")));
    assertEquals("s: \"a\\nb\"\n", emit(YamlEmitOptions.DEFAULT.withBlockScalars(false), map("s", "a\nb")));
    assertEquals("[\"a\\nb\"]\n", emit(YamlEmitOptions.DEFAULT.flow(), seq("a\nb")));
    assertEquals("s: \"a\\r\\nb\"\n", emit(YamlEmitOptions.DEFAULT, map("s", "a\r\nb")));
    assertEquals("\"\\n\"\n", emit(YamlEmitOptions.DEFAULT, str("\n")));
  }

  @Test
  void quotingDecisions() {
    // Resolution: strings that would read back as something else are quoted, per schema.
    for (String s : new String[] {"123", "true", "0o7", "", "null", ".inf", "~", "-0", "1e3", "0x1F"})
      assertFalse(YamlScalars.plain(s, YamlSchema.CORE, false), s);
    for (String s : new String[] {"yes", "No", "2024-01-01", "0b101", "1_000", "190:20:30", "y", "~"})
      assertFalse(YamlScalars.plain(s, YamlSchema.YAML_1_1, false), s);
    for (String s : new String[] {"yes", "2024-01-01", "y", "0b101", "1_000", "1:20"})
      assertTrue(YamlScalars.plain(s, YamlSchema.CORE, false), s);
    assertTrue(YamlScalars.plain("0o7", YamlSchema.YAML_1_1, false));
    assertTrue(YamlScalars.plain("1e3", YamlSchema.YAML_1_1, false));
    assertFalse(YamlScalars.plain("abc", YamlSchema.JSON, false));
    assertTrue(YamlScalars.plain("123", YamlSchema.FAILSAFE, false));
    // Syntax: indicators, separators, comments, markers, white space and control characters.
    for (String s : new String[] {"- x", "-", "? x", ": x", "a: b", "a:", "a #b", "#a", "&a", "*a",
        "!a", "|", ">", "'a", "\"a", "%a", "@a", "`a", "[a", "{a", "]", "}", ",a", " a", "a ", "a\tb",
        "a\nb", "---", "...", "--- x", "<<", "\u0085", "\u2028", "\uFEFFa", "a\u0007"})
      assertFalse(YamlScalars.plain(s, YamlSchema.CORE, false), s);
    for (String s : new String[] {"-x", "?x", ":x", "a:b", "a#b", "a - b", "a, b", "a]b", "it's",
        "say \"hi\"", "a\\b", "http://x/y", "\u00e9", "\uD83D\uDE00", "x!", "a&b", "---x"})
      assertTrue(YamlScalars.plain(s, YamlSchema.CORE, false), s);
    for (String s : new String[] {"a, b", "a]b", "a:b", "-x", "a{b", ":x"})
      assertFalse(YamlScalars.plain(s, YamlSchema.CORE, true), s);
    assertTrue(YamlScalars.plain("a b", YamlSchema.CORE, true));
    assertTrue(YamlScalars.plain("=", YamlSchema.CORE, false));
    assertFalse(YamlScalars.plain("=", YamlSchema.YAML_1_1, false));
    // Double-quoted escapes.
    assertEquals("\"\\0\\a\\b\\t\\n\\v\\f\\r\\e\\\"\\\\\\N\\L\\P\\x01\\x7F\\x9F\\uFEFF\\uFFFE\u00A0\uD83D\uDE00\"",
        YamlScalars.doubleQuoted("\0\u0007\b\t\n\u000B\f\r\u001B\"\\\u0085\u2028\u2029\u0001\u007F\u009F\uFEFF\uFFFE\u00A0\uD83D\uDE00"));
    assertEquals("'it''s'", YamlScalars.singleQuoted("it's"));
    assertFalse(YamlScalars.singleQuotable("a\nb"));
    assertFalse(YamlScalars.singleQuotable("a\u0001"));
    assertTrue(YamlScalars.singleQuotable("a\tb"));
    // Values under the writer.
    assertEquals("- \"123\"\n- \"\"\n- \"null\"\n- abc\n- yes\n",
        emit(YamlEmitOptions.DEFAULT, seq("123", "", "null", "abc", "yes")));
    assertEquals("- '0o7'\n- \"a\\nb\"\n- \"\\x01\"\n",
        emit(YamlEmitOptions.DEFAULT.withQuote(Style.SINGLE).withBlockScalars(false), seq("0o7", "a\nb", "\u0001")));
    assertEquals("- \"yes\"\n- \"2024-01-01\"\n- 0o7\n",
        emit(YamlEmitOptions.DEFAULT.withSchema(YamlSchema.YAML_1_1), seq("yes", "2024-01-01", "0o7")));
    assertEquals("- \"abc\"\n- \"1\"\n",
        emit(YamlEmitOptions.DEFAULT.withSchema(YamlSchema.JSON), seq("abc", "1")));
    assertThrows(IllegalArgumentException.class,
        () -> emit(YamlEmitOptions.DEFAULT, str("a\uD800")));
    assertThrows(IllegalArgumentException.class,
        () -> emit(YamlEmitOptions.DEFAULT, str("\uDC00")));
  }

  @Test
  void typedScalars() {
    N numbers = seq(i64(-5), new S(U64, -1L, null), new S(F64, Double.doubleToRawLongBits(-0.0), null),
        new S(F64, Double.doubleToRawLongBits(1e20), null), new S(F64, Double.doubleToRawLongBits(Double.NaN), null),
        new S(F64, Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY), null),
        new S(DEC, 0, "123456789012345678901234567890"), new S(NULL, 0, null), new S(BOOL, 1, null));
    assertEquals("- -5\n- 18446744073709551615\n- -0.0\n- 1e+20\n- .nan\n- -.inf\n"
        + "- 123456789012345678901234567890\n- null\n- true\n", emit(YamlEmitOptions.DEFAULT, numbers));
    assertEquals("- -5\n- 18446744073709551615\n- -0.0\n- 1.0e+20\n- .nan\n- -.inf\n"
        + "- 123456789012345678901234567890\n- ~\n- true\n",
        emit(YamlEmitOptions.DEFAULT.withSchema(YamlSchema.YAML_1_1).withTildeNull(true), numbers));
    assertEquals("- !!int -5\n- !!int 18446744073709551615\n- !!float -0.0\n- !!float 1e+20\n"
        + "- !!float .nan\n- !!float -.inf\n- !!int 123456789012345678901234567890\n- !!null null\n"
        + "- !!bool true\n", emit(YamlEmitOptions.DEFAULT.withSchema(YamlSchema.FAILSAFE), numbers));
    // JSON has no plain spelling for non-finite floats: they carry !!float.
    assertEquals("- -5\n- 18446744073709551615\n- -0.0\n- 1e+20\n- !!float .nan\n- !!float -.inf\n"
        + "- 123456789012345678901234567890\n- null\n- true\n",
        emit(YamlEmitOptions.DEFAULT.withSchema(YamlSchema.JSON).withTildeNull(true), numbers));
    for (YamlSchema schema : YamlSchema.values())
      roundTrip(YamlEmitOptions.DEFAULT.withSchema(schema), numbers);
    // A decimal float without '.' and a signed exponent has no YAML 1.1 spelling with that text.
    assertThrows(IllegalArgumentException.class,
        () -> emit(YamlEmitOptions.DEFAULT.withSchema(YamlSchema.YAML_1_1), new S(DEC, 0, "1e400")));
    // A tag that contradicts the value is rejected.
    assertThrows(IllegalArgumentException.class, () -> {
      try (YamlWriter w = new YamlWriter(new StringBuilder())) {
        w.tag("tag:yaml.org,2002:str");
        w.longValue(1);
      }
    });
  }

  @Test
  void keys() {
    StringBuilder out = new StringBuilder();
    try (YamlWriter w = new YamlWriter(out)) {
      w.beginMapping();
      w.key("1", KeyKind.INT);
      w.value("a");
      w.key("1.5", KeyKind.FLOAT);
      w.value("b");
      w.key("", KeyKind.NULL);
      w.value("c");
      w.key("true", KeyKind.BOOL);
      w.value("d");
      w.key("2");
      w.value("e");
      w.key("01", KeyKind.INT); // not canonical: written as the string it is
      w.value("f");
      w.end();
    }
    assertEquals("1: a\n1.5: b\nnull: c\ntrue: d\n\"2\": e\n\"01\": f\n", out.toString());
    String longKey = "k".repeat(1001);
    assertEquals("? " + longKey + "\n: v\n", emit(YamlEmitOptions.DEFAULT, map(longKey, "v")));
    assertEquals("{? " + longKey + ": v}\n", emit(YamlEmitOptions.DEFAULT.flow(), map(longKey, "v")));
    assertEquals("? " + longKey + "\n:\n  - v\n",
        emit(YamlEmitOptions.DEFAULT.withIndentSequences(false), map(longKey, seq("v"))));
    assertEquals("b: 1\nc:\n  a: 2\n  z: 3\n\"d\\ne\": 4\n",
        emit(YamlEmitOptions.DEFAULT.withSortKeys(true),
            map("d\ne", i64(4), "c", map("z", i64(3), "a", i64(2)), "b", i64(1))));
  }

  @Test
  void anchorsAliasesAndTags() {
    StringBuilder out = new StringBuilder();
    try (YamlWriter w = new YamlWriter(out)) {
      w.beginSequence();
      w.anchor("x");
      w.value("v");
      w.alias("x");
      w.anchor("m");
      w.tag("tag:yaml.org,2002:map");
      w.beginMapping();
      w.key("k");
      w.tag("!local");
      w.value("t");
      w.end();
      w.anchor("x"); // redefinition gets a fresh output name
      w.beginSequence();
      w.value("w");
      w.end();
      w.alias("x");
      w.alias("m");
      w.tag("tag:example.com,2000:app/x y");
      w.value("u");
      w.end();
    }
    assertEquals("- &x v\n- *x\n- &m !!map\n  k: !local t\n- &x\n  - w\n- *x\n- *m\n"
        + "- !<tag:example.com,2000:app/x%20y> u\n", out.toString());
    Boolean kept = SESSIONS.get(YamlSchema.CORE).with(out.toString().getBytes(StandardCharsets.UTF_8),
        root -> Yaml.isAlias(root.element(4)) && "!local".equals(Yaml.tag(root.element(2).member("k"))));
    assertTrue(kept);
    // Sorting moves an alias before its anchor: the definition moves with it.
    StringBuilder sorted = new StringBuilder();
    try (YamlWriter w = new YamlWriter(sorted, YamlEmitOptions.DEFAULT.withSortKeys(true))) {
      w.beginMapping();
      w.key("b");
      w.anchor("x");
      w.value("v");
      w.key("a");
      w.alias("x");
      w.end();
    }
    assertEquals("a: &x v\nb: *x\n", sorted.toString());
    assertEquals("--- &r\na: b\n", emit(YamlEmitOptions.DEFAULT, new Anc("r", map("a", "b"))));
    assertEquals("&r x\n", emit(YamlEmitOptions.DEFAULT, new Anc("r", str("x"))));
    assertEquals("- &r\n  a: b\n- *r\n",
        emit(YamlEmitOptions.DEFAULT, seq(new Anc("r", map("a", "b")), new Ali("r", map()))));
    assertEquals("a: &r\n  - b\n", emit(YamlEmitOptions.DEFAULT.withIndentSequences(false),
        map("a", new Anc("r", seq("b")))));
    // rapidyaml would give "- &r" to an explicit first key on the next line: flow instead.
    String longKey = "k".repeat(1001);
    assertEquals("- &r {? " + longKey + ": v}\n",
        emit(YamlEmitOptions.DEFAULT, seq(new Anc("r", map(longKey, "v")))));
    roundTrip(YamlEmitOptions.DEFAULT, seq(new Anc("r", map(longKey, "v")), new Ali("r", map(longKey, "v"))));
    // An anchored key that sorting places after its alias: the alias position takes the anchor.
    StringBuilder keyed = new StringBuilder();
    try (YamlWriter w = new YamlWriter(keyed, YamlEmitOptions.DEFAULT.withSortKeys(true))) {
      w.beginMapping();
      w.anchor("k");
      w.key("b");
      w.value("v");
      w.key("a");
      w.alias("k");
      w.end();
    }
    assertEquals("a: &k b\nb: v\n", keyed.toString());
    assertThrows(IllegalArgumentException.class, () -> emit(YamlEmitOptions.DEFAULT, new Ali("nope", map())));
    assertThrows(IllegalArgumentException.class, () -> new YamlWriter(new StringBuilder()).anchor("a b"));
  }

  @Test
  void protocolMisuseFails() {
    assertThrows(IllegalStateException.class, () -> {
      YamlWriter w = new YamlWriter(new StringBuilder());
      w.beginMapping();
      w.value("no key");
    });
    assertThrows(IllegalStateException.class, () -> {
      YamlWriter w = new YamlWriter(new StringBuilder());
      w.beginSequence();
      w.key("k");
    });
    assertThrows(IllegalStateException.class, () -> {
      YamlWriter w = new YamlWriter(new StringBuilder());
      w.beginSequence();
      w.close();
    });
    assertThrows(IllegalStateException.class, () -> new YamlWriter(new StringBuilder()).end());
    assertThrows(IllegalArgumentException.class, () -> YamlEmitOptions.DEFAULT.withIndent(0));
    assertThrows(IllegalArgumentException.class, () -> YamlEmitOptions.DEFAULT.withQuote(Style.PLAIN));
  }
}
