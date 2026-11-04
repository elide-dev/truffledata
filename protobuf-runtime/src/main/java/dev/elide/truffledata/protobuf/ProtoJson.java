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
  private static final java.util.regex.Pattern DURATION =
      java.util.regex.Pattern.compile("(-?)([0-9]+)(?:\\.([0-9]+))?s");
  private static final java.util.regex.Pattern TIMESTAMP =
      java.util.regex.Pattern.compile(
          "([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})(?:\\.([0-9]+))?(Z|[+-][0-9]{2}:[0-9]{2})");
  private final JsonReader input;
  private final ProtoRow.Budget budget;
  private final Map<String, ProtoPlan> anyTypes;
  private final int maxText;
  private final int depthLimit;

  private ProtoJson(
      ProtoPlan plan,
      String json,
      Map<String, ProtoPlan> anyTypes,
      ProtoRow.Budget budget,
      int depthLimit) {
    if (json.length() > plan.model.maxWireBytes / 2)
      throw bad("Protobuf JSON input budget exceeded");
    input = new JsonReader(new StringReader(json));
    input.setLenient(false);
    this.budget = budget;
    this.depthLimit = Math.min(depthLimit, plan.maxDepth());
    this.anyTypes = anyTypes;
    maxText = plan.maxWireBytes() / 2;
  }

  static ProtoRow decode(ProtoPlan plan, String json, List<ProtoPlan> registry) {
    Map<String, ProtoPlan> anyTypes = new HashMap<>();
    for (ProtoPlan registered : registry)
      if (anyTypes.put(registered.name(), registered) != null) throw bad("Duplicate Any type");
    ProtoJson parser =
        new ProtoJson(plan, json, anyTypes, new ProtoRow.Budget(plan.limits()), plan.maxDepth());
    try {
      ProtoRow row = parser.message(plan, 1);
      if (parser.input.peek() != JsonToken.END_DOCUMENT) throw bad("Trailing JSON input");
      return row;
    } catch (ContractViolation e) {
      throw e;
    } catch (IOException
        | IllegalStateException
        | IllegalArgumentException
        | ArithmeticException
        | java.time.DateTimeException e) {
      throw bad("Invalid ProtoJSON");
    }
  }

  private static ContractViolation bad(String message) {
    return new ContractViolation(message);
  }

  private ProtoRow message(ProtoPlan plan, int depth) throws IOException {
    if (depth > depthLimit) throw bad("JSON nesting budget exceeded");
    budget.cells(plan.size());
    if (plan.jsonType() != ProtoJsonType.ORDINARY) return special(plan, depth);
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
      if (input.peek() == JsonToken.NULL
          && (field.repeated() || (!nullEnum(field) && !valueMessage(plan, ordinal)))) {
        input.nextNull();
        continue;
      }
      if (field.oneof() >= 0)
        for (int i = 0; i < plan.size(); i++)
          if (i != ordinal && builder.has(i) && plan.field(i).oneof() == field.oneof())
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
    if (depth > depthLimit) throw bad("JSON map nesting budget exceeded");
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
    if (field.kind().message()) {
      if (input.peek() == JsonToken.NULL && !valueMessage(plan, ordinal))
        throw bad("Null repeated element or map value");
      return message(plan.nested(ordinal), depth + 1);
    }
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

  private static boolean valueMessage(ProtoPlan plan, int ordinal) {
    return plan.field(ordinal).kind().message()
        && plan.nested(ordinal).jsonType() == ProtoJsonType.VALUE;
  }

  private ProtoRow special(ProtoPlan plan, int depth) throws IOException {
    var builder = plan.newBuilder();
    switch (plan.jsonType()) {
      case WRAPPER -> {
        if (input.peek() == JsonToken.NULL)
          throw bad("Null wrapper requires an enclosing nullable field");
        builder.set(plan.ordinal(1), value(plan, plan.ordinal(1), depth));
      }
      case TIMESTAMP, DURATION -> {
        if (input.peek() != JsonToken.STRING) throw bad("Expected time string");
        String text = input.nextString();
        if (text.length() > 128) throw bad("Time token budget");
        long[] parts =
            plan.jsonType() == ProtoJsonType.TIMESTAMP ? timestamp(text) : duration(text);
        builder.set(plan.ordinal(1), parts[0]).set(plan.ordinal(2), parts[1]);
      }
      case FIELD_MASK -> {
        if (input.peek() != JsonToken.STRING) throw bad("Expected FieldMask string");
        String text = input.nextString();
        List<Object> paths = new ArrayList<>();
        for (String path : text.split(",", -1)) {
          if (path.isEmpty()) continue;
          budget.cells(1);
          StringBuilder converted = new StringBuilder();
          for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c >= 'A' && c <= 'Z') converted.append('_').append((char) (c + 32));
            else converted.append(c);
          }
          budget.bytes(2L * converted.length());
          paths.add(converted.toString());
        }
        builder.set(plan.ordinal(1), new DataArray(paths.toArray()));
      }
      case STRUCT -> builder.set(plan.ordinal(1), map(plan.nested(plan.ordinal(1)), depth + 1));
      case LIST_VALUE -> {
        input.beginArray();
        List<Object> items = new ArrayList<>();
        while (input.hasNext()) {
          budget.cells(1);
          items.add(value(plan, plan.ordinal(1), depth));
        }
        input.endArray();
        builder.set(plan.ordinal(1), new DataArray(items.toArray()));
      }
      case VALUE -> {
        int number =
            switch (input.peek()) {
              case NULL -> 1;
              case NUMBER -> 2;
              case STRING -> 3;
              case BOOLEAN -> 4;
              case BEGIN_OBJECT -> 5;
              case BEGIN_ARRAY -> 6;
              default -> throw bad("Invalid Value token");
            };
        builder.set(plan.ordinal(number), value(plan, plan.ordinal(number), depth));
      }
      case ANY -> {
        return any(plan, depth);
      }
      default -> throw bad("Invalid JSON dispatch");
    }
    return builder.build();
  }

  private static long fraction(String text) {
    if (text.isEmpty() || text.length() > 9 || !text.chars().allMatch(c -> c >= '0' && c <= '9'))
      throw bad("Invalid nanoseconds");
    long value = Long.parseLong(text);
    for (int i = text.length(); i < 9; i++) value *= 10;
    return value;
  }

  private static long[] duration(String text) {
    var match = DURATION.matcher(text);
    if (!match.matches()) throw bad("Invalid Duration");
    long seconds = Long.parseLong(match.group(2));
    String fractional = match.group(3);
    long nanos = fractional == null ? 0 : fraction(fractional);
    if (seconds > 315576000000L) throw bad("Duration range");
    boolean negative = !match.group(1).isEmpty();
    return new long[] {negative ? -seconds : seconds, negative ? -nanos : nanos};
  }

  private static long[] timestamp(String text) {
    var match = TIMESTAMP.matcher(text);
    if (!match.matches()) throw bad("Invalid Timestamp");
    int year = Integer.parseInt(match.group(1));
    if (year == 0) throw bad("Timestamp year");
    var date =
        java.time.LocalDateTime.of(
            year,
            Integer.parseInt(match.group(2)),
            Integer.parseInt(match.group(3)),
            Integer.parseInt(match.group(4)),
            Integer.parseInt(match.group(5)),
            Integer.parseInt(match.group(6)));
    String zone = match.group(8);
    int offset = 0;
    if (!zone.equals("Z")) {
      int hour = Integer.parseInt(zone.substring(1, 3)),
          minute = Integer.parseInt(zone.substring(4));
      if (hour > 23 || minute > 59) throw bad("Timestamp offset");
      offset = (hour * 3600 + minute * 60) * (zone.charAt(0) == '-' ? -1 : 1);
    }
    long seconds = date.toEpochSecond(java.time.ZoneOffset.UTC) - offset;
    if (seconds < -62135596800L || seconds > 253402300799L) throw bad("Timestamp range");
    String fractional = match.group(7);
    return new long[] {seconds, fractional == null ? 0 : fraction(fractional)};
  }

  // Any permits @type after payload fields. Buffer bounded JSON text, not a DynamicMessage/DOM
  // tree.
  private ProtoRow any(ProtoPlan plan, int depth) throws IOException {
    input.beginObject();
    String typeUrl = "";
    boolean sawType = false;
    java.io.StringWriter text = new java.io.StringWriter();
    var output = new com.google.gson.stream.JsonWriter(text);
    output.beginObject();
    while (input.hasNext()) {
      String name = input.nextName();
      if (name.equals("@type")) {
        if (sawType || input.peek() != JsonToken.STRING) throw bad("Invalid Any type URL");
        sawType = true;
        typeUrl = input.nextString();
      } else {
        output.name(name);
        copyJson(output, text, depth + 1);
      }
    }
    input.endObject();
    output.endObject();
    output.close();
    if (!sawType) {
      if (!text.toString().equals("{}")) throw bad("Missing Any type URL");
      return plan.newBuilder().build();
    }
    int slash = typeUrl.lastIndexOf('/');
    ProtoPlan target = slash < 0 ? null : anyTypes.get(typeUrl.substring(slash + 1));
    if (target == null) throw bad("Unregistered Any type");
    ProtoJson nested = new ProtoJson(target, text.toString(), anyTypes, budget, depthLimit);
    ProtoRow value;
    if (target.jsonType() == ProtoJsonType.ORDINARY) value = nested.message(target, depth + 1);
    else {
      nested.input.beginObject();
      if (!nested.input.hasNext() || !nested.input.nextName().equals("value"))
        throw bad("Missing Any value");
      value = nested.message(target, depth + 1);
      if (nested.input.hasNext()) throw bad("Unknown Any member");
      nested.input.endObject();
    }
    if (nested.input.peek() != JsonToken.END_DOCUMENT) throw bad("Trailing Any input");
    byte[] wire = target.encode(value);
    budget.cells(wire.length);
    budget.bytes(typeUrl.length() * 2L);
    return plan.newBuilder()
        .set(plan.ordinal(1), typeUrl)
        .set(plan.ordinal(2), DataArray.copyOfBytes(wire))
        .build();
  }

  private void copyJson(
      com.google.gson.stream.JsonWriter output, java.io.StringWriter text, int depth)
      throws IOException {
    if (depth > depthLimit) throw bad("Any JSON nesting budget");
    budget.cells(1);
    switch (input.peek()) {
      case BEGIN_OBJECT -> {
        input.beginObject();
        output.beginObject();
        while (input.hasNext()) {
          output.name(input.nextName());
          copyJson(output, text, depth + 1);
        }
        input.endObject();
        output.endObject();
      }
      case BEGIN_ARRAY -> {
        input.beginArray();
        output.beginArray();
        while (input.hasNext()) copyJson(output, text, depth + 1);
        input.endArray();
        output.endArray();
      }
      case STRING -> output.value(input.nextString());
      case NUMBER -> output.jsonValue(input.nextString());
      case BOOLEAN -> output.value(input.nextBoolean());
      case NULL -> {
        input.nextNull();
        output.nullValue();
      }
      default -> throw bad("Invalid Any JSON");
    }
    if (text.getBuffer().length() > maxText) throw bad("Any text buffer budget");
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
