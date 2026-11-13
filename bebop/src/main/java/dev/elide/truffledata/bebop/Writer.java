package dev.elide.truffledata.bebop;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

/** A growable little-endian output with a byte limit, and the bounded loads readers share. */
final class Writer {
  static final VarHandle I16 =
      MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
  static final VarHandle I32 =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
  static final VarHandle I64 =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
  static final VarHandle B64 =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

  byte[] buf;
  int pos;
  private final int max;

  Writer(int capacity, int max) {
    this.buf = new byte[Math.min(capacity, max)];
    this.max = max;
  }

  /** Reserves {@code n} bytes and returns their offset. */
  int reserve(int n) {
    int at = pos;
    if (n > max - at) throw BebopException.limit("Encoded size exceeds the byte limit");
    if (n > buf.length - at)
      buf = Arrays.copyOf(buf, (int) Math.min(max, Math.max((long) buf.length * 2, (long) at + n)));
    pos = at + n;
    return at;
  }

  // reserve() may replace buf, so the offset is taken before buf is read.
  void u8(int v) {
    int at = reserve(1);
    buf[at] = (byte) v;
  }

  void u16(int v) {
    int at = reserve(2);
    I16.set(buf, at, (short) v);
  }

  void u32(int v) {
    int at = reserve(4);
    I32.set(buf, at, v);
  }

  void u64(long v) {
    int at = reserve(8);
    I64.set(buf, at, v);
  }

  void b64(long v) {
    int at = reserve(8);
    B64.set(buf, at, v);
  }

  void bytes(byte[] src, int from, int length) {
    int at = reserve(length);
    System.arraycopy(src, from, buf, at, length);
  }

  byte[] toByteArray() {
    return Arrays.copyOf(buf, pos);
  }
}
