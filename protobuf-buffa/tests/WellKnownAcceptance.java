package dev.elide.truffledata.buffa.tests;

import com.google.protobuf.*;
import com.google.protobuf.util.JsonFormat;
import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.*;
import dev.elide.truffledata.generated.WellKnownContracts;
import dev.elide.truffledata.protobuf.*;
import java.util.*;

/** Real WKT schemas generated at build time, checked against protobuf-java on JVM and SVM. */
final class WellKnownAcceptance {
  private static Map<String, Message> defaults() {
    return Map.ofEntries(
        Map.entry("Timestamp", Timestamp.getDefaultInstance()),
        Map.entry("Duration", Duration.getDefaultInstance()),
        Map.entry("FieldMask", FieldMask.getDefaultInstance()),
        Map.entry("Struct", Struct.getDefaultInstance()),
        Map.entry("Value", Value.getDefaultInstance()),
        Map.entry("ListValue", ListValue.getDefaultInstance()),
        Map.entry("Any", Any.getDefaultInstance()),
        Map.entry("DoubleValue", DoubleValue.getDefaultInstance()),
        Map.entry("FloatValue", FloatValue.getDefaultInstance()),
        Map.entry("Int64Value", Int64Value.getDefaultInstance()),
        Map.entry("UInt64Value", UInt64Value.getDefaultInstance()),
        Map.entry("Int32Value", Int32Value.getDefaultInstance()),
        Map.entry("UInt32Value", UInt32Value.getDefaultInstance()),
        Map.entry("BoolValue", BoolValue.getDefaultInstance()),
        Map.entry("StringValue", StringValue.getDefaultInstance()),
        Map.entry("BytesValue", BytesValue.getDefaultInstance()),
        Map.entry("Field", com.google.protobuf.Field.getDefaultInstance()));
  }

  private WellKnownAcceptance() {}

  static ProtoPlan.Model model() {
    return WellKnownContracts.create(null, Limits.DEFAULT, 64, 1_000_000);
  }

