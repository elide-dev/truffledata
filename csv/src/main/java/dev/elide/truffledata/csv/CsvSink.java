package dev.elide.truffledata.csv;

/**
 * Receiver for a one-pass export of a batch: rows in order, and within a row every schema field
 * once, in schema order, between {@link #beginRow} and {@link #endRow}. STRING cells arrive as a
 * reusable {@link CsvText} view that is valid only during the {@link #text} call. Exceptions thrown
 * by the sink propagate out of {@link CsvBatch#export} unchanged.
 */
public interface CsvSink {
  /** {@code record} is the stream data-record index; {@code fields == schema.size()}. */
  void beginRow(long record, int fields);

  /** A STRING cell; the view is valid only during this call. */
  void text(int column, CsvText value);

  void longValue(int column, long value);

  void doubleValue(int column, double value);

  void booleanValue(int column, boolean value);

  void nullValue(int column);

  /** Flexible short rows, or optional fields absent from the header. */
  void missing(int column);

  void endRow();
}
