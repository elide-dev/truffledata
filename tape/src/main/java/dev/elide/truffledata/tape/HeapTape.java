package dev.elide.truffledata.tape;

import static dev.elide.truffledata.tape.TapeReader.*;

import dev.elide.myna.values.ValueTape;
import com.oracle.truffle.api.memory.ByteArraySupport;
import java.util.Arrays;

/** Detached compact words, directories and only the input/pool ranges referenced by the subtree. */
public final class HeapTape implements TapeReader {
  private static final ByteArraySupport WORD = ByteArraySupport.littleEndian();
  private final long[] words, directory;
  private final byte[] pool, input;
  private final int[] meta;
  private final int flags;
  HeapTape(long[] words, byte[] pool, byte[] input, long[] directory, int[] meta, int flags) {
    this(words, pool, Arrays.copyOf(input, Math.addExact(input.length, 7)), directory, meta, flags, true);
  }
  private HeapTape(long[] words, byte[] pool, byte[] input, long[] directory, int[] meta, int flags,
      boolean padded) {
    this.words = words; this.pool = pool; this.input = input; this.directory = directory; this.meta = meta;
    this.flags = flags;
  }
  @Override public long readWord(long index) { return words[(int) index]; }
  @Override public byte textByte(boolean owned, long offset) { return (owned ? pool : input)[(int) offset]; }
  @Override public void copyText(boolean owned, long offset, byte[] output, int at, int length) {
    if (offset < 0 || offset > (owned ? poolSize() : inputSize()) - length)
      throw new IndexOutOfBoundsException("Tape text copy");
    System.arraycopy(owned ? pool : input, Math.toIntExact(offset), output, at, length);
  }
  @Override public long textWord(boolean owned, long offset, int length) {
    if (length == 0) return 0;
    byte[] bytes = owned ? pool : input;
    int at = (int) offset;
    // Every input has seven private padding bytes. A valid short key therefore needs one
    // word load even at the logical end; the mask excludes padding from comparison.
    if (!owned || at <= bytes.length - 8) {
      long value = WORD.getLong(bytes, at);
      return length == 8 ? value : value & (-1L >>> (64 - length * 8));
    }
    // A snapshot keeps exactly its referenced bytes. Its final short word has no padding;
    // bounded explicit loads avoid leaving a byte-copy loop in compiled member reads.
    long value = bytes[at] & 255L;
    if (length > 1) value |= (bytes[at + 1] & 255L) << 8;
    if (length > 2) value |= (bytes[at + 2] & 255L) << 16;
    if (length > 3) value |= (bytes[at + 3] & 255L) << 24;
    if (length > 4) value |= (bytes[at + 4] & 255L) << 32;
    if (length > 5) value |= (bytes[at + 5] & 255L) << 40;
    if (length > 6) value |= (bytes[at + 6] & 255L) << 48;
    if (length > 7) value |= (bytes[at + 7] & 255L) << 56;
    return value;
  }

  @Override public long directoryEntry(long index) { return directory[(int) index]; }
  @Override public int metadata(long index) { return meta.length == 0 ? 0 : meta[(int) index]; }
  @Override public void copyWords(long from, long[] output, int at, int count) {
    TapeReader.checkCopy(from, words.length, output.length, at, count, "Tape word copy");
    System.arraycopy(words, (int) from, output, at, count);
  }
  @Override public void copyDirectory(long from, long[] output, int at, int count) {
    TapeReader.checkCopy(from, directory.length, output.length, at, count, "Tape directory copy");
    System.arraycopy(directory, (int) from, output, at, count);
  }
  @Override public void copyMetadata(long from, int[] output, int at, int count) {
    TapeReader.checkCopy(from, words.length, output.length, at, count, "Tape metadata copy");
    if (meta.length == 0) Arrays.fill(output, at, at + count, 0);
    else System.arraycopy(meta, (int) from, output, at, count);
  }

