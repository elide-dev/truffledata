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
import org.jspecify.annotations.Nullable;

/**
 * The FFM plan with the cells route: {@code typed_decode} goes through its cell ({@code
 * executeInt}); compilation, the library reference count and release are the wrapped {@link
 * JvmPlanBackend}'s, and the input is staged exactly as it stages it. Every pointer the cell
 * receives is owned here for the call; see {@link Cells} for the calling convention.
 */
final class CellsJvmPlanBackend extends YamlPlan.Backend {
  private final JvmPlanBackend backend;
  private final YamlCellsJvmTruffle.typed_decode_Cell decode;

  /** plan, data*, length*, result*: reused, a plan is confined to its thread (see Cells). */
  private final @Nullable Object[] decodeArguments;

  /** Takes over {@code backend}: releases it if the cell cannot be set up. */
  CellsJvmPlanBackend(JvmPlanBackend backend) {
    this.backend = backend;
    try {
      decode = Cells.adopt(new YamlCellsJvmTruffle.typed_decode_Cell());
      decodeArguments = new Object[] {NativePointer.of(backend.handle().address()), null, null, null};
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
        @Nullable Object[] arguments = decodeArguments;
        int status;
        try {
          arguments[1] = NativePointer.of(data.address());
          arguments[2] = (long) utf8.length;
          arguments[3] = NativePointer.of(result.address());
          status = decode.executeInt(arguments);
        } finally {
          arguments[1] = null;
          arguments[3] = null;
        }
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
