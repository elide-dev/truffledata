package dev.elide.truffledata.bebop;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/** Strict UTF-8: no overlong forms, no surrogates, nothing above U+10FFFF, nothing truncated. */
final class Utf8 {
  private static final VarHandle LONG =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

  private Utf8() {}

  /** Whether {@code [from, to)} is valid. Every load stays inside the range. */
  static boolean valid(byte[] b, int from, int to) {
    int i = from;
    while (i < to) {
      // ASCII runs, eight bytes per load.
      while (i + 8 <= to && ((long) LONG.get(b, i) & 0x8080808080808080L) == 0) i += 8;
      if (i >= to) break;
      int c = b[i];
      if (c >= 0) {
        i++;
      } else if (c >= (byte) 0xC2 && c <= (byte) 0xDF) {
        if (i + 1 >= to || !cont(b[i + 1])) return false;
        i += 2;
      } else if ((c & 0xF0) == 0xE0) {
        if (i + 2 >= to || !cont(b[i + 1]) || !cont(b[i + 2])) return false;
        int second = b[i + 1] & 0xFF;
        if (c == (byte) 0xE0 && second < 0xA0) return false;
        if (c == (byte) 0xED && second > 0x9F) return false;
        i += 3;
      } else if (c >= (byte) 0xF0 && c <= (byte) 0xF4) {
        if (i + 3 >= to || !cont(b[i + 1]) || !cont(b[i + 2]) || !cont(b[i + 3])) return false;
        int second = b[i + 1] & 0xFF;
        if (c == (byte) 0xF0 && second < 0x90) return false;
        if (c == (byte) 0xF4 && second > 0x8F) return false;
        i += 4;
      } else {
        return false;
      }
    }
    return true;
  }

  private static boolean cont(byte b) {
    return (b & 0xC0) == 0x80;
  }

  /** Whether every surrogate in the string is paired, so it has an exact UTF-8 encoding. */
  static boolean encodable(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) return false;
        i++;
      } else if (Character.isLowSurrogate(c)) {
        return false;
      }
    }
    return true;
  }
}
