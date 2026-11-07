package dev.elide.truffledata.csv.kernel;

/**
 * The structural index contract shared by every kernel (Java and native, see
 * {@code csv/native/index.h}).
 *
 * <p>Over the byte stream formed by concatenating every call's input, with delimiter {@code d},
 * quote {@code q} (when quoting) and the record separators LF and CR:
 *
 * <ul>
 *   <li>{@code inside[i]} is the XOR of {@code quote[0..i]} (inclusive): 1 from an opening quote
 *       up to the byte before its closing quote;
 *   <li>a <em>separator</em> is a delimiter, LF or CR byte with {@code inside[i] == 0};
 *   <li>{@code reopen[i]} is a quote byte right after a closing quote (the second byte of a
 *       doubled quote);
 *   <li>{@code unquoted[i]} is a byte outside quotes that is neither a quote nor a separator.
 * </ul>
 *
 * <p>A field is the byte range between consecutive separators. Each separator emits one {@code
 * int} entry: its offset relative to the call's first byte (bits 0..28), plus flags for the field
 * that ends at it: {@link #QUOTED} (the field holds a quote byte), {@link #ESCAPED} (it holds a
 * reopen) and {@link #UNQUOTED} (QUOTED and it holds an unquoted byte).
 *
 * <p>A field with QUOTED and without UNQUOTED is always a well-formed quoted field: it starts and
 * ends with a quote, its content is the bytes in between, and its only interior quotes are doubled
 * pairs, present exactly when ESCAPED is set. QUOTED with UNQUOTED is malformed for a strict
 * parser, or holds blanks around the quotes when trimming.
 *
 * <p>The carry connects calls: {@link #CARRY_INSIDE}, {@link #CARRY_CLOSING} (the last byte was a
 * closing quote) and the pending flags of the trailing field. A call indexes {@link #indexable}
 * bytes, so output never exceeds capacity and every kernel consumes the same prefix.
 */
public final class CsvIndex {
  public static final int QUOTED = 1 << 31;
  public static final int ESCAPED = 1 << 30;
  public static final int UNQUOTED = 1 << 29;
  public static final int OFFSET_MASK = (1 << 29) - 1;
  public static final int FLAGS = QUOTED | ESCAPED | UNQUOTED;
  /** The most bytes one call may index (offsets have 29 bits). */
  public static final int MAX_CALL_BYTES = OFFSET_MASK;

  public static final long CARRY_INSIDE = 1;
  public static final long CARRY_CLOSING = 2;
  public static final long CARRY_QUOTED = 4;
  public static final long CARRY_ESCAPED = 8;
  public static final long CARRY_UNQUOTED = 16;
  public static final long CARRY_MASK = 31;

  private CsvIndex() {}

  /** Bytes one call indexes: all of them when capacity allows, else whole 64-byte blocks. */
  public static int indexable(int length, int capacity) {
    return capacity >= length ? length : capacity & ~63;
  }

  public static int offset(int entry) {
    return entry & OFFSET_MASK;
  }

  public static boolean quoted(int entry) {
    return entry < 0;
  }

  public static boolean escaped(int entry) {
    return (entry & ESCAPED) != 0;
  }

  public static boolean unquoted(int entry) {
    return (entry & UNQUOTED) != 0;
  }

  /** Flags the trailing (unterminated) field would carry, from a call's carry-out. */
  public static int pendingFlags(long carry) {
    boolean q = (carry & CARRY_QUOTED) != 0;
    return (q ? QUOTED : 0)
        | ((carry & CARRY_ESCAPED) != 0 ? ESCAPED : 0)
        | (q && (carry & CARRY_UNQUOTED) != 0 ? UNQUOTED : 0);
  }

  /** Report name of a csv/native kernel id. */
  public static String kernelName(int kernel) {
    return switch (kernel) {
      case 1 -> "scalar";
      case 2 -> "swar";
      case 3 -> "neon";
      case 4 -> "avx2";
      case 5 -> "avx512";
      default -> "kernel" + kernel;
    };
  }

  /** Validates a format: the quote must differ from the delimiter, LF and CR. */
  static void check(byte delimiter, byte quote, boolean quoting) {
    if (delimiter == '\n' || delimiter == '\r')
      throw new IllegalArgumentException("CSV index: delimiter is a record separator");
    if (quoting && (quote == delimiter || quote == '\n' || quote == '\r'))
      throw new IllegalArgumentException("CSV index: quote is a separator");
  }

  public static void checkCall(byte[] input, int from, int length, int[] out) {
    if (from < 0 || length < 0 || length > input.length - from)
      throw new IndexOutOfBoundsException("CSV index: input range");
    if (length > MAX_CALL_BYTES) throw new IllegalArgumentException("CSV index: call too long");
    if (out.length < length) throw new IllegalArgumentException("CSV index: output too small");
  }
}
