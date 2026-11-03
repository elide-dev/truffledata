package dev.elide.truffledata;

/** Structural budgets apply before storage allocation. Payload limit counts UTF-16 string bytes. */
public record Limits(int maxRows, int maxColumns, long maxCells, long maxStringBytes) {
  public static final Limits DEFAULT = new Limits(1024, 256, 262144, 8 * 1024 * 1024);

  public Limits {
    if (maxRows <= 0 || maxColumns <= 0 || maxCells <= 0 || maxStringBytes < 0)
      throw new IllegalArgumentException("Invalid limits");
  }

  public void check(int rows, int columns) {
    if (rows < 0
        || columns < 0
        || rows > maxRows
        || columns > maxColumns
        || (long) rows * columns > maxCells)
      throw new ContractViolation("Batch structural budget exceeded");
  }
}
