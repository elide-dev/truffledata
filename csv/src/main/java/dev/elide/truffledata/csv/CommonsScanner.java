package dev.elide.truffledata.csv;

import org.jspecify.annotations.Nullable;

/**
 * A byte-level port of the Apache Commons CSV 1.14.1 {@code Lexer} and {@code
 * CSVParser.nextRecord}, for Commons formats. It reports the same records as Commons over UTF-8
 * input, as spans (see {@link Dialect}); it never copies field bytes while scanning.
 *
 * <p>Streaming: without eof, any read past {@code to} (including the one-byte lookahead after CR or a
 * closing quote, and multi-byte delimiter lookahead) abandons the current record, which is reported
 * incomplete by returning its start. Record boundaries are canonical lexer states (the last byte was
 * a line end, the last token was not a delimiter), so scanning resumes there exactly.
 *
 * <p>The same lexer decodes complex spans ({@link #decode}): it re-lexes the raw token with the end of
 * the span as end of input. Not thread-safe; decoders are per use.
 */
final class CommonsScanner {
  private static final int EOF = -1;
  private static final int CR = '\r';
  private static final int LF = '\n';
  private static final byte[] EMPTY = new byte[0];

  // Token types.
  private static final int TOKEN = 0;
  private static final int EORECORD = 1;
  private static final int EOF_READY = 2; // end of input, with a value
  private static final int EOF_EMPTY = 3; // end of input, no value
  private static final int COMMENT = 4;

