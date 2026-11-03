package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.jdbc.*;
import dev.elide.truffledata.jdbc.mysql.MysqlAdapter;
import dev.elide.truffledata.jdbc.postgresql.PostgresqlAdapter;
import java.sql.*;
import java.util.*;
import org.graalvm.polyglot.*;
import org.junit.jupiter.api.Test;

/** Real services are mandatory for this separately scheduled acceptance target. */
class RealDriversTest {
  private static String url(String name) {
    return Objects.requireNonNull(System.getenv(name), "Missing real-driver test URL: " + name);
  }

  @Test
  void mysql() throws Exception {
    exercise(url("TRUFFLEDATA_MYSQL_URL"), true, MysqlAdapter.INSTANCE);
  }

  @Test
  void postgresql() throws Exception {
    exercise(url("TRUFFLEDATA_POSTGRESQL_URL"), false, PostgresqlAdapter.INSTANCE);
  }

  @Test
  void mysqlValueAndFailureMatrix() throws Exception {
    edgeValues(url("TRUFFLEDATA_MYSQL_URL"), true, MysqlAdapter.INSTANCE);
  }

  @Test
  void postgresqlValueAndFailureMatrix() throws Exception {
    edgeValues(url("TRUFFLEDATA_POSTGRESQL_URL"), false, PostgresqlAdapter.INSTANCE);
  }

  @Test
  void mysqlQueryTimeoutPreservesBorrowedConnection() throws Exception {
    timeout(url("TRUFFLEDATA_MYSQL_URL"), "select sleep(5)", MysqlAdapter.INSTANCE);
  }

  @Test
  void postgresqlQueryTimeoutPreservesBorrowedConnection() throws Exception {
    timeout(url("TRUFFLEDATA_POSTGRESQL_URL"), "select pg_sleep(5)", PostgresqlAdapter.INSTANCE);
  }

  private static void timeout(String url, String sql, JdbcAdapter adapter) throws Exception {
    try (Connection connection = DriverManager.getConnection(url)) {
      connection.setAutoCommit(false);
      SQLException failure =
          assertThrows(
              SQLException.class,
              () -> {
                try (var cursor =
                    JdbcSource.execute(
                        connection,
                        sql,
                        List.of(),
                        null,
                        null,
                        Limits.DEFAULT,
                        adapter,
                        new JdbcQueryOptions(2, 1, true))) {
                  cursor.nextRecord();
                }
              });
      assertTrue(
          failure instanceof SQLTimeoutException || "57014".equals(failure.getSQLState()),
          "Expected a driver timeout, got " + failure.getClass().getName());
      assertFalse(connection.isClosed());
      assertFalse(connection.getAutoCommit());
      // PostgreSQL aborts a timed-out transaction. Recovery belongs to the borrower.
      connection.rollback();
      assertUsable(connection);
    }
  }

