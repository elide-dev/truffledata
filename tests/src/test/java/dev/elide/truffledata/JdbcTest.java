package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.interop.InteropLibrary;
import dev.elide.truffledata.jdbc.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class JdbcTest {
  @Test
  void sqliteAdapterPreservesBorrowedTransactionsAndRejectsCoercion() throws Exception {
    var adapter = dev.elide.truffledata.jdbc.sqlite.SqliteAdapter.INSTANCE;
    try (Connection wrong = DriverManager.getConnection("jdbc:h2:mem:")) {
      assertThrows(
          SQLException.class, () -> adapter.prepare(wrong, "select 1", JdbcQueryOptions.DEFAULT));
    }
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("create table sample(value)");
        statement.execute("insert into sample values (1), ('invalid'), (NULL)");
      }
      try (var cursor =
          JdbcSource.execute(
              connection,
              "select value from sample",
              List.of(),
              null,
              null,
              Limits.DEFAULT,
              adapter,
              JdbcQueryOptions.DEFAULT)) {
        assertEquals(Field.Kind.ANY, cursor.schema().field(0).kind());
        assertTrue(cursor.schema().field(0).nullable());
        Batch batch = java.util.Objects.requireNonNull(cursor.nextBatch());
        assertEquals(3, batch.size());
        assertEquals("invalid", DataLibrary.factory().getUncached().read(batch.row(1), 0));
      }
      try (var cursor =
          JdbcSource.execute(
              connection,
              "select value from sample",
              List.of(),
              Schema.of(Field.requiredLong("value")),
              null,
              Limits.DEFAULT,
              adapter,
              JdbcQueryOptions.DEFAULT)) {
        assertThrows(ContractViolation.class, cursor::nextBatch);
        assertTrue(cursor.isClosed());
      }
      assertFalse(connection.getAutoCommit());
      assertFalse(connection.isClosed());
      connection.rollback();
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery("select count(*) from sqlite_master where name='sample'")) {
        assertTrue(result.next());
        assertEquals(0, result.getInt(1));
      }
    }
  }

  @Test
  void bindsParametersRetainsRowsAndBorrowsConnection() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:")) {
      var limits = new Limits(1, 4, 4, 1024);
      Batch.Row retained;
      try (var cursor =
          JdbcSource.execute(
              connection,
              "select cast(x as bigint) as a from system_range(1, 3) where x > ?",
              List.of(1),
              Schema.of(Field.requiredLong("a")),
              limits)) {
        retained = Objects.requireNonNull(cursor.nextBatch()).row(0);
        assertEquals(
            3L,
            DataLibrary.factory()
                .getUncached()
                .readLong(Objects.requireNonNull(cursor.nextBatch()).row(0), 0));
        assertNull(cursor.nextBatch());
        assertTrue(cursor.isClosed());
      }
      assertEquals(2L, DataLibrary.factory().getUncached().readLong(retained, 0));
      assertFalse(connection.isClosed());
    }
  }

  @Test
  void nullContractFailureClosesCursorWithoutCoercion() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:")) {
      var cursor =
          JdbcSource.execute(
              connection,
              "select cast(null as bigint) as a",
              List.of(),
              Schema.of(Field.requiredLong("a")),
              Limits.DEFAULT);
      assertThrows(ContractViolation.class, cursor::nextBatch);
      assertTrue(cursor.isClosed());
      assertFalse(connection.isClosed());
      assertThrows(IllegalStateException.class, cursor::nextBatch);
    }
  }

  @Test
  void metadataAndDecoderCooperation() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:")) {
      try (var cursor =
          JdbcSource.execute(
              connection, "select cast(null as bigint) as a", List.of(), null, Limits.DEFAULT)) {
        assertSame(
            DataNull.INSTANCE,
            DataLibrary.factory()
                .getUncached()
                .read(Objects.requireNonNull(cursor.nextBatch()).row(0), 0));
      }
      try (var cursor =
          JdbcSource.execute(
              connection,
              "select '42' as a",
              List.of(),
              Schema.of(Field.requiredLong("a")),
              Limits.DEFAULT,
              (rs, column) -> Long.parseLong(rs.getString(column)))) {
        assertEquals(
            42L,
            DataLibrary.factory()
                .getUncached()
                .readLong(Objects.requireNonNull(cursor.nextBatch()).row(0), 0));
      }
    }
  }

  @Test
  void iteratorHasNextIsIdempotentAndClosesAtEnd() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:");
        var cursor =
            JdbcSource.execute(
                connection,
                "select cast(x as bigint) as a from system_range(1, 2)",
                List.of(),
                null,
                new Limits(1, 2, 2, 100))) {
      InteropLibrary io = InteropLibrary.getUncached();
      assertTrue(io.hasIteratorNextElement(cursor));
      assertTrue(io.hasIteratorNextElement(cursor));
      assertEquals(1L, io.readArrayElement(io.getIteratorNextElement(cursor), 0));
      assertTrue(io.hasIteratorNextElement(cursor));
      assertEquals(2L, io.readArrayElement(io.getIteratorNextElement(cursor), 0));
      assertFalse(io.hasIteratorNextElement(cursor));
      assertTrue(cursor.isClosed());
      assertThrows(IllegalStateException.class, cursor::nextBatch);
    }
  }

  @Test
  void explicitCloseAndBudgetFailure() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:")) {
      var cursor =
          JdbcSource.execute(
              connection, "select 'oversized' as s", List.of(), null, new Limits(2, 2, 4, 2));
      assertThrows(ContractViolation.class, cursor::nextBatch);
      assertTrue(cursor.isClosed());
      cursor.close();
      var other = JdbcSource.execute(connection, "select 1", List.of(), null, Limits.DEFAULT);
      other.close();
      assertThrows(IllegalStateException.class, other::nextBatch);
      assertThrows(
          ContractViolation.class,
          () ->
              JdbcSource.execute(
                  connection,
                  "select 1, 2",
                  List.of(),
                  Schema.of(Field.requiredLong("a")),
                  Limits.DEFAULT));
    }
  }

  @Test
  void staticRowsAreDecodedLazilyWithoutBatchCopies() throws Exception {
    try (var context = org.graalvm.polyglot.Context.newBuilder("td-test").build();
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:")) {
      context.initialize("td-test");
      context.enter();
      try {
        Schema schema = Schema.of(Field.requiredLong("a"));
        StaticLayout layout =
            new StaticLayout(TestLanguage.current().language(), schema, Limits.DEFAULT);
        int[] decoded = {0};
        try (var cursor =
            JdbcSource.executeStatic(
                connection,
                "select cast(x as bigint) from system_range(1, 3)",
                List.of(),
                layout,
                Limits.DEFAULT,
                (result, column) -> {
                  decoded[0]++;
                  return result.getObject(column);
                },
                statement -> {})) {
          InteropLibrary io = InteropLibrary.getUncached();
          assertTrue(io.hasIteratorNextElement(cursor));
          assertTrue(io.hasIteratorNextElement(cursor));
          assertEquals(1, decoded[0], "lookahead must decode exactly one row");
          Object retained = io.getIteratorNextElement(cursor);
          assertInstanceOf(StaticLayout.Record.class, retained);
          assertEquals(1, decoded[0], "consumption must reuse the lookahead record");
          cursor.close();
          assertEquals(1L, io.readArrayElement(retained, 0));
          assertThrows(IllegalStateException.class, cursor::nextRecord);
        }
        try (var cursor =
            JdbcSource.executeStatic(
                connection,
                "select case when x = 1 then cast(1 as bigint) else null end from system_range(1,"
                    + " 2)",
                List.of(),
                layout,
                Limits.DEFAULT)) {
          assertNotNull(
              cursor.nextRecord(), "an invalid subsequent row must not force eager ingestion");
          assertThrows(ContractViolation.class, cursor::nextRecord);
          assertTrue(cursor.isClosed());
        }
      } finally {
        context.leave();
      }
    }
  }

  @Test
  void cooperatingAdapterCompilesDecoderOnceAndClosesAfterErrors() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:")) {
      List<PreparedStatement> statements = new ArrayList<>();
      List<ResultSet> results = new ArrayList<>();
      int[] decoders = {0};
      JdbcAdapter adapter =
          new JdbcAdapter() {
            public String id() {
              return "test-h2";
            }

            public PreparedStatement prepare(Connection c, String sql, JdbcQueryOptions options)
                throws SQLException {
              assertFalse(options.requireStreaming());
              PreparedStatement statement =
                  c.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
              statement.setFetchSize(options.fetchSize());
              statement.setQueryTimeout(options.timeoutSeconds());
              statements.add(statement);
              return statement;
            }

            public Schema describe(ResultSetMetaData metadata, Limits limits) throws SQLException {
              return JdbcContracts.describe(metadata, JdbcContracts.Dialect.POSTGRESQL, limits);
            }

            public JdbcSource.Decoder decoder(
                ResultSetMetaData metadata, Schema schema, Limits limits) {
              decoders[0]++;
              return (result, column) -> {
                results.add(result);
                throw new AssertionError("decoder assertion");
              };
            }
          };
      var cursor =
          JdbcSource.execute(
              connection,
              "select cast(? as bigint)",
              List.of(7),
              null,
              null,
              Limits.DEFAULT,
              adapter,
              new JdbcQueryOptions(16, 5, false));
      assertEquals(1, decoders[0]);
      assertThrows(AssertionError.class, cursor::nextBatch);
      assertTrue(cursor.isClosed());
      assertTrue(statements.getFirst().isClosed());
      assertTrue(results.getFirst().isClosed());
      assertFalse(connection.isClosed());
      assertTrue(connection.getAutoCommit(), "borrowed transaction policy must be unchanged");
    }
  }
}
