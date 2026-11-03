package dev.elide.truffledata;

import org.jspecify.annotations.Nullable;

/** Immutable, validated value facts. Bounds are inclusive; absent facts mean unknown, not zero. */
public record Constraints(
    @Nullable IntegerWidth integer,
    int floatBits,
    @Nullable Length length,
    @Nullable Field element,
    @Nullable String recordType,
    @Nullable Decimal decimal,
    @Nullable IntegerRange integerRange) {
  public static final Constraints NONE = new Constraints(null, 0, null, null, null, null, null);

  public Constraints {
    if (floatBits != 0 && floatBits != 32 && floatBits != 64)
      throw new IllegalArgumentException("Floating width must be 32 or 64");
  }

  public record IntegerWidth(int bits, boolean signed) {
    public IntegerWidth {
      if (bits != 8 && bits != 16 && bits != 32 && bits != 64)
        throw new IllegalArgumentException("Integer width must be 8, 16, 32 or 64");
    }

    public long minimum() {
      return signed ? bits == 64 ? Long.MIN_VALUE : -(1L << (bits - 1)) : 0;
    }

    public long maximum() {
      if (!signed && bits == 64) throw new IllegalStateException("UINT64 requires exact storage");
      return signed ? bits == 64 ? Long.MAX_VALUE : (1L << (bits - 1)) - 1 : (1L << bits) - 1;
    }
  }

  public record IntegerRange(long min, long max) {
    public IntegerRange {
      if (max < min) throw new IllegalArgumentException("Invalid integer range");
    }
  }

  public enum LengthUnit {
    ELEMENTS,
    UTF16,
    CODE_POINTS
  }

  public record Length(int min, int max, LengthUnit unit) {
    public Length {
      if (min < 0 || max < min) throw new IllegalArgumentException("Invalid length range");
      java.util.Objects.requireNonNull(unit);
    }

    public boolean exact() {
      return min == max;
    }
  }

  /** SQL decimal precision and scale, including negative PostgreSQL scales. */
  public record Decimal(int precision, int scale) {
    public Decimal {
      if (precision < 1 || precision > 100000 || scale < -100000 || scale > 100000)
        throw new IllegalArgumentException("Unsupported decimal precision/scale budget");
    }
  }

  public Constraints withIntegerRange(IntegerRange value) {
    return new Constraints(integer, floatBits, length, element, recordType, decimal, value);
  }

  public Constraints withInteger(IntegerWidth value) {
    return new Constraints(value, floatBits, length, element, recordType, decimal, integerRange);
  }

  public Constraints withFloatBits(int value) {
    return new Constraints(integer, value, length, element, recordType, decimal, integerRange);
  }

  public Constraints withLength(Length value) {
    return new Constraints(integer, floatBits, value, element, recordType, decimal, integerRange);
  }

  public Constraints withElement(Field value) {
    return new Constraints(integer, floatBits, length, value, recordType, decimal, integerRange);
  }

  public Constraints withRecordType(String value) {
    return new Constraints(integer, floatBits, length, element, value, decimal, integerRange);
  }

  public Constraints withDecimal(Decimal value) {
    return new Constraints(integer, floatBits, length, element, recordType, value, integerRange);
  }
}
