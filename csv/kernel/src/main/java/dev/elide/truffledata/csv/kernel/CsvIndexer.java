package dev.elide.truffledata.csv.kernel;

/**
 * A structural indexer bound to one format (see {@link CsvIndex} for the contract). Not
 * thread-safe: an indexer holds the carry between calls over one stream.
 */
public abstract class CsvIndexer {
  protected final byte delimiter;
  protected final byte quote;
  protected final boolean quoting;
  /** Carry between calls ({@link CsvIndex#CARRY_MASK} bits); 0 at a stream start. */
  protected long carry;

  protected CsvIndexer(byte delimiter, byte quote, boolean quoting) {
    CsvIndex.check(delimiter, quote, quoting);
    this.delimiter = delimiter;
    this.quote = quote;
    this.quoting = quoting;
  }

  /** A short kernel name for reports, such as {@code java-swar} or {@code native-avx512}. */
  public abstract String name();

  /**
   * Indexes {@code input[from, from + length)}, which callers size with {@link
   * CsvIndex#indexable}, and returns the number of entries written to {@code out[0..)}. {@code
   * out} must hold at least {@code length} entries; entries past the result are unspecified.
   */
  public abstract int index(byte[] input, int from, int length, int[] out);

  public final long carry() {
    return carry;
  }

  /** Restarts at a stream start (or restores a saved carry). */
  public final void carry(long carry) {
    if ((carry & ~CsvIndex.CARRY_MASK) != 0) throw new IllegalArgumentException("CSV index carry");
    this.carry = carry;
  }
}
