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
  void snapshotsOutliveMessageAndDecoderAndCrossThreads() throws Exception {
    dev.elide.truffledata.json.JsonSnapshot snapshot;
    dev.elide.truffledata.json.JsonSnapshot subtree;
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message =
            decoder.decode(
                utf8("{\"k\\u0041\":[1,-0,\"escaped \\u4e2d text that is long\",\"plain text that is long enough\"],\"n\":1E400}"))) {
      snapshot = message.snapshot();
      subtree = message.root().member("kA").snapshot();
    }
    JsonValue root = snapshot.root();
    assertEquals(JsonValue.Kind.OBJECT, root.kind());
    assertEquals("\"k\\u0041\"", root.keyAt(0).rawText());
    JsonValue array = root.member("kA");
    assertEquals(4, array.size());
    assertEquals("1", array.element(0).numberText());
    assertEquals("-0", array.element(1).numberText());
    assertEquals("escaped 中 text that is long", array.element(2).stringValue());
    assertEquals("plain text that is long enough", array.element(3).stringValue());
    assertEquals("\"plain text that is long enough\"", array.element(3).rawText());
    assertEquals("1E400", root.member("n").numberText());
    assertEquals(JsonValue.Kind.ARRAY, subtree.root().kind());
    assertEquals(5, subtree.nodeCount());
    assertEquals("plain text that is long enough", subtree.root().element(3).stringValue());
    assertTrue(snapshot.assumption().isValid());
    String[] fromOtherThread = new String[1];
    Thread other = new Thread(() -> fromOtherThread[0] = snapshot.root().member("n").numberText());
    other.start();
    other.join();
    assertEquals("1E400", fromOtherThread[0]);
  }

  @Test
  void indexesLargeArraysOnceForRandomAccess() {
    StringBuilder source = new StringBuilder("[");
    for (int i = 0; i < 10_000; i++) source.append(i == 0 ? "" : ",").append(i);
    source.append(",[\"nested\"]]");
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message = decoder.decode(utf8(source.toString()))) {
      JsonValue array = message.root();
      assertEquals(0, message.indexedArrays());
      assertEquals("9999", array.element(9_999).numberText());
      assertEquals(1, message.indexedArrays());
      assertEquals("9999", array.element(9_999).numberText());
      assertEquals("nested", array.element(10_000).element(0).stringValue());
      assertEquals(1, message.indexedArrays(), "small arrays are walked, not indexed");
      assertThrows(IndexOutOfBoundsException.class, () -> array.element(10_001));
      long count = 0;
      for (JsonValue element : array.elements()) count++;
      assertEquals(10_001, count);
      assertEquals(1, message.indexedArrays(), "sequential traversal needs no index");
    }
  }

  @Test
  void ordersObjectMembersBySource() {
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message = decoder.decode(utf8("{\"z\":1,\"a\":[2],\"m\":3}"))) {
      JsonValue root = message.root();
      assertEquals("z", root.keyAt(0).stringValue());
      assertEquals("a", root.keyAt(1).stringValue());
      assertEquals("m", root.keyAt(2).stringValue());
      assertEquals("3", root.memberAt(2).numberText());
      assertEquals(JsonValue.Kind.ARRAY, root.memberAt(1).kind());
      assertThrows(IndexOutOfBoundsException.class, () -> root.keyAt(3));
    }
  }

  @Test
  void snapshotArrayIndexIsSafeAcrossThreads() throws Exception {
    StringBuilder source = new StringBuilder("[");
    for (int i = 0; i < 1_000; i++) source.append(i == 0 ? "" : ",").append(i);
    source.append("]");
    dev.elide.truffledata.json.JsonSnapshot snapshot;
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message = decoder.decode(utf8(source.toString()))) {
      snapshot = message.snapshot();
    }
    Thread[] readers = new Thread[8];
    String[] seen = new String[readers.length];
    for (int t = 0; t < readers.length; t++) {
      int slot = t;
      readers[t] = new Thread(() -> seen[slot] = snapshot.root().element(999 - slot).numberText());
      readers[t].start();
    }
    for (Thread reader : readers) reader.join();
    for (int t = 0; t < readers.length; t++) assertEquals(Integer.toString(999 - t), seen[t]);
    assertEquals(1, snapshot.indexedArrays());
  }

  @Test
  void staticBackendIsUnavailableOnTheJvm() {
    assertThrows(UnsupportedOperationException.class, dev.elide.truffledata.json.JsonSvm::open);
  }
}
