package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.Schema;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CsvReaderTest {
  private static final DataLibrary DATA = DataLibrary.factory().getUncached();

  static final String FIXTURE =
      "id,text,n\r\n"
          + "1,\"say \"\"hi\"\"\",x\r\n"
          + "\r\n"
          + "2,\"line\r\nbreak\",中文\n"
          + "\n"
          + "3,\"\",😀\r"
          + "4,plain,\"é\"\"\"\r\n"
          + "\r"
          + "5,\"a,b\",z";

  static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  /** Every row as (record, values...) strings, read through DataLibrary. */
  static List<List<Object>> rows(CsvBatch batch) {
    List<List<Object>> rows = new ArrayList<>();
    for (int r = 0; r < batch.size(); r++) {
      CsvRow row = batch.row(r);
      List<Object> values = new ArrayList<>();
      values.add(row.record());
      for (int c = 0; c < batch.schema().size(); c++) values.add(DATA.read(row, c));
      rows.add(values);
    }
    return rows;
  }

  static List<List<Object>> drain(CsvReader reader) throws IOException {
    List<List<Object>> rows = new ArrayList<>();
    Schema schema = reader.schema();
    for (CsvBatch batch; (batch = reader.next()) != null; ) {
      assertTrue(batch.size() > 0);
      assertSame(schema, batch.schema());
      rows.addAll(rows(batch));
    }
    assertNull(reader.next());
    return rows;
  }

  static List<String> names(Schema schema) {
    return CsvBatchTest.names(schema);
  }

  @Test
  void everyCutPositionMatchesWholeParse() throws IOException {
    for (CsvFormat format :
        List.of(CsvFormat.RFC4180, CsvFormat.RFC4180.withHeader(false), CsvFormat.RFC4180.withTrim(true))) {
      for (String text : List.of(FIXTURE, "﻿" + FIXTURE)) {
        byte[] bytes = utf8(text);
        CsvBatch whole = Csv.parse(bytes, format);
        List<List<Object>> expected = rows(whole);
        for (int cut = 1; cut <= bytes.length; cut++) {
          ChunkedInput input = new ChunkedInput(bytes, cut, 1, 2, 3);
          try (CsvReader reader = Csv.open(input, format, CsvLimits.DEFAULT, cut)) {
            assertEquals(names(whole.schema()), names(reader.schema()), "cut " + cut);
            assertEquals(expected, drain(reader), "cut " + cut);
            assertEquals(whole.size(), reader.records());
            assertEquals(bytes.length, reader.position(), "cut " + cut);
          }
          assertEquals(1, input.closes);
        }
      }
    }
  }

  @Test
  void multibyteSequenceSplitAcrossReads() throws IOException {
    byte[] bytes = utf8("t\n😀中é\n");
    for (int batch = 1; batch <= bytes.length; batch++) {
      try (CsvReader reader =
          Csv.open(new ChunkedInput(bytes, 1), CsvFormat.RFC4180, CsvLimits.DEFAULT, batch)) {
        assertEquals(List.of(List.of(0L, "😀中é")), drain(reader), "batch " + batch);
      }
    }
    // An invalid sequence split across reads reports its stream offset.
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(utf8("t\nok\nab"));
    out.write(0xE4);
    out.write(0xB8);
    out.write('(');
    out.writeBytes(utf8("\n"));
    for (int batch = 1; batch <= out.size(); batch++) {
      try (CsvReader reader =
          Csv.open(new ChunkedInput(out.toByteArray(), 1), CsvFormat.RFC4180, CsvLimits.DEFAULT, batch)) {
        var e =
            assertThrows(
                IllegalArgumentException.class,
                () -> {
                  while (reader.next() != null) {}
                });
        assertEquals("CSV syntax at record 3, byte 7: invalid UTF-8", e.getMessage(), "batch " + batch);
      }
    }
  }

  @Test
  void schemaIdentityAcrossBatches() throws IOException {
    StringBuilder text = new StringBuilder("id,name\n");
    for (int i = 0; i < 10_000; i++) text.append(i).append(",\"n ").append(i).append("\"\n");
    byte[] bytes = utf8(text.toString());
    try (CsvReader reader =
        Csv.open(new ChunkedInput(bytes, 7, 64, 13), CsvFormat.RFC4180, CsvLimits.DEFAULT, 64)) {
      Schema schema = reader.schema();
      assertSame(schema, reader.schema());
      assertEquals(8, reader.position());
      int batches = 0;
      long expected = 0;
      for (CsvBatch batch; (batch = reader.next()) != null; ) {
        batches++;
        assertSame(schema, batch.schema());
        assertEquals(expected, batch.firstRecord());
        for (int r = 0; r < batch.size(); r++) {
          assertEquals(Long.toString(expected + r), batch.row(r).getString(0));
          assertEquals("n " + (expected + r), batch.row(r).getString(1));
          assertEquals(expected + r, batch.row(r).record());
        }
        expected += batch.size();
        assertEquals(expected, reader.records());
        assertTrue(batch.inputBytes() <= 64 + 64, "batch buffer " + batch.inputBytes());
      }
      assertEquals(10_000, expected);
      assertTrue(batches > 100, "batches " + batches);
      assertEquals(bytes.length, reader.position());
    }
    // maxBatchRows splits batches, carrying the rest.
    CsvLimits three = new CsvLimits(1 << 20, 1 << 10, 16, 3);
    try (CsvReader reader =
        Csv.open(new ChunkedInput(utf8("h\n0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n"), 1 << 10), CsvFormat.RFC4180, three, 1 << 10)) {
      List<Integer> sizes = new ArrayList<>();
      List<Long> firsts = new ArrayList<>();
      for (CsvBatch batch; (batch = reader.next()) != null; ) {
        sizes.add(batch.size());
        firsts.add(batch.firstRecord());
        assertEquals(Long.toString(batch.firstRecord()), batch.row(0).getString(0));
      }
      assertEquals(List.of(3, 3, 3, 1), sizes);
      assertEquals(List.of(0L, 3L, 6L, 9L), firsts);
    }
  }

  /** {@code a,"} followed by an endless run of {@code x}. */
  private static final class Endless extends InputStream {
    long served;

    @Override
    public int read() {
      return served++ < 3 ? "a,\"".charAt((int) served - 1) : 'x';
    }

    @Override
    public int read(byte[] target, int offset, int length) {
      for (int i = 0; i < length; i++) target[offset + i] = (byte) read();
      return length;
    }
  }

  @Test
  void unterminatedQuoteFailsAtRecordLimit() {
    CsvLimits limits = new CsvLimits(1 << 20, 4096, 16, 1024);
    for (CsvFormat format : List.of(CsvFormat.RFC4180, CsvFormat.RFC4180.withHeader(false))) {
      Endless endless = new Endless();
      CsvReader reader = Csv.open(endless, format, limits, 64);
      var e =
          assertThrows(
              IllegalArgumentException.class,
              () -> {
                reader.schema();
                reader.next();
              });
      assertEquals("CSV resource limit: maxRecordBytes", e.getMessage());
      assertTrue(endless.served <= 2 * 4096 + 64, "served " + endless.served);
    }
    // After a complete header, the data path is bounded as well.
    Endless data = new Endless();
    InputStream withHeader =
        new java.io.SequenceInputStream(new java.io.ByteArrayInputStream(utf8("h1,h2\n")), data);
    CsvReader reader = Csv.open(withHeader, CsvFormat.RFC4180, limits, 64);
    var e = assertThrows(IllegalArgumentException.class, reader::next);
    assertEquals("CSV resource limit: maxRecordBytes", e.getMessage());
    assertTrue(data.served <= 2 * 4096 + 64, "served " + data.served);
    // A record of exactly maxRecordBytes, CRLF-terminated, is accepted at every buffer size.
    String big = "y".repeat(4096);
    for (int batch : new int[] {1, 64, 4095, 4096, 4097, 4098, 5000}) {
      try (CsvReader exact =
          Csv.open(new ChunkedInput(utf8("h\r\n" + big + "\r\n" + big), 1 << 20), CsvFormat.RFC4180, limits, batch)) {
        assertEquals(List.of(List.of(0L, big), List.of(1L, big)), drain(exact), "batch " + batch);
      } catch (IOException io) {
        throw new AssertionError(io);
      }
    }
  }

  @Test
  void ioExceptionPropagates() throws IOException {
    IOException boom = new IOException("boom");
    byte[] bytes = utf8("a,b\n1,2\n3,4\n5,6\n");
    ChunkedInput input = new ChunkedInput(bytes, 1).failingAt(12, boom);
    CsvReader reader = Csv.open(input, CsvFormat.RFC4180, CsvLimits.DEFAULT, 6);
    assertEquals(List.of("a", "b"), names(reader.schema()));
    CsvBatch first = reader.next();
    assertNotNull(first);
    assertSame(boom, assertThrows(IOException.class, reader::next));
    reader.close();
    reader.close();
    assertEquals(1, input.closes);
    assertThrows(IllegalStateException.class, reader::next);
    // A failure before the header surfaces from schema().
    ChunkedInput early = new ChunkedInput(bytes, 1).failingAt(2, boom);
    try (CsvReader failing = Csv.open(early, CsvFormat.RFC4180)) {
      assertSame(boom, assertThrows(IOException.class, failing::schema));
    }
  }

  @Test
  void headerOnlyInputHasSchemaAndNoBatches() throws IOException {
    for (String text : List.of("a,b\n", "a,b", "a,b\r\n\r\n\n", "﻿a,b\n")) {
      byte[] bytes = utf8(text);
      try (CsvReader reader = Csv.open(new ChunkedInput(bytes, 1), CsvFormat.RFC4180, CsvLimits.DEFAULT, 2)) {
        assertEquals(List.of("a", "b"), names(reader.schema()));
        assertNull(reader.next());
        assertNull(reader.next());
        assertEquals(0, reader.records());
        assertEquals(bytes.length, reader.position(), text);
      }
    }
    // next() binds the header itself.
    try (CsvReader reader = Csv.open(new ChunkedInput(utf8("a\n1\n"), 1), CsvFormat.RFC4180)) {
      CsvBatch batch = reader.next();
      assertNotNull(batch);
      assertEquals("1", batch.row(0).getString(0));
      assertSame(batch.schema(), reader.schema());
    }
    try (CsvReader reader = Csv.open(new ChunkedInput(new byte[0], 1), CsvFormat.RFC4180)) {
      var e = assertThrows(IllegalArgumentException.class, reader::schema);
      assertEquals("CSV header missing", e.getMessage());
    }
    try (CsvReader reader =
        Csv.open(new ChunkedInput(new byte[0], 1), CsvFormat.RFC4180.withHeader(false))) {
      assertEquals(0, reader.schema().size());
      assertNull(reader.next());
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> Csv.open(new ChunkedInput(new byte[0], 1), CsvFormat.RFC4180, CsvLimits.DEFAULT, 0));
  }

  /**
   * Streams {@code text} at every batch size and asserts the result matches the whole parse, with
   * the schema, data rows and final position equal. Used by the blank-line limit tests below.
   */
  private static void streamMatchesParse(String text, CsvFormat format, CsvLimits limits)
      throws IOException {
    byte[] bytes = utf8(text);
    CsvBatch parsed = Csv.parse(bytes, format, limits);
    for (int batch = 1; batch <= bytes.length; batch++) {
      try (CsvReader reader = Csv.open(new ChunkedInput(bytes, batch), format, limits, batch)) {
        assertEquals(names(parsed.schema()), names(reader.schema()), "schema batch " + batch);
        assertEquals(rows(parsed), drain(reader), "data batch " + batch);
        assertEquals(bytes.length, reader.position(), "position batch " + batch);
      }
    }
  }

  @Test
  void blankLinesDoNotCountTowardMaxRecordBytes() throws IOException {
    // Skipped blank lines are not record content (docs/CSV.md): a run of blank lines
    // exceeding maxRecordBytes, before a tiny record, must not trip the streaming
    // maxRecordBytes limit. parse() already agrees; streaming must match parse() under
    // identical limits. The strict scanner already measures only record bytes; this
    // exercises the Commons path (CommonsScanner) and the strict path as a control.
    CsvLimits limits = new CsvLimits(1 << 20, 16, 16, 1024);
    String blanks = "\n".repeat(20); // 20 bytes > maxRecordBytes (16)
    CsvFormat commonsNoHeader = CsvFormat.Predefined.DEFAULT.format();
    CsvFormat commonsHeader = commonsNoHeader.toBuilder().setHeader().build();
    CsvFormat strictNoHeader = CsvFormat.RFC4180.withHeader(false);
    CsvFormat strictHeader = CsvFormat.RFC4180;
    // Commons, no header: blank lines before the width-setting first record (bind path).
    streamMatchesParse(blanks + "a,b\nc,d\n", commonsNoHeader, limits);
    // Commons, header: blank lines before the header (bind) and before data (next).
    streamMatchesParse(blanks + "h1,h2\n" + blanks + "a,b\n", commonsHeader, limits);
    // Strict, no header: control (strict already measures only record bytes).
    streamMatchesParse(blanks + "a,b\nc,d\n", strictNoHeader, limits);
    // Strict, header: control.
    streamMatchesParse(blanks + "h1,h2\n" + blanks + "a,b\n", strictHeader, limits);
  }

  @Test
  void crlfBlankLinesDoNotCountTowardMaxRecordBytes() throws IOException {
    // CRLF blank lines are skipped too; a run exceeding maxRecordBytes must not trip the limit.
    CsvLimits limits = new CsvLimits(1 << 20, 16, 16, 1024);
    String blanks = "\r\n".repeat(20); // 40 bytes > maxRecordBytes (16)
    CsvFormat format = CsvFormat.Predefined.DEFAULT.format().toBuilder().setHeader().build();
    streamMatchesParse(blanks + "h1,h2\r\n" + blanks + "a,b\r\n", format, limits);
  }

  @Test
  void blankLinesAndCommentsDoNotCountTowardMaxRecordBytes() throws IOException {
    // Complete blank lines and complete comments are not record content: a run of each
    // exceeding maxRecordBytes, before a tiny record, streams correctly.
    CsvFormat format =
        CsvFormat.Predefined.DEFAULT.format().toBuilder().setHeader().setCommentMarker('#').build();
    CsvLimits limits = new CsvLimits(1 << 20, 16, 16, 1024);
    String blanks = "\n".repeat(20);
    streamMatchesParse(
        blanks + "# comment\n" + blanks + "h1,h2\n" + blanks + "# another\n" + blanks + "a,b\n",
        format,
        limits);
  }

  @Test
  void incompleteCommentStillCountsTowardMaxRecordBytes() throws IOException {
    // An incomplete comment counts toward maxRecordBytes while it is incomplete in a stream
    // (docs/CSV.md): a comment longer than the limit, buffered mid-comment, still trips it.
    CsvFormat format =
        CsvFormat.Predefined.DEFAULT.format().toBuilder().setCommentMarker('#').build();
    CsvLimits limits = new CsvLimits(1 << 20, 16, 16, 1024);
    byte[] bytes = utf8("#" + "x".repeat(20)); // 21-byte comment, no terminator
    try (CsvReader reader = Csv.open(new ChunkedInput(bytes, 3), format, limits, 3)) {
      var e = assertThrows(IllegalArgumentException.class, reader::schema);
      assertEquals("CSV resource limit: maxRecordBytes", e.getMessage());
    }
  }

  @Test
  void blankLineRunLargerThanMaxRecordBytesAtModerateLimits() throws IOException {
    // A blank-line run exceeding maxRecordBytes at a moderate limit (not just 16) streams
    // correctly: the fix is not sensitive to the specific limit value.
    CsvFormat format = CsvFormat.Predefined.DEFAULT.format().toBuilder().setHeader().build();
    CsvLimits limits = new CsvLimits(1 << 20, 4096, 16, 1024);
    int blankCount = limits.maxRecordBytes() + 100;
    StringBuilder sb = new StringBuilder(blankCount + 8);
    for (int i = 0; i < blankCount; i++) sb.append('\n');
    sb.append("h1,h2\na,b\n");
    byte[] bytes = utf8(sb.toString());
    CsvBatch parsed = Csv.parse(bytes, format, limits);
    assertEquals(1, parsed.size());
    assertEquals("a", parsed.row(0).getString(0));
    assertEquals("b", parsed.row(0).getString(1));
    try (CsvReader reader = Csv.open(new ChunkedInput(bytes, 256), format, limits, 256)) {
      assertEquals(List.of("h1", "h2"), names(reader.schema()));
      assertEquals(rows(parsed), drain(reader));
      assertEquals(bytes.length, reader.position());
    }
  }
}
