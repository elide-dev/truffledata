package dev.elide.truffledata.buffa;

import dev.elide.truffledata.protobuf.*;

/** One decode's root flyweight. Retain before the session is recycled or closed. */
public final class BorrowedSlabMessage extends BorrowedSlabRow implements ProtoMessage {
  BorrowedSlabMessage(SlabSession session, long epoch) {
    super(session, epoch, 0, SlabFormat.ROOT);
  }

  @Override
  public BorrowedSlabMessage row() {
    owner.check(epoch);
    return this;
  }

  @Override
  public SlabMessage snapshot() {
    return retain();
  }

  public SlabMessage retain() {
    return owner.retain(epoch);
  }

  public SlabSession.Pin pin() {
    return owner.pin(epoch);
  }

  public int slabBytes() {
    return owner.extent(epoch);
  }

  public boolean slabBacked() {
    owner.check(epoch);
    return true;
  }

  @Override
  public byte[] encode() {
    return retain().encode();
  }

  @Override
  public void close() {
    owner.release(epoch);
  }
}
