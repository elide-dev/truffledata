package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;
import static dev.elide.truffledata.bebop.BebopType.ref;
import static dev.elide.truffledata.bebop.Fixtures.general;
import static dev.elide.truffledata.bebop.Fixtures.hex;
import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.bebop.BebopException.Kind;
import dev.elide.truffledata.bebop.BebopSchema.TaggedField;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The wire specification's examples and the general profile. */
final class BebopCodecTest {
  private static void vector(BebopCodec codec, Object value, String hex) {
    byte[] expected = hex(hex);
    assertEquals(Fixtures.hex(expected), Fixtures.hex(codec.encode(value)));
    assertTrue(BebopValues.equal(value, codec.decode(expected)), () -> "decoded " + codec.decode(expected));
  }

  private static Kind fails(BebopCodec codec, String hex) {
    return assertThrows(BebopException.class, () -> codec.decode(hex(hex))).kind();
  }

  @Test
  void specificationExamples() {
    vector(general(STRING), "hello", "05 00 00 00 68 65 6c 6c 6f 00");
    vector(general(ref("Status")), 1, "01");
    vector(general(BebopType.array(INT32)), List.of(1, 2, 3), "03000000 01000000 02000000 03000000");
    vector(general(BebopType.fixed(BYTE, 4)), hex("deadbeef"), "de ad be ef");
    Map<Object, Object> map = new LinkedHashMap<>();
    map.put(1, 100);
    map.put(2, 200);
    vector(general(BebopType.map(BYTE, INT32)), map, "02000000 01 64000000 02 c8000000");
    vector(general(ref("Point")), new BebopRecord("Point", 1.0f, 2.0f), "0000803f 00000040");
    vector(
        general(ref("UserRequest")),
        new BebopRecord("UserRequest", 42, true),
        """
        08 00 00 00    // length = 8 bytes
        2a 00 00 00    // field 1: user_id = 42
        01             // field 2: include_profile = true
        04             // field 2 starts at payload offset 4
        03             // Mask 8 directory: tags 1 and 2 are present
        10             // Mask 8 directory, 1-byte boundaries
        """);
    vector(general(ref("UserRequest")), new BebopRecord("UserRequest", 42, null), "06000000 2a000000 01 10");
    vector(general(ref("UserRequest")), new BebopRecord("UserRequest", null, null), "01000000 00");
    vector(
        general(ref("Shape")),
        new BebopUnion(1, new BebopRecord("Circle", 5.0f)),
        "05000000 01 0000a040");
    vector(
        general(ref("Shape")),
        new BebopUnion(2, new BebopRecord("Rectangle", 3.0f, 4.0f)),
        "09000000 02 00004040 00008040");
    vector(general(ref("Event")), new BebopUnion(2, new BebopRecord("Ack", 0)), "05000000 02 00000000");
    vector(
        general(BebopType.array(BebopType.array(INT32))),
        List.of(List.of(1, 2), List.of(3)),
        "02000000 02000000 01000000 02000000 01000000 03000000");
    vector(
        general(BebopType.array(STRING)),
        List.of("ab", "cde"),
        "02000000 02000000 616200 03000000 63646500");
    vector(
        general(BebopType.fixed(BebopType.array(INT32), 3)),
        List.of(List.of(1, 2), List.of(), List.of(3)),
        "02000000 01000000 02000000 00000000 01000000 03000000");
  }

