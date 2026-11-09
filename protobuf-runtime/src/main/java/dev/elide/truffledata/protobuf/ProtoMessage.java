package dev.elide.truffledata.protobuf;

/** A parsed message with explicit ownership and a durable snapshot operation. */
public interface ProtoMessage extends AutoCloseable {
  ProtoAccess row();

  ProtoAccess snapshot();

  byte[] encode();

  @Override
  void close();
}
