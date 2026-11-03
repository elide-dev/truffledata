package dev.elide.truffledata.jdbc;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import java.sql.*;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** SQL is opaque. The connection is borrowed; the cursor owns its statement/result set. */
public final class JdbcSource {
  private JdbcSource() {}

  @FunctionalInterface
  public interface Decoder {
    /** Return a core interop value, or Java null for SQL NULL. Column index is JDBC 1-based. */
    @Nullable Object read(ResultSet result, int column) throws SQLException;
  }

  @FunctionalInterface
  public interface StatementConfigurer {
    /**
     * Configure driver-specific fetch behavior before execution; do not execute or close the
     * statement.
     */
    void configure(PreparedStatement statement) throws SQLException;
  }

  public static final Decoder DEFAULT_DECODER = (result, column) -> result.getObject(column);

  @TruffleBoundary
  public static JdbcCursor execute(
      Connection connection,
      String sql,
      List<? extends @Nullable Object> parameters,
      @Nullable Schema contract,
      Limits limits)
      throws SQLException {
    return execute(connection, sql, parameters, contract, limits, DEFAULT_DECODER);
  }

  @TruffleBoundary
  public static JdbcCursor execute(
      Connection connection,
      String sql,
      List<? extends @Nullable Object> parameters,
      @Nullable Schema contract,
      Limits limits,
      Decoder decoder)
      throws SQLException {
    return execute(connection, sql, parameters, contract, limits, decoder, statement -> {});
  }

  @TruffleBoundary
  public static JdbcCursor execute(
      Connection connection,
      String sql,
      List<? extends @Nullable Object> parameters,
      @Nullable Schema contract,
      Limits limits,
      Decoder decoder,
      StatementConfigurer configurer)
      throws SQLException {
    return executeInternal(
        connection, sql, parameters, contract, limits, decoder, configurer, null);
  }

  /**
   * Reuse one language-owned layout for a known query contract across executions. SQL remains
   * opaque.
   */
  @TruffleBoundary
  public static JdbcCursor executeStatic(
      Connection connection,
      String sql,
      List<? extends @Nullable Object> parameters,
      StaticLayout layout,
      Limits limits,
      Decoder decoder,
      StatementConfigurer configurer)
      throws SQLException {
    return executeInternal(
        connection, sql, parameters, layout.schema(), limits, decoder, configurer, layout);
  }

  public static JdbcCursor executeStatic(
      Connection connection,
      String sql,
      List<? extends @Nullable Object> parameters,
      StaticLayout layout,
      Limits limits)
      throws SQLException {
    return executeStatic(
        connection, sql, parameters, layout, limits, DEFAULT_DECODER, statement -> {});
  }

  /**
   * Execute with an explicit cooperating driver. An explicit contract overrides metadata inference;
   * a supplied layout must belong to that exact schema identity. The connection remains borrowed.
   */
  public static JdbcCursor execute(
      Connection connection,
      String sql,
      List<? extends @Nullable Object> parameters,
      @Nullable Schema contract,
      @Nullable StaticLayout layout,
      Limits limits,
      JdbcAdapter adapter,
      JdbcQueryOptions options)
      throws SQLException {
    return execute(
        connection,
        sql,
        parameters,
        contract,
        layout,
        limits,
        adapter,
        options,
        JdbcConsumptionLimits.DEFAULT);
  }

  /** Execute with separate per-batch and cumulative query ingestion limits. */
  public static JdbcCursor execute(
      Connection connection,
      String sql,
      List<? extends @Nullable Object> parameters,
      @Nullable Schema contract,
      @Nullable StaticLayout layout,
      Limits limits,
      JdbcAdapter adapter,
      JdbcQueryOptions options,
      JdbcConsumptionLimits consumption)
      throws SQLException {
    return execute(
        connection, sql, parameters, contract, layout, limits, adapter, options, consumption, null);
  }

