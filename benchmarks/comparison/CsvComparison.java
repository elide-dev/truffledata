package dev.elide.truffledata.comparison;

import org.jspecify.annotations.NullMarked;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.csv.CsvFactory;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.sun.management.ThreadMXBean;
import com.univocity.parsers.csv.CsvParserSettings;
import de.siegmar.fastcsv.reader.AbstractBaseCsvCallbackHandler;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;
import dev.elide.truffledata.Batch;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.ReadBooleanNode;
import dev.elide.truffledata.ReadDoubleNode;
import dev.elide.truffledata.ReadLongNode;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.csv.Csv;
import dev.elide.truffledata.csv.CsvBatch;
import dev.elide.truffledata.csv.CsvBench;
import dev.elide.truffledata.csv.CsvFormat;
import dev.elide.truffledata.csv.CsvLimits;
import dev.elide.truffledata.csv.CsvPlan;
import dev.elide.truffledata.csv.CsvRow;
import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.IndexedScanner;
import dev.elide.truffledata.csv.kernel.ScalarIndexer;
import dev.elide.truffledata.csv.kernel.SwarIndexer;
import dev.elide.truffledata.csv.kernel.TableIndexer;
import dev.elide.truffledata.csv.kernel.vector.VectorIndexer;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.Nullable;

/**
 * CSV comparison cells. One process measures one cell and prints one JSON line with raw samples:
 *
 * <ul>
 *   <li>{@code index}: structural indexing only (entries; every kernel).
 *   <li>{@code scan}: records and field boundaries without decoding (the current strict scanner,
 *       the indexed scanner per kernel, FastCSV's callback handler and Jackson's token stream).
 *   <li>{@code strings}: full materialization, one {@code java.lang.String} per data cell.
 *   <li>{@code typed}: scan plus typed decode of the INT64, FLOAT64 and BOOLEAN columns.
 *   <li>{@code typed-reads}: retained typed rows read by a guest loop (read-aot, interpret, peak),
 *       CSV plan rows against core {@code Batch} rows built from FastCSV.
 * </ul>
 *
 * Throughput cells are the {@code ingest} stage over a whole fixture held in a {@code byte[]}.
 * Engines that decode text (all competitors, and the truffledata string and typed paths) start
 * from the same bytes; truffledata validates UTF-8 strictly, the competitors decode leniently.
 */
@NullMarked
public final class CsvComparison {
  /** Native indexers for the runtime in use (SVM static library or JVM FFM). */
  public interface Natives {
    String runtime();

    /** {@code kernel} 0 picks the best; {@code small} selects the leaf (SVM) or critical (JVM) call. */
    CsvIndexer create(byte delimiter, byte quote, boolean quoting, int kernel, boolean small);

    long invalidUtf8(byte[] input);
  }

  static Natives natives =
      new Natives() {
        @Override
        public String runtime() {
          return "none";
        }

        @Override
        public CsvIndexer create(byte d, byte q, boolean quoting, int kernel, boolean small) {
          throw new IllegalStateException("No native kernel in this runtime");
        }

        @Override
        public long invalidUtf8(byte[] input) {
          throw new IllegalStateException("No native kernel in this runtime");
        }
      };

  static final List<String> WORKLOADS = List.of("index", "scan", "strings", "typed", "typed-reads");
  static final List<String> STAGES = List.of("ingest", "read-aot", "interpret", "peak");
  static final CsvFormat FORMAT = CsvFormat.RFC4180;
  static final CsvLimits BIG = new CsvLimits(Integer.MAX_VALUE - 64, 1 << 20, 4096, Integer.MAX_VALUE);
  static final Limits BATCH_LIMITS = new Limits(Integer.MAX_VALUE, 64, Long.MAX_VALUE, Long.MAX_VALUE);
  static final int JAVA_CAPACITY = 1 << 16;
  static final int NATIVE_CAPACITY = 1 << 20;
  private static final int ROWS = 32;
  private static final long COMPILED_READ = 1L << 48;
  private static volatile long sink;
  private static volatile Object retained = new Object();

