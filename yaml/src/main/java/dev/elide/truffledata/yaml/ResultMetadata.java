package dev.elide.truffledata.yaml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Assembles format metadata copied from a live native result. Each backend reads the result's
 * tables (yaml_meta, tags, anchors, directives and their strings) through its own generated
 * record and counted-view accessors: {@link SvmSessionBackend#metadata} and {@link
 * JvmSessionBackend#metadata}.
 */
final class ResultMetadata {
  private static final String[] NO_TEXT = new String[0];
  private static final long[] NO_ANCHORS = new long[0];
  private static final YamlDirectives NO_DIRECTIVES = new YamlDirectives(0, Map.of());

  private ResultMetadata() {}

  static String[] texts(int count) {
    return count == 0 ? NO_TEXT : new String[count];
  }

  static long[] anchors(int count) {
    return count == 0 ? NO_ANCHORS : new long[count];
  }

  static Map<String, String> directives(int count) {
    return count == 0 ? Map.of() : new LinkedHashMap<>();
  }

  static YamlMetadata create(
      int[] words,
      String[] tags,
      long[] anchors,
      String[] names,
      Map<String, String> directives,
      int version,
      long expansionBudget) {
    return new YamlMetadata(
        words,
        tags,
        anchors,
        names,
        directives.isEmpty() && version == 0
            ? NO_DIRECTIVES
            : new YamlDirectives(version, directives),
        expansionBudget);
  }
}
