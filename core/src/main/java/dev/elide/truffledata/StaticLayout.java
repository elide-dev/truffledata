package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.staticobject.*;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Language-owned immutable shape. Narrow primitive storage reflects validated contract widths. */
public final class StaticLayout {
  public enum Storage {
    BYTE,
    SHORT,
    INT,
    UBYTE,
    USHORT,
    UINT,
    LONG,
    FLOAT,
    DOUBLE,
    BOOLEAN,
    OBJECT
  }

  private final Schema schema;
  private final Limits limits;
  private final boolean primitivePayload;
  private final StaticShape<DefaultStaticObjectFactory> shape;

  @CompilationFinal(dimensions = 1)
  private final StaticProperty[] properties;

  @CompilationFinal(dimensions = 1)
  private final @Nullable StaticProperty[] states;

  @CompilationFinal(dimensions = 1)
  private final Storage[] storageKinds;

  @CompilationFinal(dimensions = 1)
  private final Accessor[] accessors;

  @TruffleBoundary
  public StaticLayout(TruffleLanguage<?> language, Schema schema, Limits limits) {
    limits.check(1, schema.size());
    this.schema = schema;
    this.limits = limits;
    StaticShape.Builder builder = StaticShape.newBuilder(language);
    properties = new StaticProperty[schema.size()];
    states = new StaticProperty[schema.size()];
    storageKinds = new Storage[schema.size()];
    for (int i = 0; i < properties.length; i++) {
      Field field = schema.field(i);
      Storage kind = storageKinds[i] = storage(field);
      properties[i] = new DefaultStaticProperty("field" + i);
      Class<?> type =
          switch (kind) {
            case BYTE, UBYTE -> byte.class;
            case SHORT, USHORT -> short.class;
            case INT, UINT -> int.class;
            case LONG -> long.class;
            case FLOAT -> float.class;
            case DOUBLE -> double.class;
            case BOOLEAN -> boolean.class;
            case OBJECT -> Object.class;
          };
      builder.property(properties[i], type, true);
      if (field.nullable() || field.optional()) {
        StaticProperty state = new DefaultStaticProperty("state" + i);
        states[i] = state;
        builder.property(state, byte.class, true);
      }
    }
    shape = builder.build();
    boolean primitives = true;
    for (int i = 0; i < schema.size(); i++) {
      Field.Kind kind = schema.field(i).kind();
      primitives &=
          kind == Field.Kind.INT64 || kind == Field.Kind.FLOAT64 || kind == Field.Kind.BOOLEAN;
    }
    primitivePayload = primitives;
    accessors = new Accessor[schema.size()];
    for (int i = 0; i < accessors.length; i++) accessors[i] = new Slot(this, i);
  }

  /** This layout's accessor for {@code ordinal}; it rejects records of every other layout. */
  public Accessor accessor(int ordinal) {
    return accessors[ordinal];
  }

  /** Constant property, storage kind and state of one field of this layout. */
  private static final class Slot extends Accessor {
    private final StaticLayout layout;
    private final StaticProperty property;
    private final Storage kind;
    private final @Nullable StaticProperty state;

    Slot(StaticLayout layout, int ordinal) {
      super(layout.schema, ordinal);
      this.layout = layout;
      property = layout.properties[ordinal];
      kind = layout.storageKinds[ordinal];
      state = layout.states[ordinal];
    }

    @Override
    public boolean accepts(Object row) {
      return row instanceof Record r && r.layout == layout;
    }

    private Object storage(Object row) {
      if (row instanceof Record r && r.layout == layout) return r.storage;
      throw mismatch();
    }

    /**
     * Storage of a row the read node's guard already admitted; only the layout check is skipped.
     */
    private Object trusted(Object row) {
      assertAccepted(row);
      return ((Record) row).storage;
    }

    @Override
    public Object read(Object row) {
      return value(storage(row), property, kind, state);
    }

    @Override
    public boolean present(Object row) {
      Object storage = storage(row);
      return state == null || state.getByte(storage) != 2;
    }

    /** Null or missing state as the rejected value, after the read of a present field. */
    private void checkPresent(Object storage) throws UnexpectedResultException {
      if (state != null) {
        byte status = state.getByte(storage);
        if (status != 0)
          throw new UnexpectedResultException(status == 1 ? DataNull.INSTANCE : Missing.INSTANCE);
      }
    }

