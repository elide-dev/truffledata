package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The kernel contract a faster (SWAR, SIMD or native) kernel must keep. */
final class CsvKernelTest {
  private static final byte[] ALPHABET = {
    'a', 'b', ' ', '\t', ',', '|', '"', '\\', '\r', '\n', (byte) 0xC3, (byte) 0xA9, (byte) 0xE4, '0'
  };

  private static int referenceUnquoted(
      byte[] in, int from, int to, byte delimiter, byte quote, boolean quoting, int escape) {
    for (int i = from; i < to; i++) {
      byte b = in[i];
      if (b == delimiter || b == '\r' || b == '\n' || (quoting && b == quote) || b == escape)
        return i;
    }
    return to;
  }

  private static int referenceQuoted(byte[] in, int from, int to, byte quote, int escape) {
    for (int i = from; i < to; i++) if (in[i] == quote || in[i] == escape) return i;
    return to;
  }

  @Test
  void matchesReferenceAtEveryOffset() {
    Random random = new Random(9);
    byte[][] delimiters = {{','}, {'\t'}, {'|'}};
    for (int round = 0; round < 2_000; round++) {
      byte delimiter = delimiters[round % 3][0];
      boolean quoting = random.nextBoolean();
      int escape = random.nextBoolean() ? '\\' : -1;
      CsvKernel kernel = new CsvKernel(delimiter, (byte) '"', quoting, escape);
      byte[] in = new byte[random.nextInt(80)];
      for (int i = 0; i < in.length; i++)
        in[i] = random.nextInt(4) == 0 ? ALPHABET[random.nextInt(ALPHABET.length)] : (byte) 'x';
      for (int from = 0; from <= in.length; from++) {
        int to = from + random.nextInt(in.length - from + 1);
        assertEquals(
            referenceUnquoted(in, from, to, delimiter, (byte) '"', quoting, escape),
            kernel.skipUnquoted(in, from, to));
        assertEquals(
            referenceQuoted(in, from, to, (byte) '"', escape), kernel.skipQuoted(in, from, to));
      }
    }
  }

  @Test
  void longOrdinaryRunsReachTheEnd() {
    CsvKernel kernel = new CsvKernel((byte) ',', (byte) '"', true, -1);
    byte[] in = new byte[1000];
    java.util.Arrays.fill(in, (byte) 'z');
    assertEquals(1000, kernel.skipUnquoted(in, 0, 1000));
    assertEquals(1000, kernel.skipQuoted(in, 0, 1000));
    in[777] = ',';
    in[900] = '"';
    assertEquals(777, kernel.skipUnquoted(in, 3, 1000));
    assertEquals(900, kernel.skipQuoted(in, 3, 1000));
  }
}
