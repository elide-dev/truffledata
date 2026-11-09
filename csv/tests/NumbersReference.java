package dev.elide.truffledata.csv;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * The INT64 and FLOAT64 parsers exactly as shipped before the WS10b conversion work
 * ({@code Numbers} at {@code f775a9a}), kept verbatim as the differential oracle for {@link
 * NumbersDifferentialTest}. Only the class name, the shared {@code POW5} table reference and the
 * boundary annotation differ.
 */
final class NumbersReference {
  private static final VarHandle LONGS =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
  private static final double[] POWERS = {
    1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15, 1e16,
    1e17, 1e18, 1e19, 1e20, 1e21, 1e22
  };
  private static final long[] TENS = {
    1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L
  };
  private static final long MAX_EXACT = 1L << 53;
  private static final int EXPONENT_CAP = 100_000; // far beyond any finite or nonzero binary64
  private static final long ZEROS = 0x3030_3030_3030_3030L;
  private static final long DOTS = 0x2E2E_2E2E_2E2E_2E2EL;
  private static final long LOW7 = 0x7F7F_7F7F_7F7F_7F7FL;
  private static final long INFINITY_BITS = 0x7FF0_0000_0000_0000L;

  /** Slow-path count (benign races; diagnostics only). */
  private static long fallbacks;

  private NumbersReference() {}

  static long fallbacks() {
    return fallbacks;
  }

