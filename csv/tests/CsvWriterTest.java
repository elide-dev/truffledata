package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVPrinter;
import org.junit.jupiter.api.Test;

final class CsvWriterTest {
  private static final CsvFormat DEFAULT = CsvFormat.Predefined.DEFAULT.format();

  /** Our output for records, asserted equal to Commons CSVPrinter's. */
  static String write(CsvFormat format, List<List<Object>> records) throws IOException {
    StringBuilder ours = new StringBuilder();
    try (CsvWriter writer = new CsvWriter(ours, format)) {
      for (List<Object> record : records) writer.printRecord(record);
    }
    StringBuilder theirs = new StringBuilder();
    try (CSVPrinter printer = new CSVPrinter(theirs, CommonsFormats.toCommons(format))) {
      for (List<Object> record : records) printer.printRecord(record);
    }
    assertEquals(theirs.toString(), ours.toString(), "format " + format);
    return ours.toString();
  }

  private static List<List<Object>> one(Object... values) {
    return List.of(java.util.Arrays.asList(values));
  }

  @Test
  void minimalQuoting() throws IOException {
    assertEquals(
        "a,\"b,c\",\"say \"\"hi\"\"\",,\r\n", write(DEFAULT, one("a", "b,c", "say \"hi\"", "", null)));
    assertEquals("\"\"\r\n", write(DEFAULT, one("")));
    assertEquals("\" x\",\"#y\",\"z \",é\r\n", write(DEFAULT, one(" x", "#y", "z ", "é")));
    assertEquals("\"a\nb\",\"c\rd\"\r\n", write(DEFAULT, one("a\nb", "c\rd")));
    assertEquals("1,2.5,true\r\n", write(DEFAULT, one(1L, 2.5, true)));
  }