    @Override
    public long readLong(Object row) throws UnexpectedResultException {
      return longOf(storage(row));
    }

    @Override
    protected long readLongBound(Object row) throws UnexpectedResultException {
      return longOf(trusted(row));
    }

    private long longOf(Object storage) throws UnexpectedResultException {
      checkPresent(storage);
      if (field().kind() == Field.Kind.INT64) return integer(storage, property, kind);
      Object value = value(storage, property, kind, null);
      if (value instanceof Long n) return n;
      throw new UnexpectedResultException(value);
    }

    @Override
    public double readDouble(Object row) throws UnexpectedResultException {
      return doubleOf(storage(row));
    }

    @Override
    protected double readDoubleBound(Object row) throws UnexpectedResultException {
      return doubleOf(trusted(row));
    }

    private double doubleOf(Object storage) throws UnexpectedResultException {
      checkPresent(storage);
      if (kind == Storage.FLOAT) return property.getFloat(storage);
      if (kind == Storage.DOUBLE) return property.getDouble(storage);
      Object value = value(storage, property, kind, null);
      if (value instanceof Double n) return n;
      throw new UnexpectedResultException(value);
    }

    @Override
    public boolean readBoolean(Object row) throws UnexpectedResultException {
      return booleanOf(storage(row));
    }

    @Override
    protected boolean readBooleanBound(Object row) throws UnexpectedResultException {
      return booleanOf(trusted(row));
    }

    private boolean booleanOf(Object storage) throws UnexpectedResultException {
      checkPresent(storage);
      if (kind == Storage.BOOLEAN) return property.getBoolean(storage);
      Object value = value(storage, property, kind, null);
      if (value instanceof Boolean b) return b;
      throw new UnexpectedResultException(value);
    }

    @Override
    protected int lengthBound(Object row) {
      return lengthOf(value(trusted(row), property, kind, state));
    }
  }

  private static Storage storage(Field field) {
    return switch (field.kind()) {
      case INT64 -> {
        var width = field.constraints().integer();
        var range = field.constraints().integerRange();
        if (range != null) {
          long min = Math.max(range.min(), width == null ? Long.MIN_VALUE : width.minimum());
          long max = Math.min(range.max(), width == null ? Long.MAX_VALUE : width.maximum());
          if (min >= Byte.MIN_VALUE && max <= Byte.MAX_VALUE) yield Storage.BYTE;
          if (min >= 0 && max <= 255) yield Storage.UBYTE;
          if (min >= Short.MIN_VALUE && max <= Short.MAX_VALUE) yield Storage.SHORT;
          if (min >= 0 && max <= 65535) yield Storage.USHORT;
          if (min >= Integer.MIN_VALUE && max <= Integer.MAX_VALUE) yield Storage.INT;
          if (min >= 0 && max <= 4294967295L) yield Storage.UINT;
          yield Storage.LONG;
        }
        if (width == null || width.bits() == 64) yield Storage.LONG;
        if (!width.signed())
          yield width.bits() == 8
              ? Storage.UBYTE
              : width.bits() == 16 ? Storage.USHORT : Storage.UINT;
        int bits = width.bits();
        yield bits <= 8
            ? Storage.BYTE
            : bits <= 16 ? Storage.SHORT : bits <= 32 ? Storage.INT : Storage.LONG;
      }
      case FLOAT64 -> field.constraints().floatBits() == 32 ? Storage.FLOAT : Storage.DOUBLE;
      case BOOLEAN -> Storage.BOOLEAN;
      default -> Storage.OBJECT;
    };
  }

  @Idempotent
  public Schema schema() {
    return schema;
  }

  public Storage storageKind(int index) {
    return storageKinds[index];
  }

  @Idempotent
  boolean longField(int index) {
    return schema.field(index).kind() == Field.Kind.INT64;
  }

  StaticProperty property(int index) {
    return properties[index];
  }

  @Nullable StaticProperty state(int index) {
    return states[index];
  }

  @TruffleBoundary
  public Record create(@Nullable Object... values) {
    if (values.length != schema.size())
      throw new ContractViolation("Static record width differs from contract");
    return read(index -> values[index], null);
  }

