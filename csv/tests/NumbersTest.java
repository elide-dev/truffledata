package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The INT64 and FLOAT64 conversions against exact oracles: {@link Long#parseLong} and {@link
 * Double#parseDouble} under the documented grammars, with every token placed at many offsets so
 * the SWAR paths (which load the eight or sixteen bytes before the field's end) and the scalar
 * paths both run, and the Eisel-Lemire table recomputed from its definition.
 */
final class NumbersTest {
  private static final Pattern LONG = Pattern.compile("-?[0-9]+");
  private static final Pattern DOUBLE =
      Pattern.compile("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?");

  /** {@code token} inside an array, after {@code prefix} filler bytes and before 3 more. */
  private static byte[] placed(String token, int prefix, byte filler) {
    byte[] t = token.getBytes(StandardCharsets.ISO_8859_1);
    byte[] out = new byte[prefix + t.length + 3];
    java.util.Arrays.fill(out, filler);
    System.arraycopy(t, 0, out, prefix, t.length);
    return out;
  }

  private static final byte[] FILLERS = {',', '9', '.', '-', (byte) 0xFF, '\n', '0'};

  private static void checkLong(String token, Random random) {
    Long expected = null;
    if (LONG.matcher(token).matches()) {
      try {
        expected = Long.parseLong(token);
      } catch (NumberFormatException overflow) {
        expected = null;
      }
    }
    for (int prefix : new int[] {0, 1, 7, 8, 9, 15, 16, 17, 24, random.nextInt(40)}) {
      byte[] bytes = placed(token, prefix, FILLERS[random.nextInt(FILLERS.length)]);
      if (expected == null) {
        assertThrows(NumberFormatException.class, () -> Numbers.parseLong(bytes, prefix, token.length()), token);
      } else {
        assertEquals(expected.longValue(), Numbers.parseLong(bytes, prefix, token.length()), token + " at " + prefix);
      }
    }
  }

  private static void checkDouble(String token, Random random) {
    Double expected = null;
    if (DOUBLE.matcher(token).matches()) {
      double d = Double.parseDouble(token);
      if (!Double.isInfinite(d)) expected = d;
    }
    for (int prefix : new int[] {0, 1, 7, 8, 9, 16, 17, random.nextInt(40)}) {
      byte[] bytes = placed(token, prefix, FILLERS[random.nextInt(FILLERS.length)]);
      if (expected == null) {
        assertThrows(NumberFormatException.class, () -> Numbers.parseDouble(bytes, prefix, token.length()), token);
      } else {
        double actual = Numbers.parseDouble(bytes, prefix, token.length());
        assertEquals(
            Double.doubleToRawLongBits(expected),
            Double.doubleToRawLongBits(actual),
            () -> token + " at " + prefix + ": " + actual);
      }
    }
  }

  @Test
  void powerOfFiveTableMatchesItsDefinition() {
    long[] table = Numbers.POW5;
    assertEquals(2 * (308 + 342 + 1), table.length);
    BigInteger two127 = BigInteger.ONE.shiftLeft(127);
    BigInteger two128 = BigInteger.ONE.shiftLeft(128);
    for (int q = -342; q <= 308; q++) {
      BigInteger c;
      if (q >= 0) {
        c = BigInteger.valueOf(5).pow(q);
        while (c.compareTo(two127) < 0) c = c.shiftLeft(1);
        while (c.compareTo(two128) >= 0) c = c.shiftRight(1);
      } else {
        BigInteger p = BigInteger.valueOf(5).pow(-q);
        int z = p.subtract(BigInteger.ONE).bitLength(); // smallest z with 2^z >= p
        int b = q >= -27 ? z + 127 : 2 * z + 128;
        c = BigInteger.ONE.shiftLeft(b).divide(p).add(BigInteger.ONE);
        while (c.compareTo(two128) >= 0) c = c.shiftRight(1);
      }
      int index = 2 * (q + 342);
      BigInteger actual =
          new BigInteger(Long.toUnsignedString(table[index]))
              .shiftLeft(64)
              .or(new BigInteger(Long.toUnsignedString(table[index + 1])));
      assertEquals(c, actual, "q=" + q);
    }
  }

  @Test
  void integersMatchTheGrammarAtEveryPlacement() {
    Random random = new Random(1);
    List<String> tokens =
        new ArrayList<>(
            List.of(
                "0", "-0", "7", "-7", "12345678", "123456789", "1234567812345678", "12345678123456789",
                "9223372036854775807", "-9223372036854775808", "9223372036854775808",
                "-9223372036854775809", "00000000000000000000042", "", "-", "+1", "1-", "1 ", " 1",
                "12a4", "１", "99999999", "9999999999999999", "0000000000000000"));
    String alphabet = "0123456789-+ a.";
    for (int i = 0; i < 40_000; i++) {
      StringBuilder sb = new StringBuilder();
      if (random.nextInt(3) == 0) sb.append('-');
      int n = random.nextInt(4) == 0 ? random.nextInt(22) : 1 + random.nextInt(17);
      for (int k = 0; k < n; k++) {
        if (random.nextInt(60) == 0) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        else sb.append((char) ('0' + random.nextInt(10)));
      }
      tokens.add(sb.toString());
    }
    for (int i = 0; i < 2_000; i++) tokens.add(Long.toString(random.nextLong()));
    for (String token : tokens) checkLong(token, random);
  }

  @Test
  void doublesMatchTheJdkAtEveryPlacement() {
    Random random = new Random(2);
    List<String> tokens =
        new ArrayList<>(
            List.of(
                "0", "-0", "+0", "0.0", "-0.000", ".5", "5.", "-.5", "+5.", ".", "-.", "", "+", "e5",
                "1e", "1e+", "1.5e-3", "1E400", "-1e400", "1e-400", "-1e-400", "nan", "1.2.3", "1..2",
                "12345678.12345678", "123456789.1234567", "99999999.99999999", "9007199254740993",
                "9007199254740993.0", "900719925474099.3", "2.2250738585072011e-308",
                "2.2250738585072012e-308", "4.9406564584124654e-324", "2.4703282292062327e-324",
                "2.4703282292062328e-324", "1.7976931348623157e308", "1.7976931348623158e308",
                "1.7976931348623159e308", "179769313486231580793728971405303415079934132710037826936173778980444968292764750946649017977587207096330286416692887910946555547851940402630657488671505820681908902000708383676273854845817711531764475730270069855571366959622842914819860834936475292719074168444365510704342711559699508093042880177904174497791",
                "0.1", "0.2", "0.3", "1e22", "1e23", "8.98846567431158e307", "7.2057594037927933e16",
                "9007199254740992e22", "123456789012345678901234567890", "0.00000000000000000000000000001",
                "1.00000000000000011102230246251565404236316680908203125",
                "1.00000000000000011102230246251565404236316680908203124",
                "1.00000000000000011102230246251565404236316680908203126"));
    // Random grammar soup.
    String alphabet = "0123456789.eE+-x ";
    for (int i = 0; i < 30_000; i++) {
      StringBuilder sb = new StringBuilder();
      int n = random.nextInt(14);
      for (int k = 0; k < n; k++) {
        if (random.nextInt(3) == 0) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        else sb.append((char) ('0' + random.nextInt(10)));
      }
      tokens.add(sb.toString());
    }
    // Decimals in the fast-path shape and around it.
    for (int i = 0; i < 60_000; i++) {
      StringBuilder sb = new StringBuilder();
      if (random.nextInt(4) == 0) sb.append(random.nextBoolean() ? '-' : '+');
      int whole = random.nextInt(11);
      int fraction = random.nextInt(11);
      for (int k = 0; k < whole; k++) sb.append((char) ('0' + random.nextInt(10)));
      if (random.nextInt(8) != 0) sb.append('.');
      for (int k = 0; k < fraction; k++) sb.append((char) ('0' + random.nextInt(10)));
      if (random.nextInt(5) == 0) sb.append(random.nextBoolean() ? 'e' : 'E').append(random.nextInt(61) - 30);
      tokens.add(sb.toString());
    }
    // Long significands and every exponent range (Eisel-Lemire, truncation, under- and overflow).
    for (int i = 0; i < 60_000; i++) {
      StringBuilder sb = new StringBuilder();
      int digits = 1 + random.nextInt(random.nextInt(4) == 0 ? 40 : 20);
      int point = random.nextInt(digits + 2) - 1;
      for (int d = 0; d < digits; d++) {
        if (d == point) sb.append('.');
        sb.append((char) ('0' + random.nextInt(10)));
      }
      sb.append('e').append(random.nextInt(720) - 360);
      tokens.add(sb.toString());
    }
    // Shortest representations of random doubles, normal and subnormal.
    for (int i = 0; i < 40_000; i++) {
      double d = Double.longBitsToDouble(random.nextLong() & 0x7FFF_FFFF_FFFF_FFFFL);
      if (!Double.isFinite(d)) continue;
      tokens.add(Double.toString(d));
      tokens.add(new BigDecimal(d).toString().replace("E+", "e"));
    }
    long before = Numbers.fallbacks();
    for (String token : tokens) checkDouble(token, random);
    long fallbacks = Numbers.fallbacks() - before;
    // The exact fallback is for products too close to call: rare outside exact ties.
    assertTrue(fallbacks < tokens.size() / 100, "fallbacks " + fallbacks + " of " + tokens.size());
    tokens.clear();
    // Exact midpoints between adjacent doubles (ties to even), and their neighbours. Their
    // significands need more than 19 digits, so these usually take the exact fallback.
    for (int i = 0; i < 20_000; i++) {
      double d =
          random.nextBoolean()
              ? Double.longBitsToDouble(random.nextLong() & 0x7FEF_FFFF_FFFF_FFFFL)
              : random.nextInt(1 << 20) * Math.ulp(1.0) * (1L << random.nextInt(60));
      if (!Double.isFinite(d) || !Double.isFinite(Math.nextUp(d))) continue;
      BigDecimal mid = new BigDecimal(d).add(new BigDecimal(Math.nextUp(d))).divide(BigDecimal.valueOf(2));
      BigDecimal tiny = BigDecimal.ONE.movePointLeft(mid.scale() + 3);
      for (BigDecimal v : new BigDecimal[] {mid, mid.add(tiny), mid.subtract(tiny)}) {
        String s = v.toString();
        if (s.length() < 900) tokens.add(s.replace("E+", "e"));
      }
    }
    for (String token : tokens) checkDouble(token, random);
  }

  @Test
  void typicalTokensNeverFallBack() {
    long before = Numbers.fallbacks();
    for (String token :
        List.of(
            "1.5", "-0.25", "123456789012345", "9007199254740992", "1e22", "3.14159e-7", "12345.678901",
            "0.000123", "-98765432.1", "1e-300", "6.02214076e23", "1.7976931348623157e308",
            "4.9e-324", "123456789012345678", "0.1234567890123456789")) {
      byte[] bytes = placed(token, 9, (byte) ',');
      assertEquals(
          Double.doubleToRawLongBits(Double.parseDouble(token)),
          Double.doubleToRawLongBits(Numbers.parseDouble(bytes, 9, token.length())),
          token);
    }
    assertEquals(before, Numbers.fallbacks());
  }

  @Test
  void eiselLemireEdges() {
    assertEquals(0, Numbers.eiselLemire(1, -343));
    assertEquals(Numbers.INFINITY_BITS, Numbers.eiselLemire(1, 309));
    assertEquals(Double.doubleToRawLongBits(1.0), Numbers.eiselLemire(1, 0));
    assertEquals(Double.doubleToRawLongBits(Double.MIN_VALUE), Numbers.eiselLemire(49406564584124654L, -340));
    assertEquals(Double.doubleToRawLongBits(Double.MAX_VALUE), Numbers.eiselLemire(17976931348623157L, 292));
    assertEquals(Numbers.INFINITY_BITS, Numbers.eiselLemire(17976931348623159L, 292));
    assertEquals(Double.doubleToRawLongBits(Double.MIN_NORMAL), Numbers.eiselLemire(22250738585072014L, -324));
    assertEquals(0, Numbers.eiselLemire(1, -400));
  }
}
