package dev.elide.truffledata.csv;

/**
 * The structural-scan kernel: the only code a SWAR, Vector API or native fast path replaces. It maps
 * (bytes, from, to) to the next structural position and never allocates. Both scanners skip every
 * run through it; everything else (record framing, quoting rules, spans) stays in the scanners.
 *
 * <p>A final class rather than an interface so callers keep it in monomorphic final fields (SVM
 * rule: no interface-typed hot fields).
 */
final class CsvKernel {
  /** Nonzero for bytes that end an unquoted run. */
  private final boolean[] unquoted = new boolean[256];

  private final byte quote;
  private final int escape;

  /**
   * @param delimiter the delimiter's first byte
   * @param escape the escape byte, or -1 when unset
   */
  CsvKernel(byte delimiter, byte quote, boolean quoting, int escape) {
    unquoted[delimiter & 0xFF] = true;
    unquoted['\r'] = true;
    unquoted['\n'] = true;
    if (quoting) unquoted[quote & 0xFF] = true;
    if (escape >= 0) unquoted[escape & 0xFF] = true;
    this.quote = quote;
    // An unset escape never matches a byte, which is sign-extended into -128..127.
    this.escape = escape >= 0 ? (byte) escape : Integer.MIN_VALUE;
  }

  /**
   * The first index in [from, to) holding the delimiter's first byte, CR, LF, the quote (when quoting)
   * or the escape; {@code to} when there is none.
   */
  int skipUnquoted(byte[] input, int from, int to) {
    final boolean[] stop = unquoted;
    int p = from;
    while (p < to && !stop[input[p] & 0xFF]) p++;
    return p;
  }

  /** The first index in [from, to) holding the quote or the escape; {@code to} when there is none. */
  int skipQuoted(byte[] input, int from, int to) {
    final byte q = quote;
    final int e = escape;
    int p = from;
    if (e == Integer.MIN_VALUE) {
      while (p < to && input[p] != q) p++;
      return p;
    }
    while (p < to) {
      byte b = input[p];
      if (b == q || b == e) break;
      p++;
    }
    return p;
  }
}
