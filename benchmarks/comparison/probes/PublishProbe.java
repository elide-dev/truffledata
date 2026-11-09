package dev.elide.truffledata.protobuf;

import dev.elide.truffledata.ContractViolation;
import org.jspecify.annotations.NullMarked;

/**
 * Benchmark-only access to the owning-slab publication stages (plan 2026-11-08-beat-stock-svm
 * P0.1). Lives in the benchmark target only (split package); no production class can reach it.
 */
@NullMarked
public final class PublishProbe {
  private PublishProbe() {}

  /** The retained-size check and structural walk of {@link SlabMessage#takeOwnership}. */
  public static int verify(ProtoPlan plan, byte[] slab, int extent, SlabLimits limits) {
    if (slab.length > limits.maxSlabBytes())
      throw new ContractViolation("Retained slab byte budget exceeded");
    return SlabVerifier.verifyOwning(slab, extent, plan.layout(), limits);
  }

  /** Publication without the generated-root factory: always the generic root class. */
  public static SlabMessage generic(ProtoPlan plan, byte[] slab, int extent, int verification) {
    return new SlabMessage(plan.layout(), slab, extent, verification);
  }

  /** Publication with no Java walk at all (what WS2 would do for in-process slabs). */
  public static SlabMessage trusted(ProtoPlan plan, byte[] slab, int extent) {
    return SlabMessage.owning(plan.layout(), slab, extent, 0);
  }

  /** Header check plus exact logical validation, without the structural walk. */
  public static SlabMessage internal(ProtoPlan plan, byte[] slab, int extent) {
    return SlabMessage.owning(plan.layout(), slab, extent, SlabMessage.INTERNAL);
  }

  public static byte[] bytes(SlabMessage message) {
    return message.bytes;
  }
}
