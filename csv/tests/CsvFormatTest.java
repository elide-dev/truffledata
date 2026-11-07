package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.UnaryOperator;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;

final class CsvFormatTest {
  private static Character character(int value) {
    return value < 0 ? null : Character.valueOf((char) value);
  }

  @Test
  void predefinedFormatsMatchCommons() {
    for (CsvFormat.Predefined p : CsvFormat.Predefined.values()) {
      CsvFormat ours = p.format();
      CSVFormat theirs = CommonsFormats.commons(p).getFormat();
      String name = p.name();
      assertFalse(ours.strict(), name);
      assertTrue(ours.flexible(), name);
      assertEquals(theirs.getDelimiterString(), ours.delimiterString(), name);
      assertEquals(
          theirs.getQuoteCharacter(), ours.quoting() ? Character.valueOf((char) ours.quote()) : null, name);
      assertEquals(theirs.getEscapeCharacter(), character(ours.escape()), name);
      assertEquals(theirs.getCommentMarker(), character(ours.commentMarker()), name);
      assertNull(theirs.getHeader(), name);
      assertFalse(ours.header(), name);
      assertNull(ours.headerNames(), name);
      assertEquals(theirs.getSkipHeaderRecord(), ours.skipHeaderRecord(), name);
      assertEquals(theirs.getAllowMissingColumnNames(), ours.allowMissingColumnNames(), name);
      assertEquals(theirs.getDuplicateHeaderMode().name(), ours.duplicateHeaderMode().name(), name);
      assertEquals(theirs.getIgnoreHeaderCase(), ours.ignoreHeaderCase(), name);
      assertEquals(theirs.getIgnoreEmptyLines(), ours.ignoreEmptyLines(), name);
      assertEquals(theirs.getIgnoreSurroundingSpaces(), ours.ignoreSurroundingSpaces(), name);
      assertEquals(theirs.getTrim(), ours.trim(), name);
      List<String> nulls = ours.nullStrings();
      assertEquals(theirs.getNullString(), nulls == null ? null : nulls.get(0), name);
      assertEquals(
          theirs.getQuoteMode() == null ? null : theirs.getQuoteMode().name(),
          ours.quoteMode() == null ? null : ours.quoteMode().name(),
          name);
      assertEquals(theirs.getTrailingDelimiter(), ours.trailingDelimiter(), name);
      assertEquals(theirs.getTrailingData(), ours.trailingData(), name);
      assertEquals(theirs.getLenientEof(), ours.lenientEof(), name);
      assertEquals(theirs.getMaxRows(), ours.maxRows(), name);
      assertEquals(theirs.getRecordSeparator(), ours.recordSeparator(), name);
      assertEquals(theirs.getAutoFlush(), ours.autoFlush(), name);
      // The helper round-trips to an equal Commons format.
      assertEquals(theirs, CommonsFormats.toCommons(ours), name);
    }
  }

  /** One builder mutation applied to both libraries. */
  private record Mutation(
      String name,
      UnaryOperator<CsvFormat.Builder> ours,
      UnaryOperator<CSVFormat.Builder> theirs) {}

  @Test
  void validationMatchesCommons() {
    List<Mutation> rejected =
        List.of(
            new Mutation("quote in delimiter", b -> b.setDelimiter("a\"b"), b -> b.setDelimiter("a\"b")),
            new Mutation("quote is delimiter", b -> b.setQuote(','), b -> b.setQuote(',')),
            new Mutation("escape is delimiter", b -> b.setEscape(','), b -> b.setEscape(',')),
            new Mutation("comment is delimiter", b -> b.setCommentMarker(','), b -> b.setCommentMarker(',')),
            new Mutation("comment is quote", b -> b.setCommentMarker('"'), b -> b.setCommentMarker('"')),
            new Mutation(
                "comment is escape",
                b -> b.setEscape('#').setCommentMarker('#'),
                b -> b.setEscape('#').setCommentMarker('#')),
            new Mutation(
                "NONE without escape",
                b -> b.setQuoteMode(CsvFormat.QuoteMode.NONE),
                b -> b.setQuoteMode(org.apache.commons.csv.QuoteMode.NONE)),
            new Mutation(
                "duplicate explicit header",
                b -> b.setDuplicateHeaderMode(CsvFormat.DuplicateHeaderMode.DISALLOW).setHeader("a", "a"),
                b -> b.setDuplicateHeaderMode(org.apache.commons.csv.DuplicateHeaderMode.DISALLOW).setHeader("a", "a")),
            new Mutation(
                "duplicate blank header without ALLOW_EMPTY",
                b -> b.setDuplicateHeaderMode(CsvFormat.DuplicateHeaderMode.DISALLOW).setHeader(" ", ""),
                b -> b.setDuplicateHeaderMode(org.apache.commons.csv.DuplicateHeaderMode.DISALLOW).setHeader(" ", "")));
    for (Mutation m : rejected) {
      assertThrows(
          IllegalArgumentException.class,
          () -> m.theirs.apply(CSVFormat.DEFAULT.builder()).get(),
          "Commons " + m.name);
      assertThrows(
          IllegalArgumentException.class,
          () -> m.ours.apply(CsvFormat.Predefined.DEFAULT.format().toBuilder()).build(),
          m.name);
    }
    List<Mutation> accepted =
        List.of(
            new Mutation(
                "blank duplicates with ALLOW_EMPTY",
                b -> b.setDuplicateHeaderMode(CsvFormat.DuplicateHeaderMode.ALLOW_EMPTY).setHeader("", " "),
                b -> b.setDuplicateHeaderMode(org.apache.commons.csv.DuplicateHeaderMode.ALLOW_EMPTY).setHeader("", " ")),
            new Mutation("escape equals quote", b -> b.setEscape('"'), b -> b.setEscape('"')),
            new Mutation("multi-character delimiter", b -> b.setDelimiter("[|]"), b -> b.setDelimiter("[|]")),
            new Mutation(
                "duplicates allowed by default", b -> b.setHeader("a", "a"), b -> b.setHeader("a", "a")));
    for (Mutation m : accepted) {
      m.theirs.apply(CSVFormat.DEFAULT.builder()).get();
      m.ours.apply(CsvFormat.Predefined.DEFAULT.format().toBuilder()).build();
    }
    // Ours only: meta characters are ASCII, and delimiters are at most 16 bytes.
    CsvFormat.Builder commons = CsvFormat.Predefined.DEFAULT.format().toBuilder();
    assertThrows(IllegalArgumentException.class, () -> commons.setDelimiter("é"));
    assertThrows(IllegalArgumentException.class, () -> commons.setEscape('é'));
    assertThrows(IllegalArgumentException.class, () -> commons.setDelimiter("").build());
    assertThrows(IllegalArgumentException.class, () -> commons.setDelimiter("x".repeat(17)).build());
    assertThrows(IllegalArgumentException.class, () -> commons.setDelimiter("a\nb").build());
  }

