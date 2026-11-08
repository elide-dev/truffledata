package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import dev.elide.truffledata.tape.TapeReader;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TapeLexical;
import dev.elide.truffledata.tape.TapeOwner;
import org.jspecify.annotations.Nullable;

/** Owned YAML document. Every derived view is invalidated when the message closes. */
public abstract class YamlMessage extends TapeOwner implements AutoCloseable {
  interface Storage extends TapeLexical, Runnable {}

  private final YamlMetadata metadata;
  private @Nullable Assumption alive;

  YamlMessage(TapeReader tape, YamlMetadata metadata) {
    super(tape, Thread.currentThread());
    this.metadata = metadata;
  }

  YamlMessage(YamlMetadata metadata) {
    super(Thread.currentThread());
    this.metadata = metadata;
  }

  protected abstract void release();

  private static class Managed extends YamlMessage {
    private final Storage storage;

    Managed(TapeReader tape, Storage storage, YamlMetadata metadata) {
      super(tape, metadata);
      this.storage = storage;
    }

    @Override protected long lexical(long node) { return storage.span(node); }
    @Override protected byte lexicalByte(long offset) { return storage.inputByte(offset); }
    @Override protected void release() { storage.run(); }
  }

  @Override
  protected final boolean confined() {
    return true;
  }

  // A class-constant absence of references keeps fresh child views scalar-replaceable.
  // Tags, anchors, styles and YAML numeric spelling still live in the full format metadata.
  @Override
  protected boolean aliases() {
    return true;
  }

  private static final class Plain extends Managed {
    Plain(TapeReader tape, Storage storage, YamlMetadata metadata) {
      super(tape, storage, metadata);
    }

    @Override
    protected boolean aliases() {
      return false;
    }
  }

  static YamlMessage create(TapeReader tape, Storage storage, YamlMetadata metadata) {
    return hasAliases(metadata) ? new Managed(tape, storage, metadata) : new Plain(tape, storage, metadata);
  }

  static boolean hasAliases(YamlMetadata metadata) {
    for (int word : metadata.words)
      if ((word & 16) != 0) return true;
    return false;
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
  @TruffleBoundary
  public void close() {
    if (markClosed()) {
      if (alive != null) alive.invalidate();
      release();
    }
  }
}
