package dev.elide.truffledata.buffa;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.protobuf.Layout;
import dev.elide.truffledata.protobuf.ProtoPlan;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned, descriptor-free schema program. All offsets are checked by the native reader. */
final class NativeSchema {
  private NativeSchema() {}

  static List<ProtoPlan> types(ProtoPlan root, List<ProtoPlan> registry, int maximum) {
    if (maximum < 8) throw new ContractViolation("Native schema budget");
    List<ProtoPlan> types = new ArrayList<>();
    Map<String, Integer> indices = new HashMap<>();
    types.add(root);
    indices.put(root.name(), 0);
    if (registry.size() >= maximum / 16) throw new ContractViolation("Native registry budget");
    for (ProtoPlan registered : registry) {
      Integer previous = indices.putIfAbsent(registered.name(), types.size());
      if (previous == null) types.add(registered);
      else if (types.get(previous) != registered)
        throw new ContractViolation("Conflicting Any type");
    }
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
          } else if (!types
              .get(indices.get(nested.name()))
              .definition()
              .equals(nested.definition())) {
            throw new ContractViolation("Conflicting nested Any contract");
          }
        }
    }
    return List.copyOf(types);
  }

  static byte[] encode(List<ProtoPlan> types, List<ProtoPlan> registry, int maximum) {
    Map<String, Integer> indices = new HashMap<>();
    for (int i = 0; i < types.size(); i++) indices.put(types.get(i).name(), i);
    Set<String> allowed = new HashSet<>();
    for (ProtoPlan plan : registry)
      if (!allowed.add(plan.name())) throw new ContractViolation("Duplicate Any type");
    byte[] layout = Layout.compile(types).program();
    // A bounded staging allocation, made once per compiled plan, not per message.
    ByteBuffer out = ByteBuffer.allocate(maximum).order(ByteOrder.LITTLE_ENDIAN);
    try {
      out.putInt(0x54444233).putInt(types.size());
      int[] offsets = new int[types.size()];
      int next = Math.addExact(8, Math.multiplyExact(4, types.size()));
      for (int i = 0; i < types.size(); i++) {
        offsets[i] = next;
        next = Math.addExact(next, Math.addExact(16, Math.multiplyExact(40, types.get(i).size())));
        out.putInt(offsets[i]);
      }
      out.position(next);
      for (int i = 0; i < types.size(); i++) {
        ProtoPlan plan = types.get(i);
        int base = offsets[i];
        out.putInt(base, plan.size())
            .putInt(
                base + 4,
                (plan.definition().mapEntry() ? 1 : 0)
                    | (plan.jsonType().ordinal() << 1)
                    | (allowed.contains(plan.name()) ? 256 : 0)
                    | (plan.definition().fields().stream().noneMatch(ProtoPlan.FieldPlan::required)
                        ? 512
                        : 0));
        string(out, base + 8, plan.name());
        for (int j = 0; j < plan.size(); j++) {
          var f = plan.field(j);
          int at = base + 16 + 40 * j;
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
      // TDL1 slab layout (docs/SLAB.md): appended after every TDB3 table and string, which only
      // header offsets reach, so ABI 9 readers never see it. The trailer locates the section.
      int section = (out.position() + 3) & ~3;
      out.position(section);
      out.put(layout);
      out.putInt(section).putInt(Layout.PROGRAM_MAGIC);
      return Arrays.copyOf(out.array(), out.position());
    } catch (IndexOutOfBoundsException
        | IllegalArgumentException
        | ArithmeticException
        | BufferOverflowException failure) {
      throw new ContractViolation("Native schema budget exceeded");
    }
  }

  private static void string(ByteBuffer output, int at, String value) {
    if (value.length() > output.remaining())
      throw new ContractViolation("Native schema string budget");
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    output.putInt(at, output.position()).putInt(at + 4, bytes.length);
    output.put(bytes);
  }
}
