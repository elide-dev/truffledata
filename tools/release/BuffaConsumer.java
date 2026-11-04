import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.buffa.BuffaWire;
import dev.elide.truffledata.protobuf.ProtoPlan;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.nativeimage.ImageInfo;

/** Uses only publication jars and native attachments, not the source libraries. */
public final class BuffaConsumer {
  public static void main(String[] args) throws Exception {
    var field =
        new ProtoPlan.FieldPlan(
            "count", 1, ProtoPlan.Kind.INT32, false, false, false, false, false, -1, "", null, 0L);
    var plan =
        new ProtoPlan.Model(
                null,
                List.of(new ProtoPlan.Type("Consumer", false, List.of(field))),
                Limits.DEFAULT,
                8,
                4096)
            .type("Consumer");
    Path library = ImageInfo.inImageCode() ? null : Path.of(args[0]);
    for (var mode : List.of(BuffaWire.Mode.NATIVE, BuffaWire.Mode.JAVA)) {
      try (var decoder = BuffaWire.open(mode, library);
          var compiled = decoder.compile(plan, 4096);
          var input = decoder.allocate(2).put(new byte[] {8, 42})) {
        for (int format = 0; format < 3; format++) {
          try (var message =
              format == 0
                  ? compiled.decode(input, 4096)
                  : format == 1
                      ? compiled.parseText("count: 42", 4096)
                      : compiled.parseJson("{\"count\":42}", 4096)) {
            if (DataLibrary.factory().getUncached().readLong(message.row(), 0) != 42)
              throw new AssertionError("Published Buffa input format " + format);
          }
        }
      }
    }
    System.out.println(
        "Published Buffa native and Java routes passed; SVM=" + ImageInfo.inImageCode());
  }
}
