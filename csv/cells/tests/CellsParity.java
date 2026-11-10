package dev.elide.truffledata.csv.cells;

import dev.elide.truffledata.csv.Csv;
import dev.elide.truffledata.csv.CsvBatch;
import dev.elide.truffledata.csv.CsvFormat;
import dev.elide.truffledata.csv.CsvIndexing;
import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Flag-on parity of the CSV cells route against the baseline route, shared by HotSpot
 * (CellsRouteTest) and the Native Image (CellsAcceptance): identical index entries, counts and
 * carries for every supported kernel on both sides of the leaf split, identical UTF-8 offsets,
 * identical parses through the installed providers, and the same errors (type and message) in the
 * same order. Plain Java, no JUnit, so the image runs it as is.
 */
final class CellsParity {
  /** Constructs one route's indexer for a kernel id and leaf/critical split. */
  interface Route {
    CsvIndexer create(byte delimiter, byte quote, boolean quoting, int kernel, int split);
  }

  private static final byte[][] FORMATS = {{',', '"', 1}, {';', '\'', 1}, {'\t', '"', 0}};

  private CellsParity() {}

  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError("CSV cells parity: " + message);
  }

  /** Runs every check; returns the number of kernel/split pairs compared. */
  static int check(
      Route baseline, Route cells, CsvIndexing.Provider base, CsvIndexing.Provider routed)
      throws Exception {
    int compared = 0;
    for (int kernel = 0; kernel <= 6; kernel++)
      for (int split : new int[] {0, Integer.MAX_VALUE}) {
        int id = kernel;
        CsvIndexer probe;
        try {
          probe = baseline.create((byte) ',', (byte) '"', true, id, split);
        } catch (RuntimeException failure) {
          // An unsupported kernel fails the same way on both routes.
          sameFailure(failure, () -> { close(cells.create((byte) ',', (byte) '"', true, id, split)); });
          continue;
        }
        close(probe);
        for (byte[] format : FORMATS) {
          CsvIndexer a = baseline.create(format[0], format[1], format[2] != 0, id, split);
          CsvIndexer b = cells.create(format[0], format[1], format[2] != 0, id, split);
          try {
            index(a, b, format, new Random(31L * id + split + format[0]));
            utf8(a, b, new Random(id + format[0]));
            errors(a, b);
          } finally {
            close(a);
            close(b);
          }
        }
        compared++;
      }
    require(compared >= 2, "no kernel compared");
    parses(base, routed);
    return compared;
  }

  /**
   * The routes' cells are shared by every indexer, so indexers on several threads call the same
   * cells at once: each thread runs its own stream and UTF-8 comparison over the best kernel.
   */
  static void concurrent(Route baseline, Route cells, int threads) throws Exception {
    Throwable[] failures = new Throwable[threads];
    Thread[] workers = new Thread[threads];
    for (int t = 0; t < threads; t++) {
      int id = t;
      workers[t] =
          new Thread(
              () -> {
                try {
                  for (int round = 0; round < 4; round++) {
                    byte[] format = FORMATS[(id + round) % FORMATS.length];
                    int split = (id + round) % 2 == 0 ? 0 : Integer.MAX_VALUE;
                    CsvIndexer a = baseline.create(format[0], format[1], format[2] != 0, 0, split);
                    CsvIndexer b = cells.create(format[0], format[1], format[2] != 0, 0, split);
                    try {
                      index(a, b, format, new Random(977L * id + round));
                      utf8(a, b, new Random(id + 13L * round));
                    } finally {
                      close(a);
                      close(b);
                    }
                  }
                } catch (Throwable failure) {
                  failures[id] = failure;
                }
              });
      workers[t].start();
    }
    for (Thread worker : workers) worker.join();
    for (Throwable failure : failures)
      if (failure != null) throw new AssertionError("CSV cells parity: concurrent use", failure);
  }

  /**
   * An indexer handed to another thread between calls (scanners are not thread-safe, but they are
   * not thread-bound either): the stream continues with the same entries and carry.
   */
  static void handOff(Route baseline, Route cells) throws Exception {
    byte[] format = FORMATS[0];
    CsvIndexer a = baseline.create(format[0], format[1], true, 0, Integer.MAX_VALUE);
    CsvIndexer b = cells.create(format[0], format[1], true, 0, Integer.MAX_VALUE);
    try {
      index(a, b, format, new Random(5));
      Throwable[] failure = new Throwable[1];
      Thread other =
          new Thread(
              () -> {
                try {
                  index(a, b, format, new Random(6));
                  utf8(a, b, new Random(7));
                } catch (Throwable thrown) {
                  failure[0] = thrown;
                }
              });
      other.start();
      other.join();
      if (failure[0] != null)
        throw new AssertionError("CSV cells parity: hand-off to another thread", failure[0]);
    } finally {
      close(a);
      close(b);
    }
  }

  private static void close(CsvIndexer indexer) {
    if (indexer instanceof AutoCloseable closeable)
      try {
        closeable.close();
      } catch (Exception failure) {
        throw new IllegalStateException(failure);
      }
  }

  private static byte[] noise(Random random, int length, byte[] f) {
    byte[][] pieces = {
      {f[0]}, {f[1]}, {f[1], f[1]}, {'\n'}, {'\r'}, {'\r', '\n'}, {' '}, {'a'}, {'7'},
      "é".getBytes(StandardCharsets.UTF_8), "中".getBytes(StandardCharsets.UTF_8),
      "😀".getBytes(StandardCharsets.UTF_8), "plain".getBytes(StandardCharsets.UTF_8),
    };
    byte[] out = new byte[length];
    int at = 0;
    while (at < length) {
      byte[] piece = pieces[random.nextInt(pieces.length)];
      int n = Math.min(piece.length, length - at);
      System.arraycopy(piece, 0, out, at, n);
      at += n;
    }
    return out;
  }

  /** One stream per input, cut at random offsets (aligned and not), carries threaded through. */
  private static void index(CsvIndexer a, CsvIndexer b, byte[] f, Random random) {
    int[] sizes = {0, 1, 63, 64, 65, 1000, 4096, 70_000, 200_000};
    for (int size : sizes) {
      byte[] input = noise(random, size, f);
      a.carry(0);
      b.carry(0);
      int pos = 0;
      do {
        int remaining = input.length - pos;
        int length =
            Math.min(remaining, random.nextBoolean() ? random.nextInt(70_000) : 64 * random.nextInt(1100));
        int[] outA = new int[length + 1];
        int[] outB = new int[length + 1];
        Arrays.fill(outB, -7);
        int na = a.index(input, pos, length, outA);
        int nb = b.index(input, pos, length, outB);
        require(na == nb, "entry count at " + pos + "+" + length + ": " + na + " vs " + nb);
        require(
            Arrays.equals(outA, 0, na, outB, 0, nb), "entries at " + pos + "+" + length);
        require(a.carry() == b.carry(), "carry at " + pos + "+" + length);
        pos += length;
      } while (pos < input.length);
    }
  }

  private static void utf8(CsvIndexer a, CsvIndexer b, Random random) {
    for (int round = 0; round < 64; round++) {
      byte[] input = noise(random, random.nextInt(9000), new byte[] {',', '"', 1});
      if (round % 2 == 1 && input.length > 0)
        input[random.nextInt(input.length)] = (byte) (0x80 + random.nextInt(0x80));
      int from = input.length == 0 ? 0 : random.nextInt(input.length);
      int length = input.length - from == 0 ? 0 : random.nextInt(input.length - from + 1);
      long va = a.validateUtf8(input, from, length);
      long vb = b.validateUtf8(input, from, length);
      require(va == vb, "UTF-8 offset " + va + " vs " + vb);
    }
  }

  /** The baseline's argument errors, raised identically and before any native call. */
  private static void errors(CsvIndexer a, CsvIndexer b) {
    byte[] input = new byte[16];
    int[] out = new int[16];
    sameFailure(() -> a.index(input, -1, 1, out), () -> b.index(input, -1, 1, out));
    sameFailure(() -> a.index(input, 8, 9, out), () -> b.index(input, 8, 9, out));
    sameFailure(
        () -> a.index(new byte[32], 0, 32, out), () -> b.index(new byte[32], 0, 32, out));
    sameFailure(() -> a.validateUtf8(input, 4, 13), () -> b.validateUtf8(input, 4, 13));
    sameFailure(() -> a.validateUtf8(input, -1, 0), () -> b.validateUtf8(input, -1, 0));
    sameFailure(() -> a.carry(64), () -> b.carry(64));
  }

  private static void sameFailure(Runnable baseline, Runnable cells) {
    RuntimeException expected = null;
    try {
      baseline.run();
    } catch (RuntimeException failure) {
      expected = failure;
    }
    if (expected == null) throw new AssertionError("CSV cells parity: baseline accepted a bad call");
    sameFailure(expected, cells);
  }

  private static void sameFailure(RuntimeException expected, Runnable cells) {
    try {
      cells.run();
    } catch (RuntimeException failure) {
      require(
          failure.getClass() == expected.getClass()
              && String.valueOf(failure.getMessage()).equals(String.valueOf(expected.getMessage())),
          "error " + failure + " vs baseline " + expected);
      return;
    }
    throw new AssertionError("CSV cells parity: cells accepted what the baseline rejects: " + expected);
  }

  /** Full parses over each provider, installed in turn: the same rows, values and errors. */
  private static void parses(CsvIndexing.Provider base, CsvIndexing.Provider routed) {
    CsvIndexing.Provider previous = CsvIndexing.provider();
    try {
      Random random = new Random(11);
      for (int round = 0; round < 12; round++) {
        byte[] input = table(random, 40 + random.nextInt(400));
        if (round % 4 == 3) input = Arrays.copyOf(input, input.length + 2);
        if (round % 4 == 3) input[input.length - 2] = '"'; // unterminated quoted field
        if (round % 6 == 5) input[random.nextInt(input.length / 2) + input.length / 4] = (byte) 0xFF;
        CsvIndexing.install(base);
        Object expected = parse(input);
        CsvIndexing.install(routed);
        Object actual = parse(input);
        require(expected.equals(actual), "parse " + round + ": " + actual + " vs " + expected);
      }
    } finally {
      CsvIndexing.install(previous);
    }
  }

  private static Object parse(byte[] input) {
    try (CsvBatch batch = Csv.parse(input, CsvFormat.RFC4180.withHeader(false))) {
      List<String> values = new ArrayList<>();
      int columns = batch.schema().size();
      values.add(batch.size() + "x" + columns);
      for (int row = 0; row < batch.size(); row++)
        for (int column = 0; column < columns; column++) {
          try {
            values.add(String.valueOf(batch.row(row).getString(column)));
          } catch (RuntimeException failure) {
            values.add("!" + failure.getClass().getName() + ": " + failure.getMessage());
          }
        }
      return values;
    } catch (RuntimeException failure) {
      return "!" + failure.getClass().getName() + ": " + failure.getMessage();
    }
  }

  /** A strict RFC 4180 table of at least 16 KiB (the index path), with every field shape. */
  private static byte[] table(Random random, int rows) {
    StringBuilder text = new StringBuilder();
    int columns = 3 + random.nextInt(5);
    while (text.length() < (32 << 10)) {
      for (int row = 0; row < rows; row++) {
        for (int column = 0; column < columns; column++) {
          if (column > 0) text.append(',');
          switch (random.nextInt(6)) {
            case 0 -> text.append(random.nextInt(1_000_000));
            case 1 -> text.append("\"q,").append(row).append("\"\"x\"\"\n中\"");
            case 2 -> text.append("é😀").append(column);
            case 3 -> text.append(random.nextDouble());
            case 4 -> text.append("\"\"");
            default -> text.append("plain").append(row);
          }
        }
        text.append(random.nextBoolean() ? "\r\n" : "\n");
      }
    }
    return text.toString().getBytes(StandardCharsets.UTF_8);
  }

  static String describe(CsvIndexing.Provider provider) {
    return provider.name() + " (" + provider.capacity() + " entries per call, max "
        + CsvIndex.MAX_CALL_BYTES + ")";
  }
}
