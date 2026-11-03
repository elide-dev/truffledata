package dev.elide.truffledata.jdbc.mysql;

import com.mysql.cj.conf.PropertyKey;
import com.mysql.cj.jdbc.JdbcConnection;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import dev.elide.truffledata.jdbc.*;
import java.sql.*;

/** Driver cooperation over borrowed connections; transaction ownership remains with the caller. */
public final class MysqlAdapter implements JdbcAdapter {
  public static final MysqlAdapter INSTANCE = new MysqlAdapter();

  private MysqlAdapter() {}

  @Override
  public String id() {
    return "mysql-v1";
  }

  @Override
  @TruffleBoundary
  public PreparedStatement prepare(Connection connection, String sql, JdbcQueryOptions options)
      throws SQLException {
    if (!connection.isWrapperFor(JdbcConnection.class))
      throw new SQLException("MySQL adapter requires Connector/J");
    JdbcConnection driver = connection.unwrap(JdbcConnection.class);
    if (options.requireStreaming()
        && driver.getPropertySet().getBooleanProperty(PropertyKey.allowMultiQueries).getValue())
      throw new SQLException("MySQL streaming requires allowMultiQueries=false");
    PreparedStatement statement =
        connection.prepareStatement(
            sql,
            ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY,
            ResultSet.CLOSE_CURSORS_AT_COMMIT);
    try {
      statement.setFetchSize(options.requireStreaming() ? Integer.MIN_VALUE : options.fetchSize());
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
    return JdbcContracts.describe(metadata, JdbcContracts.Dialect.MYSQL, limits);
  }

  @Override
  public JdbcSource.Decoder decoder(ResultSetMetaData metadata, Schema schema, Limits limits)
      throws SQLException {
    limits.check(1, metadata.getColumnCount());
    if (metadata.getColumnCount() != schema.size())
      throw new ContractViolation("Projection width differs from contract");
    boolean[] integerBooleans = new boolean[schema.size()];
    for (int i = 0; i < integerBooleans.length; i++) {
      int type = metadata.getColumnType(i + 1);
      integerBooleans[i] =
          schema.field(i).kind() == Field.Kind.BOOLEAN
              && (type == Types.BIT
                  || type == Types.BOOLEAN
                  || type == Types.TINYINT
                  || type == Types.SMALLINT
                  || type == Types.INTEGER
                  || type == Types.BIGINT);
    }
    JdbcSource.Decoder generic = JdbcValues.decoder(schema, limits);
    return (result, column) -> {
      if (!integerBooleans[column - 1]) return generic.read(result, column);
      // Connector/J getObject may already turn TINYINT(1)=2 into true. Read the integral
      // value first so an explicit boolean contract still enforces the domain 0..1.
      long number = result.getLong(column);
      if (result.wasNull()) return null;
      if (number != 0 && number != 1) throw new ContractViolation("MySQL BOOLEAN must be 0 or 1");
      return number == 1;
    };
  }
}
