package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;
import static dev.elide.truffledata.bebop.BebopType.ref;
import static dev.elide.truffledata.bebop.Fixtures.general;
import static dev.elide.truffledata.bebop.Fixtures.hex;
import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.Accessor;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.ExactNumber;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Missing;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.Values;
import dev.elide.truffledata.bebop.BebopException.Kind;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Rows and arrays read in place, against the decoded value. */
final class BebopRowTest {
  private static final DataLibrary DATA = DataLibrary.factory().getUncached();
  private static final InteropLibrary INTEROP = InteropLibrary.getUncached();

  @Test
  void everyTypeReadsAsItDecodes() {
    Random r = new Random(0x0B0E);
    for (BebopCodec codec : new BebopCodec[] {general(ref("Everything")), Fixtures.strict(ref("Everything"))}) {
      for (int i = 0; i < 1500; i++) {
        byte[] bytes = codec.encode(Gen.value(r, ref("Everything"), 0));
        BebopDocument doc = codec.adopt(bytes);
        Gen.same(ref("Everything"), codec.decode(bytes), doc.root(), "root");
      }
    }
  }

  @Test
  void rootsOfEveryShape() {
    BebopCodec ints = general(BebopType.array(INT32));
    BebopArray array = (BebopArray) ints.open(ints.encode(List.of(7, -8, 9))).root();
    assertEquals(3, array.size());
    assertEquals(-8, array.getLong(1));
    assertFalse(array.isBytes());
    assertThrows(ContractViolation.class, () -> array.copyBytesTo(new byte[3], 0));
    assertThrows(UnsupportedOperationException.class, () -> array.getDouble(0));
    assertThrows(IndexOutOfBoundsException.class, () -> array.get(3));
    assertThrows(IllegalStateException.class, () -> ints.open(ints.encode(List.of())).row());
    BebopCodec floats = general(BebopType.fixed(FLOAT32, 2));
    BebopArray pair = (BebopArray) floats.open(floats.encode(List.of(1.5f, -2.5f))).root();
    assertEquals(-2.5, pair.getDouble(1));
    assertThrows(UnsupportedOperationException.class, () -> pair.getLong(0));
    assertSame(floats, pair.document().codec());
    assertEquals(42L, general(INT32).open(hex("2a000000")).root());
    assertEquals("hi", general(STRING).open(hex("02000000 686900")).root());
    assertEquals(1L, general(ref("Status")).open(hex("01")).root());
    BebopRow point = general(ref("Point")).open(hex("0000803f 00000040")).row();
    assertEquals(1.0, point.get(0));
    assertEquals(2.0, point.get(1));
    assertEquals(8, point.document().size());
    // Only validated bytes become documents.
    assertEquals(
        Kind.MALFORMED,
        assertThrows(BebopException.class, () -> general(ref("Point")).open(hex("0000803f"))).kind());
  }

