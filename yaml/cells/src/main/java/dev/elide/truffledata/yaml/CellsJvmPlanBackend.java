package dev.elide.truffledata.yaml;

import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.yaml.cells.generated.YamlCellsJvmTruffle;
import dev.elide.truffledata.yaml.generated.YamlFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/**
 * The FFM plan with the cells route: {@code typed_decode} goes through the cell's static {@code
 * invoke} entry (see {@link CellsJvmSessionBackend}); compilation, the library reference count and
 * release are the wrapped {@link JvmPlanBackend}'s, and the input is staged exactly as it stages
 * it. Every address the cell receives is owned here and outlives the call.
 */
final class CellsJvmPlanBackend extends YamlPlan.Backend {
  private final JvmPlanBackend backend;

  /** The plan handle: it lives as long as {@link #backend}. */
  private final long plan;

  /** Takes over {@code backend}: releases it if this cannot be set up. */
  CellsJvmPlanBackend(JvmPlanBackend backend) {
    this.backend = backend;
    try {
      plan = backend.handle().address();
    } catch (RuntimeException | Error failure) {
      backend.release();
      throw failure;
    }
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
            YamlCellsJvmTruffle.typed_decode_Cell.invoke(
                plan, data.address(), utf8.length, result.address());
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
        // Published before the input copy is freed, as the FFM plan does.
        TypedOwner owner =
            new JvmTypedOwner(result, schemas, () -> backend.retire(result, resultArena));
        published = true;
        return owner;
      } finally {
        input.close();
      }
    } finally {
      if (!published) backend.retire(result, resultArena);
    }
  }

  @Override
  void release() {
    backend.release();
  }
}
