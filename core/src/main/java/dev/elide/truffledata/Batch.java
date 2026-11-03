package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable column storage. Retained rows remain valid after cursor advancement and close. */
public final class Batch {
  private final Schema schema;

  @CompilationFinal(dimensions = 1)
  private final Column[] columns;

  private final int size;

  private Batch(Schema schema, Column[] columns, int size) {
    this.schema = schema;
    this.columns = columns;
    this.size = size;
  }

  @TruffleBoundary
  public static Batch copyOf(Schema schema, List<Object[]> rows, Limits limits) {
    limits.check(rows.size(), schema.size());
    Column[] columns = new Column[schema.size()];
    for (int i = 0; i < columns.length; i++) columns[i] = new Column(schema.field(i), rows.size());
    long stringBytes = 0;
    for (int r = 0; r < rows.size(); r++) {
      Object[] row = rows.get(r);
      if (row.length != schema.size())
        throw new ContractViolation("Row width differs from contract");
      for (int c = 0; c < columns.length; c++) {
        Object value = Values.validate(schema.field(c), Values.normalize(row[c]));
        if (value instanceof String s) {
          stringBytes += 2L * s.length();
          if (stringBytes > limits.maxStringBytes())
            throw new ContractViolation("Batch string budget exceeded");
        }
        columns[c].set(r, value);
      }
    }
    return new Batch(schema, columns, rows.size());
  }

  public int size() {
    return size;
  }

  public Schema schema() {
    return schema;
  }

  public Row row(int index) {
    Objects.checkIndex(index, size);
    return new Row(this, index);
  }

  private static final class Column {
    private final long @Nullable [] longs;
    private final @Nullable Object @Nullable [] objects;
    private final byte @Nullable [] states;

    Column(Field field, int size) {
      longs = field.kind() == Field.Kind.INT64 ? new long[size] : null;
      objects = longs == null ? new Object[size] : null;
      states = field.nullable() || field.optional() ? new byte[size] : null;
    }

    void set(int index, Object value) {
      if (value == DataNull.INSTANCE || value == Missing.INSTANCE) {
        Objects.requireNonNull(states)[index] = (byte) (value == DataNull.INSTANCE ? 1 : 2);
      } else if (longs != null) longs[index] = (Long) value;
      else Objects.requireNonNull(objects)[index] = value;
    }

    Object read(int index) {
      if (states != null && states[index] != 0)
        return states[index] == 1 ? DataNull.INSTANCE : Missing.INSTANCE;
      return longs != null
          ? longs[index]
          : Objects.requireNonNull(Objects.requireNonNull(objects)[index]);
    }

    long readLong(int index) throws UnexpectedResultException {
      if (longs != null && (states == null || states[index] == 0)) return longs[index];
      throw new UnexpectedResultException(read(index));
    }
  }

  @ExportLibrary(DataLibrary.class)
  public static final class Row extends AbstractRow {
    private final Batch batch;
    private final int index;

    private Row(Batch batch, int index) {
      super(batch.schema);
      this.batch = batch;
      this.index = index;
    }

    @ExportMessage
    Schema getSchema() {
      return descriptor;
    }

    @ExportMessage
    Object read(int field) {
      return batch.columns[field].read(index);
    }

    @ExportMessage
    static class ReadLong {
      @Specialization(
          guards = {
            "row.descriptor == schema",
            "field == cachedField",
            "schema.requiredLong(cachedField)"
          },
          limit = "3")
      static long required(
          Row row,
          int field,
          @Cached("row.descriptor") Schema schema,
          @Cached("field") int cachedField) {
        return Objects.requireNonNull(row.batch.columns[cachedField].longs)[row.index];
      }

      @Specialization(replaces = "required")
      static long generic(Row row, int field) throws UnexpectedResultException {
        return row.batch.columns[field].readLong(row.index);
      }
    }

    @ExportMessage
    boolean isPresent(int field) {
      byte[] states = batch.columns[field].states;
      return states == null || states[index] != 2;
    }
  }
}
