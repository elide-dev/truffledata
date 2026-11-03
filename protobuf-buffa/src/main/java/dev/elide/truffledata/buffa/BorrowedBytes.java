package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;
import dev.elide.truffledata.DataArray;

/** Read-only, zero-copy payload view. Its enclosing WireMessage must remain open. */
@ExportLibrary(InteropLibrary.class)
public final class BorrowedBytes implements dev.elide.truffledata.BorrowedBinary {
  private final WireMessage message;
  private final int offset;
  private final int length;

  BorrowedBytes(WireMessage message, int offset, int length) {
    this.message = message;
    this.offset = offset;
    this.length = length;
  }

  public int size() {
    message.check();
    return length;
  }

  public long get(int index) {
    message.check();
    if (index < 0 || index >= length) throw new IndexOutOfBoundsException(index);
    return Byte.toUnsignedLong(message.lease.bytes.get(offset + index));
  }

  @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
  public DataArray snapshot() {
    message.check();
    return DataArray.copyOfBytes(message.lease.slice(offset, length));
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
