package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.DataNull;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class CsvNullsTest {
  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static List<@Nullable String> row(@Nullable String... values) {
    return Arrays.asList(values);
  }

  /** Our generic records, asserted equal to Commons'. */
  private static List<List<@Nullable String>> both(String input, CsvFormat format) {
    Differential.Result theirs = Differential.commons(input, CommonsFormats.toCommons(format));
    Differential.Result ours = Differential.ours(utf8(input), format);
    assertTrue(theirs.ok(), () -> "Commons: " + theirs.error());
    assertTrue(ours.ok(), () -> "ours: " + ours.error());
    assertEquals(theirs.records(), ours.records());
    return ours.records();
  }

  @Test
  void sqlNullTokens() {
    for (CsvFormat.Predefined p :
        List.of(CsvFormat.Predefined.MYSQL, CsvFormat.Predefined.POSTGRESQL_TEXT)) {
      List<List<@Nullable String>> rows = both("a\t\\N\tb\t\\\\N\t\n", p.format());
      // An escaped backslash also decodes to the token: Commons compares decoded values.
      assertEquals(List.of(row("a", null, "b", null, "")), rows, p.name());
    }
  }

  @Test
  void quotedAndUnquotedFollowTheQuoteMode() {
    // POSTGRESQL_CSV: null string "", ALL_NON_NULL: an unquoted empty field is null, "" is empty.
    assertEquals(
        List.of(row("a", null, "")), both("a,,\"\"\n", CsvFormat.Predefined.POSTGRESQL_CSV.format()));
    // ORACLE: \N, MINIMAL: quoted or not, the token is null.
    assertEquals(
        List.of(row(null, null, "x")), both("\"\\N\",\\N,x\n", CsvFormat.Predefined.ORACLE.format()));
    // A strict quote mode keeps quoted tokens as text.
    CsvFormat strictMode =
        CsvFormat.Predefined.DEFAULT.format().toBuilder()
            .setNullString("NA")
            .setQuoteMode(CsvFormat.QuoteMode.NON_NUMERIC)
            .build();
    assertEquals(List.of(row(null, "NA", "na")), both("NA,\"NA\",na\n", strictMode));
    // A strict quote mode without a null string: unquoted empty only.
    CsvFormat emptyOnly =
        CsvFormat.Predefined.DEFAULT.format().toBuilder()
            .setQuoteMode(CsvFormat.QuoteMode.ALL_NON_NULL)
            .build();
    assertEquals(List.of(row(null, "", " ")), both(",\"\", \n", emptyOnly));
    // Trimming happens before the rule.
    CsvFormat trimmed = CsvFormat.Predefined.ORACLE.format();
    assertEquals(List.of(row(null, "a")), both("  \\N  , a\n", trimmed));
  }

  @Test
  void severalNullStringsInAStrictFormat() {
    CsvFormat strict = CsvFormat.RFC4180.withNullStrings("NA", "");
    CsvBatch batch = Csv.parse(utf8("a,b,c,d\nNA,,\"NA\",x\n"), strict);
    assertTrue(batch.schema().field(0).nullable());
    assertTrue(batch.row(0).isNull(0));
    assertTrue(batch.row(0).isNull(1));
    assertTrue(batch.row(0).isNull(2)); // no strict quote mode: quoted tokens are null too
    assertEquals("x", batch.row(0).getString(3));
    assertSame(DataNull.INSTANCE, DataLibrary.factory().getUncached().read(batch.row(0), 0));
    CsvBatch quotedKept =
        Csv.parse(
            utf8("a,b\nNA,\"NA\"\n"), strict.withQuoteMode(CsvFormat.QuoteMode.ALL_NON_NULL));
    assertTrue(quotedKept.row(0).isNull(0));
    assertEquals("NA", quotedKept.row(0).getString(1));
    ListSink sink = new ListSink();
    batch.export(sink);
    assertEquals(List.of(0L, DataNull.INSTANCE, DataNull.INSTANCE, DataNull.INSTANCE, "x"), sink.rows.get(0));
  }

  @Test
  void typedNullTokens() {
    Schema schema =
        Schema.of(
            new Field("n", Field.Kind.INT64, true, false),
            new Field("s", Field.Kind.STRING, true, false),
            Field.requiredLong("r"));
    CsvFormat mysql = CsvFormat.Predefined.MYSQL.format().toBuilder().setHeader().build();
    CsvPlan plan = CsvPlan.compile(schema, mysql);
    CsvBatch batch = plan.parse(utf8("n\ts\tr\n\\N\t\\N\t1\n7\tx\t2\n"));
    assertTrue(batch.row(0).isNull(0));
    assertTrue(batch.row(0).isNull(1));
    assertEquals(7L, batch.row(1).getLong(0));
    ContractViolation e =
        assertThrows(ContractViolation.class, () -> plan.parse(utf8("n\ts\tr\n1\tx\t\\N\n")));
    assertEquals("CSV record 2: Field 'r': null token in a non-nullable field", e.getMessage());
    Schema required = Schema.of(new Field("s", Field.Kind.STRING, false, false));
    ContractViolation text =
        assertThrows(
            ContractViolation.class,
            () -> CsvPlan.compile(required, mysql).parse(utf8("s\nSECRET\n\\N\n")));
    assertEquals("CSV record 3: Field 's': null token in a non-nullable field", text.getMessage());
    assertFalse(text.getMessage().contains("\\N"));
  }

  @Test
  void unconfiguredStrictFormatsKeepTheOriginalRule() {
    Schema schema =
        Schema.of(
            new Field("n", Field.Kind.INT64, true, false),
            new Field("s", Field.Kind.STRING, false, false),
            new Field("t", Field.Kind.STRING, true, false));
    CsvBatch batch = CsvPlan.compile(schema, CsvFormat.RFC4180).parse(utf8("n,s,t\n,,\n"));
    assertTrue(batch.row(0).isNull(0));
    assertEquals("", batch.row(0).getString(1));
    assertTrue(batch.row(0).isNull(2));
    CsvBatch generic = Csv.parse(utf8("a,b\n,\"\"\n"), CsvFormat.RFC4180);
    assertFalse(generic.schema().field(0).nullable());
    assertEquals("", generic.row(0).getString(0));
    assertThrows(
        ContractViolation.class,
        () ->
            CsvPlan.compile(Schema.of(Field.requiredLong("n"), Field.requiredLong("m")), CsvFormat.RFC4180)
                .parse(utf8("n,m\n,1\n")));
    // Commons formats without null strings or a strict quote mode have no nulls at all.
    CsvBatch commons = Csv.parse(utf8("a,,b\n"), CsvFormat.Predefined.DEFAULT.format());
    assertFalse(commons.schema().field(1).nullable());
    assertEquals("", commons.row(0).getString(1));
  }

  @Test
  void headerNullNamesAreBlank() {
    CsvFormat pg = CsvFormat.Predefined.POSTGRESQL_CSV.format().toBuilder().setHeader().build();
    Differential.Result theirs = Differential.commons("a,,b\n", CommonsFormats.toCommons(pg));
    assertFalse(theirs.ok());
    assertThrows(IllegalArgumentException.class, () -> Csv.parse(utf8("a,,b\n"), pg));
    CsvFormat allowed = pg.toBuilder().setAllowMissingColumnNames(true).build();
    assertEquals(List.of(row("1", null, "3")), both("a,,b\n1,,3\n", allowed));
    assertEquals(List.of("a", "", "b"), Differential.names(Csv.parse(utf8("a,,b\n"), allowed)));
  }
}
