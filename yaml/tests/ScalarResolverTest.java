package dev.elide.truffledata.yaml;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.tape.TapeSink;
import dev.elide.truffledata.tape.TapeText;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Java resolver against the native decoder: values, mapping-key text and rejections. */
final class ScalarResolverTest {
  static Path library() {
    String override = System.getProperty("yaml.library");
    return override != null
        ? Path.of(override)
        : Path.of(
            System.getenv("TEST_SRCDIR"),
            System.getenv("TEST_WORKSPACE"),
            "yaml/native/yaml_shared.so");
  }

  private static final class Events implements TapeSink {
    final List<String> values = new ArrayList<>();

    public void beginObject(long n) {}

    public void beginArray(long n) {}

    public void end() {}

    public void key(TapeText key) {
      values.add("key:" + key.toJavaString());
    }

    public void nullValue() {
      values.add("null");
    }

    public void booleanValue(boolean value) {
      values.add("bool:" + value);
    }

    public void longValue(long value) {
      values.add("i64:" + value);
    }

    public void unsignedValue(long value) {
      values.add("u64:" + Long.toUnsignedString(value));
    }

    public void doubleValue(double value) {
      values.add("f64:" + Long.toHexString(Double.doubleToRawLongBits(value)));
    }

    public void decimalValue(TapeText value) {
      values.add("decimal:" + value.toJavaString());
    }

    public void stringValue(TapeText value) {
      values.add("string:" + value.toJavaString());
    }
  }

  private static String event(ScalarResolver.Scalar r) {
    return switch (r.kind()) {
      case NULL -> "null";
      case BOOL -> "bool:" + (r.bits() != 0);
      case I64 -> "i64:" + r.bits();
      case U64 -> "u64:" + Long.toUnsignedString(r.bits());
      case F64 -> "f64:" + Long.toHexString(r.bits());
      case DECIMAL -> "decimal:" + r.text();
      case STRING -> "string:" + r.text();
    };
  }

  /** Native events, or "!" + message when the document is rejected. */
  private static String nativeEvents(YamlSession session, String doc) {
    try {
      Events sink = new Events();
      session.parse(doc.getBytes(StandardCharsets.UTF_8), sink);
      return String.join(" ", sink.values);
    } catch (IllegalArgumentException failure) {
      return "!" + failure.getMessage();
    }
  }

  private static String javaValue(String text, YamlSchema schema, String tag) {
    try {
      return event(ScalarResolver.resolve(text, true, schema, ScalarResolver.classify(tag)));
    } catch (ScalarResolver.ResolveException failure) {
      return "!" + failure.getMessage();
    }
  }

  private static String javaKey(String text, YamlSchema schema) {
    try {
      ScalarResolver.Scalar r = ScalarResolver.resolvePlain(text, schema);
      return "key:" + ScalarResolver.keyText(r) + " " + javaValue("0", schema, null);
    } catch (ScalarResolver.ResolveException failure) {
      return "!" + failure.getMessage();
    }
  }

  private static Set<String> corpus() {
    Set<String> out = new LinkedHashSet<>();
    for (String s :
        new String[] {
          "", "~", "null", "Null", "NULL", "nULL", "true", "True", "TRUE", "tRUE", "false", "False",
          "y", "Y", "n", "N", "yes", "Yes", "YES", "no", "No", "on", "On", "ON", "off", "Off", "OFF",
          "0", "-0", "+0", "00", "007", "012", "-012", "0o17", "0o", "0o8", "0x1F", "0xff", "0x",
          "0b101", "-0b101", "0b1_0", "0x_1", "1_000", "-1_000", "1__0", "_1", "190:20:30",
          "1:20", "-1:20", "1:60", "1:5:7", "190:20:30.15", "1:20.5", "9223372036854775807",
          "9223372036854775808", "-9223372036854775808", "-9223372036854775809",
          "18446744073709551615", "18446744073709551616", "123456789012345678901234567890",
          "-123456789012345678901234567890", "0x10000000000000000", "0o2000000000000000000000",
          "0xffffffffffffffff", "0x8000000000000000", "1.5", "-1.5", "+1.5", "1.", ".5", "-.5",
          "1e3", "1E3", "1e+3", "1e-3", "1.e3", ".e3", "1e", "1.5e", "1e400", "-1e400", "0009e400", "+00.5e999", "-000.e999", "00e999", "1e-400",
          "-1e-400", "0.1", "0.30000000000000004", "123456789.123456789", "1e15", "1e16", "1e14",
          "1e21", "1e-5", "1e-4", "0.0001", "0.00001", "5e-324", "1.7976931348623157e308",
          "100000000000000000000", "1_0.5", "1.0_5", "1.5e+3", "1.5e3", ".inf", "-.inf", "+.inf",
          ".Inf", ".INF", ".iNF", ".nan", ".NaN", ".NAN", "-.nan", "nan", "inf", "2001-12-14",
          "2001-12-14t21:59:43.10-05:00", "2001-12-14 21:59:43.10 -5", "2001-12-15T02:59:43.1Z",
          "2001-1-1", "2001-1-1T1:00:00", "2001-12-14 21:59:43.10 Z", "abc", "a1", "1a", "-",
          "+", ".", "..", "1..2", "0x1g", "--1", "+-1", "1.5.5", "12:30:45:61", "007.5"
        }) out.add(s);
    Random random = new Random(41);
    String alphabet = "0123456789+-._:eExobn";
    while (out.size() < 4000) {
      StringBuilder s = new StringBuilder();
      int n = 1 + random.nextInt(8);
      for (int i = 0; i < n; i++) s.append(alphabet.charAt(random.nextInt(alphabet.length())));
      out.add(s.toString());
    }
    return out;
  }

