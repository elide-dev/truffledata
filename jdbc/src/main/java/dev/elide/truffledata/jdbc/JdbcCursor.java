package dev.elide.truffledata.jdbc;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;
import dev.elide.truffledata.*;
import java.sql.*;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** Single-pass, thread-confined cursor. Exhaustion and failed ingestion close owned resources. */
@ExportLibrary(InteropLibrary.class)
public final class JdbcCursor implements AutoCloseable, TruffleObject {
  private final PreparedStatement statement;
  private final ResultSet result;
  private final Schema schema;
  private final Limits limits;
  private final JdbcConsumptionLimits consumption;
  private final PayloadBudget queryBudget;
  private final @Nullable JdbcCancellation cancellation;
  private long rowsRead;
  private final JdbcSource.Decoder decoder;
  private final @Nullable StaticLayout layout;
  private final Thread owner = Thread.currentThread();
  private boolean closed;
  private boolean exhausted;
  private @Nullable Batch pending;
  private int nextRow;
  private @Nullable AbstractRow pendingStatic;
  private boolean iteratorMode;
  private boolean batchMode;

  JdbcCursor(
      PreparedStatement statement,
      ResultSet result,
      Schema schema,
      Limits limits,
      JdbcSource.Decoder decoder,
      @Nullable StaticLayout layout) {
    this(statement, result, schema, limits, decoder, layout, JdbcConsumptionLimits.DEFAULT, null);
  }

  JdbcCursor(
      PreparedStatement statement,
      ResultSet result,
      Schema schema,
      Limits limits,
      JdbcSource.Decoder decoder,
      @Nullable StaticLayout layout,
      JdbcConsumptionLimits consumption,
      @Nullable JdbcCancellation cancellation) {
    this.cancellation = cancellation;
    this.consumption = consumption;
    this.queryBudget = consumption.budget();
    this.statement = statement;
    this.result = result;
    this.schema = schema;
    this.limits = limits;
    this.decoder = decoder;
    this.layout = layout;
  }

  public Schema schema() {
    return schema;
  }

  public boolean isClosed() {
    return closed;
  }

  @TruffleBoundary
  public @Nullable Batch nextBatch() throws SQLException {
    checkThread();
    if (iteratorMode) throw new IllegalStateException("Cannot mix batch and iterator consumption");
    batchMode = true;
    return fetch();
  }

  private @Nullable Batch fetch() throws SQLException {
    if (exhausted) return null;
    if (closed) throw new IllegalStateException("Cursor is closed");
    try {
      if (cancellation != null) cancellation.enter();
      int capacity =
          (int) Math.min(limits.maxRows(), limits.maxCells() / Math.max(1, schema.size()));
      capacity = (int) Math.min(capacity, Math.max(1, consumption.maxRows() - rowsRead));
      Batch batch =
          Batch.read(
              schema,
              capacity,
              limits,
              new Batch.Source<SQLException>() {
                public boolean next() throws SQLException {
                  return advance();
                }

                public @Nullable Object read(int column) throws SQLException {
                  return decoder.read(result, column + 1);
                }
              },
              queryBudget);
      if (cancellation != null) cancellation.check();
      if (batch.size() < capacity) {
        exhausted = true;
        close();
      }
      return batch.size() == 0 ? null : batch;
    } catch (SQLException | RuntimeException | Error e) {
      closeResources(e);
      throw e;
    } finally {
      if (cancellation != null) cancellation.leave();
    }
  }

  private boolean advance() throws SQLException {
    if (cancellation != null) cancellation.check();
    boolean available = result.next();
    if (cancellation != null) cancellation.check();
    if (!available) return false;
    if (rowsRead == consumption.maxRows())
      throw new ContractViolation("Cumulative query row budget exceeded");
    rowsRead++;
    return true;
  }

