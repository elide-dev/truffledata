import com.google.protobuf.StringValue;
import com.oracle.truffle.api.interop.InteropLibrary;
import dev.elide.truffledata.Batch;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.jdbc.JdbcSource;
import dev.elide.truffledata.protobuf.ProtobufAdapter;
import java.util.List;

/** Compiles and runs against publication jars, exercising generated interop exports. */
public final class Consumer {
  public static void main(String[] args) throws Exception {
    var schema = Schema.of(Field.requiredLong("a"));
    var row = Batch.copyOf(schema, List.<Object[]>of(new Object[] {42L}), Limits.DEFAULT).row(0);
    var interop = InteropLibrary.getUncached();
    if (!interop.readMember(row, "a").equals(42L)) throw new AssertionError("core interop");
    var proto = new ProtobufAdapter(StringValue.getDescriptor(), Limits.DEFAULT, 8);
    if (!interop.readMember(proto.snapshot(StringValue.of("hello")), "value").equals("hello")) {
      throw new AssertionError("protobuf interop");
    }
    var plan =
        dev.elide.truffledata.protobuf.ProtoCompiler.compile(
            null, StringValue.getDescriptor(), Limits.DEFAULT, 8, 4096);
    var source =
        dev.elide.truffledata.protobuf.ProtoCompiler.bind(plan, StringValue.getDescriptor());
    var nativeRow = plan.decode(StringValue.of("hello").toByteArray());
    if (!nativeRow.has(0) || !interop.readMember(nativeRow, "value").equals("hello"))
      throw new AssertionError("native protobuf publication");
    if (!interop.readMember(source.parseJson("\"hello\""), "value").equals("hello"))
      throw new AssertionError("ProtoJSON publication dependencies");
    if (!interop.readMember(source.parseText("value: 'hello'"), "value").equals("hello"))
      throw new AssertionError("protobuf text publication");
    Class.forName("org.sqlite.JDBC");
    Class.forName("org.postgresql.Driver");
    Class.forName("com.mysql.cj.jdbc.Driver");
    if (!dev.elide.truffledata.jdbc.mysql.MysqlAdapter.INSTANCE.id().equals("mysql-v1"))
      throw new AssertionError("MySQL publication");
    if (!dev.elide.truffledata.jdbc.postgresql.PostgresqlAdapter.INSTANCE
        .id()
        .equals("postgresql-v1")) throw new AssertionError("PostgreSQL publication");
    try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
        var cursor =
            JdbcSource.execute(
                connection,
                "select 42 as a",
                List.of(),
                schema,
                null,
                Limits.DEFAULT,
                dev.elide.truffledata.jdbc.sqlite.SqliteAdapter.INSTANCE,
                dev.elide.truffledata.jdbc.JdbcQueryOptions.DEFAULT)) {
      var record = java.util.Objects.requireNonNull(cursor.nextRecord());
      if (!interop.readMember(record, "a").equals(42L))
        throw new AssertionError("SQLite publication");
    }
    if (JdbcSource.class.getDeclaredMethods().length == 0) throw new AssertionError("JDBC linkage");
  }
}
