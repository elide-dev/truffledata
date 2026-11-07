package dev.elide.truffledata.buffa;

import dev.elide.truffledata.Limits;
import dev.elide.truffledata.protobuf.ProtoPlan;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal ownership transfer checks, shared by the JVM and static SVM acceptance images. */
public final class NativeTapeAcceptance {
  private NativeTapeAcceptance() {}

  /** The schema program (TDB3 plus the TDL1 slab-layout section) a plan compiles to. */
  public static byte[] program(ProtoPlan plan, List<ProtoPlan> registry, int maximum) {
    return NativeSchema.encode(NativeSchema.types(plan, registry, maximum), registry, maximum);
  }

  public static void run(@Nullable Path library) {
    var field =
        new ProtoPlan.FieldPlan(
            "value", 1, ProtoPlan.Kind.INT32, false, false, true, false, false, -1, "", null, 0L);
    var plan =
        new ProtoPlan.Model(
                null,
                List.of(
                    new ProtoPlan.Type(
                        "Retained",
                        false,
                        List.of(
                            field,
                            new ProtoPlan.FieldPlan(
                                "second",
                                2,
                                ProtoPlan.Kind.INT32,
                                false,
                                false,
                                true,
                                false,
                                false,
                                -1,
                                "",
                                null,
                                0L),
                            new ProtoPlan.FieldPlan(
                                "third",
                                3,
                                ProtoPlan.Kind.INT32,
                                false,
                                false,
                                true,
                                false,
                                false,
                                -1,
                                "",
                                null,
                                0L)))),
                Limits.DEFAULT,
                16,
                65536)
            .type("Retained");
    byte[] program =
        NativeSchema.encode(NativeSchema.types(plan, List.of(), 65536), List.of(), 65536);
    try (var decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, library);
        var schema = decoder.allocate(program.length).put(program);
        var empty = decoder.allocate(0);
        var first = decoder.allocate(2).put(new byte[] {8, 42});
        var second = decoder.allocate(2).put(new byte[] {8, 99})) {
      var backend = Objects.requireNonNull(decoder.backend);
      try (var proof = backend.typed(schema, first, 3, empty, plan, 65536)) {
        if (!proof.viewReady()) throw new AssertionError("native binary view proof");
      }
      // ABI 9 views own one allocation each; decoder scratch (shared by eager and view decodes)
      // is reused underneath them without ever being retained by a result.
      long views = backend.compileView(schema);
      try (var compiled = decoder.compile(plan, 65536);
          var view = compiled.decodeView(first, 65536);
          var retained = Objects.requireNonNull(backend.view(views, first, plan, 65536, false))) {
        if (!view.nativeBacked()) throw new AssertionError("native view selected");
        if (!retained.viewReady() || retained.count() != 1 || retained.words != 3)
          throw new AssertionError("view directory extent");
        // One 24-byte entry plus three directory words, rounded up to whole entry slots.
        if (retained.allocated != 48)
          throw new AssertionError("exact view allocation");
        // Default decodes ask for a budget fallback instead of a ContractViolation; views do not.
        if (backend.view(views, second, plan, 1, true) != null)
          throw new AssertionError("budget fallback signal");
        try {
          backend.view(views, second, plan, 1, false);
          throw new AssertionError("strict view budget");
        } catch (dev.elide.truffledata.ContractViolation expected) {
          // The scoped view API keeps reporting native budget failures.
        }
        byte[] expected = new byte[retained.bytes().capacity()];
        retained.bytes().get(0, expected);
        for (int i = 0; i < 8; i++) {
          try (var scratch = backend.typed(schema, second, 0, empty, plan, 65536)) {
            if (scratch.count() != 1) throw new AssertionError("scratch count");
          }
          try (var again = Objects.requireNonNull(backend.view(views, second, plan, 65536, false))) {
            if (again.count() != 1) throw new AssertionError("view scratch count");
          }
        }
        try (var other = Objects.requireNonNull(backend.view(views, second, plan, 65536, false))) {
          decoder.close();
          if (view.row().getLong(0) != 42) throw new AssertionError("view after decoder close");
          for (int i = 0; i < expected.length; i++)
            if (retained.bytes().get(i) != expected[i])
              throw new AssertionError("retained index changed");
          if (other.count() != 1 || other.bytes().getLong(8) != 99)
            throw new AssertionError("second retained index");
          retained.close();
          retained.close();
          if (other.bytes().getLong(8) != 99)
            throw new AssertionError("independent index ownership");
        }
      } finally {
        // Compiled schemas and views stay releasable after their decoder closes.
        backend.releaseCompiled(views);
      }
    }
  }
}
