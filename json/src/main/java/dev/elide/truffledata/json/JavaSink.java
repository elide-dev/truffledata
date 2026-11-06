package dev.elide.truffledata.json;

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
 */
public final class JavaSink implements JsonSink {
  private final ArrayList<Object> open = new ArrayList<>();
  private @Nullable String key;
  private @Nullable Object result;
  private boolean done;

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
      String name = key;
      if (name == null) throw new IllegalStateException("JSON member value without a key");
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
  public void key(JsonValue name) {
    if (key != null || open.isEmpty() || !(open.get(open.size() - 1) instanceof Map))
      throw new IllegalStateException("JSON key outside a member position");
    key = name.stringValue();
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
  public void decimalValue(JsonValue token) {
    add(new BigDecimal(token.numberText()));
  }

  @Override
  @TruffleBoundary
  public void stringValue(JsonValue text) {
    add(text.stringValue());
  }
}
