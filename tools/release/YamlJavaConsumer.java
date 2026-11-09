import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.JavaSink;
import dev.elide.truffledata.yaml.YamlJava;
import dev.elide.truffledata.yaml.YamlOptions;
import dev.elide.truffledata.yaml.YamlSession;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** The published pure-Java YAML kernel: no native library, selected by YamlSession.open(). */
public final class YamlJavaConsumer {
  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  public static void main(String[] args) {
    if (!(YamlJava.source() instanceof dev.elide.truffledata.yaml.events.YamlEventSource))
      throw new AssertionError("Published YAML event source");
    byte[] input = bytes("point: &p {n: 42, name: \"trüffle 雪\"}\nalias: *p\nbase: &b {x: 1}\nm: {<<: *b}\n");
    try (var session = YamlSession.open(YamlOptions.DEFAULT.mergeKeys(true));
        var message = session.decode(input)) {
      var alias = message.root().member("alias");
      if (alias.member("n").longValue() != 42 || !alias.member("name").stringValue().equals("trüffle 雪"))
        throw new AssertionError("Published Java YAML alias/scalar reads");
      if (message.root().member("m").member("x").longValue() != 1)
        throw new AssertionError("Published Java YAML merge keys");
      var sink = new JavaSink();
      alias.export(sink);
      if (!Map.of("n", 42L, "name", "trüffle 雪").equals(sink.result()))
        throw new AssertionError("Published Java YAML export");
      try (var documents = session.documents(bytes("---\nn: 1\n---\nn: 2\n"))) {
        long sum = 0;
        for (var root = documents.next(); root != null; root = documents.next()) sum += root.member("n").longValue();
        if (sum != 3) throw new AssertionError("Published Java YAML stream");
      }
      try (var plan = session.compile(Schema.of(Field.requiredLong("n")), Map.of());
          var row = plan.decode(bytes("n: 0x1f\n"))) {
        if (row.longValue(0) != 31) throw new AssertionError("Published Java YAML typed plan");
      }
      try {
        session.decode(bytes("a: 1\na: 2\n")).close();
        throw new AssertionError("Published Java YAML accepted duplicate keys");
      } catch (IllegalArgumentException expected) {
        if (!expected.getMessage().equals("YAML syntax error: duplicate YAML key: a at line 2, column 1"))
          throw new AssertionError(expected.getMessage());
      }
    }
    System.out.println("Published Java YAML kernel decode, aliases, merges, streams and typed reads passed");
  }
}
