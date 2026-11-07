package dev.elide.truffledata.csv;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import org.jspecify.annotations.Nullable;

/**
 * Record scanner over a byte range: the strict RFC 4180 loop below, or, for Commons formats, {@link
 * CommonsScanner}. Strict scanning: It reports field spans into the input and never
 * copies field bytes. A span packs {@code (long) offset << 32 | (long) length << 2 | quoted << 1 |
 * escaped}: {@code offset} and {@code length} delimit the content with quotes and trimmed blanks
 * excluded, and {@code escaped} marks quoted content containing doubled quotes.
 *
 * <p>Not thread-safe: one scanner keeps one reusable span buffer. It does not validate UTF-8 (record
 * consumers do, over each complete record).
 */
final class CsvScanner {
  /** One complete, non-empty record. Spans use the packing above; spans has length >= count. */
  interface Records {
    /** {@code start} is the record's first byte, {@code end} the offset after its terminator. */
    boolean record(long[] spans, int count, int start, int end); // false: stop before this record
  }

  private static final byte CR = '\r';
  private static final byte LF = '\n';
  private static final byte QUOTE = 3;
  private static final int LENGTH_MASK = (1 << 30) - 1;

  private final byte delimiter;
  private final byte quote;
  private final boolean quoting;
  private final boolean trim;
  private final int maxColumns;
  private final int maxRecordBytes;
  /** Byte classes for unquoted runs: 0 ordinary, 1 delimiter, 2 CR or LF, 3 quote. */
  private final byte[] classes = new byte[256];

  /** Run skips; the seam a faster kernel replaces. */
  private final CsvKernel kernel;

  private long[] spans;

  /** Physical records before the scanned range, for diagnostics (1-based record numbers). */
  long recordBase;

  /** Stream offset of input index 0, for diagnostics. */
  long byteBase;

  /** Stop before the record whose physical number would exceed this (format maxRows). */
  long stopAfter = Long.MAX_VALUE;

  /** Whether the last scan stopped at {@link #stopAfter}. */
  boolean stopped;

  /** The Commons lexer, for Commons formats. */
  private final @Nullable CommonsScanner commons;

  private final Dialect dialect;
  private final CsvLimits limits;

  /** The kernel provider when the dialect is indexable and indexing is on, else null. */
  private final CsvIndexing.@Nullable Provider provider;

  /** The index path, created by the first scan of at least {@link CsvIndexing#MIN_INDEX_BYTES}. */
  private @Nullable IndexScan indexed;

  CsvScanner(CsvFormat format, CsvLimits limits) {
    delimiter = format.delimiter();
    quote = format.quote();
    quoting = format.quoting();
    trim = format.trim();
    maxColumns = limits.maxColumns();
    maxRecordBytes = limits.maxRecordBytes();
    classes[delimiter & 0xFF] = 1;
    classes[CR] = 2;
    classes[LF] = 2;
    if (quoting) classes[quote & 0xFF] = QUOTE;
    kernel = new CsvKernel(delimiter, quote, quoting, -1);
    spans = new long[Math.min(16, maxColumns)];
    this.limits = limits;
    dialect = Dialect.of(format);
    commons = format.strict() ? null : new CommonsScanner(dialect, maxColumns, maxRecordBytes);
    CsvIndexing.Provider p = CsvIndexing.provider();
    provider = p != null && IndexScan.indexable(dialect) ? p : null;
  }

  /**
   * The first invalid UTF-8 offset in [from, to), or -1: the index kernel's validator when it has
   * one (simdutf for native kernels), else the Java validator.
   */
  int invalidUtf8(byte[] input, int from, int to) {
    CsvIndexing.Provider p = provider;
    if (p != null && to - from >= CsvIndexing.minIndexBytes) {
      IndexScan ix = indexed;
      if (ix == null) indexed = ix = CsvIndexing.scanner(p, dialect, limits, to - from);
      CsvIndexer indexer = ix.indexer();
      // Calls of at most the index capacity (native calls may hold off the GC), cut only before a
      // byte that starts a sequence, so no sequence spans two calls.
      int chunk = Math.max(ix.capacity(), 64);
      for (int at = from; at < to; ) {
        int end = to - at <= chunk ? to : at + chunk;
        if (end < to) while (end > at + 1 && (input[end] & 0xC0) == 0x80) end--;
        long bad = indexer.validateUtf8(input, at, end - at);
        if (bad == CsvIndexer.NO_VALIDATOR) return Utf8.invalidOffset(input, at, to);
        if (bad >= 0) return at + (int) bad;
        at = end;
      }
      return -1;
    }
    return Utf8.invalidOffset(input, from, to);
  }

