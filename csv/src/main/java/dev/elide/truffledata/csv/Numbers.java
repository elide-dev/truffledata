package dev.elide.truffledata.csv;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.nio.charset.StandardCharsets;

/** Exact INT64 and FLOAT64 grammars over byte ranges (ingest only; never partially evaluated). */
final class Numbers {
  private static final double[] POWERS = {
    1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15, 1e16,
    1e17, 1e18, 1e19, 1e20, 1e21, 1e22
  };
  private static final long MAX_EXACT = 1L << 53;
  private static final int EXPONENT_CAP = 100_000; // far beyond any finite or nonzero binary64

  /** Slow-path count (benign races; diagnostics only). */
  private static long fallbacks;

  private Numbers() {}

  static long fallbacks() {
    return fallbacks;
  }

  /** {@code -?[0-9]+} within int64; leading zeros allowed. */
  static long parseLong(byte[] b, int off, int len) {
    int i = off;
    int end = off + len;
    boolean negative = i < end && b[i] == '-';
    if (negative) i++;
    if (i >= end) throw invalid();
    // Accumulate negatively so Long.MIN_VALUE is representable.
    long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
    long floor = limit / 10;
    long result = 0;
    for (; i < end; i++) {
      int digit = b[i] - '0';
      if (digit < 0 || digit > 9) throw invalid();
      if (result < floor) throw invalid();
      result *= 10;
      if (result < limit + digit) throw invalid();
      result -= digit;
    }
    return negative ? result : -result;
  }

  /**
   * {@code [+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]+)?}, correctly rounded; non-finite
   * results throw. Clinger's fast path is exact when the significand fits 2^53 and the decimal
   * exponent is within ±22; other tokens fall back to {@link Double#parseDouble}.
   */
  static double parseDouble(byte[] b, int off, int len) {
    int i = off;
    int end = off + len;
    boolean negative = false;
    if (i < end && (b[i] == '+' || b[i] == '-')) negative = b[i++] == '-';
    long significand = 0;
    int significant = 0; // digits accumulated after leading zeros
    boolean exact = true; // false once a nonzero digit is dropped (more than 18 significant)
    int scale = 0;
    int digits = 0;
    for (; i < end && isDigit(b[i]); i++, digits++) {
      int digit = b[i] - '0';
      if (significant < 18) {
        significand = significand * 10 + digit;
        if (significand != 0) significant++;
      } else {
        scale++;
        if (digit != 0) exact = false;
      }
    }
    if (i < end && b[i] == '.') {
      for (i++; i < end && isDigit(b[i]); i++, digits++) {
        int digit = b[i] - '0';
        if (significant < 18) {
          significand = significand * 10 + digit;
          if (significand != 0) significant++;
          scale--;
        } else if (digit != 0) exact = false;
      }
    }
    if (digits == 0) throw invalid();
    int exponent = 0;
    if (i < end && (b[i] == 'e' || b[i] == 'E')) {
      i++;
      boolean negativeExponent = false;
      if (i < end && (b[i] == '+' || b[i] == '-')) negativeExponent = b[i++] == '-';
      int start = i;
      for (; i < end && isDigit(b[i]); i++)
        if (exponent < EXPONENT_CAP) exponent = exponent * 10 + (b[i] - '0');
      if (i == start) throw invalid();
      if (negativeExponent) exponent = -exponent;
    }
    if (i != end) throw invalid();
    if (significand == 0 && exact) return negative ? -0.0 : 0.0;
    int power = scale + exponent;
    if (exact && significand <= MAX_EXACT && power >= -22 && power <= 22) {
      double value = significand;
      value = power < 0 ? value / POWERS[-power] : value * POWERS[power];
      return negative ? -value : value;
    }
    double value = fallback(b, off, len);
    if (Double.isInfinite(value)) throw invalid();
    return value;
  }

  @TruffleBoundary
  private static double fallback(byte[] b, int off, int len) {
    fallbacks++;
    return Double.parseDouble(new String(b, off, len, StandardCharsets.ISO_8859_1));
  }

  private static boolean isDigit(byte value) {
    return value >= '0' && value <= '9';
  }

  private static NumberFormatException invalid() {
    return new NumberFormatException();
  }
}
