package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Builds plain Java objects: {@code LinkedHashMap<String,Object>} in source member order, {@code
 * ArrayList<Object>}, {@code String}, {@code Long} (I64), {@code Double} (F64), {@code BigInteger}
 * (U64), {@code BigDecimal} (DECIMAL), {@code Boolean} and {@code null}. One sink builds one tree.
 * Sinks that share a {@link KeyCache} reuse one {@code String} per distinct key across documents.
 */
public final class JavaSink implements TapeSink {
  private final ArrayList<Object> open = new ArrayList<>();
  private final @Nullable KeyCache keys;
  private @Nullable String key;
  private @Nullable Object result;
  private boolean done;

  /** A sink that materializes every key. */
  public JavaSink() {
    this.keys = null;
  }

  /** A sink that takes member names from {@code keys}, shared by the sinks of one thread. */
  public JavaSink(KeyCache keys) {
    this.keys = java.util.Objects.requireNonNull(keys);
  }

  /**
   * Bounded intern table of member names keyed by the decoder's key hash and the decoded bytes, so
   * documents of one schema stop allocating key {@code String}s after the first. Holds at most
   * {@value #CAPACITY} names; later distinct names are materialized per use. Not thread-safe: share
   * one cache among the sinks of one thread.
   */
  public static final class KeyCache {
    static final int CAPACITY = 1024;
    // Open addressing at load factor ≤ 1/2, indexed by the 15-bit key hash.
    private final String[] names = new String[2 * CAPACITY];
    private final byte[][] utf8 = new byte[2 * CAPACITY][];
    private int size;

    /** Number of distinct names held. */
    public int size() {
      return size;
    }

    @TruffleBoundary
    String intern(TapeText key) {
      int mask = names.length - 1;
      int slot = key.hash() & mask;
      for (String name; (name = names[slot]) != null; slot = (slot + 1) & mask)
        if (key.utf8Equals(utf8[slot])) return name;
      String name = key.toJavaString();
      if (size < CAPACITY) {
        byte[] bytes = new byte[key.utf8Length()];
        key.copyUtf8(bytes, 0);
        utf8[slot] = bytes;
        names[slot] = name;
        size++;
      }
      return name;
    }
  }

  /** The exported tree; throws until one complete value has been received. */
  public @Nullable Object result() {
    if (!done || !open.isEmpty()) throw new IllegalStateException("Tape export incomplete");
    return result;
  }

  @SuppressWarnings("unchecked")
  @TruffleBoundary
  private void add(@Nullable Object value) {
    if (open.isEmpty()) {
      if (done) throw new IllegalStateException("Sink already holds a value");
      result = value;
      done = true;
    } else if (open.get(open.size() - 1) instanceof Map<?, ?> map) {
      String name = key;
      if (name == null) throw new IllegalStateException("Member value without a key");
      ((Map<String, @Nullable Object>) map).put(name, value);
      key = null;
    } else {
      ((List<@Nullable Object>) open.get(open.size() - 1)).add(value);
    }
  }

  private void push(Object container) {
    add(container);
    open.add(container);
  }

  /** Presize, but never trust a count enough to allocate more than a modest table up front. */
  private static int capacity(long count) {
    return (int) Math.min(count, 1 << 12);
  }

  @Override
  @TruffleBoundary
  public void beginObject(long members) {
    push(new LinkedHashMap<String, @Nullable Object>(capacity(members) * 4 / 3 + 1));
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
    key = keys == null ? name.toJavaString() : keys.intern(name);
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
    add(text.toJavaString());
  }
}
