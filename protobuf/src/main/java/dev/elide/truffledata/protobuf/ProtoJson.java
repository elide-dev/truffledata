package dev.elide.truffledata.protobuf;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.elide.truffledata.*;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

/** Streaming ProtoJSON into owned contract rows; no intermediate JSON or protobuf object tree. */
final class ProtoJson {
  private static final Set<String> SPECIAL_JSON_TYPES =
      Set.of(
          "google.protobuf.Any",
          "google.protobuf.Timestamp",
          "google.protobuf.Duration",
          "google.protobuf.FieldMask",
          "google.protobuf.Struct",
          "google.protobuf.Value",
          "google.protobuf.ListValue",
          "google.protobuf.DoubleValue",
          "google.protobuf.FloatValue",
          "google.protobuf.Int64Value",
          "google.protobuf.UInt64Value",
          "google.protobuf.Int32Value",
          "google.protobuf.UInt32Value",
          "google.protobuf.BoolValue",
          "google.protobuf.StringValue",
          "google.protobuf.BytesValue");

  private final JsonReader input;
  private final ProtoRow.Budget budget;

  private ProtoJson(ProtoPlan plan, String json) {
    if (json.length() > plan.model.maxWireBytes / 2)
      throw bad("Protobuf JSON input budget exceeded");
    input = new JsonReader(new StringReader(json));
    input.setLenient(false);
    budget = new ProtoRow.Budget(plan.model.limits);
  }

  static ProtoRow decode(ProtoPlan plan, String json) {
    ProtoJson parser = new ProtoJson(plan, json);
    try {
      ProtoRow row = parser.message(plan, 1);
      if (parser.input.peek() != JsonToken.END_DOCUMENT) throw bad("Trailing JSON input");
      return row;
    } catch (ContractViolation e) {
      throw e;
    } catch (IOException
        | IllegalStateException
        | IllegalArgumentException
        | ArithmeticException e) {
      throw bad("Invalid ProtoJSON");
    }
  }

  private static ContractViolation bad(String message) {
    return new ContractViolation(message);
  }

  private ProtoRow message(ProtoPlan plan, int depth) throws IOException {
    // These types have special JSON mappings. Never silently treat them as ordinary objects.
    if (SPECIAL_JSON_TYPES.contains(plan.name()))
      throw bad("Well-known JSON mapping requires ProtoCompiler.bind(...).parseJson");
    if (depth > plan.model.maxDepth) throw bad("JSON nesting budget exceeded");
    budget.cells(plan.size());
    var builder = plan.newBuilder();
    boolean[] seen = new boolean[plan.size()];
    input.beginObject();
    while (input.hasNext()) {
      String name = input.nextName();
      int ordinal = -1;
      for (int i = 0; i < plan.size(); i++) {
        var f = plan.field(i);
        if (f.name().equals(name) || f.jsonName().equals(name)) {
          if (ordinal != -1) throw bad("Ambiguous JSON field spelling");
          ordinal = i;
        }
      }
      if (ordinal == -1) throw bad("Unknown JSON field: " + name);
      if (seen[ordinal]) throw bad("Duplicate JSON field: " + name);
      seen[ordinal] = true;
      var field = plan.field(ordinal);
      if (input.peek() == JsonToken.NULL && (field.repeated() || !nullEnum(field))) {
        input.nextNull();
        continue;
      }
      if (field.oneof() >= 0)
        for (int i = 0; i < plan.size(); i++)
          if (i != ordinal && builder.present[i] && plan.field(i).oneof() == field.oneof())
            throw bad("Multiple JSON oneof members");
      Object value;
      if (field.map()) value = map(plan.nested(ordinal), depth + 1);
      else if (field.repeated()) {
        input.beginArray();
        List<Object> items = new ArrayList<>();
        while (input.hasNext()) {
          budget.cells(1);
          items.add(value(plan, ordinal, depth));
        }
        input.endArray();
        value = new DataArray(items.toArray());
      } else value = value(plan, ordinal, depth);
      builder.set(ordinal, value);
    }
    input.endObject();
    return builder.build();
  }

