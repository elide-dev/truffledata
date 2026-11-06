package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class CsvScannerTest {
  /** Collects records as unescaped strings plus their raw spans. */
  private static final class Collector implements CsvScanner.Records {
    final byte[] input;
    final byte quote;
    final List<List<String>> records = new ArrayList<>();
    final List<long[]> spans = new ArrayList<>();
    final List<int[]> bounds = new ArrayList<>();

    Collector(byte[] input, byte quote) {
      this.input = input;
      this.quote = quote;
    }

    @Override
    public boolean record(long[] spans, int count, int start, int end) {
      assertTrue(spans.length >= count);
      List<String> record = new ArrayList<>();
      for (int i = 0; i < count; i++) record.add(text(input, spans[i], quote));
      records.add(record);
      long[] copy = new long[count];
      System.arraycopy(spans, 0, copy, 0, count);
      this.spans.add(copy);
      bounds.add(new int[] {start, end});
      return true;
    }
  }

  static String text(byte[] input, long span, byte quote) {
    byte[] out = new byte[CsvScanner.length(span)];
    int length = CsvScanner.unescape(input, span, quote, out, 0);
    return new String(out, 0, length, StandardCharsets.UTF_8);
  }

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static Collector scan(String csv, CsvFormat format, CsvLimits limits, boolean eof) {
    byte[] input = utf8(csv);
    Collector collector = new Collector(input, format.quote());
    int end = new CsvScanner(format, limits).scan(input, 0, input.length, eof, collector);
    if (eof) assertEquals(input.length, end);
    return collector;
  }

  private static List<List<String>> records(String csv, CsvFormat format) {
    return scan(csv, format, CsvLimits.DEFAULT, true).records;
  }

  private static List<List<String>> records(String csv) {
    return records(csv, CsvFormat.RFC4180);
  }

  @Test
  void rfcCases() {
    assertEquals(List.of(List.of("a,b", "c")), records("\"a,b\",c\n"));
    assertEquals(List.of(List.of("line\r\nbreak", "x")), records("\"line\r\nbreak\",x\r\n"));
    Collector escaped = scan("\"say \"\"hi\"\"\",plain\n", CsvFormat.RFC4180, CsvLimits.DEFAULT, true);
    assertEquals(List.of(List.of("say \"hi\"", "plain")), escaped.records);
    long span = escaped.spans.get(0)[0];
    assertTrue(CsvScanner.quoted(span));
    assertTrue(CsvScanner.escaped(span));
    assertEquals(1, CsvScanner.offset(span));
    assertEquals("say \"\"hi\"\"".length(), CsvScanner.length(span));
    long plain = escaped.spans.get(0)[1];
    assertFalse(CsvScanner.quoted(plain));
    assertFalse(CsvScanner.escaped(plain));
    assertEquals(List.of(List.of("a", "", "b")), records("a,,b\n"));
    assertEquals(List.of(List.of("", "")), records(",\n"));
    assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), records("a,b\nc,d"));
    assertEquals(
        List.of(List.of("a"), List.of("b"), List.of("c"), List.of("d")),
        records("a\r\nb\nc\rd\r\n"));
    assertEquals(List.of(List.of("a"), List.of("b")), records("\n\r\n\ra\n\n\r\n\rb\n\n"));
    Collector empty = scan("\"\",x\n", CsvFormat.RFC4180, CsvLimits.DEFAULT, true);
    assertEquals(List.of(List.of("", "x")), empty.records);
    assertTrue(CsvScanner.quoted(empty.spans.get(0)[0]));
    assertEquals(0, CsvScanner.length(empty.spans.get(0)[0]));
    assertEquals(List.of(List.of("")), records("\"\""));
    assertEquals(List.of(), records(""));
    // Record bounds: start of the record and the offset after its terminator.
    Collector bounds = scan("ab,c\r\nd\n", CsvFormat.RFC4180, CsvLimits.DEFAULT, true);
    assertArrayEquals(new int[] {0, 6}, bounds.bounds.get(0));
    assertArrayEquals(new int[] {6, 8}, bounds.bounds.get(1));
  }

  private static void rejects(String csv, CsvFormat format, CsvLimits limits, String message) {
    var e = assertThrows(IllegalArgumentException.class, () -> scan(csv, format, limits, true));
    assertTrue(e.getMessage().contains(message), e.getMessage());
  }

  @Test
  void strictRejections() {
    rejects("x\na\"b\n", CsvFormat.RFC4180, CsvLimits.DEFAULT, "CSV syntax at record 2, byte 3:");
    rejects("\"a\"b\n", CsvFormat.RFC4180, CsvLimits.DEFAULT, "CSV syntax at record 1, byte 3:");
    rejects("a,\"bc\n", CsvFormat.RFC4180, CsvLimits.DEFAULT, "CSV syntax at record 1, byte 2:");
    rejects("\"a\" \n", CsvFormat.RFC4180, CsvLimits.DEFAULT, "CSV syntax at record 1, byte 3:");
    CsvLimits three = new CsvLimits(1 << 20, 1 << 10, 3, 1 << 10);
    assertEquals(
        List.of(List.of("a", "b", "c")),
        scan("a,b,c\n", CsvFormat.RFC4180, three, true).records);
    rejects("a,b,c,d\n", CsvFormat.RFC4180, three, "CSV resource limit: maxColumns");
    rejects("a,b,c,\n", CsvFormat.RFC4180, three, "CSV resource limit: maxColumns");
    CsvLimits small = new CsvLimits(1 << 20, 4, 8, 1 << 10);
    assertEquals(List.of(List.of("abcd")), scan("abcd\r\n", CsvFormat.RFC4180, small, true).records);
    rejects("abcde\n", CsvFormat.RFC4180, small, "CSV resource limit: maxRecordBytes");
  }

  @Test
  void wideRecordsGrowTheSpanTableUpToMaxColumns() {
    List<String> wide = new ArrayList<>();
    for (int i = 0; i < 40; i++) wide.add("f" + i);
    assertEquals(List.of(wide, wide), records(String.join(",", wide) + "\n" + String.join(",", wide)));
    // The table grows 16 -> 20 (capped at maxColumns), then the 21st field fails.
    CsvLimits twenty = new CsvLimits(1 << 20, 1 << 10, 20, 1 << 10);
    assertEquals(
        List.of(wide.subList(0, 20)),
        scan(String.join(",", wide.subList(0, 20)) + "\n", CsvFormat.RFC4180, twenty, true).records);
    rejects(
        String.join(",", wide.subList(0, 21)) + "\n",
        CsvFormat.RFC4180,
        twenty,
        "CSV resource limit: maxColumns");
  }

  @Test
  void trimAndUnquoted() {
    CsvFormat trim = CsvFormat.RFC4180.withTrim(true);
    assertEquals(
        List.of(List.of("a", "b c", "q", "", " in ")),
        records(" a ,\tb c\t, \"q\" \t,  ,\" in \"\n", trim));
    Collector spans = scan(" a , \"\" \n", trim, CsvLimits.DEFAULT, true);
    assertFalse(CsvScanner.quoted(spans.spans.get(0)[0]));
    assertTrue(CsvScanner.quoted(spans.spans.get(0)[1]));
    assertEquals(0, CsvScanner.length(spans.spans.get(0)[1]));
    // Without trim, blanks are content and a quote after a blank is a bare quote.
    assertEquals(List.of(List.of(" a ", "b")), records(" a ,b\n"));
    rejects(" \"a\"\n", CsvFormat.RFC4180, CsvLimits.DEFAULT, "byte 1:");
    rejects(" \"a\" x\n", trim, CsvLimits.DEFAULT, "byte 5:");
    CsvFormat raw = CsvFormat.RFC4180.withoutQuoting();
    assertEquals(List.of(List.of("\"a", "b\"\"c\"")), records("\"a,b\"\"c\"\n", raw));
    CsvFormat semicolon = CsvFormat.RFC4180.withDelimiter(';').withQuote('\'');
    assertEquals(List.of(List.of("a;b", "c,d\"")), records("'a;b';c,d\"\n", semicolon));
  }

  @Test
  void ambiguousTailIsIncomplete() {
    for (String tail : List.of("\"a\"", "\"a\"\"", "x\r", "x", "x,", "\"a")) {
      String csv = "first\n" + tail;
      Collector partial = scan(csv, CsvFormat.RFC4180, CsvLimits.DEFAULT, false);
      byte[] input = utf8(csv);
      Collector check = new Collector(input, (byte) '"');
      int end = new CsvScanner(CsvFormat.RFC4180, CsvLimits.DEFAULT).scan(input, 0, input.length, false, check);
      assertEquals(6, end, tail);
      assertEquals(List.of(List.of("first")), partial.records, tail);
    }
    assertEquals(List.of(List.of("first"), List.of("a")), records("first\n\"a\""));
    assertEquals(List.of(List.of("first"), List.of("x")), records("first\nx\r"));
    assertEquals(List.of(List.of("first"), List.of("x")), records("first\nx"));
    assertEquals(List.of(List.of("first"), List.of("x", "")), records("first\nx,"));
    rejects("first\n\"a\"\"", CsvFormat.RFC4180, CsvLimits.DEFAULT, "CSV syntax at record 2, byte 6:");
    // A CR followed by LF in the same buffer is one terminator.
    byte[] input = utf8("x\r\n");
    Collector crlf = new Collector(input, (byte) '"');
    assertEquals(3, new CsvScanner(CsvFormat.RFC4180, CsvLimits.DEFAULT).scan(input, 0, 3, false, crlf));
    assertArrayEquals(new int[] {0, 3}, crlf.bounds.get(0));
  }

  @Test
  void stopsBeforeRefusedRecord() {
    byte[] input = utf8("a\nb\nc\n");
    List<String> seen = new ArrayList<>();
    int end =
        new CsvScanner(CsvFormat.RFC4180, CsvLimits.DEFAULT)
            .scan(
                input,
                0,
                input.length,
                true,
                (spans, count, start, stop) -> {
                  if (seen.size() == 2) return false;
                  seen.add(text(input, spans[0], (byte) '"'));
                  return true;
                });
    assertEquals(4, end);
    assertEquals(List.of("a", "b"), seen);
  }

  @Test
  void roundTripProperty() {
    Random random = new Random(0x5eed_c5f);
    CsvScanner scanner = new CsvScanner(CsvFormat.RFC4180, CsvLimits.DEFAULT);
    for (int i = 0; i < 2000; i++) {
      RecordGenerator.Encoded set = RecordGenerator.generate(random);
      Collector collector = new Collector(set.bytes(), (byte) '"');
      int end = scanner.scan(set.bytes(), 0, set.bytes().length, true, collector);
      assertEquals(set.bytes().length, end, "set " + i);
      assertEquals(set.records(), collector.records, "set " + i);
    }
  }

  @Test
  void formatAndLimitsValidate() {
    assertThrows(IllegalArgumentException.class, () -> CsvFormat.RFC4180.withDelimiter('"'));
    assertThrows(IllegalArgumentException.class, () -> CsvFormat.RFC4180.withDelimiter('\n'));
    assertThrows(IllegalArgumentException.class, () -> CsvFormat.RFC4180.withQuote('\r'));
    assertThrows(IllegalArgumentException.class, () -> CsvFormat.RFC4180.withDelimiter('é'));
    assertThrows(IllegalArgumentException.class, () -> CsvFormat.RFC4180.withDelimiter('\0'));
    assertThrows(
        IllegalArgumentException.class, () -> CsvFormat.RFC4180.withDelimiter('\t').withTrim(true));
    assertEquals((byte) '\t', CsvFormat.RFC4180.withDelimiter('\t').delimiter());
    assertTrue(CsvFormat.RFC4180.header());
    assertFalse(CsvFormat.RFC4180.trim());
    assertFalse(CsvFormat.RFC4180.flexible());
    assertThrows(IllegalArgumentException.class, () -> new CsvLimits(0, 1, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> new CsvLimits(10, 11, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> new CsvLimits(Integer.MAX_VALUE, 1, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> new CsvLimits(1 << 30, 1 << 30, 1, 1));
    assertEquals(64 << 20, CsvLimits.DEFAULT.maxBatchBytes());
    assertThrows(
        IllegalArgumentException.class, () -> CsvFormat.RFC4180.withQuote(' ').withTrim(true));
    assertEquals((byte) ' ', CsvFormat.RFC4180.withoutQuoting().withQuote(' ').withTrim(true).quote());
    // Formats and limits are values: equal options are interchangeable.
    CsvFormat tab = CsvFormat.RFC4180.withDelimiter('\t');
    assertEquals(tab, CsvFormat.RFC4180.withDelimiter('\t'));
    assertEquals(tab.hashCode(), CsvFormat.RFC4180.withDelimiter('\t').hashCode());
    assertNotEquals(tab, CsvFormat.RFC4180);
    assertTrue(tab.toString().startsWith("CsvFormat["), tab.toString());
    CsvLimits limits = new CsvLimits(64 << 20, 1 << 20, 4096, 1 << 20);
    assertEquals(CsvLimits.DEFAULT, limits);
    assertEquals(CsvLimits.DEFAULT.hashCode(), limits.hashCode());
    assertNotEquals(CsvLimits.DEFAULT, new CsvLimits(1, 1, 1, 1));
    assertTrue(limits.toString().startsWith("CsvLimits["), limits.toString());
  }
}
