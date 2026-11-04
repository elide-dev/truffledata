package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import dev.elide.truffledata.DataArray;

/** Read-only, zero-copy payload view. Its enclosing ViewOwner must remain open. */
@ExportLibrary(InteropLibrary.class)
public final class BorrowedBytes implements dev.elide.truffledata.BorrowedBinary {
  private final ViewOwner message;
  private final int offset;
  private final int length;

  BorrowedBytes(ViewOwner message, int offset, int length) {
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
  public Object readArrayElement(
      long index, @Bind Node node, @Cached InlinedExactClassProfile bufferType)
      throws InvalidArrayIndexException {
    if (!isArrayElementReadable(index)) throw InvalidArrayIndexException.create(index);
    return Byte.toUnsignedLong(
        bufferType.profile(node, message.lease.bytes).get(offset + (int) index));
  }
}
