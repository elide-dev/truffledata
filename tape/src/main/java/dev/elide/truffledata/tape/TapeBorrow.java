package dev.elide.truffledata.tape;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import dev.elide.myna.values.ValueTape;
import org.jspecify.annotations.Nullable;

/**
 * A document borrowed from a {@link TapeSession}: it reads session storage that the session's next
 * call overwrites, so the session ends the borrow (closing this owner and invalidating its
 * assumption) before that call. Views captured from a borrow throw afterwards, in interpreted and
 * compiled code alike. Session backends create one per borrowed decode.
 */
public final class TapeBorrow extends TapeOwner {
  private final TapeLexical lexical;
  private final TapeFormat format;
  private @Nullable Assumption alive;

  /** A borrow of the session's current document, confined to the calling thread. */
  public TapeBorrow(ValueTape tape, TapeLexical lexical, TapeFormat format) {
    super(tape, Thread.currentThread());
    this.lexical = lexical;
    this.format = checkFormat(tape, format);
  }

  @Override
  public TapeFormat format() {
    return format;
  }

  @Override
  @TruffleBoundary
  public Assumption assumption() {
    check();
    if (alive == null) alive = Truffle.getRuntime().createAssumption(format.name + " borrow alive");
    return alive;
  }

  @Override
  protected long lexical(long node) {
    if (node < 0 || node >= tape.nodeCount()) throw new IndexOutOfBoundsException("Node index");
    return lexical.span(node);
  }

  @Override
  protected byte lexicalByte(long offset) {
    return lexical.inputByte(offset);
  }

  /** Ends the borrow: every view of it fails from now on. */
  void end() {
    if (markClosed() && alive != null) alive.invalidate();
  }
}
