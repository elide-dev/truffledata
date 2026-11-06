package dev.elide.truffledata.csv;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Values;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Fills one batch's columns as the scanner reports records: validates each record's UTF-8 and
 * width, then stores spans (STRING) or converts and stores primitives (INT64, FLOAT64, BOOLEAN) per
 * schema field in the same pass. Arrays grow geometrically and are trimmed when more than 1/8 of the
 * capacity is unused. Diagnostics name the record and field, never the field's text.
 */
final class BatchBuilder implements CsvScanner.Records {
  private static final long[] NO_WORDS = new long[0];
  private static final double[] NO_DOUBLES = new double[0];
  private static final boolean[] NO_BOOLEANS = new boolean[0];
  private static final byte[] NO_STATES = new byte[0];
  private static final byte[] NO_BYTES = new byte[0];

  private final CsvPlan.Binding binding;
  private final byte[] input;
  private final int maxRows;
  private final boolean flexible;
  private final Dialect dialect;
  private final @Nullable CommonsScanner decoder;
  private final Nulls nulls;
  private final boolean nullsActive;
  private final boolean nullsLegacy;
  private byte[] scratch = NO_BYTES;

  /** The current conversion view (see {@link #view}). */
  private byte[] viewBytes = NO_BYTES;

  private int viewOffset;
  private final long firstRecord;
  private final long recordBase;
  private final long byteBase;

