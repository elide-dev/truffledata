package dev.elide.truffledata.buffa;

import dev.elide.truffledata.buffa.generated.Buffa;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.PublishProbe;
import dev.elide.truffledata.protobuf.SlabLimits;
import dev.elide.truffledata.protobuf.SlabMessage;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.NullMarked;

/**
 * Benchmark-only SVM decode pipeline ablation (plan 2026-11-08-beat-stock-svm P0.1). Lives in the
 * benchmark target only (split package for access to package-private plumbing); no production
 * class can reach it. Each kind stops the owning-slab pipeline of {@code SvmBackend.slab} after
 * one stage, or isolates one per-call cost of the session path, with the same native producer,
 * compiled layout, limits and learned capacity as the production decode.
 */
@NullMarked
@SuppressWarnings({"removal", "deprecation"})
public final class DecodeProbe implements AutoCloseable {
  /** Probe engines in pipeline order; the consumer harness names them {@code probe-<name>}. */
  public static final List<String> KINDS =
      List.of(
          "empty",
          "lease",
          "transition",
          "leaf-call",
          "pin-only",
          "native-leaf",
          "native",
          "alloc",
          "pin",
          "trim",
          "verify",
          "publish-generic",
          "publish",
          "trusted",
          "internal",
          "heap-pin2",
          "heap-copy-in",
          "copy-out",
          "copy-out-publish",
          "copy-out-leaf-publish",
          "trusted-copy-out-leaf",
          "session-raw");

  private static final Object MARK = Boolean.TRUE;

  private final BuffaWire.Backend backend;
  private final ProtoPlan plan;
  private final SlabLimits limits;
  private final long compiled;
  private final Object callHandle;
  private final int capacity;
  private final long result;
  private final long scratch;
  private final ByteBuffer scratchBytes;
  private final long inputScratch;
  private final ByteBuffer inputBytes;
  private final byte[] pinTarget;
  private final WireInput arena;
  private long sink;
  private int trims;
  private int extent;

  /**
   * @param capacity the plan's learned first-attempt capacity (the production steady state: the
   *     largest slab extent of the fixture), so every probe decode succeeds on its first attempt.
   */
  public DecodeProbe(
      BuffaWire decoder, ProtoPlan plan, int maxSlabBytes, int capacity, int maxWireBytes) {
    if (!ImageInfo.inImageRuntimeCode())
      throw new UnsupportedOperationException("Decode probes are SVM-only");
    backend = java.util.Objects.requireNonNull(decoder.backend);
    this.plan = plan;
    limits = new SlabLimits(plan.maxDepth() - 1, maxSlabBytes);
    this.capacity = capacity;
    // The program BuffaPlan compiles for its slab layout (binary Any payloads stay bytes).
    try (var schema = decoder.allocate(program(plan).length).put(program(plan))) {
      compiled = backend.compileLayout(schema);
    }
    callHandle = backend.prepareSlab(compiled);
    VoidPointer slot = UnmanagedMemory.calloc(Buffa.Result.SIZE + Buffa.SlabResult.SIZE);
    result = slot.rawValue();
    scratch = UnmanagedMemory.malloc(Math.max(1, capacity)).rawValue();
    scratchBytes = CTypeConversion.asByteBuffer(WordFactory.pointer(scratch), capacity);
    inputScratch = UnmanagedMemory.malloc(Math.max(1, maxWireBytes)).rawValue();
    inputBytes = CTypeConversion.asByteBuffer(WordFactory.pointer(inputScratch), maxWireBytes);
    pinTarget = new byte[capacity];
    arena = backend.allocate(capacity);
  }

  /** The schema program of {@code BuffaPlan}'s native slab layout, for standalone runs. */
  public static byte[] program(ProtoPlan plan) {
    return NativeSchema.encode(NativeSchema.types(plan, List.of(), 65536), List.of(), 65536);
  }

  public SlabLimits limits() {
    return limits;
  }

  /** Rows of the last pass whose output went through the production trim copy. */
  public int trims() {
    int value = trims;
    trims = 0;
    return value;
  }

  private static long address(WireInput input) {
    if (!(input.lease.attachment instanceof BuffaSvm.Address address))
      throw new IllegalArgumentException("Input is not SVM native memory");
    return address.value();
  }

  private int decode(long input, int length, long output, boolean leaf) {
    VoidPointer slot = WordFactory.pointer(result + Buffa.Result.SIZE);
    CCharPointer layout = WordFactory.pointer(compiled);
    int status =
        leaf
            ? Buffa.decodeSlabSmall(
                layout,
                WordFactory.pointer(input),
                length,
                1,
                WordFactory.nullPointer(),
                WordFactory.pointer(output),
                capacity,
                limits.maxDepth(),
                limits.maxSlabBytes(),
                slot)
            : Buffa.decodeSlab(
                layout,
                WordFactory.pointer(input),
                length,
                1,
                WordFactory.nullPointer(),
                WordFactory.pointer(output),
                capacity,
                limits.maxDepth(),
                limits.maxSlabBytes(),
                slot);
    long bytes = Buffa.SlabResult.bytes(slot);
    if (status != 0 || bytes <= 0 || bytes > capacity)
      throw new IllegalStateException("Probe decode failed: status " + status);
    return (int) bytes;
  }

  /** Decode into a fresh pinned {@code byte[capacity]}, as {@code SvmBackend.slab} does. */
  private byte[] pinned(long input, int length) {
    byte[] output = new byte[capacity];
    try (var pin = PinnedObject.create(output)) {
      extent = decode(input, length, pin.addressOfArrayElement(0).rawValue(), false);
    }
    return output;
  }

