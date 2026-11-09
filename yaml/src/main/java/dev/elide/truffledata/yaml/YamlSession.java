package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TapeSession;
import dev.elide.truffledata.tape.TypedLayout;
import java.util.Map;
import java.util.Objects;
import org.graalvm.nativeimage.ImageInfo;

/**
 * Thread-confined YAML parser, arena, and borrowed-document lifetime: the native decoder (static
 * archive in a native image, {@code yaml_shared} through {@link YamlJvm} on a JVM) or the pure-Java
 * kernel ({@code truffledata-yaml-java}), which implement the same semantics.
 */
public final class YamlSession extends TapeSession<YamlMessage> {
  abstract static class Backend extends TapeSession.Backend<YamlMessage> {
    abstract YamlPlan compile(TypedLayout layout, YamlOptions options);
  }

  private final YamlOptions options;

  YamlSession(YamlOptions options, Backend backend) {
    super("YAML", options.limits().maxInputBytes(), backend);
    this.options = options;
  }

  @TruffleBoundary
  public static YamlSession open() {
    return open(YamlOptions.DEFAULT);
  }

  /**
   * A session on the best backend of this runtime: the linked static archive in a native image,
   * otherwise the Java kernel when {@code truffledata-yaml-java} is on the class path. Throws
   * {@link UnsupportedOperationException} when neither is available.
   */
  @TruffleBoundary
  public static YamlSession open(YamlOptions options) {
    Objects.requireNonNull(options);
    if (ImageInfo.inImageRuntimeCode() && YamlLinkage.NATIVE) return openNative(options);
    YamlSessionProvider kernel = YamlLinkage.kernel();
    if (kernel != null) return kernel.open(options);
    return openNative(options);
  }

  /** The static-archive session; the build-time constant keeps it out of archive-less images. */
  static YamlSession openNative(YamlOptions options) {
    if (ImageInfo.inImageRuntimeCode() && !YamlLinkage.NATIVE) throw YamlLinkage.unlinked();
    YamlSvm.requireRuntime();
    return new YamlSession(options, new SvmSessionBackend(options));
  }

  /** Compiles an independent native plan using this session's policies and resource limits. */
  @TruffleBoundary
  public YamlPlan compile(Schema root, Map<String, Schema> recordTypes) {
    Backend open = (Backend) check();
    return open.compile(new TypedLayout(root, recordTypes, YamlPlan.FORMAT), options);
  }

  public YamlOptions options() {
    return options;
  }

  @Override
  protected boolean allowsEmptyInput() {
    return true;
  }

  @Override
  protected void checkStatus(int status) {
    YamlSvm.checkStatus(status);
  }

  @TruffleBoundary
  public YamlStream stream(YamlStreamOptions options) {
    Objects.requireNonNull(options);
    return register(
        new YamlStream(
            this, createStream(options.maxDocumentBytes(), options.bufferBytes()), options));
  }

  /** A complete bounded input buffer containing zero or more YAML documents. */
  @TruffleBoundary
  public YamlStream documents(byte[] input) {
    Objects.requireNonNull(input);
    if (input.length > options.limits().maxInputBytes() || input.length > Integer.MAX_VALUE / 2)
      throw new IllegalArgumentException("YAML input limit");
    int capacity = Math.max(1, input.length);
    YamlStream stream = stream(new YamlStreamOptions(capacity, capacity));
    try {
      if (stream.feed(input) != input.length)
        throw new IllegalStateException("YAML complete buffer was not accepted");
      stream.finish();
      return stream;
    } catch (RuntimeException | Error failure) {
      stream.close();
      throw failure;
    }
  }
}
