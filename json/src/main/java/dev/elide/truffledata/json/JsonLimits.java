package dev.elide.truffledata.json;

/** Native decode budgets. Input and index bytes bound allocations; nodes, text and depth bound the tape. */
public record JsonLimits(
    long maxInputBytes, long maxIndexBytes, long maxNodes, long maxTextBytes, int maxDepth) {
  public static final JsonLimits DEFAULT =
      new JsonLimits(64 << 20, 64 << 20, 1_000_000, 64 << 20, 128);

  public JsonLimits {
    if (maxInputBytes <= 0 || maxIndexBytes <= 0 || maxNodes <= 0 || maxTextBytes <= 0)
      throw new IllegalArgumentException("JSON limits must be positive");
    if (maxDepth <= 0 || maxDepth > 128)
      throw new IllegalArgumentException("JSON depth limit must be within 1..128");
  }
}
