package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Type;
import dev.elide.truffledata.jdbc.*;
import dev.elide.truffledata.protobuf.ProtobufAdapter;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.graalvm.polyglot.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Executable samples: generated protobuf and descriptor sets, JDBC fixtures, and native SQLite. */
class IntegrationTest {
  private static Context context() {
    Context context =
        Context.newBuilder("td-test", "js", "python")
            .allowExperimentalOptions(true)
            .allowHostAccess(HostAccess.NONE)
            .build();
    context.initialize("td-test");
    return context;
  }

  private static void guests(
      Context context, AbstractRow row, String js, String python, long expected) {
    context.getBindings("js").putMember("row", row);
    assertEquals(expected, context.eval("js", js).asLong());
    context.getBindings("python").putMember("row", row);
    assertEquals(expected, context.eval("python", python).asLong());
  }

  @Test
  void generatedNativeContractsDecodeDirectlyIntoJavaScriptAndPython() {
    try (Context context = context()) {
      context.enter();
      try {
        var plan =
            dev.elide.truffledata.generated.TruffleDataContracts.create(
                    TestLanguage.current().language(), Limits.DEFAULT, 16, 4096)
                .type("truffledata.lossless.Sample");
        var row = plan.decode(new byte[] {8, 7, 26, 1, (byte) 255, 32, 99, 66, 2, 8, 3});
        guests(
            context,
            row,
            "row.count + row.blob[0] + row.status + row.child.a + row.explicit_count",
            "row[0] + row.blob[0] + row.status + row.child.a + row.explicit_count",
            364);
        assertFalse(row.has(1));
        assertTrue(DataLibrary.factory().getUncached().isPresent(row, 1));
      } finally {
        context.leave();
      }
    }
  }

  @Test
  void generatedProtobufAndSerializedDescriptorSetUseStaticNestedRecords() throws Exception {
    // Real protoc-generated classes, from google/protobuf/type.proto in protobuf-java.
    Type input =
        Type.newBuilder()
            .setName("User")
            .addFields(com.google.protobuf.Field.newBuilder().setName("id").setNumber(1))
            .addFields(com.google.protobuf.Field.newBuilder().setName("name").setNumber(2))
            .build();
    try (Context context = context()) {
      context.enter();
      try {
        var language = TestLanguage.current().language();
        var lengths =
            Map.of(
                "google.protobuf.Type.name",
                new Constraints.Length(4, 4, Constraints.LengthUnit.UTF16),
                "google.protobuf.Type.fields",
                new Constraints.Length(2, 2, Constraints.LengthUnit.ELEMENTS));
        var adapter =
            new ProtobufAdapter(language, Type.getDescriptor(), Limits.DEFAULT, 16, lengths);
        var row = adapter.snapshot(input);
        assertInstanceOf(StaticLayout.Record.class, row);
        int fields = row.schema().indexOf("fields");
        DataArray nested = (DataArray) DataLibrary.factory().getUncached().read(row, fields);
        assertInstanceOf(StaticLayout.Record.class, nested.get(0));
        assertSame(((AbstractRow) nested.get(0)).schema(), ((AbstractRow) nested.get(1)).schema());
        assertEquals(
            32,
            Objects.requireNonNull(
                    ((AbstractRow) nested.get(0))
                        .schema()
                        .field(((AbstractRow) nested.get(0)).schema().indexOf("number"))
                        .constraints()
                        .integer())
                .bits());
        guests(
            context,
            row,
            "row.fields[0].number + row.name.length",
            "row.fields[0].number + len(row.name)",
            5);
        assertThrows(
            ContractViolation.class,
            () -> adapter.snapshot(input.toBuilder().setName("Longer").build()));
        assertThrows(
            ContractViolation.class,
            () -> adapter.snapshot(input.toBuilder().clearFields().build()));

        // Same wire format as protoc --include_imports --descriptor_set_out; serialize/parse across
        // the boundary.
        var files = FileDescriptorSet.newBuilder();
        Set<FileDescriptor> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<FileDescriptor> queue = new ArrayDeque<>(List.of(Type.getDescriptor().getFile()));
        while (!queue.isEmpty()) {
          FileDescriptor file = queue.removeFirst();
          if (seen.add(file)) {
            files.addFile(file.toProto());
            queue.addAll(file.getDependencies());
          }
        }
        var loaded =
            ProtobufAdapter.fromDescriptorSet(
                language,
                FileDescriptorSet.parseFrom(files.build().toByteArray()),
                "google.protobuf.Type",
                Limits.DEFAULT,
                16);
        var decoded = DynamicMessage.parseFrom(loaded.descriptor(), input.toByteArray());
        var loadedRow = loaded.snapshot(decoded);
        assertInstanceOf(StaticLayout.Record.class, loadedRow);
        guests(
            context,
            loadedRow,
            "row.fields[1].number + row.name.length",
            "row.fields[1].number + len(row.name)",
            6);
      } finally {
        context.leave();
      }
    }
  }

