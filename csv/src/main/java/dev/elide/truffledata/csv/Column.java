package dev.elide.truffledata.csv;

import org.jspecify.annotations.Nullable;

/**
 * One immutable schema field of a batch, mirroring core {@code Batch.Column}. Exactly one value
 * array is set: {@code spans} (STRING, packed {@link CsvScanner} spans into the batch input),
 * {@code longs} (INT64), {@code doubles} (FLOAT64) or {@code booleans} (BOOLEAN). {@code states} is
 * present only for nullable or optional fields: 0 present, 1 null, 2 missing.
 */
final class Column {
  static final byte PRESENT = 0;
  static final byte NULL = 1;
  static final byte MISSING = 2;

  final long @Nullable [] spans;
  final long @Nullable [] longs;
  final double @Nullable [] doubles;
  final boolean @Nullable [] booleans;
  final byte @Nullable [] states;

  /**
   * The value array when every row is present ({@code states} is null) and the batch is on the
   * heap, else an empty array: a row read's fast path is one load and a length test ({@link
   * CsvRow#getLong} and siblings), and every other read (nullable, optional, wrong kind, mapped)
   * fails the test and takes the checked path. Never null: Graal's speculative guard movement
   * hoisted null checks of kind-specific arrays above the caller's kind dispatch and deoptimized
   * on every compilation.
   */
  final long[] presentSpans;

  final long[] presentLongs;
  final double[] presentDoubles;
  final boolean[] presentBooleans;

  private static final long[] NO_LONGS = new long[0];
  private static final double[] NO_DOUBLES = new double[0];
  private static final boolean[] NO_BOOLEANS = new boolean[0];

  Column(
      long @Nullable [] spans,
      long @Nullable [] longs,
      double @Nullable [] doubles,
      boolean @Nullable [] booleans,
      byte @Nullable [] states,
      boolean heap) {
    this.spans = spans;
    this.longs = longs;
    this.doubles = doubles;
    this.booleans = booleans;
    this.states = states;
    boolean dense = states == null && heap;
    presentSpans = dense && spans != null ? spans : NO_LONGS;
    presentLongs = dense && longs != null ? longs : NO_LONGS;
    presentDoubles = dense && doubles != null ? doubles : NO_DOUBLES;
    presentBooleans = dense && booleans != null ? booleans : NO_BOOLEANS;
  }

  byte state(int row) {
    byte[] s = states;
    return s == null ? PRESENT : s[row];
  }
}
