package dev.elide.truffledata.json;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import dev.elide.myna.values.ValueTape;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TapeLexical;
import dev.elide.truffledata.tape.TapeOwner;
import org.jspecify.annotations.Nullable;

/** Thread-confined owner of a validated JSON index. Closing invalidates every derived view. */
public final class JsonMessage extends TapeOwner implements AutoCloseable {
  /** JSON tapes hold no alias nodes: one shared format, without alias tables. */
  static final TapeFormat FORMAT = TapeFormat.of("JSON");

  private final Runnable release;
  private final TapeLexical lexical;
  private @Nullable Assumption alive;

  JsonMessage(ValueTape tape, TapeLexical lexical, Runnable release) {
    super(tape, Thread.currentThread());
    this.lexical = lexical;
    this.release = release;
  }

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
    return lexical.span(node);
  }

  @Override
  protected byte lexicalByte(long offset) {
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
