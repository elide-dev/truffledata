package dev.elide.truffledata.json.tests;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.elide.truffledata.json.JavaSink;
import dev.elide.truffledata.json.JsonDecoder;
import dev.elide.truffledata.json.JsonExportNode;
import dev.elide.truffledata.json.JsonJvm;
import dev.elide.truffledata.json.JsonMessage;
import dev.elide.truffledata.json.JsonSink;
import dev.elide.truffledata.json.JsonSnapshot;
import dev.elide.truffledata.json.JsonValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class JsonExportTest {
  private static JsonDecoder decoder() {
    return JsonJvm.open(JvmScopedTest.library());
  }

  private static Object export(JsonValue value) {
    var sink = new JavaSink();
    value.export(sink);
    return sink.result();
  }

  @Test
  void exportsMixedDocumentAsJavaTree() {
    String source =
        "{\"s\":\"short\",\"long\":\"a string longer than sixteen bytes\",\"e\":\"tab\\tquote\\\"\\u00e9\\ud83d\\ude00\","
            + "\"i\":-42,\"u\":18446744073709551615,\"f\":1.5,\"z\":-0,\"big\":1E400,\"t\":true,\"n\":null,"
            + "\"a\":[1,[],{},[2,[3]]],\"o\":{\"k\":{\"deep\":false}}}";
    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("s", "short");
    expected.put("long", "a string longer than sixteen bytes");
    expected.put("e", "tab\tquote\"é😀");
    expected.put("i", -42L);
    expected.put("u", new BigInteger("18446744073709551615"));
    expected.put("f", 1.5);
    expected.put("z", -0.0);
    expected.put("big", new BigDecimal("1E400"));
    expected.put("t", true);
    expected.put("n", null);
    expected.put("a", List.of(1L, List.of(), Map.of(), List.of(2L, List.of(3L))));
    expected.put("o", Map.of("k", Map.of("deep", false)));
    try (var decoder = decoder(); var message = decoder.decode(JvmScopedTest.utf8(source))) {
      Object actual = export(message.root());
      assertEquals(expected, actual);
      assertEquals(List.copyOf(expected.keySet()), List.copyOf(((Map<?, ?>) actual).keySet()));
      var zero = (Double) ((Map<?, ?>) actual).get("z");
      assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(zero));
      var sink = new JavaSink();
      message.export(sink);
      assertEquals(expected, sink.result());
      assertEquals(List.of(2L, List.of(3L)), export(message.root().member("a").element(3)));
    }
  }

  @Test
  void exportsRootScalars() {
    try (var decoder = decoder()) {
      for (var entry :
          Map.of("\"x\"", (Object) "x", "7", 7L, "true", true, "2.25", 2.25).entrySet()) {
        try (var message = decoder.decode(JvmScopedTest.utf8(entry.getKey()))) {
          assertEquals(entry.getValue(), export(message.root()));
        }
      }
      try (var message = decoder.decode(JvmScopedTest.utf8("null"))) {
        assertNull(export(message.root()));
      }
    }
  }

  /** Counts events without materializing anything. */
  private static class Counting implements JsonSink {
    long keys, values, begins, ends;

    public void beginObject(long members) {
      begins++;
    }

    public void key(JsonValue key) {
      keys++;
    }

    public void beginArray(long elements) {
      begins++;
    }

    public void end() {
      ends++;
    }

    public void nullValue() {
      values++;
    }

    public void booleanValue(boolean value) {
      values++;
    }

    public void longValue(long value) {
      values++;
    }

    public void doubleValue(double value) {
      values++;
    }

    public void unsignedValue(long bits) {
      values++;
    }

    public void decimalValue(JsonValue token) {
      values++;
    }

    public void stringValue(JsonValue text) {
      values++;
    }
  }

  @Test
  void wideObjectExportsWithOneStackLevel() {
    var source = new StringBuilder("{");
    for (int i = 0; i < 10_000; i++) source.append(i == 0 ? "" : ",").append("\"m").append(i).append("\":").append(i);
    try (var decoder = decoder(); var message = decoder.decode(JvmScopedTest.utf8(source.append('}').toString()))) {
      var node = JsonExportNode.create();
      var counting = new Counting();
      node.execute(message.root(), counting);
      assertEquals(1, node.maxDepthSeen());
      assertEquals(10_000, counting.keys);
      assertEquals(10_000, counting.values);
      assertEquals(1, counting.begins);
      assertEquals(1, counting.ends);
      @SuppressWarnings("unchecked")
      var tree = (Map<String, Object>) export(message.root());
      assertEquals(10_000, tree.size());
      assertEquals(9_999L, tree.get("m9999"));
    }
  }

  private static String nested(int depth) {
    return "[".repeat(depth) + "]".repeat(depth);
  }

  @Test
  void deepestAcceptedDocumentExportsAndDeeperIsRejectedAtDecode() {
    try (var decoder = decoder()) {
      int deepest = 0;
      for (int depth = 1; depth <= 200; depth++) {
        try (var message = decoder.decode(JvmScopedTest.utf8(nested(depth)))) {
          deepest = depth;
          var node = JsonExportNode.create();
          node.execute(message.root(), new Counting());
          assertEquals(depth, node.maxDepthSeen());
        } catch (IllegalArgumentException limit) {
          break;
        }
      }
      assertTrue(deepest >= 128, "default depth limit admits at least 128 levels: " + deepest);
      int rejected = deepest + 1;
      assertThrows(IllegalArgumentException.class, () -> decoder.decode(JvmScopedTest.utf8(nested(rejected))));
      try (var message = decoder.decode(JvmScopedTest.utf8(nested(deepest)))) {
        Object tree = export(message.root());
        for (int i = 1; i < deepest; i++) tree = ((List<?>) tree).get(0);
        assertEquals(List.of(), tree);
      }
    }
  }

  @Test
  void closingDuringExportIsRefusedAndSucceedsAfter() {
    try (var decoder = decoder()) {
      JsonMessage message = decoder.decode(JvmScopedTest.utf8("[\"first\",\"second beyond sixteen bytes\"]"));
      var strings = new ArrayList<String>();
      var closing =
          new Counting() {
            @Override
            public void stringValue(JsonValue text) {
              strings.add(text.stringValue());
              if (strings.size() == 1)
                assertThrows(IllegalStateException.class, message::close, "close during export");
            }
          };
      message.root().export(closing);
      assertEquals(List.of("first", "second beyond sixteen bytes"), strings);
      message.close();
      assertThrows(IllegalStateException.class, () -> message.root());
      var sink = new JavaSink();
      JsonValue[] stale = new JsonValue[1];
      try (var other = decoder.decode(JvmScopedTest.utf8("[1]"))) {
        stale[0] = other.root();
      }
      assertThrows(IllegalStateException.class, () -> stale[0].export(sink));
    }
  }

  @Test
  void snapshotsExportAfterTheMessageCloses() {
    try (var decoder = decoder()) {
      JsonSnapshot snapshot;
      try (var message = decoder.decode(JvmScopedTest.utf8("{\"a\":[1,\"two beyond sixteen bytes\",1E400]}"))) {
        snapshot = message.snapshot();
      }
      assertEquals(
          Map.of("a", List.of(1L, "two beyond sixteen bytes", new BigDecimal("1E400"))),
          export(snapshot.root()));
    }
  }

  @Test
  void nestedExportOnTheSameMessageUsesItsOwnStack() {
    try (var decoder = decoder();
        var message = decoder.decode(JvmScopedTest.utf8("[[1,[2]],\"x\",[3,[4,[5]]]]"))) {
      var inner = new ArrayList<Object>();
      var outer =
          new Counting() {
            @Override
            public void stringValue(JsonValue text) {
              inner.add(export(message.root().element(2)));
            }
          };
      var node = JsonExportNode.create();
      node.execute(message.root(), outer);
      assertEquals(List.of(List.of(3L, List.of(4L, List.of(5L)))), inner);
      assertEquals(4, node.maxDepthSeen());
      assertEquals(6, outer.begins);
      assertEquals(6, outer.ends);
      assertEquals(5, outer.values, "five numbers; the string went to the nested export");
      assertEquals(List.of(List.of(1L, List.of(2L)), "x", List.of(3L, List.of(4L, List.of(5L)))), export(message.root()));
    }
  }

  // ---- differential against Gson with the decoder's number classification ----

  private static Object number(String token) {
    if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
      BigInteger value = new BigInteger(token);
      if (value.signum() == 0 && token.startsWith("-")) return -0.0;
      if (value.bitLength() < 64) return value.longValue();
      if (value.signum() > 0 && value.bitLength() == 64) return value;
      return new BigDecimal(token);
    }
    double value = Double.parseDouble(token);
    return Double.isInfinite(value) ? new BigDecimal(token) : value;
  }

  private static Object fromGson(JsonElement element) {
    if (element.isJsonNull()) return null;
    if (element.isJsonObject()) {
      var map = new LinkedHashMap<String, Object>();
      for (var entry : element.getAsJsonObject().entrySet()) map.put(entry.getKey(), fromGson(entry.getValue()));
      return map;
    }
    if (element.isJsonArray()) {
      var list = new ArrayList<Object>();
      for (var child : element.getAsJsonArray()) list.add(fromGson(child));
      return list;
    }
    var primitive = element.getAsJsonPrimitive();
    if (primitive.isBoolean()) return primitive.getAsBoolean();
    if (primitive.isNumber()) return number(primitive.getAsString());
    return primitive.getAsString();
  }

  private static final String[] NUMBERS = {
    "0", "-0", "7", "-123456789", "9223372036854775807", "-9223372036854775808", "9223372036854775808",
    "18446744073709551615", "18446744073709551616", "-9223372036854775809", "1.5", "-2.25e3", "6.02E23",
    "1E400", "-1e400", "0.1", "123456789012345678901234567890"
  };

  private static final String[] STRINGS = {
    "", "a", "plain text longer than sixteen bytes", "esc\\\"aped", "line\\nbreak", "\\u00e9t\\u00e9",
    "\\ud83d\\ude00 emoji", "back\\\\slash", "中文字符串", "\\/slash"
  };

  private static void value(Random random, StringBuilder out, int depth) {
    int choice = random.nextInt(depth >= 5 ? 6 : 8);
    switch (choice) {
      case 0 -> out.append("null");
      case 1 -> out.append(random.nextBoolean());
      case 2, 3 -> out.append(NUMBERS[random.nextInt(NUMBERS.length)]);
      case 4, 5 -> out.append('"').append(STRINGS[random.nextInt(STRINGS.length)]).append('"');
      case 6 -> {
        out.append('[');
        int count = random.nextInt(6);
        for (int i = 0; i < count; i++) {
          if (i > 0) out.append(',');
          value(random, out, depth + 1);
        }
        out.append(']');
      }
      default -> {
        out.append('{');
        int count = random.nextInt(6);
        for (int i = 0; i < count; i++) {
          if (i > 0) out.append(',');
          out.append("\"k").append(i).append(STRINGS[random.nextInt(STRINGS.length)]).append("\":");
          value(random, out, depth + 1);
        }
        out.append('}');
      }
    }
  }

  @Test
  void matchesGsonOnGeneratedDocuments() {
    var random = new Random(0x150A5EEDL);
    try (var decoder = decoder()) {
      for (int document = 0; document < 200; document++) {
        var source = new StringBuilder();
        value(random, source, 0);
        String text = source.toString();
        Object expected = fromGson(JsonParser.parseString(text));
        try (var message = decoder.decode(text.getBytes(StandardCharsets.UTF_8))) {
          assertEquals(expected, export(message.root()), text);
        }
      }
    }
  }

  @Test
  void javaSinkRejectsUnbalancedEvents() {
    var sink = new JavaSink();
    assertThrows(IllegalStateException.class, sink::result);
    assertThrows(IllegalStateException.class, sink::end);
    sink.beginArray(0);
    assertThrows(IllegalStateException.class, sink::result);
    sink.end();
    assertEquals(List.of(), sink.result());
    assertThrows(IllegalStateException.class, sink::nullValue);
  }
}
