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
      } catch (SQLException | RuntimeException e) {
        try {
          result.close();
        } catch (SQLException close) {
          e.addSuppressed(close);
        }
        throw e;
      }
    } catch (SQLException | RuntimeException e) {
      try {
        statement.close();
      } catch (SQLException close) {
        e.addSuppressed(close);
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
