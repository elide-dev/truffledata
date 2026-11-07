package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.AnyProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.SourceContext;
import com.google.protobuf.SourceContextProto;
import com.google.protobuf.Syntax;
import com.google.protobuf.Type;
import com.google.protobuf.TypeProto;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.Layout.SlotKind;
import dev.elide.truffledata.protobuf.ProtoPlan.FieldPlan;
import dev.elide.truffledata.protobuf.ProtoPlan.Kind;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Task A1: the layout compiler, the TDL1 program and the TDSLAB1 golden slabs (docs/SLAB.md). */
class LayoutTest {
  private static final Limits LIMITS = new Limits(1024, 256, 1 << 22, 64L << 20);

  // ---- Schema construction ----

  static Object defaultValue(Kind kind) {
    return switch (kind) {
      case DOUBLE, FLOAT -> 0.0;
      case BOOL -> false;
      case STRING -> "";
      case BYTES -> DataArray.copyOfBytes(new byte[0]);
      case MESSAGE, GROUP -> Missing.INSTANCE;
      case UINT64, FIXED64 -> Values.normalize(java.math.BigInteger.ZERO);
      default -> 0L;
    };
  }

  static FieldPlan scalar(String name, int number, Kind kind, boolean explicit) {
    return new FieldPlan(
        name, number, kind, false, false, explicit, false, false, -1, "", null, defaultValue(kind));
  }

  static FieldPlan required(String name, int number, Kind kind) {
    return new FieldPlan(
        name, number, kind, false, false, true, true, false, -1, "", null, defaultValue(kind));
  }

  static FieldPlan repeated(String name, int number, Kind kind, boolean packed) {
    return new FieldPlan(
        name, number, kind, true, false, false, false, packed, -1, "", null, defaultValue(kind));
  }

  static FieldPlan message(String name, int number, String type, boolean repeated) {
    return new FieldPlan(
        name,
        number,
        Kind.MESSAGE,
        repeated,
        false,
        !repeated,
        false,
        false,
        -1,
        type,
        null,
        Missing.INSTANCE);
  }

  static FieldPlan group(String name, int number, String type) {
    return new FieldPlan(
        name,
        number,
        Kind.GROUP,
        false,
        false,
        true,
        false,
        false,
        -1,
        type,
        null,
        Missing.INSTANCE);
  }

  static FieldPlan map(String name, int number, String entry) {
    return new FieldPlan(
        name,
        number,
        Kind.MESSAGE,
        true,
        true,
        false,
        false,
        false,
        -1,
        entry,
        null,
        Missing.INSTANCE);
  }

  static FieldPlan member(String name, int number, Kind kind, String type, int oneof) {
    return new FieldPlan(
        name,
        number,
        kind,
        false,
        false,
        true,
        false,
        false,
        oneof,
        type,
        null,
        defaultValue(kind));
  }

  static FieldPlan enumeration(
      String name, int number, ProtoPlan.EnumType type, boolean repeated, boolean packed) {
    return new FieldPlan(
        name, number, Kind.ENUM, repeated, false, !repeated, false, packed, -1, "", type, 0L);
  }

  static ProtoPlan.Type type(String name, FieldPlan... fields) {
    return new ProtoPlan.Type(name, false, List.of(fields));
  }

  static ProtoPlan.Type entry(String name, FieldPlan key, FieldPlan value) {
    return new ProtoPlan.Type(name, true, List.of(key, value));
  }

  static ProtoPlan.Model model(ProtoPlan.Type... types) {
    return new ProtoPlan.Model(null, List.of(types), LIMITS, 32, 1 << 24);
  }

  static final ProtoPlan.EnumType COLOR =
      new ProtoPlan.EnumType("Color", false, Map.of("RED", 0, "GREEN", 1));

  // ---- Golden schemas (also the schemas Tasks A2/A3 decode the golden wires with) ----

  static ProtoPlan timestamp() {
    return model(
            type(
                "Timestamp",
                scalar("seconds", 1, Kind.INT64, false),
                scalar("nanos", 2, Kind.INT32, false)))
        .type("Timestamp");
  }

  static ProtoPlan choice() {
    return model(
            type(
                "Choice",
                member("a", 1, Kind.INT32, "", 0),
                member("b", 2, Kind.STRING, "", 0),
                member("c", 3, Kind.MESSAGE, "Child", 0),
                member("d", 4, Kind.INT64, "", 1),
                member("e", 5, Kind.BOOL, "", 1),
                member("f", 6, Kind.BYTES, "", 1),
                scalar("plain", 7, Kind.INT32, false)),
            type("Child", scalar("x", 1, Kind.INT32, false)))
        .type("Choice");
  }

