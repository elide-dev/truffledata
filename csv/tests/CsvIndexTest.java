package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.csv.kernel.BlockStep;
import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.SwarIndexer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The index contract helpers, the SWAR kernel's edges, and the scanner's use of a kernel. */
final class CsvIndexTest {
  @Test
  void entryAndCarryHelpers() {
    int entry = 17 | CsvIndex.QUOTED | CsvIndex.ESCAPED;
    assertEquals(17, CsvIndex.offset(entry));
    assertTrue(CsvIndex.quoted(entry));
    assertTrue(CsvIndex.escaped(entry));
    assertFalse(CsvIndex.unquoted(entry));
    assertTrue(CsvIndex.unquoted(entry | CsvIndex.UNQUOTED));
    assertEquals(0, CsvIndex.pendingFlags(CsvIndex.CARRY_UNQUOTED)); // unquoted bytes alone are plain
    assertEquals(
        CsvIndex.QUOTED | CsvIndex.ESCAPED | CsvIndex.UNQUOTED,
        CsvIndex.pendingFlags(CsvIndex.CARRY_QUOTED | CsvIndex.CARRY_ESCAPED | CsvIndex.CARRY_UNQUOTED));
    assertEquals(128, CsvIndex.indexable(200, 130));
    assertEquals(100, CsvIndex.indexable(100, 100));
    String[] names = {"kernel0", "scalar", "swar", "neon", "avx2", "avx512", "kernel6"};
    for (int k = 0; k < names.length; k++) assertEquals(names[k], CsvIndex.kernelName(k));
  }

  @Test
  void indexerArgumentsAreChecked() {
    assertThrows(IllegalArgumentException.class, () -> new SwarIndexer((byte) '\n', (byte) '"', true));
    assertThrows(IllegalArgumentException.class, () -> new SwarIndexer((byte) '\r', (byte) '"', false));
    assertThrows(IllegalArgumentException.class, () -> new SwarIndexer((byte) ',', (byte) ',', true));
    assertThrows(IllegalArgumentException.class, () -> new SwarIndexer((byte) ',', (byte) '\n', true));
    assertThrows(IllegalArgumentException.class, () -> new SwarIndexer((byte) ',', (byte) '\r', true));
    new SwarIndexer((byte) ',', (byte) ',', false); // the quote is ordinary without quoting
    CsvIndexer swar = new SwarIndexer((byte) ',', (byte) '"', true);
    assertEquals("java-swar", swar.name());
    assertEquals(CsvIndexer.NO_VALIDATOR, swar.validateUtf8(new byte[4], 0, 4));
    assertThrows(IllegalArgumentException.class, () -> swar.carry(32));
    swar.carry(CsvIndex.CARRY_MASK);
    assertEquals(CsvIndex.CARRY_MASK, swar.carry());
    byte[] input = new byte[8];
    assertThrows(IndexOutOfBoundsException.class, () -> swar.index(input, -1, 2, new int[8]));
    assertThrows(IndexOutOfBoundsException.class, () -> swar.index(input, 4, 5, new int[8]));
    assertThrows(IndexOutOfBoundsException.class, () -> swar.index(input, 0, -1, new int[8]));
    assertThrows(IllegalArgumentException.class, () -> swar.index(input, 0, 8, new int[7]));
    assertEquals('b', BlockStep.pad((byte) 'a', (byte) '"', true));
    assertEquals('c', BlockStep.pad((byte) 'b', (byte) 'a', true));
    assertEquals('b', BlockStep.pad((byte) ',', (byte) 'a', true));
    assertEquals('a', BlockStep.pad((byte) ',', (byte) 'a', false));
  }

  /** SWAR against a byte-at-a-time reading of the contract, across carries and cut points. */
  @Test
  void swarMatchesTheContract() {
    Random random = new Random(5);
    byte[] pieces = {',', '"', '\n', '\r', 'a', ' ', (byte) 0xC3, (byte) 0xA9};
    for (int i = 0; i < 2_000; i++) {
      byte[] input = new byte[random.nextInt(300)];
      for (int k = 0; k < input.length; k++) input[k] = pieces[random.nextInt(pieces.length)];
      int[] expected = reference(input);
      SwarIndexer swar = new SwarIndexer((byte) ',', (byte) '"', true);
      int[] out = new int[input.length];
      int[] actual = new int[input.length];
      int count = 0;
      for (int pos = 0; pos < input.length; ) {
        int length = Math.min(input.length - pos, 1 + random.nextInt(130));
        int n = swar.index(input, pos, length, out);
        for (int k = 0; k < n; k++) actual[count++] = (out[k] & CsvIndex.FLAGS) | (pos + CsvIndex.offset(out[k]));
        pos += length;
      }
      assertArrayEquals(expected, java.util.Arrays.copyOf(actual, count));
    }
  }

