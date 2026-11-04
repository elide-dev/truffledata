package dev.elide.truffledata.protobuf;

import dev.elide.truffledata.*;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** Bounded wire codec. No descriptors, reflection, generated messages, or DynamicMessage. */
final class ProtoCodec {
  private ProtoCodec() {}

  static ProtoRow decode(ProtoPlan plan, byte[] wire) {
    if (wire.length > plan.model.maxWireBytes) throw bad("Wire byte budget exceeded");
    if (plan.compactScalars) {
      ProtoRow compact = scalars(plan, wire);
      if (compact != null) return compact;
    }
    return decodeRegion(plan, ByteBuffer.wrap(wire));
  }

  /** Common small integer records need no builder, boxed values, or temporary arrays. */
  private static @Nullable ProtoRow scalars(ProtoPlan plan, byte[] wire) {
    int firstTag = plan.field(0).number() << 3;
    int secondTag = plan.size() == 2 ? plan.field(1).number() << 3 : -1;
    long first = 0, second = 0;
    int position = 0;
    while (position < wire.length) {
      int tag = wire[position++] & 255;
      // Preserve unknown fields, noncanonical tags, and errors in the general lossless codec.
      if (tag != firstTag && tag != secondTag) return null;
      long value = 0;
      boolean complete = false;
      for (int shift = 0; shift < 64; shift += 7) {
        if (position == wire.length) return null;
        int octet = wire[position++] & 255;
        if (shift == 63 && (octet & 254) != 0) return null;
        value |= (long) (octet & 127) << shift;
        if ((octet & 128) == 0) {
          complete = true;
          break;
        }
      }
      if (!complete) return null;
      int ordinal = tag == firstTag ? 0 : 1;
      if (plan.field(ordinal).kind() == ProtoPlan.Kind.INT32) value = (int) value;
      if (ordinal == 0) first = value;
      else second = value;
    }
    return new ProtoRow(plan, first, second);
  }

  static ProtoRow decode(ProtoPlan plan, ByteBuffer source) {
    return decodeRegion(plan, source.slice());
  }

  private static ProtoRow decodeRegion(ProtoPlan plan, ByteBuffer wire) {
    if (wire.remaining() > plan.model.maxWireBytes) throw bad("Wire byte budget exceeded");
    Reader input = new Reader(wire, new ProtoRow.Budget(plan.model.limits), plan.model.maxDepth);
    State state = new State(plan, input.budget);
    input.message(state, wire.remaining(), 0, 1);
    return state.finish();
  }

  static DataArray inspect(byte[] wire, Limits limits, int maxDepth, int maxWireBytes) {
    if (wire.length > maxWireBytes || maxWireBytes < 0 || maxDepth < 1 || maxDepth > 128)
      throw bad("Invalid wire budgets");
    Reader reader = new Reader(ByteBuffer.wrap(wire), new ProtoRow.Budget(limits), maxDepth);
    Schema schema =
        Schema.of(
            Field.requiredLong("number"),
            Field.requiredLong("wireType"),
            new Field("value", Field.Kind.ANY, false, false));
    return reader.inspect(schema, limits, wire.length, 0, 1);
  }

  private static ContractViolation bad(String message) {
    return new ContractViolation(message);
  }

  private static final class State {
    final ProtoRow.Builder builder;
    final Object[] values;
    @Nullable ByteArrayOutputStream unknown;

    ByteArrayOutputStream unknown() {
      if (unknown == null) unknown = new ByteArrayOutputStream();
      return unknown;
    }

    boolean invalidMapEnum;

    State(ProtoPlan plan, ProtoRow.Budget budget) {
      budget.cells(plan.size());
      builder = new ProtoRow.Builder(plan);
      values = builder.values;
    }

    void select(int ordinal) {
      builder.select(ordinal);
      builder.present[ordinal] = true;
    }