  static void run(BuffaWire decoder) throws Exception {
    var model = model();
    var defaults = defaults();
    List<ProtoPlan> registry =
        defaults.keySet().stream().map(n -> model.type("google.protobuf." + n)).toList();
    var descriptors = TypeRegistry.newBuilder();
    for (var value : defaults.values()) descriptors.add(value.getDescriptorForType());
    var oracle = JsonFormat.parser().usingTypeRegistry(descriptors.build());
    Map<String, List<String>> cases =
        Map.ofEntries(
            Map.entry(
                "Timestamp",
                List.of(
                    "\"1970-01-01T00:00:00Z\"",
                    "\"0001-01-01T00:00:00Z\"",
                    "\"9999-12-31T23:59:59.999999999Z\"",
                    "\"2000-02-29T12:34:56.123456789+05:30\"",
                    "\"1969-12-31T23:59:59.000000001Z\"")),
            Map.entry(
                "Duration",
                List.of(
                    "\"0s\"", "\"-0.000000001s\"", "\"315576000000.999999999s\"", "\"-123.456s\"")),
            Map.entry(
                "FieldMask",
                List.of("\"\"", "\"fooBar,child.longName\"", "\"ABC,already_snake,,x\"")),
            Map.entry(
                "Struct", List.of("{}", "{\"a\":null,\"b\":true,\"c\":[1,\"NaN\",{\"x\":false}]}")),
            Map.entry(
                "Value", List.of("null", "true", "1.25", "\"NaN\"", "[]", "{\"x\":[null,{},[]]}")),
            Map.entry("ListValue", List.of("[]", "[null,false,-1.25,\"x\",{},[null]]")),
            Map.entry("DoubleValue", List.of("1.25", "\"NaN\"", "\"-Infinity\"")),
            Map.entry("FloatValue", List.of("1.25", "\"Infinity\"")),
            Map.entry("Int64Value", List.of("\"-9223372036854775808\"", "\"9223372036854775807\"")),
            Map.entry("UInt64Value", List.of("\"18446744073709551615\"")),
            Map.entry("Int32Value", List.of("-2147483648", "2147483647", "0")),
            Map.entry("UInt32Value", List.of("4294967295")),
            Map.entry("BoolValue", List.of("true", "false")),
            Map.entry("StringValue", List.of("\"hello €\\n\"", "\"\"")),
            Map.entry("BytesValue", List.of("\"Af8=\"", "\"_w\"", "\"\"")),
            Map.entry(
                "Any",
                List.of(
                    "{}",
                    "{\"@type\":\"type.googleapis.com/google.protobuf.Timestamp\",\"value\":\"2000-02-29T00:00:00Z\"}",
                    "{\"name\":\"hello\",\"number\":42,\"@type\":\"custom.example/google.protobuf.Field\"}",
                    "{\"value\":[null,1,{\"x\":true}],\"@type\":\"type.googleapis.com/google.protobuf.Value\"}",
                    "{\"@type\":\"type.googleapis.com/google.protobuf.Any\",\"value\":{\"@type\":\"type.googleapis.com/google.protobuf.StringValue\",\"value\":\"nested\"}}")));
    for (var entry : cases.entrySet()) {
      ProtoPlan plan = model.type("google.protobuf." + entry.getKey());
      try (var compiled = decoder.compile(plan, 65536, registry)) {
        for (String json : entry.getValue()) {
          var builder =
              DynamicMessage.newBuilder(
                  Objects.requireNonNull(defaults.get(entry.getKey())).getDescriptorForType());
          oracle.merge(json, builder);
          var expected = plan.decode(builder.build().toByteArray());
          TypedAcceptance.equal(expected, plan.parseJson(json, registry));
          try (var parsed = compiled.parseJson(json, 1_000_000)) {
            TypedAcceptance.equal(expected, parsed.row());
            TypedAcceptance.equal(expected, parsed.snapshot());
          }
        }
      }
    }
    var timestampPlan = model.type("google.protobuf.Timestamp");
    Random random = new Random(48213);
    try (var compiled = decoder.compile(timestampPlan, 65536)) {
      for (int i = 0; i < 200; i++) {
        var timestamp =
            Timestamp.newBuilder()
                .setSeconds(random.nextLong(315537897600L) - 62135596800L)
                .setNanos(random.nextInt(1_000_000_000))
                .build();
        String json = JsonFormat.printer().print(timestamp);
        try (var parsed = compiled.parseJson(json, 4096)) {
          TypedAcceptance.equal(timestampPlan.decode(timestamp.toByteArray()), parsed.row());
          TypedAcceptance.equal(timestampPlan.parseJson(json), parsed.snapshot());
        }
      }
    }
    var envelope = model.type("truffledata.wkt.Envelope");
    try (var compiled = decoder.compile(envelope, 65536, registry)) {
      for (String json :
          List.of(
              "{\"when\":\"1970-01-01T00:00:01Z\",\"count\":0,\"value\":null,\"values\":[null,0,{}],\"counts\":[0,1]}",
              "{\"when\":null,\"count\":null,\"values\":null}",
              "{\"elapsed\":\"-0.5s\",\"mask\":\"ABC\",\"payload\":{\"@type\":\"x/google.protobuf.StringValue\",\"value\":\"hi\"}}")) {
        try (var parsed = compiled.parseJson(json, 100000)) {
          TypedAcceptance.equal(envelope.parseJson(json, registry), parsed.row());
        }
      }
      for (String json :
          List.of("{\"counts\":[null]}", "{\"when\":{}}", "{\"values\":[undefined]}")) {
        Acceptance.rejects(() -> compiled.parseJson(json, 100000), ContractViolation.class);
        Acceptance.rejects(() -> envelope.parseJson(json, registry), ContractViolation.class);
      }
    }
    Acceptance.rejects(
        () ->
            new ProtoPlan.Model(
                null,
                List.of(new ProtoPlan.Type("google.protobuf.Timestamp", false, List.of())),
                Limits.DEFAULT,
                8,
                1000),
        ContractViolation.class);
    Acceptance.rejects(
        () -> decoder.compile(timestampPlan, 65536, List.of(timestampPlan, timestampPlan)),
        ContractViolation.class);
    crossModelAnyRegistry(decoder);
    // Runtime-compiled schemas use exactly the same native dispatch as generated schemas.
    var runtime = ProtoCompiler.compile(null, Timestamp.getDescriptor(), Limits.DEFAULT, 8, 10000);
    try (var compiled = decoder.compile(runtime, 65536);
        var parsed = compiled.parseJson("\"1970-01-01T00:00:00.123Z\"", 4096)) {
      TypedAcceptance.equal(runtime.parseJson("\"1970-01-01T00:00:00.123Z\""), parsed.row());
    }
    Map<String, List<String>> invalid =
        Map.of(
            "Timestamp",
            List.of(
                "\"1900-02-29T00:00:00Z\"",
                "\"0000-01-01T00:00:00Z\"",
                "\"2000-01-01T00:00:60Z\"",
                "\"2000-01-01T00:00:00.1234567890Z\"",
                "\"9999-12-31T23:59:59-01:00\"",
                "{}"),
            "Duration",
            List.of("\"315576000001s\"", "\"1.1234567890s\"", "\"1\"", "\"--1s\""),
            "Int32Value",
            List.of("null", "{}", "2147483648"),
            "Struct",
            List.of("{\"x\":1,\"x\":2}", "[]"),
            "Value",
            List.of("1e999999", "undefined"),
            "Any",
            List.of(
                "{\"@type\":\"unknown/Nope\"}",
                "{\"@type\":\"google.protobuf.Field\"}",
                "{\"@type\":\"x/google.protobuf.Field\",\"@type\":\"x/google.protobuf.Field\"}",
                "{\"@type\":\"x/google.protobuf.Timestamp\"}",
                "{\"@type\":\"x/google.protobuf.Timestamp\",\"value\":\"2000-01-01T00:00:00Z\",\"extra\":0}"));
    for (var entry : invalid.entrySet()) {
      ProtoPlan plan = model.type("google.protobuf." + entry.getKey());
      try (var compiled = decoder.compile(plan, 65536, registry)) {
        for (String json : entry.getValue()) {
          Acceptance.rejects(() -> compiled.parseJson(json, 1_000_000), ContractViolation.class);
          Acceptance.rejects(() -> plan.parseJson(json, registry), ContractViolation.class);
        }
      }
    }
    // Exercise WKT expansion budgets on both backends, independently of token validity.
    for (var limits : List.of(new Limits(1024, 256, 512, 10000), new Limits(1024, 256, 10000, 2))) {
      var bounded =
          WellKnownContracts.create(null, limits, 64, 10000).type("google.protobuf.ListValue");
      String json = "[\"long string\"," + "0,".repeat(2000) + "0]";
      try (var compiled = decoder.compile(bounded, 65536)) {
        Acceptance.rejects(() -> compiled.parseJson(json, 100000), ContractViolation.class);
        Acceptance.rejects(() -> bounded.parseJson(json), ContractViolation.class);
      }
    }
    var shallow =
        WellKnownContracts.create(null, Limits.DEFAULT, 2, 10000).type("google.protobuf.Value");
    try (var compiled = decoder.compile(shallow, 65536)) {
      Acceptance.rejects(() -> compiled.parseJson("[[[[0]]]]", 100000), ContractViolation.class);
      Acceptance.rejects(() -> shallow.parseJson("[[[[0]]]]"), ContractViolation.class);
    }
    var any = model.type("google.protobuf.Any");
    try (var compiled = decoder.compile(any, 65536)) {
      Acceptance.rejects(
          () ->
              compiled.parseJson(
                  "{\"@type\":\"x/google.protobuf.Timestamp\",\"value\":\"1970-01-01T00:00:00Z\"}",
                  4096),
          ContractViolation.class);
    }
  }

