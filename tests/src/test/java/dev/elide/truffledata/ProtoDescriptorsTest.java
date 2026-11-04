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
