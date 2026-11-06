package dev.elide.truffledata.csv.kernel;

/**
 * The block step shared by the bitmap kernels (simdjson stage 1): from one 64-byte block's
 * separator-candidate and quote bitmaps to index entries. Mirrors {@code csv/native/stage.h}.
 */
public final class BlockStep {
  private long insideCarry; // all ones when the previous block ended inside quotes
  private long closingCarry; // 1 when the previous block's last byte was a closing quote
  private boolean fq, fe, fu; // pending flags of the field continuing into this block

  public void load(long carry) {
    insideCarry = (carry & CsvIndex.CARRY_INSIDE) != 0 ? -1L : 0;
    closingCarry = (carry & CsvIndex.CARRY_CLOSING) != 0 ? 1 : 0;
    fq = (carry & CsvIndex.CARRY_QUOTED) != 0;
    fe = (carry & CsvIndex.CARRY_ESCAPED) != 0;
    fu = (carry & CsvIndex.CARRY_UNQUOTED) != 0;
  }

  public long carry() {
    return (insideCarry != 0 ? CsvIndex.CARRY_INSIDE : 0)
        | (closingCarry != 0 ? CsvIndex.CARRY_CLOSING : 0)
        | (fq ? CsvIndex.CARRY_QUOTED : 0)
        | (fe ? CsvIndex.CARRY_ESCAPED : 0)
        | (fu ? CsvIndex.CARRY_UNQUOTED : 0);
  }

  public static long prefixXor(long x) {
    x ^= x << 1;
    x ^= x << 2;
    x ^= x << 4;
    x ^= x << 8;
    x ^= x << 16;
    x ^= x << 32;
    return x;
  }

  /**
   * One block. {@code sep} marks delimiter, LF and CR bytes, {@code quote} quote bytes and
   * {@code valid} the real bytes (all ones except in a padded tail). Writes entries at {@code
   * out[n..)} with offsets {@code base + bit} and returns the new count.
   */
  public int block(long sep, long quote, long valid, int base, int[] out, int n) {
    if (quote == 0 && !fq) {
      // Nothing is inside quotes (an open quote would have set fq): every candidate is a
      // separator and every flag is zero.
      long unq = ~sep & valid;
      closingCarry = 0;
      if (sep == 0) {
        fu |= unq != 0;
        return n;
      }
      int last = 63 - Long.numberOfLeadingZeros(sep);
      fe = false;
      fu = (unq >>> last >>> 1) != 0;
      while (sep != 0) {
        out[n++] = base + Long.numberOfTrailingZeros(sep);
        sep &= sep - 1;
      }
      return n;
    }
    long inside = prefixXor(quote) ^ insideCarry;
    insideCarry = inside >> 63;
    long seps = sep & ~inside;
    long closing = quote & ~inside;
    long reopen = quote & inside & ((closing << 1) | closingCarry);
    closingCarry = (closing >>> (63 - Long.numberOfLeadingZeros(valid))) & 1;
    long unq = ~inside & ~quote & ~sep & valid;
    long seen = 0; // bits up to and including the previous separator
    while (seps != 0) {
      long bit = seps & -seps;
      long m = (bit - 1) & ~seen;
      boolean q = fq || (quote & m) != 0;
      boolean e = fe || (reopen & m) != 0;
      boolean u = fu || (unq & m) != 0;
      out[n++] =
          (base + Long.numberOfTrailingZeros(bit))
              | (q ? CsvIndex.QUOTED : 0)
              | (e ? CsvIndex.ESCAPED : 0)
              | (q && u ? CsvIndex.UNQUOTED : 0);
      fq = fe = fu = false;
      seen = bit | (bit - 1);
      seps ^= bit;
    }
    long tail = valid & ~seen;
    fq |= (quote & tail) != 0;
    fe |= (reopen & tail) != 0;
    fu |= (unq & tail) != 0;
    return n;
  }

  /** The tail padding byte: neither a separator nor the quote. */
  public static byte pad(byte delimiter, byte quote, boolean quoting) {
    byte pad = 'a';
    while (pad == delimiter || (quoting && pad == quote)) pad++;
    return pad;
  }
}
