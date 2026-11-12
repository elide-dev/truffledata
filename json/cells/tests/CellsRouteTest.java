package dev.elide.truffledata.json;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.myna.truffle.HeapBytes;
import dev.elide.myna.truffle.NativeBytes;
import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellException;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.myna.truffle.NativeResource;
import dev.elide.myna.truffle.NativeStatusException;
import dev.elide.truffledata.json.cells.generated.JsonCellsJvmTruffle;
import dev.elide.truffledata.json.generated.JsonFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.HexFormat;
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
    var cell = adopt(new JsonCellsJvmTruffle.decode_adopted_Cell());
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment result = arena.allocate(88, 8);
      // decode_adopted's input is a held borrow (json/cells/ownership.seam): the guest passes the
      // buffer, whose size is the capacity, and the length.
      Object[] arguments = {
        null, 2L, 1L << 20, 1L << 20, 1L << 20, 1L << 20, 128, NativeBytes.of(result, true)
      };
      assertThrows(NativeCellException.class, () -> cell.execute(arguments));
      // A bare pointer has no size to derive, so it is refused too.
      Object[] pointer = {
        NativePointer.of(result.address()), 2L, 1L << 20, 1L << 20, 1L << 20, 1L << 20, 128,
        NativeBytes.of(result, true)
      };
      assertThrows(NativeCellException.class, () -> cell.execute(pointer));
      // An out-of-range u32 (max_depth) is refused too, not truncated.
      Object[] negative = {
        NativeBytes.of(result, false), 0L, 1L, 1L, 1L, 1L, -1, NativeBytes.of(result, true)
      };
      assertThrows(NativeCellException.class, () -> cell.execute(negative));
    }
  }

  /**
   * The call shape of both routes (Myna 0.16.0). The Native Image route is host code compiled ahead
   * of time, and the HotSpot route lends only native segments it has scope-checked, so each calls
   * the generated cells' static {@code invoke} (native carriers and raw addresses, nothing
   * allocated) for decode and adopted decode, and neither holds a cell, builds an argument array,
   * boxes a carrier or makes a {@code NativePointer}. No {@code execute} call of any kind remains on
   * either route. The HotSpot route keeps its explicit scope check, ahead of the call.
   *
   * <p>Release is the one routed call without {@code invoke}: since Myna 0.18.0 it is the destroy of
   * the {@code JsonResult} resource (json/cells/ownership.seam), and a destroy cell has no static
   * entry (its {@code execute} takes only an owner its create returned). Each route calls the same
   * cells class's raw binding instead, exactly once: {@code JsonCells.release} (the derived
   * {@code @CFunction}, what the former {@code invoke} called) and {@code JsonCellsJvmFFM.release}
   * (the FFM downcall, given the result segment itself).
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
              ? List.of("decode_Cell", "decode_adopted_Cell")
              : List.of("decode_adopted_Cell");
      for (String cell : cells)
        assertEquals(
            1,
            code.split(Pattern.quote(owner + "." + cell + ".invoke("), -1).length - 1,
            source + " must call " + cell + ".invoke exactly once");
      assertFalse(code.contains("release_Cell"), source + " calls the release cell");
      String binding = source.equals("CellsSvm.java") ? "JsonCells.release(" : "JsonCellsJvmFFM.release(";
      assertEquals(
          1,
          code.split(Pattern.quote(binding), -1).length - 1,
          source + " must call " + binding + ") exactly once");
      // Each called cell declares the static entry with exactly the carriers the route passes.
      Class<?> holder = Class.forName(entry.getValue(), false, route.getClassLoader());
      // The destroy cell declares no static entry.
      Class<?> destroy = Class.forName(entry.getValue() + "$release_Cell", false, route.getClassLoader());
      for (Method method : destroy.getDeclaredMethods())
        assertFalse(method.getName().equals("invoke"), destroy + " declares " + method);
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
        assertEquals(int.class, invoke.getReturnType(), invoke.toString());
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

  private static <T extends NativeCell> T adopt(T cell) {
    new NativeCellRootNode(cell).adoptChildren();
    return cell;
  }

  private static final long LIMIT = 1L << 20;

  private static MemorySegment bytes(Arena arena, byte[] bytes, long padding) {
    MemorySegment segment = arena.allocate(bytes.length + padding, 8);
    MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 0, bytes.length);
    return segment;
  }

  private static MemorySegment at(long address, long size) {
    return MemorySegment.ofAddress(address).reinterpret(size);
  }

  /**
   * A JsonResult's compact tape, as hex: its words, pool and input bytes (TapeView in json.seam;
   * every pointer it holds is compared by what it points to).
   */
  static String tape(MemorySegment result) {
    long view = result.get(ValueLayout.JAVA_LONG, 8);
    if (view == 0) return "no view";
    MemorySegment v = at(view, 88);
    var hex = HexFormat.of();
    StringBuilder out = new StringBuilder("v" + v.get(ValueLayout.JAVA_INT, 0) + " ");
    long words = v.get(ValueLayout.JAVA_LONG, 16) * 8, pool = v.get(ValueLayout.JAVA_LONG, 32);
    long input = v.get(ValueLayout.JAVA_LONG, 48);
    out.append(hex.formatHex(at(v.get(ValueLayout.JAVA_LONG, 8), words).toArray(ValueLayout.JAVA_BYTE)));
    out.append('|').append(pool == 0 ? "" : hex.formatHex(at(v.get(ValueLayout.JAVA_LONG, 24), pool).toArray(ValueLayout.JAVA_BYTE)));
    out.append('|').append(hex.formatHex(at(v.get(ValueLayout.JAVA_LONG, 40), input).toArray(ValueLayout.JAVA_BYTE)));
    return out.toString();
  }

  /** The address a JsonResult's tape reads its input from. */
  static long tapeInput(MemorySegment result) {
    return at(result.get(ValueLayout.JAVA_LONG, 8), 88).get(ValueLayout.JAVA_LONG, 40);
  }

  /**
   * The borrows of json/cells/ownership.seam (Myna 0.18.0), as a guest calls the FFM cells: each
   * byte buffer is one argument whose length the cell derives, from heap bytes (copied for the
   * call) and from a native segment (lent in place); a segment whose scope is closed is refused
   * before native code runs; and an absent nullable buffer (text_out) reaches native code as null
   * and length zero. Every result equals the production binding's for the same input.
   */
  @Test
  void guestCallsBorrowTheirBuffers() throws Throwable {
    new CellsFfm(library());
    byte[] document = "{\"a\":[1,true,\"t\\u00e9xt\"],\"b\":{\"c\":-2.5},\"d\":null}".getBytes(StandardCharsets.UTF_8);
    var decode = adopt(new JsonCellsJvmTruffle.decode_Cell());
    var release = adopt(new JsonCellsJvmTruffle.release_Cell());
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment input = bytes(arena, document, 0);
      // The production binding, for the expected tape.
      MemorySegment baseline = arena.allocate(88, 8);
      assertEquals(0, JsonFFM.decode(input, document.length, LIMIT, LIMIT, LIMIT, LIMIT, 128, baseline));
      String expected = tape(baseline);
      JsonFFM.release(baseline);
      // decode(data, max_input_bytes, max_index_bytes, max_nodes, max_text_bytes, max_depth, result):
      // the length is no longer a guest argument.
      for (Object data : List.of(new HeapBytes(document, false), NativeBytes.of(input, false))) {
        MemorySegment result = arena.allocate(88, 8);
        assertEquals(
            0,
            decode.executeInt(new Object[] {data, LIMIT, LIMIT, LIMIT, LIMIT, 128, NativeBytes.of(result, true)}));
        assertEquals(expected, tape(result));
        // decode is not JsonResult's create (decode_adopted is): its result is the guest's own
        // struct, which the release cell refuses, so host code releases it through the binding.
        assertThrows(NativeCellException.class, () -> release.execute(new Object[] {NativeBytes.of(result, true)}));
        JsonFFM.release(result);
      }
      MemorySegment result = arena.allocate(88, 8);
      assertThrows(
          NativeCellException.class,
          () ->
              decode.executeInt(
                  new Object[] {new HeapBytes(document, false), (long) document.length, LIMIT, LIMIT, LIMIT, LIMIT, 128, NativeBytes.of(result, true)}));
      Arena closed = Arena.ofShared();
      MemorySegment stale = bytes(closed, document, 0);
      closed.close();
      assertThrows(
          RuntimeException.class,
          () -> decode.executeInt(new Object[] {NativeBytes.of(stale, false), LIMIT, LIMIT, LIMIT, LIMIT, 128, NativeBytes.of(result, true)}));
      assertEquals(0L, result.get(ValueLayout.JAVA_LONG, 0), "refused before native code");

      // session_decode(session, data, flags, text_out, result): text_out absent (null) and present.
      MemorySegment limits = arena.allocate(40, 8);
      limits.set(ValueLayout.JAVA_LONG, 0, LIMIT);
      limits.set(ValueLayout.JAVA_LONG, 8, 1L << 24);
      limits.set(ValueLayout.JAVA_LONG, 16, LIMIT);
      limits.set(ValueLayout.JAVA_LONG, 24, LIMIT);
      limits.set(ValueLayout.JAVA_INT, 32, 128);
      MemorySegment production = JsonFFM.session_create(limits);
      assertNotEquals(0, production.address());
      var create = adopt(new JsonCellsJvmTruffle.session_create_Cell());
      var sessionDecode = adopt(new JsonCellsJvmTruffle.session_decode_Cell());
      var sessionRelease = adopt(new JsonCellsJvmTruffle.session_release_Cell());
      NativeResource session = (NativeResource) create.execute(new Object[] {NativeBytes.of(limits, false)});
      try {
        long capacity = 2L * document.length + 16;
        for (int flags : new int[] {0, 1 /* TD_JSON_WANT_TEXT */}) {
          MemorySegment text = flags == 0 ? MemorySegment.NULL : arena.allocate(capacity, 8);
          MemorySegment base = arena.allocate(88, 8);
          assertEquals(0, JsonFFM.session_decode(production, input, document.length, flags, text, flags == 0 ? 0 : capacity, base));
          String borrowed = tape(base);
          long textBytes = base.get(ValueLayout.JAVA_LONG, 80);
          byte[] expectedText = flags == 0 ? new byte[0] : text.asSlice(0, textBytes).toArray(ValueLayout.JAVA_BYTE);
          MemorySegment out = arena.allocate(88, 8);
          MemorySegment guestText = flags == 0 ? null : arena.allocate(capacity, 8);
          assertEquals(
              0,
              sessionDecode.executeInt(
                  new Object[] {
                    session, new HeapBytes(document, false), flags,
                    guestText == null ? null : NativeBytes.of(guestText, true), NativeBytes.of(out, true)
                  }));
          assertEquals(borrowed, tape(out));
          assertEquals(textBytes, out.get(ValueLayout.JAVA_LONG, 80));
          if (guestText != null)
            assertArrayEquals(expectedText, guestText.asSlice(0, textBytes).toArray(ValueLayout.JAVA_BYTE));
        }
        // A present text buffer without the flag is the native argument error, as on the binding.
        assertEquals(
            1,
            sessionDecode.executeInt(
                new Object[] {session, new HeapBytes(document, false), 0, NativeBytes.of(arena.allocate(8, 8), true), NativeBytes.of(arena.allocate(88, 8), true)}));
      } finally {
        sessionRelease.execute(new Object[] {session});
        JsonFFM.session_release(production);
      }
    }
  }

  /**
   * The resources of json/cells/ownership.seam (Myna 0.18.0), as a guest uses them: a session owner
   * from session_create, used and then closed exactly once (a second close is a no-op, as Myna
   * documents, and nothing can use it after); a stream created from it, which holds it open; a
   * status create that fails, which surfaces its status as {@link NativeStatusException} and makes
   * no owner; and decode_adopted's held borrow, which keeps its input valid until the result owner
   * closes, whatever the guest does to its own bytes meanwhile.
   */
  @Test
  void guestResourcesOwnTheirHandles() throws Throwable {
    new CellsFfm(library());
    byte[] document = "{\"k\":[\"v\",2,{\"n\":null}]}".getBytes(StandardCharsets.UTF_8);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment limits = arena.allocate(40, 8);
      limits.set(ValueLayout.JAVA_LONG, 0, LIMIT);
      limits.set(ValueLayout.JAVA_LONG, 8, 1L << 24);
      limits.set(ValueLayout.JAVA_LONG, 16, LIMIT);
      limits.set(ValueLayout.JAVA_LONG, 24, LIMIT);
      limits.set(ValueLayout.JAVA_INT, 32, 128);
      var create = adopt(new JsonCellsJvmTruffle.session_create_Cell());
      var sessionDecode = adopt(new JsonCellsJvmTruffle.session_decode_Cell());
      var sessionRelease = adopt(new JsonCellsJvmTruffle.session_release_Cell());
      var streamCreate = adopt(new JsonCellsJvmTruffle.stream_create_Cell());
      var feed = adopt(new JsonCellsJvmTruffle.stream_feed_Cell());
      var finish = adopt(new JsonCellsJvmTruffle.stream_finish_Cell());
      var next = adopt(new JsonCellsJvmTruffle.stream_next_Cell());
      var streamRelease = adopt(new JsonCellsJvmTruffle.stream_release_Cell());

      // Invalid limits: the create returns null and the guest gets no owner.
      MemorySegment invalid = arena.allocate(40, 8);
      assertFalse(create.execute(new Object[] {NativeBytes.of(invalid, false)}) instanceof NativeResource);

      Object owner = create.execute(new Object[] {NativeBytes.of(limits, false)});
      NativeResource session = assertInstanceOf(NativeResource.class, owner);
      assertEquals("JsonSession", session.type());
      MemorySegment out = arena.allocate(88, 8);
      assertEquals(0, sessionDecode.executeInt(new Object[] {session, new HeapBytes(document, false), 0, null, NativeBytes.of(out, true)}));
      // A session parameter takes the session's owner; any other pointer parameter refuses it.
      assertThrows(
          NativeCellException.class,
          () -> sessionDecode.executeInt(new Object[] {session, new HeapBytes(document, false), 0, null, session}));

      // A stream holds its session open.
      Object made = streamCreate.execute(new Object[] {session, 1024L, 65536L});
      NativeResource stream = assertInstanceOf(NativeResource.class, made);
      byte[] lines = "{\"x\":1}\n{\"y\":[2]}\n".getBytes(StandardCharsets.UTF_8);
      assertEquals(lines.length, feed.executeLong(new Object[] {stream, new HeapBytes(lines, false)}));
      finish.execute(new Object[] {stream});
      assertEquals(0, next.executeInt(new Object[] {stream, 0, NativeBytes.of(out, true)}));
      assertNotEquals("no view", tape(out));
      assertThrows(NativeCellException.class, () -> sessionRelease.execute(new Object[] {session}));
      assertFalse(session.isClosed(), "a session with an open stream stays open");
      assertEquals(0, next.executeInt(new Object[] {stream, 0, NativeBytes.of(out, true)}));
      assertEquals(8, next.executeInt(new Object[] {stream, 0, NativeBytes.of(out, true)}), "TD_JSON_STREAM_EMPTY");
      streamRelease.execute(new Object[] {stream});
      assertTrue(stream.isClosed());
      assertThrows(NativeCellException.class, () -> next.executeInt(new Object[] {stream, 0, NativeBytes.of(out, true)}));

      // Closed exactly once: the destroy runs once, a second close or destroy is a no-op, and
      // nothing takes the owner afterwards. A raw pointer is no owner.
      assertThrows(
          NativeCellException.class,
          () -> sessionRelease.execute(new Object[] {NativePointer.of(out.address())}));
      sessionRelease.execute(new Object[] {session});
      assertTrue(session.isClosed());
      sessionRelease.execute(new Object[] {session});
      session.close();
      assertThrows(
          NativeCellException.class,
          () -> sessionDecode.executeInt(new Object[] {session, new HeapBytes(document, false), 0, null, NativeBytes.of(out, true)}));
      assertThrows(NativeCellException.class, () -> streamCreate.execute(new Object[] {session, 1024L, 65536L}));

      // A status create that fails: no owner, the status on NativeStatusException, and the
      // guest's struct buffer holds what the create left (zeroed: nothing to release).
      var adopted = adopt(new JsonCellsJvmTruffle.decode_adopted_Cell());
      var release = adopt(new JsonCellsJvmTruffle.release_Cell());
      MemorySegment failed = arena.allocate(88, 8);
      failed.fill((byte) 0x5a);
      byte[] truncated = "[1,".getBytes(StandardCharsets.UTF_8);
      NativeStatusException syntax =
          assertThrows(
              NativeStatusException.class,
              () -> adopted.execute(new Object[] {new HeapBytes(truncated, false), (long) truncated.length, LIMIT, LIMIT, LIMIT, LIMIT, 128, NativeBytes.of(failed, true)}));
      assertEquals(4, syntax.status(), "TD_JSON_SYNTAX");
      assertEquals(0L, failed.get(ValueLayout.JAVA_LONG, 0));
      assertEquals(0L, failed.get(ValueLayout.JAVA_LONG, 8));
      // capacity < length is the native argument error.
      NativeStatusException argument =
          assertThrows(
              NativeStatusException.class,
              () -> adopted.execute(new Object[] {new HeapBytes(truncated, false), (long) truncated.length + 1, LIMIT, LIMIT, LIMIT, LIMIT, 128, NativeBytes.of(failed, true)}));
      assertEquals(1, argument.status(), "TD_JSON_ARGUMENT");
      var plan = adopt(new JsonCellsJvmTruffle.plan_create_Cell());
      MemorySegment records = arena.allocate(8, 8);
      NativeStatusException empty =
          assertThrows(
              NativeStatusException.class,
              () -> plan.execute(new Object[] {NativeBytes.of(records, false), 0L, null, 0L, 0L, NativeBytes.of(arena.allocate(8, 8), true)}));
      assertEquals(1, empty.status(), "TD_JSON_ARGUMENT for a plan without records");

      // decode_adopted's held borrow. The production binding decodes the same bytes first.
      MemorySegment input = bytes(arena, document, 64);
      MemorySegment baseline = arena.allocate(88, 8);
      assertEquals(0, JsonFFM.decode_adopted(input, document.length, document.length + 64, LIMIT, LIMIT, LIMIT, LIMIT, 128, baseline));
      String expected = tape(baseline);
      JsonFFM.release(baseline);
      // Heap bytes: copied, and the copy is kept, not the guest's array (padding makes it adopt).
      byte[] heap = Arrays.copyOf(document, document.length + 64);
      MemorySegment struct = arena.allocate(88, 8);
      NativeResource held =
          assertInstanceOf(
              NativeResource.class,
              adopted.execute(new Object[] {new HeapBytes(heap, false), (long) document.length, LIMIT, LIMIT, LIMIT, LIMIT, 128, NativeBytes.of(struct, true)}));
      assertEquals("JsonResult", held.type());
      // The guest's buffer holds a copy of the struct the runtime keeps; the tape reads the input
      // in place, from the held copy, until the result is released.
      assertNotEquals(0L, tapeInput(struct));
      Arrays.fill(heap, (byte) '!');
      System.gc();
      assertEquals(expected, tape(struct));
      // A native segment: lent in place, so the tape reads the guest's own memory.
      Arena shared = Arena.ofShared();
      MemorySegment region = bytes(shared, document, 64);
      MemorySegment inPlace = arena.allocate(88, 8);
      NativeResource lent =
          assertInstanceOf(
              NativeResource.class,
              adopted.execute(new Object[] {NativeBytes.of(region, false), (long) document.length, LIMIT, LIMIT, LIMIT, LIMIT, 128, NativeBytes.of(inPlace, true)}));
      assertEquals(region.address(), tapeInput(inPlace));
      assertEquals(expected, tape(inPlace));
      // Closing one result leaves the other's input valid.
      release.execute(new Object[] {held});
      assertTrue(held.isClosed());
      assertEquals(expected, tape(inPlace));
      release.execute(new Object[] {held});
      assertThrows(NativeCellException.class, held::address);
      release.execute(new Object[] {lent});
      // Memory the guest frees explicitly is its own to keep alive while the result is open (Myna
      // 0.18.0 holds no FFM scope for a held borrow), so the arena closes only now.
      shared.close();
    }
  }
}