  private void checkThread() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("Cursor belongs to another thread");
  }

  @Override
  @TruffleBoundary
  public void close() throws SQLException {
    checkThread();
    Throwable failure = closeResources(null);
    if (failure instanceof SQLException sql) throw sql;
    if (failure instanceof RuntimeException runtime) throw runtime;
    if (failure instanceof Error error) throw error;
  }

  /** Preserve a primary failure and attach independent close failures directly to it. */
  private @Nullable Throwable closeResources(@Nullable Throwable failure) {
    if (closed) return failure;
    if (cancellation != null) cancellation.finish();
    closed = true;
    pending = null;
    pendingStatic = null;
    try {
      result.close();
    } catch (SQLException | RuntimeException | Error close) {
      if (failure == null) failure = close;
      else if (failure != close) failure.addSuppressed(close);
    }
    try {
      statement.close();
    } catch (SQLException | RuntimeException | Error close) {
      if (failure == null) failure = close;
      else if (failure != close) failure.addSuppressed(close);
    }
    return failure;
  }

  @ExportMessage
  boolean hasIterator() {
    return true;
  }

  @ExportMessage
  Object getIterator() {
    return this;
  }

  @ExportMessage
  boolean isIterator() {
    return true;
  }

  private boolean prepareNext() throws SQLException {
    checkThread();
    if (cancellation != null) {
      try {
        cancellation.check();
      } catch (SQLException failure) {
        closeResources(failure);
        throw failure;
      }
    }
    if (batchMode) throw new IllegalStateException("Cannot mix batch and iterator consumption");
    iteratorMode = true;
    if (layout != null) return prepareStatic();
    if (pending != null && nextRow < pending.size()) return true;
    if (exhausted) return false;
    pending = fetch();
    nextRow = 0;
    return pending != null;
  }

  /** Retained immutable row; static when executeStatic supplied a layout. */
  @TruffleBoundary
  public @Nullable AbstractRow nextRecord() throws SQLException {
    if (!prepareNext()) return null;
    if (layout == null) return Objects.requireNonNull(pending).row(nextRow++);
    AbstractRow row = Objects.requireNonNull(pendingStatic);
    pendingStatic = null;
    return row;
  }

  /** One-row lookahead: static consumers never allocate an intermediate columnar batch. */
  private boolean prepareStatic() throws SQLException {
    if (pendingStatic != null) return true;
    if (exhausted) return false;
    if (closed) throw new IllegalStateException("Cursor is closed");
    try {
      if (cancellation != null) cancellation.enter();
      if (!advance()) {
        exhausted = true;
        close();
        return false;
      }
      PayloadBudget budget = new PayloadBudget(limits, queryBudget);
      budget.cells(schema.size());
      Object[] values = new Object[schema.size()];
      for (int i = 0; i < values.length; i++) {
        Object value = Values.normalize(decoder.read(result, i + 1));
        budget.accept(value);
        values[i] = value;
      }
      pendingStatic = Objects.requireNonNull(layout).create(values);
      if (cancellation != null) cancellation.check();
      return true;
    } catch (SQLException | RuntimeException | Error failure) {
      closeResources(failure);
      throw failure;
    } finally {
      if (cancellation != null) cancellation.leave();
    }
  }

  @ExportMessage
  @TruffleBoundary
  boolean hasIteratorNextElement() {
    try {
      return prepareNext();
    } catch (SQLException e) {
      throw new IllegalStateException("JDBC read failed", e);
    }
  }

  @ExportMessage
  @TruffleBoundary
  Object getIteratorNextElement() throws StopIterationException {
    try {
      AbstractRow row = nextRecord();
      if (row == null) throw StopIterationException.create();
      return row;
    } catch (SQLException e) {
      throw new IllegalStateException("JDBC read failed", e);
    }
  }

  @ExportMessage
  boolean hasMembers() {
    return true;
  }

  @ExportMessage
  Object getMembers(boolean includeInternal) {
    return MEMBERS;
  }

  private static final DataArray MEMBERS = new DataArray(new Object[] {"close"});

  @ExportMessage
  boolean isMemberInvocable(String name) {
    return name.equals("close");
  }

  @ExportMessage
  @TruffleBoundary
  Object invokeMember(String name, Object[] arguments)
      throws UnknownIdentifierException, ArityException {
    if (!name.equals("close")) throw UnknownIdentifierException.create(name);
    if (arguments.length != 0) throw ArityException.create(0, 0, arguments.length);
    try {
      close();
    } catch (SQLException e) {
      throw new IllegalStateException("JDBC close failed", e);
    }
    return DataNull.INSTANCE;
  }
}