  /**
   * Construct a single INT64 field without boxing or a varargs array. Keep the layout constant in
   * compiled guest code to allow temporary storage to be eliminated. Width and range contracts are
   * enforced before allocation; nullable and optional fields receive a present, non-null value.
   */
  public Record createLong(long value) {
    if (!CompilerDirectives.isPartialEvaluationConstant(this)) return genericCreateLong(value);
    return directCreateLong(value);
  }

  @TruffleBoundary
  private Record genericCreateLong(long value) {
    return directCreateLong(value);
  }

  private Record directCreateLong(long value) {
    if (schema.size() != 1 || !longField(0))
      throw new ContractViolation("Expected a single INT64 field");
    Values.validateLong(schema.field(0), value);
    Object storage = shape.getFactory().create();
    StaticProperty state = states[0];
    if (state != null) state.setByte(storage, (byte) 0);
    StaticProperty property = properties[0];
    switch (storageKinds[0]) {
      case BYTE, UBYTE -> property.setByte(storage, (byte) value);
      case SHORT, USHORT -> property.setShort(storage, (short) value);
      case INT, UINT -> property.setInt(storage, (int) value);
      case LONG -> property.setLong(storage, value);
      default -> throw new IllegalStateException("Not integer storage");
    }
    return new Record(this, storage);
  }

  /** Synchronous field source; each ordinal is read once, in order, and is never retained. */
  @FunctionalInterface
  public interface Source<E extends Exception> {
    @Nullable Object read(int column) throws E;
  }

  /**
   * Fill owned static storage directly, without a temporary row array. Layout and parent budgets
   * share one payload walk. No record escapes if reading or validation fails; accepted parent
   * charges are not refunded.
   */
  @TruffleBoundary
  public <E extends Exception> Record read(Source<E> source, @Nullable PayloadBudget parent)
      throws E {
    Object storage = shape.getFactory().create();
    // The constructor already checked local cell limits. Valid primitive fields carry no
    // nested cells or string payload; cumulative parent accounting still charges the row.
    PayloadBudget budget = primitivePayload ? null : new PayloadBudget(limits, parent);
    if (budget != null) budget.cells(schema.size());
    else if (parent != null) parent.cells(schema.size());
    for (int i = 0; i < schema.size(); i++) {
      Object value = Values.normalize(source.read(i));
      if (budget != null) budget.accept(value);
      Values.validate(schema.field(i), value);
      byte state = (byte) (value == DataNull.INSTANCE ? 1 : value == Missing.INSTANCE ? 2 : 0);
      StaticProperty stateProperty = states[i];
      if (stateProperty != null) stateProperty.setByte(storage, state);
      if (state != 0) continue;
      StaticProperty property = properties[i];
      switch (storageKinds[i]) {
        case BYTE, UBYTE -> property.setByte(storage, ((Long) value).byteValue());
        case SHORT, USHORT -> property.setShort(storage, ((Long) value).shortValue());
        case INT, UINT -> property.setInt(storage, ((Long) value).intValue());
        case LONG -> property.setLong(storage, (Long) value);
        case FLOAT -> property.setFloat(storage, ((Double) value).floatValue());
        case DOUBLE -> property.setDouble(storage, (Double) value);
        case BOOLEAN -> property.setBoolean(storage, (Boolean) value);
        case OBJECT -> property.setObject(storage, value);
      }
    }
    return new Record(this, storage);
  }

  private static long integer(Object storage, StaticProperty property, Storage kind) {
    return switch (kind) {
      case BYTE -> property.getByte(storage);
      case SHORT -> property.getShort(storage);
      case INT -> property.getInt(storage);
      case UBYTE -> Byte.toUnsignedLong(property.getByte(storage));
      case USHORT -> Short.toUnsignedLong(property.getShort(storage));
      case UINT -> Integer.toUnsignedLong(property.getInt(storage));
      case LONG -> property.getLong(storage);
      default -> throw new IllegalStateException("Not integer storage");
    };
  }

