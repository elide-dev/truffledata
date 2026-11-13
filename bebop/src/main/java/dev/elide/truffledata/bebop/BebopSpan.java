package dev.elide.truffledata.bebop;

import dev.elide.truffledata.bebop.BebopType.Scalar;
import java.util.Objects;

/**
 * A bounded view of one column of fixed-width little-endian values inside a {@link
 * BebopDocument}: row {@code r} is the {@link #width} bytes at {@code byteOffset + r * stride}.
 * The bytes are read in place, at whatever alignment they have; no load leaves the column and no
 * object is allocated per element. The view is bound to its document's schema identity and
 * lifetime.
 */
public final class BebopSpan {
  private final BebopDocument owner;
  private final Scalar kind;
  private final int count;
  private final int stride;
  private final int offset;

  BebopSpan(BebopDocument owner, Scalar kind, int count, int stride, int offset) {
    this.owner = owner;
    this.kind = kind;
    this.count = count;
    this.stride = stride;
    this.offset = offset;
  }

  public BebopDocument document() {
    return owner;
  }

  /** The element type, which fixes width, signedness and interpretation. */
  public Scalar kind() {
    return kind;
  }

  /** Bytes per element. */
  public int width() {
    return kind.width();
  }

  /** Rows: {@code [0, count)}. */
  public int count() {
    return count;
  }

  /** Bytes between consecutive rows; larger than the width for a column of an array of structs. */
  public int stride() {
    return stride;
  }

  /** Offset of row 0 in the document. */
  public int byteOffset() {
    return offset;
  }

  /** Bytes from the first row's first byte to the last row's last byte. */
  public int byteLength() {
    return count == 0 ? 0 : (count - 1) * stride + kind.width();
  }

  private int at(int row) {
    return offset + Objects.checkIndex(row, count) * stride;
  }

  /** The element's bits, zero-extended. For types of at most eight bytes. */
  public long bits(int row) {
    byte[] b = owner.bytes();
    int at = at(row);
    return switch (kind.width()) {
      case 1 -> b[at] & 0xFFL;
      case 2 -> (short) Writer.I16.get(b, at) & 0xFFFFL;
      case 4 -> (int) Writer.I32.get(b, at) & 0xFFFFFFFFL;
      case 8 -> (long) Writer.I64.get(b, at);
      default -> throw new UnsupportedOperationException(kind + " is wider than eight bytes");
    };
  }

  /**
   * The element as an integer: signed types sign-extended, unsigned types zero-extended ({@code
   * uint64} as its bits), booleans as 0 or 1.
   */
  public long getLong(int row) {
    long bits = bits(row);
    return switch (kind) {
      case BOOL -> bits != 0 ? 1 : 0;
      case BYTE, UINT16, UINT32, INT64, UINT64 -> bits;
      case INT8 -> (byte) bits;
      case INT16 -> (short) bits;
      case INT32 -> (int) bits;
      default -> throw new UnsupportedOperationException(kind + " is not an integer");
    };
  }

  /** The element as a floating-point number, for the floating-point types. */
  public double getDouble(int row) {
    long bits = bits(row);
    return switch (kind) {
      case FLOAT16 -> BebopCodec.fromFloat16((short) bits);
      case BFLOAT16 -> Float.intBitsToFloat((int) bits << 16);
      case FLOAT32 -> Float.intBitsToFloat((int) bits);
      case FLOAT64 -> Double.longBitsToDouble(bits);
      default -> throw new UnsupportedOperationException(kind + " is not floating-point");
    };
  }

  /** Copies rows {@code [from, from + length)} as integers. */
  public void copyLongs(int from, long[] target, int targetOffset, int length) {
    Objects.checkFromIndexSize(from, length, count);
    Objects.checkFromIndexSize(targetOffset, length, target.length);
    for (int i = 0; i < length; i++) target[targetOffset + i] = getLong(from + i);
  }

  /** Copies rows {@code [from, from + length)} as floating-point numbers. */
  public void copyDoubles(int from, double[] target, int targetOffset, int length) {
    Objects.checkFromIndexSize(from, length, count);
    Objects.checkFromIndexSize(targetOffset, length, target.length);
    for (int i = 0; i < length; i++) target[targetOffset + i] = getDouble(from + i);
  }

  /** Copies one element's bytes, of any width. */
  public void copyBytes(int row, byte[] target, int targetOffset) {
    Objects.checkFromIndexSize(targetOffset, kind.width(), target.length);
    System.arraycopy(owner.bytes(), at(row), target, targetOffset, kind.width());
  }
}