  static ProtoPlan unknowns() {
    return model(
            type(
                "Unknowns",
                scalar("known", 1, Kind.INT32, true),
                enumeration("color", 2, COLOR, false, false),
                enumeration("colors", 3, COLOR, true, true)))
        .type("Unknowns");
  }

  static ProtoPlan merge() {
    return model(
            type("Outer", message("inner", 1, "Inner", false)),
            type(
                "Inner",
                scalar("a", 1, Kind.INT32, false),
                repeated("r", 2, Kind.INT32, true),
                scalar("s", 3, Kind.STRING, false)))
        .type("Outer");
  }

  static ProtoPlan maps() {
    return model(
            type("MapHolder", map("counts", 1, "CountsEntry"), map("children", 2, "ChildrenEntry")),
            entry(
                "CountsEntry",
                scalar("key", 1, Kind.STRING, false),
                scalar("value", 2, Kind.INT32, false)),
            entry(
                "ChildrenEntry",
                scalar("key", 1, Kind.INT64, false),
                message("value", 2, "Child", false)),
            type("Child", scalar("x", 1, Kind.INT32, false)))
        .type("MapHolder");
  }

  static ProtoPlan lists() {
    return model(
            type(
                "Lists",
                repeated("i32", 1, Kind.INT32, true),
                repeated("i64", 2, Kind.INT64, false),
                repeated("f64", 3, Kind.DOUBLE, true),
                repeated("flags", 4, Kind.BOOL, true),
                repeated("fx", 5, Kind.FIXED32, false),
                repeated("z", 6, Kind.SINT64, true),
                repeated("f32", 7, Kind.FLOAT, true),
                repeated("u64", 8, Kind.UINT64, true)))
        .type("Lists");
  }

  static ProtoPlan texts() {
    return model(
            type(
                "Texts",
                scalar("ascii", 1, Kind.STRING, false),
                scalar("latin", 2, Kind.STRING, false),
                scalar("cjk", 3, Kind.STRING, false),
                scalar("emoji", 4, Kind.STRING, false),
                scalar("empty", 5, Kind.STRING, true),
                repeated("many", 6, Kind.STRING, false),
                scalar("blob", 7, Kind.BYTES, false),
                repeated("blobs", 8, Kind.BYTES, false),
                scalar("implicit", 9, Kind.STRING, false),
                scalar("zero", 10, Kind.INT32, false)))
        .type("Texts");
  }

  static ProtoPlan wide() {
    FieldPlan[] fields = new FieldPlan[130];
    for (int i = 0; i < fields.length; i++)
      fields[i] = scalar("f" + i, i + 1, i % 2 == 0 ? Kind.INT32 : Kind.INT64, true);
    return model(type("Wide", fields)).type("Wide");
  }

  static ProtoPlan legacy() {
    return model(
            type(
                "Legacy",
                required("id", 1, Kind.INT32),
                group("g", 2, "Legacy.G"),
                message("next", 4, "Legacy", false),
                scalar("ratio", 5, Kind.FLOAT, true),
                scalar("big", 6, Kind.UINT64, true)),
            type("Legacy.G", scalar("x", 3, Kind.SINT32, true)))
        .type("Legacy");
  }

  static ProtoPlan googleType() {
    byte[] descriptors =
        FileDescriptorSet.newBuilder()
            .addFile(AnyProto.getDescriptor().toProto())
            .addFile(SourceContextProto.getDescriptor().toProto())
            .addFile(TypeProto.getDescriptor().toProto())
            .build()
            .toByteArray();
    return ProtoDescriptors.compile(null, descriptors, "google.protobuf.Type", LIMITS, 32, 1 << 24);
  }

  /** Message 1 of the shared consumer fixture (benchmarks/comparison/ConsumerComparison.java). */
  static byte[] consumerMessage1() {
    int i = 1;
    var b = Type.newBuilder().setName("catalog-" + i + "-λ").setSyntax(Syntax.SYNTAX_PROTO3);
    int count = 3 + i % 6;
    b.setSourceContext(SourceContext.newBuilder().setFileName("schema-" + i + ".proto"));
    b.addOneofs("identity");
    for (int j = 0; j < count; j++)
      b.addFields(
          com.google.protobuf.Field.newBuilder()
              .setName("column-" + j)
              .setNumber(j + 1)
              .setKind(com.google.protobuf.Field.Kind.TYPE_INT64)
              .setCardinality(com.google.protobuf.Field.Cardinality.CARDINALITY_REPEATED)
              .setPacked((i + j) % 3 != 0));
    return b.build().toByteArray();
  }

