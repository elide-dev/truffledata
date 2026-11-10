package dev.elide.truffledata.yaml;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Native Image built with {@code -Dtruffledata.yaml.cells=true} and {@link YamlFeature}: the route
 * check, the shared native acceptance on the cells route ({@code YamlSession.open()}), and exact
 * parity of the cells backend with the static backend over the built-in inputs and the corpus
 * directories given as arguments (testdata, session fuzz corpus, yaml-test-suite), typed plans and
 * results read after their session and plan are closed.
 */
public final class CellsAcceptance {
  private CellsAcceptance() {}

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    // The route check: plans compiled through the public entries are the cells route's, so the
    // session YamlSession.open() returns and YamlSvm.compile both took the build-time route.
    try (YamlSession session = YamlSession.open();
        YamlPlan viaSession = session.compile(CellsParity.FLAT, Map.of());
        YamlPlan direct = YamlSvm.compile(CellsParity.FLAT, Map.of())) {
      require(
          viaSession.backend() instanceof CellsSvmPlanBackend
              && direct.backend() instanceof CellsSvmPlanBackend,
          "image was not built with the cells route");
    }
    dev.elide.truffledata.yaml.tests.SvmAcceptance.main(new String[0]);
    Map<String, byte[]> corpus = new TreeMap<>(CellsParity.builtIn());
    for (String dir : args) {
      Map<String, byte[]> files = CellsParity.files(Path.of(dir), dir.contains("suite") ? "in.yaml" : "");
      files.keySet().removeIf(name -> name.startsWith("name/") || name.startsWith("tags/"));
      for (var file : files.entrySet()) corpus.put(dir + "/" + file.getKey(), file.getValue());
    }
    List<String> mismatches =
        CellsParity.compare(
            options -> new YamlSession(options, new SvmSessionBackend(options)),
            options -> new YamlSession(options, new CellsSvmSessionBackend(CellsSvm.image(), options)),
            corpus);
    require(mismatches.isEmpty(), mismatches.size() + " mismatches:\n" + String.join("\n", mismatches));
    mismatches =
        CellsParity.compareTyped(
            options -> new YamlSession(options, new SvmSessionBackend(options)),
            options -> new YamlSession(options, new CellsSvmSessionBackend(CellsSvm.image(), options)),
            CellsSvmPlanBackend.class);
    require(mismatches.isEmpty(), String.join("\n", mismatches));
    String expected =
        CellsParity.lifetimes(options -> new YamlSession(options, new SvmSessionBackend(options)));
    String actual =
        CellsParity.lifetimes(
            options -> new YamlSession(options, new CellsSvmSessionBackend(CellsSvm.image(), options)));
    require(expected.contains("core: {"), "baseline results did not outlive their session: " + expected);
    require(expected.equals(actual), "lifetimes\n  baseline: " + expected + "\n  cells:    " + actual);
    // The image's cells are shared by every session on every thread (CellsSvm): the same exact
    // parity over the built-in inputs from four threads at once, each with its own sessions.
    Map<String, byte[]> builtIn = CellsParity.builtIn();
    List<String> concurrent = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    Thread[] threads = new Thread[4];
    for (int t = 0; t < threads.length; t++) {
      threads[t] =
          new Thread(
              () -> {
                try {
                  for (int round = 0; round < 3; round++)
                    concurrent.addAll(
                        CellsParity.compare(
                            options -> new YamlSession(options, new SvmSessionBackend(options)),
                            options ->
                                new YamlSession(
                                    options, new CellsSvmSessionBackend(CellsSvm.image(), options)),
                            builtIn));
                } catch (Throwable failure) {
                  concurrent.add("thread failed: " + failure);
                }
              });
      threads[t].start();
    }
    for (Thread thread : threads) thread.join();
    require(concurrent.isEmpty(), "concurrent parity:\n" + String.join("\n", concurrent));
    require(
        CellsSvm.image() == CellsSvm.image() && CellsSvm.image().decode == CellsSvm.image().decode,
        "the image's cells are one shared set");
    System.out.println(
        "YAML cells route: acceptance and exact parity over " + corpus.size() + " inputs x "
            + CellsParity.MODES.size() + " modes passed; shared cells from "
            + threads.length + " threads");
  }
}
