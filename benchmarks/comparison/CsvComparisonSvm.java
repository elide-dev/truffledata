package dev.elide.truffledata.comparison;

import org.jspecify.annotations.NullMarked;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.svm.SvmIndexer;

/** Native Image entry point: native kernels come from the statically linked csv/native library. */
@NullMarked
public final class CsvComparisonSvm {
  private CsvComparisonSvm() {}

  public static void main(String[] args) throws Exception {
    CsvComparison.main(
        args,
        new CsvComparison.Natives() {
          @Override
          public String runtime() {
            return "svm";
          }

          @Override
          public CsvIndexer create(byte d, byte q, boolean quoting, int kernel, boolean small) {
            return new SvmIndexer(d, q, quoting, kernel, small ? Integer.MAX_VALUE : 0);
          }

          @Override
          public long invalidUtf8(byte[] input) {
            return SvmIndexer.invalidUtf8(input, 0, input.length);
          }
        });
  }
}
