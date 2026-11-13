package dev.elide.truffledata.bebop;

import java.util.Objects;

/** A type expression: a scalar, a reference to a named definition, or a collection. */
public sealed interface BebopType {
  /** Primitive types with their wire width in bytes ({@code -1}: variable). */
  enum Scalar implements BebopType {
    BOOL(1),
    BYTE(1),
    INT8(1),
    INT16(2),
    UINT16(2),
    INT32(4),
    UINT32(4),
    INT64(8),
    UINT64(8),
    INT128(16),
    UINT128(16),
    FLOAT16(2),
    FLOAT32(4),
    FLOAT64(8),
    BFLOAT16(2),
    UUID(16),
    TIMESTAMP(16),
    DURATION(12),
    STRING(-1);

    private final int width;

    Scalar(int width) {
      this.width = width;
    }

    public int width() {
      return width;
    }

    /** Fixed-width integers of at most 64 bits. */
    public boolean integer64() {
      return ordinal() >= BYTE.ordinal() && ordinal() <= UINT64.ordinal();
    }

    boolean integer() {
      return ordinal() >= BYTE.ordinal() && ordinal() <= UINT128.ordinal();
    }
  }

  /** A named enum, struct, message or union of the schema. */
  record Ref(String name) implements BebopType {
    public Ref {
      Objects.requireNonNull(name);
    }
  }

  /** A dynamic array. A set is an array whose strict encoding is sorted and duplicate-free. */
  record Array(BebopType element, boolean set) implements BebopType {
    public Array {
      Objects.requireNonNull(element);
    }
  }

  /** A fixed array of 1 to 65535 elements, encoded without a count. */
  record FixedArray(BebopType element, int length) implements BebopType {
    public FixedArray {
      Objects.requireNonNull(element);
      if (length < 1 || length > 65535) throw new IllegalArgumentException("Fixed array length");
    }
  }

  /** A map. Keys are integers, booleans, strings, UUIDs or enums. */
  record MapOf(BebopType key, BebopType value) implements BebopType {
    public MapOf {
      Objects.requireNonNull(key);
      Objects.requireNonNull(value);
    }
  }

  /** A schema-level constraint on values of a type. It never changes the type's wire form. */
  enum Refinement {
    /**
     * A timestamp whose zone offset is zero: an instant in UTC. The 16 bytes are unchanged; a
     * nonzero offset is rejected, never cleared.
     */
    UTC_INSTANT
  }

  /**
   * A type narrowed by a refinement. A codec refuses a combination it cannot validate instead of
   * validating only the base type.
   */
  record Refined(BebopType base, Refinement refinement) implements BebopType {
    public Refined {
      Objects.requireNonNull(base);
      Objects.requireNonNull(refinement);
    }
  }

  /** A timestamp that must carry a zero zone offset. */
  static Refined utcInstant() {
    return new Refined(Scalar.TIMESTAMP, Refinement.UTC_INSTANT);
  }

  static Ref ref(String name) {
    return new Ref(name);
  }

  static Array array(BebopType element) {
    return new Array(element, false);
  }

  static Array set(BebopType element) {
    return new Array(element, true);
  }

  static FixedArray fixed(BebopType element, int length) {
    return new FixedArray(element, length);
  }

  static MapOf map(BebopType key, BebopType value) {
    return new MapOf(key, value);
  }
}
