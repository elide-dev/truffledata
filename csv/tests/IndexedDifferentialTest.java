package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.SwarIndexer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The index path against the per-byte scanners it replaces: identical records, spans, return
 * offsets, stops and errors at the scanner, and identical batches, nulls, missing fields, reader
 * positions and errors through every entry point (heap, streamed at many buffer sizes, mapped),
 * across strict and Commons formats, options, limits and typed plans. The index path is forced at
 * every input size, with both the default capacity and a 64-entry capacity (a kernel call
 * boundary every 64 bytes).
 */
final class IndexedDifferentialTest {
  @TempDir Path dir;

  /** SWAR with one 64-byte block per call: every record crosses call boundaries. */
  static final CsvIndexing.Provider SMALL =
      new CsvIndexing.Provider() {
        @Override
        public String name() {
          return "java-swar-64";
        }

        @Override
        public CsvIndexer create(byte delimiter, byte quote, boolean quoting) {
          return new SwarIndexer(delimiter, quote, quoting);
        }

        @Override
        public int capacity() {
          return 64;
        }
      };

  /** Runs {@code body} with a provider (null: per-byte only) forced at every input size. */
  static <T> T with(CsvIndexing.@Nullable Provider provider, Supplier<T> body) {
    CsvIndexing.Provider saved = CsvIndexing.provider();
    int savedMin = CsvIndexing.minIndexBytes;
    CsvIndexing.install(provider);
    CsvIndexing.minIndexBytes = 0;
    try {
      return body.get();
    } finally {
      CsvIndexing.install(saved);
      CsvIndexing.minIndexBytes = savedMin;
    }
  }

  // ---- formats

  /** Strict formats: every option the strict scanner has. */
  static List<CsvFormat> strictFormats() {
    CsvFormat rfc = CsvFormat.RFC4180;
    return List.of(
        rfc,
        rfc.withHeader(false),
        rfc.withTrim(true),
        rfc.withFlexible(true).withHeader(false),
        rfc.withoutQuoting().withHeader(false),
        rfc.withDelimiter(';').withQuote('\'').withHeader(false),
        rfc.withDelimiter('\t').withHeader(false).withTrim(false),
        rfc.withDelimiter('|').withQuote('a').withHeader(false),
        rfc.withNullStrings("N", "").withHeader(false),
        rfc.withQuoteMode(CsvFormat.QuoteMode.ALL_NON_NULL).withHeader(false),
        rfc.toBuilder().setMaxRows(3).setHeader(false).build(),
        rfc.toBuilder().setMaxRows(2).build(),
        rfc.toBuilder().setHeader("x", "y", "z").setSkipHeaderRecord(true).build());
  }

  /** Commons formats: the indexable presets and options, and some that stay per-byte. */
  static List<CsvFormat> commonsFormats() {
    List<CsvFormat> formats = new ArrayList<>();
    for (CsvFormat.Predefined p : CsvFormat.Predefined.values()) formats.add(p.format());
    CsvFormat.Builder d = CsvFormat.Predefined.DEFAULT.format().toBuilder();
    formats.add(d.setIgnoreEmptyLines(false).build());
    formats.add(CsvFormat.Predefined.DEFAULT.format().toBuilder().setTrailingData(true).build());
    formats.add(CsvFormat.Predefined.DEFAULT.format().toBuilder().setLenientEof(true).build());
    formats.add(CsvFormat.Predefined.DEFAULT.format().toBuilder().setMaxRows(2).build());
    formats.add(
        CsvFormat.Predefined.DEFAULT.format().toBuilder()
            .setIgnoreEmptyLines(false)
            .setMaxRows(3)
            .build());
    formats.add(CsvFormat.Predefined.DEFAULT.format().toBuilder().setHeader().build());
    formats.add(CsvFormat.Predefined.DEFAULT.format().toBuilder().setQuote(null).build());
    formats.add(CsvFormat.Predefined.DEFAULT.format().toBuilder().setEscape('"').build());
    formats.add(CsvFormat.Predefined.DEFAULT.format().toBuilder().setNullString("N").build());
    formats.add(CsvFormat.Predefined.EXCEL.format().toBuilder().setDelimiter(';').build());
    return formats;
  }

