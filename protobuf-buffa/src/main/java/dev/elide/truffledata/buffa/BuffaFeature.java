package dev.elide.truffledata.buffa;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * Explicit opt-in: --features=dev.elide.truffledata.buffa.BuffaFeature plus the static archive.
 *
 * <p>The backend is fixed here, at image build time. {@code -Dtruffledata.buffa.cells=true} on the
 * native-image command line selects the Myna Truffle cells route of {@code
 * truffledata-protobuf-buffa-cells} (which must then be on the image class path); without it the
 * image registers the static backend alone and contains no cells code (docs/BUFFA.md).
 */
@org.graalvm.nativeimage.Platforms(org.graalvm.nativeimage.Platform.HOSTED_ONLY.class)
public final class BuffaFeature implements Feature {
  @Override
  public void afterRegistration(AfterRegistrationAccess access) {
    ImageSingletons.add(
        BuffaWire.NativeFactory.class,
        Boolean.getBoolean(CellsRoute.PROPERTY) ? cells(access) : new SvmBackend.Factory());
  }

  private static BuffaWire.NativeFactory cells(AfterRegistrationAccess access) {
    Class<?> type = access.findClassByName(CellsRoute.SVM_FACTORY);
    if (type == null)
      throw new IllegalStateException(
          "-D"
              + CellsRoute.PROPERTY
              + "=true needs truffledata-protobuf-buffa-cells on the image class path");
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return (BuffaWire.NativeFactory) constructor.newInstance();
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Buffa cells backend factory", failure);
    }
  }
}
