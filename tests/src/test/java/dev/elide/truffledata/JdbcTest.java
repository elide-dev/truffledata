package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.interop.InteropLibrary;
import dev.elide.truffledata.jdbc.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class JdbcTest {
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
}
