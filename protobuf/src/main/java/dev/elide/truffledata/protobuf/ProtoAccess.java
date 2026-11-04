package dev.elide.truffledata.protobuf;

import dev.elide.truffledata.*;

/**
 * Direct schema-bound host access used by generated getters; borrowed implementations check
 * lifetime.
 */
public interface ProtoAccess {
  ProtoPlan plan();

  boolean has(int ordinal);

  Object get(int ordinal);

  default long getLong(int ordinal) {
    Object value = get(ordinal);
    if (value instanceof Long number) return number;
    throw new ContractViolation("Expected INT64");
  }

  default double getDouble(int ordinal) {
    Object value = get(ordinal);
    if (value instanceof Double number) return number;
    throw new ContractViolation("Expected FLOAT64");
  }

  default boolean getBoolean(int ordinal) {
    Object value = get(ordinal);
    if (value instanceof Boolean flag) return flag;
    throw new ContractViolation("Expected BOOLEAN");
  }

  default int getSize(int ordinal) {
    Object value = get(ordinal);
    if (value instanceof DataArray array) return array.size();
    throw new ContractViolation("Expected array");
  }
}