  private static int[] reference(byte[] input) {
    int[] out = new int[input.length];
    int n = 0;
    boolean inside = false, closing = false, q = false, e = false, u = false;
    for (int i = 0; i < input.length; i++) {
      byte b = input[i];
      if (b == '"') {
        inside = !inside;
        if (inside && closing) e = true;
        closing = !inside;
        q = true;
        continue;
      }
      closing = false;
      if (!inside && (b == ',' || b == '\n' || b == '\r')) {
        out[n++] = i | (q ? CsvIndex.QUOTED : 0) | (e ? CsvIndex.ESCAPED : 0) | (q && u ? CsvIndex.UNQUOTED : 0);
        q = e = u = false;
      } else if (!inside) u = true;
    }
    return java.util.Arrays.copyOf(out, n);
  }

  /** Records wider than the initial span table grow it on the index path too. */
  @Test
  void wideRecordsOnTheIndexPath() {
    StringBuilder sb = new StringBuilder();
    for (int r = 0; r < 20; r++) {
      for (int c = 0; c < 40; c++) sb.append(c > 0 ? "," : "").append(c % 3 == 0 ? "\"q\"" : "v" + c);
      sb.append('\n');
    }
    byte[] input = sb.toString().getBytes(StandardCharsets.UTF_8);
    assertTrue(input.length >= CsvIndexing.MIN_INDEX_BYTES);
    CsvBatch batch =
        IndexedDifferentialTest.with(CsvIndexing.JAVA, () -> Csv.parse(input, CsvFormat.RFC4180.withHeader(false)));
    assertEquals(20, batch.size());
    assertEquals(40, batch.schema().size());
    assertEquals("v38", batch.row(19).getString(38));
    assertEquals("q", batch.row(19).getString(39));
    CsvBatch commons =
        IndexedDifferentialTest.with(
            CsvIndexing.JAVA, () -> Csv.parse(input, CsvFormat.Predefined.DEFAULT.format()));
    assertEquals("v38", commons.row(19).getString(38));
  }

  /** An indexer with its own validator (the Java rules), called in chunks of the index capacity. */
  private static final CsvIndexing.Provider VALIDATING =
      new CsvIndexing.Provider() {
        @Override
        public String name() {
          return "validating";
        }

        @Override
        public CsvIndexer create(byte delimiter, byte quote, boolean quoting) {
          SwarIndexer swar = new SwarIndexer(delimiter, quote, quoting);
          return new CsvIndexer(delimiter, quote, quoting) {
            @Override
            public String name() {
              return "validating";
            }

            @Override
            public int index(byte[] input, int from, int length, int[] out) {
              swar.carry(carry);
              int n = swar.index(input, from, length, out);
              carry = swar.carry();
              return n;
            }

            @Override
            public long validateUtf8(byte[] input, int from, int length) {
              // A chunk must never end inside a sequence that continues in the next one.
              int bad = Utf8.invalidOffset(input, from, from + length);
              return bad < 0 ? -1 : bad - from;
            }
          };
        }

        @Override
        public int capacity() {
          return 64;
        }
      };

  @Test
  void chunkedValidationFindsTheFirstInvalidByte() {
    Random random = new Random(9);
    byte[][] pieces = {
      {'a'}, {','}, {'\n'}, "é".getBytes(StandardCharsets.UTF_8), "中".getBytes(StandardCharsets.UTF_8),
      "😀".getBytes(StandardCharsets.UTF_8), {(byte) 0x80}, {(byte) 0xF0, (byte) 0x9F}, {(byte) 0xED, (byte) 0xA0, (byte) 0x80}
    };
    for (int i = 0; i < 3_000; i++) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      int length = random.nextInt(400);
      boolean clean = random.nextBoolean();
      while (out.size() < length) out.writeBytes(pieces[random.nextInt(clean ? 6 : pieces.length)]);
      byte[] input = out.toByteArray();
      int from = input.length > 0 ? random.nextInt(Math.min(3, input.length)) : 0;
      int expected = Utf8.invalidOffset(input, from, input.length);
      for (CsvIndexing.Provider p : new CsvIndexing.Provider[] {VALIDATING, IndexedDifferentialTest.SMALL}) {
        int actual =
            IndexedDifferentialTest.with(
                p,
                () -> new CsvScanner(CsvFormat.RFC4180, CsvLimits.DEFAULT).invalidUtf8(input, from, input.length));
        assertEquals(expected, actual, p.name());
      }
    }
    // Without the index path, the Java validator runs directly.
    byte[] bad = {'a', (byte) 0xFF};
    assertEquals(1, IndexedDifferentialTest.with(null, () -> new CsvScanner(CsvFormat.RFC4180, CsvLimits.DEFAULT).invalidUtf8(bad, 0, 2)));
  }
}
