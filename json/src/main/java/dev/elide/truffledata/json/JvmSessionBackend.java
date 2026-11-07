package dev.elide.truffledata.json;

import dev.elide.truffledata.json.generated.JsonFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.jspecify.annotations.Nullable;

/**
 * JVM session backend (performance plan Task E1): the same native session as static images, called
 * through Myna's generated FFM {@code LibraryBinding}. Normal FFM downcalls take native memory only,
 * so array input is staged in a reusable session-owned native buffer (the native session then
 * copies it into its padded parser buffer, as on SVM). No text arenas: a Java array cannot be the
 * target of a normal downcall, so shared-text sinks copy each string on the JVM.
 *
 * <p>Inputs of at most {@code criticalLimit} bytes (≤ {@link JsonSession#CRITICAL_LIMIT}) decode
 * through Myna's {@code CriticalLibraryBinding} (Task E2): {@code session_decode_small} and {@code
 * session_reset} are declared native leaves (bounded input, no locks, no callbacks, no exception
 * escapes; json/native/api.h), so the call skips the thread-state transition. Myna's critical
 * adapters use {@code Linker.Option.critical(false)}, which forbids heap segments, so the input is
 * still staged natively.
 */
final class JvmSessionBackend extends JsonSession.Backend {
  private final JsonJvm library;
  private final JsonFFM.LibraryBinding calls;
  private final JsonFFM.CriticalLibraryBinding critical;
  private final int criticalLimit;
  private long criticalDecodes;
  // Session lifetime: the result struct; the staging buffer has its own arena so it can grow.
  private final Arena arena = Arena.ofConfined();
  private final MemorySegment session;
  private final MemorySegment result;
  private Arena stagingArena = Arena.ofConfined();
  private MemorySegment staging = MemorySegment.NULL;

  JvmSessionBackend(
      JsonJvm library,
      JsonFFM.LibraryBinding calls,
      JsonFFM.CriticalLibraryBinding critical,
      int criticalLimit,
      JsonLimits limits) {
    this.library = library;
    this.calls = calls;
    this.critical = critical;
    this.criticalLimit = criticalLimit;
    try {
      MemorySegment struct = arena.allocate(JsonFFM.JsonLimitsStruct_LAYOUT);
      struct.set(ValueLayout.JAVA_LONG, JsonFFM.JsonLimitsStruct_max_input_bytes_OFFSET, limits.maxInputBytes());
      struct.set(ValueLayout.JAVA_LONG, JsonFFM.JsonLimitsStruct_max_index_bytes_OFFSET, limits.maxIndexBytes());
      struct.set(ValueLayout.JAVA_LONG, JsonFFM.JsonLimitsStruct_max_nodes_OFFSET, limits.maxNodes());
      struct.set(ValueLayout.JAVA_LONG, JsonFFM.JsonLimitsStruct_max_text_bytes_OFFSET, limits.maxTextBytes());
      struct.set(ValueLayout.JAVA_INT, JsonFFM.JsonLimitsStruct_max_depth_OFFSET, limits.maxDepth());
      result = arena.allocate(JsonFFM.JsonResult_LAYOUT);
      MemorySegment created;
      try {
        created = calls.session_create(struct);
      } catch (Throwable failure) {
        throw JsonJvm.callFailure(failure);
      }
      if (created.address() == 0) throw new OutOfMemoryError("JSON session allocation");
      session = created;
    } catch (RuntimeException | Error failure) {
      stagingArena.close();
      arena.close();
      throw failure;
    }
  }

  /** The session's native copy of {@code length} bytes of {@code utf8} at {@code offset}. */
  MemorySegment stage(byte[] utf8, int offset, int length) {
    if (staging.byteSize() < length) {
      Arena grown = Arena.ofConfined();
      try {
        // 8-byte aligned: UTF-16 input must be 2-byte aligned for the native transcoder.
        staging = grown.allocate(Math.max(length, Math.min(2 * staging.byteSize(), Integer.MAX_VALUE)), 8);
      } catch (RuntimeException | Error failure) {
        grown.close();
        throw failure;
      }
      stagingArena.close();
      stagingArena = grown;
    }
    MemorySegment.copy(utf8, offset, staging, ValueLayout.JAVA_BYTE, 0, length);
    return staging;
  }

