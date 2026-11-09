package dev.elide.truffledata.yaml;

/**
 * A Java-kernel decode failure carrying the native decoder's status, message and position, so
 * {@link #raise()} throws exactly what the native backends throw for the same status ({@link
 * YamlStatus#checkDetailed}, {@link YamlStatus#checkStatus}).
 */
final class JavaFailure extends RuntimeException {
  private static final long serialVersionUID = 1L;

  static final int LIMIT = 2, SYNTAX = 4, INTERNAL = 5, CONTRACT = 6, MULTIPLE = 7;

  final int status;
  final String detail;
  final long line, column;
  // Raised by the event source itself (the parser), as rapidyaml's error callbacks mark theirs.
  final boolean parser;

  JavaFailure(int status, String detail, long line, long column, boolean parser) {
    super(detail, null, false, false);
    this.status = status;
    // Native messages are fixed 256-byte buffers: at most 255 bytes reach Java.
    byte[] utf8 = detail.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    this.detail = utf8.length <= 255 ? detail
        : new String(utf8, 0, 255, java.nio.charset.StandardCharsets.UTF_8);
    this.line = line;
    this.column = column;
    this.parser = parser;
  }

  static JavaFailure syntax(String detail, long line, long column) {
    return new JavaFailure(SYNTAX, detail, line, column, false);
  }

  /** The native message "YAML resource limit: <which>" (the session prefixes it once more). */
  static JavaFailure limit(String which, long line, long column) {
    return new JavaFailure(LIMIT, "YAML resource limit: " + which, line, column, false);
  }

  static JavaFailure internal(String detail) {
    return new JavaFailure(INTERNAL, detail, 0, 0, false);
  }

  /** Throws the exception the native backends throw for this status. */
  RuntimeException raise() {
    if (status == LIMIT || status == SYNTAX || status == CONTRACT || status == MULTIPLE)
      YamlStatus.checkDetailed(status, detail, line, column);
    YamlStatus.checkStatus(status);
    throw new IllegalStateException("YAML status " + status);
  }
}
