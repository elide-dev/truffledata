package dev.elide.truffledata.protobuf;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import dev.elide.truffledata.*;
import java.math.BigInteger;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** Optional descriptor front end; generated plans do not call this class at runtime. */
public final class ProtoCompiler {
  private ProtoCompiler() {}

  @TruffleBoundary
  public static List<ProtoPlan.Type> definitions(Collection<Descriptor> roots) {
    return definitions(roots, Limits.DEFAULT);
  }

  private static List<ProtoPlan.Type> definitions(Collection<Descriptor> roots, Limits limits) {
    Map<String, ProtoPlan.Type> result = new LinkedHashMap<>();
    long metadata = 0;
    Deque<Descriptor> pending = new ArrayDeque<>(roots);
    while (!pending.isEmpty()) {
      Descriptor descriptor = pending.removeFirst();
      if (result.containsKey(descriptor.getFullName())) continue;
      if (descriptor.getFile().toProto().getSyntax().equals("editions")
          || descriptor.toProto().getExtensionRangeCount() != 0
          || descriptor.getOptions().getMessageSetWireFormat())
        throw new ContractViolation("Editions/extensions/MessageSet require an explicit backend");
      limits.check(1, descriptor.getFields().size());
      metadata += descriptor.getFields().size();
      if (result.size() >= limits.maxColumns() || metadata > limits.maxCells())
        throw new ContractViolation("Protobuf descriptor graph budget exceeded");
      List<ProtoPlan.FieldPlan> fields = new ArrayList<>();
      for (FieldDescriptor field : descriptor.getFields()) {
        ProtoPlan.Kind kind = ProtoPlan.Kind.valueOf(field.getType().name());
        ProtoPlan.EnumType enumeration = null;
        if (kind == ProtoPlan.Kind.ENUM) {
          metadata += field.getEnumType().getValues().size();
          if (metadata > limits.maxCells())
            throw new ContractViolation("Enum metadata budget exceeded");
          Map<String, Integer> numbers = new LinkedHashMap<>();
          for (EnumValueDescriptor value : field.getEnumType().getValues())
            numbers.put(value.getName(), value.getNumber());
          enumeration =
              new ProtoPlan.EnumType(
                  field.getEnumType().getFullName(), !field.getEnumType().isClosed(), numbers);
        }
        String typeName = kind.message() ? field.getMessageType().getFullName() : "";
        Object defaultValue;
        if (kind.message()) {
          defaultValue = Missing.INSTANCE;
          pending.add(field.getMessageType());
        } else if (field.isRepeated()) defaultValue = scalarDefault(kind);
        else defaultValue = normalize(field, field.getDefaultValue());
        var oneof = field.getContainingOneof();
        fields.add(
            new ProtoPlan.FieldPlan(
                field.getName(),
                field.getNumber(),
                kind,
                field.isRepeated(),
                field.isMapField(),
                field.hasPresence(),
                field.isRequired(),
                field.isPacked(),
                oneof == null ? -1 : oneof.getIndex(),
                typeName,
                enumeration,
                defaultValue));
      }
      result.put(
          descriptor.getFullName(),
          new ProtoPlan.Type(
              descriptor.getFullName(), descriptor.getOptions().getMapEntry(), fields));
    }
    return List.copyOf(result.values());
  }

  @TruffleBoundary
  public static ProtoPlan compile(
      @Nullable TruffleLanguage<?> language,
      Descriptor descriptor,
      Limits limits,
      int maxDepth,
      int maxWireBytes) {
    return new ProtoPlan.Model(
            language, definitions(List.of(descriptor), limits), limits, maxDepth, maxWireBytes)
        .type(descriptor.getFullName());
  }

  @TruffleBoundary
  public static ProtoPlan compile(
      @Nullable TruffleLanguage<?> language,
      DescriptorProtos.FileDescriptorSet descriptors,
      String messageName,
      Limits limits,
      int maxDepth,
      int maxWireBytes)
      throws DescriptorValidationException {
    if (descriptors.getSerializedSize() > maxWireBytes
        || descriptors.getFileCount() > limits.maxColumns())
      throw new ContractViolation("Descriptor budget exceeded");
    List<FileDescriptor> files = files(descriptors.getFileList());
    Deque<Descriptor> pending = new ArrayDeque<>();
    files.forEach(file -> pending.addAll(file.getMessageTypes()));
    while (!pending.isEmpty()) {
      Descriptor candidate = pending.removeFirst();
      if (candidate.getFullName().equals(messageName))
        return compile(language, candidate, limits, maxDepth, maxWireBytes);
      pending.addAll(candidate.getNestedTypes());
    }
    throw new ContractViolation("Unknown protobuf message: " + messageName);
  }

