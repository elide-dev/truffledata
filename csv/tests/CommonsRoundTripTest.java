package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Writer round trips against Commons CSV, over seeded records (meta characters, line breaks,
 * blanks, Unicode, numbers, booleans and nulls), for every predefined format with random quote modes,
 * escapes, null strings, separators, headers and comments:
 *
 * <ol>
 *   <li>our output equals Commons {@code CSVPrinter} output, character for character;
 *   <li>Commons parses our output to what it parses from its own output;
 *   <li>we parse Commons' output to the records Commons parses (the differential rules);
 *   <li>where the format is lossless for the values, both parse back the written values.
 * </ol>
 */
final class CommonsRoundTripTest {
  private static final String[] PIECES = {
    "a", "b", "xyz", " ", "\t", ",", ";", "|", "\"", "'", "\\", "#", "\n", "\r", "\r\n", "é", "中",
    "😀", "　", "N", "\\N", "NULL", ""
  };

  private static Object value(Random r) {
    return switch (r.nextInt(10)) {
      case 0 -> r.nextLong(-1000, 1000);
      case 1 -> r.nextInt(100) / 4.0;
      case 2 -> r.nextBoolean();
      case 3 -> "";
      default -> {
        StringBuilder sb = new StringBuilder();
        int n = r.nextInt(5);
        for (int i = 0; i < n; i++) sb.append(PIECES[r.nextInt(PIECES.length)]);
        yield sb.toString();
      }
    };
  }

  private static CsvFormat format(Random r, CsvFormat.Predefined p) {
    CsvFormat.Builder b = p.format().toBuilder();
    if (r.nextInt(3) == 0) {
      CsvFormat.QuoteMode[] modes = CsvFormat.QuoteMode.values();
      b.setQuoteMode(modes[r.nextInt(modes.length)]);
    }
    if (r.nextInt(4) == 0) b.setEscape(r.nextBoolean() ? Character.valueOf('\\') : null);
    if (r.nextInt(4) == 0) b.setNullString(r.nextBoolean() ? "NULL" : "");
    if (r.nextInt(4) == 0) b.setRecordSeparator(new String[] {"\n", "\r\n", "\r"}[r.nextInt(3)]);
    if (r.nextInt(5) == 0) b.setTrailingDelimiter(true);
    if (r.nextInt(5) == 0) b.setDelimiter(new String[] {";", "||", "\t"}[r.nextInt(3)]);
    if (r.nextInt(4) == 0) b.setCommentMarker('#').setHeaderComments("made", "by\ntest");
    return b.build();
  }

  /** Values as text, the way they are written (numbers and booleans by toString). */
  private static List<@Nullable String> text(List<Object> record) {
    List<@Nullable String> out = new ArrayList<>();
    for (Object v : record) out.add(v == null ? null : v.toString());
    return out;
  }

  /** Whether reading back must return the written values exactly. */
  private static boolean lossless(CsvFormat f, List<List<Object>> records) {
    if (!f.quoting() || f.trim() || f.ignoreSurroundingSpaces() || f.trailingDelimiter()) return false;
    if (f.quoteMode() == CsvFormat.QuoteMode.NONE) return false;
    // Commons MINIMAL does not quote a value ending in a prefix of a multi-byte delimiter ("|" then
    // "||" reads as "|||"); both libraries agree, but the values do not survive.
    if (f.delimiterString().length() > 1) return false;
    String separator = f.recordSeparator();
    if (separator == null) return false;
    List<String> nulls = f.nullStrings();
    for (List<Object> record : records)
      for (Object v : record)
        if (v == null || (nulls != null && nulls.contains(v.toString()))) return false;
    return true;
  }

  @Test
  void roundTrips() throws IOException {
    Random r = new Random(1106);
    Differential d = new Differential();
    int lossless = 0;
    int cases = 0;
    for (int round = 0; round < 4_000; round++) {
      CsvFormat.Predefined p = CsvFormat.Predefined.values()[round % CsvFormat.Predefined.values().length];
      CsvFormat format;
      try {
        format = format(r, p);
      } catch (IllegalArgumentException invalid) {
        continue;
      }
      if (format.quoteMode() == CsvFormat.QuoteMode.ALL && !format.quoting() && format.nullStrings() != null)
        continue; // rejected by our writer; Commons writes "null" quote text
      int width = 1 + r.nextInt(4);
      List<List<Object>> records = new ArrayList<>();
      int count = 1 + r.nextInt(4);
      for (int i = 0; i < count; i++) {
        List<Object> record = new ArrayList<>();
        for (int c = 0; c < width; c++) record.add(r.nextInt(8) == 0 ? null : value(r));
        records.add(record);
      }
      String[] names = new String[width];
      for (int c = 0; c < width; c++) names[c] = "h" + c;
      if (r.nextBoolean()) format = format.toBuilder().setHeader(names).build();
      cases++;
      // 1. Character for character.
      String ours = CsvWriterTest.write(format, records);
      CSVFormat commonsFormat = CommonsFormats.toCommons(format);
      StringBuilder theirs = new StringBuilder();
      try (CSVPrinter printer = new CSVPrinter(theirs, commonsFormat)) {
        for (List<Object> record : records) printer.printRecord(record);
      }
      assertEquals(theirs.toString(), ours);
      // 2. Commons reads our output as its own.
      assertEquals(
          Differential.commons(theirs.toString(), commonsFormat).records(),
          Differential.commons(ours, commonsFormat).records());
      // 3. We read Commons' output as Commons does (a header record reads as data here).
      d.check(theirs.toString(), format, commonsFormat, 1 + r.nextInt(ours.length() + 2));
      // 4. Lossless formats read back the written values.
      if (lossless(format, records)) {
        List<List<@Nullable String>> expected = new ArrayList<>();
        if (format.headerNames() != null) expected.add(new ArrayList<>(Arrays.asList(names)));
        for (List<Object> record : records) expected.add(text(record));
        Differential.Result back = Differential.ours(ours.getBytes(java.nio.charset.StandardCharsets.UTF_8), format);
        assertTrue(back.ok(), () -> String.valueOf(back.error()));
        String context = "format " + format + "\n" + Differential.escape(ours);
        assertEquals(expected, back.records(), context);
        lossless++;
      }
    }
    System.out.println("round trips " + cases + ", lossless " + lossless + ", reads " + d.counts.keySet());
    assertTrue(lossless > 500, "lossless cases " + lossless);
  }
}
