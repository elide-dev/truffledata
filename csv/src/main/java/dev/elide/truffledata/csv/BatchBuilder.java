package dev.elide.truffledata.csv;

import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Values;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Fills one batch's columns as the scanner reports records: validates each record's UTF-8 and
 * width, then stores spans (STRING) or converts and stores primitives (INT64, FLOAT64, BOOLEAN) per
 * schema field. Arrays grow geometrically and are trimmed when more than 1/8 of the capacity is
 * unused. Diagnostics name the record and field, never the field's text.
 *
 * <p>Staged ingest ({@link #stages}): while the index scan frames a batch with its plain loop, a
 * complete, valid record only copies its spans into per-column chunks of {@link #CHUNK} rows;
 * {@link #convert} then converts each field over the chunk in one monomorphic pass. Every other
 * record (and every record of the per-byte scanners) takes the per-record path, which converts the
 * staged rows first, so diagnostics and their order are the per-record path's.
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

  /** Whether row {@link #rows} has capacity (false once the batch reaches maxBatchRows). */
  private boolean writable;

  /** Rows per staged conversion chunk. */
  static final int CHUNK = 64;

  /**
   * Per record column, the spans of the rows staged since {@link #chunkBase} (lent for one index
   * scan, {@link #lend}); empty otherwise. No number parser is inlined into the framing loop.
   */
  private long[][] staged = NO_STAGE;

  private static final long[][] NO_STAGE = new long[0][];

  /** The record width {@link #record} stages while chunks are lent ({@link #lend}), else -1. */
  private int stagedWidth = -1;

  /**
   * Whether this binding stages: every retained field is in its column, none needs a state array,
   * and the null rule needs no decision beyond the strict legacy one (no typed grammar accepts an
   * empty field and a STRING field stores it as is).
   */
  private final boolean stages;

  /**
   * Per-thread staging chunks, lent to one builder for one index scan: the scan converts every
   * staged row before it returns ({@link #giveBack}), so nothing is staged between scans.
   */
  private static final ThreadLocal<long @Nullable [][]> CHUNKS = new ThreadLocal<>();

  /** First row of the staged chunk; rows [chunkBase, chunkBase + pending) are not converted yet. */
  private int chunkBase;

  private int pending;

  /** Per-record scratch for {@link #replay}, allocated on the first declined chunk. */
  private long[] replayRow = NO_WORDS;

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
    boolean simple = !nullsActive || nullsLegacy;
    for (int f = 0; f < fields; f++) {
      int column = binding.fieldToColumn[f];
      if (binding.stateful[f] || column < 0 || column >= binding.width) simple = false;
    }
    stages = simple;
  }

  /** Whether {@link IndexScan} frames this batch with the plain loop and staged records. */
  boolean stages() {
    return stages;
  }

  /**
   * Lends this builder the thread's staging chunks for one index scan. A scan nested in a
   * callback finds none and allocates its own.
   */
  void lend() {
    int width = binding.width;
    long[][] chunks = CHUNKS.get();
    CHUNKS.set(null);
    if (chunks == null || chunks.length < width) {
      chunks = new long[width][];
      for (int column = 0; column < width; column++) chunks[column] = new long[CHUNK];
    }
    staged = chunks;
    stagedWidth = width;
  }

  /** Converts the staged rows (reporting their first diagnostic) and returns the chunks. */
  void giveBack() {
    try {
      convert();
    } finally {
      abandon();
    }
  }

  /** Returns the chunks without converting (the scan is failing); staged rows are dropped. */
  void abandon() {
    long[][] chunks = staged;
    staged = NO_STAGE;
    stagedWidth = -1;
    pending = 0;
    if (chunks.length != 0) CHUNKS.set(chunks);
  }

  /**
   * Converts the staged rows field by field. The first value no grammar accepts (or a constraint,
   * or a complex Commons span) sends the whole chunk through {@link #replay}, which converts it
   * row by row in record and field order as {@link #record} does, so diagnostics and their order
   * are the per-record path's. Called when a chunk fills, before {@link #record} writes a row, and
   * when the index scan returns, so no later framing error can overtake a staged one.
   */
  @InliningCutoff
  void convert() {
    int n = pending;
    if (n == 0) return;
    int base = chunkBase;
    CsvPlan.Binding b = binding;
    byte[] kinds = b.kinds;
    int[] fieldToColumn = b.fieldToColumn;
    boolean commons = decoder != null;
    for (int f = 0; f < kinds.length; f++) {
      long[] spans = staged[fieldToColumn[f]];
      boolean converted =
          switch (kinds[f]) {
            case CsvPlan.STRING -> shiftSpans(spans, n, words[f], base, spanShift);
            case CsvPlan.INT64 ->
                convertLongs(input, spans, n, words[f], base, commons, b.constrained[f] ? b.fields[f] : null);
            case CsvPlan.FLOAT64 ->
                convertDoubles(input, spans, n, doubles[f], base, commons, b.binary32[f]);
            default -> convertBooleans(input, spans, n, booleans[f], base, commons);
          };
      if (!converted) {
        replay(base, n);
        return;
      }
    }
    chunkBase = base + n;
    pending = 0;
  }

  private static boolean shiftSpans(long[] spans, int n, long[] out, int base, long shift) {
    for (int i = 0; i < n; i++) out[base + i] = spans[i] + shift;
    return true;
  }

  // One field over a chunk. False on the first value the per-record path decides (no grammar
  // accepts it, a constraint, a complex Commons span): the chunk is then replayed per record.
  // Host-inlining roots, so Native Image inlines the number parsers
  // and their SWAR helpers into each monomorphic pass.

  @BytecodeInterpreterSwitch
  private static boolean convertLongs(
      byte[] input, long[] spans, int n, long[] out, int base, boolean commons, @Nullable Field constrained) {
    try {
      for (int i = 0; i < n; i++) {
        long span = spans[i];
        if (commons && CsvScanner.escaped(span)) return false;
        long value = Numbers.parseLong(input, CsvScanner.offset(span), CsvScanner.length(span));
        if (constrained != null) Values.validateLong(constrained, value);
        out[base + i] = value;
      }
      return true;
    } catch (NumberFormatException | ContractViolation e) {
      return false;
    }
  }

  @BytecodeInterpreterSwitch
  private static boolean convertDoubles(
      byte[] input, long[] spans, int n, double[] out, int base, boolean commons, boolean binary32) {
    try {
      for (int i = 0; i < n; i++) {
        long span = spans[i];
        if (commons && CsvScanner.escaped(span)) return false;
        double value = Numbers.parseDouble(input, CsvScanner.offset(span), CsvScanner.length(span));
        if (binary32 && (double) (float) value != value) return false;
        out[base + i] = value;
      }
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  @BytecodeInterpreterSwitch
  private static boolean convertBooleans(
      byte[] input, long[] spans, int n, boolean[] out, int base, boolean commons) {
    for (int i = 0; i < n; i++) {
      long span = spans[i];
      if (commons && CsvScanner.escaped(span)) return false;
      int b = bool(input, CsvScanner.offset(span), CsvScanner.length(span));
      if (b < 0) return false;
      out[base + i] = b != 0;
    }
    return true;
  }

  /** The staged chunk through the per-record conversion ({@link #fill}), row by row. */
  private void replay(int base, int n) {
    int width = binding.width;
    if (replayRow.length < width) replayRow = new long[width];
    long[] row = replayRow;
    for (int i = 0; i < n; i++) {
      for (int column = 0; column < width; column++) row[column] = staged[column][i];
      fill(base + i, row, width, recordBase + base + i + 1);
    }
    chunkBase = base + n;
    pending = 0;
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

  /**
   * One framed record. While chunks are lent, a complete record within row capacity whose UTF-8 is
   * valid (prevalidated, or checked here) is staged; everything else takes {@link #recordSlow}.
   */
  @Override
  @InliningCutoff
  public boolean record(long[] spans, int count, int start, int end) {
    // Staged plans framed by the index scan: a complete, valid record only copies its spans.
    if (count == stagedWidth
        && writable
        && ((start >= validFrom && end <= validTo) || Utf8.invalidOffset(input, start, end) < 0)) {
      long[][] s = staged;
      int k = pending;
      for (int column = 0; column < count; column++) s[column][k] = spans[column];
      advance(end);
      if (++pending == CHUNK) convert();
      return true;
    }
    return recordSlow(spans, count, start, end);
  }

  /** {@link #record} for every record the staged copy does not take. */
  private boolean recordSlow(long[] spans, int count, int start, int end) {
    if (pending != 0) convert();
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
    fill(rows, spans, count, number);
    advance(end);
    chunkBase = rows;
    return true;
  }

  /** Converts and stores one accepted record's fields into {@code row}; throws its diagnostic. */
  private void fill(int row, long[] spans, int count, long number) {
    CsvPlan.Binding b = binding;
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
    convert();
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
    convert();
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
