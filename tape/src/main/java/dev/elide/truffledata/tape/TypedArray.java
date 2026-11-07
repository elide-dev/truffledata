package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import dev.elide.truffledata.*;
import java.util.ArrayList;

/**
 * Scoped elements of a repeated typed field. Elements are never null; scalar elements are slots in
 * the owner's arena, record elements are contiguous record-table entries. Every access checks the
 * owner; {@link #snapshot()} detaches an owned immutable copy.
 */
@ExportLibrary(InteropLibrary.class)
public final class TypedArray implements BorrowedArray {
  final TypedOwner owner;
  // Element contract: a scalar kind or RECORD.
  final Field element;
  // Slot index (scalar elements) or record-table index (record elements) of element 0.
  final long first;
  final int count;

  TypedArray(TypedOwner owner, Field element, long first, int count) {
    this.owner = owner;
    this.element = element;
    this.first = first;
    this.count = count;
  }

  /** Element contract shared by every element. */
  public Field elementField() {
    return element;
  }

  @Override
  public int size() {
    owner.check();
    return count;
  }

  private long slot(int index, Field.Kind kind) {
    owner.check();
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
    if (element.kind() != kind) throw new IllegalArgumentException("Element kind mismatch");
    return first + index;
  }

  public long longValue(int index) {
    return owner.bits(slot(index, Field.Kind.INT64));
  }

  public double doubleValue(int index) {
    return Double.longBitsToDouble(owner.bits(slot(index, Field.Kind.FLOAT64)));
  }

  public boolean booleanValue(int index) {
    return owner.bits(slot(index, Field.Kind.BOOLEAN)) != 0;
  }

  public String stringValue(int index) {
    return owner.text(slot(index, Field.Kind.STRING));
  }

  /** Record element; shares this array's owner. */
  public TypedRecord record(int index) {
    owner.check();
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
    if (element.kind() != Field.Kind.RECORD)
      throw new IllegalArgumentException("Element kind mismatch");
    return new TypedRecord(owner, Math.toIntExact(first + index));
  }

  /** Normalized element value: Long, Double, Boolean, String, ExactNumber or TypedRecord. */
  @Override
  public Object element(int index) {
    return switch (element.kind()) {
      case INT64 -> longValue(index);
      case FLOAT64 -> doubleValue(index);
      case BOOLEAN -> booleanValue(index);
      case STRING -> stringValue(index);
      case EXACT ->
          Values.normalize(TypedOwner.unsigned(owner.bits(slot(index, Field.Kind.EXACT))));
      case RECORD -> record(index);
      default -> throw new IllegalStateException("Unsupported element kind");
    };
  }

  /** Owned immutable copy; record elements become rows of one {@link Batch}. */
  @Override
  @TruffleBoundary
  public DataArray snapshot() {
    owner.check();
    Object[] values = new Object[count];
    if (element.kind() == Field.Kind.RECORD) {
      if (count == 0) return new DataArray(values);
      var rows = new ArrayList<Object[]>(count);
      Schema schema = record(0).schema();
      for (int i = 0; i < count; i++) rows.add(record(i).snapshotValues());
      Batch batch = Batch.copyOf(schema, rows, TypedRecord.SNAPSHOT_LIMITS);
      for (int i = 0; i < count; i++) values[i] = batch.row(i);
    } else {
      for (int i = 0; i < count; i++) values[i] = element(i);
    }
    return new DataArray(values);
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
    if (index < 0 || index >= size()) throw InvalidArrayIndexException.create(index);
    return element((int) index);
  }
}