  /** Plain scalars that are scalars in a block sequence and as block mapping keys. */
  private static boolean blockPlain(String s) {
    if (s.isEmpty()) return true;
    if (s.endsWith(":") || s.contains(": ") || s.startsWith("- ") || s.equals("-")) return false;
    if (s.startsWith("? ") || s.equals("?") || s.startsWith(":")) return false;
    return true;
  }

  @Test
  void valuesAndKeysMatchNative() {
    int compared = 0;
    for (YamlSchema schema : YamlSchema.values()) {
      YamlOptions options = YamlOptions.DEFAULT.withSchema(schema);
      try (YamlSession session = YamlJvm.openSession(library(), options)) {
        for (String text : corpus()) {
          if (!blockPlain(text)) continue;
          String value = nativeEvents(session, "- " + text + "\n");
          // Syntax errors that are not resolution errors are the parser's, not the resolver's.
          if (value.startsWith("!") && !value.contains("JSON schema") && !value.contains("limit"))
            continue;
          assertEquals(normalize(value), normalize(javaValue(text, schema, null)), schema + " " + text);
          String key = nativeEvents(session, text + ": 0\n");
          if (key.startsWith("!") && !key.contains("JSON schema") && !key.contains("limit"))
            continue;
          assertEquals(normalize(key), normalize(javaKey(text, schema)), schema + " key " + text);
          compared++;
        }
      }
    }
    assertTrue(compared > 8000, "compared " + compared);
  }

  @Test
  void taggedScalarsMatchNative() {
    String[] tags = {"!!str", "!!int", "!!float", "!!bool", "!!null", "!", "!local", "!!seq"};
    for (YamlSchema schema : YamlSchema.values()) {
      if (schema == YamlSchema.JSON) continue; // JSON rejects tags as unknown
      try (YamlSession session =
          YamlJvm.openSession(library(), YamlOptions.DEFAULT.withSchema(schema))) {
        for (String text : corpus()) {
          if (!blockPlain(text) || text.isEmpty()) continue;
          for (String tag : tags) {
            String value = nativeEvents(session, "- " + tag + " " + text + "\n");
            if (value.startsWith("!") && !value.contains(" tag ") && !value.contains("limit"))
              continue;
            String uri =
                tag.startsWith("!!") ? ScalarResolver.CORE_PREFIX + tag.substring(2) : tag;
            assertEquals(
                normalize(value), normalize(javaValue(text, schema, uri)),
                schema + " " + tag + " " + text);
          }
        }
      }
    }
  }

  /** Rejections compare by kind of failure, not by position suffix. */
  private static String normalize(String outcome) {
    if (!outcome.startsWith("!")) return outcome;
    if (outcome.contains("limit")) return "!limit";
    int at = outcome.indexOf(" at line ");
    String message = at < 0 ? outcome : outcome.substring(0, at);
    return message.replace("YAML syntax error: ", "");
  }

  @Test
  void longZeroPrefixesNormalizeInLinearTime() {
    String token = "0".repeat(2_000_000) + "1e400";
    long start = System.nanoTime();
    ScalarResolver.Scalar r = ScalarResolver.resolvePlain(token, YamlSchema.CORE);
    assertEquals(ScalarResolver.Kind.DECIMAL, r.kind());
    assertEquals("1e400", r.text());
    assertTrue(System.nanoTime() - start < 5_000_000_000L, "quadratic normalization");
  }

  @Test
  void formatsKeysLikeSimdjson() {
    String[][] cases = {
      {"1.0", "1"}, {"-0.0", "-0"}, {"0.5", "0.5"}, {"1e14", "100000000000000"},
      {"1e15", "1e+15"}, {"1e21", "1e+21"}, {"1.5e300", "1.5e+300"}, {"1e-4", "0.0001"},
      {"1e-5", "1e-05"}, {"1.25e-7", "1.25e-07"}, {"123.456", "123.456"}
    };
    for (String[] c : cases)
      assertEquals(c[1], ScalarResolver.keyText(ScalarResolver.resolvePlain(c[0], YamlSchema.CORE)), c[0]);
  }

  @Test
  void plainIsStringOnlyForStrings() {
    assertTrue(ScalarResolver.plainIsString("hello", YamlSchema.CORE));
    assertFalse(ScalarResolver.plainIsString("123", YamlSchema.CORE));
    assertFalse(ScalarResolver.plainIsString("true", YamlSchema.CORE));
    assertFalse(ScalarResolver.plainIsString("0o7", YamlSchema.CORE));
    assertFalse(ScalarResolver.plainIsString("", YamlSchema.CORE));
    assertTrue(ScalarResolver.plainIsString("yes", YamlSchema.CORE));
    assertFalse(ScalarResolver.plainIsString("yes", YamlSchema.YAML_1_1));
    assertFalse(ScalarResolver.plainIsString("2024-01-01", YamlSchema.YAML_1_1));
    assertTrue(ScalarResolver.plainIsString("2024-01-01", YamlSchema.CORE));
    assertFalse(ScalarResolver.plainIsString("x", YamlSchema.JSON));
    assertTrue(ScalarResolver.plainIsString("123", YamlSchema.FAILSAFE));
  }
}
