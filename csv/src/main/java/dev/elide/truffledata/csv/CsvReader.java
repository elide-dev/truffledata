package dev.elide.truffledata.csv;

import dev.elide.truffledata.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Streams CSV as immutable batches that all share one {@link Schema} instance. Each batch owns a
 * fresh buffer (about {@code batchBytes} plus the carried incomplete record); buffers are never
 * reused, so returned batches stay valid after later reads and after close. Memory is bounded by
 * one batch buffer and one incomplete record of at most {@code maxRecordBytes}. Single-threaded;
 * the reader owns and closes its stream.
 */
public final class CsvReader implements AutoCloseable {
  private static final byte[] EMPTY = new byte[0];

  private final InputStream input;
  private final CsvPlan plan;
  private final CsvLimits limits;
  private final int batchBytes;
  private final CsvScanner scanner;

  /** Unconsumed bytes: carry[carryFrom, carryFrom + carryLength) at stream offset carryOffset. */
  private byte[] carry = EMPTY;

  private int carryFrom;
  private int carryLength;
  private long carryOffset;
  private int filled;
  private boolean eof;
  private boolean bomChecked;
  private boolean closed;

  /** The format's maxRows was reached: no further batches. */
  private boolean finished;

  /** Physical records the binding consumed (a header or skipped record). */
  private long consumed;
  private CsvPlan.@Nullable Binding binding;
  private long physical;
  private long records;
  private int rowHint = 16;

  CsvReader(InputStream input, CsvPlan plan, CsvLimits limits, int batchBytes) {
    if (batchBytes <= 0 || batchBytes > limits.maxBatchBytes())
      throw new IllegalArgumentException("CSV batchBytes must be in 1..maxBatchBytes");
    this.input = Objects.requireNonNull(input);
    this.plan = plan;
    this.limits = limits;
    this.batchBytes = batchBytes;
    this.scanner = new CsvScanner(plan.format(), limits);
  }

  /** Reads the header (or first record) on first call; the same instance for every batch. */
  public Schema schema() throws IOException {
    return bind().schema;
  }

  /** The next batch of at least one row, or null at end of input. */
  public @Nullable CsvBatch next() throws IOException {
    CsvPlan.Binding b = bind();
    int size = carryLength + batchBytes;
    for (; ; ) {
      long stop = plan.stopAfter(consumed);
      if (finished || physical >= stop) return null;
      if (eof && carryLength == 0) return null;
      byte[] buffer = fill(size);
      scanner.recordBase = physical;
      scanner.byteBase = carryOffset;
      scanner.stopAfter = stop;
      BatchBuilder builder =
          new BatchBuilder(
              b, buffer, limits, plan.dialect, records, physical, carryOffset, rowHint);
      int end = scanner.scan(buffer, 0, filled, eof, builder);
      if (scanner.stopped) finished = true;
      else if (!builder.full()) checkIncomplete(end);
      consume(buffer, end);
      int rows = builder.rows();
      if (rows > 0) {
        records += rows;
        physical += rows;
        rowHint = rows;
        return builder.build();
      }
      if (eof) return null; // only empty lines remained
      size = next(size, end);
    }
  }

  /** Data records returned so far. */
  public long records() {
    return records;
  }

  /** Stream bytes consumed into returned batches (BOM and header included). */
  public long position() {
    return carryOffset;
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    carry = EMPTY;
    carryLength = 0;
    input.close();
  }

  private CsvPlan.Binding bind() throws IOException {
    if (closed) throw new IllegalStateException("CSV reader closed");
    CsvPlan.Binding b = binding;
    if (b != null) return b;
    int size = batchBytes;
    for (; ; ) {
      byte[] buffer = fill(size);
      int from = 0;
      if (!bomChecked) {
        if (filled < 3 && !eof) {
          consume(buffer, 0);
          size = Math.max(size * 2, 3);
          continue;
        }
        bomChecked = true;
        if (CsvPlan.hasBom(buffer, filled)) from = 3;
      }
      scanner.recordBase = 0;
      scanner.byteBase = carryOffset;
      CsvPlan.Binder binder = new CsvPlan.Binder(plan);
      boolean bound = binder.bind(scanner, buffer, from, filled, eof);
      int end = Math.max(binder.end, from);
      if (!bound) checkIncomplete(end);
      consume(buffer, end);
      if (bound) {
        b = Objects.requireNonNull(binder.binding);
        binding = b;
        physical = consumed = binder.consumed;
        return b;
      }
      size = next(size, end - from);
    }
  }

  /** An incomplete record longer than maxRecordBytes plus a pending CR can never complete. */
  private void checkIncomplete(int end) {
    if (!eof && filled - end > limits.maxRecordBytes() + 1) throw CsvScanner.limit("maxRecordBytes");
  }

  /** Next buffer size: a fresh batch after progress, else double for the incomplete record. */
  private int next(int size, int progress) {
    if (progress > 0) return carryLength + batchBytes;
    return Math.max(carryLength + 1, Math.min(size * 2, limits.maxRecordBytes() + 2));
  }

  /** A fresh buffer holding the carried bytes, filled until full or end of stream. */
  private byte[] fill(int size) throws IOException {
    byte[] buffer = new byte[size];
    System.arraycopy(carry, carryFrom, buffer, 0, carryLength);
    int n = carryLength;
    while (n < size && !eof) {
      int read = input.read(buffer, n, size - n);
      if (read < 0) eof = true;
      else n += read;
    }
    filled = n;
    // A short final buffer is trimmed so a batch does not retain unused capacity.
    if (eof && size - n > size / 8) buffer = Arrays.copyOf(buffer, n);
    return buffer;
  }

  private void consume(byte[] buffer, int end) {
    carry = buffer;
    carryFrom = end;
    carryLength = filled - end;
    carryOffset += end;
  }
}
