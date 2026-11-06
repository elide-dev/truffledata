package dev.elide.truffledata.json;

import java.lang.foreign.MemorySegment;

/**
 * Backend handle producing thread-confined messages. Messages retain their own input, index and
 * lexical storage: closing the decoder never invalidates a message already returned.
 */
public interface JsonDecoder extends AutoCloseable {
  /** Copies UTF-8 input into retained padded storage under {@link JsonLimits#DEFAULT}. */
  default JsonMessage decode(byte[] utf8) {
    return decode(utf8, JsonLimits.DEFAULT);
  }

  JsonMessage decode(byte[] utf8, JsonLimits limits);

  /**
   * Adopts native memory holding {@code length} logical bytes; {@code data.byteSize()} is the
   * capacity. With at least 64 padding bytes the input is parsed in place, otherwise copied once.
   * {@code release} runs exactly once, on the owning thread, when the message closes. The caller
   * must not mutate or free the region before then.
   */
  JsonMessage adopt(MemorySegment data, long length, Runnable release);

  @Override
  void close();
}
