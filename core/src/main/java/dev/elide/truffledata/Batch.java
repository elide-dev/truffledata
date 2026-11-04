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
    PayloadBudget budget = new PayloadBudget(limits);
    budget.cells((long) rows.size() * schema.size());
    for (int r = 0; r < rows.size(); r++) {
      Object[] row = rows.get(r);
      if (row.length != schema.size())
        throw new ContractViolation("Row width differs from contract");
      for (int c = 0; c < columns.length; c++) {
        Object value = Values.normalize(row[c]);
        budget.accept(value);
        Values.validate(schema.field(c), value);
        columns[c].set(r, value);
      }
    }
    return new Batch(schema, columns, rows.size());
  }

  /** Synchronous source; column indexes are zero-based. The caller owns source cleanup. */
  public interface Source<E extends Exception> {
    boolean next() throws E;

    @Nullable Object read(int column) throws E;
  }

  /**
   * Fill immutable columns directly, validating each cell once. Capacity is retained without
   * trimming/copying; callers should choose a bounded capacity appropriate to their fetch size. No
   * source reference is retained. A short batch means the source is exhausted.
   */
  public static <E extends Exception> Batch read(
      Schema schema, int capacity, Limits limits, Source<E> source) throws E {
    return read(schema, capacity, limits, source, null);
  }

  /** As above, also charging a thread-confined cumulative parent during the same payload walk. */
  @TruffleBoundary
  public static <E extends Exception> Batch read(
      Schema schema, int capacity, Limits limits, Source<E> source, @Nullable PayloadBudget parent)
      throws E {
    if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
    limits.check(capacity, schema.size());
    Column[] columns = new Column[schema.size()];
    for (int i = 0; i < columns.length; i++) columns[i] = new Column(schema.field(i), capacity);
    PayloadBudget budget = new PayloadBudget(limits, parent);
    int size = 0;
    while (size < capacity && source.next()) {
      budget.cells(schema.size());
      for (int c = 0; c < columns.length; c++) {
        Object value = Values.normalize(source.read(c));
        budget.accept(value);
        columns[c].set(size, Values.validate(schema.field(c), value));
      }
      size++;
    }
    return new Batch(schema, columns, size);
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
  public static final class Row extends AbstractRow implements SnapshotStorage {
    private final Batch batch;
    private final int index;

    private Row(Batch batch, int index) {
      super(batch.schema);
      this.batch = batch;
      this.index = index;
    }

    void acceptPayload(PayloadBudget budget, int depth) {
      for (int i = 0; i < batch.columns.length; i++) {
        Field.Kind kind = descriptor.field(i).kind();
        if (kind != Field.Kind.INT64 && kind != Field.Kind.FLOAT64 && kind != Field.Kind.BOOLEAN)
          budget.accept(batch.columns[i].read(index), depth);
      }
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