  // ---- Wire construction ----

  static final class Wire {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    Wire varint(long value) {
      while ((value & ~127L) != 0) {
        out.write((int) (value & 127) | 128);
        value >>>= 7;
      }
      out.write((int) value);
      return this;
    }

    Wire tag(int number, int wire) {
      return varint(((long) number << 3) | wire);
    }

    Wire field(int number, long value) {
      return tag(number, 0).varint(value);
    }

    Wire fixed32(int number, int value) {
      tag(number, 5);
      for (int i = 0; i < 4; i++) out.write(value >>> (8 * i));
      return this;
    }

    Wire fixed64(int number, long value) {
      tag(number, 1);
      for (int i = 0; i < 8; i++) out.write((int) (value >>> (8 * i)));
      return this;
    }

    Wire bytes(int number, byte[] value) {
      tag(number, 2).varint(value.length);
      out.writeBytes(value);
      return this;
    }

    Wire string(int number, String value) {
      return bytes(number, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    Wire message(int number, Wire value) {
      return bytes(number, value.bytes());
    }

    byte[] bytes() {
      return out.toByteArray();
    }
  }

  static Wire wire() {
    return new Wire();
  }

  /** Packed payload of fixed-width or varint values, written by {@code body}. */
  static byte[] packed(java.util.function.Consumer<Wire> body) {
    Wire w = wire();
    body.accept(w);
    return w.bytes();
  }

  record Golden(String name, ProtoPlan plan, byte[] wire, int @Nullable [] words) {}

  /** Placeholder for the layout fingerprint in hand-written header words. */
  private static final int FP = 0xF1F1F1F1;

  static List<Golden> goldens() {
    List<Golden> goldens = new ArrayList<>();
    goldens.add(
        new Golden(
            "timestamp",
            timestamp(),
            wire().field(1, 1_700_000_000L).field(2, 123).bytes(),
            new int[] {
              MAGIC,
              OWNING | CERTIFIED,
              64,
              1,
              1,
              32,
              FP,
              0,
              // root: presence {seconds, nanos}, handle 0, no unknown, seconds (u64), nanos, pad
              3,
              0,
              0,
              0,
              1_700_000_000,
              0,
              123,
              0
            }));
    goldens.add(
        new Golden(
            "type",
            googleType(),
            consumerMessage1(),
            null)); // Checked field by field against ProtoRow and the checked-in bytes.
    goldens.add(
        new Golden(
            "map",
            maps(),
            wire()
                .message(1, wire().string(1, "a").field(2, 1))
                .message(1, wire().string(1, "b").field(2, 2))
                .message(1, wire().string(1, "a").field(2, 3))
                .message(2, wire().field(1, 1).message(2, wire().field(1, 4)))
                .message(2, wire().field(1, 2))
                .bytes(),
            new int[] {
              MAGIC,
              OWNING | CERTIFIED,
              320,
              8,
              11,
              32,
              FP,
              0,
              // 32 MapHolder: {counts, children}, handle 0, counts list 288, children list 304
              3,
              0,
              0,
              0,
              288,
              304,
              // 56 CountsEntry "a" → 1 (handle 1; key header 80)
              3,
              0,
              1,
              0,
              80,
              1,
              // 80 "a" (ASCII, 1 unit, handle 2), payload padded to 4
              0x80000001,
              1,
              2,
              'a',
              // 96 CountsEntry "b" → 2, 120 "b"
              3,
              0,
              3,
              0,
              120,
              2,
              0x80000001,
              1,
              4,
              'b',
              // 136 CountsEntry "a" → 3 (the surviving entry for key "a"), 160 "a"
              3,
              0,
              5,
              0,
              160,
              3,
              0x80000001,
              1,
              6,
              'a',
              // 176 ChildrenEntry 1 → child 208 (key u64, value offset, pad)
              3,
              0,
              7,
              0,
              1,
              0,
              208,
              0,
              // 208 Child {x: 4}
              1,
              0,
              8,
              0,
              4,
              0,
              // 232 ChildrenEntry 2 with no value: an empty Child is written at 264
              3,
              0,
              9,
              0,
              2,
              0,
              264,
              0,
              // 264 empty Child
              0,
              0,
              10,
              0,
              0,
              0,
              // 288 counts: first-key order, last entry per key; 304 children
              2,
              0,
              136,
              96,
              2,
              0,
              176,
              232
            }));
    goldens.add(
        new Golden(
            "oneof",
            choice(),
            wire()
                .field(1, 5)
                .string(2, "x")
                .field(4, 9)
                .message(3, wire().field(1, 7))
                .field(5, 1)
                .field(7, 0)
                .bytes(),
            new int[] {
              MAGIC,
              OWNING | CERTIFIED,
              128,
              2,
              3,
              32,
              FP,
              0,
              // 32 Choice: presence {c, e}, handle 0, cases (first = c: ordinal 2 + 1,
              // second = e: 4 + 1), case padding, d (cleared u64), a, b (cleared), c, e, f, plain
              0b10100,
              0,
              0,
              0,
              0x0005_0003,
              0,
              0,
              0,
              0,
              0,
              104,
              1,
              0,
              0,
              // 88 orphaned "x" (b was cleared by c), handle 1
              0x80000001,
              1,
              1,
              'x',
              // 104 Child {x: 7}, handle 2
              1,
              0,
              2,
              0,
              7,
              0
            }));
    goldens.add(
        new Golden(
            "unknown",
            unknowns(),
            wire()
                .field(99, 1)
                .field(1, 7)
                .string(100, "zz")
                .field(2, 5)
                .bytes(3, new byte[] {1, 7, 0})
                .fixed32(101, 0x04030201)
                .bytes(),
            new int[] {
              MAGIC,
              OWNING | CERTIFIED,
              104,
              1,
              1,
              32,
              FP,
              0,
              // 32 Unknowns: {known, colors}, handle 0, unknown span 80, known, color, colors list
              0b101,
              0,
              0,
              80,
              7,
              0,
              64,
              0,
              // 64 colors [1, 0] (7 is not a Color)
              2,
              0,
              1,
              0,
              // 80 unknown span: 18 bytes in wire order — field 99, field 100, color 5 verbatim,
              // the packed 7 re-encoded as a varint field (18 07), field 101
              18,
              0xA2010698,
              0x7A7A0206,
              0x07180510,
              0x020106AD,
              0x00000403
            }));
    goldens.add(
        new Golden(
            "merge",
            merge(),
            wire()
                .message(1, wire().field(1, 1).field(2, 1).string(3, "x"))
                .message(1, wire().field(2, 2).string(3, "yy"))
                .bytes(),
            new int[] {
              MAGIC,
              OWNING,
              200,
              3,
              6,
              32,
              FP,
              0,
              // 32 Outer {inner = the merged record at 120}
              1,
              0,
              0,
              0,
              120,
              0,
              // 56 first Inner (orphaned once merged): {a: 1, r: list 104, s: "x" at 88}
              7,
              0,
              1,
              0,
              1,
              104,
              88,
              0,
              // 88 "x"; 104 r [1]
              0x80000001,
              1,
              2,
              'x',
              1,
              0,
              1,
              0,
              // 120 merged Inner, decoded from both payloads: a 1, r [1, 2] at 184, s "yy" at 168
              7,
              0,
              3,
              0,
              1,
              184,
              168,
              0,
              // 152 "x" (overwritten), 168 "yy"
              0x80000001,
              1,
              4,
              'x',
              0x80000002,
              2,
              5,
              'y' | 'y' << 8,
              // 184 r [1, 2]
              2,
              0,
              1,
              2
            }));
    goldens.add(
        new Golden(
            "lists",
            lists(),
            wire()
                .bytes(1, packed(w -> w.varint(1).varint(-1L).varint(300)))
                .field(1, 7)
                .field(2, Long.MIN_VALUE)
                .field(2, 42)
                .bytes(3, packed(w -> w.out.writeBytes(longBytes(Double.doubleToLongBits(1.5)))))
                .fixed64(3, Double.doubleToLongBits(-0.0))
                .bytes(4, new byte[] {1, 0, 2})
                .fixed32(5, -1)
                .bytes(6, packed(w -> w.varint(1).varint(2)))
                .field(6, 3)
                .bytes(7, packed(w -> w.out.writeBytes(intBytes(Float.floatToIntBits(0.25f)))))
                .bytes(8, packed(w -> w.varint(-1L)))
                .bytes(),
            null));
    goldens.add(
        new Golden(
            "strings",
            texts(),
            wire()
                .string(1, "plain")
                .string(2, "café")
                .string(3, "数据")
                .string(4, "a😀")
                .string(5, "")
                .string(6, "x")
                .string(6, "λ")
                .bytes(7, new byte[] {(byte) 0xff, 0})
                .bytes(8, new byte[0])
                .bytes(8, new byte[] {65})
                .string(9, "")
                .field(10, 0)
                .bytes(),
            null));
    Wire wide = wire();
    for (int ordinal : new int[] {0, 63, 64, 127, 129})
      wide.field(ordinal + 1, ordinal % 2 == 0 ? ordinal : -ordinal);
    goldens.add(new Golden("wide", wide(), wide.bytes(), null));
    goldens.add(
        new Golden(
            "legacy",
            legacy(),
            wire()
                .field(1, 1)
                .tag(2, 3)
                .field(3, 9)
                .tag(2, 4)
                .message(4, wire().field(1, 2).tag(2, 3).field(3, -3 & 0xffffffffL).tag(2, 4))
                .fixed32(5, Float.floatToIntBits(Float.NaN))
                .field(6, -1L)
                .bytes(),
            null));
    return goldens;
  }

  static byte[] longBytes(long value) {
    byte[] out = new byte[8];
    for (int i = 0; i < 8; i++) out[i] = (byte) (value >>> (8 * i));
    return out;
  }

  static byte[] intBytes(int value) {
    byte[] out = new byte[4];
    for (int i = 0; i < 4; i++) out[i] = (byte) (value >>> (8 * i));
    return out;
  }

  static byte[] words(int[] words, int fingerprint) {
    byte[] out = new byte[4 * words.length];
    for (int i = 0; i < words.length; i++)
      putInt(out, 4 * i, words[i] == FP && i == HEADER_LAYOUT / 4 ? fingerprint : words[i]);
    return out;
  }

  static Path resources() {
    String srcdir = System.getenv("TEST_SRCDIR"), workspace = System.getenv("TEST_WORKSPACE");
    if (srcdir != null && workspace != null)
      return Path.of(srcdir, workspace, "protobuf-runtime/src/test/resources/slab");
    return Path.of("protobuf-runtime/src/test/resources/slab");
  }

  // ---- Tests ----

  @Test
  void offsets_are_aligned_and_disjoint() {
    Random random = new Random(0x51AB1L);
    Kind[] kinds = Kind.values();
    for (int schema = 0; schema < 200; schema++) {
      int count = random.nextInt(schema % 10 == 0 ? 200 : 40);
      int oneofs = random.nextInt(4);
      List<FieldPlan> fields = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        Kind kind = kinds[random.nextInt(kinds.length)];
        String typeName = kind.message() ? "T" : "";
        int shape = random.nextInt(10);
        if (kind == Kind.ENUM) kind = Kind.INT32;
        if (shape < 3 && kind != Kind.GROUP)
          fields.add(
              new FieldPlan(
                  "f" + i,
                  i + 1,
                  kind,
                  true,
                  false,
                  false,
                  false,
                  false,
                  -1,
                  typeName,
                  null,
                  defaultValue(kind)));
        else if (shape < 5 && oneofs > 0)
          fields.add(member("f" + i, i + 1, kind, typeName, random.nextInt(oneofs)));
        else if (shape == 5) fields.add(map("f" + i, i + 1, "E"));
        else
          fields.add(
              new FieldPlan(
                  "f" + i,
                  i + 1,
                  kind,
                  false,
                  false,
                  kind.message() || random.nextBoolean(),
                  false,
                  false,
                  -1,
                  typeName,
                  null,
                  defaultValue(kind)));
      }
      var plan =
          model(
                  new ProtoPlan.Type("T", false, fields),
                  entry("E", scalar("key", 1, Kind.STRING, false), message("value", 2, "T", false)))
              .type("T");
      Layout layout = plan.layout();
      for (int type = 0; type < layout.types(); type++) {
        int fieldsCount = layout.fields(type);
        int words = layout.presenceWords(type);
        assertEquals((fieldsCount + 63) / 64, words);
        assertEquals(8 * words, layout.handleOffset(type));
        assertEquals(8 * words + 4, layout.unknownOffset(type));
        int header = 8 * words + 8 + align8(2 * layout.oneofs(type));
        BitSet used = new BitSet();
        used.set(0, header);
        int last8 = header, first4 = Integer.MAX_VALUE, end = header;
        for (int ordinal = 0; ordinal < fieldsCount; ordinal++) {
          SlotKind kind = layout.kind(type, ordinal);
          int slot = layout.slot(type, ordinal);
          assertEquals(0, slot % kind.width, "aligned slot");
          assertTrue(used.get(slot, slot + kind.width).isEmpty(), "disjoint slot");
          used.set(slot, slot + kind.width);
          if (kind.width == 8) last8 = Math.max(last8, slot + 8);
          else first4 = Math.min(first4, slot);
          end = Math.max(end, slot + kind.width);
          assertEquals(SlotKind.of(layout.plan(type).field(ordinal)), kind);
        }
        assertTrue(last8 <= first4 || first4 == Integer.MAX_VALUE, "8-byte slots first");
        assertEquals(0, layout.recordBytes(type) % 8, "record size multiple of 8");
        assertEquals(align8(end), layout.recordBytes(type), "record size is the padded extent");
      }
      assertSame(layout, plan.layout());
    }
  }

