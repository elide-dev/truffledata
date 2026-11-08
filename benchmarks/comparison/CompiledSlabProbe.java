package dev.elide.truffledata.comparison;

import com.google.protobuf.SourceContext;
import com.google.protobuf.Syntax;
import com.google.protobuf.Type;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.generated.ComparisonContracts;
import dev.elide.truffledata.protobuf.*;
import java.util.*;
import java.util.function.Supplier;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A4 experiment. No runtime decoder selection changes unless both performance gates pass. */
@NullMarked
public final class CompiledSlabProbe {
  private static volatile Object retained = new Object();

  private CompiledSlabProbe() {}

  public static void main(String[] args) {
    try (var context =
        Context.newBuilder("td-compare")
            .allowExperimentalOptions(true)
            .option("engine.BackgroundCompilation", "false")
            .option("engine.SingleTierCompilationThreshold", "100000000")
            .option("engine.MultiTier", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .build()) {
      context.initialize("td-compare");
      context.enter();
      try {
        var plan =
            ComparisonContracts.create(ComparisonLanguage.current(), Limits.DEFAULT, 32, 1 << 20)
                .type("google.protobuf.Type");
        var host =
            Type.newBuilder()
                .setName("catalog-λ")
                .setSyntax(Syntax.SYNTAX_PROTO3)
                .setSourceContext(SourceContext.newBuilder().setFileName("schema.proto"));
        for (int i = 0; i < 6; i++)
          host.addFields(
              com.google.protobuf.Field.newBuilder()
                  .setName("column-" + i)
                  .setNumber(i + 1)
                  .setKind(com.google.protobuf.Field.Kind.TYPE_INT64));
        byte[] wire = host.build().toByteArray();
        var generated = new ComparisonContracts.SlabDecoder_google$protobuf$Type(plan);
        var candidate = new Candidate(plan);
        verify(candidate, wire);
        boolean compiled = compile(candidate, wire, "type");
        measure("generated", () -> generated.decode(wire));
        measure("table", () -> SlabCodec.decode(plan.layout(), wire));
        if (compiled) measure("compiled", () -> candidate.decode(wire));
        var fields = new ArrayList<ProtoPlan.FieldPlan>();
        for (int i = 0; i < 64; i++)
          fields.add(
              new ProtoPlan.FieldPlan(
                  "f" + i,
                  i + 1,
                  ProtoPlan.Kind.INT32,
                  false,
                  false,
                  false,
                  false,
                  false,
                  -1,
                  "",
                  null,
                  0L));
        var widePlan =
            new ProtoPlan.Model(
                    ComparisonLanguage.current(),
                    List.of(new ProtoPlan.Type("Wide", false, fields)),
                    Limits.DEFAULT,
                    8,
                    65536)
                .type("Wide");
        var row = widePlan.newBuilder();
        for (int i = 0; i < 64; i++) row.set(i, (long) i + 1);
        byte[] wideWire = widePlan.encode(row.build());
        var wide = new Candidate(widePlan);
        verify(wide, wideWire);
        compile(wide, wideWire, "wide64");
      } finally {
        context.leave();
      }
    }
  }

  private static void verify(Candidate candidate, byte[] fixture) {
    var random = new Random(0xa4);
    for (int i = 0; i < 2048; i++) {
      byte[] wire = i == 0 ? fixture : Arrays.copyOf(fixture, random.nextInt(fixture.length + 1));
      if (i > 0 && wire.length > 0 && (i & 1) == 0)
        wire[random.nextInt(wire.length)] ^= (byte) (1 << random.nextInt(8));
      byte[] expected;
      try {
        expected = SlabCodec.decode(candidate.plan.layout(), wire);
      } catch (dev.elide.truffledata.ContractViolation invalid) {
        try {
          candidate.decode(wire);
          throw new AssertionError("Compiled candidate accepted invalid input");
        } catch (dev.elide.truffledata.ContractViolation expectedFailure) {
          continue;
        }
      }
      if (!Arrays.equals(expected, candidate.decode(wire)))
        throw new AssertionError("Compiled slab byte mismatch");
    }
  }

  private static boolean compile(Candidate candidate, byte[] wire, String name) {
    try {
      for (int i = 0; i < 10000; i++) retained = candidate.decode(wire);
      candidate.target.compile(true);
      for (int i = 0; i < 1000; i++) retained = candidate.decode(wire);
      boolean valid = candidate.target.isValidLastTier();
      System.out.printf(
          Locale.ROOT,
          "{\"case\":\"compile-%s\",\"runtime\":\"%s\",\"compiled\":%s,\"graph_budget\":10000}%n",
          name,
          runtime(),
          valid);
      return valid;
    } catch (RuntimeException failed) {
      System.out.printf(
          Locale.ROOT,
          "{\"case\":\"compile-%s\",\"runtime\":\"%s\",\"compiled\":false,\"graph_budget\":10000,\"failure_type\":\"%s\"}%n",
          name,
          runtime(),
          failed.getClass().getName());
      failed.printStackTrace(System.err);
      return false;
    }
  }

  private static String runtime() {
    return ImageInfo.inImageCode() ? "svm" : "jvm";
  }

  private static void measure(String name, Supplier<Object> operation) {
    long until = System.nanoTime() + Long.getLong("compiledSlab.warmupMillis", 3000) * 1_000_000;
    while (System.nanoTime() < until) retained = operation.get();
    double[] ns = new double[5];
    for (int sample = 0; sample < ns.length; sample++) {
      long start = System.nanoTime(), count = 0, end;
      do {
        for (int i = 0; i < 32; i++) retained = operation.get();
        count += 32;
        end = System.nanoTime();
      } while (end - start < 250_000_000);
      ns[sample] = (double) (end - start) / count;
    }
    System.out.printf(
        Locale.ROOT,
        "{\"case\":\"%s\",\"runtime\":\"%s\",\"ns_per_op\":%s}%n",
        name,
        runtime(),
        Arrays.toString(ns));
  }

  private static final class Candidate {
    final ProtoPlan plan;
    final OptimizedCallTarget target;
    final SlabLimits limits;

    Candidate(ProtoPlan plan) {
      this.plan = plan;
      limits = new SlabLimits(plan.maxDepth() - 1, 1 << 20);
      Layout layout = plan.layout();
      TypeRoot[] types = new TypeRoot[layout.types()];
      for (int i = 0; i < types.length; i++) types[i] = new TypeRoot(layout, i);
      for (var type : types) type.link(types);
      target = (OptimizedCallTarget) new EntryRoot(layout, types[0], limits).getCallTarget();
    }

    byte[] decode(byte[] wire) {
      return (byte[]) target.call(wire);
    }
  }

  private static final class EntryRoot extends RootNode {
    final Layout layout;
    final SlabLimits limits;
    @Child DirectCallNode body;
    int lastLength, lastExtent;

    EntryRoot(Layout layout, TypeRoot root, SlabLimits limits) {
      super(ComparisonLanguage.current());
      this.layout = layout;
      this.limits = limits;
      body = DirectCallNode.create(root.getCallTarget());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      byte[] wire = (byte[]) frame.getArguments()[0];
      var writer =
          new GeneratedSlabWriter(
              layout, wire, 0, wire.length, limits, wire.length == lastLength ? lastExtent : 0);
      try {
        body.call(writer, wire.length, 0);
        byte[] result = writer.finish();
        lastLength = wire.length;
        lastExtent = result.length;
        return result;
      } catch (GeneratedSlabWriter.Fallback restart) {
        return table(layout, wire, limits);
      }
    }

    @TruffleBoundary
    static byte[] table(Layout layout, byte[] wire, SlabLimits limits) {
      return SlabCodec.decode(layout, wire, 0, wire.length, limits);
    }
  }

  private static final class TypeRoot extends RootNode {
    final Layout layout;
    final int type, recordBytes;

    @CompilationFinal(dimensions = 1)
    final ProtoPlan.FieldPlan[] fields;

    @CompilationFinal(dimensions = 1)
    final int[] slots;

    @CompilationFinal(dimensions = 1)
    final int[] widths;

    @Children @Nullable DirectCallNode[] children;

    TypeRoot(Layout layout, int type) {
      super(ComparisonLanguage.current());
      this.layout = layout;
      this.type = type;
      ProtoPlan plan = layout.plan(type);
      recordBytes = layout.recordBytes(type);
      fields = new ProtoPlan.FieldPlan[plan.size()];
      slots = new int[plan.size()];
      widths = new int[plan.size()];
      children = new DirectCallNode[plan.size()];
      for (int i = 0; i < fields.length; i++) {
        fields[i] = plan.field(i);
        slots[i] = layout.slot(type, i);
        widths[i] = layout.kind(type, i).element;
      }
    }

    void link(TypeRoot[] roots) {
      for (int i = 0; i < fields.length; i++)
        if (fields[i].kind().message())
          children[i] = DirectCallNode.create(roots[layout.childType(type, i)].getCallTarget());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var writer = (GeneratedSlabWriter) frame.getArguments()[0];
      int end = (Integer) frame.getArguments()[1], depth = (Integer) frame.getArguments()[2];
      int record = writer.record(recordBytes, depth);
      long[][] pending = new long[fields.length][];
      int[] counts = new int[fields.length];
      while (writer.position < end) {
        int tag = writer.tag(end);
        field(writer, record, end, depth, tag, pending, counts);
      }
      finish(writer, record, pending, counts);
      return record;
    }

    @ExplodeLoop
    private void field(
        GeneratedSlabWriter writer,
        int record,
        int end,
        int depth,
        int tag,
        long[][] pending,
        int[] counts) {
      for (int ordinal = 0; ordinal < fields.length; ordinal++) {
        var field = fields[ordinal];
        boolean packed = field.repeated() && field.kind().packable() && (tag & 7) == 2;
        if ((tag >>> 3) != field.number()) continue;
        if (field.map()
            || field.oneof() >= 0
            || field.kind() == ProtoPlan.Kind.GROUP
            || (!packed && (tag & 7) != wireType(field.kind())))
          throw GeneratedSlabWriter.fallback();
        int slot = record + slots[ordinal];
        if (packed) throw GeneratedSlabWriter.fallback();
        long bits;
        if (field.kind().message()) {
          if (!field.repeated() && writer.get32(slot) != 0) throw GeneratedSlabWriter.fallback();
          writer.descend(depth);
          int length = writer.length(end), childEnd = writer.position + length;
          bits =
              (Integer) Objects.requireNonNull(children[ordinal]).call(writer, childEnd, depth + 1);
        } else bits = scalar(writer, field.kind(), end);
        if (field.enumType() != null && !field.enumType().accepts((int) bits))
          throw GeneratedSlabWriter.fallback();
        if (field.repeated()) {
          long[] values = pending[ordinal];
          pending[ordinal] =
              writer.append(
                  values == null ? GeneratedSlabWriter.EMPTY : values,
                  counts[ordinal]++,
                  bits,
                  widths[ordinal]);
        } else {
          if (widths[ordinal] == 8) writer.put64(slot, bits);
          else writer.put32(slot, (int) bits);
          writer.presence(
              record, ordinal, field.explicitPresence() || nonDefault(writer, field.kind(), bits));
        }
        return;
      }
      throw GeneratedSlabWriter.fallback();
    }

    @ExplodeLoop
    private void finish(GeneratedSlabWriter writer, int record, long[][] pending, int[] counts) {
      for (int i = 0; i < fields.length; i++) {
        if (counts[i] != 0) {
          writer.put32(
              record + slots[i],
              writer.list(Objects.requireNonNull(pending[i]), counts[i], widths[i]));
          writer.presence(record, i, true);
        }
        if (fields[i].required()) writer.required(record, i);
      }
    }

    private static int wireType(ProtoPlan.Kind kind) {
      return switch (kind) {
        case DOUBLE, FIXED64, SFIXED64 -> 1;
        case STRING, BYTES, MESSAGE -> 2;
        case GROUP -> 3;
        case FLOAT, FIXED32, SFIXED32 -> 5;
        default -> 0;
      };
    }

    private static long scalar(GeneratedSlabWriter writer, ProtoPlan.Kind kind, int end) {
      return switch (kind) {
        case INT32, ENUM -> (int) writer.varint(end);
        case UINT32 -> writer.varint(end) & 0xffffffffL;
        case INT64, UINT64 -> writer.varint(end);
        case SINT32 -> {
          int value = (int) writer.varint(end);
          yield (value >>> 1) ^ -(value & 1);
        }
        case SINT64 -> {
          long value = writer.varint(end);
          yield (value >>> 1) ^ -(value & 1);
        }
        case BOOL -> writer.varint(end) == 0 ? 0 : 1;
        case FIXED32, SFIXED32, FLOAT -> writer.fixed32(end);
        case FIXED64, SFIXED64, DOUBLE -> writer.fixed64(end);
        case STRING, BYTES -> writer.string(end, kind == ProtoPlan.Kind.STRING);
        default -> throw GeneratedSlabWriter.fallback();
      };
    }

    private static boolean nonDefault(GeneratedSlabWriter writer, ProtoPlan.Kind kind, long bits) {
      return switch (kind) {
        case STRING, BYTES -> writer.stringLength((int) bits) != 0;
        case FLOAT -> Float.intBitsToFloat((int) bits) != 0.0f;
        case DOUBLE -> Double.longBitsToDouble(bits) != 0.0;
        default -> bits != 0;
      };
    }
  }
}
