package dev.elide.truffledata.cells;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The call shape of every hand-written cell call site, in every {@code <format>/cells} package
 * (docs/superpowers/plans/briefs/2026-11-09-myna-0.14, item 2; Myna 0.16.0's {@code invoke}).
 *
 * <ul>
 *   <li>Native Image routes (sources whose file name contains {@code Svm}) are host code compiled
 *       ahead of time, where a cell's {@code execute(Object[])} path is not scalar-replaced: they
 *       call the generated cell's static {@code invoke} (native carriers and raw addresses,
 *       nothing allocated) and never an {@code execute} method.
 *   <li>HotSpot routes call {@code invoke} too wherever every buffer is a native address; where
 *       they lend a heap array they hold the concrete generated {@code *_Cell} type and call the
 *       typed method the generator overrides ({@code executeInt}, {@code executeLong}, {@code
 *       executeDouble}, {@code executeBoolean}). Generic {@code execute(Object[])} is allowed only
 *       on cells without a typed override (pointer, void and resource results).
 *   <li>Boxed casts or unboxing of a cell result, and {@code UnexpectedResultException} handlers,
 *       are rejected everywhere; an {@code invoke} must name a cell the scanned generated sources
 *       declare with that static entry.
 * </ul>
 *
 * <p>A source scan over the runfiles: each {@code <format>/cells} package exports a {@code
 * call_sites} filegroup (its hand-written sources and its generated {@code *Truffle.java}), listed
 * in this test's {@code data}. A format without a cells package yet (CSV) simply has nothing to
 * scan; the scan reports which formats it saw. Receivers resolve by name within a file: a name
 * declared with several types (in different methods) must satisfy the rules for each of them, and
 * a receiver declared with {@code var} is rejected, since the scan cannot see its type.
 */
final class CellsCallShapeTest {
  private static final List<String> FORMATS = List.of("protobuf-buffa", "json", "yaml", "csv");

