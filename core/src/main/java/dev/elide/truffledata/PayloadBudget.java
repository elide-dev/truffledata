package dev.elide.truffledata;

import org.jspecify.annotations.Nullable;

/** Per-snapshot/batch accounting, including nested array elements and UTF-16 string payloads. */
public final class PayloadBudget {
  private final Limits limits;
  private final @Nullable PayloadBudget parent;
  private long cells;
  private long stringBytes;

  public PayloadBudget(Limits limits) {
    this(limits, null);
  }

  /** Thread-confined cumulative parent; failed snapshots do not refund accepted payloads. */
  public PayloadBudget(Limits limits, @Nullable PayloadBudget parent) {
    this.limits = limits;
    this.parent = parent;
  }

  public void cells(long count) {
    if (count < 0 || count > limits.maxCells() - cells)
      throw new ContractViolation("Nested cell budget exceeded");
    if (parent != null) parent.cells(count);
    cells += count;
  }

  public void accept(Object value) {
    accept(value, 0);
  }

  private void stringBytes(long count) {
    if (count > limits.maxStringBytes() - stringBytes)
      throw new ContractViolation("String payload budget exceeded");
    if (parent != null) parent.stringBytes(count);
    stringBytes += count;
  }

  private void accept(Object value, int depth) {
    if (depth > 128) throw new ContractViolation("Value nesting budget exceeded");
    if (value instanceof String text) {
      long bytes = 2L * text.length();
      stringBytes(bytes);
    } else if (value instanceof DataArray array) {
      cells(array.size());
      if (!array.isBytes() || depth == 128)
        for (int i = 0; i < array.size(); i++) accept(array.get(i), depth + 1);
    } else if (value instanceof AbstractRow row) {
      cells(row.schema().size());
      DataLibrary data = DataLibrary.factory().getUncached();
      for (int i = 0; i < row.schema().size(); i++) accept(data.read(row, i), depth + 1);
    }
  }
}
