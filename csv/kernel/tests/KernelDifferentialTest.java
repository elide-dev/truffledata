package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.IndexedScanner;
import dev.elide.truffledata.csv.kernel.ScalarIndexer;
import dev.elide.truffledata.csv.kernel.SwarIndexer;
import dev.elide.truffledata.csv.kernel.TableIndexer;
import dev.elide.truffledata.csv.kernel.jvm.FfmIndexer;
import dev.elide.truffledata.csv.kernel.vector.VectorIndexer;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Every kernel against the scalar index oracle, and the indexed scanner (with strict-scanner
 * fallback) against {@link CsvScanner}, on a large randomized corpus: quotes, doubled quotes,
 * CRLF/LF/CR, blanks, multi-byte UTF-8, malformed input, and calls cut at every offset.
 */
final class KernelDifferentialTest {
  private static final Path LIBRARY =
      Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), "csv/native/csv_shared.so");
  private static final List<FfmIndexer> OPEN = new ArrayList<>();
  private static final java.util.Map<Fmt, List<CsvIndexer>> KERNELS = new java.util.HashMap<>();

  private record Fmt(byte delimiter, byte quote, boolean quoting) {}

  private static final Fmt[] FORMATS = {
    new Fmt((byte) ',', (byte) '"', true),
    new Fmt((byte) ';', (byte) '\'', true),
    new Fmt((byte) '\t', (byte) '"', true),
    new Fmt((byte) ',', (byte) '"', false),
    new Fmt((byte) '|', (byte) 'a', true),
    new Fmt((byte) 0x80, (byte) 0xFF, true),
  };

  @AfterAll
  static void close() {
    for (FfmIndexer indexer : OPEN) indexer.close();
  }

  /** Every kernel this JVM and CPU support, for one format (indexers are reset per run). */
  private static List<CsvIndexer> kernels(Fmt f) {
    return KERNELS.computeIfAbsent(f, KernelDifferentialTest::create);
  }

  private static List<CsvIndexer> create(Fmt f) {
    List<CsvIndexer> all = new ArrayList<>();
    all.add(new TableIndexer(f.delimiter, f.quote, f.quoting));
    all.add(new SwarIndexer(f.delimiter, f.quote, f.quoting));
    all.add(new VectorIndexer(f.delimiter, f.quote, f.quoting));
    for (int kernel = 1; kernel <= 5; kernel++) {
      for (int critical : new int[] {0, Integer.MAX_VALUE}) {
        try {
          FfmIndexer native_ = new FfmIndexer(LIBRARY, f.delimiter, f.quote, f.quoting, kernel, critical);
          OPEN.add(native_);
          all.add(native_);
        } catch (IllegalArgumentException unsupported) {
          // This CPU lacks the kernel's instruction set.
        }
      }
    }
    return all;
  }

  private static byte[] noise(Random random, int length, Fmt f) {
    byte[][] pieces = {
      {f.delimiter}, {f.quote}, {f.quote, f.quote}, {'\n'}, {'\r'}, {'\r', '\n'}, {' '}, {'\t'},
      {'a'}, {'7'}, "é".getBytes(StandardCharsets.UTF_8), "中".getBytes(StandardCharsets.UTF_8),
      "😀".getBytes(StandardCharsets.UTF_8),
    };
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    while (out.size() < length) out.writeBytes(pieces[random.nextInt(pieces.length)]);
    return Arrays.copyOf(out.toByteArray(), length);
  }

  /** Well-formed records (mostly), with needless quoting, blanks, empty lines and CR variants. */
  private static byte[] records(Random random, Fmt f) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int rows = random.nextInt(12);
    int width = 1 + random.nextInt(6);
    String[] values = {"", "a", "xyz", " pad ", "1.5", "é中😀", ",", "\"", "\r\n", "\n", "\r", "say \"hi\""};
    for (int r = 0; r < rows; r++) {
      if (random.nextInt(8) == 0) out.writeBytes(random.nextBoolean() ? "\r\n".getBytes() : "\n".getBytes());
      for (int c = 0; c < width; c++) {
        if (c > 0) out.write(f.delimiter);
        byte[] value = values[random.nextInt(values.length)].getBytes(StandardCharsets.UTF_8);
        boolean special = false;
        for (byte b : value) special |= b == f.delimiter || b == f.quote || b == '\r' || b == '\n' || b == '"' || b == ',';
        if (f.quoting && (special || random.nextInt(4) == 0 || (width == 1 && value.length == 0))) {
          out.write(f.quote);
          for (byte b : value) {
            out.write(b);
            if (b == f.quote) out.write(b);
          }
          out.write(f.quote);
        } else if (!special) {
          out.writeBytes(value);
        } else {
          out.write('z');
        }
      }
      int end = random.nextInt(3);
      if (r + 1 < rows || random.nextBoolean())
        out.writeBytes(end == 0 ? new byte[] {'\n'} : end == 1 ? new byte[] {'\r', '\n'} : new byte[] {'\r'});
    }
    byte[] bytes = out.toByteArray();
    // Occasionally corrupt one byte, so fallbacks and diagnostics are exercised.
    if (bytes.length > 0 && random.nextInt(6) == 0)
      bytes[random.nextInt(bytes.length)] = random.nextBoolean() ? f.quote : (byte) ' ';
    return bytes;
  }

  private static byte[] input(Random random, Fmt f, int iteration) {
    return iteration % 3 == 0 ? noise(random, random.nextInt(300), f) : records(random, f);
  }

  /** Indexes {@code input} in calls of the given lengths; entries become absolute offsets. */
  private static long[] run(CsvIndexer indexer, byte[] input, int[] cuts) {
    indexer.carry(0);
    int[] out = new int[input.length + 1];
    List<Long> all = new ArrayList<>();
    int pos = 0;
    int c = 0;
    do {
      int length = c < cuts.length ? Math.min(cuts[c], input.length - pos) : input.length - pos;
      c++;
      int n = indexer.index(input, pos, length, out);
      assertTrue(n <= length);
      for (int i = 0; i < n; i++) {
        int e = out[i];
        assertTrue(CsvIndex.offset(e) < length);
        all.add((long) (CsvIndex.offset(e) + pos) | (long) (e & CsvIndex.FLAGS) << 32);
      }
      pos += length;
    } while (pos < input.length);
    long[] result = new long[all.size() + 1];
    for (int i = 0; i < all.size(); i++) result[i] = all.get(i);
    result[all.size()] = indexer.carry() | 1L << 62;
    return result;
  }

  @Test
  void everyKernelMatchesTheScalarOracle() {
    Random random = new Random(20261106);
    int cases = 0;
    for (int iteration = 0; iteration < 1200; iteration++) {
      Fmt f = FORMATS[iteration % FORMATS.length];
      ScalarIndexer oracle = new ScalarIndexer(f.delimiter, f.quote, f.quoting);
      List<CsvIndexer> kernels = kernels(f);
      byte[] input = input(random, f, iteration);
      long[] expected = run(oracle, input, new int[0]);
      for (CsvIndexer kernel : kernels) {
        assertArrayEquals(expected, run(kernel, input, new int[0]), kernel.name());
        // One call boundary at every offset (inside CRLF, doubled quotes, UTF-8 sequences).
        if (iteration % 4 == 0)
          for (int cut = 0; cut <= input.length; cut++) {
            assertArrayEquals(expected, run(kernel, input, new int[] {cut}), kernel.name() + " cut " + cut);
            cases++;
          }
        // A random partition, including empty calls and whole-block calls.
        int[] cuts = new int[1 + input.length / 16];
        for (int i = 0; i < cuts.length; i++) cuts[i] = random.nextInt(4) == 0 ? 64 * random.nextInt(3) : random.nextInt(40);
        assertArrayEquals(expected, run(kernel, input, cuts), kernel.name() + " partition");
        cases += 2;
      }
    }
    // Large inputs through the capacity rule.
    for (int iteration = 0; iteration < 4; iteration++) {
      Fmt f = FORMATS[iteration % 3];
      byte[] input = noise(random, (1 << 20) + random.nextInt(999), f);
      long[] expected = run(new ScalarIndexer(f.delimiter, f.quote, f.quoting), input, new int[0]);
      int capacity = 64 * (1 + random.nextInt(3000)) + random.nextInt(64);
      List<Integer> cuts = new ArrayList<>();
      for (int pos = 0; pos < input.length; ) {
        int n = CsvIndex.indexable(input.length - pos, capacity);
        cuts.add(n);
        pos += n;
      }
      int[] partition = cuts.stream().mapToInt(Integer::intValue).toArray();
      for (CsvIndexer kernel : kernels(f)) assertArrayEquals(expected, run(kernel, input, partition), kernel.name());
      cases++;
    }
    assertTrue(cases > 10_000, "cases " + cases);
  }

  /** Records as text, so a mismatch prints readably. */
  private static final class Collect implements CsvScanner.Records, IndexedScanner.Records {
    final List<String> records = new ArrayList<>();
    final int declineAt;
    int reported;

    Collect(int declineAt) {
      this.declineAt = declineAt;
    }

    @Override
    public boolean record(long[] spans, int count, int start, int end) {
      if (reported == declineAt) return false;
      reported++;
      StringBuilder text = new StringBuilder().append(start).append('-').append(end).append(':');
      for (int i = 0; i < count; i++) text.append(Long.toHexString(spans[i])).append(',');
      records.add(text.toString());
      return true;
    }
  }

  private static String reference(CsvFormat format, CsvLimits limits, byte[] input, int to, boolean eof, Collect sink) {
    try {
      return "end " + new CsvScanner(format, limits).scan(input, 0, to, eof, sink);
    } catch (IllegalArgumentException failure) {
      return "error " + failure.getMessage();
    }
  }

  /** The integration shape: the indexed scanner, and the strict scanner for each fallback record. */
  private static String combined(
      IndexedScanner fast, CsvFormat format, CsvLimits limits, byte[] input, int to, boolean eof, Collect sink) {
    try {
      int pos = 0;
      while (true) {
        int stop = fast.scan(input, pos, to, eof, sink);
        if (!fast.fallback()) return "end " + stop;
        CsvScanner strict = new CsvScanner(format, limits);
        strict.recordBase = sink.reported;
        int before = sink.reported;
        int after =
            strict.scan(
                input, stop, to, eof,
                (spans, count, start, end) -> sink.reported == before && sink.record(spans, count, start, end));
        if (after == stop) return "end " + stop;
        pos = after;
      }
    } catch (IllegalArgumentException failure) {
      return "error " + failure.getMessage();
    }
  }

  @Test
  void indexedScannerMatchesTheStrictScanner() {
    Random random = new Random(7);
    int fallbacks = 0, cases = 0;
    for (int iteration = 0; iteration < 3000; iteration++) {
      Fmt f = FORMATS[iteration % 5]; // CsvFormat bytes are ASCII
      boolean trim = iteration % 5 == 1 && f.delimiter != '\t';
      CsvFormat format =
          CsvFormat.builder()
              .setDelimiter((char) f.delimiter)
              .setQuote(f.quoting ? Character.valueOf((char) f.quote) : null)
              .setHeader(false)
              .setTrim(trim)
              .setFlexible(false)
              .build();
      CsvLimits limits =
          iteration % 7 == 0 ? new CsvLimits(1 << 20, 24, 3, 1 << 20) : CsvLimits.DEFAULT;
      byte[] input = input(random, f, iteration);
      List<CsvIndexer> kernels = new ArrayList<>(kernels(f));
      kernels.add(new ScalarIndexer(f.delimiter, f.quote, f.quoting));
      for (CsvIndexer kernel : kernels) {
        IndexedScanner fast = new IndexedScanner(format, limits, kernel, 64 + random.nextInt(3) * 64);
        int declineAt = random.nextInt(4) == 0 ? random.nextInt(4) : Integer.MAX_VALUE;
        // Whole input at EOF, and a prefix without EOF (incomplete tails).
        int[] ends = {input.length, random.nextInt(input.length + 1)};
        for (int k = 0; k < 2; k++) {
          boolean eof = k == 0;
          Collect expected = new Collect(declineAt), actual = new Collect(declineAt);
          String want = reference(format, limits, input, ends[k], eof, expected);
          String got = combined(fast, format, limits, input, ends[k], eof, actual);
          String where = kernel.name() + " on " + Arrays.toString(Arrays.copyOf(input, ends[k]));
          assertEquals(want, got, where);
          assertEquals(expected.records, actual.records, where);
          cases++;
        }
        if (fast.fallback()) fallbacks++;
      }
    }
    assertTrue(cases > 20_000 && fallbacks > 100, "cases " + cases + ", fallbacks " + fallbacks);
  }

  @Test
  void nativeUtf8MatchesTheJavaValidator() {
    Random random = new Random(11);
    FfmIndexer native_ = new FfmIndexer(LIBRARY, (byte) ',', (byte) '"', true, 0, 0);
    OPEN.add(native_);
    byte[][] pieces = {
      {'a'}, "é".getBytes(StandardCharsets.UTF_8), "中".getBytes(StandardCharsets.UTF_8),
      "😀".getBytes(StandardCharsets.UTF_8), {(byte) 0x80}, {(byte) 0xC0, (byte) 0x80},
      {(byte) 0xED, (byte) 0xA0, (byte) 0x80}, {(byte) 0xF4, (byte) 0x90}, {(byte) 0xFF},
    };
    for (int iteration = 0; iteration < 20_000; iteration++) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      int length = random.nextInt(200);
      int bad = iteration % 3 == 0 ? pieces.length : 4;
      while (out.size() < length) out.writeBytes(pieces[random.nextInt(bad)]);
      byte[] bytes = out.toByteArray();
      int from = bytes.length == 0 ? 0 : random.nextInt(bytes.length);
      int to = from + random.nextInt(bytes.length - from + 1);
      int expected = Utf8.invalidOffset(bytes, from, to);
      long actual = native_.invalidUtf8(bytes, from, to - from);
      assertEquals(expected < 0 ? -1 : expected - from, actual, Arrays.toString(bytes));
    }
  }
}