  @Test
  void presence_words_scale_past_64_fields() {
    for (int[] c : new int[][] {{0, 0}, {1, 1}, {64, 1}, {65, 2}, {128, 2}, {130, 3}}) {
      FieldPlan[] fields = new FieldPlan[c[0]];
      for (int i = 0; i < fields.length; i++) fields[i] = scalar("f" + i, i + 1, Kind.BOOL, true);
      Layout layout = model(type("W", fields)).type("W").layout();
      assertEquals(c[1], layout.presenceWords(0), c[0] + " fields");
      assertEquals(8 * c[1], layout.handleOffset(0));
      if (c[0] > 0) assertEquals(8 * c[1] + 8, layout.slot(0, 0));
      assertEquals(align8(8 * c[1] + 8 + 4 * c[0]), layout.recordBytes(0));
    }
    // Presence bits address the right word past 64.
    Layout layout = wide().layout();
    byte[] slab = SlabReference.decode(layout, goldens().get(8).wire());
    for (int ordinal = 0; ordinal < 130; ordinal++)
      assertEquals(
          Set.of(0, 63, 64, 127, 129).contains(ordinal),
          present(slab, ROOT, ordinal),
          "bit " + ordinal);
    assertEquals(1L << 63 | 1L, getLong(slab, ROOT));
    assertEquals(1L << 63 | 1L, getLong(slab, ROOT + 8));
    assertEquals(2L, getLong(slab, ROOT + 16));
  }

