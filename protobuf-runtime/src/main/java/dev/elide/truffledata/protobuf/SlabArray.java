package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import dev.elide.truffledata.DataArray;
import dev.elide.truffledata.OwnedArray;
import java.util.Objects;

/** Lazy durable list: primitive elements stay unboxed in the slab until requested. */
@ExportLibrary(InteropLibrary.class)
final class SlabArray implements OwnedArray {
  static final DataArray EMPTY = new DataArray(new Object[0]);
  private final SlabMessage owner;
  private final int type;
  private final int ordinal;
  private final int list;
  private final int count;
  private final int width;
  private final boolean expanded;

  SlabArray(SlabMessage owner, int type, int ordinal, int list, boolean expanded) {
    this.owner = owner;
    this.type = type;
    this.ordinal = ordinal;
    this.list = list;
    this.expanded = expanded;
    count = SlabFormat.listCount(owner.bytes, list);
    width = owner.layout.kind(type, ordinal).element;
  }

  @Override
  public int size() {
    return count;
  }

  @Override
  public Object get(int index) {
    Objects.checkIndex(index, count);
    owner.requireValue(type, ordinal, expanded);
    return SlabRow.value(
        owner, type, ordinal, SlabFormat.listElement(list, width, index), expanded);
  }

  @ExportMessage
  boolean hasArrayElements() {
    return true;
  }

  @ExportMessage
  long getArraySize() {
    return count;
  }

  @ExportMessage
  boolean isArrayElementReadable(long index) {
    return index >= 0 && index < count;
  }

  @ExportMessage
  Object readArrayElement(long index) throws InvalidArrayIndexException {
    if (!isArrayElementReadable(index)) throw InvalidArrayIndexException.create(index);
    return get((int) index);
  }
}
