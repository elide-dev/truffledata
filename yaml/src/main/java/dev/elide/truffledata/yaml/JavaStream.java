package dev.elide.truffledata.yaml;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Multi-document input for the Java kernel, a port of {@code yaml/native/stream.h}: a bounded
 * carry-over buffer split into documents by a line scan (never by parsing), each chunk decoded by
 * the session's single-document decode, so document boundaries, backpressure, truncation and the
 * failed-stream state match the native stream.
 *
 * <ul>
 *   <li>a line starting with {@code ---} or {@code ...} followed by space, tab, CR, LF or the end of
 *       input is a marker line; {@code ---} starts a new document once the current one started;
 *       {@code ...} ends the current document after its line;
 *   <li>a {@code %} line at column 0 belongs to the following document only when none is open;
 *   <li>a chunk holding no document (comments only) is skipped.
 * </ul>
 */
final class JavaStream {
  static final int OK = 0, LIMIT = 2, INTERNAL = 5, EMPTY = 8, TRUNCATED = 9;

  /** The session's single-document decode of one chunk; throws {@link JavaFailure}. */
  interface Chunks {
    JavaDecoder.Document decode(byte[] chunk);
  }

  private final int maxDocument;
  private final int bufferBytes;
  private byte[] data = new byte[0];
  private int size, start, scan, boundary;
  // Bytes [scan, lineFrom) of the current partial line are known to hold no LF, so a long line fed
  // in small chunks is searched once, not once per chunk.
  private int lineFrom;
  private boolean complete, documentOpen, finished;
  private int failed;
  private @Nullable JavaFailure lastError;
  private JavaDecoder.@Nullable Document current;

  JavaStream(int maxDocumentBytes, int bufferBytes) {
    this.maxDocument = Math.max(1, maxDocumentBytes);
    this.bufferBytes = bufferBytes > 0 ? bufferBytes : 4096;
  }

  /** Bytes the next {@link #feed} accepts at most (0 once finished or failed). */
  int room() {
    if (finished || failed != 0) return 0;
    long pending = size - start;
    long limit = bufferBytes;
    // An open document may grow the buffer, a buffer at a time, up to maxDocument + 5: the +5 lets
    // a full terminating `---`/`...` marker line (the 3-byte marker, an optional CR, and its LF) be
    // buffered before scan() can classify it (a line needs its LF), without counting the marker
    // bytes toward the document (next() bounds the classified extent, not total pending).
    if (!complete && pending >= limit) limit = Math.min(pending + bufferBytes, (long) maxDocument + 5);
    return (int) Math.min(Integer.MAX_VALUE - 8, pending < limit ? limit - pending : 0);
  }

  /** Appends up to {@code length} bytes; fewer than offered means the buffer is full. */
  int feed(byte[] bytes, int offset, int length) {
    if (finished || failed != 0) return 0;
    compact();
    int take = Math.min(length, room());
    if (take == 0) return 0;
    reserve(size + take);
    System.arraycopy(bytes, offset, data, size, take);
    size += take;
    scan();
    return take;
  }

  void finish() {
    finished = true;
    scan();
  }

  /** The document the last successful {@link #next} decoded. */
  JavaDecoder.Document document() {
    return java.util.Objects.requireNonNull(current);
  }

  /** The failure that failed this stream, when a document decode caused it. */
  @Nullable JavaFailure lastError() {
    return lastError;
  }

