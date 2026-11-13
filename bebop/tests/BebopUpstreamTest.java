package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.ref;
import static dev.elide.truffledata.bebop.Fixtures.upstream;
import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.bebop.BebopException.Kind;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Values encoded by upstream's C runtime and {@code bebopc-gen-c} at {@code 2026.0.0-alpha.6}
 * ({@code descriptors/gen/values.c}), read with codecs compiled from the loaded descriptor sets.
 * The general profile must decode each to the value written and re-encode it byte for byte; the
 * strict profile must do the same wherever upstream's bytes are the canonical encoding.
 */
final class BebopUpstreamTest {
  private static final BebopSchema TYPES = BebopDescriptors.load(upstream("types.bin"));
  private static final BebopSchema BARE = BebopDescriptors.load(upstream("bare.bin"));
  private static final BebopSchema FLAGS = BebopDescriptors.load(upstream("flags.bin"));
  private static final BebopSchema VECTORS = BebopDescriptors.load(upstream("vectors.bin"));

  private static final BebopTimestamp AT = new BebopTimestamp(1_700_000_000L, 123_456_789, -3_600_000);
  private static final BebopDuration SPAN = new BebopDuration(-5, -500);

  private static UUID uuid(int start) {
    long high = 0;
    long low = 0;
    for (int i = 0; i < 8; i++) {
      high = high << 8 | (start + i);
      low = low << 8 | (start + 8 + i);
    }
    return new UUID(high, low);
  }

