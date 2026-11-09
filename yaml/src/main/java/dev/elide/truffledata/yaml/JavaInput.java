package dev.elide.truffledata.yaml;

/**
 * Input preparation of the Java kernel, a port of {@code yaml/native/input.h}: the encoding from a
 * byte order mark (UTF-8, UTF-16LE/BE, UTF-32LE/BE; none means UTF-8), transcoding to UTF-8, strict
 * UTF-8 validation and the YAML c-printable set. Failures carry the native messages and, for
 * validation errors, the line and column of the offending byte.
 */
final class JavaInput {
  private JavaInput() {}

  private static final int UTF8 = 0, UTF8_BOM = 1, UTF16LE = 2, UTF16BE = 3, UTF32LE = 4, UTF32BE = 5;

  /** The UTF-8 text after any byte order mark; {@code data} itself when it needs no change. */
  static byte[] prepare(byte[] data, long maxInputBytes) {
    if (data.length > Math.min(maxInputBytes, 0xffffffffL)) throw JavaFailure.limit("input bytes", 0, 0);
    int encoding = detect(data);
    byte[] text;
    switch (encoding) {
      case UTF8 -> text = data;
      case UTF8_BOM -> text = java.util.Arrays.copyOfRange(data, 3, data.length);
      case UTF16LE, UTF16BE -> text = utf16(data, encoding == UTF16LE);
      default -> text = utf32(data, encoding == UTF32LE);
    }
    validate(text);
    return text;
  }

  private static int detect(byte[] d) {
    int n = d.length;
    if (n >= 4 && d[0] == 0 && d[1] == 0 && (d[2] & 255) == 0xFE && (d[3] & 255) == 0xFF) return UTF32BE;
    if (n >= 4 && (d[0] & 255) == 0xFF && (d[1] & 255) == 0xFE && d[2] == 0 && d[3] == 0) return UTF32LE;
    if (n >= 2 && (d[0] & 255) == 0xFE && (d[1] & 255) == 0xFF) return UTF16BE;
    if (n >= 2 && (d[0] & 255) == 0xFF && (d[1] & 255) == 0xFE) return UTF16LE;
    if (n >= 3 && (d[0] & 255) == 0xEF && (d[1] & 255) == 0xBB && (d[2] & 255) == 0xBF) return UTF8_BOM;
    return UTF8;
  }

  private static byte[] utf16(byte[] d, boolean little) {
    int body = d.length - 2;
    if (body % 2 != 0) throw JavaFailure.syntax("truncated UTF-16 YAML input", 0, 0);
    int units = body / 2;
    byte[] out = new byte[Math.multiplyExact(units, 3)];
    int written = 0;
    for (int i = 0; i < units; i++) {
      int c = unit16(d, i, little);
      if (c >= 0xD800 && c <= 0xDBFF) {
        int low = i + 1 < units ? unit16(d, i + 1, little) : 0;
        if (low < 0xDC00 || low > 0xDFFF)
          throw JavaFailure.syntax("invalid UTF-16 YAML input (unpaired surrogate)", 0, 0);
        c = 0x10000 + ((c - 0xD800) << 10) + (low - 0xDC00);
        i++;
      } else if (c >= 0xDC00 && c <= 0xDFFF) {
        throw JavaFailure.syntax("invalid UTF-16 YAML input (unpaired surrogate)", 0, 0);
      }
      written = encode(c, out, written);
    }
    return java.util.Arrays.copyOf(out, written);
  }

  private static int unit16(byte[] d, int i, boolean little) {
    int a = d[2 + 2 * i] & 255, b = d[3 + 2 * i] & 255;
    return little ? a | b << 8 : a << 8 | b;
  }

