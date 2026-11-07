package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.ValueTape;

/**
 * A prepared member name over Myna's {@link ValueTape.Utf8Key} (elide-dev/myna#47): its UTF-8
 * bytes, the node words a short key is stored as, and the 32-bit FNV-1a hash Myna ABI v4 object
 * directories are sorted by. Prepare once and reuse; reads compare a short key against a node with
 * three word loads, and look keys up through the object's directory.
 */
public final class TapeKey {
  /** Keys up to this many UTF-8 bytes are stored inline in their node (Myna's inline text). */
  static final int INLINE_BYTES = 16;

  final ValueTape.Utf8Key prepared;
  final int length;
  final byte[] bytes;
  // Node word 0 of this key stored inline: tag STRING | length << 32.
  final long header;
  // Inline text words; Myna zero-pads bytes past the length.
  final long low;
  final long high;
  // Myna's directory hash: 32-bit FNV-1a over the decoded bytes (ValueTape.hashUtf8).
  final int hash;

  private TapeKey(ValueTape.Utf8Key prepared) {
    this.prepared = prepared;
    this.length = prepared.byteLength();
    this.bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = prepared.byteAt(i);
    this.low = prepared.lowWord();
    this.high = prepared.highWord();
    this.header = ValueTape.STRING | ((long) length << 32);
    this.hash = prepared.hash();
  }

  /** Encodes {@code name} as UTF-8; an unpaired surrogate is rejected, as no decoded key can hold it. */
  @TruffleBoundary
  public static TapeKey of(String name) {
    return new TapeKey(ValueTape.Utf8Key.of(name));
  }

  /** The same key prepared by Myna. */
  @TruffleBoundary
  public static TapeKey of(ValueTape.Utf8Key prepared) {
    return new TapeKey(java.util.Objects.requireNonNull(prepared));
  }

  /** UTF-8 length of the key. */
  public int utf8Length() {
    return length;
  }

  /** The low 15 bits of {@link #hash}: what the native key side table publishes per key node. */
  int sideHash() {
    return hash & 0x7fff;
  }

  boolean inline() {
    return length <= INLINE_BYTES;
  }
}
