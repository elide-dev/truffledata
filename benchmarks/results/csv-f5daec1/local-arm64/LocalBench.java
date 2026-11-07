import dev.elide.truffledata.comparison.CsvFixture;
import dev.elide.truffledata.csv.CsvBench;
import dev.elide.truffledata.csv.CsvFormat;
import dev.elide.truffledata.csv.CsvLimits;
import dev.elide.truffledata.csv.kernel.*;
import dev.elide.truffledata.csv.kernel.vector.VectorIndexer;
import java.nio.file.*;
import java.util.*;

/** Local (Apple arm64) advisory driver: index and scan GB/s for the Java kernels on the JVM. */
public class LocalBench {
  static final CsvLimits BIG = new CsvLimits(Integer.MAX_VALUE - 64, 1 << 20, 4096, Integer.MAX_VALUE);
  static volatile long sink;

  interface Op { long run(byte[] in); }

  static double[] measure(byte[] in, Op op) {
    long expected = op.run(in);
    long warm = System.nanoTime() + 2_000_000_000L;
    for (int i = 0; i < 3 || System.nanoTime() < warm; i++) if (op.run(in) != expected) throw new AssertionError();
    double[] r = new double[5];
    for (int s = 0; s < 5; s++) {
      long start = System.nanoTime(), el, ops = 0;
      do { sink = op.run(in); ops++; el = System.nanoTime() - start; } while (el < 300_000_000L);
      r[s] = (double) (ops * in.length) / el; // GB/s
    }
    return r;
  }

  public static void main(String[] a) throws Exception {
    String out = a[0];
    for (String ds : CsvFixture.DATASETS) {
      for (int size : new int[] {1 << 20, 128 << 20}) {
        byte[] in = CsvFixture.generate(ds, size);
        if (size > (1 << 20)) Files.write(Path.of(out, ds + ".csv"), in);
        Map<String, Op> ops = new LinkedHashMap<>();
        for (CsvIndexer ix : List.of(new ScalarIndexer((byte) ',', (byte) '"', true), new TableIndexer((byte) ',', (byte) '"', true),
            new SwarIndexer((byte) ',', (byte) '"', true), new VectorIndexer((byte) ',', (byte) '"', true))) {
          int[] buf = new int[1 << 16];
          ops.put("index " + ix.name(), input -> {
            long e = 0; ix.carry(0);
            for (int p = 0; p < input.length;) { int n = CsvIndex.indexable(input.length - p, buf.length); e += ix.index(input, p, n, buf); p += n; }
            return e;
          });
          IndexedScanner sc = new IndexedScanner(CsvFormat.RFC4180, BIG, ix, 1 << 16);
          ops.put("scan kernel-" + ix.name(), input -> {
            long[] c = new long[2];
            sc.scan(input, 0, input.length, true, (s, n, st, en) -> { c[0]++; c[1] += n; return true; });
            return c[0] * 1_000_003L + c[1];
          });
        }
        ops.put("scan truffledata-scanner", input -> {
          long[] c = new long[2];
          CsvBench.scan(CsvFormat.RFC4180, BIG, input, 0, (s, n, st, en) -> { c[0]++; c[1] += n; });
          return c[0] * 1_000_003L + c[1];
        });
        for (var e : ops.entrySet()) {
          double[] r = measure(in, e.getValue());
          Arrays.sort(r);
          System.out.printf(Locale.ROOT, "{\"host\":\"apple-m5-max\",\"runtime\":\"jvm\",\"dataset\":\"%s\",\"bytes\":%d,\"cell\":\"%s\",\"gb_per_s\":%s}%n",
              ds, in.length, e.getKey(), Arrays.toString(r));
        }
      }
    }
  }
}
