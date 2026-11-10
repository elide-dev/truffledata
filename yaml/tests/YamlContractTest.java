package dev.elide.truffledata.yaml;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class YamlContractTest {
  /**
   * The cells route without its artifact (this suite's class path has no truffledata-yaml-cells):
   * it fails, never falling back to the FFM backend, and releases the backend it was given, which
   * lets the library go.
   */
  @Test
  void cellsRouteWithoutItsArtifactFailsAndReleasesTheBackend() {
    assertFalse(CellsRoute.ENABLED);
    java.nio.file.Path library =
        java.nio.file.Path.of(
            System.getenv("TEST_SRCDIR"),
            System.getenv("TEST_WORKSPACE"),
            "yaml/native/yaml_shared.so");
    java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined();
    JvmSessionBackend backend =
        new JvmSessionBackend(
            YamlOptions.DEFAULT,
            new dev.elide.truffledata.yaml.generated.YamlFFM.LibraryBinding(
                java.lang.foreign.SymbolLookup.libraryLookup(library.toAbsolutePath(), arena)),
            arena);
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> CellsRoute.jvm(backend, library));
    assertEquals(
        "-Dtruffledata.yaml.cells=true needs truffledata-yaml-cells on the class path",
        failure.getMessage());
    assertInstanceOf(ClassNotFoundException.class, failure.getCause());
    assertFalse(arena.scope().isAlive());
  }

  @Test
  void enumOrdinalsMatchNativePolicies() {
    assertArrayEquals(
        new YamlSchema[] {
          YamlSchema.CORE, YamlSchema.JSON, YamlSchema.FAILSAFE, YamlSchema.YAML_1_1
        },
        YamlSchema.values());
    assertArrayEquals(
        new DuplicateKeys[] {DuplicateKeys.REJECT, DuplicateKeys.LAST_WINS},
        DuplicateKeys.values());
    assertArrayEquals(new Aliases[] {Aliases.ALLOW, Aliases.REJECT}, Aliases.values());
    assertArrayEquals(
        new UnknownTags[] {UnknownTags.PRESERVE, UnknownTags.REJECT}, UnknownTags.values());
    assertArrayEquals(
        new VersionDirective[] {VersionDirective.IGNORE, VersionDirective.HONOR},
        VersionDirective.values());
    assertArrayEquals(
        new Style[] {
          Style.PLAIN,
          Style.SINGLE,
          Style.DOUBLE,
          Style.LITERAL,
          Style.FOLDED,
          Style.BLOCK,
          Style.FLOW
        },
        Style.values());
    assertArrayEquals(
        new KeyKind[] {KeyKind.STRING, KeyKind.NULL, KeyKind.BOOL, KeyKind.INT, KeyKind.FLOAT},
        KeyKind.values());
    for (YamlSchema value : YamlSchema.values())
      assertSame(value, YamlSchema.valueOf(value.name()));
    for (DuplicateKeys value : DuplicateKeys.values())
      assertSame(value, DuplicateKeys.valueOf(value.name()));
    for (Aliases value : Aliases.values()) assertSame(value, Aliases.valueOf(value.name()));
    for (UnknownTags value : UnknownTags.values())
      assertSame(value, UnknownTags.valueOf(value.name()));
    for (VersionDirective value : VersionDirective.values())
      assertSame(value, VersionDirective.valueOf(value.name()));
    for (Style value : Style.values()) assertSame(value, Style.valueOf(value.name()));
    for (KeyKind value : KeyKind.values()) assertSame(value, KeyKind.valueOf(value.name()));
  }

  @Test
  void optionRecordsAndDirectiveMapsAreImmutableValues() {
    Map<String, String> mutable = new LinkedHashMap<>();
    mutable.put("!e!", "tag:example.com,2026:");
    YamlDirectives directives = new YamlDirectives(12, mutable);
    mutable.clear();
    YamlDirectives copy = new YamlDirectives(12, Map.of("!e!", "tag:example.com,2026:"));
    assertEquals(copy, directives);
    assertEquals(copy.hashCode(), directives.hashCode());
    assertTrue(directives.toString().contains("!e!"));
    assertThrows(UnsupportedOperationException.class, () -> directives.tags().clear());
    YamlLimits limits =
        new YamlLimits(64L << 20, 64L << 20, 1_000_000, 64L << 20, 128, 10_000, 1_000_000);
    assertEquals(YamlLimits.DEFAULT, limits);
    assertEquals(YamlLimits.DEFAULT.hashCode(), limits.hashCode());
    assertTrue(limits.toString().contains("maxDepth=128"));
    assertThrows(IllegalArgumentException.class, () -> new YamlLimits(0, 1, 1, 1, 1, 0, 1));
    YamlOptions options = YamlOptions.DEFAULT.withLimits(limits);
    assertEquals(YamlOptions.DEFAULT, options);
    assertEquals(YamlOptions.DEFAULT.hashCode(), options.hashCode());
    assertTrue(options.toString().contains("schema=CORE"));
    YamlStreamOptions stream = new YamlStreamOptions(1 << 20, 1 << 20);
    assertEquals(YamlStreamOptions.DEFAULT, stream);
    assertEquals(YamlStreamOptions.DEFAULT.hashCode(), stream.hashCode());
    assertTrue(stream.toString().contains("bufferBytes="));
    assertThrows(IllegalArgumentException.class, () -> new YamlStreamOptions(0, 1));
  }

  @Test
  void nativeCallFailuresKeepTheirCauseAndSeverity() {
    var checked = new IOException("native failure");
    var wrapped = YamlJvm.callFailure(checked);
    assertInstanceOf(IllegalStateException.class, wrapped);
    assertSame(checked, wrapped.getCause());
    var runtime = new IllegalArgumentException("bad input");
    assertSame(runtime, YamlJvm.callFailure(runtime));
    var error = new OutOfMemoryError("native allocation");
    assertSame(error, assertThrows(OutOfMemoryError.class, () -> YamlJvm.callFailure(error)));
    assertThrows(UnsupportedOperationException.class, YamlSvm::open);
    assertThrows(UnsupportedOperationException.class, () -> YamlSvm.decode(new byte[0]));
    assertThrows(
        UnsupportedOperationException.class,
        () -> YamlSvm.decode(new byte[0], YamlOptions.DEFAULT));
  }
}
