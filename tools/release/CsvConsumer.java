import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.csv.Csv;
import dev.elide.truffledata.csv.CsvBatch;
import dev.elide.truffledata.csv.CsvFormat;
import dev.elide.truffledata.csv.CsvLimits;
import dev.elide.truffledata.csv.CsvPlan;
import dev.elide.truffledata.csv.CsvReader;
import dev.elide.truffledata.csv.CsvRow;
import dev.elide.truffledata.csv.CsvSink;
import dev.elide.truffledata.csv.CsvText;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.graalvm.nativeimage.ImageInfo;

/** Uses only publication jars (truffledata-csv and truffledata-core), not the source libraries. */
public final class CsvConsumer {
  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static void check(boolean condition, String what) {
    if (!condition) throw new AssertionError("Published CSV " + what);
  }

  /** Collects rows as lists; text views are materialized during the call that receives them. */
  private static final class ListSink implements CsvSink {
    final List<List<Object>> rows = new ArrayList<>();
    private List<Object> row = new ArrayList<>();

    @Override
    public void beginRow(long record, int fields) {
      row = new ArrayList<>(fields);
      row.add(record);
    }

    @Override
    public void text(int column, CsvText value) {
      row.add(value.toString());
    }

    @Override
    public void longValue(int column, long value) {
      row.add(value);
    }

    @Override
    public void doubleValue(int column, double value) {
      row.add(value);
    }

    @Override
    public void booleanValue(int column, boolean value) {
      row.add(value);
    }

    @Override
    public void nullValue(int column) {
      row.add("<null>");
    }

    @Override
    public void missing(int column) {
      row.add("<missing>");
    }

    @Override
    public void endRow() {
      rows.add(row);
    }
  }

  public static void main(String[] args) throws IOException {
    // Generic: a BOM, CRLF, an escaped quote, an embedded newline and multi-byte text.
    CsvBatch generic =
        Csv.parse(
            utf8("﻿name,note\r\n\"say \"\"hi\"\"\",trüffle\r\n\"two\nlines\",\r\n"),
            CsvFormat.RFC4180);
    check(generic.size() == 2, "generic row count");
    Schema header = generic.schema();
    check(
        header.size() == 2
            && header.field(0).name().equals("name")
            && header.field(1).name().equals("note"),
        "header schema");
    CsvRow first = generic.row(0);
    check(first.getString(0).equals("say \"hi\""), "escaped field");
    check(first.getString(1).equals("trüffle"), "multi-byte field");
    check(generic.row(1).getString(0).equals("two\nlines"), "embedded newline");
    check(generic.row(1).getString(1).isEmpty(), "empty generic field");

    // Streaming: small buffers give several batches that share one schema.
    StringBuilder text = new StringBuilder("v,w\n");
    long expectedSum = 0;
    for (int i = 0; i < 2000; i++) {
      text.append(i).append(",\"x\"\"").append(i).append("\"\n");
      expectedSum += i;
    }
    long sum = 0;
    int batches = 0;
    try (CsvReader reader =
        Csv.open(
            new ByteArrayInputStream(utf8(text.toString())),
            CsvFormat.RFC4180,
            CsvLimits.DEFAULT,
            512)) {
      Schema schema = reader.schema();
      for (CsvBatch batch; (batch = reader.next()) != null; batches++) {
        check(batch.schema() == schema, "stream schema identity");
        for (int r = 0; r < batch.size(); r++) {
          CsvRow row = batch.row(r);
          check(row.getString(1).equals("x\"" + row.record()), "streamed escaped field");
          sum += Long.parseLong(row.getString(0));
        }
      }
      check(reader.records() == 2000, "stream record count");
    }
    check(batches > 1 && sum == expectedSum, "stream batches " + batches + ", sum " + sum);

    // Typed: projection of an unknown column, nullable FLOAT64, case-insensitive BOOLEAN.
    Schema schema =
        Schema.of(
            Field.requiredLong("id"),
            new Field("price", Field.Kind.FLOAT64, true, false),
            new Field("active", Field.Kind.BOOLEAN, false, false),
            new Field("name", Field.Kind.STRING, false, false));
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    CsvBatch typed =
        plan.parse(utf8("id,skipped,price,active,name\n7,a,2.5,TRUE,ann\n-8,b,,false,\"b\"\"o\"\n"));
    check(typed.schema() == plan.schema(), "typed schema identity");
    CsvRow row = typed.row(0);
    check(row.getLong(0) == 7 && row.getDouble(1) == 2.5 && row.getBoolean(2), "typed reads");
    CsvRow second = typed.row(1);
    check(second.getLong(0) == -8 && second.isNull(1) && !second.getBoolean(2), "typed null");
    check(second.getString(3).equals("b\"o"), "typed string");
    // Header columns map by name, in any order.
    try (CsvReader reader =
        plan.open(new ByteArrayInputStream(utf8("name,active,price,id\nz,false,,1\n")))) {
      CsvBatch batch = reader.next();
      check(batch != null && batch.schema() == plan.schema(), "typed stream schema");
      CsvRow streamed = batch.row(0);
      check(streamed.getLong(0) == 1 && streamed.isNull(1), "typed stream reads");
      check(streamed.getString(3).equals("z") && reader.next() == null, "typed stream end");
    }

    // Export: one pass, schema order, null and text events.
    ListSink sink = new ListSink();
    typed.export(sink);
    List<List<Object>> expected =
        List.of(Arrays.asList(0L, 7L, 2.5, true, "ann"), Arrays.asList(1L, -8L, "<null>", false, "b\"o"));
    check(expected.equals(sink.rows), "export: " + sink.rows);

    // Strictness: syntax and conversion failures throw; nothing falls back.
    try {
      Csv.parse(utf8("a\nx\"y\n"), CsvFormat.RFC4180);
      throw new AssertionError("Published CSV accepted a bare quote");
    } catch (IllegalArgumentException e) {
      check(e.getMessage().startsWith("CSV syntax at record 2"), "syntax message: " + e.getMessage());
    }
    try {
      plan.parse(utf8("id,price,active,name\n+1,1,true,x\n"));
      throw new AssertionError("Published CSV accepted +1 as INT64");
    } catch (ContractViolation e) {
      check(e.getMessage().equals("CSV record 2: Field 'id': invalid INT64"), "typed message: " + e.getMessage());
    }
    System.out.println(
        "Published CSV parse, stream, typed reads and export passed; SVM=" + ImageInfo.inImageCode());
  }
}
