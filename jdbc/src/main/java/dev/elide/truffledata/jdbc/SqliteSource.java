package dev.elide.truffledata.jdbc;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import java.sql.*;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * SQLite's dynamic storage classes require an explicit contract; affinity alone is insufficient.
 */
public final class SqliteSource {
  private SqliteSource() {}

  public static JdbcCursor execute(
      Connection connection,
      String sql,
      List<? extends @Nullable Object> parameters,
      StaticLayout layout,
      Limits limits)
      throws SQLException {
    return JdbcSource.executeStatic(
        connection,
        sql,
        parameters,
        layout,
        limits,
        (result, column) ->
            decode(result.getObject(column), layout.schema().field(column - 1), limits),
        statement -> {});
  }

  @TruffleBoundary
  private static @Nullable Object decode(@Nullable Object value, Field field, Limits limits) {
    if (value == null) return null;
    if (value instanceof byte[] bytes) {
      if (bytes.length > limits.maxCells())
        throw new ContractViolation("SQLite blob budget exceeded");
      Object[] items = new Object[bytes.length];
      for (int i = 0; i < bytes.length; i++) items[i] = (long) Byte.toUnsignedInt(bytes[i]);
      return new DataArray(items);
    }
    if (field.kind() == Field.Kind.BOOLEAN && (value instanceof Integer || value instanceof Long)) {
      long number = ((Number) value).longValue();
      if (number != 0 && number != 1) throw new ContractViolation("SQLite BOOLEAN must be 0 or 1");
      return number == 1;
    }
    return Values.normalize(value);
  }
}