  static List<FileDescriptor> files(List<DescriptorProtos.FileDescriptorProto> protos)
      throws DescriptorValidationException {
    Map<String, DescriptorProtos.FileDescriptorProto> pending = new LinkedHashMap<>();
    for (var proto : protos)
      if (pending.put(proto.getName(), proto) != null)
        throw new ContractViolation("Duplicate descriptor file");
    Map<String, FileDescriptor> built = new LinkedHashMap<>();
    while (!pending.isEmpty()) {
      boolean progress = false;
      var iterator = pending.values().iterator();
      while (iterator.hasNext()) {
        var proto = iterator.next();
        if (!built.keySet().containsAll(proto.getDependencyList())) continue;
        FileDescriptor[] dependencies =
            proto.getDependencyList().stream().map(built::get).toArray(FileDescriptor[]::new);
        built.put(proto.getName(), FileDescriptor.buildFrom(proto, dependencies));
        iterator.remove();
        progress = true;
      }
      if (!progress) throw new ContractViolation("Missing or cyclic descriptor imports");
    }
    return List.copyOf(built.values());
  }

  static Object scalarDefault(ProtoPlan.Kind kind) {
    return switch (kind) {
      case STRING -> "";
      case BYTES -> DataArray.copyOfBytes(new byte[0]);
      case BOOL -> false;
      case FLOAT, DOUBLE -> 0.0;
      case UINT64, FIXED64 -> Values.normalize(BigInteger.ZERO);
      default -> 0L;
    };
  }

  static Object normalize(FieldDescriptor field, Object value) {
    if (value instanceof ByteString bytes)
      return DataArray.copyOfBytes(bytes.asReadOnlyByteBuffer());
    if (value instanceof EnumValueDescriptor enumeration) return (long) enumeration.getNumber();
    return switch (field.getType()) {
      case UINT32, FIXED32 -> Integer.toUnsignedLong((Integer) value);
      case UINT64, FIXED64 -> {
        long n = (Long) value;
        yield Values.normalize(
            n >= 0 ? BigInteger.valueOf(n) : BigInteger.valueOf(n & Long.MAX_VALUE).setBit(63));
      }
      default -> Values.normalize(value);
    };
  }

  /**
   * The compatibility ingestion path accepts generated or dynamic Message and Builder instances.
   */
  @TruffleBoundary
  public static ProtoRow snapshot(ProtoPlan plan, MessageOrBuilder message) {
    return bind(plan, message.getDescriptorForType()).snapshot(message);
  }

  /**
   * Bind once on the host; repeated snapshots check descriptor identity without recompiling plans.
   */
  @TruffleBoundary
  public static Source bind(ProtoPlan plan, Descriptor descriptor) {
    boolean closedMaps = false;
    for (var type : definitions(List.of(descriptor), plan.model.limits)) {
      ProtoPlan expected = plan.model.type(type.name());
      if (expected.size() != type.fields().size()
          || expected.definition().mapEntry() != type.mapEntry())
        throw new ContractViolation("Message descriptor differs from plan");
      for (int i = 0; i < expected.size(); i++) {
        var actual = type.fields().get(i);
        var field = expected.field(i);
        if (field.map()) {
          var enumeration = expected.nested(i).field(1).enumType();
          closedMaps |= enumeration != null && !enumeration.open();
        }
        // Use the existing default to compare structural metadata; binary/exact defaults need value
        // equality.
        var shape =
            new ProtoPlan.FieldPlan(
                actual.name(),
                actual.number(),
                actual.kind(),
                actual.repeated(),
                actual.map(),
                actual.explicitPresence(),
                actual.required(),
                actual.packed(),
                actual.oneof(),
                actual.typeName(),
                actual.enumType(),
                field.defaultValue());
        if (!shape.equals(field) || !sameDefault(actual.defaultValue(), field.defaultValue()))
          throw new ContractViolation("Message field differs from plan: " + actual.name());
      }
    }
    if (!descriptor.getFullName().equals(plan.name()))
      throw new ContractViolation("Root type mismatch");
    return new Source(plan, descriptor, closedMaps);
  }

