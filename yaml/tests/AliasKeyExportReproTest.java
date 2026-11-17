package dev.elide.truffledata.yaml.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.tape.JavaSink;
import dev.elide.truffledata.tape.TapeSink;
import dev.elide.truffledata.tape.TapeText;
import dev.elide.truffledata.yaml.YamlJvm;
import dev.elide.truffledata.yaml.YamlMessage;
import dev.elide.truffledata.yaml.YamlSession;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end YAML export of mapping keys that carry anchor and tag metadata, exercised through the
 * native decoder. Verified behaviors: {@link TapeSink#anchor} precedes {@link TapeSink#key} for an
 * anchored key, the key's anchor id counts as seen for {@link TapeSink#alias(int)} reuse, tagged
 * mapping keys emit {@link TapeSink#tag}, and an alias used as a key resolves to its target's key
 * text without firing an alias event inside the key slot.
 */
final class AliasKeyExportReproTest {
  static Path library() {
    return YamlIntegrationTest.library();
  }

  static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  static final class Recording implements TapeSink {
    final List<String> out = new ArrayList<>();
    final boolean reuseAliases;

    Recording(boolean reuseAliases) {
      this.reuseAliases = reuseAliases;
    }

    @Override public void beginObject(long n) { out.add("{" + n); }
    @Override public void beginArray(long n) { out.add("[" + n); }
    @Override public void end() { out.add("end"); }
    @Override public void key(TapeText name) { out.add("key:" + name.toJavaString()); }
    @Override public void nullValue() { out.add("null"); }
    @Override public void booleanValue(boolean b) { out.add("bool:" + b); }
    @Override public void longValue(long n) { out.add("i64:" + n); }
    @Override public void unsignedValue(long n) { out.add("u64:" + Long.toUnsignedString(n)); }
    @Override public void doubleValue(double d) { out.add("f64:" + d); }
    @Override public void decimalValue(TapeText t) { out.add("dec:" + t.toJavaString()); }
    @Override public void stringValue(TapeText t) { out.add("string:" + t.toJavaString()); }
    @Override public void tag(TapeText uri) { out.add("tag:" + uri.toJavaString()); }
    @Override public void anchor(int id) { out.add("anchor:" + id); }
    @Override
    public boolean alias(int id) {
      out.add("alias:" + id + ":" + reuseAliases);
      return reuseAliases;
    }
  }

  @Test
  void anchoredKeyEmitsAnchorBeforeKeyAndReusesAlias() {
    String source = "{&a k: v, other: *a}\n";
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(bytes(source))) {
      Recording sink = new Recording(true);
      message.export(sink);
      assertEquals(List.of(
          "{2",
          "anchor:0",
          "key:k",
          "string:v",
          "key:other",
          "alias:0:true",
          "end"), sink.out);
    }
  }

  @Test
  void anchoredKeyAliasExpandsWhenSinkDeclinesReuse() {
    String source = "{&a k: v, other: *a}\n";
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(bytes(source))) {
      Recording sink = new Recording(false);
      message.export(sink);
      assertEquals(List.of(
          "{2",
          "anchor:0",
          "key:k",
          "string:v",
          "key:other",
          "alias:0:false",
          "anchor:0",
          "string:k",
          "end"), sink.out);
    }
  }

  @Test
  void taggedKeyEmitsTagBeforeKey() {
    String source = "!!str 1: v\n";
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(bytes(source))) {
      Recording sink = new Recording(true);
      message.export(sink);
      assertEquals(List.of(
          "{1",
          "tag:tag:yaml.org,2002:str",
          "key:1",
          "string:v",
          "end"), sink.out);
    }
  }

  @Test
  void integerTaggedKeyEmitsIntTagBeforeKey() {
    String source = "!!int 1: w\n";
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(bytes(source))) {
      Recording sink = new Recording(true);
      message.export(sink);
      assertEquals(List.of(
          "{1",
          "tag:tag:yaml.org,2002:int",
          "key:1",
          "string:w",
          "end"), sink.out);
    }
  }

  @Test
  void anchoredAndTaggedKeyEmitsTagThenAnchorBeforeKey() {
    String source = "!!str &a 1: v\n";
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(bytes(source))) {
      Recording sink = new Recording(true);
      message.export(sink);
      assertEquals(List.of(
          "{1",
          "tag:tag:yaml.org,2002:str",
          "anchor:0",
          "key:1",
          "string:v",
          "end"), sink.out);
    }
  }

  /** {@code base: &a 31\nother: *a : v\n} — alias used as a key resolves to "31" with no alias event. */
  @Test
  void aliasUsedAsKeyEmitsResolvedTextAndNoAliasEvent() {
    String source = "base: &a 31\nother: *a : v\n";
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(bytes(source))) {
      Recording sink = new Recording(true);
      message.export(sink);
      assertEquals(List.of(
          "{2",
          "key:base",
          "anchor:0",
          "i64:31",
          "key:other",
          "{1",
          "key:31",
          "string:v",
          "end",
          "end"), sink.out);
    }
  }

  @Test
  void javaSinkReusesAnchoredKeyStringThroughAlias() {
    String source = "{&a k: v, other: *a}\n";
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(bytes(source))) {
      JavaSink sink = new JavaSink();
      message.export(sink);
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) sink.result();
      assertEquals("v", map.get("k"));
      assertEquals("k", map.get("other"));
      assertSame(map.keySet().iterator().next(), map.get("other"));
    }
  }

  @Test
  void javaSinkRepeatedAliasesToAnchoredKeyReuseSameStringInstance() {
    String source = "{&a k: v, other: *a, next: *a}\n";
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(bytes(source))) {
      JavaSink sink = new JavaSink();
      message.export(sink);
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) sink.result();
      assertEquals("v", map.get("k"));
      assertEquals("k", map.get("other"));
      assertEquals("k", map.get("next"));
      String anchored = map.keySet().iterator().next();
      assertSame(anchored, map.get("other"));
      assertSame(anchored, map.get("next"));
    }
  }

  @Test
  void truffleSinkReusesAnchoredKeyThroughAlias() {
    String source = "{&a k: v, other: *a}\n";
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(bytes(source))) {
      dev.elide.truffledata.tape.TruffleSink sink =
          new dev.elide.truffledata.tape.TruffleSink(
              new dev.elide.truffledata.tape.TruffleSink.KeyCache());
      message.export(sink);
      Object result = sink.result();
      assertInstanceOf(Map.class, result);
      @SuppressWarnings("unchecked")
      Map<Object, Object> map = (Map<Object, Object>) result;
      Object anchored = map.keySet().iterator().next();
      assertEquals("k", anchored.toString());
      assertSame(anchored, map.get(map.keySet().toArray()[1]));
    }
  }
}
