package dev.elide.truffledata.json;

import dev.elide.myna.values.NativeValueTape;
import org.graalvm.word.Pointer;

/** Static-image tape exposing the copy hooks snapshots need. */
final class SvmValueTape extends NativeValueTape implements TapeSource {
  private final long regions;

  SvmValueTape(Pointer descriptor) {
    super(descriptor);
    regions = descriptor.readLong(48);
  }

  @Override
  public int nodeInt(long byteOffset) {
    return readInt(byteOffset);
  }

  @Override
  public long nodeLong(long byteOffset) {
    return readLong(byteOffset);
  }

  @Override
  public long regionCount() {
    return regions;
  }

  @Override
  public long regionSize(long region) {
    return regionLength(region);
  }

  @Override
  public void copyRegion(long region, long start, byte[] output, int offset, int length) {
    copyRegionBytes(region, start, output, offset, length);
  }
}
