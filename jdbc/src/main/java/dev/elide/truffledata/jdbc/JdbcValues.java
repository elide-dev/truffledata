package dev.elide.truffledata.jdbc;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/** Driver-value conversion. Budgets bound owned snapshots, not upstream driver buffering. */
public final class JdbcValues {
  private JdbcValues() {}

  public static JdbcSource.Decoder decoder(Schema schema, Limits limits) {
    return decoder(schema, limits, UnaryOperator.identity());
  }

  /**
   * Compile once per execution. The mapper converts vendor scalar objects, including array items.
   */
  public static JdbcSource.Decoder decoder(
      Schema schema, Limits limits, UnaryOperator<Object> scalarMapper) {
    return (result, column) -> {
      Object value = decode(result.getObject(column), scalarMapper, limits, null, 0);
      if (schema.field(column - 1).kind() == Field.Kind.BOOLEAN && value instanceof Long number) {
        if (number != 0 && number != 1) throw new ContractViolation("BOOLEAN must be 0 or 1");
        return number == 1;
      }
      return value;
    };
  }

  @TruffleBoundary
  private static Object decode(
      @Nullable Object input,
      UnaryOperator<Object> scalarMapper,
      Limits limits,
      @Nullable PayloadBudget budget,
      int depth)
      throws SQLException {
    if (input instanceof java.sql.Array array) {
      try (var owned = new OwnedArray(array);
          ResultSet rows = array.getResultSet()) {
        if (depth >= 128) throw new ContractViolation("JDBC array nesting budget exceeded");
        PayloadBudget accounting = budget == null ? new PayloadBudget(limits) : budget;
        var elements = new ArrayList<Object>();
        while (rows.next()) {
          accounting.cells(1);
          elements.add(decode(rows.getObject(2), scalarMapper, limits, accounting, depth + 1));
        }
        return new DataArray(elements.toArray());
      }
    }
    if (input == null) return DataNull.INSTANCE;
    Object value = scalarMapper.apply(input);
    if (value instanceof byte[] bytes) {
      if (budget != null) budget.cells(bytes.length);
      else if (bytes.length > limits.maxCells())
        throw new ContractViolation("Binary payload budget exceeded");
      return DataArray.copyOfBytes(bytes);
    }
    if (value instanceof Timestamp timestamp) value = timestamp.toLocalDateTime().toString();
    else if (value instanceof Date date) value = date.toLocalDate().toString();
    else if (value instanceof Time time) value = time.toLocalTime().toString();
    else if (value instanceof java.time.temporal.TemporalAccessor
        || value instanceof java.util.UUID) value = value.toString();
    Object normalized = Values.normalize(value);
    if (budget != null) budget.accept(normalized);
    else if (normalized instanceof String text) {
      if (2L * text.length() > limits.maxStringBytes())
        throw new ContractViolation("String payload budget exceeded");
    } else if (normalized instanceof DataArray || normalized instanceof AbstractRow) {
      new PayloadBudget(limits).accept(normalized);
    }
    return normalized;
  }

  private record OwnedArray(java.sql.Array value) implements AutoCloseable {
    @Override
    public void close() throws SQLException {
      value.free();
    }
  }
}