  /** Engines per workload; the index kernels are named by role, the report adds the kernel. */
  static final Map<String, List<String>> ENGINES = new LinkedHashMap<>();

  static {
    ENGINES.put(
        "index",
        List.of(
            "index-scalar", "index-table", "index-swar", "index-vector", "index-native",
            "index-native-small", "index-native-swar", "index-native-avx2", "index-native-avx512"));
    ENGINES.put(
        "scan",
        List.of(
            "truffledata-scanner", "kernel-table", "kernel-swar", "kernel-vector", "kernel-native",
            "kernel-native-small", "fastcsv", "jackson"));
    ENGINES.put(
        "strings",
        List.of(
            "truffledata-batch", "kernel-swar", "kernel-native", "fastcsv", "univocity",
            "commons-csv", "jackson"));
    ENGINES.put(
        "typed",
        List.of(
            "truffledata-plan", "kernel-swar", "kernel-native", "fastcsv", "univocity",
            "commons-csv", "jackson"));
    ENGINES.put("typed-reads", List.of("truffledata-plan", "batch-fastcsv"));
  }

  private CsvComparison() {}

  /** Runs one throughput engine over one input and returns its checksum. */
  interface Engine {
    long run(byte[] input) throws Exception;

    default String kernel() {
      return "";
    }
  }

  static void main(String[] args, Natives runtime) throws Exception {
    natives = runtime;
    if (args.length == 4 && args[0].equals("--fixture")) {
      CsvFixture.write(args[1], Integer.parseInt(args[2]), Path.of(args[3]));
    } else if (args.length >= 1 && args[0].equals("--smoke")) {
      smoke(args.length > 1 ? Integer.parseInt(args[1]) : 64 << 10);
    } else if (args.length == 5) {
      cell(args[0], args[1], args[2], Integer.parseInt(args[3]), args[4], false);
    } else {
      throw new IllegalArgumentException(
          "--fixture dataset bytes path | --smoke [bytes] | workload engine dataset bytes stage");
    }
  }

  /** Every engine once per workload and dataset; checksums must agree within a workload. */
  private static void smoke(int bytes) throws Exception {
    for (String workload : WORKLOADS) {
      for (String dataset : CsvFixture.DATASETS) {
        if (workload.startsWith("typed") && CsvFixture.typedSchema(dataset) == null) continue;
        if (workload.equals("typed-reads") && !dataset.equals("numeric")) continue;
        for (String engine : java.util.Objects.requireNonNull(ENGINES.get(workload))) {
          if (!available(engine)) continue;
          for (String stage : workload.equals("typed-reads") ? STAGES.subList(1, 4) : List.of("ingest"))
            cell(workload, engine, dataset, bytes, stage, true);
        }
      }
    }
    if (CsvBench.floatFallbacks() != 0)
      throw new AssertionError("FLOAT64 slow path used: " + CsvBench.floatFallbacks());
  }

  private static final Map<String, Long> SMOKE_CHECKSUMS = new LinkedHashMap<>();

