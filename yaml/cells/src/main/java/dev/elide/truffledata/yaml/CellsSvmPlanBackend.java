package dev.elide.truffledata.yaml;

import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.yaml.cells.generated.YamlCellsTruffle;
import dev.elide.truffledata.yaml.generated.Yaml;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;

/**
 * The static plan with the cells route: {@code typed_decode} goes through the cell's static {@code
 * invoke} entry (raw addresses, nothing allocated; see {@link CellsSvmSessionBackend}); the plan
 * itself (compilation and release) is the static {@link SvmPlanBackend}'s, wrapped, so contracts,
 * errors and the owner are the same. The input is pinned around the call, as the static plan pins
 * it, and lent without a copy; the plan handle and the result outlive the call.
 */
final class CellsSvmPlanBackend extends YamlPlan.Backend {
  private final SvmPlanBackend backend;

  /** The plan handle: it lives as long as {@link #backend}. */
  private final long plan;

  private CellsSvmPlanBackend(SvmPlanBackend backend) {
    this.backend = backend;
    plan = backend.handle().rawValue();
  }

  static YamlPlan create(TypedLayout layout, YamlOptions options) {
    YamlPlan plan = SvmPlanBackend.create(layout, options);
    try {
      return new YamlPlan(
          layout.records,
          new CellsSvmPlanBackend((SvmPlanBackend) plan.backend()),
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
      int status;
      PinnedObject input = PinnedObject.create(utf8);
      try {
        status =
            YamlCellsTruffle.typed_decode_Cell.invoke(
                plan, input.addressOfArrayElement(0).rawValue(), utf8.length, result.rawValue());
      } finally {
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