  private static volatile Object sink = new Object();

  static void allocation(BuffaWire decoder) throws Exception {
    var defaults = defaults();
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    var model = model();
    for (var entry :
        Map.of(
                "Timestamp",
                "\"2000-02-29T12:34:56.123456789Z\"",
                "Int64Value",
                "\"9223372036854775807\"",
                "FieldMask",
                "\"fooBar,child.longName\"")
            .entrySet()) {
      var plan = model.type("google.protobuf." + entry.getKey());
      var source =
          ProtoCompiler.bind(
              plan, Objects.requireNonNull(defaults.get(entry.getKey())).getDescriptorForType());
      String json = entry.getValue();
      try (var compiled = decoder.compile(plan, 65536)) {
        for (int i = 0; i < 2000; i++) {
          try (var parsed = compiled.parseJson(json, 4096)) {
            sink = parsed.row();
          }
          sink = source.parseJson(json);
        }
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 500; i++)
          try (var parsed = compiled.parseJson(json, 4096)) {
            sink = parsed.row();
          }
        long nativeBytes = (bean.getThreadAllocatedBytes(thread) - before) / 500;
        before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 500; i++) sink = source.parseJson(json);
        long compatibilityBytes = (bean.getThreadAllocatedBytes(thread) - before) / 500;
        System.out.println(
            "WKT "
                + entry.getKey()
                + " native Java bytes/message="
                + nativeBytes
                + " compatibility="
                + compatibilityBytes);
        Acceptance.check(nativeBytes < 16384, "WKT ingestion allocation budget");
      }
    }
  }

  /**
   * The Any allowlist API (docs/BUFFA.md) permits a root plan and registry entries from different
   * ProtoPlan.Models. Same-named nested types reached both via the root's message fields and via
   * the registry must be structurally compared, not identity-compared, because BYTES and
   * UINT64/FIXED64 defaults allocate fresh DataArray/ExactNumber instances per Model.
   */
  static void crossModelAnyRegistry(BuffaWire decoder) {
    for (var kind :
        List.of(
            ProtoPlan.Kind.BYTES,
            ProtoPlan.Kind.UINT64,
            ProtoPlan.Kind.FIXED64,
            ProtoPlan.Kind.STRING)) {
      var rootModel = anyRegistryModel(kind);
      var registryModel = anyRegistryModel(kind);
      // Before the fix, BYTES/UINT64/FIXED64 threw ContractViolation("Conflicting nested Any
      // contract") because record equals() compared DataArray/ExactNumber defaults by identity.
      try (var compiled =
          decoder.compile(
              rootModel.type("Outer"), 65536, List.of(registryModel.type("Payload")))) {
      }
    }
    // Genuinely conflicting nested types are still rejected.
    var bytesModel = anyRegistryModel(ProtoPlan.Kind.BYTES);
    var stringModel = anyRegistryModel(ProtoPlan.Kind.STRING);
    Acceptance.rejects(
        () ->
            decoder.compile(
                bytesModel.type("Outer"), 65536, List.of(stringModel.type("Payload"))),
        ContractViolation.class);
    // Same-Model registry entries (interned by name) still compile.
    var same = anyRegistryModel(ProtoPlan.Kind.BYTES);
    try (var compiled =
        decoder.compile(same.type("Outer"), 65536, List.of(same.type("Payload")))) {
    }
  }

  private static ProtoPlan.Model anyRegistryModel(ProtoPlan.Kind scalar) {
    Object defaultValue =
        switch (scalar) {
          case BYTES -> DataArray.copyOfBytes(new byte[0]);
          case UINT64, FIXED64 -> Values.normalize(java.math.BigInteger.ZERO);
          case STRING -> "";
          default -> throw new IllegalArgumentException(String.valueOf(scalar));
        };
    return new ProtoPlan.Model(
        null,
        List.of(
            new ProtoPlan.Type(
                "Outer",
                false,
                List.of(
                    new ProtoPlan.FieldPlan(
                        "payload",
                        1,
                        ProtoPlan.Kind.MESSAGE,
                        false,
                        false,
                        true,
                        false,
                        false,
                        -1,
                        "Payload",
                        null,
                        Missing.INSTANCE))),
            new ProtoPlan.Type(
                "Payload",
                false,
                List.of(
                    new ProtoPlan.FieldPlan(
                        "data",
                        1,
                        scalar,
                        false,
                        false,
                        false,
                        false,
                        false,
                        -1,
                        "",
                        null,
                        defaultValue)))),
        Limits.DEFAULT,
        64,
        1_000_000);
  }
}