  static boolean available(String engine) {
    try {
      if (engine.contains("native")) {
        int kernel = nativeKernel(engine);
        natives.create((byte) ',', (byte) '"', true, kernel, false);
      }
      if (engine.contains("vector")) new VectorIndexer((byte) ',', (byte) '"', true);
      return true;
    } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException
        | LinkageError unavailable) {
      return false;
    }
  }

  private static int nativeKernel(String engine) {
    if (engine.endsWith("-swar")) return 2;
    if (engine.endsWith("-avx2")) return 4;
    if (engine.endsWith("-avx512")) return 5;
    return 0;
  }

  private static CsvIndexer indexer(String engine) {
    byte d = FORMAT.delimiter(), q = FORMAT.quote();
    String kind = engine.substring(engine.indexOf('-') + 1);
    return switch (kind) {
      case "scalar" -> new ScalarIndexer(d, q, true);
      case "table" -> new TableIndexer(d, q, true);
      case "swar" -> new SwarIndexer(d, q, true);
      case "vector" -> new VectorIndexer(d, q, true);
      case "native", "native-swar", "native-avx2", "native-avx512" ->
          natives.create(d, q, true, nativeKernel(engine), false);
      case "native-small" -> natives.create(d, q, true, 0, true);
      default -> throw new IllegalArgumentException("engine " + engine);
    };
  }

  private static int capacity(CsvIndexer indexer) {
    return indexer.name().startsWith("native") ? NATIVE_CAPACITY : JAVA_CAPACITY;
  }

  // ---------------------------------------------------------------- engines

  static Engine engine(String workload, String engine, String dataset) {
    switch (workload) {
      case "index" -> {
        CsvIndexer indexer = indexer(engine);
        int[] out = new int[capacity(indexer)];
        return new Engine() {
          @Override
          public long run(byte[] input) {
            long entries = 0;
            indexer.carry(0);
            for (int pos = 0; pos < input.length; ) {
              int length = CsvIndex.indexable(input.length - pos, out.length);
              entries += indexer.index(input, pos, length, out);
              pos += length;
            }
            return entries << 8 | indexer.carry();
          }

          @Override
          public String kernel() {
            return indexer.name();
          }
        };
      }
      case "scan" -> {
        return switch (engine) {
          case "truffledata-scanner" -> input -> {
            long[] counts = new long[2];
            CsvBench.scan(FORMAT, BIG, input, 0, (spans, count, start, end) -> {
              counts[0]++;
              counts[1] += count;
            });
            return counts[0] * 1_000_003L + counts[1];
          };
          case "fastcsv" -> input -> {
            FieldCounter counter = new FieldCounter();
            long records = 0;
            try (var reader =
                CsvReader.builder().build(counter, new ByteArrayInputStream(input), StandardCharsets.UTF_8)) {
              for (Object ignored : reader) records++;
            }
            return records * 1_000_003L + counter.fields;
          };
          case "jackson" -> input -> {
            long records = 0, fields = 0;
            try (CsvParser parser = Competitors.JACKSON.createParser(input)) {
              JsonToken token;
              while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.START_ARRAY) records++;
                else if (token == JsonToken.VALUE_STRING) {
                  fields++;
                  if (parser.getTextLength() < 0) throw new AssertionError();
                }
              }
            }
            return records * 1_000_003L + fields;
          };
          default -> new KernelEngine(indexer(engine), dataset, Mode.SCAN);
        };
      }
      case "strings" -> {
        return switch (engine) {
          case "truffledata-batch" -> input -> {
            CsvBatch batch = Csv.parse(input, FORMAT, BIG);
            retained = batch;
            long sum = 0;
            int width = batch.schema().size();
            for (int r = 0, n = batch.size(); r < n; r++) {
              CsvRow row = batch.row(r);
              for (int c = 0; c < width; c++) sum += row.getString(c).length();
            }
            return sum;
          };
          case "fastcsv" -> input -> {
            long sum = 0;
            boolean header = true;
            try (var reader =
                CsvReader.builder().ofCsvRecord(new ByteArrayInputStream(input), StandardCharsets.UTF_8)) {
              for (CsvRecord record : reader) {
                if (header) {
                  header = false;
                  continue;
                }
                for (int c = 0, n = record.getFieldCount(); c < n; c++) sum += record.getField(c).length();
              }
            }
            return sum;
          };
          case "univocity" -> input -> {
            var parser = new com.univocity.parsers.csv.CsvParser(univocity());
            parser.beginParsing(new ByteArrayInputStream(input), StandardCharsets.UTF_8);
            long sum = 0;
            boolean header = true;
            String[] row;
            while ((row = parser.parseNext()) != null) {
              if (header) {
                header = false;
                continue;
              }
              for (String value : row) sum += value == null ? 0 : value.length();
            }
            parser.stopParsing();
            return sum;
          };
          case "commons-csv" -> input -> {
            long sum = 0;
            try (var parser =
                Competitors.COMMONS.parse(new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8))) {
              for (CSVRecord record : parser)
                for (int c = 0, n = record.size(); c < n; c++) sum += record.get(c).length();
            }
            return sum;
          };
          case "jackson" -> input -> {
            long sum = 0;
            int row = -1;
            try (CsvParser parser = Competitors.JACKSON.createParser(input)) {
              JsonToken token;
              while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.START_ARRAY) row++;
                else if (token == JsonToken.VALUE_STRING && row > 0) sum += parser.getText().length();
              }
            }
            return sum;
          };
          default -> new KernelEngine(indexer(engine), dataset, Mode.STRINGS);
        };
      }
      case "typed" -> {
        char[] kinds = CsvFixture.kinds(dataset);
        Schema schema = java.util.Objects.requireNonNull(CsvFixture.typedSchema(dataset));
        return switch (engine) {
          case "truffledata-plan" -> {
            CsvPlan plan = CsvPlan.compile(schema, FORMAT);
            yield input -> {
              CsvBatch batch = plan.parse(input, BIG);
              retained = batch;
              Typed sum = new Typed();
              for (int r = 0, n = batch.size(); r < n; r++) {
                CsvRow row = batch.row(r);
                for (int c = 0; c < kinds.length; c++) {
                  switch (kinds[c]) {
                    case 'L' -> sum.longs += row.getLong(c);
                    case 'D' -> sum.doubles += row.getDouble(c);
                    case 'B' -> sum.trues += row.getBoolean(c) ? 1 : 0;
                    default -> {}
                  }
                }
              }
              return sum.checksum();
            };
          }
          case "fastcsv" -> input -> {
            Typed sum = new Typed();
            boolean header = true;
            try (var reader =
                CsvReader.builder().ofCsvRecord(new ByteArrayInputStream(input), StandardCharsets.UTF_8)) {
              for (CsvRecord record : reader) {
                if (header) {
                  header = false;
                  continue;
                }
                for (int c = 0; c < kinds.length; c++) sum.add(kinds[c], record.getField(c));
              }
            }
            return sum.checksum();
          };
          case "univocity" -> input -> {
            var parser = new com.univocity.parsers.csv.CsvParser(univocity());
            parser.beginParsing(new ByteArrayInputStream(input), StandardCharsets.UTF_8);
            Typed sum = new Typed();
            boolean header = true;
            String[] row;
            while ((row = parser.parseNext()) != null) {
              if (header) {
                header = false;
                continue;
              }
              for (int c = 0; c < kinds.length; c++) sum.add(kinds[c], row[c]);
            }
            parser.stopParsing();
            return sum.checksum();
          };
          case "commons-csv" -> input -> {
            Typed sum = new Typed();
            try (var parser =
                Competitors.COMMONS.parse(new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8))) {
              for (CSVRecord record : parser)
                for (int c = 0; c < kinds.length; c++) sum.add(kinds[c], record.get(c));
            }
            return sum.checksum();
          };
          case "jackson" -> input -> {
            Typed sum = new Typed();
            int row = -1, column = 0;
            try (CsvParser parser = Competitors.JACKSON.createParser(input)) {
              JsonToken token;
              while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.START_ARRAY) {
                  row++;
                  column = 0;
                } else if (token == JsonToken.VALUE_STRING) {
                  if (row > 0 && kinds[column] != 'S') sum.add(kinds[column], parser.getText());
                  column++;
                }
              }
            }
            return sum.checksum();
          };
          default -> new KernelEngine(indexer(engine), dataset, Mode.TYPED);
        };
      }
      default -> throw new IllegalArgumentException("workload " + workload);
    }
  }

  /** Competitor singletons, initialized at run time (Native Image keeps them out of the heap). */
  static final class Competitors {
    static final CsvFactory JACKSON = new CsvFactory();
    static final CSVFormat COMMONS =
        CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
  }

  private static CsvParserSettings univocity() {
    CsvParserSettings settings = new CsvParserSettings();
    settings.getFormat().setLineSeparator("\n");
    settings.setIgnoreLeadingWhitespaces(false);
    settings.setIgnoreTrailingWhitespaces(false);
    settings.setIgnoreLeadingWhitespacesInQuotes(false);
    settings.setIgnoreTrailingWhitespacesInQuotes(false);
    settings.setEmptyValue("");
    settings.setNullValue("");
    settings.setMaxCharsPerColumn(1 << 20);
    settings.setMaxColumns(4096);
    settings.setReadInputOnSeparateThread(false);
    return settings;
  }

  /** FastCSV's lowest-level API: field callbacks over its char buffer, no strings. */
  static final class FieldCounter extends AbstractBaseCsvCallbackHandler<Object> {
    long fields;

    @Override
    protected void handleField(int fieldIdx, char[] buf, int offset, int len, boolean quoted) {
      fields++;
    }

    @Override
    protected Object buildRecord() {
      return Boolean.TRUE;
    }
  }

  /** Typed checksum: identical summation order on every engine. */
  static final class Typed {
    long longs;
    double doubles;
    long trues;

    void add(char kind, String text) {
      switch (kind) {
        case 'L' -> longs += Long.parseLong(text);
        case 'D' -> doubles += Double.parseDouble(text);
        case 'B' -> {
          if (text.equalsIgnoreCase("true")) trues++;
          else if (!text.equalsIgnoreCase("false")) throw new IllegalArgumentException("boolean");
        }
        default -> {}
      }
    }

    long checksum() {
      return longs * 31 + Double.doubleToLongBits(doubles) + trues;
    }
  }

  enum Mode {
    SCAN,
    STRINGS,
    TYPED
  }

  /**
   * The indexed scanner over one kernel, with the strict scanner for fallback records: the shape
   * of an integrated scanner. Strings and typed modes validate UTF-8 strictly first (simdutf for
   * native kernels, the Java validator otherwise), as Csv.parse does.
   */
  static final class KernelEngine implements Engine, IndexedScanner.Records {
    private final CsvIndexer indexer;
    private final IndexedScanner scanner;
    private final Mode mode;
    private final char[] kinds;
    private byte[] input = new byte[0];
    private byte[] scratch = new byte[256];
    private long records, fields, strings;
    private final Typed typed = new Typed();

    KernelEngine(CsvIndexer indexer, String dataset, Mode mode) {
      this.indexer = indexer;
      this.mode = mode;
      this.kinds = mode == Mode.TYPED ? CsvFixture.kinds(dataset) : new char[0];
      scanner = new IndexedScanner(FORMAT, BIG, indexer, capacity(indexer));
    }

    @Override
    public String kernel() {
      return indexer.name();
    }

    @Override
    public long run(byte[] input) {
      this.input = input;
      records = fields = strings = 0;
      typed.longs = typed.trues = 0;
      typed.doubles = 0;
      if (mode != Mode.SCAN) {
        long invalid =
            indexer.name().startsWith("native")
                ? natives.invalidUtf8(input)
                : CsvBench.invalidUtf8(input, 0, input.length);
        if (invalid >= 0) throw new IllegalArgumentException("invalid UTF-8 at " + invalid);
      }
      int pos = 0;
      while (true) {
        int stop = scanner.scan(input, pos, input.length, true, this);
        if (!scanner.fallback()) break;
        int after =
            CsvBench.scanOne(
                FORMAT, BIG, input, stop, input.length, true, records, (spans, count, start, end) -> handle(spans, count));
        if (after == stop) break;
        pos = after;
      }
      return switch (mode) {
        case SCAN -> records * 1_000_003L + fields;
        case STRINGS -> strings;
        case TYPED -> typed.checksum();
      };
    }

    @Override
    public boolean record(long[] spans, int count, int start, int end) {
      handle(spans, count);
      return true;
    }

    private void handle(long[] spans, int count) {
      long ordinal = records++;
      fields += count;
      if (mode == Mode.SCAN || ordinal == 0) return; // the header row
      for (int c = 0; c < count; c++) {
        long span = spans[c];
        int offset = (int) (span >>> 32);
        int length = (int) (span >>> 2) & ((1 << 30) - 1);
        byte[] bytes = input;
        if ((span & 1) != 0) {
          if (scratch.length < length) scratch = new byte[Math.max(length, 2 * scratch.length)];
          length = CsvBench.unescape(input, span, FORMAT.quote(), scratch);
          bytes = scratch;
          offset = 0;
        }
        if (mode == Mode.STRINGS) {
          strings += new String(bytes, offset, length, StandardCharsets.UTF_8).length();
        } else {
          switch (kinds[c]) {
            case 'L' -> typed.longs += CsvBench.parseLong(bytes, offset, length);
            case 'D' -> typed.doubles += CsvBench.parseDouble(bytes, offset, length);
            case 'B' -> typed.trues += bool(bytes, offset, length) ? 1 : 0;
            default -> {}
          }
        }
      }
    }

    private static boolean bool(byte[] b, int offset, int length) {
      if (length == 4
          && (b[offset] | 0x20) == 't'
          && (b[offset + 1] | 0x20) == 'r'
          && (b[offset + 2] | 0x20) == 'u'
          && (b[offset + 3] | 0x20) == 'e') return true;
      if (length == 5
          && (b[offset] | 0x20) == 'f'
          && (b[offset + 1] | 0x20) == 'a'
          && (b[offset + 2] | 0x20) == 'l'
          && (b[offset + 3] | 0x20) == 's'
          && (b[offset + 4] | 0x20) == 'e') return false;
      throw new IllegalArgumentException("invalid BOOLEAN");
    }
  }

  // ---------------------------------------------------------------- measurement

  static void cell(String workload, String engineName, String dataset, int bytes, String stage, boolean smoke)
      throws Exception {
    List<String> engines = ENGINES.get(workload);
    if (engines == null || !engines.contains(engineName))
      throw new IllegalArgumentException("engine " + workload + "/" + engineName);
    if (!CsvFixture.DATASETS.contains(dataset)) throw new IllegalArgumentException("dataset");
    if (workload.equals("typed-reads")) {
      typedReads(engineName, dataset, stage, smoke);
      return;
    }
    if (!stage.equals("ingest")) throw new IllegalArgumentException("stage " + stage);
    byte[] input = CsvFixture.generate(dataset, bytes);
    long[] shape = shape(input);
    long rows = shape[0] - 1; // data rows; the header is not a row
    Engine engine = engine(workload, engineName, dataset);
    long expected = engine.run(input);
    String key = workload + "/" + dataset + "/" + bytes;
    if (smoke && !workload.equals("index")) {
      Long previous = SMOKE_CHECKSUMS.putIfAbsent(key, expected);
      if (previous != null && previous != expected)
        throw new AssertionError("Checksum mismatch " + key + " " + engineName + ": " + expected + " vs " + previous);
    }
    ThreadMXBean counter = counter();
    long thread = Thread.currentThread().threadId();
    // Warm up: at least three runs and two seconds (JIT tiers on the JVM, caches on SVM).
    long warm = System.nanoTime() + (smoke ? 0 : 2_000_000_000L);
    for (int i = 0; i < 3 || System.nanoTime() < warm; i++) check(engine.run(input), expected);
    double[] time = new double[smoke ? 1 : 5];
    for (int s = 0; s < time.length; s++) {
      long start = System.nanoTime(), elapsed, ops = 0;
      do {
        check(engine.run(input), expected);
        ops++;
        elapsed = System.nanoTime() - start;
      } while (elapsed < (smoke ? 0 : 300_000_000L));
      time[s] = elapsed / (double) (ops * input.length);
    }
    double[] memory = new double[smoke ? 1 : 3];
    int ops = (int) Math.max(1, (8L << 20) / input.length);
    for (int s = 0; s < memory.length; s++) {
      long before = counter.getThreadAllocatedBytes(thread);
      for (int i = 0; i < ops; i++) check(engine.run(input), expected);
      memory[s] = (counter.getThreadAllocatedBytes(thread) - before) / (double) (ops * Math.max(1, rows));
    }
    System.out.printf(
        Locale.ROOT,
        "{\"workload\":\"%s\",\"engine\":\"%s\",\"kernel\":\"%s\",\"dataset\":\"%s\",\"target\":%d,"
            + "\"bytes\":%d,\"rows\":%d,\"fields\":%d,\"stage\":\"ingest\",\"runtime\":\"%s\","
            + "\"checksum\":%d,\"sha256\":\"%s\",\"ns_per_byte\":%s,\"allocated_bytes_per_row\":%s}%n",
        workload, engineName, engine.kernel(), dataset, bytes, input.length, rows, shape[1],
        natives.runtime(), expected, smoke ? "" : CsvFixture.sha256(input), Arrays.toString(time),
        Arrays.toString(memory));
  }

  /** Records (header included) and fields, from the strict scanner. */
  private static long[] shape(byte[] input) {
    long[] counts = new long[2];
    CsvBench.scan(FORMAT, BIG, input, 0, (spans, count, start, end) -> {
      counts[0]++;
      counts[1] += count;
    });
    return counts;
  }

  private static void check(long actual, long expected) {
    if (actual != expected) throw new AssertionError("Checksum " + actual + " != " + expected);
    sink = actual;
  }

  private static ThreadMXBean counter() {
    ThreadMXBean counter = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!counter.isThreadAllocatedMemorySupported()) throw new AssertionError("Allocation counter required");
    counter.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    long calibration = counter.getThreadAllocatedBytes(thread);
    retained = new byte[1 << 20];
    if (calibration < 0 || counter.getThreadAllocatedBytes(thread) - calibration < 1 << 20)
      throw new AssertionError("Allocation calibration");
    return counter;
  }

  // ---------------------------------------------------------------- retained typed reads

  /** 32 retained numeric rows read through the core read nodes (the JSON comparison stages). */
  private static void typedReads(String engine, String dataset, String stage, boolean smoke) throws Exception {
    if (!STAGES.contains(stage) || stage.equals("ingest")) throw new IllegalArgumentException("stage " + stage);
    boolean peak = stage.equals("peak");
    boolean guest = peak || stage.equals("interpret");
    Schema schema = java.util.Objects.requireNonNull(CsvFixture.typedSchema(dataset));
    char[] kinds = CsvFixture.kinds(dataset);
    byte[] input = CsvFixture.generate(dataset, 4096);
    Object[] rows = new Object[ROWS];
    if (engine.equals("truffledata-plan")) {
      CsvBatch batch = CsvPlan.compile(schema, FORMAT).parse(input);
      for (int i = 0; i < ROWS; i++) rows[i] = batch.row(i);
    } else {
      List<Object[]> values = new ArrayList<>();
      boolean header = true;
      try (var reader = CsvReader.builder().ofCsvRecord(new ByteArrayInputStream(input), StandardCharsets.UTF_8)) {
        for (CsvRecord record : reader) {
          if (header) {
            header = false;
            continue;
          }
          if (values.size() == ROWS) break;
          Object[] row = new Object[kinds.length];
          for (int c = 0; c < kinds.length; c++) {
            String text = record.getField(c);
            row[c] =
                switch (kinds[c]) {
                  case 'L' -> Long.parseLong(text);
                  case 'D' -> Double.parseDouble(text);
                  case 'B' -> Boolean.parseBoolean(text);
                  default -> text;
                };
          }
          values.add(row);
        }
      }
      Batch batch = Batch.copyOf(schema, values, BATCH_LIMITS);
      for (int i = 0; i < ROWS; i++) rows[i] = batch.row(i);
    }
    try (Context context =
        Context.newBuilder("td-compare")
            .allowExperimentalOptions(true)
            .option("engine.Compilation", Boolean.toString(peak))
            .option("engine.BackgroundCompilation", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .build()) {
      context.initialize("td-compare");
      context.enter();
      try {
        Reads root = new Reads(engine, kinds);
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        long expected = root.read(rows, 0);
        ThreadMXBean counter = counter();
        long thread = Thread.currentThread().threadId();
        if (peak) {
          for (int attempt = 0; attempt < 8 && !target.isValidLastTier(); attempt++) {
            for (int i = 0; i < 1024; i++) target.call(rows, i);
            target.compile(true);
            target.waitForCompilation();
          }
        }
        tier(target, peak, rows);
        for (long until = System.nanoTime() + (smoke ? 1_000_000 : 100_000_000); System.nanoTime() < until; )
          readBatch(root, target, guest, rows, expected, 128);
        double[] time = new double[3], memory = new double[3];
        for (int s = 0; s < 3; s++) {
          tier(target, peak, rows);
          long before = counter.getThreadAllocatedBytes(thread);
          readBatch(root, target, guest, rows, expected, 128);
          memory[s] = (counter.getThreadAllocatedBytes(thread) - before) / (128.0 * ROWS);
          long start = System.nanoTime(), elapsed, count = 0;
          do {
            readBatch(root, target, guest, rows, expected, 128);
            count += 128;
            elapsed = System.nanoTime() - start;
          } while (elapsed < (smoke ? 1_000_000 : 50_000_000L));
          time[s] = elapsed / (double) (count * ROWS);
          tier(target, peak, rows);
        }
        System.out.printf(
            Locale.ROOT,
            "{\"workload\":\"typed-reads\",\"engine\":\"%s\",\"dataset\":\"%s\",\"stage\":\"%s\",\"runtime\":\"%s\","
                + "\"guest_compiled\":%s,\"checksum\":%d,\"rows\":%d,\"ns_per_row\":%s,\"allocated_bytes_per_row\":%s}%n",
            engine, dataset, stage, natives.runtime(), peak, expected, ROWS, Arrays.toString(time),
            Arrays.toString(memory));
      } finally {
        context.leave();
      }
    }
  }

  private static void readBatch(Reads root, OptimizedCallTarget target, boolean guest, Object[] rows, long expected, int count) {
    long sum = 0;
    for (int i = 0; i < count; i++)
      sum += guest ? ((Long) target.call(rows, i)) & ~COMPILED_READ : root.read(rows, i);
    if (sum != expected * count) throw new AssertionError("Checksum");
    sink = sum;
  }

  private static void tier(OptimizedCallTarget target, boolean peak, Object[] rows) {
    if (peak ? !target.isValidLastTier() : target.isValid()) throw new AssertionError("Execution tier");
    if (peak && (((Long) target.call(rows, 0)) & COMPILED_READ) == 0)
      throw new AssertionError("Read helper did not execute as compiled guest code");
  }

  /** The guest loop: every typed column of 32 rows through ReadLong/Double/BooleanNode. */
  static final class Reads extends RootNode {
    private final String name;
    @CompilerDirectives.CompilationFinal(dimensions = 1)
    private final char[] kinds;
    @Children private final ReadLongNode[] longs;
    @Children private final ReadDoubleNode[] doubles;
    @Children private final ReadBooleanNode[] booleans;

    Reads(String engine, char[] kinds) {
      super(ComparisonLanguage.current());
      this.name = "CsvReads[" + engine + "]";
      this.kinds = kinds;
      longs = new ReadLongNode[kinds.length];
      doubles = new ReadDoubleNode[kinds.length];
      booleans = new ReadBooleanNode[kinds.length];
      for (int c = 0; c < kinds.length; c++) {
        longs[c] = ReadLongNode.create();
        doubles[c] = ReadDoubleNode.create();
        booleans[c] = ReadBooleanNode.create();
      }
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return read((Object[]) frame.getArguments()[0], (Integer) frame.getArguments()[1]);
    }

    long read(Object[] rows, int rotation) {
      long sum = 0;
      for (int i = 0; i < ROWS; i++) sum += row(rows[(i + rotation) & (ROWS - 1)]);
      return sum | (CompilerDirectives.inCompiledCode() ? COMPILED_READ : 0);
    }

    @ExplodeLoop
    private long row(Object row) {
      long sum = 0;
      for (int c = 0; c < kinds.length; c++) {
        switch (kinds[c]) {
          case 'L' -> sum += longs[c].execute(row, c);
          case 'D' -> sum += (long) doubles[c].execute(row, c);
          case 'B' -> sum += booleans[c].execute(row, c) ? 1 : 0;
          default -> {}
        }
      }
      return sum;
    }
  }
}
