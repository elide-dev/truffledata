package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;

/**
 * Reusable cursor over the key, string or decimal token a {@link JsonSink} event receives. One
 * cursor serves a whole export and is rebound per event, so an export creates no view per string.
 * It is valid only during the event that passed it: afterwards every method throws {@link
 * IllegalStateException}. Call {@link #view()} for a {@link JsonValue} that lives as long as its
 * owner.
 */
public final class JsonText {
  /**
   * Non-ASCII strings at least this long materialize through the tape's bulk path (simdutf on
   * SVM, elide-dev/myna#49); shorter and ASCII strings decode from the cursor's scratch buffer.
   */
  static final int BULK_THRESHOLD = 256;

  // Scratch kept between strings; longer ASCII strings use a temporary array.
  private static final int MAX_SCRATCH = 4096;

  // The owner exports pin; a session rebinds its cursor to each borrowed document.
  private JsonOwner owner;
  private long node = -1;
  // Open containers of the export walk: exclusive end node, sign bit set for objects.
  final long[] ends = new long[JsonExportNode.MAX_DEPTH];
  private byte[] scratch = EMPTY;

  private static final byte[] EMPTY = new byte[0];

  JsonText(JsonOwner owner) {
    this.owner = owner;
  }

  /** Rebinds a session's cursor to its next borrowed document. */
  void rebind(JsonOwner next) {
    owner = next;
    node = -1;
  }

  void bind(long next) {
    node = next;
  }

  void unbind() {
    node = -1;
  }

  private long node() {
    long bound = node;
    if (bound < 0) throw new IllegalStateException("JSON text used outside its sink event");
    owner.check();
    return bound;
  }

  /** Decoded UTF-8 length of a key or string; the token length of a decimal. */
  public int utf8Length() {
    return Math.toIntExact(owner.tape.length(node()));
  }

  /** True when every decoded byte is ASCII (from the decoder's side table, no scan). */
  public boolean isAscii() {
    return owner.ascii(node());
  }

  /**
   * The decoder's 15-bit FNV-1a hash of a key's decoded bytes (low 15 bits of the 32-bit hash); 0
   * for strings and decimals. Equal keys have equal hashes in every document.
   */
  public int hash() {
    return (owner.meta(node()) >>> 16) & 0x7fff;
  }

  /** Decoded text of a key or string, or the exact source token of a decimal. */
  @TruffleBoundary
  public String toJavaString() {
    long at = node();
    if (owner.tag(at) == ValueTape.DECIMAL) return owner.lexicalText(at);
    int length = Math.toIntExact(owner.tape.length(at));
    if (length == 0) return "";
    boolean ascii = owner.ascii(at);
    // Long non-ASCII strings: the tape's bulk transcoder (simdutf straight into the String's
    // UTF-16 on SVM). ASCII strings become Latin-1 Strings with one copy from scratch.
    if (!ascii && length >= BULK_THRESHOLD) return owner.tape.stringValue(at);
    byte[] buffer = length <= MAX_SCRATCH ? scratch(length) : new byte[length];
    owner.tape.copyStringUtf8(at, buffer, 0);
    // Tape strings are valid UTF-8 (stage 1 validated the input; escapes were decoded by simdjson),
    // so the decoding constructor never substitutes.
    return new String(buffer, 0, length, ascii ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
  }

  private byte[] scratch(int length) {
    byte[] buffer = scratch;
    if (buffer.length < length)
      scratch = buffer = new byte[Math.min(MAX_SCRATCH, Math.max(length, buffer.length * 2))];
    return buffer;
  }

  /**
   * The text as a {@link TruffleString} in {@code encoding}: one copy of the decoded UTF-8 bytes,
   * transcoded only when {@code encoding} is not UTF-8 and the text is not ASCII.
   */
  @TruffleBoundary
  public TruffleString toTruffleString(TruffleString.Encoding encoding) {
    long at = node();
    if (owner.tag(at) == ValueTape.DECIMAL)
      return TruffleString.fromJavaStringUncached(owner.lexicalText(at), encoding);
    byte[] bytes = new byte[Math.toIntExact(owner.tape.length(at))];
    owner.tape.copyStringUtf8(at, bytes, 0);
    TruffleString utf8 =
        TruffleString.fromByteArrayUncached(bytes, 0, bytes.length, TruffleString.Encoding.UTF_8, false);
    return encoding == TruffleString.Encoding.UTF_8 ? utf8 : utf8.switchEncodingUncached(encoding);
  }

  /** Copies the decoded UTF-8 bytes of a key or string into {@code output}; returns the length. */
  @TruffleBoundary
  public int copyUtf8(byte[] output, int offset) {
    return owner.tape.copyStringUtf8(node(), output, offset);
  }

  /** Whether a key or string decodes to exactly {@code utf8}, without materializing it. */
  @TruffleBoundary
  public boolean utf8Equals(byte[] utf8) {
    return owner.tape.stringEquals(node(), utf8);
  }

  /** A view of the current node that stays valid while its owner is open. */
  public JsonValue view() {
    return new JsonValue(owner, node());
  }
}
