package dev.elide.truffledata.bench.jdbc;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.*;
import dev.elide.truffledata.*;
import dev.elide.truffledata.bench.BenchmarkLanguage;
import dev.elide.truffledata.jdbc.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.*;

/** Equal retained-output boundaries over real drivers; elapsed timing is separate from CodSpeed. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(3)
public class JdbcPipelineBenchmark {
  public static final int ROWS = 256;

  @Param({"sqlite", "mysql", "postgresql"})
  public String driver = "sqlite";

  private @Nullable Workload workload;

  @Setup(Level.Trial)
  public void setup() throws Exception {
    if (Boolean.getBoolean("truffledata.strict"))
      throw new IllegalStateException(
          "Timing requires the production path, not strict diagnostics");
    var context =
        Context.newBuilder("td-bench")
            .allowExperimentalOptions(true)
            .option("engine.BackgroundCompilation", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .build();
    boolean entered = false;
    try {
      context.initialize("td-bench");
      context.enter();
      entered = true;
      workload = new Workload(driver, context);
    } catch (Exception | Error failure) {
      try {
        if (entered) context.leave();
      } finally {
        context.close();
      }
      throw failure;
    }
  }

  @TearDown(Level.Trial)
  public void teardown() throws Exception {
    var state = Objects.requireNonNull(workload);
    try {
      state.connection.close();
    } finally {
      state.context.leave();
      state.context.close();
      workload = null;
    }
  }

  @Benchmark
  @OperationsPerInvocation(ROWS)
  public long plainQueryRetain() throws Exception {
    var state = Objects.requireNonNull(workload);
    PlainRow[] rows = state.plain();
    state.retained = rows;
    return state.check(plainSum(rows));
  }

  @Benchmark
  @OperationsPerInvocation(ROWS)
  public long batchQueryRetain() throws Exception {
    var state = Objects.requireNonNull(workload);
    AbstractRow[] rows = state.rows(false);
    state.retained = rows;
    return state.check((long) state.batchTarget.call((Object) rows));
  }

  @Benchmark
  @OperationsPerInvocation(ROWS)
  public long staticQueryRetain() throws Exception {
    var state = Objects.requireNonNull(workload);
    AbstractRow[] rows = state.rows(true);
    state.retained = rows;
    return state.check((long) state.staticTarget.call((Object) rows));
  }

  @Benchmark
  @OperationsPerInvocation(ROWS)
  public long plainRetainedRead() {
    var state = Objects.requireNonNull(workload);
    return state.check(plainSum(state.plainRows));
  }

  @Benchmark
  @OperationsPerInvocation(ROWS)
  public long batchRetainedRead() {
    var state = Objects.requireNonNull(workload);
    return state.check((long) state.batchTarget.call((Object) state.batchRows));
  }

  @Benchmark
  @OperationsPerInvocation(ROWS)
  public long staticRetainedRead() {
    var state = Objects.requireNonNull(workload);
    return state.check((long) state.staticTarget.call((Object) state.staticRows));
  }

  private record PlainRow(long id, long optional, boolean present, String text, boolean enabled) {}

  private static long plainSum(PlainRow[] rows) {
    long sum = 0;
    for (PlainRow row : rows)
      sum += row.id + (row.present ? row.optional : 0) + row.text.length() + (row.enabled ? 1 : 0);
    return sum;
  }

  private static long integer(Object value) {
    if (!(value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long))
      throw new IllegalStateException("Expected an integral driver value");
    long number = ((Number) value).longValue();
    if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
      throw new IllegalStateException("INT32 overflow");
    return number;
  }

  private static final class Workload {
    final Context context;
    final Connection connection;
    final JdbcAdapter adapter;
    final String driver;
    final Schema schema =
        Schema.of(
            Field.integer("id", 32, true, false, false),
            Field.integer("optional_value", 32, true, true, false),
            new Field(
                "label",
                Field.Kind.STRING,
                false,
                false,
                Constraints.NONE.withLength(
                    new Constraints.Length(4, 4, Constraints.LengthUnit.UTF16))),
            new Field("enabled", Field.Kind.BOOLEAN, false, false));
    static final Limits LIMITS = new Limits(ROWS, 4, ROWS * 4, ROWS * 8);
    static final JdbcConsumptionLimits TOTAL = new JdbcConsumptionLimits(ROWS, ROWS * 4, ROWS * 8);
    final StaticLayout layout;
    final PlainRow[] plainRows;
    final AbstractRow[] batchRows;
    final AbstractRow[] staticRows;
    final CallTarget batchTarget;
    final CallTarget staticTarget;
    final long expected;
    volatile Object retained = new Object[0];
    static final String SQL =
        "select id, optional_value, label, enabled from td_bench_rows where id >= ? order by id";
    static final JdbcQueryOptions OPTIONS = new JdbcQueryOptions(256, 30, true);

    Workload(String driver, Context context) throws Exception {
      this.driver = driver;
      this.context = context;
      adapter =
          switch (driver) {
            case "sqlite" -> dev.elide.truffledata.jdbc.sqlite.SqliteAdapter.INSTANCE;
            case "mysql" -> dev.elide.truffledata.jdbc.mysql.MysqlAdapter.INSTANCE;
            case "postgresql" -> dev.elide.truffledata.jdbc.postgresql.PostgresqlAdapter.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown driver");
          };
      String url =
          driver.equals("sqlite")
              ? "jdbc:sqlite::memory:"
              : Objects.requireNonNull(
                  System.getenv("TRUFFLEDATA_" + driver.toUpperCase(Locale.ROOT) + "_URL"),
                  "Missing benchmark database URL");
      connection = DriverManager.getConnection(url);
      try {
        try (Statement statement = connection.createStatement()) {
          statement.execute(
              "create temporary table td_bench_rows(id integer not null, optional_value integer,"
                  + " label varchar(4) not null, enabled boolean not null)");
        }
        connection.setAutoCommit(false);
        long checksum = 0;
        try (PreparedStatement insert =
            connection.prepareStatement("insert into td_bench_rows values (?, ?, ?, ?)")) {
          for (int i = 0; i < ROWS; i++) {
            insert.setLong(1, 1000 + i);
            if ((i & 1) == 0) insert.setLong(2, i * 2L);
            else insert.setNull(2, Types.INTEGER);
            insert.setString(3, "data");
            insert.setBoolean(4, i % 3 == 0);
            insert.addBatch();
            checksum += 1000 + i + ((i & 1) == 0 ? i * 2L : 0) + 4 + (i % 3 == 0 ? 1 : 0);
          }
          insert.executeBatch();
        }
        expected = 322262;
        if (checksum != expected) throw new IllegalStateException("Dataset identity changed");
        var metadata = connection.getMetaData();
        System.out.printf(
            "JDBC_BENCH_METADATA dataset=jdbc-retained-four-field-v1 driver=%s driverVersion=%s"
                + " databaseVersion=%s rows=%d checksum=%d%n",
            driver,
            metadata.getDriverVersion(),
            metadata.getDatabaseProductVersion(),
            ROWS,
            expected);
        layout = new StaticLayout(BenchmarkLanguage.current().language(), schema, LIMITS);
        plainRows = plain();
        batchRows = rows(false);
        staticRows = rows(true);
        var batchRoot = new ReadRoot();
        var staticRoot = new ReadRoot();
        batchTarget = batchRoot.getCallTarget();
        staticTarget = staticRoot.getCallTarget();
        warm(batchRoot, batchTarget, batchRows);
        warm(staticRoot, staticTarget, staticRows);
        check(plainSum(plainRows));
      } catch (Exception | Error failure) {
        try {
          connection.close();
        } catch (SQLException close) {
          failure.addSuppressed(close);
        }
        throw failure;
      }
    }

    private void warm(ReadRoot root, CallTarget target, AbstractRow[] rows) {
      for (int i = 0; i < 10000 && !root.compiled; i++) check((long) target.call((Object) rows));
      if (!root.compiled)
        throw new IllegalStateException("Retained read did not reach compiled Truffle code");
    }

    long check(long sum) {
      if (sum != expected) throw new IllegalStateException("Benchmark checksum mismatch");
      return sum;
    }

    AbstractRow[] rows(boolean useStatic) throws SQLException {
      var rows = new AbstractRow[ROWS];
      int size = 0;
      try (var cursor =
          JdbcSource.execute(
              connection,
              SQL,
              List.of(1000L),
              schema,
              useStatic ? layout : null,
              LIMITS,
              adapter,
              OPTIONS,
              TOTAL)) {
        AbstractRow row;
        while ((row = cursor.nextRecord()) != null) {
          if (size == ROWS) throw new IllegalStateException("Unexpected row count");
          rows[size++] = row;
        }
      }
      if (size != ROWS) throw new IllegalStateException("Unexpected row count");
      return rows;
    }

    PlainRow[] plain() throws SQLException {
      var rows = new PlainRow[ROWS];
      int size = 0;
      // Match adapter transport policy, but use the driver's JDBC API directly.
      try (PreparedStatement statement =
          driver.equals("sqlite")
              ? connection.prepareStatement(
                  SQL, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
              : connection.prepareStatement(
                  SQL,
                  ResultSet.TYPE_FORWARD_ONLY,
                  ResultSet.CONCUR_READ_ONLY,
                  ResultSet.CLOSE_CURSORS_AT_COMMIT)) {
        statement.setFetchSize(driver.equals("mysql") ? Integer.MIN_VALUE : OPTIONS.fetchSize());
        statement.setQueryTimeout(OPTIONS.timeoutSeconds());
        statement.setObject(1, 1000L);
        try (ResultSet result = statement.executeQuery()) {
          while (result.next()) {
            if (size == ROWS) throw new IllegalStateException("Unexpected row count");
            long id = integer(Objects.requireNonNull(result.getObject(1)));
            Object optional = result.getObject(2);
            Object text = result.getObject(3);
            if (!(text instanceof String label) || label.length() != 4)
              throw new IllegalStateException("Expected a four-code-unit string");
            Object flag;
            if (driver.equals("mysql")) {
              flag = result.getLong(4);
              if (result.wasNull()) throw new IllegalStateException("Null boolean");
            } else flag = Objects.requireNonNull(result.getObject(4));
            boolean enabled;
            if (flag instanceof Boolean booleanValue) enabled = booleanValue;
            else {
              long number = integer(flag);
              if (number != 0 && number != 1) throw new IllegalStateException("Invalid boolean");
              enabled = number == 1;
            }
            rows[size++] =
                new PlainRow(
                    id, optional == null ? 0 : integer(optional), optional != null, label, enabled);
          }
        }
      }
      if (size != ROWS) throw new IllegalStateException("Unexpected row count");
      return rows;
    }
  }

  private static final class ReadRoot extends RootNode {
    @Child private ReadLongNode id = ReadLongNode.create();
    @Child private ReadLengthNode length = ReadLengthNode.create();
    @Child private ReadBooleanNode enabled = ReadBooleanNode.create();
    @Child private DataLibrary nullable = DataLibrary.factory().createDispatched(3);
    boolean compiled;

    ReadRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] rows = (Object[]) frame.getArguments()[0];
      long sum = 0;
      for (Object row : rows) {
        Object optional = nullable.read(row, 1);
        sum +=
            id.execute(row, 0)
                + (optional == DataNull.INSTANCE ? 0 : (long) optional)
                + length.execute(row, 2)
                + (enabled.execute(row, 3) ? 1 : 0);
      }
      if (CompilerDirectives.inCompiledCode()) {
        if (!compiled) compiled = true;
      } else LoopNode.reportLoopCount(this, rows.length);
      return sum;
    }
  }
}
