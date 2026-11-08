package dev.elide.truffledata.protobuf;

import dev.elide.truffledata.ContractViolation;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

/** Checks limits before growing a wire or unknown-field scratch buffer. */
final class BoundedOutput extends ByteArrayOutputStream {
  private final int maximum;

  BoundedOutput(int maximum) {
    super(Math.min(maximum, 32));
    this.maximum = maximum;
  }

  private void reserve(int size) {
    if (size < 0 || size > maximum - count)
      throw new ContractViolation("Wire scratch byte budget exceeded");
    if (size > buf.length - count)
      buf =
          Arrays.copyOf(
              buf, (int) Math.min(maximum, Math.max((long) count + size, 2L * buf.length)));
  }

  @Override
  public synchronized void write(int value) {
    reserve(1);
    buf[count++] = (byte) value;
  }

  @Override
  public synchronized void write(byte[] bytes, int offset, int length) {
    Objects.checkFromIndexSize(offset, length, bytes.length);
    reserve(length);
    System.arraycopy(bytes, offset, buf, count, length);
    count += length;
  }

  @Override
  public void writeBytes(byte[] bytes) {
    write(bytes, 0, bytes.length);
  }
}
