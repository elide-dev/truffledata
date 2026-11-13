package dev.elide.truffledata.bebop;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The validated byte ranges of an array of strings inside a {@link BebopDocument}. Offsets and
 * lengths are in bytes of UTF-8 content, without the length prefix or the terminator, and every
 * range lies inside {@code [dataStart, dataEnd)}.
 */
public final class BebopOffsets {
  private final BebopDocument owner;
  private final int[] starts;
  private final int[] lengths;
  private final int dataStart;
  private final int dataEnd;

  BebopOffsets(BebopDocument owner, int[] starts, int[] lengths, int dataStart, int dataEnd) {
    this.owner = owner;
    this.starts = starts;
    this.lengths = lengths;
    this.dataStart = dataStart;
    this.dataEnd = dataEnd;
  }

  public BebopDocument document() {
    return owner;
  }

  public int count() {
    return starts.length;
  }

  /** Offset in the document of row {@code row}'s content. */
  public int byteOffset(int row) {
    return starts[row];
  }

  /** Length in bytes of row {@code row}'s content. */
  public int byteLength(int row) {
    return lengths[row];
  }

  public int dataStart() {
    return dataStart;
  }

  public int dataEnd() {
    return dataEnd;
  }

  public String string(int row) {
    Objects.checkIndex(row, starts.length);
    return new String(owner.bytes(), starts[row], lengths[row], StandardCharsets.UTF_8);
  }

  /** Copies row {@code row}'s content. */
  public void copyBytes(int row, byte[] target, int targetOffset) {
    Objects.checkFromIndexSize(targetOffset, lengths[row], target.length);
    System.arraycopy(owner.bytes(), starts[row], target, targetOffset, lengths[row]);
  }
}
