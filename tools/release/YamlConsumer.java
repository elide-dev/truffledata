import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.JavaSink;
import dev.elide.truffledata.tape.TruffleSink;
import dev.elide.truffledata.yaml.YamlJvm;
import dev.elide.truffledata.yaml.YamlSession;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.graalvm.nativeimage.ImageInfo;

/** Exercises published YAML jars and native attachments without source-library dependencies. */
public final class YamlConsumer {
  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  /** The static image deliberately excludes the JVM backend from its runtime classpath. */
  private static final class Jvm {
    static YamlSession open(Path library) {
      return YamlJvm.openSession(library);
    }
  }

  public static void main(String[] args) {
    byte[] input = bytes("point: &p {n: 42, name: \"trüffle 雪\"}\nalias: *p\n");
    try (var session = ImageInfo.inImageCode() ? YamlSession.open() : Jvm.open(Path.of(args[0]));
        var message = session.decode(input)) {
      var alias = message.root().member("alias");
      if (alias.member("n").longValue() != 42
          || !alias.member("name").stringValue().equals("trüffle 雪"))
        throw new AssertionError("Published YAML alias/scalar reads");
      var sink = new JavaSink();
      alias.export(sink);
      if (!Map.of("n", 42L, "name", "trüffle 雪").equals(sink.result()))
        throw new AssertionError("Published YAML export");
      var snapshot = message.snapshot();
      if (snapshot.root().member("alias").member("n").longValue() != 42)
        throw new AssertionError("Published YAML snapshot");
      var truffle = new TruffleSink(new TruffleSink.KeyCache());
      session.parse(bytes("name: \"雪😀\"\n"), truffle);
      if (truffle.result() == null) throw new AssertionError("Published YAML shared text");
      try (var documents = session.documents(bytes("---\nn: 1\n---\nn: 2\n"))) {
        long sum = 0;
        for (var root = documents.next(); root != null; root = documents.next())
          sum += root.member("n").longValue();
        if (sum != 3) throw new AssertionError("Published YAML stream");
      }
      try (var plan = session.compile(Schema.of(Field.requiredLong("n")), Map.of());
          var row = plan.decode(bytes("n: 7\n"))) {
        if (row.longValue(0) != 7) throw new AssertionError("Published YAML typed plan");
      }
      try {
        session.decode(bytes("a: 1\na: 2\n")).close();
        throw new AssertionError("Published YAML accepted duplicate keys");
      } catch (IllegalArgumentException expected) {
        // The published configuration rejects duplicates and recovers for subsequent calls.
      }
      session.close();
      if (alias.member("n").longValue() != 42)
        throw new AssertionError("Published YAML retained owner after session close");
    }
    System.out.println(
        "Published YAML decode, aliases, text, snapshots, streams and typed reads passed; SVM="
            + ImageInfo.inImageCode());
  }
}
