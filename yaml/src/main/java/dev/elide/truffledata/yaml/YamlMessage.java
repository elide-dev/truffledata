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
public class YamlMessage extends TapeOwner implements AutoCloseable {
  interface Storage extends TapeLexical, Runnable {}

  private final Storage storage;
  private final YamlMetadata metadata;
  private @Nullable Assumption alive;

  private YamlMessage(TapeReader tape, Storage storage, YamlMetadata metadata) {
    super(tape, Thread.currentThread());
    this.storage = storage;
    this.metadata = metadata;
  }

  // A class-constant absence of references keeps fresh child views scalar-replaceable.
  // Tags, anchors, styles and YAML numeric spelling still live in the full format metadata.
  @Override
  protected boolean aliases() {
    return true;
  }

  private static final class Plain extends YamlMessage {
    Plain(TapeReader tape, Storage storage, YamlMetadata metadata) {
      super(tape, storage, metadata);
    }

    @Override
    protected boolean aliases() {
      return false;
    }
  }

  static YamlMessage create(TapeReader tape, Storage storage, YamlMetadata metadata) {
    for (int word : metadata.words)
      if ((word & 16) != 0) return new YamlMessage(tape, storage, metadata);
    return new Plain(tape, storage, metadata);
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
