package dev.elide.truffledata.csv;

/** Entry points for generic CSV: every column is a non-nullable STRING named by the header. */
public final class Csv {
  private Csv() {}

  public static CsvBatch parse(byte[] utf8, CsvFormat format) {
    return parse(utf8, format, CsvLimits.DEFAULT);
  }

  /** Copies the input once (after checking maxBatchBytes) and retains the copy. */
  public static CsvBatch parse(byte[] utf8, CsvFormat format, CsvLimits limits) {
    return CsvPlan.generic(format).parse(utf8, limits);
  }
}
