package dev.elide.truffledata.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.*;
import com.google.protobuf.Message;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import java.math.BigInteger;
import java.util.*;

/** Snapshot adapter. Reflection/conversion happens once at ingestion, never in field read nodes. */
public final class ProtobufAdapter {
  private final Descriptor descriptor;
  private final Limits limits;
  private final int maxDepth;
  private final Schema schema;
  private final Map<Descriptor, Schema> schemas;

  @TruffleBoundary
  public ProtobufAdapter(Descriptor descriptor, Limits limits, int maxDepth) {
    if (maxDepth < 1 || maxDepth > 128)
      throw new IllegalArgumentException("Depth must be between 1 and 128");
    this.descriptor = descriptor;
    this.limits = limits;
    this.maxDepth = maxDepth;
    schemas = compileSchemas(descriptor);
    schema = Objects.requireNonNull(schemas.get(descriptor));
  }

  public Schema schema() {
    return schema;
  }

  @TruffleBoundary
  public Batch.Row snapshot(Message message) {
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
      Field.Kind kind =
          f.isRepeated()
              ? Field.Kind.ANY
              : switch (f.getJavaType()) {
                case INT, LONG ->
                    f.getType() == FieldDescriptor.Type.UINT64
                            || f.getType() == FieldDescriptor.Type.FIXED64
                        ? Field.Kind.ANY
                        : Field.Kind.INT64;
                case FLOAT, DOUBLE -> Field.Kind.FLOAT64;
                case BOOLEAN -> Field.Kind.BOOLEAN;
                case STRING, ENUM -> Field.Kind.STRING;
                default -> Field.Kind.ANY;
              };
      fields.add(new Field(f.getName(), kind, false, f.hasPresence() && !f.isRequired()));
    }
    return new Schema(fields);
  }

  private Batch.Row record(Message message, Schema schema, Budget budget, int depth) {
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
    return Batch.copyOf(schema, Collections.singletonList(values), limits).row(0);
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
