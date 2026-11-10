package dev.elide.truffledata.yaml;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.yaml.generated.YamlFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The YAML cells route on HotSpot ({@code truffledata.yaml.cells}): the route the flag selects,
 * exact parity of the cells backend with the FFM backend over the YAML corpus (testdata, the
 * session fuzz corpus, the yaml-test-suite and built-in edge cases), typed plans, and the
 * specialized-call rule. Runs with the flag on ({@code jvm}) and off ({@code jvm_off}); parity
 * constructs both backends directly either way.
 */
final class CellsJvmTest {
  static Path runfile(String path) {
    return Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), path);
  }

  static Path library() {
    return runfile("yaml/native/yaml_shared.so");
  }

  /** A session on the FFM backend, wrapped in the cells backend when {@code cells}. */
  static YamlSession open(YamlOptions options, boolean cells) {
    Arena arena = Arena.ofConfined();
    JvmSessionBackend backend;
    try {
      backend =
          new JvmSessionBackend(
              options,
              new YamlFFM.LibraryBinding(SymbolLookup.libraryLookup(library().toAbsolutePath(), arena)),
              arena);
    } catch (RuntimeException | Error failure) {
      arena.close();
      throw failure;
    }
    return new YamlSession(options, cells ? CellsRoute.jvm(backend, library()) : backend);
  }

  @Test
  void flagSelectsRoute() {
    boolean expected = Boolean.getBoolean("truffledata.yaml.cells.expected");
    assertEquals(expected, CellsRoute.ENABLED);
    try (YamlSession session = YamlJvm.openSession(library())) {
      assertEquals(
          expected ? CellsJvmSessionBackend.class : JvmSessionBackend.class,
          CellsParity.backend(session).getClass());
      try (YamlPlan plan = session.compile(CellsParity.FLAT, Map.of())) {
        assertEquals(
            expected ? CellsJvmPlanBackend.class : JvmPlanBackend.class,
            plan.backend().getClass());
      }
    }
  }

  @Test
  void wrongLibraryIsRejectedBeforeCells() {
    // The production fingerprint check runs first: the other ABI class never reaches the cells.
    Path other = runfile("yaml/native/other_abi_class.so");
    assertThrows(IllegalStateException.class, () -> YamlJvm.openSession(other));
  }

  static Map<String, byte[]> corpus() throws Exception {
    Map<String, byte[]> corpus = new TreeMap<>(CellsParity.builtIn());
    corpus.putAll(CellsParity.files(runfile("yaml/native/testdata"), ".yaml"));
    Path session = runfile("yaml/native/corpus/session");
    if (Files.exists(session)) corpus.putAll(CellsParity.files(session, ""));
    Path suite = Path.of(System.getenv("TEST_SRCDIR"), "+http_archive+yaml_test_suite");
    Map<String, byte[]> cases = CellsParity.files(suite, "in.yaml");
    cases.keySet().removeIf(name -> name.startsWith("name/") || name.startsWith("tags/"));
    assertEquals(402, cases.size());
    corpus.putAll(cases);
    return corpus;
  }

  @Test
  void parity() throws Exception {
    Map<String, byte[]> corpus = corpus();
    List<String> mismatches =
        CellsParity.compare(options -> open(options, false), options -> open(options, true), corpus);
    assertTrue(
        mismatches.isEmpty(),
        mismatches.size() + " mismatches:\n" + String.join("\n", mismatches));
  }

  @Test
  void typedParity() {
    List<String> mismatches =
        CellsParity.compareTyped(
            options -> open(options, false), options -> open(options, true), CellsJvmPlanBackend.class);
    assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));
  }

  /** Every scalar cell call site holds the concrete *_Cell and uses executeInt/executeLong. */
  @Test
  void scalarCellsUseSpecializedCalls() throws Exception {
    Pattern generic = Pattern.compile("\\.execute\\(");
    Pattern typed = Pattern.compile("\\.execute(Int|Long)\\(");
    int calls = 0;
    for (String name :
        List.of(
            "CellsSvmSessionBackend", "CellsSvmPlanBackend", "CellsJvmSessionBackend", "CellsJvmPlanBackend")) {
      String source =
          Files.readString(
              runfile("yaml/cells/src/main/java/dev/elide/truffledata/yaml/" + name + ".java"));
      assertFalse(generic.matcher(source).find(), name + " calls a cell through execute(Object[])");
      assertFalse(source.contains("catch (UnexpectedResultException"), name);
      var matcher = typed.matcher(source);
      while (matcher.find()) calls++;
    }
    // session_decode, session_write_text, session_retain, stream_feed, stream_next (SVM: 5 sites),
    // typed_decode (SVM 1), JVM: decode, retain, feed x2, next, typed_decode (6).
    assertEquals(12, calls);
  }
}
