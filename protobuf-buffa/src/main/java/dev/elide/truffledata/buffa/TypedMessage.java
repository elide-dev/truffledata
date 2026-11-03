package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.protobuf.*;

/** Explicit lifetime for native typed rows and payloads. Closing invalidates compiled readers. */
public final class TypedMessage implements AutoCloseable {
  private final WireMessage owner;
  private final TypedRow row;
  private final boolean binary;
  private final long nativeBytes;

  TypedMessage(WireMessage owner, TypedRow row, boolean binary, long nativeBytes) {
    this.owner = owner;
    this.row = row;
    this.binary = binary;
    this.nativeBytes = nativeBytes;
  }

  public TypedRow row() {
    owner.check();
    return row;
  }

  public long nativeIndexBytes() {
    owner.check();
    return nativeBytes;
  }

  /** Binary detachment also preserves unknown wire fields through the bounded owned codec. */
  @TruffleBoundary
  public ProtoRow snapshot() {
    owner.check();
    return binary ? row.plan().decode(owner.lease.bytes) : row.snapshot();
  }

  @Override
  @TruffleBoundary
  public void close() {
    owner.close();
  }
}
