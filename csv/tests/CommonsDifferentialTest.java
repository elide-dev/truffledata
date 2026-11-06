package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;

/**
 * Differential suite: seeded generated and adversarial inputs through Commons CSV 1.14.1 and through
 * our parser (whole and streamed), for every predefined format and for random option combinations
 * over them. Asserts identical records, nulls and errors, or a documented difference.
 */
final class CommonsDifferentialTest {
  private static final int INPUTS_PER_FORMAT = 3_000;
  private static final int COMBINATIONS = 600;
  private static final int INPUTS_PER_COMBINATION = 60;
  private static final String[] DELIMITERS = {",", ";", "\t", "|", "||", "::"};
  private static final String[] NAMES = {"a", "b", "A", "", " ", "c"};

  /** Formats under test; Task 12 lifts this to keep the format's null strings and quote mode. */
  static CsvFormat.Builder withoutNulls(CsvFormat.Builder b) {
    return b.setNullString(null).setQuoteMode(null);
  }

  private static CsvFormat.Builder header(Random r, CsvFormat.Builder b) {
    switch (r.nextInt(4)) {
      case 0 -> b.setHeader(false);
      case 1 -> b.setHeader(true);
      default -> {
        String[] names = new String[1 + r.nextInt(4)];
        for (int i = 0; i < names.length; i++) names[i] = NAMES[r.nextInt(NAMES.length)];
        b.setHeader(names).setSkipHeaderRecord(r.nextBoolean());
      }
    }
    if (r.nextInt(3) == 0) b.setAllowMissingColumnNames(r.nextBoolean());
    if (r.nextInt(3) == 0)
      b.setDuplicateHeaderMode(CsvFormat.DuplicateHeaderMode.values()[r.nextInt(3)]);
    if (r.nextInt(4) == 0) b.setIgnoreHeaderCase(true);
    if (r.nextInt(6) == 0) b.setMaxRows(1 + r.nextInt(3));
    return b;
  }

  private static CsvFormat.Builder options(Random r, CsvFormat.Builder b) {
    if (r.nextInt(3) == 0) b.setIgnoreSurroundingSpaces(r.nextBoolean());
    if (r.nextInt(3) == 0) b.setTrim(r.nextBoolean());
    if (r.nextInt(3) == 0) b.setIgnoreEmptyLines(r.nextBoolean());
    if (r.nextInt(4) == 0) b.setTrailingDelimiter(r.nextBoolean());
    if (r.nextInt(4) == 0) b.setTrailingData(r.nextBoolean());
    if (r.nextInt(4) == 0) b.setLenientEof(r.nextBoolean());
    if (r.nextInt(4) == 0) b.setCommentMarker(r.nextBoolean() ? Character.valueOf('#') : null);
    if (r.nextInt(4) == 0) b.setEscape(r.nextBoolean() ? Character.valueOf('\\') : null);
    if (r.nextInt(5) == 0) b.setDelimiter(DELIMITERS[r.nextInt(DELIMITERS.length)]);
    if (r.nextInt(6) == 0) b.setQuote(r.nextBoolean() ? Character.valueOf('\'') : null);
    return b;
  }

  private static void run(Differential d, Random r, CsvFormat format, int inputs) {
    CSVFormat commons = CommonsFormats.toCommons(format);
    CsvFuzz fuzz = new CsvFuzz(r, format);
    for (int i = 0; i < inputs; i++) {
      String text = fuzz.next();
      int bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      d.check(text, format, commons, 1 + r.nextInt(bytes + 2));
    }
  }

  private static void report(String name, Differential d) {
    System.out.println(name + " " + summary(d));
  }

  private static Map<String, Integer> summary(Differential d) {
    Map<String, Integer> out = new TreeMap<>();
    d.counts.forEach((k, v) -> out.put(k, v[0]));
    return out;
  }

  @Test
  void predefinedFormats() {
    for (CsvFormat.Predefined p : CsvFormat.Predefined.values()) {
      Differential d = new Differential();
      Random r = new Random(p.ordinal() * 7919L + 11);
      CsvFormat plain = withoutNulls(p.format().toBuilder()).build();
      run(d, r, plain, INPUTS_PER_FORMAT);
      CsvFormat named = withoutNulls(p.format().toBuilder().setHeader(true)).build();
      run(d, r, named, INPUTS_PER_FORMAT / 3);
      report(p.name(), d);
      assertTrue(d.count("equal", 0) > INPUTS_PER_FORMAT / 4, () -> p + " " + summary(d));
    }
  }

  @Test
  void optionCombinations() {
    Random r = new Random(20261106);
    Differential d = new Differential();
    int formats = 0;
    for (int i = 0; i < COMBINATIONS; i++) {
      CsvFormat.Predefined p = CsvFormat.Predefined.values()[r.nextInt(CsvFormat.Predefined.values().length)];
      CsvFormat format;
      try {
        format = withoutNulls(header(r, options(r, p.format().toBuilder()))).build();
      } catch (IllegalArgumentException invalid) {
        d.count("invalid-format");
        continue;
      }
      formats++;
      run(d, r, format, INPUTS_PER_COMBINATION);
    }
    report("combinations(" + formats + ")", d);
    assertTrue(formats > COMBINATIONS / 2);
    assertTrue(d.count("equal", 0) > formats * INPUTS_PER_COMBINATION / 6, () -> summary(d).toString());
  }
}