  /**
   * Detaches the subtree at {@code root}: the words are read from the source once, in bulk, and
   * both the extent scan and the rebase run over that heap copy (WS7). The retained input is
   * trimmed to the bytes the subtree references, every text, scalar and container word is
   * re-based to the copy, and the subtree's directory entries and metadata are copied in bulk.
   */
  static HeapTape copy(TapeReader source, long root) {
    long end = source.next(root);
    int count = Math.toIntExact(end - root);
    int flags = source.flags();
    long[] words = new long[count];
    source.copyWords(root, words, 0, count);
    long inputFirst = source.inputSize(), inputLast = 0, poolFirst = source.poolSize(), poolLast = 0;
    long dirFirst = source.directorySize(), dirLast = 0;
    for (int i = 0; i < count;) {
      long w = words[i];
      int k = kind(w), len = field(w);
      boolean wide = (w & WIDE) != 0;
      long second = ((k == 6 || k == 7 || k == 9) && wide) ? words[i + 1] : 0;
      long span = rawSpan(w, second, flags);
      if (span != 0) { inputFirst = Math.min(inputFirst, span >>> 32); inputLast = Math.max(inputLast, (span >>> 32) + (span & 0xffffffffL)); }
      if (k == 6 || k == 7) {
        if ((w & OWNED) != 0) { poolFirst = Math.min(poolFirst, low(w)); poolLast = Math.max(poolLast, low(w) + len); }
        else { inputFirst = Math.min(inputFirst, low(w)); inputLast = Math.max(inputLast, low(w) + len); }
      }
      long dir = k == 9 ? second >>> 32 : 0;
      if (dir != 0) {
        dirFirst = Math.min(dirFirst, dir - 1);
        dirLast = Math.max(dirLast, dir - 1 + (len == MAX_COUNT ? source.length(root + i) : len));
      }
      i += wide ? 2 : 1;
    }
    if (inputLast == 0) inputFirst = 0;
    if (poolLast == 0) poolFirst = 0;
    if (dirLast == 0) dirFirst = 0;
    byte[] input = new byte[Math.addExact(Math.toIntExact(inputLast - inputFirst), 7)], pool = new byte[Math.toIntExact(poolLast - poolFirst)];
    source.copyText(false, inputFirst, input, 0, input.length - 7);
    source.copyText(true, poolFirst, pool, 0, pool.length);
    long[] dirs = new long[Math.toIntExact(dirLast - dirFirst)];
    source.copyDirectory(dirFirst, dirs, 0, dirs.length);
    int[] meta = (flags & 2) == 0 ? new int[0] : new int[count];
    if (meta.length != 0) source.copyMetadata(root, meta, 0, count);
    for (int i = 0; i < count;) {
      long w = words[i];
      boolean wide = (w & WIDE) != 0;
      int k = kind(w);
      if (k == 8 || k == 9) {
        w = (w & ~0xffffffffL) | (low(w) - root);
        if (wide) {
          long d = words[i + 1] >>> 32;
          words[i + 1] = d == 0 ? 0 : (d - dirFirst) << 32; // container source offsets are not exposed
        }
      } else if (k == 10) {
        long target = low(w);
        w = (w & ~0xffffffffL) | (target < root ? 0xffffffffL : target - root);
      } else if (k <= 7) {
        long base = (w & OWNED) == 0 ? inputFirst : poolFirst;
        // Synthetic scalar values have no lexical span and therefore no input offset.
        if (k >= 6 || field(w) != 0) w = (w & ~0xffffffffL) | (low(w) - base);
        if (k >= 6 && wide && words[i + 1] != 0) words[i + 1] -= inputFirst << 32;
      }
      words[i] = w;
      i += wide ? 2 : 1;
    }
    return new HeapTape(words, pool, input, dirs, meta, flags, true);
  }

  ValueTape toMyna() {
    int[] mapping = new int[words.length + 1];
    int nodes = 0;
    for (long i = 0; i < nodeCount(); i = step(i)) mapping[(int) i] = nodes++;
    mapping[words.length] = nodes;
    long[] out = new long[nodes * 3];
    long[] dirs = new long[directory.length];
    for (long i = 0; i < nodeCount(); i = step(i)) {
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
    }
    return new HeapValueTape(out, pool.clone(), new byte[][] {Arrays.copyOf(input, input.length - 7)}, dirs, flags & 1);
  }
  @Override public long nodeCount() { return words.length; }
  @Override public long poolSize() { return pool.length; }
  @Override public long inputSize() { return input.length - 7; }
  @Override public long directorySize() { return directory.length; }
  @Override public int flags() { return flags; }
}