  @Test
  void oneof_case_bytes_assigned() {
    Layout layout = choice().layout();
    assertEquals(2, layout.oneofs(0));
    assertEquals(0, layout.oneofs(1));
    assertEquals(8 + 8, layout.caseOffset(0, 0));
    assertEquals(8 + 8 + 2, layout.caseOffset(0, 1));
    assertThrows(ContractViolation.class, () -> layout.caseOffset(0, 2));
    int[] expected = {0, 0, 0, 1, 1, 1, Layout.NO_ONEOF};
    for (int ordinal = 0; ordinal < expected.length; ordinal++)
      assertEquals(expected[ordinal], layout.oneofIndex(0, ordinal));
    // Case entries (2 × u16) pad to 8 so the 8-byte slot of `d` stays aligned right after them.
    assertEquals(24, layout.slot(0, 3));
    assertEquals(SlotKind.I64, layout.kind(0, 3));
    assertEquals(32, layout.slot(0, 0));
    assertEquals(56, layout.recordBytes(0));
    assertEquals(1, layout.childType(0, 2));
    assertEquals(-1, layout.childType(0, 0));
    // Three oneofs of one member each (proto3 optional): three entries, still one padded word.
    Layout three =
        model(
                type(
                    "O",
                    member("x", 1, Kind.INT32, "", 0),
                    member("y", 2, Kind.INT32, "", 1),
                    member("z", 3, Kind.INT32, "", 2),
                    member("w", 4, Kind.INT32, "", 3),
                    member("v", 5, Kind.INT32, "", 4)))
            .type("O")
            .layout();
    assertEquals(5, three.oneofs(0));
    assertEquals(8 + 8 + 16, three.slot(0, 0));
  }

