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

  /** The scanned window; spans stored for STRING fields are shifted to batch offsets. */
  private byte[] input;

  /** Batch offset of {@code input[0]} (windowed mapped parses), applied to stored spans. */
  private long spanShift;
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
  private long byteBase;

  // Per field; the arrays a field's kind does not use stay empty.
  private final long[][] words; // spans (STRING) or values (INT64)
  private final double[][] doubles;
  private final boolean[][] booleans;
  private final byte[][] states; // only nullable or optional fields
  private int capacity;
  private int rows;
  private boolean full;

  // Fused ingest ({@link #fusable}): the index path writes each field into row {@link #rows} as
  // it frames it ({@link #put}) and commits the record at its end ({@link #commit}); a record it
  // cannot convert in place goes through {@link #record} instead, which rewrites the same row.
  private static final byte SKIP = -1;

  /** Whether {@link #put} and {@link #commit} may build this batch. */
  private final boolean fusable;

  /** Per record column: the field kind it fills, or {@link #SKIP} (projected away). */
  private final byte[] fuseKind;

  /** Per record column: the field it fills. */
  private final int[] fuseField;

  /** Whether row {@link #rows} has capacity (false once the batch reaches maxBatchRows). */
  private boolean writable;

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
    writable = rows < this.capacity;
    fuseKind = new byte[binding.width];
    fuseField = new int[binding.width];
    Arrays.fill(fuseKind, SKIP);
    // Fused records hold every field (no state arrays) and need no null-token decision beyond the
    // strict legacy rule, which no typed grammar accepts and a STRING field stores as is.
    boolean simple = !nullsActive || nullsLegacy;
    for (int f = 0; f < fields; f++) {
      int column = binding.fieldToColumn[f];
      if (binding.stateful[f] || column < 0 || column >= binding.width) simple = false;
      else {
        fuseKind[column] = binding.kinds[f];
        fuseField[column] = f;
      }
    }
    fusable = simple;
  }

  /** Whether the index path may fill this batch through {@link #put} and {@link #commit}. */
  boolean fusable() {
    return fusable;
  }

  /**
   * Converts field {@code column} of the record being framed into row {@link #rows}. False when the
   * record must go through {@link #record} (a value no grammar accepts, a constraint, a complex
   * Commons span in a typed field, a column past the width, or no capacity): that path then
   * reports the exact diagnostic, or accepts the record, rewriting the same row.
   */
  boolean put(int column, long span) {
    byte[] kinds = fuseKind;
    if (column >= kinds.length || !writable) return false;
    byte kind = kinds[column];
    if (kind == SKIP) return true;
    int f = fuseField[column];
    if (kind == CsvPlan.STRING) {
      words[f][rows] = span + spanShift;
      return true;
    }
    if (decoder != null && CsvScanner.escaped(span)) return false;
    int offset = CsvScanner.offset(span);
    int length = CsvScanner.length(span);
    try {
      switch (kind) {
        case CsvPlan.INT64 -> {
          long value = Numbers.parseLong(input, offset, length);
          if (binding.constrained[f]) Values.validateLong(binding.fields[f], value);
          words[f][rows] = value;
        }
        case CsvPlan.FLOAT64 -> {
          double value = Numbers.parseDouble(input, offset, length);
          if (binding.binary32[f] && (double) (float) value != value) return false;
          doubles[f][rows] = value;
        }
        default -> {
          int b = bool(input, offset, length);
          if (b < 0) return false;
          booleans[f][rows] = b != 0;
        }
      }
    } catch (NumberFormatException | ContractViolation e) {
      return false;
    }
    return true;
  }

  /**
   * Commits the record of {@code count} fields that {@link #put} wrote, [start, end) of the input,
   * after checking its width and, when the prevalidated range does not cover it, its UTF-8. False
   * leaves the record to {@link #record} (which reports the error or handles a flexible width).
   */
  boolean commit(int count, int start, int end) {
    if (count != binding.width) return false;
    if ((start < validFrom || end > validTo) && Utf8.invalidOffset(input, start, end) >= 0)
      return false;
    advance(end);
    return true;
  }

  /** Counts one row and keeps row {@link #rows} writable while maxBatchRows allows. */
  private void advance(int consumed) {
    if (++rows == capacity && rows < maxRows) grow(consumed);
    writable = rows < capacity;
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

  /** Input [validFrom, validTo) is known to be strict UTF-8 ({@link #prevalidate}). */
  private int validFrom;

  private int validTo;

  /**
   * Validates input [from, to) once, ahead of the records: records inside the valid prefix skip
   * their own validation, and a record reaching the first invalid byte validates itself, so every
   * diagnostic (record, offset, and its order against syntax errors) is the per-record one.
   */
  void prevalidate(CsvScanner scanner, int from, int to) {
    int bad = scanner.invalidUtf8(input, from, to);
    validFrom = from;
    validTo = bad < 0 ? to : bad;
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
    if (start < validFrom || end > validTo) {
      int bad = Utf8.invalidOffset(input, start, end);
      if (bad >= 0) throw CsvScanner.syntax(number, byteBase + bad, "invalid UTF-8");
    }
    CsvPlan.Binding b = binding;
    if (count != b.width && (count > b.width || !flexible))
      throw CsvScanner.syntax(
          number, byteBase + start, "record has " + count + " fields, expected " + b.width);
    if (rows == capacity) grow(start);
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
        case CsvPlan.STRING -> words[f][row] = span + spanShift;
        case CsvPlan.INT64 -> words[f][row] = toLong(number, f, span);
        case CsvPlan.FLOAT64 -> doubles[f][row] = toDouble(number, f, span);
        default -> booleans[f][row] = toBoolean(number, f, span);
      }
    }
    advance(end);
    return true;
  }

  // Escaped content holds quote bytes, which no numeric or boolean grammar accepts.
  private long toLong(long number, int field, long span) {
    long value;
    try {
      // Strict spans (and simple Commons spans) are read in place; only complex Commons spans decode.
      if (decoder == null || !CsvScanner.escaped(span)) {
        value = Numbers.parseLong(input, CsvScanner.offset(span), CsvScanner.length(span));
      } else {
        int length = view(span);
        value = Numbers.parseLong(viewBytes, viewOffset, length);
      }
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
      if (decoder == null || !CsvScanner.escaped(span)) {
        value = Numbers.parseDouble(input, CsvScanner.offset(span), CsvScanner.length(span));
      } else {
        int length = view(span);
        value = Numbers.parseDouble(viewBytes, viewOffset, length);
      }
    } catch (NumberFormatException e) {
      throw violation(number, field, "invalid FLOAT64");
    }
    if (binding.binary32[field] && (double) (float) value != value)
      throw violation(number, field, "floating width");
    return value;
  }

  /** {@code true} or {@code false}, ASCII case-insensitive. */
  private boolean toBoolean(long number, int field, long span) {
    int length = view(span);
    int b = bool(viewBytes, viewOffset, length);
    if (b < 0) throw violation(number, field, "invalid BOOLEAN");
    return b != 0;
  }

  /** 1 for {@code true}, 0 for {@code false} (ASCII case-insensitive), else -1. */
  private static int bool(byte[] v, int o, int length) {
    if (length == 4
        && (v[o] | 0x20) == 't'
        && (v[o + 1] | 0x20) == 'r'
        && (v[o + 2] | 0x20) == 'u'
        && (v[o + 3] | 0x20) == 'e') return 1;
    if (length == 5
        && (v[o] | 0x20) == 'f'
        && (v[o + 1] | 0x20) == 'a'
        && (v[o + 2] | 0x20) == 'l'
        && (v[o + 3] | 0x20) == 's'
        && (v[o + 4] | 0x20) == 'e') return 0;
    return -1;
  }

  private ContractViolation violation(long number, int field, String reason) {
    return new ContractViolation(
        "CSV record " + number + ": Field '" + binding.fields[field].name() + "': " + reason);
  }

  /**
   * Doubles the row arrays, or, when the scanned range is known ({@link #expect}), grows them to
   * the row count extrapolated from the bytes consumed so far (plus 1/16, and at least 1/8 more),
   * so a uniform input grows once or twice and is not doubled near its end.
   */
  private void grow(int consumed) {
    long size = (long) capacity * 2;
    long done = (long) consumed - expectFrom;
    if (expecting && done > 0) {
      // Late in the range the estimate is close: grow by 1/8 at least instead of doubling.
      long estimate = rows * (expectTo - expectFrom) / done;
      size = Math.max(capacity + (capacity >> 3) + 16, estimate + (estimate >> 4) + 16);
    }
    resize((int) Math.min(size, maxRows));
  }

  /**
   * The bytes this batch will scan, [from, to) in the coordinates of the current input: a window
   * of a whole-file parse passes a negative {@code from} (the window's batch offset).
   */
  private long expectFrom;

  private long expectTo;
  private boolean expecting;

  void expect(long from, long to) {
    expectFrom = from;
    expectTo = to;
    expecting = to > from;
  }

  private void resize(int size) {
    for (int f = 0; f < words.length; f++) {
      if (words[f] != NO_WORDS) words[f] = Arrays.copyOf(words[f], size);
      if (doubles[f] != NO_DOUBLES) doubles[f] = Arrays.copyOf(doubles[f], size);
      if (booleans[f] != NO_BOOLEANS) booleans[f] = Arrays.copyOf(booleans[f], size);
      if (states[f] != NO_STATES) states[f] = Arrays.copyOf(states[f], size);
    }
    capacity = size;
    writable = rows < capacity;
  }

  /** Continues the batch over the next window: {@code shift} is its batch offset. */
  void window(byte[] input, int shift, long byteBase) {
    this.input = input;
    this.spanShift = (long) shift << 32;
    this.byteBase = byteBase;
    validFrom = validTo = 0;
  }

  /** A batch over {@code length} mapped bytes at file offset {@code base}. */
  CsvBatch buildMapped(CsvMapping mapping, long base, int length, boolean owns) {
    trim();
    return new CsvBatch(
        binding.schema, NO_BYTES, columns(false), rows, firstRecord, dialect, 0, mapping, base, length, owns);
  }

  private void trim() {
    if (capacity - rows > capacity / 8) resize(rows);
  }

  /** A batch without STRING fields: every value is converted, so it retains no input. */
  CsvBatch buildDetached() {
    trim();
    return new CsvBatch(binding.schema, NO_BYTES, columns(true), rows, firstRecord, dialect, 0);
  }

  CsvBatch build() {
    trim();
    return new CsvBatch(binding.schema, input, columns(true), rows, firstRecord, dialect, 0);
  }

  /** {@code heap}: false for mapped batches, whose reads always check the mapping first. */
  private Column[] columns(boolean heap) {
    Column[] columns = new Column[words.length];
    for (int f = 0; f < columns.length; f++) {
      byte kind = binding.kinds[f];
      columns[f] =
          new Column(
              kind == CsvPlan.STRING ? words[f] : null,
              kind == CsvPlan.INT64 ? words[f] : null,
              kind == CsvPlan.FLOAT64 ? doubles[f] : null,
              kind == CsvPlan.BOOLEAN ? booleans[f] : null,
              binding.stateful[f] ? states[f] : null,
              heap);
    }
    return columns;
  }
}
