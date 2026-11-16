package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.TapeSession;
import dev.elide.truffledata.yaml.generated.YamlConstants.Status;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Status codes of the YAML C ABI (yaml.seam {@code enum Status}) and their exceptions, shared by
 * every backend (the static archive, the JVM FFM library and the Java kernel) so each throws the
 * same types and messages.
 */
final class YamlStatus {
  private YamlStatus() {}

  static void checkStatus(int status) {
    switch (status) {
      case Status.OK -> {}
      case Status.ARGUMENT, Status.SYNTAX, Status.CONTRACT ->
          throw new IllegalArgumentException("Invalid YAML input or contract: " + status);
      case Status.LIMIT -> throw new IllegalArgumentException("YAML resource limit");
      case Status.ALLOCATION -> throw new OutOfMemoryError("YAML native allocation");
      case Status.MULTIPLE_DOCUMENTS ->
          throw new IllegalArgumentException("Multiple YAML documents; use a stream");
      case Status.STREAM_TRUNCATED -> throw new IllegalArgumentException("Truncated YAML document");
      default -> throw new IllegalStateException("YAML native status " + status);
    }
  }

  /** Whether a failure with this status publishes a message, line and column in its result. */
  static boolean detailed(int status) {
    return status == Status.LIMIT
        || status == Status.SYNTAX
        || status == Status.CONTRACT
        || status == Status.MULTIPLE_DOCUMENTS;
  }

  static void checkDetailed(int status, String detail, long line, long column) {
    if (status == Status.MULTIPLE_DOCUMENTS) checkStatus(status);
    String prefix =
        status == Status.LIMIT
            ? "YAML resource limit: "
            : status == Status.SYNTAX ? "YAML syntax error: " : "YAML ";
    throw new IllegalArgumentException(prefix + detail + " at line " + line + ", column " + column);
  }

  // TapeSession supplies UTF-16 and Latin-1 for TruffleString input. Native YAML accepts BOM
  // encodings; prefix UTF-16 rather than replacing malformed code units through Java decoding.
  static byte[] utf8(byte[] data, int offset, int length, int encoding) {
    if (encoding == TapeSession.UTF8)
      return offset == 0 && length == data.length
          ? data
          : Arrays.copyOfRange(data, offset, offset + length);
    if (encoding == TapeSession.LATIN1)
      return new String(data, offset, length, StandardCharsets.ISO_8859_1)
          .getBytes(StandardCharsets.UTF_8);
    byte[] bom = new byte[Math.addExact(length, 2)];
    boolean little = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    bom[0] = (byte) (little ? 0xff : 0xfe);
    bom[1] = (byte) (little ? 0xfe : 0xff);
    System.arraycopy(data, offset, bom, 2, length);
    return bom;
  }
}
