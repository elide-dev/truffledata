package dev.elide.truffledata.jdbc;

import dev.elide.truffledata.Limits;
import dev.elide.truffledata.Schema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * Version 1 driver cooperation SPI. Implementations are immutable and shareable. Statements and
 * decoders belong to one execution. Implementations must neither commit nor change autocommit,
 * isolation, read-only state or ownership of the supplied connection.
 */
public interface JdbcAdapter {
  int API_VERSION = 1;

  /**
   * Stable integration identifier, suitable for diagnostics; must not contain connection details.
   */
  String id();

  /**
   * Validate driver/streaming prerequisites before executing SQL. Return a configured forward-only,
   * read-only statement. Close it if configuration fails. Fail if requested streaming cannot be
   * provided; never quietly downgrade to eager buffering. SQL is not parsed by this layer.
   */
  PreparedStatement prepare(Connection connection, String sql, JdbcQueryOptions options)
      throws SQLException;

  /** Conservative driver metadata interpretation; returned facts are validated during ingestion. */
  Schema describe(ResultSetMetaData metadata, Limits limits) throws SQLException;

  /** Compile execution-local decoding once, after the result's schema has been selected. */
  JdbcSource.Decoder decoder(ResultSetMetaData metadata, Schema schema, Limits limits)
      throws SQLException;
}
