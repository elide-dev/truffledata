package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;
import org.jspecify.annotations.Nullable;

/** Shared interop facade. Concrete representations export DataLibrary. */
@ExportLibrary(InteropLibrary.class)
public abstract class AbstractRow implements TruffleObject {
  protected final Schema descriptor;

  protected AbstractRow(Schema descriptor) {
    this.descriptor = descriptor;
  }

  public final Schema schema() {
    return descriptor;
  }

  /**
   * Whether this row borrows a closable owner. Scoped adapters override this constant predicate;
   * owned containers reject such rows until the adapter explicitly detaches them. Field validation
   * can still accept a scoped RECORD/ANY value for guest reads within its owner's lifetime.
   */
  protected boolean scoped() {
    return false;
  }

  /**
   * Optional transparent immutable backing for host-side payload accounting. The returned storage
   * must have the identical schema and values that DataLibrary reads from this row, including null
   * and missing states. Strict mode checks this cooperation contract. Mutable or transforming
   * adapters must retain the default; their values are read through DataLibrary as usual.
   */
  protected @Nullable SnapshotStorage payloadStorage() {
    return null;
  }

  @ExportMessage
  final boolean hasMembers() {
    return true;
  }

  @ExportMessage
  final Object getMembers(boolean includeInternal) {
    return descriptor.names();
  }

  @ExportMessage
  final boolean hasArrayElements() {
    return true;
  }

  @ExportMessage
  final long getArraySize() {
    return descriptor.size();
  }

  @ExportMessage
  final boolean isArrayElementReadable(long index, @CachedLibrary("this") DataLibrary data) {
    return index >= 0 && index < descriptor.size() && data.isPresent(this, (int) index);
  }

  @ExportMessage
  final Object readArrayElement(long index, @CachedLibrary("this") DataLibrary data)
      throws InvalidArrayIndexException {
    if (index < 0 || index >= descriptor.size()) throw InvalidArrayIndexException.create(index);
    Object value = data.read(this, (int) index);
    if (value == Missing.INSTANCE) throw InvalidArrayIndexException.create(index);
    return value;
  }

  @ExportMessage
  @TruffleBoundary
  final boolean isMemberReadable(String name) {
    int index = descriptor.indexOf(name);
    return index >= 0 && DataLibrary.factory().getUncached().isPresent(this, index);
  }

  @ExportMessage
  static class ReadMember {
    @Specialization(
        guards = {"receiver.descriptor == schema", "name.equals(cachedName)"},
        limit = "3")
    static Object cached(
        AbstractRow receiver,
        String name,
        @Cached("receiver.descriptor") Schema schema,
        @Cached("name") String cachedName,
        @Cached("schema.indexOf(cachedName)") int index,
        @CachedLibrary("receiver") DataLibrary data)
        throws UnknownIdentifierException {
      return readAt(receiver, name, index, data);
    }

    @Specialization(replaces = "cached")
    static Object generic(
        AbstractRow receiver, String name, @CachedLibrary(limit = "3") DataLibrary data)
        throws UnknownIdentifierException {
      return readAt(receiver, name, receiver.descriptor.indexOf(name), data);
    }

    static Object readAt(AbstractRow receiver, String name, int index, DataLibrary data)
        throws UnknownIdentifierException {
      if (index < 0) throw UnknownIdentifierException.create(name);
      Object value = data.read(receiver, index);
      if (value == Missing.INSTANCE) throw UnknownIdentifierException.create(name);
      return value;
    }
  }
}
