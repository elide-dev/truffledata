package dev.elide.truffledata.csv;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * The parse-time view of a {@link CsvFormat}: meta bytes and flags as plain fields, plus the decode
 * of complex spans. Immutable and shared by a plan's scanners, builders and batches.
 *
 * <p>Strict formats mark quoted fields holding doubled quotes as escaped; their content span excludes
 * the quotes and decoding collapses each doubled quote. Commons formats mark a field complex when its
 * value is not one contiguous input slice (escapes, doubled quotes, trailing data); its span covers
 * the raw token, and decoding re-lexes that token with {@link CommonsScanner}.
 */
final class Dialect {
  /** A meta character that is not set (never equal to a byte value). */
  static final int UNSET = -2;

  final CsvFormat format;
  final boolean strict;
  final byte[] delimiter;
  final byte quote;
  final boolean quoting;
  final int quoteChar; // quote & 0xFF or UNSET
  final int escape; // or UNSET
  final int comment; // or UNSET
  final boolean ignoreEmptyLines;
  final boolean ignoreSurroundingSpaces;
  final boolean trim;
  final boolean trailingData;
  final boolean trailingDelimiter;
  final boolean lenientEof;
  final Nulls nulls;

  private Dialect(CsvFormat f) {
    format = f;
    strict = f.strict();
    delimiter = f.delimiterBytes();
    quote = f.quote();
    quoting = f.quoting();
    quoteChar = f.quoting() ? f.quote() & 0xFF : UNSET;
    escape = f.escape() < 0 ? UNSET : f.escape();
    comment = f.commentMarker() < 0 ? UNSET : f.commentMarker();
    ignoreEmptyLines = f.ignoreEmptyLines();
    ignoreSurroundingSpaces = f.ignoreSurroundingSpaces();
    trim = f.trim();
    trailingData = f.trailingData();
    trailingDelimiter = f.trailingDelimiter();
    lenientEof = f.lenientEof();
    nulls = Nulls.of(f);
  }

  static Dialect of(CsvFormat format) {
    return new Dialect(format);
  }

  /** A decoder for complex Commons spans (one per thread of use), or null for strict formats. */
  @Nullable CommonsScanner decoder() {
    return strict ? null : new CommonsScanner(this, Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  /**
   * Writes a span's value into {@code target} at {@code at} and returns its length, at most {@link
   * CsvScanner#length}. {@code decoder} is {@link #decoder()} (null for strict formats).
   */
  int decode(byte[] input, long span, byte[] target, int at, @Nullable CommonsScanner decoder) {
    if (decoder == null || !CsvScanner.escaped(span))
      return CsvScanner.unescape(input, span, quote, target, at);
    return decoder.decode(input, span, target, at);
  }

  /** Materializes one span (allocates). */
  String string(byte[] input, long span) {
    if (!CsvScanner.escaped(span))
      return new String(
          input, CsvScanner.offset(span), CsvScanner.length(span), StandardCharsets.UTF_8);
    byte[] text = new byte[CsvScanner.length(span)];
    int length = decode(input, span, text, 0, decoder());
    return new String(text, 0, length, StandardCharsets.UTF_8);
  }

  // ---- Java Character.isWhitespace over UTF-8

  private static final boolean[] ASCII_WHITESPACE = new boolean[128];

  static {
    for (int c = 0; c < 128; c++) ASCII_WHITESPACE[c] = Character.isWhitespace((char) c);
  }

  static boolean asciiWhitespace(int b) {
    return b >= 0 && b < 128 && ASCII_WHITESPACE[b];
  }

  /**
   * Whether the three bytes form a non-ASCII {@code Character.isWhitespace} character: U+1680,
   * U+2000..U+2006, U+2008..U+200A, U+2028, U+2029, U+205F or U+3000.
   */
  static boolean wideWhitespace(int b0, int b1, int b2) {
    return switch (b0) {
      case 0xE1 -> b1 == 0x9A && b2 == 0x80;
      case 0xE2 ->
          (b1 == 0x80
                  && ((b2 >= 0x80 && b2 <= 0x86)
                      || (b2 >= 0x88 && b2 <= 0x8A)
                      || b2 == 0xA8
                      || b2 == 0xA9))
              || (b1 == 0x81 && b2 == 0x9F);
      case 0xE3 -> b1 == 0x80 && b2 == 0x80;
      default -> false;
    };
  }

  /** Whether a byte can lead a {@link #wideWhitespace} sequence. */
  static boolean wideLead(int b) {
    return b == 0xE1 || b == 0xE2 || b == 0xE3;
  }

  /** The end of [from, to) after removing trailing {@code Character.isWhitespace} characters. */
  static int trimTrailingWhitespace(byte[] in, int from, int to) {
    int end = to;
    while (end > from) {
      int b = in[end - 1] & 0xFF;
      if (asciiWhitespace(b)) end--;
      else if (end - from >= 3
          && wideWhitespace(in[end - 3] & 0xFF, in[end - 2] & 0xFF, b)) end -= 3;
      else break;
    }
    return end;
  }

  /** Commons {@code String.trim}: chars at most U+0020, which are single UTF-8 bytes. */
  static boolean trimByte(byte b) {
    return b >= 0 && b <= 0x20;
  }
}
