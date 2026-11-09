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
    return definitions(roots, limits, List.of(), false);
  }

  private static List<ProtoPlan.Type> definitions(
      Collection<Descriptor> roots,
      Limits limits,
      List<FieldDescriptor> extensions,
      boolean explicit) {
    Set<FieldDescriptor> used = Collections.newSetFromMap(new IdentityHashMap<>());
    Set<FieldDescriptor> supplied = Collections.newSetFromMap(new IdentityHashMap<>());
    for (FieldDescriptor extension : extensions)
      if (!extension.isExtension() || !supplied.add(extension))
        throw new ContractViolation("Duplicate or non-extension descriptor");
    Map<String, ProtoPlan.Type> result = new LinkedHashMap<>();
    long metadata = 0;
    Deque<Descriptor> pending = new ArrayDeque<>(roots);
    while (!pending.isEmpty()) {
      Descriptor descriptor = pending.removeFirst();
      if (result.containsKey(descriptor.getFullName())) continue;
      if (!explicit
          && (descriptor.getFile().toProto().getSyntax().equals("editions")
              || descriptor.toProto().getExtensionRangeCount() != 0
              || descriptor.getOptions().getMessageSetWireFormat()))
        throw new ContractViolation("Editions/extensions/MessageSet require an explicit backend");
      List<FieldDescriptor> declared = new ArrayList<>(descriptor.getFields());
      for (FieldDescriptor extension : extensions)
        if (extension.getContainingType() == descriptor) {
          declared.add(extension);
          used.add(extension);
        }
      limits.check(1, declared.size());
      metadata += declared.size();
      if (result.size() >= limits.maxColumns() || metadata > limits.maxCells())
        throw new ContractViolation("Protobuf descriptor graph budget exceeded");
      List<ProtoPlan.FieldPlan> fields = new ArrayList<>();
      for (FieldDescriptor field : declared) {
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
                field.isExtension() ? field.getFullName() : field.getName(),
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
                defaultValue,
                field.isExtension() ? "[" + field.getFullName() + "]" : field.getJsonName()));
      }
      result.put(
          descriptor.getFullName(),
          new ProtoPlan.Type(
              descriptor.getFullName(), descriptor.getOptions().getMapEntry(), fields));
    }
    if (used.size() != extensions.size())
      throw new ContractViolation("Unreachable extension descriptor");
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
    return bind(plan, descriptor, List.of());
  }

  /**
   * Bind a resolved plan to an explicit descriptor graph and extension allowlist. Resolved Editions
   * field features are read through the descriptor API; ordinary compilation still requires an
   * explicit backend for these graphs. Extension ordinals follow registration order.
   */
  @TruffleBoundary
  public static Source bind(
      ProtoPlan plan, Descriptor descriptor, List<FieldDescriptor> extensions) {
    extensions = List.copyOf(extensions);
    boolean closedMaps = false;
    for (var type : definitions(List.of(descriptor), plan.model.limits, extensions, true)) {
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
                field.defaultValue(),
                actual.jsonName());
        if (!shape.equals(field)
            || (!field.repeated() && !sameDefault(actual.defaultValue(), field.defaultValue())))
          throw new ContractViolation("Message field differs from plan: " + actual.name());
      }
    }
    if (!descriptor.getFullName().equals(plan.name()))
      throw new ContractViolation("Root type mismatch");
    return new Source(plan, descriptor, closedMaps, extensions);
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

    /** Representation support is decided during preparation, before any host message is sized. */
    public enum Ingestion {
      DIRECT,
      WIRE_NORMALIZED,
      UNSUPPORTED
    }

    private final boolean closedMaps;
    private final Map<Descriptor, List<FieldDescriptor>> fields;
    private final Set<Descriptor> extensible = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ExtensionRegistry registry;
    private final Ingestion ingestion;

    private Source(
        ProtoPlan plan,
        Descriptor descriptor,
        boolean closedMaps,
        List<FieldDescriptor> extensions) {
      this.plan = plan;
      this.descriptor = descriptor;
      this.closedMaps = closedMaps;
      registry = ExtensionRegistry.newInstance();
      for (FieldDescriptor extension : extensions) {
        if (extension.getJavaType() == FieldDescriptor.JavaType.MESSAGE)
          registry.add(extension, DynamicMessage.getDefaultInstance(extension.getMessageType()));
        else registry.add(extension);
      }
      Map<Descriptor, List<FieldDescriptor>> resolved = new IdentityHashMap<>();
      Deque<Descriptor> pending = new ArrayDeque<>();
      pending.add(descriptor);
      boolean messageSet = false;
      while (!pending.isEmpty()) {
        Descriptor current = pending.removeFirst();
        if (resolved.containsKey(current)) continue;
        messageSet |= current.getOptions().getMessageSetWireFormat();
        if (current.toProto().getExtensionRangeCount() != 0) extensible.add(current);
        List<FieldDescriptor> all = new ArrayList<>(current.getFields());
        for (FieldDescriptor extension : extensions)
          if (extension.getContainingType() == current) all.add(extension);
        resolved.put(current, List.copyOf(all));
        for (FieldDescriptor field : all)
          if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE)
            pending.add(field.getMessageType());
      }
      fields = Collections.unmodifiableMap(resolved);
      ingestion =
          closedMaps
              ? (messageSet ? Ingestion.UNSUPPORTED : Ingestion.WIRE_NORMALIZED)
              : Ingestion.DIRECT;
    }

    public Ingestion ingestion() {
      return ingestion;
    }

    private List<FieldDescriptor> fields(MessageOrBuilder message) {
      List<FieldDescriptor> bound = fields.get(message.getDescriptorForType());
      if (bound == null) throw new ContractViolation("Nested descriptor identity mismatch");
      // Ordinary messages cannot contain extensions. Avoid constructing protobuf-java's
      // reflective populated-field map in that common prepared path.
      if (extensible.contains(message.getDescriptorForType()))
        for (FieldDescriptor field : message.getAllFields().keySet())
          if (!bound.contains(field))
            throw new UnsupportedOperationException(
                "Unregistered extension: " + field.getFullName());
      return bound;
    }

    public ProtoPlan plan() {
      return plan;
    }

    @TruffleBoundary
    public ProtoRow snapshot(MessageOrBuilder message) {
      if (message.getDescriptorForType() != descriptor)
        throw new ContractViolation("Descriptor identity mismatch");
      if (ingestion == Ingestion.UNSUPPORTED)
        throw new UnsupportedOperationException(
            "MessageSet with closed-enum maps needs wire normalization");
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
      return ProtoCompiler.snapshot(plan, message, new ProtoRow.Budget(plan.model.limits), 1, this);
    }

    /** Calculate bounded wire size ourselves before invoking recursive protobuf-java sizing. */
    private long preflight(MessageOrBuilder message, ProtoRow.Budget budget, int depth) {
      if (depth > plan.model.maxDepth)
        throw new ContractViolation("Message nesting budget exceeded");
      var fields = fields(message);
      budget.cells(fields.size());
      long size = 0;
      for (var field : fields) {
        int tag = CodedOutputStream.computeTagSize(field.getNumber());
        if (field.isRepeated()) {
          int count = message.getRepeatedFieldCount(field);
          budget.cells(count);
          long payload = 0;
          for (int i = 0; i < count; i++) {
            long item = preflightValue(field, message.getRepeatedField(field, i), budget, depth);
            payload = wireSize(payload + item + (field.isPacked() ? 0 : tag));
          }
          if (field.isPacked() && count != 0)
            payload =
                wireSize(payload + tag + CodedOutputStream.computeUInt32SizeNoTag((int) payload));
          size = wireSize(size + payload);
        } else if (message.hasField(field))
          size =
              wireSize(size + tag + preflightValue(field, message.getField(field), budget, depth));
      }
      var unknown = message.getUnknownFields();
      validateUnknown(unknown, budget, depth, plan.model.maxDepth, plan.model.maxWireBytes);
      return wireSize(size + unknownWireSize(unknown));
    }

    private long wireSize(long size) {
      if (size > plan.model.maxWireBytes) throw new ContractViolation("Wire byte budget exceeded");
      return size;
    }

    private long unknownWireSize(UnknownFieldSet fields) {
      long size = 0;
      for (var entry : fields.asMap().entrySet()) {
        var field = entry.getValue();
        int tag = CodedOutputStream.computeTagSize(entry.getKey());
        size =
            wireSize(
                size
                    + (long) (tag + 4) * field.getFixed32List().size()
                    + (long) (tag + 8) * field.getFixed64List().size());
        for (long value : field.getVarintList())
          size = wireSize(size + tag + CodedOutputStream.computeUInt64SizeNoTag(value));
        for (var bytes : field.getLengthDelimitedList())
          size =
              wireSize(
                  size
                      + tag
                      + CodedOutputStream.computeUInt32SizeNoTag(bytes.size())
                      + bytes.size());
        for (var group : field.getGroupList())
          size = wireSize(size + 2L * tag + unknownWireSize(group));
      }
      return size;
    }

    private long preflightValue(
        FieldDescriptor field, Object value, ProtoRow.Budget budget, int depth) {
      if (value instanceof MessageOrBuilder nested) {
        long size = preflight(nested, budget, depth + 1);
        return wireSize(
            size
                + (field.getType() == FieldDescriptor.Type.GROUP
                    ? CodedOutputStream.computeTagSize(field.getNumber())
                    : CodedOutputStream.computeUInt32SizeNoTag((int) size)));
      }
      if (value instanceof ByteString bytes) {
        budget.cells(bytes.size());
        return wireSize(
            (long) bytes.size() + CodedOutputStream.computeUInt32SizeNoTag(bytes.size()));
      }
      if (value instanceof String text) {
        budget.bytes(2L * text.length());
        int bytes = GeneratedOutput.utf8Length(text);
        return wireSize((long) bytes + CodedOutputStream.computeUInt32SizeNoTag(bytes));
      }
      return switch (field.getType()) {
        case DOUBLE, FIXED64, SFIXED64 -> 8;
        case FLOAT, FIXED32, SFIXED32 -> 4;
        case BOOL -> 1;
        case INT32 -> CodedOutputStream.computeInt32SizeNoTag((Integer) value);
        case UINT32 -> CodedOutputStream.computeUInt32SizeNoTag((Integer) value);
        case SINT32 -> CodedOutputStream.computeSInt32SizeNoTag((Integer) value);
        case SINT64 -> CodedOutputStream.computeSInt64SizeNoTag((Long) value);
        case INT64, UINT64 -> CodedOutputStream.computeUInt64SizeNoTag((Long) value);
        case ENUM ->
            CodedOutputStream.computeEnumSizeNoTag(((EnumValueDescriptor) value).getNumber());
        default -> throw new ContractViolation("Invalid host field representation");
      };
    }

    /** Compatibility text parser; temporary protobuf builders never escape into the row graph. */
    @TruffleBoundary
    public ProtoRow parseText(String text) throws TextFormat.ParseException {
      textBudget(text);
      var builder = DynamicMessage.newBuilder(descriptor);
      TextFormat.getParser().merge(text, registry, builder);
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
      ProtoPlan plan, MessageOrBuilder message, ProtoRow.Budget budget, int depth, Source source) {
    if (depth > plan.model.maxDepth) throw new ContractViolation("Message nesting budget exceeded");
    budget.cells(plan.size());
    var builder = plan.newBuilder();
    var fields = source.fields(message);
    for (int i = 0; i < plan.size(); i++) {
      var f = fields.get(i);
      if (f.isRepeated()) {
        int count = message.getRepeatedFieldCount(f);
        budget.cells(count);
        Object[] values = new Object[count];
        for (int j = 0; j < count; j++)
          values[j] = value(plan, i, f, message.getRepeatedField(f, j), budget, depth, source);
        // Maps expose last-key-wins semantics, including DynamicMessage's repeated entries.
        if (f.isMapField()) {
          Map<Object, Object> entries = new LinkedHashMap<>();
          for (Object entry : values)
            entries.put(ProtoRow.mapKey(DataLibrary.factory().getUncached().read(entry, 0)), entry);
          values = entries.values().toArray();
        }
        builder.set(i, new DataArray(values));
      } else if (message.hasField(f))
        builder.set(i, value(plan, i, f, message.getField(f), budget, depth, source));
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
      int depth,
      Source source) {
    if (value instanceof MessageOrBuilder nested)
      return snapshot(plan.nested(index), nested, budget, depth + 1, source);
    if (value instanceof ByteString bytes) budget.cells(bytes.size());
    if (value instanceof String string) budget.bytes(2L * string.length());
    return normalize(field, value);
  }
}
