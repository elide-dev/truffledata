package dev.elide.truffledata.jdbc;

/** Per-query driver policy. No option authorizes changing a borrowed connection's transaction. */
public record JdbcQueryOptions(int fetchSize, int timeoutSeconds, boolean requireStreaming) {
  public static final JdbcQueryOptions DEFAULT = new JdbcQueryOptions(256, 30, true);

  public JdbcQueryOptions {
    if (fetchSize <= 0 || timeoutSeconds <= 0)
      throw new IllegalArgumentException("Fetch size and query timeout must be positive");
  }
}
