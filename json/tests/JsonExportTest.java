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
import dev.elide.truffledata.json.JsonText;
import dev.elide.truffledata.json.JsonValue;
import dev.elide.truffledata.json.TruffleSink;
import com.oracle.truffle.api.strings.TruffleString;
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

    public void key(JsonText key) {
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

    public void decimalValue(JsonText token) {
      values++;
    }

    public void stringValue(JsonText text) {
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
            public void stringValue(JsonText text) {
              strings.add(text.toJavaString());
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
            public void stringValue(JsonText text) {
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

  // ---- JsonText cursor (performance plan Task D1) ----

  @Test
  void textCursorIsValidOnlyDuringItsEvent() {
    try (var decoder = decoder();
        var message = decoder.decode(JvmScopedTest.utf8("{\"key\":\"value\",\"n\":1E400,\"z\":null}"))) {
      var seen = new ArrayList<JsonText>();
      var views = new ArrayList<JsonValue>();
      var retaining =
          new Counting() {
            @Override
            public void key(JsonText key) {
              seen.add(key);
              views.add(key.view());
            }

            @Override
            public void stringValue(JsonText text) {
              seen.add(text);
              assertEquals("value", text.toJavaString());
            }

            @Override
            public void beginArray(long elements) {}

            @Override
            public void nullValue() {
              // A cursor retained from an earlier event fails inside a later one too.
              assertThrows(IllegalStateException.class, seen.get(0)::toJavaString);
            }
          };
      message.root().export(retaining);
      assertEquals(4, seen.size());
      assertSame(seen.get(0), seen.get(1), "one cursor per export, rebound per event");
      for (JsonText text : seen) {
        assertThrows(IllegalStateException.class, text::toJavaString);
        assertThrows(IllegalStateException.class, text::utf8Length);
        assertThrows(IllegalStateException.class, text::view);
      }
      // Views taken during an event outlive it while the message is open.
      assertEquals(List.of("key", "n", "z"), views.stream().map(JsonValue::stringValue).toList());
      message.root().export(new Counting());
      assertSame(seen.get(0), cursorOf(message), "the outermost export reuses the owner's cursor");
    }
  }

  private static JsonText cursorOf(JsonMessage message) {
    var cursor = new JsonText[1];
    message
        .root()
        .export(
            new Counting() {
              @Override
              public void key(JsonText key) {
                cursor[0] = key;
              }
            });
    return cursor[0];
  }

  @Test
  void textCursorReadsKeysStringsAndDecimals() {
    String note = "é".repeat(150); // 300 UTF-8 bytes: the bulk path
    String ascii = "a".repeat(5000); // longer than the cursor's scratch
    String source =
        "{\"id\":\"ascii\",\"\\u00e9t\\u00e9\":\"" + note + "\",\"big\":-12.5e999,\"long\":\"" + ascii + "\"}";
    try (var decoder = decoder(); var message = decoder.decode(JvmScopedTest.utf8(source))) {
      var events = new ArrayList<String>();
      message
          .root()
          .export(
              new Counting() {
                @Override
                public void key(JsonText key) {
                  String name = key.toJavaString();
                  byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
                  assertEquals(utf8.length, key.utf8Length());
                  assertEquals(name.equals("id") || name.equals("big") || name.equals("long"), key.isAscii());
                  assertEquals(fnv15(utf8), key.hash(), name);
                  assertTrue(key.utf8Equals(utf8));
                  assertFalse(key.utf8Equals(new byte[] {'x'}));
                  byte[] copy = new byte[utf8.length + 2];
                  assertEquals(utf8.length, key.copyUtf8(copy, 2));
                  assertArrayEquals(utf8, java.util.Arrays.copyOfRange(copy, 2, copy.length));
                  assertEquals(name, key.view().stringValue());
                  events.add(name);
                }

                @Override
                public void stringValue(JsonText text) {
                  String value = text.toJavaString();
                  assertEquals(0, text.hash(), "strings carry no key hash");
                  assertEquals(value, text.toTruffleString(TruffleString.Encoding.UTF_16).toJavaStringUncached());
                  assertEquals(value, text.toTruffleString(TruffleString.Encoding.UTF_8).toJavaStringUncached());
                  events.add(value);
                }

                @Override
                public void decimalValue(JsonText token) {
                  events.add(token.toJavaString());
                  assertEquals(
                      "-12.5e999", token.toTruffleString(TruffleString.Encoding.UTF_8).toJavaStringUncached());
                }
              });
      assertEquals(List.of("id", "ascii", "été", note, "big", "-12.5e999", "long", ascii), events);
      // The bulk path (≥ 256 non-ASCII bytes) and the scratch path agree with the tape's decoder.
      assertEquals(note, message.root().member("été").stringValue());
    }
  }

  private static int fnv15(byte[] utf8) {
    int hash = 0x811c9dc5;
    for (byte b : utf8) hash = (hash ^ (b & 0xff)) * 0x01000193;
    return hash & 0x7fff;
  }

  @Test
  void keyCacheReturnsOneStringPerKeyAcrossDocuments() {
    var keys = new JavaSink.KeyCache();
    try (var decoder = decoder()) {
      Map<?, ?> first, second;
      try (var message = decoder.decode(JvmScopedTest.utf8("{\"id\":1,\"name\":\"a\",\"\\u00e9\":2}"))) {
        var sink = new JavaSink(keys);
        message.export(sink);
        first = (Map<?, ?>) sink.result();
      }
      try (var message = decoder.decode(JvmScopedTest.utf8("{\"\\u00e9\":3,\"name\":\"b\",\"id\":4}"))) {
        var sink = new JavaSink(keys);
        message.export(sink);
        second = (Map<?, ?>) sink.result();
      }
      assertEquals(3, keys.size());
      var firstKeys = new ArrayList<Object>(first.keySet());
      var secondKeys = new ArrayList<Object>(second.keySet());
      assertSame(firstKeys.get(0), secondKeys.get(2));
      assertSame(firstKeys.get(1), secondKeys.get(1));
      assertSame(firstKeys.get(2), secondKeys.get(0));
      assertEquals(List.of("é", "name", "id"), secondKeys);
      // Past its capacity the cache stops growing and still returns correct names.
      var wide = new StringBuilder("{");
      for (int i = 0; i < 1500; i++) wide.append(i == 0 ? "" : ",").append("\"w").append(i).append("\":").append(i);
      try (var message = decoder.decode(JvmScopedTest.utf8(wide.append('}').toString()))) {
        for (int round = 0; round < 2; round++) {
          var sink = new JavaSink(keys);
          message.export(sink);
          var tree = (Map<?, ?>) sink.result();
          assertEquals(1500, tree.size());
          assertEquals(1499L, tree.get("w1499"));
        }
      }
      assertEquals(1024, keys.size());
      assertThrows(NullPointerException.class, () -> new JavaSink(null));
    }
  }

  @Test
  void keyCacheSinksMatchGsonOnGeneratedDocuments() {
    var random = new Random(0x7E47C0DEL);
    var keys = new JavaSink.KeyCache();
    try (var decoder = decoder()) {
      for (int document = 0; document < 200; document++) {
        var source = new StringBuilder();
        value(random, source, 0);
        String text = source.toString();
        try (var message = decoder.decode(text.getBytes(StandardCharsets.UTF_8))) {
          var sink = new JavaSink(keys);
          message.export(sink);
          assertEquals(fromGson(JsonParser.parseString(text)), sink.result(), text);
        }
      }
    }
  }

  // ---- TruffleSink (performance plan Task D2) ----

  /** The Java tree a TruffleSink tree corresponds to: TruffleString keys and strings as String. */
  private static Object fromTruffle(Object value) {
    if (value instanceof TruffleString text) {
      assertTrue(text.isCompatibleToUncached(TruffleString.Encoding.UTF_16), "UTF-16 guest strings");
      return text.toJavaStringUncached();
    }
    if (value instanceof Map<?, ?> map) {
      var out = new LinkedHashMap<String, Object>();
      for (var entry : map.entrySet()) out.put((String) fromTruffle(entry.getKey()), fromTruffle(entry.getValue()));
      return out;
    }
    if (value instanceof List<?> list) {
      var out = new ArrayList<Object>();
      for (Object item : list) out.add(fromTruffle(item));
      return out;
    }
    return value;
  }

  @Test
  void truffleSinkMatchesJavaSinkWithInternedKeys() {
    var random = new Random(0x7255F1E5L);
    var keys = new TruffleSink.KeyCache();
    assertFalse(new JavaSink().sharedText(), "sinks copy text unless they ask to share");
    try (var decoder = decoder()) {
      for (int document = 0; document < 200; document++) {
        var source = new StringBuilder();
        value(random, source, 0);
        String text = source.toString();
        try (var message = decoder.decode(text.getBytes(StandardCharsets.UTF_8))) {
          var sink = new TruffleSink(keys);
          assertTrue(sink.sharedText());
          message.export(sink);
          assertEquals(fromGson(JsonParser.parseString(text)), fromTruffle(sink.result()), text);
        }
      }
      try (var first = decoder.decode(JvmScopedTest.utf8("{\"id\":1,\"\\u00e9\":[\"x\",-0,18446744073709551615,1E400,null,true]}"));
          var second = decoder.decode(JvmScopedTest.utf8("{\"\\u00e9\":2,\"id\":3}"))) {
        var a = new TruffleSink(keys);
        first.export(a);
        var b = new TruffleSink(keys);
        second.export(b);
        var x = new ArrayList<Object>(((Map<?, ?>) a.result()).keySet());
        var y = new ArrayList<Object>(((Map<?, ?>) b.result()).keySet());
        assertSame(x.get(0), y.get(1));
        assertSame(x.get(1), y.get(0));
        assertEquals(
            java.util.Arrays.asList("x", -0.0, new BigInteger("18446744073709551615"), new BigDecimal("1E400"), null, true),
            fromTruffle(((Map<?, ?>) a.result()).get(TruffleString.fromJavaStringUncached("é", TruffleString.Encoding.UTF_16))));
      }
      int before = keys.size();
      var wide = new StringBuilder("{");
      for (int i = 0; i < 1500; i++) wide.append(i == 0 ? "" : ",").append("\"t").append(i).append("\":").append(i);
      try (var message = decoder.decode(JvmScopedTest.utf8(wide.append('}').toString()))) {
        var sink = new TruffleSink(keys);
        message.export(sink);
        assertEquals(1500, ((Map<?, ?>) sink.result()).size());
      }
      assertTrue(before < 1024 && keys.size() == 1024);
    }
    var sink = new TruffleSink(keys);
    assertThrows(IllegalStateException.class, sink::result);
    assertThrows(IllegalStateException.class, sink::end);
    assertThrows(IllegalStateException.class, () -> sink.key(null));
    sink.beginArray(0);
    sink.end();
    assertEquals(List.of(), sink.result());
    assertThrows(IllegalStateException.class, sink::nullValue);
    assertThrows(NullPointerException.class, () -> new TruffleSink(null));
  }
}
