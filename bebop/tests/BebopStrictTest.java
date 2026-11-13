package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;
import static dev.elide.truffledata.bebop.BebopType.ref;
import static dev.elide.truffledata.bebop.Fixtures.hex;
import static dev.elide.truffledata.bebop.Fixtures.strict;
import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.bebop.BebopException.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The strict profile: one accepted encoding per value, checked against the reference validator. */
final class BebopStrictTest {
  private static Kind fails(BebopCodec codec, String hex) {
    Kind kind = assertThrows(BebopException.class, () -> codec.decode(hex(hex))).kind();
    assertEquals(kind, assertThrows(BebopException.class, () -> codec.decodeReference(hex(hex))).kind());
    return kind;
  }

  private static void accepts(BebopCodec codec, Object value, String hex) {
    byte[] bytes = hex(hex);
    assertEquals(Fixtures.hex(bytes), Fixtures.hex(codec.encode(value)));
    assertTrue(BebopValues.equal(value, codec.decode(bytes)));
    assertTrue(BebopValues.equal(value, codec.decodeReference(bytes)));
  }

  @Test
  void profileChoicesAreExplicit() {
    for (BebopType.Scalar kind : new BebopType.Scalar[] {FLOAT16, BFLOAT16, FLOAT32, FLOAT64})
      assertEquals(
          Kind.UNSUPPORTED,
          assertThrows(BebopException.class, () -> BebopProfile.strict("x").admit(kind).build()).kind());
    assertThrows(IllegalArgumentException.class, () -> BebopProfile.strict("x").canonicalNaN32(0x3F800000));
    assertThrows(IllegalArgumentException.class, () -> BebopProfile.strict("x").canonicalNaN64(0L));
    assertThrows(IllegalArgumentException.class, () -> BebopProfile.strict("x").canonicalNaN16(0x7C00));
    assertThrows(IllegalArgumentException.class, () -> BebopProfile.strict("x").canonicalNaN16(0x17E00));
    assertThrows(IllegalArgumentException.class, () -> BebopProfile.strict("x").canonicalNaNBrain16(0x7F80));
    assertThrows(IllegalArgumentException.class, () -> BebopProfile.strict("x").canonicalNaNBrain16(0x3F80));
    assertThrows(IllegalArgumentException.class, () -> BebopProfile.strict(""));
    assertThrows(IllegalArgumentException.class, () -> BebopProfile.strict("a;b"));
    String identity = Fixtures.STRICT.identity();
    assertEquals(identity, Fixtures.STRICT.toString());
    assertTrue(identity.startsWith("strict-experimental;rules=strict-rules-0.11;wire=" + BebopProfile.WIRE_REVISION + ";admit="));
    assertTrue(identity.contains("nan16=7e00") && identity.contains("nanbrain16=7fc0"));
    assertTrue(identity.contains("nan32=7fc00000") && identity.contains("nan64=7ff8000000000000"));
    BebopProfile narrow = BebopProfile.strict("test").admit(BOOL, INT32).build();
    assertNotEquals(identity, narrow.identity());
    assertTrue(narrow.strict() && narrow.admits(BOOL) && !narrow.admits(STRING));
    assertFalse(BebopProfile.GENERAL.strict());
    assertEquals("general;wire=" + BebopProfile.WIRE_REVISION, BebopProfile.GENERAL.identity());
    // The named rules revision cannot be claimed with other choices: a custom profile says so.
    BebopProfile other =
        BebopProfile.strict("strict-rules-0.11")
            .admitAll()
            .canonicalNaN16(0x7E01)
            .canonicalNaNBrain16(0x7FC0)
            .canonicalNaN32(0x7FC00000)
            .canonicalNaN64(0x7FF8000000000000L)
            .build();
    assertTrue(other.identity().startsWith("strict-experimental;custom=strict-rules-0.11;"));
    assertNotEquals(identity, other.identity());
    assertNotEquals(identity, BebopProfile.rules011("another-name").identity());
    assertEquals(identity, BebopProfile.rules011("strict-rules-0.11").identity());
    // Types outside the admitted subset are an unsupported schema, not a malformed encoding.
    for (BebopType type : new BebopType[] {INT8, STRING, ref("Status"), BebopType.array(BYTE), BebopType.fixed(BYTE, 2)})
      assertEquals(
          Kind.UNSUPPORTED,
          assertThrows(
                  BebopException.class,
                  () -> BebopCodec.compile(Fixtures.SCHEMA, type, narrow, BebopLimits.DEFAULT))
              .kind());
  }

