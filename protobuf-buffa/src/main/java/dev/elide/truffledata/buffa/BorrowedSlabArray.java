package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.SlabFormat;
import java.util.Objects;

/** Lazy repeated values or bytes in a session arena. */
@ExportLibrary(InteropLibrary.class)
public final class BorrowedSlabArray implements BorrowedArray {
  private final SlabSession owner;
  private final long epoch;
  private final int type, ordinal, offset;
  private final boolean expanded;

  BorrowedSlabArray(SlabSession owner, long epoch, int type, int ordinal, int offset) {
    this(owner, epoch, type, ordinal, offset, false);
  }

  BorrowedSlabArray(
      SlabSession owner, long epoch, int type, int ordinal, int offset, boolean expanded) {
    this.owner = owner;
    this.epoch = epoch;
    this.type = type;
    this.ordinal = ordinal;
    this.offset = offset;
    this.expanded = expanded;
  }

  @Override
  public int size() {
    owner.check(epoch);
    return type < 0 ? owner.length(offset) : owner.word(offset);
  }

  @Override
  public Object element(int index) {
    Objects.checkIndex(index, size());
    if (type >= 0) owner.requireValue(type, ordinal, expanded);
    return type < 0
        ? (long) owner.payloadByte(offset, index)
        : owner.value(
            epoch,
            type,
            ordinal,
            SlabFormat.listElement(offset, owner.layout.kind(type, ordinal).element, index),
            expanded);
  }

  @Override
  @TruffleBoundary
  public DataArray snapshot() {
    int count = size();
    if (type < 0) return DataArray.copyOfBytes(owner.copyPayload(offset));
    Object[] values = new Object[count];
    for (int i = 0; i < count; i++) {
      Object value = element(i);
      values[i] =
          value instanceof BorrowedSlabRow row
              ? row.snapshot()
              : value instanceof BorrowedMaterializedRow row
                  ? row.snapshot()
                  : value instanceof BorrowedArray array ? array.snapshot() : value;
    }
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