  private byte[] trim(byte[] output, int extent) {
    if (output.length - extent > Math.max(256, extent)) {
      trims++;
      return Arrays.copyOf(output, extent);
    }
    return output;
  }

  private byte[] copyOut(long input, int length, boolean leaf) {
    int extent = decode(input, length, scratch, leaf);
    byte[] output = new byte[extent];
    scratchBytes.get(0, output, 0, extent);
    return output;
  }

  /** One message through probe {@code kind} (an index into {@link #KINDS}). */
  public Object run(int kind, WireInput input, byte[] wire) {
    switch (kind) {
      case 0 -> {
        return MARK;
      }
      case 1 -> {
        // WireInput/lease handling of BuffaPlan.decodeSlab and SvmBackend.slab, no native call.
        var lease = input.retain();
        try {
          sink += address(input) + input.size();
        } finally {
          lease.release();
        }
        return MARK;
      }
      case 2 -> {
        // One ordinary (non-leaf) @CFunction transition into a native no-op.
        Buffa.release(WordFactory.pointer(result));
        return MARK;
      }
      case 3 -> {
        sink += Buffa.fingerprint().rawValue();
        return MARK;
      }
      case 4 -> {
        try (var pin = PinnedObject.create(pinTarget)) {
          sink += pin.addressOfArrayElement(0).rawValue();
        }
        return MARK;
      }
      case 5 -> {
        sink += decode(address(input), input.size(), scratch, true);
        return MARK;
      }
      case 6 -> {
        sink += decode(address(input), input.size(), scratch, false);
        return MARK;
      }
      case 7 -> {
        byte[] output = new byte[capacity];
        output[0] = (byte) decode(address(input), input.size(), scratch, false);
        return output;
      }
      case 8 -> {
        return pinned(address(input), input.size());
      }
      case 9 -> {
        byte[] output = pinned(address(input), input.size());
        return trim(output, extent);
      }
      case 10 -> {
        byte[] output = trim(pinned(address(input), input.size()), extent);
        sink += PublishProbe.verify(plan, output, extent, limits);
        return output;
      }
      case 11 -> {
        byte[] output = trim(pinned(address(input), input.size()), extent);
        return PublishProbe.generic(
            plan, output, extent, PublishProbe.verify(plan, output, extent, limits));
      }
      case 12 -> {
        byte[] output = trim(pinned(address(input), input.size()), extent);
        return SlabMessage.takeOwnership(plan, output, extent, limits);
      }
      case 13 -> {
        // What WS2 would publish: no Java walk at all (benchmark-only; needs a ruling).
        byte[] output = trim(pinned(address(input), input.size()), extent);
        return PublishProbe.trusted(plan, output, extent);
      }
      case 14 -> {
        // Header check plus exact logical validation only (the Java INTERNAL decode path).
        byte[] output = trim(pinned(address(input), input.size()), extent);
        return PublishProbe.internal(plan, output, extent);
      }
      case 15 -> {
        // Heap input as the production heap path: both arrays pinned around one call.
        byte[] output = new byte[capacity];
        try (var in = PinnedObject.create(wire);
            var out = PinnedObject.create(output)) {
          extent =
              decode(
                  in.addressOfArrayElement(0).rawValue(),
                  wire.length,
                  out.addressOfArrayElement(0).rawValue(),
                  false);
        }
        return trim(output, extent);
      }
      case 16 -> {
        // Copy-instead-of-pin for the heap input: reusable native input scratch, output pinned.
        inputBytes.put(0, wire, 0, wire.length);
        return trim(pinned(inputScratch, wire.length), extent);
      }
      case 17 -> {
        // Copy-instead-of-pin for the output: reusable native scratch, exact-extent heap copy.
        return copyOut(address(input), input.size(), false);
      }
      case 18 -> {
        byte[] output = copyOut(address(input), input.size(), false);
        return SlabMessage.takeOwnership(plan, output, output.length, limits);
      }
      case 19 -> {
        byte[] output = copyOut(address(input), input.size(), true);
        return SlabMessage.takeOwnership(plan, output, output.length, limits);
      }
      case 20 -> {
        byte[] output = copyOut(address(input), input.size(), true);
        return PublishProbe.trusted(plan, output, output.length);
      }
      case 21 -> {
        // The session's native write (result slot, busy flag, arena checks) without its
        // validation, epoch, invalidation or borrowed message.
        sink += backend.writeSlab(callHandle, input, arena, limits, 1, null);
        return MARK;
      }
      default -> throw new IllegalArgumentException("Unknown probe");
    }
  }

  /** The probe's native decode equals the production slab byte for byte. */
  public void check(WireInput input, SlabMessage production) {
    int extent = decode(address(input), input.size(), scratch, false);
    byte[] expected = PublishProbe.bytes(production);
    byte[] actual = new byte[extent];
    scratchBytes.get(0, actual, 0, extent);
    if (extent != production.slabBytes()
        || !Arrays.equals(actual, 0, extent, expected, 0, extent))
      throw new AssertionError("Probe slab differs from the production slab");
  }

  public long sink() {
    return sink;
  }

  @Override
  public void close() {
    arena.close();
    backend.releaseLayout(compiled);
    UnmanagedMemory.free(WordFactory.pointer(inputScratch));
    UnmanagedMemory.free(WordFactory.pointer(scratch));
    UnmanagedMemory.free(WordFactory.pointer(result));
  }
}
