package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.yaml.events.YamlEventSource;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Pure-Java YAML kernel entry point ({@code truffledata-yaml-java}): the same {@link YamlSession},
 * documents, streams, typed plans and policies as the native decoder, with no native library, on a
 * JVM or in a native image built without the static YAML archive. Parsing goes through a {@link
 * YamlEventSource}: the one on the class path ({@code truffledata-yaml-snakeyaml}) or one given
 * explicitly. {@link YamlSession#open(YamlOptions)} selects this kernel automatically when no
 * archive is linked.
 */
public final class YamlJava {
  private YamlJava() {}

  @TruffleBoundary
  public static YamlSession openSession() {
    return openSession(YamlOptions.DEFAULT);
  }

  /** A session over the event source found on the class path. */
  @TruffleBoundary
  public static YamlSession openSession(YamlOptions options) {
    return openSession(options, source());
  }

  /** A session over {@code source}, which every decode of the session uses. */
  public static YamlSession openSession(YamlOptions options, YamlEventSource source) {
    Objects.requireNonNull(options);
    Objects.requireNonNull(source);
    return new YamlSession(options, new JavaSessionBackend(options, source));
  }

  /** The event source on the class path; throws {@link IllegalStateException} when none is. */
  public static YamlEventSource source() {
    YamlEventSource found = Sources.SOURCE;
    if (found == null)
      throw new IllegalStateException(
          "No YAML event source for the Java kernel: add truffledata-yaml-snakeyaml");
    return found;
  }

  private static final class Sources {
    static final @org.jspecify.annotations.Nullable YamlEventSource SOURCE = find();

    private static @org.jspecify.annotations.Nullable YamlEventSource find() {
      for (YamlEventSource source : ServiceLoader.load(YamlEventSource.class, YamlJava.class.getClassLoader()))
        return source;
      return null;
    }
  }
}
