package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.Constraints;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.DataNull;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Missing;
import dev.elide.truffledata.ReadBooleanNodeGen;
import dev.elide.truffledata.ReadDoubleNodeGen;
import dev.elide.truffledata.ReadLongNodeGen;
import dev.elide.truffledata.Schema;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class CsvPlanTest {
  private static final DataLibrary DATA = DataLibrary.factory().getUncached();

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static final Field PAD = new Field("pad", Field.Kind.STRING, false, false);

  /**
   * Parses one record {@code token,p} under the header {@code v,pad} and returns the token's value.
   * The pad column keeps an empty token from forming an (ignored) empty line.
   */
  private static Object one(Field field, String token) {
    return one(field, token, CsvFormat.RFC4180);
  }

  private static Object one(Field field, String token, CsvFormat format) {
    CsvPlan plan = CsvPlan.compile(Schema.of(field, PAD), format);
    CsvBatch batch = plan.parse(utf8(field.name() + ",pad\n" + token + ",p\n"));
    assertEquals(1, batch.size(), token);
    assertEquals("p", batch.row(0).getString(1));
    return DATA.read(batch.row(0), 0);
  }

  private static void rejects(Field field, String token, String reason) {
    var e = assertThrows(ContractViolation.class, () -> one(field, token), token);
    assertEquals("CSV record 2: Field '" + field.name() + "': " + reason, e.getMessage(), token);
  }

  private static void sameDouble(double expected, Object actual) {
    assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits((Double) actual));
  }

  @Test
  void conversionBoundaries() {
    Field i64 = Field.requiredLong("v");
    assertEquals(0L, one(i64, "0"));
    assertEquals(0L, one(i64, "-0"));
    assertEquals(7L, one(i64, "007"));
    assertEquals(-12L, one(i64, "-12"));
    assertEquals(Long.MAX_VALUE, one(i64, "9223372036854775807"));
    assertEquals(Long.MIN_VALUE, one(i64, "-9223372036854775808"));
    assertEquals(42L, one(i64, "\"42\""));
    assertEquals(5L, one(i64, "00000000000000000000000000005"));
    for (String bad :
        List.of(
            "9223372036854775808", "-9223372036854775809", "99999999999999999999", "+1", " 42",
            "42 ", "1.0", "-", "1e3", "0x1", "١", "\"\"", "\"4\"\"2\"")) rejects(i64, bad, "invalid INT64");
    rejects(i64, "", "null");
    assertEquals(42L, one(i64, " 42 ", CsvFormat.RFC4180.withTrim(true)));
    Field nullableLong = new Field("v", Field.Kind.INT64, true, false);
    assertSame(DataNull.INSTANCE, one(nullableLong, ""));
    rejects(nullableLong, "\"\"", "invalid INT64");
    Field int8 = Field.integer("v", 8, true, false, false);
    assertEquals(127L, one(int8, "127"));
    rejects(int8, "128", "integer width");
    Field ranged =
        Field.requiredLong("v")
            .withConstraints(Constraints.NONE.withIntegerRange(new Constraints.IntegerRange(1, 9)));
    assertEquals(9L, one(ranged, "9"));
    rejects(ranged, "0", "integer range");

    Field f64 = new Field("v", Field.Kind.FLOAT64, false, false);
    sameDouble(1.0, one(f64, "1."));
    sameDouble(0.5, one(f64, ".5"));
    sameDouble(-0.0, one(f64, "-0"));
    sameDouble(-0.0, one(f64, "-0.000e5"));
    sameDouble(0.0, one(f64, "0"));
    sameDouble(1.5, one(f64, "+1.5"));
    sameDouble(1e10, one(f64, "1e10"));
    sameDouble(1e-5, one(f64, "1E-5"));
    sameDouble(0.1, one(f64, "0.1"));
    sameDouble(Double.MAX_VALUE, one(f64, "1.7976931348623157e308"));
    sameDouble(Double.MIN_VALUE, one(f64, "4.9e-324"));
    sameDouble(0.0, one(f64, "1e-400"));
    sameDouble(12.0, one(f64, "\"12\""));
    for (String bad :
        List.of(
            "1e400", "-1e400", "1.8e308", "NaN", "Infinity", "inf", ".", "e5", "1e", "1e+", "1.5.",
            " 1", "0x1p3", "1d", "1f", "--1", "1_0", "\"\""))
      rejects(f64, bad, "invalid FLOAT64");
    rejects(f64, "", "null");
    Field f32 = f64.withConstraints(Constraints.NONE.withFloatBits(32));
    sameDouble(0.5, one(f32, "0.5"));
    sameDouble(16777216.0, one(f32, "16777216"));
    rejects(f32, "16777217", "floating width");
    rejects(f32, "0.1", "floating width");

    Field bool = new Field("v", Field.Kind.BOOLEAN, false, false);
    assertEquals(true, one(bool, "true"));
    assertEquals(true, one(bool, "TRUE"));
    assertEquals(true, one(bool, "tRuE"));
    assertEquals(false, one(bool, "False"));
    assertEquals(true, one(bool, "\"true\""));
    for (String bad : List.of("yes", "1", "t", "truee", "fals", " true", "\"\""))
      rejects(bool, bad, "invalid BOOLEAN");
    rejects(bool, "", "null");

    Field text = new Field("v", Field.Kind.STRING, false, false);
    assertEquals("", one(text, ""));
    assertEquals("", one(text, "\"\""));
    Field nullableText = new Field("v", Field.Kind.STRING, true, false);
    assertSame(DataNull.INSTANCE, one(nullableText, ""));
    assertEquals("", one(nullableText, "\"\""));
    assertEquals(" ", one(nullableText, " "));
    assertSame(DataNull.INSTANCE, one(nullableText, " ", CsvFormat.RFC4180.withTrim(true)));
  }

  @Test
  void fastPathMatchesParseDouble() {
    Random random = new Random(0xd0b1e);
    List<String> tokens = new ArrayList<>(List.of(
        "2.2250738585072011e-308", "2.2250738585072012e-308", "2.2250738585072014e-308",
        "4.9e-324", "2.4703282292062327e-324", "2.4703282292062328e-324", "1.7976931348623157e308",
        "1.7976931348623158e308", "9007199254740992", "9007199254740993", "9007199254740995",
        "123456789012345678", "1234567890123456789", "12345678901234567890", "0.1", "0.3",
        "1e22", "1e23", "9007199254740993e-22", "9007199254740992e22", "4.35e-322", "1e-323",
        "0.000000000000000000000000000000000000000000001", "00000000000000000000001.5"));
    for (int i = 0; i < 100_000; i++) {
      StringBuilder token = new StringBuilder();
      if (random.nextInt(4) == 0) token.append(random.nextBoolean() ? '-' : '+');
      int digits = random.nextInt(4) == 0 ? 1 + random.nextInt(14) : 15 + random.nextInt(6);
      int point = random.nextInt(digits + 2) - 1; // -1: no point
      for (int d = 0; d < digits; d++) {
        if (d == point) token.append('.');
        token.append((char) ('0' + random.nextInt(10)));
      }
      if (random.nextInt(3) > 0) {
        int exponent =
            random.nextInt(5) == 0 ? -310 - random.nextInt(20) : random.nextInt(701) - 350;
        token.append(random.nextBoolean() ? 'e' : 'E').append(exponent);
      }
      tokens.add(token.toString());
    }
    int finite = 0;
    for (String token : tokens) {
      double expected = Double.parseDouble(token);
      byte[] bytes = utf8("x" + token + "y");
      if (Double.isInfinite(expected)) {
        assertThrows(NumberFormatException.class, () -> Numbers.parseDouble(bytes, 1, token.length()), token);
        continue;
      }
      finite++;
      double actual = Numbers.parseDouble(bytes, 1, token.length());
      assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual), token);
    }
    assertTrue(finite > 90_000, "finite " + finite);
    // Short tokens with small exponents take the exact fast path: no fallback.
    long before = Numbers.fallbacks();
    for (String token : List.of("1.5", "-0.25", "123456789012345", "9007199254740992", "1e22", "3.14159e-7")) {
      Numbers.parseDouble(utf8(token), 0, token.length());
    }
    assertEquals(before, Numbers.fallbacks());
    for (long value : new long[] {0, 1, -1, Long.MAX_VALUE, Long.MIN_VALUE, 1234567890123L}) {
      String token = Long.toString(value);
      assertEquals(value, Numbers.parseLong(utf8(token), 0, token.length()));
    }
    assertThrows(NumberFormatException.class, () -> Numbers.parseLong(new byte[0], 0, 0));
  }

  @Test
  void headerProjectionAndOptional() throws IOException {
    Schema schema =
        Schema.of(
            Field.requiredLong("id"),
            new Field("name", Field.Kind.STRING, false, false),
            new Field("score", Field.Kind.FLOAT64, true, true));
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    assertSame(schema, plan.schema());
    assertSame(CsvFormat.RFC4180, plan.format());
    CsvBatch batch = plan.parse(utf8("name,extra,id\nann,zzz,1\n\"b,b\",,2\n"));
    assertSame(schema, batch.schema());
    assertEquals(2, batch.size());
    CsvRow row = batch.row(1);
    assertEquals(2L, row.getLong(0));
    assertEquals("b,b", row.getString(1));
    assertFalse(DATA.isPresent(row, 2));
    assertSame(Missing.INSTANCE, DATA.read(row, 2));
    // Present when the header has it.
    CsvBatch scored = plan.parse(utf8("score,id,name\n1.5,1,a\n,2,b\n"));
    assertEquals(1.5, scored.row(0).getDouble(2));
    assertTrue(scored.row(1).isNull(2));
    assertSame(DataNull.INSTANCE, DATA.read(scored.row(1), 2));
    // A required field absent from the header fails at the header.
    var absent = assertThrows(ContractViolation.class, () -> plan.parse(utf8("name\na\n")));
    assertEquals("CSV header: Field 'id': missing", absent.getMessage());
    var twice = assertThrows(ContractViolation.class, () -> plan.parse(utf8("id,name,id\n1,a,1\n")));
    assertEquals("CSV header: Field 'id': matched by two columns", twice.getMessage());
    // Ragged rows: rejected, or with flexible, absent fields must be optional.
    var ragged = assertThrows(IllegalArgumentException.class, () -> plan.parse(utf8("id,name,score\n1,a\n")));
    assertTrue(ragged.getMessage().startsWith("CSV syntax at record 2, byte 14:"), ragged.getMessage());
    CsvPlan flexible = CsvPlan.compile(schema, CsvFormat.RFC4180.withFlexible(true));
    CsvBatch shortRow = flexible.parse(utf8("id,name,score\n1,a\n"));
    assertSame(Missing.INSTANCE, DATA.read(shortRow.row(0), 2));
    var required = assertThrows(ContractViolation.class, () -> flexible.parse(utf8("id,name,score\n1\n")));
    assertEquals("CSV record 2: Field 'name': missing", required.getMessage());
    // Without a header, columns map by position and the width must equal the schema.
    CsvPlan positional = CsvPlan.compile(schema, CsvFormat.RFC4180.withHeader(false));
    CsvBatch rows = positional.parse(utf8("7,x,2.5\n"));
    assertEquals(7L, rows.row(0).getLong(0));
    assertEquals(2.5, rows.row(0).getDouble(2));
    assertThrows(IllegalArgumentException.class, () -> positional.parse(utf8("7,x\n")));
    assertThrows(IllegalArgumentException.class, () -> positional.parse(utf8("7,x,1,2\n")));
    CsvPlan positionalFlexible = CsvPlan.compile(schema, CsvFormat.RFC4180.withHeader(false).withFlexible(true));
    assertSame(Missing.INSTANCE, DATA.read(positionalFlexible.parse(utf8("7,x\n")).row(0), 2));
    assertThrows(ContractViolation.class, () -> positionalFlexible.parse(utf8("7\n")));
    assertEquals(0, positional.parse(new byte[0]).size());
    // Typed plans stream with the plan's schema on every batch.
    StringBuilder text = new StringBuilder("id,name,score\n");
    for (int i = 0; i < 1000; i++) text.append(i).append(",n").append(i).append(',').append(i).append(".5\n");
    try (CsvReader reader = plan.open(new ChunkedInput(utf8(text.toString()), 3), CsvLimits.DEFAULT, 128)) {
      assertSame(schema, reader.schema());
      long sum = 0;
      int count = 0;
      for (CsvBatch next; (next = reader.next()) != null; ) {
        assertSame(schema, next.schema());
        for (int r = 0; r < next.size(); r++) {
          sum += next.row(r).getLong(0);
          assertEquals(next.row(r).getLong(0) + 0.5, next.row(r).getDouble(2));
          count++;
        }
      }
      assertEquals(1000, count);
      assertEquals(499_500, sum);
    }
    try (CsvReader reader = plan.open(new ChunkedInput(utf8("id,name\n1,a\n"), 4))) {
      CsvBatch next = reader.next();
      assertNotNull(next);
      assertEquals(1L, next.row(0).getLong(0));
    }
  }

  @Test
  void diagnosticsOmitValues() {
    CsvPlan plan = CsvPlan.compile(Schema.of(new Field("id", Field.Kind.STRING, false, false), Field.requiredLong("amount")), CsvFormat.RFC4180);
    var e = assertThrows(ContractViolation.class, () -> plan.parse(utf8("id,amount\nok,1\nsecret,9secret123x\n")));
    assertTrue(e.getMessage().contains("record 3"), e.getMessage());
    assertTrue(e.getMessage().contains("'amount'"), e.getMessage());
    assertFalse(e.getMessage().contains("secret"), e.getMessage());
    assertFalse(e.getMessage().contains("9"), e.getMessage());
    CsvPlan bool = CsvPlan.compile(Schema.of(new Field("flag", Field.Kind.BOOLEAN, false, false)), CsvFormat.RFC4180);
    var b = assertThrows(ContractViolation.class, () -> bool.parse(utf8("flag\nhunter2\n")));
    assertFalse(b.getMessage().contains("hunter2"), b.getMessage());
    CsvBatch flags = bool.parse(utf8("flag\nTRUE\nfalse\n"));
    assertTrue(flags.row(0).getBoolean(0));
    assertFalse(flags.row(1).getBoolean(0));
  }

  /** Adopts a cached, dispatched DataLibrary so it can run outside a call. */
  private static final class Adopted extends RootNode {
    @Child DataLibrary data = DataLibrary.factory().createDispatched(3);

    Adopted() {
      super(null);
      adoptChildren();
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return 0;
    }
  }

  @Test
  void typedReadsThroughDataLibrary() throws UnexpectedResultException {
    Schema schema =
        Schema.of(
            Field.requiredLong("n"),
            new Field("x", Field.Kind.FLOAT64, false, false),
            new Field("b", Field.Kind.BOOLEAN, false, false),
            new Field("s", Field.Kind.STRING, false, false),
            new Field("maybe", Field.Kind.INT64, true, false));
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    CsvBatch batch = plan.parse(utf8("n,x,b,s,maybe\n1,2.5,true,s1,\n-3,-0.0,FALSE,s2,4\n"));
    CsvBatch other = plan.parse(utf8("n,x,b,s,maybe\n5,1e3,false,s3,6\n"));
    Adopted root = new Adopted();
    DataLibrary cached = root.data;
    long longs = 0;
    double doubles = 0;
    int trues = 0;
    for (CsvBatch each : List.of(batch, other)) {
      for (int r = 0; r < each.size(); r++) {
        CsvRow row = each.row(r);
        assertSame(schema, cached.getSchema(row));
        longs += cached.readLong(row, 0);
        doubles += cached.readDouble(row, 1);
        if (cached.readBoolean(row, 2)) trues++;
        assertEquals(DATA.readLong(row, 0), ReadLongNodeGen.getUncached().execute(row, 0));
        assertEquals(DATA.readDouble(row, 1), ReadDoubleNodeGen.getUncached().execute(row, 1));
        assertEquals(DATA.readBoolean(row, 2), ReadBooleanNodeGen.getUncached().execute(row, 2));
      }
    }
    assertEquals(3, longs);
    assertEquals(1002.5, doubles);
    assertEquals(1, trues);
    CsvRow row = batch.row(0);
    assertEquals(Long.valueOf(1), DATA.read(row, 0));
    assertEquals(Double.valueOf(2.5), DATA.read(row, 1));
    assertEquals(Boolean.TRUE, DATA.read(row, 2));
    assertEquals("s1", DATA.read(row, 3));
    assertTrue(row.isNull(4));
    assertEquals(4L, batch.row(1).getLong(4));
    assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(batch.row(1).getDouble(1)));
    // Wrong-type reads carry the value.
    var text = assertThrows(UnexpectedResultException.class, () -> cached.readLong(row, 3));
    assertEquals("s1", text.getResult());
    var number = assertThrows(UnexpectedResultException.class, () -> DATA.readDouble(row, 0));
    assertEquals(1L, number.getResult());
    var flag = assertThrows(UnexpectedResultException.class, () -> DATA.readLong(row, 2));
    assertEquals(Boolean.TRUE, flag.getResult());
    var nothing = assertThrows(UnexpectedResultException.class, () -> cached.readLong(row, 4));
    assertSame(DataNull.INSTANCE, nothing.getResult());
    var real = assertThrows(UnexpectedResultException.class, () -> DATA.readBoolean(row, 1));
    assertEquals(2.5, real.getResult());
    assertThrows(ContractViolation.class, () -> row.getLong(4));
    assertThrows(ContractViolation.class, () -> row.getString(0));
    assertThrows(ContractViolation.class, () -> ReadLongNodeGen.getUncached().execute(row, 4));
  }

  @Test
  void planRejectsUnsupportedFields() {
    List<Field> unsupported =
        List.of(
            Field.integer("u", 64, false, false, false),
            new Field("a", Field.Kind.ARRAY, false, false),
            new Field("r", Field.Kind.RECORD, false, false),
            new Field("any", Field.Kind.ANY, false, false),
            new Field("s", Field.Kind.STRING, false, false)
                .withConstraints(
                    Constraints.NONE.withLength(
                        new Constraints.Length(1, 4, Constraints.LengthUnit.UTF16))));
    for (Field field : unsupported) {
      var e = assertThrows(IllegalArgumentException.class, () -> CsvPlan.compile(Schema.of(field), CsvFormat.RFC4180), field.toString());
      assertTrue(e.getMessage().contains("'" + field.name() + "'"), e.getMessage());
    }
    Schema duplicate = Schema.of(Field.requiredLong("a"), Field.requiredLong("a"));
    assertThrows(IllegalArgumentException.class, () -> CsvPlan.compile(duplicate, CsvFormat.RFC4180));
    // Positional plans may repeat names: columns map by ordinal.
    CsvPlan positional = CsvPlan.compile(duplicate, CsvFormat.RFC4180.withHeader(false));
    assertEquals(2L, positional.parse(utf8("1,2\n")).row(0).getLong(1));
  }
}
