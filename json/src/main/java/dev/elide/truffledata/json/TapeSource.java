package dev.elide.truffledata.json;

/** Raw access needed to copy a tape into detached storage; implemented by every backend tape. */
interface TapeSource {
  int nodeInt(long byteOffset);

  long nodeLong(long byteOffset);

  /** Key-hash side-table entry of a node (see {@code JsonOwner#meta}). */
  int keyMeta(long node);

  /**
   * Whether node {@code k} (a valid index) has side-table entry {@code meta} (ASCII bit ignored)
   * and node words {@code header}, {@code low} and, when {@code wide}, {@code high}. One straight-line
   * call per hinted member read, so compiled code type-switches on the tape once per read.
   */
  boolean keyNodeEquals(long k, int meta, long header, long low, long high, boolean wide);

  long regionCount();

  long regionSize(long region);

  void copyRegion(long region, long start, byte[] output, int offset, int length);
}
