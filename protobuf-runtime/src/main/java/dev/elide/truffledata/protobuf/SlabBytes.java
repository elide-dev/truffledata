package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import dev.elide.truffledata.OwnedArray;
import java.util.Objects;

/** An immutable byte span over the owner's slab, without a second payload allocation. */
@ExportLibrary(InteropLibrary.class)
final class SlabBytes implements OwnedArray {
  private final SlabMessage owner;
  private final byte[] bytes;
  private final int offset;
  private final int length;

  SlabBytes(SlabMessage owner, int header) {
    this.owner = owner;
    bytes = owner.payloadBytes(header);
    offset = owner.payloadOffset(header);
    length = SlabFormat.stringLength(owner.bytes, header);
  }

  @Override
  public int size() {
    return length;
  }

  @Override
  public boolean isBytes() {
    return true;
  }

  @Override
  public Object get(int index) {
    Objects.checkIndex(index, length);
    return (long) Byte.toUnsignedInt(bytes[offset + index]);
  }

  @Override
  public void copyBytesTo(byte[] target, int targetOffset) {
    Objects.checkFromIndexSize(targetOffset, length, target.length);
    System.arraycopy(bytes, offset, target, targetOffset, length);
  }

  @ExportMessage
  boolean hasArrayElements() {
    return true;
  }

  @ExportMessage
  long getArraySize() {
    return length;
  }

  @ExportMessage
  boolean isArrayElementReadable(long index) {
    return index >= 0 && index < length;
  }

  @ExportMessage
  Object readArrayElement(long index) throws InvalidArrayIndexException {
    if (!isArrayElementReadable(index)) throw InvalidArrayIndexException.create(index);
    return get((int) index);
  }
}