  private DataArray map(ProtoPlan entry, int depth) throws IOException {
    if (depth > entry.model.maxDepth) throw bad("JSON map nesting budget exceeded");
    input.beginObject();
    Map<Object, ProtoRow> items = new LinkedHashMap<>();
    while (input.hasNext()) {
      budget.cells(1L + entry.size());
      String key = input.nextName();
      Object normalized =
          switch (entry.field(0).kind()) {
            case STRING -> {
              budget.bytes(2L * key.length());
              yield key;
            }
            case BOOL ->
                switch (key) {
                  case "true" -> true;
                  case "false" -> false;
                  default -> throw bad("Invalid boolean map key");
                };
            default -> {
              if (!key.matches("-?[0-9]+")) throw bad("Invalid integer map key");
              yield integer(entry.field(0).kind(), key);
            }
          };
      Object value = value(entry, 1, depth);
      ProtoRow row = entry.newBuilder().set(0, normalized).set(1, value).build();
      if (items.put(ProtoRow.mapKey(normalized), row) != null) throw bad("Duplicate JSON map key");
    }
    input.endObject();
    return new DataArray(items.values().toArray());
  }

  private Object value(ProtoPlan plan, int ordinal, int depth) throws IOException {
    var field = plan.field(ordinal);
    if (field.kind().message()) return message(plan.nested(ordinal), depth + 1);
    if (input.peek() == JsonToken.NULL) {
      if (!nullEnum(field)) throw bad("Null repeated element or map value");
      input.nextNull();
      return 0L;
    }
    return switch (field.kind()) {
      case BOOL -> input.nextBoolean();
      case STRING -> {
        if (input.peek() != JsonToken.STRING) throw bad("Expected JSON string");
        String text = input.nextString();
        budget.bytes(2L * text.length());
        yield text;
      }
      case BYTES -> {
        if (input.peek() != JsonToken.STRING) throw bad("Expected base64 string");
        String text = input.nextString();
        // Charge decoded size before allocation, including unpadded input.
        int padding = text.endsWith("==") ? 2 : text.endsWith("=") ? 1 : 0;
        long decoded = (long) text.length() * 6 / 8 - padding;
        if (decoded < 0) throw bad("Invalid base64 padding");
        budget.cells(decoded);
        byte[] bytes =
            (text.indexOf('-') >= 0 || text.indexOf('_') >= 0
                    ? Base64.getUrlDecoder()
                    : Base64.getDecoder())
                .decode(text);
        yield DataArray.copyOfBytes(ByteBuffer.wrap(bytes));
      }
      case FLOAT, DOUBLE -> {
        boolean quoted = input.peek() == JsonToken.STRING;
        String text = input.nextString();
        double number =
            switch (text) {
              case "NaN" -> Double.NaN;
              case "Infinity" -> Double.POSITIVE_INFINITY;
              case "-Infinity" -> Double.NEGATIVE_INFINITY;
              default -> {
                double n = Double.parseDouble(text);
                if (!Double.isFinite(n)) throw bad("JSON floating point overflow");
                yield n;
              }
            };
        if (!Double.isFinite(number) && !quoted) throw bad("Non-finite numbers must be quoted");
        if (field.kind() == ProtoPlan.Kind.FLOAT) {
          float rounded = (float) number;
          if (Double.isFinite(number) && !Float.isFinite(rounded)) throw bad("JSON float overflow");
          number = rounded;
        }
        yield number;
      }
      case ENUM -> {
        String text = input.nextString();
        var enumeration = Objects.requireNonNull(field.enumType());
        Integer named = enumeration.values().get(text);
        long number = named == null ? decimalInteger(text).intValueExact() : named.longValue();
        if (!enumeration.accepts((int) number)) throw bad("Unknown closed-enum number");
        yield number;
      }
      default -> integer(field.kind(), input.nextString());
    };
  }

  private static boolean nullEnum(ProtoPlan.FieldPlan field) {
    var enumeration = field.enumType();
    return enumeration != null && enumeration.name().equals("google.protobuf.NullValue");
  }

  private static BigInteger decimalInteger(String text) {
    // All legal magnitudes fit 64 bits. Bound decimal exponent expansion before BigInteger.
    if (text.length() > 128) throw bad("JSON integer token budget exceeded");
    BigDecimal value = new BigDecimal(text);
    if (value.signum() != 0 && (long) value.precision() - value.scale() > 20)
      throw bad("JSON integer overflow");
    if (value.signum() == 0) return BigInteger.ZERO;
    if (value.scale() > 128) throw bad("Fractional JSON integer");
    return value.toBigIntegerExact();
  }

  private static Object integer(ProtoPlan.Kind kind, String text) {
    BigInteger value = decimalInteger(text);
    return kind == ProtoPlan.Kind.UINT64 || kind == ProtoPlan.Kind.FIXED64
        ? Values.normalize(value)
        : value.longValueExact();
  }
}
