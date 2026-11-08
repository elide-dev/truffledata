package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.protobuf.*;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Explicit lifetime for native typed rows and payloads. Closing invalidates compiled readers. */
public final class TypedMessage extends ViewOwner implements ProtoMessage {
  @CompilationFinal private @Nullable TypedRow row;
  @CompilationFinal private long nativeBytes;

  TypedMessage(WireInput.Lease lease) {
    super(lease);
  }

  /** Complete exactly once, before exposing the message or any of its views to a guest. */
  TypedMessage finish(TypedRow row, long nativeBytes) {
    if (this.row != null) throw new IllegalStateException("Typed message already initialized");
    this.row = row;
    this.nativeBytes = nativeBytes;
    return this;
  }

  public TypedRow row() {
    check();
    return Objects.requireNonNull(row);
  }

  public long nativeIndexBytes() {
    check();
    return nativeBytes;
  }

  /** Encode the scoped text/ProtoJSON value while its payload owner is open. */
  @TruffleBoundary
  public byte[] encode() {
    check();
    return row().plan().encode(row().snapshot());
  }

  @TruffleBoundary
  public ProtoRow snapshot() {
    check();
    return row().snapshot();
  }

  @Override
  @TruffleBoundary
  public void close() {
    closeOwner();
  }
}