    @SuppressWarnings("unchecked")
    void add(int ordinal, Object value) {
      Object old = values[ordinal];
      List<Object> list;
      if (old instanceof List<?>) list = (List<Object>) old;
      else {
        list = new ArrayList<>();
        values[ordinal] = list;
      }
      list.add(value);
      builder.present[ordinal] = true;
    }

    ProtoRow finish() {
      for (int i = 0; i < values.length; i++) {
        var field = builder.plan.field(i);
        if (values[i] instanceof State nested) values[i] = nested.finish();
        else if (values[i] instanceof List<?> items) {
          Object[] frozen = new Object[items.size()];
          for (int j = 0; j < frozen.length; j++) {
            Object item = items.get(j);
            frozen[j] = item instanceof State nested ? nested.finish() : item;
          }
          if (field.map()) {
            Map<Object, Object> entries = new LinkedHashMap<>();
            var data = DataLibrary.factory().getUncached();
            for (Object entry : frozen) entries.put(ProtoRow.mapKey(data.read(entry, 0)), entry);
            frozen = entries.values().toArray();
          }
          values[i] = new DataArray(frozen);
        }
        if (!field.explicitPresence()) builder.present[i] = ProtoRow.nonDefault(field, values[i]);
        if (field.required() && !builder.present[i])
          throw bad("Missing required field: " + field.name());
      }
      if (unknown != null) builder.unknown = unknown.toByteArray();
      // The reader has charged allocations and depth before materialization; validate aggregate
      // too.
      return builder.buildDecoded();
    }
  }

  private static final class Reader {
    final ByteBuffer wire;
    final ProtoRow.Budget budget;
    final int maxDepth;
    int position;

    Reader(ByteBuffer wire, ProtoRow.Budget budget, int maxDepth) {
      this.wire = wire;
      this.budget = budget;
      this.maxDepth = maxDepth;
    }

    long varint(int end) {
      long value = 0;
      for (int shift = 0; shift < 64; shift += 7) {
        if (position >= end) throw bad("Truncated varint");
        int b = wire.get(position++) & 255;
        if (shift == 63 && (b & 254) != 0) throw bad("Varint overflow");
        value |= (long) (b & 127) << shift;
        if ((b & 128) == 0) return value;
      }
      throw bad("Malformed varint");
    }

    int length(int end) {
      long length = varint(end);
      if (length < 0 || length > end - position) throw bad("Truncated or oversized field");
      return (int) length;
    }

    long fixed(int width, int end) {
      if (end - position < width) throw bad("Truncated fixed-width field");
      long value = 0;
      for (int i = 0; i < width; i++) value |= (long) (wire.get(position++) & 255) << (i * 8);
      return value;
    }

    int tag(int end) {
      long tag = varint(end);
      if (tag < 0 || tag > 0xffffffffL || (tag >>> 3) == 0 || (tag & 7) > 5)
        throw bad("Invalid wire tag");
      return (int) tag;
    }

