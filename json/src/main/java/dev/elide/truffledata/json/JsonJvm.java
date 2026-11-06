package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.json.generated.JsonFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Objects;

/** JVM FFM backend. Its arena owns only the library; every message owns its native result. */
public final class JsonJvm implements JsonDecoder {
  private static final long PADDING = 64;
  private final Arena libraryArena = Arena.ofConfined();
  private final Thread thread = Thread.currentThread();
  private final MethodHandle decode;
  private final MethodHandle decodeAdopted;
  private final MethodHandle release;
  private boolean open = true;
  private int live;

  private JsonJvm(Path library) {
    try {
      SymbolLookup symbols = SymbolLookup.libraryLookup(library.toAbsolutePath(), libraryArena);
      MemorySegment fingerprint;
      try {
        fingerprint = (MemorySegment) JsonFFM.fingerprint(symbols).invokeExact();
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      String actual = fingerprint.reinterpret(JsonFFM.ABI_FINGERPRINT.length() + 1).getString(0);
      if (!actual.equals(JsonFFM.ABI_FINGERPRINT))
        throw new IllegalStateException("Myna/JSON ABI fingerprint mismatch");
      decode = JsonFFM.decode(symbols);
      decodeAdopted = JsonFFM.decode_adopted(symbols);
      release = JsonFFM.release(symbols);
    } catch (RuntimeException | Error failure) {
      libraryArena.close();
      throw failure;
    }
  }

  /** Loads the shared JSON library; the decoder is confined to the opening thread. */
  @TruffleBoundary
  public static JsonDecoder open(Path library) {
    return new JsonJvm(library);
  }

  private void check() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON decoder thread mismatch");
    if (!open) throw new IllegalStateException("JSON decoder closed");
  }

  @Override
  @TruffleBoundary
  public JsonMessage decode(byte[] utf8, JsonLimits limits) {
    check();
    if (utf8.length == 0) throw new IllegalArgumentException("Empty JSON input");
    if (utf8.length > limits.maxInputBytes()) throw new IllegalArgumentException("JSON input limit");
    Arena inputArena = Arena.ofConfined();
    try {
      MemorySegment padded = inputArena.allocate(utf8.length + PADDING);
      MemorySegment.copy(utf8, 0, padded, ValueLayout.JAVA_BYTE, 0, utf8.length);
      return publish(padded, utf8.length, limits, inputArena::close);
    } catch (RuntimeException | Error failure) {
      inputArena.close();
      throw failure;
    }
  }

  @Override
  @TruffleBoundary
  public JsonMessage adopt(MemorySegment data, long length, Runnable release) {
    check();
    Objects.requireNonNull(release);
    if (!data.isNative() || !data.scope().isAlive() || data.address() == 0)
      throw new IllegalArgumentException("Expected a live native region");
    if (length <= 0 || length > data.byteSize())
      throw new IllegalArgumentException("JSON length exceeds adopted capacity");
    return publish(data, length, JsonLimits.DEFAULT, release);
  }

  private JsonMessage publish(MemorySegment data, long length, JsonLimits limits, Runnable onClose) {
    Arena resultArena = Arena.ofConfined();
    MemorySegment result = resultArena.allocate(JsonFFM.JsonResult_LAYOUT);
    boolean published = false;
    try {
      int status;
      try {
        status =
            (int)
                decodeAdopted.invokeExact(
                    data,
                    length,
                    data.byteSize(),
                    limits.maxInputBytes(),
                    limits.maxIndexBytes(),
                    limits.maxNodes(),
                    limits.maxTextBytes(),
                    limits.maxDepth(),
                    result);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      JsonSvm.checkStatus(status);
      MemorySegment descriptor = result.get(ValueLayout.ADDRESS, JsonFFM.JsonResult_view_OFFSET);
      SegmentValueTape tape = SegmentValueTape.of(descriptor);
      long spanCount = result.get(ValueLayout.JAVA_LONG, JsonFFM.JsonResult_lexical_count_OFFSET);
      if (spanCount != tape.nodeCount()) throw new IllegalStateException("JSON lexical table mismatch");
      MemorySegment spans =
          result.get(ValueLayout.ADDRESS, JsonFFM.JsonResult_lexical_OFFSET).reinterpret(spanCount * 8);
      MemorySegment input = tape.region(1);
      JsonMessage.Lexical lexical =
          new JsonMessage.Lexical() {
            @Override
            public long span(long node) {
              return spans.get(ValueLayout.JAVA_LONG, node * 8);
            }

            @Override
            public byte inputByte(long offset) {
              return input.get(ValueLayout.JAVA_BYTE, offset);
            }
          };
      live++;
      published = true;
      return new JsonMessage(tape, lexical, () -> releaseResult(result, resultArena, onClose));
    } finally {
      if (!published) {
        free(result);
        resultArena.close();
      }
    }
  }

  private void releaseResult(MemorySegment result, Arena resultArena, Runnable onClose) {
    try {
      free(result);
    } finally {
      try {
        resultArena.close();
      } finally {
        live--;
        try {
          onClose.run();
        } finally {
          if (!open && live == 0) libraryArena.close();
        }
      }
    }
  }

  private void free(MemorySegment result) {
    try {
      release.invokeExact(result);
    } catch (Throwable failure) {
      throw callFailure(failure);
    }
  }

  static RuntimeException callFailure(Throwable failure) {
    if (failure instanceof RuntimeException exception) return exception;
    if (failure instanceof Error error) throw error;
    return new IllegalStateException("Native invocation failed", failure);
  }

  /** Messages already returned stay valid; the library unloads once the last one closes. */
  @Override
  @TruffleBoundary
  public void close() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON decoder thread mismatch");
    if (open) {
      open = false;
      if (live == 0) libraryArena.close();
    }
  }
}
