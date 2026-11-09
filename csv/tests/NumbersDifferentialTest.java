package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.comparison.CsvFixture;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * {@link Numbers} against {@link NumbersReference}, the parsers as shipped before the WS10b
 * conversion work: every token must give the same double bits (or the same long), or both must
 * reject it. Tokens are placed after filler bytes that look like digits, dots, signs and non-ASCII
 * so the word loads that end a field see every kind of neighbour, and the benchmark fixtures are
 * parsed field by field in place. {@link NumbersTest} separately checks both grammars against the
 * JDK.
 */
final class NumbersDifferentialTest {
  private static final byte[] FILLERS = {',', '9', '0', '.', '-', '+', 'e', (byte) 0xFF, '\n', '"', 0};
  private static final int[] PREFIXES = {0, 1, 7, 8, 9, 15, 16, 17, 24};

  private long checks;

  private static final Object REJECTED = new Object() {
    @Override
    public String toString() {
      return "rejected";
    }
  };

  private interface Parse {
    Object run();
  }

  private static Object outcome(Parse parse) {
    try {
      return parse.run();
    } catch (NumberFormatException e) {
      return REJECTED;
    }
  }

  private void check(byte[] b, int off, int len) {
    Object expectedDouble = outcome(() -> Double.doubleToRawLongBits(NumbersReference.parseDouble(b, off, len)));
    Object actualDouble = outcome(() -> Double.doubleToRawLongBits(Numbers.parseDouble(b, off, len)));
    if (!expectedDouble.equals(actualDouble)) {
      fail("FLOAT64 " + describe(b, off, len) + ": reference " + show(expectedDouble) + ", new " + show(actualDouble));
    }
    Object expectedLong = outcome(() -> NumbersReference.parseLong(b, off, len));
    Object actualLong = outcome(() -> Numbers.parseLong(b, off, len));
    if (!expectedLong.equals(actualLong)) {
      fail("INT64 " + describe(b, off, len) + ": reference " + expectedLong + ", new " + actualLong);
    }
    checks++;
  }

  private static String show(Object bits) {
    return bits instanceof Long l ? Double.longBitsToDouble(l) + " (0x" + Long.toHexString(l) + ")" : bits.toString();
  }

  private static String describe(byte[] b, int off, int len) {
    StringBuilder sb = new StringBuilder("'");
    for (int i = off; i < off + len; i++) {
      int c = b[i] & 0xFF;
      if (c >= 0x20 && c < 0x7F) sb.append((char) c);
      else sb.append(String.format("\\x%02x", c));
    }
    sb.append("' at ").append(off).append(" after '");
    for (int i = Math.max(0, off - 16); i < off; i++) {
      int c = b[i] & 0xFF;
      sb.append(c >= 0x20 && c < 0x7F ? String.valueOf((char) c) : String.format("\\x%02x", c));
    }
    return sb.append('\'').toString();
  }

  /** {@code token} at every placement in {@link #PREFIXES} plus one random one. */
  private void placed(byte[] token, Random random) {
    for (int k = 0; k <= PREFIXES.length; k++) {
      int prefix = k < PREFIXES.length ? PREFIXES[k] : random.nextInt(40);
      byte[] out = new byte[prefix + token.length + 3];
      // One filler in four is a uniformly random byte: bytes 1..9 would read as digit values if a
      // word path forgot to subtract '0' from a byte outside the field.
      for (int i = 0; i < out.length; i++) {
        out[i] = random.nextInt(4) == 0 ? (byte) random.nextInt(256) : FILLERS[random.nextInt(FILLERS.length)];
      }
      System.arraycopy(token, 0, out, prefix, token.length);
      check(out, prefix, token.length);
    }
  }

  private void placed(String token, Random random) {
    placed(token.getBytes(StandardCharsets.ISO_8859_1), random);
  }

  private static String digits(Random random, int n) {
    StringBuilder sb = new StringBuilder(n);
    for (int i = 0; i < n; i++) sb.append((char) ('0' + random.nextInt(10)));
    return sb.toString();
  }

  private static String sign(Random random) {
    return switch (random.nextInt(4)) {
      case 0 -> "-";
      case 1 -> "+";
      default -> "";
    };
  }

