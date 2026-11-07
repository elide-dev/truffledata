package dev.elide.truffledata.comparison;

import org.jspecify.annotations.NullMarked;
import dev.elide.truffledata.csv.CsvIndexing;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.jvm.FfmIndexer;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * JVM entry point: native kernels load csv_shared.so (system property {@code
 * truffledata.csv.library}) through FFM.
 */
@NullMarked
public final class CsvComparisonJvm {
  private CsvComparisonJvm() {}

  public static void main(String[] args) throws Exception {
    String property = System.getProperty("truffledata.csv.library", "");
    String srcdir = System.getenv("TEST_SRCDIR");
    String library =
        !property.isEmpty() || srcdir == null
            ? property
            : Path.of(srcdir, System.getenv("TEST_WORKSPACE"), "csv/native/csv_shared.so").toString();
    CsvComparison.main(
        args,
        new CsvComparison.Natives() {
          private @Nullable FfmIndexer validator;

          @Override
          public String runtime() {
            return "jvm";
          }

          @Override
          public CsvIndexer create(byte d, byte q, boolean quoting, int kernel, boolean small) {
            if (library.isEmpty()) throw new IllegalStateException("truffledata.csv.library unset");
            return new FfmIndexer(Path.of(library), d, q, quoting, kernel, small ? Integer.MAX_VALUE : 0);
          }

          private CsvIndexing.@Nullable Provider provider;

          @Override
          public CsvIndexing.Provider provider() {
            if (library.isEmpty()) throw new IllegalStateException("truffledata.csv.library unset");
            CsvIndexing.Provider p = provider;
            // The published opt-in path: the FFM backend found by CsvIndexing.loadNative.
            if (p == null) provider = p = CsvIndexing.loadNative(Path.of(library));
            return p;
          }

          @Override
          public long invalidUtf8(byte[] input) {
            FfmIndexer v = validator;
            if (v == null) validator = v = (FfmIndexer) create((byte) ',', (byte) '"', true, 0, false);
            return v.invalidUtf8(input, 0, input.length);
          }
        });
  }
}
