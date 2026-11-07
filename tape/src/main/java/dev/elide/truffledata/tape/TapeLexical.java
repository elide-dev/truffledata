package dev.elide.truffledata.tape;

/**
 * Backend-specific access to a decoded document's native lexical span table and retained input
 * (one {@code offset << 32 | length} span per node). Implemented by each format's static-image and
 * JVM backends; owners read it only after their liveness checks.
 */
public interface TapeLexical {
  /** Packed span of {@code node} (a valid node index), or 0. */
  long span(long node);

  /** Byte at {@code offset} of the retained input. */
  byte inputByte(long offset);
}