  @Test
  void interopAndDataLibrary() throws Exception {
    BebopCodec codec = general(ref("UserRequest"));
    BebopRow row = codec.open(hex("08000000 2a000000 01 04 03 10")).row();
    Schema schema = row.schema();
    assertEquals("UserRequest", schema.typeName());
    assertEquals(Field.Kind.INT64, schema.field(0).kind());
    assertTrue(schema.field(0).optional());
    assertEquals(Field.Kind.BOOLEAN, schema.field(1).kind());
    assertSame(schema, DATA.getSchema(row));
    assertEquals(42L, DATA.read(row, 0));
    assertEquals(42L, DATA.readLong(row, 0));
    assertTrue(DATA.readBoolean(row, 1));
    assertTrue(DATA.isPresent(row, 1));
    assertEquals(true, assertThrows(UnexpectedResultException.class, () -> DATA.readLong(row, 1)).getResult());
    assertEquals(42L, assertThrows(UnexpectedResultException.class, () -> DATA.readBoolean(row, 0)).getResult());
    assertEquals(42L, assertThrows(UnexpectedResultException.class, () -> DATA.readDouble(row, 0)).getResult());
    assertEquals(42L, INTEROP.readMember(row, "user_id"));
    assertEquals(true, INTEROP.readArrayElement(row, 1));
    assertEquals(2, INTEROP.getArraySize(row));
    // Every row of one codec type shares a schema, so compiled reads specialize once.
    BebopRow absent = codec.open(hex("06000000 2a000000 01 10")).row();
    assertSame(schema, absent.schema());
    assertFalse(DATA.isPresent(absent, 1));
    assertSame(Missing.INSTANCE, DATA.read(absent, 1));
    assertSame(Missing.INSTANCE, assertThrows(UnexpectedResultException.class, () -> DATA.readBoolean(absent, 1)).getResult());
    assertFalse(INTEROP.isMemberReadable(absent, "include_profile"));
    assertThrows(UnknownIdentifierException.class, () -> INTEROP.readMember(absent, "include_profile"));
    assertThrows(InvalidArrayIndexException.class, () -> INTEROP.readArrayElement(absent, 1));
    BebopRow empty = codec.open(hex("01000000 00")).row();
    assertFalse(empty.present(0));
    assertSame(Missing.INSTANCE, assertThrows(UnexpectedResultException.class, () -> DATA.readLong(empty, 0)).getResult());

    BebopCodec table = general(ref("Table"));
    BebopRecord value =
        new BebopRecord(
            "Table",
            List.of(new BebopRecord("Row", 7L, 0.5, true), new BebopRecord("Row", 8L, -1.5, false)),
            List.of("x", "yy"),
            List.of(1L, 2L, 3L),
            hex("c8c9"),
            null);
    BebopRow root = table.open(table.encode(value)).row();
    Object rows = INTEROP.readMember(root, "rows");
    assertTrue(INTEROP.hasArrayElements(rows));
    assertEquals(2, INTEROP.getArraySize(rows));
    assertTrue(INTEROP.isArrayElementReadable(rows, 1));
    assertFalse(INTEROP.isArrayElementReadable(rows, 2));
    assertFalse(INTEROP.isArrayElementReadable(rows, -1));
    Object second = INTEROP.readArrayElement(rows, 1);
    assertEquals(8L, INTEROP.readMember(second, "id"));
    assertEquals(-1.5, DATA.readDouble(second, 1));
    assertEquals(7L, assertThrows(UnexpectedResultException.class, () -> DATA.readDouble(INTEROP.readArrayElement(rows, 0), 0)).getResult());
    assertFalse(DATA.readBoolean(second, 2));
    assertThrows(InvalidArrayIndexException.class, () -> INTEROP.readArrayElement(rows, 2));
    assertThrows(InvalidArrayIndexException.class, () -> INTEROP.readArrayElement(rows, -1));
    assertEquals("yy", INTEROP.readArrayElement(INTEROP.readMember(root, "names"), 1));
    BebopArray blob = (BebopArray) root.get(3);
    assertTrue(blob.isBytes());
    assertEquals(0xC9L, blob.get(1));
    // Rows and arrays are owned values: they pass the ownership-transfer checks of core containers.
    Values.requireInterop(root);
    Values.requireInterop(blob);
    Values.requireInterop(rows);
    for (int i = 0; i < schema.size(); i++) Values.validate(schema.field(i), row.get(i));
    for (int i = 0; i < root.schema().size(); i++) Values.validate(root.schema().field(i), root.get(i));
  }

