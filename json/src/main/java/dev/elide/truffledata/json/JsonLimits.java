package dev.elide.truffledata.json;

import dev.elide.truffledata.json.generated.JsonConstants;

/** Native decode budgets. Input and index bytes bound allocations; nodes, text and depth bound the tape. */
public record JsonLimits(
    long maxInputBytes, long maxIndexBytes, long maxNodes, long maxTextBytes, int maxDepth) {
  public static final JsonLimits DEFAULT =
      new JsonLimits(64 << 20, 64 << 20, 1_000_000, 64 << 20, JsonConstants.MAX_DEPTH);

  public JsonLimits {
    if (maxInputBytes <= 0 || maxIndexBytes <= 0 || maxNodes <= 0 || maxTextBytes <= 0)
      throw new IllegalArgumentException("JSON limits must be positive");
    // json.seam MAX_DEPTH: the largest depth the native decoder accepts.
    if (maxDepth <= 0 || maxDepth > JsonConstants.MAX_DEPTH)
      throw new IllegalArgumentException(
          "JSON depth limit must be within 1.." + JsonConstants.MAX_DEPTH);
  }
}
