package dev.elide.truffledata.yaml;

import org.graalvm.nativeimage.ImageInfo;

/** The static-archive backend for {@link YamlSession#open(YamlOptions)}, in native images only. */
public final class SvmSessionProvider implements YamlSessionProvider {
  public SvmSessionProvider() {}

  @Override
  public boolean available() {
    return ImageInfo.inImageRuntimeCode();
  }

  @Override
  public int priority() {
    return 100;
  }

  @Override
  public YamlSession open(YamlOptions options) {
    return YamlSvm.openSession(options);
  }
}
