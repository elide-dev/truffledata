package dev.elide.truffledata.csv;

import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import org.jspecify.annotations.Nullable;

/**
 * Record framing over a structural index ({@link CsvIndex}): the fast path of {@link CsvScanner} for
 * every dialect the index represents ({@link #indexable}). It reports exactly the records, spans,
 * return offsets and {@code stopped} flags of the per-byte scanner of the same dialect (the strict
 * loop in {@link CsvScanner}, or {@link CommonsScanner}), and never raises a diagnostic.
 *
 * <p>When it reaches a record the per-byte scanner might reject or must decode byte by byte (a
 * quoted field holding unquoted bytes, which covers malformed quotes, blanks around quotes under
 * strict trim and Commons trailing data or mid-token quotes; an open quote at end of input; more
 * than {@code maxColumns} fields or {@code maxRecordBytes}; or, without eof, an incomplete tail that
 * holds a quote or is already {@code maxColumns} wide), it stops before that record, returns the
 * offset the per-byte scanner must resume from and sets {@link #fallback}. {@link CsvScanner} then
 * runs the per-byte scanner over that one record and resumes here.
 *
 * <p>Strict and Commons framing differ only at record boundaries: the strict loop commits skipped
 * empty lines (and a lone final CR) at once, while the Commons lexer commits them only with the
 * next complete record, and Commons formats may keep empty lines as one-field records.
 *
 * <p>Not thread-safe; allocates only when a record is wider than any before it.
 */
final class IndexScan {
  private static final byte CR = '\r';
  private static final byte LF = '\n';

  private final CsvIndexer indexer;
  private final byte delimiter;
  private final boolean trim; // strict trim (Commons formats that trim are not indexable)
  private final boolean commons;
  private final boolean emptyLineRecords; // Commons ignoreEmptyLines(false)
  private final int maxColumns;
  private final int maxRecordBytes;
  /** Index entries per kernel call (the shared buffer holds at least this many). */
  private final int capacity;
  private long[] spans;

  /** Set by {@link #scan}: whether it stopped before a record the per-byte scanner must handle. */
  boolean fallback;

  /** Set by {@link #scan}: whether it stopped at {@code stopAfter} (the per-byte rule). */
  boolean stopped;

  /** Set by {@link #scan}: records reported. */
  long reported;

  IndexScan(Dialect dialect, CsvLimits limits, CsvIndexer indexer, int capacity) {
    this.indexer = indexer;
    delimiter = dialect.delimiter[0];
    trim = dialect.strict && dialect.trim;
    commons = !dialect.strict;
    emptyLineRecords = commons && !dialect.ignoreEmptyLines;
    maxColumns = limits.maxColumns();
    maxRecordBytes = limits.maxRecordBytes();
    this.capacity = Math.max(64, Math.min(capacity, CsvIndex.MAX_CALL_BYTES));
    spans = new long[Math.min(16, maxColumns)];
  }

  /**
   * Whether the index represents a dialect: every strict format, and Commons formats with a
   * one-byte delimiter, no comment marker, no escape other than the quote, and none of the Commons
   * trims or trailing delimiter (those change field values or framing per byte).
   */
  static boolean indexable(Dialect d) {
    if (d.strict) return true;
    return d.delimiter.length == 1
        && d.comment == Dialect.UNSET
        && (d.escape == Dialect.UNSET || d.escape == d.quoteChar)
        && !d.ignoreSurroundingSpaces
        && !d.trim
        && !d.trailingDelimiter;
  }

  CsvIndexer indexer() {
    return indexer;
  }

  /** Index capacity (entries per kernel call). */
  int capacity() {
    return capacity;
  }

  /**
   * Scans complete records in [from, to), which starts at a record boundary, and returns the offset
   * after the last record consumed (the {@link CsvScanner#scan} contract; see the class comment for
   * {@link #fallback}). {@code recordBase} counts physical records before {@code from}.
   */
  int scan(
      byte[] input,
      int from,
      int to,
      boolean eof,
      CsvScanner.Records records,
      long recordBase,
      long stopAfter) {
    fallback = false;
    stopped = false;
    reported = 0;
    if (recordBase >= stopAfter && (commons || from < to)) {
      stopped = true;
      return from;
    }
    // The index is live only during this call, so one buffer per thread serves every scanner.
    int[] idx = SHARED.get();
    if (idx == null || idx.length < capacity) idx = new int[capacity];
    SHARED.set(null); // a scan nested in a callback allocates its own
    try {
      return frame(idx, input, from, to, eof, records, recordBase, stopAfter);
    } finally {
      SHARED.set(idx);
    }
  }

  /**
   * Per-thread index buffer: at most the largest capacity a provider asked for (64 Ki entries,
   * 256 KiB, for the shipped providers), kept for the thread's lifetime.
   */
  private static final ThreadLocal<int @Nullable []> SHARED = new ThreadLocal<>();