  private static void assertUsable(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("select 42")) {
      assertTrue(result.next());
      assertEquals(42, result.getInt(1));
    }
  }

  private static void edgeValues(String url, boolean mysql, JdbcAdapter adapter) throws Exception {
    byte[] payload = new byte[65536];
    Arrays.fill(payload, (byte) 255);
    String text = "a😀é".repeat(4096);
    var amount = new java.math.BigDecimal("12345678901234567890.1234567890");
    var schema =
        Schema.of(
            new Field("amount", Field.Kind.EXACT, false, false),
            new Field("label", Field.Kind.STRING, false, false),
            new Field("payload", Field.Kind.ARRAY, false, false),
            new Field("nullable_value", Field.Kind.INT64, true, false),
            new Field("day_value", Field.Kind.STRING, false, false),
            new Field("stamp", Field.Kind.STRING, false, false));
    String sql =
        "select cast(? as decimal(30,10)) as amount, ? as label, ? as payload, "
            + "cast(null as "
            + (mysql ? "signed" : "bigint")
            + ") as nullable_value, "
            + "cast('2024-02-29' as date) as day_value, "
            + "cast('2024-02-29 12:34:56.123456' as "
            + (mysql ? "datetime(6)" : "timestamp(6)")
            + ") as stamp";
    List<AbstractRow> retained = new ArrayList<>();
    var data = DataLibrary.factory().getUncached();
    try (Connection connection = DriverManager.getConnection(url);
        Context context = Context.newBuilder("td-test").build()) {
      connection.setAutoCommit(false);
      context.initialize("td-test");
      context.enter();
      try {
        var layout = new StaticLayout(TestLanguage.current().language(), schema, Limits.DEFAULT);
        for (boolean useStatic : new boolean[] {false, true}) {
          try (var cursor =
              JdbcSource.execute(
                  connection,
                  sql,
                  List.of(amount, text, payload),
                  schema,
                  useStatic ? layout : null,
                  Limits.DEFAULT,
                  adapter,
                  JdbcQueryOptions.DEFAULT)) {
            retained.add(Objects.requireNonNull(cursor.nextRecord()));
            assertNull(cursor.nextRecord());
          }
        }
        for (Limits limits : List.of(new Limits(2, 6, 32, 100000), new Limits(2, 6, 100000, 8))) {
          for (boolean useStatic : new boolean[] {false, true}) {
            try (var cursor =
                JdbcSource.execute(
                    connection,
                    sql,
                    List.of(amount, text, payload),
                    schema,
                    useStatic ? layout : null,
                    limits,
                    adapter,
                    JdbcQueryOptions.DEFAULT)) {
              assertThrows(ContractViolation.class, cursor::nextRecord);
              assertTrue(cursor.isClosed());
            }
            assertFalse(connection.getAutoCommit());
            assertUsable(connection);
          }
        }
        assertThrows(
            SQLException.class,
            () -> {
              try (var ignored =
                  JdbcSource.execute(
                      connection,
                      "select td_nonexistent_column",
                      List.of(),
                      null,
                      null,
                      Limits.DEFAULT,
                      adapter,
                      JdbcQueryOptions.DEFAULT)) {
                fail("Invalid SQL was accepted");
              }
            });
        assertFalse(connection.isClosed());
        assertFalse(connection.getAutoCommit());
        connection.rollback();
        assertUsable(connection);
      } finally {
        context.leave();
      }
    }
    // Both representations survive cursor, connection and context close and parameter mutation.
    Arrays.fill(payload, (byte) 0);
    for (AbstractRow row : retained) {
      assertEquals(amount, ((ExactNumber) data.read(row, 0)).decimal());
      assertEquals(text, data.read(row, 1));
      var bytes = (DataArray) data.read(row, 2);
      assertTrue(bytes.isBytes());
      assertEquals(65536, bytes.size());
      assertEquals(255L, bytes.get(65535));
      assertSame(DataNull.INSTANCE, data.read(row, 3));
      assertEquals("2024-02-29", data.read(row, 4));
      assertEquals("2024-02-29T12:34:56.123456", data.read(row, 5));
    }
  }

  private static void exercise(String url, boolean mysql, JdbcAdapter adapter) throws Exception {
    try (Connection connection = DriverManager.getConnection(url);
        Context context =
            Context.newBuilder("td-test", "js", "python")
                .allowExperimentalOptions(true)
                .allowHostAccess(HostAccess.NONE)
                .build()) {
      context.initialize("td-test");
      context.enter();
      try {
        if (!mysql) {
          assertThrows(
              SQLException.class,
              () -> adapter.prepare(connection, "select 1", JdbcQueryOptions.DEFAULT));
          assertTrue(connection.getAutoCommit());
        }
        try (Statement statement = connection.createStatement()) {
          statement.execute(
              "create temporary table td_people(id "
                  + (mysql ? "integer unsigned" : "integer")
                  + " not null, name varchar(4) not null, payload "
                  + (mysql ? "varbinary(2)" : "bytea")
                  + " not null, enabled boolean not null)");
        }
        connection.setAutoCommit(false);
        long id = mysql ? 4294967295L : -7L;
        try (PreparedStatement insert =
            connection.prepareStatement("insert into td_people values (?, ?, ?, ?)")) {
          insert.setLong(1, id);
          insert.setString(2, "Anne");
          insert.setBytes(3, new byte[] {0, (byte) 255});
          insert.setBoolean(4, true);
          assertEquals(1, insert.executeUpdate());
        }
        String sql = "select id, name, payload, enabled from td_people where id = ?";
        try (var cursor =
            JdbcSource.execute(
                connection,
                sql,
                List.of(id),
                null,
                null,
                Limits.DEFAULT,
                adapter,
                new JdbcQueryOptions(2, 10, true))) {
          assertEquals(Field.Kind.INT64, cursor.schema().field(0).kind());
          assertEquals(
              32, Objects.requireNonNull(cursor.schema().field(0).constraints().integer()).bits());
          assertEquals(
              !mysql,
              Objects.requireNonNull(cursor.schema().field(0).constraints().integer()).signed());
          assertEquals(
              id,
              DataLibrary.factory()
                  .getUncached()
                  .readLong(Objects.requireNonNull(cursor.nextRecord()), 0));
        }
        var schema =
            Schema.of(
                Field.integer("id", 32, !mysql, false, false),
                new Field("name", Field.Kind.STRING, false, false),
                new Field("payload", Field.Kind.ARRAY, false, false),
                new Field("enabled", Field.Kind.BOOLEAN, false, false));
        var layout = new StaticLayout(TestLanguage.current().language(), schema, Limits.DEFAULT);
        AbstractRow row;
        try (var cursor =
            JdbcSource.execute(
                connection,
                sql,
                List.of(id),
                schema,
                layout,
                Limits.DEFAULT,
                adapter,
                new JdbcQueryOptions(2, 10, true))) {
          row = Objects.requireNonNull(cursor.nextRecord());
          assertInstanceOf(StaticLayout.Record.class, row);
          assertNull(cursor.nextRecord());
        }
        for (String language : List.of("js", "python")) {
          context.getBindings(language).putMember("row", row);
          assertEquals(
              id + 256,
              context
                  .eval(
                      language,
                      language.equals("js")
                          ? "row.id + row.payload[1] + (row.enabled ? 1 : 0)"
                          : "row.id + row.payload[1] + int(row.enabled)")
                  .asLong());
        }
        if (mysql) {
          try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("update td_people set enabled=2");
          }
          try (var bad =
              JdbcSource.execute(
                  connection,
                  sql,
                  List.of(id),
                  schema,
                  layout,
                  Limits.DEFAULT,
                  adapter,
                  JdbcQueryOptions.DEFAULT)) {
            assertThrows(ContractViolation.class, bad::nextRecord);
            assertTrue(bad.isClosed());
          }
        }
        if (!mysql) {
          assertThrows(
              SQLException.class,
              () -> adapter.prepare(connection, "select 1; select 2", JdbcQueryOptions.DEFAULT));
          try (var quoted =
              adapter.prepare(connection, "select ';' as text", JdbcQueryOptions.DEFAULT)) {
            assertEquals(256, quoted.getFetchSize());
          }
          try (var cursor =
              JdbcSource.execute(
                  connection,
                  "select ARRAY[1,NULL,3] as values, '{\"ok\":true}'::jsonb as document",
                  List.of(),
                  null,
                  null,
                  Limits.DEFAULT,
                  adapter,
                  JdbcQueryOptions.DEFAULT)) {
            var record = Objects.requireNonNull(cursor.nextRecord());
            var values = (DataArray) DataLibrary.factory().getUncached().read(record, 0);
            assertEquals(1L, values.get(0));
            assertSame(DataNull.INSTANCE, values.get(1));
            assertEquals(3L, values.get(2));
            assertTrue(
                ((String) DataLibrary.factory().getUncached().read(record, 1)).contains("true"));
          }
        }
        var seriesSchema = Schema.of(Field.requiredLong("n"));
        var seriesLayout =
            new StaticLayout(TestLanguage.current().language(), seriesSchema, Limits.DEFAULT);
        String series =
            mysql
                ? "with recursive series(n) as (select 1 union all select n + 1 from series where n"
                    + " < 64) select n from series"
                : "select generate_series(1,64)::bigint as n";
        try (var cursor =
            JdbcSource.execute(
                connection,
                series,
                List.of(),
                seriesSchema,
                seriesLayout,
                Limits.DEFAULT,
                adapter,
                new JdbcQueryOptions(2, 10, true))) {
          assertEquals(
              1L,
              DataLibrary.factory()
                  .getUncached()
                  .readLong(Objects.requireNonNull(cursor.nextRecord()), 0));
          if (mysql) {
            // A rejected second execution must not close the first statement's active stream.
            assertThrows(
                SQLException.class,
                () -> {
                  try (var rejected =
                      JdbcSource.execute(
                          connection,
                          "select 42",
                          List.of(),
                          null,
                          null,
                          Limits.DEFAULT,
                          adapter,
                          JdbcQueryOptions.DEFAULT)) {
                    fail("Connector/J accepted another query while streaming");
                  }
                });
            assertEquals(
                2L,
                DataLibrary.factory()
                    .getUncached()
                    .readLong(Objects.requireNonNull(cursor.nextRecord()), 0));
          }
          if (!mysql) {
            try (Statement statement = connection.createStatement();
                ResultSet cursors = statement.executeQuery("select count(*) from pg_cursors")) {
              assertTrue(cursors.next());
              assertTrue(cursors.getInt(1) > 0, "pgJDBC must retain a real server cursor");
            }
          }
        }
        for (boolean useStatic : new boolean[] {false, true}) {
          try (var bounded =
              JdbcSource.execute(
                  connection,
                  series,
                  List.of(),
                  seriesSchema,
                  useStatic ? seriesLayout : null,
                  new Limits(1, 1, 100, 0),
                  adapter,
                  new JdbcQueryOptions(2, 10, true),
                  new JdbcConsumptionLimits(2, 100, 0))) {
            assertNotNull(bounded.nextRecord());
            assertNotNull(bounded.nextRecord());
            assertThrows(ContractViolation.class, bounded::nextRecord);
            assertTrue(bounded.isClosed());
          }
          assertUsable(connection);
        }
        // Early close must leave the borrowed transaction usable. The caller controls rollback.
        assertFalse(connection.getAutoCommit());
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select 42")) {
          assertTrue(result.next());
          assertEquals(42, result.getInt(1));
        }
        connection.rollback();
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select count(*) from td_people")) {
          assertTrue(result.next());
          assertEquals(0, result.getInt(1));
        }
      } finally {
        context.leave();
      }
    }
  }
}
