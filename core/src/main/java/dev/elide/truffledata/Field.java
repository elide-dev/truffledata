package dev.elide.truffledata;

import java.util.Objects;

/**
 * Presence and nullability are independent. Constraints describe values only when present/non-null.
 */
public record Field(
    String name, Kind kind, boolean nullable, boolean optional, Constraints constraints) {
  public enum Kind {
    INT64,
    FLOAT64,
    BOOLEAN,
    STRING,
    EXACT,
    ARRAY,
    RECORD,
    ANY
  }

  public Field(String name, Kind kind, boolean nullable, boolean optional) {
    this(name, kind, nullable, optional, Constraints.NONE);
  }

  public Field {
    Objects.requireNonNull(name);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(constraints);
    var integer = constraints.integer();
    if (integer != null
        && kind != (integer.bits() == 64 && !integer.signed() ? Kind.EXACT : Kind.INT64))
      throw new IllegalArgumentException("Integer width incompatible with kind");
    var range = constraints.integerRange();
    if (range != null) {
      if (kind != Kind.INT64) throw new IllegalArgumentException("Integer range requires INT64");
      if (integer != null && (range.min() > integer.maximum() || range.max() < integer.minimum()))
        throw new IllegalArgumentException("Integer range and width have no intersection");
    }
    if (constraints.floatBits() != 0 && kind != Kind.FLOAT64)
      throw new IllegalArgumentException("Floating width requires FLOAT64");
    if (constraints.length() != null && kind != Kind.STRING && kind != Kind.ARRAY)
      throw new IllegalArgumentException("Length requires STRING or ARRAY");
    var length = constraints.length();
    if (length != null
        && (kind == Kind.ARRAY) != (length.unit() == Constraints.LengthUnit.ELEMENTS))
      throw new IllegalArgumentException("Length unit incompatible with kind");
    if (constraints.element() != null && kind != Kind.ARRAY)
      throw new IllegalArgumentException("Element contract requires ARRAY");
    if (constraints.recordType() != null && kind != Kind.RECORD)
      throw new IllegalArgumentException("Record identity requires RECORD");
    if (constraints.decimal() != null && kind != Kind.EXACT)
      throw new IllegalArgumentException("Decimal contract requires EXACT");
  }

  public static Field requiredLong(String name) {
    return new Field(name, Kind.INT64, false, false);
  }

  public static Field integer(
      String name, int bits, boolean signed, boolean nullable, boolean optional) {
    return new Field(
        name,
        bits == 64 && !signed ? Kind.EXACT : Kind.INT64,
        nullable,
        optional,
        Constraints.NONE.withInteger(new Constraints.IntegerWidth(bits, signed)));
  }

  public Field withConstraints(Constraints value) {
    return new Field(name, kind, nullable, optional, value);
  }
}