  @Test
  void generatedCorpusMatchesTheReference() {
    Random random = new Random(20261109);
    long before = checks;
    // Every word-path shape: 1..18 bytes after the sign, a dot at every position or none.
    for (int n = 1; n <= 18; n++) {
      for (int dot = -1; dot < n; dot++) {
        for (int k = 0; k < 120; k++) {
          String s = digits(random, n);
          if (dot >= 0) s = s.substring(0, dot) + '.' + s.substring(dot + 1);
          if (random.nextInt(6) == 0) s = s.replace('1', '0').replace('2', '0'); // leading/trailing zeros
          placed(sign(random) + s, random);
        }
      }
    }
    // Every byte value at every position of the word-path shapes (the SWAR digit and dot tests).
    for (int n = 1; n <= 17; n++) {
      for (int position = 0; position < n; position++) {
        for (int value = 0; value < 256; value++) {
          byte[] token = digits(random, n).getBytes(StandardCharsets.ISO_8859_1);
          int dot = random.nextInt(n + 1) - 1;
          if (dot >= 0 && dot != position) token[dot] = '.';
          token[position] = (byte) value;
          placed(token, random);
        }
      }
    }
    // Grammar soup, including exponents, signs, spaces and non-ASCII.
    String alphabet = "0123456789.eE+- xÿ";
    for (int i = 0; i < 40_000; i++) {
      StringBuilder sb = new StringBuilder();
      int n = random.nextInt(24);
      for (int k = 0; k < n; k++) {
        if (random.nextInt(3) == 0) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        else sb.append((char) ('0' + random.nextInt(10)));
      }
      placed(sb.toString(), random);
    }
    // Near 2^53, where the integer path's exactness bound sits.
    for (long v = (1L << 53) - 40; v <= (1L << 53) + 40; v++) {
      String s = Long.toString(v);
      placed(s, random);
      placed("-" + s, random);
      for (int dot = 0; dot <= s.length(); dot++) placed(s.substring(0, dot) + '.' + s.substring(dot), random);
    }
    // Boundary exponents: every q the power table covers, its edges, and beyond.
    for (int q = -400; q <= 400; q++) {
      for (String mantissa : new String[] {"1", "9", "4.9", "2.4703282292062328", "1.7976931348623157", "9.999999999999999"}) {
        placed(mantissa + "e" + q, random);
        placed("-" + mantissa + "E" + (q >= 0 ? "+" : "") + q, random);
      }
    }
    for (String s :
        List.of(
            "4.9406564584124654e-324", "2.4703282292062327e-324", "2.4703282292062328e-324",
            "2.2250738585072011e-308", "2.2250738585072012e-308", "2.2250738585072014e-308",
            "1.7976931348623157e308", "1.7976931348623158e308", "1.7976931348623159e308", "1e23",
            "8.98846567431158e307", "-0", "-0.", "-.0", "+0.000", "0e0", "-0e-0", ".", "-.", "+.", "..",
            "1..", ".1.", "e", "1e", "1e+", "-", "+", "")) {
      placed(s, random);
    }
    // 17-20 digit significands (truncation and the successor check) at every exponent range.
    for (int i = 0; i < 40_000; i++) {
      int n = 17 + random.nextInt(4);
      String s = digits(random, n);
      int point = random.nextInt(n + 2) - 1;
      if (point >= 0 && point <= n) s = s.substring(0, point) + '.' + s.substring(point);
      if (random.nextBoolean()) s += "e" + (random.nextInt(700) - 350);
      placed(sign(random) + s, random);
    }
    // Shortest representations of random doubles, normal and subnormal.
    for (int i = 0; i < 20_000; i++) {
      double d = Double.longBitsToDouble(random.nextLong() & 0x7FFF_FFFF_FFFF_FFFFL);
      if (!Double.isFinite(d)) continue;
      placed(Double.toString(d), random);
      placed(new BigDecimal(d).toPlainString().length() < 40 ? new BigDecimal(d).toPlainString() : Double.toString(-d), random);
    }
    // Exact midpoints between adjacent doubles (ties to even) and their neighbours.
    for (int i = 0; i < 4_000; i++) {
      double d =
          random.nextBoolean()
              ? Double.longBitsToDouble(random.nextLong() & 0x7FEF_FFFF_FFFF_FFFFL)
              : random.nextInt(1 << 20) * Math.ulp(1.0) * (1L << random.nextInt(60));
      if (!Double.isFinite(d) || !Double.isFinite(Math.nextUp(d))) continue;
      BigDecimal mid = new BigDecimal(d).add(new BigDecimal(Math.nextUp(d))).divide(BigDecimal.valueOf(2));
      BigDecimal tiny = BigDecimal.ONE.movePointLeft(mid.scale() + 3);
      for (BigDecimal v : new BigDecimal[] {mid, mid.add(tiny), mid.subtract(tiny)}) {
        String s = v.toString();
        if (s.length() < 900) placed(s.replace("E+", "e"), random);
      }
    }
    // Short decimals with a halfway-looking last digit (exact division by 10^k).
    for (int i = 0; i < 20_000; i++) {
      int whole = random.nextInt(9);
      int fraction = random.nextInt(9);
      placed(sign(random) + digits(random, whole) + "." + digits(random, fraction) + (random.nextBoolean() ? "5" : ""), random);
    }
    // Integers: every length to 21 digits, the int64 edges and their neighbours.
    for (int i = 0; i < 40_000; i++) placed(sign(random) + digits(random, 1 + random.nextInt(21)), random);
    for (long edge : new long[] {Long.MAX_VALUE, Long.MIN_VALUE, 99_999_999L, 100_000_000L, 9_999_999_999_999_999L}) {
      for (long delta = -3; delta <= 3; delta++) {
        placed(Long.toString(edge + delta), random);
      }
    }
    for (String s : List.of("9223372036854775808", "-9223372036854775809", "00000000000000000000009223372036854775807", "-00000000000000000000000")) {
      placed(s, random);
    }
    long corpus = checks - before;
    System.out.println("NumbersDifferentialTest: generated corpus " + corpus + " placed tokens");
    assertTrue(corpus > 1_000_000, "corpus " + corpus);
  }

  @Test
  void benchmarkFixturesMatchTheReferenceFieldByField() {
    long before = checks;
    for (String dataset : CsvFixture.DATASETS) {
      byte[] input = CsvFixture.generate(dataset, 1 << 20);
      // RFC 4180 fields; a quoted field's content (between its quotes) is parsed in place too.
      int start = 0;
      boolean quoted = false;
      for (int i = 0; i < input.length; i++) {
        byte c = input[i];
        if (c == '"') quoted = !quoted;
        else if (!quoted && (c == ',' || c == '\n')) {
          int from = start;
          int to = i;
          if (to - from >= 2 && input[from] == '"' && input[to - 1] == '"') {
            from++;
            to--;
          }
          check(input, from, to - from);
          start = i + 1;
        }
      }
    }
    long fields = checks - before;
    System.out.println("NumbersDifferentialTest: fixture fields " + fields);
    assertTrue(fields > 100_000, "fields " + fields);
  }
}
