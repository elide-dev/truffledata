package dev.elide.truffledata.csv.kernel;

/** Byte-at-a-time reference indexer: the oracle the other kernels are tested against. */
public final class ScalarIndexer extends CsvIndexer {
  public ScalarIndexer(byte delimiter, byte quote, boolean quoting) {
    super(delimiter, quote, quoting);
  }

  @Override
  public String name() {
    return "java-scalar";
  }

  @Override
  public int index(byte[] input, int from, int length, int[] out) {
    CsvIndex.checkCall(input, from, length, out);
    long c = carry;
    boolean inside = (c & CsvIndex.CARRY_INSIDE) != 0;
    boolean closing = (c & CsvIndex.CARRY_CLOSING) != 0;
    boolean fq = (c & CsvIndex.CARRY_QUOTED) != 0;
    boolean fe = (c & CsvIndex.CARRY_ESCAPED) != 0;
    boolean fu = (c & CsvIndex.CARRY_UNQUOTED) != 0;
    int count = 0;
    for (int i = 0; i < length; i++) {
      byte b = input[from + i];
      if (quoting && b == quote) {
        inside = !inside;
        if (inside && closing) fe = true;
        closing = !inside;
        fq = true;
        continue;
      }
      closing = false;
      if (inside) continue;
      if (b == delimiter || b == '\n' || b == '\r') {
        out[count++] =
            i
                | (fq ? CsvIndex.QUOTED : 0)
                | (fe ? CsvIndex.ESCAPED : 0)
                | (fq && fu ? CsvIndex.UNQUOTED : 0);
        fq = fe = fu = false;
      } else {
        fu = true;
      }
    }
    carry =
        (inside ? CsvIndex.CARRY_INSIDE : 0)
            | (closing ? CsvIndex.CARRY_CLOSING : 0)
            | (fq ? CsvIndex.CARRY_QUOTED : 0)
            | (fe ? CsvIndex.CARRY_ESCAPED : 0)
            | (fu ? CsvIndex.CARRY_UNQUOTED : 0);
    return count;
  }
}
