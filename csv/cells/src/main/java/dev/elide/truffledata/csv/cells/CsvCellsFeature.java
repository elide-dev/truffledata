package dev.elide.truffledata.csv.cells;

import dev.elide.truffledata.csv.CsvIndexing;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

/**
 * Fixes the CSV kernel route at image build time. Registered by this artifact's {@code
 * native-image.properties}, so it runs whenever {@code truffledata-csv-cells} is on the image class
 * path: with {@code -Dtruffledata.csv.cells=true} on the native-image command line it registers
 * {@link CellsSvmIndexer.Backend} as the image's {@code CsvIndexing.NativeBackend} singleton, which
 * {@code SvmIndexer.Backend.load} defers to; without the flag it registers nothing, and the image
 * contains no cells code (nothing instantiates the cells backend).
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class CsvCellsFeature implements Feature {
  @Override
  public void afterRegistration(AfterRegistrationAccess access) {
    if (!Boolean.getBoolean(CsvCellsRoute.PROPERTY)) return;
    // The singleton lives in the image heap; its class must initialize at build time.
    RuntimeClassInitialization.initializeAtBuildTime(CellsSvmIndexer.Backend.class);
    ImageSingletons.add(CsvIndexing.NativeBackend.class, new CellsSvmIndexer.Backend());
  }
}
