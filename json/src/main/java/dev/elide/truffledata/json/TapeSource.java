package dev.elide.truffledata.json;

/** Raw access needed to copy a tape into detached storage; implemented by every backend tape. */
interface TapeSource {
  int nodeInt(long byteOffset);

  long nodeLong(long byteOffset);

  long regionCount();

  long regionSize(long region);

  void copyRegion(long region, long start, byte[] output, int offset, int length);
}
