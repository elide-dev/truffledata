package dev.elide.truffledata.bebop;

/** A codec failure. The kinds are separate outcomes and are never folded into one another. */
public final class BebopException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Why an operation failed. */
  public enum Kind {
    /** The schema or a type in it is outside what the codec or the selected profile supports. */
    UNSUPPORTED,
    /** The bytes are malformed, or valid but not the canonical encoding the profile requires. */
    MALFORMED,
    /** A local byte, count, depth, work or allocation limit was reached. Not a verdict on the bytes. */
    LIMIT,
    /** A host value given to the encoder is outside the schema's value domain. */
    VALUE
  }

  private final Kind kind;

  BebopException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }

  static BebopException unsupported(String message) {
    return new BebopException(Kind.UNSUPPORTED, message);
  }

  static BebopException malformed(String message) {
    return new BebopException(Kind.MALFORMED, message);
  }

  static BebopException limit(String message) {
    return new BebopException(Kind.LIMIT, message);
  }

  static BebopException value(String message) {
    return new BebopException(Kind.VALUE, message);
  }
}
