package dev.elide.truffledata.yaml.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.Constraints;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TapeSink;
import dev.elide.truffledata.tape.TapeText;
import dev.elide.truffledata.tape.TapeValue;
import dev.elide.truffledata.tape.TypedRecord;
import dev.elide.truffledata.yaml.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The Java kernel against the native decoder (yaml_shared through FFM) on the same inputs and
 * options: accept/reject class, every exported event (all numeric kinds, tags, anchors, aliases),
 * per-node metadata (styles, key kinds, alias bits, tags, anchors), raw lexical text, directives,
 * stream documents and typed plans. Known parser divergences are listed, with reasons, in
 * java_kernel_excluded.txt; every listed case must still diverge, and nothing else may.
 */
final class JavaKernelDifferentialTest {
  /** Option sets every corpus input runs under. */
  static final Map<String, YamlOptions> MODES = new LinkedHashMap<>();

  static {
    MODES.put("core", YamlOptions.DEFAULT);
    MODES.put("retain", YamlOptions.DEFAULT.retainSource(true));
    MODES.put("yaml11", YamlOptions.yaml11().retainSource(true));
    MODES.put("json", YamlOptions.json());
    MODES.put("lastwins-merge",
        YamlOptions.DEFAULT.withDuplicateKeys(DuplicateKeys.LAST_WINS).mergeKeys(true));
    MODES.put("failsafe-strict",
        YamlOptions.DEFAULT.withSchema(YamlSchema.FAILSAFE).withAliases(Aliases.REJECT)
            .withUnknownTags(UnknownTags.REJECT).withVersionDirective(VersionDirective.HONOR));
  }

  // Native decoder messages raised by policy, not by the parser: when either side reports one,
  // both must report exactly the same message.
  private static final List<String> SEMANTIC = List.of(
      "duplicate YAML key", "undefined YAML alias", "recursive YAML alias", "YAML aliases are rejected",
      "YAML complex key", "YAML unknown tag", "YAML merge", "YAML !!", "YAML collection tag",
      "YAML mapping tag", "YAML sequence tag", "YAML scalar tag", "invalid base64", "invalid YAML !!",
      "unsupported YAML version", "too many YAML %TAG", "YAML JSON schema", "invalid UTF-",
      "non-printable", "truncated UTF-", "YAML resource limit", "YAML tag not supported",
      "unpaired surrogate", "invalid Unicode escape");

  /** Events of an export that keeps aliases as references. */
  static class Events implements TapeSink {
    final StringBuilder out;
    Events(StringBuilder out) { this.out = out; }
    public void beginObject(long n) { out.append("{").append(n).append(' '); }
    public void beginArray(long n) { out.append("[").append(n).append(' '); }
    public void end() { out.append("} "); }
    public void key(TapeText key) { out.append("k:").append(key.toJavaString()).append(' '); }
    public void nullValue() { out.append("null "); }
    public void booleanValue(boolean value) { out.append(value).append(' '); }
    public void longValue(long value) { out.append("i").append(value).append(' '); }
    public void unsignedValue(long value) { out.append("u").append(Long.toUnsignedString(value)).append(' '); }
    public void doubleValue(double value) {
      out.append("f").append(Long.toHexString(Double.doubleToRawLongBits(value))).append(' ');
    }
    public void decimalValue(TapeText value) { out.append("d").append(value.toJavaString()).append(' '); }
    public void stringValue(TapeText value) { out.append("s:").append(value.toJavaString()).append(' '); }
    @Override public void tag(TapeText uri) { out.append("!").append(uri.toJavaString()).append(' '); }
    @Override public void anchor(int id) { out.append("&").append(id).append(' '); }
    @Override public boolean alias(int id) { out.append("*").append(id).append(' '); return true; }
  }

  private static String raw(TapeValue value) {
    try {
      return value.rawText();
    } catch (IllegalStateException none) {
      return "-";
    }
  }

  /** Per-node metadata and lexical text, walking without expanding aliases. */
  static void walk(TapeValue value, StringBuilder out) {
    out.append('<').append(Yaml.style(value)).append(Yaml.isAlias(value) ? " alias" : "");
    String tag = Yaml.tag(value), anchor = Yaml.anchor(value);
    if (tag != null) out.append(" !").append(tag);
    if (anchor != null) out.append(" &").append(anchor);
    if (Yaml.isAlias(value)) {
      out.append("> ");
      return;
    }
    out.append(" raw=").append(raw(value));
    if (value.kind() == TapeValue.Kind.NUMBER) out.append(" n=").append(value.numberText());
    out.append("> ");
    if (value.kind() == TapeValue.Kind.OBJECT) {
      for (long i = 0; i < value.size(); i++) {
        TapeValue key = value.keyAt(i);
        out.append("key(").append(Yaml.keyKind(value, i)).append(' ').append(Yaml.style(key))
            .append(Yaml.isAlias(key) ? " alias" : "");
        if (Yaml.tag(key) != null) out.append(" !").append(Yaml.tag(key));
        if (Yaml.anchor(key) != null) out.append(" &").append(Yaml.anchor(key));
        out.append(" raw=").append(raw(key)).append(") ");
        walk(value.memberAt(i), out);
      }
    } else if (value.kind() == TapeValue.Kind.ARRAY) {
      for (long i = 0; i < value.size(); i++) walk(value.element(i), out);
    }
  }