  // Per field; the arrays a field's kind does not use stay empty.
  private final long[][] words; // spans (STRING) or values (INT64)
  private final double[][] doubles;
  private final boolean[][] booleans;
  private final byte[][] states; // only nullable or optional fields
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
      Dialect dialect,
      long firstRecord,
      long recordBase,
      long byteBase,
      int capacity) {
    this.binding = binding;
    this.input = input;
    this.maxRows = limits.maxBatchRows();
    this.flexible = dialect.format.flexible();
    this.dialect = dialect;
    this.decoder = dialect.decoder();
    this.nulls = dialect.nulls;
    this.nullsActive = nulls.active();
    this.nullsLegacy = nulls.legacy();
    this.firstRecord = firstRecord;
    this.recordBase = recordBase;
    this.byteBase = byteBase;
    this.capacity = Math.max(1, Math.min(capacity, maxRows));
    int fields = binding.fields.length;
    words = new long[fields][];
    doubles = new double[fields][];
    booleans = new boolean[fields][];
    states = new byte[fields][];
    for (int f = 0; f < fields; f++) {
      byte kind = binding.kinds[f];
      words[f] =
          kind == CsvPlan.STRING || kind == CsvPlan.INT64 ? new long[this.capacity] : NO_WORDS;
      doubles[f] = kind == CsvPlan.FLOAT64 ? new double[this.capacity] : NO_DOUBLES;
      booleans[f] = kind == CsvPlan.BOOLEAN ? new boolean[this.capacity] : NO_BOOLEANS;
      states[f] = binding.stateful[f] ? new byte[this.capacity] : NO_STATES;
    }
  }

  /** Whether a field is a null token ({@link Nulls}, the one null rule, on its final value). */
  private boolean nullToken(long span) {
    if (nullsLegacy) return !CsvScanner.quoted(span) && CsvScanner.length(span) == 0;
    boolean quoted = CsvScanner.quoted(span);
    if (!CsvScanner.escaped(span))
      return nulls.isNull(input, CsvScanner.offset(span), CsvScanner.length(span), quoted);
    int length = CsvScanner.length(span);
    if (scratch.length < length) scratch = new byte[Math.max(length, scratch.length * 2)];
    return nulls.isNull(scratch, 0, dialect.decode(input, span, scratch, 0, decoder), quoted);
  }

  /**
   * Points {@link #viewBytes} and {@link #viewOffset} at a span's value and returns its length.
   * Complex Commons spans decode into the scratch buffer; strict escaped spans stay raw (their
   * doubled quotes fail every typed grammar).
   */
  private int view(long span) {
    CommonsScanner d = decoder;
    if (d != null && CsvScanner.escaped(span)) {
      int length = CsvScanner.length(span);
      if (scratch.length < length) scratch = new byte[Math.max(length, scratch.length * 2)];
      viewBytes = scratch;
      viewOffset = 0;
      return d.decode(input, span, scratch, 0);
    }
    viewBytes = input;
    viewOffset = CsvScanner.offset(span);
    return CsvScanner.length(span);
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
      if (nullsActive && nullToken(span)) {
        if (b.nullable[f]) {
          states[f][row] = Column.NULL;
          continue;
        }
        // Unconfigured strict formats read an empty field as "" in a non-nullable STRING column.
        if (!nullsLegacy) throw violation(number, f, "null token in a non-nullable field");
        if (b.kinds[f] != CsvPlan.STRING) throw violation(number, f, "null");
      }
      switch (b.kinds[f]) {
        case CsvPlan.STRING -> words[f][row] = span;
        case CsvPlan.INT64 -> words[f][row] = toLong(number, f, span);
        case CsvPlan.FLOAT64 -> doubles[f][row] = toDouble(number, f, span);
        default -> booleans[f][row] = toBoolean(number, f, span);
      }
    }
    rows++;
    return true;
  }

  // Escaped content holds quote bytes, which no numeric or boolean grammar accepts.
  private long toLong(long number, int field, long span) {
    long value;
    try {
      int length = view(span);
      value = Numbers.parseLong(viewBytes, viewOffset, length);
    } catch (NumberFormatException e) {
      throw violation(number, field, "invalid INT64");
    }
    if (binding.constrained[field]) {
      try {
        Values.validateLong(binding.fields[field], value);
      } catch (ContractViolation e) {
        throw new ContractViolation("CSV record " + number + ": " + e.getMessage());
      }
    }
    return value;
  }

  private double toDouble(long number, int field, long span) {
    double value;
    try {
      int length = view(span);
      value = Numbers.parseDouble(viewBytes, viewOffset, length);
    } catch (NumberFormatException e) {
      throw violation(number, field, "invalid FLOAT64");
    }
    if (binding.binary32[field] && (double) (float) value != value)
      throw violation(number, field, "floating width");
    return value;
  }

  private boolean toBoolean(long number, int field, long span) {
    int length = view(span);
    int offset = viewOffset;
    if (length == 4 && matches(offset, "true")) return true;
    if (length == 5 && matches(offset, "false")) return false;
    throw violation(number, field, "invalid BOOLEAN");
  }

  /** ASCII case-insensitive match of a lower-case word of letters. */
  private boolean matches(int offset, String word) {
    for (int i = 0; i < word.length(); i++)
      if ((viewBytes[offset + i] | 0x20) != word.charAt(i)) return false;
    return true;
  }

  private ContractViolation violation(long number, int field, String reason) {
    return new ContractViolation(
        "CSV record " + number + ": Field '" + binding.fields[field].name() + "': " + reason);
  }

  private void grow() {
    resize((int) Math.min((long) capacity * 2, maxRows));
  }

  private void resize(int size) {
    for (int f = 0; f < words.length; f++) {
      if (words[f] != NO_WORDS) words[f] = Arrays.copyOf(words[f], size);
      if (doubles[f] != NO_DOUBLES) doubles[f] = Arrays.copyOf(doubles[f], size);
      if (booleans[f] != NO_BOOLEANS) booleans[f] = Arrays.copyOf(booleans[f], size);
      if (states[f] != NO_STATES) states[f] = Arrays.copyOf(states[f], size);
    }
    capacity = size;
  }

  CsvBatch build() {
    if (capacity - rows > capacity / 8) resize(rows);
    Column[] columns = new Column[words.length];
    for (int f = 0; f < columns.length; f++) {
      byte kind = binding.kinds[f];
      columns[f] =
          new Column(
              kind == CsvPlan.STRING ? words[f] : null,
              kind == CsvPlan.INT64 ? words[f] : null,
              kind == CsvPlan.FLOAT64 ? doubles[f] : null,
              kind == CsvPlan.BOOLEAN ? booleans[f] : null,
              binding.stateful[f] ? states[f] : null);
    }
    return new CsvBatch(binding.schema, input, columns, rows, firstRecord, dialect, 0);
  }
}
