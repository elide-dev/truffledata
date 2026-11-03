package dev.elide.truffledata.jdbc;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.Schema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Opt-in execution contract with provenance, a caller-owned revision lifetime and an exact ordered
 * projection signature. This does not infer query semantics or eliminate value validation.
 */
public final class JdbcContract {
  public enum Origin {
    EXPLICIT,
    DRIVER_METADATA
  }

  /**
   * One borrowed connection acquisition and contract revision. Close before returning the
   * connection to a pool or changing session/schema assumptions. Closing revokes new admissions,
   * releases this object's connection reference, and never closes the connection or already
   * admitted cursors.
   */
  public static final class Scope implements AutoCloseable {
    private volatile @Nullable Connection connection;
    private final String revision;

    public Scope(Connection connection, String revision) {
      if (revision.isBlank()) throw new IllegalArgumentException("Contract revision is required");
      this.connection = Objects.requireNonNull(connection);
      this.revision = revision;
    }

    public String revision() {
      return revision;
    }

    private void check(@Nullable Connection candidate) {
      Connection current = connection;
      if (current == null) throw new ContractViolation("Contract scope is closed");
      if (candidate != null && candidate != current)
        throw new ContractViolation("Contract belongs to another connection acquisition");
    }

    @Override
    public void close() {
      connection = null;
    }
  }

  private record Column(
      String label,
      int type,
      String typeName,
      int nullable,
      boolean signed,
      int precision,
      int scale) {}

  private final Schema schema;
  private final Origin origin;
  private final Scope scope;
  private final JdbcAdapter adapter;
  private final List<Column> projection;

  private JdbcContract(
      Schema schema, Origin origin, Scope scope, JdbcAdapter adapter, List<Column> projection) {
    this.schema = schema;
    this.origin = origin;
    this.scope = scope;
    this.adapter = adapter;
    this.projection = projection;
  }

  /**
   * Capture from an actual result in this scope. A null explicit schema selects adapter metadata
   * inference. Metadata is copied, never retained; neither SQL nor parameters are stored. Supplied
   * schema names may deliberately differ from SQL labels; the captured labels still guard reuse.
   */
  @TruffleBoundary
  public static JdbcContract capture(
      JdbcAdapter adapter,
      Scope scope,
      ResultSetMetaData metadata,
      @Nullable Schema explicit,
      Limits limits)
      throws SQLException {
    scope.check(null);
    limits.check(1, metadata.getColumnCount());
    Schema schema = explicit == null ? adapter.describe(metadata, limits) : explicit;
    if (schema.size() != metadata.getColumnCount())
      throw new ContractViolation("JDBC result width differs from contract");
    return new JdbcContract(
        schema,
        explicit == null ? Origin.DRIVER_METADATA : Origin.EXPLICIT,
        scope,
        adapter,
        projection(metadata));
  }

  public Schema schema() {
    return schema;
  }

  public Origin origin() {
    return origin;
  }

  public String revision() {
    return scope.revision();
  }

  /**
   * Pass this adapter to JdbcSource.execute, with null or this exact schema and an optional
   * matching static layout. Checks run once per execution, before decoder creation, not on guest
   * reads. A scope close racing admission can reject decoder creation; an already admitted cursor
   * retains its validated immutable schema. Use cancellation separately to stop that cursor.
   */
  public JdbcAdapter guardedAdapter() {
    return new JdbcAdapter() {
      @Override
      public String id() {
        return adapter.id();
      }

      @Override
      public PreparedStatement prepare(Connection connection, String sql, JdbcQueryOptions options)
          throws SQLException {
        scope.check(connection);
        return adapter.prepare(connection, sql, options);
      }

      @Override
      public ResultSet execute(PreparedStatement statement) throws SQLException {
        return adapter.execute(statement);
      }

      @Override
      public void cancel(PreparedStatement statement) throws SQLException {
        adapter.cancel(statement);
      }

      @Override
      public Schema describe(ResultSetMetaData metadata, Limits limits) {
        return schema;
      }

      @Override
      public JdbcSource.Decoder decoder(ResultSetMetaData metadata, Schema selected, Limits limits)
          throws SQLException {
        scope.check(null);
        if (selected != schema) throw new ContractViolation("Contract schema identity differs");
        if (!projection.equals(projection(metadata)))
          throw new ContractViolation("JDBC result projection differs from captured contract");
        return adapter.decoder(metadata, schema, limits);
      }
    };
  }

  private static List<Column> projection(ResultSetMetaData metadata) throws SQLException {
    int count = metadata.getColumnCount();
    var columns = new ArrayList<Column>(count);
    for (int i = 1; i <= count; i++) {
      columns.add(
          new Column(
              metadata.getColumnLabel(i),
              metadata.getColumnType(i),
              metadata.getColumnTypeName(i),
              metadata.isNullable(i),
              metadata.isSigned(i),
              metadata.getPrecision(i),
              metadata.getScale(i)));
    }
    return List.copyOf(columns);
  }
}
