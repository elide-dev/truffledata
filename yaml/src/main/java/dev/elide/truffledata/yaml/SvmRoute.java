package dev.elide.truffledata.yaml;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;

/**
 * Whether this image has the Myna Truffle cells route of the static backend ({@code
 * -Dtruffledata.yaml.cells=true} with {@link YamlFeature}; see {@link SvmCells}). The holder is
 * initialized at image build time with the rest of {@code dev.elide.truffledata} (as every
 * TruffleData image requires), so {@link #ENABLED} folds: an image built without the flag compiles
 * the static calls alone and contains no cells code.
 *
 * <p>The flag and the registration must agree, or the image build fails: a flag without {@code
 * --features=dev.elide.truffledata.yaml.YamlFeature} does not quietly build the static route.
 *
 * <p>The name is deliberate: //yaml/cells/tests:flag_off_image_test proves that an image built
 * without the flag does not contain the {@link SvmCells} type by searching the binary for that
 * name, so this holder, which every static image contains, must not share its prefix.
 */
final class SvmRoute {
  static final boolean ENABLED = enabled();

  private SvmRoute() {}

  private static boolean enabled() {
    // Only a Native Image has image singletons; YamlSvm rejects every other runtime before it gets
    // here. Not inImageCode(): the tracing agent sets it on a plain JVM.
    if (!ImageInfo.inImageBuildtimeCode() && !ImageInfo.inImageRuntimeCode()) return false;
    boolean registered = ImageSingletons.contains(SvmCells.class);
    if (ImageInfo.inImageBuildtimeCode()) {
      boolean requested = Boolean.getBoolean(YamlFeature.PROPERTY);
      if (requested != registered)
        throw new IllegalStateException(
            "-D"
                + YamlFeature.PROPERTY
                + "="
                + requested
                + " needs --features=dev.elide.truffledata.yaml.YamlFeature and"
                + " truffledata-yaml-cells on the image class path");
    }
    return registered;
  }

  /**
   * The image's cells route; reachable only when {@link #ENABLED} folds true. {@code contains}
   * folds when this is parsed, so an image without the route never looks its singleton up: one
   * that initializes this holder at run time still builds and takes the static calls, where a bare
   * lookup of the absent singleton would fail its build.
   */
  static SvmCells cells() {
    if (!ImageSingletons.contains(SvmCells.class))
      throw new IllegalStateException("The YAML cells route is not in this image");
    return ImageSingletons.lookup(SvmCells.class);
  }
}