  private static Object value(
      Object storage, StaticProperty property, Storage kind, @Nullable StaticProperty state) {
    if (state != null) {
      byte present = state.getByte(storage);
      if (present != 0) return present == 1 ? DataNull.INSTANCE : Missing.INSTANCE;
    }
    return switch (kind) {
      case BYTE, SHORT, INT, UBYTE, USHORT, UINT, LONG -> integer(storage, property, kind);
      case FLOAT -> (double) property.getFloat(storage);
      case DOUBLE -> property.getDouble(storage);
      case BOOLEAN -> property.getBoolean(storage);
      case OBJECT -> Objects.requireNonNull(property.getObject(storage));
    };
  }

  /** Direct access for a generated getter bound to this immutable layout. */
  public Object get(Record row, int ordinal) {
    if (!CompilerDirectives.isPartialEvaluationConstant(this)) return genericGet(row, ordinal);
    return directGet(row, ordinal);
  }

  @TruffleBoundary
  private Object genericGet(Record row, int ordinal) {
    return directGet(row, ordinal);
  }

  private Object directGet(Record row, int ordinal) {
    if (row.layout != this) throw new ContractViolation("Static layout mismatch");
    Objects.checkIndex(ordinal, properties.length);
    return value(row.storage, properties[ordinal], storageKinds[ordinal], states[ordinal]);
  }

  /** Primitive direct access, retaining type, null, and missing checks. */
  public long getLong(Record row, int ordinal) {
    if (!CompilerDirectives.isPartialEvaluationConstant(this)) return genericLong(row, ordinal);
    return directLong(row, ordinal);
  }

  @TruffleBoundary
  private long genericLong(Record row, int ordinal) {
    return directLong(row, ordinal);
  }

  private long directLong(Record row, int ordinal) {
    if (row.layout != this) throw new ContractViolation("Static layout mismatch");
    Objects.checkIndex(ordinal, properties.length);
    if (!longField(ordinal)) throw new ContractViolation("Expected INT64");
    StaticProperty state = states[ordinal];
    if (state != null && state.getByte(row.storage) != 0)
      throw new ContractViolation("Expected present, non-null INT64");
    return integer(row.storage, properties[ordinal], storageKinds[ordinal]);
  }

  public double getDouble(Record row, int ordinal) {
    if (!CompilerDirectives.isPartialEvaluationConstant(this)) return genericDouble(row, ordinal);
    return directDouble(row, ordinal);
  }

  @TruffleBoundary
  private double genericDouble(Record row, int ordinal) {
    return directDouble(row, ordinal);
  }

  private double directDouble(Record row, int ordinal) {
    if (row.layout != this) throw new ContractViolation("Static layout mismatch");
    Objects.checkIndex(ordinal, properties.length);
    Storage kind = storageKinds[ordinal];
    if (kind != Storage.FLOAT && kind != Storage.DOUBLE)
      throw new ContractViolation("Unexpected primitive type");
    StaticProperty state = states[ordinal];
    if (state != null && state.getByte(row.storage) != 0)
      throw new ContractViolation("Expected present, non-null value");
    StaticProperty property = properties[ordinal];
    return kind == Storage.FLOAT ? property.getFloat(row.storage) : property.getDouble(row.storage);
  }

  public boolean getBoolean(Record row, int ordinal) {
    if (!CompilerDirectives.isPartialEvaluationConstant(this)) return genericBoolean(row, ordinal);
    return directBoolean(row, ordinal);
  }

  @TruffleBoundary
  private boolean genericBoolean(Record row, int ordinal) {
    return directBoolean(row, ordinal);
  }

  private boolean directBoolean(Record row, int ordinal) {
    if (row.layout != this) throw new ContractViolation("Static layout mismatch");
    Objects.checkIndex(ordinal, properties.length);
    Storage kind = storageKinds[ordinal];
    if (kind != Storage.BOOLEAN) throw new ContractViolation("Unexpected primitive type");
    StaticProperty state = states[ordinal];
    if (state != null && state.getByte(row.storage) != 0)
      throw new ContractViolation("Expected present, non-null value");
    StaticProperty property = properties[ordinal];
    return property.getBoolean(row.storage);
  }

  @ExportLibrary(DataLibrary.class)
  public static final class Record extends AbstractRow implements SnapshotStorage {
    private final StaticLayout layout;
    private final Object storage;

    private Record(StaticLayout layout, Object storage) {
      super(layout.schema);
      this.layout = layout;
      this.storage = storage;
    }

