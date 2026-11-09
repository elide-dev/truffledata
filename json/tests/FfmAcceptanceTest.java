package dev.elide.truffledata.json.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.TargetAbi;
import dev.elide.truffledata.json.JsonDecoder;
import dev.elide.truffledata.json.JsonJvm;
import dev.elide.truffledata.json.generated.JsonAbi;
import dev.elide.truffledata.json.generated.JsonFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FfmAcceptanceTest {
  static Path runfile(String path) {
    return Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), path);
  }

  static String fingerprintOf(String abiClass) throws Exception {
    return Files.readString(runfile("json/native/generated/abi/" + abiClass + "/seam.abi")).strip();
  }

  /** Myna 0.14.0: the bindings carry each ABI class's fingerprint; Linux AArch64 selects its own. */
  @Test
  void abiClassFingerprintsFollowTheGeneratedClasses() throws Exception {
    assertEquals(fingerprintOf("lp64_little"), JsonAbi.LP64_LITTLE);
    assertEquals(fingerprintOf("lp64_little_aapcs64"), JsonAbi.LP64_LITTLE_AAPCS64);
    assertNotEquals(JsonAbi.LP64_LITTLE, JsonAbi.LP64_LITTLE_AAPCS64);
    assertEquals(
        JsonAbi.LP64_LITTLE_AAPCS64,
        JsonAbi.fingerprint(TargetAbi.of("Linux", "aarch64").charUnsigned()));
    assertEquals(
        JsonAbi.LP64_LITTLE, JsonAbi.fingerprint(TargetAbi.of("Linux", "amd64").charUnsigned()));
    assertEquals(
        JsonAbi.LP64_LITTLE,
        JsonAbi.fingerprint(TargetAbi.of("Mac OS X", "aarch64").charUnsigned()));
  }

  /**
   * A library of the other ABI class (Linux AArch64's fingerprint here, the shared x86-64/Apple one
   * there) is rejected before any symbol is bound, never loaded as a fallback; this host's is accepted.
   */
  @Test
  void otherAbiClassLibraryIsRejected() throws Exception {
    Path other = runfile("json/native/other_abi_class.so");
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> JsonJvm.open(other));
    assertTrue(failure.getMessage().contains("ABI fingerprint mismatch"), failure.getMessage());
    try (JsonDecoder accepted = JsonJvm.open(runfile("json/native/json_shared.so"))) {
      assertNotNull(accepted);
    }
  }

  @Test
  void compilerInternalsAreNotOnTheApplicationClasspath() {
    assertThrows(
        ClassNotFoundException.class, () -> Class.forName("com.oracle.svm.shared.AlwaysInline"));
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("com.oracle.svm.guest.staging.c.CGlobalData"));
  }

  @Test
  void staticBackendRejectsJvmBeforeNativeAccess() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> dev.elide.truffledata.json.JsonSvm.decode(new byte[] {'{', '}'}));
  }

  @Test
  void generatedBindingsRetainNativeResultAfterInputCloses() throws Throwable {
    Path library =
        Path.of(
            System.getenv("TEST_SRCDIR"),
            System.getenv("TEST_WORKSPACE"),
            "json/native/json_shared.so");
    try (Arena owner = Arena.ofConfined()) {
      SymbolLookup lookup = SymbolLookup.libraryLookup(library, owner);
      MethodHandle decode = JsonFFM.decode(lookup);
      MethodHandle release = JsonFFM.release(lookup);
      MemorySegment fingerprint = (MemorySegment) JsonFFM.fingerprint(lookup).invokeExact();
      assertEquals(
          JsonAbi.fingerprint(TargetAbi.HOST.charUnsigned()),
          fingerprint.reinterpret(65).getString(0));
      MemorySegment result = owner.allocate(JsonFFM.JsonResult_LAYOUT);
      try {
        String source = "{\"n\":1e999999999999999999,\"s\":\"long escaped \\u4e2d text\"}";
        try (Arena inputOwner = Arena.ofConfined()) {
          byte[] utf8 = source.getBytes(StandardCharsets.UTF_8);
          MemorySegment input = inputOwner.allocateFrom(ValueLayout.JAVA_BYTE, utf8);
          assertEquals(
              0,
              (int)
                  decode.invokeExact(
                      input,
                      (long) utf8.length,
                      1048576L,
                      1048576L,
                      10000L,
                      1048576L,
                      128,
                      result));
          input.fill((byte) 'x');
        }
        MemorySegment view =
            result.get(ValueLayout.ADDRESS, JsonFFM.JsonResult_view_OFFSET).reinterpret(88);
        assertEquals(1, view.get(ValueLayout.JAVA_INT, 0));
        assertEquals(7, view.get(ValueLayout.JAVA_LONG, 16));
        assertEquals(2, view.get(ValueLayout.JAVA_LONG, 64));
        assertEquals(0, view.get(ValueLayout.JAVA_LONG, 80));
        var tape = new dev.elide.truffledata.tape.SegmentTape(view);
        var borrowed = new dev.elide.truffledata.tape.TapeBorrow(tape, dev.elide.truffledata.tape.TapeFormat.of("JSON"));
        assertEquals("1e999999999999999999", borrowed.root().member("n").numberText());
        assertEquals("long escaped 中 text", borrowed.root().member("s").stringValue());
        borrowed.toMyna().validate();

      } finally {
        release.invokeExact(result);
      }
      release.invokeExact(result);
      assertEquals(0, result.get(ValueLayout.ADDRESS, JsonFFM.JsonResult_owner_OFFSET).address());
      try (Arena inputOwner = Arena.ofConfined()) {
        MemorySegment input = inputOwner.allocateFrom("[1,]");
        assertEquals(
            4,
            (int) decode.invokeExact(input, 4L, 1048576L, 1048576L, 10000L, 1048576L, 128, result));
        assertEquals(0, result.get(ValueLayout.ADDRESS, JsonFFM.JsonResult_view_OFFSET).address());
      }
    }
  }
}
