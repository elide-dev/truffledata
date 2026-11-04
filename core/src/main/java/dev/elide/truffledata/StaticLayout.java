package dev.elide.truffledata;

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
  private final StaticShape<DefaultStaticObjectFactory> shape;

  @CompilationFinal(dimensions = 1)
  private final StaticProperty[] properties;

  @CompilationFinal(dimensions = 1)
  private final @Nullable StaticProperty[] states;

  @CompilationFinal(dimensions = 1)
  private final Storage[] storageKinds;

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
  public Record create(Object... values) {
    if (values.length != schema.size())
      throw new ContractViolation("Static record width differs from contract");
    return read(index -> values[index], null);
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
    PayloadBudget budget = new PayloadBudget(limits, parent);
    budget.cells(schema.size());
    for (int i = 0; i < schema.size(); i++) {
      Object value = Values.normalize(source.read(i));
      budget.accept(value);
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

  @ExportLibrary(DataLibrary.class)
  public static final class Record extends AbstractRow {
    private final StaticLayout layout;
    private final Object storage;

    private Record(StaticLayout layout, Object storage) {
      super(layout.schema);
      this.layout = layout;
      this.storage = storage;
    }

    @Idempotent
    StaticLayout layout() {
      return layout;
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
