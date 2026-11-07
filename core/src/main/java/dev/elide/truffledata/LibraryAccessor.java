package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Default accessor of a {@link Schema}: reads any row of that schema through the uncached {@link
 * DataLibrary}. It keeps today's behaviour for representations that do not bind their own
 * accessors, for host callers outside guest compilation. Read nodes never cache it: rows that only
 * offer this accessor keep their cached library specializations.
 */
public final class LibraryAccessor extends Accessor {
  LibraryAccessor(Schema schema, int ordinal) {
    super(schema, ordinal);
  }

  @Override
  public boolean accepts(Object row) {
    return row instanceof AbstractRow r && r.schema() == schema();
  }

  private Object checked(Object row) {
    if (!accepts(row)) throw mismatch();
    return row;
  }

  @Override
  @TruffleBoundary
  public Object read(Object row) {
    return DataLibrary.factory().getUncached().read(checked(row), ordinal());
  }

  @Override
  @TruffleBoundary
  public boolean present(Object row) {
    return DataLibrary.factory().getUncached().isPresent(checked(row), ordinal());
  }

  @Override
  @TruffleBoundary
  public long readLong(Object row) throws UnexpectedResultException {
    return DataLibrary.factory().getUncached().readLong(checked(row), ordinal());
  }

  @Override
  @TruffleBoundary
  public double readDouble(Object row) throws UnexpectedResultException {
    return DataLibrary.factory().getUncached().readDouble(checked(row), ordinal());
  }

  @Override
  @TruffleBoundary
  public boolean readBoolean(Object row) throws UnexpectedResultException {
    return DataLibrary.factory().getUncached().readBoolean(checked(row), ordinal());
  }
}
