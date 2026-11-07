import dev.elide.truffledata.json.JsonDecoder;
import dev.elide.truffledata.json.JsonJvm;
import dev.elide.truffledata.json.JsonMessage;
import dev.elide.truffledata.json.JsonSvm;
import dev.elide.truffledata.tape.JavaSink;
import dev.elide.truffledata.tape.TapeValue;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.graalvm.nativeimage.ImageInfo;

/** Uses only publication jars and native attachments, not the source libraries. */
public final class JsonConsumer {
  private static final byte[] INPUT =
      "{\"id\":42,\"name\":\"tr\\u00fcffle\",\"items\":[1,2.5,\"x\",null,true,{\"big\":18446744073709551615}]}"
          .getBytes(StandardCharsets.UTF_8);

  /** Only reached on the JVM; the static image classpath has no truffledata-json-jvm. */
  private static final class Jvm {
    static JsonDecoder open(Path library) {
      return JsonJvm.open(library);
    }
  }

  public static void main(String[] args) {
    try (JsonDecoder decoder =
            ImageInfo.inImageCode() ? JsonSvm.open() : Jvm.open(Path.of(args[0]));
        JsonMessage message = decoder.decode(INPUT)) {
      TapeValue root = message.root();
      if (root.member("id").longValue() != 42) throw new AssertionError("Published JSON member read");
      if (!root.member("name").stringValue().equals("trüffle"))
        throw new AssertionError("Published JSON string read");
      JavaSink sink = new JavaSink();
      root.member("items").export(sink);
      Map<String, Object> big = new LinkedHashMap<>();
      big.put("big", new BigInteger("18446744073709551615"));
      Object expected = Arrays.asList(1L, 2.5, "x", null, true, big);
      if (!expected.equals(sink.result()))
        throw new AssertionError("Published JSON export: " + sink.result());
      try {
        decoder.decode("{\"a\":1,}".getBytes(StandardCharsets.UTF_8)).close();
        throw new AssertionError("Published JSON accepted a trailing comma");
      } catch (IllegalArgumentException expectedFailure) {
        // Strict parsing: malformed input throws and never falls back.
      }
    }
    System.out.println("Published JSON decode, member read and export passed; SVM=" + ImageInfo.inImageCode());
  }
}
