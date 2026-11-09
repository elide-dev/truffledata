package dev.elide.truffledata.comparison;

import com.google.protobuf.*;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.Descriptors.FileDescriptor;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.generated.ComparisonContracts;
import dev.elide.truffledata.protobuf.ProtoCompiler;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.SlabCodec;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;
import org.graalvm.nativeimage.ImageInfo;
import org.jspecify.annotations.NullMarked;

/** Separates reusable host ingestion, its wire fallback, and one-time binding cost (#33). */
@NullMarked
public final class SourceIngestionProbe {
  private static volatile Object retained = new Object();

  private SourceIngestionProbe() {}

  public static void main(String[] args) throws Exception {
    var host =
        Type.newBuilder()
            .setName("ordinary-λ")
            .setSyntax(Syntax.SYNTAX_PROTO3)
            .setSourceContext(SourceContext.newBuilder().setFileName("schema.proto"));
    for (int i = 0; i < 6; i++)
      host.addFields(
          com.google.protobuf.Field.newBuilder()
              .setName("column-" + i)
              .setNumber(i + 1)
              .setKind(com.google.protobuf.Field.Kind.TYPE_INT64));
    Type generated = host.build();
    var plan = ProtoCompiler.compile(null, Type.getDescriptor(), Limits.DEFAULT, 32, 1 << 20);
    var source = ProtoCompiler.bind(plan, Type.getDescriptor());
    var dynamic = DynamicMessage.parseFrom(Type.getDescriptor(), generated.toByteArray());
    check(plan, generated, source);
    check(plan, dynamic, source);
    measure("bind-generated", () -> ProtoCompiler.bind(plan, Type.getDescriptor()));
    measure("snapshot-generated", () -> source.snapshot(generated));
    measure("snapshot-dynamic", () -> source.snapshot(dynamic));
    measure("wire-generated", () -> plan.decode(generated.toByteArray()));

    byte[] wire = generated.toByteArray();
    var slabPlan =
        ComparisonContracts.create(null, Limits.DEFAULT, 32, 1 << 20).type("google.protobuf.Type");
    var slabDecoder = new ComparisonContracts.SlabDecoder_google$protobuf$Type(slabPlan);
    if (!Arrays.equals(SlabCodec.decode(slabPlan.layout(), wire), slabDecoder.decode(wire)))
      throw new AssertionError("Generated slab producer differs");
    measure(
        "stock-parse",
        () -> {
          try {
            return Type.parseFrom(wire);
          } catch (InvalidProtocolBufferException failure) {
            throw new AssertionError(failure);
          }
        });
    measure("slab-table", () -> SlabCodec.decode(slabPlan.layout(), wire));
    measure("slab-generated", () -> slabDecoder.decode(wire));

    var fallback = fallback();
    var fallbackPlan =
        ProtoCompiler.compile(null, fallback.getDescriptorForType(), Limits.DEFAULT, 32, 1 << 20);
    var fallbackSource = ProtoCompiler.bind(fallbackPlan, fallback.getDescriptorForType());
    if (fallbackSource.ingestion() != ProtoCompiler.Source.Ingestion.WIRE_NORMALIZED)
      throw new AssertionError("Expected closed-enum map normalization");
    check(fallbackPlan, fallback, fallbackSource);
    measure("snapshot-closed-map-fallback", () -> fallbackSource.snapshot(fallback));
    measure("wire-closed-map", () -> fallbackPlan.decode(fallback.toByteArray()));
  }

  private static void check(ProtoPlan plan, Message message, ProtoCompiler.Source source) {
    if (!Arrays.equals(
        plan.encode(plan.decode(message.toByteArray())), plan.encode(source.snapshot(message))))
      throw new AssertionError("Ingestion semantics differ");
  }

  private static void measure(String name, Supplier<Object> operation) {
    long warmup = Long.getLong("ingestion.warmupMillis", 500) * 1_000_000;
    long duration = Long.getLong("ingestion.sampleMillis", 100) * 1_000_000;
    long until = System.nanoTime() + warmup;
    while (System.nanoTime() < until) retained = operation.get();
    double[] ns = new double[5];
    for (int sample = 0; sample < ns.length; sample++) {
      long count = 0, start = System.nanoTime(), end;
      do {
        for (int i = 0; i < 32; i++) retained = operation.get();
        count += 32;
        end = System.nanoTime();
      } while (end - start < duration);
      ns[sample] = (double) (end - start) / count;
    }
    System.out.printf(
        Locale.ROOT,
        "{\"case\":\"%s\",\"runtime\":\"%s\",\"ns_per_op\":%s}%n",
        name,
        ImageInfo.inImageRuntimeCode() ? "svm" : "jvm",
        Arrays.toString(ns));
  }

  private static FieldDescriptorProto.Builder field(
      String name, int number, FieldDescriptorProto.Type type) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(type)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
  }

  private static DynamicMessage fallback() throws Exception {
    var enumeration =
        EnumDescriptorProto.newBuilder()
            .setName("Value")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ZERO").setNumber(0));
    var entry =
        DescriptorProto.newBuilder()
            .setName("Entry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true))
            .addField(field("key", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(
                field("value", 2, FieldDescriptorProto.Type.TYPE_ENUM)
                    .setTypeName(".ingestion.Value"));
    var root =
        DescriptorProto.newBuilder()
            .setName("Root")
            .addNestedType(entry)
            .addField(
                field("items", 1, FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".ingestion.Root.Entry")
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED));
    var file =
        FileDescriptor.buildFrom(
            FileDescriptorProto.newBuilder()
                .setName("ingestion.proto")
                .setPackage("ingestion")
                .setSyntax("proto2")
                .addEnumType(enumeration)
                .addMessageType(root)
                .build(),
            new FileDescriptor[0]);
    var descriptor = file.findMessageTypeByName("Root");
    var entryType = descriptor.findNestedTypeByName("Entry");
    var host = DynamicMessage.newBuilder(descriptor);
    for (int i = 0; i < 6; i++)
      host.addRepeatedField(
          descriptor.findFieldByNumber(1),
          DynamicMessage.newBuilder(entryType)
              .setField(entryType.findFieldByNumber(1), "key-" + i)
              .build());
    return host.build();
  }
}
