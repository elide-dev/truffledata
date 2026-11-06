package dev.elide.truffledata.json;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import dev.elide.myna.values.ValueTape;
import org.jspecify.annotations.Nullable;

/** Thread-confined owner of a validated JSON index. Closing invalidates every derived view. */
public final class JsonMessage extends JsonOwner implements AutoCloseable {
  private final Runnable release;
  private final Lexical lexical;
  private @Nullable Assumption alive;

  /** Backend-specific access to the native lexical span and key-hash tables and retained input. */
  interface Lexical {
    long span(long node);

    /** Key-hash side-table entry of a node (see {@link JsonOwner#keyHash}). */
    int meta(long node);

    byte inputByte(long offset);
  }

  JsonMessage(ValueTape tape, Lexical lexical, Runnable release) {
    super(tape, Thread.currentThread());
    this.lexical = lexical;
    this.release = release;
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
  int meta(long node) {
    if (node < 0 || node >= tape.nodeCount()) throw new IndexOutOfBoundsException("Node index");
    return lexical.meta(node);
  }

  @Override
  byte lexicalByte(long offset) {
    return lexical.inputByte(offset);
  }

  @Override
  @TruffleBoundary
  public void close() {
    if (markClosed()) {
      if (alive != null) alive.invalidate();
      release.run();
    }
  }
}
