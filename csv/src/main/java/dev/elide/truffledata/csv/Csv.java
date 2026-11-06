package dev.elide.truffledata.csv;

import java.io.InputStream;

/** Entry points for generic CSV: every column is a non-nullable STRING named by the header. */
public final class Csv {
  static final int DEFAULT_BATCH_BYTES = 1 << 20;

  private Csv() {}

  public static CsvBatch parse(byte[] utf8, CsvFormat format) {
    return parse(utf8, format, CsvLimits.DEFAULT);
  }

  /** Copies the input once (after checking maxBatchBytes) and retains the copy. */
  public static CsvBatch parse(byte[] utf8, CsvFormat format, CsvLimits limits) {
    return CsvPlan.generic(format).parse(utf8, limits);
  }

  /** Streams batches of about 1 MiB of input each. The reader owns the stream. */
  public static CsvReader open(InputStream input, CsvFormat format) {
    return open(input, format, CsvLimits.DEFAULT, DEFAULT_BATCH_BYTES);
  }

  public static CsvReader open(
      InputStream input, CsvFormat format, CsvLimits limits, int batchBytes) {
    return CsvPlan.generic(format).open(input, limits, batchBytes);
  }
}
