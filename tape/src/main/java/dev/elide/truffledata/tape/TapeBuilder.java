package dev.elide.truffledata.tape;

import java.util.Arrays;

/**
 * Java writer of the compact tape ({@code tape/native/ctape.h}): the same words, owned-text pool,
 * input references, sorted object directories and per-word metadata a native producer publishes,
 * so a pure-Java producer's document is read by the same {@link TapeReader} paths. Nodes are
 * appended in tape order; a container is opened with {@link #begin}, its children follow, and
 * {@link #end} closes it (an object's children alternate KEY strings and values).
 *
 * <p>Spans are {@code offset << 32 | length} into the retained input passed to the constructor; 0
 * means "no source token". The builder checks structure, not policy: limits, duplicate-key
 * policies and alias rules belong to the producer, though {@link #end} still refuses two equal keys
 * in one object, as the native directory writer does.
 */
public final class TapeBuilder {
  /** Descriptor flags ({@code TD_TAPE_*}). */
  public static final int NONFINITE = 1, HAS_META = 2, YAML = 8;

  /** Largest text token and saturated container count. */
  public static final int MAX_LENGTH = 0xffffff;

  private static final int NULL = 0, FALSE = 1, TRUE = 2, I64 = 3, U64 = 4, F64 = 5, DECIMAL = 6,
      STRING = 7, ARRAY = 8, OBJECT = 9, REF = 10;

  private final byte[] input;
  private long[] words = new long[64];
  private int[] meta = new int[64];
  private int size;
  private byte[] pool = new byte[64];
  private int poolSize;
  private long[] directory = new long[16];
  private int directorySize;
  private boolean nonfinite;

  /** A builder whose spans and input references index {@code input} (kept, not copied). */
  public TapeBuilder(byte[] input) {
    this.input = input;
  }

  private static long make(int kind, long flags, int field, long low) {
    return (long) kind << 60 | flags | ((long) (field & MAX_LENGTH) << 32) | (low & 0xffffffffL);
  }

  private int push(long first, long second, boolean wide, int metadata) {
    int need = size + (wide ? 2 : 1);
    if (need < 0) throw new IllegalArgumentException("Tape word limit");
    if (need > words.length) {
      int capacity = Math.max(need, words.length * 2);
      words = Arrays.copyOf(words, capacity);
      meta = Arrays.copyOf(meta, capacity);
    }
    int at = size;
    words[at] = first;
    meta[at] = metadata;
    if (wide) {
      words[at + 1] = second;
      meta[at + 1] = 0;
    }
    size = need;
    return at;
  }

  private void checkSpan(long span) {
    long offset = span >>> 32, length = span & 0xffffffffL;
    if (offset + length > input.length) throw new IndexOutOfBoundsException("Tape input span");
  }

  private static int tokenLength(long span) {
    long length = span & 0xffffffffL;
    if (length > MAX_LENGTH) throw new IllegalArgumentException("Tape text token limit");
    return (int) length;
  }

  public int nullValue(long span, int metadata) {
    checkSpan(span);
    return push(make(NULL, 0, tokenLength(span), span >>> 32), 0, false, metadata);
  }

  public int booleanValue(boolean value, long span, int metadata) {
    checkSpan(span);
    return push(make(value ? TRUE : FALSE, 0, tokenLength(span), span >>> 32), 0, false, metadata);
  }

  public int longValue(long value, long span, int metadata) {
    return number(I64, value, span, metadata);
  }

  public int unsignedValue(long bits, long span, int metadata) {
    return number(U64, bits, span, metadata);
  }

  public int doubleValue(double value, long span, int metadata) {
    if (!Double.isFinite(value)) nonfinite = true;
    return number(F64, Double.doubleToRawLongBits(value), span, metadata);
  }

  private int number(int kind, long bits, long span, int metadata) {
    checkSpan(span);
    return push(make(kind, TapeReader.WIDE, tokenLength(span), span >>> 32), bits, true, metadata);
  }

  /** A DECIMAL whose spelling is owned (copied into the pool); {@code raw} is its source token. */
  public int decimalValue(byte[] digits, long raw, int metadata) {
    return owned(DECIMAL, 0, digits, raw, metadata);
  }

  /** A STRING whose decoded text is the input span itself (one word). */
  public int stringRef(long span, boolean ascii, boolean key, int metadata) {
    checkSpan(span);
    long flags = (ascii ? TapeReader.ASCII : 0) | (key ? TapeReader.KEY : 0);
    return push(make(STRING, flags, tokenLength(span), span >>> 32), 0, false, metadata);
  }

  /** A STRING with owned decoded text and an independent raw source span (0 when none). */
  public int string(byte[] utf8, long raw, boolean ascii, boolean key, int metadata) {
    return owned(STRING, (ascii ? TapeReader.ASCII : 0) | (key ? TapeReader.KEY : 0), utf8, raw, metadata);
  }

  private int owned(int kind, long flags, byte[] text, long raw, int metadata) {
    if (text.length > MAX_LENGTH) throw new IllegalArgumentException("Tape text token limit");
    checkSpan(raw);
    if (poolSize + (long) text.length > 0xffffffffL || poolSize + (long) text.length > Integer.MAX_VALUE - 8)
      throw new IllegalArgumentException("Tape pool limit");
    int at = poolSize;
    if (at + text.length > pool.length) pool = Arrays.copyOf(pool, Math.max(at + text.length, pool.length * 2));
    System.arraycopy(text, 0, pool, at, text.length);
    poolSize += text.length;
    return push(make(kind, flags | TapeReader.OWNED | TapeReader.WIDE, text.length, at), raw, true, metadata);
  }

