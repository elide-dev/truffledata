package dev.elide.truffledata.yaml;

import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TapeSink;
import dev.elide.truffledata.tape.TapeText;
import dev.elide.truffledata.tape.TapeValue;
import dev.elide.truffledata.tape.TypedRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Cells route against the baseline route on the same inputs and options, compared exactly: every
 * stream document (fed whole and in 7-byte chunks), each retained document, the single-document
 * decode with its directives, shared-text parses and typed plans; a rejection must be the same
 * exception class with the same message at the same point. Shared by the JVM test and the native
 * image (no JUnit).
 */
public final class CellsParity {
  private CellsParity() {}

  /** Option sets every input runs under. */
  static final Map<String, YamlOptions> MODES = new LinkedHashMap<>();

  static {
    MODES.put("core", YamlOptions.DEFAULT);
    MODES.put("retain", YamlOptions.DEFAULT.retainSource(true));
    MODES.put("yaml11", YamlOptions.yaml11().retainSource(true));
    MODES.put("json", YamlOptions.json());
    MODES.put(
        "lastwins-merge",
        YamlOptions.DEFAULT.withDuplicateKeys(DuplicateKeys.LAST_WINS).mergeKeys(true));
    MODES.put(
        "failsafe-strict",
        YamlOptions.DEFAULT
            .withSchema(YamlSchema.FAILSAFE)
            .withAliases(Aliases.REJECT)
            .withUnknownTags(UnknownTags.REJECT)
            .withVersionDirective(VersionDirective.HONOR));
    MODES.put(
        "tight-limits",
        YamlOptions.DEFAULT.withLimits(
            new YamlLimits(4096, 1 << 16, 64, 1024, 4, 32, 8)));
  }

  /** Inputs that exercise acceptance, every rejection class and the limits. */
  static Map<String, byte[]> builtIn() {
    Map<String, byte[]> corpus = new TreeMap<>();
    String[] inputs = {
      "",
      "x: 1",
      "a: &item {x: 31, text: 'hello'}\nb: *item\nn: .nan\ni: .inf\nhex: 0x1F\nbig: 0x10000000000000000\n",
      "x: 1\nx: 2",
      "---\na\n---\nb\n",
      "%YAML 1.2\n%TAG !e! tag:example.com,2000:\n---\n!e!thing {k: v}\n",
      "key: [unterminated",
      "\"bad escape \\q\"",
      "<<: {a: 1}\nb: 2\n",
      "? [complex]\n: key\n",
      "a: *undefined\n",
      "!unknown x\n",
      "- !!binary aGVsbG8=\n- !!binary '***'\n",
      "unicode: [café, 雪, 😀, '', ascii]\n",
      "a: &a [*a]\n",
      "- " + "[".repeat(40) + "]".repeat(40) + "\n",
      "items: [" + "1, ".repeat(200) + "1]\n",
      "text: " + "x".repeat(2000) + "\n",
      "\u00ff\u00fe",
      "a: 1\n---\nb: [\n",
      "%YAML 1.3\n---\nx\n",
      "anchor: &key " + "k".repeat(300) + "\nitems: [{*key : v}, {*key : w}]\n",
    };
    for (int i = 0; i < inputs.length; i++)
      corpus.put(String.format("builtin/%02d", i), inputs[i].getBytes(StandardCharsets.UTF_8));
    corpus.put("builtin/invalid-utf8", new byte[] {'x', ':', ' ', (byte) 0xC3, (byte) 0x28});
    return corpus;
  }

  /** Every regular file under {@code root} whose name ends with {@code suffix}, keyed by path. */
  static Map<String, byte[]> files(Path root, String suffix) throws IOException {
    Map<String, byte[]> corpus = new TreeMap<>();
    Path real = root.toRealPath();
    try (var walk = Files.walk(real)) {
      for (Path file : walk.filter(p -> p.getFileName().toString().endsWith(suffix)).toList())
        corpus.put(real.relativize(file).toString(), Files.readAllBytes(file));
    }
    return corpus;
  }

  // ---- outcomes ----

