package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * Reusable cursor over the key, string or decimal token a {@link TapeSink} event receives. One
 * cursor serves a whole export and is rebound per event, so an export creates no view per string.
 * It is valid only during the event that passed it: afterwards every method throws {@link
 * IllegalStateException}. Call {@link #view()} for a {@link TapeValue} that lives as long as its
 * owner.
 */
public final class TapeText {
  /**
   * Non-ASCII strings at least this long materialize through the tape's bulk path (simdutf on
   * SVM, elide-dev/myna#49); shorter and ASCII strings decode from the cursor's scratch buffer.
   */
  static final int BULK_THRESHOLD = 256;

  // Scratch kept between strings; longer ASCII strings use a temporary array.
  private static final int MAX_SCRATCH = 4096;

  // The owner exports pin; a session rebinds its cursor to each borrowed document.
  private TapeOwner owner;
  private long node = -1;
  // Open containers of the export walk: exclusive end node, sign bit set for objects.
  final long[] ends = new long[TapeExportNode.MAX_DEPTH];
  // YAML frames and tag cursors grow during warm-up, then exports reuse them without allocation.
  AliasExport.@Nullable State aliasState;
  private byte[] scratch = EMPTY;
  // Text arena of the session parse this cursor serves (Task D2), else null.
  private @Nullable TapeTextArena arena;

  private static final byte[] EMPTY = new byte[0];

  TapeText(TapeOwner owner) {
    this.owner = owner;
  }

  /** Rebinds a session's cursor to its next borrowed document. */
  void rebind(TapeOwner next) {
    owner = next;
    node = -1;
  }

  /** Attaches the session's text arena for one borrowed parse, or detaches it (null). */
  void arena(@Nullable TapeTextArena text) {
    arena = text;
  }

  void bind(long next) {
    node = next;
  }

  void unbind() {
    node = -1;
  }

  private long node() {
    long bound = node;
    if (bound < 0) throw new IllegalStateException("Tape text used outside its sink event");
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
    return owner.tape.hash(node()) & 0x7fff;
  }

  /**
   * Writes the three node words of the bound node into {@code words} at {@code offset} when it is an
   * inline string or key, and returns whether it was.
   */
  boolean inlineWords(long[] words, int offset) {
    long at = node();
    long length = owner.tape.length(at);
    if (owner.tag(at) != ValueTape.STRING || length > TapeKey.INLINE_BYTES) return false;
    words[offset] = ValueTape.STRING | (length << 32);
    words[offset + 1] = owner.tape.textWord(at, 0);
    words[offset + 2] = owner.tape.textWord(at, 8);
    return true;
  }

  private boolean copyInline(long at, int length, byte[] output, int offset) {
    if (length > TapeKey.INLINE_BYTES || owner.tag(at) != ValueTape.STRING) return false;
    owner.tape.copyStringUtf8(at, output, offset);
    return true;
  }

  /** Decoded text of a key or string, or the exact source token of a decimal. */
  @TruffleBoundary
  public String toJavaString() {
    long at = node();
    if (owner.tag(at) == ValueTape.DECIMAL) return owner.numberText(at);
    int length = Math.toIntExact(owner.tape.length(at));
    if (length == 0) return "";
    boolean ascii = owner.ascii(at);
    // Long non-ASCII strings: the tape's bulk transcoder (simdutf straight into the String's
    // UTF-16 on SVM). ASCII strings become Latin-1 Strings with one copy from scratch.
    if (!ascii && length >= BULK_THRESHOLD) return owner.tape.stringValue(at);
    byte[] buffer = length <= MAX_SCRATCH ? scratch(length) : new byte[length];
    if (!copyInline(at, length, buffer, 0)) owner.tape.copyStringUtf8(at, buffer, 0);
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
   * The text as a {@link TruffleString} in {@code encoding}. In a session parse whose sink asked
   * for {@linkplain TapeSink#sharedText() shared text}, a string is built over the document's text
   * arena without a copy (Latin-1 or UTF-16 as written natively, switched to {@code encoding};
   * strings of 64 KiB or more are copied). Otherwise: one copy of the decoded UTF-8 bytes,
   * transcoded only when {@code encoding} is not UTF-8 and the text is not ASCII.
   */
  @TruffleBoundary
  public TruffleString toTruffleString(TruffleString.Encoding encoding) {
    long at = node();
    if (owner.tag(at) == ValueTape.DECIMAL)
      return TruffleString.fromJavaStringUncached(owner.numberText(at), encoding);
    TapeTextArena text = arena;
    if (text != null) return text.string(at, encoding);
    byte[] bytes = new byte[Math.toIntExact(owner.tape.length(at))];
    if (!copyInline(at, bytes.length, bytes, 0)) owner.tape.copyStringUtf8(at, bytes, 0);
    TruffleString utf8 =
        TruffleString.fromByteArrayUncached(bytes, 0, bytes.length, TruffleString.Encoding.UTF_8, false);
    return encoding == TruffleString.Encoding.UTF_8 ? utf8 : utf8.switchEncodingUncached(encoding);
  }

  /** Copies the decoded UTF-8 bytes of a key or string into {@code output}; returns the length. */
  @TruffleBoundary
  public int copyUtf8(byte[] output, int offset) {
    long at = node();
    int length = Math.toIntExact(owner.tape.length(at));
    if (owner.tag(at) == ValueTape.STRING && copyInline(at, length, output, offset)) return length;
    return owner.tape.copyStringUtf8(at, output, offset);
  }

  /** Whether a key or string decodes to exactly {@code utf8}, without materializing it. */
  @TruffleBoundary
  public boolean utf8Equals(byte[] utf8) {
    return owner.tape.stringEquals(node(), utf8);
  }

  /** A view of the current node that stays valid while its owner is open. */
  public TapeValue view() {
    return new TapeValue(owner, node());
  }
}