  private static boolean sameDefault(Object left, Object right) {
    if (left instanceof DataArray a && right instanceof DataArray b) {
      if (a.size() != b.size()) return false;
      for (int i = 0; i < a.size(); i++) if (!a.get(i).equals(b.get(i))) return false;
      return true;
    }
    if (left instanceof ExactNumber && right instanceof ExactNumber) {
      try {
        var io = com.oracle.truffle.api.interop.InteropLibrary.getUncached();
        return io.asBigInteger(left).equals(io.asBigInteger(right));
      } catch (com.oracle.truffle.api.interop.UnsupportedMessageException e) {
        return false;
      }
    }
    return left.equals(right);
  }

  public static final class Source {
    private final ProtoPlan plan;
    private final Descriptor descriptor;
    private final boolean closedMaps;

    private Source(ProtoPlan plan, Descriptor descriptor, boolean closedMaps) {
      this.plan = plan;
      this.descriptor = descriptor;
      this.closedMaps = closedMaps;
    }

    public ProtoPlan plan() {
      return plan;
    }

    @TruffleBoundary
    public ProtoRow snapshot(MessageOrBuilder message) {
      if (message.getDescriptorForType() != descriptor)
        throw new ContractViolation("Descriptor identity mismatch");
      if (closedMaps) {
        // DynamicMessage retains closed-enum map entries differently from the wire contract.
        // Normalize this uncommon compatibility case through the shared decoder, with preflight
        // before protobuf-java recursively sizes/serializes the supplied graph.
        preflight(message, new ProtoRow.Budget(plan.model.limits), 1);
        Message immutable =
            message instanceof Message m ? m : ((Message.Builder) message).buildPartial();
        int size = immutable.getSerializedSize();
        if (size < 0 || size > plan.model.maxWireBytes)
          throw new ContractViolation("Wire byte budget exceeded");
        return plan.decode(immutable.toByteArray());
      }
      return ProtoCompiler.snapshot(plan, message, new ProtoRow.Budget(plan.model.limits), 1);
    }

    private void preflight(MessageOrBuilder message, ProtoRow.Budget budget, int depth) {
      if (depth > plan.model.maxDepth)
        throw new ContractViolation("Message nesting budget exceeded");
      var fields = message.getDescriptorForType().getFields();
      budget.cells(fields.size());
      for (var field : fields) {
        if (field.isRepeated()) {
          int count = message.getRepeatedFieldCount(field);
          budget.cells(count);
          for (int i = 0; i < count; i++)
            preflightValue(message.getRepeatedField(field, i), budget, depth);
        } else if (message.hasField(field)) preflightValue(message.getField(field), budget, depth);
      }
      validateUnknown(
          message.getUnknownFields(), budget, depth, plan.model.maxDepth, plan.model.maxWireBytes);
    }

    private void preflightValue(Object value, ProtoRow.Budget budget, int depth) {
      if (value instanceof MessageOrBuilder nested) preflight(nested, budget, depth + 1);
      else if (value instanceof ByteString bytes) {
        budget.cells(bytes.size());
        if (bytes.size() > plan.model.maxWireBytes)
          throw new ContractViolation("Wire byte budget exceeded");
      } else if (value instanceof String text) budget.bytes(2L * text.length());
    }

    /** Compatibility text parser; temporary protobuf builders never escape into the row graph. */
    @TruffleBoundary
    public ProtoRow parseText(String text) throws TextFormat.ParseException {
      textBudget(text);
      var builder = DynamicMessage.newBuilder(descriptor);
      TextFormat.getParser().merge(text, builder);
      return snapshot(builder);
    }

    @TruffleBoundary
    public ProtoRow parseJson(String json) throws InvalidProtocolBufferException {
      textBudget(json);
      var builder = DynamicMessage.newBuilder(descriptor);
      com.google.protobuf.util.JsonFormat.parser().merge(json, builder);
      return snapshot(builder);
    }

