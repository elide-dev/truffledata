package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;

/**
 * Schemaless inspection. Never guesses strings/messages/packed values from length-delimited bytes.
 */
public final class ProtoWire {
  private ProtoWire() {}

  /** Ordered rows of (number, wireType, value); duplicate fields remain separate. */
  @TruffleBoundary
  public static DataArray decode(byte[] bytes, Limits limits, int maxDepth, int maxWireBytes) {
    return ProtoCodec.inspect(bytes, limits, maxDepth, maxWireBytes);
  }
}