  @Test
  void strictRejectsCommonsOnlyOptions() {
    List<UnaryOperator<CsvFormat.Builder>> commonsOnly =
        List.of(
            b -> b.setEscape('\\'),
            b -> b.setCommentMarker('#'),
            b -> b.setDelimiter("||"),
            b -> b.setIgnoreSurroundingSpaces(true),
            b -> b.setTrailingDelimiter(true),
            b -> b.setTrailingData(true),
            b -> b.setLenientEof(true),
            b -> b.setIgnoreEmptyLines(false));
    for (UnaryOperator<CsvFormat.Builder> option : commonsOnly) {
      IllegalArgumentException e =
          assertThrows(IllegalArgumentException.class, () -> option.apply(CsvFormat.builder()).build());
      assertTrue(e.getMessage().contains("requires a Commons (non-strict) format"), e.getMessage());
      option.apply(CsvFormat.builder().setStrict(false)).build();
    }
    // Strict formats take the header, null, quote-mode and writer options.
    CsvFormat strict =
        CsvFormat.builder()
            .setHeader("a", "b")
            .setSkipHeaderRecord(true)
            .setNullStrings("NA", "")
            .setQuoteMode(CsvFormat.QuoteMode.ALL_NON_NULL)
            .setMaxRows(10)
            .setRecordSeparator("\n")
            .setIgnoreHeaderCase(true)
            .build();
    assertTrue(strict.strict());
    assertEquals(List.of("a", "b"), strict.headerNames());
    assertEquals(List.of("NA", ""), strict.nullStrings());
  }

  @Test
  void rfc4180Unchanged() {
    CsvFormat f = CsvFormat.RFC4180;
    assertTrue(f.strict());
    assertEquals((byte) ',', f.delimiter());
    assertEquals(",", f.delimiterString());
    assertEquals((byte) '"', f.quote());
    assertTrue(f.quoting());
    assertTrue(f.header());
    assertNull(f.headerNames());
    assertFalse(f.trim());
    assertFalse(f.flexible());
    assertEquals(-1, f.escape());
    assertEquals(-1, f.commentMarker());
    assertTrue(f.ignoreEmptyLines());
    assertTrue(f.allowMissingColumnNames());
    assertEquals(CsvFormat.DuplicateHeaderMode.ALLOW_ALL, f.duplicateHeaderMode());
    assertNull(f.nullStrings());
    assertNull(f.quoteMode());
    assertEquals("\r\n", f.recordSeparator());
    assertEquals(f, CsvFormat.builder().build());
    assertEquals(f, f.toBuilder().build());
    assertEquals(f.hashCode(), CsvFormat.builder().build().hashCode());
    assertFalse(f.withoutQuoting().quoting());
    assertFalse(f.withHeader(false).header());
    assertTrue(f.withHeader().header());
    assertEquals(List.of("x"), f.withHeader("x").headerNames());
    assertFalse(f.withHeader("x").header());
    assertTrue(f.withTrim(true).trim());
    assertTrue(f.withFlexible(true).flexible());
    assertEquals((byte) ';', f.withDelimiter(';').delimiter());
    assertEquals((byte) '\'', f.withQuote('\'').quote());
    assertEquals(List.of("NA"), f.withNullStrings("NA").nullStrings());
    assertNull(f.withNullStrings().nullStrings());
    assertEquals(CsvFormat.QuoteMode.MINIMAL, f.withQuoteMode(CsvFormat.QuoteMode.MINIMAL).quoteMode());
    assertNotEquals(f, CsvFormat.Predefined.RFC4180.format());
  }
}