  /** Optional single-execution control supports cancellation from another thread. */
  @TruffleBoundary
  public static JdbcCursor execute(
      Connection connection,
      String sql,
      List<? extends @Nullable Object> parameters,
      @Nullable Schema contract,
      @Nullable StaticLayout layout,
      Limits limits,
      JdbcAdapter adapter,
      JdbcQueryOptions options,
      JdbcConsumptionLimits consumption,
      @Nullable JdbcCancellation cancellation)
      throws SQLException {
    if (layout != null && contract != layout.schema())
      throw new IllegalArgumentException("Static layout requires its exact schema as contract");
    if (cancellation != null) cancellation.check();
    PreparedStatement statement = adapter.prepare(connection, sql, options);
    boolean attached = false;
    try {
      if (cancellation != null) {
        cancellation.attach(statement, adapter);
        attached = true;
        cancellation.enter();
      }
      for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
      ResultSet result = adapter.execute(statement);
      try {
        ResultSetMetaData metadata = result.getMetaData();
        int count = metadata.getColumnCount();
        limits.check(1, count);
        Schema schema = contract == null ? adapter.describe(metadata, limits) : contract;
        if (schema.size() != count)
          throw new ContractViolation("JDBC result width differs from contract");
        JdbcSource.Decoder decoder = adapter.decoder(metadata, schema, limits);
        if (cancellation != null) cancellation.check();
        return new JdbcCursor(
            statement, result, schema, limits, decoder, layout, consumption, cancellation);
      } catch (SQLException | RuntimeException | Error failure) {
        if (attached && cancellation != null) cancellation.finish();
        try {
          result.close();
        } catch (SQLException | RuntimeException | Error close) {
          if (failure != close) failure.addSuppressed(close);
        }
        throw failure;
      }
    } catch (SQLException | RuntimeException | Error failure) {
      if (attached && cancellation != null) cancellation.finish();
      try {
        statement.close();
      } catch (SQLException | RuntimeException | Error close) {
        if (failure != close) failure.addSuppressed(close);
      }
      throw failure;
    } finally {
      if (attached && cancellation != null) cancellation.leave();
    }
  }

  private static JdbcCursor executeInternal(
      Connection connection,
      String sql,
      List<? extends @Nullable Object> parameters,
      @Nullable Schema contract,
      Limits limits,
      Decoder decoder,
      StatementConfigurer configurer,
      @Nullable StaticLayout layout)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    try {
      configurer.configure(statement);
      for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
      ResultSet result = statement.executeQuery();
      try {
        ResultSetMetaData metadata = result.getMetaData();
        int count = metadata.getColumnCount();
        limits.check(1, count);
        Schema schema = contract == null ? describe(metadata) : contract;
        if (schema.size() != count)
          throw new ContractViolation("JDBC result width differs from contract");
        return new JdbcCursor(statement, result, schema, limits, decoder, layout);
      } catch (SQLException | RuntimeException | Error e) {
        try {
          result.close();
        } catch (SQLException | RuntimeException | Error close) {
          if (e != close) e.addSuppressed(close);
        }
        throw e;
      }
    } catch (SQLException | RuntimeException | Error e) {
      try {
        statement.close();
      } catch (SQLException | RuntimeException | Error close) {
        if (e != close) e.addSuppressed(close);
      }
      throw e;
    }
  }

  /** Metadata is a hint: even declared non-null fields are validated on ingestion. */
  @TruffleBoundary
  private static Schema describe(ResultSetMetaData metadata) throws SQLException {
    List<Field> fields = new ArrayList<>();
    for (int i = 1; i <= metadata.getColumnCount(); i++) {
      Field.Kind kind =
          switch (metadata.getColumnType(i)) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> Field.Kind.INT64;
            case Types.REAL, Types.FLOAT, Types.DOUBLE -> Field.Kind.FLOAT64;
            case Types.BOOLEAN, Types.BIT -> Field.Kind.BOOLEAN;
            case Types.CHAR,
                Types.VARCHAR,
                Types.LONGVARCHAR,
                Types.NCHAR,
                Types.NVARCHAR,
                Types.LONGNVARCHAR ->
                Field.Kind.STRING;
            default -> Field.Kind.ANY;
          };
      fields.add(
          new Field(
              metadata.getColumnLabel(i),
              kind,
              metadata.isNullable(i) != ResultSetMetaData.columnNoNulls,
              false));
    }
    return new Schema(fields);
  }
}
