package dev.elide.truffledata.json;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import dev.elide.myna.values.ValueTape;
import org.jspecify.annotations.Nullable;

/**
 * A document borrowed from a {@link JsonSession}: it reads session storage that the session's next
 * call overwrites, so the session ends the borrow (closing this owner and invalidating its
 * assumption) before that call. Views captured from a borrow throw afterwards, in interpreted and
 * compiled code alike.
 */
final class JsonBorrow extends JsonOwner {
  private final JsonMessage.Lexical lexical;
  private @Nullable Assumption alive;

  JsonBorrow(ValueTape tape, JsonMessage.Lexical lexical) {
    super(tape, Thread.currentThread());
    this.lexical = lexical;
  }

  @Override
  @TruffleBoundary
  public Assumption assumption() {
    check();
    if (alive == null) alive = Truffle.getRuntime().createAssumption("JSON borrow alive");
    return alive;
  }

  @Override
  long lexical(long node) {
    if (node < 0 || node >= tape.nodeCount()) throw new IndexOutOfBoundsException("Node index");
    return lexical.span(node);
  }

  @Override
  byte lexicalByte(long offset) {
    return lexical.inputByte(offset);
  }

  /** Ends the borrow: every view of it fails from now on. */
  void end() {
    if (markClosed() && alive != null) alive.invalidate();
  }
}
