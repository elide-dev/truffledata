package dev.elide.truffledata;

import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;

/** Immutable interop sequence of already normalized values. */
@ExportLibrary(InteropLibrary.class)
public final class DataArray implements TruffleObject {
  private final Object values;

  public DataArray(Object[] values) {
    this.values = values.clone();
    for (Object v : (Object[]) this.values) Values.requireInterop(v);
  }

  /** Copy normalized collection elements into owned storage without an intermediate array. */
  public static DataArray copyOf(java.util.Collection<?> source) {
    // Collection.toArray guarantees a fresh array owned by its caller.
    Object[] owned = source.toArray();
    for (Object value : owned) Values.requireInterop(value);
    return new DataArray((Object) owned);
  }

  private DataArray(Object owned) {
    values = owned;
  }

  /** Snapshot mutable bytes once; guest elements are unsigned INT64 values. */
  public static DataArray copyOfBytes(byte[] bytes) {
    return new DataArray(bytes.clone());
  }

  /** Snapshot a byte region once, without temporary buffer wrappers. */
  public static DataArray copyOfBytes(byte[] bytes, int offset, int length) {
    java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
    return new DataArray(java.util.Arrays.copyOfRange(bytes, offset, offset + length));
  }

  /** Copies remaining bytes without changing the caller's position or retaining mutable storage. */
  public static DataArray copyOfBytes(java.nio.ByteBuffer bytes) {
    byte[] owned = new byte[bytes.remaining()];
    bytes.duplicate().get(owned);
    return new DataArray(owned);
  }

  /** Copy primitive bytes directly into caller-owned output, without element boxing. */
  public void copyBytesTo(byte[] target, int offset) {
    if (!(values instanceof byte[] bytes)) throw new ContractViolation("Expected byte storage");
    java.util.Objects.checkFromIndexSize(offset, bytes.length, target.length);
    System.arraycopy(bytes, 0, target, offset, bytes.length);
  }

  public boolean isBytes() {
    return values instanceof byte[];
  }

  public int size() {
    return values instanceof byte[] bytes ? bytes.length : ((Object[]) values).length;
  }

  public Object get(int index) {
    return values instanceof byte[] bytes
        ? (long) Byte.toUnsignedInt(bytes[index])
        : ((Object[]) values)[index];
  }

  @ExportMessage
  public boolean hasArrayElements() {
    return true;
  }

  @ExportMessage
  public long getArraySize() {
    return size();
  }

  @ExportMessage
  public boolean isArrayElementReadable(long index) {
    return index >= 0 && index < size();
  }

  @ExportMessage
  public Object readArrayElement(long index) throws InvalidArrayIndexException {
    if (!isArrayElementReadable(index)) throw InvalidArrayIndexException.create(index);
    return get((int) index);
  }
}
