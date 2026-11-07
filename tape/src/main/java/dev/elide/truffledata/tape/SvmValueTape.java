package dev.elide.truffledata.tape;

import dev.elide.myna.values.NativeValueTape;
import dev.elide.myna.values.SimdNativeValueTape;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * Static-image tape over a native Myna descriptor (ABI v4 with object directories, or v3): exposes
 * the copy hooks snapshots need, answers hinted member reads from the native directory and
 * materializes long strings with Myna's simdutf bridge. A session keeps one reader and {@linkplain
 * #rebind rebinds} it to each borrowed document (Myna 0.11.0, elide-dev/myna#47); retained messages
 * own theirs. Thread-confined, like its owners.
 */
public final class SvmValueTape extends NativeValueTape implements TapeSource {
  // One int per node: the native key-hash side table (the result's key_hashes).
  private Pointer hashes;
  // UTF-8 bytes from which external strings transcode through simdutf (elide-dev/myna#49).
  private final int simdThreshold;

  /** A reader over a native Myna descriptor and its key-hash side table (one int per node). */
  public SvmValueTape(Pointer descriptor, Pointer hashes) {
    this(descriptor, hashes, SimdNativeValueTape.SIMD_THRESHOLD);
  }

  /**
   * As {@link #SvmValueTape(Pointer, Pointer)}, transcoding external strings of at least {@code
   * simdThreshold} UTF-8 bytes through simdutf (0: every nonempty external string; {@link
   * Integer#MAX_VALUE}: never), the per-reader crossover of Myna's {@code SimdNativeValueTape}.
   */
  public SvmValueTape(Pointer descriptor, Pointer hashes, int simdThreshold) {
    super(descriptor);
    if (simdThreshold < 0) throw new IllegalArgumentException("Negative SIMD threshold");
    this.simdThreshold = simdThreshold;
    bindTables(hashes);
  }

  /**
   * Reuses this reader for another document of the same thread: the descriptor is checked before
   * anything changes (an invalid one leaves the previous binding intact), and the old document's
   * storage may be released afterwards. Owners of the previous document must already be closed.
   */
  public void rebind(Pointer descriptor, Pointer hashes) {
    if (hashes.isNull()) throw new IllegalArgumentException("Key-hash table missing");
    rebind(descriptor);
    bindTables(hashes);
  }

  private void bindTables(Pointer hashes) {
    if (hashes.isNull()) throw new IllegalArgumentException("Key-hash table missing");
    this.hashes = hashes;
  }

  /** The simdutf crossover of this reader, in UTF-8 bytes. */
  public int simdThreshold() {
    return simdThreshold;
  }

  // Myna's simdutf transcoder (myna-values), linked into every static image that uses this tape:
  // //json/native:svm links it through //json/native/bridge. A normal transition: string lengths
  // are unbounded.
  @CFunction("myna_utf8_to_utf16_v2")
  private static native long transcode(Pointer input, long length, CShortPointer output, long capacity);

  /**
   * Strings of at least {@link #simdThreshold()} bytes transcode with simdutf straight into the
   * String's UTF-16 storage, as Myna's SimdNativeValueTape does with the same per-reader threshold.
   * That class is still final in Myna 0.11.0, and this tape must extend NativeValueTape for the
   * snapshot hooks, so the 15-line transcoding call is mirrored here. Shorter strings decode
   * locally.
   */
  @Override
  protected String decodeExternalUtf8(long region, long start, int len) {
    if (len == 0 || len < simdThreshold) return super.decodeExternalUtf8(region, start, len);
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
  public long directorySize() {
    return directoryCount();
  }

  @Override
  public long directoryEntry(long index) {
    return (directoryHash(index) & 0xffffffffL) | (directoryKeyOffset(index) << 32);
  }

  @Override
  public long directoryKey(long object, int rank, int hash, long header, long low, long high, boolean wide) {
    long at = object * NODE_SIZE;
    long word = readLong(at);
    long payload = readLong(at + 8);
    // An object (no storage flag on containers) with a directory of more than `rank` entries.
    if ((int) word != OBJECT || rank >= (word >>> 32) || payload == 0) return -1;
    long entry = payload - 1 + rank;
    if (entry >= directoryCount() || directoryHash(entry) != hash) return -1;
    long k = object + directoryKeyOffset(entry);
    if (k >= nodeCount()) return -1;
    long key = k * NODE_SIZE;
    return readLong(key) == header && readLong(key + 8) == low && (!wide || readLong(key + 16) == high) ? k : -1;
  }

  @Override
  public int nodeInt(long byteOffset) {
    return readInt(byteOffset);
  }

  @Override
  public long nodeLong(long byteOffset) {
    return readLong(byteOffset);
  }

  /**
   * The bound descriptor's region count, read back through Myna's descriptor writer: only snapshot
   * copies ask, so readers (one per retained message) carry no field for it.
   */
  @Override
  public long regionCount() {
    Pointer descriptor = StackValue.get(88);
    writeDescriptor(descriptor);
    return descriptor.readLong(48);
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
