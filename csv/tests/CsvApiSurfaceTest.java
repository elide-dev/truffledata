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
