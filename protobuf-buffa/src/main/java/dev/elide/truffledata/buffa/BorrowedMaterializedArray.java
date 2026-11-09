package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;
import dev.elide.truffledata.*;

/** Scoped array inside a materialized lazy child. */
@ExportLibrary(InteropLibrary.class)
final class BorrowedMaterializedArray implements BorrowedArray {
  private final SlabSession owner;
  private final long epoch;
  private final OwnedArray array;

  BorrowedMaterializedArray(SlabSession owner, long epoch, OwnedArray array) {
    this.owner = owner;
    this.epoch = epoch;
    this.array = array;
  }

  @Override
  public int size() {
    owner.check(epoch);
    return array.size();
  }

  @Override
  public Object element(int index) {
    owner.check(epoch);
    return owner.wrap(epoch, array.get(index));
  }

  @Override
  @TruffleBoundary
  public DataArray snapshot() {
    owner.check(epoch);
    if (array.isBytes()) {
      byte[] bytes = new byte[array.size()];
      array.copyBytesTo(bytes, 0);
      return DataArray.copyOfBytes(bytes);
    }
    Object[] values = new Object[array.size()];
    for (int i = 0; i < values.length; i++) values[i] = array.get(i);
    return new DataArray(values);
  }

  @ExportMessage
  boolean hasArrayElements() {
    owner.check(epoch);
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
