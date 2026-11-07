package dev.elide.truffledata.tape;

/**
 * A closed or cross-thread owner. The message names the owner's format ({@code "JSON message
 * closed"}) but is built only when read, so the liveness checks inlined into every compiled read
 * throw with the same shape as a constant-message exception and owners carry no format reference.
 */
final class OwnerStateException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  static final int CLOSED = 0, THREAD = 1, RECORD_CLOSED = 2, RECORD_THREAD = 3;

  private final transient Object owner;
  private final int kind;

  OwnerStateException(Object owner, int kind) {
    this.owner = owner;
    this.kind = kind;
  }

  @Override
  public String getMessage() {
    TapeFormat format =
        owner instanceof TapeOwner tape ? tape.format() : ((TypedOwner) owner).format();
    return switch (kind) {
      case CLOSED -> format.closed;
      case THREAD -> format.threadMismatch;
      case RECORD_CLOSED -> format.recordClosed;
      default -> format.recordThreadMismatch;
    };
  }
}
