package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.tape.TapeSession;
import dev.elide.truffledata.yaml.generated.Yaml;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.c.type.CTypeConversion;

/** Static-image YAML entry point. Native libraries are linked when building the image. */
public final class YamlSvm {
  private YamlSvm() {}

  private static volatile boolean verified;

  static void requireRuntime() {
    if (!ImageInfo.inImageRuntimeCode())
      throw new UnsupportedOperationException("Static YAML requires SVM");
    if (!YamlLinkage.NATIVE) throw YamlLinkage.unlinked();
    if (!verified) {
      if (!CTypeConversion.toJavaString(Yaml.fingerprint()).equals(Yaml.ABI_FINGERPRINT))
        throw new IllegalStateException("Myna/YAML ABI fingerprint mismatch");
      verified = true;
    }
  }

  static void checkStatus(int status) {
    switch (status) {
      case 0 -> {}
      case 1, 4, 6 ->
          throw new IllegalArgumentException("Invalid YAML input or contract: " + status);
      case 2 -> throw new IllegalArgumentException("YAML resource limit");
      case 3 -> throw new OutOfMemoryError("YAML native allocation");
      case 7 -> throw new IllegalArgumentException("Multiple YAML documents; use a stream");
      case 9 -> throw new IllegalArgumentException("Truncated YAML document");
      default -> throw new IllegalStateException("YAML native status " + status);
    }
  }

  static void checkDetailed(int status, String detail, long line, long column) {
    if (status == 7) checkStatus(status);
    String prefix =
        status == 2 ? "YAML resource limit: " : status == 4 ? "YAML syntax error: " : "YAML ";
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

  @TruffleBoundary
  public static YamlSession open() {
    return YamlSession.openNative(YamlOptions.DEFAULT);
  }

  @TruffleBoundary
  public static YamlMessage decode(byte[] input) {
    return decode(input, YamlOptions.DEFAULT);
  }

  @TruffleBoundary
  public static YamlMessage decode(byte[] input, YamlOptions options) {
    try (YamlSession session = YamlSession.openNative(options)) {
      return session.decode(input);
    }
  }
}
