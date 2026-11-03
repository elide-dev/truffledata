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
    if (JdbcSource.class.getDeclaredMethods().length == 0) throw new AssertionError("JDBC linkage");
  }
}
