package dev.elide.truffledata.yaml.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.tape.JavaSink;
import dev.elide.truffledata.tape.TapeValue;
import dev.elide.truffledata.yaml.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.CoreSchema;

/** Engine 3.2 with explicit YAML 1.2 core resolution, duplicate rejection and merges disabled. */
final class SnakeYamlDifferentialTest {
  private static Load oracle() {
    return new Load(LoadSettings.builder().setSchema(new CoreSchema() {
          @Override public org.snakeyaml.engine.v2.resolver.ScalarResolver getScalarResolver() {
            return new org.snakeyaml.engine.v2.resolver.CoreScalarResolver(false);
          }
        })
        .setAllowNonScalarKeys(false).setAllowDuplicateKeys(false).setMaxAliasesForCollections(10000).build());
  }

  static Path workspace(String path) {
    String root = System.getenv("TEST_SRCDIR");
    return root == null ? Path.of(path)
        : Path.of(root, System.getenv("TEST_WORKSPACE"), path);
  }

  private static Path suite() {
    String override = System.getProperty("yaml.suite");
    return override != null ? Path.of(override)
        : Path.of(System.getenv("TEST_SRCDIR"), "+http_archive+yaml_test_suite");
  }

  private static Object normalize(Object value) {
    if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
    if (value instanceof Number number) {
      if (number instanceof Double d && !Double.isFinite(d)) return d.toString();
      return new BigDecimal(number.toString()).stripTrailingZeros();
    }
    if (value instanceof Set<?> set) {
      Map<Object, Object> map = new LinkedHashMap<>();
      for (Object key : set) map.put(key, null);
      return normalize(map);
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      for (var entry : map.entrySet()) {
        Object key = entry.getKey();
        // The tape's documented scalar-key contract excludes collection keys (D2).
        if (key instanceof Map<?, ?> || key instanceof List<?>)
          throw new IllegalArgumentException("Collection mapping key is outside the tape contract");
        Object normalized = normalize(key);
        String name = key == null ? "" : normalized instanceof BigDecimal decimal ? decimal.toPlainString()
            : Objects.toString(normalized);
        if (result.containsKey(name)) throw new IllegalArgumentException("Normalized duplicate key");
        result.put(name, normalize(entry.getValue()));
      }
      return result;
    }
    if (value instanceof List<?> list) {
      List<Object> result = new ArrayList<>();
      for (Object item : list) result.add(normalize(item));
      return result;
    }
    return value;
  }

  // D2: the generic tape preserves unknown tags without constructing application-specific types.
  // These exact suite cases must remain accepted by us and rejected by Engine's default loader.
  private static final Set<String> PRESERVED_TAGS = Set.of(
      "5TYM", "6CK3", "6WLZ", "7FWL", "9WXW", "C4HZ", "CC74", "CUP7",
      "J7PZ", "M5C3", "P76L", "UGM3", "Z67P", "Z9M4");

  private static Object normalizeActual(TapeValue value, Object materialized) {
    if ("tag:yaml.org,2002:binary".equals(value.tagUri()))
      return Base64.getEncoder().encodeToString(Yaml.binary(value));
    if (materialized instanceof Map<?, ?> map) {
      Map<String, Object> normalized = new LinkedHashMap<>();
      for (long i = 0; i < value.size(); i++) {
        String name = value.keyAt(i).stringValue();
        normalized.put(name, normalizeActual(value.memberAt(i), map.get(name)));
      }
      return normalized;
    }
    if (materialized instanceof List<?> list) {
      List<Object> normalized = new ArrayList<>();
      for (int i = 0; i < list.size(); i++) normalized.add(normalizeActual(value.element(i), list.get(i)));
      return normalized;
    }
    return normalize(materialized);
  }