  @Override
  void decode(byte[] data, int offset, int length, int input, @Nullable JsonTextArena text) {
    call(stage(data, offset, length), length, input);
  }

  @Override
  void decode(MemorySegment data, long length) {
    if (!data.isNative() || data.address() == 0)
      throw new IllegalArgumentException("Expected a native segment");
    if (!data.scope().isAlive()) throw new IllegalStateException("JSON input segment closed");
    if (!data.isAccessibleBy(Thread.currentThread()))
      throw new IllegalStateException("JSON input segment thread mismatch");
    call(data, length, 0);
  }

  /** One normal session_decode over native memory the caller keeps alive for the call. */
  void call(MemorySegment data, long length, int input) {
    int status;
    try {
      if (length <= criticalLimit) {
        status = critical.session_decode_small(session, data, length, input, MemorySegment.NULL, 0L, result);
        criticalDecodes++;
      } else {
        status = calls.session_decode(session, data, length, input, MemorySegment.NULL, 0L, result);
      }
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    }
    JsonSvm.checkStatus(status);
  }

  @Override
  @Nullable
  JsonTextArena textArena(byte[] array) {
    return null;
  }

  @Override
  JsonBorrow borrow() {
    SegmentValueTape tape = JsonJvm.tape(result);
    return new JsonBorrow(tape, JsonJvm.lexical(result, tape));
  }

  @Override
  JsonMessage retain() {
    return library.retain(this::retainInto);
  }

  /** session_retain into {@code owned}; resets the session first when it fails. */
  int retainInto(MemorySegment owned) {
    try {
      int status = calls.session_retain(session, owned);
      if (status != 0) calls.session_reset(session);
      return status;
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    }
  }

  @Override
  long criticalDecodes() {
    return criticalDecodes;
  }

  @Override
  void reset() {
    try {
      // O(1), frees nothing: a native leaf.
      critical.session_reset(session);
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    }
  }

  @Override
  long streamCreate(int maxDocumentBytes, int batchBytes) {
    MemorySegment stream;
    try {
      stream = calls.stream_create(session, maxDocumentBytes, batchBytes);
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    }
    if (stream.address() == 0) throw new OutOfMemoryError("JSON stream allocation");
    return stream.address();
  }

  @Override
  int streamFeed(long stream, byte[] data, int offset, int length) {
    // Normal downcalls take native memory: stage the chunk (the stream copies what it accepts).
    return (int) streamFeedNative(stream, stage(data, offset, length), length);
  }

  @Override
  long streamFeed(long stream, MemorySegment data) {
    if (!data.isNative() || data.address() == 0)
      throw new IllegalArgumentException("Expected a native segment");
    if (!data.scope().isAlive()) throw new IllegalStateException("JSON input segment closed");
    if (!data.isAccessibleBy(Thread.currentThread()))
      throw new IllegalStateException("JSON input segment thread mismatch");
    return streamFeedNative(stream, data, data.byteSize());
  }

  private long streamFeedNative(long stream, MemorySegment data, long length) {
    try {
      return calls.stream_feed(MemorySegment.ofAddress(stream), data, length);
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    }
  }

  @Override
  void streamFinish(long stream) {
    try {
      calls.stream_finish(MemorySegment.ofAddress(stream));
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    }
  }

  @Override
  int streamNext(long stream) {
    try {
      return calls.stream_next(MemorySegment.ofAddress(stream), result);
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    }
  }

  @Override
  int streamSkip(long stream) {
    try {
      return calls.stream_skip(MemorySegment.ofAddress(stream));
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    }
  }

  @Override
  long streamTruncatedBytes(long stream) {
    try {
      return calls.stream_truncated_bytes(MemorySegment.ofAddress(stream));
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    }
  }

  @Override
  void streamRelease(long stream) {
    try {
      calls.stream_release(MemorySegment.ofAddress(stream));
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    }
  }

  @Override
  void release() {
    try {
      calls.session_release(session);
    } catch (Throwable failure) {
      throw JsonJvm.callFailure(failure);
    } finally {
      try {
        stagingArena.close();
        arena.close();
      } finally {
        library.sessionClosed();
      }
    }
  }
}
