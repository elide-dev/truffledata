package dev.elide.truffledata.yaml;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Copies format metadata while its native result is live. */
final class ResultMetadata {
  private static final String[] NO_TEXT = new String[0];
  private static final long[] NO_ANCHORS = new long[0];
  private static final YamlDirectives NO_DIRECTIVES = new YamlDirectives(0, Map.of());
  interface Memory {
    long pointer(long base, int offset);

    long word(long base, long offset);

    int integer(long base, long offset);

    byte octet(long base, long offset);
  }

  private ResultMetadata() {}

  static String text(Memory memory, long strings, long offset, int length) {
    byte[] utf8 = new byte[length];
    for (int i = 0; i < length; i++) utf8[i] = memory.octet(strings, offset + i);
    return new String(utf8, StandardCharsets.UTF_8);
  }

  static YamlMetadata read(Memory m, long result, long nodeCount, long expansionBudget) {
    int[] words = new int[Math.toIntExact(nodeCount)];
    long meta = m.pointer(result, 72);
    for (int i = 0; i < words.length; i++) words[i] = m.integer(meta, 4L * i);
    long strings = m.pointer(result, 112);
    long tagTable = m.pointer(result, 80);
    int tagCount = Math.toIntExact(m.word(result, 88));
    String[] tags = tagCount == 0 ? NO_TEXT : new String[tagCount];
    for (int i = 0; i < tags.length; i++)
      tags[i] =
          text(
              m,
              strings,
              Integer.toUnsignedLong(m.integer(tagTable, 8L * i)),
              m.integer(tagTable, 8L * i + 4));
    long anchorTable = m.pointer(result, 96);
    int anchorCount = Math.toIntExact(m.word(result, 104));
    long[] anchors = anchorCount == 0 ? NO_ANCHORS : new long[anchorCount];
    String[] names = anchorCount == 0 ? NO_TEXT : new String[anchorCount];
    for (int i = 0; i < anchors.length; i++) {
      anchors[i] = m.word(anchorTable, 16L * i);
      names[i] =
          text(
              m,
              strings,
              Integer.toUnsignedLong(m.integer(anchorTable, 16L * i + 8)),
              m.integer(anchorTable, 16L * i + 12));
    }
    long directiveTable = m.pointer(result, 128);
    int count = Math.toIntExact(m.word(result, 136));
    Map<String, String> directives = count == 0 ? Map.of() : new LinkedHashMap<>();
    for (int i = 0; i < count; i++) {
      long at = 16L * i;
      directives.put(
          text(
              m,
              strings,
              Integer.toUnsignedLong(m.integer(directiveTable, at)),
              m.integer(directiveTable, at + 4)),
          text(
              m,
              strings,
              Integer.toUnsignedLong(m.integer(directiveTable, at + 8)),
              m.integer(directiveTable, at + 12)));
    }
    int version = m.integer(result, 160);
    return new YamlMetadata(
        words,
        tags,
        anchors,
        names,
        count == 0 && version == 0 ? NO_DIRECTIVES : new YamlDirectives(version, directives),
        expansionBudget);
  }
}
