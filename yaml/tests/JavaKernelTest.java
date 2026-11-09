package dev.elide.truffledata.yaml.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.tape.TapeValue;
import dev.elide.truffledata.yaml.*;
import dev.elide.truffledata.yaml.events.YamlEventSource;
import dev.elide.truffledata.yaml.events.YamlMark;
import dev.elide.truffledata.yaml.events.YamlScalarStyle;
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
}
