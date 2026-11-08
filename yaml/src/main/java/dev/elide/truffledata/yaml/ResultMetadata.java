package dev.elide.truffledata.yaml;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Copies format metadata while its native result is live. */
final class ResultMetadata {
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
    long meta = m.pointer(result, 96);
    for (int i = 0; i < words.length; i++) words[i] = m.integer(meta, 4L * i);
    long strings = m.pointer(result, 152);
    long tagTable = m.pointer(result, 104);
    String[] tags = new String[Math.toIntExact(m.word(result, 112))];
    for (int i = 0; i < tags.length; i++)
      tags[i] =
          text(
              m,
              strings,
              Integer.toUnsignedLong(m.integer(tagTable, 8L * i)),
              m.integer(tagTable, 8L * i + 4));
    long anchorTable = m.pointer(result, 120);
    long[] anchors = new long[Math.toIntExact(m.word(result, 128))];
    String[] names = new String[anchors.length];
    for (int i = 0; i < anchors.length; i++) {
      anchors[i] = m.word(anchorTable, 16L * i);
      names[i] =
          text(
              m,
              strings,
              Integer.toUnsignedLong(m.integer(anchorTable, 16L * i + 8)),
              m.integer(anchorTable, 16L * i + 12));
    }
    long aliasTable = m.pointer(result, 136);
    long[] aliases = new long[Math.toIntExact(m.word(result, 144))];
    for (int i = 0; i < aliases.length; i++) aliases[i] = m.word(aliasTable, 8L * i);
    long directiveTable = m.pointer(result, 168);
    int count = Math.toIntExact(m.word(result, 176));
    Map<String, String> directives = new LinkedHashMap<>();
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
    return new YamlMetadata(
        words,
        tags,
        anchors,
        names,
        aliases,
        new YamlDirectives(m.integer(result, 200), directives),
        expansionBudget);
  }
}
