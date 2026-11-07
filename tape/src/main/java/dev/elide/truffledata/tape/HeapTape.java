package dev.elide.truffledata.tape;

import dev.elide.myna.values.ValueTape;

/** Detached compact words, directories and only the input/pool ranges referenced by the subtree. */
public final class HeapTape extends TapeReader {
  private final long[] words, directory;
  private final byte[] pool, input;
  private final int[] meta;
  HeapTape(long[] words, byte[] pool, byte[] input, long[] directory, int[] meta, int flags) {
    this.words = words; this.pool = pool; this.input = input; this.directory = directory; this.meta = meta;
    count = words.length; poolBytes = pool.length; inputBytes = input.length; directoryCount = directory.length; this.flags = flags;
  }
  @Override protected long readWord(long index) { return words[Math.toIntExact(index)]; }
  @Override protected byte textByte(boolean owned, long offset) { return (owned ? pool : input)[Math.toIntExact(offset)]; }
  @Override protected long textWord(boolean owned, long offset, int length) {
    byte[] bytes = owned ? pool : input;
    long value = 0;
    for (int j = 0; j < length; j++) value |= (bytes[Math.toIntExact(offset) + j] & 255L) << (j * 8);
    return value;
  }
  @Override public long directoryEntry(long index) { return directory[Math.toIntExact(index)]; }
  @Override public int metadata(long index) { return meta.length == 0 ? 0 : meta[Math.toIntExact(index)]; }

  static HeapTape copy(TapeReader source, long root) {
    long end = source.next(root);
    long inputFirst = source.inputBytes, inputLast = 0, poolFirst = source.poolBytes, poolLast = 0;
    long dirFirst = source.directoryCount, dirLast = 0;
    for (long i = root; i < end; i = source.step(i)) {
      long w = source.word(i), span = source.span(i);
      int k = kind(w), len = field(w);
      if (span != 0) { inputFirst = Math.min(inputFirst, span >>> 32); inputLast = Math.max(inputLast, (span >>> 32) + (span & 0xffffffffL)); }
      if (k == 6 || k == 7) {
        if ((w & OWNED) != 0) { poolFirst = Math.min(poolFirst, low(w)); poolLast = Math.max(poolLast, low(w) + len); }
        else { inputFirst = Math.min(inputFirst, low(w)); inputLast = Math.max(inputLast, low(w) + len); }
      }
      long dir = source.directory(i);
      if (dir != 0) { dirFirst = Math.min(dirFirst, dir - 1); dirLast = Math.max(dirLast, dir - 1 + source.length(i)); }
    }
    if (inputLast == 0) inputFirst = 0;
    if (poolLast == 0) poolFirst = 0;
    if (dirLast == 0) dirFirst = 0;
    byte[] input = new byte[Math.toIntExact(inputLast - inputFirst)], pool = new byte[Math.toIntExact(poolLast - poolFirst)];
    for (int i = 0; i < input.length; i++) input[i] = source.textByte(false, inputFirst + i);
    for (int i = 0; i < pool.length; i++) pool[i] = source.textByte(true, poolFirst + i);
    long[] words = new long[Math.toIntExact(end - root)], dirs = new long[Math.toIntExact(dirLast - dirFirst)];
    int[] meta = (source.flags & 2) == 0 ? new int[0] : new int[words.length];
    for (int i = 0; i < dirs.length; i++) dirs[i] = source.directoryEntry(dirFirst + i);
    for (long i = root; i < end; i = source.step(i)) {
      int at = Math.toIntExact(i - root);
      long w = source.word(i), second = (w & WIDE) == 0 ? 0 : source.word(i + 1);
      int k = kind(w);
      if (k == 8 || k == 9) {
        w = (w & ~0xffffffffL) | (low(w) - root);
        long d = second >>> 32;
        second = (d == 0 ? 0 : (d - dirFirst) << 32); // container source offsets are not exposed
      } else if (k == 10) {
        long target = low(w);
        w = (w & ~0xffffffffL) | (target < root ? 0xffffffffL : target - root);
      } else if (k >= 3 && k <= 7) {
        long base = (w & OWNED) == 0 ? inputFirst : poolFirst;
        // Synthetic numeric values have no lexical span and therefore no input offset.
        if (k >= 6 || field(w) != 0) w = (w & ~0xffffffffL) | (low(w) - base);
        if ((w & OWNED) != 0 && second != 0) second -= inputFirst << 32;
      }
      words[at] = w;
      if ((w & WIDE) != 0) words[at + 1] = second;
      if (meta.length != 0) meta[at] = source.metadata(i);
    }
    return new HeapTape(words, pool, input, dirs, meta, source.flags);
  }

  ValueTape toMyna() {
    int[] mapping = new int[words.length + 1];
    int nodes = 0;
    for (long i = 0; i < count; i = step(i)) mapping[(int) i] = nodes++;
    mapping[words.length] = nodes;
    long[] out = new long[nodes * 3];
    int[] hashes = new int[nodes];
    long[] dirs = new long[directory.length];
    for (long i = 0; i < count; i = step(i)) {
      int n = mapping[(int) i], at = n * 3, k = kindAt(i);
      long w = word(i), len = length(i), payload = 0;
      int tag = k;
      if (k == ValueTape.STRING || k == ValueTape.DECIMAL) {
        tag |= ValueTape.REGION_TEXT;
        payload = ((w & OWNED) != 0 ? 0 : 1L << 32) | low(w);
      } else if (k == ValueTape.I64 || k == ValueTape.U64 || k == ValueTape.F64) payload = word(i + 1);
      else if (k == ValueTape.BOOL) payload = kind(w) == 2 ? 1 : 0;
      else if (k == ValueTape.REF) {
        long target = low(w);
        if (target >= i) throw new IllegalArgumentException("Reference outside the copied subtree");
        payload = mapping[Math.toIntExact(target)];
      }
      else if (k == ValueTape.OBJECT) {
        payload = directory(i);
        if (payload != 0) for (long e = payload - 1; e < payload - 1 + len; e++) {
          long entry = directoryEntry(e);
          dirs[(int) e] = ((long) (mapping[Math.toIntExact(i + (entry >>> 32))] - n) << 32) | (entry & 0xffffffffL);
        }
      }
      out[at] = tag | ((k == ValueTape.STRING || k == ValueTape.DECIMAL || k == ValueTape.OBJECT || k == ValueTape.ARRAY ? len : 0) << 32); out[at + 1] = payload; out[at + 2] = mapping[Math.toIntExact(next(i))];
      hashes[n] = (ascii(i) ? 0x80000000 : 0) | ((hash(i) & 0x7fff) << 16);
    }
    return new HeapValueTape(out, pool.clone(), new byte[][] {input.clone()}, hashes, dirs, flags & 1);
  }
}