  static List<CsvLimits> limits() {
    return List.of(
        CsvLimits.DEFAULT,
        new CsvLimits(1 << 20, 1 << 10, 3, 1 << 20),
        new CsvLimits(1 << 20, 9, 4096, 1 << 20),
        new CsvLimits(1 << 20, 1 << 10, 16, 2));
  }

  @Test
  void indexableDialects() {
    for (CsvFormat f : strictFormats()) assertTrue(IndexScan.indexable(Dialect.of(f)), f.toString());
    assertTrue(IndexScan.indexable(Dialect.of(CsvFormat.Predefined.DEFAULT.format())));
    assertTrue(IndexScan.indexable(Dialect.of(CsvFormat.Predefined.EXCEL.format())));
    assertTrue(IndexScan.indexable(Dialect.of(CsvFormat.Predefined.RFC4180.format())));
    assertTrue(IndexScan.indexable(Dialect.of(CsvFormat.Predefined.INFORMIX_UNLOAD_CSV.format())));
    assertTrue(IndexScan.indexable(Dialect.of(CsvFormat.Predefined.POSTGRESQL_CSV.format())));
    assertTrue(IndexScan.indexable(Dialect.of(CsvFormat.Predefined.MONGODB_CSV.format())));
    assertTrue(IndexScan.indexable(Dialect.of(CsvFormat.Predefined.MONGODB_TSV.format())));
    // Per-byte features: an escape other than the quote, trims, comments, multi-byte delimiters.
    for (CsvFormat.Predefined p :
        new CsvFormat.Predefined[] {
          CsvFormat.Predefined.INFORMIX_UNLOAD,
          CsvFormat.Predefined.MYSQL,
          CsvFormat.Predefined.ORACLE,
          CsvFormat.Predefined.POSTGRESQL_TEXT,
          CsvFormat.Predefined.TDF
        })
      assertFalse(IndexScan.indexable(Dialect.of(p.format())), p.name());
    CsvFormat.Builder d = CsvFormat.Predefined.DEFAULT.format().toBuilder();
    assertFalse(IndexScan.indexable(Dialect.of(d.setCommentMarker('#').build())));
    assertFalse(
        IndexScan.indexable(
            Dialect.of(CsvFormat.Predefined.DEFAULT.format().toBuilder().setDelimiter("::").build())));
    assertFalse(
        IndexScan.indexable(
            Dialect.of(
                CsvFormat.Predefined.DEFAULT.format().toBuilder().setTrailingDelimiter(true).build())));
  }

  // ---- scanner level

  /** One scan's observable behaviour: every record, the return offset and stop, or the error. */
  private static String trace(
      CsvFormat format,
      CsvLimits limits,
      byte[] input,
      int from,
      int to,
      boolean eof,
      long recordBase,
      long stopAfter,
      int refuseAfter) {
    CsvScanner scanner = new CsvScanner(format, limits);
    scanner.recordBase = recordBase;
    scanner.byteBase = 1000;
    scanner.stopAfter = stopAfter;
    StringBuilder out = new StringBuilder();
    int[] seen = {0};
    try {
      int end =
          scanner.scan(
              input,
              from,
              to,
              eof,
              (spans, count, start, stop) -> {
                if (seen[0]++ == refuseAfter) return false;
                out.append(start).append('-').append(stop).append(':');
                for (int i = 0; i < count; i++) out.append(Long.toHexString(spans[i])).append(' ');
                out.append('\n');
                return true;
              });
      out.append("end ").append(end).append(" stopped ").append(scanner.stopped);
      assertEquals(recordBase, scanner.recordBase, "recordBase restored");
    } catch (IllegalArgumentException e) {
      out.append("error ").append(e.getClass().getSimpleName()).append(' ').append(e.getMessage());
    }
    return out.toString();
  }

