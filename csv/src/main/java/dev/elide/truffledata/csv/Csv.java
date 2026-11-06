package dev.elide.truffledata.csv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Entry points for generic CSV: every column is a STRING named by the header (nullable when the
 * format configures null strings). Inputs are heap bytes, streams, or memory-mapped files.
 */
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

  /** One mapped batch over a caller-owned mapping; valid while the mapping is open. */
  public static CsvBatch parse(CsvMapping mapping, CsvFormat format) {
    return parse(mapping, format, CsvLimits.DEFAULT);
  }

  public static CsvBatch parse(CsvMapping mapping, CsvFormat format, CsvLimits limits) {
    return CsvPlan.generic(format).parse(mapping, limits);
  }

  /** One mapped batch over a private confined mapping; {@link CsvBatch#close()} unmaps it. */
  public static CsvBatch parse(Path path, CsvFormat format) throws IOException {
    return parse(path, format, CsvLimits.DEFAULT);
  }

  public static CsvBatch parse(Path path, CsvFormat format, CsvLimits limits) throws IOException {
    return CsvPlan.generic(format).parse(path, limits);
  }

  /** Streams mapped batches over a caller-owned mapping. */
  public static CsvReader open(CsvMapping mapping, CsvFormat format) {
    return open(mapping, format, CsvLimits.DEFAULT, DEFAULT_BATCH_BYTES);
  }

  public static CsvReader open(
      CsvMapping mapping, CsvFormat format, CsvLimits limits, int batchBytes) {
    return CsvPlan.generic(format).open(mapping, limits, batchBytes);
  }

  /** Streams mapped batches over a private confined mapping; closing the reader unmaps it. */
  public static CsvReader open(Path path, CsvFormat format) throws IOException {
    return open(path, format, CsvLimits.DEFAULT, DEFAULT_BATCH_BYTES);
  }

  public static CsvReader open(Path path, CsvFormat format, CsvLimits limits, int batchBytes)
      throws IOException {
    return CsvPlan.generic(format).open(path, limits, batchBytes);
  }
}
