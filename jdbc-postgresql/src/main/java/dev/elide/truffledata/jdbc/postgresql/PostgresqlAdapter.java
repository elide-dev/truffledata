package dev.elide.truffledata.jdbc.postgresql;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import dev.elide.truffledata.jdbc.*;
import java.sql.*;
import org.postgresql.core.BaseConnection;
import org.postgresql.jdbc.PreferQueryMode;
import org.postgresql.util.PGobject;

/** Driver cooperation over borrowed connections; transaction ownership remains with the caller. */
public final class PostgresqlAdapter implements JdbcAdapter {
  public static final PostgresqlAdapter INSTANCE = new PostgresqlAdapter();

  private PostgresqlAdapter() {}

  @Override
  public String id() {
    return "postgresql-v1";
  }

  @Override
  @TruffleBoundary
  public PreparedStatement prepare(Connection connection, String sql, JdbcQueryOptions options)
      throws SQLException {
    if (!connection.isWrapperFor(BaseConnection.class))
      throw new SQLException("PostgreSQL adapter requires pgJDBC");
    BaseConnection driver = connection.unwrap(BaseConnection.class);
    if (options.requireStreaming()) {
      if (connection.getAutoCommit())
        throw new SQLException("PostgreSQL streaming requires an existing transaction");
      if (driver.getQueryExecutor().getPreferQueryMode() == PreferQueryMode.SIMPLE)
        throw new SQLException("PostgreSQL streaming requires extended query mode");
      // Delegate statement classification to the driver's own JDBC parser. No SQL parsing or
      // query planning is implemented here. Never execute this temporary query.
      var query = driver.createQuery(sql, true, true).query;
      try {
        var parts = query.getSubqueries();
        if (query.isEmpty() || (parts != null && parts.length != 1))
          throw new SQLException("PostgreSQL streaming requires a single statement");
      } finally {
        query.close();
      }
    }
    PreparedStatement statement =
        connection.prepareStatement(
            sql,
            ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY,
            ResultSet.CLOSE_CURSORS_AT_COMMIT);
    try {
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
    return JdbcContracts.describe(metadata, JdbcContracts.Dialect.POSTGRESQL, limits);
  }

  @Override
  public JdbcSource.Decoder decoder(ResultSetMetaData metadata, Schema schema, Limits limits)
      throws SQLException {
    limits.check(1, metadata.getColumnCount());
    if (metadata.getColumnCount() != schema.size())
      throw new ContractViolation("Projection width differs from contract");
    return JdbcValues.decoder(
        schema,
        limits,
        value -> value instanceof PGobject object ? Values.normalize(object.getValue()) : value);
  }
}
