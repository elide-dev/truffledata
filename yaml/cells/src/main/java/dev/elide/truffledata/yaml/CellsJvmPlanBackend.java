package dev.elide.truffledata.yaml;

import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.yaml.cells.generated.YamlCellsJvmTruffle;
import dev.elide.truffledata.yaml.generated.YamlFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/**
 * The FFM plan with the cells route: {@code typed_decode} goes through its cell ({@code
 * executeInt}); compilation, the library reference count and release are the wrapped {@link
 * JvmPlanBackend}'s, and the input is staged exactly as it stages it.
 */
final class CellsJvmPlanBackend extends YamlPlan.Backend {
  private final JvmPlanBackend backend;
  private final YamlCellsJvmTruffle.typed_decode_Cell decode;

  CellsJvmPlanBackend(JvmPlanBackend backend) {
    this.backend = backend;
    try {
      decode = CellsSvmSessionBackend.adopt(new YamlCellsJvmTruffle.typed_decode_Cell());
    } catch (RuntimeException | Error failure) {
      backend.release();
      throw failure;
    }
  }

  private static NativePointer pointer(MemorySegment segment) {
    return CellsSvmSessionBackend.pointer(segment.address());
  }

  @Override
  TypedOwner decode(byte[] utf8, Schema[] schemas) {
    Arena resultArena = Arena.ofConfined();
    MemorySegment result;
    try {
      result = resultArena.allocate(YamlFFM.YamlTypedResult_LAYOUT);
    } catch (RuntimeException | Error failure) {
      resultArena.close();
      throw failure;
    }
    backend.retainLibrary();
    boolean published = false;
    try {
      Arena input = Arena.ofConfined();
      try {
        MemorySegment data = input.allocate(Math.max(1, utf8.length));
        MemorySegment.copy(utf8, 0, data, ValueLayout.JAVA_BYTE, 0, utf8.length);
        int status =
            decode.executeInt(
                new Object[] {
                  pointer(backend.handle()), pointer(data), (long) utf8.length, pointer(result)
                });
        if (status == 2 || status == 4 || status == 6 || status == 7) {
          int length = Math.min(255, result.get(ValueLayout.JAVA_INT, 128));
          String text =
              new String(
                  result.asSlice(136, length).toArray(ValueLayout.JAVA_BYTE),
                  StandardCharsets.UTF_8);
          YamlStatus.checkDetailed(
              status,
              text,
              result.get(ValueLayout.JAVA_LONG, 112),
              result.get(ValueLayout.JAVA_LONG, 120));
        }
        YamlStatus.checkStatus(status);
      } finally {
        input.close();
      }
      TypedOwner owner = new JvmTypedOwner(result, schemas, () -> backend.retire(result, resultArena));
      published = true;
      return owner;
    } finally {
      if (!published) backend.retire(result, resultArena);
    }
  }

  @Override
  void release() {
    backend.release();
  }
}
