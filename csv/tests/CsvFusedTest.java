package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.Constraints;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fused index path (BatchBuilder.put and commit) and the String fast paths: every record the
 * fused path declines must give the per-byte path's result or diagnostic, and every cell must read
 * as the per-byte path reads it.
 */
final class CsvFusedTest {
  @TempDir Path dir;
  private CsvIndexing.@org.jspecify.annotations.Nullable Provider saved;

  @BeforeEach
  void forceIndex() {
    saved = CsvIndexing.provider();
    CsvIndexing.install(CsvIndexing.JAVA);
    CsvIndexing.minIndexBytes = 0;
  }

  @AfterEach
  void restore() {
    CsvIndexing.install(saved);
    CsvIndexing.minIndexBytes = CsvIndexing.MIN_INDEX_BYTES;
  }

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  /** The outcome on the index path and on the per-byte path: cells, or the failure message. */
  private static void same(Supplier<Object> parse) {
    Object indexed = outcome(parse);
    CsvIndexing.install(null);
    Object bytes = outcome(parse);
    CsvIndexing.install(CsvIndexing.JAVA);
    assertEquals(bytes, indexed);
  }

  private static Object outcome(Supplier<Object> parse) {
    try {
      return parse.get();
    } catch (IllegalArgumentException e) { // ContractViolation too
      return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
  }

  private static List<Object> cells(CsvBatch batch) {
    List<Object> out = new ArrayList<>();
    for (int r = 0; r < batch.size(); r++) {
      CsvRow row = batch.row(r);
      for (int c = 0; c < batch.schema().size(); c++) {
        if (row.isNull(c)) out.add(null);
        else
          out.add(
              switch (batch.schema().field(c).kind()) {
                case INT64 -> row.getLong(c);
                case FLOAT64 -> row.getDouble(c);
                case BOOLEAN -> row.getBoolean(c);
                default -> row.getString(c);
              });
      }
    }
    return out;
  }

  private static final Schema TYPED =
      Schema.of(
          Field.requiredLong("n"),
          new Field("x", Field.Kind.FLOAT64, false, false),
          new Field("b", Field.Kind.BOOLEAN, false, false),
          new Field("s", Field.Kind.STRING, false, false));

  @Test
  void declinedRecordsGiveThePerByteResult() {
    CsvPlan plan = CsvPlan.compile(TYPED, CsvFormat.RFC4180);
    String ok = "n,x,b,s\n1,2.5,true,a\n-2,1e3,FALSE,\"q\"\"t\"\n";
    for (String input :
        new String[] {
          ok,
          ok + "x,1,true,a\n", // INT64 grammar
          ok + "1,1.5.5,true,a\n", // FLOAT64 grammar
          ok + "1,1,yes,a\n", // BOOLEAN grammar
          ok + "1,1,true\n", // short record
          ok + "1,1,true,a,extra\n", // long record
          ok + ",1,true,a\n", // legacy null in a required INT64
          ok + "1,1,true,\n", // legacy empty STRING
          ok + "\"1\",\"2\",\"true\",\"s\"\n", // quoted typed values
          ok + "\"1\"\"\",1,true,a\n", // an escaped typed value
          ok + "1,1,true,é\n", // non-ASCII STRING
        }) {
      same(() -> cells(plan.parse(utf8(input))));
    }
    // Typed batches without STRING fields retain no input, and read the same.
    CsvPlan numbers =
        CsvPlan.compile(
            Schema.of(Field.requiredLong("n"), new Field("b", Field.Kind.BOOLEAN, false, false)),
            CsvFormat.RFC4180);
    byte[] input = utf8("n,b\n7,true\n8,false\n");
    CsvBatch batch = numbers.parse(input);
    assertEquals(0, batch.inputBytes());
    assertEquals(List.of(7L, true, 8L, false), cells(batch));
  }

  @Test
  void constraintsAndWidthsDeclineToTheRecordPath() {
    Schema schema =
        Schema.of(
            Field.requiredLong("n")
                .withConstraints(Constraints.NONE.withIntegerRange(new Constraints.IntegerRange(1, 9))),
            new Field("x", Field.Kind.FLOAT64, false, false)
                .withConstraints(Constraints.NONE.withFloatBits(32)));
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    same(() -> cells(plan.parse(utf8("n,x\n3,0.5\n4,1.25\n"))));
    same(() -> cells(plan.parse(utf8("n,x\n3,0.5\n10,1.25\n"))));
    same(() -> cells(plan.parse(utf8("n,x\n3,0.5\n4,0.1\n"))));
    // A projection by header skips columns; a flexible short record is accepted by record().
    Schema projected = Schema.of(Field.requiredLong("n"));
    CsvFormat flexible = CsvFormat.RFC4180.toBuilder().setFlexible(true).build();
    same(() -> cells(CsvPlan.compile(projected, flexible).parse(utf8("a,n,c\n1,2,3\nx,4\n"))));
    same(() -> cells(CsvPlan.compile(projected, CsvFormat.RFC4180).parse(utf8("a,n,c\n1,2,3\nx,4\n"))));
  }

  @Test
  void rowLimitsAndUnvalidatedRecords() {
    CsvPlan plan = CsvPlan.compile(TYPED, CsvFormat.RFC4180);
    CsvLimits two = new CsvLimits(1 << 20, 2, 64, 1 << 20);
    same(() -> cells(plan.parse(utf8("n,x,b,s\n1,1,true,a\n2,2,false,b\n"), two)));
    same(() -> cells(plan.parse(utf8("n,x,b,s\n1,1,true,a\n2,2,false,b\n3,3,true,c\n"), two)));
    // maxRows skips prevalidation: each fused record checks its own UTF-8.
    CsvPlan limited = CsvPlan.compile(TYPED, CsvFormat.RFC4180.toBuilder().setMaxRows(2).build());
    same(() -> cells(limited.parse(utf8("n,x,b,s\n1,1,true,é\n2,2,false,b\n3,3,true,c\n"))));
    byte[] invalid = utf8("n,x,b,s\n1,1,true,ab\n");
    invalid[invalid.length - 2] = (byte) 0xC3;
    same(() -> cells(limited.parse(invalid)));
  }

  @Test
  void commonsComplexSpansInTypedFields() {
    CsvFormat format = CsvFormat.Predefined.DEFAULT.format().toBuilder().setHeader().setSkipHeaderRecord(true).build();
    CsvPlan plan = CsvPlan.compile(TYPED, format);
    same(() -> cells(plan.parse(utf8("n,x,b,s\n\"1\"\"\",1,true,a\n"))));
    same(() -> cells(plan.parse(utf8("n,x,b,s\n1,1,true,\"a\"\"b\"\n2,2,false,c\n"))));
    same(() -> cells(Csv.parse(utf8("a,b\n\"x\"\"y\",z\n"), format)));
  }

  @Test
  void stringFastPathsReadEveryShape() {
    StringBuilder text = new StringBuilder("a,b\n");
    String longEscaped = "\"" + "x\"\"".repeat(2000) + "\""; // past the 4 KiB unescape buffer
    text.append("short,über\n");
    text.append("exactly16bytes!!,0123456789abcdef0\n");
    text.append("\"q\"\"t\",").append(longEscaped).append('\n');
    text.append(",\"\"\n");
    text.append("end,z"); // the last cells end the input (no word of slack)
    byte[] input = utf8(text.toString());
    same(() -> cells(Csv.parse(input, CsvFormat.RFC4180)));
    CsvBatch batch = Csv.parse(input, CsvFormat.RFC4180);
    assertEquals("x\"".repeat(2000), batch.row(2).getString(1));
    assertEquals("z", batch.row(4).getString(1));
    assertEquals("über", batch.row(0).getString(1));
    assertEquals("0123456789abcdef0", batch.row(1).getString(1));
  }

  @Test
  void spanOnlyProjectionAndFallbackMatchPerByte() {
    // Retained fields are reordered and the middle input column is projected away. Grow the
    // column arrays before exercising fallback, so stale partially written rows cannot hide.
    Schema schema =
        Schema.of(
            new Field("b", Field.Kind.STRING, false, false),
            new Field("a", Field.Kind.STRING, false, false));
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    String prefix = "a,ignored,b\r\n" + "first,unused,\"last\"\"quoted\"\r\n".repeat(40);
    for (String tail :
        new String[] {
          "é,,end", // final unterminated row
          ",,\"\"\r\n", // legacy empty STRINGs stay present
          "next,unused\r\n", // too short after the first projected store
          "next,unused,last,extra\r\n", // too wide after every projected store
          "next,\"bad\"quote,last\r\n", // malformed projected-away field
          "next,\"unterminated,last", // incomplete quote after a projected store
        }) {
      byte[] input = utf8(prefix + tail);
      same(() -> cells(plan.parse(input)));
      same(() -> cells(plan.parseOwned(input)));
    }
    byte[] invalid = utf8(prefix + "next,unused,last\n");
    invalid[invalid.length - 2] = (byte) 0xC3;
    same(() -> cells(plan.parse(invalid)));

    // A flexible short row may omit only projected-away columns; record() accepts and rewrites
    // the partial fused row. The following full row must still land at the next batch position.
    CsvPlan flexible =
        CsvPlan.compile(
            Schema.of(new Field("a", Field.Kind.STRING, false, false)),
            CsvFormat.RFC4180.withFlexible(true));
    same(() -> cells(flexible.parse(utf8("a,ignored\nfirst\nsecond,unused\nlast"))));
  }

  @Test
  void spanOnlyLimitsPreserveErrorOrder() {
    String text = "a,b\nfirst,one\nsecond,two\nthird,three\n";
    CsvLimits two = new CsvLimits(1 << 20, 2, 64, 1 << 20);
    same(() -> cells(Csv.parse(utf8(text), CsvFormat.RFC4180, two)));
    CsvFormat stop = CsvFormat.RFC4180.toBuilder().setMaxRows(2).build();
    same(() -> cells(Csv.parse(utf8(text + "\"bad"), stop)));
    // maxRows disables whole-range prevalidation: UTF-8 must still precede the width error.
    byte[] invalid = utf8("a,b\nfirst,one\nbad,utf8,extra\n");
    invalid[invalid.length - 3] = (byte) 0xC3;
    same(() -> cells(Csv.parse(invalid, stop)));
  }

  @Test
  void mappedCellsReadThroughTheSharedBuffer() throws Exception {
    String big = "y".repeat(5000); // past the 4 KiB mapped-cell buffer
    Path path = dir.resolve("m.csv");
    Files.write(path, utf8("a,b\nshort,\"e\"\"s\"\n" + big + ",é\n"));
    try (CsvBatch batch = Csv.parse(path, CsvFormat.RFC4180)) {
      assertEquals(List.of("short", "e\"s", big, "é"), cells(batch));
    }
  }

  @Test
  void nativeLoadingIsExplicit() {
    // Without a native backend on the class path nothing loads, and nothing is installed.
    IllegalStateException missing = assertThrows(IllegalStateException.class, CsvIndexing::loadNative);
    assertTrue(missing.getMessage().startsWith("No native CSV kernel for this runtime"));
    assertEquals("java-swar", CsvIndexing.kernel());
  }
}
