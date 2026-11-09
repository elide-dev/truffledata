package dev.elide.truffledata.csv;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A view of one STRING cell's UTF-8 bytes after unescaping, without a {@code String}. It comes in
 * two forms:
 *
 * <ul>
 *   <li><b>A cell</b> ({@link CsvRow#getText(int)}, {@link CsvRow#getText(int, CsvText)},
 *       DataLibrary and interop reads of a plan with {@link CsvPlan#withTextCells text cells}):
 *       bound to its batch, it is retained as long as the batch is and reads the batch's bytes
 *       directly. Unescaped heap cells view the batch input with no copy; escaped cells and cells of
 *       a mapped batch fetch one decoded copy of their bytes on the first byte-level read (nothing
 *       is copied for {@link #toString()}, which materializes as {@link CsvRow#getString} does). It
 *       has the batch's lifetime: once a mapped batch's mapping is closed, every read throws {@code
 *       IllegalStateException("CSV mapping closed")}; heap batches never close.
 *   <li><b>The export view</b> ({@link CsvSink#text}): one reusable instance valid only during the
 *       sink call; escaped fields view the export's scratch buffer.
 * </ul>
 *
 * <p>A text created with {@link #CsvText()} is <em>unbound</em> (every read throws {@code
 * IllegalStateException}) until a row binds it through {@link CsvRow#getText(int, CsvText)}; binding
 * it again re-points it at another cell, so one instance can walk a batch without allocating (the
 * reader keeps the text, not a copy). A text obtained from {@link CsvRow#getText(int)} is the
 * caller's to keep.
 *
 * <p><b>Equality and hashing</b> are by content: two texts are {@link #equals equal} when their
 * UTF-8 bytes after unescaping are equal, whatever their batches; {@link #hashCode} is the
 * polynomial (31) hash of those bytes, consistent with {@code equals} but not equal to {@link
 * String#hashCode} of {@link #toString()}. A text is never equal to a {@code String}; compare with
 * {@link #contentEquals} or {@code toString()}. Because a rebound text changes content, do not key
 * collections by a text that is reused.
 */
@ExportLibrary(InteropLibrary.class)
public final class CsvText implements TruffleObject {
  private static final byte[] EMPTY = new byte[0];

  /** The batch whose cell this text views; null for an export view and for an unbound text. */
  private @Nullable CsvBatch batch;

  /** The cell's span in {@link #batch} (cells only). */
  private long span;

  private boolean bound;

  /** The bytes are still in the batch (escaped or mapped cell): fetch before a byte-level read. */
  private boolean pending;

  /** {@link #length} is final; false while an escaped cell's unescaped length is unknown. */
  private boolean exact;

  /** The readable bytes once resolved (package-visible for the export test). */
  byte[] bytes = EMPTY;

  private int offset;
  private int length;
  private @Nullable String string;

  /** An unbound text, to be bound by {@link CsvRow#getText(int, CsvText)}. */
  public CsvText() {}

  static CsvText of(CsvBatch batch, long span) {
    CsvText text = new CsvText();
    text.bind(batch, span);
    return text;
  }

  /** The export view over decoded bytes. */
  void set(byte[] bytes, int offset, int length) {
    this.batch = null;
    this.bytes = bytes;
    this.offset = offset;
    this.length = length;
    bound = exact = true;
    pending = false;
    string = null;
  }

  /** Binds this text to one STRING cell of {@code batch} (a present span). */
  void bind(CsvBatch batch, long span) {
    this.batch = batch;
    this.span = span;
    this.length = CsvScanner.length(span);
    bound = true;
    string = null;
    boolean escaped = CsvScanner.escaped(span);
    if (batch.mapping == null && !escaped) {
      bytes = batch.input;
      offset = CsvScanner.offset(span);
      pending = false;
      exact = true;
    } else {
      bytes = EMPTY;
      offset = 0;
      pending = true;
      exact = !escaped;
    }
  }

  /** Every read starts here: an unbound text fails, and a cell fails once its batch is closed. */
  private void live() {
    if (!bound) throw unbound();
    CsvBatch b = batch;
    if (b != null) b.checkLive();
  }

  @TruffleBoundary
  private static IllegalStateException unbound() {
    return new IllegalStateException("CsvText is not bound to a cell");
  }

  /** The cell's bytes, fetched from the batch on first use (escaped or mapped cells). */
  private byte[] resolve() {
    if (pending) fetch();
    return bytes;
  }

  @TruffleBoundary
  private void fetch() {
    byte[] cell = Objects.requireNonNull(batch).cellBytes(span);
    bytes = cell;
    offset = 0;
    length = cell.length;
    pending = false;
    exact = true;
  }

  /** UTF-8 bytes after unescaping. */
  public int length() {
    live();
    if (!exact) fetch();
    return length;
  }

  /**
   * The length of {@link #toString()} in UTF-16 units, computed from the bytes without
   * materializing: one unit per code point, two for supplementary planes.
   */
  @BytecodeInterpreterSwitch
  public int charLength() {
    live();
    byte[] b = resolve();
    int off = offset;
    int n = length;
    if (n <= 16 && off + 16 <= b.length && Utf8.ascii(b, off, n)) return n;
    int units = 0;
    int i = off;
    int end = off + n;
    for (; i + 8 <= end && Utf8.asciiWord(b, i); i += 8) units += 8;
    for (; i < end; i++) {
      int c = b[i] & 0xFF;
      if ((c & 0xC0) != 0x80) units += c >= 0xF0 ? 2 : 1;
    }
    return units;
  }

  public byte byteAt(int index) {
    live();
    byte[] b = resolve();
    Objects.checkIndex(index, length);
    return b[offset + index];
  }

  public void copyTo(byte[] target, int offset) {
    live();
    byte[] b = resolve();
    System.arraycopy(b, this.offset, target, offset, length);
  }

  public boolean contentEquals(byte[] utf8) {
    live();
    byte[] b = resolve();
    return Arrays.equals(b, offset, offset + length, utf8, 0, utf8.length);
  }

  /** UTF-8, one copy, no intermediate String. */
  @TruffleBoundary
  public TruffleString toTruffleString() {
    live();
    byte[] b = resolve();
    return TruffleString.fromByteArrayUncached(
        b, offset, length, TruffleString.Encoding.UTF_8, true);
  }

  /**
   * The cell as a String, built as {@link CsvRow#getString} builds it; cached per binding. A
   * host-inlining root like {@link CsvBatch#stringAt}: the lifetime test and the Latin-1 fast path
   * are inlined here rather than called per cell.
   */
  @BytecodeInterpreterSwitch
  @Override
  public String toString() {
    live();
    String s = string;
    if (s != null) return s;
    CsvBatch b = batch;
    s = b != null && pending ? CsvBatch.string(b, span) : Utf8.string(bytes, offset, length);
    string = s;
    return s;
  }

  /** Content equality over the UTF-8 bytes after unescaping (see the class comment). */
  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof CsvText t)) return false;
    live();
    t.live();
    byte[] a = resolve();
    byte[] b = t.resolve();
    return Arrays.equals(a, offset, offset + length, b, t.offset, t.offset + t.length);
  }

  /** The polynomial hash of the UTF-8 bytes after unescaping (see the class comment). */
  @Override
  public int hashCode() {
    live();
    byte[] b = resolve();
    int h = 0;
    for (int i = offset, end = offset + length; i < end; i++) h = 31 * h + b[i];
    return h;
  }

  // ---- interop: a string value

  @ExportMessage
  boolean isString() {
    return true;
  }

  @ExportMessage
  @TruffleBoundary
  String asString() {
    return toString();
  }

  @ExportMessage
  @TruffleBoundary
  TruffleString asTruffleString() {
    return toTruffleString();
  }

  @ExportMessage
  @TruffleBoundary
  Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
    return toString();
  }
}