  @Test
  void mysqlAndPostgresMetadataContractsThroughStaticRowsToGuests() throws Exception {
    for (JdbcContracts.Dialect dialect :
        List.of(JdbcContracts.Dialect.MYSQL, JdbcContracts.Dialect.POSTGRESQL)) {
      boolean mysql = dialect == JdbcContracts.Dialect.MYSQL;
      long id = mysql ? 4294967295L : -7L;
      try (Context context = context()) {
        context.enter();
        try {
          Fixture fixture =
              new Fixture(mysql, new Object[] {id, "Anne", null, new BigDecimal("12.30"), true});
          Schema schema = JdbcContracts.describe(fixture.metadata, dialect, Limits.DEFAULT);
          var layout = new StaticLayout(TestLanguage.current().language(), schema, Limits.DEFAULT);
          assertEquals(
              mysql ? StaticLayout.Storage.UINT : StaticLayout.Storage.INT, layout.storageKind(0));
          assertEquals(new Constraints.Decimal(5, 2), schema.field(3).constraints().decimal());
          try (var cursor =
              JdbcSource.executeStatic(
                  fixture.connection,
                  "select id, name, score, balance, enabled from people where id = ?",
                  List.of(id),
                  layout,
                  Limits.DEFAULT)) {
            AbstractRow row = Objects.requireNonNull(cursor.nextRecord());
            assertInstanceOf(StaticLayout.Record.class, row);
            assertNull(cursor.nextRecord());
            assertTrue(fixture.resultClosed && fixture.statementClosed);
            assertFalse(fixture.connectionClosed);
            assertEquals(id, fixture.bound);
            assertEquals(DataNull.INSTANCE, DataLibrary.factory().getUncached().read(row, 2));
            guests(
                context,
                row,
                "row.id + row.name.length + (row.enabled ? 1 : 0)",
                "row.id + len(row.name) + int(row.enabled)",
                id + 5);
          }
          for (Object[] invalid :
              List.of(
                  new Object[] {id, "too long", null, new BigDecimal("1.00"), true},
                  new Object[] {
                    mysql ? -1L : 2147483648L, "Anne", null, new BigDecimal("1.00"), true
                  },
                  new Object[] {id, "Anne", null, new BigDecimal("1.001"), true})) {
            Fixture bad = new Fixture(mysql, invalid);
            try (var cursor =
                JdbcSource.executeStatic(
                    bad.connection, "select ...", List.of(), layout, Limits.DEFAULT)) {
              assertThrows(ContractViolation.class, cursor::nextRecord);
              assertTrue(bad.resultClosed && bad.statementClosed);
            }
          }
        } finally {
          context.leave();
        }
      }
    }
  }

