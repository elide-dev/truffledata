package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.TapeFormat;
import org.jspecify.annotations.Nullable;

/** Detached metadata for a document. Offsets identify header words in the native compact tape. */
public final class YamlMetadata {
  final TapeFormat format;
  final int[] words;
  final String[] tags;
  final long[] anchors;
  final String[] names;
  final YamlDirectives directives;

  YamlMetadata(
      int[] words,
      String[] tags,
      long[] anchors,
      String[] names,
      YamlDirectives directives,
      long expansionBudget) {
    this.words = words;
    this.tags = tags;
    this.anchors = anchors;
    this.names = names;
    this.directives = directives;
    this.format =
        TapeFormat.of("YAML")
            .withMetadata(words)
            .withEvents(anchors, names, tags, expansionBudget);
  }

  public int style(int node) {
    return words[node] & 7;
  }

  public int keyKind(int node) {
    return (words[node] >>> 5) & 7;
  }

  public boolean isAlias(int node) {
    return (words[node] & 16) != 0;
  }

  public @Nullable String tag(int node) {
    int index = words[node] >>> 8;
    return isAlias(node) || index == 0 ? null : tags[index - 1];
  }

  public @Nullable String anchor(int node) {
    for (int i = 0; i < anchors.length; i++) if (anchors[i] == node) return names[i];
    return null;
  }
}
