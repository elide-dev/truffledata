package dev.elide.truffledata.yaml;

/** Per-document native budgets, including a bound on alias materialization. */
public record YamlLimits(
    long maxInputBytes,
    long maxIndexBytes,
    long maxNodes,
    long maxTextBytes,
    int maxDepth,
    long maxAliases,
    long maxAliasExpansion) {
  public static final YamlLimits DEFAULT =
      new YamlLimits(64L << 20, 64L << 20, 1_000_000, 64L << 20, 128, 10_000, 1_000_000);

  public YamlLimits {
    if (maxInputBytes <= 0
        || maxIndexBytes <= 0
        || maxNodes <= 0
        || maxTextBytes <= 0
        || maxAliases < 0
        || maxAliasExpansion <= 0
        || maxDepth <= 0
        || maxDepth > 128) throw new IllegalArgumentException("Invalid YAML resource limits");
  }
}
