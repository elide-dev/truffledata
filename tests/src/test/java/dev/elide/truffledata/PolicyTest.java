package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
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
        Map.of(
            "Values.java",
            2,
            "Schema.java",
            1,
            "Batch.java",
            1,
            "AbstractRow.java",
            1,
            "ExactNumber.java",
            6,
            "StaticLayout.java",
            4,
            "JdbcCursor.java",
            5,
            "JdbcSource.java",
            4,
            "ProtobufAdapter.java",
            2);
    Pattern limit = Pattern.compile("limit\\s*=\\s*\"(\\d+)\"");
    int checked = 0;
    for (String module : new String[] {"core", "jdbc", "protobuf"}) {
      try (var paths = Files.walk(root.resolve(module + "/src/main/java"))) {
        for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
          String source = Files.readString(path);
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
    assertTrue(checked >= 18, "Missing policy inputs");
    String policy = Files.readString(root.resolve("bazel/java.bzl"));
    assertTrue(policy.contains("-Xep:NullAway:ERROR"));
    assertTrue(policy.contains("-XepOpt:NullAway:JSpecifyMode=true"));
    assertTrue(policy.contains("plugins = plugins + [\"//bazel:nullaway\"]"));
  }
}
