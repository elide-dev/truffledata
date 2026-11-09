package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Convenience overloads and accessors not reached through the scenario suites. */
final class CsvApiSurfaceTest {
  @TempDir Path dir;

  private Path file(String text) throws Exception {
    Path path = dir.resolve("data.csv");
    Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    return path;
  }

  @Test
  void pathAndMappingReadersStreamTheSameRecords() throws Exception {
    Path path = file("n,s\n1,a\n2,b\n");
    try (CsvReader reader = Csv.open(path, CsvFormat.RFC4180)) {
      CsvBatch batch = reader.next();
      assertNotNull(batch);
      assertEquals(2, batch.size());
      assertEquals("b", batch.row(1).getString(1));
      assertNull(reader.next());
    }
    Schema schema = Schema.of(Field.requiredLong("n"), new Field("s", Field.Kind.STRING, false, false));
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    try (CsvReader reader = plan.open(path)) {
      CsvBatch batch = reader.next();
      assertNotNull(batch);
      assertEquals(2L, batch.row(1).getLong(0));
    }
    try (CsvMapping mapping = CsvMapping.map(path);
        CsvReader reader = plan.open(mapping)) {
      CsvBatch batch = reader.next();
      assertNotNull(batch);
      assertEquals(1L, batch.row(0).getLong(0));
    }
  }

  /** Windowed, owned and channel overloads parse the same records as the copying entry points. */
  @Test
  void windowedOwnedAndChannelOverloadsAgree() throws Exception {
    byte[] framed = "xx\nn,s\n1,a\n2,b\nyy".getBytes(StandardCharsets.UTF_8);
    int from = 3, to = framed.length - 2;
    byte[] exact = java.util.Arrays.copyOfRange(framed, from, to);
    CsvBatch reference = Csv.parse(exact, CsvFormat.RFC4180);
    assertEquals(2, reference.size());
    assertEquals("b", Csv.parse(framed, from, to, CsvFormat.RFC4180).row(1).getString(1));
    assertEquals("b", Csv.parseOwned(framed, from, to, CsvFormat.RFC4180).row(1).getString(1));
    assertEquals("a", Csv.parseOwned(exact.clone(), CsvFormat.RFC4180).row(0).getString(1));
    assertEquals(
        2, Csv.parseOwned(exact.clone(), CsvFormat.RFC4180, CsvLimits.DEFAULT).size());
    try (CsvReader reader = Csv.open(exact, CsvFormat.RFC4180)) {
      CsvBatch batch = reader.next();
      assertNotNull(batch);
      assertEquals(2, batch.size());
      assertNull(reader.next());
    }
    Schema schema = Schema.of(Field.requiredLong("n"), new Field("s", Field.Kind.STRING, false, false));
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    assertEquals(2L, plan.parseOwned(exact.clone()).row(1).getLong(0));
    assertEquals(1L, plan.parseOwned(exact.clone(), CsvLimits.DEFAULT).row(0).getLong(0));
    try (CsvReader reader = plan.open(exact)) {
      assertEquals(2L, java.util.Objects.requireNonNull(reader.next()).row(1).getLong(0));
    }
    try (CsvReader reader = plan.open(framed, from, to)) {
      assertEquals(1L, java.util.Objects.requireNonNull(reader.next()).row(0).getLong(0));
    }
    Path path = file(new String(exact, StandardCharsets.UTF_8));
    try (java.nio.channels.FileChannel channel =
        java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.READ)) {
      assertEquals(2L, plan.parse(channel).row(1).getLong(0));
    }
    try (java.nio.channels.FileChannel channel =
            java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.READ);
        CsvReader reader = plan.open(channel)) {
      assertEquals("a", java.util.Objects.requireNonNull(reader.next()).row(0).getString(1));
    }
  }

  @Test
  void builderSettersAndEnumsRoundTrip() {
    CsvFormat format =
        CsvFormat.Predefined.DEFAULT.format().toBuilder().setRecordSeparator('\n').setAutoFlush(true).build();
    assertEquals("\n", format.recordSeparator());
    assertTrue(format.autoFlush());
    assertEquals(CsvFormat.Predefined.EXCEL, CsvFormat.Predefined.valueOf("EXCEL"));
    assertEquals(CsvFormat.QuoteMode.MINIMAL, CsvFormat.QuoteMode.valueOf("MINIMAL"));
    assertEquals(
        CsvFormat.DuplicateHeaderMode.ALLOW_ALL, CsvFormat.DuplicateHeaderMode.valueOf("ALLOW_ALL"));
  }

  @Test
  void writerExposesItsFormatAndFlushes() throws Exception {
    StringWriter out = new StringWriter();
    CsvFormat format = CsvFormat.Predefined.DEFAULT.format();
    try (CsvWriter writer = new CsvWriter(out, format)) {
      assertSame(format, writer.format());
      writer.printRecord("a", 1);
      writer.flush();
      assertEquals("a,1\r\n", out.toString());
    }
  }
}