  /** The kernel the index path uses, or null when this scanner has not indexed (yet). */
  @Nullable String kernel() {
    IndexScan ix = indexed;
    return ix == null ? null : ix.indexer().name();
  }

  /**
   * Scans complete records in [from, to) and returns the offset after the last record consumed.
   * Without eof, a record that could continue past `to` is not reported and its start is returned:
   * an open quote, a final quote byte inside a quoted field, a final CR, or a missing terminator.
   * With eof, a final unterminated record is reported and an open quote throws. More than
   * maxColumns fields throws the columns limit.
   */
  int scan(byte[] input, int from, int to, boolean eof, Records records) {
    CsvIndexing.Provider p = provider;
    if (p != null && to - from >= CsvIndexing.minIndexBytes) {
      IndexScan ix = indexed;
      if (ix == null) indexed = ix = CsvIndexing.scanner(p, dialect, limits, to - from);
      return scanIndexed(ix, input, from, to, eof, records);
    }
    return scanBytes(input, from, to, eof, records);
  }

  /**
   * The index path: {@link IndexScan} frames records until it declines one, the per-byte scanner
   * takes that one record (reporting its exact diagnostic, or accepting it), and indexing resumes
   * after it. Record numbers in diagnostics count the records already reported.
   */
  private int scanIndexed(
      IndexScan ix, byte[] input, int from, int to, boolean eof, Records records) {
    final long base = recordBase;
    long reported = 0;
    int pos = from;
    try {
      for (; ; ) {
        int end = ix.scan(input, pos, to, eof, records, base + reported, stopAfter);
        reported += ix.reported;
        stopped = ix.stopped;
        if (!ix.fallback) return end;
        // Rare (malformed records, quoted tails of stream buffers): one small object each.
        One o = new One(records);
        recordBase = base + reported;
        int after = scanBytes(input, end, to, eof, o);
        if (o.passed) reported++;
        if (stopped || after == end) return after;
        pos = after;
      }
    } finally {
      recordBase = base;
    }
  }

  /** Passes the first record of a per-byte scan through and refuses the next one. */
  private static final class One implements Records {
    private final Records target;
    boolean passed;

    One(Records target) {
      this.target = target;
    }

    @Override
    public boolean record(long[] spans, int count, int start, int end) {
      if (passed || !target.record(spans, count, start, end)) return false;
      passed = true;
      return true;
    }
  }