    void message(State state, int end, int group, int depth) {
      if (depth > maxDepth) throw bad("Protobuf nesting budget exceeded");
      while (position < end) {
        int start = position;
        int tag = tag(end), number = tag >>> 3, wireType = tag & 7;
        if (wireType == 4) {
          if (group != number) throw bad("Unexpected end group");
          return;
        }
        int ordinal = state.builder.plan.ordinal(number);
        var field = ordinal < 0 ? null : state.builder.plan.field(ordinal);
        boolean packed =
            field != null && field.repeated() && field.kind().packable() && wireType == 2;
        if (field == null || (!packed && wireType != field.kind().wire)) {
          skip(tag, end, depth);
          retain(state, start);
          continue;
        }
        if (packed) {
          int count = length(end), packedEnd = position + count;
          while (position < packedEnd) {
            Object value = scalar(field.kind(), packedEnd);
            var enumeration = field.enumType();
            if (enumeration != null && !enumeration.accepts(((Long) value).intValue())) {
              Writer size = new Writer(null, 20);
              size.varint((long) number << 3);
              size.varint((Long) value);
              byte[] unknown = new byte[size.position];
              Writer out = new Writer(unknown, unknown.length);
              out.varint((long) number << 3);
              out.varint((Long) value);
              budget.bytes(unknown.length);
              state.unknown().writeBytes(unknown);
            } else {
              budget.cells(1);
              state.add(ordinal, value);
            }
          }
          continue;
        }
        Object value;
        if (field.kind().message()) {
          if (depth >= maxDepth) throw bad("Protobuf nesting budget exceeded");
          State nested;
          if (!field.repeated() && state.values[ordinal] instanceof State existing)
            nested = existing;
          else nested = new State(state.builder.plan.nested(ordinal), budget);
          if (!field.repeated()) state.select(ordinal);
          if (field.kind() == ProtoPlan.Kind.GROUP) message(nested, end, number, depth + 1);
          else {
            int count = length(end), nestedEnd = position + count;
            message(nested, nestedEnd, 0, depth + 1);
          }
          if (field.map() && nested.invalidMapEnum) {
            retain(state, start);
            continue;
          }
          value = nested;
        } else {
          value = scalar(field.kind(), end);
          var enumeration = field.enumType();
          if (state.builder.plan.definition().mapEntry() && ordinal == 1 && enumeration != null)
            state.invalidMapEnum = !enumeration.accepts(((Long) value).intValue());
          if (enumeration != null && !enumeration.accepts(((Long) value).intValue())) {
            retain(state, start);
            continue;
          }
        }
        if (field.repeated()) {
          budget.cells(1);
          state.add(ordinal, value);
        } else {
          state.select(ordinal);
          state.values[ordinal] = value;
        }
      }
      if (group != 0) throw bad("Unterminated group");
      if (state.builder.plan.definition().mapEntry()
          && state.builder.plan.field(1).kind().message()
          && state.values[1] == Missing.INSTANCE) {
        if (depth >= maxDepth) throw bad("Protobuf nesting budget exceeded");
        state.values[1] = new State(state.builder.plan.nested(1), budget);
        state.builder.present[1] = true;
      }
    }

    void retain(State state, int start) {
      budget.bytes(position - start);
      if (wire.hasArray())
        state.unknown().write(wire.array(), wire.arrayOffset() + start, position - start);
      else for (int i = start; i < position; i++) state.unknown().write(wire.get(i));
    }

    Object scalar(ProtoPlan.Kind kind, int end) {
      return switch (kind) {
        case INT32, ENUM -> (long) (int) varint(end);
        case UINT32 -> varint(end) & 0xffffffffL;
        case INT64 -> varint(end);
        case UINT64 -> unsigned(varint(end));
        case SINT32 -> {
          int n = (int) varint(end);
          yield (long) ((n >>> 1) ^ -(n & 1));
        }
        case SINT64 -> {
          long n = varint(end);
          yield (n >>> 1) ^ -(n & 1);
        }
        case FIXED32 -> fixed(4, end);
        case SFIXED32 -> (long) (int) fixed(4, end);
        case FIXED64 -> unsigned(fixed(8, end));
        case SFIXED64 -> fixed(8, end);
        case BOOL -> varint(end) != 0;
        case FLOAT -> (double) Float.intBitsToFloat((int) fixed(4, end));
        case DOUBLE -> Double.longBitsToDouble(fixed(8, end));
        case STRING -> {
          int count = length(end);
          budget.bytes(2L * utf16Length(position, count));
          String result =
              wire.hasArray()
                  ? new String(
                      wire.array(), wire.arrayOffset() + position, count, StandardCharsets.UTF_8)
                  : StandardCharsets.UTF_8.decode(wire.slice(position, count)).toString();
          position += count;
          yield result;
        }
        case BYTES -> {
          int count = length(end);
          budget.cells(count);
          DataArray result = DataArray.copyOfBytes(wire.slice(position, count));
          position += count;
          yield result;
        }
        default -> throw bad("Message must use structured decoder");
      };
    }