  @Test
  void reducedFloats() {
    accepts(strict(FLOAT16), Float.NaN, "007e");
    assertEquals(Kind.MALFORMED, fails(strict(FLOAT16), "017e"));
    assertEquals(Kind.MALFORMED, fails(strict(FLOAT16), "00fe"));
    assertEquals(Kind.MALFORMED, fails(strict(FLOAT16), "017c")); // signaling
    accepts(strict(FLOAT16), Float.POSITIVE_INFINITY, "007c");
    accepts(strict(FLOAT16), -0.0f, "0080");
    accepts(strict(FLOAT16), 1.5f, "003e");
    assertEquals("007e", Fixtures.hex(strict(FLOAT16).encode(Float.intBitsToFloat(0xFFC12345))));
    accepts(strict(BFLOAT16), Float.NaN, "c07f");
    assertEquals(Kind.MALFORMED, fails(strict(BFLOAT16), "c17f"));
    assertEquals(Kind.MALFORMED, fails(strict(BFLOAT16), "c0ff"));
    accepts(strict(BFLOAT16), Float.NEGATIVE_INFINITY, "80ff");
    accepts(strict(BFLOAT16), 1.0f, "803f");
    assertEquals("c07f", Fixtures.hex(strict(BFLOAT16).encode(Float.intBitsToFloat(0x7F812345))));
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> strict(BFLOAT16).encode(0.1f)).kind());
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> Fixtures.general(FLOAT16).encode(Float.intBitsToFloat(0x7F800001))).kind());
  }

  @Test
  void temporalTypesAndUuids() {
    BebopCodec at = strict(TIMESTAMP);
    accepts(at, new BebopTimestamp(-1, 999_999_999, 86_400_000), "ffffffffffffffff ffc99a3b 005c2605");
    accepts(at, new BebopTimestamp(0, 0, -86_400_000), "0000000000000000 00000000 00a4d9fa");
    assertEquals(Kind.MALFORMED, fails(at, "0000000000000000 00ca9a3b 00000000")); // 1e9 nanoseconds
    assertEquals(Kind.MALFORMED, fails(at, "0000000000000000 ffffffff 00000000")); // negative nanoseconds
    assertEquals(Kind.MALFORMED, fails(at, "0000000000000000 00000000 015c2605")); // offset past a day
    assertEquals(Kind.MALFORMED, fails(at, "0000000000000000 00000000 ffa3d9fa"));
    assertEquals(
        Kind.VALUE,
        assertThrows(BebopException.class, () -> at.encode(new BebopTimestamp(0, 1_000_000_000, 0))).kind());
    // The same instant at two offsets is two values.
    assertFalse(
        java.util.Arrays.equals(
            at.encode(new BebopTimestamp(5, 0, 0)), at.encode(new BebopTimestamp(5, 0, 3_600_000))));
    // The general profile keeps whatever it is given.
    assertEquals(
        new BebopTimestamp(0, -1, 0),
        Fixtures.general(TIMESTAMP).decode(hex("0000000000000000 ffffffff 00000000")));
    BebopCodec span = strict(DURATION);
    accepts(span, new BebopDuration(1, 999_999_999), "0100000000000000 ffc99a3b");
    accepts(span, new BebopDuration(-1, -999_999_999), "ffffffffffffffff 013665c4");
    accepts(span, new BebopDuration(0, -5), "0000000000000000 fbffffff");
    accepts(span, new BebopDuration(0, 0), "0000000000000000 00000000");
    assertEquals(Kind.MALFORMED, fails(span, "0100000000000000 ffffffff")); // +1 s, -1 ns
    assertEquals(Kind.MALFORMED, fails(span, "ffffffffffffffff 01000000")); // -1 s, +1 ns
    assertEquals(Kind.MALFORMED, fails(span, "0000000000000000 00ca9a3b")); // a whole second of ns
    assertEquals(Kind.MALFORMED, fails(span, "0000000000000000 003665c4"));
    assertEquals(
        Kind.VALUE,
        assertThrows(BebopException.class, () -> span.encode(new BebopDuration(1, -1))).kind());
    assertEquals(new BebopDuration(1, -1), Fixtures.general(DURATION).decode(hex("0100000000000000 ffffffff")));
    // Every 16-byte pattern is a distinct UUID, nil included.
    accepts(strict(UUID), new java.util.UUID(0, 0), "00000000000000000000000000000000");
    accepts(
        strict(UUID),
        java.util.UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100"),
        "ffeeddccbbaa99887766554433221100");
    // Sets of UUIDs sort by their bytes, which is their text order.
    assertEquals(
        "02000000" + "00000000000000000000000000000001" + "ff000000000000000000000000000000",
        Fixtures.hex(
            strict(BebopType.set(UUID))
                .encode(List.of(new java.util.UUID(0xFF00000000000000L, 0), new java.util.UUID(0, 1)))));
    accepts(strict(INT128), java.math.BigInteger.valueOf(-2), "feffffffffffffff ffffffffffffffff");
  }

  @Test
  void booleans() {
    accepts(strict(BOOL), false, "00");
    accepts(strict(BOOL), true, "01");
    assertEquals(Kind.MALFORMED, fails(strict(BOOL), "02"));
    assertEquals(Kind.MALFORMED, fails(strict(BOOL), "ff"));
  }

  @Test
  void floats() {
    accepts(strict(FLOAT32), Float.NaN, "0000c07f");
    accepts(strict(FLOAT64), Double.NaN, "000000000000f87f");
    assertEquals(Kind.MALFORMED, fails(strict(FLOAT32), "0100c07f")); // payload
    assertEquals(Kind.MALFORMED, fails(strict(FLOAT32), "0000c0ff")); // sign
    assertEquals(Kind.MALFORMED, fails(strict(FLOAT64), "010000000000f87f"));
    // Host NaN payloads are normalized on the way in.
    assertEquals("0000c07f", Fixtures.hex(strict(FLOAT32).encode(Float.intBitsToFloat(0xFFC12345))));
    // Zeros keep their sign.
    accepts(strict(FLOAT64), 0.0, "0000000000000000");
    accepts(strict(FLOAT64), -0.0, "0000000000000080");
    assertFalse(BebopValues.equal(0.0, strict(FLOAT64).decode(hex("0000000000000080"))));
    accepts(strict(FLOAT32), Float.NEGATIVE_INFINITY, "000080ff");
  }

  @Test
  void presenceIsNotDefaultEquivalence() {
    BebopCodec codec = strict(ref("Request"));
    accepts(codec, new BebopRecord("Request", null, null), "01000000 00");
    accepts(codec, new BebopRecord("Request", "", null), "07000000 00000000 00 01 10");
    accepts(codec, new BebopRecord("Request", null, ""), "07000000 00000000 00 02 10");
    BebopCodec user = strict(ref("UserRequest"));
    accepts(user, new BebopRecord("UserRequest", 0, false), "08000000 00000000 00 04 03 10");
    accepts(user, new BebopRecord("UserRequest", 0, null), "06000000 00000000 01 10");
  }

  @Test
  void mapsAreSortedByEncodedKey() {
    BebopCodec byId = strict(BebopType.map(INT32, BOOL));
    Map<Object, Object> ids = new LinkedHashMap<>();
    ids.put(256, true);
    ids.put(1, false);
    ids.put(-1, true);
    // Little-endian bytes order 256 (00 01 ..) before 1 (01 00 ..) before -1 (ff ..).
    accepts(byId, ids, "03000000 00010000 01 01000000 00 ffffffff 01");
    assertEquals(Kind.MALFORMED, fails(byId, "02000000 01000000 00 00010000 01")); // unsorted
    assertEquals(Kind.MALFORMED, fails(byId, "02000000 01000000 00 01000000 01")); // duplicate
    BebopCodec byName = strict(BebopType.map(STRING, BOOL));
    Map<Object, Object> names = new LinkedHashMap<>();
    names.put("b", true);
    names.put("aa", true);
    // The length prefix leads: "b" (01 ..) sorts before "aa" (02 ..).
    accepts(byName, names, "02000000 01000000 6200 01 02000000 616100 01");
    assertEquals(Kind.MALFORMED, fails(byName, "02000000 02000000 616100 01 01000000 6200 01"));
    Map<Object, Object> colliding = new LinkedHashMap<>();
    colliding.put(1, true);
    colliding.put(1L, false);
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> byId.encode(colliding)).kind());
  }

  @Test
  void setsAreSortedAndDistinct() {
    BebopCodec set = strict(BebopType.set(UINT16));
    assertEquals("03000000 0001 0100 0200".replace(" ", ""), Fixtures.hex(set.encode(List.of(2, 1, 256, 1, 2))));
    accepts(set, List.of(256, 1, 2), "03000000 0001 0100 0200");
    assertEquals(Kind.MALFORMED, fails(set, "02000000 0100 0001"));
    assertEquals(Kind.MALFORMED, fails(set, "02000000 0100 0100"));
    // A sequence keeps its order and its duplicates.
    accepts(strict(BebopType.array(UINT16)), List.of(2, 1, 2), "03000000 0200 0100 0200");
    // A set of zero-byte elements has at most one member.
    BebopCodec empties = strict(BebopType.set(ref("Empty")));
    BebopRecord empty = new BebopRecord("Empty");
    accepts(empties, List.of(empty), "01000000");
    assertEquals("01000000", Fixtures.hex(empties.encode(List.of(empty, empty))));
    assertEquals(Kind.MALFORMED, fails(empties, "02000000"));
    accepts(strict(BebopType.array(ref("Empty"))), List.of(empty, empty), "02000000");
  }

  @Test
  void exactSchema() {
    assertEquals(Kind.MALFORMED, fails(strict(ref("UserRequest")), "09000000 2a000000 ffff 04 05 10")); // tag 3
    assertEquals(Kind.MALFORMED, fails(strict(ref("Status")), "02"));
    accepts(strict(ref("Status")), 3, "03");
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> strict(ref("Status")).encode(2)).kind());
    assertEquals(Kind.MALFORMED, fails(strict(ref("Event")), "03000000 09 aabb"));
    assertEquals(
        Kind.VALUE,
        assertThrows(BebopException.class, () -> strict(ref("Event")).encode(new BebopUnion(9, hex("aabb")))).kind());
    assertEquals(Kind.MALFORMED, fails(strict(ref("Event")), "06000000 02 00000000 00")); // branch underrun
    assertEquals(Kind.MALFORMED, fails(strict(ref("UserRequest")), "06000000 2a000000 01 10 00")); // trailing
    // A zero-byte field range is only valid for a type with a zero-byte encoding.
    assertEquals(Kind.MALFORMED, fails(strict(ref("UserRequest")), "07000000 2a000000 04 03 10"));
  }

  /** The same preservation property under the general profile, where float payloads survive. */
  @Test
  void generalValuesArePreserved() {
    BebopCodec codec = Fixtures.general(ref("Everything"));
    Random r = new Random(0x6E6);
    for (int i = 0; i < 2000; i++) {
      Object value = Gen.value(r, ref("Everything"), 0);
      byte[] bytes = codec.encode(value);
      assertEquals(
          Abstract.of(Fixtures.SCHEMA, ref("Everything"), value, false),
          Abstract.of(Fixtures.SCHEMA, ref("Everything"), codec.decode(bytes), false));
      assertArrayEquals(bytes, codec.encode(codec.decode(bytes)));
    }
  }

  /**
   * Every one of the 65,536 patterns of each 16-bit float: every non-NaN and exactly the one named
   * NaN are accepted and re-encode to themselves, through the direct decoder, the allocation-free
   * validator and the reference; every other NaN is rejected by all three.
   */
  @Test
  void reducedFloatsExhaustively() {
    for (BebopType.Scalar kind : new BebopType.Scalar[] {FLOAT16, BFLOAT16}) {
      BebopCodec codec = strict(kind);
      int exponent = kind == FLOAT16 ? 0x7C00 : 0x7F80;
      int mantissa = kind == FLOAT16 ? 0x3FF : 0x7F;
      int canonical = kind == FLOAT16 ? 0x7E00 : 0x7FC0;
      int accepted = 0;
      for (int bits = 0; bits < 0x10000; bits++) {
        byte[] bytes = {(byte) bits, (byte) (bits >>> 8)};
        boolean nan = (bits & exponent) == exponent && (bits & mantissa) != 0;
        String label = kind + " " + Integer.toHexString(bits);
        if (nan && bits != canonical) {
          assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> codec.decode(bytes), label).kind());
          assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> codec.validate(bytes), label).kind());
          assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> codec.decodeReference(bytes), label).kind());
          continue;
        }
        accepted++;
        Float value = (Float) codec.decode(bytes);
        codec.validate(bytes);
        assertEquals(value, codec.decodeReference(bytes), label);
        assertArrayEquals(bytes, codec.encode(value), label);
        assertEquals(nan, Float.isNaN(value), label);
        // The host float is the same number: widening is exact, so narrowing it back with the
        // JDK's own conversion gives the pattern again, with no rounding on either side.
        if (!nan) {
          int back = kind == FLOAT16 ? Float.floatToFloat16(value) & 0xFFFF : Float.floatToRawIntBits(value) >>> 16;
          assertEquals(bits, back, label);
          if (kind == BFLOAT16) assertEquals(0, Float.floatToRawIntBits(value) & 0xFFFF, label);
        }
      }
      assertEquals(kind == FLOAT16 ? 65536 - 2 * 1023 + 1 : 65536 - 2 * 127 + 1, accepted, kind.toString());
    }
    // Subnormal boundaries and the values just outside them: nothing rounds and nothing flushes.
    BebopCodec half = strict(FLOAT16);
    assertEquals(Float.intBitsToFloat(0x33800000), half.decode(hex("0100"))); // 2^-24, smallest subnormal
    assertEquals(Float.intBitsToFloat(0x387FC000), half.decode(hex("ff03"))); // largest subnormal
    assertEquals(Float.intBitsToFloat(0x38800000), half.decode(hex("0004"))); // 2^-14, smallest normal
    assertEquals(65504.0f, half.decode(hex("ff7b")));
    for (int bits : new int[] {0x33000000, 0x33800001, 0x387FC001, 0x477FE001, 0x47800000, 0x3DCCCCCD})
      assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> half.encode(Float.intBitsToFloat(bits))).kind());
    BebopCodec brain = strict(BFLOAT16);
    assertEquals(Float.intBitsToFloat(0x00010000), brain.decode(hex("0100")));
    assertEquals(Float.intBitsToFloat(0x007F0000), brain.decode(hex("7f00")));
    assertEquals(Float.intBitsToFloat(0x00800000), brain.decode(hex("8000")));
    for (int bits : new int[] {0x00000001, 0x0000FFFF, 0x3F800001, 0x7F7FFFFF})
      assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> brain.encode(Float.intBitsToFloat(bits))).kind());
  }

  @Test
  void identityBindsRootRefinementsAndRules() {
    BebopCodec base = strict(BebopType.array(BebopType.array(UINT16)));
    java.util.Set<String> schemaIdentities = new java.util.HashSet<>();
    java.util.Set<String> identities = new java.util.HashSet<>();
    BebopType[] roots = {
      BebopType.array(BebopType.array(UINT16)),
      BebopType.array(BebopType.set(UINT16)),
      BebopType.set(BebopType.array(UINT16)),
      BebopType.set(BebopType.set(UINT16)),
      BebopType.fixed(BebopType.array(UINT16), 2),
      TIMESTAMP,
      BebopType.utcInstant(),
      BebopType.array(TIMESTAMP),
      BebopType.array(BebopType.utcInstant()),
      ref("UserRequest"),
      ref("Request"),
    };
    for (BebopType root : roots) {
      assertTrue(schemaIdentities.add(strict(root).schemaIdentity()), root.toString());
      assertTrue(identities.add(strict(root).identity()), root.toString());
      // The profile is not part of the schema identity, and is part of the full one.
      assertEquals(strict(root).schemaIdentity(), Fixtures.general(root).schemaIdentity());
      assertTrue(identities.add(Fixtures.general(root).identity()), root.toString());
    }
    assertEquals(base.identity(), strict(BebopType.array(BebopType.array(UINT16))).identity());
    assertEquals(64, base.identity().length());
    // A definition anywhere in the schema changes it too.
    BebopSchema one = BebopSchema.builder().struct("S", new BebopSchema.Field("x", INT32)).build();
    BebopSchema two = BebopSchema.builder().struct("S", new BebopSchema.Field("x", UINT32)).build();
    assertNotEquals(
        BebopCodec.compile(one, ref("S"), Fixtures.STRICT, BebopLimits.DEFAULT).schemaIdentity(),
        BebopCodec.compile(two, ref("S"), Fixtures.STRICT, BebopLimits.DEFAULT).schemaIdentity());
    // Refinements the codec cannot validate are refused, not ignored.
    for (BebopType bad :
        new BebopType[] {
          new BebopType.Refined(DURATION, BebopType.Refinement.UTC_INSTANT),
          new BebopType.Refined(BebopType.array(TIMESTAMP), BebopType.Refinement.UTC_INSTANT),
        })
      assertEquals(Kind.UNSUPPORTED, assertThrows(BebopException.class, () -> strict(bad)).kind());
    assertEquals(
        Kind.UNSUPPORTED,
        assertThrows(
                BebopException.class,
                () ->
                    BebopCodec.compile(
                        Fixtures.SCHEMA,
                        BebopType.utcInstant(),
                        BebopProfile.strict("x").admit(BOOL).build(),
                        BebopLimits.DEFAULT))
            .kind());
  }

  @Test
  void utcInstantsAndNestedSets() {
    // The primitive keeps offsets and distinguishes them; the refinement rejects them and never
    // repairs: the bytes of an accepted instant are the same 16 bytes.
    BebopCodec any = strict(TIMESTAMP);
    BebopCodec utc = strict(BebopType.utcInstant());
    BebopTimestamp zero = new BebopTimestamp(5, 7, 0);
    BebopTimestamp offset = new BebopTimestamp(5, 7, 3_600_000);
    assertNotEquals(Fixtures.hex(any.encode(zero)), Fixtures.hex(any.encode(offset)));
    assertNotEquals(any.decode(any.encode(zero)), any.decode(any.encode(offset)));
    assertArrayEquals(any.encode(zero), utc.encode(zero));
    assertEquals(16, utc.encode(zero).length);
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> utc.encode(offset)).kind());
    assertEquals(Kind.MALFORMED, fails(utc, Fixtures.hex(any.encode(offset))));
    // The general profile enforces the schema's constraint as well.
    BebopCodec generalUtc = Fixtures.general(BebopType.utcInstant());
    assertEquals(zero, generalUtc.decode(any.encode(zero)));
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> generalUtc.decode(any.encode(offset))).kind());
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> generalUtc.validate(any.encode(offset))).kind());
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> generalUtc.encode(offset)).kind());
    assertEquals(new BebopTimestamp(0, -1, 0), generalUtc.decode(hex("0000000000000000 ffffffff 00000000")));
    // Nested sets and sequences are different schemas with different acceptance.
    byte[] unsortedInner = hex("01000000 02000000 0200 0100");
    assertEquals(List.of(List.of(2, 1)), strict(BebopType.array(BebopType.array(UINT16))).decode(unsortedInner));
    assertEquals(Kind.MALFORMED, fails(strict(BebopType.array(BebopType.set(UINT16))), "01000000 02000000 0200 0100"));
    assertEquals(
        "01000000 02000000 0100 0200".replace(" ", ""),
        Fixtures.hex(strict(BebopType.array(BebopType.set(UINT16))).encode(List.of(List.of(2, 1, 2)))));
    // Integer host types are one value; a map cannot hold it twice.
    BebopCodec ints = strict(BebopType.set(INT32));
    assertEquals("01000000 07000000".replace(" ", ""), Fixtures.hex(ints.encode(List.of(7, 7L, (short) 7, java.math.BigInteger.valueOf(7)))));
    Map<Object, Object> colliding = new LinkedHashMap<>();
    colliding.put(7, true);
    colliding.put((short) 7, true);
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> strict(BebopType.map(INT32, BOOL)).encode(colliding)).kind());
    assertThrows(
        IllegalArgumentException.class,
        () -> Abstract.of(Fixtures.SCHEMA, BebopType.map(INT32, BOOL), colliding, true));
    // Both zeros are members of one set.
    assertEquals(2, ((List<?>) strict(BebopType.set(FLOAT64)).decode(strict(BebopType.set(FLOAT64)).encode(List.of(0.0, -0.0, 0.0)))).size());
    // Durations: the adapter is explicit; neither the encoder nor the decoder applies it.
    assertEquals(new BebopDuration(0, -500_000_000), BebopDuration.normalized(-1, 500_000_000));
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> BebopDuration.normalized(Long.MAX_VALUE, 1_000_000_000)).kind());
  }

  @Test
  void roundTripsAndReferenceAgreement() {
    BebopCodec codec = strict(ref("Everything"));
    Random r = new Random(0xBEB0);
    int accepted = 0;
    int rejected = 0;
    for (int i = 0; i < 3000; i++) {
      Object value = Gen.value(r, ref("Everything"), 0);
      byte[] bytes = codec.encode(value);
      Object decoded = codec.decode(bytes);
      // Value preservation, against a reference that never calls the codec: the abstract value
      // of the original host value is the abstract value of what came back.
      assertEquals(
          Abstract.of(Fixtures.SCHEMA, ref("Everything"), value, true),
          Abstract.of(Fixtures.SCHEMA, ref("Everything"), decoded, true));
      assertArrayEquals(bytes, codec.encode(decoded));
      assertTrue(BebopValues.equal(decoded, codec.decodeReference(bytes)));
      codec.validate(bytes);
      // The strict decoder, the allocation-free validator and the decode-and-reencode reference
      // accept exactly the same inputs, for the same reason.
      for (int m = 0; m < 24; m++) {
        byte[] mutant = Gen.mutate(r, bytes);
        Object direct = attempt(() -> codec.decode(mutant));
        Object reference = attempt(() -> codec.decodeReference(mutant));
        Object validated = attempt(() -> {
          codec.validate(mutant);
          return direct;
        });
        if (direct instanceof BebopException a) {
          assertTrue(reference instanceof BebopException, () -> "reference accepted " + Fixtures.hex(mutant));
          assertTrue(validated instanceof BebopException, () -> "validator accepted " + Fixtures.hex(mutant));
          // The reference decodes leniently first, so on bytes that are both non-canonical and
          // over a local limit it can reach the limit where the direct decoder had already
          // rejected the form. Both reject; only the reported reason may differ, and only so.
          Kind other = ((BebopException) reference).kind();
          assertTrue(a.kind() == other || other == Kind.LIMIT, () -> Fixtures.hex(mutant));
          assertEquals(a.getMessage(), ((BebopException) validated).getMessage(), () -> Fixtures.hex(mutant));
          rejected++;
        } else {
          assertFalse(reference instanceof BebopException, () -> "reference rejected " + Fixtures.hex(mutant) + ": " + reference);
          assertFalse(validated instanceof BebopException, () -> "validator rejected " + Fixtures.hex(mutant) + ": " + validated);
          assertTrue(BebopValues.equal(direct, reference));
          assertArrayEquals(mutant, codec.encode(direct), () -> Fixtures.hex(mutant));
          accepted++;
        }
      }
    }
    assertTrue(accepted > 500 && rejected > 5000, accepted + " accepted, " + rejected + " rejected");
  }

  private static Object attempt(java.util.function.Supplier<Object> action) {
    try {
      return action.get();
    } catch (BebopException e) {
      return e;
    }
  }
}
