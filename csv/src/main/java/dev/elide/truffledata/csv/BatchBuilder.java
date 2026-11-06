package dev.elide.truffledata.csv;

import dev.elide.truffledata.ContractViolation;
import java.util.Arrays;

/**
 * Fills one batch's columns as the scanner reports records: validates each record's UTF-8 and
 * width, then stores spans (STRING) or converted primitives per schema field. Arrays grow
 * geometrically and are trimmed when more than 1/8 of the capacity is unused.
 */
final class BatchBuilder implements CsvScanner.Records {
  private final CsvPlan.Binding binding;
  private final byte[] input;
  private final int maxRows;
  private final boolean flexible;
  private final byte quote;
  private final long firstRecord;
  private final long recordBase;
  private final long byteBase;

  private final long[][] words; // spans (STRING) or values (INT64), per field
  private final byte[][] states; // per field; empty when the field is neither nullable nor optional
  private int capacity;
  private int rows;
  private boolean full;

  /**
   * @param firstRecord data-record index of the first row
   * @param recordBase physical records (header included) before the scanned range
   * @param byteBase stream offset of {@code input[0]}
   */
  BatchBuilder(
      CsvPlan.Binding binding,
      byte[] input,
      CsvLimits limits,
      CsvFormat format,
      long firstRecord,
      long recordBase,
      long byteBase,
      int capacity) {
    this.binding = binding;
    this.input = input;
    this.maxRows = limits.maxBatchRows();
    this.flexible = format.flexible();
    this.quote = format.quote();
    this.firstRecord = firstRecord;
    this.recordBase = recordBase;
    this.byteBase = byteBase;
    this.capacity = Math.max(1, Math.min(capacity, maxRows));
    int fields = binding.fields.length;
    words = new long[fields][];
    states = new byte[fields][];
    for (int f = 0; f < fields; f++) {
      words[f] = new long[this.capacity];
      states[f] = binding.stateful[f] ? new byte[this.capacity] : new byte[0];
    }
  }

  /** Null rule: the only null token is an unquoted, empty field (in a nullable column). */
  static boolean nullToken(long span) {
    return !CsvScanner.quoted(span) && CsvScanner.length(span) == 0;
  }

  int rows() {
    return rows;
  }

  /** Whether a record was refused because the batch reached maxBatchRows. */
  boolean full() {
    return full;
  }

  @Override
  public boolean record(long[] spans, int count, int start, int end) {
    if (rows == maxRows) {
      full = true;
      return false;
    }
    long number = recordBase + rows + 1;
    int bad = Utf8.invalidOffset(input, start, end);
    if (bad >= 0) throw CsvScanner.syntax(number, byteBase + bad, "invalid UTF-8");
    CsvPlan.Binding b = binding;
    if (count != b.width && (count > b.width || !flexible))
      throw CsvScanner.syntax(
          number, byteBase + start, "record has " + count + " fields, expected " + b.width);
    if (rows == capacity) grow();
    int row = rows;
    int[] fieldToColumn = b.fieldToColumn;
    for (int f = 0; f < fieldToColumn.length; f++) {
      int column = fieldToColumn[f];
      if (column < 0 || column >= count) {
        if (!b.fields[f].optional()) throw violation(number, f, "missing");
        states[f][row] = Column.MISSING;
        continue;
      }
      long span = spans[column];
      if (b.nullable[f] && nullToken(span)) {
        states[f][row] = Column.NULL;
        continue;
      }
      words[f][row] = span;
    }
    rows++;
    return true;
  }

  private ContractViolation violation(long number, int field, String reason) {
    return new ContractViolation(
        "CSV record " + number + ": Field '" + binding.fields[field].name() + "': " + reason);
  }

  private void grow() {
    int grown = (int) Math.min((long) capacity * 2, maxRows);
    resize(grown);
  }

  private void resize(int size) {
    for (int f = 0; f < words.length; f++) {
      words[f] = Arrays.copyOf(words[f], size);
      if (binding.stateful[f]) states[f] = Arrays.copyOf(states[f], size);
    }
    capacity = size;
  }

  CsvBatch build() {
    if (capacity - rows > capacity / 8) resize(rows);
    Column[] columns = new Column[words.length];
    for (int f = 0; f < columns.length; f++) {
      columns[f] =
          new Column(words[f], null, null, null, binding.stateful[f] ? states[f] : null);
    }
    return new CsvBatch(binding.schema, input, columns, rows, firstRecord, quote, 0);
  }
}
