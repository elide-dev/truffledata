package dev.elide.truffledata.yaml;

import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;

/**
 * Which YAML backend this runtime has. In a native image the static archive is linked exactly when
 * its generated bindings ({@code truffledata-yaml-bindings}, whose {@code @CLibrary} requires the
 * archive) are on the image class path; the jar's native-image.properties initializes this class at
 * build time, so {@link #NATIVE} is a constant and an image without the bindings never reaches the
 * {@code @CFunction} backend. Images without the archive must therefore leave the bindings jar out
 * and add {@code truffledata-yaml-java} with an event source.
 */
final class YamlLinkage {
  private YamlLinkage() {}

  static final boolean NATIVE = bindings();

  private static boolean bindings() {
    try {
      Class.forName(
          "dev.elide.truffledata.yaml.generated.Yaml", false, YamlLinkage.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError absent) {
      return false;
    }
  }

  static UnsupportedOperationException unlinked() {
    return new UnsupportedOperationException(
        "The static YAML archive is not linked into this image: add truffledata-yaml-java and an"
            + " event source (truffledata-yaml-snakeyaml) for the Java kernel");
  }

  /** The Java kernel (or another provider) on the class path, or null; looked up once. */
  static @Nullable YamlSessionProvider kernel() {
    return Kernel.PROVIDER;
  }

  private static final class Kernel {
    static final @Nullable YamlSessionProvider PROVIDER = find();

    private static @Nullable YamlSessionProvider find() {
      for (YamlSessionProvider provider :
          ServiceLoader.load(YamlSessionProvider.class, YamlLinkage.class.getClassLoader()))
        return provider;
      return null;
    }
  }
}