  /** Control flow for a read past the buffer without eof; thrown at most once per scan. */
  private static final class Incomplete extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Incomplete() {
      super(null, null, false, false);
    }
  }

  private static final Incomplete INCOMPLETE = new Incomplete();

  private final Dialect dialect;
  private final byte[] delimiter;
  private final int dlen;
  private final int d0;
  private final int quote;
  private final int escape;
  private final int comment;
  private final boolean ignoreSurroundingSpaces;
  private final boolean ignoreEmptyLines;
  private final boolean lenientEof;
  private final boolean trailingData;
  private final boolean trailingDelimiter;
  private final boolean trim;
  private final CsvKernel kernel;
  private final int maxColumns;
  private final int maxRecordBytes;

  private long[] spans;
  private @Nullable CommonsScanner valueDecoder;
  private byte[] valueScratch = EMPTY;

  /** Physical records before the scanned range, for diagnostics and {@link #stopAfter}. */
  long recordBase;

  /** Stream offset of input index 0, for diagnostics. */
  long byteBase;

  /** Stop before the record whose physical number would exceed this (Commons maxRows). */
  long stopAfter = Long.MAX_VALUE;

  /** Whether the last scan stopped at {@link #stopAfter}. */
  boolean stopped;

  // Lexer state.
  private byte[] in = EMPTY;
  private int pos;
  private int to;
  private boolean eof;
  private int lastChar;
  private boolean lastTokenDelimiter;
  private long ordinal;

  /**
   * The offset of the first byte past the blank/comment lines skipped by the current
   * {@code nextToken}, for {@link #scan}'s incomplete tail when no token of the pending
   * record has been parsed yet. Once a token is parsed, {@code recordStart} is the
   * record-relative start; this is only read before that, so {@code filled - end} measures
   * record bytes only and {@code CsvReader.checkIncomplete} matches the strict scanner.
   */
  private int pendingStart;

  // The current token.
  private int type;
  private boolean quoted;
  private boolean complex;
  private int tokenStart;
  private int contentStart;
  private int contentEnd;
  private int regionEnd;

  // Decode output; null while scanning.
  private byte @Nullable [] out;
  private int outLen;

  CommonsScanner(Dialect dialect, int maxColumns, int maxRecordBytes) {
    this.dialect = dialect;
    delimiter = dialect.delimiter;
    dlen = delimiter.length;
    d0 = delimiter[0] & 0xFF;
    quote = dialect.quoteChar;
    escape = dialect.escape;
    comment = dialect.comment;
    ignoreSurroundingSpaces = dialect.ignoreSurroundingSpaces;
    ignoreEmptyLines = dialect.ignoreEmptyLines;
    lenientEof = dialect.lenientEof;
    trailingData = dialect.trailingData;
    trailingDelimiter = dialect.trailingDelimiter;
    trim = dialect.trim;
    // Quotes are ordinary inside unquoted Commons tokens.
    kernel =
        new CsvKernel(
            delimiter[0], dialect.quote, false, escape == Dialect.UNSET ? -1 : escape);
    this.maxColumns = maxColumns;
    this.maxRecordBytes = maxRecordBytes;
    spans = new long[Math.min(16, Math.max(1, maxColumns))];
  }

  /** {@link CsvScanner#scan} for Commons formats. */
  int scan(byte[] input, int from, int to, boolean eof, CsvScanner.Records records) {
    this.in = input;
    this.to = to;
    this.eof = eof;
    this.out = null;
    stopped = false;
    pos = from;
    ordinal = 0;
    int start = from;
    int recordStart = -1;
    try {
      for (; ; ) {
        if (recordBase + ordinal >= stopAfter) {
          stopped = true;
          return pos;
        }
        start = pos;
        // Canonical state at a record boundary: after a line end, not after a delimiter.
        lastChar = LF;
        lastTokenDelimiter = false;
        int count = 0;
        recordStart = -1;
        int recordEnd = -1;
        for (; ; ) {
          nextToken();
          int t = type;
          if (t == COMMENT) continue;
          if (t == EOF_EMPTY) break;
          if (recordStart < 0) recordStart = tokenStart;
          recordEnd = regionEnd;
          boolean last = t != TOKEN;
          count = add(count, last);
          if (last) break;
        }
        if (count == 0) {
          // Commons ends parsing silently when trailingDelimiter empties a record; we reject it.
          if (recordStart >= 0)
            throw syntax(recordStart, "empty record with a trailing delimiter");
          return pos; // only empty lines and comments remained
        }
        if (recordEnd - recordStart > maxRecordBytes) throw CsvScanner.limit("maxRecordBytes");
        if (!records.record(spans, count, recordStart, pos)) return recordStart;
        ordinal++;
      }
    } catch (Incomplete e) {
      // The incomplete tail starts at the pending record's first byte (after blank/comment
      // lines were skipped), so CsvReader.checkIncomplete measures record bytes only. When
      // the first token of the record was already parsed, that is recordStart; otherwise
      // pendingStart is the post-skip offset (the would-be first byte, or `to` if the
      // buffer ends mid-skip with no pending record).
      return recordStart >= 0 ? recordStart : pendingStart;
    }
  }

  /** Commons {@code addRecordValue}: trim, then drop a trailing empty value. */
  private int add(int count, boolean last) {
    long span;
    if (complex) span = CsvScanner.span(tokenStart, regionEnd - tokenStart, quoted, true);
    else {
      int cs = contentStart;
      int ce = contentEnd;
      if (!quoted && ignoreSurroundingSpaces) ce = Dialect.trimTrailingWhitespace(in, cs, ce);
      if (trim) {
        while (cs < ce && Dialect.trimByte(in[cs])) cs++;
        while (ce > cs && Dialect.trimByte(in[ce - 1])) ce--;
      }
      span = CsvScanner.span(cs, ce - cs, quoted, false);
    }
    if (last && trailingDelimiter && valueLength(span) == 0) return count;
    if (count == maxColumns) throw CsvScanner.limit("maxColumns");
    if (count == spans.length) {
      long[] grown = new long[(int) Math.min((long) spans.length * 2, maxColumns)];
      System.arraycopy(spans, 0, grown, 0, spans.length);
      spans = grown;
    }
    spans[count] = span;
    return count + 1;
  }

  private int valueLength(long span) {
    if (!CsvScanner.escaped(span)) return CsvScanner.length(span);
    CommonsScanner d = valueDecoder;
    if (d == null) valueDecoder = d = new CommonsScanner(dialect, 1, Integer.MAX_VALUE);
    int length = CsvScanner.length(span);
    if (valueScratch.length < length) valueScratch = new byte[length];
    return d.decode(in, span, valueScratch, 0);
  }

  /**
   * Decodes a complex span (see {@link Dialect}) into {@code target} at {@code at}, applying the
   * Commons trims, and returns the value length (at most the span length).
   */
  int decode(byte[] input, long span, byte[] target, int at) {
    int offset = CsvScanner.offset(span);
    in = input;
    pos = offset;
    to = offset + CsvScanner.length(span);
    eof = true;
    out = target;
    outLen = at;
    lastChar = 'x';
    lastTokenDelimiter = false;
    int cPos = pos;
    int c = read();
    token(c, cPos, readEndOfLine(c));
    int start = at;
    int end = outLen;
    if (!quoted && ignoreSurroundingSpaces) end = Dialect.trimTrailingWhitespace(target, start, end);
    if (trim) {
      while (start < end && Dialect.trimByte(target[start])) start++;
      while (end > start && Dialect.trimByte(target[end - 1])) end--;
    }
    if (start > at) System.arraycopy(target, start, target, at, end - start);
    out = null;
    in = EMPTY;
    return end - start;
  }

  // ---- reader primitives (ExtendedBufferedReader over [pos, to))

  private int read() {
    if (pos < to) return lastChar = in[pos++] & 0xFF;
    if (!eof) throw INCOMPLETE;
    return lastChar = EOF;
  }

  private int peek() {
    if (pos < to) return in[pos] & 0xFF;
    if (!eof) throw INCOMPLETE;
    return EOF;
  }

  /** The byte at {@code at}, or EOF past the end of input. */
  private int byteAt(int at) {
    if (at < to) return in[at] & 0xFF;
    if (!eof) throw INCOMPLETE;
    return EOF;
  }

  private void append(int b) {
    byte[] o = out;
    if (o != null) o[outLen++] = (byte) b;
  }

  private void appendRange(int from, int until) {
    byte[] o = out;
    if (o != null) {
      System.arraycopy(in, from, o, outLen, until - from);
      outLen += until - from;
    }
  }

  private static boolean startOfLine(int c) {
    return c == LF || c == CR; // the start of input is canonicalized to LF
  }

  // ---- Lexer

  private boolean readEndOfLine(int c) {
    if (c == CR && peek() == LF) read();
    return c == CR || c == LF;
  }

  private boolean isDelimiter(int c) {
    lastTokenDelimiter = false;
    if (c != d0) return false;
    if (dlen > 1) {
      for (int i = 1; i < dlen; i++) {
        int b = byteAt(pos + i - 1);
        if (b != (delimiter[i] & 0xFF)) return false; // EOF never matches
      }
      pos += dlen - 1;
      lastChar = delimiter[dlen - 1] & 0xFF;
    }
    lastTokenDelimiter = true;
    return true;
  }

  /** An escaped delimiter: delimiter[0], then escape and delimiter[i] for each further byte. */
  private boolean isEscapeDelimiter() {
    int n = 2 * dlen - 1;
    for (int i = 0; i < n; i++) {
      int expected = (i & 1) == 0 ? delimiter[i >> 1] & 0xFF : escape;
      if (byteAt(pos + i) != expected) return false;
    }
    pos += n;
    lastChar = delimiter[dlen - 1] & 0xFF;
    return true;
  }

  /** Commons {@code appendNextEscapedCharacterToToken}; the escape byte has just been read. */
  private void escaped() {
    complex = true;
    if (isEscapeDelimiter()) {
      appendDelimiter();
      return;
    }
    int at = pos;
    int ch = read();
    switch (ch) {
      case 'r' -> append(CR);
      case 'n' -> append(LF);
      case 't' -> append('\t');
      case 'b' -> append('\b');
      case 'f' -> append('\f');
      case CR, LF, '\f', '\t', '\b' -> append(ch);
      case EOF -> throw syntax(at, "escape at end of input");
      default -> {
        if (ch == escape || ch == quote || ch == comment) append(ch);
        else {
          append(escape);
          append(ch);
        }
      }
    }
  }

  private void appendDelimiter() {
    for (byte b : delimiter) append(b & 0xFF);
  }

  /**
   * Whether the byte just read ({@code c}) starts a {@code Character.isWhitespace} character; a
   * three-byte one is consumed.
   */
  private boolean whitespace(int c) {
    if (c < 0x80) return Dialect.asciiWhitespace(c);
    if (!Dialect.wideLead(c)) return false;
    int b1 = byteAt(pos);
    if (b1 == EOF) return false;
    int b2 = byteAt(pos + 1);
    if (b2 == EOF || !Dialect.wideWhitespace(c, b1, b2)) return false;
    pos += 2;
    lastChar = b2;
    return true;
  }

  private void nextToken() {
    int last = lastChar;
    int cPos = pos;
    pendingStart = cPos;
    int c = read();
    boolean eol = readEndOfLine(c);
    if (ignoreEmptyLines) {
      while (eol && startOfLine(last)) {
        last = c;
        cPos = pos;
        pendingStart = cPos;
        c = read();
        eol = readEndOfLine(c);
        if (c == EOF) {
          type = EOF_EMPTY;
          return;
        }
      }
    }
    if (last == EOF || (!lastTokenDelimiter && c == EOF)) {
      type = EOF_EMPTY;
      return;
    }
    if (startOfLine(last) && c == comment) {
      comment(cPos);
      return;
    }
    token(c, cPos, eol);
  }

  /** Commons {@code readLine} after a comment marker: the line is validated and skipped. */
  private void comment(int markerPos) {
    if (peek() == EOF) {
      type = EOF_EMPTY;
      return;
    }
    int end = pos;
    while (end < to && in[end] != CR && in[end] != LF) end++;
    if (end == to && !eof) throw INCOMPLETE;
    int bad = Utf8.invalidOffset(in, markerPos, end);
    if (bad >= 0) throw syntax(bad, "invalid UTF-8");
    pos = end;
    int c = read();
    if (c == CR && peek() == LF) read();
    type = COMMENT;
  }

  private void token(int first, int firstPos, boolean firstEol) {
    int c = first;
    int cPos = firstPos;
    boolean eol = firstEol;
    tokenStart = firstPos;
    quoted = false;
    complex = false;
    if (ignoreSurroundingSpaces) {
      while (whitespace(c) && !isDelimiter(c) && !eol) {
        cPos = pos;
        c = read();
        eol = readEndOfLine(c);
      }
    }
    if (isDelimiter(c)) empty(TOKEN, cPos);
    else if (eol) empty(EORECORD, cPos);
    else if (c == quote) encapsulated(cPos);
    else if (c == EOF) empty(EOF_READY, cPos);
    else simple(c, cPos);
  }

  private void empty(int tokenType, int at) {
    type = tokenType;
    contentStart = contentEnd = regionEnd = at;
  }

  private void simple(int first, int firstPos) {
    int cur = first;
    int curPos = firstPos;
    contentStart = firstPos;
    for (; ; ) {
      if (readEndOfLine(cur)) {
        type = EORECORD;
        break;
      }
      if (cur == EOF) {
        type = EOF_READY;
        break;
      }
      if (isDelimiter(cur)) {
        type = TOKEN;
        break;
      }
      if (cur == escape) escaped();
      else {
        append(cur);
        int run = kernel.skipUnquoted(in, pos, to);
        if (run > pos) {
          appendRange(pos, run);
          lastChar = in[run - 1] & 0xFF;
          pos = run;
        }
      }
      curPos = pos;
      cur = read();
    }
    contentEnd = regionEnd = curPos;
  }

  private void encapsulated(int openPos) {
    quoted = true;
    contentStart = pos;
    for (; ; ) {
      int run = kernel.skipQuoted(in, pos, to);
      if (run > pos) {
        appendRange(pos, run);
        lastChar = in[run - 1] & 0xFF;
        pos = run;
      }
      int cPos = pos;
      int c = read();
      if (c == quote) {
        if (peek() == quote) {
          read();
          complex = true;
          append(c);
        } else {
          contentEnd = cPos;
          afterQuote();
          return;
        }
      } else if (c == escape) escaped();
      else if (c == EOF) {
        if (lenientEof) {
          type = EOF_READY;
          contentEnd = regionEnd = cPos;
          return;
        }
        throw syntax(openPos, "unterminated quoted field");
      } else append(c);
    }
  }

  /** After a closing quote: whitespace is skipped, other bytes are an error or trailing data. */
  private void afterQuote() {
    for (; ; ) {
      int at = pos;
      int c = read();
      if (isDelimiter(c)) {
        type = TOKEN;
        regionEnd = at;
        return;
      }
      if (c == EOF) {
        type = EOF_READY;
        regionEnd = at;
        return;
      }
      if (readEndOfLine(c)) {
        type = EORECORD;
        regionEnd = at;
        return;
      }
      if (trailingData) {
        complex = true;
        append(c);
      } else if (!whitespace(c)) throw syntax(at, "unexpected byte after a closing quote");
    }
  }

  private IllegalArgumentException syntax(int offset, String reason) {
    return CsvScanner.syntax(recordBase + ordinal + 1, byteBase + offset, reason);
  }
}
