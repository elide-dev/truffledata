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
            Map.entry("ProtoPlan.java", 11), // Generated contract binding, outside reads.
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
            // JSON: native downcalls, slow-path conversions and allocation stay out of compiled reads.
            Map.entry("JavaSink.java", 9), // Embedder tree building and key interning, outside the tape walk.
            Map.entry("JsonAccess.java", 2),
            Map.entry("JsonArray.java", 1),
            Map.entry("JsonJvm.java", 5), // FFM downcalls and arena management.
            Map.entry("JsonMessage.java", 2),
            Map.entry("JsonOwner.java", 10), // Close, pinning, array index, member directories, export cursors.
            Map.entry("JsonPlan.java", 4), // Plan compilation and decode entry points.
            Map.entry("JsonRecord.java", 4),
            Map.entry("JsonSvm.java", 4), // Static-linked native calls.
            Map.entry("JsonTypedOwner.java", 3),
            Map.entry("JsonValue.java", 26), // Static (owner, node) slow paths per interop message and hint misses.
            Map.entry("JvmTypedOwner.java", 3),
            Map.entry("SvmTypedOwner.java", 1),
            Map.entry("JsonSession.java", 9), // Session entry points (open, parse, with, decode, retain, close).
            Map.entry("JsonBorrow.java", 1), // Borrowed-result assumption.
            Map.entry("JsonKey.java", 1), // Key preparation (UTF-8 encode and hash).
            Map.entry("JsonText.java", 4), // Sink-side materialization: String, TruffleString, copy, compare.
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
    exportedMessages.put("JsonValue.java", 29);
    exportedMessages.put("JsonArray.java", 4);
    exportedMessages.put("JsonRecord.java", 6);
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
