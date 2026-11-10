package dev.elide.truffledata.csv.kernel.svm;

import dev.elide.truffledata.csv.CsvIndexing;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;

/**
 * The opt-in Myna Truffle cells route of the static CSV kernel, {@code
 * -Dtruffledata.csv.cells=true} on the native-image command line (default off). The route is chosen
 * when the image is built: {@code CsvCellsFeature}, which the separate {@code
 * truffledata-csv-cells} artifact registers, installs the cells backend as the image's {@link
 * CsvIndexing.NativeBackend} singleton when the flag is set. This holder is initialized at image
 * build time with the rest of {@code dev.elide.truffledata} (as every TruffleData image requires),
 * so {@link #ENABLED} folds: an image built without the flag compiles the static calls alone and
 * contains no cells code.
 *
 * <p>The flag and the registration must agree, or the image build fails: a flag without {@code
 * truffledata-csv-cells} on the image class path does not quietly build the static route, and a
 * backend registered without the flag is not quietly used.
 */
final class CellsRoute {
  static final String PROPERTY = "truffledata.csv.cells";
  static final boolean ENABLED = enabled();

  private CellsRoute() {}

  private static boolean enabled() {
    // Only a Native Image has this backend (Backend.available) and image singletons; the HotSpot
    // backend reads the flag itself. Not inImageCode(): the tracing agent sets it on a plain JVM.
    if (!ImageInfo.inImageBuildtimeCode() && !ImageInfo.inImageRuntimeCode()) return false;
    boolean registered = ImageSingletons.contains(CsvIndexing.NativeBackend.class);
    if (ImageInfo.inImageBuildtimeCode()) {
      boolean requested = Boolean.getBoolean(PROPERTY);
      if (requested != registered)
        throw new IllegalStateException(
            "-D"
                + PROPERTY
                + "="
                + requested
                + (requested
                    ? " needs truffledata-csv-cells on the image class path"
                    : " but a CSV cells backend is registered: set the flag or remove"
                        + " truffledata-csv-cells' registration"));
    }
    return registered;
  }

  /**
   * The image's cells backend; reachable only when {@link #ENABLED} folds true. {@code contains}
   * folds when this is parsed, so an image without the route never looks its singleton up: one
   * that initializes this holder at run time still builds and takes the static calls, where a bare
   * lookup of the absent singleton would fail its build.
   */
  static CsvIndexing.NativeBackend backend() {
    if (!ImageSingletons.contains(CsvIndexing.NativeBackend.class))
      throw new IllegalStateException("The CSV cells route is not in this image");
    return ImageSingletons.lookup(CsvIndexing.NativeBackend.class);
  }
}
