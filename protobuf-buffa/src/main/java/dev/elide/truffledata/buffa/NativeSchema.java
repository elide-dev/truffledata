package dev.elide.truffledata.buffa;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.protobuf.ProtoPlan;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned, descriptor-free schema program. All offsets are checked by the native reader. */
final class NativeSchema {
  private NativeSchema() {}

  static byte[] encode(ProtoPlan root, int maximum) {
    if (maximum < 8) throw new ContractViolation("Native schema budget");
    List<ProtoPlan> types = new ArrayList<>();
    Map<String, Integer> indices = new HashMap<>();
    types.add(root);
    indices.put(root.name(), 0);
    for (int i = 0; i < types.size(); i++) {
      ProtoPlan plan = types.get(i);
      for (int j = 0; j < plan.size(); j++)
        if (plan.field(j).kind().message()) {
          ProtoPlan nested = plan.nested(j);
          if (!indices.containsKey(nested.name())) {
            if (types.size() >= maximum / 12)
              throw new ContractViolation("Native schema type budget");
            indices.put(nested.name(), types.size());
            types.add(nested);
          }
        }
    }
    // A bounded staging allocation, made once per compiled plan, not per message.
    ByteBuffer out = ByteBuffer.allocate(maximum).order(ByteOrder.LITTLE_ENDIAN);
    try {
      out.putInt(0x54444232).putInt(types.size());
      int[] offsets = new int[types.size()];
      int next = Math.addExact(8, Math.multiplyExact(4, types.size()));
      for (int i = 0; i < types.size(); i++) {
        offsets[i] = next;
        next = Math.addExact(next, Math.addExact(8, Math.multiplyExact(40, types.get(i).size())));
        out.putInt(offsets[i]);
      }
      out.position(next);
      for (int i = 0; i < types.size(); i++) {
        ProtoPlan plan = types.get(i);
        int base = offsets[i];
        out.putInt(base, plan.size())
            .putInt(
                base + 4,
                (plan.definition().mapEntry() ? 1 : 0) | (specialJson(plan.name()) ? 2 : 0));
        for (int j = 0; j < plan.size(); j++) {
          var f = plan.field(j);
          int at = base + 8 + 40 * j;
          out.putInt(at, f.number()).putInt(at + 4, f.kind().ordinal() + 1);
          int flags =
              (f.repeated() ? 1 : 0)
                  | (f.map() ? 2 : 0)
                  | (f.explicitPresence() ? 4 : 0)
                  | (f.required() ? 8 : 0)
                  | (f.enumType() != null && f.enumType().open() ? 16 : 0);
          out.putInt(at + 8, flags)
              .putInt(at + 12, f.kind().message() ? indices.get(f.typeName()) : -1);
          var enumeration = f.enumType();
          if (enumeration != null) {
            int table = out.position();
            out.putInt(at + 16, table).putInt(at + 20, enumeration.values().size());
            out.position(Math.addExact(table, Math.multiplyExact(enumeration.values().size(), 12)));
            for (var entry : new TreeMap<>(enumeration.values()).entrySet()) {
              out.putInt(table, entry.getValue());
              string(out, table + 4, entry.getKey());
              table += 12;
            }
          }
          String name =
              f.kind() == ProtoPlan.Kind.GROUP
                  ? f.typeName().substring(f.typeName().lastIndexOf('.') + 1)
                  : f.name();
          string(out, at + 24, name);
          string(out, at + 32, f.jsonName());
        }
      }
      return Arrays.copyOf(out.array(), out.position());
    } catch (IndexOutOfBoundsException
        | IllegalArgumentException
        | ArithmeticException
        | BufferOverflowException failure) {
      throw new ContractViolation("Native schema budget exceeded");
    }
  }

  private static boolean specialJson(String name) {
    return switch (name) {
      case "google.protobuf.Any",
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
          "google.protobuf.BytesValue" ->
          true;
      default -> false;
    };
  }

  private static void string(ByteBuffer output, int at, String value) {
    if (value.length() > output.remaining())
      throw new ContractViolation("Native schema string budget");
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    output.putInt(at, output.position()).putInt(at + 4, bytes.length);
    output.put(bytes);
  }
}