  @Test
  void layout_identity_stable() throws Exception {
    ProtoPlan plan = merge();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<Layout>> futures = new ArrayList<>();
      for (int i = 0; i < 64; i++) futures.add(pool.submit(plan::layout));
      Layout first = plan.layout();
      for (var future : futures) assertSame(first, future.get());
    } finally {
      pool.shutdownNow();
    }
    // Each plan roots its own closure; the nested plan's layout is a different instance.
    ProtoPlan inner = plan.nested(0);
    assertNotSame(plan.layout(), inner.layout());
    assertSame(inner, plan.layout().plan(1));
    assertSame(inner, inner.layout().plan(0));
    assertEquals(inner.layout().recordBytes(0), plan.layout().recordBytes(1));
    // Equal structure, equal program and fingerprint, distinct identity per plan.
    assertNotSame(merge().layout(), plan.layout());
    assertArrayEquals(merge().layout().program(), plan.layout().program());
  }

  @Test
  void program_round_trips() {
    for (ProtoPlan plan : List.of(timestamp(), choice(), maps(), wide(), legacy(), googleType())) {
      Layout layout = plan.layout();
      byte[] program = layout.program();
      assertEquals(Layout.PROGRAM_MAGIC, getInt(program, 0));
      assertEquals(Layout.PROGRAM_VERSION, getInt(program, 4));
      assertEquals(layout.types(), getInt(program, 8));
      assertEquals(layout.fingerprint(), getInt(program, 12));
      int expectedBytes = Layout.PROGRAM_HEADER_BYTES;
      for (int t = 0; t < layout.types(); t++) expectedBytes += 16 + 12 * layout.fields(t);
      assertEquals(expectedBytes, program.length);
      Layout.Program parsed = Layout.parse(program, 0);
      assertEquals(layout.tables(), parsed);
      assertEquals(layout.fingerprint(), parsed.fingerprint());
      // At an offset, as inside a schema program.
      byte[] shifted = new byte[program.length + 13];
      System.arraycopy(program, 0, shifted, 13, program.length);
      assertEquals(parsed, Layout.parse(shifted, 13));
      // Any changed table byte fails the fingerprint; a truncated section fails to parse.
      byte[] corrupt = program.clone();
      corrupt[Layout.PROGRAM_HEADER_BYTES + 4] ^= 1;
      assertThrows(ContractViolation.class, () -> Layout.parse(corrupt, 0));
      assertThrows(
          ContractViolation.class,
          () -> Layout.parse(Arrays.copyOf(program, program.length - 1), 0));
      assertThrows(ContractViolation.class, () -> Layout.parse(program, 4));
    }
    // First type's table, written out: Timestamp {seconds: I64 @16, nanos: I32 @24}, 32 bytes.
    byte[] program = timestamp().layout().program();
    int[] expected = {32, 1, 0, 2, 16, SlotKind.I64.code(), -1, 24, SlotKind.I32.code(), -1};
    for (int i = 0; i < expected.length; i++)
      assertEquals(expected[i], getInt(program, Layout.PROGRAM_HEADER_BYTES + 4 * i));
    assertEquals(SlotKind.MAP, SlotKind.of(SlotKind.MAP.code()));
    assertThrows(ContractViolation.class, () -> SlotKind.of(0));
    assertThrows(ContractViolation.class, () -> SlotKind.of(18));
  }