  /** The per-byte scanners: the strict loop below, or the Commons lexer. */
  private int scanBytes(byte[] input, int from, int to, boolean eof, Records records) {
    CommonsScanner c = commons;
    if (c != null) {
      c.recordBase = recordBase;
      c.byteBase = byteBase;
      c.stopAfter = stopAfter;
      int end = c.scan(input, from, to, eof, records);
      stopped = c.stopped;
      return end;
    }
    stopped = false;
    final byte[] cls = classes;
    final CsvKernel k = kernel;
    final byte q = quote;
    int pos = from;
    long ordinal = 0;
    while (pos < to) {
      if (recordBase + ordinal >= stopAfter) {
        stopped = true;
        return pos;
      }
      final int start = pos;
      byte first = input[pos];
      // Completely empty lines are skipped. A lone final CR is consumed even without eof: whether
      // an LF follows or not, it ends an empty line.
      if (first == LF || first == CR) {
        pos += first == CR && pos + 1 < to && input[pos + 1] == LF ? 2 : 1;
        continue;
      }
      int count = 0;
      for (; ; ) {
        if (count == maxColumns) throw limit("maxColumns");
        int p = pos;
        if (trim) while (p < to && CsvFormat.blank(input[p])) p++;
        long span;
        if (quoting && p < to && input[p] == q) {
          final int open = p;
          final int contentStart = ++p;
          boolean escaped = false;
          for (; ; ) {
            p = k.skipQuoted(input, p, to);
            if (p >= to) {
              if (eof) throw syntax(ordinal, open, "unterminated quoted field");
              return start;
            }
            if (p + 1 >= to) {
              if (!eof) return start; // the quote may be the first of a doubled pair
              break;
            }
            if (input[p + 1] != q) break;
            escaped = true;
            p += 2;
          }
          final int contentEnd = p++;
          if (trim) while (p < to && CsvFormat.blank(input[p])) p++;
          if (p < to && cls[input[p] & 0xFF] != 1 && cls[input[p] & 0xFF] != 2)
            throw syntax(ordinal, p, "unexpected byte after a closing quote");
          span = span(contentStart, contentEnd - contentStart, true, escaped);
        } else {
          final int contentStart = p;
          p = k.skipUnquoted(input, p, to);
          if (p < to && cls[input[p] & 0xFF] == QUOTE)
            throw syntax(ordinal, p, "quote in an unquoted field");
          int contentEnd = p;
          if (trim) while (contentEnd > contentStart && CsvFormat.blank(input[contentEnd - 1])) contentEnd--;
          span = span(contentStart, contentEnd - contentStart, false, false);
        }
        if (count == spans.length) grow();
        spans[count++] = span;
        pos = p;
        if (pos >= to) {
          if (!eof) return start;
          checkLength(pos - start);
          if (!records.record(spans, count, start, to)) return start;
          ordinal++;
          break;
        }
        byte next = input[pos];
        if (next == delimiter) {
          pos++;
          continue;
        }
        checkLength(pos - start);
        if (next == LF) pos++;
        else if (pos + 1 < to) pos += input[pos + 1] == LF ? 2 : 1;
        else if (eof) pos++;
        else return start; // a final CR may be the first half of CRLF
        if (!records.record(spans, count, start, pos)) return start;
        ordinal++;
        break;
      }
    }
    return pos;
  }

  private void checkLength(int recordBytes) {
    if (recordBytes > maxRecordBytes) throw limit("maxRecordBytes");
  }

  private void grow() {
    long[] grown = new long[Math.min(spans.length * 2, maxColumns)];
    System.arraycopy(spans, 0, grown, 0, spans.length);
    spans = grown;
  }

  private IllegalArgumentException syntax(long ordinal, int offset, String reason) {
    return syntax(recordBase + ordinal + 1, byteBase + offset, reason);
  }

  @TruffleBoundary
  static IllegalArgumentException syntax(long record, long offset, String reason) {
    return new IllegalArgumentException(
        "CSV syntax at record " + record + ", byte " + offset + ": " + reason);
  }

  @TruffleBoundary
  static IllegalArgumentException limit(String name) {
    return new IllegalArgumentException("CSV resource limit: " + name);
  }

  static long span(int offset, int length, boolean quoted, boolean escaped) {
    return (long) offset << 32 | (long) length << 2 | (quoted ? 2 : 0) | (escaped ? 1 : 0);
  }

  static int offset(long span) {
    return (int) (span >>> 32);
  }

  static int length(long span) {
    return (int) (span >>> 2) & LENGTH_MASK;
  }

  static boolean quoted(long span) {
    return (span & 2) != 0;
  }

  static boolean escaped(long span) {
    return (span & 1) != 0;
  }

  /**
   * Copies a span's content into {@code target} at {@code at}, collapsing doubled quotes when the
   * span is escaped, and returns the number of bytes written (at most {@link #length}).
   */
  static int unescape(byte[] input, long span, byte quote, byte[] target, int at) {
    int from = offset(span);
    int length = length(span);
    if (!escaped(span)) {
      System.arraycopy(input, from, target, at, length);
      return length;
    }
    // Content holds only doubled quotes: copy each run up to and including a quote, skip its twin.
    // Native Image copies short runs (escape-heavy text) inline, where an arraycopy per run is a
    // call; the JVM's intrinsic arraycopy is faster at every run length (same-worker probes).
    int to = from + length;
    int out = at;
    int i = from;
    while (i < to) {
      int q = i;
      while (q < to && input[q] != quote) q++;
      int run = q < to ? q + 1 - i : q - i;
      if (TruffleOptions.AOT && run <= 16) {
        for (int k = 0; k < run; k++) target[out + k] = input[i + k];
      } else {
        System.arraycopy(input, i, target, out, run);
      }
      out += run;
      i = q + 2;
    }
    return out - at;
  }
}
