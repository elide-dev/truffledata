package dev.elide.truffledata.yaml;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * Explicit opt-in for the Myna Truffle cells route of the static YAML backend: {@code
 * --features=dev.elide.truffledata.yaml.YamlFeature -Dtruffledata.yaml.cells=true} on the
 * native-image command line, with {@code truffledata-yaml-cells} on the image class path. The route
 * is fixed here, at image build time ({@link SvmCells}); without the flag the Feature registers
 * nothing and the image is the static backend alone, with no cells code.
 */
@org.graalvm.nativeimage.Platforms(org.graalvm.nativeimage.Platform.HOSTED_ONLY.class)
public final class YamlFeature implements Feature {
  static final String PROPERTY = "truffledata.yaml.cells";
  static final String SVM_CELLS = "dev.elide.truffledata.yaml.CellsSvm";

  @Override
  public void afterRegistration(AfterRegistrationAccess access) {
    if (!Boolean.getBoolean(PROPERTY)) return;
    Class<?> type = access.findClassByName(SVM_CELLS);
    if (type == null)
      throw new IllegalStateException(
          "-D" + PROPERTY + "=true needs truffledata-yaml-cells on the image class path");
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      ImageSingletons.add(SvmCells.class, (SvmCells) constructor.newInstance());
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("YAML cells route", failure);
    }
  }
}
