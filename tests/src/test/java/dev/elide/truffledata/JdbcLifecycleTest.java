package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.jdbc.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class JdbcLifecycleTest {
  private static final Schema SCHEMA = Schema.of(Field.requiredLong("n"));

  @Test
  void preCancelledExecutionDoesNotPrepareAndFinishedControlCannotCancelAgain() throws Exception {
    var cancellation = new JdbcCancellation();
    cancellation.cancel();
    var prepares = new AtomicInteger();
    JdbcAdapter adapter = controlled(prepares, new AtomicInteger(), null, null, null);
    try (Fixture fixture = new Fixture(null, null, null)) {
      SQLException failure =
          assertThrows(
              SQLException.class,
              () ->
                  JdbcSource.execute(
                      fixture.borrowed,
                      "select 1",
                      List.of(),
                      SCHEMA,
                      null,
                      Limits.DEFAULT,
                      adapter,
                      JdbcQueryOptions.DEFAULT,
                      JdbcConsumptionLimits.DEFAULT,
                      cancellation));
      assertEquals("57014", failure.getSQLState());
      assertEquals(0, prepares.get());
    }
    var finished = new JdbcCancellation();
    var cancels = new AtomicInteger();
    try (Fixture fixture = new Fixture(null, null, null)) {
      try (var cursor =
          JdbcSource.execute(
              fixture.borrowed,
              "select 1",
              List.of(),
              SCHEMA,
              null,
              Limits.DEFAULT,
              controlled(prepares, cancels, null, null, null),
              JdbcQueryOptions.DEFAULT,
              JdbcConsumptionLimits.DEFAULT,
              finished)) {
        assertNotNull(cursor.nextRecord());
      }
      finished.cancel();
      assertEquals(0, cancels.get());
      assertFalse(finished.isCancelled());
      assertThrows(
          IllegalStateException.class,
          () ->
              JdbcSource.execute(
                  fixture.borrowed,
                  "select 1",
                  List.of(),
                  SCHEMA,
                  null,
                  Limits.DEFAULT,
                  ADAPTER,
                  JdbcQueryOptions.DEFAULT,
                  JdbcConsumptionLimits.DEFAULT,
                  finished));
    }
  }

  @Test
  void idleCancellationRejectsBufferedRowsWithoutCallingDriver() throws Exception {
    var cancellation = new JdbcCancellation();
    var cancels = new AtomicInteger();
    try (Fixture fixture = new Fixture(null, null, null)) {
      var cursor =
          JdbcSource.execute(
              fixture.borrowed,
              "select X from system_range(1, 3)",
              List.of(),
              SCHEMA,
              null,
              new Limits(2, 1, 10, 0),
              controlled(new AtomicInteger(), cancels, null, null, null),
              JdbcQueryOptions.DEFAULT,
              JdbcConsumptionLimits.DEFAULT,
              cancellation);
      assertNotNull(cursor.nextRecord());
      cancellation.cancel();
      assertEquals(0, cancels.get());
      assertThrows(SQLException.class, cursor::nextRecord);
      assertTrue(cursor.isClosed());
      fixture.assertClosedOnce();
    }
  }

  @Test
  void activeCancellationFinishesBeforeResultCleanupAndPreservesBorrowedConnection()
      throws Exception {
    var cancellation = new JdbcCancellation();
    var executing = new CountDownLatch(1);
    var cancelling = new CountDownLatch(1);
    var releaseCancel = new CountDownLatch(1);
    var cancels = new AtomicInteger();
    try (Fixture fixture = new Fixture(null, null, null);
        var owner = Executors.newSingleThreadExecutor();
        var requester = Executors.newSingleThreadExecutor()) {
      Future<?> operation =
          owner.submit(
              () -> {
                SQLException failure =
                    assertThrows(
                        SQLException.class,
                        () ->
                            JdbcSource.execute(
                                fixture.borrowed,
                                "select 1",
                                List.of(),
                                SCHEMA,
                                null,
                                Limits.DEFAULT,
                                controlled(
                                    new AtomicInteger(),
                                    cancels,
                                    executing,
                                    cancelling,
                                    releaseCancel),
                                JdbcQueryOptions.DEFAULT,
                                JdbcConsumptionLimits.DEFAULT,
                                cancellation));
                assertEquals("57014", failure.getSQLState());
                fixture.assertClosedOnce();
                assertFalse(fixture.borrowed.isClosed());
                return null;
              });
      assertTrue(executing.await(10, TimeUnit.SECONDS));
      Future<?> request =
          requester.submit(
              () -> {
                cancellation.cancel();
                return null;
              });
      try {
        assertTrue(cancelling.await(10, TimeUnit.SECONDS));
        assertFalse(
            fixture.resultClosed.await(100, TimeUnit.MILLISECONDS),
            "Result cleanup raced an in-flight cancellation");
      } finally {
        releaseCancel.countDown();
      }
      request.get(10, TimeUnit.SECONDS);
      operation.get(10, TimeUnit.SECONDS);
      cancellation.cancel();
      assertEquals(1, cancels.get());
    } finally {
      releaseCancel.countDown();
    }
  }

  @Test
  void sqliteNativeExecutionCanBeCancelledAndConnectionReused() throws Exception {
    var cancellation = new JdbcCancellation();
    var running = new CountDownLatch(1);
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        var owner = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      org.sqlite.ProgressHandler.setHandler(
          connection,
          1000,
          new org.sqlite.ProgressHandler() {
            protected int progress() {
              running.countDown();
              return 0;
            }
          });
      Future<?> query =
          owner.submit(
              () -> {
                try {
                  assertThrows(
                      SQLException.class,
                      () -> {
                        try (var cursor =
                            JdbcSource.execute(
                                connection,
                                "with recursive series(n) as (select 1 union all select n+1 from"
                                    + " series where n<100000000) select sum(n) from series",
                                List.of(),
                                null,
                                null,
                                Limits.DEFAULT,
                                dev.elide.truffledata.jdbc.sqlite.SqliteAdapter.INSTANCE,
                                new JdbcQueryOptions(1, 60, true),
                                JdbcConsumptionLimits.DEFAULT,
                                cancellation)) {
                          cursor.nextRecord();
                        }
                      });
                } finally {
                  org.sqlite.ProgressHandler.clearHandler(connection);
                }
                assertTrue(cancellation.isCancelled());
                assertFalse(connection.isClosed());
                assertFalse(connection.getAutoCommit());
                connection.rollback();
                try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("select 42")) {
                  assertTrue(result.next());
                  assertEquals(42, result.getInt(1));
                }
                return null;
              });
      try {
        assertTrue(running.await(10, TimeUnit.SECONDS));
      } finally {
        cancellation.cancel();
      }
      query.get(15, TimeUnit.SECONDS);
    }
  }

  @Test
  void driverCancellationFailureDoesNotPreventOwnerCleanup() throws Exception {
    var cancellation = new JdbcCancellation();
    var executing = new CountDownLatch(1);
    var cancelling = new CountDownLatch(1);
    var driverFailure = new SQLException("cancel transport failed");
    try (Fixture fixture = new Fixture(null, null, null);
        var owner = Executors.newSingleThreadExecutor()) {
      Future<?> operation =
          owner.submit(
              () -> {
                SQLException failure =
                    assertThrows(
                        SQLException.class,
                        () ->
                            JdbcSource.execute(
                                fixture.borrowed,
                                "select 1",
                                List.of(),
                                SCHEMA,
                                null,
                                Limits.DEFAULT,
                                controlled(
                                    new AtomicInteger(),
                                    new AtomicInteger(),
                                    executing,
                                    cancelling,
                                    null,
                                    driverFailure),
                                JdbcQueryOptions.DEFAULT,
                                JdbcConsumptionLimits.DEFAULT,
                                cancellation));
                assertEquals("57014", failure.getSQLState());
                fixture.assertClosedOnce();
                return null;
              });
      assertTrue(executing.await(10, TimeUnit.SECONDS));
      assertSame(driverFailure, assertThrows(SQLException.class, cancellation::cancel));
      operation.get(10, TimeUnit.SECONDS);
      assertTrue(cancellation.isCancelled());
    }
  }

  private static JdbcAdapter controlled(
      AtomicInteger prepares,
      AtomicInteger cancels,
      @Nullable CountDownLatch executing,
      @Nullable CountDownLatch cancelling,
      @Nullable CountDownLatch releaseCancel) {
    return controlled(prepares, cancels, executing, cancelling, releaseCancel, null);
  }

  private static JdbcAdapter controlled(
      AtomicInteger prepares,
      AtomicInteger cancels,
      @Nullable CountDownLatch executing,
      @Nullable CountDownLatch cancelling,
      @Nullable CountDownLatch releaseCancel,
      @Nullable SQLException cancelFailure) {
    return new JdbcAdapter() {
      public String id() {
        return "controlled-fixture";
      }

      public PreparedStatement prepare(Connection connection, String sql, JdbcQueryOptions options)
          throws SQLException {
        prepares.incrementAndGet();
        return connection.prepareStatement(sql);
      }

      public ResultSet execute(PreparedStatement statement) throws SQLException {
        if (executing != null) executing.countDown();
        if (cancelling != null) await(cancelling);
        return statement.executeQuery();
      }

      public void cancel(PreparedStatement statement) throws SQLException {
        cancels.incrementAndGet();
        if (cancelling != null) cancelling.countDown();
        if (releaseCancel != null) await(releaseCancel);
        if (cancelFailure != null) throw cancelFailure;
      }

      public Schema describe(ResultSetMetaData metadata, Limits limits) {
        return SCHEMA;
      }

      public JdbcSource.Decoder decoder(ResultSetMetaData metadata, Schema schema, Limits limits) {
        return JdbcSource.DEFAULT_DECODER;
      }
    };
  }

  private static void await(CountDownLatch latch) throws SQLException {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new SQLException("Test coordination timed out");
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new SQLException("Test interrupted", failure);
    }
  }

  private static Throwable problem(int kind) {
    return switch (kind) {
      case 0 -> new SQLException("SQL failure");
      case 1 -> new IllegalStateException("runtime failure");
      default -> new AssertionError("assertion failure");
    };
  }

  private static Object fail(Throwable failure) throws SQLException {
    if (failure instanceof SQLException sql) throw sql;
    if (failure instanceof RuntimeException runtime) throw runtime;
    throw (Error) failure;
  }

  @Test
  void closeAttemptsBothResourcesForCheckedUncheckedAndAssertionFailures() throws Exception {
    for (int kind = 0; kind < 3; kind++) {
      Throwable resultFailure = problem(kind);
      Throwable statementFailure = problem((kind + 1) % 3);
      try (Fixture fixture = new Fixture(resultFailure, statementFailure, null)) {
        var cursor =
            JdbcSource.execute(fixture.borrowed, "select 1", List.of(), SCHEMA, Limits.DEFAULT);
        assertSame(resultFailure, assertThrows(Throwable.class, cursor::close));
        assertArrayEquals(new Throwable[] {statementFailure}, resultFailure.getSuppressed());
        assertTrue(cursor.isClosed());
        cursor.close();
        fixture.assertClosedOnce();
      }
    }
  }

  @Test
  void decoderFailureRemainsPrimaryAndAllCleanupFailuresAreRetained() throws Exception {
    for (int kind = 0; kind < 3; kind++) {
      Throwable primary = problem(kind);
      Throwable resultFailure = problem((kind + 1) % 3);
      Throwable statementFailure = problem((kind + 2) % 3);
      try (Fixture fixture = new Fixture(resultFailure, statementFailure, null)) {
        var cursor =
            JdbcSource.execute(
                fixture.borrowed,
                "select 1",
                List.of(),
                SCHEMA,
                Limits.DEFAULT,
                (result, column) -> fail(primary));
        assertSame(primary, assertThrows(Throwable.class, cursor::nextBatch));
        assertArrayEquals(
            new Throwable[] {resultFailure, statementFailure}, primary.getSuppressed());
        assertEquals(0, resultFailure.getSuppressed().length);
        assertTrue(cursor.isClosed());
        cursor.close();
        fixture.assertClosedOnce();
      }
    }
  }

  @Test
  void initializationFailureRemainsPrimaryForBothExecutionApis() throws Exception {
    for (boolean cooperative : new boolean[] {false, true}) {
      Throwable primary = new AssertionError("metadata failure");
      Throwable resultFailure = new IllegalStateException("result close");
      Throwable statementFailure = new SQLException("statement close");
      try (Fixture fixture = new Fixture(resultFailure, statementFailure, primary)) {
        assertSame(
            primary,
            assertThrows(
                Throwable.class,
                () -> {
                  if (cooperative) {
                    JdbcSource.execute(
                        fixture.borrowed,
                        "select 1",
                        List.of(),
                        SCHEMA,
                        null,
                        Limits.DEFAULT,
                        ADAPTER,
                        new JdbcQueryOptions(1, 1, false));
                  } else {
                    JdbcSource.execute(
                        fixture.borrowed, "select 1", List.of(), SCHEMA, Limits.DEFAULT);
                  }
                }));
        assertArrayEquals(
            new Throwable[] {resultFailure, statementFailure}, primary.getSuppressed());
        fixture.assertClosedOnce();
      }
    }
  }

  @Test
  void selfSuppressionNeverReplacesThePrimaryFailure() throws Exception {
    var shared = new AssertionError("same driver failure instance");
    try (Fixture fixture = new Fixture(shared, shared, shared)) {
      assertSame(
          shared,
          assertThrows(
              AssertionError.class,
              () ->
                  JdbcSource.execute(
                      fixture.borrowed, "select 1", List.of(), SCHEMA, Limits.DEFAULT)));
      assertEquals(0, shared.getSuppressed().length);
      fixture.assertClosedOnce();
    }
    try (Fixture fixture = new Fixture(shared, shared, null)) {
      var cursor =
          JdbcSource.execute(
              fixture.borrowed,
              "select 1",
              List.of(),
              SCHEMA,
              Limits.DEFAULT,
              (result, column) -> fail(shared));
      assertSame(shared, assertThrows(AssertionError.class, cursor::nextBatch));
      assertEquals(0, shared.getSuppressed().length);
      fixture.assertClosedOnce();
    }
  }

  @Test
  void staticDecoderFailureDoesNotCreateSuppressionCycles() throws Exception {
    var primary = new AssertionError("decoder and statement failure");
    var resultFailure = new SQLException("result close failure");
    try (var context = org.graalvm.polyglot.Context.newBuilder("td-test").build();
        Fixture fixture = new Fixture(resultFailure, primary, null)) {
      context.initialize("td-test");
      context.enter();
      try {
        var layout = new StaticLayout(TestLanguage.current().language(), SCHEMA, Limits.DEFAULT);
        var cursor =
            JdbcSource.executeStatic(
                fixture.borrowed,
                "select 1",
                List.of(),
                layout,
                Limits.DEFAULT,
                (result, column) -> fail(primary),
                statement -> {});
        assertSame(primary, assertThrows(AssertionError.class, cursor::nextRecord));
        assertArrayEquals(new Throwable[] {resultFailure}, primary.getSuppressed());
        assertEquals(0, resultFailure.getSuppressed().length);
        cursor.close();
        fixture.assertClosedOnce();
      } finally {
        context.leave();
      }
    }
  }

  private static final JdbcAdapter ADAPTER =
      new JdbcAdapter() {
        public String id() {
          return "lifecycle-fixture";
        }

        public PreparedStatement prepare(
            Connection connection, String sql, JdbcQueryOptions options) throws SQLException {
          return connection.prepareStatement(sql);
        }

        public Schema describe(ResultSetMetaData metadata, Limits limits) {
          return SCHEMA;
        }

        public JdbcSource.Decoder decoder(
            ResultSetMetaData metadata, Schema schema, Limits limits) {
          return JdbcSource.DEFAULT_DECODER;
        }
      };

  /** Real H2 resources with adversarial wrapper failures after the underlying close. */
  private static final class Fixture implements AutoCloseable {
    private final Connection physical;
    final Connection borrowed;
    private final @Nullable Throwable resultFailure;
    private final @Nullable Throwable statementFailure;
    private final @Nullable Throwable metadataFailure;
    final CountDownLatch resultClosed = new CountDownLatch(1);
    private int resultsClosed;
    private int statementsClosed;
    private int connectionsClosed;

    Fixture(
        @Nullable Throwable resultFailure,
        @Nullable Throwable statementFailure,
        @Nullable Throwable metadataFailure)
        throws SQLException {
      this.resultFailure = resultFailure;
      this.statementFailure = statementFailure;
      this.metadataFailure = metadataFailure;
      physical = DriverManager.getConnection("jdbc:h2:mem:");
      borrowed =
          (Connection)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement"))
                      return statement(
                          (PreparedStatement)
                              Objects.requireNonNull(invoke(physical, method, args)));
                    if (method.getName().equals("close")) connectionsClosed++;
                    return invoke(physical, method, args);
                  });
    }

    private PreparedStatement statement(PreparedStatement target) {
      return (PreparedStatement)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {PreparedStatement.class},
              (proxy, method, args) -> {
                if (method.getName().equals("executeQuery"))
                  return result((ResultSet) Objects.requireNonNull(invoke(target, method, args)));
                if (method.getName().equals("close")) {
                  statementsClosed++;
                  target.close();
                  if (statementFailure != null) throw statementFailure;
                  return null;
                }
                return invoke(target, method, args);
              });
    }

    private ResultSet result(ResultSet target) {
      return (ResultSet)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {ResultSet.class},
              (proxy, method, args) -> {
                if (method.getName().equals("getMetaData") && metadataFailure != null)
                  throw metadataFailure;
                if (method.getName().equals("close")) {
                  resultsClosed++;
                  resultClosed.countDown();
                  target.close();
                  if (resultFailure != null) throw resultFailure;
                  return null;
                }
                return invoke(target, method, args);
              });
    }

    private static @Nullable Object invoke(Object target, Method method, Object @Nullable [] args)
        throws Throwable {
      try {
        return method.invoke(target, args);
      } catch (InvocationTargetException failure) {
        throw Objects.requireNonNull(failure.getCause());
      }
    }

    void assertClosedOnce() throws SQLException {
      assertEquals(1, resultsClosed);
      assertEquals(1, statementsClosed);
      assertEquals(0, connectionsClosed);
      assertFalse(physical.isClosed());
      assertTrue(physical.getAutoCommit());
    }

    @Override
    public void close() throws SQLException {
      physical.close();
    }
  }
}
