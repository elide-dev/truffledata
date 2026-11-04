package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import dev.elide.truffledata.generated.TruffleDataContracts;
import dev.elide.truffledata.protobuf.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GeneratedGraphTest {
  private static final String PREFIX = "truffledata.lossless.";

  private static ProtoPlan.Model model(Limits limits, int depth, int wire) {
    return TruffleDataContracts.create(null, limits, depth, wire);
  }

  private static ProtoPlan.Model model() {
    return model(Limits.DEFAULT, 32, 1000000);
  }

  @Test
  void reusableInputSizesRemainCorrectAcrossCanonicalizationAndWidePresence() {
    var plans = model();
    var plan = plans.type(PREFIX + "Sample");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Sample(plan);
    for (byte[] wire :
        List.of(
            new byte[] {8, 0},
            new byte[] {8, (byte) 128, 0},
            new byte[] {8, -1, -1, -1, -1, 15},
            new byte[] {8, 1, 8, 2},
            new byte[] {8, 1, 8, 0},
            new byte[] {16, 0},
            new byte[] {32, 0},
            new byte[] {40, (byte) 129, (byte) 128, (byte) 128, (byte) 128, 16},
            new byte[] {80, 1, 90, 0},
            new byte[] {90, 2, 8, 1, 90, 3, 18, 1, 65},
            new byte[] {74, 2, 2, 4, 74, 1, 6},
            new byte[] {72, 2, 72, 4},
            new byte[] {74, 0},
            new byte[] {(byte) 178, 1, 2, 0, 1},
            new byte[] {18, 0},
            new byte[] {122, 0},
            new byte[] {26, 0},
            new byte[] {112, 2},
            new byte[] {112, 0},
            new byte[] {112, (byte) 128, 1},
            new byte[] {(byte) 248, 1, (byte) 129, 0},
            new byte[] {(byte) 136, 0, 1},
            region(8, new byte[] {8, 0}),
            region(8, new byte[] {18, 0}),
            region(7, new byte[] {10, 1, 97}))) {
      equivalent(plan, wire, access.decode(wire));
    }
    var sizing = plans.type(PREFIX + "WireSizing");
    var packedAccess = new TruffleDataContracts.Access_truffledata$lossless$WireSizing(sizing);
    for (byte[] wire :
        List.of(
            new byte[] {10, 3, 1, 2, 3},
            new byte[] {18, 0},
            new byte[] {18, 2, 0, 1},
            new byte[] {16, 1, 16, 0},
            new byte[] {18, 1, 2})) equivalent(sizing, wire, packedAccess.decode(wire));
    var wide = plans.type(PREFIX + "WideGraph");
    byte[] wire = wide.encode(wide.parseText("f33: 9 f34: 10 child {a: 7}"));
    var actual = new TruffleDataContracts.Access_truffledata$lossless$WideGraph(wide).decode(wire);
    equivalent(wide, wire, actual);
    assertTrue(actual.has(32));
    assertTrue(actual.has(33));
    assertTrue(actual.has(34));
    assertFalse(actual.has(31));
    assertEquals(9, actual.get_f33());
    assertEquals(10, actual.get_f34());
    var input =
        new GeneratedGraphInput(
            model(new Limits(1024, 256, Long.MAX_VALUE, 1000), 32, 1000).type(plan.name()),
            new byte[0]);
    assertTrue(input.encodedSizeReusable());
    input.discardEncodedSize();
    input.cells(Long.MAX_VALUE);
    input.cells(0);
    assertFalse(input.encodedSizeReusable());
    assertThrows(ContractViolation.class, () -> input.cells(1));
  }

  @Test
  void graphEncodingBudgetsCheckCanonicalExpansionAndReturnIndependentOutputs() {
    var full = model().type(PREFIX + "Sample");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Sample(full);
    byte[] wire = full.encode(full.parseText("child {a: 3 b: '€😀'} numbers: [1,2]"));
    var row = access.decode(wire);
    byte[] first = row.encode();
    first[0] ^= 1;
    assertArrayEquals(wire, row.encode());
    // Five-byte INT32 -1 input is legal but needs ten bytes when encoded canonically.
    byte[] negative = {8, -1, -1, -1, -1, 15};
    var narrow = model(Limits.DEFAULT, 32, negative.length).type(full.name());
    var direct =
        new TruffleDataContracts.Access_truffledata$lossless$Sample(narrow).decode(negative);
    assertThrows(ContractViolation.class, direct::encode);
    assertThrows(ContractViolation.class, () -> narrow.encode(narrow.decode(negative)));
    byte[] nested = region(8, negative);
    var nestedPlan = model(Limits.DEFAULT, 32, nested.length).type(full.name());
    var child =
        new TruffleDataContracts.Access_truffledata$lossless$Sample(nestedPlan).decode(nested);
    assertThrows(ContractViolation.class, child::encode);
    byte[] packed = region(1, new byte[] {-1, -1, -1, -1, 15});
    var repeated = model(Limits.DEFAULT, 32, packed.length).type(PREFIX + "RepeatedScalars");
    var list =
        new TruffleDataContracts.Access_truffledata$lossless$RepeatedScalars(repeated)
            .decode(packed);
    assertThrows(ContractViolation.class, list::encode);
  }

  @Test
  void generatedGraphBindingChecksReachableContractsBeforeDecoding() {
    var root = model().type(PREFIX + "Sample");
    Map<String, ProtoPlan.Type> definitions = new LinkedHashMap<>();
    var pending = new ArrayDeque<ProtoPlan>();
    pending.add(root);
    while (!pending.isEmpty()) {
      var plan = pending.removeFirst();
      if (definitions.putIfAbsent(plan.name(), plan.definition()) != null) continue;
      for (int i = 0; i < plan.size(); i++)
        if (plan.field(i).kind().message()) pending.add(plan.nested(i));
    }
    var child = Objects.requireNonNull(definitions.get(PREFIX + "Sample.Child"));
    var fields = new ArrayList<>(child.fields());
    var f = fields.get(0);
    fields.set(
        0,
        new ProtoPlan.FieldPlan(
            f.name(),
            27,
            f.kind(),
            f.repeated(),
            f.map(),
            f.explicitPresence(),
            f.required(),
            f.packed(),
            f.oneof(),
            f.typeName(),
            f.enumType(),
            f.defaultValue(),
            f.jsonName()));
    definitions.put(child.name(), new ProtoPlan.Type(child.name(), child.mapEntry(), fields));
    var changed =
        new ProtoPlan.Model(null, List.copyOf(definitions.values()), Limits.DEFAULT, 32, 1000000)
            .type(root.name());
    assertTrue(changed.matchesDefinition(root.definition()));
    assertThrows(
        ContractViolation.class,
        () -> new TruffleDataContracts.Access_truffledata$lossless$Sample(changed));
    // The unchanged recursive graph is accepted and preserves shared child plan identity.
    var valid =
        new TruffleDataContracts.Access_truffledata$lossless$Sample(root)
            .decode(new byte[] {66, 0});
    assertSame(root.nested(7), ((ProtoAccess) valid.get_child()).plan());
  }

  @Test
  void concreteGraphStorageRetainsDeclaredScalarWidths() throws Exception {
    var type = TruffleDataContracts.Message_truffledata$lossless$Sample.class;
    assertEquals(int.class, type.getDeclaredField("f0").getType());
    assertEquals(int.class, type.getDeclaredField("f4").getType());
    assertEquals(float.class, type.getDeclaredField("f11").getType());
    assertEquals(double.class, type.getDeclaredField("f12").getType());
    assertEquals(long.class, type.getDeclaredField("f20").getType());
  }

  @Test
  void generatedAndGenericGraphDecodersAgreeOnDeterministicWireMutations() {
    var plans = model();
    var sample = plans.type(PREFIX + "Sample");
    var legacy = plans.type(PREFIX + "Legacy");
    var required = plans.type(PREFIX + "Required");
    var repeated = plans.type(PREFIX + "RepeatedScalars");
    record Decoder(
        ProtoPlan plan,
        java.util.function.Function<byte[], GeneratedMessage> decode,
        String text) {}
    var decoders =
        List.of(
            new Decoder(
                sample,
                new TruffleDataContracts.Access_truffledata$lossless$Sample(sample)::decode,
                "count: 7 child {a: 3 b: 'hello'} numbers: [-3,4,5] selected_child {a: 7} children"
                    + " {key: 'a' value {b: 'map'}} blobs: ['abc','def'] counts {key: 7 value: 9}"),
            new Decoder(
                legacy,
                new TruffleDataContracts.Access_truffledata$lossless$Legacy(legacy)::decode,
                "Old {value: 9} statuses: [FIRST,SECOND] states {key: 'a' value: MAP_ONE}"),
            new Decoder(
                required,
                new TruffleDataContracts.Access_truffledata$lossless$Required(required)::decode,
                "count: 3 child {count: 4 child {count: 5}}"),
            new Decoder(
                repeated,
                new TruffleDataContracts.Access_truffledata$lossless$RepeatedScalars(repeated)
                    ::decode,
                "i32: [-1,123] f32: [1,4294967295] single: [-0.0,1.5] text: ['€','abc'] blob:"
                    + " ['xyz']"));
    var random = new Random(0x6aaf);
    for (var decoder : decoders) {
      byte[] seed = decoder.plan().encode(decoder.plan().parseText(decoder.text()));
      for (int attempt = 0; attempt < 1024; attempt++) {
        byte[] wire = Arrays.copyOf(seed, random.nextInt(seed.length + 8));
        for (int edit = 0; edit < 1 + attempt % 3 && wire.length > 0; edit++)
          wire[random.nextInt(wire.length)] = (byte) random.nextInt(256);
        ProtoRow expected;
        try {
          expected = decoder.plan().decode(wire);
        } catch (ContractViolation invalid) {
          assertThrows(
              ContractViolation.class,
              () -> decoder.decode().apply(wire),
              decoder.plan().name() + " attempt " + attempt);
          continue;
        }
        assertArrayEquals(
            decoder.plan().encode(expected),
            decoder.decode().apply(wire).encode(),
            decoder.plan().name() + " attempt " + attempt);
      }
    }
  }

  private static byte[] concat(byte[]... parts) {
    var result = new ByteArrayOutputStream();
    for (byte[] part : parts) result.writeBytes(part);
    return result.toByteArray();
  }

  private static byte[] varint(long value) {
    var result = new ByteArrayOutputStream();
    do {
      int octet = (int) value & 127;
      value >>>= 7;
      result.write(value == 0 ? octet : octet | 128);
    } while (value != 0);
    return result.toByteArray();
  }

  private static byte[] region(int number, byte[] contents) {
    return concat(varint(((long) number << 3) | 2), varint(contents.length), contents);
  }

  private static Descriptors.Descriptor descriptor(String name) throws Exception {
    var path =
        Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), "tests/native.pb");
    var files = DescriptorProtos.FileDescriptorSet.parseFrom(Files.readAllBytes(path));
    for (var file : files.getFileList())
      for (var message :
          Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0])
              .getMessageTypes()) if (message.getFullName().equals(name)) return message;
    throw new AssertionError(name);
  }

  private static void equivalent(ProtoPlan plan, byte[] wire, GeneratedMessage actual) {
    var expected = plan.decode(wire);
    assertSame(plan, actual.plan());
    assertArrayEquals(plan.encode(expected), actual.encode());
    for (int i = 0; i < plan.size(); i++)
      assertEquals(expected.has(i), actual.has(i), "field " + i);
  }

  @Test
  void concreteGraphsMatchGenericAndStockAndOwnTheirStorage() throws Exception {
    var plan = model().type(PREFIX + "Sample");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Sample(plan);
    var source =
        plan.parseText(
            "count: -5 explicit_count: 0 blob: '\\000\\377' status: 123"
                + " u32: 4294967295 u64: 18446744073709551615"
                + " children {key: 'a' value {a: 12 b: 'nested' child {b: 'leaf'}}}"
                + " child {a: 9 b: 'child'} numbers: [-2147483648,0,2147483647]"
                + " selected_child {a: 3} f: -0.0 d: 2.5 flag: true text: '€😀'"
                + " fixed32: 4294967295 fixed64: 18446744073709551615 sfixed32: -123"
                + " sfixed64: -456 sint64: -789 int64: -9223372036854775808"
                + " statuses: [READY,123] blobs: ['one','two']"
                + " counts {key: 18446744073709551615 value: 1} custom_value: 42");
    byte[] wire = plan.encode(source);
    var result = access.decode(wire);
    equivalent(plan, wire, result);
    var descriptor = descriptor(plan.name());
    assertEquals(
        DynamicMessage.parseFrom(descriptor, wire),
        DynamicMessage.parseFrom(descriptor, result.encode()));
    assertEquals(-5, result.get_count());
    var child = (TruffleDataContracts.Message_truffledata$lossless$Sample$Child) result.get_child();
    assertEquals("child", child.get_b());
    var data = DataLibrary.factory().getUncached();
    assertEquals(-5, data.readLong(result, 0));
    assertEquals(2.5, data.readDouble(result, 12));
    assertTrue(data.readBoolean(result, 13));
    assertSame(plan.schema(), data.getSchema(result));
    assertSame(child, access.get_child(result));
    byte[] snapshot = result.encode();
    Arrays.fill(wire, (byte) 0);
    assertArrayEquals(snapshot, result.encode());
    assertEquals(0L, result.get_blob().get(0));
    assertThrows(ContractViolation.class, () -> result.getLong(14));
    assertThrows(ContractViolation.class, () -> result.getDouble(0));
    assertThrows(ContractViolation.class, () -> result.getBoolean(0));
    assertThrows(ContractViolation.class, () -> result.get(-1));
    var other =
        new TruffleDataContracts.Access_truffledata$lossless$Sample(model().type(plan.name()));
    assertThrows(ContractViolation.class, () -> other.get_count(result));
    equivalent(plan, new byte[0], access.decode(new byte[0]));
  }

  @Test
  void repeatedScalarKindsAcceptPackedAndUnpackedSegments() throws Exception {
    var plan = model().type(PREFIX + "RepeatedScalars");
    var access = new TruffleDataContracts.Access_truffledata$lossless$RepeatedScalars(plan);
    var source =
        plan.parseText(
            "i32: [-1,2147483647] i64: [-9223372036854775808,1]"
                + " u32: [0,4294967295] u64: [0,18446744073709551615] s32: [-123,456]"
                + " s64: [-9223372036854775808,9223372036854775807] f32: [0,4294967295]"
                + " f64: [0,18446744073709551615] sf32: [-2147483648,1]"
                + " sf64: [-9223372036854775808,1] flag: [true,false] single: [-0.0,1.5]"
                + " real: [-0.0,2.5] text: ['','€😀'] blob: ['','\\000\\377']");
    byte[] packed = plan.encode(source);
    byte[] unpacked = concat(new byte[] {8}, varint(-7), new byte[] {13, 1, 0, 0, 0});
    // A wrong wire kind for a known field remains an unknown field.
    byte[] both = concat(packed, unpacked, packed);
    var actual = access.decode(both);
    equivalent(plan, both, actual);
    assertEquals(5, actual.get_i32().size());
    assertEquals(4, actual.get_text().size());
    assertEquals(4, actual.get_blob().size());
    var descriptor = descriptor(plan.name());
    assertEquals(
        DynamicMessage.parseFrom(descriptor, both),
        DynamicMessage.parseFrom(descriptor, actual.encode()));
    for (byte[] malformed :
        List.of(
            region(7, new byte[] {1}),
            region(8, new byte[] {1}),
            region(1, new byte[] {(byte) 128}),
            region(14, new byte[] {(byte) 0x80})))
      assertThrows(ContractViolation.class, () -> access.decode(malformed));
  }

  @Test
  void singularMessagesMergeAndOneofSwitchesDiscardPriorStates() {
    var plan = model().type(PREFIX + "Sample");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Sample(plan);
    byte[] first = plan.encode(plan.parseText("child {a: 3} selected_child {a: 7} numbers: [1,2]"));
    byte[] second =
        plan.encode(plan.parseText("child {b: 'merged'} selected_child {b: 'same'} numbers: [3]"));
    var merged = access.decode(concat(first, second));
    equivalent(plan, concat(first, second), merged);
    var child = (ProtoAccess) merged.get_child();
    assertEquals(3L, child.get(0));
    assertEquals("merged", child.get(1));
    var selected = (ProtoAccess) merged.get_selected_child();
    assertEquals(7L, selected.get(0));
    assertEquals("same", selected.get(1));
    byte[] switched = concat(first, plan.encode(plan.parseText("selected: 8")), second);
    var last = access.decode(switched);
    equivalent(plan, switched, last);
    assertEquals(0L, ((ProtoAccess) last.get_selected_child()).get(0));
    assertFalse(last.has(9));
    byte[] unpacked = {72, 2, 72, 4};
    equivalent(plan, unpacked, access.decode(unpacked));
    assertEquals(2, access.decode(unpacked).get_numbers().size());
  }

  @Test
  void groupsRequiredFieldsAndClosedEnumsPreserveUnknowns() {
    var plans = model();
    var legacy = plans.type(PREFIX + "Legacy");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Legacy(legacy);
    byte[] wire =
        concat(
            legacy.encode(legacy.parseText("Old {value: 3} states {key: 'a' value: MAP_ONE}")),
            new byte[] {34, 4, 1, (byte) 231, 7, 2, 24, (byte) 231, 7},
            region(9, new byte[] {10, 1, 98, 16, (byte) 231, 7}),
            new byte[] {(byte) 0xf8, 1, 9, (byte) 0xa3, 1, 8, 1, (byte) 0xa4, 1});
    var result = access.decode(wire);
    equivalent(legacy, wire, result);
    assertEquals(2, result.get_statuses().size());
    assertEquals(1, result.get_states().size());
    assertEquals(42, result.get_count());
    assertEquals("hello", result.get_text());
    assertEquals(2, result.get_status());
    for (byte[] bad :
        List.of(new byte[] {59}, new byte[] {59, 68}, new byte[] {60}, new byte[] {59, 64}))
      assertThrows(ContractViolation.class, () -> access.decode(bad));
    var required = plans.type(PREFIX + "Required");
    var requiredAccess = new TruffleDataContracts.Access_truffledata$lossless$Required(required);
    byte[] split = {8, 1, 18, 2, 18, 0, 18, 2, 8, 2, 18, 4, 18, 2, 8, 3};
    equivalent(required, split, requiredAccess.decode(split));
    assertThrows(ContractViolation.class, () -> requiredAccess.decode(new byte[0]));
    assertThrows(ContractViolation.class, () -> requiredAccess.decode(new byte[] {8, 1, 18, 0}));
  }

  @Test
  void mapsUseLastValueAndIntegerValueEqualityAndDefaultMessageValues() {
    var plan = model().type(PREFIX + "Sample");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Sample(plan);
    byte[] first =
        plan.encode(
            plan.parseText(
                "children {key: 'k' value {a: 1}} counts {key: 18446744073709551615 value: 1}"));
    byte[] second =
        plan.encode(
            plan.parseText(
                "children {key: 'k' value {b: 'last'}} counts {key: 18446744073709551615 value:"
                    + " 2}"));
    byte[] wire = concat(first, second, region(7, new byte[] {10, 1, 122}));
    var result = access.decode(wire);
    equivalent(plan, wire, result);
    assertEquals(1, result.get_counts().size());
    assertEquals(2L, ((ProtoAccess) result.get_counts().get(0)).get(1));
    assertEquals(2, result.get_children().size());
    var entry = (ProtoAccess) result.get_children().get(0);
    assertEquals("last", ((ProtoAccess) entry.get(1)).get(1));
    assertEquals(0L, ((ProtoAccess) entry.get(1)).get(0));
    assertInstanceOf(GeneratedMessage.class, ((ProtoAccess) result.get_children().get(1)).get(1));
    var shallow = model(Limits.DEFAULT, 2, 1000).type(plan.name());
    assertThrows(
        ContractViolation.class,
        () ->
            new TruffleDataContracts.Access_truffledata$lossless$Sample(shallow)
                .decode(region(7, new byte[] {10, 1, 122})));
  }

  @Test
  void parsedAndRetainedBudgetsIncludeDefaultsDiscardedValuesAndUnknowns() {
    var plans = model(new Limits(1024, 256, 262144, 17), 8, 1000);
    var plan = plans.type(PREFIX + "DefaultGraph");
    var access = new TruffleDataContracts.Access_truffledata$lossless$DefaultGraph(plan);
    // Absent root default is eight bytes; absent child default adds ten despite no string input.
    assertEquals("root", access.decode(new byte[0]).get_text());
    assertThrows(ContractViolation.class, () -> access.decode(new byte[] {10, 2, 8, 1}));
    var exact =
        new TruffleDataContracts.Access_truffledata$lossless$DefaultGraph(
            model(new Limits(1024, 256, 262144, 18), 8, 1000).type(plan.name()));
    assertEquals(
        "hello", ((ProtoAccess) exact.decode(new byte[] {10, 2, 8, 1}).get_child()).get(1));
    assertThrows(
        ContractViolation.class, () -> exact.decode(new byte[] {18, 2, 8, 1, 18, 2, 8, 2}));
    var bytesPlan = model(new Limits(1024, 256, 140, 1000), 8, 1000).type(PREFIX + "Sample");
    var bytesAccess = new TruffleDataContracts.Access_truffledata$lossless$Sample(bytesPlan);
    assertThrows(ContractViolation.class, () -> bytesAccess.decode(region(3, new byte[141])));
    byte[] oneof =
        concat(
            region(11, region(2, new byte[8])),
            new byte[] {80, 1},
            region(11, region(2, new byte[8])));
    var sample =
        new TruffleDataContracts.Access_truffledata$lossless$Sample(plans.type(PREFIX + "Sample"));
    assertThrows(ContractViolation.class, () -> sample.decode(oneof));
    assertThrows(
        ContractViolation.class,
        () ->
            sample.decode(
                concat(new byte[] {80, 1}, new byte[] {(byte) 0xfa, 1, 16}, new byte[16])));
    var shallow =
        new TruffleDataContracts.Access_truffledata$lossless$Sample(
            model(Limits.DEFAULT, 1, 1000).type(PREFIX + "Sample"));
    assertThrows(ContractViolation.class, () -> shallow.decode(new byte[] {66, 0}));
    assertThrows(
        ContractViolation.class, () -> shallow.decode(new byte[] {(byte) 0xa3, 1, (byte) 0xa4, 1}));
    var shortWire =
        new TruffleDataContracts.Access_truffledata$lossless$Sample(
            model(Limits.DEFAULT, 8, 1).type(PREFIX + "Sample"));
    assertThrows(ContractViolation.class, () -> shortWire.decode(new byte[] {8, 1}));
    assertThrows(ContractViolation.class, () -> bytesAccess.decode(region(9, new byte[140])));
    var defaultBytes =
        new TruffleDataContracts.Access_truffledata$lossless$DefaultGraph(
            model(new Limits(1024, 256, 140, 1000), 8, 1000).type(PREFIX + "DefaultGraph"));
    byte[][] entries = new byte[20][];
    Arrays.fill(entries, new byte[] {18, 2, 8, 1});
    // Parsing fits 140 cells. The retained default byte arrays bring the graph to 163.
    assertThrows(ContractViolation.class, () -> defaultBytes.decode(concat(entries)));
  }

  @Test
  void malformedGraphsRejectLengthVarintUtf8AndTagErrors() {
    var access =
        new TruffleDataContracts.Access_truffledata$lossless$Sample(
            model().type(PREFIX + "Sample"));
    for (byte[] bad :
        List.of(
            new byte[] {0},
            new byte[] {8},
            new byte[] {66, 2, 8},
            new byte[] {66, 0, 4},
            new byte[] {66, 1, 12},
            new byte[] {66, -1, -1, -1, -1, 127},
            new byte[] {66, 2, 10, 2},
            new byte[] {122, 1, (byte) 0x80},
            new byte[] {8, -1, -1, -1, -1, -1, -1, -1, -1, -1, 2},
            new byte[] {14},
            new byte[] {93},
            new byte[] {97},
            new byte[] {72, (byte) 128}))
      assertThrows(ContractViolation.class, () -> access.decode(bad), Arrays.toString(bad));
    byte[] unknown =
        concat(new byte[] {(byte) 0xf9, 1}, new byte[8], new byte[] {(byte) 0xfd, 1}, new byte[4]);
    equivalent(accessPlan(access), unknown, access.decode(unknown));
  }

  private static ProtoPlan accessPlan(
      TruffleDataContracts.Access_truffledata$lossless$Sample access) {
    return access.decode(new byte[0]).plan();
  }

  @Test
  void hostBuildersAcceptGeneratedChildrenWithExactPlanAndCumulativeBudgets() {
    var plans = model();
    var parent = plans.type(PREFIX + "Sample");
    var childPlan = plans.type(PREFIX + "Sample.Child");
    var child =
        new TruffleDataContracts.Access_truffledata$lossless$Sample$Child(childPlan)
            .decode(childPlan.encode(childPlan.parseText("a: 3 b: 'hello' child {a: 4}")));
    var row = parent.newBuilder().set(7, child).build();
    assertArrayEquals(
        parent.encode(parent.parseText("child {a: 3 b: 'hello' child {a: 4}}")),
        parent.encode(row));
    var foreign =
        new TruffleDataContracts.Access_truffledata$lossless$Sample$Child(
                model().type(childPlan.name()))
            .decode(new byte[0]);
    assertThrows(ContractViolation.class, () -> parent.newBuilder().set(7, foreign));
    ProtoAccess external =
        new ProtoAccess() {
          public ProtoPlan plan() {
            return childPlan;
          }

          public boolean has(int ordinal) {
            return true;
          }

          public Object get(int ordinal) {
            return child.get(ordinal);
          }
        };
    assertThrows(ContractViolation.class, () -> parent.newBuilder().set(7, external));
    var tiny = model(new Limits(1024, 256, 262144, 8), 8, 1000);
    var tinyChild = tiny.type(childPlan.name());
    var generated =
        new TruffleDataContracts.Access_truffledata$lossless$Sample$Child(tinyChild)
            .decode(region(2, new byte[] {97, 98, 99}));
    assertThrows(
        ContractViolation.class,
        () -> tiny.type(parent.name()).newBuilder().set(7, generated).set(14, "xy").build());
  }

  @Test
  void graphCursorRejectsInvalidPublicOperationsAndChecksOverflowBeforeAddition() {
    var plan = model().type(PREFIX + "Sample");
    var input = new GeneratedGraphInput(plan, new byte[0]);
    assertThrows(ContractViolation.class, () -> input.limit(-1));
    assertThrows(ContractViolation.class, () -> input.limit(1));
    assertThrows(ContractViolation.class, () -> input.cells(-1));
    assertThrows(ContractViolation.class, () -> input.message(1, 0));
    assertThrows(ContractViolation.class, () -> input.fixed(3));
    assertThrows(ContractViolation.class, () -> input.retain(null, -1));
    assertThrows(ContractViolation.class, () -> input.retainedCells(1, Long.MAX_VALUE));
    assertThrows(ContractViolation.class, () -> input.retainedCells(-1, 0));
    assertThrows(ContractViolation.class, () -> input.retainedBytes(1, Long.MAX_VALUE));
    assertThrows(ContractViolation.class, () -> input.retainedBytes(-1, 0));
    assertThrows(ContractViolation.class, () -> plan.nested(0));
  }
}
