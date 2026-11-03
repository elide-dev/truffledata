package dev.elide.truffledata.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.*;
import com.google.protobuf.Message;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import dev.elide.truffledata.*;
import java.math.BigInteger;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** Snapshot adapter. Reflection/conversion happens once at ingestion, never in field read nodes. */
public final class ProtobufAdapter {
  private final Descriptor descriptor;
  private final Limits limits;
  private final int maxDepth;
  private final Schema schema;
  private final Map<Descriptor, Schema> schemas;
  private final Map<Descriptor, StaticLayout> layouts;
  private final Map<String, Constraints.Length> lengthContracts;

  public ProtobufAdapter(Descriptor descriptor, Limits limits, int maxDepth) {
    this(null, descriptor, limits, maxDepth);
  }

  /**
   * With a language owner, every descriptor (including recursive/nested messages) has a static
   * layout.
   */
  public ProtobufAdapter(
      @Nullable TruffleLanguage<?> language, Descriptor descriptor, Limits limits, int maxDepth) {
    this(language, descriptor, limits, maxDepth, Map.of());
  }

  @TruffleBoundary
  public ProtobufAdapter(
      @Nullable TruffleLanguage<?> language,
      Descriptor descriptor,
      Limits limits,
      int maxDepth,
      Map<String, Constraints.Length> lengthContracts) {
    this.lengthContracts = Map.copyOf(lengthContracts);
    if (maxDepth < 1 || maxDepth > 128)
      throw new IllegalArgumentException("Depth must be between 1 and 128");
    this.descriptor = descriptor;
    this.limits = limits;
    this.maxDepth = maxDepth;
    schemas = compileSchemas(descriptor);
    schema = Objects.requireNonNull(schemas.get(descriptor));
    Map<Descriptor, StaticLayout> compiled = new IdentityHashMap<>();
    if (language != null)
      schemas.forEach((d, s) -> compiled.put(d, new StaticLayout(language, s, limits)));
    layouts = Collections.unmodifiableMap(compiled);
    for (String name : lengthContracts.keySet()) {
      boolean found =
          schemas.keySet().stream()
              .flatMap(d -> d.getFields().stream())
              .anyMatch(f -> f.getFullName().equals(name));
      if (!found) throw new ContractViolation("Unknown field length contract: " + name);
    }
  }

  public Schema schema() {
    return schema;
  }

  @TruffleBoundary
  public AbstractRow snapshot(Message message) {
    if (message.getDescriptorForType() != descriptor)
      throw new ContractViolation("Protobuf descriptor differs from adapter");
    return record(message, schema, new Budget(), 1);
  }

