package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.strings.TruffleString;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CsvExportTest {
  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static List<List<Object>> exported(CsvBatch batch) {
    ListSink sink = new ListSink();
    batch.export(sink);
    return sink.rows;
  }

  @Test
  void exportMatchesRows() {
    CsvBatch generic =
        Csv.parse(
            utf8("a,b,c\n1,\"x \"\"y\"\"\",中\n,\"\",\n3\n"), CsvFormat.RFC4180.withFlexible(true));
    assertEquals(CsvReaderTest.rows(generic), exported(generic));
    assertEquals(3, exported(generic).size());
    Schema schema =
        Schema.of(
            Field.requiredLong("n"),
            new Field("x", Field.Kind.FLOAT64, true, false),
            new Field("b", Field.Kind.BOOLEAN, false, false),
            new Field("s", Field.Kind.STRING, true, false),
            new Field("o", Field.Kind.INT64, false, true));
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    CsvBatch typed = plan.parse(utf8("s,n,x,b,o\nhi,1,2.5,true,9\n,-2,,FALSE,8\n\"q\"\"\",3,-0,false,7\n"));
    List<List<Object>> rows = exported(typed);
    assertEquals(CsvReaderTest.rows(typed), rows);
    CsvBatch absent = plan.parse(utf8("n,x,b,s\n1,1,true,s\n"));
    assertEquals(CsvReaderTest.rows(absent), exported(absent));
    // Batches from a stream export their stream record indexes.
    StringBuilder text = new StringBuilder("v\n");
    for (int i = 0; i < 100; i++) text.append(i).append('\n');
    List<List<Object>> all = new ArrayList<>();
    List<List<Object>> expected = new ArrayList<>();
    try (CsvReader reader = Csv.open(new ChunkedInput(utf8(text.toString()), 5), CsvFormat.RFC4180, CsvLimits.DEFAULT, 16)) {
      for (CsvBatch batch; (batch = reader.next()) != null; ) {
        all.addAll(exported(batch));
        expected.addAll(CsvReaderTest.rows(batch));
      }
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
    assertEquals(expected, all);
    assertEquals(99L, all.get(99).get(0));
    // An empty batch exports nothing.
    assertEquals(List.of(), exported(Csv.parse(utf8("a\n"), CsvFormat.RFC4180)));
  }

  @Test
  void escapedTextUsesScratchOnly() {
    CsvBatch batch =
        Csv.parse(utf8("a,b\n\"say \"\"hi\"\"\",plain\n\"\"\"x\"\"\",\"q,d\"\n"), CsvFormat.RFC4180);
    List<byte[]> escapedBuffers = new ArrayList<>();
    List<String> seen = new ArrayList<>();
    CsvSink sink =
        new ListSink() {
          @Override
          public void text(int column, CsvText value) {
            super.text(column, value);
            String expected = batch.row(rows.size()).getString(column);
            seen.add(expected);
            byte[] bytes = utf8(expected);
            assertEquals(bytes.length, value.length());
            for (int i = 0; i < bytes.length; i++) assertEquals(bytes[i], value.byteAt(i));
            assertThrows(IndexOutOfBoundsException.class, () -> value.byteAt(bytes.length));
            assertTrue(value.contentEquals(bytes));
            assertFalse(value.contentEquals(utf8(expected + "!")));
            byte[] copy = new byte[bytes.length + 2];
            value.copyTo(copy, 1);
            for (int i = 0; i < bytes.length; i++) assertEquals(bytes[i], copy[i + 1]);
            TruffleString string = value.toTruffleString();
            assertEquals(expected, string.toJavaStringUncached());
            assertTrue(string.isValidUncached(TruffleString.Encoding.UTF_8));
            if (column == 0) {
              // Escaped: unescaped into the export's scratch buffer, never the input.
              assertNotSame(batch.input, value.bytes);
              escapedBuffers.add(value.bytes);
            } else {
              // Unescaped quoted or plain fields are views of the retained input.
              assertSame(batch.input, value.bytes);
            }
          }
        };
    batch.export(sink);
    assertEquals(List.of("say \"hi\"", "plain", "\"x\"", "q,d"), seen);
    assertEquals(2, escapedBuffers.size());
    assertSame(escapedBuffers.get(0), escapedBuffers.get(1), "one scratch buffer per export");
    assertEquals(0, batch.copiedBytes());
  }

  /** Touches every value without allocating. */
  private static final class CountingSink implements CsvSink {
    long sum;
    long rows;

    @Override
    public void beginRow(long record, int fields) {
      sum += fields;
    }

    @Override
    public void text(int column, CsvText value) {
      sum += value.length();
      if (value.length() > 0) sum += value.byteAt(0);
    }

    @Override
    public void longValue(int column, long value) {
      sum += value;
    }

    @Override
    public void doubleValue(int column, double value) {
      sum += (long) value;
    }

    @Override
    public void booleanValue(int column, boolean value) {
      if (value) sum++;
    }

    @Override
    public void nullValue(int column) {
      sum += 7;
    }

    @Override
    public void missing(int column) {
      sum += 11;
    }

    @Override
    public void endRow() {
      rows++;
    }
  }

  @Test
  void exportDoesNotAllocatePerRow() {
    Schema schema =
        Schema.of(
            Field.requiredLong("id"),
            new Field("price", Field.Kind.FLOAT64, false, false),
            new Field("active", Field.Kind.BOOLEAN, true, false),
            new Field("name", Field.Kind.STRING, false, false),
            new Field("note", Field.Kind.STRING, true, true));
    StringBuilder text = new StringBuilder("id,price,active,name,note\n");
    for (int i = 0; i < 10_000; i++) {
      text.append(i).append(',').append(i).append(".25,");
      text.append(i % 3 == 0 ? "" : i % 2 == 0 ? "true" : "false").append(',');
      text.append(i % 2 == 0 ? "\"n \"\"" + i + "\"\"\"" : "plain" + i).append(',');
      text.append(i % 5 == 0 ? "" : "note").append('\n');
    }
    CsvBatch batch = CsvPlan.compile(schema, CsvFormat.RFC4180).parse(utf8(text.toString()));
    assertEquals(10_000, batch.size());
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long thread = Thread.currentThread().threadId();
    CountingSink sink = new CountingSink();
    for (int i = 0; i < 20; i++) batch.export(sink);
    long expectedSum = sink.sum / 20;
    CountingSink measured = new CountingSink();
    long before = threads.getThreadAllocatedBytes(thread);
    batch.export(measured);
    long allocated = threads.getThreadAllocatedBytes(thread) - before;
    assertEquals(10_000, measured.rows);
    assertEquals(expectedSum, measured.sum);
    assertTrue(allocated < 1024, "allocated " + allocated + " bytes for 10,000 rows");
  }

  @Test
  void sinkExceptionPropagates() {
    CsvBatch batch = Csv.parse(utf8("a\n1\n2\n3\n4\n"), CsvFormat.RFC4180);
    IllegalStateException stop = new IllegalStateException("stop");
    List<Long> begun = new ArrayList<>();
    CsvSink sink =
        new ListSink() {
          @Override
          public void beginRow(long record, int fields) {
            begun.add(record);
            if (record == 2) throw stop;
            super.beginRow(record, fields);
          }
        };
    assertSame(stop, assertThrows(IllegalStateException.class, () -> batch.export(sink)));
    assertEquals(List.of(0L, 1L, 2L), begun);
    // The batch is unaffected and exports again.
    assertEquals(4, exported(batch).size());
  }
}
