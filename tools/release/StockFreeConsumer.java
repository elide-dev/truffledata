import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.*;
import dev.elide.truffledata.generated.WellKnownContracts;
import dev.elide.truffledata.protobuf.*;
import java.util.Arrays;

/** Runs from publication jars and generated contracts, with no stock protobuf runtime. */
public final class StockFreeConsumer {
  private StockFreeConsumer() {}

  public static void main(String[] args) throws Exception {
    for (String forbidden :
        new String[] {
          "com.google.protobuf.Message",
          "com.google.protobuf.util.JsonFormat",
          "dev.elide.truffledata.protobuf.ProtoCompiler"
        }) {
      try {
        Class.forName(forbidden);
        throw new AssertionError("Unexpected compatibility runtime: " + forbidden);
      } catch (ClassNotFoundException expected) {
        // The standalone consumer must resolve neither stock nor our compatibility bridge.
      }
    }
    var descriptorPath =
        java.nio.file.Path.of(
            java.util.Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            java.util.Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "tests/native.pb");
    var runtimePlan =
        ProtoDescriptors.compile(
            null,
            java.nio.file.Files.readAllBytes(descriptorPath),
            "truffledata.lossless.Flat",
            Limits.DEFAULT,
            32,
            1_000_000);
    var runtimeRow = runtimePlan.parseText("i32: 123 text: 'stock-free'");
    check(
        runtimePlan
            .decode(runtimePlan.encode(runtimeRow))
            .get(runtimePlan.schema().indexOf("text"))
            .equals("stock-free"),
        "independent runtime descriptors");
    var model = WellKnownContracts.create(null, Limits.DEFAULT, 32, 1_000_000);
    var timestamp = model.type("google.protobuf.Timestamp");
    var getters = new WellKnownContracts.Access_google$protobuf$Timestamp(timestamp);
    var value = timestamp.parseText("seconds: 123 nanos: 456");
    byte[] wire = timestamp.encode(value);
    var slabDecoder = new WellKnownContracts.SlabDecoder_google$protobuf$Timestamp(timestamp);
    check(
        Arrays.equals(slabDecoder.decode(wire), SlabCodec.decode(timestamp.layout(), wire)),
        "generated native-free slab producer");
    var durable = timestamp.decodeSlab(wire);
    check(durable.getLong(0) == 123 && durable.snapshot() == durable, "durable Java slab");
    check(Arrays.equals(wire, durable.encode()), "slab serialization without stock runtime");
    var generated = getters.decode(wire);
    check(
        generated.get_seconds() == 123 && generated.get_nanos() == 456, "generated inline fields");
    check(Arrays.equals(wire, generated.encode()), "generated serialization");
    check(
        DataLibrary.factory().getUncached().readLong(generated, 0) == 123,
        "generated library export");
    check(getters.get_seconds(timestamp.decode(wire)) == 123, "binary scalar");
    var envelope = model.type("truffledata.wkt.Envelope");
    var graphAccess = new WellKnownContracts.Access_truffledata$wkt$Envelope(envelope);
    byte[] graphWire =
        envelope.encode(
            envelope.parseText(
                "when {seconds: 7} values {string_value: 'first'} values {bool_value: true}"
                    + " counts {value: 1} counts {value: 2}"));
    var graph = graphAccess.decode(graphWire);
    check(
        graph.get_values().size() == 2 && graph.get_counts().size() == 2, "generated graph arrays");
    check(
        ((WellKnownContracts.Message_google$protobuf$Timestamp) graph.get_when()).get_seconds()
            == 7,
        "generated nested concrete message");
    check(Arrays.equals(graphWire, graph.encode()), "generated graph serialization");
    check(
        getters.get_nanos(timestamp.parseJson("\"1970-01-01T00:02:03.000000456Z\"")) == 456,
        "well-known JSON");
    var string = model.type("google.protobuf.StringValue");
    check(
        string.parseText("value: '\\u20ac' '\\U0001f600'").get(0).equals("€😀"), "Unicode escapes");
    check(string.parseText("value: '\\x41\\101'").get(0).equals("AA"), "byte escapes");
    check(
        string
            .parseText("value: '\\a\\b\\f\\n\\r\\t\\v\\?'")
            .get(0)
            .equals("\u0007\b\f\n\r\t\u000b?"),
        "control escapes");
    check(string.parseText("value: '中' \"x\"").get(0).equals("中x"), "adjacent strings");
    var bytes = model.type("google.protobuf.BytesValue");
    check(bytes.parseText("value: '\\377\\0'").getSize(0) == 2, "arbitrary bytes");
    for (String invalid :
        new String[] {"\\uD800", "\\U00110000", "\\u000", "\\x", "\\q", "\\377"}) {
      rejects(() -> string.parseText("value: '" + invalid + "'"), ContractViolation.class);
    }
    // Unknown fields survive an owned decode/encode cycle without stock messages.
    byte[] unknown = Arrays.copyOf(wire, wire.length + 2);
    unknown[wire.length] = 120;
    unknown[wire.length + 1] = 7;
    check(
        Arrays.equals(unknown, timestamp.encode(timestamp.decode(unknown))),
        "unknown preservation");
    boolean nativeExpected = args.length > 0 && args[0].equals("native");
    try (var decoder =
            BuffaWire.open(nativeExpected ? BuffaWire.Mode.NATIVE : BuffaWire.Mode.JAVA, null);
        var plan = decoder.compile(timestamp, 1_000_000);
        var input = decoder.allocate(wire.length).put(wire)) {
      check((decoder.selected() != BuffaWire.Selected.JAVA) == nativeExpected, "backend");
      var durableBackend = plan.decodeSlab(input, 4096);
      check(durableBackend.getLong(0) == 123, "durable backend slab");
      check(Arrays.equals(wire, durableBackend.encode()), "durable backend serialization");
      SlabMessage durableAfterClose;
      try (var message = plan.decode(input, 4096)) {
        durableAfterClose = message;
        check(getters.get_seconds(message.row()) == 123, "durable scalar");
        check(getters.get_nanos(message.row()) == 456, "durable nanos");
        check(message.snapshot() == message, "identity snapshot");
        check(Arrays.equals(wire, timestamp.encode(message.snapshot())), "detach");
        check(Arrays.equals(wire, message.encode()), "durable serialization");
      }
      check(getters.get_seconds(durableAfterClose) == 123, "durable row survives close");
      try (var text = plan.parseText("seconds: 123 nanos: 456", 4096);
          var json = plan.parseJson("\"1970-01-01T00:02:03.000000456Z\"", 4096)) {
        check(Arrays.equals(wire, text.encode()), "text serialization");
        check(Arrays.equals(wire, json.encode()), "JSON serialization");
        check(getters.get_seconds(text.row()) == 123, "backend text");
        check(getters.get_nanos(json.row()) == 456, "backend JSON");
      }
    }
    System.out.println("STOCK_FREE consumer passed native=" + nativeExpected);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void rejects(Runnable operation, Class<? extends RuntimeException> type) {
    try {
      operation.run();
    } catch (RuntimeException error) {
      if (type.isInstance(error)) return;
      throw error;
    }
    throw new AssertionError("Expected " + type.getSimpleName());
  }
}
