package dev.elide.truffledata.json;

import dev.elide.truffledata.Schema;
import java.lang.foreign.MemorySegment;
import java.util.Map;

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

  /** Compiles a closed scalar schema; see {@link #compile(Schema, Map)}. */
  default JsonPlan compile(Schema root) {
    return compile(root, Map.of());
  }

  /**
   * Compiles a typed plan on this backend under the contract of {@link JsonPlan#compile(Schema,
   * Map)}. The plan is confined to the compiling thread. Once an FFM decoder closes, its plans
   * reject decoding; records they already returned stay valid, and each plan must still be closed.
   * Closing the static SVM decoder is a no-op and leaves its plans usable.
   */
  JsonPlan compile(Schema root, Map<String, Schema> recordTypes);

  @Override
  void close();
}
