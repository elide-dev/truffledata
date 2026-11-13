package dev.elide.truffledata.bebop;

import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A struct or message value: the field values in definition order. A {@code null} entry is an
 * absent message field; struct fields are never absent.
 */
public final class BebopRecord {
  private final String type;
  private final @Nullable Object[] values;

  public BebopRecord(String type, @Nullable Object... values) {
    this.type = Objects.requireNonNull(type);
    this.values = values.clone();
  }

  private BebopRecord(@Nullable Object[] values, String type) {
    this.type = type;
    this.values = values;
  }

  /** Takes ownership of a freshly decoded array. */
  static BebopRecord owning(String type, @Nullable Object[] values) {
    return new BebopRecord(values, type);
  }

  public String type() {
    return type;
  }

  public int size() {
    return values.length;
  }

  public @Nullable Object get(int ordinal) {
    return values[ordinal];
  }

  public boolean present(int ordinal) {
    return values[ordinal] != null;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof BebopRecord that && BebopValues.equal(this, that);
  }

  @Override
  public int hashCode() {
    return type.hashCode() * 31 + values.length;
  }

  @Override
  public String toString() {
    return type + Arrays.deepToString(values);
  }
}
