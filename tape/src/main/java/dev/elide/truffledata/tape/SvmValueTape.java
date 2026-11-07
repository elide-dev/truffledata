package dev.elide.truffledata.tape;

import dev.elide.myna.values.NativeValueTape;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * Static-image tape exposing the copy hooks snapshots need and materializing long strings with
 * Myna's simdutf bridge.
 */
public final class SvmValueTape extends NativeValueTape implements TapeSource {
  private final long regions;
  // One int per node: the native key-hash side table (the result's key_hashes).
  private final Pointer hashes;

  /** A reader over a native Myna descriptor and its key-hash side table (one int per node). */
  public SvmValueTape(Pointer descriptor, Pointer hashes) {
    super(descriptor);
    regions = descriptor.readLong(48);
    this.hashes = hashes;
  }

  // Myna's simdutf transcoder (myna-values), linked into every static image that uses this tape:
  // //json/native:svm links it through //json/native/bridge. A normal transition: string lengths
  // are unbounded.
  @CFunction("myna_utf8_to_utf16_v2")
  private static native long transcode(Pointer input, long length, CShortPointer output, long capacity);

  /**
   * Long strings transcode with simdutf straight into the String's UTF-16 storage, as Myna's
   * SimdNativeValueTape does. That class is final and its 256-byte threshold fixed
   * (elide-dev/myna#49), so this tape mirrors it over its own reader. Shorter strings decode
   * locally.
   */
  @Override
  protected String decodeExternalUtf8(long region, long start, int len) {
    if (len < TapeText.BULK_THRESHOLD) return super.decodeExternalUtf8(region, start, len);
    // One UTF-16 unit per UTF-8 byte bounds the output; simdutf validates while converting and
    // neither pointer escapes the pinned interval.
    char[] chars = new char[len];
    long written;
    try (PinnedObject pinned = PinnedObject.create(chars)) {
      written =
          transcode(
              regionAddress(region).add(WordFactory.signed(start)), len, pinned.addressOfArrayElement(0), len);
    }
    if (written < 0 || written > len) throw new IllegalArgumentException("Invalid UTF-8 or output extent");
    return new String(chars, 0, (int) written);
  }

  @Override
  public int keyMeta(long node) {
    return hashes.readInt(WordFactory.signed(node * 4));
  }

  @Override
  public boolean keyNodeEquals(long k, int meta, long header, long low, long high, boolean wide) {
    long at = k * NODE_SIZE;
    return (hashes.readInt(WordFactory.signed(k * 4)) & 0x7fffffff) == meta
        && readLong(at) == header
        && readLong(at + 8) == low
        && (!wide || readLong(at + 16) == high);
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
