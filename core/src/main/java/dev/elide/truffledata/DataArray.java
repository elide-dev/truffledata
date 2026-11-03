package dev.elide.truffledata;

import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;

/** Immutable interop sequence of already normalized values. */
@ExportLibrary(InteropLibrary.class)
public final class DataArray implements TruffleObject {
  private final Object[] values;

  public DataArray(Object[] values) {
    this.values = values.clone();
    for (Object v : values) Values.requireInterop(v);
  }

  @ExportMessage
  public boolean hasArrayElements() {
    return true;
  }

  @ExportMessage
  public long getArraySize() {
    return values.length;
  }

  @ExportMessage
  public boolean isArrayElementReadable(long index) {
    return index >= 0 && index < values.length;
  }

  @ExportMessage
  public Object readArrayElement(long index) throws InvalidArrayIndexException {
    if (!isArrayElementReadable(index)) throw InvalidArrayIndexException.create(index);
    return values[(int) index];
  }
}