  @Test
  void everyScalarCellCallIsSpecialized() throws IOException {
    Path root =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")));
    Map<String, String> generated = new TreeMap<>(), handwritten = new TreeMap<>();
    var seen = new LinkedHashMap<String, String>();
    for (String format : FORMATS) {
      Path cells = root.resolve(format).resolve("cells");
      if (!Files.isDirectory(cells)) {
        seen.put(format, "absent");
        continue;
      }
      int before = generated.size() + handwritten.size();
      try (Stream<Path> files = Files.walk(cells)) {
        for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
          String relative = root.relativize(file).toString().replace('\\', '/');
          String text = Files.readString(file);
          if (relative.contains("/generated/")) generated.put(relative, text);
          else handwritten.put(relative, text);
        }
      }
      seen.put(format, (generated.size() + handwritten.size() - before) + " files");
    }
    System.out.println("CELLS call-shape scan " + seen);
    // Never vacuous: the Buffa route and its generated cells are always present.
    assertTrue(
        handwritten.keySet().stream().anyMatch(p -> p.endsWith("/CellsSvmBackend.java")),
        "Buffa SVM cells route not scanned: " + handwritten.keySet());
    assertTrue(
        handwritten.keySet().stream().anyMatch(p -> p.endsWith("/CellsFfmBackend.java")),
        "Buffa FFM cells route not scanned: " + handwritten.keySet());
    var scan = Scan.of(generated);
    assertTrue(scan.cells.containsKey("decodeSlab_Cell"), "generated Buffa cells not parsed");
    List<String> violations = new ArrayList<>();
    int typed = 0, direct = 0, nativeImageDirect = 0;
    for (var entry : handwritten.entrySet()) {
      Scan.Calls calls = scan.check(entry.getKey(), entry.getValue(), violations);
      typed += calls.typed();
      direct += calls.direct();
      if (Scan.nativeImage(entry.getKey())) nativeImageDirect += calls.direct();
    }
    System.out.println(
        "CELLS call-shape scan: " + direct + " invoke calls (" + nativeImageDirect
            + " in Native Image routes), " + typed + " typed execute calls");
    assertEquals(List.of(), violations, "cells call sites");
    // Never vacuous: each format's Native Image route makes at least one invoke call.
    assertTrue(
        nativeImageDirect >= 4, "expected invoke calls in every Native Image route, found " + nativeImageDirect);
    assertTrue(typed + direct >= 8, "expected the routes' call sites, found " + (typed + direct));
  }

  // The scanner itself: each rule fails on a minimal offending source.

  private static final String GENERATED =
      """
      public final class DemoTruffle {
          public static final class status_Cell extends NativeCell {
              public static int invoke(long handle) {
                  return 0;
              }
              @Override
              public Object execute(Object[] arguments) {
                  return executeInt(arguments);
              }
              @Override
              public int executeInt(Object[] arguments) {
                  return 0;
              }
          }
          public static final class size_Cell extends NativeCell {
              @Override
              public long executeLong(Object[] arguments) {
                  return 0;
              }
          }
          public static final class open_Cell extends NativeCell {
              @Override
              public Object execute(Object[] arguments) {
                  return NativePointer.NULL;
              }
          }
      }
      """;

  private static List<String> violations(String source) {
    return violations("demo/cells/src/Route.java", source);
  }

  private static List<String> violations(String path, String source) {
    var scan = Scan.of(Map.of("demo/cells/generated/DemoTruffle.java", GENERATED));
    var out = new ArrayList<String>();
    scan.check(path, source, out);
    return out;
  }

  @Test
  void acceptsInvokeAndCountsIt() {
    var scan = Scan.of(Map.of("demo/cells/generated/DemoTruffle.java", GENERATED));
    var out = new ArrayList<String>();
    Scan.Calls calls =
        scan.check(
            "demo/cells/src/RouteSvm.java",
            """
            final class RouteSvm {
              int a(long handle) { return DemoTruffle.status_Cell.invoke(handle); }
              int b(long handle) { return status_Cell.invoke(handle); }
              // status.execute(arguments) in a comment is not a call
            }
            """,
            out);
    assertEquals(List.of(), out);
    assertEquals(2, calls.direct());
    assertEquals(0, calls.typed());
  }

  @Test
  void rejectsExecuteInANativeImageRoute() {
    // Typed or not: ahead-of-time host code allocates on every execute path.
    assertFalse(
        violations(
                "demo/cells/src/RouteSvm.java",
                """
                final class RouteSvm {
                  private final DemoTruffle.status_Cell status;
                  int a(Object[] arguments) { return status.executeInt(arguments); }
                }
                """)
            .isEmpty());
    assertFalse(
        violations(
                "demo/cells/src/SvmRoute.java",
                """
                final class SvmRoute {
                  private final DemoTruffle.open_Cell open;
                  Object a(Object[] arguments) { return open.execute(arguments); }
                }
                """)
            .isEmpty());
  }

  /**
   * One cell name in two formats with different shapes (Myna 0.18.0: a status create returns an
   * owner, so it has no typed override, where the same name elsewhere is a scalar call): a call
   * qualified by its generated class resolves against that class alone.
   */
  @Test
  void resolvesQualifiedInvokeAcrossFormats() {
    String other =
        """
        public final class OtherTruffle {
            public static final class status_Cell extends NativeCell {
                public static int invoke(long handle) {
                    return 0;
                }
                @Override
                public Object execute(Object[] arguments) {
                    return NativePointer.NULL;
                }
            }
            public static final class open_Cell extends NativeCell {
                public static long invoke() {
                    return 0;
                }
            }
        }
        """;
    var scan =
        Scan.of(
            Map.of(
                "demo/cells/generated/DemoTruffle.java", GENERATED,
                "other/cells/generated/OtherTruffle.java", other));
    var out = new ArrayList<String>();
    Scan.Calls calls =
        scan.check(
            "demo/cells/src/RouteSvm.java",
            """
            final class RouteSvm {
              int a(long handle) { return DemoTruffle.status_Cell.invoke(handle); }
              int b(long handle) { return OtherTruffle.status_Cell.invoke(handle); }
              long c() { return OtherTruffle.open_Cell.invoke(); }
            }
            """,
            out);
    assertEquals(List.of(), out);
    assertEquals(3, calls.direct());
    // DemoTruffle's open_Cell has no invoke, though OtherTruffle's has.
    scan.check("demo/cells/src/RouteSvm.java", "long d() { return DemoTruffle.open_Cell.invoke(); }", out);
    assertEquals(1, out.size(), out.toString());
  }

  @Test
  void rejectsInvokeOnAnUnknownCellOrOneWithoutTheEntry() {
    assertFalse(
        violations(
                """
                final class Route {
                  int a(long handle) { return OtherTruffle.mystery_Cell.invoke(handle); }
                }
                """)
            .isEmpty());
    // size_Cell is declared by the scanned source, but without a static invoke.
    assertFalse(
        violations(
                """
                final class Route {
                  long a() { return DemoTruffle.size_Cell.invoke(); }
                }
                """)
            .isEmpty());
  }

  @Test
  void acceptsSpecializedAndPointerCalls() {
    assertEquals(
        List.of(),
        violations(
            """
            final class Route {
              private final DemoTruffle.status_Cell status = new DemoTruffle.status_Cell();
              private final DemoTruffle.size_Cell size;
              private final DemoTruffle.open_Cell open;
              // status.execute(arguments) in a comment is not a call
              int a(Object[] arguments) { return status.executeInt(arguments); }
              long b(Object[] arguments) { return this.size.executeLong(arguments); }
              Object c(Object[] arguments) { return open.execute(arguments); }
              String d() { return "status.execute(x)"; }
              long e(Object compiled) { return (Long) compiled; }
            }
            """));
  }

  @Test
  void rejectsGenericExecuteOnAScalarCell() {
    assertFalse(
        violations(
                """
                final class Route {
                  private final DemoTruffle.status_Cell status;
                  Object a(Object[] arguments) { return status.execute(arguments); }
                }
                """)
            .isEmpty());
  }

  @Test
  void rejectsBoxedCastsAndUnboxing() {
    assertFalse(
        violations(
                """
                final class Route {
                  private final DemoTruffle.open_Cell open;
                  int a(Object[] arguments) { return (Integer) open.execute(arguments); }
                }
                """)
            .isEmpty());
    assertFalse(
        violations(
                """
                final class Route {
                  private final DemoTruffle.open_Cell open;
                  long a(Object[] arguments) { return ((Number) open.execute(arguments)).longValue(); }
                }
                """)
            .isEmpty());
  }

  @Test
  void rejectsAbstractHoldersAndUnexpectedResults() {
    assertFalse(
        violations(
                """
                final class Route {
                  private final NativeCell cell;
                  int a(Object[] arguments) throws UnexpectedResultException {
                    return cell.executeInt(arguments);
                  }
                }
                """)
            .isEmpty());
    assertFalse(
        violations(
                """
                final class Route {
                  static <T extends NativeCell> Object a(T cell, Object[] arguments) {
                    return cell.execute(arguments);
                  }
                }
                """)
            .isEmpty());
  }

  @Test
  void rejectsMismatchedOrUnknownTypedCalls() {
    // executeInt on a long cell falls back to NativeCell's unboxing default.
    assertFalse(
        violations(
                """
                final class Route {
                  private final DemoTruffle.size_Cell size;
                  int a(Object[] arguments) { return size.executeInt(arguments); }
                }
                """)
            .isEmpty());
    // A cell class the scan has no generated source for cannot be classified.
    assertFalse(
        violations(
                """
                final class Route {
                  private final OtherTruffle.mystery_Cell mystery;
                  int a(Object[] arguments) { return mystery.executeInt(arguments); }
                }
                """)
            .isEmpty());
    // A receiver whose type `var` hides.
    assertFalse(
        violations(
                """
                final class Route {
                  private final DemoTruffle.status_Cell status;
                  Object a(Object[] arguments) { var cell = status; return cell.execute(arguments); }
                }
                """)
            .isEmpty());
    // A call whose receiver cannot be resolved.
    assertFalse(
        violations(
                """
                final class Route {
                  int a(Object[] arguments) { return cells().get(0).executeInt(arguments); }
                }
                """)
            .isEmpty());
  }

  /** The generated cell classes and their typed overrides, and the hand-written call-site rules. */
  static final class Scan {
    private static final Pattern CELL_CLASS =
        Pattern.compile("\\bclass\\s+(\\w+_Cell)\\s+extends\\s+NativeCell\\b");
    private static final Pattern TYPED_OVERRIDE =
        Pattern.compile(
            "\\b(int|long|double|boolean)\\s+(execute(?:Int|Long|Double|Boolean))\\s*\\(\\s*Object\\s*\\[\\s*\\]");
    private static final Pattern CELL_DECLARATION =
        Pattern.compile("\\b(?:\\w+\\s*\\.\\s*)*(\\w+_Cell)\\s+(\\w+)\\s*(?=[;=,)])");
    private static final Pattern ABSTRACT_TYPE_VARIABLE =
        Pattern.compile("\\b(\\w+)\\s+extends\\s+(?:[\\w.]+\\.)?NativeCell\\b");
    private static final Pattern DECLARATION =
        Pattern.compile(
            "\\b([A-Z][\\w.]*)(?:\\s*<[^;(){}]*>)?(?:\\s*\\[\\s*\\])*\\s+(\\w+)\\s*(?=[;=,)])");
    private static final Pattern CALL =
        Pattern.compile("(?:(\\w+)|(\\)))\\s*\\.\\s*(execute(?:Int|Long|Double|Boolean)?)\\s*\\(");
    private static final Pattern INFERRED = Pattern.compile("\\bvar\\s+(\\w+)\\s*[=:]");
    private static final Pattern STATIC_INVOKE =
        Pattern.compile("\\bstatic\\s+[\\w.\\[\\]]+\\s+invoke\\s*\\(");
    private static final Pattern INVOKE =
        Pattern.compile("\\b(?:(\\w+)\\s*\\.\\s*)?(\\w+_Cell)\\s*\\.\\s*invoke\\s*\\(");
    private static final Pattern NATIVE_IMAGE_SOURCE = Pattern.compile("(?:^|/)[^/]*Svm[^/]*\\.java$");

    /** Typed {@code execute*} calls and static {@code invoke} calls found in one source. */
    record Calls(int typed, int direct) {}

    /** Whether a source is a Native Image route: host code compiled ahead of time. */
    static boolean nativeImage(String path) {
      return NATIVE_IMAGE_SOURCE.matcher(path).find();
    }
    private static final Pattern BOX_CAST =
        Pattern.compile(
            "\\(\\s*(?:java\\.lang\\.)?(?:Integer|Long|Double|Boolean|Short|Byte|Float|Character|Number)\\s*\\)\\s*\\(?\\s*(?:this\\s*\\.\\s*)?\\w*\\s*$");
    private static final Pattern UNBOX =
        Pattern.compile(
            "^\\s*\\)?\\s*\\.\\s*(?:int|long|double|boolean|short|byte|float|char)Value\\s*\\(");

    /** Cell simple name to its typed method, or {@code ""} for a cell without one. */
    final Map<String, String> cells = new HashMap<>();

    /** Cells whose generated class declares the static {@code invoke} entry (Myna 0.16.0). */
    final Set<String> invokable = new HashSet<>();

    private final Set<String> ambiguous = new HashSet<>();

    /**
     * Per generated class ({@code <Outer>.<cell>_Cell}): whether the cell declares the static
     * {@code invoke}. A call qualified by its generated class resolves here, so a cell name shared by
     * two formats with different shapes (since Myna 0.18.0, a create in one and a plain call in the
     * other, or a destroy without {@code invoke}) is told apart.
     */
    final Map<String, Boolean> qualified = new HashMap<>();

    static Scan of(Map<String, String> generated) {
      var scan = new Scan();
      for (var file : generated.entrySet()) {
        String name = file.getKey().substring(file.getKey().lastIndexOf('/') + 1);
        String outer = name.endsWith(".java") ? name.substring(0, name.length() - 5) : name;
        String text = strip(file.getValue());
        Matcher m = CELL_CLASS.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (m.find()) {
          starts.add(m.start());
          names.add(m.group(1));
        }
        for (int i = 0; i < names.size(); i++) {
          int end = i + 1 < names.size() ? starts.get(i + 1) : text.length();
          String body = text.substring(starts.get(i), end);
          Matcher typed = TYPED_OVERRIDE.matcher(body);
          String method = typed.find() ? typed.group(2) : "";
          boolean entry = STATIC_INVOKE.matcher(body).find();
          if (entry) scan.invokable.add(names.get(i));
          scan.qualified.put(outer + "." + names.get(i), entry);
          String previous = scan.cells.putIfAbsent(names.get(i), method);
          // Same simple name in two modules with different result kinds: refuse to guess.
          if (previous != null && !previous.equals(method)) scan.ambiguous.add(names.get(i));
        }
      }
      return scan;
    }

    /** Appends violations for one hand-written source; returns its typed and invoke calls. */
    Calls check(String path, String source, List<String> violations) {
      String text = strip(source);
      boolean nativeImage = nativeImage(path);
      int direct = 0;
      Matcher invoke = INVOKE.matcher(text);
      while (invoke.find()) {
        String outer = invoke.group(1), type = invoke.group(2);
        String where = path + ":" + line(text, invoke.start()) + ": " + type + ".invoke(";
        Boolean entry = outer == null ? null : qualified.get(outer + "." + type);
        if (entry != null) {
          if (entry) direct++;
          else violations.add(where + ", but the generated cell declares no static invoke");
        } else if (!cells.containsKey(type) || ambiguous.contains(type))
          violations.add(where + ", which no scanned generated *Truffle.java classifies");
        else if (!invokable.contains(type))
          violations.add(where + ", but the generated cell declares no static invoke");
        else direct++;
      }
      if (text.contains("UnexpectedResultException"))
        violations.add(path + ": UnexpectedResultException (hold the concrete *_Cell instead)");
      // Receiver name -> every type it is declared with in this file.
      Map<String, Set<String>> declared = new HashMap<>();
      Set<String> typeVariables = new HashSet<>();
      Matcher v = ABSTRACT_TYPE_VARIABLE.matcher(text);
      while (v.find()) typeVariables.add(v.group(1));
      Matcher d = DECLARATION.matcher(text);
      while (d.find()) {
        String type = d.group(1);
        String simple = type.substring(type.lastIndexOf('.') + 1);
        if (typeVariables.contains(simple)) simple = "NativeCell";
        declared.computeIfAbsent(d.group(2), k -> new HashSet<>()).add(simple);
      }
      Matcher c = CELL_DECLARATION.matcher(text);
      while (c.find()) declared.computeIfAbsent(c.group(2), k -> new HashSet<>()).add(c.group(1));
      // `var` hides the type from this scan, so a cell behind it could take any call.
      Set<String> inferred = new HashSet<>();
      Matcher i = INFERRED.matcher(text);
      while (i.find()) inferred.add(i.group(1));
      int typed = 0;
      Matcher call = CALL.matcher(text);
      while (call.find()) {
        String receiver = call.group(1), method = call.group(3);
        String where = path + ":" + line(text, call.start()) + ": " + method + "(";
        boolean generic = method.equals("execute");
        Set<String> types = receiver == null ? null : declared.get(receiver);
        if (receiver != null && receiver.equals("this")) types = null;
        if (types == null || types.isEmpty()) {
          // An unresolved receiver is fine only for an execute( on something that is not a cell
          // at all; a typed call or a chained receiver must resolve.
          if (!generic || receiver == null)
            violations.add(
                where + " on an unresolved receiver: hold the concrete *_Cell in a field or local");
          else if (inferred.contains(receiver))
            violations.add(where + " on `var " + receiver + "`: declare the concrete type");
          else if (text.substring(call.end()).stripLeading().startsWith("new Object"))
            violations.add(where + " with an Object[] on an unresolved receiver " + receiver);
          continue;
        }
        boolean cell = false;
        for (String type : types) {
          if (type.equals("NativeCell")) {
            cell = true;
            violations.add(
                where + " on abstract NativeCell " + receiver + ": hold the concrete *_Cell");
            continue;
          }
          if (!type.endsWith("_Cell")) continue;
          cell = true;
          if (nativeImage) {
            violations.add(
                where
                    + " on "
                    + type
                    + " in a Native Image route: ahead-of-time host code calls "
                    + type
                    + ".invoke");
            continue;
          }
          String override = cells.get(type);
          if (override == null || ambiguous.contains(type))
            violations.add(
                where + " on " + type + ", which no scanned generated *Truffle.java classifies");
          else if (generic && !override.isEmpty())
            violations.add(where + " on scalar cell " + type + ": call " + override + " instead");
          else if (!generic && !override.equals(method))
            violations.add(
                where
                    + " on "
                    + type
                    + ", whose typed method is "
                    + (override.isEmpty() ? "none" : override));
          else if (!generic) typed++;
        }
        if (!cell) continue;
        if (BOX_CAST.matcher(text.substring(Math.max(0, call.start() - 80), call.start())).find())
          violations.add(where + " result is cast to a box type");
        int close = closing(text, call.end() - 1);
        if (close > 0 && UNBOX.matcher(text.substring(close + 1)).find())
          violations.add(where + " result is unboxed");
      }
      return new Calls(typed, direct);
    }

    private static int line(String text, int offset) {
      int line = 1;
      for (int i = 0; i < offset; i++) if (text.charAt(i) == '\n') line++;
      return line;
    }

    /** The index of the parenthesis closing the one at {@code open}, or -1. */
    private static int closing(String text, int open) {
      int depth = 0;
      for (int i = open; i < text.length(); i++) {
        char ch = text.charAt(i);
        if (ch == '(') depth++;
        else if (ch == ')' && --depth == 0) return i;
      }
      return -1;
    }

    /** Blanks comments and string/char literals, keeping offsets and line breaks. */
    static String strip(String source) {
      var out = new StringBuilder(source.length());
      int i = 0, n = source.length();
      while (i < n) {
        char ch = source.charAt(i);
        if (ch == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
          while (i < n && source.charAt(i) != '\n') {
            out.append(' ');
            i++;
          }
        } else if (ch == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
          int end = source.indexOf("*/", i + 2);
          end = end < 0 ? n : end + 2;
          for (; i < end; i++) out.append(source.charAt(i) == '\n' ? '\n' : ' ');
        } else if (source.startsWith("\"\"\"", i)) {
          int end = source.indexOf("\"\"\"", i + 3);
          end = end < 0 ? n : end + 3;
          for (; i < end; i++) out.append(source.charAt(i) == '\n' ? '\n' : ' ');
        } else if (ch == '"' || ch == '\'') {
          out.append(' ');
          i++;
          while (i < n && source.charAt(i) != ch && source.charAt(i) != '\n') {
            if (source.charAt(i) == '\\' && i + 1 < n) {
              out.append(' ');
              i++;
            }
            out.append(' ');
            i++;
          }
          if (i < n) {
            out.append(source.charAt(i) == '\n' ? '\n' : ' ');
            i++;
          }
        } else {
          out.append(ch);
          i++;
        }
      }
      return out.toString();
    }
  }
}
