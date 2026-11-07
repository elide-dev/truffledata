package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Reference sink for Truffle guests: builds {@code LinkedHashMap<TruffleString,Object>} in source
 * member order, {@code ArrayList<Object>}, UTF-16 {@link TruffleString} keys and strings, and the
 * same number and literal objects as {@link JavaSink}. A guest {@code JSON.parse} or {@code YAML.parse} sink (GraalJS
 * objects instead of maps) follows the same shape. One sink builds one tree.
 *
 * <p>The sink asks for the session's text arena ({@link #sharedText()}): a {@link TapeSession}
 * parse then writes every string once, natively, into a per-document array, and each string value
 * is a {@code TruffleString} over that array with no copy (strings of 64 KiB or more are copied, so
 * a small retained string never pins a large document). Member names come from a {@link KeyCache},
 * copied once and reused across documents.
 */
public final class TruffleSink implements TapeSink {
  private final ArrayList<Object> open = new ArrayList<>();
  private final KeyCache keys;
  private @Nullable TruffleString key;
  private @Nullable Object result;
  private boolean done;
  private @Nullable Map<Integer, @Nullable Object> anchors;
  private int pendingAnchor = -1;

  /** A sink whose member names come from {@code keys}, shared by the sinks of one thread. */
  public TruffleSink(KeyCache keys) {
    this.keys = java.util.Objects.requireNonNull(keys);
  }

  /**
   * Bounded intern table of UTF-16 member names keyed by the decoder's key hash and the decoded
   * bytes. Names are copied out of the document, so the cache never retains a text arena. Holds at
   * most {@value #CAPACITY} names; later distinct names are materialized per use. Not thread-safe.
   */
  public static final class KeyCache {
    static final int CAPACITY = 1024;
    private final TruffleString[] names = new TruffleString[2 * CAPACITY];
    private final byte[][] utf8 = new byte[2 * CAPACITY][];
    // Node words of names stored inline (at most 16 bytes), as in JavaSink.KeyCache.
    private final long[] words = new long[3 * 2 * CAPACITY];
    private final long[] probe = new long[3];
    private int size;

    /** Number of distinct names held. */
    public int size() {
      return size;
    }

    @TruffleBoundary
    TruffleString intern(TapeText key) {
      int mask = names.length - 1;
      int slot = key.hash() & mask;
      long[] w = probe;
      boolean inline = key.inlineWords(w, 0);
      for (TruffleString name; (name = names[slot]) != null; slot = (slot + 1) & mask) {
        int at = 3 * slot;
        if (inline
            ? words[at] == w[0] && words[at + 1] == w[1] && words[at + 2] == w[2]
            : key.utf8Equals(utf8[slot])) return name;
      }
      byte[] bytes = new byte[key.utf8Length()];
      key.copyUtf8(bytes, 0);
      TruffleString name =
          TruffleString.fromByteArrayUncached(bytes, TruffleString.Encoding.UTF_8, false)
              .switchEncodingUncached(TruffleString.Encoding.UTF_16);
      if (size < CAPACITY) {
        utf8[slot] = bytes;
        if (inline) System.arraycopy(w, 0, words, 3 * slot, 3);
        names[slot] = name;
        size++;
      }
      return name;
    }
  }

  @Override
  public boolean sharedText() {
    return true;
  }

  /** The exported tree; throws until one complete value has been received. */
  public @Nullable Object result() {
    if (!done || !open.isEmpty()) throw new IllegalStateException("Tape export incomplete");
    return result;
  }

  @SuppressWarnings("unchecked")
  @TruffleBoundary
  private void add(@Nullable Object value) {
    if (pendingAnchor >= 0) {
      Map<Integer, @Nullable Object> table = anchors;
      if (table == null) anchors = table = new LinkedHashMap<>();
      table.put(pendingAnchor, value);
      pendingAnchor = -1;
    }
    if (open.isEmpty()) {
      if (done) throw new IllegalStateException("Sink already holds a value");
      result = value;
      done = true;
    } else if (open.get(open.size() - 1) instanceof Map<?, ?> map) {
      TruffleString name = key;
      if (name == null) throw new IllegalStateException("Member value without a key");
      ((Map<TruffleString, @Nullable Object>) map).put(name, value);
      key = null;
    } else {
      ((List<@Nullable Object>) open.get(open.size() - 1)).add(value);
    }
  }

  @Override
  public void anchor(int id) { pendingAnchor = id; }

  @Override
  @TruffleBoundary
  public boolean alias(int id) {
    Map<Integer, @Nullable Object> table = anchors;
    if (table == null || !table.containsKey(id)) return false;
    add(table.get(id));
    return true;
  }

  private void push(Object container) {
    add(container);
    open.add(container);
  }

  private static int capacity(long count) {
    return (int) Math.min(count, 1 << 12);
  }

  @Override
  @TruffleBoundary
  public void beginObject(long members) {
    push(new LinkedHashMap<TruffleString, @Nullable Object>(capacity(members) * 4 / 3 + 1));
  }

  @Override
  @TruffleBoundary
  public void beginArray(long elements) {
    push(new ArrayList<@Nullable Object>(capacity(elements)));
  }

  @Override
  @TruffleBoundary
  public void key(TapeText name) {
    if (key != null || open.isEmpty() || !(open.get(open.size() - 1) instanceof Map))
      throw new IllegalStateException("Key outside a member position");
    key = keys.intern(name);
  }

  @Override
  @TruffleBoundary
  public void end() {
    if (open.isEmpty() || key != null) throw new IllegalStateException("Unbalanced container end");
    open.remove(open.size() - 1);
  }

  @Override
  public void nullValue() {
    add(null);
  }

  @Override
  public void booleanValue(boolean value) {
    add(value);
  }

  @Override
  public void longValue(long value) {
    add(value);
  }

  @Override
  public void doubleValue(double value) {
    add(value);
  }

  @Override
  @TruffleBoundary
  public void unsignedValue(long bits) {
    add(new BigInteger(Long.toUnsignedString(bits)));
  }

  /** Throws {@link NumberFormatException} for exponents beyond {@code BigDecimal}'s scale range. */
  @Override
  @TruffleBoundary
  public void decimalValue(TapeText token) {
    add(new BigDecimal(token.toJavaString()));
  }

  @Override
  @TruffleBoundary
  public void stringValue(TapeText text) {
    add(text.toTruffleString(TruffleString.Encoding.UTF_16));
  }
}
