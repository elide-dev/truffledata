package dev.elide.truffledata.csv;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.AbstractRow;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.DataNull;
import dev.elide.truffledata.Missing;
import dev.elide.truffledata.Schema;
import java.util.Objects;

/**
 * A view of one batch row. Rows are immutable and unconfined; members are named by the schema (the
 * header), elements by ordinal. Absent fields of flexible short rows read as {@link Missing}. Rows of
 * a mapped batch throw {@code IllegalStateException} on every read once its mapping is closed.
 */
@ExportLibrary(DataLibrary.class)
public final class CsvRow extends AbstractRow {
  final CsvBatch batch;
  final int index;

  CsvRow(CsvBatch batch, int index) {
    super(batch.schema);
    this.batch = batch;
    this.index = index;
  }

  public CsvBatch batch() {
    return batch;
  }

  public int index() {
    return index;
  }

  /** Stream data-record index of this row. */
  public long record() {
    return batch.firstRecord + index;
  }

  public boolean isNull(int column) {
    batch.checkLive();
    return batch.columns[column].state(index) == Column.NULL;
  }

  /** Throws {@link ContractViolation} on null, missing or non-STRING. */
  public String getString(int column) {
    batch.checkLive();
    Column c = batch.columns[column];
    long[] spans = c.spans;
    if (spans == null || c.state(index) != Column.PRESENT) throw expected(descriptor, column, "STRING");
    return CsvBatch.string(batch, spans[index]);
  }

  public long getLong(int column) {
    batch.checkLive();
    Column c = batch.columns[column];
    long[] longs = c.longs;
    if (longs == null || c.state(index) != Column.PRESENT) throw expected(descriptor, column, "INT64");
    return longs[index];
  }

  public double getDouble(int column) {
    batch.checkLive();
    Column c = batch.columns[column];
    double[] doubles = c.doubles;
    if (doubles == null || c.state(index) != Column.PRESENT) throw expected(descriptor, column, "FLOAT64");
    return doubles[index];
  }

  public boolean getBoolean(int column) {
    batch.checkLive();
    Column c = batch.columns[column];
    boolean[] booleans = c.booleans;
    if (booleans == null || c.state(index) != Column.PRESENT) throw expected(descriptor, column, "BOOLEAN");
    return booleans[index];
  }

  @TruffleBoundary
  private static ContractViolation expected(Schema schema, int column, String kind) {
    return new ContractViolation(
        "Field '" + schema.field(column).name() + "': expected a present, non-null " + kind);
  }

  static Object value(CsvBatch batch, Column column, int row) {
    byte state = column.state(row);
    if (state != Column.PRESENT) return state == Column.NULL ? DataNull.INSTANCE : Missing.INSTANCE;
    long[] spans = column.spans;
    if (spans != null) return CsvBatch.string(batch, spans[row]);
    long[] longs = column.longs;
    if (longs != null) return longs[row];
    double[] doubles = column.doubles;
    if (doubles != null) return doubles[row];
    return Objects.requireNonNull(column.booleans)[row];
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(int field) {
    batch.checkLive();
    return value(batch, batch.columns[field], index);
  }

  @ExportMessage
  boolean isPresent(int field) {
    batch.checkLive();
    return batch.columns[field].state(index) != Column.MISSING;
  }

  @ExportMessage
  static class ReadLong {
    @Specialization(
        guards = {
          "row.schema() == schema",
          "field == cachedField",
          "schema.requiredLong(cachedField)"
        },
        limit = "3")
    static long required(
        CsvRow row,
        int field,
        @Cached("row.schema()") Schema schema,
        @Cached("field") int cachedField) {
      row.batch.checkLive();
      return Objects.requireNonNull(row.batch.columns[cachedField].longs)[row.index];
    }

    @Specialization(replaces = "required")
    static long generic(CsvRow row, int field) throws UnexpectedResultException {
      row.batch.checkLive();
      Column column = row.batch.columns[field];
      long[] longs = column.longs;
      if (longs != null && column.state(row.index) == Column.PRESENT) return longs[row.index];
      throw new UnexpectedResultException(value(row.batch, column, row.index));
    }
  }

  @ExportMessage
  static class ReadDouble {
    @Specialization(
        guards = {
          "row.schema() == schema",
          "field == cachedField",
          "schema.requiredDouble(cachedField)"
        },
        limit = "3")
    static double required(
        CsvRow row,
        int field,
        @Cached("row.schema()") Schema schema,
        @Cached("field") int cachedField) {
      row.batch.checkLive();
      return Objects.requireNonNull(row.batch.columns[cachedField].doubles)[row.index];
    }

    @Specialization(replaces = "required")
    static double generic(CsvRow row, int field) throws UnexpectedResultException {
      row.batch.checkLive();
      Column column = row.batch.columns[field];
      double[] doubles = column.doubles;
      if (doubles != null && column.state(row.index) == Column.PRESENT) return doubles[row.index];
      throw new UnexpectedResultException(value(row.batch, column, row.index));
    }
  }

  @ExportMessage
  static class ReadBoolean {
    @Specialization(
        guards = {
          "row.schema() == schema",
          "field == cachedField",
          "schema.requiredBoolean(cachedField)"
        },
        limit = "3")
    static boolean required(
        CsvRow row,
        int field,
        @Cached("row.schema()") Schema schema,
        @Cached("field") int cachedField) {
      row.batch.checkLive();
      return Objects.requireNonNull(row.batch.columns[cachedField].booleans)[row.index];
    }

    @Specialization(replaces = "required")
    static boolean generic(CsvRow row, int field) throws UnexpectedResultException {
      row.batch.checkLive();
      Column column = row.batch.columns[field];
      boolean[] booleans = column.booleans;
      if (booleans != null && column.state(row.index) == Column.PRESENT)
        return booleans[row.index];
      throw new UnexpectedResultException(value(row.batch, column, row.index));
    }
  }
}
