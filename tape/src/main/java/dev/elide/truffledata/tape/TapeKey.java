package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Prepared UTF-8 member name with its FNV-1a directory hash and two packed comparison words.
 * Prepare once and reuse; short keys compare directly against compact tape text.
 */
public final class TapeKey {
  static final int INLINE_BYTES = 16;
  final int length;
  final byte[] bytes;
  final long low, high;
  final int hash;

  private TapeKey(byte[] bytes) {
    this.bytes = bytes;
    this.length = bytes.length;
    long lo = 0, hi = 0;
    int h = 0x811c9dc5;
    for (int i = 0; i < bytes.length; i++) {
      long b = bytes[i] & 255L;
      if (i < 8) lo |= b << (8 * i);
      else if (i < 16) hi |= b << (8 * (i - 8));
      h = (h ^ (int) b) * 0x01000193;
    }
    this.low = lo;
    this.high = hi;
    this.hash = h;
  }

  /** Encodes name as UTF-8; an unpaired surrogate cannot occur in a decoded member name. */
  @TruffleBoundary
  public static TapeKey of(String name) {
    Objects.requireNonNull(name);
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (++i == name.length() || !Character.isLowSurrogate(name.charAt(i)))
          throw new IllegalArgumentException("Unpaired surrogate in member name");
      } else if (Character.isLowSurrogate(c)) {
        throw new IllegalArgumentException("Unpaired surrogate in member name");
      }
    }
    return new TapeKey(name.getBytes(StandardCharsets.UTF_8));
  }

  /** Copies a Myna prepared key for source compatibility; compact reads use this key's bytes. */
  @TruffleBoundary
  public static TapeKey of(ValueTape.Utf8Key prepared) {
    Objects.requireNonNull(prepared);
    byte[] bytes = new byte[prepared.byteLength()];
    for (int i = 0; i < bytes.length; i++) bytes[i] = prepared.byteAt(i);
    return new TapeKey(bytes);
  }

  /** UTF-8 length of the key. */
  public int utf8Length() { return length; }

  boolean inline() { return length <= INLINE_BYTES; }
}
