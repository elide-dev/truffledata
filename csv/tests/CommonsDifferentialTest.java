package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

  private static final String[] NULL_STRINGS = {"", "\\N", "NULL", "N", "a"};

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
    if (r.nextInt(3) == 0)
      b.setNullString(r.nextInt(4) == 0 ? null : NULL_STRINGS[r.nextInt(NULL_STRINGS.length)]);
    if (r.nextInt(3) == 0) {
      CsvFormat.QuoteMode[] modes = CsvFormat.QuoteMode.values();
      b.setQuoteMode(r.nextInt(5) == 0 ? null : modes[r.nextInt(modes.length)]);
    }
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
      CsvFormat plain = (p.format().toBuilder()).build();
      run(d, r, plain, INPUTS_PER_FORMAT);
      CsvFormat named = (p.format().toBuilder().setHeader(true)).build();
      run(d, r, named, INPUTS_PER_FORMAT / 3);
      report(p.name(), d);
      assertTrue(d.count("equal", 0) > INPUTS_PER_FORMAT / 4, () -> p + " " + summary(d));
    }
  }

  private static final String[] TYPED_TOKENS = {
    "", "\"\"", "42", "-7", "007", "+1", "x", "\"5\"", " 3", "9223372036854775808", "\\N", "NULL",
    "\"\\N\"", "N"
  };

  private static final String[] GOOD_INTS = {"42", "-7", "007", "0"};
  private static final String[] GOOD_TEXT = {"x", "y z", "\"q\"", "é"};

  private static boolean int64(String v) {
    if (!v.matches("-?[0-9]+")) return false;
    try {
      Long.parseLong(v);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Typed plans over Commons formats: nullable and non-nullable INT64 and STRING columns read exactly
   * Commons' values passed through our grammar, and fail (naming the record and field) exactly where
   * Commons yields null in a non-nullable field or a value outside the grammar.
   */
  @Test
  void typedPlans() {
    Schema schema =
        Schema.of(
            new Field("c0", Field.Kind.INT64, true, false),
            new Field("c1", Field.Kind.INT64, false, false),
            new Field("c2", Field.Kind.STRING, true, false),
            new Field("c3", Field.Kind.STRING, false, false));
    Random r = new Random(4242);
    Differential d = new Differential();
    for (CsvFormat.Predefined p : CsvFormat.Predefined.values()) {
      for (String nullString : new String[] {null, "NULL", ""}) {
        CsvFormat.Builder b = p.format().toBuilder();
        if (nullString != null) b.setNullString(nullString);
        CsvFormat format = b.build();
        CSVFormat commons = CommonsFormats.toCommons(format);
        CsvPlan plan = CsvPlan.compile(schema, format);
        for (int i = 0; i < 400; i++) {
          StringBuilder sb = new StringBuilder();
          int records = 1 + r.nextInt(4);
          for (int rec = 0; rec < records; rec++) {
            for (int f = 0; f < 4; f++) {
              if (f > 0) sb.append(format.delimiterString());
              if (r.nextInt(3) == 0) sb.append(TYPED_TOKENS[r.nextInt(TYPED_TOKENS.length)]);
              else sb.append(f < 2 ? GOOD_INTS[r.nextInt(GOOD_INTS.length)] : GOOD_TEXT[r.nextInt(GOOD_TEXT.length)]);
            }
            sb.append('\n');
          }
          String text = sb.toString();
          Differential.Result theirs = Differential.commons(text, commons);
          if (!theirs.ok() || theirs.records().stream().anyMatch(rec -> rec.size() != 4)) {
            d.count("skipped");
            continue;
          }
          String violation = null;
          outer:
          for (int rec = 0; rec < theirs.records().size(); rec++) {
            List<String> values = theirs.records().get(rec);
            for (int f = 0; f < 4; f++) {
              String v = values.get(f);
              String reason =
                  v == null
                      ? (schema.field(f).nullable() ? null : "null token in a non-nullable field")
                      : (f < 2 && !int64(v) ? "invalid INT64" : null);
              if (reason != null) {
                violation = "CSV record " + (rec + 1) + ": Field 'c" + f + "': " + reason;
                break outer;
              }
            }
          }
          String context = p + " null=" + nullString + " input " + Differential.escape(text);
          if (violation != null) {
            String expected = violation;
            ContractViolation e =
                assertThrows(ContractViolation.class, () -> plan.parse(text.getBytes(StandardCharsets.UTF_8)), context);
            assertEquals(expected, e.getMessage(), context);
            d.count("violation");
            continue;
          }
          CsvBatch batch = plan.parse(text.getBytes(StandardCharsets.UTF_8));
          assertSame(schema, batch.schema());
          assertEquals(theirs.records().size(), batch.size(), context);
          for (int rec = 0; rec < batch.size(); rec++) {
            CsvRow row = batch.row(rec);
            List<String> values = theirs.records().get(rec);
            for (int f = 0; f < 4; f++) {
              String v = values.get(f);
              if (v == null) assertTrue(row.isNull(f), context);
              else if (f < 2) assertEquals(Long.parseLong(v), row.getLong(f), context);
              else assertEquals(v, row.getString(f), context);
            }
          }
          d.count("equal");
        }
      }
    }
    report("typed", d);
    assertTrue(d.count("equal", 0) > 2_000, () -> summary(d).toString());
    assertTrue(d.count("violation", 0) > 500, () -> summary(d).toString());
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
        format = (header(r, options(r, p.format().toBuilder()))).build();
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
