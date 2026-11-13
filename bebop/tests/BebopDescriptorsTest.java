package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;
import static dev.elide.truffledata.bebop.BebopType.ref;
import static dev.elide.truffledata.bebop.Fixtures.upstream;
import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.bebop.BebopDescriptors.Options;
import dev.elide.truffledata.bebop.BebopException.Kind;
import dev.elide.truffledata.bebop.BebopSchema.Branch;
import dev.elide.truffledata.bebop.BebopSchema.Definition;
import dev.elide.truffledata.bebop.BebopSchema.EnumDef;
import dev.elide.truffledata.bebop.BebopSchema.Field;
import dev.elide.truffledata.bebop.BebopSchema.Member;
import dev.elide.truffledata.bebop.BebopSchema.TaggedField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The descriptor-set loader against descriptor sets compiled by upstream's {@code bebopc} at
 * {@code 2026.0.0-alpha.6} (sources beside them in {@code descriptors/}), and against
 * hand-built descriptors that each break one rule.
 */
final class BebopDescriptorsTest {
  // --- Expected schemas, written by hand from the .bop sources ---------------------------------

  private static final String S = "golden.scalars.";
  private static final String T = "golden.types.";

  private static final Field[] SCALAR_FIELDS = {
    new Field("b", BOOL),
    new Field("u8", BYTE),
    new Field("i8", INT8),
    new Field("i16", INT16),
    new Field("u16", UINT16),
    new Field("i32", INT32),
    new Field("u32", UINT32),
    new Field("i64", INT64),
    new Field("u64", UINT64),
    new Field("i128", INT128),
    new Field("u128", UINT128),
    new Field("f16", FLOAT16),
    new Field("f32", FLOAT32),
    new Field("f64", FLOAT64),
    new Field("bf16", BFLOAT16),
    new Field("s", STRING),
    new Field("id", UUID),
    new Field("at", TIMESTAMP),
    new Field("span", DURATION),
  };

  private static BebopSchema.Builder scalars(BebopSchema.Builder b) {
    TaggedField[] tagged = new TaggedField[SCALAR_FIELDS.length];
    for (int i = 0; i < tagged.length; i++)
      tagged[i] = new TaggedField(i + 1, SCALAR_FIELDS[i].name(), SCALAR_FIELDS[i].type());
    return b.struct(S + "AllScalars", SCALAR_FIELDS).message(S + "AllScalarsMessage", tagged);
  }

  static BebopSchema scalarsExpected() {
    return scalars(BebopSchema.builder()).build();
  }

