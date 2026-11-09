package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Per-snapshot/batch accounting, including nested array elements and UTF-16 string payloads. */
public final class PayloadBudget {
  private static final boolean STRICT =
      PayloadBudget.class.desiredAssertionStatus() && Boolean.getBoolean("truffledata.strict");
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

  void accept(Object value, int depth) {
    if (depth > 128) throw new ContractViolation("Value nesting budget exceeded");
    if (value instanceof String text) {
      long bytes = 2L * text.length();
      stringBytes(bytes);
    } else if (value instanceof OwnedArray array) {
      cells(array.size());
      if (!array.isBytes() || depth == 128)
        for (int i = 0; i < array.size(); i++) accept(array.get(i), depth + 1);
    } else if (value instanceof AbstractRow row) {
      cells(row.schema().size());
      SnapshotStorage storage =
          row instanceof SnapshotStorage snapshot ? snapshot : row.payloadStorage();
      if (storage != null) {
        if (depth == 128 && row.schema().size() != 0)
          throw new ContractViolation("Value nesting budget exceeded");
        if (storage.schema() != row.schema())
          throw new ContractViolation("Payload storage schema differs from row schema");
        if (STRICT && storage != row) checkStorage(row, storage);
        if (storage instanceof StaticLayout.Record record) record.acceptPayload(this, depth + 1);
        else ((Batch.Row) storage).acceptPayload(this, depth + 1);
        return;
      }
      DataLibrary data = DataLibrary.factory().getUncached();
      for (int i = 0; i < row.schema().size(); i++) accept(data.read(row, i), depth + 1);
    }
  }

  @TruffleBoundary
  private static void checkStorage(AbstractRow row, SnapshotStorage storage) {
    DataLibrary data = DataLibrary.factory().getUncached();
    for (int i = 0; i < row.schema().size(); i++)
      if (!Objects.equals(data.read(row, i), data.read(storage, i)))
        throw new AssertionError("Payload storage differs from DataLibrary values");
  }
}
