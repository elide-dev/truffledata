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

  /** Schema-generated producer; each result must be a fresh, exclusively owned slab array. */
  @FunctionalInterface
  public interface SlabDecoder {
    byte[] decode(byte[] input, int offset, int length, SlabLimits limits);

    /** Existing generated/custom producers retain the checked table fallback for projections. */
    default byte[] decodeProjected(
        Layout layout,
        byte[] input,
        int offset,
        int length,
        SlabLimits limits,
        Projection projection) {
      return SlabCodec.decode(layout, input, offset, length, limits, projection);
    }
  }

  /** Immutable model hook that binds generated code to the complete checked type definition. */
  @FunctionalInterface
  public interface SlabDecoderFactory {
    SlabDecoder bind(ProtoPlan plan);
  }

  /**
   * Schema-generated slab row classes of one {@link Layout}: a final class per (layout type,
   * root-or-child), so a read node's layout guard is an exact class compare and every field read is
   * a constant-offset load. Generated rows exist only on unprojected owning slabs; projected and
   * lazily expanded rows keep the generic {@link SlabRow}.
   */
  public interface SlabRows {
    /** The root message of a verified owning slab (see {@code SlabMessage} verification bits). */
    SlabMessage root(Layout layout, byte[] slab, int extent, int verification);

    /** A child record of {@code owner} at {@code record} whose layout type is {@code type}. */
    SlabRow child(SlabMessage owner, int type, int record);
  }

  /**
   * Binds generated row classes to a layout whose every type matches the generated definitions, or
   * returns null so that layout keeps the generic rows.
   */
  @FunctionalInterface
  public interface SlabRowsFactory {
    @Nullable SlabRows bind(Layout layout);
  }

  /** All graph references resolve once when the model is built, including recursive messages. */
  public static final class Model {
    private final Map<String, ProtoPlan> types;
    final Limits limits;
    final int maxDepth;
    final int maxWireBytes;
    final @Nullable SlabDecoderFactory slabDecoders;
    final @Nullable SlabRowsFactory slabRows;

    @TruffleBoundary
    public Model(
        @Nullable TruffleLanguage<?> language,
        List<Type> definitions,
        Limits limits,
        int maxDepth,
        int maxWireBytes) {
      this(language, definitions, limits, maxDepth, maxWireBytes, null, null);
    }

    /** Generated contracts supply their producer factory; runtime models use the table decoder. */
    public Model(
        @Nullable TruffleLanguage<?> language,
        List<Type> definitions,
        Limits limits,
        int maxDepth,
        int maxWireBytes,
        @Nullable SlabDecoderFactory slabDecoders) {
      this(language, definitions, limits, maxDepth, maxWireBytes, slabDecoders, null);
    }

    /** Generated contracts also supply their slab row classes; runtime models use generic rows. */
    @TruffleBoundary
    public Model(
        @Nullable TruffleLanguage<?> language,
        List<Type> definitions,
        Limits limits,
        int maxDepth,
        int maxWireBytes,
        @Nullable SlabDecoderFactory slabDecoders,
        @Nullable SlabRowsFactory slabRows) {
      this.slabDecoders = slabDecoders;
      this.slabRows = slabRows;
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
        for (int ordinal = 0; ordinal < plan.fields.length; ordinal++) {
          FieldPlan field = plan.fields[ordinal];
          if (field.kind().message()) {
            ProtoPlan nested = type(field.typeName());
            if (field.map() != nested.definition.mapEntry())
              throw new ContractViolation("Invalid map entry reference");
            plan.nestedTypes[ordinal] = nested;
          }
        }
        if (plan.jsonType() == ProtoJsonType.STRUCT) {
          ProtoPlan entry = plan.nested(plan.ordinal(1));
          if (entry.size() != 2
              || entry.field(0).kind() != Kind.STRING
              || entry.field(1).kind() != Kind.MESSAGE
              || !entry.field(1).typeName().equals("google.protobuf.Value"))
            throw new ContractViolation("Invalid Struct entry schema");
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
  final boolean compactScalars;
  private final Type definition;

  @CompilationFinal(dimensions = 1)
  private final FieldPlan[] fields;

  @CompilationFinal(dimensions = 1)
  private final @Nullable ProtoPlan[] nestedTypes;

  private final Schema schema;
  private final ProtoJsonType jsonType;
  private final @Nullable StaticLayout layout;
  private final Map<Integer, Integer> numbers;

  @CompilationFinal(dimensions = 1)
  private final int[] denseNumbers;

  /** Slab layout of this plan's closure, compiled on first use; published safely once. */
  private volatile @Nullable Layout slabLayout;

  private volatile @Nullable SlabDecoder slabDecoder;

  private ProtoPlan(Model model, @Nullable TruffleLanguage<?> language, Type definition) {
    this.model = model;
    this.definition = definition;
    fields = definition.fields().toArray(FieldPlan[]::new);
    nestedTypes = new ProtoPlan[fields.length];
    model.limits.check(1, fields.length);
    boolean compact = fields.length > 0 && fields.length <= 2;
    for (var field : fields) {
      compact &=
          !field.repeated()
              && !field.explicitPresence()
              && (field.kind() == Kind.INT32 || field.kind() == Kind.INT64)
              && field.number() < 16
              && field.defaultValue().equals(0L);
    }
    compactScalars = compact;
    List<Field> contracts = new ArrayList<>();
    Map<Integer, Integer> indices = new HashMap<>();
    Set<String> names = new HashSet<>();
    for (int i = 0; i < fields.length; i++) {
      if (indices.put(fields[i].number(), i) != null || !names.add(fields[i].name()))
        throw new ContractViolation("Duplicate protobuf field");
      contracts.add(fields[i].contract());
    }
    // Bound the dense prefix by schema size, never by an untrusted maximum tag.
    // Common low field numbers avoid hashing and boxing; sparse tags retain the map path.
    int denseSize = (int) Math.min(4096L, 2L * fields.length + 1);
    denseNumbers = new int[denseSize];
    Arrays.fill(denseNumbers, -1);
    for (var field : indices.entrySet()) {
      if (field.getKey() < denseSize) denseNumbers[field.getKey()] = field.getValue();
    }
    numbers = Map.copyOf(indices);
    schema = new Schema(definition.name(), contracts);
    layout = language == null ? null : new StaticLayout(language, schema, model.limits);
    jsonType = ProtoJsonType.classify(this);
  }

  /** The model's generated slab row classes, bound per layout; null for runtime models. */
  @Nullable SlabRowsFactory slabRows() {
    return model.slabRows;
  }

  /** The slab layout rooted at this plan (type 0), compiled once; identity-stable per plan. */
  @TruffleBoundary
  public Layout layout() {
    Layout result = slabLayout;
    if (result == null) {
      synchronized (this) {
        result = slabLayout;
        if (result == null) slabLayout = result = Layout.compile(this);
      }
    }
    return result;
  }

  /**
   * Decode to an exclusively owned byte array, choosing this model's generated producer when
   * present.
   */
  @TruffleBoundary
  public byte[] decodeSlabBytes(byte[] input, int offset, int length, SlabLimits limits) {
    SlabDecoderFactory factory = model.slabDecoders;
    if (factory == null) return SlabCodec.decode(layout(), input, offset, length, limits);
    SlabDecoder decoder = slabDecoder;
    if (decoder == null) {
      synchronized (this) {
        decoder = slabDecoder;
        if (decoder == null) slabDecoder = decoder = Objects.requireNonNull(factory.bind(this));
      }
    }
    return decoder.decode(input, offset, length, limits);
  }

  /** Decode a projection through a prepared generated producer when it supports that shape. */
  @TruffleBoundary
  public byte[] decodeSlabBytes(
      byte[] input, int offset, int length, SlabLimits limits, Projection projection) {
    projection.requireLayout(layout());
    SlabDecoderFactory factory = model.slabDecoders;
    if (factory == null)
      return SlabCodec.decode(layout(), input, offset, length, limits, projection);
    SlabDecoder decoder = slabDecoder;
    if (decoder == null) {
      synchronized (this) {
        decoder = slabDecoder;
        if (decoder == null) slabDecoder = decoder = Objects.requireNonNull(factory.bind(this));
      }
    }
    return decoder.decodeProjected(layout(), input, offset, length, limits, projection);
  }

  public ProtoJsonType jsonType() {
    return jsonType;
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
    return number >= 0 && number < denseNumbers.length
        ? denseNumbers[number]
        : numbers.getOrDefault(number, -1);
  }

  public ProtoPlan nested(int ordinal) {
    ProtoPlan nested = nestedTypes[ordinal];
    if (nested == null) throw new ContractViolation("Expected message field");
    return nested;
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

  /** Descriptor-free ProtoJSON, including schema-specialized well-known types. */
  @TruffleBoundary
  public ProtoRow parseJson(String json) {
    return ProtoJson.decode(this, json, List.of());
  }

  /** Explicit allowlist for Any payloads; no descriptor discovery or network resolution. */
  @TruffleBoundary
  public ProtoRow parseJson(String json, List<ProtoPlan> anyTypes) {
    return ProtoJson.decode(this, json, anyTypes);
  }

  /** Decode directly into a durable owning slab; no native library is needed. */
  @TruffleBoundary
  public SlabMessage decodeSlab(byte[] wire) {
    return SlabMessage.decode(this, wire);
  }

  @TruffleBoundary
  public byte[] encode(ProtoRow row) {
    if (row.plan() != this) throw new ContractViolation("Protobuf plan identity mismatch");
    return ProtoCodec.encode(row);
  }

  /** Encode any durable protobuf representation, including generated records and slab rows. */
  @TruffleBoundary
  public byte[] encode(ProtoAccess row) {
    if (row.plan() != this) throw new ContractViolation("Protobuf plan identity mismatch");
    if (!(row instanceof ProtoRow || row instanceof GeneratedMessage || row instanceof SlabRow))
      throw new ContractViolation("Encoding requires a durable protobuf row");
    return ProtoCodec.encode(row);
  }

  /** Structural contract comparison, including byte defaults by value rather than identity. */
  @TruffleBoundary
  public boolean matchesDefinition(Type expected) {
    Type actual = definition();
    if (!actual.name().equals(expected.name())
        || actual.mapEntry() != expected.mapEntry()
        || actual.fields().size() != expected.fields().size()) return false;
    for (int i = 0; i < actual.fields().size(); i++) {
      FieldPlan a = actual.fields().get(i), b = expected.fields().get(i);
      if (a.defaultValue() instanceof DataArray x && b.defaultValue() instanceof DataArray y) {
        if (x.size() != y.size()) return false;
        for (int j = 0; j < x.size(); j++) if (!x.get(j).equals(y.get(j))) return false;
      } else if (a.defaultValue() instanceof ExactNumber x
          && b.defaultValue() instanceof ExactNumber y) {
        if (x.decimal().compareTo(y.decimal()) != 0) return false;
      } else if (!a.defaultValue().equals(b.defaultValue())) return false;
      b =
          new FieldPlan(
              b.name(),
              b.number(),
              b.kind(),
              b.repeated(),
              b.map(),
              b.explicitPresence(),
              b.required(),
              b.packed(),
              b.oneof(),
              b.typeName(),
              b.enumType(),
              a.defaultValue(),
              b.jsonName());
      if (!a.equals(b)) return false;
    }
    return true;
  }

  /** Direct access: keep this plan constant in compiled guest code. */
  public Object get(ProtoAccess row, int ordinal) {
    if (row.plan() != this) throw new ContractViolation("Generated plan mismatch");
    if (row instanceof ProtoRow owned && !compactScalars)
      return directValue(owned.storage, ordinal);
    return row.get(ordinal);
  }

  public long getLong(ProtoAccess row, int ordinal) {
    if (row.plan() != this) throw new ContractViolation("Generated plan mismatch");
    if (row instanceof ProtoRow owned) {
      if (compactScalars) {
        Objects.checkIndex(ordinal, fields.length);
        return owned.scalarUnchecked(ordinal);
      }
      return directLong(owned.storage, ordinal);
    }
    return row.getLong(ordinal);
  }

  public double getDouble(ProtoAccess row, int ordinal) {
    if (row.plan() != this) throw new ContractViolation("Generated plan mismatch");
    if (row instanceof ProtoRow owned && !compactScalars && layout != null)
      return layout.getDouble((StaticLayout.Record) owned.storage, ordinal);
    Object value = get(row, ordinal);
    if (value instanceof Double number) return number;
    throw new ContractViolation("Expected FLOAT64");
  }

  public boolean getBoolean(ProtoAccess row, int ordinal) {
    if (row.plan() != this) throw new ContractViolation("Generated plan mismatch");
    if (row instanceof ProtoRow owned && !compactScalars && layout != null)
      return layout.getBoolean((StaticLayout.Record) owned.storage, ordinal);
    Object value = get(row, ordinal);
    if (value instanceof Boolean flag) return flag;
    throw new ContractViolation("Expected BOOLEAN");
  }

  public int getSize(ProtoAccess row, int ordinal) {
    if (row instanceof ProtoRow) {
      Object value = get(row, ordinal);
      if (value instanceof DataArray array) return array.size();
      return durableArraySize(value);
    }
    if (row.plan() != this) throw new ContractViolation("Generated plan mismatch");
    return row.getSize(ordinal);
  }

  // The ordinary ProtoRow representation stores concrete DataArray values. Other durable
  // arrays remain supported without leaving an opaque interface call in the guest graph.
  @TruffleBoundary
  private static int durableArraySize(Object value) {
    if (value instanceof OwnedArray array) return array.size();
    throw new ContractViolation("Expected array");
  }

  Object directValue(Object storage, int ordinal) {
    if (layout != null) return layout.get((StaticLayout.Record) storage, ordinal);
    return DataLibrary.factory().getUncached().read(storage, ordinal);
  }

  long directLong(Object storage, int ordinal) {
    if (layout != null) return layout.getLong((StaticLayout.Record) storage, ordinal);
    try {
      return DataLibrary.factory().getUncached().readLong(storage, ordinal);
    } catch (com.oracle.truffle.api.nodes.UnexpectedResultException error) {
      throw new ContractViolation("Expected INT64");
    }
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
