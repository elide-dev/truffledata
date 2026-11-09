package dev.elide.truffledata.yaml;

/** Maximum document bytes and native carry-over buffer size. */
public record YamlStreamOptions(int maxDocumentBytes, int bufferBytes) {
  public static final YamlStreamOptions DEFAULT = new YamlStreamOptions(1 << 20, 1 << 20);

  public YamlStreamOptions {
    if (maxDocumentBytes <= 0 || maxDocumentBytes > Integer.MAX_VALUE / 2 || bufferBytes <= 0)
      throw new IllegalArgumentException("Invalid YAML stream bounds");
  }
}
