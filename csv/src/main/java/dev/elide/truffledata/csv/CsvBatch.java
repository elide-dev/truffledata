package dev.elide.truffledata.csv;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import dev.elide.truffledata.Schema;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Immutable rows of one CSV batch over its retained input bytes. Batches need no closing, are safe
 * to share across threads, and are their own snapshot. Guests see an array of {@link CsvRow}s.
 */
@ExportLibrary(InteropLibrary.class)
public final class CsvBatch implements TruffleObject {
  final Schema schema;
  final byte[] input;

  @CompilationFinal(dimensions = 1)
  final Column[] columns;

  final int size;
  final long firstRecord;
  final byte quote;
  private final int copiedBytes;

  CsvBatch(
      Schema schema,
      byte[] input,
      Column[] columns,
      int size,
      long firstRecord,
      byte quote,
      int copiedBytes) {
    this.schema = schema;
    this.input = input;
    this.columns = columns;
    this.size = size;
    this.firstRecord = firstRecord;
    this.quote = quote;
    this.copiedBytes = copiedBytes;
  }

  public Schema schema() {
    return schema;
  }

  public int size() {
    return size;
  }

  public CsvRow row(int index) {
    Objects.checkIndex(index, size);
    return new CsvRow(this, index);
  }

  /** Stream data-record index of row 0 (the header is not a data record). */
  public long firstRecord() {
    return firstRecord;
  }

  /** Retained input length. */
  public int inputBytes() {
    return input.length;
  }

  /** Field bytes ingest copied out of the retained input; spans reference the input instead. */
  int copiedBytes() {
    return copiedBytes;
  }

  /** Materializes one STRING span, unescaping doubled quotes. */
  @TruffleBoundary
  static String string(byte[] input, long span, byte quote) {
    if (!CsvScanner.escaped(span))
      return new String(
          input, CsvScanner.offset(span), CsvScanner.length(span), StandardCharsets.UTF_8);
    byte[] text = new byte[CsvScanner.length(span)];
    int length = CsvScanner.unescape(input, span, quote, text, 0);
    return new String(text, 0, length, StandardCharsets.UTF_8);
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
    if (index < 0 || index >= size) throw InvalidArrayIndexException.create(index);
    return new CsvRow(this, (int) index);
  }
}
