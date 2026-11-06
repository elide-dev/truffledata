package dev.elide.truffledata.csv;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * A reusable view of one STRING cell's UTF-8 bytes, after unescaping. It is valid only during the
 * {@link CsvSink#text} call that receives it: the export reuses it for the next cell. Unescaped
 * fields view the batch input directly; escaped fields view the export's scratch buffer.
 */
public final class CsvText {
  private static final byte[] EMPTY = new byte[0];

  byte[] bytes = EMPTY;
  private int offset;
  private int length;

  CsvText() {}

  void set(byte[] bytes, int offset, int length) {
    this.bytes = bytes;
    this.offset = offset;
    this.length = length;
  }

  /** UTF-8 bytes after unescaping. */
  public int length() {
    return length;
  }

  public byte byteAt(int index) {
    Objects.checkIndex(index, length);
    return bytes[offset + index];
  }

  public void copyTo(byte[] target, int offset) {
    System.arraycopy(bytes, this.offset, target, offset, length);
  }

  public boolean contentEquals(byte[] utf8) {
    return Arrays.equals(bytes, offset, offset + length, utf8, 0, utf8.length);
  }

  /** UTF-8, one copy, no intermediate String. */
  @TruffleBoundary
  public TruffleString toTruffleString() {
    return TruffleString.fromByteArrayUncached(
        bytes, offset, length, TruffleString.Encoding.UTF_8, true);
  }

  @TruffleBoundary
  @Override
  public String toString() {
    return new String(bytes, offset, length, StandardCharsets.UTF_8);
  }
}
