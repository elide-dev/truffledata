package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.jspecify.annotations.Nullable;

public final class Values {
  private Values() {}

  @TruffleBoundary
  public static Object normalize(@Nullable Object value) {
    if (value == null) return DataNull.INSTANCE;
    if (value instanceof Byte || value instanceof Short || value instanceof Integer)
      return ((Number) value).longValue();
    if (value instanceof java.math.BigDecimal n) return new ExactNumber(n);
    if (value instanceof java.math.BigInteger n)
      return new ExactNumber(new java.math.BigDecimal(n));
    if (value instanceof Float n) return n.doubleValue();
    requireInterop(value);
    return value;
  }

  public static void requireInterop(Object value) {
    if (!(value instanceof String
        || value instanceof Long
        || value instanceof Double
        || value instanceof Boolean
        || value instanceof ExactNumber
        || value instanceof DataNull
        || value instanceof Missing
        || value instanceof AbstractRow
        || value instanceof DataArray)) {
      throw new ContractViolation(
          "Unsupported value representation: " + value.getClass().getName());
    }
  }

  public static Object validate(Field field, Object value) {
    if (value == Missing.INSTANCE) {
      if (!field.optional()) throw violation(field, "missing");
    } else if (value == DataNull.INSTANCE) {
      if (!field.nullable()) throw violation(field, "null");
    } else {
      boolean valid =
          switch (field.kind()) {
            case INT64 -> value instanceof Long;
            case FLOAT64 -> value instanceof Double;
            case BOOLEAN -> value instanceof Boolean;
            case STRING -> value instanceof String;
            case ANY -> true;
          };
      if (!valid) throw violation(field, "type mismatch");
      requireInterop(value);
    }
    return value;
  }

  @TruffleBoundary
  private static ContractViolation violation(Field field, String reason) {
    return new ContractViolation("Field '" + field.name() + "': " + reason);
  }
}