  /** Opens an ARRAY or OBJECT; children follow until {@link #end}. Returns its index. */
  public int begin(boolean object, int metadata) {
    return push(make(object ? OBJECT : ARRAY, TapeReader.WIDE, 0, 0), 0, true, metadata);
  }

  /**
   * Closes the container at {@code index}, which must be the innermost open one: everything
   * written since belongs to it. An object of two or more members gets its directory (sorted by
   * unsigned FNV-1a hash, then key offset); equal keys throw {@link IllegalArgumentException}.
   */
  public void end(int index) {
    long w = words[index];
    int kind = (int) (w >>> 60);
    if ((kind != ARRAY && kind != OBJECT) || (w & 0xffffffffL) != 0)
      throw new IllegalStateException("Tape container is not open");
    int count = 0;
    long[] entries = kind == OBJECT ? new long[8] : null;
    for (int child = index + 2; child < size; count++) {
      if (entries != null) {
        long key = words[child];
        if ((int) (key >>> 60) != STRING || (key & TapeReader.KEY) == 0)
          throw new IllegalStateException("Object key is not a KEY string");
        if (count == entries.length) entries = Arrays.copyOf(entries, count * 2);
        entries[count] = Integer.toUnsignedLong(hash(child)) << 32 | (child - index);
        child = step(child);
        if (child >= size) throw new IllegalStateException("Object key without a value");
      }
      child = after(child);
      if (child <= index) throw new IllegalStateException("Tape container is not closed");
    }
    int stored = Math.min(count, MAX_LENGTH);
    long second = words[index + 1];
    if (entries != null && count >= 2) {
      long[] sorted = Arrays.copyOf(entries, count);
      // (hash << 32 | offset) as an unsigned key sorts by hash, then offset, like the native writer.
      for (int i = 0; i < count; i++) sorted[i] ^= Long.MIN_VALUE;
      Arrays.sort(sorted);
      for (int i = 0; i < count; i++) sorted[i] ^= Long.MIN_VALUE;
      for (int i = 1; i < count; i++) {
        if ((sorted[i] >>> 32) != (sorted[i - 1] >>> 32)) continue;
        for (int j = i; j > 0 && (sorted[j - 1] >>> 32) == (sorted[i] >>> 32); j--)
          if (sameText(index + (int) sorted[j - 1], index + (int) sorted[i]))
            throw new IllegalArgumentException("duplicate key");
      }
      if (directorySize + (long) count > Integer.MAX_VALUE - 8)
        throw new IllegalArgumentException("Tape directory limit");
      int start = directorySize;
      if (start + count > directory.length)
        directory = Arrays.copyOf(directory, Math.max(start + count, directory.length * 2));
      // Published entries are key_offset << 32 | hash (myna_value_directory_entry).
      for (int i = 0; i < count; i++)
        directory[start + i] = (sorted[i] & 0xffffffffL) << 32 | (sorted[i] >>> 32);
      directorySize += count;
      second = (long) (start + 1) << 32 | (second & 0xffffffffL);
    }
    words[index] = make(kind, TapeReader.WIDE, stored, size);
    words[index + 1] = second;
  }

  /** A REF to the earlier, closed node at {@code target} (a YAML alias). Returns its index. */
  public int ref(int target, int metadata) {
    if (target < 0 || target >= size) throw new IllegalArgumentException("REF target must precede the reference");
    long w = words[target];
    int kind = (int) (w >>> 60);
    if ((kind == ARRAY || kind == OBJECT) && (w & 0xffffffffL) == 0)
      throw new IllegalArgumentException("REF target is open");
    return push(make(REF, 0, 0, target), 0, false, metadata);
  }

  private int step(int i) {
    return i + ((words[i] & TapeReader.WIDE) == 0 ? 1 : 2);
  }

  private int after(int i) {
    int kind = (int) (words[i] >>> 60);
    return kind == ARRAY || kind == OBJECT ? (int) words[i] : step(i);
  }

  private byte textByte(int node, int i) {
    long w = words[node];
    int offset = (int) w + i;
    return (w & TapeReader.OWNED) != 0 ? pool[offset] : input[offset];
  }

  private int hash(int node) {
    int hash = 0x811c9dc5;
    for (int i = 0, n = (int) (words[node] >>> 32) & MAX_LENGTH; i < n; i++)
      hash = (hash ^ (textByte(node, i) & 255)) * 0x01000193;
    return hash;
  }

  private boolean sameText(int a, int b) {
    int n = (int) (words[a] >>> 32) & MAX_LENGTH;
    if (n != ((int) (words[b] >>> 32) & MAX_LENGTH)) return false;
    for (int i = 0; i < n; i++) if (textByte(a, i) != textByte(b, i)) return false;
    return true;
  }

  /**
   * The finished tape: exactly one root, every container closed. {@code flags} are descriptor
   * flags; {@link #NONFINITE} is added when needed, and per-word metadata is kept only under
   * {@link #HAS_META}.
   */
  public HeapTape build(int flags) {
    if (size == 0) throw new IllegalStateException("Empty compact tape");
    if (after(0) != size) throw new IllegalStateException("Tape root does not end the tape");
    for (int i = 0; i < size; i = step(i)) {
      int kind = (int) (words[i] >>> 60);
      if ((kind == ARRAY || kind == OBJECT) && (words[i] & 0xffffffffL) == 0)
        throw new IllegalStateException("Tape container is not closed");
    }
    int f = flags | (nonfinite ? NONFINITE : 0);
    return new HeapTape(
        Arrays.copyOf(words, size),
        Arrays.copyOf(pool, poolSize),
        input,
        Arrays.copyOf(directory, directorySize),
        (f & HAS_META) != 0 ? Arrays.copyOf(meta, size) : new int[0],
        f);
  }
}
