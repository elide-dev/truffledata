package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.Descriptors.*;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.oracle.truffle.api.interop.InteropLibrary;
import dev.elide.truffledata.protobuf.ProtobufAdapter;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ProtobufTest {
  @Test
  void protovalidateOptionsCompileFromRealDescriptorSetWithUnknownExtensions() throws Exception {
    var file =
        java.nio.file.Path.of(
            java.util.Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            java.util.Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "tests/validation.pb");
    var set = FileDescriptorSet.parseFrom(java.nio.file.Files.readAllBytes(file));
    try (var context = org.graalvm.polyglot.Context.newBuilder("td-test").build()) {
      context.initialize("td-test");
      context.enter();
      try {
        var plain =
            ProtobufAdapter.fromDescriptorSet(
                TestLanguage.current().language(),
                set,
                "truffledata.validation.Sample",
                Limits.DEFAULT,
                16);
        var descriptor = plain.descriptor();
        var adapter =
            new ProtobufAdapter(
                TestLanguage.current().language(),
                descriptor,
                Limits.DEFAULT,
                16,
                java.util.Map.of(),
                true);
        var schema = adapter.schema();
        assertFalse(schema.field(0).optional());
        assertEquals(
            new Constraints.Length(2, 3, Constraints.LengthUnit.CODE_POINTS),
            schema.field(0).constraints().length());
        assertEquals(
            new Constraints.IntegerRange(0, 127), schema.field(1).constraints().integerRange());
        assertNull(
            schema.field(5).constraints().integerRange(),
            "Excluded intervals are not contiguous facts");
        assertNull(
            schema.field(6).constraints().integerRange(),
            "Conditional rules cannot become unconditional facts");
        assertTrue(schema.field(7).optional());
        assertEquals(
            10,
            java.util.Objects.requireNonNull(schema.field(7).constraints().integerRange()).min());
        assertTrue(schema.field(8).optional());
        assertNull(schema.field(8).constraints().length());
        var builder =
            DynamicMessage.newBuilder(descriptor)
                .setField(descriptor.findFieldByName("name"), "😀a")
                .setField(descriptor.findFieldByName("small"), 42)
                .addRepeatedField(descriptor.findFieldByName("numbers"), 1)
                .setField(
                    descriptor.findFieldByName("blob"),
                    com.google.protobuf.ByteString.copyFrom(new byte[] {1, 2}));
        var map = descriptor.findFieldByName("pairs");
        var entry =
            DynamicMessage.newBuilder(map.getMessageType())
                .setField(map.getMessageType().findFieldByName("key"), "key")
                .setField(map.getMessageType().findFieldByName("value"), 1)
                .build();
        builder.addRepeatedField(map, entry);
        var valid = builder.build();
        // Build the option extension against canonical generated descriptor types, then parse
        // registered options as generated Java schemas do instead of retaining unknown fields.
        var validationFile =
            set.getFileList().stream()
                .filter(value -> value.getName().equals("validate.proto"))
                .findFirst()
                .orElseThrow();
        var validationDescriptor =
            FileDescriptor.buildFrom(
                validationFile,
                new FileDescriptor[] {
                  com.google.protobuf.DescriptorProtos.getDescriptor(),
                  com.google.protobuf.DurationProto.getDescriptor(),
                  com.google.protobuf.FieldMaskProto.getDescriptor(),
                  com.google.protobuf.TimestampProto.getDescriptor()
                });
        var extension = validationDescriptor.findExtensionByName("field");
        var registry = com.google.protobuf.ExtensionRegistry.newInstance();
        registry.add(extension, DynamicMessage.getDefaultInstance(extension.getMessageType()));
        var sourceFile =
            set.getFileList().stream()
                .filter(value -> value.getName().equals("validation.proto"))
                .findFirst()
                .orElseThrow();
        var registeredFile =
            FileDescriptor.buildFrom(
                FileDescriptorProto.parseFrom(sourceFile.toByteArray(), registry),
                new FileDescriptor[] {validationDescriptor});
        var registered =
            new ProtobufAdapter(
                null,
                registeredFile.findMessageTypeByName("Sample"),
                Limits.DEFAULT,
                16,
                java.util.Map.of(),
                true);
        assertEquals(schema.field(0), registered.schema().field(0));
        assertEquals(schema.field(1), registered.schema().field(1));
        registered.snapshot(DynamicMessage.parseFrom(registered.descriptor(), valid.toByteArray()));
        var batchAdapter =
            ProtobufAdapter.fromDescriptorSet(
                null, set, "truffledata.validation.Sample", Limits.DEFAULT, 16, true);
        assertInstanceOf(
            Batch.Row.class,
            batchAdapter.snapshot(
                DynamicMessage.parseFrom(batchAdapter.descriptor(), valid.toByteArray())));
        assertEquals(
            StaticLayout.Storage.BYTE,
            new StaticLayout(TestLanguage.current().language(), schema, Limits.DEFAULT)
                .storageKind(1));
        var row = adapter.snapshot(valid);
        assertEquals(
            0, java.util.Objects.requireNonNull(schema.field(4).constraints().length()).min());
        adapter.snapshot(
            valid.toBuilder().addRepeatedField(map, entry).addRepeatedField(map, entry).build());
        assertInstanceOf(StaticLayout.Record.class, row);
        assertEquals("😀a", DataLibrary.factory().getUncached().read(row, 0));
        assertThrows(
            ContractViolation.class,
            () ->
                adapter.snapshot(
                    valid.toBuilder().clearField(descriptor.findFieldByName("name")).build()));
        for (Object[] invalid :
            new Object[][] {
              {"name", "a"},
              {"name", "abcd"},
              {"small", -1},
              {"small", 128},
              {"present", 0},
              {"blob", com.google.protobuf.ByteString.EMPTY}
            }) {
          assertThrows(
              ContractViolation.class,
              () ->
                  adapter.snapshot(
                      valid.toBuilder()
                          .setField(descriptor.findFieldByName((String) invalid[0]), invalid[1])
                          .build()));
        }
        assertThrows(
            ContractViolation.class,
            () ->
                adapter.snapshot(
                    valid.toBuilder().clearField(descriptor.findFieldByName("numbers")).build()));
        // No validator-wide claim: implicit required/nonzero and ignored conditional bounds remain
        // external.
        assertFalse((Boolean) DataLibrary.factory().getUncached().read(row, 9));
        assertTrue(plain.schema().field(0).optional(), "Annotation interpretation is opt-in");
      } finally {
        context.leave();
      }
    }
  }

  @Test
  void consumerNativeMetadataIncludesImportedMessagesBuildersEnumsAndDescriptors() {
    String config =
        dev.elide.truffledata.protobuf.NativeMetadata.reflectionConfig(
            com.google.protobuf.Type.class);
    for (Class<?> required :
        new Class<?>[] {
          com.google.protobuf.Type.class,
          com.google.protobuf.Type.Builder.class,
          com.google.protobuf.Field.class,
          com.google.protobuf.Field.Builder.class,
          com.google.protobuf.SourceContext.class,
          com.google.protobuf.SourceContext.Builder.class,
          com.google.protobuf.Syntax.class,
          com.google.protobuf.DescriptorProtos.FeatureSet.class,
          com.google.protobuf.DescriptorProtos.FeatureSet.Builder.class,
          com.google.protobuf.JavaFeaturesProto.JavaFeatures.class
        }) {
      assertTrue(config.contains("\"name\":\"" + required.getName() + "\""), required.getName());
    }
    assertEquals(
        config,
        dev.elide.truffledata.protobuf.NativeMetadata.reflectionConfig(
            com.google.protobuf.Type.class, com.google.protobuf.Type.class));
    assertFalse(config.contains("java.lang.String"));
    assertThrows(
        IllegalArgumentException.class,
        () -> dev.elide.truffledata.protobuf.NativeMetadata.reflectionConfig(String.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> dev.elide.truffledata.protobuf.NativeMetadata.reflectionConfig(Message.class));
  }

  @Test
  void generatedBinaryMessagesUseCompactUnsignedSnapshots() throws Exception {
    byte[] input = new byte[256];
    java.util.Arrays.fill(input, (byte) 255);
    var chunk = com.google.protobuf.ByteString.copyFrom(input);
    var message = com.google.protobuf.BytesValue.of(chunk.concat(chunk));
    var adapter =
        new ProtobufAdapter(com.google.protobuf.BytesValue.getDescriptor(), Limits.DEFAULT, 8);
    var decoded = com.google.protobuf.BytesValue.parseFrom(message.toByteArray());
    var row = adapter.snapshot(decoded);
    var bytes = (DataArray) DataLibrary.factory().getUncached().read(row, 0);
    assertTrue(bytes.isBytes());
    assertEquals(512, bytes.size());
    input[0] = 0;
    assertEquals(255L, bytes.get(0));
    assertEquals(255L, InteropLibrary.getUncached().readArrayElement(bytes, 511));
    var bounded =
        new ProtobufAdapter(
            com.google.protobuf.BytesValue.getDescriptor(), new Limits(1, 1, 511, 0), 8);
    assertThrows(ContractViolation.class, () -> bounded.snapshot(decoded));
  }

  static Descriptor descriptor() throws Exception {
    var message =
        DescriptorProto.newBuilder()
            .setName("Record")
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("choice"))
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_optional_value"))
            .addField(field("implicit_value", 1, FieldDescriptorProto.Type.TYPE_INT64))
            .addField(
                field("optional_value", 2, FieldDescriptorProto.Type.TYPE_INT64)
                    .setProto3Optional(true)
                    .setOneofIndex(1))
            .addField(
                field("items", 3, FieldDescriptorProto.Type.TYPE_INT64)
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED))
            .addField(field("unsigned_value", 4, FieldDescriptorProto.Type.TYPE_UINT64))
            .addField(field("text", 5, FieldDescriptorProto.Type.TYPE_STRING).setOneofIndex(0))
            .addField(field("number", 6, FieldDescriptorProto.Type.TYPE_INT64).setOneofIndex(0));
    return FileDescriptor.buildFrom(
            FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .setSyntax("proto3")
                .addMessageType(message)
                .build(),
            new FileDescriptor[0])
        .getMessageTypes()
        .get(0);
  }

  static FieldDescriptorProto.Builder field(
      String name, int number, FieldDescriptorProto.Type type) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(type)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
  }

  @Test
  void presenceDefaultsOneofAndUnsignedAreLossless() throws Exception {
    Descriptor descriptor = descriptor();
    var adapter = new ProtobufAdapter(descriptor, Limits.DEFAULT, 16);
    var empty = adapter.snapshot(DynamicMessage.getDefaultInstance(descriptor));
    InteropLibrary io = InteropLibrary.getUncached();
    assertEquals(0L, io.readMember(empty, "implicit_value"));
    assertFalse(io.isMemberReadable(empty, "optional_value"));
    assertEquals(0, io.getArraySize(io.readMember(empty, "items")));
    var builder =
        DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByName("optional_value"), 0L)
            .setField(descriptor.findFieldByName("unsigned_value"), -1L)
            .setField(descriptor.findFieldByName("text"), "hello")
            .setField(descriptor.findFieldByName("number"), 7L);
    var row = adapter.snapshot(builder.build());
    builder.setField(descriptor.findFieldByName("optional_value"), 5L);
    assertEquals(0L, io.readMember(row, "optional_value"));
    assertFalse(io.isMemberReadable(row, "text"));
    assertEquals(7L, io.readMember(row, "number"));
    assertEquals(
        new BigInteger("18446744073709551615"),
        io.asBigInteger(io.readMember(row, "unsigned_value")));
  }

  @Test
  void nestingBudgetAndDescriptorIdentity() throws Exception {
    var node =
        DescriptorProto.newBuilder()
            .setName("Node")
            .addField(
                field("next", 1, FieldDescriptorProto.Type.TYPE_MESSAGE).setTypeName(".Node"));
    Descriptor descriptor =
        FileDescriptor.buildFrom(
                FileDescriptorProto.newBuilder()
                    .setName("node.proto")
                    .setSyntax("proto3")
                    .addMessageType(node)
                    .build(),
                new FileDescriptor[0])
            .getMessageTypes()
            .get(0);
    Message leaf = DynamicMessage.getDefaultInstance(descriptor);
    Message middle =
        DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByName("next"), leaf)
            .build();
    Message root =
        DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByName("next"), middle)
            .build();
    var adapter = new ProtobufAdapter(descriptor, Limits.DEFAULT, 2);
    assertThrows(ContractViolation.class, () -> adapter.snapshot(root));
    assertThrows(
        ContractViolation.class,
        () -> adapter.snapshot(DynamicMessage.getDefaultInstance(descriptor())));
    var valid = new ProtobufAdapter(descriptor, Limits.DEFAULT, 3);
    var first = valid.snapshot(root);
    var nested = (AbstractRow) DataLibrary.factory().getUncached().read(first, 0);
    assertSame(valid.schema(), nested.schema());
    assertSame(first.schema(), valid.snapshot(root).schema());
  }

  @Test
  void repeatedAndStringBudgets() throws Exception {
    Descriptor d = descriptor();
    Message repeated =
        DynamicMessage.newBuilder(d)
            .addRepeatedField(d.findFieldByName("items"), 1L)
            .addRepeatedField(d.findFieldByName("items"), 2L)
            .build();
    assertThrows(
        ContractViolation.class,
        () -> new ProtobufAdapter(d, new Limits(1, 10, 7, 100), 16).snapshot(repeated));
    Message text =
        DynamicMessage.newBuilder(d).setField(d.findFieldByName("text"), "hello").build();
    assertThrows(
        ContractViolation.class,
        () -> new ProtobufAdapter(d, new Limits(1, 10, 100, 2), 16).snapshot(text));
  }
}
