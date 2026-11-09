package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TapeSession;
import dev.elide.truffledata.tape.TypedLayout;
import java.util.Map;
import java.util.Objects;

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
   * A session on the best backend of this runtime: the static archive in a native image that
   * carries {@code truffledata-yaml-native}, otherwise the Java kernel ({@code
   * truffledata-yaml-java}). Throws {@link UnsupportedOperationException} when no backend is
   * available.
   */
  @TruffleBoundary
  public static YamlSession open(YamlOptions options) {
    Objects.requireNonNull(options);
    YamlSessionProvider best = null;
    for (YamlSessionProvider provider : Providers.ALL)
      if (provider.available() && (best == null || provider.priority() > best.priority())) best = provider;
    if (best == null)
      throw new UnsupportedOperationException(
          "No YAML backend: add truffledata-yaml-native (a native image linking the static YAML"
              + " archive) or truffledata-yaml-snakeyaml (the Java kernel); on a JVM,"
              + " YamlJvm.openSession takes the yaml_shared library");
    return best.open(options);
  }

  /** The backends on the class path, looked up once. */
  private static final class Providers {
    static final java.util.List<YamlSessionProvider> ALL = find();

    private static java.util.List<YamlSessionProvider> find() {
      java.util.List<YamlSessionProvider> found = new java.util.ArrayList<>();
      for (YamlSessionProvider provider :
          java.util.ServiceLoader.load(YamlSessionProvider.class, YamlSession.class.getClassLoader()))
        found.add(provider);
      return java.util.List.copyOf(found);
    }
  }

  /** Compiles an independent plan using this session's policies and resource limits. */
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
    YamlStatus.checkStatus(status);
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
