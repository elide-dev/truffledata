package dev.elide.truffledata.csv;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The null rule, in one place. It decides on a field's final value (after unescaping and trimming)
 * and whether the field was quoted, mirroring Commons {@code CSVParser.handleNull}:
 *
 * <ul>
 *   <li>a value equal to one of the null strings is null, unless the quote mode is {@code
 *       ALL_NON_NULL} or {@code NON_NUMERIC} (a "strict" quote mode) and the field was quoted;
 *   <li>without null strings, an unquoted empty value is null only under a strict quote mode.
 * </ul>
 *
 * <p>A format <em>configures</em> nulls when it sets null strings or a strict quote mode; generic
 * columns are then nullable, and a null token in a non-nullable typed field is a contract violation.
 * Unconfigured strict formats keep the original rule ({@link #legacy}): an unquoted empty field is
 * null in a nullable typed column and {@code ""} in a non-nullable STRING column, and generic columns
 * have no nulls.
 */
final class Nulls {
  private final byte[][] tokens;
  private final String[] strings;
  private final boolean strictQuoteMode;
  private final boolean configured;
  private final boolean legacy;

  private Nulls(CsvFormat format) {
    List<String> list = format.nullStrings();
    strings = list == null ? new String[0] : list.toArray(new String[0]);
    tokens = new byte[strings.length][];
    for (int i = 0; i < strings.length; i++) tokens[i] = strings[i].getBytes(StandardCharsets.UTF_8);
    CsvFormat.QuoteMode mode = format.quoteMode();
    strictQuoteMode =
        mode == CsvFormat.QuoteMode.ALL_NON_NULL || mode == CsvFormat.QuoteMode.NON_NUMERIC;
    configured = strings.length > 0 || strictQuoteMode;
    legacy = format.strict() && !configured;
  }

  static Nulls of(CsvFormat format) {
    return new Nulls(format);
  }

  /** Whether the format configures nulls (generic columns are then nullable). */
  boolean configured() {
    return configured;
  }

  /** Whether the original strict rule applies (unquoted empty; {@code ""} in non-nullable STRING). */
  boolean legacy() {
    return legacy;
  }

  /** Whether any value can be null. */
  boolean active() {
    return legacy || configured;
  }

  /** The rule on the value bytes [off, off + len). */
  boolean isNull(byte[] value, int off, int len, boolean quoted) {
    if (legacy) return !quoted && len == 0;
    byte[][] t = tokens;
    if (t.length > 0) {
      for (byte[] token : t)
        if (token.length == len && java.util.Arrays.equals(token, 0, len, value, off, off + len))
          return !(strictQuoteMode && quoted);
      return false;
    }
    return strictQuoteMode && len == 0 && !quoted;
  }

  /** The rule on a decoded value (header names). */
  boolean isNull(String value, boolean quoted) {
    if (legacy) return !quoted && value.isEmpty();
    if (strings.length > 0) {
      for (String token : strings) if (token.equals(value)) return !(strictQuoteMode && quoted);
      return false;
    }
    return strictQuoteMode && value.isEmpty() && !quoted;
  }
}
