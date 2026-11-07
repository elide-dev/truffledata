package dev.elide.truffledata.csv;

import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.apache.commons.csv.QuoteMode;

/** Test helper: the Commons CSV 1.14.1 format equivalent to one of ours. */
final class CommonsFormats {
  private CommonsFormats() {}

  static CSVFormat.Predefined commons(CsvFormat.Predefined p) {
    return switch (p) {
      case DEFAULT -> CSVFormat.Predefined.Default;
      case EXCEL -> CSVFormat.Predefined.Excel;
      case INFORMIX_UNLOAD -> CSVFormat.Predefined.InformixUnload;
      case INFORMIX_UNLOAD_CSV -> CSVFormat.Predefined.InformixUnloadCsv;
      case MONGODB_CSV -> CSVFormat.Predefined.MongoDBCsv;
      case MONGODB_TSV -> CSVFormat.Predefined.MongoDBTsv;
      case MYSQL -> CSVFormat.Predefined.MySQL;
      case ORACLE -> CSVFormat.Predefined.Oracle;
      case POSTGRESQL_CSV -> CSVFormat.Predefined.PostgreSQLCsv;
      case POSTGRESQL_TEXT -> CSVFormat.Predefined.PostgreSQLText;
      case RFC4180 -> CSVFormat.Predefined.RFC4180;
      case TDF -> CSVFormat.Predefined.TDF;
    };
  }

  static QuoteMode quoteMode(CsvFormat.QuoteMode mode) {
    return QuoteMode.valueOf(mode.name());
  }

  /** Builds the Commons format with every option of {@code f} (at most one null string). */
  static CSVFormat toCommons(CsvFormat f) {
    CSVFormat.Builder b = CSVFormat.DEFAULT.builder();
    b.setDelimiter(f.delimiterString());
    b.setQuote(f.quoting() ? Character.valueOf((char) f.quote()) : null);
    b.setEscape(f.escape() < 0 ? null : Character.valueOf((char) f.escape()));
    b.setCommentMarker(f.commentMarker() < 0 ? null : Character.valueOf((char) f.commentMarker()));
    List<String> names = f.headerNames();
    if (names != null) b.setHeader(names.toArray(new String[0]));
    else if (f.header()) b.setHeader();
    else b.setHeader((String[]) null);
    b.setSkipHeaderRecord(f.skipHeaderRecord());
    b.setAllowMissingColumnNames(f.allowMissingColumnNames());
    b.setDuplicateHeaderMode(DuplicateHeaderMode.valueOf(f.duplicateHeaderMode().name()));
    b.setIgnoreHeaderCase(f.ignoreHeaderCase());
    b.setIgnoreEmptyLines(f.ignoreEmptyLines());
    b.setIgnoreSurroundingSpaces(f.ignoreSurroundingSpaces());
    b.setTrim(f.trim());
    List<String> nulls = f.nullStrings();
    if (nulls != null && nulls.size() > 1)
      throw new IllegalArgumentException("Commons supports one null string");
    // After setQuote: Commons derives its quoted null string from the current quote.
    // (Commons sets a quoted null string even for null; skip it to keep equality with presets.)
    if (nulls != null) b.setNullString(nulls.get(0));
    CsvFormat.QuoteMode mode = f.quoteMode();
    b.setQuoteMode(mode == null ? null : quoteMode(mode));
    b.setTrailingDelimiter(f.trailingDelimiter());
    b.setTrailingData(f.trailingData());
    b.setLenientEof(f.lenientEof());
    b.setMaxRows(f.maxRows());
    b.setRecordSeparator(f.recordSeparator());
    List<String> comments = f.headerComments();
    b.setHeaderComments(comments == null ? null : comments.toArray(new String[0]));
    b.setAutoFlush(f.autoFlush());
    return b.get();
  }
}
