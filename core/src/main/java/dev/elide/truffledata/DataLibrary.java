package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/** Internal opt-in protocol; ordinary guests consume InteropLibrary instead. */
@GenerateLibrary(assertions = DataLibrary.Assertions.class)
public abstract class DataLibrary extends Library {
  public static LibraryFactory<DataLibrary> factory() {
    return LibraryFactory.resolve(DataLibrary.class);
  }

  public abstract Schema getSchema(Object receiver);

  public abstract Object read(Object receiver, int index);

  public long readLong(Object receiver, int index) throws UnexpectedResultException {
    Object value = read(receiver, index);
    if (value instanceof Long n) return n;
    throw new UnexpectedResultException(value);
  }

  public double readDouble(Object receiver, int index) throws UnexpectedResultException {
    Object value = read(receiver, index);
    if (value instanceof Double n) return n;
    throw new UnexpectedResultException(value);
  }

  public boolean readBoolean(Object receiver, int index) throws UnexpectedResultException {
    Object value = read(receiver, index);
    if (value instanceof Boolean b) return b;
    throw new UnexpectedResultException(value);
  }

  public boolean isPresent(Object receiver, int index) {
    return read(receiver, index) != Missing.INSTANCE;
  }

  /** Diagnostic wrapper installed by Truffle only when JVM assertions are enabled. */
  static final class Assertions extends DataLibrary {
    private static final boolean STRICT = Boolean.getBoolean("truffledata.strict");
    @Child private DataLibrary delegate;

    Assertions(DataLibrary delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean accepts(Object receiver) {
      return delegate.accepts(receiver);
    }

    @Override
    public Schema getSchema(Object receiver) {
      Schema schema = delegate.getSchema(receiver);
      if (STRICT && receiver instanceof AbstractRow row && row.schema() != schema)
        throw new AssertionError("DataLibrary schema differs from row schema");
      return schema;
    }

    @Override
    public Object read(Object receiver, int index) {
      Object value = delegate.read(receiver, index);
      if (STRICT) check(receiver, index, value);
      return value;
    }

    @Override
    public long readLong(Object receiver, int index) throws UnexpectedResultException {
      try {
        long value = delegate.readLong(receiver, index);
        if (STRICT) check(receiver, index, value);
        return value;
      } catch (UnexpectedResultException e) {
        if (STRICT) {
          check(receiver, index, e.getResult());
          if (e.getResult() instanceof Long) throw new AssertionError("INT64 read rejected a Long");
        }
        throw e;
      }
    }

    @Override
    public double readDouble(Object receiver, int index) throws UnexpectedResultException {
      try {
        double value = delegate.readDouble(receiver, index);
        if (STRICT) check(receiver, index, value);
        return value;
      } catch (UnexpectedResultException e) {
        if (STRICT) {
          check(receiver, index, e.getResult());
          if (e.getResult() instanceof Double)
            throw new AssertionError("FLOAT64 read rejected a Double");
        }
        throw e;
      }
    }

    @Override
    public boolean readBoolean(Object receiver, int index) throws UnexpectedResultException {
      try {
        boolean value = delegate.readBoolean(receiver, index);
        if (STRICT) check(receiver, index, value);
        return value;
      } catch (UnexpectedResultException e) {
        if (STRICT) {
          check(receiver, index, e.getResult());
          if (e.getResult() instanceof Boolean)
            throw new AssertionError("BOOLEAN read rejected a Boolean");
        }
        throw e;
      }
    }

    @Override
    public boolean isPresent(Object receiver, int index) {
      boolean present = delegate.isPresent(receiver, index);
      if (STRICT && !present && !getSchema(receiver).field(index).optional())
        throw new AssertionError("Required field reported missing");
      return present;
    }

    // Diagnostics deliberately stay outside guest compilation. Never reread the source value:
    // accessors may observe live mutable state, so checking must not change access semantics.
    @TruffleBoundary
    private void check(Object receiver, int index, Object value) {
      try {
        Values.validate(getSchema(receiver).field(index), value);
      } catch (ContractViolation e) {
        throw new AssertionError("DataLibrary result contradicts its field contract", e);
      }
    }
  }
}
