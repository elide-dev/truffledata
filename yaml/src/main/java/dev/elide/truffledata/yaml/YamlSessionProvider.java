package dev.elide.truffledata.yaml;

/**
 * A YAML session implementation found through {@link java.util.ServiceLoader} when {@link
 * YamlSession#open(YamlOptions)} has no native archive to use: on a JVM, or in a native image built
 * without the static YAML archive. {@code truffledata-yaml-java} provides the pure-Java kernel.
 */
public interface YamlSessionProvider {
  /** A new session confined to the calling thread. */
  YamlSession open(YamlOptions options);
}
