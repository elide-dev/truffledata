package dev.elide.truffledata.yaml;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellException;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.tape.JavaSink;
import dev.elide.truffledata.yaml.cells.generated.YamlCellsJvmTruffle;
import dev.elide.truffledata.yaml.generated.YamlFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The YAML cells route on HotSpot ({@code truffledata.yaml.cells}): the route the flag selects,
 * exact parity of the cells backend with the FFM backend over the YAML corpus (testdata, the
 * session fuzz corpus, the yaml-test-suite and built-in edge cases), typed plans, result lifetimes,
 * the scope checks of caller-supplied native segments, and the invoke call shape. Runs with the
 * flag on ({@code jvm}) and off ({@code jvm_off}); parity constructs both backends directly either
 * way.
 */
final class CellsJvmTest {
  static Path runfile(String path) {
    return Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), path);
  }

  static Path library() {
    return runfile("yaml/native/yaml_shared.so");
  }

  /** A session on the FFM backend, wrapped in the cells backend when {@code cells}. */
  static YamlSession open(YamlOptions options, boolean cells) {
    Arena arena = Arena.ofConfined();
    JvmSessionBackend backend;
    try {
      backend =
          new JvmSessionBackend(
              options,
              new YamlFFM.LibraryBinding(SymbolLookup.libraryLookup(library().toAbsolutePath(), arena)),
              arena);
    } catch (RuntimeException | Error failure) {
      arena.close();
      throw failure;
    }
    return new YamlSession(options, cells ? CellsRoute.jvm(backend, library()) : backend);
  }

  @Test
  void flagSelectsRoute() {
    boolean expected = Boolean.getBoolean("truffledata.yaml.cells.expected");
    assertEquals(expected, CellsRoute.ENABLED);
    try (YamlSession session = YamlJvm.openSession(library())) {
      assertEquals(
          expected ? CellsJvmSessionBackend.class : JvmSessionBackend.class,
          CellsParity.backend(session).getClass());
      try (YamlPlan plan = session.compile(CellsParity.FLAT, Map.of())) {
        assertEquals(
            expected ? CellsJvmPlanBackend.class : JvmPlanBackend.class,
            plan.backend().getClass());
      }
    }
  }

  @Test
  void wrongLibraryIsRejectedBeforeCells() {
    // The production fingerprint check runs first: the other ABI class never reaches the cells.
    Path other = runfile("yaml/native/other_abi_class.so");
    assertThrows(IllegalStateException.class, () -> YamlJvm.openSession(other));
  }

  /** The cells bind one library per class loader; another path fails and releases its backend. */
  @Test
  void secondLibraryPathIsRejected() throws Exception {
    try (YamlSession first = open(YamlOptions.DEFAULT, true)) {
      assertEquals(CellsJvmSessionBackend.class, CellsParity.backend(first).getClass());
    }
    Path copy = Path.of(System.getenv("TEST_TMPDIR"), "yaml_shared_copy.so");
    try {
      Files.copy(library(), copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      Arena arena = Arena.ofConfined();
      JvmSessionBackend backend =
          new JvmSessionBackend(
              YamlOptions.DEFAULT,
              new YamlFFM.LibraryBinding(SymbolLookup.libraryLookup(copy.toAbsolutePath(), arena)),
              arena);
      IllegalStateException failure =
          assertThrows(IllegalStateException.class, () -> CellsRoute.jvm(backend, copy));
      assertTrue(failure.getMessage().startsWith("YAML cells are bound to "), failure.getMessage());
      // The route released the backend, which let its library go: exactly one release.
      assertFalse(arena.scope().isAlive());
    } finally {
      Files.deleteIfExists(copy);
    }
  }

  /** Each cell actually constructed by the JVM route must belong to the verified native image. */
  @Test
  void everyCellBindingRejectsMissingOrRedirectedSymbols() throws Exception {
    Set<String> symbols = new TreeSet<>(Set.of("td_yaml_fingerprint"));
    Pattern call = Pattern.compile("YamlCellsJvmTruffle\\.(\\w+)_Cell\\.invoke\\(");
    for (String name : List.of("CellsJvmSessionBackend", "CellsJvmPlanBackend")) {
      var matcher = call.matcher(source(name));
      while (matcher.find()) symbols.add("td_yaml_" + matcher.group(1));
    }
    assertTrue(symbols.contains("td_yaml_stream_release"));
    Map<String, MemorySegment> addresses = new TreeMap<>();
    for (String symbol : symbols)
      addresses.put(symbol, MemorySegment.ofAddress(16L * (addresses.size() + 1)));
    SymbolLookup verified = name -> Optional.ofNullable(addresses.get(name));
    Path library = Path.of("verified-yaml.so");
    assertDoesNotThrow(() -> CellsJvmSessionBackend.verifyBindings(library, verified, verified));
    for (String symbol : symbols) {
      SymbolLookup missing = name -> name.equals(symbol) ? Optional.empty() : verified.find(name);
      SymbolLookup redirected =
          name -> name.equals(symbol) ? Optional.of(MemorySegment.ofAddress(4096)) : verified.find(name);
      for (SymbolLookup bound : List.of(missing, redirected)) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () -> CellsJvmSessionBackend.verifyBindings(library, verified, bound),
                symbol);
        assertTrue(failure.getMessage().contains(symbol), failure.getMessage());
      }
      // Even agreement on absence is invalid: the verified library must export every used symbol.
      assertThrows(
          IllegalStateException.class,
          () -> CellsJvmSessionBackend.verifyBindings(library, missing, missing),
          symbol);
    }
  }

  static Map<String, byte[]> corpus() throws Exception {
    Map<String, byte[]> corpus = new TreeMap<>(CellsParity.builtIn());
    corpus.putAll(CellsParity.files(runfile("yaml/native/testdata"), ".yaml"));
    Path session = runfile("yaml/native/corpus/session");
    if (Files.exists(session)) corpus.putAll(CellsParity.files(session, ""));
    Path suite = Path.of(System.getenv("TEST_SRCDIR"), "+http_archive+yaml_test_suite");
    Map<String, byte[]> cases = CellsParity.files(suite, "in.yaml");
    cases.keySet().removeIf(name -> name.startsWith("name/") || name.startsWith("tags/"));
    assertEquals(402, cases.size());
    corpus.putAll(cases);
    return corpus;
  }

  @Test
  void parity() throws Exception {
    Map<String, byte[]> corpus = corpus();
    List<String> mismatches =
        CellsParity.compare(options -> open(options, false), options -> open(options, true), corpus);
    assertTrue(
        mismatches.isEmpty(),
        mismatches.size() + " mismatches:\n" + String.join("\n", mismatches));
  }

  @Test
  void typedParity() {
    List<String> mismatches =
        CellsParity.compareTyped(
            options -> open(options, false), options -> open(options, true), CellsJvmPlanBackend.class);
    assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));
  }

  /** Messages, retained stream documents and typed rows read after their session and plan close. */
  @Test
  void resultsOutliveTheirSessionOnBothRoutes() {
    String baseline = CellsParity.lifetimes(options -> open(options, false));
    assertEquals(baseline, CellsParity.lifetimes(options -> open(options, true)));
    assertTrue(baseline.contains("core: {"), baseline);
  }

  // ---- caller-supplied native segments ----

  private static String attempt(Runnable action) {
    try {
      action.run();
      return "ok";
    } catch (RuntimeException failure) {
      return CellsParity.failure(failure);
    }
  }

  /** Parses and feeds {@code segment}; the outcomes, with any rejection's class and message. */
  private static String segmentOutcome(boolean cells, MemorySegment segment, long length) {
    StringBuilder out = new StringBuilder();
    try (YamlSession session = open(YamlOptions.DEFAULT, cells)) {
      JavaSink sink = new JavaSink();
      String parsed = attempt(() -> session.parse(segment, length, sink));
      out.append(parsed);
      if (parsed.equals("ok")) out.append(" -> ").append(sink.result());
      out.append(" | feed ");
      try (YamlStream stream = session.stream(YamlStreamOptions.DEFAULT)) {
        long[] accepted = new long[1];
        String fed = attempt(() -> accepted[0] = stream.feed(segment));
        if (!fed.equals("ok")) return out.append(fed).toString();
        stream.finish();
        JavaSink document = new JavaSink();
        boolean next = stream.next(document);
        out.append(accepted[0]).append(" next ").append(next);
        if (next) out.append(' ').append(document.result());
      }
    }
    return out.toString();
  }

  /**
   * Caller-supplied native segments keep the FFM scope checks on the cells route: a live segment
   * decodes identically, and a closed arena or another thread's confined segment is rejected by the
   * downcall, never lent to the decoder as a raw address.
   */
  @Test
  void nativeSegmentsKeepTheirScopeChecks() throws Exception {
    byte[] yaml = "a: [1, two]\n".getBytes(StandardCharsets.UTF_8);
    String[] live = new String[2], closed = new String[2], foreign = new String[2];
    for (int route = 0; route < 2; route++) {
      boolean cells = route == 1;
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment segment = arena.allocate(yaml.length);
        MemorySegment.copy(yaml, 0, segment, ValueLayout.JAVA_BYTE, 0, yaml.length);
        live[route] = segmentOutcome(cells, segment, yaml.length);
      }

      Arena gone = Arena.ofConfined();
      MemorySegment freed = gone.allocate(yaml.length);
      gone.close();
      closed[route] = segmentOutcome(cells, freed, yaml.length);

      MemorySegment[] other = new MemorySegment[1];
      CountDownLatch ready = new CountDownLatch(1), release = new CountDownLatch(1);
      Thread owner =
          new Thread(
              () -> {
                try (Arena confined = Arena.ofConfined()) {
                  other[0] = confined.allocate(yaml.length);
                  MemorySegment.copy(yaml, 0, other[0], ValueLayout.JAVA_BYTE, 0, yaml.length);
                  ready.countDown();
                  release.await();
                } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                }
              });
      owner.start();
      try {
        ready.await();
        foreign[route] = segmentOutcome(cells, other[0], yaml.length);
      } finally {
        release.countDown();
        owner.join();
      }
    }
    assertEquals(live[0], live[1]);
    assertTrue(live[0].startsWith("ok -> "), live[0]);
    assertTrue(live[0].contains(" | feed " + yaml.length + " next true "), live[0]);
    assertEquals(closed[0], closed[1]);
    assertTrue(
        closed[0].startsWith("reject java.lang.IllegalStateException: Already closed"), closed[0]);
    assertTrue(closed[0].contains("| feed reject java.lang.IllegalStateException"), closed[0]);
    assertEquals(foreign[0], foreign[1]);
    assertTrue(foreign[0].startsWith("reject java.lang.WrongThreadException"), foreign[0]);
    assertTrue(foreign[0].contains("| feed reject java.lang.WrongThreadException"), foreign[0]);
  }

  // ---- the call shape ----

  private static String source(String name) throws Exception {
    return Files.readString(
        runfile("yaml/cells/src/main/java/dev/elide/truffledata/yaml/" + name + ".java"));
  }

  /** The {@code <cell>} of every {@code <generated class>.<cell>_Cell.invoke(} call in {@code source}. */
  private static Set<String> invoked(String generated, String source) {
    Set<String> found = new TreeSet<>();
    var matcher =
        Pattern.compile("\\b" + generated + "\\.(\\w+)_Cell\\.invoke\\(").matcher(source);
    while (matcher.find()) found.add(matcher.group(1));
    return found;
  }

  /**
   * The route is host code below Truffle boundaries, so it calls each cell's static {@code invoke}
   * (Myna 0.16.0) and nothing else: per source, exactly the expected cells, each through its own
   * runtime's generated class; no {@code execute} call of any kind, no cell instance, no argument
   * array, no {@code NativePointer}, no boxed cast and no {@code UnexpectedResultException}. The
   * HotSpot sources make no other cell call either: their caller-segment paths go through the
   * wrapped FFM backend.
   */
  @Test
  void cellsAreCalledThroughInvoke() throws Exception {
    Set<String> session =
        Set.of(
            "session_decode",
            "session_retain",
            "session_reset",
            "stream_create",
            "stream_feed",
            "stream_next",
            "stream_finish",
            "stream_release");
    Map<String, Set<String>> expected =
        Map.of(
            "CellsSvmSessionBackend",
            union(session, "session_write_text"),
            "CellsJvmSessionBackend",
            session,
            "CellsSvmPlanBackend",
            Set.of("typed_decode"),
            "CellsJvmPlanBackend",
            Set.of("typed_decode"),
            "CellsSvm",
            Set.of());
    for (var file : expected.entrySet()) {
      String name = file.getKey(), text = source(name);
      boolean svm = name.contains("Svm");
      String own = svm ? "YamlCellsTruffle" : "YamlCellsJvmTruffle";
      String other = svm ? "YamlCellsJvmTruffle" : "YamlCellsTruffle";
      assertEquals(file.getValue(), invoked(own, text), name);
      assertEquals(Set.of(), invoked(other, text), name);
      // Every invoke call is one of those: none through another class, an alias or an import.
      int calls = 0;
      for (var matcher = Pattern.compile("\\.invoke\\(").matcher(text); matcher.find(); ) calls++;
      int qualified = 0;
      for (var matcher = Pattern.compile("\\b" + own + "\\.\\w+_Cell\\.invoke\\(").matcher(text);
          matcher.find(); )
        qualified++;
      assertEquals(qualified, calls, name);
      assertFalse(Pattern.compile("\\.execute\\w*\\(").matcher(text).find(), name);
      assertFalse(Pattern.compile("\\bnew\\s+\\w+\\.\\w+_Cell\\s*\\(").matcher(text).find(), name);
      assertFalse(Pattern.compile("_Cell\\s+\\w+\\s*[;=]").matcher(text).find(), name);
      assertFalse(Pattern.compile("\\bNativeCell\\b").matcher(text).find(), name);
      assertFalse(text.contains("new Object["), name);
      assertFalse(text.contains("import dev.elide.myna"), name);
      assertFalse(Pattern.compile("\\bNativePointer\\.").matcher(text).find(), name);
      assertFalse(
          Pattern.compile("(import|catch|throws)\\b[^;{]*UnexpectedResultException").matcher(text).find(),
          name);
      assertFalse(Pattern.compile("\\((Integer|Long|Double|Boolean)\\)").matcher(text).find(), name);
    }
    // The sources the scan covers are all the route's sources.
    try (var files = Files.list(runfile("yaml/cells/src/main/java/dev/elide/truffledata/yaml"))) {
      Set<String> names = new TreeSet<>();
      files.forEach(file -> names.add(file.getFileName().toString().replace(".java", "")));
      assertEquals(new TreeSet<>(expected.keySet()), names);
    }
  }

  private static Set<String> union(Set<String> base, String... more) {
    Set<String> all = new TreeSet<>(base);
    all.addAll(List.of(more));
    return all;
  }

  /**
   * The {@code invoke} entries the route calls exist with the expected signatures (Myna 0.16.0),
   * public and static, on both the FFM and the Native Image cells.
   */
  @Test
  void generatedCellsDeclareInvoke() throws Exception {
    Class<?> L = long.class, I = int.class, V = void.class;
    Map<String, List<Class<?>>> signatures =
        Map.of(
            "session_decode_Cell", List.of(I, L, L, L, I, L, L, L),
            "session_write_text_Cell", List.of(I, L, L, L, L),
            "session_retain_Cell", List.of(I, L, L),
            "session_reset_Cell", List.of(V, L),
            "stream_create_Cell", List.of(L, L, L, L),
            "stream_feed_Cell", List.of(L, L, L, L),
            "stream_next_Cell", List.of(I, L, I, L),
            "stream_finish_Cell", List.of(V, L),
            "stream_release_Cell", List.of(V, L),
            "typed_decode_Cell", List.of(I, L, L, L, L));
    for (String outer : List.of("YamlCellsJvmTruffle", "YamlCellsTruffle"))
      for (var cell : signatures.entrySet()) {
        Class<?> type =
            Class.forName(
                "dev.elide.truffledata.yaml.cells.generated." + outer + "$" + cell.getKey(),
                false,
                CellsJvmTest.class.getClassLoader());
        assertTrue(NativeCell.class.isAssignableFrom(type), type.getName());
        List<Class<?>> signature = cell.getValue();
        var invoke =
            type.getMethod(
                "invoke", signature.subList(1, signature.size()).toArray(new Class<?>[0]));
        assertEquals(type, invoke.getDeclaringClass(), type.getName());
        assertEquals(signature.get(0), invoke.getReturnType(), type.getName());
        assertTrue(java.lang.reflect.Modifier.isStatic(invoke.getModifiers()), type.getName());
      }
  }

  /**
   * The cells' guest entry (the route itself uses {@code invoke}), strict coercion ({@code
   * truffle_coercion=strict}): a null or null-address pointer for a
   * non-nullable parameter, an out-of-range integer and a wrong argument count are rejected with
   * {@code NativeCellException} before any native call.
   */
  @Test
  void strictCoercionRejectsBeforeTheCall() {
    var next = new YamlCellsJvmTruffle.stream_next_Cell();
    new NativeCellRootNode(next).adoptChildren();
    NativePointer somewhere = NativePointer.of(16);
    assertThrows(
        NativeCellException.class, () -> next.executeInt(new Object[] {null, 0, somewhere}));
    assertThrows(
        NativeCellException.class,
        () -> next.executeInt(new Object[] {NativePointer.NULL, 0, somewhere}));
    assertThrows(
        NativeCellException.class, () -> next.executeInt(new Object[] {somewhere, -1, somewhere}));
    assertThrows(
        NativeCellException.class,
        () -> next.executeInt(new Object[] {somewhere, 1L << 32, somewhere}));
    assertThrows(
        NativeCellException.class, () -> next.executeInt(new Object[] {somewhere, "4", somewhere}));
    assertThrows(NativeCellException.class, () -> next.executeInt(new Object[] {somewhere, 0}));
  }
}
