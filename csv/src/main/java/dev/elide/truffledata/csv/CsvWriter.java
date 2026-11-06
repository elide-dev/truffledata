package dev.elide.truffledata.csv;

import dev.elide.truffledata.DataNull;
import dev.elide.truffledata.Missing;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Writes CSV with the Apache Commons CSV 1.14.1 {@code CSVPrinter} rules for every format: quote
 * modes ({@code MINIMAL} when unset), escapes, the null string, the record separator, the trailing
 * delimiter, {@code trim}, header records and comments. Output equals {@code CSVPrinter}'s for the
 * same values, except that a format naming columns from the first record prints no header until
 * {@link #printBatch} supplies names (Commons prints an empty record), and that {@code QuoteMode.ALL}
 * with a null string requires a quote character (Commons prints {@code "null"} around it).
 *
 * <p>Values are {@link CharSequence}s, {@link Number}s (unquoted under {@code NON_NUMERIC}), any
 * other object (its {@code toString()}), or null (the first null string, or nothing). Not
 * thread-safe.
 */
public final class CsvWriter implements Flushable, Closeable {
  private final Appendable out;
  private final CsvFormat format;
  private final String delimiter;
  private final char[] delimiterChars;
  private final boolean quoting;
  private final char quote;
  private final boolean escaping;
  private final char escape;
  private final @Nullable String nullString;
  private final CsvFormat.QuoteMode quoteMode;
  private boolean newRecord = true;
  private boolean headerPending;
  private long recordCount;

  /** Prints the header comments, then the explicit header names unless {@code skipHeaderRecord}. */
  public CsvWriter(Appendable out, CsvFormat format) throws IOException {
    this.out = Objects.requireNonNull(out);
    this.format = Objects.requireNonNull(format);
    delimiter = format.delimiterString();
    delimiterChars = delimiter.toCharArray();
    quoting = format.quoting();
    quote = (char) format.quote();
    escaping = format.escape() >= 0;
    escape = escaping ? (char) format.escape() : 0;
    List<String> nulls = format.nullStrings();
    nullString = nulls == null ? null : nulls.get(0);
    CsvFormat.QuoteMode mode = format.quoteMode();
    quoteMode = mode == null ? CsvFormat.QuoteMode.MINIMAL : mode;
    if (quoteMode == CsvFormat.QuoteMode.ALL && !quoting && nullString != null)
      throw new IllegalArgumentException("CSV quote mode ALL needs a quote to write null strings");
    List<String> comments = format.headerComments();
    if (comments != null) for (String comment : comments) printComment(comment);
    List<String> names = format.headerNames();
    if (names != null && !format.skipHeaderRecord()) printRecord(names);
    headerPending = format.header() && !format.skipHeaderRecord();
  }

  /** A UTF-8 writer over {@code out}; {@link #close()} closes it. */
  public static CsvWriter open(OutputStream out, CsvFormat format) throws IOException {
    return new CsvWriter(
        new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)), format);
  }

  public CsvFormat format() {
    return format;
  }

  /** Records ended by {@link #printRecord} or {@link #printBatch} (comments and println excluded). */
  public long recordCount() {
    return recordCount;
  }

  /** One value of the current record. */
  public void print(@Nullable Object value) throws IOException {
    CharSequence text;
    if (value == null) {
      String ns = nullString;
      if (ns == null) text = "";
      else if (quoteMode == CsvFormat.QuoteMode.ALL) text = quote + ns + quote;
      else text = ns;
    } else if (value instanceof CharSequence cs) text = cs;
    else text = value.toString();
    if (format.trim()) text = trim(text);
    if (!newRecord) out.append(delimiter);
    if (value == null) out.append(text);
    else if (quoting) printWithQuotes(value, text);
    else if (escaping) printWithEscapes(text);
    else out.append(text);
    newRecord = false;
  }

  /** Ends the record: the trailing delimiter (when set) and the record separator. */
  public void println() throws IOException {
    if (format.trailingDelimiter()) out.append(delimiter);
    String separator = format.recordSeparator();
    if (separator != null) out.append(separator);
    newRecord = true;
  }

  public void printRecord(@Nullable Object... values) throws IOException {
    for (Object value : values) print(value);
    endRecord();
  }

  public void printRecord(Iterable<?> values) throws IOException {
    for (Object value : values) print(value);
    endRecord();
  }

  private void endRecord() throws IOException {
    println();
    recordCount++;
    headerPending = false;
  }

  /**
   * A comment, one marker line per comment line; nothing when the format has no comment marker. Ends
   * an open record first.
   */
  public void printComment(@Nullable String comment) throws IOException {
    int marker = format.commentMarker();
    if (comment == null || marker < 0) return;
    if (!newRecord) println();
    out.append((char) marker).append(' ');
    for (int i = 0; i < comment.length(); i++) {
      char c = comment.charAt(i);
      if (c == '\r' || c == '\n') {
        if (c == '\r' && i + 1 < comment.length() && comment.charAt(i + 1) == '\n') i++;
        println();
        out.append((char) marker).append(' ');
      } else out.append(c);
    }
    println();
  }

  /**
   * One record per row, fields in schema order: STRING values as text, INT64, FLOAT64 and BOOLEAN as
   * numbers and booleans, nulls as the null string. Trailing missing fields (flexible short rows) are
   * omitted; other missing fields print as null. When the format names columns from the first record
   * and nothing was printed yet, the schema names come first as the header record.
   */
  public void printBatch(CsvBatch batch) throws IOException {
    if (headerPending) {
      for (int i = 0; i < batch.schema().size(); i++) print(batch.schema().field(i).name());
      endRecord();
    }
    Column[] columns = batch.columns;
    for (int r = 0; r < batch.size(); r++) {
      int width = columns.length;
      while (width > 0 && columns[width - 1].state(r) == Column.MISSING) width--;
      for (int c = 0; c < width; c++) {
        Object value = CsvRow.value(batch, columns[c], r);
        print(value instanceof DataNull || value instanceof Missing ? null : value);
      }
      endRecord();
    }
  }

  @Override
  public void flush() throws IOException {
    if (out instanceof Flushable f) f.flush();
  }

  /** Flushes when {@code autoFlush}, then closes the target when it is closeable. */
  @Override
  public void close() throws IOException {
    if (format.autoFlush()) flush();
    if (out instanceof Closeable c) c.close();
  }

  // ---- Commons CSVFormat.print internals

  private static boolean trimChar(char c) {
    return c <= ' ';
  }

  private static CharSequence trim(CharSequence s) {
    if (s instanceof String string) return string.trim();
    int len = s.length();
    int start = 0;
    while (start < len && trimChar(s.charAt(start))) start++;
    while (start < len && trimChar(s.charAt(len - 1))) len--;
    return start > 0 || len < s.length() ? s.subSequence(start, len) : s;
  }

  private boolean delimiterAt(char c, CharSequence s, int at) {
    char[] d = delimiterChars;
    if (c != d[0] || at + d.length > s.length()) return false;
    for (int i = 1; i < d.length; i++) if (s.charAt(at + i) != d[i]) return false;
    return true;
  }

  private void printWithEscapes(CharSequence s) throws IOException {
    int start = 0;
    int pos = 0;
    int end = s.length();
    while (pos < end) {
      char c = s.charAt(pos);
      boolean delimiterStart = delimiterAt(c, s, pos);
      boolean cr = c == '\r';
      boolean lf = c == '\n';
      if (cr || lf || c == escape || delimiterStart) {
        if (pos > start) out.append(s, start, pos);
        if (lf) c = 'n';
        else if (cr) c = 'r';
        out.append(escape).append(c);
        if (delimiterStart) {
          for (int i = 1; i < delimiterChars.length; i++) {
            pos++;
            out.append(escape).append(s.charAt(pos));
          }
        }
        start = pos + 1;
      }
      pos++;
    }
    if (pos > start) out.append(s, start, pos);
  }

  private void printWithQuotes(Object value, CharSequence s) throws IOException {
    boolean quoted = false;
    int start = 0;
    int pos = 0;
    int len = s.length();
    char escapeChar = escaping ? escape : quote;
    switch (quoteMode) {
      case ALL, ALL_NON_NULL -> quoted = true;
      case NON_NUMERIC -> quoted = !(value instanceof Number);
      case NONE -> {
        printWithEscapes(s);
        return;
      }
      case MINIMAL -> {
        if (len <= 0) {
          if (newRecord) quoted = true;
        } else {
          char c = s.charAt(pos);
          if (c <= '#') quoted = true;
          else {
            while (pos < len) {
              c = s.charAt(pos);
              if (c == '\n' || c == '\r' || c == quote || c == escapeChar || delimiterAt(c, s, pos)) {
                quoted = true;
                break;
              }
              pos++;
            }
            if (!quoted) {
              pos = len - 1;
              if (trimChar(s.charAt(pos))) quoted = true;
            }
          }
        }
        if (!quoted) {
          out.append(s, start, len);
          return;
        }
      }
    }
    if (!quoted) {
      out.append(s, start, len);
      return;
    }
    out.append(quote);
    while (pos < len) {
      char c = s.charAt(pos);
      if (c == quote || c == escapeChar) {
        out.append(s, start, pos);
        out.append(escapeChar);
        start = pos;
      }
      pos++;
    }
    out.append(s, start, pos);
    out.append(quote);
  }
}
