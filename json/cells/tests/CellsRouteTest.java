package dev.elide.truffledata.json;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellException;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.json.cells.generated.JsonCellsJvmTruffle;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
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
   * The call shape of both routes (Myna 0.16.0). The Native Image route is host code compiled ahead
   * of time, and the HotSpot route lends only native segments it has scope-checked, so each calls
   * the generated cells' static {@code invoke} (native carriers and raw addresses, nothing
   * allocated) for decode, adopted decode and release, and neither holds a cell, builds an argument
   * array, boxes a carrier or makes a {@code NativePointer}. No {@code execute} call of any kind
   * remains on either route. The HotSpot route keeps its explicit scope check, ahead of the call.
   */
  @Test
  void routesCallTheCellsInvokeEntry() throws Exception {
    Map<String, String> generated =
        Map.of(
            "CellsSvm.java", "dev.elide.truffledata.json.cells.generated.JsonCellsTruffle",
            "CellsFfm.java", "dev.elide.truffledata.json.cells.generated.JsonCellsJvmTruffle");
    for (var entry : generated.entrySet()) {
      String source = entry.getKey();
      Class<?> route =
          Class.forName("dev.elide.truffledata.json." + source.replace(".java", ""), false, getClass().getClassLoader());
      for (Field field : route.getDeclaredFields())
        assertFalse(NativeCell.class.isAssignableFrom(field.getType()), "route holds no cell: " + field);
      assertEquals(0, route.getDeclaredClasses().length, route + " nests no holder");
      String text =
          Files.readString(
              runfile("json/cells/src/main/java/dev/elide/truffledata/json/" + source));
      // Comments describe execute; only code is scanned.
      String code = text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
      for (String forbidden :
          List.of(
              ".execute",
              "new Object[",
              "NativePointer",
              "NativeCell",
              "Borrow",
              "Long.valueOf",
              "Integer.valueOf",
              "catch (UnexpectedResultException"))
        assertFalse(code.contains(forbidden), source + " uses " + forbidden);
      String owner = entry.getValue().substring(entry.getValue().lastIndexOf('.') + 1);
      List<String> cells =
          source.equals("CellsSvm.java")
              ? List.of("decode_Cell", "decode_adopted_Cell", "release_Cell")
              : List.of("decode_adopted_Cell", "release_Cell");
      for (String cell : cells)
        assertEquals(
            1,
            code.split(Pattern.quote(owner + "." + cell + ".invoke("), -1).length - 1,
            source + " must call " + cell + ".invoke exactly once");
      // Each called cell declares the static entry with exactly the carriers the route passes.
      Class<?> holder = Class.forName(entry.getValue(), false, route.getClassLoader());
      for (Class<?> cell : holder.getDeclaredClasses()) {
        if (!cells.contains(cell.getSimpleName())) continue;
        Class<?>[] expected =
            switch (cell.getSimpleName()) {
              case "decode_Cell" ->
                  new Class<?>[] {long.class, long.class, long.class, long.class, long.class, long.class, int.class, long.class};
              case "decode_adopted_Cell" ->
                  new Class<?>[] {
                    long.class, long.class, long.class, long.class, long.class, long.class, long.class, int.class, long.class
                  };
              default -> new Class<?>[] {long.class};
            };
        Method invoke = cell.getDeclaredMethod("invoke", expected);
        assertTrue(Modifier.isStatic(invoke.getModifiers()), invoke.toString());
        assertEquals(
            cell.getSimpleName().equals("release_Cell") ? void.class : int.class,
            invoke.getReturnType(),
            invoke.toString());
      }
    }
    // The HotSpot route restores the downcall's scope check on the caller's segment, before the
    // cell call (JvmScopedTest#adoptingAnotherThreadsConfinedSegmentFailsInTheDowncall).
    String ffm =
        Files.readString(
            runfile("json/cells/src/main/java/dev/elide/truffledata/json/CellsFfm.java"));
    int check = ffm.indexOf("data.get(ValueLayout.JAVA_BYTE, 0);");
    assertTrue(check > 0, "CellsFfm scope check");
    assertTrue(check < ffm.indexOf("decode_adopted_Cell.invoke("), "scope check precedes the call");
  }
}
