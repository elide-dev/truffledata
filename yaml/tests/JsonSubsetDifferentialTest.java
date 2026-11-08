package dev.elide.truffledata.yaml.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.comparison.JsonFixture;
import dev.elide.truffledata.json.*;
import dev.elide.truffledata.tape.*;
import dev.elide.truffledata.yaml.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Exact event comparison: source member order, decoded text and all four numeric kinds. */
final class JsonSubsetDifferentialTest {
  private static Path library() {
    String override = System.getProperty("json.library");
    return override != null ? Path.of(override) : SnakeYamlDifferentialTest.workspace("json/native/json_shared.so");
  }

  private static final class Events implements TapeSink {
    final List<String> values = new ArrayList<>();
    public void beginObject(long n) { values.add("object:" + n); }
    public void beginArray(long n) { values.add("array:" + n); }
    public void end() { values.add("end"); }
    public void key(TapeText key) { values.add("key:" + key.toJavaString()); }
    public void nullValue() { values.add("null"); }
    public void booleanValue(boolean value) { values.add("bool:" + value); }
    public void longValue(long value) { values.add("i64:" + value); }
    public void unsignedValue(long value) { values.add("u64:" + Long.toUnsignedString(value)); }
    public void doubleValue(double value) { values.add("f64:" + Long.toHexString(Double.doubleToRawLongBits(value))); }
    public void decimalValue(TapeText value) { values.add("decimal:" + value.toJavaString()); }
    public void stringValue(TapeText value) { values.add("string:" + value.toJavaString()); }
  }

  private record Outcome(List<String> events, boolean rejected) {}
  private static Outcome json(JsonDecoder decoder, byte[] bytes) {
    try (JsonMessage message = decoder.decode(bytes)) {
      Events sink = new Events(); message.export(sink); return new Outcome(sink.values, false);
    } catch (IllegalArgumentException failure) { return new Outcome(List.of(), true); }
  }
  private static Outcome yaml(YamlSession session, byte[] bytes) {
    try (YamlMessage message = session.decode(bytes)) {
      Events sink = new Events(); message.export(sink); return new Outcome(sink.values, false);
    } catch (IllegalArgumentException failure) { return new Outcome(List.of(), true); }
  }
  private static void equal(JsonDecoder decoder, YamlSession session, String name, byte[] input) {
    assertEquals(json(decoder, input), yaml(session, input), name);
  }

  @Test void generatedBenchmarkFixtures() {
    try (JsonDecoder decoder = JsonJvm.open(library());
        YamlSession session = YamlJvm.openSession(YamlIntegrationTest.library(), YamlOptions.json())) {
      for (int i = 0; i < 2000; i++) equal(decoder, session, "fixture/" + i, JsonFixture.document(i));
    }
  }

  @Test void yamlSuiteJsonFixtures() throws Exception {
    String override = System.getProperty("yaml.suite");
    Path root = (override != null ? Path.of(override)
        : Path.of(System.getenv("TEST_SRCDIR"), "+http_archive+yaml_test_suite")).toRealPath();
    int cases = 0;
    try (JsonDecoder decoder = JsonJvm.open(library());
        YamlSession session = YamlJvm.openSession(YamlIntegrationTest.library(), YamlOptions.json());
        var files = Files.walk(root)) {
      for (Path input : files.filter(p -> p.getFileName().toString().equals("in.json")).sorted().toList()) {
        String name = root.relativize(input).toString();
        if (name.startsWith("name/") || name.startsWith("tags/")) continue;
        byte[] bytes = Files.readAllBytes(input);
        // Several suite fixtures contain a stream of JSON texts. Compare each complete value
        // separately; neither decoder's single-document API accepts a JSON text sequence.
        for (byte[] document : jsonDocuments(bytes)) equal(decoder, session, name, document);
        cases++;
      }
    }
    assertTrue(cases > 200, "The pinned suite's JSON fixtures must be present");
  }

  // A fixture splitter only: strings/escapes and bracket depth determine complete top-level
  // values, preserving every byte of each number/string. Production parsers remain the oracle.
  private static List<byte[]> jsonDocuments(byte[] bytes) {
    List<byte[]> result = new ArrayList<>();
    int start = -1, depth = 0; boolean quoted = false, escaped = false;
    for (int i = 0; i < bytes.length; i++) {
      byte c = bytes[i];
      if (start < 0) { if (c <= 32) continue; start = i; }
      if (quoted) {
        if (escaped) escaped = false;
        else if (c == '\\') escaped = true;
        else if (c == '"') quoted = false;
      } else if (c == '"') quoted = true;
      else if (c == '[' || c == '{') depth++;
      else if (c == ']' || c == '}') depth--;
      if (!quoted && depth == 0 && (c == ']' || c == '}' || c == '"' || c <= 32)) {
        result.add(Arrays.copyOfRange(bytes, start, i + 1)); start = -1;
      }
    }
    if (start >= 0) result.add(Arrays.copyOfRange(bytes, start, bytes.length));
    return result;
  }

  @Test void numericKindsUnicodeAndDuplicateKeys() {
    try (JsonDecoder decoder = JsonJvm.open(library());
        YamlSession session = YamlJvm.openSession(YamlIntegrationTest.library(), YamlOptions.json())) {
      for (String input : List.of(
          "[-0,-0.0,1E400,18446744073709551615,18446744073709551616,-9223372036854775809,1e-5000]",
          "[\"\",\"\\u0000\",\"\\ud83d\\ude00\",\"雪\",true,false,null]",
          "{\"a\":1,\"\\u0061\":2}", "{\"é\":1,\"\\u00e9\":2}"))
        equal(decoder, session, input, SvmAcceptance.bytes(input));
      for (int width : new int[] {4, 64, 4096}) {
        StringBuilder input = new StringBuilder("{");
        for (int i = 0; i < width; i++) input.append(i == 0 ? "" : ",").append('"').append("k").append(i).append("\":").append(i);
        input.append(",\"\\u006b0\":0}");
        byte[] bytes = SvmAcceptance.bytes(input.toString());
        assertTrue(json(decoder, bytes).rejected()); assertTrue(yaml(session, bytes).rejected());
      }
    }
  }
}
