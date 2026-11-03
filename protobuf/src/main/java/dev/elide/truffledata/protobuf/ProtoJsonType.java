package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.ProtoPlan.Kind.*;

import dev.elide.truffledata.ContractViolation;

/** Schema-validated ProtoJSON dispatch. Ordinals form part of the native schema-program ABI. */
public enum ProtoJsonType {
  ORDINARY,
  TIMESTAMP,
  DURATION,
  FIELD_MASK,
  STRUCT,
  VALUE,
  LIST_VALUE,
  ANY,
  WRAPPER;

  static ProtoJsonType classify(ProtoPlan plan) {
    ProtoJsonType type =
        switch (plan.name()) {
          case "google.protobuf.Timestamp" -> TIMESTAMP;
          case "google.protobuf.Duration" -> DURATION;
          case "google.protobuf.FieldMask" -> FIELD_MASK;
          case "google.protobuf.Struct" -> STRUCT;
          case "google.protobuf.Value" -> VALUE;
          case "google.protobuf.ListValue" -> LIST_VALUE;
          case "google.protobuf.Any" -> ANY;
          case "google.protobuf.DoubleValue",
              "google.protobuf.FloatValue",
              "google.protobuf.Int64Value",
              "google.protobuf.UInt64Value",
              "google.protobuf.Int32Value",
              "google.protobuf.UInt32Value",
              "google.protobuf.BoolValue",
              "google.protobuf.StringValue",
              "google.protobuf.BytesValue" ->
              WRAPPER;
          default -> ORDINARY;
        };
    ProtoPlan.Kind[] expected =
        switch (type) {
          case TIMESTAMP, DURATION -> new ProtoPlan.Kind[] {INT64, INT32};
          case FIELD_MASK -> new ProtoPlan.Kind[] {STRING};
          case STRUCT, LIST_VALUE -> new ProtoPlan.Kind[] {MESSAGE};
          case VALUE -> new ProtoPlan.Kind[] {ENUM, DOUBLE, STRING, BOOL, MESSAGE, MESSAGE};
          case ANY -> new ProtoPlan.Kind[] {STRING, BYTES};
          case WRAPPER ->
              new ProtoPlan.Kind[] {
                switch (plan.name()) {
                  case "google.protobuf.DoubleValue" -> DOUBLE;
                  case "google.protobuf.FloatValue" -> FLOAT;
                  case "google.protobuf.Int64Value" -> INT64;
                  case "google.protobuf.UInt64Value" -> UINT64;
                  case "google.protobuf.Int32Value" -> INT32;
                  case "google.protobuf.UInt32Value" -> UINT32;
                  case "google.protobuf.BoolValue" -> BOOL;
                  case "google.protobuf.StringValue" -> STRING;
                  default -> BYTES;
                }
              };
          default -> new ProtoPlan.Kind[0];
        };
    if (type == ORDINARY) return type;
    if (plan.size() != expected.length) throw bad();
    for (int i = 0; i < expected.length; i++) {
      int ordinal = plan.ordinal(i + 1);
      if (ordinal < 0) throw bad();
      var field = plan.field(ordinal);
      boolean repeated = type == FIELD_MASK || type == STRUCT || type == LIST_VALUE;
      if (field.kind() != expected[i]
          || field.repeated() != repeated
          || field.required()
          || field.map() != (type == STRUCT)) throw bad();
    }
    if (type == LIST_VALUE
        && !plan.field(plan.ordinal(1)).typeName().equals("google.protobuf.Value")) throw bad();
    if (type == VALUE) {
      int group = plan.field(plan.ordinal(1)).oneof();
      if (group < 0) throw bad();
      for (int i = 0; i < plan.size(); i++) if (plan.field(i).oneof() != group) throw bad();
      if (!plan.field(plan.ordinal(5)).typeName().equals("google.protobuf.Struct")
          || !plan.field(plan.ordinal(6)).typeName().equals("google.protobuf.ListValue"))
        throw bad();
      var enumeration = plan.field(plan.ordinal(1)).enumType();
      if (enumeration == null
          || !enumeration.name().equals("google.protobuf.NullValue")
          || !enumeration.accepts(0)) throw bad();
    }
    return type;
  }

  private static ContractViolation bad() {
    return new ContractViolation("Invalid well-known-type schema");
  }
}
