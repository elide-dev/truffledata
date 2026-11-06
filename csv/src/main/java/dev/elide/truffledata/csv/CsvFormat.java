package dev.elide.truffledata.csv;

/**
 * The strict CSV dialect: RFC 4180 records with a configurable single-byte delimiter and quote.
 *
 * <p>{@code quoting} off makes the quote byte ordinary. {@code header} (default on) takes field
 * names from the first record. {@code trim} strips ASCII space and tab around unquoted content and
 * outside the quotes of quoted fields. {@code flexible} accepts rows shorter than the header (or
 * schema); their absent trailing fields read as missing. Longer rows are always rejected.
 */
public record CsvFormat(
    byte delimiter, byte quote, boolean quoting, boolean header, boolean trim, boolean flexible) {
  public static final CsvFormat RFC4180 =
      new CsvFormat((byte) ',', (byte) '"', true, true, false, false);

  public CsvFormat {
    checkByte(delimiter, "delimiter");
    checkByte(quote, "quote");
    if (delimiter == quote)
      throw new IllegalArgumentException("CSV delimiter and quote must differ");
    if (trim && blank(delimiter))
      throw new IllegalArgumentException("CSV trim forbids a space or tab delimiter");
    if (trim && quoting && blank(quote))
      throw new IllegalArgumentException("CSV trim forbids a space or tab quote");
  }

  private static void checkByte(byte value, String name) {
    if (value < 0x01 || value == '\r' || value == '\n')
      throw new IllegalArgumentException(
          "CSV " + name + " must be one ASCII byte in 0x01..0x7F other than CR or LF");
  }

  static boolean blank(byte value) {
    return value == ' ' || value == '\t';
  }

  private static byte ascii(char c, String name) {
    if (c > 0x7F)
      throw new IllegalArgumentException(
          "CSV " + name + " must be one ASCII byte in 0x01..0x7F other than CR or LF");
    return (byte) c;
  }

  public CsvFormat withDelimiter(char c) {
    return new CsvFormat(ascii(c, "delimiter"), quote, quoting, header, trim, flexible);
  }

  public CsvFormat withQuote(char c) {
    return new CsvFormat(delimiter, ascii(c, "quote"), quoting, header, trim, flexible);
  }

  public CsvFormat withoutQuoting() {
    return new CsvFormat(delimiter, quote, false, header, trim, flexible);
  }

  public CsvFormat withHeader(boolean h) {
    return new CsvFormat(delimiter, quote, quoting, h, trim, flexible);
  }

  public CsvFormat withTrim(boolean t) {
    return new CsvFormat(delimiter, quote, quoting, header, t, flexible);
  }

  public CsvFormat withFlexible(boolean f) {
    return new CsvFormat(delimiter, quote, quoting, header, trim, f);
  }
}
