package dev.elide.truffledata.csv;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/** Strict UTF-8 validation: no overlongs, surrogates, values above U+10FFFF or truncation. */
final class Utf8 {
  private static final VarHandle LONGS =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
  private static final long HIGH_BITS = 0x8080_8080_8080_8080L;

  private Utf8() {}

  /** -1 when [from, to) is strict UTF-8, else the offset of the first invalid or truncated sequence. */
  static int invalidOffset(byte[] bytes, int from, int to) {
    int i = from;
    while (i < to) {
      if (i + 8 <= to && ((long) LONGS.get(bytes, i) & HIGH_BITS) == 0) {
        i += 8;
        continue;
      }
      int lead = bytes[i] & 0xFF;
      if (lead < 0x80) {
        i++;
        continue;
      }
      int trailing;
      int low = 0x80;
      int high = 0xBF;
      if (lead >= 0xC2 && lead <= 0xDF) trailing = 1;
      else if (lead >= 0xE0 && lead <= 0xEF) {
        trailing = 2;
        if (lead == 0xE0) low = 0xA0; // overlong
        else if (lead == 0xED) high = 0x9F; // surrogates
      } else if (lead >= 0xF0 && lead <= 0xF4) {
        trailing = 3;
        if (lead == 0xF0) low = 0x90; // overlong
        else if (lead == 0xF4) high = 0x8F; // above U+10FFFF
      } else return i;
      if (i + trailing >= to) return i;
      int second = bytes[i + 1] & 0xFF;
      if (second < low || second > high) return i;
      for (int k = 2; k <= trailing; k++) if ((bytes[i + k] & 0xC0) != 0x80) return i;
      i += trailing + 1;
    }
    return -1;
  }
}
