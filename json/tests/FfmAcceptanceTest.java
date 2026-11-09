package dev.elide.truffledata.json.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.json.generated.JsonFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FfmAcceptanceTest {
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
      assertEquals(JsonFFM.ABI_FINGERPRINT, fingerprint.reinterpret(65).getString(0));
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
