package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** One case per Commons Lexer and header rule, each checked against Commons CSV itself. */
final class CommonsScannerTest {
  private static final CsvFormat DEFAULT = CsvFormat.Predefined.DEFAULT.format();

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  @SafeVarargs
  private static List<List<@Nullable String>> rows(List<@Nullable String>... rows) {
    return Arrays.asList(rows);
  }

  private static List<@Nullable String> row(@Nullable String... values) {
    return Arrays.asList(values);
  }

  /** Parses with both libraries, asserts equal records, and returns them. */
  private static List<List<@Nullable String>> both(String input, CsvFormat format) {
    Differential.Result theirs = Differential.commons(input, CommonsFormats.toCommons(format));
    Differential.Result ours = Differential.ours(utf8(input), format);
    assertTrue(theirs.ok(), () -> "Commons: " + theirs.error());
    assertTrue(ours.ok(), () -> "ours: " + ours.error());
    assertEquals(theirs.records(), ours.records());
    return ours.records();
  }

  /** Both libraries reject; returns our message. */
  private static String bothFail(String input, CsvFormat format) {
    Differential.Result theirs = Differential.commons(input, CommonsFormats.toCommons(format));
    assertFalse(theirs.ok(), () -> "Commons parsed " + theirs.records());
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> Csv.parse(utf8(input), format));
    return e.getMessage();
  }

  @Test
  void escapes() {
    CsvFormat informix = CsvFormat.Predefined.INFORMIX_UNLOAD.format();
    assertEquals(rows(row("a\nb\tc", "d")), both("a\\nb\\tc|d\n", informix));
    assertEquals(rows(row("a|b", "c")), both("a\\|b|c\n", informix));
    assertEquals(rows(row("a\\qb", "c")), both("a\\qb|c\n", informix));
    assertEquals(rows(row("a\\b", "x\"")), both("a\\\\b|\"x\\\"\"\n", informix));
    assertEquals(rows(row("a\r", "b")), both("a\\\r|b", informix));
    assertEquals(rows(row("x\\é", "y")), both("x\\é|y", informix));
    assertTrue(bothFail("a\\", informix).contains("escape at end of input"));
    assertTrue(bothFail("\"a\\", informix).contains("escape at end of input"));
    CsvFormat brackets = informix.toBuilder().setDelimiter("[|]").build();
    assertEquals(rows(row("a", "b[|]c", "d")), both("a[|]b\\[\\|\\]c[|]d\n", brackets));
    assertEquals(rows(row("a[|", "b")), both("a[|[|]b\n", brackets));
  }

  @Test
  void quotesAndEscapeEqualToQuote() {
    assertEquals(rows(row("say \"hi\"", "b")), both("\"say \"\"hi\"\"\",b\n", DEFAULT));
    assertEquals(rows(row("a\"b", "c")), both("a\"b,c\n", DEFAULT)); // bare quote is content
    CsvFormat mongo = CsvFormat.Predefined.MONGODB_CSV.format();
    assertEquals(rows(row("a\"b", "c\"d", "e\"z", "e\f")), both("a\"\"b,\"c\"\"d\",e\"z,e\"f\n", mongo));
    assertEquals(rows(row("x,y", "z")), both("x\",y,z\n", mongo));
  }

  @Test
  void commentsOnlyAtLineStart() {
    CsvFormat comments = DEFAULT.toBuilder().setCommentMarker('#').build();
    assertEquals(rows(row("a", "b"), row(" #c")), both("#one\n#two\r\na,b\n #c\n# tail", comments));
    assertEquals(rows(row("a#", "#b")), both("a#,#b\n", comments));
    assertEquals(rows(), both("#", comments));
    CsvFormat keepEmpty = comments.toBuilder().setIgnoreEmptyLines(false).build();
    assertEquals(rows(row(""), row("a")), both("#c\n\na\n", keepEmpty));
  }

  @Test
  void emptyLines() {
    assertEquals(rows(row("a"), row("b")), both("\n\na\n\r\n\rb\n\n", DEFAULT));
    CsvFormat rfc = CsvFormat.Predefined.RFC4180.format();
    assertEquals(rows(row(""), row("a"), row(""), row("b")), both("\na\n\nb\n", rfc));
    assertEquals(rows(row(" ")), both(" \n", DEFAULT));
  }

  @Test
  void surroundingSpacesAndTrim() {
    CsvFormat tdf = CsvFormat.Predefined.TDF.format();
    assertEquals(rows(row("a", "b c", "")), both("　a \t b c \t  \n", tdf));
    assertEquals(rows(row(" a ", "q ")), both(" a \t \"q \" \n", tdf));
    CsvFormat iss = DEFAULT.toBuilder().setIgnoreSurroundingSpaces(true).setEscape('\\').build();
    assertEquals(rows(row("a", "b")), both(" a\\t ,b\n", iss)); // escaped tab trimmed as content
    CsvFormat trim = DEFAULT.toBuilder().setTrim(true).build();
    assertEquals(rows(row("a", "b", "c d")), both(" a ,\" b \",\tc d\u000b\n", trim));
    assertEquals(rows(row("a", "　b　")), both("a,　b　\n", trim));
  }

  @Test
  void afterClosingQuote() {
    assertEquals(rows(row("a", "b")), both("\"a\"   ,b\n", DEFAULT));
    assertTrue(bothFail("\"a\"x,b\n", DEFAULT).contains("unexpected byte after a closing quote"));
    bothFail("\"a\" ,b\n", DEFAULT);
    CsvFormat trailing = DEFAULT.toBuilder().setTrailingData(true).build();
    assertEquals(rows(row("ax y", "b")), both("\"a\"x y,b\n", trailing));
    assertEquals(rows(row("a \"x\"", "b")), both("\"a\" \"x\",b\n", trailing));
  }

  @Test
  void lenientEofAndUnterminated() {
    assertTrue(bothFail("a,\"bc", DEFAULT).contains("unterminated quoted field"));
    CsvFormat excel = CsvFormat.Predefined.EXCEL.format();
    assertEquals(rows(row("a", "bc\n")), both("a,\"bc\n", excel));
  }

  @Test
  void trailingDelimiter() {
    CsvFormat trailing = DEFAULT.toBuilder().setTrailingDelimiter(true).build();
    assertEquals(rows(row("a", "b"), row("c", "")), both("a,b,\nc,,\n", trailing));
    assertEquals(rows(row("a", "b")), both("a,b,\"\"\n", trailing));
    // Commons stops silently at a record emptied by the trailing delimiter; we reject it.
    CsvFormat keep = trailing.toBuilder().setIgnoreEmptyLines(false).build();
    assertEquals(rows(row("a")), Differential.commons("a\n\nb\n", CommonsFormats.toCommons(keep)).records());
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> Csv.parse(utf8("a\n\nb\n"), keep));
    assertEquals(
        "CSV syntax at record 2, byte 2: empty record with a trailing delimiter", e.getMessage());
  }

  @Test
  void multiByteDelimiters() {
    CsvFormat pipes = DEFAULT.toBuilder().setDelimiter("||").build();
    assertEquals(rows(row("a", "b|c", "|"), row("x|")), both("a||b|c|||\nx|\n", pipes));
    assertEquals(rows(row("a", "")), both("a||", pipes));
  }

  @Test
  void maxRowsCountsDataRecords() {
    CsvFormat header = DEFAULT.toBuilder().setHeader().setMaxRows(1).build();
    assertEquals(rows(row("1")), both("h\n1\n\"bad", header));
    CsvFormat plain = DEFAULT.toBuilder().setMaxRows(2).build();
    assertEquals(rows(row("1"), row("2")), both("1\n2\n\"bad", plain));
    // Streams stop too.
    assertEquals(
        rows(row("1")), Differential.streamed(utf8("h\n1\n\"bad"), header, 2).records());
    CsvFormat skip = DEFAULT.toBuilder().setHeader("x").setSkipHeaderRecord(true).setMaxRows(1).build();
    assertEquals(rows(row("1")), both("h\n1\n\"bad", skip));
    // Strict formats take maxRows as well.
    CsvBatch strict = Csv.parse(utf8("h\n1\n2\n\"bad"), CsvFormat.RFC4180.toBuilder().setMaxRows(2).build());
    assertEquals(2, strict.size());
  }

  @Test
  void headerOptions() {
    CsvFormat first = DEFAULT.toBuilder().setHeader().build();
    CsvBatch batch = Csv.parse(utf8("x,y\n1,2\n"), first);
    assertEquals(List.of("x", "y"), Differential.names(batch));
    assertEquals(rows(row("1", "2")), both("x,y\n1,2\n", first));
    // Explicit names, with and without skipping the first record.
    CsvFormat explicit = DEFAULT.toBuilder().setHeader("p", "q").build();
    assertEquals(rows(row("x", "y"), row("1", "2")), both("x,y\n1,2\n", explicit));
    assertEquals(List.of("p", "q"), Differential.names(Csv.parse(utf8("x,y\n"), explicit)));
    CsvFormat skip = explicit.toBuilder().setSkipHeaderRecord(true).build();
    assertEquals(rows(row("1", "2")), both("x,y\n1,2\n", skip));
    assertEquals(rows(), both("", skip));
    // Missing names.
    assertTrue(bothFail("a,,b\n", first).contains("CSV header: missing name at column 1"));
    CsvFormat missing = first.toBuilder().setAllowMissingColumnNames(true).build();
    assertEquals(rows(row("1", "2", "3")), both("a, ,b\n1,2,3\n", missing));
    // Duplicates by mode.
    assertEquals(rows(row("1", "2")), both("a,a\n1,2\n", first));
    CsvFormat disallow = missing.toBuilder().setDuplicateHeaderMode(CsvFormat.DuplicateHeaderMode.DISALLOW).build();
    assertTrue(bothFail("a,a\n", disallow).contains("CSV header: duplicate name at column 1"));
    bothFail("a,,\n", disallow);
    CsvFormat allowEmpty = missing.toBuilder().setDuplicateHeaderMode(CsvFormat.DuplicateHeaderMode.ALLOW_EMPTY).build();
    assertEquals(rows(row("1", "2", "3")), both("a,,\n1,2,3\n", allowEmpty));
    bothFail("a,b,a\n", allowEmpty);
    CsvFormat noCase = disallow.toBuilder().setIgnoreHeaderCase(true).build();
    bothFail("a,A\n", noCase);
    assertEquals(rows(row("1", "2")), both("a,b\n1,2\n", noCase));
    // Empty input with a first-record header: Commons gives no names and no records.
    assertEquals(rows(), both("", first));
    assertEquals(0, Csv.parse(utf8(""), first).schema().size());
    assertThrows(IllegalArgumentException.class, () -> Csv.parse(utf8(""), CsvFormat.RFC4180));
  }

  @Test
  void strictHeaderOptions() {
    CsvFormat strict = CsvFormat.RFC4180.withHeader("p", "q");
    CsvBatch batch = Csv.parse(utf8("1,2\n"), strict);
    assertEquals(List.of("p", "q"), Differential.names(batch));
    assertEquals("1", batch.row(0).getString(0));
    CsvBatch skipped =
        Csv.parse(utf8("x,y\n1,2\n"), strict.toBuilder().setSkipHeaderRecord(true).build());
    assertEquals(1, skipped.size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Csv.parse(
                utf8("a,a\n"),
                CsvFormat.RFC4180.toBuilder()
                    .setDuplicateHeaderMode(CsvFormat.DuplicateHeaderMode.DISALLOW)
                    .build()));
    assertThrows(
        IllegalArgumentException.class,
        () -> Csv.parse(utf8("a,\n"), CsvFormat.RFC4180.toBuilder().setAllowMissingColumnNames(false).build()));
  }

  @Test
  void typedBindingIgnoresHeaderCase() {
    Schema schema = Schema.of(Field.requiredLong("id"), new Field("name", Field.Kind.STRING, false, false));
    CsvFormat noCase = DEFAULT.toBuilder().setHeader().setIgnoreHeaderCase(true).build();
    CsvBatch batch = CsvPlan.compile(schema, noCase).parse(utf8("NAME,Id\nann,7\n"));
    assertEquals(7L, batch.row(0).getLong(0));
    assertEquals("ann", batch.row(0).getString(1));
    assertThrows(
        ContractViolation.class,
        () -> CsvPlan.compile(schema, noCase.toBuilder().setIgnoreHeaderCase(false).build()).parse(utf8("NAME,Id\n")));
    // Typed fields decode complex Commons values before converting.
    CsvFormat excel = CsvFormat.Predefined.EXCEL.format().toBuilder().setHeader().build();
    CsvBatch escaped =
        CsvPlan.compile(Schema.of(Field.requiredLong("n")), excel).parse(utf8("n\n\"4\"2\n"));
    assertEquals(42L, escaped.row(0).getLong(0));
  }

  @Test
  void wideRowsAreRejected() {
    assertEquals(rows(row("a"), row("b", "c")), Differential.commons("a\nb,c\n", CommonsFormats.toCommons(DEFAULT)).records());
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> Csv.parse(utf8("a\nb,c\n"), DEFAULT));
    assertTrue(e.getMessage().contains("record has 2 fields, expected 1"), e.getMessage());
    // Shorter rows read their absent fields as missing (Commons presets are flexible).
    assertEquals(rows(row("a", "b"), row("c")), both("a,b\nc\n", DEFAULT));
  }

  @Test
  void whitespaceMatchesCharacterIsWhitespace() {
    for (int c = 0; c < 0x10000; c++) {
      if (Character.isSurrogate((char) c)) continue;
      byte[] b = String.valueOf((char) c).getBytes(StandardCharsets.UTF_8);
      boolean ours =
          b.length == 1
              ? Dialect.asciiWhitespace(b[0] & 0xFF)
              : b.length == 3 && Dialect.wideWhitespace(b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF);
      int code = c;
      assertEquals(Character.isWhitespace((char) c), ours, () -> Integer.toHexString(code));
    }
  }

  @Test
  void incompleteRecordsWaitForMoreInput() {
    // Every cut position of a stream equals the whole parse, for each Commons rule.
    CsvFormat format =
        CsvFormat.Predefined.INFORMIX_UNLOAD.format().toBuilder()
            .setDelimiter("||")
            .setCommentMarker('#')
            .setIgnoreSurroundingSpaces(true)
            .build();
    String text = "#c\r\na\\||b|| \"q\"\"x\" ||　c　\r\n\n\"m\nn\"||\\t\r";
    List<List<@Nullable String>> whole = both(text, format);
    for (int cut = 1; cut <= text.length() + 2; cut++)
      assertEquals(whole, Differential.streamed(utf8(text), format, cut).records(), "cut " + cut);
  }
}
