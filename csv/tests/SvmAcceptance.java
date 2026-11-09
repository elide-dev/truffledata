package dev.elide.truffledata.csv.tests;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.DataNull;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Missing;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.csv.Csv;
import dev.elide.truffledata.csv.CsvBatch;
import dev.elide.truffledata.csv.CsvFormat;
import dev.elide.truffledata.csv.CsvLimits;
import dev.elide.truffledata.csv.CsvPlan;
import dev.elide.truffledata.csv.CsvReader;
import dev.elide.truffledata.csv.CsvSink;
import dev.elide.truffledata.csv.CsvText;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;

/** Runs the parse, stream, typed and export smoke checks in a native image, then the gates. */
public final class SvmAcceptance {
  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError("SVM CSV acceptance: " + message);
  }

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static void rejects(Runnable action, String message) {
    try {
      action.run();
    } catch (IllegalArgumentException expected) {
      String text = String.valueOf(expected.getMessage());
      require(text.startsWith(message), text);
      return;
    }
    throw new AssertionError("SVM CSV acceptance: accepted " + message);
  }

  /** Generic parse: escaped quotes read unescaped on every path; syntax is strict. */
  private static void parse() throws Exception {
    CsvBatch batch =
        Csv.parse(utf8("﻿name,note\r\n\"say \"\"hi\"\"\",\"a,b\nc\"\r\n中文,x\r\n"), CsvFormat.RFC4180);
    require(batch.size() == 2, "rows");
    require(
        batch.schema().field(0).name().equals("name") && batch.schema().field(1).name().equals("note"),
        "header names");
    require(batch.row(0).getString(0).equals("say \"hi\""), "escaped getString");
    require(batch.row(0).getString(1).equals("a,b\nc"), "quoted delimiter and newline");
    require(batch.row(1).getString(0).equals("中文"), "UTF-8 text");
    var data = DataLibrary.factory().getUncached();
    require(data.read(batch.row(0), 0).equals("say \"hi\""), "escaped DataLibrary read");
    var interop = InteropLibrary.getUncached();
    require(interop.getArraySize(batch) == 2, "interop rows");
    Object row = interop.readArrayElement(batch, 0);
    require(interop.asString(interop.readMember(row, "name")).equals("say \"hi\""), "interop asString");
    require(interop.asString(interop.readArrayElement(row, 1)).equals("a,b\nc"), "interop element");
    var positional = Csv.parse(utf8("a,b\n"), CsvFormat.RFC4180.withHeader(false));
    require(positional.schema().field(1).name().equals("1"), "positional names");
    var flexible = Csv.parse(utf8("a,b\nx\n"), CsvFormat.RFC4180.withFlexible(true));
    require(data.read(flexible.row(0), 1) == Missing.INSTANCE, "flexible short row");
    rejects(() -> Csv.parse(utf8("a\nx\"y\n"), CsvFormat.RFC4180), "CSV syntax at record 2");
    rejects(() -> Csv.parse(utf8("a\n\"x\n"), CsvFormat.RFC4180), "CSV syntax at record 2");
    rejects(() -> Csv.parse(utf8("a,b\nx\n"), CsvFormat.RFC4180), "CSV syntax at record 2");
    rejects(() -> Csv.parse(new byte[] {'a', '\n', (byte) 0xC0, (byte) 0x80}, CsvFormat.RFC4180), "CSV");
  }

  /** Above the index threshold on every ISA: reordered STRING spans, growth and fallback. */
  private static void indexedStrings() {
    int rows = 1024;
    var text = new StringBuilder("left,ignored,right\r\n");
    for (int i = 0; i < rows; i++)
      text.append("café")
          .append(i)
          .append(",\"ignored,cell\",\"q\"\"")
          .append(i)
          .append("\n雪\"\r\n");
    byte[] input = utf8(text.toString());
    require(input.length > 16 << 10, "indexed strings exceed provider threshold");
    var schema =
        Schema.of(
            new Field("right", Field.Kind.STRING, false, false),
            new Field("left", Field.Kind.STRING, false, false));
    var plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    // Ownership is transferred only after the copied parse. Neither parse mutates the bytes.
    var copied = plan.parse(input);
    var owned = plan.parseOwned(input);
    for (var batch : List.of(copied, owned)) {
      require(batch.size() == rows && batch.schema() == schema, "indexed string batch");
      for (int i = 0; i < rows; i++) {
        require(batch.row(i).getString(0).equals("q\"" + i + "\n雪"), "indexed quoted right " + i);
        require(batch.row(i).getString(1).equals("café" + i), "indexed projected left " + i);
      }
    }
    String failure = "CSV syntax at record " + (rows + 2);
    // The malformed field is projected away, but the parser must still reject its syntax.
    rejects(() -> plan.parse(utf8(text + "next,\"bad\"quote,last\r\n")), failure);
    rejects(() -> plan.parseOwned(utf8(text + "next,unused\r\n")), failure);
  }

  /** Streaming: every cut of a tricky input matches the whole parse; batches share the schema. */
  private static void stream() throws Exception {
    byte[] input = utf8("k,v\n1,\"\"\"\"\r\n2,\"x\r\ny\"\r3,中\n\n4,\"\"\n");
    CsvBatch whole = Csv.parse(input, CsvFormat.RFC4180);
    List<String> expected = cells(List.of(whole));
    require(expected.equals(List.of("1", "\"", "2", "x\r\ny", "3", "中", "4", "")), "whole " + expected);
    for (int cut = 1; cut <= input.length; cut++) {
      List<CsvBatch> batches = new ArrayList<>();
      try (CsvReader reader =
          Csv.open(new ByteArrayInputStream(input), CsvFormat.RFC4180, CsvLimits.DEFAULT, cut)) {
        for (CsvBatch batch; (batch = reader.next()) != null; ) {
          require(batch.schema() == reader.schema(), "shared schema at cut " + cut);
          batches.add(batch);
        }
      }
      require(cells(batches).equals(expected), "cut " + cut);
    }
    // An unterminated quote in an endless stream fails at maxRecordBytes.
    InputStream endless =
        new InputStream() {
          private long position;

          @Override
          public int read() {
            return position++ < 2 ? (position == 1 ? 'a' : '\n') : (position == 3 ? '"' : 'x');
          }
        };
    try (CsvReader reader =
        Csv.open(endless, CsvFormat.RFC4180, new CsvLimits(1 << 16, 1 << 10, 16, 1 << 10), 256)) {
      reader.next();
      throw new AssertionError("SVM CSV acceptance: endless quote accepted");
    } catch (IllegalArgumentException expectedLimit) {
      String text = String.valueOf(expectedLimit.getMessage());
      require(text.equals("CSV resource limit: maxRecordBytes"), text);
    }
  }

  private static List<String> cells(List<CsvBatch> batches) {
    List<String> cells = new ArrayList<>();
    for (CsvBatch batch : batches)
      for (int r = 0; r < batch.size(); r++)
        for (int c = 0; c < batch.schema().size(); c++) cells.add(batch.row(r).getString(c));
    return cells;
  }

  /** Typed plans: exact conversions, null only for an unquoted empty field, strict rejections. */
  private static void typed() throws Exception {
    Schema schema =
        Schema.of(
            Field.requiredLong("n"),
            new Field("x", Field.Kind.FLOAT64, true, false),
            new Field("b", Field.Kind.BOOLEAN, false, false),
            new Field("s", Field.Kind.STRING, true, false),
            new Field("o", Field.Kind.INT64, false, true));
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    String[] doubles = {
      "2.5", "-0", "0.1", "1e22", "123456789012345.6", "0.30000000000000004", "4.9e-324",
      "2.2250738585072011e-308", "1.7976931348623157e308", "1e-400", ".5", "5.", "+7E+1"
    };
    var text = new StringBuilder("s,b,x,n\n");
    for (int i = 0; i < doubles.length; i++)
      text.append("t").append(i).append(",TRUE,").append(doubles[i]).append(',').append(i).append('\n');
    text.append(",false,,-9223372036854775808\n");
    CsvBatch batch = plan.parse(utf8(text.toString()));
    require(batch.schema() == schema, "plan schema identity");
    var data = DataLibrary.factory().getUncached();
    for (int i = 0; i < doubles.length; i++) {
      var row = batch.row(i);
      require(row.getLong(0) == i, "INT64 " + i);
      require(
          Double.doubleToRawLongBits(row.getDouble(1))
              == Double.doubleToRawLongBits(Double.parseDouble(doubles[i])),
          "FLOAT64 " + doubles[i]);
      require(row.getBoolean(2), "BOOLEAN");
      require(data.read(row, 4) == Missing.INSTANCE && !data.isPresent(row, 4), "optional missing");
    }
    var last = batch.row(doubles.length);
    require(last.getLong(0) == Long.MIN_VALUE, "INT64 min");
    require(data.read(last, 1) == DataNull.INSTANCE && last.isNull(1), "unquoted empty is null");
    require(data.read(last, 3) == DataNull.INSTANCE, "nullable STRING null");
    try {
      data.readLong(last, 1);
      throw new AssertionError("SVM CSV acceptance: null read as INT64");
    } catch (UnexpectedResultException rejected) {
      require(rejected.getResult() == DataNull.INSTANCE, "rejected null preserved");
    }
    try {
      last.getDouble(1);
      throw new AssertionError("SVM CSV acceptance: null getDouble");
    } catch (ContractViolation rejected) {
      String message = String.valueOf(rejected.getMessage());
      require(message.contains("'x'"), message);
    }
    CsvPlan strict =
        CsvPlan.compile(
            Schema.of(
                Field.requiredLong("n"),
                new Field("x", Field.Kind.FLOAT64, true, false),
                new Field("b", Field.Kind.BOOLEAN, false, false)),
            CsvFormat.RFC4180);
    require(strict.parse(utf8("n,x,b\n1,,true\n")).row(0).isNull(1), "strict null");
    for (String bad : new String[] {"9223372036854775808", "+1", " 42", "1.5", "\"\"", ""})
      rejects(() -> strict.parse(utf8("n,x,b\n" + bad + ",1,true\n")), "CSV record 2: Field 'n'");
    for (String bad : new String[] {"1e400", "NaN", "Infinity", "\"\"", "1e"})
      rejects(() -> strict.parse(utf8("n,x,b\n1," + bad + ",true\n")), "CSV record 2: Field 'x'");
    rejects(() -> strict.parse(utf8("n,x,b\n1,1,yes\n")), "CSV record 2: Field 'b'");
    rejects(() -> strict.parse(utf8("x,b\n1,true\n")), "CSV header: Field 'n'");
    rejects(
        () -> CsvPlan.compile(Schema.of(new Field("a", Field.Kind.ANY, false, false)), CsvFormat.RFC4180),
        "CSV plan: Field 'a'");
  }

  /** Export: one pass, schema order, unescaped text views, nulls and missing fields. */
  private static void export() {
    CsvBatch batch =
        Csv.parse(utf8("a,b\n\"say \"\"hi\"\"\",plain\nlast\n"), CsvFormat.RFC4180.withFlexible(true));
    List<String> events = new ArrayList<>();
    batch.export(
        new CsvSink() {
          @Override
          public void beginRow(long record, int fields) {
            events.add("row " + record + " " + fields);
          }

          @Override
          public void text(int column, CsvText value) {
            byte[] copy = new byte[value.length()];
            value.copyTo(copy, 0);
            require(value.contentEquals(copy), "contentEquals");
            require(value.toTruffleString().toJavaStringUncached().equals(value.toString()), "views");
            events.add(column + "=" + new String(copy, StandardCharsets.UTF_8));
          }

          @Override
          public void longValue(int column, long value) {
            events.add(column + "=" + value);
          }

          @Override
          public void doubleValue(int column, double value) {
            events.add(column + "=" + value);
          }

          @Override
          public void booleanValue(int column, boolean value) {
            events.add(column + "=" + value);
          }

          @Override
          public void nullValue(int column) {
            events.add(column + " null");
          }

          @Override
          public void missing(int column) {
            events.add(column + " missing");
          }

          @Override
          public void endRow() {
            events.add("end");
          }
        });
    require(
        events.equals(
            List.of(
                "row 0 2", "0=say \"hi\"", "1=plain", "end", "row 1 2", "0=last", "1 missing", "end")),
        "export events " + events);
    var typed =
        CsvPlan.compile(
                Schema.of(
                    Field.requiredLong("n"),
                    new Field("x", Field.Kind.FLOAT64, true, false),
                    new Field("b", Field.Kind.BOOLEAN, false, false)),
                CsvFormat.RFC4180)
            .parse(utf8("n,x,b\n1,,true\n2,2.5,false\n"));
    var sink = new CompiledReads.CountingSink();
    typed.export(sink);
    // Two rows, 1 + 2, one null, 2.5 truncated, one true.
    require(sink.sum == 2_000_000 + 3 + 7 + 2 + 1, "typed export " + sink.sum);
  }

  public static void main(String[] args) throws Exception {
    try (var context = Context.create("csv-test")) {
      context.enter();
      try {
        parse();
        indexedStrings();
        stream();
        typed();
        export();
      } finally {
        context.leave();
      }
    }
    CompiledReads.run();
    System.out.println("SVM CSV acceptance passed");
  }
}
