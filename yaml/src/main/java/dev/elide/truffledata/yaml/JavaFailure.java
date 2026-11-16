package dev.elide.truffledata.yaml;

import dev.elide.truffledata.yaml.generated.YamlConstants;
import dev.elide.truffledata.yaml.generated.YamlConstants.Status;

/**
 * A Java-kernel decode failure carrying the native decoder's status, message and position, so
 * {@link #raise()} throws exactly what the native backends throw for the same status ({@link
 * YamlStatus#checkDetailed}, {@link YamlStatus#checkStatus}).
 */
final class JavaFailure extends RuntimeException {
  private static final long serialVersionUID = 1L;

  // yaml.seam's status codes, as the native decoder reports them.
  static final int LIMIT = Status.LIMIT,
      SYNTAX = Status.SYNTAX,
      INTERNAL = Status.INTERNAL,
      CONTRACT = Status.CONTRACT,
      MULTIPLE = Status.MULTIPLE_DOCUMENTS;

  static final int MESSAGE_BYTES = YamlConstants.ERROR_MESSAGE_CAPACITY - 1;

  final int status;
  final String detail;
  final long line, column;
  // Raised by the event source itself (the parser), as rapidyaml's error callbacks mark theirs.
  final boolean parser;

  JavaFailure(int status, String detail, long line, long column, boolean parser) {
    super(detail, null, false, false);
    this.status = status;
    // Native messages are fixed NUL-terminated buffers: at most capacity - 1 bytes reach Java.
    byte[] utf8 = detail.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    this.detail = utf8.length <= MESSAGE_BYTES ? detail
        : new String(utf8, 0, MESSAGE_BYTES, java.nio.charset.StandardCharsets.UTF_8);
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

  /** Throws the exception the native backends throw for this status. */
  RuntimeException raise() {
    if (YamlStatus.detailed(status))
      YamlStatus.checkDetailed(status, detail, line, column);
    YamlStatus.checkStatus(status);
    throw new IllegalStateException("YAML status " + status);
  }
}
