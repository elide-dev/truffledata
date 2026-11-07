import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.LongSupplier;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.ScalarEvent;

/** YAML probe (temporary): SnakeYAML Engine 3.2, SnakeYAML 2.7, Jackson YAML 2.22.4 on the JVM. */
public final class JProbe {
  static volatile long sink;

  public static void main(String[] args) throws Exception {
    if (args[0].equals("events-batch")) {
      // args: list-file out-dir ; list lines "id<TAB>path"
      Path out = Path.of(args[2]);
      Files.createDirectories(out);
      for (String line : Files.readAllLines(Path.of(args[1]))) {
        String[] p = line.split("\t");
        String name = p[0].replace('/', '_');
        try {
          String src = Files.readString(Path.of(p[1]), StandardCharsets.UTF_8);
          StringBuilder sb = new StringBuilder();
          LoadSettings settings = LoadSettings.builder().build();
          for (Event e : new Parse(settings).parseString(src)) sb.append(e).append('\n');
          Files.writeString(out.resolve(name + ".out"), sb.toString());
        } catch (Throwable t) {
          Files.writeString(out.resolve(name + ".err"), String.valueOf(t));
        }
      }
      return;
    }
    int iters = Integer.parseInt(args[1]);
    for (int a = 2; a < args.length; a++) {
      Path path = Path.of(args[a]);
      String src = Files.readString(path, StandardCharsets.UTF_8);
      byte[] bytes = Files.readAllBytes(path);
      String file = path.getFileName().toString();
      int it = Math.max(1, (int) (iters * 200000.0 / (bytes.length + 1000)));
      LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).setCodePointLimit(Integer.MAX_VALUE).build();
      bench("snakeyaml-engine(scan)", file, bytes.length, it, () -> {
        long n = 0;
        for (Event e : new Parse(settings).parseString(src)) {
          n++;
          if (e instanceof ScalarEvent s) n += s.getValue().length();
        }
        return n;
      });
      bench("snakeyaml-engine(load)", file, bytes.length, it, () -> {
        long n = 0;
        for (Object o : new Load(settings).loadAllFromString(src)) n += o == null ? 0 : 1;
        return n;
      });
      org.yaml.snakeyaml.LoaderOptions lo = new org.yaml.snakeyaml.LoaderOptions();
      lo.setCodePointLimit(Integer.MAX_VALUE);
      bench("snakeyaml-1.1(load)", file, bytes.length, it, () -> {
        long n = 0;
        for (Object o : new org.yaml.snakeyaml.Yaml(lo).loadAll(src)) n += o == null ? 0 : 1;
        return n;
      });
      ObjectMapper mapper = new ObjectMapper(YAMLFactory.builder().loaderOptions(lo).build());
      bench("jackson-yaml(tree)", file, bytes.length, it, () -> {
        long n = 0;
        try (MappingIterator<JsonNode> m = mapper.readerFor(JsonNode.class).readValues(bytes)) {
          while (m.hasNext()) n += m.next().size();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        return n;
      });
    }
  }

  static void bench(String name, String file, int bytes, int iters, LongSupplier f) {
    for (int w = 0; w < 3; w++) for (int i = 0; i < iters; i++) sink += f.getAsLong();  // warm-up
    double best = Double.MAX_VALUE;
    for (int round = 0; round < 7; round++) {
      long t0 = System.nanoTime();
      for (int i = 0; i < iters; i++) sink += f.getAsLong();
      double ns = (System.nanoTime() - t0) / (double) iters;
      best = Math.min(best, ns);
    }
    System.out.printf("%-22s %-20s %9d B %12.0f ns %8.1f MB/s%n", name, file, bytes, best, bytes / best * 1e3);
  }
}
