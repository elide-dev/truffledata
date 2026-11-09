package dev.elide.truffledata.yaml.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.tape.TapeValue;
import dev.elide.truffledata.yaml.*;
import dev.elide.truffledata.yaml.events.YamlEventSource;
import dev.elide.truffledata.yaml.events.YamlMark;
import dev.elide.truffledata.yaml.events.YamlScalarStyle;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The shared acceptance contracts on the pure-Java kernel, and its selection and event SPI. */
final class JavaKernelTest {
  @Test
  void sharedAcceptance() {
    try (YamlSession session = YamlJava.openSession()) {
      SvmAcceptance.verify(session);
    }
    try (YamlSession session = YamlJava.openSession()) {
      TypedAcceptance.verify(session);
    }
  }

  @Test
  void openSelectsTheJavaKernelWithoutAnArchive() {
    // On a JVM no static archive is linked: YamlSession.open falls back to the kernel on the class
    // path, while the explicitly static entry points keep requiring SVM.
    try (YamlSession session = YamlSession.open(YamlOptions.yaml11());
        YamlMessage message = session.decode(SvmAcceptance.bytes("on: yes\nx: 0755\n"))) {
      assertTrue(message.root().member("true").booleanValue());
      assertEquals(493, message.root().member("x").longValue());
    }
    assertThrows(UnsupportedOperationException.class, YamlSvm::open);
  }

  @Test
  void errorsMatchTheNativeShapes() {
    try (YamlSession session = YamlJava.openSession()) {
      var duplicate =
          assertThrows(IllegalArgumentException.class, () -> session.decode(SvmAcceptance.bytes("x: 0\na: 1\na: 2\n")));
      assertEquals("YAML syntax error: duplicate YAML key: a at line 3, column 1", duplicate.getMessage());
      var multiple =
          assertThrows(IllegalArgumentException.class, () -> session.decode(SvmAcceptance.bytes("a\n---\nb\n")));
      assertEquals("Multiple YAML documents; use a stream", multiple.getMessage());
      var syntax = assertThrows(IllegalArgumentException.class, () -> session.decode(SvmAcceptance.bytes("a: [1, 2\n")));
      assertTrue(syntax.getMessage().startsWith("YAML syntax error: "), syntax.getMessage());
      // The session recovers after every failure.
      try (YamlMessage message = session.decode(SvmAcceptance.bytes("a: 1"))) {
        assertEquals(1, message.root().member("a").longValue());
      }
    }
    YamlLimits shallow = new YamlLimits(1 << 20, 1 << 20, 1000, 1 << 20, 2, 10, 100);
    try (YamlSession session = YamlJava.openSession(YamlOptions.DEFAULT.withLimits(shallow))) {
      var limit = assertThrows(IllegalArgumentException.class, () -> session.decode(SvmAcceptance.bytes("[[[1]]]")));
      assertTrue(limit.getMessage().startsWith("YAML resource limit: YAML resource limit: tape depth limit"), limit.getMessage());
    }
  }

  /** A hand-written event source: the kernel depends on the SPI alone. */
  @Test
  void anyEventSourceDrivesTheKernel() {
    YamlEventSource fixed =
        (utf8, length, events) -> {
          YamlMark at = new YamlMark(0, 1, 1);
          events.streamStart();
          events.documentStart(false, 0, Map.of(), at);
          events.mappingStart(null, null, false, at);
          events.scalar("k", YamlScalarStyle.PLAIN, false, null, null, at, 0);
          events.sequenceStart("s", null, true, at);
          events.scalar("0x10", YamlScalarStyle.PLAIN, false, null, null, at, 0);
          events.scalar("x", YamlScalarStyle.DOUBLE_QUOTED, false, null, "tag:yaml.org,2002:str", at, 0);
          events.sequenceEnd(at);
          events.scalar("again", YamlScalarStyle.PLAIN, false, null, null, at, 0);
          events.alias("s", at);
          events.mappingEnd(at);
          events.documentEnd(false, at);
          events.streamEnd();
        };
    try (YamlSession session = YamlJava.openSession(YamlOptions.DEFAULT, fixed);
        YamlMessage message = session.decode(new byte[] {'-'})) {
      TapeValue root = message.root();
      assertEquals(16, root.member("k").element(0).longValue());
      assertEquals("x", root.member("again").element(1).stringValue());
      assertTrue(Yaml.isAlias(root.memberAt(1)));
      assertEquals("s", Yaml.anchor(root.member("k")));
      assertEquals(Style.FLOW, Yaml.style(root.member("k")));
    }
  }

  // ---- untrusted-input complexity (security review of #41) ------------------------------------

  private static final Duration LINEAR = Duration.ofSeconds(20);

