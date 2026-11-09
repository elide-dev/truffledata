package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import dev.elide.truffledata.DataArray;
import dev.elide.truffledata.OwnedArray;
import java.util.Objects;

/**
 * Lazy durable list: primitive elements stay unboxed in the slab until requested. The element
 * category, child type, expansion and projection outcome are decided once when the view is
 * created, so an element read is a bounds check, one slab load and the category's conversion.
 */
@ExportLibrary(InteropLibrary.class)
final class SlabArray implements OwnedArray {
  static final DataArray EMPTY = new DataArray(new Object[0]);

  private static final int MESSAGE = 0;
  private static final int STRING = 1;
  private static final int BYTES = 2;
  private static final int INT = 3;
  private static final int UINT32 = 4;
  private static final int LONG = 5;
  private static final int UINT64 = 6;
  private static final int FLOAT = 7;
  private static final int DOUBLE = 8;
  private static final int BOOL = 9;

  private final SlabMessage owner;
  private final int list;
  private final int count;
  private final int width;
  private final int category;
  private final int childType;
  private final boolean expandedChild;
  private final boolean omitted;

  SlabArray(SlabMessage owner, int type, int ordinal, int list, boolean expanded) {
    this.owner = owner;
    this.list = list;
    count = SlabFormat.listCount(owner.bytes, list);
    width = owner.layout.kind(type, ordinal).element;
    var kind = owner.layout.plan(type).field(ordinal).kind();
    category =
        switch (kind) {
          case MESSAGE, GROUP -> MESSAGE;
          case STRING -> STRING;
          case BYTES -> BYTES;
          case UINT32, FIXED32 -> UINT32;
          case INT64, SINT64, SFIXED64 -> LONG;
          case UINT64, FIXED64 -> UINT64;
          case FLOAT -> FLOAT;
          case DOUBLE -> DOUBLE;
          case BOOL -> BOOL;
          default -> INT;
        };
    childType = kind.message() ? owner.layout.childType(type, ordinal) : -1;
    expandedChild = expanded || owner.expandChild(type, ordinal);
    omitted = owner.omitted(type, ordinal, expanded);
  }

  @Override
  public int size() {
    return count;
  }

  @Override
  public Object get(int index) {
    Objects.checkIndex(index, count);
    if (omitted) throw SlabMessage.omitted();
    byte[] slab = owner.bytes;
    int at = SlabFormat.listElement(list, width, index);
    return switch (category) {
      case MESSAGE -> owner.row(childType, getInt(slab, at), expandedChild);
      case STRING -> owner.text(getInt(slab, at));
      case BYTES -> owner.blob(getInt(slab, at));
      case INT -> (long) getInt(slab, at);
      case UINT32 -> getInt(slab, at) & 0xffffffffL;
      case LONG -> SlabFormat.getLong(slab, at);
      case UINT64 -> owner.unsigned(at, SlabFormat.getLong(slab, at));
      case FLOAT -> (double) Float.intBitsToFloat(getInt(slab, at));
      case DOUBLE -> Double.longBitsToDouble(SlabFormat.getLong(slab, at));
      default -> getUnsignedByte(slab, at) != 0;
    };
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
