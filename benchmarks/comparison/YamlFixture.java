package dev.elide.truffledata.comparison;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.elide.truffledata.Constraints;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/** Identical logical records in block YAML, flow YAML and a multi-document stream. */
@NullMarked
public final class YamlFixture {
  private YamlFixture() {}

  public static byte[] document(int index) {
    var object =
        JsonParser.parseString(new String(JsonFixture.document(index), StandardCharsets.UTF_8))
            .getAsJsonObject();
    var out = new StringBuilder();
    for (var entry : object.entrySet())
      out.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  public record Input(String name, byte[] bytes, int records, long checksum, long events) {}

  public static Input input(String name) {
    int count = name.equals("yaml-config") ? 8 : 1000;
    boolean stream = name.equals("yaml-stream"), flow = name.equals("yaml-flow");
    if (!List.of("yaml-config", "yaml-records", "yaml-flow", "yaml-stream").contains(name))
      throw new IllegalArgumentException(name);
    var out = new StringBuilder(stream ? "" : flow ? "{\"rows\":[" : "rows:\n");
    long checksum = 0,
        events = stream ? 2 : 8; // stream, document, wrapper mapping and row sequence
    for (int i = 0; i < count; i++) {
      checksum += JsonFixture.checksum(i);
      var json = new String(JsonFixture.document(i), StandardCharsets.UTF_8);
      events += eventCount(JsonParser.parseString(json)) + (stream ? 2 : 0);
      if (stream) out.append("---\n").append(new String(document(i), StandardCharsets.UTF_8));
      else if (flow) out.append(i == 0 ? "" : ",").append(json);
      else {
        var lines = new String(document(i), StandardCharsets.UTF_8).split("\n");
        for (int line = 0; line < lines.length; line++)
          out.append(line == 0 ? "  - " : "    ").append(lines[line]).append('\n');
      }
    }
    if (flow) out.append("]}");
    if (!stream) events++; // wrapper key
    if (name.equals("yaml-config")) {
      int padding = 3800 - out.toString().getBytes(StandardCharsets.UTF_8).length;
      if (padding < 2) throw new AssertionError("Config fixture grew beyond its byte budget");
      out.append('#').append(" ".repeat(padding - 2)).append('\n');
    }
    return new Input(
        name, out.toString().getBytes(StandardCharsets.UTF_8), count, checksum, events);
  }

  private static long eventCount(JsonElement value) {
    if (value.isJsonObject()) {
      long count = 2;
      for (var entry : value.getAsJsonObject().entrySet())
        count += 1 + eventCount(entry.getValue());
      return count;
    }
    if (value.isJsonArray()) {
      long count = 2;
      for (var item : value.getAsJsonArray()) count += eventCount(item);
      return count;
    }
    return 1;
  }

  private static Field field(String name, Field.Kind kind) {
    return new Field(name, kind, false, false);
  }

  private static Field record(String name, String type) {
    return field(name, Field.Kind.RECORD).withConstraints(Constraints.NONE.withRecordType(type));
  }

  private static Field array(String name, Field element) {
    return field(name, Field.Kind.ARRAY).withConstraints(Constraints.NONE.withElement(element));
  }

  public static final Schema DIMS =
      new Schema(
          "YamlDims",
          List.of(
              Field.requiredLong("w"),
              field("h", Field.Kind.FLOAT64),
              field("unit", Field.Kind.STRING)));
  public static final Schema ROW =
      new Schema(
          "YamlRow",
          List.of(
              Field.requiredLong("id"),
              field("name", Field.Kind.STRING),
              field("price", Field.Kind.FLOAT64),
              field("active", Field.Kind.BOOLEAN),
              array("tags", field("tag", Field.Kind.STRING)),
              record("dims", "YamlDims"),
              array("scores", Field.requiredLong("score")),
              field("note", Field.Kind.STRING),
              new Field("parent", Field.Kind.STRING, true, false)));
  public static final Schema ROOT = Schema.of(array("rows", record("row", "YamlRow")));
  public static final Map<String, Schema> TYPES = Map.of("YamlDims", DIMS, "YamlRow", ROW);
}
