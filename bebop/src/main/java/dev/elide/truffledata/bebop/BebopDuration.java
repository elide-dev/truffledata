package dev.elide.truffledata.bebop;

import java.math.BigInteger;

/** A signed time span in seconds and nanoseconds. */
public record BebopDuration(long seconds, int nanos) {
  private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000);

  /**
   * The canonical form of an exact span given as any seconds and nanoseconds: the total is
   * computed exactly, then split with quotient and remainder truncated toward zero, so {@code (-1,
   * +500000000)} becomes {@code (0, -500000000)}. An explicit host adapter, never applied by the
   * codec: the strict encoder rejects a non-canonical pair and the decoder never repairs one.
   * Fails as a host-value error when the span does not fit.
   */
  public static BebopDuration normalized(long seconds, long nanos) {
    BigInteger[] parts =
        BigInteger.valueOf(seconds)
            .multiply(BILLION)
            .add(BigInteger.valueOf(nanos))
            .divideAndRemainder(BILLION);
    if (parts[0].bitLength() > 63) throw BebopException.value("Duration is out of range");
    return new BebopDuration(parts[0].longValue(), parts[1].intValue());
  }
}
