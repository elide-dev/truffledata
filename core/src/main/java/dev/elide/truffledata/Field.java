package dev.elide.truffledata;

import java.util.Objects;

/** Presence and nullability are independent facts about a field. */
public record Field(String name, Kind kind, boolean nullable, boolean optional) {
  public enum Kind {
    INT64,
    FLOAT64,
    BOOLEAN,
    STRING,
    ANY
  }

  public Field {
    Objects.requireNonNull(name);
    Objects.requireNonNull(kind);
  }

  public static Field requiredLong(String name) {
    return new Field(name, Kind.INT64, false, false);
  }
}
