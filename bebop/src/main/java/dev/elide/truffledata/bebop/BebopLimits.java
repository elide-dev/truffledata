package dev.elide.truffledata.bebop;

/**
 * Local resource limits. {@code maxBytes} bounds one encoded value, {@code maxCount} one
 * collection, {@code maxDepth} container nesting, {@code maxWork} the elements and fields visited
 * (so collections of zero-byte elements are bounded), and {@code maxAllocation} an estimate in
 * bytes of what a decode retains.
 */
public record BebopLimits(int maxBytes, int maxCount, int maxDepth, long maxWork, long maxAllocation) {
  public static final BebopLimits DEFAULT =
      new BebopLimits(64 << 20, 1 << 20, 64, 1L << 24, 256L << 20);

  public BebopLimits {
    if (maxBytes <= 0 || maxCount <= 0 || maxDepth <= 0 || maxWork <= 0 || maxAllocation <= 0)
      throw new IllegalArgumentException("Invalid limits");
  }
}