  @Test
  void quoteModes() throws IOException {
    CsvFormat all = DEFAULT.toBuilder().setQuoteMode(CsvFormat.QuoteMode.ALL).setNullString("NULL").build();
    assertEquals("\"NULL\",\"x\",\"1\"\r\n", write(all, one(null, "x", 1L)));
    CsvFormat allNonNull =
        DEFAULT.toBuilder().setQuoteMode(CsvFormat.QuoteMode.ALL_NON_NULL).setNullString("NULL").build();
    assertEquals("NULL,\"x\"\r\n", write(allNonNull, one(null, "x")));
    CsvFormat nonNumeric = DEFAULT.toBuilder().setQuoteMode(CsvFormat.QuoteMode.NON_NUMERIC).build();
    assertEquals("\"x\",1,2.5,\"true\"\r\n", write(nonNumeric, one("x", 1L, 2.5, true)));
    CsvFormat none = DEFAULT.toBuilder().setQuoteMode(CsvFormat.QuoteMode.NONE).setEscape('\\').build();
    assertEquals("a\\,b,c\\nd\\re,f\\\\g,\"q\"\r\n", write(none, one("a,b", "c\nd\re", "f\\g", "\"q\"")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CsvWriter(
                new StringBuilder(),
                DEFAULT.toBuilder()
                    .setQuote((Character) null)
                    .setQuoteMode(CsvFormat.QuoteMode.ALL)
                    .setNullString("N")
                    .build()));
  }

  @Test
  void escapesAndNullStrings() throws IOException {
    CsvFormat informix = CsvFormat.Predefined.INFORMIX_UNLOAD.format();
    assertEquals("\"a\\\"b\"|\"c\\\\d\"|e\n", write(informix, one("a\"b", "c\\d", "e")));
    CsvFormat mysql = CsvFormat.Predefined.MYSQL.format();
    assertEquals("a\\\tb\t\\N\tx\\ny\n", write(mysql, one("a\tb", null, "x\ny")));
    CsvFormat pg = CsvFormat.Predefined.POSTGRESQL_CSV.format();
    assertEquals("\"a\",,\"\"\n", write(pg, one("a", null, "")));
    CsvFormat multi = DEFAULT.toBuilder().setDelimiter("||").setEscape('\\').setQuote((Character) null).build();
    assertEquals("a\\|\\|b||c|d\r\n", write(multi, one("a||b", "c|d")));
  }

  @Test
  void separatorsTrailingDelimiterAndTrim() throws IOException {
    CsvFormat format =
        DEFAULT.toBuilder().setRecordSeparator("\n").setTrailingDelimiter(true).setTrim(true).build();
    assertEquals("a,b,\n,\n", write(format, List.of(List.of(" a ", "b"), java.util.Arrays.asList((Object) null))));
    CsvFormat noSeparator = DEFAULT.toBuilder().setRecordSeparator(null).build();
    assertEquals("a,bc", write(noSeparator, List.of(List.of("a", "b"), List.of("c"))));
  }

  @Test
  void headersAndComments() throws IOException {
    CsvFormat format =
        DEFAULT.toBuilder()
            .setCommentMarker('#')
            .setHeaderComments("c1", "line\r\nbreak")
            .setHeader("h1", "h2")
            .build();
    assertEquals("# c1\r\n# line\r\n# break\r\nh1,h2\r\n1,2\r\n", write(format, List.of(List.of(1L, 2L))));
    CsvFormat skipped = format.toBuilder().setSkipHeaderRecord(true).setHeaderComments().build();
    assertEquals("1,2\r\n", write(skipped, List.of(List.of(1L, 2L))));
    StringBuilder out = new StringBuilder();
    try (CsvWriter writer = new CsvWriter(out, format.toBuilder().setHeaderComments().build())) {
      writer.print("a");
      writer.printComment("mid\nrecord");
      writer.printRecord("b");
      assertEquals(2, writer.recordCount());
    }
    assertEquals("h1,h2\r\na\r\n# mid\r\n# record\r\nb\r\n", out.toString());
    // Without a comment marker, comments print nothing.
    StringBuilder none = new StringBuilder();
    new CsvWriter(none, DEFAULT).printComment("x");
    assertEquals("", none.toString());
  }

  @Test
  void printBatch() throws IOException {
    CsvBatch flexible = Csv.parse(utf8("a,b\n1\n2,\"x,y\"\n"), CsvFormat.RFC4180.withFlexible(true));
    StringBuilder out = new StringBuilder();
    try (CsvWriter writer = new CsvWriter(out, CsvFormat.RFC4180)) {
      writer.printBatch(flexible);
    }
    assertEquals("a,b\r\n1\r\n2,\"x,y\"\r\n", out.toString());
    Schema schema =
        Schema.of(
            Field.requiredLong("id"),
            new Field("price", Field.Kind.FLOAT64, true, false),
            new Field("ok", Field.Kind.BOOLEAN, false, false));
    CsvBatch typed = CsvPlan.compile(schema, CsvFormat.RFC4180).parse(utf8("id,price,ok\n7,,TRUE\n8,2.5,false\n"));
    CsvFormat nonNumeric =
        CsvFormat.RFC4180.toBuilder()
            .setQuoteMode(CsvFormat.QuoteMode.NON_NUMERIC)
            .setNullString("NULL")
            .setRecordSeparator("\n")
            .build();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (CsvWriter writer = CsvWriter.open(bytes, nonNumeric)) {
      writer.printBatch(typed);
      writer.printBatch(typed); // the header is printed once
    }
    assertEquals(
        "\"id\",\"price\",\"ok\"\n7,NULL,\"true\"\n8,2.5,\"false\"\n7,NULL,\"true\"\n8,2.5,\"false\"\n",
        bytes.toString(StandardCharsets.UTF_8));
    // Round trip: what we write, we read back.
    CsvBatch back = CsvPlan.compile(schema, nonNumeric).parse(bytes.toByteArray());
    assertEquals(4, back.size());
    assertTrue(back.row(0).isNull(1));
    assertEquals(2.5, back.row(1).getDouble(1));
    assertTrue(back.row(0).getBoolean(2));
  }

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
