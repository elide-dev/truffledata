package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.staticobject.*;

/** Language-owned static shape. Required INT64 properties use primitive final storage. */
public final class StaticLayout {
  private final Schema schema;
  private final Limits limits;
  private final StaticShape<DefaultStaticObjectFactory> shape;

  @CompilationFinal(dimensions = 1)
  private final StaticProperty[] properties;

  @TruffleBoundary
  public StaticLayout(TruffleLanguage<?> language, Schema schema, Limits limits) {
    limits.check(1, schema.size());
    this.schema = schema;
    this.limits = limits;
    StaticShape.Builder builder = StaticShape.newBuilder(language);
    properties = new StaticProperty[schema.size()];
    for (int i = 0; i < properties.length; i++) {
      properties[i] = new DefaultStaticProperty("field" + i);
      builder.property(properties[i], schema.requiredLong(i) ? long.class : Object.class, true);
    }
    shape = builder.build();
  }

  @Idempotent
  Schema schema() {
    return schema;
  }

  StaticProperty property(int index) {
    return properties[index];
  }

  @TruffleBoundary
  public Record create(Object... values) {
    if (values.length != schema.size())
      throw new ContractViolation("Static record width differs from contract");
    Object storage = shape.getFactory().create();
    long stringBytes = 0;
    for (int i = 0; i < values.length; i++) {
      Object value = Values.validate(schema.field(i), Values.normalize(values[i]));
      if (value instanceof String text) {
        stringBytes += 2L * text.length();
        if (stringBytes > limits.maxStringBytes())
          throw new ContractViolation("Static record string budget exceeded");
      }
      if (schema.requiredLong(i)) properties[i].setLong(storage, (Long) value);
      else properties[i].setObject(storage, value);
    }
    return new Record(this, storage);
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
          @Cached("layout.schema().requiredLong(cachedIndex)") boolean primitive) {
        return primitive ? property.getLong(row.storage) : property.getObject(row.storage);
      }

      @Specialization(replaces = "cached")
      @TruffleBoundary
      static Object generic(Record row, int index) {
        return row.descriptor.requiredLong(index)
            ? row.layout.properties[index].getLong(row.storage)
            : row.layout.properties[index].getObject(row.storage);
      }
    }

    @ExportMessage
    static class ReadLong {
      @Specialization(
          guards = {
            "row.layout() == layout",
            "index == cachedIndex",
            "layout.schema().requiredLong(cachedIndex)"
          },
          limit = "3")
      static long required(
          Record row,
          int index,
          @Cached("row.layout()") StaticLayout layout,
          @Cached("index") int cachedIndex,
          @Cached("layout.property(cachedIndex)") StaticProperty property) {
        return property.getLong(row.storage);
      }

      @Specialization(replaces = "required")
      @TruffleBoundary
      static long generic(Record row, int index) throws UnexpectedResultException {
        Object value = Read.generic(row, index);
        if (value instanceof Long n) return n;
        throw new UnexpectedResultException(value);
      }
    }
  }
}
