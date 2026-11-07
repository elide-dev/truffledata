package dev.elide.truffledata.csv.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/** Eight bytes at a time: SWAR byte equality on little-endian longs, 64-byte bitmap blocks. */
public final class SwarIndexer extends CsvIndexer {
  private static final VarHandle LONGS =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
  private static final long LOW7 = 0x7F7F_7F7F_7F7F_7F7FL;
  private static final long LF = 0x0A0A_0A0A_0A0A_0A0AL;
  private static final long CR = 0x0D0D_0D0D_0D0D_0D0DL;
  private static final long GATHER = 0x0102_0408_1020_4080L;

  private final long d;
  private final long q;
  private final BlockStep stage = new BlockStep();
  private final byte[] tail = new byte[64];
  private final byte pad;

  public SwarIndexer(byte delimiter, byte quote, boolean quoting) {
    super(delimiter, quote, quoting);
    d = 0x0101_0101_0101_0101L * (delimiter & 0xFF);
    q = 0x0101_0101_0101_0101L * (quote & 0xFF);
    pad = BlockStep.pad(delimiter, quote, quoting);
  }

  @Override
  public String name() {
    return "java-swar";
  }

  /** High bit of each byte of {@code x} that equals the matching byte of {@code pattern}. */
  private static long zeros(long x, long pattern) {
    long t = x ^ pattern;
    return ~(((t & LOW7) + LOW7) | t | LOW7);
  }

  /** The eight high bits of a {@link #zeros} result as a byte, byte 0 lowest. */
  private static long gather(long z) {
    return ((z >>> 7) * GATHER) >>> 56;
  }

  @Override
  public int index(byte[] input, int from, int length, int[] out) {
    CsvIndex.checkCall(input, from, length, out);
    final BlockStep s = stage;
    s.load(carry);
    final long d = this.d, q = this.q;
    final boolean quoting = this.quoting;
    int n = 0;
    int i = 0;
    for (; i + 64 <= length; i += 64) {
      long sep = 0, quotes = 0;
      int p = from + i;
      for (int k = 0; k < 8; k++) {
        long x = (long) LONGS.get(input, p + 8 * k);
        sep |= gather(zeros(x, d) | zeros(x, LF) | zeros(x, CR)) << (8 * k);
        if (quoting) quotes |= gather(zeros(x, q)) << (8 * k);
      }
      n = s.block(sep, quotes, -1L, i, out, n);
    }
    if (i < length) {
      int rest = length - i;
      byte[] t = tail;
      System.arraycopy(input, from + i, t, 0, rest);
      java.util.Arrays.fill(t, rest, 64, pad);
      long sep = 0, quotes = 0;
      for (int k = 0; k < 8; k++) {
        long x = (long) LONGS.get(t, 8 * k);
        sep |= gather(zeros(x, d) | zeros(x, LF) | zeros(x, CR)) << (8 * k);
        if (quoting) quotes |= gather(zeros(x, q)) << (8 * k);
      }
      n = s.block(sep, quotes, (1L << rest) - 1, i, out, n);
    }
    carry = s.carry();
    return n;
  }
}
