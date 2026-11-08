package dev.elide.truffledata;

import com.oracle.truffle.api.interop.TruffleObject;

/**
 * Durable immutable array of normalized values. Implementations own their storage and all elements;
 * neither access nor ownership transfer depends on an external lease. A byte array exposes unsigned
 * INT64 elements in 0..255 and can copy its primitive storage directly.
 */
public interface OwnedArray extends TruffleObject {
  int size();

  Object get(int index);

  default boolean isBytes() {
    return false;
  }

  default void copyBytesTo(byte[] target, int offset) {
    if (!isBytes()) throw new ContractViolation("Expected byte storage");
    java.util.Objects.checkFromIndexSize(offset, size(), target.length);
    for (int i = 0; i < size(); i++) target[offset + i] = (byte) ((Long) get(i)).longValue();
  }
}
