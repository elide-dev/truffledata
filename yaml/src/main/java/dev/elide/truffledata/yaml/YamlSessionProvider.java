package dev.elide.truffledata.yaml;

/**
 * A YAML session backend found through {@link java.util.ServiceLoader} by {@link
 * YamlSession#open(YamlOptions)}: {@code truffledata-yaml-native} (the static archive, in a native
 * image) and {@code truffledata-yaml-java} (the pure-Java kernel). Among the providers {@linkplain
 * #available() available} in this runtime, the one with the highest {@linkplain #priority()
 * priority} opens the session.
 */
public interface YamlSessionProvider {
  /** Whether this backend works in the current runtime. */
  default boolean available() {
    return true;
  }

  /** Preference among available providers; the native archive outranks the Java kernel. */
  default int priority() {
    return 0;
  }

  /** A new session confined to the calling thread. */
  YamlSession open(YamlOptions options);
}
