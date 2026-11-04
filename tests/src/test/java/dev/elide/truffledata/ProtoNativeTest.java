package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.*;
import dev.elide.truffledata.generated.TruffleDataContracts;
import dev.elide.truffledata.protobuf.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProtoNativeTest {
  private static final DataLibrary DATA = DataLibrary.factory().getUncached();
  private static final int WIRE_LIMIT = 1024 * 1024;

  private static Descriptor descriptor(String name) throws Exception {
    Path path =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "tests/native.pb");
    var set = DescriptorProtos.FileDescriptorSet.parseFrom(Files.readAllBytes(path));
    for (var file : set.getFileList()) {
      var descriptor = FileDescriptor.buildFrom(file, new FileDescriptor[0]);
      for (var message : descriptor.getMessageTypes())
        if (message.getFullName().equals(name)) return message;
    }
    throw new AssertionError(name);
  }

  private static ProtoPlan plan(String name) {
    return TruffleDataContracts.create(null, Limits.DEFAULT, 32, WIRE_LIMIT).type(name);
  }

  private static Object get(ProtoRow row, String field) {
    return DATA.read(row, row.schema().indexOf(field));
  }

  private static boolean has(ProtoRow row, String field) {
    return row.has(row.schema().indexOf(field));
  }

  @Test
  void generatedGettersCheckContractsAndPreserveNormalizedTypes() {
    var p = plan("truffledata.lossless.Sample");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Sample(p);
    var row =
        p.parseText(
            "count: 1234 explicit_count: 0 text: 'hello' blob: 'abc' numbers: [1,2] f: 1.5 d: 2.5"
                + " flag: true");
    // A cooperating external adapter can implement only the object protocol.
    ProtoAccess generic =
        new ProtoAccess() {
          public ProtoPlan plan() {
            return p;
          }

          public boolean has(int ordinal) {
            return row.has(ordinal);
          }

          public Object get(int ordinal) {
            return row.get(ordinal);
          }
        };
    assertEquals(1234L, generic.getLong(0));
    assertEquals(1.5, generic.getDouble(p.schema().indexOf("f")));
    assertTrue(generic.getBoolean(p.schema().indexOf("flag")));
    assertEquals(3, generic.getSize(p.schema().indexOf("blob")));
    assertThrows(ContractViolation.class, () -> generic.getLong(p.schema().indexOf("text")));
    assertThrows(ContractViolation.class, () -> generic.getDouble(0));
    assertThrows(ContractViolation.class, () -> generic.getBoolean(0));
    assertThrows(ContractViolation.class, () -> generic.getSize(0));
    assertEquals(1234L, access.get_count(row));
    assertTrue(access.has_explicit_count(row));
    assertEquals("hello", access.get_text(row));
    assertEquals(3, access.size_blob(row));
    assertEquals(2, access.size_numbers(row));
    assertEquals(1.5, access.get_f(row));
    assertEquals(2.5, access.get_d(row));
    assertTrue(access.get_flag(row));
    assertThrows(
        ContractViolation.class,
        () -> access.get_count(plan("truffledata.lossless.Sample").decode(new byte[0])));
    assertThrows(
        ContractViolation.class,
        () ->
            new TruffleDataContracts.Access_truffledata$lossless$Sample(
                plan("truffledata.lossless.Sample.Child")));
    assertThrows(ContractViolation.class, () -> p.getBoolean(row, 0));
    assertThrows(IndexOutOfBoundsException.class, () -> p.getLong(row, -1));
  }

  @Test
  void decodedOwnershipDoesNotAliasInputsOrPublicBuilders() {
    var plan = plan("truffledata.lossless.Sample");
    int ordinal = plan.schema().indexOf("explicit_count");
    var builder = plan.newBuilder().set(ordinal, 0L);
    var first = builder.build();
    builder.clear(ordinal);
    var second = builder.build();
    assertTrue(first.has(ordinal));
    assertFalse(second.has(ordinal));

    byte[] known = plan.encode(first);
    byte[] wire = Arrays.copyOf(known, known.length + 3);
    // Unknown varint field 100 is owned by the decoded result, just like known values.
    wire[known.length] = (byte) 0xa0;
    wire[known.length + 1] = 6;
    wire[known.length + 2] = 42;
    for (boolean buffer : List.of(false, true)) {
      byte[] input = wire.clone();
      ProtoRow decoded = buffer ? plan.decode(java.nio.ByteBuffer.wrap(input)) : plan.decode(input);
      byte[] encoded = plan.encode(decoded);
      Arrays.fill(input, (byte) 0);
      assertTrue(decoded.has(ordinal));
      assertArrayEquals(wire, encoded);
      assertArrayEquals(encoded, plan.encode(decoded));
    }
  }

  @Test
  void compactIntegerRowsMatchGeneralDecoderAndGeneratedMessages() throws Exception {
    var descriptor = Timestamp.getDescriptor();
    var plan = ProtoCompiler.compile(null, descriptor, Limits.DEFAULT, 8, WIRE_LIMIT);
    Random random = new Random(77219);
    for (int i = 0; i < 500; i++) {
      long seconds =
          i == 0 ? 0 : i == 1 ? Long.MIN_VALUE : i == 2 ? Long.MAX_VALUE : random.nextLong();
      int nanos =
          i == 0 ? 0 : i == 1 ? Integer.MIN_VALUE : i == 2 ? Integer.MAX_VALUE : random.nextInt();
      var expected = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
      byte[] wire = expected.toByteArray();
      for (var row : List.of(plan.decode(wire), plan.decode(java.nio.ByteBuffer.wrap(wire)))) {
        assertEquals(seconds, DATA.readLong(row, 0));
        assertEquals((long) nanos, DATA.readLong(row, 1));
        assertEquals(seconds != 0, row.has(0));
        assertEquals(nanos != 0, row.has(1));
        assertEquals(expected, Timestamp.parseFrom(plan.encode(row)));
        assertThrows(
            com.oracle.truffle.api.nodes.UnexpectedResultException.class,
            () -> DATA.readDouble(row, 0));
        assertThrows(
            com.oracle.truffle.api.nodes.UnexpectedResultException.class,
            () -> DATA.readBoolean(row, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> row.has(2));
      }
    }
    for (byte[] wire :
        List.of(
            new byte[] {8, 1, 8, 2, 16, 4}, // Duplicate scalar: last value wins.
            new byte[] {(byte) 0x88, 0, 1}, // Noncanonical tag uses the general codec.
            new byte[] {8, (byte) 0x81, 0},
            new byte[] {24, 7}, // Unknown field must survive compact storage.
            new byte[] {10, 1, 42})) { // Known number with mismatched wire type is unknown.
      assertEquals(Timestamp.parseFrom(wire), Timestamp.parseFrom(plan.encode(plan.decode(wire))));
    }
    for (byte[] bad :
        List.of(
            new byte[] {8},
            new byte[] {0},
            new byte[] {8, (byte) 0x80},
            new byte[] {8, -1, -1, -1, -1, -1, -1, -1, -1, -1, 2})) {
      assertThrows(ContractViolation.class, () -> plan.decode(bad));
      assertThrows(ContractViolation.class, () -> plan.decode(java.nio.ByteBuffer.wrap(bad)));
    }
    var builder = plan.newBuilder().set(0, 1000L);
    var first = builder.build();
    builder.set(0, 2000L);
    assertEquals(1000L, DATA.readLong(first, 0));
    assertEquals(2000L, DATA.readLong(builder.build(), 0));
    var small = ProtoCompiler.compile(null, descriptor, Limits.DEFAULT, 8, 1);
    assertThrows(ContractViolation.class, () -> small.decode(new byte[] {8, 1}));
    var single =
        ProtoCompiler.compile(null, Int64Value.getDescriptor(), Limits.DEFAULT, 8, WIRE_LIMIT);
    assertEquals(-1L, DATA.readLong(single.decode(Int64Value.of(-1).toByteArray()), 0));
    assertFalse(single.decode(new byte[0]).has(0));
  }

  @Test
  void denseAndSparseTagLookupPreservesOrdinals() throws Exception {
    var message = DescriptorProtos.DescriptorProto.newBuilder().setName("Tags");
    int[] numbers = {536870911, 1, 7, 4096, 3};
    for (int number : numbers) {
      message.addField(
          DescriptorProtos.FieldDescriptorProto.newBuilder()
              .setName("f" + number)
              .setNumber(number)
              .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64)
              .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL));
    }
    var file =
        FileDescriptor.buildFrom(
            DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("tags.proto")
                .setSyntax("proto3")
                .addMessageType(message)
                .build(),
            new FileDescriptor[0]);
    var descriptor = file.getMessageTypes().getFirst();
    var plan = ProtoCompiler.compile(null, descriptor, Limits.DEFAULT, 8, WIRE_LIMIT);
    var value = DynamicMessage.newBuilder(descriptor);
    for (int i = 0; i < numbers.length; i++) {
      assertEquals(i, plan.ordinal(numbers[i]));
      value.setField(descriptor.findFieldByNumber(numbers[i]), 1000L + i);
    }
    for (int absent : new int[] {-1, 0, 2, 6, 10, 11, 4095, 4097, Integer.MAX_VALUE})
      assertEquals(-1, plan.ordinal(absent));
    var expected = value.build();
    assertEquals(
        expected,
        DynamicMessage.parseFrom(descriptor, plan.encode(plan.decode(expected.toByteArray()))));
  }

  @Test
  void jsonNullEnumAndWellKnownMappingBoundary() {
    var nullEnum =
        new ProtoPlan.EnumType("google.protobuf.NullValue", true, Map.of("NULL_VALUE", 0));
    var field =
        new ProtoPlan.FieldPlan(
            "null_value",
            1,
            ProtoPlan.Kind.ENUM,
            false,
            false,
            true,
            false,
            false,
            -1,
            "",
            nullEnum,
            0L);
    var plan =
        new ProtoPlan.Model(
                null,
                List.of(new ProtoPlan.Type("Nulls", false, List.of(field))),
                Limits.DEFAULT,
                8,
                WIRE_LIMIT)
            .type("Nulls");
    assertTrue(plan.parseJson("{\"nullValue\":null}").has(0));
    assertEquals(0L, get(plan.parseJson("{\"nullValue\":null}"), "null_value"));
    var timestamp =
        ProtoCompiler.compile(null, Timestamp.getDescriptor(), Limits.DEFAULT, 8, WIRE_LIMIT);
    assertEquals(1767225600L, get(timestamp.parseJson("\"2026-01-01T00:00:00Z\""), "seconds"));
  }

  @Test
  void seededTextAndJsonRoundTrips() throws Exception {
    var descriptor = descriptor("truffledata.lossless.Sample");
    var plan = plan(descriptor.getFullName());
    Random random = new Random(90210);
    for (int i = 0; i < 200; i++) {
      byte[] bytes = new byte[random.nextInt(100)];
      random.nextBytes(bytes);
      var message =
          DynamicMessage.newBuilder(descriptor)
              .setField(descriptor.findFieldByName("count"), random.nextInt())
              .setField(descriptor.findFieldByName("u64"), random.nextLong())
              .setField(descriptor.findFieldByName("f"), Float.intBitsToFloat(random.nextInt()))
              .setField(descriptor.findFieldByName("d"), Double.longBitsToDouble(random.nextLong()))
              .setField(descriptor.findFieldByName("blob"), ByteString.copyFrom(bytes))
              .setField(descriptor.findFieldByName("text"), "quote \" slash \\ newline\n 中 😀 " + i)
              .setField(descriptor.findFieldByName("custom_value"), i)
              .build();
      assertEquals(
          message,
          DynamicMessage.parseFrom(
              descriptor,
              plan.encode(plan.parseText(TextFormat.printer().printToString(message)))));
      assertEquals(
          message,
          DynamicMessage.parseFrom(
              descriptor,
              plan.encode(
                  plan.parseJson(com.google.protobuf.util.JsonFormat.printer().print(message)))));
    }
  }

  @Test
  void textPayloadAndCellBudgetsAreSharedAcrossFields() {
    var small =
        TruffleDataContracts.create(null, new Limits(10, 256, 100, 2), 16, 10000)
            .type("truffledata.lossless.Sample");
    assertEquals("中", get(small.parseText("text: '中'"), "text"));
    assertEquals("中", get(small.parseJson("{\"text\":\"中\"}"), "text"));
    assertThrows(ContractViolation.class, () -> small.parseText("text: 'ab'"));
    assertThrows(ContractViolation.class, () -> small.parseJson("{\"text\":\"ab\"}"));
    assertThrows(
        ContractViolation.class, () -> small.parseText("numbers: [" + "1,".repeat(100) + "1]"));
    assertThrows(
        ContractViolation.class,
        () -> small.parseJson("{\"numbers\":[" + "1,".repeat(100) + "1]}"));
  }

  @Test
  void descriptorFreeJsonMatchesProtobufJava() throws Exception {
    var d = descriptor("truffledata.lossless.Sample");
    var plan = plan(d.getFullName());
    for (String json :
        List.of(
            "{\"customAlias\":17}",
            "{\"custom_value\":18}",
            "{\"count\":-2147483648,\"explicitCount\":0,\"u32\":4294967295,\"u64\":\"18446744073709551615\"}",
            "{\"text\":\"hello €\",\"blob\":\"Af8=\",\"blobs\":[\"eA\",\"_w\"]}",
            "{\"numbers\":[1,-2,3e0,\"4\"],\"statuses\":[\"READY\",999],\"flag\":true}",
            "{\"child\":{\"a\":1},\"selectedChild\":{\"b\":\"ok\"}}",
            "{\"children\":{\"a\":{\"a\":1}},\"counts\":{\"18446744073709551615\":-1}}",
            "{\"f\":\"-Infinity\",\"d\":\"NaN\",\"count\":null}")) {
      var expected = DynamicMessage.newBuilder(d);
      com.google.protobuf.util.JsonFormat.parser().merge(json, expected);
      assertEquals(
          expected.build(), DynamicMessage.parseFrom(d, plan.encode(plan.parseJson(json))), json);
    }
    for (String json :
        List.of(
            "{\"count\":1,\"count\":2}",
            "{\"explicitCount\":1,\"explicit_count\":2}",
            "{\"count\":1.1}",
            "{\"selected\":1,\"selectedChild\":{}}",
            "{\"unknown\":0}",
            "{\"count\":1e999999999}",
            "{\"count\":1e-999999999}",
            "{\"blob\":\"=\"}",
            "{\"f\":1e100}",
            "{\"numbers\":[null]}",
            "{\"text\":123}",
            "{} {}",
            "{count:1}",
            "{\"counts\":{\"1\":1,\"1.0\":2}}"))
      assertThrows(ContractViolation.class, () -> plan.parseJson(json), json);
  }

  @Test
  void descriptorFreeTextMatchesProtobufJava() throws Exception {
    var d = descriptor("truffledata.lossless.Sample");
    var plan = plan(d.getFullName());
    for (String text :
        List.of(
            "count: -2147483648 explicit_count: 0 u32: 0xffffffff u64: 18446744073709551615",
            "text: 'hello' \" world\" blob: '\\001\\377' blobs: ['x', 'y']",
            "numbers: [1, -2, 03, 0x4] numbers: 5 statuses: [READY, 999] flag: True",
            "child < a: 1 b: 'ok' > selected_child: { a: 2 } # comment\n",
            "children { key: 'a' value { a: 1 } } children { key: 'a' value { b: 'last' } }",
            "counts { key: 18446744073709551615 value: -1 }",
            "f: -inf d: nan fixed32: 4294967295 sfixed64: -9223372036854775808",
            "text: '\\342\\202\\254'")) {
      var expected = DynamicMessage.newBuilder(d);
      TextFormat.getParser().merge(text, expected);
      assertEquals(
          expected.build(), DynamicMessage.parseFrom(d, plan.encode(plan.parseText(text))), text);
    }
    var legacy = descriptor("truffledata.lossless.Legacy");
    var expected = DynamicMessage.newBuilder(legacy);
    TextFormat.getParser().merge("Old { value: 9 } status: FIRST", expected);
    var legacyPlan = plan(legacy.getFullName());
    assertEquals(
        expected.build(),
        DynamicMessage.parseFrom(
            legacy, legacyPlan.encode(legacyPlan.parseText("Old { value: 9 } status: FIRST"))));
  }

  @Test
  void descriptorFreeTextRejectsMalformedAndBudgetedInputs() {
    var plan = plan("truffledata.lossless.Sample");
    for (String text :
        List.of(
            "unknown: 1",
            "count: 1 count: 2",
            "selected: 1 selected_child {}",
            "count: 2147483648",
            "u64: 18446744073709551616",
            "u32: -1",
            "flag: yes",
            "child {",
            "child >",
            "count 1",
            "numbers: [1,]",
            "text: 'unterminated",
            "text: '\\xff'",
            "text: '\\q'",
            "text: 'line\nbreak'",
            "status: UNKNOWN",
            "count: --1",
            "count: 0x-1",
            "count: 1 }",
            "[extension]: 1"))
      assertThrows(ContractViolation.class, () -> plan.parseText(text), text);
    assertThrows(
        ContractViolation.class, () -> plan("truffledata.lossless.Required").parseText(""));
    assertThrows(
        ContractViolation.class,
        () -> plan("truffledata.lossless.Legacy").parseText("status: 999"));
    var shallow =
        TruffleDataContracts.create(null, Limits.DEFAULT, 1, WIRE_LIMIT)
            .type("truffledata.lossless.Sample");
    assertThrows(ContractViolation.class, () -> shallow.parseText("child {}"));
    var small =
        TruffleDataContracts.create(null, Limits.DEFAULT, 32, 4)
            .type("truffledata.lossless.Sample");
    assertThrows(ContractViolation.class, () -> small.parseText("count: 1"));
  }

  @Test
  void typedBuffersPreserveRegionAndOwnTheirPayloads() throws Exception {
    var d = descriptor("truffledata.lossless.Sample");
    var plan = plan(d.getFullName());
    var message =
        DynamicMessage.newBuilder(d)
            .setField(d.findFieldByName("text"), "hello €")
            .setField(d.findFieldByName("blob"), ByteString.copyFrom(new byte[] {1, -1}))
            .setUnknownFields(
                UnknownFieldSet.newBuilder()
                    .addField(100, UnknownFieldSet.Field.newBuilder().addVarint(17).build())
                    .build())
            .build();
    byte[] wire = message.toByteArray();
    for (java.nio.ByteBuffer buffer :
        List.of(
            java.nio.ByteBuffer.allocate(wire.length + 4),
            java.nio.ByteBuffer.allocateDirect(wire.length + 4))) {
      buffer.position(2).put(wire).limit(wire.length + 2).position(2);
      var view = buffer.asReadOnlyBuffer();
      var row = plan.decode(view);
      assertEquals(2, view.position());
      assertEquals(wire.length + 2, view.limit());
      buffer.put(2, (byte) 0);
      assertEquals(message, DynamicMessage.parseFrom(d, plan.encode(row)));
    }
  }

  @Test
  void proto3PresenceValuesAndMessageOrBuilderInputs() throws Exception {
    var d = descriptor("truffledata.lossless.Sample");
    var plan = plan(d.getFullName());
    var source = ProtoCompiler.bind(plan, d);
    var empty = source.snapshot(DynamicMessage.newBuilder(d));
    assertEquals(0L, get(empty, "count"));
    assertFalse(has(empty, "count"));
    assertTrue(DATA.isPresent(empty, 0));
    assertEquals(0L, get(empty, "explicit_count"));
    assertFalse(has(empty, "explicit_count"));
    assertSame(Missing.INSTANCE, get(empty, "child"));
    assertFalse(DATA.isPresent(empty, empty.schema().indexOf("child")));
    assertTrue(((DataArray) get(empty, "blob")).isBytes());
    assertEquals(0, plan.encode(empty).length);
    var builder =
        DynamicMessage.newBuilder(d)
            .setField(d.findFieldByName("explicit_count"), 0)
            .setField(d.findFieldByName("u32"), -1)
            .setField(d.findFieldByName("u64"), -1L)
            .setField(d.findFieldByName("blob"), ByteString.copyFrom(new byte[] {0, -1}))
            .setField(
                d.findFieldByName("status"),
                d.findFieldByName("status").getEnumType().findValueByNumberCreatingIfUnknown(999));
    for (ProtoRow row :
        List.of(
            source.snapshot(builder),
            source.snapshot(builder.build()),
            plan.decode(builder.build().toByteArray()))) {
      assertTrue(has(row, "explicit_count"));
      assertEquals(4294967295L, get(row, "u32"));
      assertEquals(
          new java.math.BigInteger("18446744073709551615"),
          com.oracle.truffle.api.interop.InteropLibrary.getUncached()
              .asBigInteger(get(row, "u64")));
      assertEquals(999L, get(row, "status"));
      assertEquals(255L, ((DataArray) get(row, "blob")).get(1));
      assertEquals(builder.build(), DynamicMessage.parseFrom(d, plan.encode(row)));
      assertEquals(
          32, Objects.requireNonNull(plan.schema().field(4).constraints().integer()).bits());
      assertFalse(Objects.requireNonNull(plan.schema().field(4).constraints().integer()).signed());
      assertTrue(plan.field(6).map());
      assertEquals("truffledata.lossless.Sample.Child", plan.nested(7).name());
    }
  }

  @Test
  void proto2DefaultsClosedEnumsRequiredAndUnknownFields() throws Exception {
    var d = descriptor("truffledata.lossless.Legacy");
    var plan = plan(d.getFullName());
    var source = ProtoCompiler.bind(plan, d);
    var empty = plan.decode(new byte[0]);
    assertEquals(42L, get(empty, "count"));
    assertEquals("hello", get(empty, "text"));
    assertEquals(2L, get(empty, "status"));
    assertFalse(has(empty, "count"));
    assertEquals(255L, ((DataArray) get(empty, "blob")).get(1));
    assertEquals(0, plan.encode(empty).length);
    // Explicitly setting a declared default must retain presence.
    var row = plan.newBuilder().set(0, 42L).build();
    assertTrue(row.has(0));
    assertTrue(DynamicMessage.parseFrom(d, plan.encode(row)).hasField(d.getFields().getFirst()));
    assertEquals(42L, get(source.snapshot(DynamicMessage.newBuilder(d)), "count"));
    byte[] unknownEnum = new byte[] {24, 99, 34, 3, 1, 99, 2, (byte) 0xa0, 6, 7};
    var unknown = plan.decode(unknownEnum);
    assertFalse(has(unknown, "status"));
    assertEquals(2, ((DataArray) get(unknown, "statuses")).size());
    assertEquals(
        DynamicMessage.parseFrom(d, unknownEnum),
        DynamicMessage.parseFrom(d, plan.encode(unknown)));
    assertThrows(ContractViolation.class, () -> plan.newBuilder().set(2, 99L));
    var required = plan("truffledata.lossless.Required");
    assertThrows(ContractViolation.class, () -> required.decode(new byte[0]));
    assertThrows(ContractViolation.class, () -> required.newBuilder().build());
    assertEquals(0L, DATA.read(required.decode(new byte[] {8, 0}), 0));
  }

  @Test
  void duplicateMessagesOneofsMapsAndPackedSegmentsMatchReference() throws Exception {
    var d = descriptor("truffledata.lossless.Sample");
    var p = plan(d.getFullName());
    byte[] wire =
        new byte[] {
          66,
          2,
          8,
          3,
          66,
          3,
          18,
          1,
          120, // child fields merge
          90,
          2,
          8,
          9,
          80,
          0,
          90,
          3,
          18,
          1,
          121, // oneof switches, old a must disappear
          74,
          2,
          2,
          4,
          72,
          5,
          74,
          1,
          6 // packed and unpacked repeated sint32
        };
    var row = p.decode(wire);
    assertEquals(3L, get((ProtoRow) get(row, "child"), "a"));
    assertEquals("x", get((ProtoRow) get(row, "child"), "b"));
    assertEquals(0L, get((ProtoRow) get(row, "selected_child"), "a"));
    assertFalse(has(row, "selected"));
    assertEquals(4, ((DataArray) get(row, "numbers")).size());
    assertEquals(DynamicMessage.parseFrom(d, wire), DynamicMessage.parseFrom(d, p.encode(row)));
    var source = ProtoCompiler.bind(p, d);
    var mapRow = source.parseJson("{\"children\":{\"key\":{\"a\":3}}}");
    var pair = (ProtoRow) ((DataArray) get(mapRow, "children")).get(0);
    assertEquals("key", DATA.read(pair, 0));
    assertEquals(3L, get((ProtoRow) DATA.read(pair, 1), "a"));
    assertEquals(
        DynamicMessage.parseFrom(d, p.encode(mapRow)),
        DynamicMessage.parseFrom(d, p.encode(p.decode(p.encode(mapRow)))));
  }

  @Test
  void scalarExtremesGroupsAndRandomDifferentialRoundTrips() throws Exception {
    var d = descriptor("truffledata.lossless.Sample");
    var p = plan(d.getFullName());
    var random = new Random(0x54525546464c45L);
    for (int round = 0; round < 100; round++) {
      var builder = DynamicMessage.newBuilder(d);
      for (var field : d.getFields()) {
        if (field.isRepeated() || field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) continue;
        Object value =
            switch (field.getJavaType()) {
              case INT -> random.nextInt();
              case LONG -> random.nextLong();
              case FLOAT -> Float.intBitsToFloat(random.nextInt());
              case DOUBLE -> Double.longBitsToDouble(random.nextLong());
              case BOOLEAN -> random.nextBoolean();
              case STRING -> "a😀\\\"" + round;
              case BYTE_STRING -> ByteString.copyFrom(new byte[] {(byte) round, -1});
              case ENUM -> field.getEnumType().findValueByNumberCreatingIfUnknown(round);
              default -> throw new AssertionError();
            };
        builder.setField(field, value);
      }
      var message = builder.build();
      assertEquals(message, DynamicMessage.parseFrom(d, p.encode(p.decode(message.toByteArray()))));
    }
    var legacy = descriptor("truffledata.lossless.Legacy");
    var old = plan(legacy.getFullName());
    byte[] group = {59, 64, 12, 60};
    assertEquals(
        DynamicMessage.parseFrom(legacy, group),
        DynamicMessage.parseFrom(legacy, old.encode(old.decode(group))));
  }

  @Test
  void textFormatsAndRuntimeSchemaAgreeWithGeneratedContracts() throws Exception {
    var d = descriptor("truffledata.lossless.Sample");
    var generated = plan(d.getFullName());
    var runtime = ProtoCompiler.compile(null, d, Limits.DEFAULT, 32, WIRE_LIMIT);
    var source = ProtoCompiler.bind(generated, d);
    var json =
        source.parseJson(
            "{\"count\":7,\"explicitCount\":0,\"blob\":\"AP8=\",\"status\":999,\"u64\":\"18446744073709551615\"}");
    var text =
        source.parseText(
            "count: 7 explicit_count: 0 blob: '\\000\\377' status: 999 u64: 18446744073709551615");
    assertArrayEquals(generated.encode(json), generated.encode(text));
    assertArrayEquals(
        generated.encode(json), runtime.encode(runtime.decode(generated.encode(json))));
    assertThrows(Exception.class, () -> source.parseJson("{\"doesNotExist\":1}"));
    assertThrows(Exception.class, () -> source.parseText("count: nope"));
  }

  @Test
  void malformedInputAndBudgetsFailBeforeUnboundedAllocation() {
    var p = plan("truffledata.lossless.Sample");
    for (byte[] bad :
        List.of(
            new byte[] {0},
            new byte[] {8, (byte) 128},
            new byte[] {26, 127},
            new byte[] {66, 2, 12, 0},
            new byte[] {122, 1, (byte) 255},
            new byte[] {8, -1, -1, -1, -1, -1, -1, -1, -1, -1, 2}))
      assertThrows(ContractViolation.class, () -> p.decode(bad), Arrays.toString(bad));
    var shallow = TruffleDataContracts.create(null, Limits.DEFAULT, 1, 32).type(p.name());
    assertThrows(ContractViolation.class, () -> shallow.decode(new byte[] {66, 0}));
    assertThrows(ContractViolation.class, () -> shallow.decode(new byte[33]));
    assertThrows(
        ContractViolation.class, () -> p.newBuilder().set(2, new DataArray(new Object[] {1L})));
    assertThrows(ContractViolation.class, () -> p.newBuilder().set(4, -1L));
    assertThrows(ContractViolation.class, () -> p.encode(plan(p.name()).newBuilder().build()));
  }

  @Test
  void schemalessPreservesWireTypesAndNeverGuessesText() {
    var rows =
        ProtoWire.decode(
            new byte[] {8, 1, 8, 2, 18, 2, 104, 105, 27, 8, 3, 28}, Limits.DEFAULT, 4, 100);
    assertEquals(4, rows.size());
    assertEquals(1L, DATA.read(rows.get(0), 0));
    assertEquals(0L, DATA.read(rows.get(0), 1));
    assertEquals(2L, DATA.read(rows.get(1), 2));
    assertTrue(((DataArray) DATA.read(rows.get(2), 2)).isBytes());
    assertEquals(1, ((DataArray) DATA.read(rows.get(3), 2)).size());
    assertThrows(
        ContractViolation.class,
        () -> ProtoWire.decode(new byte[] {27, 28}, Limits.DEFAULT, 1, 100));
  }

  @Test
  void mapDefaultsDuplicateKeysAndSnapshotOwnership() throws Exception {
    var p = plan("truffledata.lossless.Sample");
    byte[] wire = {58, 7, 10, 1, 120, 18, 2, 8, 1, 58, 7, 10, 1, 120, 18, 2, 8, 2};
    var pairs = (DataArray) get(p.decode(wire), "children");
    assertEquals(1, pairs.size());
    assertEquals(2L, get((ProtoRow) DATA.read(pairs.get(0), 1), "a"));
    var defaultPair =
        ((DataArray) get(p.decode(new byte[] {58, 3, 10, 1, 120}), "children")).get(0);
    assertInstanceOf(ProtoRow.class, DATA.read(defaultPair, 1));
    byte[] blob = {26, 2, 1, 2};
    var row = p.decode(blob);
    blob[2] = 99;
    assertEquals(1L, ((DataArray) get(row, "blob")).get(0));
    var builder = p.newBuilder().set(0, 7L);
    var first = builder.build();
    builder.clear(0).set(0, 8L);
    assertEquals(7L, get(first, "count"));
    assertThrows(
        ContractViolation.class,
        () -> builder.set(6, new DataArray(new Object[] {pairs.get(0), pairs.get(0)})));
  }

  @Test
  void runtimeDescriptorSetsGeneratorAndSourceIdentity() throws Exception {
    Path path =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "tests/native.pb");
    var set = DescriptorProtos.FileDescriptorSet.parseFrom(Files.readAllBytes(path));
    var name = "truffledata.lossless.Sample";
    var runtime = ProtoCompiler.compile(null, set, name, Limits.DEFAULT, 16, WIRE_LIMIT);
    assertEquals(0L, DATA.read(runtime.decode(new byte[0]), 0));
    assertThrows(
        ContractViolation.class,
        () -> ProtoCompiler.compile(null, set, "no.such.Type", Limits.DEFAULT, 16, WIRE_LIMIT));
    assertThrows(
        ContractViolation.class,
        () -> ProtoCompiler.compile(null, set, name, Limits.DEFAULT, 16, 1));
    var request =
        com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest.newBuilder()
            .addAllProtoFile(set.getFileList())
            .addFileToGenerate("native_proto3.proto")
            .build();
    var generated = ContractPlugin.generate(request);
    assertEquals(generated, ContractPlugin.generate(request));
    assertEquals(1, generated.getFileCount());
    assertFalse(generated.getFile(0).getContent().contains("Descriptor"));
    assertFalse(generated.getFile(0).getContent().contains("ProtoCompiler"));
    assertThrows(
        ContractViolation.class,
        () -> ContractPlugin.generate(request.toBuilder().setParameter("unknown=true").build()));
    assertThrows(
        ContractViolation.class,
        () -> ContractPlugin.generate(request.toBuilder().setParameter("class=class").build()));
    var descriptor = descriptor(name);
    var source = ProtoCompiler.bind(runtime, descriptor);
    assertSame(runtime, source.plan());
    assertEquals(
        0L, DATA.read(ProtoCompiler.snapshot(runtime, DynamicMessage.newBuilder(descriptor)), 0));
    assertThrows(
        ContractViolation.class,
        () -> source.snapshot(DynamicMessage.newBuilder(descriptor(name))));
    var shortSource =
        ProtoCompiler.bind(
            TruffleDataContracts.create(null, Limits.DEFAULT, 1, 64).type(name), descriptor);
    assertThrows(
        ContractViolation.class, () -> shortSource.parseJson("{\"child\":{\"child\":{}}}"));
    assertThrows(
        ContractViolation.class, () -> shortSource.parseText("text: '" + "x".repeat(65) + "'"));
  }

  @Test
  void resourceLimitsAndInvalidPlansAreEnforced() {
    var small =
        TruffleDataContracts.create(null, new Limits(10, 256, 100, 2), 16, 1024)
            .type("truffledata.lossless.Sample");
    assertThrows(ContractViolation.class, () -> small.decode(new byte[] {122, 2, 97, 98}));
    // One CJK code point consumes two UTF-16 bytes, despite requiring three wire bytes.
    assertEquals(
        "中", get(small.decode(new byte[] {122, 3, (byte) 0xe4, (byte) 0xb8, (byte) 0xad}), "text"));
    byte[] payload = new byte[104];
    payload[0] = 26;
    payload[1] = 102;
    assertThrows(ContractViolation.class, () -> small.decode(payload));
    assertThrows(
        ContractViolation.class, () -> TruffleDataContracts.create(null, Limits.DEFAULT, 0, 1));
    assertThrows(
        ContractViolation.class, () -> TruffleDataContracts.create(null, Limits.DEFAULT, 1, -1));
    var model = TruffleDataContracts.create(null, Limits.DEFAULT, 1, 1);
    assertThrows(ContractViolation.class, () -> model.type("absent"));
    assertThrows(
        ContractViolation.class,
        () ->
            model
                .type("truffledata.lossless.Sample")
                .encode(model.type("truffledata.lossless.Sample").newBuilder().set(0, 1L).build()));
    assertThrows(
        ContractViolation.class,
        () ->
            new ProtoPlan.FieldPlan(
                "bad",
                0,
                ProtoPlan.Kind.INT32,
                false,
                false,
                false,
                false,
                false,
                -1,
                "",
                null,
                0L));
  }

  @Test
  void unsignedMapKeysUseNumericEqualityAndClosedMapEnumsRemainUnknown() throws Exception {
    var p = plan("truffledata.lossless.Sample");
    byte[] wire = {(byte) 194, 1, 4, 8, 1, 16, 2, (byte) 194, 1, 4, 8, 1, 16, 3};
    var rows = (DataArray) get(p.decode(wire), "counts");
    assertEquals(1, rows.size());
    assertEquals(3L, DATA.read(rows.get(0), 1));
    var entryPlan = p.nested(p.schema().indexOf("counts"));
    var key = Values.normalize(java.math.BigInteger.ONE);
    var first = entryPlan.newBuilder().set(0, key).set(1, 2L).build();
    var second =
        entryPlan
            .newBuilder()
            .set(0, Values.normalize(java.math.BigInteger.ONE))
            .set(1, 3L)
            .build();
    assertThrows(
        ContractViolation.class,
        () ->
            p.newBuilder()
                .set(p.schema().indexOf("counts"), new DataArray(new Object[] {first, second})));
    var d = descriptor("truffledata.lossless.Legacy");
    var legacy = plan(d.getFullName());
    byte[] closedMap = {74, 5, 10, 1, 120, 16, 99};
    var reference = DynamicMessage.parseFrom(d, closedMap);
    var row = legacy.decode(closedMap);
    // The wire contract preserves the whole entry as unknown. DynamicMessage instead retains
    // a default-valued entry, so Source deliberately normalizes this case through the wire codec.
    assertEquals(0, ((DataArray) get(row, "states")).size());
    var source = ProtoCompiler.bind(legacy, d);
    assertEquals(0, ((DataArray) get(source.snapshot(reference), "states")).size());
    assertEquals(0, ((DataArray) get(source.snapshot(reference.toBuilder()), "states")).size());
    assertEquals(reference, DynamicMessage.parseFrom(d, legacy.encode(row)));
  }

  @Test
  void unicodeAndUnknownGroupValidationApplyToAllInputPaths() throws Exception {
    var p = plan("truffledata.lossless.Sample");
    assertThrows(ContractViolation.class, () -> p.newBuilder().set(14, "\ud800"));
    assertThrows(ContractViolation.class, () -> p.newBuilder().set(14, "\udc00"));
    for (byte[] bytes :
        List.of(
            new byte[] {122, 1, (byte) 0xc2},
            new byte[] {122, 2, (byte) 0xc2, 65},
            new byte[] {122, 3, (byte) 0xe0, (byte) 0x80, (byte) 0x80},
            new byte[] {122, 3, (byte) 0xed, (byte) 0xa0, (byte) 0x80},
            new byte[] {122, 4, (byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80}))
      assertThrows(ContractViolation.class, () -> p.decode(bytes));
    var d = descriptor(p.name());
    var shallow = TruffleDataContracts.create(null, Limits.DEFAULT, 1, 1024).type(p.name());
    var source = ProtoCompiler.bind(shallow, d);
    var message =
        DynamicMessage.newBuilder(d)
            .setUnknownFields(
                UnknownFieldSet.newBuilder()
                    .addField(
                        100,
                        UnknownFieldSet.Field.newBuilder()
                            .addGroup(UnknownFieldSet.getDefaultInstance())
                            .build())
                    .build());
    assertThrows(ContractViolation.class, () -> source.snapshot(message));
  }
}
