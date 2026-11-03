package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import dev.elide.truffledata.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * Descriptor-free immutable protobuf contracts. A model owns schema/layout identity and budgets.
 */
public final class ProtoPlan {
  public enum Kind {
    DOUBLE(1),
    FLOAT(5),
    INT64(0),
    UINT64(0),
    INT32(0),
    FIXED64(1),
    FIXED32(5),
    BOOL(0),
    STRING(2),
    GROUP(3),
    MESSAGE(2),
    BYTES(2),
    UINT32(0),
    ENUM(0),
    SFIXED32(5),
    SFIXED64(1),
    SINT32(0),
    SINT64(0);
    final int wire;

    Kind(int wire) {
      this.wire = wire;
    }

    public boolean message() {
      return this == MESSAGE || this == GROUP;
    }

    public boolean packable() {
      return !message() && this != STRING && this != BYTES;
    }
  }

  /**
   * Enum aliases retain their names and numbers; unknown open-enum numbers remain plain integers.
   */
  public record EnumType(String name, boolean open, Map<String, Integer> values) {
    public EnumType {
      Objects.requireNonNull(name);
      values = Map.copyOf(values);
    }

    public boolean accepts(int number) {
      return open || values.containsValue(number);
    }
  }

  /** Ordinals are list positions, distinct from wire field numbers. Empty typeName means scalar. */
  public record FieldPlan(
      String name,
      int number,
      Kind kind,
      boolean repeated,
      boolean map,
      boolean explicitPresence,
      boolean required,
      boolean packed,
      int oneof,
      String typeName,
      @Nullable EnumType enumType,
      Object defaultValue,
      String jsonName) {
    /** Source-compatible constructor for contracts using the standard JSON field spelling. */
    public FieldPlan(
        String name,
        int number,
        Kind kind,
        boolean repeated,
        boolean map,
        boolean explicitPresence,
        boolean required,
        boolean packed,
        int oneof,
        String typeName,
        @Nullable EnumType enumType,
        Object defaultValue) {
      this(
          name,
          number,
          kind,
          repeated,
          map,
          explicitPresence,
          required,
          packed,
          oneof,
          typeName,
          enumType,
          defaultValue,
          standardJsonName(name));
    }

    public FieldPlan {
      Objects.requireNonNull(name);
      Objects.requireNonNull(jsonName);
      Objects.requireNonNull(kind);
      Objects.requireNonNull(typeName);
      Objects.requireNonNull(defaultValue);
      if (number < 1 || number > 536870911 || (number >= 19000 && number <= 19999))
        throw new ContractViolation("Invalid protobuf field number");
      if (oneof < -1
          || (repeated && (explicitPresence || required || oneof >= 0))
          || (required && !explicitPresence)
          || (oneof >= 0 && !explicitPresence)
          || (map && (!repeated || kind != Kind.MESSAGE))
          || (packed && (!repeated || !kind.packable()))
          || kind.message() != !typeName.isEmpty()
          || (kind == Kind.ENUM) != (enumType != null))
        throw new ContractViolation("Inconsistent protobuf field plan: " + name);
      Values.validate(scalarContract(name, kind, typeName), defaultValue);
      if ((required && oneof >= 0)
          || (kind.message() && defaultValue != Missing.INSTANCE)
          || (kind == Kind.BYTES && !((DataArray) defaultValue).isBytes())
          || (enumType != null && !repeated && !enumType.accepts(((Long) defaultValue).intValue())))
        throw new ContractViolation("Invalid protobuf default or presence contract");
      if (defaultValue instanceof String text) validateString(text);
    }

    public Field contract() {
      Field scalar = scalarContract(name, kind, typeName);
      return repeated
          ? new Field(name, Field.Kind.ARRAY, false, false, Constraints.NONE.withElement(scalar))
          : scalar;
    }
  }

  public record Type(String name, boolean mapEntry, List<FieldPlan> fields) {
    public Type {
      Objects.requireNonNull(name);
      fields = List.copyOf(fields);
    }
  }

