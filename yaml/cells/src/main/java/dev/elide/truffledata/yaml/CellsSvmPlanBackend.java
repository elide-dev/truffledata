package dev.elide.truffledata.yaml;

import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.yaml.cells.generated.YamlCellsTruffle;
import dev.elide.truffledata.yaml.generated.Yaml;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.jspecify.annotations.Nullable;

/**
 * The static plan with the cells route: {@code typed_decode} goes through the image's shared cell
 * ({@link CellsSvm}, {@code executeInt}); the plan itself (compilation and release) is the static {@link SvmPlanBackend}'s,
 * wrapped, so contracts, errors and the owner are the same. The input is pinned around the call,
 * as the static plan pins it, and lent as a pointer; see {@link Cells} for the calling convention.
 */
final class CellsSvmPlanBackend extends YamlPlan.Backend {
  private final SvmPlanBackend backend;
  private final YamlCellsTruffle.typed_decode_Cell decode;

  /** plan, data*, length*, result*: reused, a plan is confined to its thread (see Cells). */
  private final @Nullable Object[] decodeArguments;

  private CellsSvmPlanBackend(CellsSvm cells, SvmPlanBackend backend) {
    this.backend = backend;
    decode = cells.typedDecode;
    decodeArguments = new Object[] {NativePointer.of(backend.handle().rawValue()), null, null, null};
  }

  static YamlPlan create(CellsSvm cells, TypedLayout layout, YamlOptions options) {
    YamlPlan plan = SvmPlanBackend.create(layout, options);
    try {
      return new YamlPlan(
          layout.records,
          new CellsSvmPlanBackend(cells, (SvmPlanBackend) plan.backend()),
          options.limits().maxInputBytes());
    } catch (RuntimeException | Error failure) {
      plan.close();
      throw failure;
    }
  }

  @Override
  TypedOwner decode(byte[] utf8, Schema[] schemas) {
    VoidPointer result = UnmanagedMemory.calloc(Yaml.YamlTypedResult.SIZE);
    if (result.isNull()) throw new OutOfMemoryError("YAML result allocation");
    try {
      @Nullable Object[] arguments = decodeArguments;
      int status;
      PinnedObject input = PinnedObject.create(utf8);
      try {
        arguments[1] = NativePointer.of(input.addressOfArrayElement(0).rawValue());
        arguments[2] = (long) utf8.length;
        arguments[3] = NativePointer.of(result.rawValue());
        status = decode.executeInt(arguments);
      } finally {
        arguments[1] = null;
        arguments[3] = null;
        input.close();
      }
      SvmSessionBackend.checkTyped(status, result);
      return new SvmTypedOwner(result, schemas);
    } catch (RuntimeException | Error failure) {
      Yaml.typed_release(result);
      UnmanagedMemory.free(result);
      throw failure;
    }
  }

  @Override
  void release() {
    backend.release();
  }
}