  static final class Events implements TapeSink {
    final StringBuilder out;

    Events(StringBuilder out) {
      this.out = out;
    }

    public void beginObject(long n) { out.append('{').append(n).append(' '); }
    public void beginArray(long n) { out.append('[').append(n).append(' '); }
    public void end() { out.append("} "); }
    public void key(TapeText key) { out.append("k:").append(key.toJavaString()).append(' '); }
    public void nullValue() { out.append("null "); }
    public void booleanValue(boolean value) { out.append(value).append(' '); }
    public void longValue(long value) { out.append('i').append(value).append(' '); }
    public void unsignedValue(long value) {
      out.append('u').append(Long.toUnsignedString(value)).append(' ');
    }
    public void doubleValue(double value) {
      out.append('f').append(Long.toHexString(Double.doubleToRawLongBits(value))).append(' ');
    }
    public void decimalValue(TapeText value) { out.append('d').append(value.toJavaString()).append(' '); }
    public void stringValue(TapeText value) { out.append("s:").append(value.toJavaString()).append(' '); }
    @Override public void tag(TapeText uri) { out.append('!').append(uri.toJavaString()).append(' '); }
    @Override public void anchor(int id) { out.append('&').append(id).append(' '); }
    @Override public boolean alias(int id) { out.append('*').append(id).append(' '); return true; }
  }

  private static String raw(TapeValue value) {
    try {
      return value.rawText();
    } catch (IllegalStateException none) {
      return "-";
    }
  }

