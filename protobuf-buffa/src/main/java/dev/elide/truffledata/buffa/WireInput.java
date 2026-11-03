package dev.elide.truffledata.buffa;

import java.nio.ByteBuffer;
import org.jspecify.annotations.Nullable;

/** A thread-confined input owner. Views retain its memory after this handle closes. */
public final class WireInput implements AutoCloseable {
  final Lease lease;
  private boolean open = true;

  WireInput(ByteBuffer bytes, Runnable release, @Nullable Object attachment) {
    lease = new Lease(bytes, release, attachment);
  }

  /** Fill before the first decode seals this input. No writable buffer escapes. */
  public WireInput put(int offset, byte value) {
    writable();
    lease.bytes.put(offset, value);
    return this;
  }

  public WireInput put(byte[] bytes) {
    writable();
    if (bytes.length != lease.bytes.capacity())
      throw new IllegalArgumentException("Input length mismatch");
    lease.bytes.duplicate().put(bytes);
    return this;
  }

  public int size() {
    check();
    return lease.bytes.capacity();
  }

  void check() {
    lease.check();
    if (!open) throw new IllegalStateException("Input handle closed");
  }

  private void writable() {
    check();
    if (lease.sealed) throw new IllegalStateException("Input is sealed");
  }

  Lease retain() {
    check();
    lease.sealed = true;
    lease.references = Math.addExact(lease.references, 1);
    return lease;
  }

  @Override
  @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
  public void close() {
    lease.thread();
    if (open) {
      open = false;
      lease.release();
    }
  }

  static final class Lease {
    final Thread owner = Thread.currentThread();
    final ByteBuffer bytes;
    final Runnable destroy;
    final @Nullable Object attachment;
    int references = 1;
    boolean sealed;

    Lease(ByteBuffer bytes, Runnable destroy, @Nullable Object attachment) {
      this.bytes = bytes;
      this.destroy = destroy;
      this.attachment = attachment;
    }

    void thread() {
      if (Thread.currentThread() != owner)
        throw new IllegalStateException("Wire memory is thread-confined");
    }

    void check() {
      thread();
      if (references == 0) throw new IllegalStateException("Wire memory released");
    }

    void release() {
      check();
      if (--references == 0) destroy.run();
    }

    ByteBuffer slice(int offset, int length) {
      check();
      return bytes.slice(offset, length).asReadOnlyBuffer();
    }
  }
}