  /** The 19 scalars values.c writes, in field order. */
  private static @Nullable Object[] scalars() {
    return new Object[] {
      true,
      0xAB,
      -5,
      -300,
      60000,
      -123456,
      4_000_000_000L,
      -1_234_567_890_123L,
      0xFEDCBA9876543210L,
      BigInteger.valueOf(-2),
      BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(7)),
      1.5f,
      -2.25f,
      3.141592653589793,
      1.0f,
      "héllo",
      uuid(0),
      AT,
      SPAN
    };
  }

  private static BebopRecord allScalars() {
    return new BebopRecord("golden.scalars.AllScalars", scalars());
  }

  private static BebopRecord message(int... present) {
    @Nullable Object[] all = scalars();
    @Nullable Object[] values = new Object[all.length];
    for (int i : present) values[i] = all[i];
    return new BebopRecord("golden.scalars.AllScalarsMessage", values);
  }

  private static Map<Object, Object> map(Object... entries) {
    Map<Object, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) out.put(entries[i], entries[i + 1]);
    return out;
  }

  private static BebopRecord envelope() {
    byte[] digest = new byte[16];
    for (int i = 0; i < 16; i++) digest[i] = (byte) (0xF0 + i);
    String inner = "golden.types.Outer.Inner";
    String deeper = inner + ".Deeper";
    return new BebopRecord(
        "golden.types.Envelope",
        new BebopRecord("golden.types.Envelope.Header", uuid(0x10), AT),
        new BebopUnion(
            2, new BebopRecord("golden.types.Shape.Polygon", List.of(List.of(1.0f, 2.0f), List.of(3.0f, 4.0f)))),
        List.of("b", "a"),
        List.of(List.of(1, 2, 3), List.of(4, 5, 6)),
        null,
        map(80, List.of(1L, -2L), 1, List.of()),
        digest,
        map(uuid(0x20), map(-1, List.of("one", "two"), 2, List.of())),
        null,
        List.of(List.of(1, -1), List.of()),
        List.of(
            new BebopRecord(inner, "left", new BebopRecord(deeper, 42L)),
            new BebopRecord(inner, "right", new BebopRecord(deeper, new Object[1]))),
        map(true, 255, false, 0),
        true);
  }

  /** Decodes under the general profile and re-encodes to the same bytes. */
  private static void general(BebopSchema schema, String root, String file, Object expected) {
    byte[] bytes = upstream("values/" + file);
    BebopCodec codec = BebopCodec.compile(schema, ref(root), BebopProfile.GENERAL, BebopLimits.DEFAULT);
    codec.validate(bytes);
    Object decoded = codec.decode(bytes);
    assertTrue(BebopValues.equal(expected, decoded), () -> file + ": " + decoded);
    assertArrayEquals(bytes, codec.encode(decoded), file);
    assertArrayEquals(bytes, codec.encode(expected), file);
    assertNotNull(codec.open(bytes).root(), file);
  }

  /** Upstream's bytes are also the strict profile's only encoding of the value. */
  private static void canonical(BebopSchema schema, String root, String file, Object expected) {
    general(schema, root, file, expected);
    byte[] bytes = upstream("values/" + file);
    BebopCodec codec = BebopCodec.compile(schema, ref(root), Fixtures.STRICT, BebopLimits.DEFAULT);
    codec.validate(bytes);
    assertTrue(BebopValues.equal(expected, codec.decode(bytes)), file);
    assertTrue(BebopValues.equal(expected, codec.decodeReference(bytes)), file);
    assertArrayEquals(bytes, codec.encode(expected), file);
  }

  @Test
  void everyScalar() {
    canonical(TYPES, "golden.scalars.AllScalars", "all_scalars.struct.bin", allScalars());
    int[] every = new int[19];
    for (int i = 0; i < every.length; i++) every[i] = i;
    canonical(TYPES, "golden.scalars.AllScalarsMessage", "all_scalars.message.bin", message(every));
  }

  @Test
  void messageIndexes() {
    // Tags 5, 16, 19: a tiny directory. No fields: the empty index. A 300-byte string: 2-byte boundaries.
    canonical(TYPES, "golden.scalars.AllScalarsMessage", "sparse.message.bin", sparse());
    canonical(TYPES, "golden.scalars.AllScalarsMessage", "empty.message.bin", message());
    @Nullable Object[] wide = new Object[19];
    wide[0] = false;
    wide[5] = 1;
    wide[15] = "x".repeat(300);
    canonical(TYPES, "golden.scalars.AllScalarsMessage", "wide.message.bin", new BebopRecord("golden.scalars.AllScalarsMessage", wide));
  }

  private static BebopRecord sparse() {
    @Nullable Object[] values = new Object[19];
    values[4] = 7;
    values[15] = "";
    values[18] = SPAN;
    return new BebopRecord("golden.scalars.AllScalarsMessage", values);
  }

  @Test
  void unions() {
    canonical(TYPES, "golden.types.Shape", "shape.circle.bin", new BebopUnion(1, new BebopRecord("golden.types.Shape.Circle", 2.5f)));
    canonical(TYPES, "golden.types.Shape", "shape.empty.bin", new BebopUnion(5, new BebopRecord("golden.types.Shape.Empty")));
    canonical(TYPES, "golden.types.Shape", "shape.plain.bin", new BebopUnion(3, allScalars()));
    canonical(
        BARE,
        "Wrapper",
        "wrapper.bin",
        new BebopRecord(
            "Wrapper",
            new BebopRecord("Bare", List.of(1, 2, 3), new byte[] {9}),
            List.of(7),
            new BebopUnion(2, new BebopRecord("Wrapper.Choice.B", "x"))));
  }

  /**
   * Nested messages, an inline message branch, arrays, fixed arrays and maps, and tag 200 (a block
   * directory). Upstream writes map entries in its hash table's order, so the general profile
   * reads and reproduces them, and the strict profile rejects the order as not canonical.
   */
  @Test
  void envelopeAndMapOrder() {
    general(TYPES, "golden.types.Envelope", "envelope.bin", envelope());
    byte[] bytes = upstream("values/envelope.bin");
    BebopCodec strict = BebopCodec.compile(TYPES, ref("golden.types.Envelope"), Fixtures.STRICT, BebopLimits.DEFAULT);
    BebopException e = assertThrows(BebopException.class, () -> strict.decode(bytes));
    assertEquals(Kind.MALFORMED, e.kind());
    assertTrue(e.getMessage().contains("ascending"), e.getMessage());
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> strict.validate(bytes)).kind());
    // The strict encoding of the same value differs only in the order of map entries.
    byte[] canonical = strict.encode(envelope());
    assertEquals(bytes.length, canonical.length);
    assertFalse(java.util.Arrays.equals(bytes, canonical));
    assertTrue(BebopValues.equal(envelope(), strict.decode(canonical)));
  }

  /** A flags value that is no declared member: the general profile keeps it. */
  @Test
  void flagsValue() {
    general(FLAGS, "golden.flags.File", "file.flags.bin", new BebopRecord("golden.flags.File", "a.txt", 5));
  }

  // --- golden.vectors (vectors.bop, gen/vectors.c) ---------------------------------------------

  private static final int[] DIRECTORY_TAGS = {1, 2, 3, 4, 5, 8, 9, 16, 17, 32, 33, 255};

  /** A Directory whose present byte fields hold their tags, and {@code pad} bytes (i & 0xFF). */
  private static BebopRecord directory(int pad, int... tags) {
    @Nullable Object[] v = new Object[DIRECTORY_TAGS.length + 1];
    for (int tag : tags)
      for (int i = 0; i < DIRECTORY_TAGS.length; i++) if (DIRECTORY_TAGS[i] == tag) v[i] = tag;
    if (pad >= 0) {
      byte[] bytes = new byte[pad];
      for (int i = 0; i < pad; i++) bytes[i] = (byte) i;
      v[DIRECTORY_TAGS.length] = bytes;
    }
    return new BebopRecord("golden.vectors.Directory", v);
  }

  /** Every directory kind and boundary width, as upstream chose them; the control byte says which. */
  @Test
  void everyDirectoryKindAndWidth() {
    String root = "golden.vectors.Directory";
    Object[][] cases = {
      {"dir.empty.bin", 0x00, directory(-1)},
      {"dir.tiny1.bin", 0x04, directory(-1, 9)},
      {"dir.tiny1.far.bin", 0x04, directory(-1, 255)},
      {"dir.tiny2.bin", 0x08, directory(-1, 9, 17)},
      {"dir.tiny3.bin", 0x0C, directory(-1, 9, 17, 32)},
      {"dir.mask8.bin", 0x10, directory(-1, 1, 2, 3, 4)},
      {"dir.mask16.bin", 0x14, directory(-1, 1, 2, 3, 16)},
      {"dir.mask32.bin", 0x18, directory(-1, 1, 2, 3, 32)},
      {"dir.blocks.bin", 0x1C, directory(-1, 1, 2, 3, 33)},
      {"dir.blocks.far.bin", 0x1C, directory(-1, 1, 2, 3, 255)},
      {"width1.bin", 0x10, directory(10, 1, 2)},
      {"width2.bin", 0x11, directory(300, 1, 2)},
      {"width4.bin", 0x12, directory(70000, 1, 2)},
      {"width2.single.bin", 0x11, directory(300)},
    };
    for (Object[] c : cases) {
      String file = (String) c[0];
      byte[] bytes = upstream("values/" + file);
      assertEquals((int) c[1], bytes[bytes.length - 1] & 0xFF, file);
      canonical(VECTORS, root, file, c[2]);
    }
  }

  private static final BebopTimestamp TMAX = new BebopTimestamp(Long.MAX_VALUE, 999_999_999, 86_400_000);
  private static final BebopTimestamp TMIN = new BebopTimestamp(Long.MIN_VALUE, 0, -86_400_000);
  private static final BebopTimestamp TZERO = new BebopTimestamp(0, 0, 0);
  private static final BebopDuration DMAX = new BebopDuration(Long.MAX_VALUE, 999_999_999);
  private static final BebopDuration DMIN = new BebopDuration(Long.MIN_VALUE, -999_999_999);
  private static final BebopDuration DZERO = new BebopDuration(0, 0);

  /** Timestamps (16 bytes) and durations as fields, in dynamic and fixed arrays, and before a sentinel. */
  @Test
  void timestampsAndDurations() {
    String root = "golden.vectors.Times";
    String stamped = "golden.vectors.Stamped";
    canonical(
        VECTORS,
        root,
        "times.fields.bin",
        new BebopRecord(root, new BebopTimestamp(1_700_000_000L, 5, 0), new BebopDuration(3, 4), null, null, null, null, null));
    canonical(
        VECTORS,
        root,
        "times.extremes.bin",
        new BebopRecord(
            root,
            TMIN,
            DMIN,
            List.of(TMAX, TMIN, TZERO),
            List.of(DMAX, DMIN, DZERO),
            List.of(TMAX, TMIN),
            List.of(DMAX, DMIN),
            new BebopRecord(stamped, new BebopTimestamp(Long.MIN_VALUE, 999_999_999, 86_400_000), new BebopDuration(-1, -1), 0x7EADBEEF)));
    canonical(
        VECTORS,
        root,
        "times.stamped.bin",
        new BebopRecord(root, null, null, null, null, null, null, new BebopRecord(stamped, TMAX, DMAX, -2)));
  }
}