  /** The definitions of types.bop (and scalars.bop), with arrays decorated by {@code set} as sets. */
  static BebopSchema typesExpected(@Nullable String setDecorator) {
    boolean plain = "set".equals(setDecorator);
    boolean spelled = "golden.types.set".equals(setDecorator);
    return scalars(BebopSchema.builder())
        .enumeration(T + "Color", UINT32, m("RED", 0), m("GREEN", 1), m("BLUE", 2))
        .enumeration(T + "Small", BYTE, m("NONE", 0), m("LOW", 1), m("HIGH", 255))
        .enumeration(T + "Tiny", INT8, m("MIN", -128), m("MINUS_ONE", -1), m("ZERO", 0), m("MAX", 127))
        .enumeration(T + "Short", INT16, m("MIN", -32768), m("ZERO", 0), m("MAX", 32767))
        .enumeration(T + "Port", UINT16, m("NONE", 0), m("HTTP", 80), m("TOP", 65535))
        .enumeration(T + "Signed", INT32, m("MIN", Integer.MIN_VALUE), m("ZERO", 0))
        .enumeration(T + "Wide", UINT32, m("ZERO", 0), m("TOP", 0xFFFFFFFFL))
        .enumeration(
            T + "Long", INT64, m("MIN", -Long.MAX_VALUE), m("MINUS_TWO", -2), m("ZERO", 0), m("MAX", Long.MAX_VALUE))
        .enumeration(T + "Huge", UINT64, m("ZERO", 0), m("TOP", Long.MAX_VALUE))
        .struct(
            T + "Outer",
            new Field("id", INT32),
            new Field("inner", ref(T + "Outer.Inner")),
            new Field("mode", ref(T + "Outer.Mode")),
            new Field("all", ref(S + "AllScalars")))
        .struct(T + "Outer.Inner", new Field("label", STRING), new Field("deep", ref(T + "Outer.Inner.Deeper")))
        .message(T + "Outer.Inner.Deeper", new TaggedField(1, "n", UINT64))
        .enumeration(T + "Outer.Mode", UINT16, m("IDLE", 0), m("ON", 1), m("OFF", 2))
        .message(
            T + "Envelope",
            new TaggedField(1, "header", ref(T + "Envelope.Header")),
            new TaggedField(2, "body", ref(T + "Shape")),
            new TaggedField(3, "tags", new BebopType.Array(STRING, plain)),
            new TaggedField(4, "matrix", BebopType.fixed(BebopType.fixed(INT32, 3), 2)),
            new TaggedField(5, "lookup", BebopType.map(STRING, ref(T + "Outer"))),
            new TaggedField(6, "byColor", BebopType.map(UINT16, BebopType.array(INT64))),
            new TaggedField(7, "digest", BebopType.fixed(BYTE, 16)),
            new TaggedField(8, "nested", BebopType.map(UUID, BebopType.map(INT32, BebopType.array(STRING)))),
            new TaggedField(9, "old", ref(S + "AllScalarsMessage")),
            new TaggedField(10, "jagged", BebopType.array(BebopType.array(INT16))),
            new TaggedField(11, "corners", BebopType.fixed(ref(T + "Outer.Inner"), 2)),
            new TaggedField(12, "small", BebopType.map(BOOL, ref(T + "Small"))),
            new TaggedField(200, "far", BOOL))
        .message(T + "Envelope.Header", new TaggedField(1, "id", UUID), new TaggedField(2, "sent", TIMESTAMP))
        .union(
            T + "Shape",
            new Branch(1, "Circle", ref(T + "Shape.Circle")),
            new Branch(2, "Polygon", ref(T + "Shape.Polygon")),
            new Branch(3, "plain", ref(S + "AllScalars")),
            new Branch(4, "header", ref(T + "Envelope.Header")),
            new Branch(5, "Empty", ref(T + "Shape.Empty")))
        .struct(T + "Shape.Circle", new Field("radius", FLOAT32))
        .message(T + "Shape.Polygon", new TaggedField(1, "points", BebopType.array(BebopType.fixed(FLOAT32, 2))))
        .struct(T + "Shape.Empty")
        .struct(
            T + "Bag",
            new Field("ids", new BebopType.Array(UUID, plain)),
            new Field("colors", new BebopType.Array(ref(T + "Color"), plain)),
            new Field("plain", BebopType.array(INT32)),
            new Field("spelled", new BebopType.Array(INT32, spelled)),
            new Field("grid", new BebopType.Array(BebopType.array(STRING), plain)))
        .build();
  }

  static BebopSchema flagsExpected() {
    return BebopSchema.builder()
        .flags(
            "golden.flags.Perm", BYTE, m("NONE", 0), m("READ", 1), m("WRITE", 2), m("EXEC", 4), m("READ_WRITE", 3))
        .flags("golden.flags.Signed", INT16, m("NONE", 0), m("LOW", 1), m("MINUS_EIGHT", -8))
        .struct("golden.flags.File", new Field("name", STRING), new Field("perm", ref("golden.flags.Perm")))
        .build();
  }

  static BebopSchema bareExpected(boolean unique) {
    return BebopSchema.builder()
        .struct("Bare", new Field("xs", new BebopType.Array(INT32, unique)), new Field("ys", BebopType.array(BYTE)))
        .message(
            "Wrapper",
            new TaggedField(1, "bare", ref("Bare")),
            new TaggedField(3, "old", new BebopType.Array(INT32, unique)),
            new TaggedField(2, "choice", ref("Wrapper.Choice")))
        .union("Wrapper.Choice", new Branch(1, "A", ref("Wrapper.Choice.A")), new Branch(2, "B", ref("Wrapper.Choice.B")))
        .struct("Wrapper.Choice.A", new Field("a", INT8))
        .message("Wrapper.Choice.B", new TaggedField(1, "b", STRING))
        .struct("Counter", new Field("n", UINT32))
        .message("Secret", new TaggedField(1, "s", STRING))
        .build();
  }

