package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
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
            Map.entry("Values.java", 5),
            Map.entry("DataLibrary.java", 1),
            Map.entry("Schema.java", 1),
            Map.entry("Batch.java", 2),
            Map.entry("AbstractRow.java", 1),
            Map.entry("ExactNumber.java", 12),
            Map.entry("StaticLayout.java", 7),
            Map.entry("JdbcCursor.java", 7),
            Map.entry("JdbcSource.java", 6),
            Map.entry("ProtobufAdapter.java", 5),
            Map.entry("NativeMetadata.java", 1),
            Map.entry("JdbcContracts.java", 1),
            Map.entry("JdbcContract.java", 1),
            Map.entry("SqliteSource.java", 1),
            Map.entry("SqliteAdapter.java", 1),
            Map.entry("MysqlAdapter.java", 1),
            Map.entry("PostgresqlAdapter.java", 1),
            Map.entry("JdbcValues.java", 1));
    Pattern limit = Pattern.compile("limit\\s*=\\s*\"(\\d+)\"");
    Map<String, Integer> exportedMessages =
        Map.of(
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
            9);
    Set<String> accessSurfaces = exportedMessages.keySet();
    Set<String> seenSurfaces = new HashSet<>();
    int checked = 0;
    for (String module :
        new String[] {"core", "jdbc", "jdbc-sqlite", "jdbc-mysql", "jdbc-postgresql", "protobuf"}) {
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