  /** Resolve a bounded descriptor graph once, including recursive types, without global caches. */
  private Map<Descriptor, Schema> compileSchemas(Descriptor root) {
    Map<Descriptor, Schema> result = new IdentityHashMap<>();
    Deque<Descriptor> pending = new ArrayDeque<>();
    pending.add(root);
    long fieldCount = 0;
    while (!pending.isEmpty()) {
      Descriptor current = pending.removeFirst();
      if (result.containsKey(current)) continue;
      fieldCount += current.getFields().size();
      if (result.size() >= limits.maxColumns() || fieldCount > limits.maxCells()) {
        throw new ContractViolation("Protobuf descriptor budget exceeded");
      }
      result.put(current, describe(current));
      for (FieldDescriptor field : current.getFields()) {
        if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE)
          pending.add(field.getMessageType());
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private Schema describe(Descriptor descriptor) {
    limits.check(1, descriptor.getFields().size());
    List<Field> fields = new ArrayList<>();
    for (FieldDescriptor f : descriptor.getFields()) {
      Field scalar = scalarField(f);
      if (f.isRepeated()) {
        fields.add(
            new Field(
                f.getName(),
                Field.Kind.ARRAY,
                false,
                false,
                Constraints.NONE
                    .withElement(scalar)
                    .withLength(
                        new Constraints.Length(
                            0,
                            (int) Math.min(Integer.MAX_VALUE, limits.maxCells()),
                            Constraints.LengthUnit.ELEMENTS))));
      } else {
        fields.add(
            new Field(
                f.getName(),
                scalar.kind(),
                false,
                f.hasPresence() && !f.isRequired(),
                scalar.constraints()));
      }
    }
    for (int i = 0; i < fields.size(); i++) {
      Constraints.Length length = lengthContracts.get(descriptor.getFields().get(i).getFullName());
      if (length != null) {
        Field field = fields.get(i);
        fields.set(i, field.withConstraints(field.constraints().withLength(length)));
      }
    }
    return new Schema(descriptor.getFullName(), fields);
  }

  private Field scalarField(FieldDescriptor f) {
    String name = f.getName();
    return switch (f.getType()) {
      case INT32, SINT32, SFIXED32 -> Field.integer(name, 32, true, false, false);
      case UINT32, FIXED32 -> Field.integer(name, 32, false, false, false);
      case INT64, SINT64, SFIXED64 -> Field.integer(name, 64, true, false, false);
      case UINT64, FIXED64 -> Field.integer(name, 64, false, false, false);
      case FLOAT, DOUBLE ->
          new Field(
              name,
              Field.Kind.FLOAT64,
              false,
              false,
              Constraints.NONE.withFloatBits(f.getType() == FieldDescriptor.Type.FLOAT ? 32 : 64));
      case BOOL -> new Field(name, Field.Kind.BOOLEAN, false, false);
      case STRING, ENUM -> new Field(name, Field.Kind.STRING, false, false);
      case MESSAGE, GROUP ->
          new Field(
              name,
              Field.Kind.RECORD,
              false,
              false,
              Constraints.NONE.withRecordType(f.getMessageType().getFullName()));
      case BYTES ->
          new Field(
              name,
              Field.Kind.ARRAY,
              false,
              false,
              Constraints.NONE.withElement(Field.integer("byte", 8, false, false, false)));
    };
  }

  /** Read protoc --descriptor_set_out --include_imports output without runtime source parsing. */
  @TruffleBoundary
  public static ProtobufAdapter fromDescriptorSet(
      TruffleLanguage<?> language,
      FileDescriptorSet set,
      String messageName,
      Limits limits,
      int maxDepth)
      throws DescriptorValidationException {
    if (set.getSerializedSize() > limits.maxStringBytes())
      throw new ContractViolation("Descriptor byte budget exceeded");
    if (set.getFileCount() > limits.maxColumns())
      throw new ContractViolation("Descriptor file budget exceeded");
    Map<String, FileDescriptorProto> pending = new LinkedHashMap<>();
    for (FileDescriptorProto file : set.getFileList())
      if (pending.put(file.getName(), file) != null)
        throw new ContractViolation("Duplicate descriptor file");
    Map<String, FileDescriptor> built = new LinkedHashMap<>();
    while (!pending.isEmpty()) {
      boolean progress = false;
      var iterator = pending.entrySet().iterator();
      while (iterator.hasNext()) {
        FileDescriptorProto file = iterator.next().getValue();
        if (!built.keySet().containsAll(file.getDependencyList())) continue;
        FileDescriptor[] dependencies =
            file.getDependencyList().stream().map(built::get).toArray(FileDescriptor[]::new);
        built.put(file.getName(), FileDescriptor.buildFrom(file, dependencies));
        iterator.remove();
        progress = true;
      }
      if (!progress) throw new ContractViolation("Missing or cyclic descriptor imports");
    }
    Deque<Descriptor> messages = new ArrayDeque<>();
    for (FileDescriptor file : built.values()) messages.addAll(file.getMessageTypes());
    while (!messages.isEmpty()) {
      Descriptor candidate = messages.removeFirst();
      if (candidate.getFullName().equals(messageName))
        return new ProtobufAdapter(language, candidate, limits, maxDepth);
      messages.addAll(candidate.getNestedTypes());
    }
    throw new ContractViolation("Unknown message: " + messageName);
  }

  public Descriptor descriptor() {
    return descriptor;
  }

  private AbstractRow record(Message message, Schema schema, Budget budget, int depth) {
    if (depth > maxDepth) throw new ContractViolation("Protobuf nesting budget exceeded");
    List<FieldDescriptor> fields = message.getDescriptorForType().getFields();
    budget.cells(fields.size());
    Object[] values = new Object[fields.size()];
    for (int i = 0; i < values.length; i++) {
      FieldDescriptor field = fields.get(i);
      if (field.hasPresence() && !message.hasField(field)) {
        values[i] = Missing.INSTANCE;
        continue;
      }
      if (field.isRepeated()) {
        int count = message.getRepeatedFieldCount(field);
        budget.cells(count);
        Object[] items = new Object[count];
        for (int j = 0; j < count; j++)
          items[j] = scalar(message.getRepeatedField(field, j), field, budget, depth);
        values[i] = new DataArray(items);
      } else values[i] = scalar(message.getField(field), field, budget, depth);
    }
    StaticLayout layout = layouts.get(message.getDescriptorForType());
    return layout == null
        ? Batch.copyOf(schema, Collections.singletonList(values), limits).row(0)
        : layout.create(values);
  }

  private Object scalar(Object value, FieldDescriptor field, Budget budget, int depth) {
    if (value instanceof Message nested) {
      Schema nestedSchema = schemas.get(nested.getDescriptorForType());
      if (nestedSchema == null)
        throw new ContractViolation("Unexpected nested Protobuf descriptor");
      return record(nested, nestedSchema, budget, depth + 1);
    }
    if (value instanceof ByteString bytes) {
      budget.cells(bytes.size());
      Object[] values = new Object[bytes.size()];
      for (int i = 0; i < values.length; i++)
        values[i] = (long) Byte.toUnsignedInt(bytes.byteAt(i));
      return new DataArray(values);
    }
    if (value instanceof EnumValueDescriptor e) value = e.getName();
    if (value instanceof String s) budget.string(s);
    if (field.getType() == FieldDescriptor.Type.UINT32
        || field.getType() == FieldDescriptor.Type.FIXED32)
      return Integer.toUnsignedLong((Integer) value);
    if (field.getType() == FieldDescriptor.Type.UINT64
        || field.getType() == FieldDescriptor.Type.FIXED64)
      return Values.normalize(new BigInteger(Long.toUnsignedString((Long) value)));
    return Values.normalize(value);
  }

  private final class Budget {
    private long cells;
    private long strings;

    void cells(int count) {
      cells += count;
      if (cells > limits.maxCells()) throw new ContractViolation("Protobuf value budget exceeded");
    }

    void string(String value) {
      strings += 2L * value.length();
      if (strings > limits.maxStringBytes())
        throw new ContractViolation("Protobuf string budget exceeded");
    }
  }
}
