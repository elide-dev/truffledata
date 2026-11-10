package dev.elide.truffledata.yaml.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.TargetAbi;
import dev.elide.truffledata.tape.JavaSink;
import dev.elide.truffledata.tape.TapeSink;
import dev.elide.truffledata.tape.TapeText;
import dev.elide.truffledata.yaml.*;
import dev.elide.truffledata.yaml.generated.YamlAbi;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.CoreSchema;

final class YamlIntegrationTest {
  static Path library() {
    String override = System.getProperty("yaml.library");
    return override != null ? Path.of(override) : runfile("yaml/native/yaml_shared.so");
  }

  static Path runfile(String path) {
    return Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), path);
  }

  static String fingerprintOf(String abiClass) throws Exception {
    return Files.readString(runfile("yaml/native/generated/abi/" + abiClass + "/seam.abi")).strip();
  }

  /** Myna 0.14.0: the bindings carry each ABI class's fingerprint; Linux AArch64 selects its own. */
  @Test
  void abiClassFingerprintsFollowTheGeneratedClasses() throws Exception {
    assertEquals(fingerprintOf("lp64_little"), YamlAbi.LP64_LITTLE);
    assertEquals(fingerprintOf("lp64_little_aapcs64"), YamlAbi.LP64_LITTLE_AAPCS64);
    assertNotEquals(YamlAbi.LP64_LITTLE, YamlAbi.LP64_LITTLE_AAPCS64);
    assertEquals(
        YamlAbi.LP64_LITTLE_AAPCS64,
        YamlAbi.fingerprint(TargetAbi.of("Linux", "aarch64").charUnsigned()));
    assertEquals(
        YamlAbi.LP64_LITTLE, YamlAbi.fingerprint(TargetAbi.of("Linux", "amd64").charUnsigned()));
    assertEquals(
        YamlAbi.LP64_LITTLE,
        YamlAbi.fingerprint(TargetAbi.of("Mac OS X", "aarch64").charUnsigned()));
  }

  /**
   * A library of the other ABI class (Linux AArch64's fingerprint here, the shared x86-64/Apple one
   * there) is rejected before any call is bound, never loaded as a fallback; this host's is accepted.
   */
  @Test
  void otherAbiClassLibraryIsRejected() {
    Path other = runfile("yaml/native/other_abi_class.so");
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> YamlJvm.openSession(other));
    assertTrue(failure.getMessage().contains("ABI fingerprint mismatch"), failure.getMessage());
    try (YamlSession accepted = YamlJvm.openSession(library())) {
      assertNotNull(accepted);
    }
  }

  /** #40: the default (fused) duplicate check reports the repeated key, not the mapping's close. */
  @Test
  void duplicateKeysReportTheRepeatedKey() {
    String[][] cases = {
      {"a: 1\na: 2\n", "duplicate YAML key: a at line 2, column 1"},
      {"x: 0\na: 1\na: 2\nz: 3\n", "duplicate YAML key: a at line 3, column 1"},
      {"m:\n  k: 1\n  k: 2\nn: 3\n", "duplicate YAML key: k at line 3, column 3"},
    };
    for (YamlOptions options :
        new YamlOptions[] {YamlOptions.DEFAULT, YamlOptions.DEFAULT.withMergeKeys(true)}) {
      try (YamlSession session = YamlJvm.openSession(library(), options)) {
        for (String[] c : cases) {
          IllegalArgumentException failure =
              assertThrows(
                  IllegalArgumentException.class,
                  () -> session.validate(c[0].getBytes(java.nio.charset.StandardCharsets.UTF_8)));
          assertEquals("YAML syntax error: " + c[1], failure.getMessage(), c[0]);
        }
        // The session still decodes after a relocated failure.
        assertEquals(2L, (long) session.with(SvmAcceptance.bytes("a: 1\nb: 2\n"), v -> v.size()));
      }
    }
  }

  @Test
  void sharedAcceptance() {
    try (YamlSession session = YamlJvm.openSession(library())) {
      SvmAcceptance.verify(session);
    }
  }

  @Test
  void retainedMessageKeepsLibraryLoaded() throws ReflectiveOperationException {
    YamlMessage retained;
    try (YamlSession session = YamlJvm.openSession(library())) {
      retained = session.decode(SvmAcceptance.bytes("hello: world"));
    }
    try (retained) {
      // The class-constant compiled hook must agree with this owner's interpreter policy.
      var confined = YamlMessage.class.getDeclaredMethod("confined");
      confined.setAccessible(true);
      assertEquals(true, confined.invoke(retained));
      assertEquals("world", retained.root().member("hello").stringValue());
    }
    assertThrows(IllegalStateException.class, retained::root);
  }

  @Test
  void snapshotKeepsPristineSource() {
    dev.elide.truffledata.tape.TapeSnapshot snapshot;
    try (YamlSession session =
            YamlJvm.openSession(library(), YamlOptions.DEFAULT.retainSource(true));
        YamlMessage message = session.decode(SvmAcceptance.bytes("x: \"hello\\nworld\"\n"))) {
      snapshot = message.snapshot();
    }
    assertEquals("\"hello\\nworld\"", snapshot.root().member("x").rawText());
  }

  @Test
  void policies() {
    try (YamlSession session = YamlJvm.openSession(library(), YamlOptions.yaml11());
        YamlMessage message = session.decode(SvmAcceptance.bytes("on: yes\nx: 0755\n"))) {
      assertTrue(message.root().member("true").booleanValue());
      assertEquals(493, message.root().member("x").longValue());
    }
    try (YamlSession session = YamlJvm.openSession(library(), YamlOptions.json())) {
      assertThrows(IllegalArgumentException.class, () -> session.decode(SvmAcceptance.bytes(".5")));
    }
    try (YamlSession session =
            YamlJvm.openSession(
                library(), YamlOptions.DEFAULT.withDuplicateKeys(DuplicateKeys.LAST_WINS));
        YamlMessage message = session.decode(SvmAcceptance.bytes("x: 1\nx: 2\n"))) {
      assertEquals(2, message.root().member("x").longValue());
    }
  }

  @Test
  void coreTreesMatchSnakeYaml() {
    Load reference =
        new Load(
            LoadSettings.builder()
                .setSchema(new CoreSchema())
                .setAllowDuplicateKeys(false)
                .build());
    try (YamlSession session = YamlJvm.openSession(library())) {
      for (int i = 0; i < 200; i++) {
        String input =
            "name: item-"
                + i
                + "\nactive: "
                + (i % 2 == 0)
                + "\nvalues: [one, two, null]\nextra: {nested: text}\n";
        JavaSink sink = new JavaSink();
        session.parse(SvmAcceptance.bytes(input), sink);
        assertEquals(reference.loadFromString(input), sink.result());
      }
    }
  }

  @Test
  void aliasExpansionIsBounded() {
    YamlLimits limits = new YamlLimits(65536, 65536, 1000, 65536, 128, 100, 12);
    try (YamlSession session =
            YamlJvm.openSession(library(), YamlOptions.DEFAULT.withLimits(limits));
        YamlMessage message =
            session.decode(
                SvmAcceptance.bytes(
                    "a: &a [one, two, three]\nb: &b [*a, *a, *a]\nc: [*b, *b, *b]\n"))) {
      JavaSink identity = new JavaSink();
      message.export(identity);
      assertNotNull(identity.result());
      TapeSink expanding =
          new TapeSink() {
            public void beginObject(long n) {}

            public void key(TapeText t) {}

            public void beginArray(long n) {}

            public void end() {}

            public void nullValue() {}

            public void booleanValue(boolean b) {}

            public void longValue(long l) {}

            public void doubleValue(double d) {}

            public void unsignedValue(long l) {}

            public void decimalValue(TapeText t) {}

            public void stringValue(TapeText t) {}
          };
      assertThrows(IllegalArgumentException.class, () -> message.export(expanding));
    }
  }

  @Test
  void metadataSurvivesAliasReadsAndSnapshots() throws Exception {
    dev.elide.truffledata.tape.TapeSnapshot snapshot;
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message =
            session.decode(
                SvmAcceptance.bytes(
                    "a: &bytes !!binary SGVsbG8=\n"
                        + "b: *bytes\n"
                        + "12: 'quoted'\n"
                        + "date: !!timestamp 2001-12-14\n"
                        + "time: !!timestamp 2001-12-14 21:59:43.1 -5\n"))) {
      var root = message.root();
      assertEquals("bytes", Yaml.anchor(root.member("a")));
      assertFalse(Yaml.isAlias(root.member("a")));
      assertTrue(Yaml.isAlias(root.member("b")));
      assertTrue(Yaml.isAlias(root.memberAt(1)));
      Object alias =
          com.oracle.truffle.api.interop.InteropLibrary.getUncached().readMember(root, "b");
      assertTrue(Yaml.isAlias((dev.elide.truffledata.tape.TapeValue) alias));
      // An adopted cached site folds the owner's confinement policy before its field checks.
      var cachedSite = new CachedMemberRead().getCallTarget();
      assertTrue(Yaml.isAlias((dev.elide.truffledata.tape.TapeValue) cachedSite.call(root, "b")));
      assertTrue(Yaml.isAlias((dev.elide.truffledata.tape.TapeValue) cachedSite.call(root, "b")));
      assertArrayEquals(SvmAcceptance.bytes("Hello"), Yaml.binary(root.member("b")));
      assertEquals(KeyKind.INT, Yaml.keyKind(root, 2));
      assertEquals(Style.SINGLE, Yaml.style(root.member("12")));
      assertEquals(java.time.LocalDate.of(2001, 12, 14), Yaml.timestamp(root.member("date")));
      assertEquals(
          java.time.OffsetDateTime.parse("2001-12-14T21:59:43.100-05:00"),
          Yaml.timestamp(root.member("time")));
      snapshot = message.snapshot();
    }
    assertTrue(Yaml.isAlias(snapshot.root().member("b")));
    assertEquals("bytes", Yaml.anchor(snapshot.root().member("a")));
    assertArrayEquals(SvmAcceptance.bytes("Hello"), Yaml.binary(snapshot.root().member("b")));
  }

  @Test
  void aliasFreeReadsKeepTagAnchorAndNumberSemantics() {
    var export = dev.elide.truffledata.tape.TapeExportNode.create();
    try (YamlSession session = YamlJvm.openSession(library())) {
      for (String input : new String[] {"[&a !!int 0x1F]", "[&a !!int 0x1F, *a]"}) {
        try (YamlMessage message = session.decode(SvmAcceptance.bytes(input))) {
          var value = message.root().element(0);
          assertEquals("31", value.numberText());
          assertEquals("a", Yaml.anchor(value));
          assertEquals("tag:yaml.org,2002:int", Yaml.tag(value));
          var events = new java.util.ArrayList<String>();
          TapeSink sink = new TapeSink() {
            public void beginObject(long n) {}
            public void key(TapeText t) {}
            public void beginArray(long n) {}
            public void end() {}
            public void nullValue() {}
            public void booleanValue(boolean b) {}
            public void longValue(long n) { events.add("int:" + n); }
            public void doubleValue(double n) {}
            public void unsignedValue(long n) {}
            public void decimalValue(TapeText t) {}
            public void stringValue(TapeText t) {}
            public void tag(TapeText t) { events.add("tag:" + t.toJavaString()); }
            public void anchor(int id) { events.add("anchor:" + id); }
          };
          export.execute(message.root(), sink);
          assertTrue(events.contains("tag:tag:yaml.org,2002:int"));
          assertTrue(events.contains("anchor:0"));
          assertTrue(events.contains("int:31"));
          assertEquals("31", message.snapshot().root().element(0).numberText());
        }
      }
    }
  }

  @Test
  void aliasArrayViewsKeepProvenance() throws Exception {
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(SvmAcceptance.bytes("[&a 42, *a, *a]"))) {
      var root = message.root();
      assertTrue(Yaml.isAlias(root.element(1)));
      var iterator = root.elements().iterator();
      iterator.next();
      assertTrue(Yaml.isAlias(iterator.next()));
      var interop = com.oracle.truffle.api.interop.InteropLibrary.getUncached();
      assertEquals(42, interop.asLong(interop.readArrayElement(root, 1)));
      assertTrue(
          Yaml.isAlias((dev.elide.truffledata.tape.TapeValue) interop.readArrayElement(root, 2)));
    }
  }

  @Test
  void filteredInputStringsSurviveSnapshotsMynaAndRetain() {
    String input = "prefix: ignore\nchild: {'ke''y': \"caf\\u00E9\\n\", short: 'it''s', long: 'decoded string longer than sixteen bytes'}\nblock: |\n  first line\n  second line\n";
    dev.elide.truffledata.tape.TapeSnapshot child;
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(SvmAcceptance.bytes(input))) {
      var value = message.root().member("child");
      assertEquals("café\n", value.member("ke'y").stringValue());
      assertThrows(IllegalStateException.class, () -> value.member("ke'y").rawText());
      child = value.snapshot();
      message.toMyna().validate();
      var sink = new JavaSink();
      dev.elide.truffledata.tape.TapeExportNode.create().execute(value, sink);
      assertEquals(java.util.Map.of("ke'y", "café\n", "short", "it's", "long", "decoded string longer than sixteen bytes"), sink.result());
      session.validate(SvmAcceptance.bytes("replacement: 'overwrites session input'"));
      assertEquals("first line\nsecond line\n", message.root().member("block").stringValue());
    }
    assertEquals("it's", child.root().member("short").stringValue());
    assertEquals("café\n", child.root().member("ke'y").stringValue());
    var myna = child.toMyna();
    myna.validate();
    assertEquals("decoded string longer than sixteen bytes", myna.stringValue(myna.find(0, dev.elide.myna.values.ValueTape.Utf8Key.of("long"))));
  }

  @Test
  void streamOptionsPreserveSourceAcrossIteratorAndSink() {
    try (YamlSession session = YamlJvm.openSession(library(), YamlOptions.DEFAULT.retainSource(true));
        YamlStream documents = session.documents(SvmAcceptance.bytes("--- {a: 'it''s', b: 2}\n--- {a: 'next', b: 3}\n"))) {
      var first = java.util.Objects.requireNonNull(documents.next());
      assertEquals("it's", first.member("a").stringValue());
      assertEquals("'it''s'", first.member("a").rawText());
      var snapshot = first.snapshot();
      var sink = new JavaSink();
      assertTrue(documents.next(sink));
      assertEquals(java.util.Map.of("a", "next", "b", 3L), sink.result());
      assertEquals("'it''s'", snapshot.root().member("a").rawText());
      assertNull(documents.next());
    }
  }

  @Test
  void completeDocumentsAndInputValidation() {
    try (YamlSession session = YamlJvm.openSession(library());
        YamlStream documents = session.documents(SvmAcceptance.bytes("---\none\n---\ntwo\n"))) {
      assertEquals("one", java.util.Objects.requireNonNull(documents.next()).stringValue());
      assertEquals("two", java.util.Objects.requireNonNull(documents.next()).stringValue());
      assertNull(documents.next());
    }
    try (YamlSession session = YamlJvm.openSession(library())) {
      assertThrows(
          IllegalArgumentException.class,
          () -> session.decode(new byte[] {(byte) 0xc0, (byte) 0xaf}));
      IllegalArgumentException failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> session.decode(SvmAcceptance.bytes("x: [one\n")));
      assertTrue(failure.getMessage().contains("line"));
      assertThrows(
          IllegalArgumentException.class, () -> session.decode(SvmAcceptance.bytes("a: &a [*a]")));
    }
  }

  @Test
  void nonfiniteInteropAndThreadConfinement() throws Exception {
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(SvmAcceptance.bytes("[.nan, .inf, -.inf]"))) {
      var interop = com.oracle.truffle.api.interop.InteropLibrary.getUncached();
      assertTrue(Double.isNaN(interop.asDouble(message.root().element(0))));
      assertEquals(Double.POSITIVE_INFINITY, interop.asDouble(message.root().element(1)));
      assertEquals(Double.NEGATIVE_INFINITY, interop.asDouble(message.root().element(2)));
      java.util.concurrent.atomic.AtomicReference<Throwable> failure =
          new java.util.concurrent.atomic.AtomicReference<>();
      Thread thread =
          new Thread(
              () -> {
                try {
                  message.root();
                } catch (Throwable t) {
                  failure.set(t);
                }
              });
      thread.start();
      thread.join();
      assertInstanceOf(IllegalStateException.class, failure.get());
    }
  }

  @Test
  void numberTextPreservesExactDecimalSpelling() {
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message =
            session.decode(
                SvmAcceptance.bytes(
                    "[1.234567890123456789, +.5000000000000000001, 1.e+4, 0x1F, -0, 1E400]"))) {
      String[] expected = {
        "1.234567890123456789", "0.5000000000000000001", "1.0e+4", "31", "-0", "1E400"
      };
      for (int i = 0; i < expected.length; i++)
        assertEquals(expected[i], message.root().element(i).numberText());
    }
  }

  @Test
  void nonfiniteFlagsSurviveMynaCopies() {
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(SvmAcceptance.bytes("[.nan, .inf, -.inf]"))) {
      message.toMyna().validate();
      message.snapshot().toMyna().validate();
    }
  }

  @Test
  void nativeSegmentsAndTruffleStrings() {
    byte[] input = SvmAcceptance.bytes("name: 'héllö 😀'\n");
    try (YamlSession session = YamlJvm.openSession(library());
        java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
      var segment = arena.allocate(input.length);
      java.lang.foreign.MemorySegment.copy(
          input, 0, segment, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, input.length);
      JavaSink nativeSink = new JavaSink();
      session.parse(segment, input.length, nativeSink);
      JavaSink textSink = new JavaSink();
      session.parse(
          com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
              "name: 'héllö 😀'\n", com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16),
          textSink);
      assertEquals(nativeSink.result(), textSink.result());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              session.parse(
                  java.lang.foreign.MemorySegment.ofArray(input), input.length, new JavaSink()));
      try (YamlStream stream = session.stream(YamlStreamOptions.DEFAULT)) {
        assertEquals(input.length, stream.feed(segment));
        stream.finish();
        JavaSink streamed = new JavaSink();
        assertTrue(stream.next(streamed));
        assertEquals(nativeSink.result(), streamed.result());
        assertFalse(stream.next(new JavaSink()));
        assertEquals(1, stream.documents());
        assertEquals(YamlStreamOptions.DEFAULT, stream.options());
      }
    }
  }

  @Test
  void retainedStreamDocumentOutlivesBothParents() {
    YamlMessage retained;
    try (YamlSession session = YamlJvm.openSession(library());
        YamlStream stream = session.documents(SvmAcceptance.bytes("answer: 42"))) {
      assertNotNull(stream.next());
      retained = stream.retain();
    }
    try (retained) {
      assertEquals(42, retained.root().member("answer").longValue());
    }
  }

  @Test
  void directivesAndExplicitPolicies() {
    YamlOptions configured =
        YamlOptions.DEFAULT
            .withSchema(YamlSchema.CORE)
            .withMergeKeys(true)
            .withRetainSource(true)
            .withVersionDirective(VersionDirective.IGNORE)
            .withUnknownTags(UnknownTags.PRESERVE)
            .withAliases(Aliases.ALLOW);
    try (YamlSession session = YamlJvm.openSession(library(), configured);
        YamlMessage message =
            session.decode(
                SvmAcceptance.bytes(
                    "%YAML 1.1\n%TAG !e! tag:example.com,2026:\n---\nx: !e!custom test\n"))) {
      assertEquals(configured, session.options());
      assertEquals("tag:example.com,2026:custom", Yaml.tag(message.root().member("x")));
      assertEquals("tag:example.com,2026:", message.directives().tags().get("!e!"));
      assertTrue(message.directives().version() != 0);
      assertNotNull(message.metadata());
    }
    try (YamlSession session =
        YamlJvm.openSession(library(), configured.withUnknownTags(UnknownTags.REJECT))) {
      assertThrows(
          IllegalArgumentException.class,
          () -> session.decode(SvmAcceptance.bytes("!unknown value")));
    }
    try (YamlSession session =
        YamlJvm.openSession(library(), configured.withAliases(Aliases.REJECT))) {
      assertThrows(
          IllegalArgumentException.class, () -> session.decode(SvmAcceptance.bytes("[&a 1, *a]")));
    }
    YamlLimits limits = new YamlLimits(65536, 65536, 2, 65536, 128, 100, 100);
    try (YamlSession session = YamlJvm.openSession(library(), configured.withLimits(limits))) {
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> session.decode(SvmAcceptance.bytes("[1, 2, 3]")));
      assertTrue(failure.getMessage().startsWith("YAML resource limit:"));
    }
  }

  @Test
  void guestSinkKeepsAliasIdentity() {
    try (YamlSession session = YamlJvm.openSession(library())) {
      var keys = new dev.elide.truffledata.tape.TruffleSink.KeyCache();
      var sink = new dev.elide.truffledata.tape.TruffleSink(keys);
      session.parse(SvmAcceptance.bytes("a: &a [héllö]\nb: *a\n"), sink);
      java.util.Map<?, ?> result = (java.util.Map<?, ?>) sink.result();
      var values = java.util.Objects.requireNonNull(result).values().iterator();
      assertSame(values.next(), values.next());
      assertEquals(2, keys.size());
      assertThrows(UnsupportedOperationException.class, YamlSvm::open);
    }
  }

  @Test
  void emptyNativeInputMatchesEmptyArray() {
    try (YamlSession session = YamlJvm.openSession(library());
        java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
      JavaSink sink = new JavaSink();
      session.parse(arena.allocate(1), 0, sink);
      assertNull(sink.result());
    }
  }

  @Test
  void retainedMetadataAndAssumptions() {
    com.oracle.truffle.api.Assumption assumption;
    try (YamlSession session = YamlJvm.openSession(library());
        YamlMessage message = session.decode(SvmAcceptance.bytes("a: &a !custom text\nb: *a\n"))) {
      assumption = message.assumption();
      assertTrue(assumption.isValid());
      assertSame(assumption, message.assumption());
      var metadata = message.metadata();
      assertEquals("a", metadata.anchor(3));
      assertNull(metadata.anchor(2));
      assertEquals(Yaml.tag(message.root().member("a")), metadata.tag(3));
      assertTrue(metadata.isAlias(5));
      assertNull(metadata.tag(5));
      assertEquals(0, metadata.style(3));
      assertEquals(0, metadata.keyKind(2));
    }
    assertFalse(assumption.isValid());
  }
  @Test
  void compactSubtreeSnapshotKeepsMetadataAndPristineSource() {
    dev.elide.truffledata.tape.TapeSnapshot snapshot;
    try (var session = YamlJvm.openSession(library(), YamlOptions.DEFAULT.withRetainSource(true));
        var message = session.decode(SvmAcceptance.bytes(
            "before: ignored\nchild:\n  tagged: !custom 'it''s'\n  a: &a {n: 17}\n  alias: *a\nafter: ignored\n"))) {
      snapshot = message.root().member("child").snapshot();
    }
    var value = snapshot.root();
    assertEquals("it's", value.member("tagged").stringValue());
    assertEquals("'it''s'", value.member("tagged").rawText());
    assertEquals("!custom", Yaml.tag(value.member("tagged")));
    assertTrue(Yaml.isAlias(value.member("alias")));
    assertEquals(17, value.member("alias").member("n").longValue());
  }


  /** Adopts a cached InteropLibrary site so exact-class owner specializations can execute. */
  private static final class CachedMemberRead extends com.oracle.truffle.api.nodes.RootNode {
    @Child private com.oracle.truffle.api.interop.InteropLibrary interop =
        com.oracle.truffle.api.interop.InteropLibrary.getFactory().createDispatched(3);

    CachedMemberRead() {
      super(null);
    }

    @Override
    public Object execute(com.oracle.truffle.api.frame.VirtualFrame frame) {
      Object[] args = frame.getArguments();
      try {
        if (!interop.isMemberReadable(args[0], (String) args[1])) throw new AssertionError("unreadable");
        return interop.readMember(args[0], (String) args[1]);
      } catch (com.oracle.truffle.api.interop.InteropException failure) {
        throw new AssertionError(failure);
      }
    }
  }
}
