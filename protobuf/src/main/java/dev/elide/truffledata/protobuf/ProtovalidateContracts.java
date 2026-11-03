package dev.elide.truffledata.protobuf;

import com.google.protobuf.Descriptors.*;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import dev.elide.truffledata.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashSet;
import org.jspecify.annotations.Nullable;

/** Sound structural facts from buf.validate.field; this is not a replacement for Protovalidate. */
final class ProtovalidateContracts {
  private ProtovalidateContracts() {}

  static Field refine(FieldDescriptor source, Field base) {
    Message rules = rules(source);
    if (rules == null) return base;
    Object ignore = get(rules, "ignore");
    String policy = ignore instanceof EnumValueDescriptor e ? e.getName() : "IGNORE_UNSPECIFIED";
    if (policy.equals("IGNORE_ALWAYS")
        || (policy.equals("IGNORE_IF_ZERO_VALUE") && !source.hasPresence())) return base;
    if (!policy.equals("IGNORE_UNSPECIFIED") && !policy.equals("IGNORE_IF_ZERO_VALUE")) return base;
    Field result = base;
    if (source.hasPresence() && Boolean.TRUE.equals(get(rules, "required")))
      result = new Field(base.name(), base.kind(), base.nullable(), false, base.constraints());
    String kind =
        source.isMapField()
            ? "map"
            : source.isRepeated()
                ? "repeated"
                : source.getType().name().toLowerCase(java.util.Locale.ROOT);
    Object typed = get(rules, kind);
    if (!(typed instanceof Message values)) return result;
    if (result.kind() == Field.Kind.INT64) return integer(result, values);
    if (kind.equals("string"))
      return length(
          result, values, "len", "min_len", "max_len", Constraints.LengthUnit.CODE_POINTS);
    if (kind.equals("bytes"))
      return length(result, values, "len", "min_len", "max_len", Constraints.LengthUnit.ELEMENTS);
    if (kind.equals("repeated"))
      return length(result, values, "", "min_items", "max_items", Constraints.LengthUnit.ELEMENTS);
    // Map entry sequences may retain duplicate keys; their length is not logical map cardinality.
    if (kind.equals("map")) return result;
    return result;
  }

  private static Field integer(Field field, Message rules) {
    BigInteger min = BigInteger.valueOf(Long.MIN_VALUE);
    BigInteger max = BigInteger.valueOf(Long.MAX_VALUE);
    Object gt = get(rules, "gt");
    Object gte = get(rules, "gte");
    Object lt = get(rules, "lt");
    Object lte = get(rules, "lte");
    if (gt instanceof Number n) min = BigInteger.valueOf(n.longValue()).add(BigInteger.ONE);
    if (gte instanceof Number n) min = min.max(BigInteger.valueOf(n.longValue()));
    if (lt instanceof Number n) max = BigInteger.valueOf(n.longValue()).subtract(BigInteger.ONE);
    if (lte instanceof Number n) max = max.min(BigInteger.valueOf(n.longValue()));
    // Reversed Protovalidate bounds express an excluded interval, not an empty range.
    if (min.compareTo(max) > 0) return field;
    Object constant = get(rules, "const");
    if (constant instanceof Number n) {
      min = min.max(BigInteger.valueOf(n.longValue()));
      max = max.min(BigInteger.valueOf(n.longValue()));
    }
    var width = field.constraints().integer();
    if (width != null) {
      min = min.max(BigInteger.valueOf(width.minimum()));
      max = max.min(BigInteger.valueOf(width.maximum()));
    }
    var existing = field.constraints().integerRange();
    if (existing != null) {
      min = min.max(BigInteger.valueOf(existing.min()));
      max = max.min(BigInteger.valueOf(existing.max()));
    }
    if (min.compareTo(max) > 0)
      throw new ContractViolation("Contradictory Protobuf integer contracts");
    return field.withConstraints(
        field
            .constraints()
            .withIntegerRange(
                new Constraints.IntegerRange(min.longValueExact(), max.longValueExact())));
  }

  private static Field length(
      Field field,
      Message rules,
      String exact,
      String lower,
      String upper,
      Constraints.LengthUnit unit) {
    Object size = get(rules, exact);
    Object minimum = get(rules, lower);
    Object maximum = get(rules, upper);
    if (size == null && minimum == null && maximum == null) return field;
    BigInteger min = BigInteger.ZERO;
    BigInteger max = BigInteger.valueOf(Integer.MAX_VALUE);
    if (minimum instanceof Long n) min = unsigned(n);
    if (maximum instanceof Long n) max = max.min(unsigned(n));
    if (size instanceof Long n) {
      min = min.max(unsigned(n));
      max = max.min(unsigned(n));
    }
    var existing = field.constraints().length();
    if (existing != null) {
      if (existing.unit() != unit)
        throw new ContractViolation("Cannot combine different Protobuf length units");
      min = min.max(BigInteger.valueOf(existing.min()));
      max = max.min(BigInteger.valueOf(existing.max()));
    }
    if (min.compareTo(max) > 0)
      throw new ContractViolation("Contradictory Protobuf length contracts");
    return field.withConstraints(
        field
            .constraints()
            .withLength(new Constraints.Length(min.intValueExact(), max.intValueExact(), unit)));
  }

  private static BigInteger unsigned(long value) {
    return new BigInteger(Long.toUnsignedString(value));
  }

  private static @Nullable Object get(Message message, String name) {
    FieldDescriptor field = message.getDescriptorForType().findFieldByName(name);
    return field != null && !field.isRepeated() && message.hasField(field)
        ? message.getField(field)
        : null;
  }

  private static @Nullable Message rules(FieldDescriptor source) {
    for (var entry : source.getOptions().getAllFields().entrySet()) {
      if (entry.getKey().getFullName().equals("buf.validate.field")
          && entry.getValue() instanceof Message message) return message;
    }
    // Descriptor sets often retain custom options as unknown fields. Resolve the extension from
    // their imported schema and merge every wire occurrence, just as the Protobuf parser does.
    var pending = new ArrayDeque<FileDescriptor>();
    var visited = new HashSet<FileDescriptor>();
    pending.add(source.getFile());
    while (!pending.isEmpty()) {
      FileDescriptor file = pending.removeFirst();
      if (!visited.add(file)) continue;
      if (visited.size() > 512)
        throw new ContractViolation("Protobuf option dependency budget exceeded");
      pending.addAll(file.getDependencies());
      for (FieldDescriptor extension : file.getExtensions()) {
        if (!extension.getFullName().equals("buf.validate.field")) continue;
        var payloads =
            source
                .getOptions()
                .getUnknownFields()
                .getField(extension.getNumber())
                .getLengthDelimitedList();
        if (payloads.isEmpty()) return null;
        var builder = DynamicMessage.newBuilder(extension.getMessageType());
        long bytes = 0;
        try {
          for (var payload : payloads) {
            bytes += payload.size();
            if (bytes > 1048576)
              throw new ContractViolation("Protobuf option byte budget exceeded");
            builder.mergeFrom(payload);
          }
        } catch (IOException failure) {
          throw new ContractViolation("Malformed Protobuf validation options");
        }
        return builder.build();
      }
    }
    return null;
  }
}
