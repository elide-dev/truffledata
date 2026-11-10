package dev.elide.truffledata.yaml;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Native Image built with {@code -Dtruffledata.yaml.cells=true} and {@link YamlFeature}: the route
 * check, the shared native acceptance on the cells route ({@code YamlSession.open()}), and exact
 * parity of the cells backend with the static backend over the built-in inputs and the corpus
 * directories given as arguments (testdata, session fuzz corpus, yaml-test-suite).
 */
public final class CellsAcceptance {
  private CellsAcceptance() {}

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    require(YamlSvm.cellsRoute(), "image was not built with the cells route");
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
            options -> new YamlSession(options, new CellsSvmSessionBackend(options)),
            corpus);
    require(mismatches.isEmpty(), mismatches.size() + " mismatches:\n" + String.join("\n", mismatches));
    mismatches =
        CellsParity.compareTyped(
            options -> new YamlSession(options, new SvmSessionBackend(options)),
            options -> new YamlSession(options, new CellsSvmSessionBackend(options)),
            CellsSvmPlanBackend.class);
    require(mismatches.isEmpty(), String.join("\n", mismatches));
    System.out.println(
        "YAML cells route: acceptance and exact parity over " + corpus.size() + " inputs x "
            + CellsParity.MODES.size() + " modes passed");
  }
}