  static String describe(TapeValue root) {
    StringBuilder out = new StringBuilder();
    root.export(new Events(out));
    out.append("| ");
    walk(root, out);
    return out.toString();
  }

  static String category(RuntimeException failure) {
    String message = String.valueOf(failure.getMessage());
    String kind = failure.getClass().getSimpleName();
    String detail = message;
    if (message.startsWith("YAML syntax error: ")) detail = message.substring(19);
    int at = detail.lastIndexOf(" at line ");
    String text = at >= 0 ? detail.substring(0, at) : detail;
    for (String semantic : SEMANTIC) if (text.startsWith(semantic)) return kind + ": " + semantic + "|" + text;
    if (message.startsWith("YAML syntax error: ")) return kind + ": syntax";
    if (message.startsWith("Truncated YAML")) return kind + ": truncated";
    return kind + ": " + text;
  }

  /** Every document of {@code input} as a stream, or the failure after the documents before it. */
  static String stream(YamlSession session, byte[] input) {
    StringBuilder out = new StringBuilder();
    try (YamlStream documents = session.documents(input)) {
      for (TapeValue root; (root = documents.next()) != null; ) out.append("doc ").append(describe(root)).append('\n');
      return out.toString();
    } catch (RuntimeException failure) {
      return out + "reject " + category(failure);
    }
  }

  /** The single-document decode of {@code input}, with its directives. */
  static String single(YamlSession session, byte[] input) {
    try (YamlMessage message = session.decode(input)) {
      return describe(message.root()) + " " + message.directives();
    } catch (RuntimeException failure) {
      return "reject " + category(failure);
    }
  }

  static Path library() {
    return YamlIntegrationTest.library();
  }

  record Sessions(YamlSession nativeSession, YamlSession java) implements AutoCloseable {
    static Sessions open(YamlOptions options) {
      YamlSession n = YamlJvm.openSession(library(), options);
      return new Sessions(n, YamlJava.openSession(options));
    }

    @Override
    public void close() {
      try { nativeSession.close(); } finally { java.close(); }
    }
  }

  /**
   * The yaml-test-suite's grammar edge cases may be rejected by both parsers at different points:
   * whether a parse error counts as truncation depends on the line each parser detects it on, and
   * a policy failure (JSON schema, complex key) can be reached before or after the other parser's
   * syntax error. Rejection itself must agree; when both sides report a policy failure, the
   * messages must still be equal. Applied to the suite only; every other corpus is compared exactly.
   */
  static String normalize(String outcome) {
    int at = outcome.lastIndexOf("reject IllegalArgumentException: ");
    if (at < 0) return outcome;
    String reason = outcome.substring(at + 33);
    if (reason.equals("truncated") || reason.equals("syntax")
        || reason.startsWith("YAML JSON schema|") || reason.startsWith("YAML complex key|"))
      return outcome.substring(0, at) + "reject IllegalArgumentException: syntax";
    return outcome;
  }

  /** Compares one input under every mode: mode (and form) to the mismatch, empty when equal. */
  static Map<String, String> compare(
      Map<String, Sessions> sessions, String name, byte[] input, boolean single, boolean suite) {
    Map<String, String> mismatches = new LinkedHashMap<>();
    for (var mode : sessions.entrySet()) {
      Sessions s = mode.getValue();
      String expected = stream(s.nativeSession, input), actual = stream(s.java, input);
      if (suite) {
        expected = normalize(expected);
        actual = normalize(actual);
      }
      if (!expected.equals(actual))
        mismatches.put(mode.getKey(), name + " [" + mode.getKey() + " stream]\n  native: " + expected + "\n  java:   " + actual);
      else if (single) {
        expected = single(s.nativeSession, input);
        actual = single(s.java, input);
        if (suite) {
          expected = normalize(expected);
          actual = normalize(actual);
        }
        if (!expected.equals(actual))
          mismatches.put(mode.getKey(), name + " [" + mode.getKey() + " decode]\n  native: " + expected + "\n  java:   " + actual);
      }
    }
    return mismatches;
  }