  private record Outcome(Object value, boolean rejected) {}
  private static Outcome expected(Load loader, String source) {
    try {
      List<Object> values = new ArrayList<>();
      for (Object value : loader.loadAllFromString(source)) values.add(normalize(value));
      return new Outcome(values, false);
    } catch (RuntimeException | StackOverflowError failure) {
      return new Outcome(failure.getMessage(), true);
    }
  }
  private static Outcome actual(YamlSession session, String source) {
    try (YamlStream stream = session.documents(SvmAcceptance.bytes(source))) {
      List<Object> values = new ArrayList<>();
      for (TapeValue value; (value = stream.next()) != null;) {
        JavaSink sink = new JavaSink(); value.export(sink); values.add(normalizeActual(value, sink.result()));
      }
      return new Outcome(values, false);
    } catch (RuntimeException failure) {
      return new Outcome(failure.getMessage(), true);
    }
  }
  private static String mismatch(YamlSession session, Load loader, String name, String source) {
    Outcome expected = expected(loader, source), actual = actual(session, source);
    if (PRESERVED_TAGS.contains(name)) {
      assertTrue(expected.rejected && Objects.toString(expected.value).contains("constructor for the tag"), name);
      assertFalse(actual.rejected, name + ": " + actual.value);
      return null;
    }
    if (expected.rejected && actual.rejected) return null;
    if (expected.rejected == actual.rejected && Objects.equals(expected.value, actual.value)) return null;
    return name + ": expected " + expected + "; actual " + actual;
  }

  @Test void seededDocumentsAndProbeFixtures() throws Exception {
    Load loader = oracle();
    try (YamlSession session = YamlJvm.openSession(YamlIntegrationTest.library())) {
      YamlFuzz fuzz = new YamlFuzz(0x59414d4cL);
      for (int i = 0; i < 5000; i++) {
        String source = fuzz.next(i);
        assertNull(mismatch(session, loader, "fuzz/" + i, source), source);
      }
      try (var files = Files.list(workspace("yaml/native/testdata"))) {
        for (Path input : files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList())
          assertNull(mismatch(session, loader, input.toString(), Files.readString(input)));
      }
    }
  }

  @Test void pinnedSuite() throws Exception {
    Set<String> excluded = new HashSet<>();
    for (String line : Files.readAllLines(workspace("yaml/tests/snakeyaml_engine_excluded.txt")))
      if (!line.isBlank() && !line.startsWith("#")) excluded.add(line.split("\t", 2)[0]);
    assertEquals(45, excluded.size());
    List<String> failures = new ArrayList<>();
    Load loader = oracle(); Path root = suite().toRealPath(); int cases = 0;
    try (YamlSession session = YamlJvm.openSession(YamlIntegrationTest.library());
        var files = Files.walk(root)) {
      for (Path input : files.filter(p -> p.getFileName().toString().equals("in.yaml"))
          .sorted().toList()) {
        String name = root.relativize(input.getParent()).toString();
        if (name.startsWith("name/") || name.startsWith("tags/")) continue;
        cases++;
        if (excluded.contains(name)) continue;
        String failure = mismatch(session, loader, name, Files.readString(input));
        if (failure != null) failures.add(failure);
      }
    }
    assertEquals(402, cases);
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  @Test void documentedNormalizations() {
    try (YamlSession session = YamlJvm.openSession(YamlIntegrationTest.library())) {
      assertNull(mismatch(session, oracle(), "normalizations",
          "12: integer-key\ntrue: bool-key\nnull: null-key\nx: 18446744073709551615\n"
          + "y: [.nan, .inf, -.inf]\nbinary: !!binary |\n  SGVs\n  bG8=\ndate: 2001-12-14\n<<: {ordinary: key}\n"));
      assertThrows(IllegalArgumentException.class,
          () -> session.decode(SvmAcceptance.bytes("? [collection, key]\n: rejected\n")));
      assertNotNull(new Load(LoadSettings.builder().setSchema(new CoreSchema()).setAllowNonScalarKeys(true).build())
          .loadFromString("? [collection, key]\n: rejected\n"));
    }
  }
}
