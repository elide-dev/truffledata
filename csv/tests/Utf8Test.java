package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class Utf8Test {
  private static byte[] bytes(int... values) {
    byte[] out = new byte[values.length];
    for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
    return out;
  }

  private static int check(byte[] input) {
    return Utf8.invalidOffset(input, 0, input.length);
  }

  @Test
  void acceptsAllScalarValues() {
    int[] boundaries = {
      0, 0x41, 0x7F, 0x80, 0x7FF, 0x800, 0xFFF, 0x1000, 0xD7FF, 0xE000, 0xFFFD, 0xFFFF, 0x10000,
      0x3FFFF, 0x40000, 0xFFFFF, 0x100000, 0x10FFFF
    };
    ByteArrayOutputStream all = new ByteArrayOutputStream();
    for (int cp : boundaries) {
      byte[] encoded = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
      assertEquals(-1, check(encoded), Integer.toHexString(cp));
      all.writeBytes(encoded);
      all.writeBytes("abcdefghij".getBytes(StandardCharsets.UTF_8));
    }
    assertEquals(-1, check(all.toByteArray()));
    // Every scalar value, in one long run.
    StringBuilder every = new StringBuilder();
    for (int cp = 0; cp <= 0x10FFFF; cp++) {
      if (cp >= 0xD800 && cp <= 0xDFFF) continue;
      every.appendCodePoint(cp);
    }
    assertEquals(-1, check(every.toString().getBytes(StandardCharsets.UTF_8)));
    // A sub-range is checked on its own.
    byte[] framed = bytes(0xFF, 'a', 'b', 0xFF);
    assertEquals(-1, Utf8.invalidOffset(framed, 1, 3));
    assertEquals(3, Utf8.invalidOffset(framed, 1, 4));
  }

  @Test
  void rejectsOverlongSurrogateAndOutOfRange() {
    int[][] invalid = {
      {0xC0, 0x80},
      {0xC1, 0xBF},
      {0xE0, 0x80, 0x80},
      {0xE0, 0x9F, 0xBF},
      {0xED, 0xA0, 0x80},
      {0xED, 0xBF, 0xBF},
      {0xF0, 0x8F, 0xBF, 0xBF},
      {0xF4, 0x90, 0x80, 0x80},
      {0xF5, 0x80, 0x80, 0x80},
      {0xF8},
      {0xFF},
      {0x80},
      {0xBF},
      {0xC2, 0x41},
      {0xE1, 0x80, 0x41},
      {0xF1, 0x80, 0x80, 0xC0},
    };
    for (int[] sequence : invalid) {
      for (int prefix : new int[] {0, 1, 7, 8, 9, 16}) {
        byte[] input = new byte[prefix + sequence.length + 3];
        Arrays.fill(input, (byte) 'x');
        for (int i = 0; i < sequence.length; i++) input[prefix + i] = (byte) sequence[i];
        assertEquals(prefix, check(input), Arrays.toString(sequence) + " at " + prefix);
      }
    }
    byte[] valid = "é中😀".getBytes(StandardCharsets.UTF_8);
    byte[] then = Arrays.copyOf(valid, valid.length + 2);
    then[valid.length] = (byte) 0xED;
    then[valid.length + 1] = (byte) 0xA0;
    assertEquals(valid.length, check(then));
  }

  @Test
  void truncatedAtEnd() {
    int[][] truncated = {{0xC3}, {0xE4}, {0xE4, 0xB8}, {0xF0}, {0xF0, 0x9F}, {0xF0, 0x9F, 0x98}};
    for (int[] sequence : truncated) {
      byte[] input = new byte[11 + sequence.length];
      Arrays.fill(input, (byte) 'y');
      for (int i = 0; i < sequence.length; i++) input[11 + i] = (byte) sequence[i];
      assertEquals(11, check(input), Arrays.toString(sequence));
    }
    byte[] full = "ab😀".getBytes(StandardCharsets.UTF_8);
    assertEquals(-1, check(full));
    assertEquals(2, Utf8.invalidOffset(full, 0, full.length - 1));
  }

  @Test
  void asciiFastPathBoundaries() {
    for (int length = 1; length <= 40; length++) {
      for (int at = 0; at <= 17 && at < length; at++) {
        byte[] input = new byte[length];
        Arrays.fill(input, (byte) 'a');
        input[at] = (byte) 0x80;
        assertEquals(at, check(input), "length " + length + " at " + at);
        input[at] = (byte) 0xC3;
        // Truncated, or followed by ASCII: invalid either way.
        assertEquals(at, check(input), "lead at " + at);
        if (at + 1 < length) {
          input[at + 1] = (byte) 0xA9;
          assertEquals(-1, check(input), "valid pair at " + at);
        }
      }
    }
  }
}