  /**
   * Checks {@code mismatches} of input {@code name} against the listed divergences: an entry
   * {@code name} covers every mode, {@code name@mode} one mode. Listed entries that matched are
   * removed from {@code stale}.
   */
  static void account(String name, Map<String, String> mismatches, Map<String, String> excluded,
      Set<String> stale, List<String> failures) {
    for (var mismatch : mismatches.entrySet()) {
      String scoped = name + "@" + mismatch.getKey();
      if (excluded.containsKey(name)) stale.remove(name);
      else if (excluded.containsKey(scoped)) stale.remove(scoped);
      else failures.add(mismatch.getValue());
    }
  }

  static Map<String, Sessions> sessions() {
    Map<String, Sessions> sessions = new LinkedHashMap<>();
    for (var mode : MODES.entrySet()) sessions.put(mode.getKey(), Sessions.open(mode.getValue()));
    return sessions;
  }

  static void close(Map<String, Sessions> sessions) {
    for (Sessions s : sessions.values()) s.close();
  }

  static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  static Map<String, String> excluded() throws Exception {
    Map<String, String> excluded = new TreeMap<>();
    for (String line : Files.readAllLines(SnakeYamlDifferentialTest.workspace("yaml/tests/java_kernel_excluded.txt")))
      if (!line.isBlank() && !line.startsWith("#")) {
        String[] parts = line.split("\t", 2);
        excluded.put(parts[0], parts.length > 1 ? parts[1] : "");
      }
    return excluded;
  }

  private static Path suite() {
    String override = System.getProperty("yaml.suite");
    return override != null ? Path.of(override) : Path.of(System.getenv("TEST_SRCDIR"), "+http_archive+yaml_test_suite");
  }

  @Test
  void yamlTestSuite() throws Exception {
    Map<String, String> excluded = excluded();
    List<String> failures = new ArrayList<>();
    Set<String> stale = new TreeSet<>(excluded.keySet());
    Map<String, Sessions> sessions = sessions();
    int cases = 0;
    try (var files = Files.walk(suite().toRealPath())) {
      Path root = suite().toRealPath();
      for (Path input : files.filter(p -> p.getFileName().toString().equals("in.yaml")).sorted().toList()) {
        String name = root.relativize(input.getParent()).toString();
        if (name.startsWith("name/") || name.startsWith("tags/")) continue;
        cases++;
        account(name, compare(sessions, name, Files.readAllBytes(input), true, true), excluded, stale, failures);
      }
    } finally {
      close(sessions);
    }
    assertEquals(402, cases);
    assertTrue(failures.isEmpty(), failures.size() + " unexpected divergences:\n" + String.join("\n", failures));
    stale.removeIf(name -> !name.matches("[0-9A-Z]{4}(/[0-9]+)?(@.*)?"));
    assertTrue(stale.isEmpty(), "Excluded cases that no longer diverge: " + stale);
  }

