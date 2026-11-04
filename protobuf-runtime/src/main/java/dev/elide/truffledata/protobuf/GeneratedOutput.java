package dev.elide.truffledata.protobuf;

import dev.elide.truffledata.*;
import org.jspecify.annotations.Nullable;

/** Exact-size output for generated codecs; string sizing and UTF-8 writes allocate no scratch. */
public final class GeneratedOutput {
  private final byte @Nullable [] target;
  private final int limit;
  private int position;

  GeneratedOutput(byte @Nullable [] target, int limit) {
    if (limit < 0 || (target != null && limit > target.length))
      throw new IllegalArgumentException("Invalid output limit");
    this.target = target;
    this.limit = limit;
  }

  int size() {
    return position;
  }

  private void reserve(int count) {
    if (count < 0 || count > limit - position)
      throw new ContractViolation("Encoded wire budget exceeded");
  }

  private void octet(int value) {
    reserve(1);
    if (target != null) target[position] = (byte) value;
    position++;
  }

  public void varint(long value) {
    while ((value & ~127L) != 0) {
      octet(((int) value & 127) | 128);
      value >>>= 7;
    }
    octet((int) value);
  }

  public void fixed(long value, int width) {
    if (width != 4 && width != 8) throw new IllegalArgumentException("Invalid fixed width");
    reserve(width);
    for (int i = 0; i < width; i++) {
      octet((int) value);
      value >>>= 8;
    }
  }

  public void string(String value) {
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
    varint(length);
    reserve((int) length);
    if (target == null) {
      position += (int) length;
      return;
    }
    for (int i = 0; i < value.length(); i++) {
      int c = value.charAt(i);
      if (c < 128) octet(c);
      else if (c < 2048) {
        octet(0xc0 | (c >>> 6));
        octet(0x80 | (c & 63));
      } else if (Character.isHighSurrogate((char) c)) {
        c = Character.toCodePoint((char) c, value.charAt(++i));
        octet(0xf0 | (c >>> 18));
        octet(0x80 | ((c >>> 12) & 63));
        octet(0x80 | ((c >>> 6) & 63));
        octet(0x80 | (c & 63));
      } else {
        octet(0xe0 | (c >>> 12));
        octet(0x80 | ((c >>> 6) & 63));
        octet(0x80 | (c & 63));
      }
    }
  }

  public void bytes(DataArray value) {
    if (!value.isBytes()) throw new ContractViolation("Expected byte storage");
    int length = value.size();
    varint(length);
    reserve(length);
    if (target != null) value.copyBytesTo(target, position);
    position += length;
  }

  void raw(byte[] value) {
    reserve(value.length);
    if (target != null) System.arraycopy(value, 0, target, position, value.length);
    position += value.length;
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
