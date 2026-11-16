package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.yaml.generated.YamlConstants.Meta;
import org.jspecify.annotations.Nullable;

/** Detached metadata for a document. Offsets identify header words in the native compact tape. */
public final class YamlMetadata {
  private static final TapeFormat FORMAT = TapeFormat.of("YAML");
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
        FORMAT.withMetadata(words)
            .withEvents(anchors, names, tags, expansionBudget);
  }

  public int style(int node) {
    return (words[node] & Meta.STYLE_MASK) >>> Meta.STYLE_SHIFT;
  }

  public int keyKind(int node) {
    return (words[node] & Meta.KEY_KIND_MASK) >>> Meta.KEY_KIND_SHIFT;
  }

  public boolean isAlias(int node) {
    return (words[node] & Meta.ALIAS) != 0;
  }

  public @Nullable String tag(int node) {
    int index = (words[node] & Meta.TAG_MASK) >>> Meta.TAG_SHIFT;
    return isAlias(node) || index == 0 ? null : tags[index - 1];
  }

  public @Nullable String anchor(int node) {
    for (int i = 0; i < anchors.length; i++) if (anchors[i] == node) return names[i];
    return null;
  }
}
