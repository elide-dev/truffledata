package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;
import static dev.elide.truffledata.bebop.BebopType.ref;
import static dev.elide.truffledata.bebop.Fixtures.general;
import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.bebop.BebopException.Kind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bulk views against decode-then-project, the correctness reference. */
final class BebopViewTest {
  private static final BebopCodec TABLE = general(ref("Table"));

  private static BebopRecord table(int rows, boolean names) {
    List<Object> structs = new ArrayList<>();
    List<Object> strings = new ArrayList<>();
    List<Object> ids = new ArrayList<>();
    List<Object> small = new ArrayList<>();
    byte[] blob = new byte[rows];
    for (int i = 0; i < rows; i++) {
      structs.add(new BebopRecord("Row", 0xFFFFFF00L + i, i % 3 == 0 ? Double.NaN : -0.5 * i, i % 2 == 1));
      strings.add("n" + "x".repeat(i % 5));
      ids.add(Long.MIN_VALUE + i);
      small.add(-i);
      blob[i] = (byte) (200 + i);
    }
    return new BebopRecord("Table", structs, names ? strings : null, ids, blob, small);
  }

  @Test
  void columnsMatchDecodeThenProject() {
    for (int rows : new int[] {0, 1, 2, 7, 64, 65}) {
      BebopRecord value = table(rows, true);
      {
        BebopDocument doc = TABLE.open(TABLE.encode(value));
        BebopSpan id = doc.span("rows", "id");
        BebopSpan score = doc.span("rows", "score");
        BebopSpan flag = doc.span("rows", "flag");
        BebopSpan ids = doc.span("ids");
        BebopSpan small = doc.span("small");
        BebopSpan blob = doc.span("blob");
        BebopOffsets names = doc.strings("names");
        assertNotNull(id);
        assertNotNull(score);
        assertNotNull(flag);
        assertNotNull(ids);
        assertNotNull(small);
        assertNotNull(blob);
        assertNotNull(names);
        assertEquals(rows, id.count());
        assertEquals(13, id.stride());
        assertEquals(4, id.width());
        assertEquals(UINT32, id.kind());
        assertEquals(8, ids.stride());
        assertEquals(rows == 0 ? 0 : (rows - 1) * 13 + 8, score.byteLength());
        assertEquals(id.byteOffset() + 4, score.byteOffset());
        assertEquals(TABLE.schemaIdentity(), doc.schemaIdentity());
        List<?> structs = (List<?>) value.get(0);
        long[] copied = new long[rows];
        ids.copyLongs(0, copied, 0, rows);
        double[] scores = new double[rows];
        score.copyDoubles(0, scores, 0, rows);
        for (int i = 0; i < rows; i++) {
          BebopRecord row = (BebopRecord) structs.get(i);
          assertEquals(row.get(0), id.getLong(i)); // high-bit unsigned stays positive
          assertEquals(
              Double.doubleToRawLongBits((Double) row.get(1)), score.bits(i)); // NaN and signed zero by bits
          assertEquals(Double.doubleToRawLongBits((Double) row.get(1)), Double.doubleToRawLongBits(scores[i]));
          assertEquals((Boolean) row.get(2) ? 1 : 0, flag.getLong(i));
          assertEquals(Long.MIN_VALUE + i, copied[i]);
          assertEquals(-i, small.getLong(i));
          assertEquals((200 + i) & 0xFF, blob.getLong(i));
          assertEquals("n" + "x".repeat(i % 5), names.string(i));
          assertTrue(names.byteOffset(i) >= names.dataStart());
          assertTrue(names.byteOffset(i) + names.byteLength(i) < names.dataEnd());
        }
        assertThrows(IndexOutOfBoundsException.class, () -> id.getLong(rows));
        assertThrows(IndexOutOfBoundsException.class, () -> id.getLong(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.copyLongs(0, new long[rows + 1], 0, rows + 1));
        if (rows > 0) assertThrows(UnsupportedOperationException.class, () -> score.getLong(0));
      }
    }
  }

  @Test
  void presenceIsNotASentinel() {
    BebopDocument doc = TABLE.open(TABLE.encode(table(3, false)));
    assertNull(doc.strings("names"));
    assertNotNull(doc.span("ids"));
    BebopDocument empty = TABLE.open(TABLE.encode(new BebopRecord("Table", null, null, null, null, null)));
    assertNull(empty.span("rows", "id"));
    assertNull(empty.span("blob"));
  }

  @Test
  void viewsKeepTheirBytes() {
    byte[] bytes = TABLE.encode(table(4, true));
    BebopDocument copied = TABLE.open(bytes);
    BebopSpan ids = copied.span("ids");
    BebopOffsets names = copied.strings("names");
    assertNotNull(ids);
    assertNotNull(names);
    assertSame(copied, ids.document());
    assertSame(copied, names.document());
    assertSame(TABLE, copied.codec());
    assertEquals(bytes.length, copied.size());
    assertTrue(BebopValues.equal(table(4, true), copied.decode()));
    // open() copies: later writes to the caller's array are not seen.
    long before = ids.getLong(2);
    String name = names.string(3);
    byte[] content = new byte[names.byteLength(3)];
    names.copyBytes(3, content, 0);
    assertEquals(name, new String(content, java.nio.charset.StandardCharsets.UTF_8));
    java.util.Arrays.fill(bytes, (byte) 0);
    assertEquals(before, ids.getLong(2));
    assertEquals(name, names.string(3));
    // adopt() takes the array itself.
    byte[] owned = TABLE.encode(table(4, true));
    BebopSpan adopted = TABLE.adopt(owned).span("ids");
    assertNotNull(adopted);
    assertEquals(before, adopted.getLong(2));
    byte[] raw = new byte[8];
    adopted.copyBytes(2, raw, 0);
    assertEquals(before, java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong());
  }

  @Test
  void onlyValidatedBytesGetViews() {
    byte[] bytes = TABLE.encode(table(4, true));
    for (int cut = 1; cut < bytes.length; cut += 7) {
      byte[] truncated = java.util.Arrays.copyOf(bytes, bytes.length - cut);
      assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> TABLE.open(truncated)).kind());
    }
    BebopCodec ints = general(BebopType.array(INT32));
    // The count's product with the width overflows 32 bits; it is rejected before any view exists.
    assertEquals(
        Kind.MALFORMED,
        assertThrows(BebopException.class, () -> ints.open(Fixtures.hex("00000040 01000000"))).kind());
  }

  @Test
  void pathsAreChecked() {
    {
      BebopDocument doc = TABLE.open(TABLE.encode(table(2, true)));
      assertThrows(IllegalArgumentException.class, () -> doc.span("nope"));
      assertThrows(IllegalArgumentException.class, () -> doc.span("rows"));
      assertThrows(IllegalArgumentException.class, () -> doc.span("rows", "nope"));
      assertThrows(IllegalArgumentException.class, () -> doc.span("names"));
      assertThrows(IllegalArgumentException.class, () -> doc.strings("ids"));
      assertThrows(IllegalArgumentException.class, () -> doc.span());
    }
    // A root array, at an odd offset inside nothing but its own bytes.
    BebopCodec floats = general(BebopType.array(FLOAT32));
    {
      BebopDocument doc = floats.open(floats.encode(List.of(1.5f, -0.0f, Float.NaN)));
      BebopSpan span = doc.span();
      assertNotNull(span);
      assertEquals(3, span.count());
      assertEquals(1.5, span.getDouble(0));
      assertEquals(Float.floatToRawIntBits(-0.0f), (int) span.bits(1));
      assertTrue(Double.isNaN(span.getDouble(2)));
    }
  }

  /** Timestamps are 16 packed bytes wherever they sit: lengths, strides and following offsets. */
  @Test
  void temporalLayout() {
    BebopSchema schema =
        BebopSchema.builder()
            .struct(
                "Stamped",
                new BebopSchema.Field("at", TIMESTAMP),
                new BebopSchema.Field("sentinel", UINT32),
                new BebopSchema.Field("span", DURATION),
                new BebopSchema.Field("tail", BYTE))
            .message(
                "Log",
                new BebopSchema.TaggedField(1, "lead", BYTE),
                new BebopSchema.TaggedField(2, "entries", BebopType.array(ref("Stamped"))),
                new BebopSchema.TaggedField(3, "times", BebopType.array(TIMESTAMP)),
                new BebopSchema.TaggedField(4, "pair", BebopType.fixed(BebopType.utcInstant(), 2)),
                new BebopSchema.TaggedField(5, "spans", BebopType.array(DURATION)),
                new BebopSchema.TaggedField(6, "at", TIMESTAMP))
            .build();
    BebopCodec codec = BebopCodec.compile(schema, ref("Log"), Fixtures.STRICT, BebopLimits.DEFAULT);
    BebopTimestamp a = new BebopTimestamp(Long.MIN_VALUE, 999_999_999, -86_400_000);
    BebopTimestamp b = new BebopTimestamp(Long.MAX_VALUE, 0, 86_400_000);
    BebopTimestamp utc = new BebopTimestamp(7, 8, 0);
    BebopDuration d = new BebopDuration(-9, -10);
    BebopRecord value =
        new BebopRecord(
            "Log",
            1, // a one-byte lead field leaves everything after it at odd offsets
            List.of(new BebopRecord("Stamped", a, 0xDEADBEEFL, d, 0xAB), new BebopRecord("Stamped", b, 0xCAFEF00DL, d, 0xCD)),
            List.of(a, b, a),
            List.of(utc, utc),
            List.of(d, d),
            b);
    byte[] bytes = codec.encode(value);
    // lead 1; entries 4 + 2 * (16 + 4 + 12 + 1); times 4 + 3 * 16; pair 2 * 16; spans 4 + 2 * 12; at 16;
    // five 1-byte boundaries, a Mask 8 directory and the control byte, behind the 4-byte length.
    assertEquals(4 + 1 + (4 + 2 * 33) + (4 + 48) + 32 + (4 + 24) + 16 + 5 + 1 + 1, bytes.length);
    BebopDocument doc = codec.adopt(bytes);
    assertTrue(BebopValues.equal(value, doc.decode()));
    BebopSpan seconds = java.util.Objects.requireNonNull(doc.span("entries", "at"));
    BebopSpan sentinel = java.util.Objects.requireNonNull(doc.span("entries", "sentinel"));
    BebopSpan span = java.util.Objects.requireNonNull(doc.span("entries", "span"));
    BebopSpan tail = java.util.Objects.requireNonNull(doc.span("entries", "tail"));
    assertEquals(TIMESTAMP, seconds.kind());
    assertEquals(16, seconds.width());
    assertEquals(33, seconds.stride());
    assertEquals(1, seconds.byteOffset() % 2); // unaligned by construction
    assertEquals(seconds.byteOffset() + 16, sentinel.byteOffset());
    assertEquals(sentinel.byteOffset() + 4, span.byteOffset());
    assertEquals(span.byteOffset() + 12, tail.byteOffset());
    assertEquals(0xDEADBEEFL, sentinel.getLong(0));
    assertEquals(0xCAFEF00DL, sentinel.getLong(1));
    assertEquals(0xCD, tail.getLong(1));
    assertEquals(12, span.width());
    byte[] raw = new byte[16];
    seconds.copyBytes(1, raw, 0);
    assertEquals("ffffffffffffff7f00000000005c2605", Fixtures.hex(raw));
    BebopSpan times = java.util.Objects.requireNonNull(doc.span("times"));
    assertEquals(3, times.count());
    assertEquals(16, times.stride());
    assertEquals(48, times.byteLength());
    BebopSpan pair = java.util.Objects.requireNonNull(doc.span("pair"));
    assertEquals(2, pair.count());
    assertEquals(16, pair.stride());
    assertEquals(12, java.util.Objects.requireNonNull(doc.span("spans")).stride());
    // The same values through guest rows.
    BebopRow row = doc.row();
    BebopRow second = (BebopRow) ((BebopArray) row.get(1)).get(1);
    assertEquals(Long.MAX_VALUE, ((BebopRow) second.get(0)).get(0));
    assertEquals(86_400_000L, ((BebopRow) second.get(0)).get(2));
    assertEquals(0xCAFEF00DL, second.get(1));
    assertEquals(-10L, ((BebopRow) second.get(2)).get(1));
    assertEquals(0L, ((BebopRow) ((BebopArray) row.get(3)).get(1)).get(2));
    // Any truncation, including every cut inside the final timestamp field, is rejected.
    for (int cut = 1; cut <= 40; cut++) {
      byte[] shorter = java.util.Arrays.copyOf(bytes, bytes.length - cut);
      assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> codec.validate(shorter)).kind());
      assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> codec.decode(shorter)).kind());
    }
    // A bare timestamp cut to 12 through 15 bytes, and a duration cut to 8 through 11.
    BebopCodec stamp = Fixtures.strict(TIMESTAMP);
    BebopCodec lapse = Fixtures.strict(DURATION);
    for (int n = 12; n < 16; n++) {
      int length = n;
      assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> stamp.validate(new byte[16], 0, length)).kind());
      assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> stamp.decode(new byte[16], 0, length)).kind());
      assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> lapse.validate(new byte[12], 0, length - 4)).kind());
    }
    assertEquals(16, stamp.encode(a).length);
    assertEquals(12, lapse.encode(d).length);
    // A UTC-instant element with an offset is rejected in place, inside the fixed array.
    byte[] shifted = bytes.clone();
    int offsetField = pair.byteOffset() + 16 + 12;
    shifted[offsetField] = 1;
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> codec.validate(shifted)).kind());
    assertEquals(
        "Timestamp has an offset under a UTC-instant schema",
        assertThrows(BebopException.class, () -> codec.decode(shifted)).getMessage());
  }
}