  private static Member m(String name, long value) {
    return new Member(name, value);
  }

  /** Same definitions, compared one by one for a readable failure, then by fingerprint. */
  private static void same(BebopSchema expected, BebopSchema actual) {
    for (Definition def : expected.definitions()) assertEquals(def, actual.definition(def.name()), def.name());
    for (Definition def : actual.definitions())
      assertNotNull(expected.definition(def.name()), () -> "unexpected " + def.name());
    assertEquals(expected.fingerprint(), actual.fingerprint());
  }

  // --- Goldens ---------------------------------------------------------------------------------

  @Test
  void scalarsGolden() {
    same(scalarsExpected(), BebopDescriptors.load(upstream("scalars.bin")));
  }

  @Test
  void typesGoldenWithImportsNestingUnionsAndCollections() {
    byte[] bytes = upstream("types.bin");
    same(typesExpected(null), BebopDescriptors.load(bytes));
    // The service, the const and the two decorator definitions are not data types.
    BebopSchema loaded = BebopDescriptors.load(bytes);
    for (String skipped : List.of("Echo", "ANSWER", "set", "note")) assertNull(loaded.definition(T + skipped));
  }

  @Test
  void setsAreAnExplicitInputComparedWithTheUsageSpelling() {
    byte[] bytes = upstream("types.bin");
    // bebopc records "@set" as "set" and "@golden.types.set" as "golden.types.set".
    same(typesExpected("set"), BebopDescriptors.load(bytes, Options.DEFAULT.withSetDecorator("set")));
    same(
        typesExpected("golden.types.set"),
        BebopDescriptors.load(bytes, Options.DEFAULT.withSetDecorator("golden.types.set")));
    same(typesExpected(null), BebopDescriptors.load(bytes, Options.DEFAULT.withSetDecorator("note2")));
    same(bareExpected(true), BebopDescriptors.load(upstream("bare.bin"), Options.DEFAULT.withSetDecorator("unique")));
  }

  @Test
  void flagsEnumsLoadAndAreGeneralOnly() {
    BebopSchema schema = BebopDescriptors.load(upstream("flags.bin"));
    same(flagsExpected(), schema);
    assertTrue(((EnumDef) Objects.requireNonNull(schema.definition("golden.flags.Perm"))).flags());
    BebopCodec general = BebopCodec.compile(schema, ref("golden.flags.File"), BebopProfile.GENERAL, BebopLimits.DEFAULT);
    BebopRecord file = new BebopRecord("golden.flags.File", "a", 5);
    assertEquals(file, general.decode(general.encode(file)));
    BebopException e =
        assertThrows(
            BebopException.class,
            () -> BebopCodec.compile(schema, ref("golden.flags.File"), Fixtures.STRICT, BebopLimits.DEFAULT));
    assertEquals(Kind.UNSUPPORTED, e.kind());
    assertTrue(e.getMessage().contains("golden.flags.Perm"), e.getMessage());
    // The flag is part of the schema's identity.
    BebopSchema plain =
        BebopSchema.builder()
            .enumeration("golden.flags.Perm", BYTE, m("NONE", 0), m("READ", 1), m("WRITE", 2), m("EXEC", 4), m("READ_WRITE", 3))
            .flags("golden.flags.Signed", INT16, m("NONE", 0), m("LOW", 1), m("MINUS_EIGHT", -8))
            .struct("golden.flags.File", new Field("name", STRING), new Field("perm", ref("golden.flags.Perm")))
            .build();
    assertNotEquals(plain.fingerprint(), schema.fingerprint());
  }

  @Test
  void packagelessSchemasNestedUnionsAndVisibility() {
    same(bareExpected(false), BebopDescriptors.load(upstream("bare.bin")));
  }

  /** Upstream's own descriptor.bop, compiled by bebopc, is exactly the schema the loader decodes with. */
  @Test
  void selfHosting() {
    BebopSchema loaded = BebopDescriptors.load(upstream("descriptor.bin"));
    same(BebopDescriptors.SCHEMA, loaded);
  }

