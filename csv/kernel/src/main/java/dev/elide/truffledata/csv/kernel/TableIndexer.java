package dev.elide.truffledata.csv.kernel;

/**
 * Branch-light scalar indexer: a byte-class table builds each block's bitmaps without
 * data-dependent branches, then the shared bitmap step emits entries.
 */
public final class TableIndexer extends CsvIndexer {
  private final byte[] classes = new byte[256]; // bit 0 separator, bit 1 quote
  private final BlockStep stage = new BlockStep();
  private final byte[] tail = new byte[64];
  private final byte pad;

  public TableIndexer(byte delimiter, byte quote, boolean quoting) {
    super(delimiter, quote, quoting);
    classes[delimiter & 0xFF] = 1;
    classes['\n'] = 1;
    classes['\r'] = 1;
    if (quoting) classes[quote & 0xFF] = 2;
    pad = BlockStep.pad(delimiter, quote, quoting);
  }

  @Override
  public String name() {
    return "java-table";
  }

  @Override
  public int index(byte[] input, int from, int length, int[] out) {
    CsvIndex.checkCall(input, from, length, out);
    final BlockStep s = stage;
    final byte[] cls = classes;
    s.load(carry);
    int n = 0;
    int i = 0;
    for (; i + 64 <= length; i += 64) {
      long sep = 0, quotes = 0;
      int p = from + i;
      for (int k = 0; k < 64; k++) {
        long c = cls[input[p + k] & 0xFF];
        sep |= (c & 1) << k;
        quotes |= (c >>> 1) << k;
      }
      n = s.block(sep, quotes, -1L, i, out, n);
    }
    if (i < length) {
      int rest = length - i;
      byte[] t = tail;
      System.arraycopy(input, from + i, t, 0, rest);
      java.util.Arrays.fill(t, rest, 64, pad);
      long sep = 0, quotes = 0;
      for (int k = 0; k < 64; k++) {
        long c = cls[t[k] & 0xFF];
        sep |= (c & 1) << k;
        quotes |= (c >>> 1) << k;
      }
      n = s.block(sep, quotes, (1L << rest) - 1, i, out, n);
    }
    carry = s.carry();
    return n;
  }
}
