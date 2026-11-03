package dev.elide.truffledata.jdbc;

import dev.elide.truffledata.Limits;
import dev.elide.truffledata.PayloadBudget;

/**
 * Cumulative ingestion limits for one query, including nested cells and UTF-16 string bytes. These
 * bound accepted logical payload, not driver buffers, JVM object overhead or mutable views. Counts
 * never reset on batch advancement or row release. The connection remains borrowed.
 */
public record JdbcConsumptionLimits(long maxRows, long maxCells, long maxStringBytes) {
  public static final JdbcConsumptionLimits DEFAULT =
      new JdbcConsumptionLimits(1_000_000, 64_000_000, 256L * 1024 * 1024);

  public JdbcConsumptionLimits {
    if (maxRows <= 0 || maxCells <= 0 || maxStringBytes < 0)
      throw new IllegalArgumentException("Invalid cumulative query limits");
  }

  PayloadBudget budget() {
    return new PayloadBudget(
        new Limits(Integer.MAX_VALUE, Integer.MAX_VALUE, maxCells, maxStringBytes));
  }
}
