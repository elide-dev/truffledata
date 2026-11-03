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
