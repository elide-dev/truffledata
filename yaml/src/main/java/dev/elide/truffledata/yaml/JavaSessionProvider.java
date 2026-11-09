package dev.elide.truffledata.yaml;

/** The Java kernel as {@link YamlSession#open(YamlOptions)}'s fallback when no archive is linked. */
public final class JavaSessionProvider implements YamlSessionProvider {
  public JavaSessionProvider() {}

  @Override
  public YamlSession open(YamlOptions options) {
    return YamlJava.openSession(options);
  }
}