    private void textBudget(String text) {
      if (text.length() > plan.model.maxWireBytes / 2)
        throw new ContractViolation("Text byte budget exceeded");
      // Bound nesting before reflective parsers allocate message trees. Quotes/comments are
      // ignored.
      int depth = 0;
      char quote = 0;
      boolean escape = false;
      boolean comment = false;
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (comment) {
          if (c == '\n') comment = false;
          continue;
        }
        if (quote != 0) {
          if (escape) escape = false;
          else if (c == '\\') escape = true;
          else if (c == quote) quote = 0;
        } else if (c == '"' || c == '\'') quote = c;
        else if (c == '#') comment = true;
        else if (c == '{' || c == '[' || c == '<') {
          if (++depth > plan.model.maxDepth)
            throw new ContractViolation("Text nesting budget exceeded");
        } else if (c == '}' || c == ']' || c == '>') depth--;
      }
    }
  }

  private static ProtoRow snapshot(
      ProtoPlan plan, MessageOrBuilder message, ProtoRow.Budget budget, int depth) {
    if (depth > plan.model.maxDepth) throw new ContractViolation("Message nesting budget exceeded");
    budget.cells(plan.size());
    var builder = plan.newBuilder();
    var fields = message.getDescriptorForType().getFields();
    for (int i = 0; i < plan.size(); i++) {
      var f = fields.get(i);
      if (f.isRepeated()) {
        int count = message.getRepeatedFieldCount(f);
        budget.cells(count);
        Object[] values = new Object[count];
        for (int j = 0; j < count; j++)
          values[j] = value(plan, i, f, message.getRepeatedField(f, j), budget, depth);
        // Maps expose last-key-wins semantics, including DynamicMessage's repeated entries.
        if (f.isMapField()) {
          Map<Object, Object> entries = new LinkedHashMap<>();
          for (Object entry : values)
            entries.put(ProtoRow.mapKey(DataLibrary.factory().getUncached().read(entry, 0)), entry);
          values = entries.values().toArray();
        }
        builder.set(i, new DataArray(values));
      } else if (message.hasField(f))
        builder.set(i, value(plan, i, f, message.getField(f), budget, depth));
    }
    long payload =
        validateUnknown(
            message.getUnknownFields(),
            budget,
            depth,
            plan.model.maxDepth,
            plan.model.maxWireBytes);
    int unknownSize = message.getUnknownFields().getSerializedSize();
    if (unknownSize < payload || unknownSize > plan.model.maxWireBytes)
      throw new ContractViolation("Unknown wire byte budget exceeded");
    budget.bytes(unknownSize - payload);
    builder.unknown = message.getUnknownFields().toByteArray();
    return builder.build();
  }

  private static long validateUnknown(
      UnknownFieldSet fields, ProtoRow.Budget budget, int depth, int maxDepth, int maxWireBytes) {
    if (depth > maxDepth) throw new ContractViolation("Unknown nesting budget exceeded");
    budget.cells(fields.asMap().size());
    long payload = 0;
    for (var field : fields.asMap().values()) {
      budget.cells(
          (long) field.getVarintList().size()
              + field.getFixed32List().size()
              + field.getFixed64List().size()
              + field.getLengthDelimitedList().size()
              + field.getGroupList().size());
      for (var bytes : field.getLengthDelimitedList()) {
        budget.bytes(bytes.size());
        payload += bytes.size();
      }
      for (var group : field.getGroupList())
        payload += validateUnknown(group, budget, depth + 1, maxDepth, maxWireBytes);
      if (payload > maxWireBytes) throw new ContractViolation("Unknown wire byte budget exceeded");
    }
    return payload;
  }

  private static Object value(
      ProtoPlan plan,
      int index,
      FieldDescriptor field,
      Object value,
      ProtoRow.Budget budget,
      int depth) {
    if (value instanceof MessageOrBuilder nested)
      return snapshot(plan.nested(index), nested, budget, depth + 1);
    if (value instanceof ByteString bytes) budget.cells(bytes.size());
    if (value instanceof String string) budget.bytes(2L * string.length());
    return normalize(field, value);
  }
}