  private static void scannerCase(
      Random random, CsvFormat format, CsvLimits limits, byte[] input, String context) {
    int from = 0; // scans start at a record boundary
    int to = random.nextInt(3) == 0 ? random.nextInt(input.length + 1) : input.length;
    boolean eof = to == input.length ? random.nextInt(4) != 0 : false;
    long recordBase = random.nextInt(3);
    long stopAfter = random.nextInt(4) == 0 ? recordBase + random.nextInt(5) : Long.MAX_VALUE;
    int refuseAfter = random.nextInt(5) == 0 ? random.nextInt(4) : Integer.MAX_VALUE;
    String expected =
        with(null, () -> trace(format, limits, input, from, to, eof, recordBase, stopAfter, refuseAfter));
    for (CsvIndexing.Provider p : new CsvIndexing.Provider[] {CsvIndexing.JAVA, SMALL}) {
      String actual =
          with(p, () -> trace(format, limits, input, from, to, eof, recordBase, stopAfter, refuseAfter));
      assertEquals(
          expected,
          actual,
          () ->
              p.name()
                  + " "
                  + context
                  + " to="
                  + to
                  + " eof="
                  + eof
                  + " base="
                  + recordBase
                  + " stopAfter="
                  + stopAfter
                  + " refuseAfter="
                  + refuseAfter);
    }
  }

  @Test
  void scannerMatchesPerByteScanners() {
    Random random = new Random(20261106);
    List<CsvFormat> formats = new ArrayList<>(strictFormats());
    formats.addAll(commonsFormats());
    int cases = 0;
    for (CsvFormat format : formats) {
      CsvFuzz fuzz = new CsvFuzz(random, format);
      for (CsvLimits limits : limits()) {
        for (int i = 0; i < 400; i++) {
          String text = fuzz.next();
          if (random.nextInt(5) == 0) text = text + text + text; // longer inputs, more calls
          byte[] input = text.getBytes(StandardCharsets.UTF_8);
          if (input.length > 0 && random.nextInt(25) == 0)
            input[random.nextInt(input.length)] = (byte) 0xFF; // invalid UTF-8 is a consumer error
          String context = "format " + format + " limits " + limits + "\ninput " + Differential.escape(text);
          scannerCase(random, format, limits, input, context);
          cases++;
        }
      }
    }
    assertTrue(cases > 30_000);
  }

  @Test
  void indexPathIsTakenAndRecordsDeclinedRecords() {
    byte[] input = "a,b\n\"x\"y,2\nc,d\n".getBytes(StandardCharsets.UTF_8);
    String error =
        with(
            CsvIndexing.JAVA,
            () -> {
              CsvScanner scanner = new CsvScanner(CsvFormat.RFC4180.withHeader(false), CsvLimits.DEFAULT);
              assertNull(scanner.kernel());
              try {
                scanner.scan(input, 0, input.length, true, (spans, count, start, end) -> true);
                return "parsed";
              } catch (IllegalArgumentException e) {
                assertEquals("java-swar", scanner.kernel());
                return e.getMessage();
              }
            });
    assertEquals("CSV syntax at record 2, byte 7: unexpected byte after a closing quote", error);
    assertEquals("java-swar", with(CsvIndexing.JAVA, CsvIndexing::kernel));
    with(null, () -> {
      assertEquals("per-byte", CsvIndexing.kernel());
      assertNull(CsvIndexing.provider());
      return null;
    });
    assertEquals(16 << 10, CsvIndexing.MIN_INDEX_BYTES);
  }

  // ---- batch level

  private static final String[] TOKENS = {
    "1", "-42", "007", "9223372036854775807", "-9223372036854775808", "9223372036854775808", "1.5",
    "-0", "1e3", "2.5E-3", ".5", "5.", "1e400", "nan", "true", "FALSE", "yes", "", " ", "N", "x",
    "y z", "é", "\"q\"\"r\"", "\"a,b\"", "\"\"", "\"7\"", "\"l1\nl2\""
  };

  private static String typedInput(Random random, int width, String delimiter, boolean header) {
    StringBuilder sb = new StringBuilder();
    if (header) {
      for (int c = 0; c < width; c++) sb.append(c > 0 ? delimiter : "").append("c").append(c);
      sb.append('\n');
    }
    int rows = random.nextInt(12);
    for (int r = 0; r < rows; r++) {
      if (random.nextInt(10) == 0) sb.append('\n');
      int fields = random.nextInt(10) == 0 ? 1 + random.nextInt(width + 1) : width;
      for (int c = 0; c < fields; c++)
        sb.append(c > 0 ? delimiter : "").append(TOKENS[random.nextInt(TOKENS.length)]);
      sb.append(random.nextInt(4) == 0 ? "\r\n" : "\n");
    }
    return sb.toString();
  }

