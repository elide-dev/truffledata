package dev.elide.truffledata.csv.kernel;

import dev.elide.truffledata.csv.CsvFormat;
import dev.elide.truffledata.csv.CsvLimits;

/**
 * Record scanner over a structural index: the reference consumer of {@link CsvIndex} and the
 * shape a scanner seam takes when it adopts a kernel.
 *
 * <p>{@link #scan} has the contract of the strict scanner ({@code CsvScanner.scan}): records are
 * reported in order through {@link Records} with spans packed {@code offset << 32 | length << 2 |
 * quoted << 1 | escaped}, empty lines are skipped, and without {@code eof} a record that could
 * continue past {@code to} is not reported and its start is returned.
 *
 * <p>The fast path never reports an error. When it meets a record that the strict scanner might
 * reject, or that needs per-byte handling (a quoted field with unquoted bytes, which includes
 * blanks around quotes under {@code trim}; an unterminated quote at end of input; more than
 * {@code maxColumns} fields; more than {@code maxRecordBytes}; an incomplete tail without
 * {@code eof} that holds a quote or is already {@code maxColumns} wide, where the strict scanner
 * may report an error before the record completes), it stops before that record, returns its
 * start and sets {@link #fallback()}. The caller then scans at least that record with
 * the strict scanner, which reports the exact diagnostic or accepts it, and resumes here. Every
 * record this scanner reports is byte-for-byte what the strict scanner reports.
 *
 * <p>Not thread-safe; never allocates per record or per field.
 */
public final class IndexedScanner {
  /** One complete, non-empty record; the same callback shape as {@code CsvScanner.Records}. */
  public interface Records {
    /** {@code start} is the record's first byte, {@code end} the offset after its terminator. */
    boolean record(long[] spans, int count, int start, int end); // false: stop before this record
  }

  private static final byte CR = '\r';
  private static final byte LF = '\n';

  private final CsvIndexer indexer;
  private final byte delimiter;
  private final boolean trim;
  private final int maxColumns;
  private final int maxRecordBytes;
  private final int[] index;
  private long[] spans;
  private boolean fallback;

  /**
   * {@code indexCapacity} entries bound each indexer call to that many bytes (at least 64; larger
   * calls amortize native transitions, smaller ones keep the index in cache).
   */
  public IndexedScanner(CsvFormat format, CsvLimits limits, CsvIndexer indexer, int indexCapacity) {
    if (indexCapacity < 64) throw new IllegalArgumentException("CSV index capacity");
    if (indexer.delimiter != format.delimiter()
        || indexer.quoting != format.quoting()
        || (format.quoting() && indexer.quote != format.quote()))
      throw new IllegalArgumentException("CSV indexer format mismatch");
    this.indexer = indexer;
    delimiter = format.delimiter();
    trim = format.trim();
    maxColumns = limits.maxColumns();
    maxRecordBytes = limits.maxRecordBytes();
    index = new int[Math.min(indexCapacity, CsvIndex.MAX_CALL_BYTES)];
    spans = new long[Math.min(16, maxColumns)];
  }

  public CsvIndexer indexer() {
    return indexer;
  }

  /** Whether the last {@link #scan} stopped before a record the strict scanner must handle. */
  public boolean fallback() {
    return fallback;
  }

  /**
   * Scans complete records in [from, to), which must start at a record boundary, and returns the
   * offset after the last record consumed (see the class contract for {@link #fallback()}).
   */
  public int scan(byte[] input, int from, int to, boolean eof, Records records) {
    fallback = false;
    final CsvIndexer ix = indexer;
    final int[] idx = index;
    final byte delim = delimiter;
    ix.carry(0);
    int pos = from;
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
          // A completely empty line: CRLF, LF or a lone CR (consumed even at a buffer's end).
          int next = p + 1;
          if (b == CR && next < to && input[next] == LF) skip = next++;
          recordStart = fieldStart = next;
          continue;
        }
        if (count == maxColumns) return stop(recordStart);
        long span;
        if (entry < 0) {
          if ((entry & CsvIndex.UNQUOTED) != 0) return stop(recordStart);
          span = span(fieldStart + 1, p - fieldStart - 2, 2 | (entry >>> 30 & 1));
          quoted = true;
        } else if (trim) {
          span = trimmed(input, fieldStart, p);
        } else {
          span = span(fieldStart, p - fieldStart, 0);
        }
        if (count == spans.length) grow();
        spans[count++] = span;
        if (b == delim) {
          fieldStart = p + 1;
          continue;
        }
        if (p - recordStart > maxRecordBytes) return stop(recordStart);
        int end;
        if (b == LF) end = p + 1;
        else if (p + 1 < to) {
          end = p + 1;
          if (input[end] == LF) skip = end++;
        } else if (eof) end = p + 1;
        else return recordStart; // a final CR may be the first half of CRLF
        if (!records.record(spans, count, recordStart, end)) return recordStart;
        recordStart = fieldStart = end;
        count = 0;
        quoted = false;
      }
      pos += length;
    }
    if (recordStart >= to) return to;
    long carry = ix.carry();
    if (!eof) {
      // An incomplete record is left for the next buffer, but the strict scanner reports errors
      // it can already see (a quote problem, too many fields), so quoted or full tails go to it.
      boolean suspect = quoted || (carry & CsvIndex.CARRY_QUOTED) != 0 || count == maxColumns;
      return suspect ? stop(recordStart) : recordStart;
    }
    // The final record has no terminator.
    if ((carry & CsvIndex.CARRY_INSIDE) != 0) return stop(recordStart);
    if (count == maxColumns || to - recordStart > maxRecordBytes) return stop(recordStart);
    int flags = CsvIndex.pendingFlags(carry);
    long span;
    if (flags < 0) {
      if ((flags & CsvIndex.UNQUOTED) != 0) return stop(recordStart);
      span = span(fieldStart + 1, to - fieldStart - 2, 2 | (flags >>> 30 & 1));
    } else if (trim) {
      span = trimmed(input, fieldStart, to);
    } else {
      span = span(fieldStart, to - fieldStart, 0);
    }
    if (count == spans.length) grow();
    spans[count++] = span;
    if (!records.record(spans, count, recordStart, to)) return recordStart;
    return to;
  }

  private int stop(int recordStart) {
    fallback = true;
    return recordStart;
  }

  private static long span(int offset, int length, int bits) {
    return (long) offset << 32 | (long) length << 2 | bits;
  }

  private static boolean blank(byte b) {
    return b == ' ' || b == '\t';
  }

  private static long trimmed(byte[] input, int start, int end) {
    while (start < end && blank(input[start])) start++;
    while (end > start && blank(input[end - 1])) end--;
    return span(start, end - start, 0);
  }

  private void grow() {
    long[] grown = new long[Math.min(spans.length * 2, maxColumns)];
    System.arraycopy(spans, 0, grown, 0, spans.length);
    spans = grown;
  }
}