  /** Re-encoding a decoded descriptor reproduces bebopc's bytes exactly: the first upstream differential. */
  @Test
  void descriptorBytesReencodeExactly() {
    BebopCodec codec = BebopCodec.compile(BebopDescriptors.SCHEMA, BebopDescriptors.ROOT, BebopProfile.GENERAL, BebopLimits.DEFAULT);
    for (String name : List.of("scalars.bin", "types.bin", "flags.bin", "bare.bin", "vectors.bin", "descriptor.bin")) {
      byte[] bytes = upstream(name);
      codec.validate(bytes);
      assertArrayEquals(bytes, codec.encode(codec.decode(bytes)), name);
    }
  }

  /** Codecs compiled from loaded schemas round-trip random values under both profiles. */
  @Test
  void loadedSchemasRoundTrip() {
    Random r = new Random(0xDE5C);
    List<BebopSchema> schemas =
        List.of(
            BebopDescriptors.load(upstream("types.bin"), Options.DEFAULT.withSetDecorator("set")),
            BebopDescriptors.load(upstream("bare.bin"), Options.DEFAULT.withSetDecorator("unique")),
            BebopDescriptors.load(upstream("flags.bin")),
            BebopDescriptors.load(upstream("descriptor.bin")));
    int strictCodecs = 0;
    for (BebopSchema schema : schemas) {
      for (Definition def : schema.definitions()) {
        BebopType root = ref(def.name());
        List<BebopCodec> codecs = new ArrayList<>();
        codecs.add(BebopCodec.compile(schema, root, BebopProfile.GENERAL, BebopLimits.DEFAULT));
        try {
          codecs.add(BebopCodec.compile(schema, root, Fixtures.STRICT, BebopLimits.DEFAULT));
          strictCodecs++;
        } catch (BebopException e) {
          // Only flags enums are outside the strict profile.
          assertEquals(Kind.UNSUPPORTED, e.kind());
          assertTrue(e.getMessage().startsWith("Flags enum"), e.getMessage());
        }
        for (BebopCodec codec : codecs) {
          for (int i = 0; i < 40; i++) {
            byte[] bytes = codec.encode(SchemaValues.value(schema, r, root, 0));
            Object decoded = codec.decode(bytes);
            assertArrayEquals(bytes, codec.encode(decoded), def.name());
            assertTrue(BebopValues.equal(decoded, codec.decode(codec.encode(decoded))), def.name());
            assertTrue(BebopValues.equal(decoded, codec.decodeReference(bytes)), def.name());
            assertNotNull(codec.open(bytes).root(), def.name());
          }
        }
      }
    }
    assertTrue(strictCodecs > 40, "strict codecs: " + strictCodecs);
  }

  // --- Malformed and unsupported descriptors ---------------------------------------------------