  private static String standardJsonName(String name) {
    StringBuilder result = new StringBuilder();
    boolean upper = false;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '_') upper = true;
      else {
        result.append(upper && c >= 'a' && c <= 'z' ? (char) (c - 'a' + 'A') : c);
        upper = false;
      }
    }
    return result.toString();
  }

  /** All graph references resolve once when the model is built, including recursive messages. */
  public static final class Model {
    private final Map<String, ProtoPlan> types;
    final Limits limits;
    final int maxDepth;
    final int maxWireBytes;

    @TruffleBoundary
    public Model(
        @Nullable TruffleLanguage<?> language,
        List<Type> definitions,
        Limits limits,
        int maxDepth,
        int maxWireBytes) {
      this.limits = Objects.requireNonNull(limits);
      if (maxDepth < 1 || maxDepth > 128 || maxWireBytes < 0)
        throw new ContractViolation("Invalid protobuf decoding budgets");
      this.maxDepth = maxDepth;
      this.maxWireBytes = maxWireBytes;
      if (definitions.size() > limits.maxColumns()) throw new ContractViolation("Too many types");
      Map<String, ProtoPlan> built = new LinkedHashMap<>();
      long fields = 0;
      for (Type type : definitions) {
        fields += type.fields().size();
        if (fields > limits.maxCells()) throw new ContractViolation("Too many descriptor fields");
        if (built.put(type.name(), new ProtoPlan(this, language, type)) != null)
          throw new ContractViolation("Duplicate protobuf type: " + type.name());
      }
      types = Map.copyOf(built);
      for (ProtoPlan plan : types.values()) {
        for (FieldPlan field : plan.fields) {
          if (field.kind().message()) {
            ProtoPlan nested = type(field.typeName());
            if (field.map() != nested.definition.mapEntry())
              throw new ContractViolation("Invalid map entry reference");
          }
        }
        if (plan.definition.mapEntry()) {
          if (plan.size() != 2
              || plan.field(0).number() != 1
              || plan.field(1).number() != 2
              || plan.field(0).repeated()
              || plan.field(1).repeated()
              || !Set.of(
                      Kind.INT32,
                      Kind.INT64,
                      Kind.UINT32,
                      Kind.UINT64,
                      Kind.SINT32,
                      Kind.SINT64,
                      Kind.FIXED32,
                      Kind.FIXED64,
                      Kind.SFIXED32,
                      Kind.SFIXED64,
                      Kind.BOOL,
                      Kind.STRING)
                  .contains(plan.field(0).kind()))
            throw new ContractViolation("Invalid map entry contract");
        }
      }
    }

    @TruffleBoundary
    public ProtoPlan type(String name) {
      ProtoPlan plan = types.get(name);
      if (plan == null) throw new ContractViolation("Unknown protobuf type: " + name);
      return plan;
    }
  }

  final Model model;
  private final Type definition;

  @CompilationFinal(dimensions = 1)
  private final FieldPlan[] fields;

  private final Schema schema;
  private final @Nullable StaticLayout layout;
  private final Map<Integer, Integer> numbers;

  private ProtoPlan(Model model, @Nullable TruffleLanguage<?> language, Type definition) {
    this.model = model;
    this.definition = definition;
    fields = definition.fields().toArray(FieldPlan[]::new);
    model.limits.check(1, fields.length);
    List<Field> contracts = new ArrayList<>();
    Map<Integer, Integer> indices = new HashMap<>();
    Set<String> names = new HashSet<>();
    for (int i = 0; i < fields.length; i++) {
      if (indices.put(fields[i].number(), i) != null || !names.add(fields[i].name()))
        throw new ContractViolation("Duplicate protobuf field");
      contracts.add(fields[i].contract());
    }
    numbers = Map.copyOf(indices);
    schema = new Schema(definition.name(), contracts);
    layout = language == null ? null : new StaticLayout(language, schema, model.limits);
  }

  public Limits limits() {
    return model.limits;
  }

  public int maxDepth() {
    return model.maxDepth;
  }

  public int maxWireBytes() {
    return model.maxWireBytes;
  }

  public String name() {
    return definition.name();
  }

  public Schema schema() {
    return schema;
  }

  public int size() {
    return fields.length;
  }

  public FieldPlan field(int ordinal) {
    return fields[ordinal];
  }

  public Type definition() {
    return definition;
  }

  @TruffleBoundary
  public int ordinal(int number) {
    return numbers.getOrDefault(number, -1);
  }

  public ProtoPlan nested(int ordinal) {
    return model.type(field(ordinal).typeName());
  }

  @TruffleBoundary
  public ProtoRow.Builder newBuilder() {
    return new ProtoRow.Builder(this);
  }

  @TruffleBoundary
  public ProtoRow decode(byte[] wire) {
    return ProtoCodec.decode(this, wire);
  }

  /**
   * Reads the remaining region without changing position/limit or copying the whole input. The
   * caller must keep the region alive and immutable during this call. Returned rows own data.
   */
  @TruffleBoundary
  public ProtoRow decode(ByteBuffer wire) {
    return ProtoCodec.decode(this, wire);
  }

  /** Descriptor-free protobuf text format. Unknown fields and duplicate singular fields fail. */
  @TruffleBoundary
  public ProtoRow parseText(String text) {
    return ProtoText.decode(this, text);
  }

  /** Descriptor-free ProtoJSON for ordinary message types (not well-known-type mappings). */
  @TruffleBoundary
  public ProtoRow parseJson(String json) {
    return ProtoJson.decode(this, json);
  }

  @TruffleBoundary
  public byte[] encode(ProtoRow row) {
    if (row.plan() != this) throw new ContractViolation("Protobuf plan identity mismatch");
    return ProtoCodec.encode(row);
  }

  AbstractRow storage(Object[] values) {
    return layout == null
        ? Batch.copyOf(schema, Collections.singletonList(values), model.limits).row(0)
        : layout.create(values);
  }

  static void validateString(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
          throw new ContractViolation("Unpaired Unicode surrogate");
      } else if (Character.isLowSurrogate(c))
        throw new ContractViolation("Unpaired Unicode surrogate");
    }
  }

  static Field scalarContract(String name, Kind kind, String typeName) {
    return switch (kind) {
      case INT32, SINT32, SFIXED32, ENUM -> Field.integer(name, 32, true, false, false);
      case UINT32, FIXED32 -> Field.integer(name, 32, false, false, false);
      case INT64, SINT64, SFIXED64 -> Field.integer(name, 64, true, false, false);
      case UINT64, FIXED64 -> Field.integer(name, 64, false, false, false);
      case DOUBLE, FLOAT ->
          new Field(
              name,
              Field.Kind.FLOAT64,
              false,
              false,
              Constraints.NONE.withFloatBits(kind == Kind.FLOAT ? 32 : 64));
      case BOOL -> new Field(name, Field.Kind.BOOLEAN, false, false);
      case STRING -> new Field(name, Field.Kind.STRING, false, false);
      case BYTES ->
          new Field(
              name,
              Field.Kind.ARRAY,
              false,
              false,
              Constraints.NONE.withElement(Field.integer("byte", 8, false, false, false)));
      case MESSAGE, GROUP ->
          new Field(
              name, Field.Kind.RECORD, false, true, Constraints.NONE.withRecordType(typeName));
    };
  }
}