  private int frame(
      int[] idx,
      byte[] input,
      int from,
      int to,
      boolean eof,
      CsvScanner.Records records,
      long recordBase,
      long stopAfter) {
    final CsvIndexer ix = indexer;
    final byte delim = delimiter;
    final boolean commonsFraming = commons;
    // Fused: a batch builder takes each field as it is framed (no per-record pass over spans); a
    // record it declines goes through records.record with the spans, as on the unfused path.
    final BatchBuilder fused = records instanceof BatchBuilder b && b.fusable() ? b : null;
    boolean converted = true; // every field of the current record so far was put
    ix.carry(0);
    int pos = from;
    int resume = from; // where a refused, incomplete or handed-over record restarts
    int recordStart = from;
    int fieldStart = from;
    int count = 0;
    boolean quoted = false; // a field of the current record holds a quote
    int skip = -1; // the LF of a CRLF pair, already consumed
    while (pos < to) {
      int length = CsvIndex.indexable(to - pos, idx.length);
      int n = ix.index(input, pos, length, idx);
      for (int k = 0; k < n; k++) {
        int entry = idx[k];
        int p = pos + (entry & CsvIndex.OFFSET_MASK);
        if (p == skip) continue;
        byte b = input[p];
        if (b != delim && count == 0 && p == fieldStart) {
          // A completely empty line: CRLF, LF or a lone CR.
          int next = p + 1;
          if (b == CR) {
            if (next < to) {
              if (input[next] == LF) skip = next++;
            } else if (commonsFraming && !eof) {
              return resume; // Commons peeks past a final CR
            }
          }
          if (emptyLineRecords) {
            spans[0] = CsvScanner.span(p, 0, false, false);
            if (!records.record(spans, 1, p, next)) return resume;
            reported++;
            if (recordBase + reported >= stopAfter) {
              stopped = true; // Commons checks before the next record, even at the end
              return next;
            }
            resume = next;
          } else if (!commonsFraming) {
            resume = next;
          }
          recordStart = fieldStart = next;
          continue;
        }
        if (count == maxColumns) return stop(resume);
        long span;
        if (entry < 0) {
          if ((entry & CsvIndex.UNQUOTED) != 0) return stop(resume);
          if ((entry & CsvIndex.ESCAPED) != 0 && commonsFraming) {
            // A Commons complex token: the span covers the raw token, decoded on read.
            span = CsvScanner.span(fieldStart, p - fieldStart, true, true);
          } else {
            span =
                (long) (fieldStart + 1) << 32
                    | (long) (p - fieldStart - 2) << 2
                    | 2
                    | (entry >>> 30 & 1);
          }
          quoted = true;
        } else if (trim) {
          span = trimmed(input, fieldStart, p);
        } else {
          span = (long) fieldStart << 32 | (long) (p - fieldStart) << 2;
        }
        if (count == spans.length) grow();
        spans[count] = span;
        if (fused != null && converted) converted = fused.put(count, span);
        count++;
        if (b == delim) {
          fieldStart = p + 1;
          continue;
        }
        if (p - recordStart > maxRecordBytes) return stop(resume);
        int end;
        if (b == LF) end = p + 1;
        else if (p + 1 < to) {
          end = p + 1;
          if (input[end] == LF) skip = end++;
        } else if (eof) end = p + 1;
        else return resume; // a final CR may be the first half of CRLF
        if (!(fused != null && converted && fused.commit(count, recordStart, end))
            && !records.record(spans, count, recordStart, end)) return resume;
        converted = true;
        reported++;
        if (recordBase + reported >= stopAfter && (commonsFraming || end < to)) {
          stopped = true;
          return end;
        }
        resume = recordStart = fieldStart = end;
        count = 0;
        quoted = false;
      }
      pos += length;
    }
    if (recordStart >= to) return commonsFraming && !eof ? resume : to;
    long carry = ix.carry();
    if (!eof) {
      // An incomplete record is left for the next buffer, but the per-byte scanners report errors
      // they can already see (a quote problem, too many fields), so quoted or full tails go to them.
      boolean suspect = quoted || (carry & CsvIndex.CARRY_QUOTED) != 0 || count == maxColumns;
      return suspect ? stop(resume) : resume;
    }
    // The final record has no terminator.
    if ((carry & CsvIndex.CARRY_INSIDE) != 0) return stop(resume);
    if (count == maxColumns || to - recordStart > maxRecordBytes) return stop(resume);
    int flags = CsvIndex.pendingFlags(carry);
    long span;
    if (flags < 0) {
      if ((flags & CsvIndex.UNQUOTED) != 0) return stop(resume);
      span =
          (flags & CsvIndex.ESCAPED) != 0 && commonsFraming
              ? CsvScanner.span(fieldStart, to - fieldStart, true, true)
              : CsvScanner.span(fieldStart + 1, to - fieldStart - 2, true, (flags & CsvIndex.ESCAPED) != 0);
    } else if (trim) {
      span = trimmed(input, fieldStart, to);
    } else {
      span = CsvScanner.span(fieldStart, to - fieldStart, false, false);
    }
    if (count == spans.length) grow();
    spans[count] = span;
    if (fused != null && converted) converted = fused.put(count, span);
    count++;
    if (!(fused != null && converted && fused.commit(count, recordStart, to))
        && !records.record(spans, count, recordStart, to)) return resume;
    reported++;
    if (commonsFraming && recordBase + reported >= stopAfter) stopped = true;
    return to;
  }

  private int stop(int at) {
    fallback = true;
    return at;
  }

  private static long trimmed(byte[] input, int start, int end) {
    while (start < end && CsvFormat.blank(input[start])) start++;
    while (end > start && CsvFormat.blank(input[end - 1])) end--;
    return CsvScanner.span(start, end - start, false, false);
  }

  private void grow() {
    long[] grown = new long[Math.min(spans.length * 2, maxColumns)];
    System.arraycopy(spans, 0, grown, 0, spans.length);
    spans = grown;
  }
}
