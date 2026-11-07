package dev.elide.truffledata.csv;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream whose reads return scripted sizes (the last size repeats). It counts the bytes it
 * serves, can fail with an {@link IOException} once a byte count is reached, and records close().
 */
final class ChunkedInput extends InputStream {
  private final byte[] data;
  private final int[] sizes;
  private int position;
  private int read;
  private long failAt = Long.MAX_VALUE;
  private IOException failure = new IOException("scripted failure");
  int closes;

  ChunkedInput(byte[] data, int... sizes) {
    if (sizes.length == 0) throw new IllegalArgumentException("sizes");
    for (int size : sizes) if (size <= 0) throw new IllegalArgumentException("size");
    this.data = data;
    this.sizes = sizes;
  }

  ChunkedInput failingAt(long bytes, IOException failure) {
    this.failAt = bytes;
    this.failure = failure;
    return this;
  }

  long served() {
    return position;
  }

  @Override
  public int read() throws IOException {
    byte[] one = new byte[1];
    return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException {
    if (length == 0) return 0;
    if (position >= failAt) throw failure;
    if (position >= data.length) return -1;
    int size = sizes[Math.min(read++, sizes.length - 1)];
    int n = (int) Math.min(Math.min(size, length), Math.min(data.length - position, failAt - position));
    System.arraycopy(data, position, target, offset, n);
    position += n;
    return n;
  }

  @Override
  public void close() {
    closes++;
  }
}
