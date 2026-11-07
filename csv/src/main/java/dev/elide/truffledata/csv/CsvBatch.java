package dev.elide.truffledata.csv;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import dev.elide.truffledata.Schema;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable rows of one CSV batch. Guests see an array of {@link CsvRow}s.
 *
 * <p>A <b>heap</b> batch retains its input {@code byte[]}: it needs no closing, is safe to share
 * across threads, and is its own snapshot ({@link #close()} does nothing). A <b>mapped</b> batch
 * ({@link #mapped()}) keeps its STRING cells as spans into a {@link CsvMapping} and must not outlive
 * it: once the mapping closes, every read throws {@code IllegalStateException}. {@link #snapshot()}
 * copies a mapped batch into a heap batch.
 */
@ExportLibrary(InteropLibrary.class)
public final class CsvBatch implements TruffleObject, AutoCloseable {
  private static final byte[] NO_BYTES = new byte[0];

  final Schema schema;

  /** Heap input; empty for mapped batches. */
  final byte[] input;

  @CompilationFinal(dimensions = 1)
  final Column[] columns;

  final int size;
  final long firstRecord;
  final Dialect dialect;
  private final int copiedBytes;

  /** The mapping holding this batch's input, or null for heap batches. */
  final @Nullable CsvMapping mapping;

  /** File offset of span offset 0 (mapped batches). */
  private final long base;

  /** Input bytes the spans address (mapped batches). */
  private final int mappedLength;

  /** Whether {@link #close()} closes the mapping ({@code Csv.parse(Path, …)}). */
  private final boolean ownsMapping;

  CsvBatch(
      Schema schema,
      byte[] input,
      Column[] columns,
      int size,
      long firstRecord,
      Dialect dialect,
      int copiedBytes) {
    this(schema, input, columns, size, firstRecord, dialect, copiedBytes, null, 0, 0, false);
  }

  CsvBatch(
      Schema schema,
      byte[] input,
      Column[] columns,
      int size,
      long firstRecord,
      Dialect dialect,
      int copiedBytes,
      @Nullable CsvMapping mapping,
      long base,
      int mappedLength,
      boolean ownsMapping) {
    this.schema = schema;
    this.input = input;
    this.columns = columns;
    this.size = size;
    this.firstRecord = firstRecord;
    this.dialect = dialect;
    this.copiedBytes = copiedBytes;
    this.mapping = mapping;
    this.base = base;
    this.mappedLength = mappedLength;
    this.ownsMapping = ownsMapping;
  }

  /** Throws once a mapped batch's mapping is closed. Inline: a null test and one load. */
  void checkLive() {
    CsvMapping m = mapping;
    if (m != null && !m.open) throw CsvMapping.closed();
  }

  public Schema schema() {
    return schema;
  }

  public int size() {
    return size;
  }

  public CsvRow row(int index) {
    checkLive();
    Objects.checkIndex(index, size);
    return new CsvRow(this, index);
  }

  /** Stream data-record index of row 0 (the header is not a data record). */
  public long firstRecord() {
    return firstRecord;
  }

  /** Retained input length (for mapped batches, the mapped bytes the batch addresses). */
  public int inputBytes() {
    return mapping != null ? mappedLength : input.length;
  }

  /** Whether the batch reads its input from a {@link CsvMapping}. */
  public boolean mapped() {
    return mapping != null;
  }

  /**
   * A heap batch with the same rows: this batch when it is already on the heap, else a copy of the
   * mapped bytes it addresses (one copy; the primitive columns are shared).
   */
  public CsvBatch snapshot() {
    CsvMapping m = mapping;
    if (m == null) return this;
    byte[] copy = new byte[mappedLength];
    m.copy(base, copy, 0, mappedLength);
    return new CsvBatch(schema, copy, columns, size, firstRecord, dialect, copiedBytes);
  }

  /**
   * Releases the mapping when this batch owns it ({@code Csv.parse(Path, …)}); otherwise nothing:
   * heap batches are never closed, and a borrowed mapping is closed by its owner.
   */
  @Override
  public void close() {
    CsvMapping m = mapping;
    if (ownsMapping && m != null) m.close();
  }

  /** Field bytes ingest copied out of the retained input; spans reference the input instead. */
  int copiedBytes() {
    return copiedBytes;
  }

  // Row reads ({@link CsvRow} getters). Each fast path is one column load, a dense-array test and
  // the heap test (at most 35 bytecodes, so HotSpot inlines it at any call site); nullable,
  // optional and mapped columns, wrong kinds and closed mappings take the slow path.

  long longAt(int row, int column) {
    long[] v = columns[column].presentLongs;
    if (row >= v.length || mapping != null) return longSlow(row, column);
    return v[row];
  }

  private long longSlow(int row, int column) {
    checkLive();
    Column c = columns[column];
    long[] longs = c.longs;
    if (longs == null || c.state(row) != Column.PRESENT)
      throw CsvRow.expected(schema, column, "INT64");
    return longs[row];
  }

  double doubleAt(int row, int column) {
    double[] v = columns[column].presentDoubles;
    if (row >= v.length || mapping != null) return doubleSlow(row, column);
    return v[row];
  }

  private double doubleSlow(int row, int column) {
    checkLive();
    Column c = columns[column];
    double[] doubles = c.doubles;
    if (doubles == null || c.state(row) != Column.PRESENT)
      throw CsvRow.expected(schema, column, "FLOAT64");
    return doubles[row];
  }

  boolean booleanAt(int row, int column) {
    boolean[] v = columns[column].presentBooleans;
    if (row >= v.length || mapping != null) return booleanSlow(row, column);
    return v[row];
  }

  private boolean booleanSlow(int row, int column) {
    checkLive();
    Column c = columns[column];
    boolean[] booleans = c.booleans;
    if (booleans == null || c.state(row) != Column.PRESENT)
      throw CsvRow.expected(schema, column, "BOOLEAN");
    return booleans[row];
  }

  String stringAt(int row, int column) {
    long[] v = columns[column].presentSpans;
    if (row >= v.length || mapping != null) return stringSlow(row, column);
    long span = v[row];
    if (CsvScanner.escaped(span)) return string(this, span);
    return Utf8.string(input, CsvScanner.offset(span), CsvScanner.length(span));
  }

  private String stringSlow(int row, int column) {
    checkLive();
    Column c = columns[column];
    long[] spans = c.spans;
    if (spans == null || c.state(row) != Column.PRESENT)
      throw CsvRow.expected(schema, column, "STRING");
    return string(this, spans[row]);
  }

  /** Materializes one STRING span of this batch, decoding escaped or complex fields. */
  @TruffleBoundary
  static String string(CsvBatch batch, long span) {
    CsvMapping m = batch.mapping;
    if (m == null) return batch.dialect.string(batch.input, span);
    // A String owns its bytes, so mapped cells pass through a per-thread buffer once (no array per
    // cell); only cells longer than the buffer get their own copy.
    int length = CsvScanner.length(span);
    byte[] region = REGION.get();
    if (length > REGION_BYTES) region = new byte[length + 16];
    else if (region == null) REGION.set(region = new byte[REGION_BYTES + 16]);
    m.copy(batch.base + CsvScanner.offset(span), region, 0, length);
    return batch.dialect.string(region, rebase(span));
  }

  /** The longest mapped cell read through {@link #REGION}. */
  private static final int REGION_BYTES = 4096;

  /** Per-thread copy of one mapped cell, plus a word of slack for the ASCII test. */
  private static final ThreadLocal<byte @Nullable []> REGION = new ThreadLocal<>();

  /** The span relative to a copy of its own bytes. */
  private static long rebase(long span) {
    return CsvScanner.span(
        0, CsvScanner.length(span), CsvScanner.quoted(span), CsvScanner.escaped(span));
  }

  /**
   * One pass over the batch: rows in order, columns in schema order. Allocates one {@link CsvText}
   * and, only when the batch has escaped fields (or is mapped), scratch buffers that grow to the
   * longest such field; nothing per row or per field.
   */
  @TruffleBoundary
  public void export(CsvSink sink) {
    Objects.requireNonNull(sink);
    checkLive();
    CsvText text = new CsvText();
    byte[] scratch = NO_BYTES;
    byte[] raw = NO_BYTES;
    Dialect d = dialect;
    CommonsScanner decoder = d.decoder();
    CsvMapping m = mapping;
    Column[] cols = columns;
    int fields = cols.length;
    for (int r = 0; r < size; r++) {
      sink.beginRow(firstRecord + r, fields);
      for (int c = 0; c < fields; c++) {
        Column column = cols[c];
        byte[] states = column.states;
        if (states != null && states[r] != Column.PRESENT) {
          if (states[r] == Column.NULL) sink.nullValue(c);
          else sink.missing(c);
          continue;
        }
        long[] spans = column.spans;
        long[] longs = column.longs;
        double[] doubles = column.doubles;
        boolean[] booleans = column.booleans;
        if (spans != null) {
          long span = spans[r];
          int length = CsvScanner.length(span);
          byte[] source = input;
          if (m != null) {
            // Mapped: copy the cell's bytes into a scratch buffer (no allocation per cell).
            if (raw.length < length) raw = new byte[Math.max(length, raw.length * 2)];
            m.copy(base + CsvScanner.offset(span), raw, 0, length);
            source = raw;
            span = rebase(span);
          }
          if (CsvScanner.escaped(span)) {
            if (scratch.length < length)
              scratch = new byte[Math.max(length, Math.min(scratch.length * 2, inputBytes()))];
            text.set(scratch, 0, d.decode(source, span, scratch, 0, decoder));
          } else text.set(source, CsvScanner.offset(span), length);
          sink.text(c, text);
        } else if (longs != null) sink.longValue(c, longs[r]);
        else if (doubles != null) sink.doubleValue(c, doubles[r]);
        else if (booleans != null) sink.booleanValue(c, booleans[r]);
      }
      sink.endRow();
    }
  }

  @ExportMessage
  boolean hasArrayElements() {
    return true;
  }

  @ExportMessage
  long getArraySize() {
    return size;
  }

  @ExportMessage
  boolean isArrayElementReadable(long index) {
    return index >= 0 && index < size;
  }

  // Inline, without a boundary, so a compiled loop can virtualize the row.
  @ExportMessage
  Object readArrayElement(long index) throws InvalidArrayIndexException {
    checkLive();
    if (index < 0 || index >= size) throw InvalidArrayIndexException.create(index);
    return new CsvRow(this, (int) index);
  }
}