  /** LAST_WINS: each repeat of a key after many anchored entries is O(1), not O(entries + anchors). */
  @Test
  void lastWinsDuplicatesStayLinear() {
    int n = 200_000;
    StringBuilder doc = new StringBuilder();
    for (int i = 0; i < n; i++) doc.append('k').append(i).append(": &a").append(i).append(' ').append(i).append('\n');
    for (int i = 0; i < n; i++) doc.append("d: ").append(i).append('\n');
    byte[] input = SvmAcceptance.bytes(doc.toString());
    assertTimeoutPreemptively(LINEAR, () -> {
      try (YamlSession session = YamlJava.openSession(YamlOptions.DEFAULT.withDuplicateKeys(DuplicateKeys.LAST_WINS));
          YamlMessage message = session.decode(input)) {
        assertEquals(n + 1, message.root().size());
        assertEquals(n - 1, message.root().member("d").longValue());
        assertEquals(n - 1, message.root().member("k" + (n - 1)).longValue());
      }
    });
    // Erased entries leave the value budget, as the native writer's erase does; their text stays
    // charged, so unbounded repeats are still bounded by maxTextBytes.
    YamlLimits text = new YamlLimits(64L << 20, 64L << 20, 1_000_000, 1000, 128, 10_000, 1_000_000);
    try (YamlSession session = YamlJava.openSession(
        YamlOptions.DEFAULT.withDuplicateKeys(DuplicateKeys.LAST_WINS).withLimits(text))) {
      var limit = assertThrows(IllegalArgumentException.class,
          () -> session.decode(SvmAcceptance.bytes("d: 1\n".repeat(2000))));
      assertTrue(limit.getMessage().contains("tape text limit"), limit.getMessage());
    }
  }

  /** Each merge checks only the nodes it removes, not every anchor of the document. */
  @Test
  void mergesStayLinear() {
    int n = 200_000;
    StringBuilder doc = new StringBuilder("b: &b {x: 1}\n");
    for (int i = 0; i < n; i++) doc.append('m').append(i).append(": &a").append(i).append(" {<<: *b, y: ").append(i).append("}\n");
    byte[] input = SvmAcceptance.bytes(doc.toString());
    assertTimeoutPreemptively(LINEAR, () -> {
      try (YamlSession session = YamlJava.openSession(YamlOptions.DEFAULT.mergeKeys(true).withLimits(
              new YamlLimits(64L << 20, 64L << 20, 2_000_000, 64L << 20, 128, 1_000_000, 1_000_000)));
          YamlMessage message = session.decode(input)) {
        assertEquals(1, message.root().member("m" + (n - 1)).member("x").longValue());
      }
    });
  }

  /** A long line fed in small chunks is searched for its line break once. */
  @Test
  void longStreamLinesStayLinear() {
    byte[] line = SvmAcceptance.bytes("x".repeat(8 << 20) + "\n---\ny\n");
    assertTimeoutPreemptively(LINEAR, () -> {
      try (YamlSession session = YamlJava.openSession();
          YamlStream stream = session.stream(new YamlStreamOptions(16 << 20, 16 << 20))) {
        for (int fed = 0; fed < line.length; ) fed += stream.feed(line, fed, Math.min(64, line.length - fed));
        stream.finish();
        assertEquals(8 << 20, stream.next().stringValue().length());
        assertEquals("y", stream.next().stringValue());
      }
    });
  }

  /** Segments are bounded before they are copied: by the input limit, or by the stream's room. */
  @Test
  void segmentsAreBoundedBeforeCopying() {
    var threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    MemorySegment big = MemorySegment.ofArray(new byte[64 << 20]).fill((byte) 'x');
    try (YamlSession session = YamlJava.openSession();
        YamlStream stream = session.stream(new YamlStreamOptions(100, 64))) {
      long before = threads.getCurrentThreadAllocatedBytes();
      long accepted = stream.feed(big);
      long allocated = threads.getCurrentThreadAllocatedBytes() - before;
      assertTrue(accepted > 0 && accepted <= 64, "accepted " + accepted);
      assertTrue(allocated < (1 << 20), "allocated " + allocated);
    }
    YamlLimits small = new YamlLimits(16, 1 << 20, 1000, 1 << 20, 128, 10, 100);
    try (YamlSession session = YamlJava.openSession(YamlOptions.DEFAULT.withLimits(small))) {
      long before = threads.getCurrentThreadAllocatedBytes();
      assertThrows(IllegalArgumentException.class,
          () -> session.parse(big, big.byteSize(), new dev.elide.truffledata.tape.JavaSink()));
      assertTrue(threads.getCurrentThreadAllocatedBytes() - before < (1 << 20));
    }
  }
}
