package dev.elide.truffledata.json;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import dev.elide.truffledata.tape.TapeReader;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TapeOwner;
import org.jspecify.annotations.Nullable;

/** Thread-confined owner of a validated JSON index. Closing invalidates every derived view. */
public abstract class JsonMessage extends TapeOwner implements AutoCloseable {
  /** JSON tapes hold no alias nodes: one shared format, without alias tables. */
  static final TapeFormat FORMAT = TapeFormat.of("JSON");

  /**
   * The JVM result's release action, run once on close. Native messages store their result
   * address and compact reader fields directly on the owner.
   */
  interface Storage extends Runnable {}

  private @Nullable Assumption alive;

  JsonMessage(TapeReader tape) { super(tape, Thread.currentThread()); }
  JsonMessage() { super(Thread.currentThread()); }
  protected abstract void release();

  static final class Managed extends JsonMessage {
    private final Storage storage;
    Managed(TapeReader tape, Storage storage) { super(tape); this.storage = storage; }
    @Override protected void release() { storage.run(); }
  }

  @Override protected final boolean aliases() { return false; }

  @Override
  public TapeFormat format() {
    return FORMAT;
  }

  @Override
  @TruffleBoundary
  public Assumption assumption() {
    check();
    if (alive == null) alive = Truffle.getRuntime().createAssumption("JSON message alive");
    return alive;
  }

  @Override
  protected long lexical(long node) {
    if (node < 0 || node >= tape.nodeCount()) throw new IndexOutOfBoundsException("Node index");
    return tape.span(node);
  }

  @Override
  protected byte lexicalByte(long offset) {
    return tape.inputByte(offset);
  }

  @Override
  @TruffleBoundary
  public void close() {
    if (markClosed()) {
      if (alive != null) alive.invalidate();
      release();
    }
  }
}
