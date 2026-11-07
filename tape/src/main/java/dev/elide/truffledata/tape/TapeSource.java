package dev.elide.truffledata.tape;

/** Raw access needed to copy a tape into detached storage; implemented by every backend tape. */
interface TapeSource {
  int nodeInt(long byteOffset);

  long nodeLong(long byteOffset);

  /** Key-hash side-table entry of a node (see {@code TapeOwner#meta}). */
  int keyMeta(long node);

  /**
   * Entries of the tape's Myna ABI v4 object directory (0 for v2/v3 tapes). An object whose payload
   * is nonzero owns the {@code length} entries starting at {@code payload - 1}.
   */
  long directorySize();

  /**
   * Directory entry {@code index} (in range) as {@code hash & 0xffffffffL | keyOffset << 32}: the
   * 32-bit FNV-1a hash of the key's decoded bytes and the key node's offset from its object.
   */
  long directoryEntry(long index);

  /**
   * The key node named by entry {@code rank} of {@code object}'s Myna v4 directory when it is
   * exactly the inline key {@code (hash, header, low, high)}, else -1. {@code object} is a valid
   * node index; every other input may be stale: the node must be an object with a directory of
   * more than {@code rank} entries, and the entry's hash and the key node's words ({@code high}
   * only when {@code wide}) must match. A directory lists only its own object's keys, so a hit
   * proves the key is a member of {@code object}. One straight-line call per hinted member read,
   * so compiled code type-switches on the tape once per read. A hit returns the value node {@code
   * k + 1} without alias resolution, so tapes of documents with alias nodes (YAML) must also return
   * -1 when node {@code k + 1} is an alias.
   */
  long directoryKey(long object, int rank, int hash, long header, long low, long high, boolean wide);

  long regionCount();

  long regionSize(long region);

  void copyRegion(long region, long start, byte[] output, int offset, int length);
}
