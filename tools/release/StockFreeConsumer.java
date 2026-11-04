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
    var model = WellKnownContracts.create(null, Limits.DEFAULT, 32, 1_000_000);
    var timestamp = model.type("google.protobuf.Timestamp");
    var getters = new WellKnownContracts.Access_google$protobuf$Timestamp(timestamp);
    var value = timestamp.parseText("seconds: 123 nanos: 456");
    byte[] wire = timestamp.encode(value);
    check(getters.get_seconds(timestamp.decode(wire)) == 123, "binary scalar");
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
      TypedRow borrowed;
      try (var message = plan.decode(input, 4096)) {
        borrowed = message.row();
        check(getters.get_seconds(borrowed) == 123, "borrowed scalar");
        check(getters.get_nanos(borrowed) == 456, "borrowed nanos");
        check(Arrays.equals(wire, timestamp.encode(message.snapshot())), "detach");
      }
      rejects(() -> getters.get_seconds(borrowed), IllegalStateException.class);
      try (var text = plan.parseText("seconds: 123 nanos: 456", 4096);
          var json = plan.parseJson("\"1970-01-01T00:02:03.000000456Z\"", 4096)) {
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