    // Validate and count before allocating the decoded string, using the public UTF-16 budget.
    int utf16Length(int start, int count) {
      int cursor = start, end = start + count, units = 0;
      while (cursor < end) {
        int first = wire.get(cursor++) & 255;
        if (first < 128) {
          units++;
          continue;
        }
        int tails, code, minimum;
        if (first >= 0xc2 && first <= 0xdf) {
          tails = 1;
          code = first & 31;
          minimum = 0x80;
        } else if (first >= 0xe0 && first <= 0xef) {
          tails = 2;
          code = first & 15;
          minimum = 0x800;
        } else if (first >= 0xf0 && first <= 0xf4) {
          tails = 3;
          code = first & 7;
          minimum = 0x10000;
        } else throw bad("Invalid UTF-8 leading byte");
        if (tails > end - cursor) throw bad("Truncated UTF-8");
        for (int i = 0; i < tails; i++) {
          int next = wire.get(cursor++) & 255;
          if ((next & 0xc0) != 0x80) throw bad("Invalid UTF-8 continuation");
          code = code << 6 | next & 63;
        }
        if (code < minimum || code > 0x10ffff || (code >= 0xd800 && code <= 0xdfff))
          throw bad("Invalid Unicode scalar");
        units += code >= 0x10000 ? 2 : 1;
      }
      return units;
    }

    DataArray inspect(Schema schema, Limits limits, int end, int group, int depth) {
      if (depth > maxDepth) throw bad("Unknown group nesting budget exceeded");
      List<Object> fields = new ArrayList<>();
      while (position < end) {
        int tag = tag(end), number = tag >>> 3, type = tag & 7;
        if (type == 4) {
          if (number != group) throw bad("Mismatched group");
          return new DataArray(fields.toArray());
        }
        budget.cells(4);
        Object value =
            switch (type) {
              case 0 -> {
                long n = varint(end);
                yield n >= 0 ? n : unsigned(n);
              }
              case 1 -> {
                long n = fixed(8, end);
                yield n >= 0 ? n : unsigned(n);
              }
              case 2 -> scalar(ProtoPlan.Kind.BYTES, end);
              case 3 -> inspect(schema, limits, end, number, depth + 1);
              case 5 -> fixed(4, end);
              default -> throw bad("Invalid wire type");
            };
        fields.add(
            Batch.copyOf(
                    schema,
                    Collections.singletonList(new Object[] {(long) number, (long) type, value}),
                    limits)
                .row(0));
      }
      if (group != 0) throw bad("Unterminated group");
      return new DataArray(fields.toArray());
    }

    void skip(int tag, int end, int depth) {
      switch (tag & 7) {
        case 0 -> varint(end);
        case 1 -> fixed(8, end);
        case 2 -> {
          int count = length(end);
          position += count;
        }
        case 3 -> {
          if (depth >= maxDepth) throw bad("Unknown group nesting budget exceeded");
          boolean closed = false;
          while (position < end) {
            int child = tag(end);
            if ((child & 7) == 4) {
              if ((child >>> 3) != (tag >>> 3)) throw bad("Mismatched group");
              closed = true;
              break;
            }
            skip(child, end, depth + 1);
          }
          if (!closed) throw bad("Unterminated unknown group");
        }
        case 5 -> fixed(4, end);
        default -> throw bad("Unexpected end group");
      }
    }
  }

  private static Object unsigned(long value) {
    return Values.normalize(
        value >= 0
            ? BigInteger.valueOf(value)
            : BigInteger.valueOf(value & Long.MAX_VALUE).setBit(63));
  }

  static byte[] encode(ProtoRow row) {
    Writer sizing = new Writer(null, row.plan().model.maxWireBytes);
    sizing.message(row);
    byte[] result = new byte[sizing.position];
    new Writer(result, result.length).message(row);
    return result;
  }

