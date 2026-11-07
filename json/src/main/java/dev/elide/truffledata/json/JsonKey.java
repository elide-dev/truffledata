package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

/**
 * A prepared member name: its UTF-8 bytes, the node words a short key is stored as, and the 15-bit
 * hash the native decoder publishes per key node (the low bits of its 32-bit FNV-1a). Prepare once
 * and reuse; reads compare a short key against a node with three word loads, and look keys up by
 * hash first.
 */
public final class JsonKey {
  /** Keys up to this many UTF-8 bytes are stored inline in their node (Myna's inline text). */
  static final int INLINE_BYTES = 16;

  final byte[] bytes;
  final int length;
  // Node word 0 of this key stored inline: tag STRING | length << 32.
  final long header;
  // Inline text words; Myna zero-pads bytes past the length.
  final long low;
  final long high;
  final int hash;

  private JsonKey(byte[] bytes) {
    this.bytes = bytes;
    this.length = bytes.length;
    long lo = 0, hi = 0;
    for (int i = 0; i < Math.min(INLINE_BYTES, bytes.length); i++) {
      if (i < 8) lo |= (bytes[i] & 255L) << (i * 8);
      else hi |= (bytes[i] & 255L) << ((i - 8) * 8);
    }
    low = lo;
    high = hi;
    header = dev.elide.myna.values.ValueTape.STRING | ((long) bytes.length << 32);
    hash = hash(bytes);
  }

  /** Low 15 bits of 32-bit FNV-1a over the bytes: the hash in the native key side table. */
  static int hash(byte[] bytes) {
    int h = 0x811c9dc5;
    for (byte b : bytes) {
      h ^= b & 0xff;
      h *= 0x01000193;
    }
    return h & 0x7fff;
  }

  /** Encodes {@code name} as UTF-8; an unpaired surrogate is rejected, as no JSON key can hold it. */
  @TruffleBoundary
  public static JsonKey of(String name) {
    try {
      ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(name));
      byte[] bytes = new byte[encoded.remaining()];
      encoded.get(bytes);
      return new JsonKey(bytes);
    } catch (CharacterCodingException error) {
      throw new IllegalArgumentException("Unpaired UTF-16 surrogate", error);
    }
  }

  /** UTF-8 length of the key. */
  public int utf8Length() {
    return length;
  }

  boolean inline() {
    return length <= INLINE_BYTES;
  }
}