  @Test
  void layoutBoundAccessors() throws Exception {
    BebopCodec codec = general(ref("Row"));
    BebopRow row = codec.open(codec.encode(new BebopRecord("Row", 9L, 2.25, true))).row();
    BebopRow other = codec.open(codec.encode(new BebopRecord("Row", 10L, -1.0, false))).row();
    Accessor id = row.accessor(0);
    Accessor score = row.accessor(1);
    Accessor flag = row.accessor(2);
    assertSame(id, other.accessor(0));
    assertNotSame(id, row.schema().accessor(0));
    assertTrue(id.accepts(row) && id.accepts(other));
    assertEquals(9L, id.readLong(row));
    assertEquals(10L, id.read(other));
    assertEquals(2.25, score.readDouble(row));
    assertTrue(flag.readBoolean(row));
    assertTrue(id.present(row));
    assertEquals(9L, assertThrows(UnexpectedResultException.class, () -> id.readDouble(row)).getResult());
    assertEquals(9L, assertThrows(UnexpectedResultException.class, () -> id.readBoolean(row)).getResult());
    assertEquals(2.25, assertThrows(UnexpectedResultException.class, () -> score.readLong(row)).getResult());
    // Another layout, even of an equal type, is rejected without being read.
    BebopRow foreign = general(ref("Point")).open(hex("0000803f 00000040")).row();
    assertFalse(id.accepts(foreign));
    assertFalse(id.accepts("row"));
    assertThrows(ContractViolation.class, () -> id.read(foreign));
    assertThrows(ContractViolation.class, () -> id.readLong("row"));
    assertThrows(ContractViolation.class, () -> id.present(foreign));
    BebopCodec again = general(ref("Row"));
    BebopRow twin = again.open(again.encode(new BebopRecord("Row", 9L, 2.25, true))).row();
    assertFalse(id.accepts(twin));
    // Lengths: strings in UTF-16 units, arrays in elements.
    BebopCodec table = general(ref("Table"));
    BebopRow root =
        table.open(table.encode(new BebopRecord("Table", null, List.of("a", "bcd"), List.of(1L, 2L), hex("0102"), null))).row();
    assertEquals(2, root.accessor(1).length(root));
    assertEquals(2, root.accessor(3).length(root));
    BebopCodec request = general(ref("Request"));
    BebopRow text = request.open(request.encode(new BebopRecord("Request", "z😀", null))).row();
    assertEquals(3, text.accessor(0).length(text));
  }

  @Test
  void unionsAndUnknownBranches() {
    BebopCodec shape = general(ref("Shape"));
    BebopRow circle = shape.open(hex("05000000 01 0000a040")).row();
    assertEquals(List.of("discriminator", "Circle", "Rectangle"), names(circle.schema()));
    assertFalse(circle.schema().field(0).optional());
    assertEquals(1L, circle.get(0));
    assertEquals(5.0, ((BebopRow) circle.get(1)).get(0));
    assertSame(Missing.INSTANCE, circle.get(2));
    BebopRow unknown = shape.open(hex("03000000 09 aabb")).row();
    assertEquals(9L, unknown.get(0));
    assertFalse(unknown.present(1) || unknown.present(2));
  }

  private static List<String> names(Schema schema) {
    List<String> out = new java.util.ArrayList<>();
    for (int i = 0; i < schema.size(); i++) out.add(schema.field(i).name());
    return out;
  }

  /** Every directory kind and boundary width, through the allocation-free field lookup. */
  @Test
  void fieldLookupMatchesTheIndex() {
    Random r = new Random(7);
    for (int round = 0; round < 400; round++) {
      int span = round % 3 == 0 ? 255 : round % 3 == 1 ? 32 : 9;
      int n = Math.min(span, 1 + r.nextInt(round % 4 == 0 ? 40 : 5));
      java.util.TreeSet<Integer> tags = new java.util.TreeSet<>();
      while (tags.size() < n) tags.add(1 + r.nextInt(span));
      BebopSchema.TaggedField[] fields = new BebopSchema.TaggedField[n];
      int f = 0;
      for (int tag : tags) fields[f++] = new BebopSchema.TaggedField(tag, "f" + tag, BebopType.array(BYTE));
      BebopSchema schema = BebopSchema.builder().message("M", fields).build();
      BebopCodec codec = BebopCodec.compile(schema, ref("M"), BebopProfile.GENERAL, BebopLimits.DEFAULT);
      Object[] values = new Object[n];
      int big = r.nextInt(3);
      for (int i = 0; i < n; i++)
        if (r.nextInt(4) != 0) values[i] = new byte[i == 0 && big > 0 ? (big == 1 ? 300 : 70_000) : r.nextInt(4)];
      BebopRecord record = new BebopRecord("M", values);
      BebopRow row = codec.adopt(codec.encode(record)).row();
      for (int i = 0; i < n; i++) {
        assertEquals(values[i] != null, row.present(i), "round " + round + " field " + i);
        if (values[i] != null) assertEquals(((byte[]) values[i]).length, ((BebopArray) row.get(i)).size());
      }
    }
  }
}