  @Test
  void probeFixturesAndFuzz() throws Exception {
    Map<String, Sessions> sessions = sessions();
    List<String> failures = new ArrayList<>();
    try {
      try (var files = Files.list(SnakeYamlDifferentialTest.workspace("yaml/native/testdata"))) {
        for (Path input : files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList()) {
          failures.addAll(compare(sessions, input.getFileName().toString(), Files.readAllBytes(input), true, false).values());
        }
      }
      YamlFuzz fuzz = new YamlFuzz(0x4a415641L);
      for (int i = 0; i < 1000; i++) {
        failures.addAll(compare(sessions, "fuzz/" + i, bytes(fuzz.next(i)), true, false).values());
      }
    } finally {
      close(sessions);
    }
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  /** Inputs of the native decoder, semantics, stream and API tests, and policy edges. */
  static final List<String> POLICY_CASES = List.of(
      // documents and scalars (decoder_test.cc)
      "", "# only a comment\n", "---\n", "--- ~\n...\n", "a: 1\nb: [true, ~, 1.5, -0, 0x1F, .inf]\nc: {d: e}\n",
      "- 18446744073709551615\n- 18446744073709551616\n- 1e400\n- +1e400\n",
      "k: 'single ''q'''\nd: \"tab\\there\"\nl: |\n  line\n  two\nf: >-\n  folded\n  text\n",
      "plain: multi\n  line\n", "!!str 12", "- !!int \"12\"\n- !!float 1\n- ! 12\n- !!null ''\n", "!!int abc",
      "!!map [1]", "!!str [1]", "!!seq [1]", "[a: b, c, \"d\": e, 1: f]", "[: x]",
      "{~: a, true: b, 0x1F: c, 1.50: d, s: e, \"1\": f}", "%YAML 1.1\n--- yes\n", "%YAML 2.0\n--- x\n",
      "a: 1\nb: [1, 2\n", "a: &x 1\nb: *x\n", "{\"a\": [1, -0, 1.5, true, null, \"x\"]}", "a: 1", "[.5]",
      "a: [1, ~, true, ]",
      // duplicate keys after resolution (#40 positions)
      "{1: a, 0x1: b}", "{1: a, \"1\": b}", "{null: a, ~: b}", "{a: 1, \"a\": 2}", "{0o17: a, 15: b}",
      "{1.5: a, 1.50: b}", "a: 1\nb: 2\na: 3\n", "{\"\": a, ~: b}", "a: 1\na: 2\n", "x: 0\na: 1\na: 2\nz: 3\n",
      "m:\n  k: 1\n  k: 2\nn: 3\n", "q: 1\n'q': 2\n", "t: >\n  folded\n  text\nk: 1\n  # c\nk: 2\n",
      "[{\u00e9: 1, \u00e9: 2}]", "{a: {a: 1}, b: {a: 2}}", "{a: 1, a: 2}",
      "a: [1, {x: y}]\nb: long string value beyond inline\na: 3\nc: {a: 4}\nb: 5\n", "{1: a, 0x1: b, \"1\": c}",
      "[{k: 1, k: 2}, {k: 3}]", "a: &v 1\na: 2\n", "a: [&v 1]\nb: *v\na: 2\n", "{a: 1, b: 2, a: 3, b: 4}",
      // complex keys
      "? [a, b]\n: c\n", "{[a]: x}", "{{a: 1}: x}", "[a]: b\n", "{a: 1}: x\n", "? {a: 1}\n: x\n", "[[a]: b]",
      "[{a: 1}: b]", "? a\n: b\n", "? a\n", "a: &c [1]\nb: {*c : x}\n",
      // depth
      "[".repeat(128) + "]".repeat(128), "[".repeat(129) + "]".repeat(129),
      // owned strings and spans
      "- 0123456789abcdef\n- 0123456789abcdefg\n- \"\\u00e9t\\u00e9 quoted, longer\"\n- |\n  literal block text\n- \"\\L grows\"\n",
      "plain: 0x1F\n'single': 'it''s'\n\"double\": \"a\\tb\"\nmulti: one\n  two\nblock: |\n  text\nflow: [1, two , +3]\nempty:\n",
      "- x # note\n- y\n# trailing\n- z\n", "- a\n- folded: >\n    one\n    two\n",
      "a: |+\n  keep\n\n\nb: >2\n   indented\n", "- |\n  text\n# comment\n- next\n",
      // multiple documents
      "a\n---\nb\n", "--- a\n--- b\n", "a\n...\n---\nb\n", "---\n---\n", "--- a\n...\n",
      // schemas (semantics_test.cc)
      "a: on\nb: yes\nc: y\nd: 0755\ne: 1:20\nf: 2001-12-14\ng: !!str 0x1F\nh: 0b101\ni: 1_000\nj: off\nk: 0x1_0000_0000_0000_0000\n<<: {m: 1}\n",
      "[0o17, 017, -0x1F, +0b11, 190:20:30, 1_000.5, .5, 1.5e+3, 1e3, 1., 190:20:30.15, ~, Null, NO, Off]",
      "- 2001-12-14t21:59:43.10-05:00\n- 2001-12-14 21:59:43.10 -5\n- 2002-1-2\n", "- 2002-1-2",
      "[!!str 0x1F, !!int 0755]", "[!!str 0x1F, !!int 0755, !!float 1, !!bool yes]", "%YAML 1.1\n--- [yes, 0755]\n",
      "%YAML 1.1\n--- [yes, 0755, {<<: {a: 1}}]\n", "%YAML 1.2\n--- [yes, 0755]\n", "[yes]",
      // aliases
      "&a [*a]", "&a {k: *a}", "&a [1, [2, *a]]", "a: &x\n  b: *x\n", "- &x\n  - *x\n", "*x", "a: *x\n", "[*x]",
      "a: &y 1\nb: *x\n", "{*x : 1}", "a: &x 1\nb: *x\nc: &x 2\nd: *x\ne: &y [*x]\n",
      "{a: &k b, &n 0x1F: c, map: {*k : 1, *n : 2}}", "[&x a: b, *x]", "a: &a [one, two, three]\nb: &b [*a, *a, *a]\nc: [*b, *b, *b]\n",
      "a: &k ~\nb: &t true\nc: &f 1.5\nd: &d 18446744073709551616\ne: {*k : 1, *t : 2, *f : 3, *d : 4}\n",
      // merges
      "base: &b {x: 1, y: 2}\nover: &o {y: 3, z: 4}\nm: {<<: [*o, *b], y: 9, w: 0}\n",
      "base: &b {x: 1, y: 2}\nover: &o {y: 3, z: 4}\nm: {w: 0, <<: [*b, *o]}\n",
      "base: &b {x: 1, y: 2}\nm: {<<: *b, x: 7}\n", "{<<: {x: 1, y: [2]}, y: 3}", "{<<: [{x: 1}, {x: 2, z: [3]}], a: 0}",
      "a: &a {k: &v [1]}\nb: &b {<<: *a}\nc: {<<: *b}\n", "base: &b {x: 1, y: 2}\nm: {q: &q 5, <<: *b}\nn: *q\n",
      "{<<: 1}", "{<<: [1]}", "a: &s [1]\nb: {<<: *s}\n", "{<<: [{a: 1}, 2]}", "{<<: {a: 1}, <<: {b: 2}}",
      "{<<: &m {a: 1}}", "{a: 1, <<: {a: 2, b: 2}, a: 3}", "{\"<<\": {a: 1}}", "{!!merge <<: {a: 1}}",
      "{<<: {a: &x 1}, a: 2, b: *x}", "{<<: {&k a: 1}}", "a: &a {x: *b}\n", "x: &x {a: 1}\ny: {<<: *x, <<: *x}\n",
      "x: &x {a: 1, b: *y}\n", "x: &x {a: &v 1}\ny: {<<: *x}\nz: *v\n", "x: &x {a: 1}\ny: {<<: [*x, *x]}\n",
      // tags
      "- !!str 12\n- !!int \"12\"\n- !local 12\n- !<tag:x.org,2000:y> v\n- ! 12\n- !!binary aGVsbG8gd29y\n  bGQ=\n",
      "!!binary 'not base64!'", "!!binary " + "A".repeat(6000), "!!binary SGVsbA=", "!!binary SGVsbA==", "!!binary S",
      "!!timestamp yesterday", "!!timestamp 2001-12-14t21:59:43.10-05:00", "--- !!set\n? a\n? b\n", "!!set {a: 1}",
      "!!omap [a: 1, b: 2]", "!!omap [a]", "!!pairs [{a: 1, b: 2}]", "!!set [a]", "!!omap {a: 1}", "!!int [1]",
      "!!seq a", "%TAG !e! tag:example.com,2000:app/\n--- !e!foo bar\n", "[!!str a, !!str b, !x c, !x d]",
      "!local 12", "[!!str 12, ! 13]", "!!merge x", "!!value =", "!!value x", "{!!merge <<: 1}",
      "%TAG !a! tag:a:\n%TAG !b! tag:b:\n%TAG !c! tag:c:\n%TAG !d! tag:d:\n%TAG !e! tag:e:\n--- !e!x y\n",
      // Unicode and input
      "[\"\\ud83d\\ude00\",\"prefix \\ud83d\\ude00 suffix\",\"\\U0001F600\"]", "\"\\ud800\"", "\"\\udc00\"",
      "\"\\ud800x\"", "\"\\U00110000\"", "{\"\u00f0\u009f\u0098\u0080\": 1, \"\\ud83d\\ude00\": 2}",
      "{~: a, true: b, 012: c, 1.50: d, .inf: e, 18446744073709551616: f, \"s\": g, !!str 3: h}",
      "[.inf, -.Inf, .NAN, !!float .inf, 1.5]", "a: \u0001", "a: \u007f", "a: \u0085b", "a: \ufffe",
      "key: caf\u00e9 \u96ea \ud83d\ude00\n", "[-0, -0.0, 0.0, +0, 1e-400, -1e-400]",
      // stream boundaries (stream_test.cc)
      "a: |\n  ---\n  x\n---\nb\n", "--- |\nline\n---\nnext\n", "---word\n", "a\n---", "# nothing\n",
      "a\n...\n# only a comment\n", "---\nkey: value\n%YAML 1.2\n---\n", "a: 1\r\n---\r\nb: 2\r\n",
      "a: 1\n---\nb: [1, 2", "a: 1\n---\nk: \"unterminated", "a\n---\nb: : c\n---\nd\n", "a\n---\n{k: 1, k: 2}",
      "a\n---\n[" + "[".repeat(129) + "]");

  /**
   * Policy-corpus inputs on which SnakeYAML Engine's grammar differs from rapidyaml's; the kernel's
   * policies are not involved. Each must still diverge.
   */
  static final Map<String, String> POLICY_DIVERGENCES = Map.of(
      "[: x]", "Engine rejects an empty implicit key in a flow sequence pair (as suite HM87/00, CFD4)",
      "- 0123456789abcdef\n- 0123456789abcdefg\n- \"\\u00e9t\\u00e9 quoted, longer\"\n- |\n  literal block text\n- \"\\L grows\"\n",
      "Engine 3.2 lacks the YAML 1.2 \\L and \\P escapes (U+2028, U+2029)",
      "--- |\nline\n---\nnext\n", "Engine rejects block scalar content at column 0 of a root node",
      "a: \u0085b", "Engine folds NEL (U+0085) as a YAML 1.1 line break; YAML 1.2 and rapidyaml keep it as content");

  @Test
  void policyCases() {
    Map<String, Sessions> sessions = sessions();
    List<String> failures = new ArrayList<>();
    try {
      Set<String> stale = new TreeSet<>(POLICY_DIVERGENCES.keySet());
      for (String input : POLICY_CASES)
        account(input, compare(sessions, input, bytes(input), true, false), POLICY_DIVERGENCES, stale, failures);
      assertTrue(stale.isEmpty(), "Listed policy divergences that no longer diverge: " + stale);
      // Encodings: BOMs, UTF-16/32, invalid UTF-8.
      byte[][] encoded = {
        {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', ':', ' ', '1'},
        utf16("a: [1, caf\u00e9]\n", true), utf16("a: [1, caf\u00e9]\n", false),
        utf32("x: \ud83d\ude00\n", true), utf32("x: \ud83d\ude00\n", false),
        {'a', ':', ' ', (byte) 0xC3}, {'a', ':', ' ', (byte) 0xC0, (byte) 0x80}, {'a', ':', ' ', (byte) 0xED, (byte) 0xA0, (byte) 0x80},
        {(byte) 0xFF, (byte) 0xFE, 'a'}, {(byte) 0xFF, (byte) 0xFE, 0, (byte) 0xD8, 0, 0},
      };
      for (int i = 0; i < encoded.length; i++) {
        failures.addAll(compare(sessions, "encoded/" + i, encoded[i], true, false).values());
      }
    } finally {
      close(sessions);
    }
    assertTrue(failures.isEmpty(), failures.size() + " divergences:\n" + String.join("\n", failures));
  }

  private static byte[] utf16(String text, boolean little) {
    byte[] body = text.getBytes(little ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16BE);
    byte[] out = new byte[body.length + 2];
    out[0] = (byte) (little ? 0xFF : 0xFE);
    out[1] = (byte) (little ? 0xFE : 0xFF);
    System.arraycopy(body, 0, out, 2, body.length);
    return out;
  }

  private static byte[] utf32(String text, boolean little) {
    int[] points = text.codePoints().toArray();
    byte[] out = new byte[4 + 4 * points.length];
    int[] bom = little ? new int[] {0xFF, 0xFE, 0, 0} : new int[] {0, 0, 0xFE, 0xFF};
    for (int i = 0; i < 4; i++) out[i] = (byte) bom[i];
    for (int i = 0; i < points.length; i++)
      for (int b = 0; b < 4; b++)
        out[4 + 4 * i + b] = (byte) (points[i] >>> (little ? 8 * b : 8 * (3 - b)));
    return out;
  }

  @Test
  void limits() {
    List<String> failures = new ArrayList<>();
    String doc = "a: &a [one, two, three]\nb: &b [*a, *a, *a]\nc: [*b, *b, *b]\nd: {x: 'long text value', y: [1, 2, 3]}\n";
    YamlLimits[] tight = {
      new YamlLimits(16, 1 << 20, 1000, 1 << 20, 128, 100, 1000),
      new YamlLimits(1 << 20, 1 << 20, 10, 1 << 20, 128, 100, 1000),
      new YamlLimits(1 << 20, 1 << 20, 1000, 20, 128, 100, 1000),
      new YamlLimits(1 << 20, 1 << 20, 1000, 1 << 20, 1, 100, 1000),
      new YamlLimits(1 << 20, 1 << 20, 1000, 1 << 20, 2, 100, 1000),
      new YamlLimits(1 << 20, 1 << 20, 1000, 1 << 20, 128, 5, 1000),
      new YamlLimits(1 << 20, 1 << 20, 1000, 1 << 20, 128, 100, 12),
    };
    for (YamlLimits limits : tight) {
      for (YamlOptions base : List.of(YamlOptions.DEFAULT, YamlOptions.yaml11())) {
        YamlOptions options = base.withLimits(limits);
        try (Sessions s = Sessions.open(options)) {
          for (String input : List.of(doc, "base: &b {x: 1, y: 2}\nm: {<<: *b, z: 3}\n", "[[1]]", "[[[1]]]", "x")) {
            String expected = stream(s.nativeSession, bytes(input)), actual = stream(s.java, bytes(input));
            if (!expected.equals(actual)) failures.add(limits + " " + input + "\n  native: " + expected + "\n  java:   " + actual);
            expected = single(s.nativeSession, bytes(input));
            actual = single(s.java, bytes(input));
            if (!expected.equals(actual)) failures.add(limits + " " + input + "\n  native: " + expected + "\n  java:   " + actual);
            expected = expanded(s.nativeSession, bytes(input));
            actual = expanded(s.java, bytes(input));
            if (!expected.equals(actual)) failures.add(limits + " expanded " + input + "\n  native: " + expected + "\n  java:   " + actual);
          }
        }
      }
    }
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  /** An export that expands every alias, charged against maxAliasExpansion. */
  static String expanded(YamlSession session, byte[] input) {
    try (YamlMessage message = session.decode(input)) {
      StringBuilder out = new StringBuilder();
      message.export(new Events(out) {
        @Override public boolean alias(int id) { return false; }
      });
      return out.toString();
    } catch (RuntimeException failure) {
      return "reject " + category(failure);
    }
  }

  /** Guest strings: Latin-1 and UTF-16 storage reach the decoder transcoded, as natively. */
  @Test
  void guestStrings() {
    List<String> failures = new ArrayList<>();
    try (Sessions s = Sessions.open(YamlOptions.DEFAULT)) {
      for (String text : List.of("a: caf\u00e9\n", "k: \u96ea \ud83d\ude00\nl: [x, \"\u00e9\"]\n", "plain: ascii\n",
          "bad: \ud800\n", "{a: 1, a: 2}")) {
        var string = com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
            text, com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16);
        String expected = guest(s.nativeSession, string), actual = guest(s.java, string);
        if (!expected.equals(actual)) failures.add(text + "\n  native: " + expected + "\n  java:   " + actual);
      }
    }
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  static String guest(YamlSession session, com.oracle.truffle.api.strings.TruffleString text) {
    try {
      dev.elide.truffledata.tape.JavaSink sink = new dev.elide.truffledata.tape.JavaSink();
      session.parse(text, sink);
      return String.valueOf(sink.result());
    } catch (RuntimeException failure) {
      return "reject " + category(failure);
    }
  }

  /** Incremental feeding: chunk sizes, backpressure, limits and failed streams. */
  @Test
  void chunkedStreams() {
    StringBuilder many = new StringBuilder();
    for (int i = 0; i < 50; i++) many.append("---\nitem: ").append(i).append("\n");
    List<String> inputs = List.of(many.toString(), "---\nsmall\n---\n" + "x".repeat(200) + "\n---\nafter\n", "x".repeat(200),
        "a\n---\nb: : c\n---\nd\n", "a: 1\n---\nb: [1, 2", "a: 1\n---\nb: \u00e9", "a: |\n  ---\n  x\n---\nb\n");
    List<String> failures = new ArrayList<>();
    try (Sessions s = Sessions.open(YamlOptions.DEFAULT)) {
      for (String input : inputs)
        for (int chunk : new int[] {1, 7, 64, 4096})
          for (int buffer : new int[] {64, 1 << 20}) {
            String expected = feed(s.nativeSession, bytes(input), chunk, 100, buffer);
            String actual = feed(s.java, bytes(input), chunk, 100, buffer);
            if (!expected.equals(actual))
              failures.add(chunk + "/" + buffer + " " + input + "\n  native: " + expected + "\n  java:   " + actual);
          }
    }
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  static String feed(YamlSession session, byte[] input, int chunk, int maxDocument, int buffer) {
    StringBuilder out = new StringBuilder();
    try (YamlStream stream = session.stream(new YamlStreamOptions(maxDocument, buffer))) {
      int fed = 0;
      while (fed < input.length) {
        int accepted = stream.feed(input, fed, Math.min(chunk, input.length - fed));
        out.append(accepted).append(',');
        fed += accepted;
        for (TapeValue root; (root = stream.next()) != null; ) out.append("doc ").append(describe(root)).append('\n');
        if (accepted == 0 && stream.next() == null && fed < input.length) {
          out.append("stalled");
          break;
        }
      }
      stream.finish();
      for (TapeValue root; (root = stream.next()) != null; ) out.append("doc ").append(describe(root)).append('\n');
      return out.toString();
    } catch (RuntimeException failure) {
      return out + "reject " + category(failure);
    }
  }

  /** Typed plans: slots, records and contract failures. */
  @Test
  void typedPlans() {
    Schema flat = Schema.of(
        new Field("s", Field.Kind.STRING, true, true),
        new Field("n", Field.Kind.INT64, true, true),
        new Field("f", Field.Kind.FLOAT64, true, true),
        new Field("b", Field.Kind.BOOLEAN, true, true),
        Field.integer("small", 8, true, true, true));
    Schema point = new Schema("Point", List.of(Field.requiredLong("n"), new Field("tag", Field.Kind.STRING, true, true)));
    Field child = new Field("a", Field.Kind.RECORD, true, true, Constraints.NONE.withRecordType("Point"));
    Field element = new Field("e", Field.Kind.RECORD, false, false, Constraints.NONE.withRecordType("Point"));
    Schema nested = Schema.of(child,
        new Field("items", Field.Kind.ARRAY, true, true, Constraints.NONE.withElement(element)),
        new Field("words", Field.Kind.ARRAY, true, true,
            Constraints.NONE.withElement(new Field("w", Field.Kind.STRING, false, false))));
    List<String> documents = List.of(
        "s: 1.0\nn: 0x1f\nf: .inf\nb: true\n", "s: x\nn: '12'\n", "s: x\nn: 1\nextra: 0\n", "n: 1\nn: 2\n",
        "s: !!str 5\nn: !!int '7'\n", "f: -0\nn: -0.0\n", "n: 9223372036854775808\n", "small: 128\n", "small: -1\n",
        "s: ~\nn: null\n", "b: yes\n", "n: 0o17\n", "s: [1]\n", "s: \"\\u00e9\"\nn: 1_000\n", "", "# x\n", "[1]",
        "a: {n: 1}\n---\na: {n: 2}\n", "n: [\n", "s: &x hi\nn: 1\n", "a: &a {n: 7, tag: null}\nitems: [*a, {n: 9}]\n",
        "items: [{n: 1}, {n: 2, tag: x}]\nwords: [a, 'b', 3]\n", "items: [1]\n", "words: [null]\n", "a: {n: 1, z: 2}\n",
        "a: {tag: x}\n", "items: {n: 1}\n");
    List<String> failures = new ArrayList<>();
    for (YamlOptions options : List.of(YamlOptions.DEFAULT, YamlOptions.yaml11(),
        YamlOptions.DEFAULT.withDuplicateKeys(DuplicateKeys.LAST_WINS),
        YamlOptions.DEFAULT.withLimits(new YamlLimits(1 << 20, 1 << 20, 1000, 1 << 20, 128, 100, 3)))) {
      try (Sessions s = Sessions.open(options)) {
        for (Schema schema : List.of(flat, nested)) {
          Map<String, Schema> types = Map.of("Point", point);
          try (YamlPlan expectedPlan = s.nativeSession.compile(schema, types);
              YamlPlan actualPlan = s.java.compile(schema, types)) {
            for (String document : documents) {
              String expected = typed(expectedPlan, schema, types, document);
              String actual = typed(actualPlan, schema, types, document);
              if (!expected.equals(actual))
                failures.add(options + " " + document + "\n  native: " + expected + "\n  java:   " + actual);
            }
          }
        }
      }
    }
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  static String typed(YamlPlan plan, Schema schema, Map<String, Schema> types, String document) {
    try (TypedRecord row = plan.decode(bytes(document))) {
      return render(row, schema, types);
    } catch (RuntimeException failure) {
      // Typed contract and limit failures carry fixed messages; parser messages differ by parser.
      String message = String.valueOf(failure.getMessage());
      return "reject " + (message.startsWith("YAML syntax error: ") ? category(failure)
          : failure.getClass().getSimpleName() + ": " + message);
    }
  }

  static String render(TypedRecord row, Schema schema, Map<String, Schema> types) {
    Function<Object, String> show = String::valueOf;
    StringBuilder out = new StringBuilder("{");
    for (int i = 0; i < schema.size(); i++) {
      Field field = schema.field(i);
      out.append(field.name()).append('=');
      if (!row.isPresent(i)) out.append("missing");
      else if (row.isNull(i)) out.append("null");
      else {
        switch (field.kind()) {
          case STRING -> out.append('"').append(row.stringValue(i)).append('"');
          case INT64 -> out.append(row.longValue(i));
          case FLOAT64 -> out.append(Long.toHexString(Double.doubleToRawLongBits(row.doubleValue(i))));
          case BOOLEAN -> out.append(row.booleanValue(i));
          case RECORD -> out.append(render(row.record(i), types.get(field.constraints().recordType()), types));
          case ARRAY -> {
            var array = row.array(i);
            out.append('[');
            for (int e = 0; e < array.size(); e++) {
              Object element = array.element(e);
              Field contract = Objects.requireNonNull(field.constraints().element());
              out.append(element instanceof TypedRecord record
                  ? render(record, types.get(contract.constraints().recordType()), types) : show.apply(element)).append(',');
            }
            out.append(']');
          }
          default -> out.append("?");
        }
      }
      out.append(' ');
    }
    return out.append('}').toString();
  }
}
