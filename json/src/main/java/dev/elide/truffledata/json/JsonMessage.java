package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.ValueTape;

/** Thread-confined owner of a validated JSON index. Closing invalidates every derived view. */
public final class JsonMessage implements AutoCloseable {
  final ValueTape tape;
  private final Thread thread = Thread.currentThread();
  private final Runnable release;
  private boolean open = true;

  JsonMessage(ValueTape tape, Runnable release) {
    this.tape = tape;
    this.release = release;
  }

  void check() {
    checkThread();
    if (!open) throw new IllegalStateException("JSON message closed");
  }

  private void checkThread() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON message thread mismatch");
  }

  public JsonValue root() {
    check();
    return new JsonValue(this, 0);
  }

  @Override
  @TruffleBoundary
  public void close() {
    checkThread();
    if (open) {
      open = false;
      release.run();
    }
  }
}
