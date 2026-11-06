package dev.elide.truffledata.csv.kernel.vector;

import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.BlockStep;
import dev.elide.truffledata.csv.kernel.CsvIndex;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

/**
 * Java Vector API indexer (incubator module {@code jdk.incubator.vector}): lane comparisons per
 * 64-byte block, {@code VectorMask.toLong} bitmaps, then the shared bitmap step. Experimental:
 * kept only where measurements show it pays on the runtime in use.
 */
public final class VectorIndexer extends CsvIndexer {
  private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
  private static final int LANES = SPECIES.length();

  private final BlockStep step = new BlockStep();
  private final byte[] tail = new byte[64];
  private final byte pad;

  public VectorIndexer(byte delimiter, byte quote, boolean quoting) {
    super(delimiter, quote, quoting);
    if (64 % LANES != 0) throw new IllegalStateException("Vector width " + LANES);
    pad = BlockStep.pad(delimiter, quote, quoting);
  }

  @Override
  public String name() {
    return "java-vector" + LANES * 8;
  }

  @Override
  public int index(byte[] input, int from, int length, int[] out) {
    CsvIndex.checkCall(input, from, length, out);
    final BlockStep s = step;
    s.load(carry);
    final byte d = delimiter, q = quote;
    final boolean quoting = this.quoting;
    int n = 0;
    int i = 0;
    for (; i + 64 <= length; i += 64) {
      long sep = 0, quotes = 0;
      for (int k = 0; k < 64; k += LANES) {
        ByteVector v = ByteVector.fromArray(SPECIES, input, from + i + k);
        VectorMask<Byte> m = v.eq(d).or(v.eq((byte) '\n')).or(v.eq((byte) '\r'));
        sep |= m.toLong() << k;
        if (quoting) quotes |= v.eq(q).toLong() << k;
      }
      n = s.block(sep, quotes, -1L, i, out, n);
    }
    if (i < length) {
      int rest = length - i;
      byte[] t = tail;
      System.arraycopy(input, from + i, t, 0, rest);
      java.util.Arrays.fill(t, rest, 64, pad);
      long sep = 0, quotes = 0;
      for (int k = 0; k < 64; k += LANES) {
        ByteVector v = ByteVector.fromArray(SPECIES, t, k);
        VectorMask<Byte> m = v.eq(d).or(v.eq((byte) '\n')).or(v.eq((byte) '\r'));
        sep |= m.toLong() << k;
        if (quoting) quotes |= v.eq(q).toLong() << k;
      }
      n = s.block(sep, quotes, (1L << rest) - 1, i, out, n);
    }
    carry = s.carry();
    return n;
  }
}
