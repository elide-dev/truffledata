package dev.elide.truffledata.json;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.myna.truffle.NativeCellException;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.json.cells.generated.JsonCellsJvmTruffle;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The JSON cells route on HotSpot ({@code -Dtruffledata.json.cells}, default off). */
final class CellsRouteTest {
  static Path runfile(String path) {
    return Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), path);
  }

  static Path library() {
    return runfile("json/native/json_shared.so");
  }

  /** The cells a decoder bound when it opened; {@code null} on the baseline route. */
  static CellsFfm cells(JsonDecoder decoder) throws ReflectiveOperationException {
    var field = JsonJvm.class.getDeclaredField("cells");
    field.setAccessible(true);
    return (CellsFfm) field.get(decoder);
  }

  /**
   * The flag is read once and selects the route; the lane states which one it expects. With the
   * flag on, a decode and the release of its result each go through a cell, exactly once.
   */
  @Test
  void routeFollowsTheFlag() throws Exception {
    boolean expected = Boolean.getBoolean("truffledata.json.cells.expected");
    assertEquals(expected, JsonCellsRoute.ENABLED);
    assertEquals(expected, Boolean.getBoolean(JsonCellsRoute.PROPERTY));
    byte[] document = "{\"a\":[1,true]}".getBytes(StandardCharsets.UTF_8);
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      CellsFfm cells = cells(decoder);
      assertEquals(expected, cells != null);
      try (JsonMessage message = decoder.decode(document)) {
        assertTrue(CellsInputs.render(message).startsWith("ok "));
        if (cells != null) assertArrayEquals(new long[] {1, 0}, cells.calls());
      }
      if (cells != null) assertArrayEquals(new long[] {1, 1}, cells.calls());
      // A refused document's result is released through the cell as well.
      assertThrows(
          IllegalArgumentException.class,
          () -> decoder.decode("[1,".getBytes(StandardCharsets.UTF_8)));
      if (cells != null) assertArrayEquals(new long[] {2, 2}, cells.calls());
    }
    // A property change after initialization does not move the route.
    String previous = System.getProperty(JsonCellsRoute.PROPERTY);
    try {
      System.setProperty(JsonCellsRoute.PROPERTY, Boolean.toString(!expected));
      try (JsonDecoder decoder = JsonJvm.open(library())) {
        assertEquals(expected, cells(decoder) != null);
      }
    } finally {
      if (previous == null) System.clearProperty(JsonCellsRoute.PROPERTY);
      else System.setProperty(JsonCellsRoute.PROPERTY, previous);
    }
  }

  /** Same outcomes, messages and errors as the production FFM binding, over corpus and fuzz. */
  @Test
  void parityWithTheBaseline() throws Exception {
    int[] counts = CellsParity.jvm(library());
    System.out.println("JSON cells parity: " + counts[0] + " ok, " + counts[1] + " errors");
  }

  /** Cells bind one library per class loader; a different path is refused, never rerouted. */
  @Test
  void secondLibraryIsRejected() {
    new CellsFfm(library());
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> new CellsFfm(runfile("json/native/other_abi_class.so")));
    assertTrue(failure.getMessage().contains("JSON cells are bound to"), failure.getMessage());
  }

  /**
   * Strict coercion: host {@code null} for a non-null pointer raises {@link NativeCellException}
   * before the native call, an ordinary guest error.
   */
  @Test
  void nullPointerIsACellError() {
    new CellsFfm(library());
    var cell = new JsonCellsJvmTruffle.decode_adopted_Cell();
    new NativeCellRootNode(cell).adoptChildren();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment result = arena.allocate(88, 8);
      Object[] arguments = {
        null, 2L, 66L, 1L << 20, 1L << 20, 1L << 20, 1L << 20, 128, NativePointer.of(result.address())
      };
      assertThrows(NativeCellException.class, () -> cell.executeInt(arguments));
      // An out-of-range u32 (max_depth) is refused too, not truncated.
      Object[] negative = {
        NativePointer.of(result.address()), 0L, 0L, 1L, 1L, 1L, 1L, -1, NativePointer.of(result.address())
      };
      assertThrows(NativeCellException.class, () -> cell.executeInt(negative));
    }
  }

  /**
   * Brief item 2: every scalar-result cell is held as its concrete generated type and called through
   * its typed method; the generic {@code execute(Object[])} only for the void {@code release}. A
   * source scan of both backends fails if a scalar cell call is routed through {@code execute}.
   */
  @Test
  void scalarCellsUseTypedCalls() throws Exception {
    Pattern field = Pattern.compile("private final (\\S+) (\\w+);");
    Pattern call = Pattern.compile("(\\w+)\\.(execute\\w*)\\(");
    for (String source : List.of("CellsFfm.java", "CellsSvm.java")) {
      String text =
          Files.readString(
              runfile("json/cells/src/main/java/dev/elide/truffledata/json/" + source));
      java.util.Map<String, String> cells = new java.util.HashMap<>();
      Matcher fields = field.matcher(text);
      while (fields.find())
        if (fields.group(1).endsWith("_Cell")) cells.put(fields.group(2), fields.group(1));
      assertFalse(cells.isEmpty(), source);
      assertFalse(text.contains("NativeCell decode"), source);
      int calls = 0;
      Matcher calling = call.matcher(text);
      while (calling.find()) {
        String type = cells.get(calling.group(1));
        if (type == null) continue;
        calls++;
        String expected = type.endsWith(".release_Cell") ? "execute" : "executeInt";
        assertEquals(expected, calling.group(2), source + ": " + calling.group());
      }
      assertEquals(cells.size(), calls, source + " calls every cell exactly once");
      assertFalse(text.contains("catch (UnexpectedResultException"), source);
    }
  }
}
