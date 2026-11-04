package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.protobuf.*;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Explicit lifetime for native typed rows and payloads. Closing invalidates compiled readers. */
public final class TypedMessage extends ViewOwner implements AutoCloseable {
  @CompilationFinal private @Nullable TypedRow row;
  private final boolean binary;
  @CompilationFinal private long nativeBytes;

  TypedMessage(WireInput.Lease lease, boolean binary) {
    super(lease);
    this.binary = binary;
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

  /** Encode an immutable view; binary input can be copied verbatim, including unknown fields. */
  @TruffleBoundary
  public byte[] encode() {
    check();
    if (!binary) return row().plan().encode(row().snapshot());
    var wire = lease.bytes;
    byte[] bytes = new byte[wire.remaining()];
    wire.get(wire.position(), bytes);
    return bytes;
  }

  /** Binary detachment also preserves unknown wire fields through the bounded owned codec. */
  @TruffleBoundary
  public ProtoRow snapshot() {
    check();
    return binary ? row().plan().decode(lease.bytes) : row().snapshot();
  }

  @Override
  @TruffleBoundary
  public void close() {
    closeOwner();
  }
}