    void acceptPayload(PayloadBudget budget, int depth) {
      // Primitive properties have already been validated and cannot contain nested payloads.
      for (int i = 0; i < layout.properties.length; i++)
        if (layout.storageKinds[i] == Storage.OBJECT)
          budget.accept(
              value(storage, layout.properties[i], Storage.OBJECT, layout.states[i]), depth);
    }

    @Idempotent
    StaticLayout layout() {
      return layout;
    }

    @Override
    public Accessor accessor(int ordinal) {
      return layout.accessors[ordinal];
    }

    @ExportMessage
    Schema getSchema() {
      return descriptor;
    }

    @ExportMessage
    static class Read {
      @Specialization(
          guards = {"row.layout() == layout", "index == cachedIndex"},
          limit = "3")
      static Object cached(
          Record row,
          int index,
          @Cached("row.layout()") StaticLayout layout,
          @Cached("index") int cachedIndex,
          @Cached("layout.property(cachedIndex)") StaticProperty property,
          @Cached("layout.storageKind(cachedIndex)") Storage kind,
          @Cached("layout.state(cachedIndex)") @Nullable StaticProperty state) {
        return value(row.storage, property, kind, state);
      }

      @Specialization(replaces = "cached")
      @TruffleBoundary
      static Object generic(Record row, int index) {
        return value(
            row.storage,
            row.layout.properties[index],
            row.layout.storageKinds[index],
            row.layout.states[index]);
      }
    }

    @ExportMessage
    static class ReadLong {
      @Specialization(
          guards = {
            "row.layout() == layout",
            "index == cachedIndex",
            "layout.longField(cachedIndex)"
          },
          limit = "3")
      static long cached(
          Record row,
          int index,
          @Cached("row.layout()") StaticLayout layout,
          @Cached("index") int cachedIndex,
          @Cached("layout.property(cachedIndex)") StaticProperty property,
          @Cached("layout.storageKind(cachedIndex)") Storage kind,
          @Cached("layout.state(cachedIndex)") @Nullable StaticProperty state)
          throws UnexpectedResultException {
        if (state != null) {
          byte status = state.getByte(row.storage);
          if (status != 0)
            throw new UnexpectedResultException(status == 1 ? DataNull.INSTANCE : Missing.INSTANCE);
        }
        return integer(row.storage, property, kind);
      }

      @Specialization(replaces = "cached")
      @TruffleBoundary
      static long generic(Record row, int index) throws UnexpectedResultException {
        Object value = Read.generic(row, index);
        if (value instanceof Long n) return n;
        throw new UnexpectedResultException(value);
      }
    }

    @ExportMessage
    static class ReadDouble {
      @Specialization(
          guards = {
            "row.layout() == layout",
            "index == cachedIndex",
            "layout.schema().requiredDouble(cachedIndex)"
          },
          limit = "3")
      static double required(
          Record row,
          int index,
          @Cached("row.layout()") StaticLayout layout,
          @Cached("index") int cachedIndex,
          @Cached("layout.property(cachedIndex)") StaticProperty property,
          @Cached("layout.storageKind(cachedIndex)") Storage kind) {
        return kind == Storage.FLOAT
            ? property.getFloat(row.storage)
            : property.getDouble(row.storage);
      }

      @Specialization(replaces = "required")
      @TruffleBoundary
      static double generic(Record row, int index) throws UnexpectedResultException {
        Object value = Read.generic(row, index);
        if (value instanceof Double n) return n;
        throw new UnexpectedResultException(value);
      }
    }

    @ExportMessage
    static class ReadBoolean {
      @Specialization(
          guards = {
            "row.layout() == layout",
            "index == cachedIndex",
            "layout.schema().requiredBoolean(cachedIndex)"
          },
          limit = "3")
      static boolean required(
          Record row,
          int index,
          @Cached("row.layout()") StaticLayout layout,
          @Cached("index") int cachedIndex,
          @Cached("layout.property(cachedIndex)") StaticProperty property) {
        return property.getBoolean(row.storage);
      }

      @Specialization(replaces = "required")
      @TruffleBoundary
      static boolean generic(Record row, int index) throws UnexpectedResultException {
        Object value = Read.generic(row, index);
        if (value instanceof Boolean n) return n;
        throw new UnexpectedResultException(value);
      }
    }
  }
}
