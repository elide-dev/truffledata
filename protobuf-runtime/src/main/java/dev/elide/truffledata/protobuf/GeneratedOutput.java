package dev.elide.truffledata.protobuf;

import dev.elide.truffledata.*;

/** Bounded output cursor for generated codecs; no scratch buffer or sizing cursor is allocated. */
public final class GeneratedOutput {
  private final byte[] target;
  private int position;

  public GeneratedOutput(byte[] target) {
    this.target = target;
  }

  private void reserve(int count) {
    if (count < 0 || count > target.length - position)
      throw new ContractViolation("Encoded wire budget exceeded");
  }

  public static int varintSize(long value) {
    return value == 0 ? 1 : (70 - Long.numberOfLeadingZeros(value)) / 7;
  }

  public void varint(long value) {
    reserve(varintSize(value));
    while ((value & ~127L) != 0) {
      target[position++] = (byte) (((int) value & 127) | 128);
      value >>>= 7;
    }
    target[position++] = (byte) value;
  }

  /** Generated callers have already bounded the exact destination size. */
  public static int varint(byte[] target, int position, long value) {
    while ((value & ~127L) != 0) {
      target[position++] = (byte) (((int) value & 127) | 128);
      value >>>= 7;
    }
    target[position++] = (byte) value;
    return position;
  }

  public void fixed(long value, int width) {
    if (width != 4 && width != 8) throw new IllegalArgumentException("Invalid fixed width");
    reserve(width);
    for (int i = 0; i < width; i++) {
      target[position++] = (byte) value;
      value >>>= 8;
    }
  }

  public static int utf8Length(String value) {
    long length = 0;
    for (int i = 0; i < value.length(); i++) {
      int c = value.charAt(i);
      if (c < 128) length++;
      else if (c < 2048) length += 2;
      else if (Character.isHighSurrogate((char) c)) {
        if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i)))
          throw new ContractViolation("Invalid Unicode scalar");
        length += 4;
      } else {
        if (Character.isLowSurrogate((char) c))
          throw new ContractViolation("Invalid Unicode scalar");
        length += 3;
      }
    }
    if (length > Integer.MAX_VALUE) throw new ContractViolation("Encoded string too large");
    return (int) length;
  }

  /** Generated callers supply the previously checked UTF-8 length of this immutable string. */
  public void utf8(String value, int length) {
    varint(length);
    reserve(length);
    position = utf8Contents(target, position, value, length);
  }

  /** Package-private: callers first validate Unicode and reserve its exact encoded length. */
  @SuppressWarnings("deprecation")
  static int utf8Contents(byte[] target, int position, String value, int length) {
    if (length == value.length()) {
      // A checked UTF-8 length equals UTF-16 length only for ASCII. Under that proof,
      // String's low-byte bulk copy is exact and avoids another character loop or buffer.
      value.getBytes(0, length, target, position);
      return position + length;
    }
    for (int i = 0; i < value.length(); i++) {
      int c = value.charAt(i);
      if (c < 128) target[position++] = (byte) c;
      else if (c < 2048) {
        target[position++] = (byte) (0xc0 | (c >>> 6));
        target[position++] = (byte) (0x80 | (c & 63));
      } else if (Character.isHighSurrogate((char) c)) {
        c = Character.toCodePoint((char) c, value.charAt(++i));
        target[position++] = (byte) (0xf0 | (c >>> 18));
        target[position++] = (byte) (0x80 | ((c >>> 12) & 63));
        target[position++] = (byte) (0x80 | ((c >>> 6) & 63));
        target[position++] = (byte) (0x80 | (c & 63));
      } else {
        target[position++] = (byte) (0xe0 | (c >>> 12));
        target[position++] = (byte) (0x80 | ((c >>> 6) & 63));
        target[position++] = (byte) (0x80 | (c & 63));
      }
    }
    return position;
  }

  public void bytes(DataArray value) {
    if (!value.isBytes()) throw new ContractViolation("Expected byte storage");
    int length = value.size();
    varint(length);
    reserve(length);
    value.copyBytesTo(target, position);
    position += length;
  }

  void raw(byte[] value) {
    reserve(value.length);
    System.arraycopy(value, 0, target, position, value.length);
    position += value.length;
  }

  public byte[] finish() {
    if (position != target.length) throw new ContractViolation("Generated output size mismatch");
    return target;
  }

  public static long integer(Object value) {
    if (value instanceof Long number) return number;
    try {
      return com.oracle.truffle.api.interop.InteropLibrary.getUncached()
          .asBigInteger(value)
          .longValue();
    } catch (com.oracle.truffle.api.interop.UnsupportedMessageException invalid) {
      throw new ContractViolation("Expected exact integer");
    }
  }
}
