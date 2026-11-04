package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.Descriptors.*;
import com.google.protobuf.DynamicMessage;
import dev.elide.truffledata.protobuf.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProtoDescriptorsTest {
  private static final int WIRE = 1_000_000;

  private static ProtoPlan compile(FileDescriptorSet set, String name) {
    return ProtoDescriptors.compile(null, set.toByteArray(), name, Limits.DEFAULT, 32, WIRE);
  }

  @Test
  void fixtureDefinitionsMatchStockIncludingDefaultsMapsAndPresence() throws Exception {
    Path path =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "tests/native.pb");
    var set = FileDescriptorSet.parseFrom(Files.readAllBytes(path));
    for (var file : set.getFileList()) {
      var stock = FileDescriptor.buildFrom(file, new FileDescriptor[0]);
      var pending = new ArrayDeque<>(stock.getMessageTypes());
      while (!pending.isEmpty()) {
        var descriptor = pending.removeFirst();
        var plan = compile(set, descriptor.getFullName());
        assertTrue(
            plan.matchesDefinition(ProtoCompiler.definitions(List.of(descriptor)).getFirst()),
            descriptor.getFullName());
        pending.addAll(descriptor.getNestedTypes());
        if (!descriptor.getOptions().getMapEntry()
            && descriptor.getFields().stream().noneMatch(FieldDescriptor::isRequired)) {
          var message = DynamicMessage.getDefaultInstance(descriptor);
          assertArrayEquals(
              message.toByteArray(),
              plan.encode(plan.decode(message.toByteArray())),
              descriptor.getFullName());
        }
      }
    }
    var flat = compile(set, "truffledata.lossless.Flat");
    // Definition equality above checks every field; binary interoperability uses a populated
    // message.
    var definition =
        set.getFileList().stream()
            .filter(f -> f.getSyntax().equals("proto3"))
            .findFirst()
            .orElseThrow();
    var stockFile = FileDescriptor.buildFrom(definition, new FileDescriptor[0]);
    var descriptor = stockFile.findMessageTypeByName("Flat");
    var field =
        descriptor.getFields().stream()
            .filter(
                f -> f.getType() == FieldDescriptor.Type.STRING && f.getContainingOneof() == null)
            .findFirst()
            .orElseThrow();
    var stock = DynamicMessage.newBuilder(descriptor).setField(field, "hello €").build();
    var row = flat.decode(stock.toByteArray());
    assertEquals("hello €", row.get(flat.schema().indexOf(field.getName())));
    assertEquals(stock, DynamicMessage.parseFrom(descriptor, flat.encode(row)));
  }

  private static DescriptorProto message(String name) {
    return DescriptorProto.newBuilder().setName(name).build();
  }

  private static FileDescriptorProto file(String name, String pkg, DescriptorProto... messages) {
    return FileDescriptorProto.newBuilder()
        .setName(name)
        .setPackage(pkg)
        .setSyntax("proto3")
        .addAllMessageType(List.of(messages))
        .build();
  }

  private static FileDescriptorSet set(FileDescriptorProto... files) {
    return FileDescriptorSet.newBuilder().addAllFile(List.of(files)).build();
  }

  private static DescriptorProto reference(String type) {
    return DescriptorProto.newBuilder()
        .setName("Root")
        .addField(
            FieldDescriptorProto.newBuilder()
                .setName("child")
                .setNumber(1)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(type))
        .build();
  }

  @Test
  void importsRelativeResolutionAndPublicVisibility() {
    var leaf = file("leaf.proto", "dep", message("Leaf"));
    var middle =
        file("middle.proto", "dep").toBuilder()
            .addDependency("leaf.proto")
            .addPublicDependency(0)
            .build();
    var root =
        file("root.proto", "dep", reference("Leaf")).toBuilder()
            .addDependency("middle.proto")
            .build();
    var plan = compile(set(root, middle, leaf), "dep.Root");
    assertEquals("dep.Leaf", plan.definition().fields().getFirst().typeName());
    assertEquals("dep.Root", compile(set(root, middle, leaf), "dep.Root").definition().name());
    var hidden = middle.toBuilder().clearPublicDependency().build();
    assertThrows(ContractViolation.class, () -> compile(set(root, hidden, leaf), "dep.Root"));
    assertThrows(ContractViolation.class, () -> compile(set(root, middle), "dep.Root"));
    var cycle = leaf.toBuilder().addDependency("root.proto").build();
    assertThrows(ContractViolation.class, () -> compile(set(root, middle, cycle), "dep.Root"));
  }

  @Test
  void optionDescriptorImportsDoNotRequireAnExtensionRuntime() {
    var metadata = com.google.protobuf.DescriptorProtos.getDescriptor().toProto();
    var root =
        file("root.proto", "sample", message("Root")).toBuilder()
            .addDependency(metadata.getName())
            .build();
    assertEquals("sample.Root", compile(set(root, metadata), "sample.Root").name());
    assertThrows(
        ContractViolation.class, () -> compile(set(metadata), "google.protobuf.FieldOptions"));
  }

  @Test
  void expandedNamesAndFieldCollectionsHaveBudgets() {
    var file =
        FileDescriptorProto.newBuilder()
            .setName("schema.proto")
            .setSyntax("proto3")
            .setPackage("p".repeat(100));
    for (int i = 0; i < 10; i++) file.addMessageType(message("Type" + i));
    byte[] descriptors = set(file.build()).toByteArray();
    assertThrows(
        ContractViolation.class,
        () ->
            ProtoDescriptors.compile(
                null,
                descriptors,
                "p".repeat(100) + ".Type0",
                new Limits(1, 256, 1000, 1024),
                32,
                WIRE));
    var many = DescriptorProto.newBuilder().setName("Root");
    for (int i = 0; i < 257; i++)
      many.addField(
          FieldDescriptorProto.newBuilder()
              .setName("field" + i)
              .setNumber(i + 1)
              .setType(FieldDescriptorProto.Type.TYPE_INT64));
    assertThrows(
        ContractViolation.class,
        () -> compile(set(file("fields.proto", "sample", many.build())), "sample.Root"));
  }

  private static byte[] append(byte[] prefix, byte[] suffix) {
    byte[] joined = Arrays.copyOf(prefix, prefix.length + suffix.length);
    System.arraycopy(suffix, 0, joined, prefix.length, suffix.length);
    return joined;
  }

  @Test
  void unknownMetadataWireKindsAndMalformedGroups() {
    byte[] base = set(file("schema.proto", "sample", message("Root"))).toByteArray();
    byte[] unknown = {
      24, 1, 33, 0, 0, 0, 0, 0, 0, 0, 0, 42, 2, 1, 2, 51, 8, 1, 17, 0, 0, 0, 0, 0, 0, 0, 0, 26, 1,
      9, 37, 0, 0, 0, 0, 52, 61, 0, 0, 0, 0
    };
    assertEquals(
        "sample.Root",
        ProtoDescriptors.compile(
                null, append(base, unknown), "sample.Root", Limits.DEFAULT, 32, WIRE)
            .name());
    for (byte[] invalid :
        new byte[][] {
          {33},
          {61},
          {51},
          {51, 60},
          {51, 26, 3, 1},
          {51, 8},
          {24, -1, -1, -1, -1, -1, -1, -1, -1, -1, 2}
        })
      assertThrows(
          ContractViolation.class,
          () ->
              ProtoDescriptors.compile(
                  null, append(base, invalid), "sample.Root", Limits.DEFAULT, 32, WIRE));
    byte[] deep = new byte[258];
    Arrays.fill(deep, 0, 129, (byte) 27);
    Arrays.fill(deep, 129, 258, (byte) 28);
    assertThrows(
        ContractViolation.class,
        () ->
            ProtoDescriptors.compile(
                null, append(base, deep), "sample.Root", Limits.DEFAULT, 32, WIRE));
    assertThrows(
        ContractViolation.class,
        () ->
            ProtoDescriptors.compile(
                null, new byte[] {10, 3, 10, 1, -1}, "sample.Root", Limits.DEFAULT, 32, WIRE));
  }

  @Test
  void rejectsInconsistentSchemaDeclarations() {
    var field =
        FieldDescriptorProto.newBuilder()
            .setName("value")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_INT32)
            .build();
    var root = DescriptorProto.newBuilder().setName("Root").addField(field).build();
    for (var invalid :
        List.of(
            root.toBuilder().addReservedName("value").build(),
            root.toBuilder()
                .addReservedRange(DescriptorProto.ReservedRange.newBuilder().setStart(1).setEnd(2))
                .build(),
            root.toBuilder().setField(0, field.toBuilder().setNumber(0)).build(),
            root.toBuilder().setField(0, field.toBuilder().setOneofIndex(0)).build(),
            root.toBuilder().setField(0, field.toBuilder().setProto3Optional(true)).build(),
            root.toBuilder().setField(0, field.toBuilder().setDefaultValue("1")).build(),
            root.toBuilder()
                .setField(0, field.toBuilder().setLabel(FieldDescriptorProto.Label.LABEL_REQUIRED))
                .build(),
            root.toBuilder()
                .addOneofDecl(OneofDescriptorProto.newBuilder().setName("same"))
                .addOneofDecl(OneofDescriptorProto.newBuilder().setName("same"))
                .build())) {
      assertThrows(
          ContractViolation.class,
          () -> compile(set(file("schema.proto", "sample", invalid)), "sample.Root"));
    }
    assertThrows(
        ContractViolation.class,
        () -> compile(set(file("schema.proto", "sample", root, root)), "sample.Root"));
    var weak = file("schema.proto", "sample", root).toBuilder().addWeakDependency(7).build();
    assertThrows(ContractViolation.class, () -> compile(set(weak), "sample.Root"));
    var bool =
        field.toBuilder().setType(FieldDescriptorProto.Type.TYPE_BOOL).setDefaultValue("invalid");
    var legacy =
        file("schema.proto", "sample", root.toBuilder().setField(0, bool).build()).toBuilder()
            .setSyntax("proto2")
            .build();
    assertThrows(ContractViolation.class, () -> compile(set(legacy), "sample.Root"));
  }

  @Test
  void descriptorStringDefaultsEscapeWithoutStockAndPackedImportsResolve() {
    String value = "slash\\ quote\" line\n return\r tab\t é";
    var field =
        FieldDescriptorProto.newBuilder()
            .setName("value")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setDefaultValue(value);
    var root = DescriptorProto.newBuilder().setName("Root").addField(field).build();
    var legacy = file("schema.proto", "sample", root).toBuilder().setSyntax("proto2").build();
    assertEquals(value, compile(set(legacy), "sample.Root").decode(new byte[0]).get(0));
    var leaf = file("leaf.proto", "dep", message("Leaf"));
    var packed =
        com.google.protobuf.UnknownFieldSet.newBuilder()
            .addField(
                10,
                com.google.protobuf.UnknownFieldSet.Field.newBuilder()
                    .addLengthDelimited(com.google.protobuf.ByteString.copyFrom(new byte[] {0}))
                    .build())
            .build();
    var middle =
        file("middle.proto", "dep").toBuilder()
            .addDependency("leaf.proto")
            .setUnknownFields(packed)
            .build();
    var top =
        file("root.proto", "dep", reference("Leaf")).toBuilder()
            .addDependency("middle.proto")
            .build();
    assertEquals("dep.Leaf", compile(set(top, middle, leaf), "dep.Root").field(0).typeName());
  }

  @Test
  void rejectsInvalidDescriptorsAndBudgets() {
    var base = file("schema.proto", "sample", message("Root"));
    var valid = set(base);
    assertThrows(ContractViolation.class, () -> compile(set(base, base), "sample.Root"));
    assertThrows(ContractViolation.class, () -> compile(valid, "missing"));
    assertThrows(
        ContractViolation.class,
        () -> compile(set(base.toBuilder().setSyntax("editions").build()), "sample.Root"));
    assertThrows(
        ContractViolation.class,
        () -> compile(set(base.toBuilder().addPublicDependency(3).build()), "sample.Root"));
    assertThrows(
        ContractViolation.class,
        () -> compile(set(file("schema.proto", "sample", reference("Missing"))), "sample.Root"));
    byte[] bytes = valid.toByteArray();
    assertThrows(
        ContractViolation.class,
        () ->
            ProtoDescriptors.compile(
                null, bytes, "sample.Root", Limits.DEFAULT, 32, bytes.length - 1));
    assertThrows(
        ContractViolation.class,
        () ->
            ProtoDescriptors.compile(
                null, bytes, "sample.Root", new Limits(1, 1, 1, 1000), 32, WIRE));
    assertThrows(
        ContractViolation.class,
        () ->
            ProtoDescriptors.compile(
                null, bytes, "sample.Root", new Limits(1, 10, 1000, 0), 32, WIRE));
    for (byte[] malformed : new byte[][] {{0}, {10, -1}, {10, 4, 1}, {12}, {10, 2, 10, -1}})
      assertThrows(
          ContractViolation.class,
          () -> ProtoDescriptors.compile(null, malformed, "sample.Root", Limits.DEFAULT, 32, WIRE));
    for (int end = 1; end < bytes.length; end++) {
      byte[] truncated = Arrays.copyOf(bytes, end);
      assertThrows(
          ContractViolation.class,
          () -> ProtoDescriptors.compile(null, truncated, "sample.Root", Limits.DEFAULT, 32, WIRE));
    }
  }
}