  /** {@code -?[0-9]+} within int64; leading zeros allowed. */
  static long parseLong(byte[] b, int off, int len) {
    int end = off + len;
    int i = off;
    boolean negative = i < end && b[i] == '-';
    if (negative) i++;
    int digits = end - i;
    if (digits <= 0) throw invalid();
    if (digits <= 8 && end >= 8) {
      long value = digits(b, end, digits);
      if (value < 0) throw invalid();
      return negative ? -value : value;
    }
    if (digits <= 16 && end >= 16) {
      long high = digits(b, end - 8, digits - 8);
      long low = digits(b, end, 8);
      if ((high | low) < 0) throw invalid();
      long value = high * 100_000_000L + low;
      return negative ? -value : value;
    }
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
   * The value of the {@code count} (0..8) bytes ending at {@code end} (at least 8) as decimal
   * digits, or -1 when one of them is not a digit. The eight bytes before {@code end} are loaded
   * and those before the field are replaced by '0', which only adds leading zeros.
   */
  private static long digits(byte[] b, int end, int count) {
    long w = (long) LONGS.get(b, end - 8);
    long keep = count == 0 ? 0 : -1L << ((8 - count) << 3);
    w = (w & keep) | (ZEROS & ~keep);
    // Every byte is 0x30..0x39 (fast_float's eight-digit test; a carry out of a byte >= 0xFA only
    // reaches later bytes, after one that already fails).
    long check =
        (w & 0xF0F0_F0F0_F0F0_F0F0L) | (((w + 0x0606_0606_0606_0606L) & 0xF0F0_F0F0_F0F0_F0F0L) >>> 4);
    if (check != 0x3333_3333_3333_3333L) return -1;
    w = ((w & 0x0F0F_0F0F_0F0F_0F0FL) * 2561) >>> 8;
    w = ((w & 0x00FF_00FF_00FF_00FFL) * 6553601) >>> 16;
    return ((w & 0x0000_FFFF_0000_FFFFL) * 42949672960001L) >>> 32;
  }

  /**
   * {@code [+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]+)?}, correctly rounded; non-finite
   * results throw.
   */
  static double parseDouble(byte[] b, int off, int len) {
    int end = off + len;
    int i = off;
    boolean negative = false;
    if (i < end && (b[i] == '+' || b[i] == '-')) negative = b[i++] == '-';
    int n = end - i;
    if (n > 0 && n <= 16 && i >= 8 && end >= 16) {
      // At most one dot, at most eight digits on each side, nothing else (else the full grammar).
      int dot = dot(b, i, end);
      int whole = dot - i;
      int fraction = dot == end ? 0 : end - dot - 1;
      if (dot >= 0 && whole <= 8 && fraction <= 8 && whole + fraction > 0) {
        long high = digits(b, dot, whole);
        long low = fraction == 0 ? 0 : digits(b, end, fraction);
        if ((high | low) >= 0) {
          long significand = high * TENS[fraction] + low;
          if (significand <= MAX_EXACT) {
            double value = significand / POWERS[fraction];
            return negative ? -value : value;
          }
          long bits = eiselLemire(significand, -fraction);
          if (bits >= 0) {
            double value = Double.longBitsToDouble(bits);
            return negative ? -value : value;
          }
        }
      }
    }
    return grammar(b, off, len, i, end, negative);
  }

  /**
   * The offset of the only dot in [from, to), {@code to} when there is none, or -1 when there are
   * two or more. Requires {@code to - from <= 16} and {@code to >= 16}.
   */
  private static int dot(byte[] b, int from, int to) {
    long low = dots((long) LONGS.get(b, to - 8));
    long high = dots((long) LONGS.get(b, to - 16));
    int before = 16 - (to - from); // window bytes before the field
    if (before >= 8) {
      low &= -1L << ((before - 8) << 3);
      high = 0;
    } else high &= -1L << (before << 3);
    // At most one marked byte in both words, without Long.bitCount: -march=compatibility images
    // have no POPCNT and compute each count in software (about 30 instructions).
    if ((low | high) == 0) return to;
    if ((low & (low - 1)) != 0 || (high & (high - 1)) != 0 || (low != 0 && high != 0)) return -1;
    return low != 0
        ? to - 8 + (Long.numberOfTrailingZeros(low) >>> 3)
        : to - 16 + (Long.numberOfTrailingZeros(high) >>> 3);
  }

  /** The high bit of every byte of {@code w} that is '.' (exact zero-byte test). */
  private static long dots(long w) {
    long t = w ^ DOTS;
    return ~(((t & LOW7) + LOW7) | t | LOW7);
  }

  /** The full grammar, from {@code i} (after the sign) to {@code end}. */
  private static double grammar(byte[] b, int off, int len, int i, int end, boolean negative) {
    long significand = 0; // unsigned: at most 19 significant digits
    int significant = 0; // digits accumulated after leading zeros
    boolean exact = true; // false once a nonzero digit is dropped (more than 19 significant)
    int scale = 0;
    int digits = 0;
    for (; i < end && isDigit(b[i]); i++, digits++) {
      int digit = b[i] - '0';
      if (significant < 19) {
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
        if (significant < 19) {
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
    if (exact && significand >= 0 && significand <= MAX_EXACT && power >= -22 && power <= 22) {
      double value = significand;
      value = power < 0 ? value / POWERS[-power] : value * POWERS[power];
      return negative ? -value : value;
    }
    long bits = eiselLemire(significand, power);
    // A truncated significand: the value lies strictly between it and its successor.
    if (!exact && bits != eiselLemire(significand + 1, power)) bits = -1;
    if (bits >= 0) {
      if (bits == INFINITY_BITS) throw invalid();
      double value = Double.longBitsToDouble(bits);
      return negative ? -value : value;
    }
    double value = fallback(b, off, len);
    if (Double.isInfinite(value)) throw invalid();
    return value;
  }

  /**
   * The bits of the binary64 nearest to {@code w * 10^q} (ties to even) for an unsigned {@code w
   * != 0}, {@link #INFINITY_BITS} on overflow, or -1 when the truncated product cannot decide (the
   * caller then falls back). fast_float's {@code compute_float<binary64>}.
   */
  static long eiselLemire(long w, int q) {
    if (q < -342) return 0;
    if (q > 308) return INFINITY_BITS;
    int lz = Long.numberOfLeadingZeros(w);
    w <<= lz;
    int index = 2 * (q + 342);
    long high = Math.unsignedMultiplyHigh(w, Numbers.POW5[index]);
    long low = w * Numbers.POW5[index];
    if ((high & 0x1FF) == 0x1FF) {
      long second = Math.unsignedMultiplyHigh(w, Numbers.POW5[index + 1]);
      low += second;
      if (Long.compareUnsigned(second, low) > 0) high++;
    }
    // Lemire's original guard: outside q in -27..55 an all-ones low word may hide a carry, so such
    // products go to the exact fallback.
    if (low == -1L && (q < -27 || q > 55)) return -1;
    int upper = (int) (high >>> 63);
    int shift = upper + 9; // 64 - 52 mantissa bits - 3
    long mantissa = high >>> shift;
    int power2 = ((217706 * q) >> 16) + 63 + upper - lz + 1023;
    if (power2 <= 0) {
      // Subnormal (or the smallest normal, after rounding up).
      if (-power2 + 1 >= 64) return 0;
      mantissa >>>= -power2 + 1;
      mantissa += mantissa & 1;
      mantissa >>>= 1;
      return mantissa < (1L << 52) ? mantissa : 1L << 52;
    }
    // Exactly halfway (only possible for -4 <= q <= 23): round to even.
    if (Long.compareUnsigned(low, 1) <= 0 && q >= -4 && q <= 23 && (mantissa & 3) == 1) {
      if ((mantissa << shift) == high) mantissa &= ~1L;
    }
    mantissa += mantissa & 1;
    mantissa >>>= 1;
    if (mantissa >= (2L << 52)) {
      mantissa = 1L << 52;
      power2++;
    }
    mantissa &= ~(1L << 52);
    if (power2 >= 0x7FF) return INFINITY_BITS;
    return (long) power2 << 52 | mantissa;
  }

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
