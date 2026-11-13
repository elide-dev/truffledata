package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;
import static dev.elide.truffledata.bebop.BebopType.ref;
import static dev.elide.truffledata.bebop.Fixtures.general;
import static dev.elide.truffledata.bebop.Fixtures.hex;
import static dev.elide.truffledata.bebop.Fixtures.strict;
import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.Missing;
import dev.elide.truffledata.ReadBooleanNode;
import dev.elide.truffledata.ReadDoubleNode;
import dev.elide.truffledata.ReadLengthNode;
import dev.elide.truffledata.ReadLongNode;
import dev.elide.truffledata.bebop.BebopException.Kind;
import dev.elide.truffledata.bebop.BebopSchema.Branch;
import dev.elide.truffledata.bebop.BebopSchema.Member;
import dev.elide.truffledata.bebop.BebopSchema.TaggedField;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Host-value errors, schema errors, structural passes and the value domain's corners. */
final class BebopEdgeTest {
  private static void value(BebopType type, Object bad) {
    for (BebopCodec codec : new BebopCodec[] {general(type), strict(type)})
      assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> codec.encode(bad), type + " " + bad).kind());
  }

  private static void same(BebopType type, Object value, String hex) {
    assertEquals(hex.replace(" ", ""), Fixtures.hex(general(type).encode(value)), type.toString());
  }

  @Test
  void hostValuesOutsideTheDomain() {
    Object wrong = new Object();
    for (BebopType.Scalar kind : BebopType.Scalar.values()) value(kind, wrong);
    value(BOOL, 1);
    value(BYTE, 256);
    value(BYTE, -1);
    value(INT8, 128);
    value(INT16, 40000);
    value(UINT16, -1);
    value(INT32, 1L << 31);
    value(UINT32, 1L << 32);
    value(UINT32, -1);
    value(INT64, BigInteger.ONE.shiftLeft(63));
    value(UINT64, BigInteger.ONE.shiftLeft(64));
    value(UINT64, BigInteger.valueOf(-1));
    value(UINT64, -1);
    value(INT128, BigInteger.ONE.shiftLeft(127));
    value(UINT128, BigInteger.ONE.shiftLeft(128));
    value(UINT128, BigInteger.valueOf(-1));
    value(UINT128, -1);
    value(FLOAT32, 1.0);
    value(FLOAT64, 1.0f);
    // Any integer box of the right value is the same value.
    same(INT32, (short) -2, "feffffff");
    same(INT32, (byte) 5, "05000000");
    same(INT32, 7L, "07000000");
    same(INT32, BigInteger.valueOf(-1), "ffffffff");
    same(UINT64, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), "ffffffffffffffff");
    same(UINT64, 5, "0500000000000000");
    same(INT128, -1, "ffffffffffffffff ffffffffffffffff");
    same(UINT128, 1L, "0100000000000000 0000000000000000");
    value(BebopType.array(BYTE), List.of());
    value(BebopType.fixed(BYTE, 2), new byte[3]);
    value(BebopType.fixed(BYTE, 2), "no");
    value(BebopType.array(INT32), new int[0]);
    value(BebopType.fixed(INT32, 2), List.of(1));
    value(BebopType.fixed(INT32, 2), 5);
    value(BebopType.array(INT32), Arrays.asList(1, null));
    value(BebopType.set(INT32), Arrays.asList(1, null));
    value(BebopType.map(INT32, INT32), List.of());
    Map<Object, Object> nullValue = new HashMap<>();
    nullValue.put(1, null);
    value(BebopType.map(INT32, INT32), nullValue);
    Map<Object, Object> nullKey = new HashMap<>();
    nullKey.put(null, 1);
    value(BebopType.map(INT32, INT32), nullKey);
    value(ref("Point"), "no");
    value(ref("Point"), new BebopRecord("Circle", 1.0f));
    value(ref("Point"), new BebopRecord("Point", 1.0f));
    value(ref("Point"), new BebopRecord("Point", 1.0f, null));
    value(ref("UserRequest"), new BebopRecord("Point", 1.0f, 2.0f));
    value(ref("Shape"), "no");
    value(ref("Shape"), new BebopUnion(9, "not bytes"));
    value(ref("Status"), "no");
    assertThrows(IllegalArgumentException.class, () -> new BebopUnion(256, 1));
    assertThrows(IllegalArgumentException.class, () -> new BebopUnion(-1, 1));
  }

  @Test
  void schemasOutsideTheCodec() {
    assertThrows(IllegalArgumentException.class, () -> new BebopSchema.EnumDef("E", STRING, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new BebopSchema.EnumDef("E", INT128, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new BebopSchema.EnumDef("E", BOOL, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new TaggedField(0, "a", BOOL));
    assertThrows(IllegalArgumentException.class, () -> new TaggedField(256, "a", BOOL));
    assertThrows(IllegalArgumentException.class, () -> new Branch(-1, "a", BOOL));
    assertThrows(IllegalArgumentException.class, () -> new Branch(256, "a", BOOL));
    assertThrows(IllegalArgumentException.class, () -> BebopType.fixed(BOOL, 0));
    assertThrows(IllegalArgumentException.class, () -> BebopType.fixed(BOOL, 65536));
    assertThrows(IllegalArgumentException.class, () -> BebopSchema.builder().struct("S").struct("S"));
    for (int[] bad : new int[][] {{0, 1, 1, 1, 1}, {1, 0, 1, 1, 1}, {1, 1, 0, 1, 1}, {1, 1, 1, 0, 1}, {1, 1, 1, 1, 0}})
      assertThrows(IllegalArgumentException.class, () -> new BebopLimits(bad[0], bad[1], bad[2], bad[3], bad[4]));
    assertThrows(IllegalArgumentException.class, () -> BebopProfile.strict("x").canonicalNaN16(0x3E00));
    assertThrows(IllegalArgumentException.class, () -> BebopProfile.strict("x").canonicalNaNBrain16(0x17FC0));
    BebopSchema twice =
        BebopSchema.builder()
            .message("M", new TaggedField(1, "a", BOOL), new TaggedField(1, "b", BOOL))
            .union("U", new Branch(1, "a", BOOL), new Branch(1, "b", BOOL))
            .struct("K", new BebopSchema.Field("x", INT32))
            .enumeration("E", UINT64, new Member("TOP", -1L))
            .build();
    for (BebopType type :
        new BebopType[] {ref("M"), ref("U"), BebopType.map(ref("K"), BOOL), BebopType.map(BebopType.array(BYTE), BOOL)})
      assertEquals(
          Kind.UNSUPPORTED,
          assertThrows(
                  BebopException.class,
                  () -> BebopCodec.compile(twice, type, BebopProfile.GENERAL, BebopLimits.DEFAULT))
              .kind());
    // Every admissible key type.
    for (BebopType key : new BebopType[] {BOOL, STRING, UUID, UINT128, ref("Status")})
      assertNotNull(general(BebopType.map(key, BOOL)));
    BebopCodec codec = general(BebopType.set(BYTE));
    assertSame(Fixtures.SCHEMA, codec.schema());
    assertSame(BebopProfile.GENERAL, codec.profile());
    assertSame(BebopLimits.DEFAULT, codec.limits());
    assertEquals(BebopType.set(BYTE), codec.rootType());
    assertEquals(List.of(3, 1), codec.decode(codec.encode(List.of(3, 1))));
    assertEquals("02000000 0103".replace(" ", ""), Fixtures.hex(strict(BebopType.set(BYTE)).encode(List.of(3, 1, 3))));
    // uint64 enums compare as bits.
    BebopCodec top = BebopCodec.compile(twice, ref("E"), Fixtures.STRICT, BebopLimits.DEFAULT);
    assertEquals(-1L, top.decode(hex("ffffffffffffffff")));
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> top.decode(hex("feffffffffffffff"))).kind());
  }

  @Test
  void valueDomainEquality() {
    BebopRecord point = new BebopRecord("Point", 1.0f, 2.0f);
    assertEquals(point, new BebopRecord("Point", 1.0f, 2.0f));
    assertEquals(point.hashCode(), new BebopRecord("Point", 1.0f, 2.0f).hashCode());
    assertNotEquals(point, new BebopRecord("Point", 1.0f, 3.0f));
    assertNotEquals(point, new BebopRecord("Other", 1.0f, 2.0f));
    assertNotEquals(point, new BebopRecord("Point", 1.0f));
    assertNotEquals(point, "Point");
    assertEquals("Point[1.0, 2.0]", point.toString());
    assertEquals("Point", point.type());
    assertTrue(point.present(0));
    assertFalse(new BebopRecord("UserRequest", null, true).present(0));
    BebopUnion union = new BebopUnion(3, hex("0102"));
    assertEquals(union, new BebopUnion(3, hex("0102")));
    assertEquals(union.hashCode(), new BebopUnion(3, hex("0102")).hashCode());
    assertNotEquals(union, new BebopUnion(4, hex("0102")));
    assertNotEquals(union, new BebopUnion(3, hex("0103")));
    assertNotEquals(union, "union");
    assertTrue(BebopValues.equal(null, null));
    assertFalse(BebopValues.equal(null, 1));
    assertFalse(BebopValues.equal(1, null));
    assertFalse(BebopValues.equal(hex("01"), "01"));
    assertFalse(BebopValues.equal(List.of(1), "x"));
    assertFalse(BebopValues.equal(List.of(1), List.of(1, 2)));
    assertFalse(BebopValues.equal(List.of(1), List.of(2)));
    assertFalse(BebopValues.equal(Map.of(1, 2), "x"));
    assertFalse(BebopValues.equal(Map.of(1, 2), Map.of()));
    assertFalse(BebopValues.equal(Map.of(1, 2), Map.of(2, 2)));
    assertFalse(BebopValues.equal(Map.of(1, 2), Map.of(1, 3)));
    assertTrue(BebopValues.equal(Map.of(1, hex("05")), Map.of(1, hex("05"))));
    assertFalse(BebopValues.equal(point, "x"));
    assertFalse(Utf8.encodable("a\ud83d"));
    assertFalse(Utf8.encodable("\ud83dz"));
    assertFalse(Utf8.encodable("\ude00"));
    assertTrue(Utf8.encodable("😀"));
  }

  @Test
  void malformedCorners() {
    BebopCodec user = general(ref("UserRequest"));
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> user.decode(hex("01000000 14"))).kind());
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> user.decode(hex("01000000 04"))).kind());
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> user.decode(hex("01000000 1c"))).kind());
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> user.decode(hex("02000000 01 1c"))).kind());
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> user.decode(hex("08000000 a0 00 00000080 80 1c"))).kind());
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> user.decode(hex("03000000 01 01 10"))).kind());
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> general(ref("Shape")).decode(hex("01000000"))).kind());
    // Found by the fuzz campaign: a union too short for its length must fail for the same reason
    // in the decoder and the validator.
    for (String bytes : new String[] {"05003540", "0500", ""}) {
      BebopCodec shape = general(ref("Shape"));
      String decoded = assertThrows(BebopException.class, () -> shape.decode(hex(bytes))).getMessage();
      assertEquals(decoded, assertThrows(BebopException.class, () -> shape.validate(hex(bytes))).getMessage());
    }
    // Also found by the campaign: a duplicate key whose entry has a bad value fails on the value.
    for (BebopType key : new BebopType[] {INT32, BOOL}) {
      BebopCodec map = general(BebopType.map(key, STRING));
      String k = key == BOOL ? "01" : "01000000";
      byte[] bytes = hex("02000000 " + k + " 00000000 00 " + k + " 01000000 61 07");
      String decoded = assertThrows(BebopException.class, () -> map.decode(bytes)).getMessage();
      assertEquals("String terminator is not NUL", decoded);
      assertEquals(decoded, assertThrows(BebopException.class, () -> map.validate(bytes)).getMessage());
    }
    // Duplicate keys, seen by the decoder and by the validator alike.
    for (String[] c :
        new String[][] {
          {"bool", "02000000 01 05000000 02 06000000"},
          {"bool", "02000000 00 05000000 00 06000000"},
          {"int", "03000000 01000000 05000000 02000000 05000000 01000000 06000000"},
        }) {
      BebopCodec codec = general(BebopType.map(c[0].equals("bool") ? BOOL : INT32, INT32));
      assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> codec.decode(hex(c[1]))).kind());
      assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> codec.validate(hex(c[1]))).kind());
    }
    BebopCodec flags = general(BebopType.map(BOOL, INT32));
    flags.validate(hex("02000000 00 05000000 07 06000000"));
    // Work is bounded apart from counts and bytes.
    BebopLimits tight = new BebopLimits(1 << 20, 1 << 20, 8, 5, 1 << 20);
    BebopCodec work = BebopCodec.compile(Fixtures.SCHEMA, BebopType.array(INT16), BebopProfile.GENERAL, tight);
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> work.validate(hex("06000000 0100 0200 0300 0400 0500 0600"))).kind());
    BebopCodec fixedWork = BebopCodec.compile(Fixtures.SCHEMA, BebopType.fixed(INT16, 6), BebopProfile.GENERAL, tight);
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> fixedWork.validate(new byte[12])).kind());
    BebopCodec general = general(ref("UserRequest"));
    assertEquals(new BebopRecord("UserRequest", 42, null), general.decodeReference(hex("06000000 2a000000 01 10")));
    assertThrows(IndexOutOfBoundsException.class, () -> general.validate(new byte[4], 2, 3));
  }

  private static BebopRecord skippy(Random r) {
    return (BebopRecord) Gen.value(r, ref("Skippy"), 0);
  }

  @Test
  void structuralPasses() {
    Random r = new Random(11);
    BebopCodec codec = general(ref("Columns"));
    for (int round = 0; round < 300; round++) {
      BebopRecord inner = skippy(r);
      List<Object> i8s = List.of(-128, 127, -1);
      List<Object> i32s = List.of(Integer.MIN_VALUE, -1, 7);
      List<Object> f16s = List.of(1.5f, Float.NaN, -0.0f);
      List<Object> bf16s = List.of(-2.0f, Float.POSITIVE_INFINITY);
      List<Object> uuids = List.of(new java.util.UUID(1, 2));
      List<Object> u16s = List.of(65535, 0);
      List<Object> bools = List.of(true, false);
      List<Object> f32s = List.of(0.25f);
      List<Object> u64s = List.of(-1L);
      List<Object> blocks = List.of(Gen.value(r, ref("Block"), 0), Gen.value(r, ref("Block"), 0));
      BebopRecord value = new BebopRecord("Columns", i8s, i32s, f16s, bf16s, uuids, u16s, bools, f32s, u64s, inner, blocks);
      byte[] bytes = codec.encode(value);
      BebopDocument doc = codec.adopt(bytes);
      Gen.same(ref("Columns"), codec.decode(bytes), doc.root(), "root");
      BebopSpan a = java.util.Objects.requireNonNull(doc.span("i8s"));
      assertEquals(-128, a.getLong(0));
      assertEquals(Integer.MIN_VALUE, java.util.Objects.requireNonNull(doc.span("i32s")).getLong(0));
      BebopSpan halves = java.util.Objects.requireNonNull(doc.span("f16s"));
      assertEquals(1.5, halves.getDouble(0));
      assertTrue(Double.isNaN(halves.getDouble(1)));
      assertEquals(-2.0, java.util.Objects.requireNonNull(doc.span("bf16s")).getDouble(0));
      assertEquals(0.25, java.util.Objects.requireNonNull(doc.span("f32s")).getDouble(0));
      BebopSpan ids = java.util.Objects.requireNonNull(doc.span("uuids"));
      assertEquals(16, ids.width());
      assertThrows(UnsupportedOperationException.class, () -> ids.bits(0));
      byte[] id = new byte[16];
      ids.copyBytes(0, id, 0);
      assertEquals(1, id[7]);
      assertEquals(65535, java.util.Objects.requireNonNull(doc.span("u16s")).getLong(0));
      assertEquals(1, java.util.Objects.requireNonNull(doc.span("bools")).getLong(0));
      assertEquals(-1L, java.util.Objects.requireNonNull(doc.span("u64s")).getLong(0));
      assertThrows(UnsupportedOperationException.class, () -> a.getDouble(0));
      BebopSpan ns = java.util.Objects.requireNonNull(doc.span("blocks", "n"));
      assertEquals(2 + 16 + 12 + 8 + 2, ns.stride());
      assertEquals(((Integer) java.util.Objects.requireNonNull(((BebopRecord) blocks.get(1)).get(4))).longValue(), ns.getLong(1));
      // Columns inside a struct with variable-width fields before them.
      List<?> ints = (List<?>) inner.get(4);
      BebopSpan nested = java.util.Objects.requireNonNull(doc.span("skippy", "ints"));
      assertEquals(ints.size(), nested.count());
      for (int i = 0; i < ints.size(); i++) assertEquals(((Integer) ints.get(i)).longValue(), nested.getLong(i));
      BebopSpan ys = java.util.Objects.requireNonNull(doc.span("skippy", "pair", "y"));
      assertEquals(2, ys.count());
      assertEquals(8, ys.stride());
      assertEquals(((Float) ((BebopRecord) ((List<?>) inner.get(6)).get(1)).get(1)).doubleValue(), ys.getDouble(1));
      assertEquals(2, java.util.Objects.requireNonNull(doc.span("skippy", "digest")).count());
      assertEquals(((byte[]) inner.get(2)).length, java.util.Objects.requireNonNull(doc.span("skippy", "blob")).count());
      BebopSpan levels = java.util.Objects.requireNonNull(doc.span("skippy", "levels"));
      assertEquals(INT64, levels.kind());
      BebopOffsets labels = java.util.Objects.requireNonNull(doc.strings("skippy", "labels"));
      assertEquals(2, labels.count());
      assertEquals(((List<?>) inner.get(14)).get(1), labels.string(1));
      assertThrows(IllegalArgumentException.class, () -> doc.span("skippy", "blob", "x"));
      assertThrows(IllegalArgumentException.class, () -> doc.span("skippy", "ints", "x"));
      assertThrows(IllegalArgumentException.class, () -> doc.span("skippy", "pair"));
      assertThrows(IllegalArgumentException.class, () -> doc.span("skippy", "names"));
      assertThrows(IllegalArgumentException.class, () -> doc.span("skippy", "nope"));
      assertThrows(IllegalArgumentException.class, () -> doc.span("skippy", "last"));
      assertThrows(IllegalArgumentException.class, () -> doc.span("skippy", "shape", "x"));
      assertThrows(IllegalArgumentException.class, () -> doc.strings("skippy", "labels", "x"));
      assertThrows(IllegalArgumentException.class, () -> doc.strings("skippy", "pair"));
      assertThrows(IllegalArgumentException.class, () -> doc.strings("skippy", "blob"));
    }
  }

  /** Read nodes in the interpreter: the layout-bound accessor path and the cached library path. */
  @Test
  void readNodes() {
    BebopCodec codec = general(ref("UserRequest"));
    BebopRow row = codec.open(hex("08000000 2a000000 01 04 03 10")).row();
    BebopRow absent = codec.open(hex("01000000 00")).row();
    BebopCodec rows = general(ref("Row"));
    BebopRow struct = rows.open(rows.encode(new BebopRecord("Row", 9L, 2.25, true))).row();
    BebopCodec table = general(ref("Table"));
    BebopRow lists =
        table.open(table.encode(new BebopRecord("Table", null, List.of("a", "bcd"), List.of(1L), null, null))).row();
    var root =
        new RootNode(null) {
          @Child ReadLongNode longs = ReadLongNode.create();
          @Child ReadDoubleNode doubles = ReadDoubleNode.create();
          @Child ReadBooleanNode booleans = ReadBooleanNode.create();
          @Child ReadLengthNode lengths = ReadLengthNode.create();
          @Child DataLibrary data = DataLibrary.factory().createDispatched(3);

          @Override
          public Object execute(VirtualFrame frame) {
            List<Object> out = new ArrayList<>();
            try {
              for (int round = 0; round < 3; round++) {
                out.add(longs.execute(row, 0));
                out.add(booleans.execute(row, 1));
                out.add(longs.execute(struct, 0));
                out.add(doubles.execute(struct, 1));
                out.add(booleans.execute(struct, 2));
                out.add(lengths.execute(lists, 1));
                out.add(data.readLong(row, 0));
                out.add(data.readBoolean(row, 1));
                out.add(data.readDouble(struct, 1));
                out.add(data.readLong(struct, 0));
                out.add(data.readBoolean(struct, 2));
                out.add(data.isPresent(absent, 0));
                out.add(data.read(absent, 1));
              }
              for (int field = 0; field < 2; field++) {
                int f = field;
                assertSame(Missing.INSTANCE, assertThrows(UnexpectedResultException.class, () -> data.readLong(absent, f)).getResult());
                assertSame(Missing.INSTANCE, assertThrows(UnexpectedResultException.class, () -> data.readBoolean(absent, f)).getResult());
              }
              assertEquals(9L, assertThrows(UnexpectedResultException.class, () -> data.readDouble(struct, 0)).getResult());
              assertEquals(2.25, assertThrows(UnexpectedResultException.class, () -> data.readLong(struct, 1)).getResult());
              assertEquals(2.25, assertThrows(UnexpectedResultException.class, () -> data.readBoolean(struct, 1)).getResult());
            } catch (UnexpectedResultException e) {
              throw new AssertionError(e);
            }
            return out;
          }
        };
    // A status outside the reason table means the native side refused its schema program.
    assertThrows(IllegalStateException.class, () -> BebopValidator.report(255));
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> BebopValidator.report(37)).kind());
    assertThrows(IllegalStateException.class, () -> BebopValidator.report(-1));
    BebopValidator.report(0);
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> BebopValidator.report(24)).kind());
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> BebopValidator.report(23)).kind());
    assertEquals("java", BebopValidator.JAVA.name());
    assertEquals(42L, codec.open(hex("06000000 2a000000 01 10"), BebopValidator.JAVA).row().get(0));
    assertTrue(BebopValidator.program(codec).length > 1000);
    // Typed read nodes on a field of another type fall back to the generic value.
    var mismatched =
        new RootNode(null) {
          @Child ReadLongNode longs = ReadLongNode.create();
          @Child ReadDoubleNode doubles = ReadDoubleNode.create();
          @Child ReadBooleanNode booleans = ReadBooleanNode.create();

          @Override
          public Object execute(VirtualFrame frame) {
            List<Object> seen = new ArrayList<>();
            for (Runnable read :
                new Runnable[] {
                  () -> longs.execute(struct, 1),
                  () -> doubles.execute(struct, 0),
                  () -> booleans.execute(struct, 0),
                  () -> longs.execute(absent, 0),
                  () -> doubles.execute(absent, 0),
                  () -> booleans.execute(absent, 1),
                }) {
              try {
                read.run();
                seen.add("value");
              } catch (RuntimeException e) {
                seen.add(e.getClass().getSimpleName());
              }
            }
            return seen;
          }
        };
    assertEquals(6, ((List<?>) mismatched.getCallTarget().call()).size());
    Object out = root.getCallTarget().call();
    List<Object> once = List.of(42L, true, 9L, 2.25, true, 2, 42L, true, 2.25, 9L, true, false, Missing.INSTANCE);
    List<Object> expected = new ArrayList<>();
    for (int i = 0; i < 3; i++) expected.addAll(once);
    assertEquals(expected, out);
    Map<Object, Object> keep = new LinkedHashMap<>();
    keep.put(1, 2);
    assertEquals(keep, general(BebopType.map(INT32, INT32)).decode(hex("01000000 01000000 02000000")));
  }
}