  private static final class Writer {
    final byte @Nullable [] target;
    final int limit;
    int position;

    Writer(byte @Nullable [] target, int limit) {
      this.target = target;
      this.limit = limit;
    }

    void octet(int value) {
      if (position >= limit) throw bad("Encoded wire budget exceeded");
      if (target != null) target[position] = (byte) value;
      position++;
    }

    void varint(long value) {
      while ((value & ~127L) != 0) {
        octet((int) value & 127 | 128);
        value >>>= 7;
      }
      octet((int) value);
    }

    void fixed(long value, int count) {
      for (int i = 0; i < count; i++) {
        octet((int) value);
        value >>>= 8;
      }
    }

    void message(ProtoRow row) {
      var data = DataLibrary.factory().getUncached();
      for (int i = 0; i < row.plan().size(); i++) {
        if (!row.has(i)) continue;
        var field = row.plan().field(i);
        Object value = data.read(row, i);
        if (field.repeated()) {
          DataArray array = (DataArray) value;
          if (field.packed()) {
            Writer size = new Writer(null, limit);
            for (int j = 0; j < array.size(); j++) size.scalar(field.kind(), array.get(j));
            varint((long) field.number() << 3 | 2);
            varint(size.position);
            for (int j = 0; j < array.size(); j++) scalar(field.kind(), array.get(j));
          } else for (int j = 0; j < array.size(); j++) field(field, array.get(j));
        } else field(field, value);
      }
      for (byte b : row.unknown) octet(b);
    }

    void field(ProtoPlan.FieldPlan field, Object value) {
      varint((long) field.number() << 3 | field.kind().wire);
      if (field.kind().message()) {
        ProtoRow row = (ProtoRow) value;
        if (field.kind() == ProtoPlan.Kind.MESSAGE) {
          Writer size = new Writer(null, limit);
          size.message(row);
          varint(size.position);
          if (target == null) {
            if (size.position > limit - position) throw bad("Encoded wire budget exceeded");
            position += size.position;
            return;
          }
        }
        message(row);
        if (field.kind() == ProtoPlan.Kind.GROUP) varint((long) field.number() << 3 | 4);
      } else scalar(field.kind(), value);
    }

    long integer(Object value) {
      if (value instanceof Long n) return n;
      try {
        return com.oracle.truffle.api.interop.InteropLibrary.getUncached()
            .asBigInteger(value)
            .longValue();
      } catch (com.oracle.truffle.api.interop.UnsupportedMessageException e) {
        throw bad("Invalid integer");
      }
    }

    void scalar(ProtoPlan.Kind kind, Object value) {
      switch (kind) {
        case INT32, INT64, UINT32, UINT64, ENUM -> varint(integer(value));
        case SINT32 -> {
          int n = (int) integer(value);
          varint(Integer.toUnsignedLong(n << 1 ^ n >> 31));
        }
        case SINT64 -> {
          long n = integer(value);
          varint(n << 1 ^ n >> 63);
        }
        case FIXED32, SFIXED32 -> fixed(integer(value), 4);
        case FIXED64, SFIXED64 -> fixed(integer(value), 8);
        case BOOL -> varint((Boolean) value ? 1 : 0);
        case FLOAT -> fixed(Float.floatToRawIntBits(((Double) value).floatValue()), 4);
        case DOUBLE -> fixed(Double.doubleToRawLongBits((Double) value), 8);
        case STRING -> {
          byte[] bytes = ((String) value).getBytes(StandardCharsets.UTF_8);
          varint(bytes.length);
          for (byte b : bytes) octet(b);
        }
        case BYTES -> {
          DataArray bytes = (DataArray) value;
          varint(bytes.size());
          for (int i = 0; i < bytes.size(); i++) octet(((Long) bytes.get(i)).intValue());
        }
        default -> throw bad("Message must use structured encoder");
      }
    }
  }
}
