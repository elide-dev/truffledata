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
  private final JdbcSource.Decoder decoder;
  private final @Nullable StaticLayout layout;
  private final Thread owner = Thread.currentThread();
  private boolean closed;
  private boolean exhausted;
  private @Nullable Batch pending;
  private int nextRow;
  private boolean iteratorMode;
  private boolean batchMode;

  JdbcCursor(
      PreparedStatement statement,
      ResultSet result,
      Schema schema,
      Limits limits,
      JdbcSource.Decoder decoder,
      @Nullable StaticLayout layout) {
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
      int capacity =
          (int) Math.min(limits.maxRows(), limits.maxCells() / Math.max(1, schema.size()));
      List<Object[]> rows = new ArrayList<>(capacity);
      PayloadBudget budget = new PayloadBudget(limits);
      while (rows.size() < capacity) {
        if (!result.next()) {
          exhausted = true;
          close();
          break;
        }
        budget.cells(schema.size());
        Object[] row = new Object[schema.size()];
        for (int c = 0; c < row.length; c++) {
          Object value = Values.normalize(decoder.read(result, c + 1));
          budget.accept(value);
          row[c] = Values.validate(schema.field(c), value);
        }
        rows.add(row);
      }
      return rows.isEmpty() ? null : Batch.copyOf(schema, rows, limits);
    } catch (SQLException | RuntimeException e) {
      try {
        close();
      } catch (SQLException close) {
        e.addSuppressed(close);
      }
      throw e;
    }
  }

  private void checkThread() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("Cursor belongs to another thread");
  }

  @Override
  @TruffleBoundary
  public void close() throws SQLException {
    checkThread();
    if (closed) return;
    closed = true;
    pending = null;
    SQLException failure = null;
    try {
      result.close();
    } catch (SQLException e) {
      failure = e;
    }
    try {
      statement.close();
    } catch (SQLException e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    }
    if (failure != null) throw failure;
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
    if (batchMode) throw new IllegalStateException("Cannot mix batch and iterator consumption");
    iteratorMode = true;
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
    Batch.Row row = Objects.requireNonNull(pending).row(nextRow++);
    if (layout == null) return row;
    Object[] values = new Object[schema.size()];
    DataLibrary data = DataLibrary.factory().getUncached();
    for (int i = 0; i < values.length; i++) values[i] = data.read(row, i);
    try {
      return layout.create(values);
    } catch (RuntimeException failure) {
      try {
        close();
      } catch (SQLException close) {
        failure.addSuppressed(close);
      }
      throw failure;
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