  @Test
  void golden_slabs_decode_by_hand() throws Exception {
    Path directory = resources();
    String regenerate = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    List<String> mismatched = new ArrayList<>();
    List<Golden> goldens = goldens();
    assertEquals(10, goldens.size());
    for (Golden golden : goldens) {
      Layout layout = golden.plan().layout();
      byte[] slab = SlabReference.decode(layout, golden.wire());
      // Every value, presence bit, oneof case and unknown span agrees with the Java codec.
      SlabReference.assertMatches(layout, slab, golden.plan().decode(golden.wire()));
      assertEquals(slab.length, getInt(slab, HEADER_BYTES_USED));
      assertEquals(0, slab.length % 8);
      if (golden.words() != null)
        assertArrayEquals(words(golden.words(), layout.fingerprint()), slab, golden.name());
      Path bin = directory.resolve(golden.name() + ".bin");
      Path input = directory.resolve(golden.name() + ".wire");
      boolean same =
          Files.exists(bin)
              && Files.exists(input)
              && Arrays.equals(Files.readAllBytes(bin), slab)
              && Arrays.equals(Files.readAllBytes(input), golden.wire());
      if (!same) {
        mismatched.add(golden.name());
        if (regenerate != null) {
          Files.write(Path.of(regenerate, golden.name() + ".bin"), slab);
          Files.write(Path.of(regenerate, golden.name() + ".wire"), golden.wire());
        }
      }
    }
    assertEquals(List.of(), mismatched, "golden slabs differ (new copies in undeclared outputs)");
    // Type message 1: spot checks of the format beyond the generic comparison.
    Golden type = goldens.get(1);
    Layout layout = type.plan().layout();
    byte[] slab = Files.readAllBytes(directory.resolve("type.bin"));
    int name = getInt(slab, ROOT + layout.slot(0, 0));
    assertEquals("catalog-1-λ".length(), utf16Length(slab, name));
    assertFalse(ascii(slab, name));
    assertEquals(
        "catalog-1-λ".getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
        stringLength(slab, name));
    int fields = getInt(slab, ROOT + layout.slot(0, 1));
    assertEquals(4, listCount(slab, fields));
    int firstField = getInt(slab, listElement(fields, 4, 0));
    int fieldType = layout.childType(0, 1);
    assertEquals("google.protobuf.Field", layout.plan(fieldType).name());
    assertEquals(0, handle(slab, ROOT, layout, 0));
    assertTrue(handle(slab, firstField, layout, fieldType) > 0);
    assertTrue(slab.length <= 4 * type.wire().length + 64, "slab amplification " + slab.length);
  }

