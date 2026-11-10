package dev.elide.truffledata.json;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * Explicit opt-in: {@code --features=dev.elide.truffledata.json.JsonCellsFeature} plus {@code
 * -Dtruffledata.json.cells=true} on the native-image command line, with truffledata-json-cells on
 * the image class path. The route is fixed here, at image build time: with the flag the Feature
 * installs the cells ({@link CellsSvm}) and {@link JsonCellsRoute#ENABLED} folds true; without it
 * the Feature installs nothing and the image is the baseline one. {@link JsonCellsRoute} fails the
 * build when the flag and the Feature disagree.
 */
@org.graalvm.nativeimage.Platforms(org.graalvm.nativeimage.Platform.HOSTED_ONLY.class)
public final class JsonCellsFeature implements Feature {
  @Override
  public void afterRegistration(AfterRegistrationAccess access) {
    if (Boolean.getBoolean(JsonCellsRoute.PROPERTY))
      ImageSingletons.add(JsonCellsRoute.Svm.class, new CellsSvm());
  }
}
