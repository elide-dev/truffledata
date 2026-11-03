package dev.elide.truffledata.bench;

import dev.elide.truffledata.DataNull;
import java.lang.reflect.*;
import java.sql.*;
import java.util.Objects;

/** Deterministic driver-boundary fixture; intentionally no server, network or SQL planner. */
final class JdbcFixture {
  private JdbcFixture() {}

  static Connection connect(Object[][] rows) {
    return proxy(
        Connection.class,
        (p, method, args) ->
            switch (method.getName()) {
              case "prepareStatement" -> statement(rows);
              case "close" -> null;
              default -> throw new UnsupportedOperationException(method.getName());
            });
  }

  private static PreparedStatement statement(Object[][] rows) {
    return proxy(
        PreparedStatement.class,
        (p, method, args) ->
            switch (method.getName()) {
              case "executeQuery" -> result(rows);
              case "setObject", "close" -> null;
              default -> throw new UnsupportedOperationException(method.getName());
            });
  }

  private static ResultSet result(Object[][] rows) {
    int[] position = {-1};
    ResultSetMetaData metadata =
        proxy(
            ResultSetMetaData.class,
            (p, method, args) -> {
              if (method.getName().equals("getColumnCount")) return rows[0].length;
              throw new UnsupportedOperationException(method.getName());
            });
    return proxy(
        ResultSet.class,
        (p, method, args) ->
            switch (method.getName()) {
              case "next" -> ++position[0] < rows.length;
              case "getObject" -> {
                Object value = rows[position[0]][(Integer) Objects.requireNonNull(args)[0] - 1];
                yield value == DataNull.INSTANCE ? null : value;
              }
              case "getMetaData" -> metadata;
              case "close" -> null;
              default -> throw new UnsupportedOperationException(method.getName());
            });
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
  }
}
