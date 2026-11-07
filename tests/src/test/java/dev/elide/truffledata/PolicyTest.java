package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PolicyTest {
  @Test
  void sourcePoliciesAndBoundaryBudgets() throws Exception {
    Path root =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")));
    Map<String, Integer> budgets =
        Map.ofEntries(
            Map.entry("Values.java", 6),
            Map.entry("DataLibrary.java", 1),
            Map.entry("PayloadBudget.java", 1), // Strict-only immutable-backing diagnostics.
            Map.entry("Schema.java", 1),
            Map.entry("Batch.java", 2),
            Map.entry("AbstractRow.java", 1),
            Map.entry("ExactNumber.java", 12),
            Map.entry("StaticLayout.java", 12), // Nonconstant-layout direct access fallbacks.
            Map.entry("JdbcCursor.java", 7),
            Map.entry("JdbcSource.java", 6),
            Map.entry("ProtobufAdapter.java", 5),
            // Generated contract binding and the lazily compiled slab layout, outside reads.
            Map.entry("ProtoPlan.java", 12),
            Map.entry("Layout.java", 3), // Slab layout compilation (×2) and TDL1 parsing.
            Map.entry("ProtoRow.java", 3),
            Map.entry("ProtoCompiler.java", 8),
            Map.entry("ProtoDescriptors.java", 1),
            Map.entry("ProtoWire.java", 1),
            Map.entry("GeneratedMessage.java", 1),
            Map.entry("NativeMetadata.java", 1),
            Map.entry("JdbcContracts.java", 1),
            Map.entry("JdbcContract.java", 1),
            Map.entry("SqliteSource.java", 1),
            Map.entry("SqliteAdapter.java", 1),
            Map.entry("MysqlAdapter.java", 1),
            Map.entry("PostgresqlAdapter.java", 1),
            Map.entry("JdbcValues.java", 1),
            // Shared tape (//tape, YAML plan Task 4): the JSON budgets below moved here with their
            // classes (JsonOwner → TapeOwner, JsonValue → TapeValue, JsonText → TapeText, JsonKey →
            // TapeKey, JsonTextArena → TapeTextArena, JsonBorrow → TapeBorrow, JsonTypedOwner →
            // TypedOwner, JsonRecord → TypedRecord, JsonArray → TypedArray, JsonAccess → TypedAccess).
            Map.entry("JavaSink.java", 9), // Embedder tree building and key interning, outside the tape walk.
            Map.entry("TypedAccess.java", 2),
            Map.entry("TypedArray.java", 1),
            // Close, pinning, array index, export cursors, the native-directory diagnostic (the Java
            // member directories went with Myna ABI v4, JSON performance ledger V1: 10 -> 9).
            Map.entry("TapeOwner.java", 9),
            Map.entry("TypedRecord.java", 4),
            Map.entry("TypedOwner.java", 3),
            // Static (owner, node) slow paths per interop message and hint misses; the scan boundary
            // folded into TapeOwner.find behind the miss boundary (V1: 26 -> 25).
            Map.entry("TapeValue.java", 25),
            Map.entry("TapeBorrow.java", 1), // Borrowed-result assumption.
            // Key preparation from a String or from Myna's prepared Utf8Key (V1: 1 -> 2).
            Map.entry("TapeKey.java", 2),
            Map.entry("TapeText.java", 4), // Sink-side materialization: String, TruffleString, copy, compare.
            Map.entry("TapeTextArena.java", 1), // TruffleString over a session text arena (sink side).
            Map.entry("TruffleSink.java", 9), // Reference guest sink: tree building and key interning.
            // The session and stream entry points JsonSession (13) and JsonStream (10) held: the
            // shared bookkeeping moved here, the JSON-only ones stayed below.
            Map.entry("TapeSession.java", 10), // parse ×4, with, decode ×2, validate, criticalDecodes, close.
            Map.entry("TapeStream.java", 8), // feed ×3, finish, next ×2, retain, close: native calls.
            Map.entry("TapeFormat.java", 1), // Alias-target failure, built outside compiled alias reads.
            // JSON: native downcalls, slow-path conversions and allocation stay out of compiled reads.
            Map.entry("JsonJvm.java", 7), // FFM downcalls, arena management and openSession (×2).
            Map.entry("JsonMessage.java", 2),
            Map.entry("JsonPlan.java", 4), // Plan compilation and decode entry points.
            Map.entry("JsonSvm.java", 4), // Static-linked native calls.
            Map.entry("JvmTypedOwner.java", 3),
            Map.entry("SvmTypedOwner.java", 1),
            Map.entry("JsonSession.java", 3), // open ×2 and stream; the rest is TapeSession's.
            Map.entry("JsonStream.java", 2), // skip and truncatedBytes; the rest is TapeStream's.
            // CSV: diagnostics, materialization and the export walk stay out of compiled reads.
            Map.entry("CsvScanner.java", 2), // Static syntax and limit failures.
            Map.entry("CsvBatch.java", 2), // Static STRING materialization and the export walk.
            Map.entry("CsvRow.java", 1), // Static typed-read contract failure.
            Map.entry("CsvText.java", 2), // Sink-side String and TruffleString copies.
            Map.entry("Numbers.java", 1), // Static Double.parseDouble fallback.
            Map.entry("CsvMapping.java", 1)); // Static closed-mapping failure.
    Pattern limit = Pattern.compile("limit\\s*=\\s*\"(\\d+)\"");
    Map<String, Integer> exportedMessages =
        new HashMap<>(
            Map.of(
                "ProtoRow.java",
                5,
                "AbstractRow.java",
                8,
                "Batch.java",
                4,
                "StaticLayout.java",
                5,
                "DataArray.java",
                4,
                "DataNull.java",
                1,
                "Missing.java",
                1,
                "ExactNumber.java",
                16,
                "DynamicRecord.java",
                4,
                "JdbcCursor.java",
                9));
    exportedMessages.put("GeneratedMessage.java", 5);
    exportedMessages.put("TapeValue.java", 29);
    exportedMessages.put("TypedArray.java", 4);
    exportedMessages.put("TypedRecord.java", 6);
    exportedMessages.put("CsvBatch.java", 4);
    exportedMessages.put("CsvRow.java", 6);
    Set<String> accessSurfaces = exportedMessages.keySet();
    Set<String> seenSurfaces = new HashSet<>();
    int checked = 0;
    for (String module :
        new String[] {
          "core",
          "jdbc",
          "jdbc-sqlite",
          "jdbc-mysql",
          "jdbc-postgresql",
          "protobuf",
          "protobuf-runtime",
          "json",
          "tape",
          "csv"
        }) {
      try (var paths = Files.walk(root.resolve(module + "/src/main/java"))) {
        for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
          String source = Files.readString(path);
          if (source.contains("@ExportLibrary(")) {
            String surface = path.getFileName().toString();
            assertTrue(
                accessSurfaces.contains(surface),
                "Add a JIT matrix and coverage contract for new exported surface: " + path);
            assertEquals(
                Objects.requireNonNull(exportedMessages.get(surface)).intValue(),
                source.split("@ExportMessage", -1).length - 1,
                "Update the JIT operation matrix for changed exported messages: " + path);
            seenSurfaces.add(surface);
          }
          assertFalse(source.contains("SuppressWarnings(\"NullAway"), path.toString());
          assertFalse(source.contains("@NullUnmarked"), path.toString());
          assertTrue(
              Files.readString(path.resolveSibling("package-info.java"))
                  .contains("@org.jspecify.annotations.NullMarked"),
              path.toString());
          int boundaries = source.split("@TruffleBoundary", -1).length - 1;
          assertEquals(
              budgets.getOrDefault(path.getFileName().toString(), 0).intValue(),
              boundaries,
              "Review the boundary budget for " + path);
          var matches = limit.matcher(source);
          while (matches.find())
            assertTrue(Integer.parseInt(matches.group(1)) <= 3, "PIC budget: " + path);
          checked++;
        }
      }
    }
    assertEquals(accessSurfaces, seenSurfaces, "Exported surface inventory changed");
    assertTrue(checked >= 18, "Missing policy inputs");
    String policy = Files.readString(root.resolve("bazel/java.bzl"));
    assertTrue(policy.contains("-Xep:NullAway:ERROR"));
    assertTrue(policy.contains("-XepOpt:NullAway:JSpecifyMode=true"));
    assertTrue(policy.contains("plugins = plugins + [\"//bazel:nullaway\"]"));
  }
}
