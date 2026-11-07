package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A per-document text array (performance plan Task D2): the session's native decode wrote every
 * key and string once, as Latin-1 when all its code points are at most U+00FF and as UTF-16LE
 * otherwise, with one {@code offset << 32 | byte length << 1 | utf16} entry per node. Strings built
 * here share the array ({@code TruffleString} without a copy; a Latin-1 string becomes UTF-16
 * without a copy too), so the session hands the next parse a fresh array once any string took this
 * one. Strings of {@value #COPY_BYTES} bytes or more are copied, so one retained string never pins
 * a large document's text (user decision 1). Valid only during the borrowed parse that wrote it.
 */
abstract class JsonTextArena {
  /** Strings at least this long (in arena bytes) are copied out instead of shared. */
  static final int COPY_BYTES = 64 * 1024;

  final byte[] array;
  // A string references the array: the session must not overwrite it.
  boolean shared;

  JsonTextArena(byte[] array) {
    this.array = array;
  }

  /** Table entry of a string or key node of the borrowed document. */
  abstract long entry(long node);

  @TruffleBoundary
  final TruffleString string(long node, TruffleString.Encoding encoding) {
    long entry = entry(node);
    int offset = (int) (entry >>> 32);
    int length = (int) (entry >>> 1) & 0x7fffffff;
    if (length == 0) return encoding.getEmpty();
    boolean utf16 = (entry & 1) != 0;
    boolean copy = length >= COPY_BYTES;
    if (!copy) shared = true;
    TruffleString.Encoding stored = utf16 ? TruffleString.Encoding.UTF_16 : TruffleString.Encoding.ISO_8859_1;
    TruffleString text = TruffleString.fromByteArrayUncached(array, offset, length, stored, copy);
    return encoding == stored ? text : text.switchEncodingUncached(encoding);
  }
}
