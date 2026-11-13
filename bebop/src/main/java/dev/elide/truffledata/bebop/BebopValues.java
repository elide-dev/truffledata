package dev.elide.truffledata.bebop;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Deep equality of decoded host values: byte arrays by content, lists by position, maps by key,
 * records and unions by field. It compares values the decoder produced for one type under one
 * profile. It is not a schema-aware equality over arbitrary host inputs: it does not treat a set
 * as unordered, an {@code Integer} and a {@code Long} as one integer, or a host float and its
 * reduced-width encoding as one number.
 */
public final class BebopValues {
  private BebopValues() {}

  public static boolean equal(@Nullable Object a, @Nullable Object b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    if (a instanceof byte[] x) return b instanceof byte[] y && Arrays.equals(x, y);
    if (a instanceof List<?> x) {
      if (!(b instanceof List<?> y) || x.size() != y.size()) return false;
      for (int i = 0; i < x.size(); i++) if (!equal(x.get(i), y.get(i))) return false;
      return true;
    }
    if (a instanceof Map<?, ?> x) {
      if (!(b instanceof Map<?, ?> y) || x.size() != y.size()) return false;
      for (Map.Entry<?, ?> e : x.entrySet()) {
        Object other = y.get(e.getKey());
        if (other == null || !equal(e.getValue(), other)) return false;
      }
      return true;
    }
    if (a instanceof BebopRecord x) {
      if (!(b instanceof BebopRecord y) || !x.type().equals(y.type()) || x.size() != y.size())
        return false;
      for (int i = 0; i < x.size(); i++) if (!equal(x.get(i), y.get(i))) return false;
      return true;
    }
    return Objects.equals(a, b);
  }
}
