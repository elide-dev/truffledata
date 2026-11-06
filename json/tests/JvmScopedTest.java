package dev.elide.truffledata.json.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.json.JsonDecoder;
import dev.elide.truffledata.json.JsonJvm;
import dev.elide.truffledata.json.JsonMessage;
import dev.elide.truffledata.json.JsonValue;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class JvmScopedTest {
  static Path library() {
    return Path.of(
        System.getenv("TEST_SRCDIR"),
        System.getenv("TEST_WORKSPACE"),
        "json/native/json_shared.so");
  }

  static byte[] utf8(String source) {
    return source.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void retainsChildrenAfterInputMutationAndDecoderClose() {
    byte[] input = utf8("{\"a\":[true,null,{\"s\":\"retained child beyond sixteen\"}],\"n\":1e9999999999999999}");
    JsonMessage message;
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      message = decoder.decode(input);
    }
    Arrays.fill(input, (byte) 'x');
    try (message) {
      JsonValue root = message.root();
      assertEquals(JsonValue.Kind.OBJECT, root.kind());
      assertEquals("retained child beyond sixteen", root.member("a").element(2).member("s").stringValue());
      assertEquals("\"retained child beyond sixteen\"", root.member("a").element(2).member("s").rawText());
      assertTrue(root.member("a").element(0).booleanValue());
      assertEquals(JsonValue.Kind.NULL, root.member("a").element(1).kind());
      assertEquals("1e9999999999999999", root.member("n").numberText());
    }
  }

  @Test
  void rejectsDuplicateKeysAndMalformedInput() {
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      assertThrows(IllegalArgumentException.class, () -> decoder.decode(utf8("{\"a\":1,\"\\u0061\":2}")));
      assertThrows(IllegalArgumentException.class, () -> decoder.decode(utf8("[1,]")));
      assertThrows(IllegalArgumentException.class, () -> decoder.decode(new byte[0]));
    }
  }

  @Test
  void confinesMessagesToTheirThreadAndClosesIdempotently() throws Exception {
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message = decoder.decode(utf8("[1,2]"))) {
      JsonValue root = message.root();
      AtomicInteger rejected = new AtomicInteger();
      Thread other =
          new Thread(
              () -> {
                try {
                  root.size();
                } catch (IllegalStateException expected) {
                  rejected.incrementAndGet();
                }
                try {
                  message.close();
                } catch (IllegalStateException expected) {
                  rejected.incrementAndGet();
                }
              });
      other.start();
      other.join();
      assertEquals(2, rejected.get());
      assertEquals(2, root.size());
      var alive = message.assumption();
      assertTrue(alive.isValid());
      message.close();
      message.close();
      assertThrows(IllegalStateException.class, root::size);
      assertFalse(alive.isValid());
      assertThrows(IllegalStateException.class, message::assumption);
    }
  }

  @Test
  void siblingsAreIndependent() {
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      JsonMessage first = decoder.decode(utf8("{\"k\":\"first value is long enough\"}"));
      JsonMessage second = decoder.decode(utf8("{\"k\":\"second value is long enough\"}"));
      first.close();
      assertEquals("second value is long enough", second.root().member("k").stringValue());
      second.close();
    }
  }

  @Test
  void adoptedSegmentReleasesExactlyOnceAfterClose() {
    byte[] source = utf8("{\"s\":\"abcdefghijklmnopqrstuvwxyz0123456789\"}");
    AtomicInteger released = new AtomicInteger();
    try (JsonDecoder decoder = JsonJvm.open(library()); Arena arena = Arena.ofConfined()) {
      MemorySegment padded = arena.allocate(source.length + 64);
      MemorySegment.copy(source, 0, padded, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, source.length);
      JsonMessage message = decoder.adopt(padded, source.length, released::incrementAndGet);
      assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", message.root().member("s").stringValue());
      assertEquals(0, released.get());
      message.close();
      message.close();
      assertEquals(1, released.get());
      MemorySegment unpadded = arena.allocate(source.length);
      MemorySegment.copy(source, 0, unpadded, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, source.length);
      try (JsonMessage copied = decoder.adopt(unpadded, source.length, released::incrementAndGet)) {
        assertEquals(JsonValue.Kind.STRING, copied.root().member("s").kind());
      }
      assertEquals(2, released.get());
    }
  }

  @Test
  void staticBackendIsUnavailableOnTheJvm() {
    assertThrows(UnsupportedOperationException.class, dev.elide.truffledata.json.JsonSvm::open);
  }
}
