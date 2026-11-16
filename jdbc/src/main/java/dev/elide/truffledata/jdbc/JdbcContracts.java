package dev.elide.truffledata.jdbc;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import java.sql.*;
import java.util.*;

/**
 * Opt-in driver metadata interpretation. Every advertised fact is still checked against each value.
 */
public final class JdbcContracts {
  private JdbcContracts() {}

  public enum Dialect {
    MYSQL,
    POSTGRESQL,
    SQLITE
  }

  @TruffleBoundary
  public static Schema describe(ResultSetMetaData metadata, Dialect dialect, Limits limits)
      throws SQLException {
    int count = metadata.getColumnCount();
    limits.check(1, count);
    List<Field> fields = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      String name = metadata.getColumnLabel(i);
      // SQLite affinity is not an enforced type, width or length contract. Supply an explicit
      // schema for optimization.
      if (dialect == Dialect.SQLITE) {
        fields.add(new Field(name, Field.Kind.ANY, true, false));
        continue;
      }
      boolean nullable = metadata.isNullable(i) != ResultSetMetaData.columnNoNulls;
      int type = metadata.getColumnType(i);
      boolean signed = dialect == Dialect.POSTGRESQL || metadata.isSigned(i);
      Field field =
          switch (type) {
            case Types.TINYINT -> Field.integer(name, 8, signed, nullable, false);
            case Types.SMALLINT -> Field.integer(name, 16, signed, nullable, false);
            case Types.INTEGER -> Field.integer(name, 32, signed, nullable, false);
            case Types.BIGINT -> Field.integer(name, 64, signed, nullable, false);
            case Types.REAL ->
                new Field(
                    name, Field.Kind.FLOAT64, nullable, false, Constraints.NONE.withFloatBits(32));
            case Types.FLOAT, Types.DOUBLE -> new Field(name, Field.Kind.FLOAT64, nullable, false);
            case Types.BIT, Types.BOOLEAN -> new Field(name, Field.Kind.BOOLEAN, nullable, false);
            case Types.NUMERIC, Types.DECIMAL -> new Field(name, Field.Kind.EXACT, nullable, false);
            case Types.CHAR,
                Types.NCHAR,
                Types.VARCHAR,
                Types.NVARCHAR,
                Types.LONGVARCHAR,
                Types.LONGNVARCHAR ->
                new Field(name, Field.Kind.STRING, nullable, false);
            case Types.ARRAY, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY ->
                new Field(name, Field.Kind.ARRAY, nullable, false);
            default -> new Field(name, Field.Kind.ANY, nullable, false);
          };
      int precision = metadata.getPrecision(i);
      if (precision > 0 && field.kind() == Field.Kind.STRING)
        field =
            field.withConstraints(
                field
                    .constraints()
                    .withLength(
                        new Constraints.Length(0, precision, Constraints.LengthUnit.CODE_POINTS)));
      if (precision > 0
          && field.kind() == Field.Kind.EXACT
          && field.constraints().integer() == null)
        field =
            field.withConstraints(
                field
                    .constraints()
                    .withDecimal(new Constraints.Decimal(precision, metadata.getScale(i))));
      fields.add(field);
    }
    return new Schema(fields);
  }
}
