package dev.elide.truffledata.jdbc.sqlite;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import dev.elide.truffledata.jdbc.*;
import java.sql.*;
import org.sqlite.SQLiteConnection;

/** Xerial integration over a borrowed connection; SQLite affinity never proves field types. */
public final class SqliteAdapter implements JdbcAdapter {
  public static final SqliteAdapter INSTANCE = new SqliteAdapter();

  private SqliteAdapter() {}

  @Override
  public String id() {
    return "sqlite-xerial-v1";
  }

  @Override
  @TruffleBoundary
  public PreparedStatement prepare(Connection connection, String sql, JdbcQueryOptions options)
      throws SQLException {
    if (!connection.isWrapperFor(SQLiteConnection.class))
      throw new SQLException("SQLite adapter requires an Xerial SQLite connection");
    PreparedStatement statement =
        connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    try {
      // Xerial steps the native SQLite cursor. Fetch size is a hint, not a memory guarantee.
      statement.setFetchSize(options.fetchSize());
      statement.setQueryTimeout(options.timeoutSeconds());
      return statement;
    } catch (SQLException | RuntimeException | Error failure) {
      try {
        statement.close();
      } catch (SQLException | RuntimeException | Error close) {
        if (failure != close) failure.addSuppressed(close);
      }
      throw failure;
    }
  }

  @Override
  public Schema describe(ResultSetMetaData metadata, Limits limits) throws SQLException {
    return JdbcContracts.describe(metadata, JdbcContracts.Dialect.SQLITE, limits);
  }

  @Override
  public JdbcSource.Decoder decoder(ResultSetMetaData metadata, Schema schema, Limits limits)
      throws SQLException {
    limits.check(1, metadata.getColumnCount());
    if (metadata.getColumnCount() != schema.size())
      throw new ContractViolation("SQLite projection width differs from contract");
    // Storage class can vary for every row, even when a declared type is available. Do not use
    // coercing primitive JDBC getters: they can silently truncate or turn invalid text into zero.
    return (result, column) ->
        SqliteSource.decode(result.getObject(column), schema.field(column - 1), limits);
  }
}