  private static Schema schema(Random random, int width) {
    List<Field> fields = new ArrayList<>();
    Field.Kind[] kinds = {Field.Kind.INT64, Field.Kind.FLOAT64, Field.Kind.BOOLEAN, Field.Kind.STRING};
    for (int c = 0; c < width; c++)
      fields.add(
          new Field(
              "c" + c, kinds[random.nextInt(kinds.length)], random.nextBoolean(), random.nextInt(4) == 0));
    return new Schema(fields);
  }

  /** Every batch of a result: schema, size, first record and exported cells. */
  private static void describe(CsvBatch batch, StringBuilder out) {
    out.append("batch ").append(batch.size()).append(" first ").append(batch.firstRecord());
    out.append(" mapped ").append(batch.mapped()).append(" schema");
    for (int i = 0; i < batch.schema().size(); i++) {
      Field f = batch.schema().field(i);
      out.append(' ').append(f.name()).append('/').append(f.kind()).append(f.nullable() ? "?" : "");
      out.append(f.optional() ? "~" : "");
    }
    ListSink sink = new ListSink();
    batch.export(sink);
    for (List<Object> row : sink.rows) out.append('\n').append(row);
    out.append('\n');
  }

  private interface Run {
    void run(StringBuilder out) throws Exception;
  }