  @Test
  void everyPrimitiveRoundTrips() {
    Object[][] cases = {
      {BOOL, true}, {BYTE, 255}, {INT8, -128}, {INT16, -2}, {UINT16, 65535}, {INT32, -7},
      {UINT32, 0xFFFFFFFFL}, {INT64, Long.MIN_VALUE}, {UINT64, -1L},
      {INT128, BigInteger.ONE.shiftLeft(127).negate()},
      {INT128, BigInteger.valueOf(-1)},
      {UINT128, BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)},
      {FLOAT16, 1.5f}, {BFLOAT16, -2.0f}, {FLOAT32, -0.0f}, {FLOAT64, Math.PI},
      {UUID, java.util.UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")},
      {TIMESTAMP, new BebopTimestamp(-5, 999_999_999, -3_600_000)},
      {DURATION, new BebopDuration(-1, -5)}, {STRING, "a\u0000b é 😀"},
    };
    for (Object[] c : cases) {
      BebopCodec codec = general((BebopType) c[0]);
      byte[] bytes = codec.encode(c[1]);
      assertEquals(c[1], codec.decode(bytes), c[0].toString());
      assertArrayEquals(bytes, codec.encode(codec.decode(bytes)));
    }
    assertEquals(
        "00112233445566778899aabbccddeeff",
        Fixtures.hex(general(UUID).encode(java.util.UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"))));
    assertEquals(
        "0100000000000000" + "0000000000000080",
        Fixtures.hex(general(INT128).encode(BigInteger.ONE.shiftLeft(127).negate().add(BigInteger.ONE))));
  }

  @Test
  void reducedFloatsKeepEveryBitPattern() {
    for (BebopType type : new BebopType[] {FLOAT16, BFLOAT16}) {
      BebopCodec codec = general(type);
      for (int bits = 0; bits < 0x10000; bits++) {
        byte[] bytes = {(byte) bits, (byte) (bits >>> 8)};
        assertArrayEquals(bytes, codec.encode(codec.decode(bytes)), type + " " + bits);
      }
    }
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> general(FLOAT16).encode(0.1f)).kind());
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> general(BFLOAT16).encode(0.1f)).kind());
  }

  @Test
  void generalFloatsKeepNaNPayloads() {
    assertEquals("010080ff", Fixtures.hex(general(FLOAT32).encode(general(FLOAT32).decode(hex("010080ff"))))); // signaling
    assertEquals("0100f87f", Fixtures.hex(general(FLOAT32).encode(general(FLOAT32).decode(hex("0100f87f")))));
    assertEquals("010000000000f8ff", Fixtures.hex(general(FLOAT64).encode(general(FLOAT64).decode(hex("010000000000f8ff")))));
  }

  /** The directory an encoder must choose for a set of present tags, and its bytes. */
  @Test
  void canonicalDirectories() {
    int[][] tagSets = {
      {1}, {8}, {9}, {1, 9}, {1, 17}, {1, 2, 17}, {1, 2, 3, 17}, {33}, {1, 33}, {1, 2, 33},
      {1, 2, 3, 33}, {255}, {1, 100, 200, 255}, {16}, {17}, {32}, {15, 16}, {1, 2, 3}, {30, 31, 32}
    };
    String[] trailers = {
      "01 10", // Mask 8 ties with Tiny 1 and wins
      "80 10",
      "09 04", // Tiny 1 is smaller than Mask 16
      "01 0101 14", // Mask 16 ties with Tiny 2 and wins
      "01 0111 08", // Tiny 2 is smaller than Mask 32
      "0102 010211 0c", // Tiny 3 is smaller than Mask 32
      "010203 07000100 18", // four fields: Mask 32
      "21 04",
      "01 0121 08",
      "0102 010221 0c",
      "010203 00 07000000 03 01000000 03 1c", // blocks: two entries and the block mask
      "ff 04",
      "010203 00 01000000 01 08000000 02 80000000 03 00000040 c9 1c",
      "10 04", // Tiny 1 is smaller than Mask 16
      "11 04",
      "20 04",
      "01 00c0 14",
      "0102 07 10",
      "0102 1e1f20 0c",
    };
    for (int i = 0; i < tagSets.length; i++) {
      int[] tags = tagSets[i];
      TaggedField[] fields = new TaggedField[tags.length];
      Object[] values = new Object[tags.length];
      for (int f = 0; f < tags.length; f++) {
        fields[f] = new TaggedField(tags[f], "f" + tags[f], BYTE);
        values[f] = 0xA0 + f;
      }
      BebopSchema schema = BebopSchema.builder().message("M", fields).build();
      BebopCodec codec = BebopCodec.compile(schema, ref("M"), BebopProfile.GENERAL, BebopLimits.DEFAULT);
      BebopRecord record = new BebopRecord("M", values);
      byte[] bytes = codec.encode(record);
      StringBuilder payload = new StringBuilder();
      for (int f = 0; f < tags.length; f++) payload.append(String.format("a%x", f));
      byte[] tail = hex(payload + trailers[i]);
      byte[] expected = new byte[4 + tail.length];
      expected[0] = (byte) tail.length;
      System.arraycopy(tail, 0, expected, 4, tail.length);
      assertEquals(Fixtures.hex(expected), Fixtures.hex(bytes), java.util.Arrays.toString(tags));
      assertEquals(record, codec.decode(bytes));
    }
  }

  @Test
  void nonCanonicalIndexesAreRejected() {
    BebopCodec codec = general(ref("UserRequest"));
    assertEquals(Kind.MALFORMED, fails(codec, "06000000 2a000000 01 04")); // Tiny 1 where Mask 8 ties
    assertEquals(Kind.MALFORMED, fails(codec, "07000000 2a000000 0100 14")); // Mask 16, tag fits Mask 8
    assertEquals(Kind.MALFORMED, fails(codec, "06000000 2a000000 01 11")); // 2-byte width, tiny payload
    assertEquals(Kind.MALFORMED, fails(codec, "06000000 2a000000 01 30")); // reserved bit
    assertEquals(Kind.MALFORMED, fails(codec, "06000000 2a000000 01 13")); // width code 3
    assertEquals(Kind.MALFORMED, fails(codec, "05000000 2a000000 00")); // payload, empty directory
    assertEquals(Kind.MALFORMED, fails(codec, "02000000 00 10")); // Mask 8 with no field
    assertEquals(Kind.MALFORMED, fails(codec, "08000000 2a000000 01 05 03 10")); // boundary past payload
    assertEquals(Kind.MALFORMED, fails(codec, "08000000 2a000000 01 03 03 10")); // field 1 is 3 bytes
    assertEquals(Kind.MALFORMED, fails(codec, "07000000 2a000000 0201 08")); // tiny tags descend
    assertEquals(Kind.MALFORMED, fails(codec, "00000000")); // no control byte
    assertEquals(Kind.MALFORMED, fails(codec, "06000000 2a000000 01")); // truncated
    assertEquals(Kind.MALFORMED, fails(codec, "06000000 2a000000 01 10 00")); // trailing byte
    // Blocks: wrong rank, empty block, tag 256.
    BebopSchema schema =
        BebopSchema.builder()
            .message(
                "M",
                new TaggedField(1, "a", BYTE),
                new TaggedField(2, "b", BYTE),
                new TaggedField(3, "c", BYTE),
                new TaggedField(33, "d", BYTE))
            .build();
    BebopCodec blocks = BebopCodec.compile(schema, ref("M"), BebopProfile.GENERAL, BebopLimits.DEFAULT);
    String good = "13000000 a0a1a2a3 010203 00 07000000 03 01000000 03 1c";
    assertNotNull(blocks.decode(hex(good)));
    assertEquals(Kind.MALFORMED, fails(blocks, good.replace("03 01000000 03", "02 01000000 03")));
    assertEquals(Kind.MALFORMED, fails(blocks, "0e000000 a0a1a2 0102 00 07000000 00 00000000 03 1c".replace("0e", "0f")));
    assertEquals(Kind.MALFORMED, fails(blocks, "0a000000 a0a1 01 00 00000080 80 1c".replace("01 00", "00")));
  }

  @Test
  void boundaryWidthFollowsPayloadSize() {
    BebopSchema schema =
        BebopSchema.builder()
            .message("M", new TaggedField(1, "a", BebopType.array(BYTE)), new TaggedField(2, "b", BYTE))
            .build();
    BebopCodec codec = BebopCodec.compile(schema, ref("M"), BebopProfile.GENERAL, BebopLimits.DEFAULT);
    int[] sizes = {250, 251, 65530, 65531, 65532};
    int[] widths = {1, 2, 2, 4, 4};
    for (int i = 0; i < sizes.length; i++) {
      // Payload is 4 + size + 1 bytes: 255, 256, 65535, 65536, 65537.
      BebopRecord record = new BebopRecord("M", new byte[sizes[i]], 7);
      byte[] bytes = codec.encode(record);
      assertEquals(4 + 4 + sizes[i] + 1 + widths[i] + 2, bytes.length, "size " + sizes[i]);
      assertEquals(0x10 | Integer.numberOfTrailingZeros(widths[i]), bytes[bytes.length - 1]);
      assertEquals(record, codec.decode(bytes));
    }
    // A single field has no boundary, but the width code still follows the payload.
    byte[] one = codec.encode(new BebopRecord("M", new byte[300], null));
    assertEquals(0x11, one[one.length - 1]);
  }

  @Test
  void generalToleratesEvolution() {
    // Tag 3 is unknown to UserRequest: skipped through its indexed range.
    Object decoded = general(ref("UserRequest")).decode(hex("09000000 2a000000 ffff 04 05 10"));
    assertEquals(new BebopRecord("UserRequest", 42, null), decoded);
    // Any nonzero byte is true.
    assertEquals(true, general(BOOL).decode(hex("02")));
    // An unknown branch keeps its content.
    BebopCodec shape = general(ref("Shape"));
    Object unknown = shape.decode(hex("03000000 09 aabb"));
    assertEquals(new BebopUnion(9, hex("aabb")), unknown);
    assertEquals("0300000009aabb", Fixtures.hex(shape.encode(unknown)));
    // Unknown enum values pass through.
    assertEquals(2, general(ref("Status")).decode(hex("02")));
  }

  @Test
  void stringsAreExact() {
    BebopCodec codec = general(STRING);
    assertEquals("a\u0000b", codec.decode(hex("03000000 610062 00")));
    assertEquals(Kind.MALFORMED, fails(codec, "02000000 6162 01")); // terminator is not NUL
    assertEquals(Kind.MALFORMED, fails(codec, "02000000 6162")); // terminator is missing
    assertEquals(Kind.MALFORMED, fails(codec, "02000000 c328 00")); // bad continuation
    assertEquals(Kind.MALFORMED, fails(codec, "02000000 c080 00")); // overlong
    assertEquals(Kind.MALFORMED, fails(codec, "03000000 eda080 00")); // surrogate
    assertEquals(Kind.MALFORMED, fails(codec, "04000000 f4908080 00")); // above U+10FFFF
    assertEquals(Kind.MALFORMED, fails(codec, "01000000 e2 00")); // truncated sequence
    assertEquals(Kind.MALFORMED, fails(codec, "ffffffff 61 00")); // length past the input
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> codec.encode("\ud800")).kind());
    StringBuilder every = new StringBuilder();
    for (int cp = 0; cp <= 0x10FFFF; cp += 97) {
      if (cp >= 0xD800 && cp <= 0xDFFF) continue;
      every.appendCodePoint(cp);
    }
    BebopCodec big = BebopCodec.compile(Fixtures.SCHEMA, STRING, BebopProfile.GENERAL, BebopLimits.DEFAULT);
    assertEquals(every.toString(), big.decode(big.encode(every.toString())));
  }

  @Test
  void mapsRejectDuplicates() {
    BebopCodec codec = general(BebopType.map(INT32, INT32));
    assertEquals(Kind.MALFORMED, fails(codec, "02000000 01000000 05000000 01000000 06000000"));
    Map<Object, Object> colliding = new LinkedHashMap<>();
    colliding.put(1, 5);
    colliding.put(1L, 6);
    assertEquals(Kind.VALUE, assertThrows(BebopException.class, () -> codec.encode(colliding)).kind());
    assertEquals(
        Kind.UNSUPPORTED,
        assertThrows(BebopException.class, () -> general(BebopType.map(FLOAT32, INT32))).kind());
  }

  @Test
  void limitsAreSeparateFromMalformedBytes() {
    // 4 billion zero-byte structs in four bytes: a local limit, not a malformed encoding.
    BebopCodec empties = general(BebopType.array(ref("Empty")));
    assertEquals(Kind.LIMIT, fails(empties, "ffffffff"));
    assertEquals(3, ((List<?>) empties.decode(hex("03000000"))).size());
    // A count that cannot fit the remaining bytes is malformed under any limits.
    assertEquals(Kind.MALFORMED, fails(general(BebopType.array(INT32)), "ffffffff 01000000"));
    BebopLimits tight = new BebopLimits(64, 4, 3, 16, 256);
    BebopCodec bytes = BebopCodec.compile(Fixtures.SCHEMA, BebopType.array(BYTE), BebopProfile.GENERAL, tight);
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> bytes.decode(new byte[65])).kind());
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> bytes.encode(new byte[61])).kind());
    BebopCodec counted = BebopCodec.compile(Fixtures.SCHEMA, BebopType.array(INT16), BebopProfile.GENERAL, tight);
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> counted.decode(hex("05000000 0100 0200 0300 0400 0500"))).kind());
    BebopCodec work = BebopCodec.compile(Fixtures.SCHEMA, BebopType.array(BebopType.array(ref("Empty"))), BebopProfile.GENERAL, tight);
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> work.decode(hex("04000000 04000000 04000000 04000000 04000000"))).kind());
    BebopCodec alloc = BebopCodec.compile(Fixtures.SCHEMA, BebopType.array(STRING), BebopProfile.GENERAL, new BebopLimits(1 << 20, 1 << 20, 8, 1 << 20, 64));
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> alloc.decode(alloc.encode(List.of("0123456789012345678901234567890123456789")))).kind());
    // Depth: a tree nested past the limit, both ways.
    BebopCodec tree = BebopCodec.compile(Fixtures.SCHEMA, ref("Tree"), BebopProfile.GENERAL, new BebopLimits(1 << 20, 1 << 20, 8, 1 << 20, 1 << 20));
    BebopRecord deep = new BebopRecord("Tree", 0, null);
    for (int i = 0; i < 3; i++) deep = new BebopRecord("Tree", i, List.of(deep));
    assertEquals(deep, tree.decode(tree.encode(deep)));
    BebopRecord deeper = deep;
    for (int i = 0; i < 3; i++) deeper = new BebopRecord("Tree", i, List.of(deeper));
    BebopRecord tooDeep = deeper;
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> tree.encode(tooDeep)).kind());
    byte[] encoded = general(ref("Tree")).encode(tooDeep);
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> tree.decode(encoded)).kind());
  }

  @Test
  void unsupportedSchemas() {
    BebopSchema recursive =
        BebopSchema.builder().struct("S", new BebopSchema.Field("self", ref("S"))).build();
    assertEquals(
        Kind.UNSUPPORTED,
        assertThrows(
                BebopException.class,
                () -> BebopCodec.compile(recursive, ref("S"), BebopProfile.GENERAL, BebopLimits.DEFAULT))
            .kind());
    assertEquals(Kind.UNSUPPORTED, assertThrows(BebopException.class, () -> general(ref("Missing"))).kind());
  }

  @Test
  void decodeReadsOnlyItsRange() {
    BebopCodec codec = general(ref("UserRequest"));
    byte[] bytes = hex("06000000 2a000000 01 10");
    byte[] padded = new byte[bytes.length + 7];
    java.util.Arrays.fill(padded, (byte) 0xEE);
    System.arraycopy(bytes, 0, padded, 3, bytes.length);
    assertEquals(new BebopRecord("UserRequest", 42, null), codec.decode(padded, 3, bytes.length));
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> codec.decode(padded, 3, bytes.length - 1)).kind());
    List<Object> truncations = new ArrayList<>();
    for (int n = 0; n < bytes.length; n++) {
      int length = n;
      truncations.add(assertThrows(BebopException.class, () -> codec.decode(padded, 3, length)).kind());
    }
    assertTrue(truncations.stream().allMatch(k -> k == Kind.MALFORMED));
  }

  @Test
  void schemaIdentity() {
    assertEquals(64, Fixtures.SCHEMA.fingerprint().length());
    BebopSchema a = BebopSchema.builder().struct("S", new BebopSchema.Field("x", INT32)).build();
    BebopSchema b = BebopSchema.builder().struct("S", new BebopSchema.Field("x", INT32)).build();
    BebopSchema c = BebopSchema.builder().struct("S", new BebopSchema.Field("x", UINT32)).build();
    assertEquals(a.fingerprint(), b.fingerprint());
    assertNotEquals(a.fingerprint(), c.fingerprint());
  }
}
