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
  // Header word of the bound node when the walk already loaded it, else 0 (no text node has a
  // zero header: text kinds are nonzero), loaded on first use. The tape is immutable while bound.
  private long header;
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
    header = 0;
  }

  /** Attaches the session's text arena for one borrowed parse, or detaches it (null). */
  void arena(@Nullable TapeTextArena text) {
    arena = text;
  }

  void bind(long next) {
    node = next;
    header = 0;
  }

  /** Binds {@code next} with the header word the caller already loaded from this owner's tape. */
  void bind(long next, long word) {
    node = next;
    header = word;
  }

  void unbind() {
    node = -1;
    header = 0;
  }

  private long node() {
    long bound = node;
    if (bound < 0) throw new IllegalStateException("Tape text used outside its sink event");
    owner.check();
    return bound;
  }

  /** Header word of the bound node {@code at} (as returned by {@link #node()}). */
  private long header(long at) {
    long word = header;
    return word != 0 ? word : (header = owner.tape.word(at));
  }

  /** Copies a key or string's decoded bytes given its loaded header; one storage call. */
  private void copyText(long at, long word, byte[] output, int offset) {
    int kind = TapeReader.kind(word);
    if (kind == 7 || kind == 6)
      owner.tape.copyText((word & TapeReader.OWNED) != 0, TapeReader.low(word), output, offset, TapeReader.field(word));
    else owner.tape.copyStringUtf8(at, output, offset);
  }

  /** Decoded UTF-8 length of a key or string; the token length of a decimal. */
  public int utf8Length() {
    long at = node();
    long word = header(at);
    int kind = TapeReader.kind(word);
    return kind == 7 || kind == 6 ? TapeReader.field(word) : Math.toIntExact(owner.tape.length(at));
  }

  /** True when every decoded byte is ASCII (from the decoder's side table, no scan). */
  public boolean isAscii() {
    long at = node();
    return (header(at) & TapeReader.ASCII) != 0;
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
    TapeReader tape = owner.tape;
    long header = header(at);
    int length = TapeReader.field(header);
    if (TapeReader.kind(header) != 7 || length > TapeKey.INLINE_BYTES) return false;
    boolean owned = (header & TapeReader.OWNED) != 0;
    long start = TapeReader.low(header);
    words[offset] = ValueTape.STRING | ((long) length << 32);
    words[offset + 1] = tape.textWord(owned, start, Math.min(8, length));
    words[offset + 2] = tape.textWord(owned, start + 8, Math.max(0, length - 8));
    return true;
  }

  /** Private cache hash of decoded inline words; full words still decide key equality. */
  static int inlineHash(long[] words) {
    long hash = words[1] ^ Long.rotateLeft(words[2], 29) ^ ((words[0] >>> 32) * 0x9e3779b97f4a7c15L);
    hash = (hash ^ (hash >>> 33)) * 0xc4ceb9fe1a85ec53L;
    return (int) (hash ^ (hash >>> 32));
  }

  /** Decoded text of a key or string, or the exact source token of a decimal. */
  @TruffleBoundary
  public String toJavaString() {
    long at = node();
    // One header load decides kind, length, ASCII and extent; one storage call copies the text.
    long word = header(at);
    if (TapeReader.kind(word) != 7) {
      if (TapeReader.valueKind(word) == ValueTape.DECIMAL) return owner.numberText(at);
      word = 0;
    }
    int length = word != 0 ? TapeReader.field(word) : Math.toIntExact(owner.tape.length(at));
    if (length == 0) return "";
    boolean ascii = word != 0 ? (word & TapeReader.ASCII) != 0 : owner.tape.isAscii(at);
    // Long non-ASCII strings: the tape's bulk transcoder (simdutf straight into the String's
    // UTF-16 on SVM). ASCII strings become Latin-1 Strings with one copy from scratch.
    if (!ascii && length >= BULK_THRESHOLD) return owner.tape.stringValue(at);
    byte[] buffer = length <= MAX_SCRATCH ? scratch(length) : new byte[length];
    if (word != 0) owner.tape.copyText((word & TapeReader.OWNED) != 0, TapeReader.low(word), buffer, 0, length);
    else owner.tape.copyStringUtf8(at, buffer, 0);
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
    byte[] bytes = new byte[utf8Length()];
    copyText(at, header(at), bytes, 0);
    TruffleString utf8 =
        TruffleString.fromByteArrayUncached(bytes, 0, bytes.length, TruffleString.Encoding.UTF_8, false);
    return encoding == TruffleString.Encoding.UTF_8 ? utf8 : utf8.switchEncodingUncached(encoding);
  }

  /** Copies the decoded UTF-8 bytes of a key or string into {@code output}; returns the length. */
  @TruffleBoundary
  public int copyUtf8(byte[] output, int offset) {
    long at = node();
    long word = header(at);
    int kind = TapeReader.kind(word);
    if (kind != 7 && kind != 6) return owner.tape.copyStringUtf8(at, output, offset);
    copyText(at, word, output, offset);
    return TapeReader.field(word);
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
