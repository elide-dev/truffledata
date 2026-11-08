package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.Descriptors.*;
import dev.elide.truffledata.host.ExplicitHost;
import dev.elide.truffledata.protobuf.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtoExplicitSourceTest {
  private static FieldDescriptorProto.Builder field(
      String name, int number, FieldDescriptorProto.Type type) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(type)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
  }

  private static FileDescriptor descriptor(boolean messageSet) throws Exception {
    var payload =
        DescriptorProto.newBuilder()
            .setName("Payload")
            .addField(field("n", 1, FieldDescriptorProto.Type.TYPE_INT32));
    var box =
        DescriptorProto.newBuilder()
            .setName("Box")
            .setOptions(MessageOptions.newBuilder().setMessageSetWireFormat(messageSet))
            .addExtensionRange(
                DescriptorProto.ExtensionRange.newBuilder().setStart(100).setEnd(200));
    return FileDescriptor.buildFrom(
        FileDescriptorProto.newBuilder()
            .setName("explicit.proto")
            .setPackage("explicit")
            .setSyntax("proto2")
            .addMessageType(payload)
            .addMessageType(box)
            .addExtension(
                field("payload", 100, FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".explicit.Payload")
                    .setExtendee(".explicit.Box"))
            .build(),
        new FileDescriptor[0]);
  }

  private static ProtoPlan plan(int maxDepth) {
    var payload =
        new ProtoPlan.Type(
            "explicit.Payload",
            false,
            List.of(
                new ProtoPlan.FieldPlan(
                    "n",
                    1,
                    ProtoPlan.Kind.INT32,
                    false,
                    false,
                    true,
                    false,
                    false,
                    -1,
                    "",
                    null,
                    0L)));
    var box =
        new ProtoPlan.Type(
            "explicit.Box",
            false,
            List.of(
                new ProtoPlan.FieldPlan(
                    "explicit.payload",
                    100,
                    ProtoPlan.Kind.MESSAGE,
                    false,
                    false,
                    true,
                    false,
                    false,
                    -1,
                    "explicit.Payload",
                    null,
                    Missing.INSTANCE,
                    "[explicit.payload]")));
    return new ProtoPlan.Model(null, List.of(box, payload), Limits.DEFAULT, maxDepth, 65536)
        .type("explicit.Box");
  }

  private static ProtoPlan.FieldPlan extensionField(
      String name,
      int number,
      ProtoPlan.Kind kind,
      boolean repeated,
      String type,
      Object fallback) {
    return new ProtoPlan.FieldPlan(
        name,
        number,
        kind,
        repeated,
        false,
        !repeated,
        false,
        false,
        -1,
        type,
        null,
        fallback,
        "[" + name + "]");
  }

  @Test
  void generatedAndDynamicHostsShareExplicitExtensionAndMessageSetSemantics() throws Exception {
    var payload = ExplicitHost.Payload.newBuilder().setN(42).build();
    var box = ExplicitHost.Box.newBuilder().setExtension(ExplicitHost.payload, payload).build();
    var ordinary = plan(16);
    var ordinarySource =
        ProtoCompiler.bind(
            ordinary,
            ExplicitHost.Box.getDescriptor(),
            List.of(ExplicitHost.payload.getDescriptor()));
    for (Message host :
        List.of(
            box,
            DynamicMessage.parseFrom(
                ExplicitHost.Box.getDescriptor(), box.toByteArray(), extensionRegistry()))) {
      var row = ordinarySource.snapshot(host);
      assertEquals(42L, ((ProtoAccess) row.get(0)).getLong(0));
      assertArrayEquals(ordinary.encode(ordinary.decode(host.toByteArray())), ordinary.encode(row));
    }
    var definitions = new java.util.ArrayList<ProtoPlan.Type>();
    definitions.add(
        new ProtoPlan.Type(
            "explicit.MessageBox",
            false,
            List.of(
                extensionField(
                    "explicit.message_payload",
                    100,
                    ProtoPlan.Kind.MESSAGE,
                    false,
                    "explicit.Payload",
                    Missing.INSTANCE))));
    definitions.add(ordinary.nested(0).definition());
    var messagePlan =
        new ProtoPlan.Model(null, definitions, Limits.DEFAULT, 16, 65536)
            .type("explicit.MessageBox");
    var messageSource =
        ProtoCompiler.bind(
            messagePlan,
            ExplicitHost.MessageBox.getDescriptor(),
            List.of(ExplicitHost.messagePayload.getDescriptor()));
    var unknown =
        UnknownFieldSet.newBuilder()
            .addField(
                101,
                UnknownFieldSet.Field.newBuilder()
                    .addLengthDelimited(ByteString.copyFromUtf8("unknown"))
                    .build())
            .build();
    var messageSet =
        ExplicitHost.MessageBox.newBuilder()
            .setExtension(ExplicitHost.messagePayload, payload)
            .setUnknownFields(unknown)
            .build();
    for (Message host :
        List.of(
            messageSet,
            DynamicMessage.parseFrom(
                ExplicitHost.MessageBox.getDescriptor(),
                messageSet.toByteArray(),
                extensionRegistry()))) {
      var row = messageSource.snapshot(host);
      assertEquals(42L, ((ProtoAccess) row.get(0)).getLong(0));
      assertArrayEquals(unknown.toByteArray(), row.unknownFieldBytes());
    }
    var scalarPlan =
        new ProtoPlan.Model(
                null,
                List.of(
                    new ProtoPlan.Type(
                        "explicit.ScalarBox",
                        false,
                        List.of(
                            extensionField(
                                "explicit.count", 100, ProtoPlan.Kind.INT32, false, "", 0L),
                            extensionField(
                                "explicit.text", 101, ProtoPlan.Kind.STRING, true, "", "")))),
                Limits.DEFAULT,
                16,
                65536)
            .type("explicit.ScalarBox");
    var scalarSource =
        ProtoCompiler.bind(
            scalarPlan,
            ExplicitHost.ScalarBox.getDescriptor(),
            List.of(ExplicitHost.count.getDescriptor(), ExplicitHost.text.getDescriptor()));
    var scalar =
        ExplicitHost.ScalarBox.newBuilder()
            .setExtension(ExplicitHost.count, 7)
            .addExtension(ExplicitHost.text, "x")
            .addExtension(ExplicitHost.text, "y")
            .build();
    for (Message host :
        List.of(
            scalar,
            DynamicMessage.parseFrom(
                ExplicitHost.ScalarBox.getDescriptor(),
                scalar.toByteArray(),
                extensionRegistry()))) {
      var row = scalarSource.snapshot(host);
      assertEquals(7L, row.getLong(0));
      assertEquals(2, row.getSize(1));
      assertArrayEquals(
          scalarPlan.encode(scalarPlan.decode(host.toByteArray())), scalarPlan.encode(row));
    }
    var maps =
        ExplicitHost.ClosedMaps.newBuilder()
            .putItems("a", ExplicitHost.ClosedMaps.Color.GREEN)
            .build();
    var mapsPlan =
        ProtoCompiler.compile(
            null, ExplicitHost.ClosedMaps.getDescriptor(), Limits.DEFAULT, 16, 65536);
    var mapsSource = ProtoCompiler.bind(mapsPlan, ExplicitHost.ClosedMaps.getDescriptor());
    assertEquals(ProtoCompiler.Source.Ingestion.WIRE_NORMALIZED, mapsSource.ingestion());
    for (Message host :
        List.of(
            maps,
            DynamicMessage.parseFrom(ExplicitHost.ClosedMaps.getDescriptor(), maps.toByteArray())))
      assertArrayEquals(
          mapsPlan.encode(mapsPlan.decode(host.toByteArray())),
          mapsPlan.encode(mapsSource.snapshot(host)));

    definitions.addAll(ProtoCompiler.definitions(List.of(ExplicitHost.ClosedMaps.getDescriptor())));
    definitions.add(
        new ProtoPlan.Type(
            "explicit.Combined",
            false,
            List.of(
                new ProtoPlan.FieldPlan(
                    "box",
                    1,
                    ProtoPlan.Kind.MESSAGE,
                    false,
                    false,
                    true,
                    false,
                    false,
                    -1,
                    "explicit.MessageBox",
                    null,
                    Missing.INSTANCE),
                new ProtoPlan.FieldPlan(
                    "maps",
                    2,
                    ProtoPlan.Kind.MESSAGE,
                    false,
                    false,
                    true,
                    false,
                    false,
                    -1,
                    "explicit.ClosedMaps",
                    null,
                    Missing.INSTANCE))));
    var combined =
        new ProtoPlan.Model(null, definitions, Limits.DEFAULT, 16, 65536).type("explicit.Combined");
    var unsupported =
        ProtoCompiler.bind(
            combined,
            ExplicitHost.Combined.getDescriptor(),
            List.of(ExplicitHost.messagePayload.getDescriptor()));
    assertEquals(ProtoCompiler.Source.Ingestion.UNSUPPORTED, unsupported.ingestion());
    assertThrows(
        UnsupportedOperationException.class,
        () -> unsupported.snapshot(ExplicitHost.Combined.getDefaultInstance()));
  }

  private static ExtensionRegistry extensionRegistry() {
    var registry = ExtensionRegistry.newInstance();
    ExplicitHost.registerAllExtensions(registry);
    return registry;
  }

  @Test
  void extensionsAndMessageSetBindDirectlyAndDetachHostStorage() throws Exception {
    for (boolean messageSet : List.of(false, true)) {
      var file = descriptor(messageSet);
      var box = file.findMessageTypeByName("Box");
      var payload = file.findMessageTypeByName("Payload");
      var extension = file.findExtensionByName("payload");
      var plan = plan(16);
      var source = ProtoCompiler.bind(plan, box, List.of(extension));
      assertEquals(ProtoCompiler.Source.Ingestion.DIRECT, source.ingestion());
      var child =
          DynamicMessage.newBuilder(payload).setField(payload.findFieldByNumber(1), 42).build();
      var unknown =
          UnknownFieldSet.newBuilder()
              .addField(
                  101,
                  UnknownFieldSet.Field.newBuilder()
                      .addLengthDelimited(ByteString.copyFrom(new byte[] {8, 9}))
                      .build())
              .build();
      var host =
          DynamicMessage.newBuilder(box).setField(extension, child).setUnknownFields(unknown);
      var result = source.snapshot(host);
      host.clear();
      assertEquals(42L, ((ProtoAccess) result.get(0)).getLong(0));
      byte[] normalized = {(byte) 0xA2, 6, 2, 8, 42, (byte) 0xAA, 6, 2, 8, 9};
      assertArrayEquals(plan.encode(plan.decode(normalized)), plan.encode(result));
      assertArrayEquals(unknown.toByteArray(), result.unknownFieldBytes());
      assertThrows(
          ContractViolation.class,
          () -> ProtoCompiler.bind(plan, box, List.of(extension, extension)));
      assertThrows(ContractViolation.class, () -> ProtoCompiler.bind(plan, box));
      var shallow = ProtoCompiler.bind(plan(1), box, List.of(extension));
      assertThrows(
          ContractViolation.class,
          () -> shallow.snapshot(DynamicMessage.newBuilder(box).setField(extension, child)));
    }
  }

  @Test
  void scalarAndRepeatedExtensionsUseTheRegisteredOrdinals() throws Exception {
    var box =
        DescriptorProto.newBuilder()
            .setName("Box")
            .addExtensionRange(
                DescriptorProto.ExtensionRange.newBuilder().setStart(100).setEnd(200));
    var file =
        FileDescriptor.buildFrom(
            FileDescriptorProto.newBuilder()
                .setName("scalars.proto")
                .setPackage("scalar")
                .setSyntax("proto2")
                .addMessageType(box)
                .addExtension(
                    field("count", 100, FieldDescriptorProto.Type.TYPE_INT32)
                        .setExtendee(".scalar.Box"))
                .addExtension(
                    field("text", 101, FieldDescriptorProto.Type.TYPE_STRING)
                        .setExtendee(".scalar.Box")
                        .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED))
                .build(),
            new FileDescriptor[0]);
    var type = file.findMessageTypeByName("Box");
    var count = file.findExtensionByName("count");
    var text = file.findExtensionByName("text");
    var definition =
        new ProtoPlan.Type(
            "scalar.Box",
            false,
            List.of(
                new ProtoPlan.FieldPlan(
                    "scalar.count",
                    100,
                    ProtoPlan.Kind.INT32,
                    false,
                    false,
                    true,
                    false,
                    false,
                    -1,
                    "",
                    null,
                    0L,
                    "[scalar.count]"),
                new ProtoPlan.FieldPlan(
                    "scalar.text",
                    101,
                    ProtoPlan.Kind.STRING,
                    true,
                    false,
                    false,
                    false,
                    false,
                    -1,
                    "",
                    null,
                    "",
                    "[scalar.text]")));
    var plan =
        new ProtoPlan.Model(null, List.of(definition), Limits.DEFAULT, 8, 1024).type("scalar.Box");
    var source = ProtoCompiler.bind(plan, type, List.of(count, text));
    var host =
        DynamicMessage.newBuilder(type)
            .setField(count, 7)
            .addRepeatedField(text, "x")
            .addRepeatedField(text, "y");
    var row = source.snapshot(host);
    assertEquals(7L, row.getLong(0));
    assertEquals(2, ((DataArray) row.get(1)).size());
    assertArrayEquals(plan.encode(plan.decode(host.build().toByteArray())), plan.encode(row));
    host.clear();
    assertEquals("x", ((DataArray) row.get(1)).get(0));
  }

  @Test
  void nestedMessageSetUnknownPayloadsArePreservedWithoutSizingHostMessages() throws Exception {
    var file = descriptor(true);
    var box = file.findMessageTypeByName("Box");
    var extension = file.findExtensionByName("payload");
    var outerFile =
        FileDescriptor.buildFrom(
            FileDescriptorProto.newBuilder()
                .setName("outer.proto")
                .setPackage("outer")
                .setSyntax("proto2")
                .addDependency("explicit.proto")
                .addMessageType(
                    DescriptorProto.newBuilder()
                        .setName("Root")
                        .addField(
                            field("box", 1, FieldDescriptorProto.Type.TYPE_MESSAGE)
                                .setTypeName(".explicit.Box")))
                .build(),
            new FileDescriptor[] {file});
    var root = outerFile.findMessageTypeByName("Root");
    var base = plan(16).layout();
    var definitions = new java.util.ArrayList<ProtoPlan.Type>();
    definitions.add(
        new ProtoPlan.Type(
            "outer.Root",
            false,
            List.of(
                new ProtoPlan.FieldPlan(
                    "box",
                    1,
                    ProtoPlan.Kind.MESSAGE,
                    false,
                    false,
                    true,
                    false,
                    false,
                    -1,
                    "explicit.Box",
                    null,
                    Missing.INSTANCE))));
    for (int i = 0; i < base.types(); i++) definitions.add(base.plan(i).definition());
    var plan = new ProtoPlan.Model(null, definitions, Limits.DEFAULT, 16, 1024).type("outer.Root");
    var unknown =
        UnknownFieldSet.newBuilder()
            .addField(
                101,
                UnknownFieldSet.Field.newBuilder()
                    .addLengthDelimited(ByteString.copyFromUtf8("payload"))
                    .build())
            .build();
    var host =
        DynamicMessage.newBuilder(root)
            .setField(
                root.findFieldByNumber(1),
                DynamicMessage.newBuilder(box).setUnknownFields(unknown).build())
            .build();
    var source = ProtoCompiler.bind(plan, root, List.of(extension));
    assertEquals(ProtoCompiler.Source.Ingestion.DIRECT, source.ingestion());
    Message guarded =
        (Message)
            java.lang.reflect.Proxy.newProxyInstance(
                Message.class.getClassLoader(),
                new Class<?>[] {Message.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getSerializedSize")
                      || method.getName().equals("toByteArray"))
                    throw new AssertionError("Direct ingestion serialized its host message");
                  return method.invoke(host, args);
                });
    ProtoRow row = source.snapshot(guarded);
    assertArrayEquals(unknown.toByteArray(), ((ProtoRow) row.get(0)).unknownFieldBytes());
  }

  @Test
  void closedEnumMapFallbackPreflightsBeforeHostSizing() throws Exception {
    var color =
        EnumDescriptorProto.newBuilder()
            .setName("Color")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("RED").setNumber(0));
    var entry =
        DescriptorProto.newBuilder()
            .setName("Entry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true))
            .addField(field("key", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(
                field("value", 2, FieldDescriptorProto.Type.TYPE_ENUM).setTypeName(".maps.Color"));
    var root =
        DescriptorProto.newBuilder()
            .setName("Root")
            .addNestedType(entry)
            .addField(
                field("items", 1, FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".maps.Root.Entry")
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED));
    var file =
        FileDescriptor.buildFrom(
            FileDescriptorProto.newBuilder()
                .setName("maps.proto")
                .setPackage("maps")
                .setSyntax("proto2")
                .addEnumType(color)
                .addMessageType(root)
                .build(),
            new FileDescriptor[0]);
    var type = file.findMessageTypeByName("Root");
    var entryType = type.findNestedTypeByName("Entry");
    var plan = ProtoCompiler.compile(null, type, new Limits(100, 100, 100, 4), 8, 1024);
    var source = ProtoCompiler.bind(plan, type);
    assertEquals(ProtoCompiler.Source.Ingestion.WIRE_NORMALIZED, source.ingestion());
    var host =
        DynamicMessage.newBuilder(type)
            .addRepeatedField(
                type.findFieldByNumber(1),
                DynamicMessage.newBuilder(entryType)
                    .setField(entryType.findFieldByNumber(1), "over-budget")
                    .build())
            .build();
    Message guarded =
        (Message)
            java.lang.reflect.Proxy.newProxyInstance(
                Message.class.getClassLoader(),
                new Class<?>[] {Message.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getSerializedSize")
                      || method.getName().equals("toByteArray"))
                    throw new AssertionError("Fallback sized its host before budget validation");
                  return method.invoke(host, args);
                });
    assertThrows(ContractViolation.class, () -> source.snapshot(guarded));
    // Aggregate wire size is also bounded before host sizing, even if each scalar fits.
    var wireBound = ProtoCompiler.compile(null, type, Limits.DEFAULT, 8, 8);
    assertThrows(
        ContractViolation.class, () -> ProtoCompiler.bind(wireBound, type).snapshot(guarded));
    var unrestricted = ProtoCompiler.compile(null, type, Limits.DEFAULT, 8, 1024);
    var normalized = ProtoCompiler.bind(unrestricted, type).snapshot(host);
    assertArrayEquals(
        unrestricted.encode(unrestricted.decode(host.toByteArray())),
        unrestricted.encode(normalized));

    // Unknown closed enum map values follow the same wire normalization as protobuf decoding.
    var unknownEntry =
        DynamicMessage.newBuilder(entryType)
            .setField(entryType.findFieldByNumber(1), "unknown")
            .setField(
                entryType.findFieldByNumber(2),
                file.findEnumTypeByName("Color").findValueByNumberCreatingIfUnknown(99))
            .build();
    var unknownHost =
        DynamicMessage.newBuilder(type)
            .addRepeatedField(type.findFieldByNumber(1), unknownEntry)
            .build();
    assertArrayEquals(
        unrestricted.encode(unrestricted.decode(unknownHost.toByteArray())),
        unrestricted.encode(ProtoCompiler.bind(unrestricted, type).snapshot(unknownHost)));
  }

  @Test
  void emptyExtensionRangeAndResolvedEditionsFieldsAreEligible() throws Exception {
    var file = descriptor(false);
    var box = file.findMessageTypeByName("Box");
    var emptyPlan =
        new ProtoPlan.Model(
                null,
                List.of(new ProtoPlan.Type("explicit.Box", false, List.of())),
                Limits.DEFAULT,
                8,
                1024)
            .type("explicit.Box");
    assertEquals(
        ProtoCompiler.Source.Ingestion.DIRECT, ProtoCompiler.bind(emptyPlan, box).ingestion());
    var edition =
        FileDescriptor.buildFrom(
            FileDescriptorProto.newBuilder()
                .setName("edition.proto")
                .setPackage("edition")
                .setSyntax("editions")
                .setEdition(Edition.EDITION_2023)
                .addMessageType(
                    DescriptorProto.newBuilder()
                        .setName("Row")
                        .addField(field("n", 1, FieldDescriptorProto.Type.TYPE_INT32)))
                .build(),
            new FileDescriptor[0]);
    var type = edition.findMessageTypeByName("Row");
    var resolved =
        new ProtoPlan.Model(
                null,
                List.of(
                    new ProtoPlan.Type(
                        "edition.Row",
                        false,
                        List.of(
                            new ProtoPlan.FieldPlan(
                                "n",
                                1,
                                ProtoPlan.Kind.INT32,
                                false,
                                false,
                                type.findFieldByNumber(1).hasPresence(),
                                false,
                                false,
                                -1,
                                "",
                                null,
                                0L)))),
                Limits.DEFAULT,
                8,
                1024)
            .type("edition.Row");
    var source = ProtoCompiler.bind(resolved, type);
    var row =
        source.snapshot(DynamicMessage.newBuilder(type).setField(type.findFieldByNumber(1), 0));
    assertTrue(row.has(0));
    assertEquals(0L, row.getLong(0));
  }
}
