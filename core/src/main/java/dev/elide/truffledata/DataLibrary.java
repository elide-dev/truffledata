package dev.elide.truffledata;

import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/** Internal opt-in protocol; ordinary guests consume InteropLibrary instead. */
@GenerateLibrary
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
}
