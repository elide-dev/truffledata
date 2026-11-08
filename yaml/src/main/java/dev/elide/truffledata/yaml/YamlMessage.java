package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import dev.elide.myna.values.ValueTape;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TapeLexical;
import dev.elide.truffledata.tape.TapeOwner;
import org.jspecify.annotations.Nullable;

/** Owned YAML document. Every derived view is invalidated when the message closes. */
public final class YamlMessage extends TapeOwner implements AutoCloseable {
  interface Storage extends TapeLexical, Runnable {}

  private final Storage storage;
  private final YamlMetadata metadata;
  private @Nullable Assumption alive;

  YamlMessage(ValueTape tape, Storage storage, YamlMetadata metadata) {
    super(tape, Thread.currentThread());
    this.storage = storage;
    this.metadata = metadata;
  }

  @Override
  public TapeFormat format() {
    return metadata.format;
  }

  @TruffleBoundary
  public YamlDirectives directives() {
    check();
    return metadata.directives;
  }

  @TruffleBoundary
  public YamlMetadata metadata() {
    check();
    return metadata;
  }

  @Override
  @TruffleBoundary
  public Assumption assumption() {
    check();
    if (alive == null) alive = Truffle.getRuntime().createAssumption("YAML message alive");
    return alive;
  }

  @Override
  protected long lexical(long node) {
    return storage.span(node);
  }

  @Override
  protected byte lexicalByte(long offset) {
    return storage.inputByte(offset);
  }

  @Override
  @TruffleBoundary
  public void close() {
    if (markClosed()) {
      if (alive != null) alive.invalidate();
      storage.run();
    }
  }
}
