package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.jspecify.annotations.Nullable;

public final class Values {
  private Values() {}

  private static final java.math.BigDecimal MAX_UINT64 =
      new java.math.BigDecimal("18446744073709551615");

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
    return validate(field, value, 0);
  }

  private static Object validate(Field field, Object value, int depth) {
    if (depth > 128) throw violation(field, "contract nesting budget");
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
            case EXACT -> value instanceof ExactNumber;
            case ARRAY -> value instanceof DataArray;
            case RECORD -> value instanceof AbstractRow;
            case ANY -> true;
          };
      if (!valid) throw violation(field, "type mismatch");
      requireInterop(value);
      validateConstraints(field, value, depth);
    }
    return value;
  }

  public static long validateLong(Field field, long value) {
    var width = field.constraints().integer();
    if (width != null && (value < width.minimum() || value > width.maximum()))
      throw violation(field, "integer width");
    var range = field.constraints().integerRange();
    if (range != null && (value < range.min() || value > range.max()))
      throw violation(field, "integer range");
    return value;
  }

  private static void validateConstraints(Field field, Object value, int depth) {
    Constraints facts = field.constraints();
    if (value instanceof Long n) validateLong(field, n);
    if (facts.floatBits() == 32
        && value instanceof Double n
        && !Double.isNaN(n)
        && (double) n.floatValue() != n) throw violation(field, "floating width");
    var length = facts.length();
    if (length != null) {
      int size =
          value instanceof DataArray a ? a.size() : stringLength((String) value, length.unit());
      if (size < length.min() || size > length.max()) throw violation(field, "length");
    }
    var element = facts.element();
    if (element != null && value instanceof DataArray array)
      for (int i = 0; i < array.size(); i++) validate(element, array.get(i), depth + 1);
    var recordType = facts.recordType();
    if (recordType != null
        && value instanceof AbstractRow row
        && !recordType.equals(row.schema().typeName())) throw violation(field, "record type");
    if (value instanceof ExactNumber number && (facts.integer() != null || facts.decimal() != null))
      validateExact(field, number);
  }

  public static int stringLength(String value, Constraints.LengthUnit unit) {
    return unit == Constraints.LengthUnit.CODE_POINTS
        ? value.codePointCount(0, value.length())
        : value.length();
  }

  @TruffleBoundary
  private static void validateExact(Field field, ExactNumber value) {
    try {
      var width = field.constraints().integer();
      if (width != null) {
        var number = value.decimal();
        if (number.signum() < 0
            || number.compareTo(MAX_UINT64) > 0
            || number.stripTrailingZeros().scale() > 0) throw violation(field, "unsigned width");
      }
      var decimal = field.constraints().decimal();
      if (decimal != null && value.decimal().signum() != 0) {
        var canonical = value.decimal().stripTrailingZeros();
        long digits = (long) canonical.precision() + decimal.scale() - canonical.scale();
        if (canonical.scale() > decimal.scale() || digits > decimal.precision())
          throw violation(field, "decimal precision or scale");
      }
    } catch (ArithmeticException e) {
      throw violation(field, "decimal scale or integer fraction");
    }
  }

  @TruffleBoundary
  private static ContractViolation violation(Field field, String reason) {
    return new ContractViolation("Field '" + field.name() + "': " + reason);
  }
}
