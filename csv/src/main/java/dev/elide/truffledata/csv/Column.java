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

  Column(
      long @Nullable [] spans,
      long @Nullable [] longs,
      double @Nullable [] doubles,
      boolean @Nullable [] booleans,
      byte @Nullable [] states) {
    this.spans = spans;
    this.longs = longs;
    this.doubles = doubles;
    this.booleans = booleans;
    this.states = states;
  }

  byte state(int row) {
    byte[] s = states;
    return s == null ? PRESENT : s[row];
  }
}