  private static String result(Run run) {
    StringBuilder out = new StringBuilder();
    try {
      run.run(out);
    } catch (IllegalArgumentException | IllegalStateException e) {
      out.append("error ").append(e.getClass().getSimpleName()).append(' ').append(e.getMessage());
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    return out.toString();
  }

  private static void stream(CsvReader reader, StringBuilder out) throws IOException {
    try (reader) {
      out.append("schema ").append(reader.schema().size()).append('\n');
      for (CsvBatch b; (b = reader.next()) != null; ) {
        describe(b, out);
        out.append("records ").append(reader.records()).append(" position ").append(reader.position());
        out.append('\n');
      }
    }
  }

  /** Every entry point over one input, as text. */
  private String paths(
      byte[] input, CsvFormat format, @Nullable CsvPlan plan, CsvLimits limits, int batchBytes, Path file)
      throws IOException {
    StringBuilder all = new StringBuilder();
    all.append("parse: ")
        .append(
            result(
                out -> describe(plan == null ? Csv.parse(input, format, limits) : plan.parse(input, limits), out)));
    for (int bytes : new int[] {1, 7, batchBytes, 4096}) {
      int b = Math.min(bytes, limits.maxBatchBytes());
      all.append("\nstream ").append(b).append(": ")
          .append(
              result(
                  out -> {
                    ChunkedInput in = new ChunkedInput(input, 1 + b / 3, 1, 7);
                    stream(plan == null ? Csv.open(in, format, limits, b) : plan.open(in, limits, b), out);
                  }));
    }
    all.append("\nmapped parse: ")
        .append(
            result(
                out -> {
                  try (CsvMapping mapping = CsvMapping.map(file)) {
                    describe(plan == null ? Csv.parse(mapping, format, limits) : plan.parse(mapping, limits), out);
                  }
                }));
    all.append("\nmapped stream: ")
        .append(
            result(
                out -> {
                  try (CsvMapping mapping = CsvMapping.map(file)) {
                    int b = Math.min(batchBytes, limits.maxBatchBytes());
                    stream(plan == null ? Csv.open(mapping, format, limits, b) : plan.open(mapping, limits, b), out);
                  }
                }));
    all.append("\npath parse: ")
        .append(
            result(
                out -> {
                  try (CsvBatch batch = plan == null ? Csv.parse(file, format, limits) : plan.parse(file, limits)) {
                    describe(batch, out);
                  }
                }));
    return all.toString();
  }

  private void batchCase(
      Random random, byte[] input, CsvFormat format, @Nullable CsvPlan plan, CsvLimits limits, String context)
      throws IOException {
    Path file = dir.resolve("input.csv");
    Files.write(file, input);
    int batchBytes = 1 + random.nextInt(300);
    String expected = with(null, () -> unchecked(() -> paths(input, format, plan, limits, batchBytes, file)));
    for (CsvIndexing.Provider p : new CsvIndexing.Provider[] {CsvIndexing.JAVA, SMALL}) {
      String actual = with(p, () -> unchecked(() -> paths(input, format, plan, limits, batchBytes, file)));
      assertEquals(expected, actual, () -> p.name() + " batchBytes " + batchBytes + " " + context);
    }
  }

  private interface IoSupplier<T> {
    T get() throws IOException;
  }

  private static <T> T unchecked(IoSupplier<T> body) {
    try {
      return body.get();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void genericBatchesMatchOnEveryPath() throws IOException {
    Random random = new Random(7);
    List<CsvFormat> formats = new ArrayList<>(strictFormats());
    formats.addAll(commonsFormats());
    for (CsvFormat format : formats) {
      CsvFuzz fuzz = new CsvFuzz(random, format);
      for (int i = 0; i < 40; i++) {
        String text = fuzz.next();
        if (random.nextBoolean()) text = text + text;
        byte[] input = text.getBytes(StandardCharsets.UTF_8);
        if (input.length > 0 && random.nextInt(15) == 0) input[random.nextInt(input.length)] = (byte) 0xC3;
        CsvLimits limits = limits().get(random.nextInt(limits().size()));
        batchCase(random, input, format, null, limits, "format " + format + "\ninput " + Differential.escape(text));
      }
    }
  }

  @Test
  void typedBatchesMatchOnEveryPath() throws IOException {
    Random random = new Random(11);
    CsvFormat[] formats = {
      CsvFormat.RFC4180,
      CsvFormat.RFC4180.withHeader(false),
      CsvFormat.RFC4180.withTrim(true).withFlexible(true),
      CsvFormat.RFC4180.withNullStrings("N", ""),
      CsvFormat.Predefined.DEFAULT.format().toBuilder().setHeader().build(),
      CsvFormat.Predefined.POSTGRESQL_CSV.format().toBuilder().setHeader().build(),
      CsvFormat.Predefined.EXCEL.format(),
      CsvFormat.Predefined.MONGODB_CSV.format().toBuilder().setHeader().build(),
    };
    for (CsvFormat format : formats) {
      for (int i = 0; i < 120; i++) {
        int width = 1 + random.nextInt(4);
        Schema schema = schema(random, width);
        CsvPlan plan;
        try {
          plan = CsvPlan.compile(schema, format);
        } catch (IllegalArgumentException unsupported) {
          continue;
        }
        String text = typedInput(random, width, format.delimiterString(), format.header());
        byte[] input = text.getBytes(StandardCharsets.UTF_8);
        CsvLimits limits = limits().get(random.nextInt(limits().size()));
        batchCase(
            random,
            input,
            format,
            plan,
            limits,
            "format " + format + " schema " + schema + "\ninput " + Differential.escape(text));
      }
    }
  }

  /** Long inputs: whole-buffer validation, many kernel calls, many windows of a mapped parse. */
  @Test
  void largeInputsMatch() throws IOException {
    Random random = new Random(3);
    StringBuilder sb = new StringBuilder("a,b,c\n");
    for (int r = 0; r < 40_000; r++) {
      sb.append(r).append(',').append(random.nextInt(4) == 0 ? "\"q,\"\"" + r + "\"" : "é" + r);
      sb.append(',').append(r % 3 == 0 ? "" : "x").append(r % 7 == 0 ? "\r\n" : "\n");
    }
    byte[] input = sb.toString().getBytes(StandardCharsets.UTF_8);
    CsvLimits limits = new CsvLimits(1 << 26, 1 << 10, 64, 1 << 20);
    batchCase(random, input, CsvFormat.RFC4180, null, limits, "large");
    byte[] bad = Arrays.copyOf(input, input.length);
    bad[bad.length / 2] = (byte) 0xFF;
    batchCase(random, bad, CsvFormat.RFC4180, null, limits, "large, invalid UTF-8 midway");
    Schema schema =
        Schema.of(
            Field.requiredLong("a"),
            new Field("b", Field.Kind.STRING, false, false),
            new Field("c", Field.Kind.STRING, true, false));
    batchCase(random, input, CsvFormat.RFC4180, CsvPlan.compile(schema, CsvFormat.RFC4180), limits, "large typed");
  }
}
