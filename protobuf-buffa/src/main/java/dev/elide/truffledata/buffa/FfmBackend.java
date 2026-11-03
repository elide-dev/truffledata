package dev.elide.truffledata.buffa;

import dev.elide.truffledata.Limits;
import dev.elide.truffledata.buffa.generated.BuffaFFM;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** JVM-only adapter. Its arena owns the library, not input buffers or returned views. */
final class FfmBackend implements BuffaWire.Backend {
  private final Arena libraryArena = Arena.ofConfined();
  private final MethodHandle decode;
  private final MethodHandle release;

  FfmBackend(Path library) {
    try {
      var symbols = SymbolLookup.libraryLookup(library.toAbsolutePath(), libraryArena);
      var fingerprint = BuffaFFM.fingerprint(symbols);
      MemorySegment address;
      try {
        address = (MemorySegment) fingerprint.invokeExact();
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      var actual =
          new String(
              address.reinterpret(64).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.US_ASCII);
      if (!actual.equals(BuffaFFM.ABI_FINGERPRINT))
        throw new IllegalStateException("Myna/Buffa ABI fingerprint mismatch");
      decode = BuffaFFM.decode(symbols);
      release = BuffaFFM.release(symbols);
    } catch (RuntimeException | Error failure) {
      libraryArena.close();
      throw failure;
    }
  }

  @Override
  public WireInput allocate(int size) {
    var arena = Arena.ofConfined();
    try {
      var memory = arena.allocate(Math.max(1, size));
      return new WireInput(memory.asSlice(0, size).asByteBuffer(), arena::close, memory);
    } catch (RuntimeException | Error failure) {
      arena.close();
      throw failure;
    }
  }

  @Override
  public BuffaWire.Tape decode(WireInput input, Limits limits, int depth, int nativeBytes) {
    if (!(input.lease.attachment instanceof MemorySegment memory))
      throw new IllegalArgumentException("Input is not JVM native memory");
    Arena call = Arena.ofConfined();
    MemorySegment result = call.allocate(BuffaFFM.Result_LAYOUT);
    try {
      int status;
      try {
        status =
            (int)
                decode.invokeExact(
                    memory,
                    (long) input.size(),
                    limits.maxCells(),
                    depth,
                    (long) nativeBytes,
                    result);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      BuffaWire.status(status);
      long count = result.get(ValueLayout.JAVA_LONG, BuffaFFM.Result_count_OFFSET),
          capacity = result.get(ValueLayout.JAVA_LONG, BuffaFFM.Result_capacity_OFFSET);
      ByteBuffer tape =
          count == 0
              ? ByteBuffer.allocate(0)
              : result
                  .get(ValueLayout.ADDRESS, BuffaFFM.Result_data_OFFSET)
                  .reinterpret(
                      Math.multiplyExact(count, BuffaFFM.Field_LAYOUT.byteSize()), call, null)
                  .asByteBuffer();
      return new BuffaWire.Tape(
          tape,
          count,
          capacity,
          () -> {
            try {
              free(result);
            } finally {
              call.close();
            }
          });
    } catch (RuntimeException | Error failure) {
      try {
        free(result);
      } finally {
        call.close();
      }
      throw failure;
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

  @Override
  public void close() {
    libraryArena.close();
  }
}
