package dev.elide.truffledata.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.jspecify.annotations.Nullable;

/**
 * Single-execution cancellation requested from any thread. Driver cancellation is best effort; the
 * consuming thread must still close its cursor. No connection is closed or transaction changed. Do
 * not call back into cursor operations from an adapter cancellation hook.
 */
public final class JdbcCancellation {
  private volatile boolean cancelled;
  private boolean used;
  private boolean finished;
  private boolean active;
  private @Nullable PreparedStatement statement;
  private @Nullable JdbcAdapter adapter;

  public boolean isCancelled() {
    return cancelled;
  }

  /**
   * Signal cancellation and, during execution/fetch, ask the driver to interrupt. May block in the
   * driver. Cleanup waits for this call to finish so cancellation cannot target a reused
   * connection. Idle cancellation does not call the driver; the next cursor read fails before
   * further fetching.
   */
  public synchronized void cancel() throws SQLException {
    if (finished || cancelled) return;
    cancelled = true;
    var target = statement;
    var integration = adapter;
    if (active && target != null && integration != null) integration.cancel(target);
  }

  synchronized void attach(PreparedStatement target, JdbcAdapter integration) throws SQLException {
    if (used) throw new IllegalStateException("Cancellation control already used");
    used = true;
    check();
    statement = target;
    adapter = integration;
  }

  synchronized void enter() throws SQLException {
    check();
    if (finished) throw new IllegalStateException("Cancellation control is finished");
    if (active) throw new IllegalStateException("Query operation already active");
    active = true;
  }

  synchronized void leave() {
    active = false;
  }

  synchronized void finish() {
    finished = true;
    active = false;
    statement = null;
    adapter = null;
  }

  void check() throws SQLException {
    if (cancelled) throw new SQLException("Query cancelled", "57014");
  }
}