  @Test
  void nativeSqliteContractAndGuestExample() throws Exception {
    Class.forName("org.sqlite.JDBC");
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        Context context = context()) {
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "create table people (id integer, name text, enabled integer, payload blob)");
        statement.execute("insert into people values (7, 'Anne', 1, X'00FF')");
        try (ResultSet version = statement.executeQuery("select sqlite_version()")) {
          assertTrue(version.next());
          assertEquals("3.53.4", version.getString(1));
        }
      }
      context.enter();
      try {
        Schema schema =
            Schema.of(
                Field.integer("id", 32, true, false, false),
                new Field(
                    "name",
                    Field.Kind.STRING,
                    false,
                    false,
                    Constraints.NONE.withLength(
                        new Constraints.Length(1, 4, Constraints.LengthUnit.CODE_POINTS))),
                new Field("enabled", Field.Kind.BOOLEAN, false, false),
                new Field(
                    "payload",
                    Field.Kind.ARRAY,
                    false,
                    false,
                    Constraints.NONE
                        .withLength(new Constraints.Length(2, 2, Constraints.LengthUnit.ELEMENTS))
                        .withElement(Field.integer("byte", 8, false, false, false))));
        var layout = new StaticLayout(TestLanguage.current().language(), schema, Limits.DEFAULT);
        try (var cursor =
            JdbcSource.execute(
                connection,
                "select id, name, enabled, payload from people where id = ?",
                List.of(7L),
                schema,
                layout,
                Limits.DEFAULT,
                dev.elide.truffledata.jdbc.sqlite.SqliteAdapter.INSTANCE,
                JdbcQueryOptions.DEFAULT)) {
          AbstractRow row = Objects.requireNonNull(cursor.nextRecord());
          assertInstanceOf(StaticLayout.Record.class, row);
          assertNull(cursor.nextRecord());
          guests(
              context,
              row,
              "row.id + row.payload[1] + (row.enabled ? 1 : 0)",
              "row.id + row.payload[1] + int(row.enabled)",
              263);
        }
        try (Statement statement = connection.createStatement()) {
          // SQLite accepts these values: declared affinity/width is not a contract.
          statement.execute("update people set enabled = 2");
        }
        try (var bad =
            SqliteSource.execute(
                connection, "select * from people", List.of(), layout, Limits.DEFAULT)) {
          assertThrows(ContractViolation.class, bad::nextRecord);
          assertTrue(bad.isClosed());
        }
        assertFalse(connection.isClosed());
      } finally {
        context.leave();
      }
    }
  }

  /**
   * Driver-shaped fixture, deliberately limited to JDBC calls used by the adapter; no SQL
   * simulation.
   */
  private static final class Fixture {
    final ResultSetMetaData metadata;
    final Connection connection;
    boolean resultClosed, statementClosed, connectionClosed;
    @Nullable Object bound;
    int index;

    Fixture(boolean mysql, @Nullable Object[] values) {
      metadata =
          proxy(
              ResultSetMetaData.class,
              (name, args) -> {
                if (name.equals("getColumnCount")) return 5;
                int i = (Integer) Objects.requireNonNull(args)[0] - 1;
                return switch (name) {
                  case "getColumnLabel" ->
                      new String[] {"id", "name", "score", "balance", "enabled"}[i];
                  case "getColumnType" ->
                      new int[] {
                            Types.INTEGER, Types.VARCHAR, Types.BIGINT, Types.DECIMAL, Types.BOOLEAN
                          }
                          [i];
                  case "isNullable" ->
                      i == 2 ? ResultSetMetaData.columnNullable : ResultSetMetaData.columnNoNulls;
                  case "isSigned" -> !mysql || i != 0;
                  case "getPrecision" -> i == 1 ? 4 : i == 3 ? 5 : 0;
                  case "getScale" -> i == 3 ? 2 : 0;
                  default -> throw new UnsupportedOperationException(name);
                };
              });
      ResultSet result =
          proxy(
              ResultSet.class,
              (name, args) ->
                  switch (name) {
                    case "getMetaData" -> metadata;
                    case "next" -> index++ == 0;
                    case "getObject" -> values[(Integer) Objects.requireNonNull(args)[0] - 1];
                    case "close" -> {
                      resultClosed = true;
                      yield null;
                    }
                    default -> throw new UnsupportedOperationException(name);
                  });
      PreparedStatement statement =
          proxy(
              PreparedStatement.class,
              (name, args) ->
                  switch (name) {
                    case "executeQuery" -> result;
                    case "setObject" -> {
                      bound = Objects.requireNonNull(args)[1];
                      yield null;
                    }
                    case "close" -> {
                      statementClosed = true;
                      yield null;
                    }
                    default -> throw new UnsupportedOperationException(name);
                  });
      connection =
          proxy(
              Connection.class,
              (name, args) ->
                  switch (name) {
                    case "prepareStatement" -> statement;
                    case "close" -> {
                      connectionClosed = true;
                      yield null;
                    }
                    default -> throw new UnsupportedOperationException(name);
                  });
    }
  }

  @FunctionalInterface
  private interface Invoke {
    @Nullable Object call(String name, @Nullable Object[] args);
  }

  private static <T> T proxy(Class<T> type, Invoke invoke) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> invoke.call(method.getName(), args)));
  }
}
