package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.jdbc.JdbcSource;
import java.sql.*;
import java.util.*;
import org.graalvm.polyglot.*;
import org.junit.jupiter.api.Test;

class PolyglotTest {
  @Test
  void strictAssertionsAreEnforcedAcrossGuestInterop() {
    try (Context context =
        Context.newBuilder("js", "python")
            .allowExperimentalOptions(true)
            .allowHostAccess(HostAccess.NONE)
            .build()) {
      for (String language : List.of("js", "python")) {
        var broken = new CoreTest.UncheckedRow(Field.requiredLong("a"), DataNull.INSTANCE);
        context.getBindings(language).putMember("broken", broken);
        assertThrows(PolyglotException.class, () -> context.eval(language, "broken.a"));
        assertEquals(1, broken.reads);
      }
    }
  }

  @Test
  void batchRowsWorkInJsAndPythonWithoutHostAccess() {
    var row =
        Batch.copyOf(
                Schema.of(Field.requiredLong("a")),
                Collections.singletonList(new Object[] {1000L}),
                Limits.DEFAULT)
            .row(0);
    try (Context context =
        Context.newBuilder("js", "python")
            .allowExperimentalOptions(true)
            .allowHostAccess(HostAccess.NONE)
            .build()) {
      context.getBindings("js").putMember("row", row);
      assertEquals(2000L, context.eval("js", "row.a + row[0]").asLong());
      context.getBindings("python").putMember("row", row);
      assertEquals(2000L, context.eval("python", "row.a + row[0]").asLong());
    }
  }

  @Test
  void staticShapeReadsAndGuestConsumption() {
    try (Context context =
        Context.newBuilder("td-test", "js", "python")
            .allowExperimentalOptions(true)
            .allowHostAccess(HostAccess.NONE)
            .build()) {
      Value record = context.eval("td-test", "static");
      assertEquals(1000L, record.getMember("a").asLong());
      context.getBindings("js").putMember("row", record);
      assertEquals(1001L, context.eval("js", "row.a + 1").asLong());
      context.getBindings("python").putMember("row", record);
      assertEquals(1001L, context.eval("python", "row.a + 1").asLong());
      context.enter();
      try {
        var row = TestLanguage.current().layout().create(2000L);
        var root = new CoreTest.ReadRoot();
        assertEquals(2000L, root.getCallTarget().call(row));
        assertThrows(
            ContractViolation.class,
            () -> TestLanguage.current().layout().create(DataNull.INSTANCE));
      } finally {
        context.leave();
      }
    }
  }

  @Test
  void databaseCursorIsGuestIterableAndClosable() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:");
        Context context =
            Context.newBuilder("js", "python")
                .allowExperimentalOptions(true)
                .allowHostAccess(HostAccess.NONE)
                .build()) {
      for (String language : List.of("js", "python")) {
        try (var early =
            JdbcSource.execute(connection, "select 1", List.of(), null, Limits.DEFAULT)) {
          context.getBindings(language).putMember("early", early);
          context.eval(language, "early.close()");
          assertTrue(early.isClosed());
        }

        try (var cursor =
            JdbcSource.execute(
                connection,
                "select cast(x as bigint) as a from system_range(1, 3)",
                List.of(),
                Schema.of(Field.requiredLong("a")),
                Limits.DEFAULT)) {
          context.getBindings(language).putMember("rows", cursor);
          String program =
              language.equals("js")
                  ? "Array.from(rows).reduce((sum, row) => sum + row.a, 0)"
                  : "sum(row.a for row in rows)";
          assertEquals(6L, context.eval(language, program).asLong());
          assertTrue(cursor.isClosed());
        }
      }
    }
  }
}
