package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.Layouts.LE;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.OwnedArray;
import dev.elide.truffledata.bebop.BebopCodec.Dec;
import dev.elide.truffledata.bebop.BebopCodec.Node;
import dev.elide.truffledata.bebop.Layouts.ArrayLayout;
import dev.elide.truffledata.bebop.Layouts.Slot;
import java.util.Objects;

/**
 * An array, set or map read in place from a validated {@link BebopDocument}. Elements of fixed
 * width are addressed directly; for variable-width elements one bounded structural pass records
 * each element's offset the first time one is read. A map is an array of rows with {@code key}
 * and {@code value}.
 */
@ExportLibrary(InteropLibrary.class)
public final class BebopArray implements OwnedArray {
  private final BebopDocument document;
  private final ArrayLayout layout;
  private final int data;
  private final int count;
  private int[] offsets = NONE;

  private static final int[] NONE = new int[0];

  private BebopArray(BebopDocument document, ArrayLayout layout, int data, int count) {
    this.document = document;
    this.layout = layout;
    this.data = data;
    this.count = count;
  }

  static BebopArray at(BebopDocument document, ArrayLayout layout, int at) {
    if (layout.length >= 0) return new BebopArray(document, layout, at, layout.length);
    return new BebopArray(document, layout, at + 4, LE.getInt(document.bytes, at));
  }

  public BebopDocument document() {
    return document;
  }

  @Override
  public int size() {
    return count;
  }

  @Override
  public Object get(int index) {
    return BebopRow.value(document, layout.element, offset(Objects.checkIndex(index, count)));
  }

  /** Element {@code index} of an array of integers to 63 bits or {@code int64}. */
  public long getLong(int index) {
    Slot element = layout.element;
    if (element.shape != Slot.LONG) throw new UnsupportedOperationException("Not an integer array");
    return element.loadLong(document.bytes, offset(Objects.checkIndex(index, count)));
  }

  /** Element {@code index} of an array of floating-point numbers. */
  public double getDouble(int index) {
    Slot element = layout.element;
    if (element.shape != Slot.DOUBLE)
      throw new UnsupportedOperationException("Not a floating-point array");
    return element.loadDouble(document.bytes, offset(Objects.checkIndex(index, count)));
  }

  private int offset(int index) {
    int stride = layout.stride;
    if (stride >= 0) return data + index * stride;
    int[] known = offsets;
    if (known == NONE) offsets = known = scan();
    return known[index];
  }

  @TruffleBoundary
  private int[] scan() {
    byte[] b = document.bytes;
    BebopCodec codec = document.codec();
    Dec d = new Dec(b, data, b.length, false, codec.profile(), codec.limits());
    int[] out = new int[count];
    for (int i = 0; i < count; i++) {
      out[i] = d.pos;
      for (Node part : layout.parts) part.skip(d);
    }
    return out;
  }

  @Override
  public boolean isBytes() {
    return layout.element.scalar == BebopType.Scalar.BYTE && layout.element.shape == Slot.LONG;
  }

  @Override
  public void copyBytesTo(byte[] target, int offset) {
    if (!isBytes()) throw new ContractViolation("Expected byte storage");
    Objects.checkFromIndexSize(offset, count, target.length);
    System.arraycopy(document.bytes, data, target, offset, count);
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
    if (index < 0 || index >= count) throw InvalidArrayIndexException.create(index);
    return BebopRow.value(document, layout.element, offset((int) index));
  }
}