  /** The executable spec agrees with the Java codec over a randomized corpus. */
  @Test
  void reference_model_matches_proto_codec() {
    Random random = new Random(42);
    int accepted = 0;
    for (ProtoPlan plan :
        List.of(choice(), unknowns(), merge(), maps(), lists(), texts(), legacy())) {
      Layout layout = plan.layout();
      for (int round = 0; round < 1500; round++) {
        byte[] input = randomWire(random, plan, 0);
        if (random.nextInt(25) == 0 && input.length > 0)
          input = Arrays.copyOf(input, random.nextInt(input.length));
        ProtoRow expected;
        try {
          expected = plan.decode(input);
        } catch (ContractViolation rejected) {
          if (rejected.getMessage().startsWith("Missing required field")) continue;
          byte[] finalInput = input;
          assertThrows(
              ContractViolation.class,
              () -> SlabReference.decode(layout, finalInput),
              () ->
                  plan.name()
                      + " accepted ("
                      + rejected.getMessage()
                      + ") "
                      + HexFormat.of().formatHex(finalInput));
          continue;
        }
        byte[] slab = SlabReference.decode(layout, input);
        SlabReference.assertMatches(layout, slab, expected);
        accepted++;
      }
    }
    assertTrue(accepted > 6000, "corpus too often malformed: " + accepted);
  }

  static byte[] randomWire(Random random, ProtoPlan plan, int depth) {
    Wire w = wire();
    int count = random.nextInt(8);
    for (int i = 0; i < count; i++) {
      if (random.nextInt(10) == 0) {
        w.field(900 + random.nextInt(5), random.nextInt(1000));
        continue;
      }
      var field = plan.field(random.nextInt(plan.size()));
      int number = field.number();
      switch (field.kind()) {
        case MESSAGE -> {
          if (depth < 4)
            w.bytes(number, randomWire(random, plan.nested(plan.ordinal(number)), depth + 1));
        }
        case GROUP -> {
          if (depth < 4) {
            w.tag(number, 3);
            w.out.writeBytes(randomWire(random, plan.nested(plan.ordinal(number)), depth + 1));
            w.tag(number, 4);
          }
        }
        case STRING ->
            w.string(number, List.of("", "a", "λ", "😀x", "plain-text").get(random.nextInt(5)));
        case BYTES -> w.bytes(number, new byte[random.nextInt(3)]);
        case FIXED32, SFIXED32, FLOAT -> w.fixed32(number, random.nextInt(4) - 1);
        case FIXED64, SFIXED64, DOUBLE -> w.fixed64(number, random.nextInt(4) - 1);
        default -> {
          if (field.repeated() && random.nextBoolean())
            w.bytes(
                number, packed(p -> p.varint(random.nextInt(4)).varint(random.nextInt(300) - 2)));
          else w.field(number, random.nextInt(5) == 0 ? -1L : random.nextInt(4));
        }
      }
      if (random.nextInt(30) == 0) w.field(number, 1); // occasional wire-type mismatch
      if (random.nextInt(60) == 0) w.out.write(random.nextInt(256));
    }
    return w.bytes();
  }
}
