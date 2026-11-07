package dev.elide.truffledata.json;

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
 * same number and literal objects as {@link JavaSink}. A guest {@code JSON.parse} sink (GraalJS
 * objects instead of maps) follows the same shape. One sink builds one tree.
 *
 * <p>The sink asks for the session's text arena ({@link #sharedText()}): a {@link JsonSession}
 * parse then writes every string once, natively, into a per-document array, and each string value
 * is a {@code TruffleString} over that array with no copy (strings of 64 KiB or more are copied, so
 * a small retained string never pins a large document). Member names come from a {@link KeyCache},
 * copied once and reused across documents.
 */
public final class TruffleSink implements JsonSink {
  private final ArrayList<Object> open = new ArrayList<>();
  private final KeyCache keys;
  private @Nullable TruffleString key;
  private @Nullable Object result;
  private boolean done;

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
    private int size;

    /** Number of distinct names held. */
    public int size() {
      return size;
    }

    @TruffleBoundary
    TruffleString intern(JsonText key) {
      int mask = names.length - 1;
      int slot = key.hash() & mask;
      for (TruffleString name; (name = names[slot]) != null; slot = (slot + 1) & mask)
        if (key.utf8Equals(utf8[slot])) return name;
      byte[] bytes = new byte[key.utf8Length()];
      key.copyUtf8(bytes, 0);
      TruffleString name =
          TruffleString.fromByteArrayUncached(bytes, TruffleString.Encoding.UTF_8, false)
              .switchEncodingUncached(TruffleString.Encoding.UTF_16);
      if (size < CAPACITY) {
        utf8[slot] = bytes;
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
    if (!done || !open.isEmpty()) throw new IllegalStateException("JSON export incomplete");
    return result;
  }

  @SuppressWarnings("unchecked")
  @TruffleBoundary
  private void add(@Nullable Object value) {
    if (open.isEmpty()) {
      if (done) throw new IllegalStateException("JSON sink already holds a value");
      result = value;
      done = true;
    } else if (open.get(open.size() - 1) instanceof Map<?, ?> map) {
      TruffleString name = key;
      if (name == null) throw new IllegalStateException("JSON member value without a key");
      ((Map<TruffleString, @Nullable Object>) map).put(name, value);
      key = null;
    } else {
      ((List<@Nullable Object>) open.get(open.size() - 1)).add(value);
    }
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
  public void key(JsonText name) {
    if (key != null || open.isEmpty() || !(open.get(open.size() - 1) instanceof Map))
      throw new IllegalStateException("JSON key outside a member position");
    key = keys.intern(name);
  }

  @Override
  @TruffleBoundary
  public void end() {
    if (open.isEmpty() || key != null) throw new IllegalStateException("Unbalanced JSON container end");
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
  public void decimalValue(JsonText token) {
    add(new BigDecimal(token.toJavaString()));
  }

  @Override
  @TruffleBoundary
  public void stringValue(JsonText text) {
    add(text.toTruffleString(TruffleString.Encoding.UTF_16));
  }
}