  private static byte[] utf32(byte[] d, boolean little) {
    int body = d.length - 4;
    if (body % 4 != 0) throw JavaFailure.syntax("truncated UTF-32 YAML input", 0, 0);
    byte[] out = new byte[body];
    int written = 0;
    for (int i = 4; i < d.length; i += 4) {
      long c =
          little
              ? (d[i] & 255L) | (d[i + 1] & 255L) << 8 | (d[i + 2] & 255L) << 16 | (d[i + 3] & 255L) << 24
              : (d[i] & 255L) << 24 | (d[i + 1] & 255L) << 16 | (d[i + 2] & 255L) << 8 | (d[i + 3] & 255L);
      if (c > 0x10FFFF || (c >= 0xD800 && c <= 0xDFFF))
        throw JavaFailure.syntax("invalid UTF-32 YAML input", 0, 0);
      written = encode((int) c, out, written);
    }
    return java.util.Arrays.copyOf(out, written);
  }

  private static int encode(int c, byte[] out, int at) {
    if (c < 0x80) {
      out[at] = (byte) c;
      return at + 1;
    }
    if (c < 0x800) {
      out[at] = (byte) (0xC0 | c >> 6);
      out[at + 1] = (byte) (0x80 | (c & 0x3F));
      return at + 2;
    }
    if (c < 0x10000) {
      out[at] = (byte) (0xE0 | c >> 12);
      out[at + 1] = (byte) (0x80 | (c >> 6 & 0x3F));
      out[at + 2] = (byte) (0x80 | (c & 0x3F));
      return at + 3;
    }
    out[at] = (byte) (0xF0 | c >> 18);
    out[at + 1] = (byte) (0x80 | (c >> 12 & 0x3F));
    out[at + 2] = (byte) (0x80 | (c >> 6 & 0x3F));
    out[at + 3] = (byte) (0x80 | (c & 0x3F));
    return at + 4;
  }

  /** Strict UTF-8 first (its errors take precedence), then the printable set. */
  static void validate(byte[] p) {
    int n = p.length;
    for (int i = 0; i < n; ) {
      int c = p[i] & 255;
      if (c < 0x80) {
        i++;
        continue;
      }
      int need, min;
      if (c >= 0xC2 && c <= 0xDF) {
        need = 1;
        min = 0x80;
      } else if (c >= 0xE0 && c <= 0xEF) {
        need = 2;
        min = 0x800;
      } else if (c >= 0xF0 && c <= 0xF4) {
        need = 3;
        min = 0x10000;
      } else {
        throw located("invalid UTF-8 in YAML input", p, i);
      }
      if (i + need >= n)
        throw located("invalid UTF-8 in YAML input", p, i);
      int value = c & (0x3F >> need);
      for (int k = 1; k <= need; k++) {
        int b = p[i + k] & 255;
        if ((b & 0xC0) != 0x80) throw located("invalid UTF-8 in YAML input", p, i);
        value = value << 6 | (b & 0x3F);
      }
      if (value < min || value > 0x10FFFF || (value >= 0xD800 && value <= 0xDFFF))
        throw located("invalid UTF-8 in YAML input", p, i);
      i += need + 1;
    }
    for (int k = 0; k < n; k++) {
      int c = p[k] & 255;
      if (c < 0x20) {
        if (c != '\t' && c != '\n' && c != '\r')
          throw located("non-printable control character in YAML input", p, k);
      } else if (c == 0x7F) {
        throw located("non-printable control character in YAML input", p, k);
      } else if (c == 0xC2 && k + 1 < n && (p[k + 1] & 255) >= 0x80 && (p[k + 1] & 255) <= 0x9F
          && (p[k + 1] & 255) != 0x85) {
        throw located("non-printable C1 control character in YAML input", p, k);
      } else if (c == 0xEF && k + 2 < n && (p[k + 1] & 255) == 0xBF
          && ((p[k + 2] & 255) == 0xBE || (p[k + 2] & 255) == 0xBF)) {
        throw located("non-printable noncharacter U+FFFE/U+FFFF in YAML input", p, k);
      }
    }
  }

  /** Line and column of {@code offset} (1-based, the column in code points). */
  private static JavaFailure located(String message, byte[] p, int offset) {
    long line = 1, column = 1;
    for (int i = 0; i < offset; i++) {
      if (p[i] == '\n') {
        line++;
        column = 1;
      } else if ((p[i] & 0xC0) != 0x80) {
        column++;
      }
    }
    return JavaFailure.syntax(message, line, column);
  }
}