  @Test
  void truncatedAndGarbageBytesAreMalformed() {
    byte[] bytes = upstream("types.bin");
    for (int n = 0; n < bytes.length; n += 1 + n / 16) {
      byte[] prefix = Arrays.copyOf(bytes, n);
      assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> BebopDescriptors.load(prefix)).kind(), "prefix " + n);
    }
    for (String garbage : List.of("", "00", "ffffffff", "0100000001", "05000000000000000000", "deadbeefdeadbeef")) {
      byte[] b = Fixtures.hex(garbage);
      assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> BebopDescriptors.load(b)).kind(), garbage);
    }
    byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> BebopDescriptors.load(trailing)).kind());
  }

  /** Any mutation of a real descriptor set loads or fails as a BebopException, never otherwise. */
  @Test
  void mutatedDescriptorsFailOnlyAsBebopExceptions() {
    Random r = new Random(0xD35C);
    int failed = 0;
    int loaded = 0;
    for (String name : List.of("types.bin", "bare.bin", "flags.bin", "descriptor.bin")) {
      byte[] bytes = upstream(name);
      for (int i = 0; i < 1500; i++) {
        byte[] mutant = Gen.mutate(r, bytes);
        try {
          BebopDescriptors.load(mutant, Options.DEFAULT.withSetDecorator("set"));
          loaded++;
        } catch (BebopException e) {
          failed++;
        }
      }
    }
    assertTrue(failed > 100 && loaded > 100, failed + " failed, " + loaded + " loaded");
  }

  @Test
  void limitsAreRespected() {
    byte[] bytes = upstream("types.bin");
    BebopException e =
        assertThrows(
            BebopException.class,
            () -> BebopDescriptors.load(bytes, Options.DEFAULT.withLimits(new BebopLimits(1 << 20, 1 << 10, 4, 1 << 20, 1 << 20))));
    assertEquals(Kind.LIMIT, e.kind());
    assertEquals(
        Kind.LIMIT,
        assertThrows(
                BebopException.class,
                () -> BebopDescriptors.load(bytes, Options.DEFAULT.withLimits(new BebopLimits(64, 16, 64, 1 << 20, 1 << 20))))
            .kind());
  }

  @Test
  void options() {
    assertThrows(IllegalArgumentException.class, () -> new Options(BebopLimits.DEFAULT, ""));
    Options o = Options.DEFAULT.withSetDecorator("set").withLimits(new BebopLimits(1, 1, 1, 1, 1));
    assertEquals("set", o.setDecorator());
    assertEquals(1, o.limits().maxBytes());
  }

  @Test
  void handBuiltDescriptorsLoad() {
    BebopSchema schema =
        load(
            enumDef("p.E", 2, member("Z", 0L)),
            struct("p.S", field("e", defined("p.E"), null), field("m", mapOf(defined("p.E"), scalar(16)), 0L)),
            message("p.M", field("s", defined("p.S"), 7L)),
            union("p.U", branch(0, null, "p.S", "s"), branch(9, "p.U.In", null, null)),
            def(2, "p.U.In", 2, rec("bebop.StructDef", 3)),
            service("p.Svc"));
    assertEquals(BebopType.map(ref("p.E"), STRING), ((BebopSchema.StructDef) Objects.requireNonNull(schema.definition("p.S"))).fields().get(1).type());
    assertEquals(new Branch(9, "In", ref("p.U.In")), ((BebopSchema.UnionDef) Objects.requireNonNull(schema.definition("p.U"))).branches().get(1));
    // An absent edition is accepted, as is an empty set.
    assertNotNull(BebopDescriptors.load(set(schema(null, struct("q.S")))).definition("q.S"));
    assertTrue(BebopDescriptors.load(DESCRIPTORS.encode(rec("bebop.DescriptorSet", 1))).definitions().isEmpty());
  }

  @Test
  void unknownKindsAreUnsupported() {
    fails(Kind.UNSUPPORTED, "Unknown type kind 24", struct("p.S", field("x", scalar(24), null)));
    fails(Kind.UNSUPPORTED, "Unknown type kind 255", struct("p.S", field("x", scalar(255), null)));
    fails(Kind.UNSUPPORTED, "unknown kind 8", def(8, "p.X", 0, null));
    failsSet(Kind.UNSUPPORTED, "Edition 1001", set(schema(1001, struct("p.S"))));
  }

  @Test
  void missingKindsAndNamesAreMalformed() {
    fails(Kind.MALFORMED, "type without a kind", struct("p.S", field("x", scalar(0), null)));
    fails(Kind.MALFORMED, "type without a kind", struct("p.S", field("x", rec("bebop.TypeDescriptor", 7), null)));
    fails(Kind.MALFORMED, "has no kind", def(0, "p.X", 0, null));
    fails(Kind.MALFORMED, "has no kind", def(null, "p.X", 0, null));
    fails(Kind.MALFORMED, "no fully-qualified name", def(2, null, 2, rec("bebop.StructDef", 3)));
    fails(Kind.MALFORMED, "no fully-qualified name", def(2, "", 2, rec("bebop.StructDef", 3)));
    fails(Kind.MALFORMED, "has no name", struct("p.S", field(null, scalar(1), null)));
    fails(Kind.MALFORMED, "has no type", struct("p.S", field("x", null, null)));
    fails(Kind.MALFORMED, "defined type has no name", struct("p.S", field("x", rec("bebop.TypeDescriptor", 7, 23), null)));
  }

  @Test
  void danglingReferencesAreMalformed() {
    fails(Kind.MALFORMED, "unknown type p.Nope", struct("p.S", field("x", defined("p.Nope"), null)));
    fails(Kind.MALFORMED, "service p.Svc", struct("p.S", field("x", defined("p.Svc"), null)), service("p.Svc"));
    fails(Kind.MALFORMED, "unknown type p.Gone", union("p.U", branch(1, null, "p.Gone", "g")));
    fails(Kind.MALFORMED, "unknown type p.U.Gone", union("p.U", branch(1, "p.U.Gone", null, null)));
  }

  @Test
  void duplicatesAreMalformed() {
    fails(Kind.MALFORMED, "Duplicate definition p.S", struct("p.S"), message("p.S"));
    fails(Kind.MALFORMED, "Duplicate definition p.S", struct("p.S"), service("p.S"));
    fails(Kind.MALFORMED, "Duplicate definition p.S", service("p.S"), struct("p.S"));
    failsSet(Kind.MALFORMED, "Duplicate definition p.S", set(schema(1000, struct("p.S")), schema(1000, struct("p.S"))));
    fails(Kind.MALFORMED, "Duplicate definition p.S.In", def(2, "p.S", 2, rec("bebop.StructDef", 3), struct("p.S.In"), struct("p.S.In")));
    fails(Kind.MALFORMED, "Duplicate field x", struct("p.S", field("x", scalar(1), null), field("x", scalar(2), null)));
    fails(Kind.MALFORMED, "Duplicate tag 1", message("p.M", field("x", scalar(1), 1L), field("y", scalar(2), 1L)));
    fails(Kind.MALFORMED, "Duplicate member A", enumDef("p.E", 2, member("A", 0L), member("A", 1L)));
    fails(Kind.MALFORMED, "Duplicate discriminator 1", union("p.U", branch(1, null, "p.S", "a"), branch(1, null, "p.S", "b")), struct("p.S"));
    fails(Kind.MALFORMED, "missing or duplicate name", union("p.U", branch(1, null, "p.S", "a"), branch(2, null, "p.S", "a")), struct("p.S"));
  }

  @Test
  void inconsistentDefinitionsAreMalformed() {
    fails(Kind.MALFORMED, "exactly the body", def(2, "p.S", 3, rec("bebop.MessageDef", 1)));
    fails(Kind.MALFORMED, "exactly the body", def(2, "p.S", 0, null));
    fails(Kind.MALFORMED, "exactly the body", def(5, "p.Svc", 0, null));
    fails(Kind.MALFORMED, "has the tag 0", message("p.M", field("x", scalar(1), 0L)));
    fails(Kind.MALFORMED, "has the tag 0", message("p.M", field("x", scalar(1), null)));
    fails(Kind.MALFORMED, "has the tag 256", message("p.M", field("x", scalar(1), 256L)));
    fails(Kind.MALFORMED, "has a tag", struct("p.S", field("x", scalar(1), 1L)));
    fails(Kind.MALFORMED, "has no base type", enumDef("p.E", null));
    fails(Kind.MALFORMED, "has no name", enumDef("p.E", 2, member(null, 0L)));
    fails(Kind.MALFORMED, "has no value", enumDef("p.E", 2, member("A", null)));
    fails(Kind.MALFORMED, "does not fit BYTE", enumDef("p.E", 2, member("A", 256L)));
    fails(Kind.MALFORMED, "does not fit INT8", enumDef("p.E", 3, member("A", 0xFFL)));
    fails(Kind.MALFORMED, "does not fit UINT32", enumDef("p.E", 7, member("A", -1L)));
    fails(Kind.MALFORMED, "has no discriminator", union("p.U", branch(null, null, "p.S", "a")), struct("p.S"));
    fails(Kind.MALFORMED, "exactly one of", union("p.U", branch(1, "p.S", "p.S", "a")), struct("p.S"));
    fails(Kind.MALFORMED, "exactly one of", union("p.U", branch(1, null, null, "a")));
    fails(Kind.MALFORMED, "has no type", union("p.U", branch(1, "", null, null)));
    fails(Kind.MALFORMED, "has no name", union("p.U", branch(1, null, "p.S", "")), struct("p.S"));
    fails(Kind.MALFORMED, "missing or duplicate name", union("p.U", branch(1, "p.U.", null, null)));
    fails(Kind.MALFORMED, "Fixed array length 0", struct("p.S", field("x", fixedOf(scalar(1), 0L), null)));
    fails(Kind.MALFORMED, "Fixed array length 0", struct("p.S", field("x", fixedOf(scalar(1), null), null)));
    fails(Kind.MALFORMED, "Fixed array length 65536", struct("p.S", field("x", fixedOf(scalar(1), 65536L), null)));
    fails(Kind.MALFORMED, "fixed array element", struct("p.S", field("x", rec("bebop.TypeDescriptor", 7, 21, null, null, 2L), null)));
    fails(Kind.MALFORMED, "array element", struct("p.S", field("x", rec("bebop.TypeDescriptor", 7, 20), null)));
    fails(Kind.MALFORMED, "map key", struct("p.S", field("x", rec("bebop.TypeDescriptor", 7, 22), null)));
    fails(Kind.MALFORMED, "map value", struct("p.S", field("x", rec("bebop.TypeDescriptor", 7, 22, null, null, null, scalar(1)), null)));
  }

  @Test
  void unrepresentableConstructsAreUnsupported() {
    fails(Kind.UNSUPPORTED, "base type STRING", enumDef("p.E", 16, member("A", 0L)));
    fails(Kind.UNSUPPORTED, "base type INT128", enumDef("p.E", 10, member("A", 0L)));
    fails(Kind.UNSUPPORTED, "base type 99", enumDef("p.E", 99, member("A", 0L)));
    fails(Kind.UNSUPPORTED, "base type UNKNOWN", enumDef("p.E", 0, member("A", 0L)));
    fails(Kind.UNSUPPORTED, "base type BOOL", enumDef("p.E", 1, member("A", 0L)));
    fails(Kind.UNSUPPORTED, "map key", struct("p.S", field("m", mapOf(defined("p.S"), scalar(1)), null)));
    fails(Kind.UNSUPPORTED, "map key", struct("p.S", field("m", mapOf(scalar(14), scalar(1)), null)));
    fails(Kind.UNSUPPORTED, "map key", struct("p.S", field("m", mapOf(arrayOf(scalar(2)), scalar(1)), null)));
    for (BebopRecord type : List.of(scalar(6), fixedOf(scalar(6), 2L), mapOf(scalar(6), scalar(6)))) {
      byte[] bytes = set(schema(1000, struct("p.S", field("x", type, null, "set"))));
      BebopException e =
          assertThrows(BebopException.class, () -> BebopDescriptors.load(bytes, Options.DEFAULT.withSetDecorator("set")));
      assertEquals(Kind.UNSUPPORTED, e.kind());
      assertTrue(e.getMessage().contains("not a dynamic array"), e.getMessage());
      BebopDescriptors.load(bytes);
    }
    // Every loaded schema compiles under the general profile, so nothing is left for the codec to refuse.
    BebopSchema keyed = load(struct("p.S", field("m", mapOf(scalar(17), scalar(1)), null), field("t", mapOf(scalar(11), scalar(1)), null)));
    BebopCodec.compile(keyed, ref("p.S"), BebopProfile.GENERAL, BebopLimits.DEFAULT);
  }

  // --- A small builder of descriptor values ----------------------------------------------------

  private static final BebopCodec DESCRIPTORS =
      BebopCodec.compile(BebopDescriptors.SCHEMA, BebopDescriptors.ROOT, BebopProfile.GENERAL, BebopLimits.DEFAULT);

  private static BebopRecord rec(String type, int size, @Nullable Object... leading) {
    @Nullable Object[] v = new Object[size];
    System.arraycopy(leading, 0, v, 0, leading.length);
    return new BebopRecord(type, v);
  }

  private static byte[] set(BebopRecord... schemas) {
    return DESCRIPTORS.encode(new BebopRecord("bebop.DescriptorSet", List.of((Object[]) schemas)));
  }

  private static BebopRecord schema(@Nullable Integer edition, BebopRecord... defs) {
    return rec("bebop.SchemaDescriptor", 6, "test.bop", null, edition, null, List.of((Object[]) defs));
  }

  private static BebopSchema load(BebopRecord... defs) {
    return BebopDescriptors.load(set(schema(1000, defs)));
  }

  private static void fails(Kind kind, String fragment, BebopRecord... defs) {
    BebopException e = assertThrows(BebopException.class, () -> load(defs));
    assertEquals(kind, e.kind(), e.getMessage());
    assertTrue(e.getMessage().contains(fragment), e.getMessage());
  }

  private static void failsSet(Kind kind, String fragment, byte[] bytes) {
    BebopException e = assertThrows(BebopException.class, () -> BebopDescriptors.load(bytes));
    assertEquals(kind, e.kind(), e.getMessage());
    assertTrue(e.getMessage().contains(fragment), e.getMessage());
  }

  /** A definition of {@code kind} with the body of {@code bodyKind} (0 for none). */
  private static BebopRecord def(@Nullable Integer kind, @Nullable String fqn, int bodyKind, @Nullable BebopRecord body, BebopRecord... nested) {
    @Nullable Object[] v = new Object[14];
    v[0] = kind;
    v[1] = fqn == null ? null : fqn.substring(fqn.lastIndexOf('.') + 1);
    v[2] = fqn;
    if (nested.length > 0) v[6] = List.of((Object[]) nested);
    if (bodyKind > 0) v[6 + bodyKind] = body;
    return new BebopRecord("bebop.DefinitionDescriptor", v);
  }

  private static BebopRecord struct(String fqn, BebopRecord... fields) {
    return def(2, fqn, 2, rec("bebop.StructDef", 3, List.of((Object[]) fields)));
  }

  private static BebopRecord message(String fqn, BebopRecord... fields) {
    return def(3, fqn, 3, rec("bebop.MessageDef", 1, List.of((Object[]) fields)));
  }

  private static BebopRecord enumDef(String fqn, @Nullable Integer base, BebopRecord... members) {
    return def(1, fqn, 1, rec("bebop.EnumDef", 3, base, List.of((Object[]) members)));
  }

  private static BebopRecord member(@Nullable String name, @Nullable Long value) {
    return rec("bebop.EnumMemberDescriptor", 5, name, null, value);
  }

  private static BebopRecord union(String fqn, BebopRecord... branches) {
    return def(4, fqn, 4, rec("bebop.UnionDef", 1, List.of((Object[]) branches)));
  }

  private static BebopRecord branch(@Nullable Integer discriminator, @Nullable String inline, @Nullable String target, @Nullable String name) {
    return rec("bebop.UnionBranchDescriptor", 6, discriminator, null, inline, target, name);
  }

  private static BebopRecord service(String fqn) {
    return def(5, fqn, 5, rec("bebop.ServiceDef", 1));
  }

  private static BebopRecord field(@Nullable String name, @Nullable BebopRecord type, @Nullable Long index, String... decorators) {
    List<Object> usages = new ArrayList<>();
    for (String d : decorators) usages.add(rec("bebop.DecoratorUsage", 3, d));
    return rec("bebop.FieldDescriptor", 5, name, null, type, index, decorators.length == 0 ? null : usages);
  }

  private static BebopRecord scalar(int kind) {
    return rec("bebop.TypeDescriptor", 7, kind);
  }

  private static BebopRecord arrayOf(BebopRecord element) {
    return rec("bebop.TypeDescriptor", 7, 20, element);
  }

  private static BebopRecord fixedOf(BebopRecord element, @Nullable Long size) {
    return rec("bebop.TypeDescriptor", 7, 21, null, element, size);
  }

  private static BebopRecord mapOf(BebopRecord key, BebopRecord value) {
    return rec("bebop.TypeDescriptor", 7, 22, null, null, null, key, value);
  }

  private static BebopRecord defined(String fqn) {
    return rec("bebop.TypeDescriptor", 7, 23, null, null, null, null, null, fqn);
  }
}
