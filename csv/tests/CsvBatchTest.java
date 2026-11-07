package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.DataNull;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Missing;
import dev.elide.truffledata.Schema;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CsvBatchTest {
  private static final DataLibrary DATA = DataLibrary.factory().getUncached();

  static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  static CsvBatch parse(String csv) {
    return Csv.parse(utf8(csv), CsvFormat.RFC4180);
  }

  static List<String> names(Schema schema) {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < schema.size(); i++) names.add(schema.field(i).name());
    return names;
  }

  @Test
  void headerBecomesSchema() {
    CsvBatch batch = parse("name,age\r\nann,3\r\n\r\nbob,4");
    Schema schema = batch.schema();
    assertEquals(List.of("name", "age"), names(schema));
    for (int i = 0; i < 2; i++) {
      Field field = schema.field(i);
      assertEquals(Field.Kind.STRING, field.kind());
      assertFalse(field.nullable());
      assertFalse(field.optional());
    }
    assertEquals(2, batch.size());
    assertEquals(0, batch.firstRecord());
    CsvRow first = batch.row(0);
    assertSame(batch, first.batch());
    assertSame(schema, first.schema());
    assertEquals(0, first.index());
    assertEquals(0, first.record());
    assertEquals("ann", first.getString(0));
    assertEquals("3", first.getString(1));
    assertEquals(1, batch.row(1).record());
    assertEquals("bob", batch.row(1).getString(0));
    assertEquals("4", batch.row(1).getString(1));
    assertSame(schema, DATA.getSchema(first));
    assertFalse(first.isNull(0));
    assertThrows(IndexOutOfBoundsException.class, () -> batch.row(2));
    assertThrows(IndexOutOfBoundsException.class, () -> batch.row(-1));
    // Generic columns are STRING: typed accessors reject them.
    assertThrows(ContractViolation.class, () -> first.getLong(1));
    assertThrows(ContractViolation.class, () -> first.getDouble(1));
    assertThrows(ContractViolation.class, () -> first.getBoolean(1));
    // An unquoted empty field in a generic (non-nullable STRING) column is an empty string.
    CsvRow empty = parse("a,b\n,x\n").row(0);
    assertEquals("", empty.getString(0));
    assertFalse(empty.isNull(0));
    // Duplicate header names stay readable by ordinal.
    CsvBatch duplicate = parse("a,a,b\n1,2,3\n");
    assertEquals(List.of("a", "a", "b"), names(duplicate.schema()));
    assertEquals("2", duplicate.row(0).getString(1));
    assertEquals(-1, duplicate.schema().indexOf("a"));
    // Header-only input has a schema and no rows.
    CsvBatch headerOnly = parse("x,y\n");
    assertEquals(List.of("x", "y"), names(headerOnly.schema()));
    assertEquals(0, headerOnly.size());
    var missing = assertThrows(IllegalArgumentException.class, () -> parse(""));
    assertEquals("CSV header missing", missing.getMessage());
    assertThrows(IllegalArgumentException.class, () -> parse("\n\r\n"));
  }

  @Test
  void positionalNamesWithoutHeader() {
    CsvFormat format = CsvFormat.RFC4180.withHeader(false);
    CsvBatch batch = Csv.parse(utf8("a,b,c\nd,e,f\n"), format);
    assertEquals(List.of("0", "1", "2"), names(batch.schema()));
    assertEquals(2, batch.size());
    assertEquals("a", batch.row(0).getString(0));
    assertEquals("f", batch.row(1).getString(2));
    CsvBatch empty = Csv.parse(new byte[0], format);
    assertEquals(0, empty.schema().size());
    assertEquals(0, empty.size());
    var wide = assertThrows(IllegalArgumentException.class, () -> Csv.parse(utf8("a,b\nc,d,e\n"), format));
    assertTrue(wide.getMessage().startsWith("CSV syntax at record 2, byte 4:"), wide.getMessage());
  }

  @Test
  void bomIsSkipped() {
    byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(bom);
    out.writeBytes(utf8("id,v\n1,\"x\"\n"));
    CsvBatch batch = Csv.parse(out.toByteArray(), CsvFormat.RFC4180);
    assertEquals(List.of("id", "v"), names(batch.schema()));
    assertEquals("1", batch.row(0).getString(0));
    assertEquals("x", batch.row(0).getString(1));
    // Only a leading BOM is skipped; elsewhere U+FEFF is content.
    CsvBatch inner = Csv.parse(utf8("a\n﻿b\n"), CsvFormat.RFC4180);
    assertEquals("﻿b", inner.row(0).getString(0));
    CsvBatch positional =
        Csv.parse(out.toByteArray(), CsvFormat.RFC4180.withHeader(false));
    assertEquals("id", positional.row(0).getString(0));
    // Byte offsets count the BOM.
    out.reset();
    out.writeBytes(bom);
    out.writeBytes(utf8("a\nb\"\n"));
    var e =
        assertThrows(
            IllegalArgumentException.class, () -> Csv.parse(out.toByteArray(), CsvFormat.RFC4180));
    assertTrue(e.getMessage().startsWith("CSV syntax at record 2, byte 6:"), e.getMessage());
  }

  @Test
  void raggedRejectedUnlessFlexible() {
    var shorter = assertThrows(IllegalArgumentException.class, () -> parse("a,b,tail\n1,2,3\n4,5\n"));
    assertTrue(shorter.getMessage().startsWith("CSV syntax at record 3, byte 15:"), shorter.getMessage());
    CsvFormat flexible = CsvFormat.RFC4180.withFlexible(true);
    CsvBatch batch = Csv.parse(utf8("a,b,tail\n1,2,3\n4\n"), flexible);
    for (int i = 0; i < 3; i++) assertTrue(batch.schema().field(i).optional());
    CsvRow full = batch.row(0);
    assertEquals("3", full.getString(2));
    assertTrue(DATA.isPresent(full, 2));
    CsvRow row = batch.row(1);
    assertEquals("4", row.getString(0));
    assertFalse(DATA.isPresent(row, 1));
    assertFalse(DATA.isPresent(row, 2));
    assertSame(Missing.INSTANCE, DATA.read(row, 1));
    assertSame(Missing.INSTANCE, DATA.read(row, 2));
    assertFalse(row.isNull(2));
    assertThrows(ContractViolation.class, () -> row.getString(2));
    var longer =
        assertThrows(
            IllegalArgumentException.class, () -> Csv.parse(utf8("a,b\n1,2\n1,2,3\n"), flexible));
    assertTrue(longer.getMessage().startsWith("CSV syntax at record 3, byte 8:"), longer.getMessage());
  }

  @Test
  void escapedFieldIsUnescapedOnEveryPath() {
    CsvBatch batch = parse("\"say \"\"hi\"\"\",q,p\n\"say \"\"hi\"\"\",\"plain quoted\",bare\n");
    assertEquals(List.of("say \"hi\"", "q", "p"), names(batch.schema()));
    CsvRow row = batch.row(0);
    assertEquals("say \"hi\"", row.getString(0));
    assertEquals("say \"hi\"", DATA.read(row, 0));
    assertEquals("plain quoted", row.getString(1));
    assertEquals("plain quoted", DATA.read(row, 1));
    assertEquals("bare", DATA.read(row, 2));
    // Unescaped quoted and plain fields reference the retained input; nothing was copied out.
    assertEquals(0, batch.copiedBytes());
    long quoted = batch.columns[1].spans[0];
    long bare = batch.columns[2].spans[0];
    assertTrue(CsvScanner.quoted(quoted));
    assertFalse(CsvScanner.escaped(quoted));
    assertFalse(CsvScanner.quoted(bare));
    assertEquals("plain quoted", new String(batch.input, CsvScanner.offset(quoted), CsvScanner.length(quoted), StandardCharsets.UTF_8));
    assertEquals("bare", new String(batch.input, CsvScanner.offset(bare), CsvScanner.length(bare), StandardCharsets.UTF_8));
    assertTrue(CsvScanner.escaped(batch.columns[0].spans[0]));
    // Unicode content round-trips.
    CsvRow text = parse("t\n\"中 \"\"😀\"\"\"\n").row(0);
    assertEquals("中 \"😀\"", text.getString(0));
  }

  @Test
  void invalidUtf8ReportsByteOffset() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(utf8("a,b\nx,"));
    out.write(0xC3);
    out.write(0x28);
    out.writeBytes(utf8("\n"));
    var data = assertThrows(IllegalArgumentException.class, () -> Csv.parse(out.toByteArray(), CsvFormat.RFC4180));
    assertEquals("CSV syntax at record 2, byte 6: invalid UTF-8", data.getMessage());
    byte[] header = {'a', (byte) 0xED, (byte) 0xA0, (byte) 0x80, '\n', 'b', '\n'};
    var head = assertThrows(IllegalArgumentException.class, () -> Csv.parse(header, CsvFormat.RFC4180));
    assertEquals("CSV syntax at record 1, byte 1: invalid UTF-8", head.getMessage());
    byte[] truncated = {'a', '\n', (byte) 0xE4, (byte) 0xB8};
    var tail = assertThrows(IllegalArgumentException.class, () -> Csv.parse(truncated, CsvFormat.RFC4180));
    assertEquals("CSV syntax at record 2, byte 2: invalid UTF-8", tail.getMessage());
  }

  @Test
  void limitsCheckedBeforeCopy() {
    CsvLimits limits = new CsvLimits(16, 16, 8, 2);
    var bytes = assertThrows(IllegalArgumentException.class, () -> Csv.parse(new byte[17], CsvFormat.RFC4180, limits));
    assertEquals("CSV resource limit: maxBatchBytes", bytes.getMessage());
    assertEquals(2, Csv.parse(utf8("h\n1\n2\n"), CsvFormat.RFC4180, limits).size());
    var rows = assertThrows(IllegalArgumentException.class, () -> Csv.parse(utf8("h\n1\n2\n3\n"), CsvFormat.RFC4180, limits));
    assertEquals("CSV resource limit: maxBatchRows", rows.getMessage());
    var columns =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                Csv.parse(
                    utf8("a,b,c,d,e,f,g,h,i\n"), CsvFormat.RFC4180, new CsvLimits(64, 64, 8, 2)));
    assertEquals("CSV resource limit: maxColumns", columns.getMessage());
    // The retained input is the copy, sized to the input.
    byte[] input = utf8("h\n1\n");
    CsvBatch batch = Csv.parse(input, CsvFormat.RFC4180);
    assertEquals(input.length, batch.inputBytes());
    assertNotSame(input, batch.input);
    input[2] = '9';
    assertEquals("1", batch.row(0).getString(0));
  }

  @Test
  void dataLibraryReadsNullAndMissingStates() {
    CsvRow row = Csv.parse(utf8("a,b\nx\n"), CsvFormat.RFC4180.withFlexible(true)).row(0);
    assertNotSame(DataNull.INSTANCE, DATA.read(row, 0));
    assertEquals("x", DATA.read(row, 0));
    assertSame(Missing.INSTANCE, DATA.read(row, 1));
  }
}
