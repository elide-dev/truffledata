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
      try (var compiled = decoder.compile(plan, 65536);
          var view = compiled.decodeView(first, 65536);
          var retained = backend.typed(schema, first, 0, empty, plan, 65536).detach()) {
        if (!view.nativeBacked()) throw new AssertionError("native view selected");
        byte[] expected = new byte[retained.bytes.capacity()];
        retained.bytes.get(0, expected);
        for (int i = 0; i < 8; i++) {
          try (var scratch = backend.typed(schema, second, 0, empty, plan, 65536)) {
            if (scratch.count != 1) throw new AssertionError("scratch count");
          }
        }
        try (var other = backend.typed(schema, second, 0, empty, plan, 65536).detach()) {
          decoder.close();
          if (view.row().getLong(0) != 42) throw new AssertionError("view after decoder close");
          for (int i = 0; i < expected.length; i++)
            if (retained.bytes.get(i) != expected[i])
              throw new AssertionError("retained index changed");
          if (other.count != 1 || other.bytes.getLong(8) != 99)
            throw new AssertionError("second retained index");
          retained.close();
          retained.close();
          if (other.bytes.getLong(8) != 99) throw new AssertionError("independent index ownership");
        }
      }
    }
  }
}
