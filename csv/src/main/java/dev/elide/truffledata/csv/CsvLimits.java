package dev.elide.truffledata.csv;

/**
 * Resource bounds. {@code maxBatchBytes} caps one batch's input (checked before any copy), {@code
 * maxRecordBytes} caps one record's content (terminator excluded) and therefore the bytes a stream
 * buffers for an incomplete record, {@code maxColumns} caps fields per record, and {@code
 * maxBatchRows} caps rows per batch.
 */
public record CsvLimits(int maxBatchBytes, int maxRecordBytes, int maxColumns, int maxBatchRows) {
  public static final CsvLimits DEFAULT = new CsvLimits(64 << 20, 1 << 20, 4096, 1 << 20);

  public CsvLimits {
    if (maxBatchBytes <= 0 || maxRecordBytes <= 0 || maxColumns <= 0 || maxBatchRows <= 0)
      throw new IllegalArgumentException("CSV limits must be positive");
    if (maxRecordBytes > maxBatchBytes)
      throw new IllegalArgumentException("CSV maxRecordBytes must not exceed maxBatchBytes");
    if (maxBatchBytes > Integer.MAX_VALUE - 64)
      throw new IllegalArgumentException("CSV maxBatchBytes exceeds the array limit");
    // Spans pack a field length in 30 bits.
    if (maxRecordBytes >= 1 << 30)
      throw new IllegalArgumentException("CSV maxRecordBytes must be below 2^30");
  }
}
