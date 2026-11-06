package dev.elide.truffledata.json;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import dev.elide.myna.values.ValueTape;
import org.jspecify.annotations.Nullable;

/** Thread-confined owner of a validated JSON index. Closing invalidates every derived view. */
public final class JsonMessage extends JsonOwner implements AutoCloseable {
  private final Thread thread = Thread.currentThread();
  private final Runnable release;
  private final Lexical lexical;
  private @Nullable Assumption alive;
  private boolean open = true;

  /** Backend-specific access to the native lexical span table and retained input. */
  interface Lexical {
    long span(long node);

    byte inputByte(long offset);
  }

  JsonMessage(ValueTape tape, Lexical lexical, Runnable release) {
    super(tape);
    this.lexical = lexical;
    this.release = release;
  }

  @Override
  void check() {
    checkThread();
    if (!open) throw new IllegalStateException("JSON message closed");
  }

  @Override
  void checkThread() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON message thread mismatch");
  }

  @Override
  @TruffleBoundary
  public Assumption assumption() {
    check();
    if (alive == null) alive = Truffle.getRuntime().createAssumption("JSON message alive");
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

  @Override
  @TruffleBoundary
  public void close() {
    checkThread();
    if (open) {
      open = false;
      if (alive != null) alive.invalidate();
      release.run();
    }
  }
}
