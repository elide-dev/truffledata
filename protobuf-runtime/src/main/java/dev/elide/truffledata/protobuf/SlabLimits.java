package dev.elide.truffledata.protobuf;

/** Limits checked before slab allocation and before entering a nested record. */
public record SlabLimits(int maxDepth, int maxSlabBytes, int criticalBytes) {
  /** Maximum pinned/critical call size; larger inputs and output slabs use native scratch. */
  public static final int CRITICAL_BYTES = 65536;

  public SlabLimits(int maxDepth, int maxSlabBytes) {
    this(maxDepth, maxSlabBytes, CRITICAL_BYTES);
  }

  public SlabLimits {
    if (maxDepth < 0
        || maxDepth > 128
        || maxSlabBytes < 0
        || maxSlabBytes > Integer.MAX_VALUE - 8
        || criticalBytes < 0
        || criticalBytes > CRITICAL_BYTES)
      throw new IllegalArgumentException("Invalid slab limits");
  }

  /** The default owning-slab budget: sixteen input bytes plus 4 KiB, capped at array size. */
  public static SlabLimits forInput(Layout layout, int inputBytes) {
    if (inputBytes < 0) throw new IllegalArgumentException("Negative input size");
    return new SlabLimits(
        layout.plan(0).maxDepth() - 1,
        (int) Math.min(Integer.MAX_VALUE - 8L, 16L * inputBytes + 4096));
  }
}
