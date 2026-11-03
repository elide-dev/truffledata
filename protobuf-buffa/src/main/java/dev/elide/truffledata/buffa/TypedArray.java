package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;
import dev.elide.truffledata.*;

/** Immutable array of scoped typed values. Reads never call native code. */
@ExportLibrary(InteropLibrary.class)
public final class TypedArray implements BorrowedArray {
  private final WireMessage owner;

  @CompilationFinal(dimensions = 1)
  private final Object[] values;

  TypedArray(WireMessage owner, Object[] values) {
    this.owner = owner;
    this.values = values;
  }

  public int size() {
    owner.check();
    return values.length;
  }

  public Object element(int index) {
    owner.check();
    return values[index];
  }

  @TruffleBoundary
  public DataArray snapshot() {
    owner.check();
    Object[] copied = new Object[values.length];
    for (int i = 0; i < copied.length; i++) copied[i] = TypedRow.detach(values[i]);
    return new DataArray(copied);
  }

  @ExportMessage
  boolean hasArrayElements() {
    return true;
  }

  @ExportMessage
  long getArraySize() {
    return size();
  }

  @ExportMessage
  boolean isArrayElementReadable(long index) {
    return index >= 0 && index < size();
  }

  @ExportMessage
  Object readArrayElement(long index) throws InvalidArrayIndexException {
    if (!isArrayElementReadable(index)) throw InvalidArrayIndexException.create(index);
    return element((int) index);
  }
}
