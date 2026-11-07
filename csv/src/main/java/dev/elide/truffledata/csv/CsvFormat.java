package dev.elide.truffledata.csv;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * An immutable CSV dialect for parsing and writing.
 *
 * <p>Two families share this type. <b>Strict</b> formats ({@link #RFC4180} and anything built from
 * {@link #builder()}) read RFC 4180 records with the strict contract: a bare quote, bytes after a
 * closing quote, an unterminated quote and ragged rows are errors. <b>Commons</b> formats ({@link
 * Predefined}, or {@code setStrict(false)}) parse and write with Apache Commons CSV 1.14.1 semantics
 * for every option, with the differences listed in {@code docs/CSV.md}.
 *
 * <p>Strict formats accept the header options, null strings, quote mode, {@code maxRows} and the
 * writer options, and reject the options that need the Commons lexer: an escape, a comment marker,
 * a multi-byte delimiter, {@code ignoreSurroundingSpaces}, {@code trailingDelimiter}, {@code
 * trailingData}, {@code lenientEof} and {@code ignoreEmptyLines(false)}.
 *
 * <p>{@code trim} differs by family: strict formats strip ASCII space and tab around unquoted content
 * and outside the quotes of quoted fields; Commons formats apply {@code String.trim} to every value,
 * quoted or not.
 */
public final class CsvFormat {
  /** Commons {@code QuoteMode}: how the writer quotes, and (strict modes) which tokens are null. */
  public enum QuoteMode {
    ALL,
    ALL_NON_NULL,
    MINIMAL,
    NON_NUMERIC,
    NONE
  }

  /** Commons {@code DuplicateHeaderMode}. */
  public enum DuplicateHeaderMode {
    ALLOW_ALL,
    ALLOW_EMPTY,
    DISALLOW
  }

  /** The Commons CSV 1.14.1 predefined formats, with Commons behaviour. */
  public enum Predefined {
    DEFAULT,
    EXCEL,
    INFORMIX_UNLOAD,
    INFORMIX_UNLOAD_CSV,
    MONGODB_CSV,
    MONGODB_TSV,
    MYSQL,
    ORACLE,
    POSTGRESQL_CSV,
    POSTGRESQL_TEXT,
    RFC4180,
    TDF;

    public CsvFormat format() {
      return Formats.ALL[ordinal()];
    }

    /** Built after the enum is initialized (a switch over the enum needs its values). */
    private static final class Formats {
      static final CsvFormat[] ALL = new CsvFormat[values().length];

      static {
        for (Predefined p : values()) ALL[p.ordinal()] = create(p).build();
      }
    }

    private static Builder create(Predefined p) {
      // Commons CSVFormat.Builder.create(): comma, double quote, CRLF, ignore empty lines, ALLOW_ALL,
      // no header, missing column names rejected. Commons records may be short: flexible.
      Builder b =
          new Builder()
              .setStrict(false)
              .setHeader(false)
              .setAllowMissingColumnNames(false)
              .setFlexible(true);
      return switch (p) {
        case DEFAULT -> b;
        case EXCEL ->
            b.setIgnoreEmptyLines(false)
                .setAllowMissingColumnNames(true)
                .setTrailingData(true)
                .setLenientEof(true);
        case INFORMIX_UNLOAD ->
            b.setDelimiter('|').setEscape('\\').setQuote('"').setRecordSeparator("\n");
        case INFORMIX_UNLOAD_CSV -> b.setDelimiter(',').setQuote('"').setRecordSeparator("\n");
        case MONGODB_CSV ->
            b.setDelimiter(',').setEscape('"').setQuote('"').setQuoteMode(QuoteMode.MINIMAL);
        case MONGODB_TSV ->
            b.setDelimiter('\t')
                .setEscape('"')
                .setQuote('"')
                .setQuoteMode(QuoteMode.MINIMAL)
                .setSkipHeaderRecord(false);
        case MYSQL ->
            b.setDelimiter('\t')
                .setEscape('\\')
                .setIgnoreEmptyLines(false)
                .setQuote((Character) null)
                .setRecordSeparator("\n")
                .setNullString("\\N")
                .setQuoteMode(QuoteMode.ALL_NON_NULL);
        case ORACLE ->
            b.setDelimiter(',')
                .setEscape('\\')
                .setIgnoreEmptyLines(false)
                .setQuote('"')
                .setNullString("\\N")
                .setTrim(true)
                .setRecordSeparator(System.lineSeparator())
                .setQuoteMode(QuoteMode.MINIMAL);
        case POSTGRESQL_CSV ->
            b.setDelimiter(',')
                .setEscape((Character) null)
                .setIgnoreEmptyLines(false)
                .setQuote('"')
                .setRecordSeparator("\n")
                .setNullString("")
                .setQuoteMode(QuoteMode.ALL_NON_NULL);
        case POSTGRESQL_TEXT ->
            b.setDelimiter('\t')
                .setEscape('\\')
                .setIgnoreEmptyLines(false)
                .setQuote((Character) null)
                .setRecordSeparator("\n")
                .setNullString("\\N")
                .setQuoteMode(QuoteMode.ALL_NON_NULL);
        case RFC4180 -> b.setIgnoreEmptyLines(false);
        case TDF -> b.setDelimiter('\t').setIgnoreSurroundingSpaces(true);
      };
    }
  }

  /** Strict RFC 4180: comma, double quote, header from the first record, CRLF when writing. */
  public static final CsvFormat RFC4180 = new Builder().build();

  private static final int MAX_DELIMITER = 16;

  private final boolean strict;
  private final byte[] delimiter;
  private final byte quote;
  private final boolean quoting;
  private final int escape;
  private final int commentMarker;
  private final boolean header;
  private final @Nullable List<String> headerNames;
  private final boolean skipHeaderRecord;
  private final boolean allowMissingColumnNames;
  private final DuplicateHeaderMode duplicateHeaderMode;
  private final boolean ignoreHeaderCase;
  private final boolean ignoreEmptyLines;
  private final boolean ignoreSurroundingSpaces;
  private final boolean trim;
  private final boolean flexible;
  private final @Nullable List<String> nullStrings;
  private final @Nullable QuoteMode quoteMode;
  private final boolean trailingDelimiter;
  private final boolean trailingData;
  private final boolean lenientEof;
  private final long maxRows;
  private final @Nullable String recordSeparator;
  private final @Nullable List<String> headerComments;
  private final boolean autoFlush;

  private CsvFormat(Builder b) {
    strict = b.strict;
    delimiter = b.delimiter.clone();
    quote = b.quote;
    quoting = b.quoting;
    escape = b.escape;
    commentMarker = b.commentMarker;
    header = b.header;
    headerNames = b.headerNames == null ? null : List.copyOf(b.headerNames);
    skipHeaderRecord = b.skipHeaderRecord;
    allowMissingColumnNames = b.allowMissingColumnNames;
    duplicateHeaderMode = b.duplicateHeaderMode;
    ignoreHeaderCase = b.ignoreHeaderCase;
    ignoreEmptyLines = b.ignoreEmptyLines;
    ignoreSurroundingSpaces = b.ignoreSurroundingSpaces;
    trim = b.trim;
    flexible = b.flexible;
    nullStrings = b.nullStrings == null || b.nullStrings.isEmpty() ? null : List.copyOf(b.nullStrings);
    quoteMode = b.quoteMode;
    trailingDelimiter = b.trailingDelimiter;
    trailingData = b.trailingData;
    lenientEof = b.lenientEof;
    maxRows = b.maxRows;
    recordSeparator = b.recordSeparator;
    headerComments = b.headerComments == null ? null : List.copyOf(b.headerComments);
    autoFlush = b.autoFlush;
    validate();
  }

  private void validate() {
    if (delimiter.length == 0 || delimiter.length > MAX_DELIMITER)
      throw new IllegalArgumentException("CSV delimiter must be 1 to 16 bytes");
    for (byte d : delimiter) checkByte(d, "delimiter");
    if (quoting) checkByte(quote, "quote");
    if (escape >= 0) checkByte((byte) escape, "escape");
    if (commentMarker >= 0) checkByte((byte) commentMarker, "comment marker");
    // Commons CSVFormat.validate().
    if (quoting && contains(delimiter, quote))
      throw new IllegalArgumentException("CSV delimiter and quote must differ");
    if (escape >= 0 && contains(delimiter, (byte) escape))
      throw new IllegalArgumentException("CSV delimiter and escape must differ");
    if (commentMarker >= 0 && contains(delimiter, (byte) commentMarker))
      throw new IllegalArgumentException("CSV delimiter and comment marker must differ");
    if (quoting && commentMarker == quote)
      throw new IllegalArgumentException("CSV quote and comment marker must differ");
    if (escape >= 0 && commentMarker == escape)
      throw new IllegalArgumentException("CSV escape and comment marker must differ");
    if (escape < 0 && quoteMode == QuoteMode.NONE)
      throw new IllegalArgumentException("CSV quote mode NONE requires an escape");
    List<String> names = headerNames;
    if (names != null && duplicateHeaderMode != DuplicateHeaderMode.ALLOW_ALL) {
      Set<String> seen = new HashSet<>();
      for (String name : names) {
        boolean blank = blank(name);
        if (!seen.add(blank ? "" : name)
            && !(blank && duplicateHeaderMode == DuplicateHeaderMode.ALLOW_EMPTY))
          throw new IllegalArgumentException("CSV header: duplicate name");
      }
    }
    if (!strict) return;
    if (delimiter[0] == quote)
      throw new IllegalArgumentException("CSV delimiter and quote must differ");
    if (trim && blank(delimiter[0]))
      throw new IllegalArgumentException("CSV trim forbids a space or tab delimiter");
    if (trim && quoting && blank(quote))
      throw new IllegalArgumentException("CSV trim forbids a space or tab quote");
    String commonsOnly =
        delimiter.length != 1
            ? "a multi-byte delimiter"
            : escape >= 0
                ? "escape"
                : commentMarker >= 0
                    ? "commentMarker"
                    : ignoreSurroundingSpaces
                        ? "ignoreSurroundingSpaces"
                        : trailingDelimiter
                            ? "trailingDelimiter"
                            : trailingData
                                ? "trailingData"
                                : lenientEof
                                    ? "lenientEof"
                                    : !ignoreEmptyLines ? "ignoreEmptyLines(false)" : null;
    if (commonsOnly != null)
      throw new IllegalArgumentException(
          "CSV option " + commonsOnly + " requires a Commons (non-strict) format");
  }

  private static boolean contains(byte[] bytes, byte value) {
    for (byte b : bytes) if (b == value) return true;
    return false;
  }

  /** Commons {@code CSVFormat.isBlank}: empty after {@code String.trim}. */
  static boolean blank(@Nullable String name) {
    return name == null || name.trim().isEmpty();
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

  /** A builder with strict RFC 4180 defaults. */
  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  // ---- accessors

  /** Strict family (the strict scanner) or Commons family (the Commons lexer). */
  public boolean strict() {
    return strict;
  }

  /** The delimiter's first byte (the whole delimiter for single-byte delimiters). */
  public byte delimiter() {
    return delimiter[0];
  }

  public String delimiterString() {
    return new String(delimiter, StandardCharsets.US_ASCII);
  }

  byte[] delimiterBytes() {
    return delimiter;
  }

  public byte quote() {
    return quote;
  }

  /** False when no quote character is set: the quote byte is then ordinary. */
  public boolean quoting() {
    return quoting;
  }

  /** The escape byte, or -1 when unset. */
  public int escape() {
    return escape;
  }

  /** The comment marker byte, or -1 when unset. */
  public int commentMarker() {
    return commentMarker;
  }

  /** Whether column names come from the first record. */
  public boolean header() {
    return header;
  }

  /** Explicit column names, or null. */
  public @Nullable List<String> headerNames() {
    return headerNames;
  }

  /** Whether columns are named (from the first record or explicitly). */
  boolean named() {
    return header || headerNames != null;
  }

  /** Whether the first record is consumed before data: a header record, or a skipped one. */
  boolean consumesFirstRecord() {
    return header || (headerNames != null && skipHeaderRecord);
  }

  public boolean skipHeaderRecord() {
    return skipHeaderRecord;
  }

  public boolean allowMissingColumnNames() {
    return allowMissingColumnNames;
  }

  public DuplicateHeaderMode duplicateHeaderMode() {
    return duplicateHeaderMode;
  }

  public boolean ignoreHeaderCase() {
    return ignoreHeaderCase;
  }

  public boolean ignoreEmptyLines() {
    return ignoreEmptyLines;
  }

  public boolean ignoreSurroundingSpaces() {
    return ignoreSurroundingSpaces;
  }

  public boolean trim() {
    return trim;
  }

  /** Accept rows shorter than the header or schema; absent trailing fields read as missing. */
  public boolean flexible() {
    return flexible;
  }

  /** Null strings, or null when unset. */
  public @Nullable List<String> nullStrings() {
    return nullStrings;
  }

  public @Nullable QuoteMode quoteMode() {
    return quoteMode;
  }

  public boolean trailingDelimiter() {
    return trailingDelimiter;
  }

  public boolean trailingData() {
    return trailingData;
  }

  public boolean lenientEof() {
    return lenientEof;
  }

  /** Data records to read (a header record does not count, as in Commons); 0 or less: all. */
  public long maxRows() {
    return maxRows;
  }

  /** The writer's record separator, or null for none. Parsing accepts CR, LF and CRLF. */
  public @Nullable String recordSeparator() {
    return recordSeparator;
  }

  public @Nullable List<String> headerComments() {
    return headerComments;
  }

  public boolean autoFlush() {
    return autoFlush;
  }

  // ---- withers

  public CsvFormat withDelimiter(char c) {
    return toBuilder().setDelimiter(c).build();
  }

  public CsvFormat withQuote(char c) {
    return toBuilder().setQuote(c).build();
  }

  public CsvFormat withoutQuoting() {
    return toBuilder().setQuote((Character) null).build();
  }

  public CsvFormat withHeader(boolean h) {
    return toBuilder().setHeader(h).build();
  }

  /** Explicit column names; no names means names come from the first record (as in Commons). */
  public CsvFormat withHeader(String... names) {
    return toBuilder().setHeader(names).build();
  }

  public CsvFormat withTrim(boolean t) {
    return toBuilder().setTrim(t).build();
  }

  public CsvFormat withFlexible(boolean f) {
    return toBuilder().setFlexible(f).build();
  }

  public CsvFormat withNullStrings(String... tokens) {
    return toBuilder().setNullStrings(tokens).build();
  }

  public CsvFormat withQuoteMode(@Nullable QuoteMode mode) {
    return toBuilder().setQuoteMode(mode).build();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof CsvFormat f)) return false;
    return strict == f.strict
        && Arrays.equals(delimiter, f.delimiter)
        && quote == f.quote
        && quoting == f.quoting
        && escape == f.escape
        && commentMarker == f.commentMarker
        && header == f.header
        && Objects.equals(headerNames, f.headerNames)
        && skipHeaderRecord == f.skipHeaderRecord
        && allowMissingColumnNames == f.allowMissingColumnNames
        && duplicateHeaderMode == f.duplicateHeaderMode
        && ignoreHeaderCase == f.ignoreHeaderCase
        && ignoreEmptyLines == f.ignoreEmptyLines
        && ignoreSurroundingSpaces == f.ignoreSurroundingSpaces
        && trim == f.trim
        && flexible == f.flexible
        && Objects.equals(nullStrings, f.nullStrings)
        && quoteMode == f.quoteMode
        && trailingDelimiter == f.trailingDelimiter
        && trailingData == f.trailingData
        && lenientEof == f.lenientEof
        && maxRows == f.maxRows
        && Objects.equals(recordSeparator, f.recordSeparator)
        && Objects.equals(headerComments, f.headerComments)
        && autoFlush == f.autoFlush;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        strict,
        Arrays.hashCode(delimiter),
        quote,
        quoting,
        escape,
        commentMarker,
        header,
        headerNames,
        nullStrings,
        quoteMode,
        trim,
        flexible);
  }

  @Override
  public String toString() {
    return "CsvFormat[strict="
        + strict
        + ", delimiter="
        + delimiterString()
        + ", quote="
        + (quoting ? String.valueOf((char) quote) : "none")
        + ", escape="
        + (escape < 0 ? "none" : String.valueOf((char) escape))
        + ", header="
        + (headerNames != null ? headerNames : header)
        + ", nullStrings="
        + nullStrings
        + ", quoteMode="
        + quoteMode
        + "]";
  }

  /** Mutable builder; {@link #build()} validates. Setter names follow Commons {@code CSVFormat.Builder}. */
  public static final class Builder {
    private boolean strict = true;
    private byte[] delimiter = {','};
    private byte quote = '"';
    private boolean quoting = true;
    private int escape = -1;
    private int commentMarker = -1;
    private boolean header = true;
    private @Nullable List<String> headerNames;
    private boolean skipHeaderRecord;
    private boolean allowMissingColumnNames = true;
    private DuplicateHeaderMode duplicateHeaderMode = DuplicateHeaderMode.ALLOW_ALL;
    private boolean ignoreHeaderCase;
    private boolean ignoreEmptyLines = true;
    private boolean ignoreSurroundingSpaces;
    private boolean trim;
    private boolean flexible;
    private @Nullable List<String> nullStrings;
    private @Nullable QuoteMode quoteMode;
    private boolean trailingDelimiter;
    private boolean trailingData;
    private boolean lenientEof;
    private long maxRows;
    private @Nullable String recordSeparator = "\r\n";
    private @Nullable List<String> headerComments;
    private boolean autoFlush;

    private Builder() {}

    private Builder(CsvFormat f) {
      strict = f.strict;
      delimiter = f.delimiter.clone();
      quote = f.quote;
      quoting = f.quoting;
      escape = f.escape;
      commentMarker = f.commentMarker;
      header = f.header;
      headerNames = f.headerNames;
      skipHeaderRecord = f.skipHeaderRecord;
      allowMissingColumnNames = f.allowMissingColumnNames;
      duplicateHeaderMode = f.duplicateHeaderMode;
      ignoreHeaderCase = f.ignoreHeaderCase;
      ignoreEmptyLines = f.ignoreEmptyLines;
      ignoreSurroundingSpaces = f.ignoreSurroundingSpaces;
      trim = f.trim;
      flexible = f.flexible;
      nullStrings = f.nullStrings;
      quoteMode = f.quoteMode;
      trailingDelimiter = f.trailingDelimiter;
      trailingData = f.trailingData;
      lenientEof = f.lenientEof;
      maxRows = f.maxRows;
      recordSeparator = f.recordSeparator;
      headerComments = f.headerComments;
      autoFlush = f.autoFlush;
    }

    /**
     * Strict (default) or Commons family. Switching family changes no other option; start from
     * {@code Predefined.DEFAULT.format().toBuilder()} for Commons defaults.
     */
    public Builder setStrict(boolean strict) {
      this.strict = strict;
      return this;
    }

    public Builder setDelimiter(char delimiter) {
      this.delimiter = new byte[] {ascii(delimiter, "delimiter")};
      return this;
    }

    /** An ASCII delimiter of 1 to 16 characters (multi-character delimiters need a Commons format). */
    public Builder setDelimiter(String delimiter) {
      byte[] bytes = new byte[delimiter.length()];
      for (int i = 0; i < bytes.length; i++) bytes[i] = ascii(delimiter.charAt(i), "delimiter");
      this.delimiter = bytes;
      return this;
    }

    public Builder setQuote(char quote) {
      this.quote = ascii(quote, "quote");
      this.quoting = true;
      return this;
    }

    /** Null disables quoting: the quote byte is then ordinary content. */
    public Builder setQuote(@Nullable Character quote) {
      if (quote == null) quoting = false;
      else setQuote(quote.charValue());
      return this;
    }

    public Builder setEscape(char escape) {
      this.escape = ascii(escape, "escape");
      return this;
    }

    public Builder setEscape(@Nullable Character escape) {
      if (escape == null) this.escape = -1;
      else setEscape(escape.charValue());
      return this;
    }

    public Builder setCommentMarker(char marker) {
      this.commentMarker = ascii(marker, "comment marker");
      return this;
    }

    public Builder setCommentMarker(@Nullable Character marker) {
      if (marker == null) this.commentMarker = -1;
      else setCommentMarker(marker.charValue());
      return this;
    }

    /** Names from the first record (true), or no header (false). Clears explicit names. */
    public Builder setHeader(boolean header) {
      this.header = header;
      this.headerNames = null;
      return this;
    }

    /** Explicit names; no names means names come from the first record, as in Commons. */
    public Builder setHeader(String... names) {
      if (names.length == 0) return setHeader(true);
      this.header = false;
      this.headerNames = List.of(names);
      return this;
    }

    public Builder setSkipHeaderRecord(boolean skip) {
      this.skipHeaderRecord = skip;
      return this;
    }

    public Builder setAllowMissingColumnNames(boolean allow) {
      this.allowMissingColumnNames = allow;
      return this;
    }

    public Builder setDuplicateHeaderMode(DuplicateHeaderMode mode) {
      this.duplicateHeaderMode = Objects.requireNonNull(mode);
      return this;
    }

    public Builder setIgnoreHeaderCase(boolean ignore) {
      this.ignoreHeaderCase = ignore;
      return this;
    }

    public Builder setIgnoreEmptyLines(boolean ignore) {
      this.ignoreEmptyLines = ignore;
      return this;
    }

    public Builder setIgnoreSurroundingSpaces(boolean ignore) {
      this.ignoreSurroundingSpaces = ignore;
      return this;
    }

    public Builder setTrim(boolean trim) {
      this.trim = trim;
      return this;
    }

    public Builder setFlexible(boolean flexible) {
      this.flexible = flexible;
      return this;
    }

    /** One null string (Commons {@code setNullString}); null clears. */
    public Builder setNullString(@Nullable String token) {
      this.nullStrings = token == null ? null : List.of(token);
      return this;
    }

    /** Several null strings; none clears. */
    public Builder setNullStrings(String... tokens) {
      List<String> list = new ArrayList<>(tokens.length);
      for (String t : tokens) list.add(Objects.requireNonNull(t));
      this.nullStrings = list.isEmpty() ? null : list;
      return this;
    }

    public Builder setQuoteMode(@Nullable QuoteMode mode) {
      this.quoteMode = mode;
      return this;
    }

    public Builder setTrailingDelimiter(boolean trailing) {
      this.trailingDelimiter = trailing;
      return this;
    }

    public Builder setTrailingData(boolean trailing) {
      this.trailingData = trailing;
      return this;
    }

    public Builder setLenientEof(boolean lenient) {
      this.lenientEof = lenient;
      return this;
    }

    public Builder setMaxRows(long maxRows) {
      this.maxRows = maxRows;
      return this;
    }

    public Builder setRecordSeparator(@Nullable String separator) {
      this.recordSeparator = separator;
      return this;
    }

    public Builder setRecordSeparator(char separator) {
      this.recordSeparator = String.valueOf(separator);
      return this;
    }

    public Builder setHeaderComments(String... comments) {
      this.headerComments = comments.length == 0 ? null : List.of(comments);
      return this;
    }

    public Builder setAutoFlush(boolean autoFlush) {
      this.autoFlush = autoFlush;
      return this;
    }

    public CsvFormat build() {
      return new CsvFormat(this);
    }
  }
}