  private static void walk(TapeValue value, StringBuilder out) {
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
        out.append("key(").append(Yaml.keyKind(value, i)).append(' ').append(Yaml.style(key));
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

  /** The exact failure: class and message. */
  static String failure(Throwable failure) {
    return "reject " + failure.getClass().getName() + ": " + failure.getMessage();
  }

  /** Whole-buffer stream: each document, then the same document retained. */
  static String documents(YamlSession session, byte[] input) {
    StringBuilder out = new StringBuilder();
    try (YamlStream documents = session.documents(input)) {
      for (TapeValue root; (root = documents.next()) != null; ) {
        out.append("doc ").append(describe(root)).append('\n');
        try (YamlMessage retained = documents.retain()) {
          out.append("retained ").append(describe(retained.root())).append('\n');
        }
      }
      return out.toString();
    } catch (RuntimeException failure) {
      return out + failure(failure);
    }
  }

  /** Incremental stream: 7-byte feeds, each document read as soon as one is complete. */
  static String chunked(YamlSession session, byte[] input) {
    StringBuilder out = new StringBuilder();
    try (YamlStream stream = session.stream(new YamlStreamOptions(1 << 16, 32))) {
      for (int at = 0; at < input.length; ) {
        int length = Math.min(7, input.length - at);
        int accepted = stream.feed(java.util.Arrays.copyOfRange(input, at, at + length));
        out.append("fed ").append(accepted).append(' ');
        if (accepted == 0) break;
        at += accepted;
      }
      stream.finish();
      for (TapeValue root; (root = stream.next()) != null; )
        out.append("doc ").append(describe(root)).append('\n');
      return out.toString();
    } catch (RuntimeException failure) {
      return out + failure(failure);
    }
  }

  /** The single-document decode with its directives, then a shared-text parse. */
  static String single(YamlSession session, byte[] input) {
    StringBuilder out = new StringBuilder();
    try (YamlMessage message = session.decode(input)) {
      out.append(describe(message.root())).append(' ').append(message.directives());
    } catch (RuntimeException failure) {
      out.append(failure(failure));
    }
    out.append(" | parse ");
    try {
      var sink = new dev.elide.truffledata.tape.JavaSink();
      session.parse(input, sink);
      out.append(sink.result());
    } catch (RuntimeException failure) {
      out.append(failure(failure));
    }
    // Shared-text parse: on the static backend this writes the text arena (session_write_text).
    out.append(" | text ");
    try {
      var sink =
          new dev.elide.truffledata.tape.TruffleSink(
              new dev.elide.truffledata.tape.TruffleSink.KeyCache());
      session.parse(input, sink);
      out.append(sink.result());
    } catch (RuntimeException failure) {
      out.append(failure(failure));
    }
    return out.toString();
  }

  static String outcome(YamlSession session, byte[] input) {
    return documents(session, input)
        + "\n== chunked\n"
        + chunked(session, input)
        + "\n== single\n"
        + single(session, input);
  }

  /** Compares every input under every mode; returns the mismatches. */
  static List<String> compare(
      Function<YamlOptions, YamlSession> baseline,
      Function<YamlOptions, YamlSession> cells,
      Map<String, byte[]> corpus) {
    List<String> mismatches = new ArrayList<>();
    for (var mode : MODES.entrySet()) {
      try (YamlSession expected = baseline.apply(mode.getValue());
          YamlSession actual = cells.apply(mode.getValue())) {
        for (var input : corpus.entrySet()) {
          String want = outcome(expected, input.getValue());
          String got = outcome(actual, input.getValue());
          if (!want.equals(got))
            mismatches.add(
                input.getKey() + " [" + mode.getKey() + "]\n  baseline: " + want + "\n  cells:    " + got);
        }
      }
    }
    return mismatches;
  }

  // ---- typed plans ----

  static final Schema FLAT =
      Schema.of(
          new Field("s", Field.Kind.STRING, false, false),
          Field.requiredLong("n"),
          new Field("f", Field.Kind.FLOAT64, false, false),
          new Field("b", Field.Kind.BOOLEAN, false, false));

  static final String[] TYPED = {
    "s: 1.0\nn: 0x1f\nf: .inf\nb: true\n",
    "s: x\nn: 7\nf: 1.5\nb: false\n",
    "s: x\nn: nope\nf: 1.5\nb: false\n",
    "s: x\nf: 1.5\nb: false\n",
    "s: x\nn: 99999999999999999999\nf: 1\nb: true\n",
    "s: [\n",
    "n: 1\nn: 2\ns: a\nf: 0\nb: true\n",
    "- not a record\n",
    "s: 😀\nn: -1\nf: -0.0\nb: true\nextra: 1\n",
  };

  static String typed(YamlPlan plan, byte[] input) {
    try (TypedRecord row = plan.decode(input)) {
      return row.stringValue(0)
          + ","
          + row.longValue(1)
          + ","
          + Long.toHexString(Double.doubleToRawLongBits(row.doubleValue(2)))
          + ","
          + row.booleanValue(3);
    } catch (RuntimeException failure) {
      return failure(failure);
    }
  }

  /** Typed plan decode on both routes; also checks the cells plan is the cells backend. */
  static List<String> compareTyped(
      Function<YamlOptions, YamlSession> baseline,
      Function<YamlOptions, YamlSession> cells,
      Class<?> cellsPlan) {
    List<String> mismatches = new ArrayList<>();
    for (var mode : MODES.entrySet()) {
      try (YamlSession expectedSession = baseline.apply(mode.getValue());
          YamlSession actualSession = cells.apply(mode.getValue());
          YamlPlan expected = expectedSession.compile(FLAT, Map.of());
          YamlPlan actual = actualSession.compile(FLAT, Map.of())) {
        if (!cellsPlan.isInstance(actual.backend()) || cellsPlan.isInstance(expected.backend()))
          mismatches.add("plan routes: " + expected.backend() + " / " + actual.backend());
        for (String text : TYPED) {
          byte[] input = text.getBytes(StandardCharsets.UTF_8);
          String want = typed(expected, input), got = typed(actual, input);
          if (!want.equals(got))
            mismatches.add(text + " [" + mode.getKey() + "]\n  baseline: " + want + "\n  cells:    " + got);
        }
      }
    }
    return mismatches;
  }

  /** The backend a session runs on (the route check). */
  static Object backend(YamlSession session) {
    try {
      var field = dev.elide.truffledata.tape.TapeSession.class.getDeclaredField("backend");
      field.setAccessible(true);
      return java.util.Objects.requireNonNull(field.get(session));
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