  /** Decodes the next document: OK, EMPTY (none buffered), TRUNCATED, or a decode status. */
  int next(Chunks chunks) {
    current = null;
    for (;;) {
      if (failed != 0) return failed;
      int end;
      if (complete) {
        end = boundary;
      } else if (finished) {
        if (scan < size) return fail(INTERNAL);
        end = size;
        if (end == start) return EMPTY;
      } else {
        // The open document itself is bounded by the classified extent (scan - start), not total
        // pending (size - start): trailing unclassified bytes of a not-yet-complete terminating
        // `---`/`...` marker line are not part of the document. The authoritative length check below
        // decides at decode time, so only a document larger than maxDocument is LIMIT.
        if (scan - start > maxDocument) return fail(LIMIT);
        return EMPTY;
      }
      byte[] chunk = Arrays.copyOfRange(data, start, end);
      boolean last = !complete;
      start = end;
      complete = false;
      documentOpen = false;
      scan();
      if (chunk.length > maxDocument) return fail(LIMIT);
      if (last && incompleteUtf8(chunk)) return fail(TRUNCATED);
      JavaDecoder.Document document;
      try {
        document = chunks.decode(chunk);
      } catch (JavaFailure failure) {
        lastError = failure;
        // A parser error on the last line of the final document: the input ended inside it (an
        // open flow collection or quoted scalar, a dangling key).
        boolean atEnd = last && finished && failure.parser && failure.status == JavaFailure.SYNTAX
            && failure.line >= lines(chunk);
        return fail(atEnd ? TRUNCATED : failure.status);
      }
      if (!document.present()) continue; // comments or blank lines only
      current = document;
      return OK;
    }
  }

  private int fail(int status) {
    failed = status;
    return status;
  }

  private static boolean marker(byte[] p, int at, int left, char c, boolean eof) {
    if (left < 3 || p[at] != c || p[at + 1] != c || p[at + 2] != c) return false;
    if (left == 3) return eof;
    byte next = p[at + 3];
    return next == ' ' || next == '\t' || next == '\r' || next == '\n';
  }

  // Classifies complete lines from `scan` until a boundary is found or the input is exhausted. A
  // final line without LF is classified only after finish().
  private void scan() {
    while (!complete && scan < size) {
      int line = scan, left = size - scan;
      int lf = -1;
      for (int i = Math.max(line, lineFrom); i < size; i++)
        if (data[i] == '\n') {
          lf = i;
          break;
        }
      if (lf < 0 && !finished) {
        lineFrom = size;
        return;
      }
      int length = lf >= 0 ? lf - line + 1 : left;
      int body = line, bodyLeft = length;
      if (bodyLeft >= 3 && (data[body] & 255) == 0xEF && (data[body + 1] & 255) == 0xBB
          && (data[body + 2] & 255) == 0xBF) {
        body += 3; // a BOM may precede a document's first line
        bodyLeft -= 3;
      }
      boolean eof = lf < 0;
      if (marker(data, body, bodyLeft, '-', eof)) {
        if (documentOpen) {
          boundary = scan; // `---` starts the next document
          complete = true;
          return;
        }
        documentOpen = true;
      } else if (marker(data, body, bodyLeft, '.', eof)) {
        scan += length;
        boundary = scan; // `...` ends this document after its line
        complete = true;
        return;
      } else if (bodyLeft > 0 && data[body] == '%' && !documentOpen) {
        // a directive of the next document
      } else if (!blankOrComment(data, body, bodyLeft)) {
        documentOpen = true;
      }
      scan += length;
    }
  }

  private static boolean blankOrComment(byte[] p, int at, int n) {
    int i = 0;
    while (i < n && (p[at + i] == ' ' || p[at + i] == '\t' || p[at + i] == '\r' || p[at + i] == '\n')) i++;
    return i == n || p[at + i] == '#';
  }

  // The last code point of a final document cut short.
  private static boolean incompleteUtf8(byte[] p) {
    int i = p.length, k = 0;
    while (i > 0 && k < 4 && (p[i - 1] & 0xC0) == 0x80) {
      i--;
      k++;
    }
    if (i == 0) return k > 0;
    int lead = p[i - 1] & 255;
    int need = lead < 0x80 ? 0 : lead >= 0xF0 ? 3 : lead >= 0xE0 ? 2 : lead >= 0xC0 ? 1 : 0;
    return need > k;
  }

  private static long lines(byte[] p) {
    long count = 1;
    for (byte b : p) if (b == '\n') count++;
    return count;
  }

  private void compact() {
    if (start == 0) return;
    int keep = size - start;
    if (keep > 0) System.arraycopy(data, start, data, 0, keep);
    size = keep;
    scan -= start;
    lineFrom = Math.max(0, lineFrom - start);
    if (complete) boundary -= start;
    start = 0;
  }

  private void reserve(int need) {
    if (need <= data.length) return;
    int capacity = Math.max(need, data.length == 0 ? 4096 : data.length * 2);
    data = Arrays.copyOf(data, capacity);
  }
}
