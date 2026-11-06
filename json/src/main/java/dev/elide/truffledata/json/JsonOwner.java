package dev.elide.truffledata.json;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.Node;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;

/**
 * Owner of a validated JSON index. Views read the tape only after {@link #check()}; owners that
 * can close expose an {@link Assumption} so compiled reads cache liveness without a field load.
 */
public abstract sealed class JsonOwner permits JsonMessage {
  final ValueTape tape;

  JsonOwner(ValueTape tape) {
    this.tape = tape;
  }

  /** Rejects closed or cross-thread access; immutable owners never fail. */
  abstract void check();

  /** Valid while this owner is open; closing invalidates it. */
  @TruffleBoundary
  public abstract Assumption assumption();

  /** Packed lexical span (offset {@code << 32 | length} into the retained input), or 0. */
  abstract long lexical(long node);

  /** Reads the raw UTF-8 bytes of a lexical span from the retained input region. */
  abstract byte lexicalByte(long offset);

  @TruffleBoundary
  final String lexicalText(long node) {
    check();
    long span = lexical(node);
    if (span == 0) throw new IllegalStateException("JSON node has no lexical text");
    int length = (int) span;
    long offset = span >>> 32;
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = lexicalByte(offset + i);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  public final JsonValue root() {
    check();
    return new JsonValue(this, 0);
  }

  /** Cache only retained owners; uncached ingestion and megamorphic sites use ordinary checks. */
  @GenerateUncached
  @GenerateInline(false)
  public abstract static class CheckOpenNode extends Node {
    public abstract void execute(JsonOwner owner);

    @NeverDefault
    public static CheckOpenNode create() {
      return JsonOwnerFactory.CheckOpenNodeGen.create();
    }

    @Specialization(guards = "owner == cachedOwner", assumptions = "alive", limit = "3")
    static void cached(
        JsonOwner owner,
        @Cached("owner") JsonOwner cachedOwner,
        @Cached("cachedOwner.assumption()") Assumption alive) {
      owner.checkThread();
    }

    @Specialization(replaces = "cached")
    static void generic(JsonOwner owner) {
      owner.check();
    }
  }

  /** Thread confinement only; liveness is covered by the assumption at cached sites. */
  abstract void checkThread();
}
