package dev.elide.truffledata.protobuf;

import java.util.*;

/** Emits owned field storage and wire dispatch for non-repeated scalar schemas. */
final class OwnedGenerator {
  private OwnedGenerator() {}

  static boolean supports(ProtoPlan.Type type) {
    return type.fields().size() <= 64
        && type.fields().stream().noneMatch(f -> f.repeated() || f.kind().message());
  }

  static String name(ProtoPlan.Type type) {
    return "Message_" + type.name().replace('.', '$');
  }

  private static String type(ProtoPlan.FieldPlan field) {
    return switch (field.kind()) {
      case FLOAT, DOUBLE -> "double";
      case BOOL -> "boolean";
      case STRING -> "String";
      case BYTES -> "DataArray";
      case UINT64, FIXED64 -> "Object";
      default -> "long";
    };
  }

  private static String read(ProtoPlan.FieldPlan field) {
    return switch (field.kind()) {
      case INT32, ENUM -> "(long) (int) input.varint()";
      case INT64 -> "input.varint()";
      case UINT32 -> "input.varint() & 0xffffffffL";
      case SINT32 ->
          "(long) dev.elide.truffledata.protobuf.GeneratedInput.zigzag32(input.varint())";
      case SINT64 -> "dev.elide.truffledata.protobuf.GeneratedInput.zigzag64(input.varint())";
      case SFIXED32 -> "(long) (int) input.fixed(4)";
      case FIXED32 -> "input.fixed(4)";
      case SFIXED64 -> "input.fixed(8)";
      case UINT64 -> "dev.elide.truffledata.protobuf.GeneratedInput.unsigned(input.varint())";
      case FIXED64 -> "dev.elide.truffledata.protobuf.GeneratedInput.unsigned(input.fixed(8))";
      case FLOAT -> "(double) Float.intBitsToFloat((int) input.fixed(4))";
      case DOUBLE -> "Double.longBitsToDouble(input.fixed(8))";
      case BOOL -> "input.varint() != 0";
      case STRING -> "input.string()";
      case BYTES -> "input.bytes()";
      default -> throw new IllegalArgumentException("Not a scalar schema");
    };
  }

  static void append(StringBuilder out, ProtoPlan.Type schema, int index) throws Exception {
    if (!supports(schema)) return;
    String name = name(schema);
    var fields = schema.fields();
    out.append(" public static final class ")
        .append(name)
        .append(" extends dev.elide.truffledata.protobuf.GeneratedMessage {\n");
    for (int i = 0; i < fields.size(); i++)
      out.append(" private final ")
          .append(type(fields.get(i)))
          .append(" f")
          .append(i)
          .append(";\n");
    out.append(" private ").append(name).append("(ProtoPlan plan, long presence, byte[] unknown");
    for (int i = 0; i < fields.size(); i++)
      out.append(", ").append(type(fields.get(i))).append(" f").append(i);
    out.append(") { super(plan, presence, unknown);");
    for (int i = 0; i < fields.size(); i++)
      out.append(" this.f").append(i).append("=f").append(i).append(";");
    out.append(" }\n");
    for (int i = 0; i < fields.size(); i++)
      out.append(" public ")
          .append(type(fields.get(i)))
          .append(" get_")
          .append(fields.get(i).name())
          .append("() { return f")
          .append(i)
          .append("; }\n");
    for (String returnType : List.of("Object", "long", "double", "boolean")) {
      String method =
          switch (returnType) {
            case "long" -> "getLong";
            case "double" -> "getDouble";
            case "boolean" -> "getBoolean";
            default -> "get";
          };
      if (fields.stream()
          .noneMatch(f -> returnType.equals("Object") || type(f).equals(returnType))) {
        out.append(" @Override public ")
            .append(returnType)
            .append(' ')
            .append(method)
            .append("(int ordinal) { ")
            .append(
                returnType.equals("Object")
                    ? "throw new ContractViolation(\"Invalid generated field/type\");"
                    : "return super." + method + "(ordinal);")
            .append(" }\n");
        continue;
      }
      out.append(" @Override public ")
          .append(returnType)
          .append(' ')
          .append(method)
          .append("(int ordinal) { return switch (ordinal) {");
      for (int i = 0; i < fields.size(); i++)
        if (returnType.equals("Object") || type(fields.get(i)).equals(returnType))
          out.append(" case ").append(i).append(" -> f").append(i).append(';');
      out.append(" default -> ")
          .append(
              returnType.equals("Object")
                  ? "throw new ContractViolation(\"Invalid generated field/type\")"
                  : "super." + method + "(ordinal)")
          .append("; }; }\n");
    }
    out.append(" private static ")
        .append(name)
        .append(" decode(ProtoPlan plan, byte[] wire) {\n")
        .append(
            " var input = new dev.elide.truffledata.protobuf.GeneratedInput(plan, wire); long"
                + " presence=0;\n");
    for (int i = 0; i < fields.size(); i++) {
      String kind = type(fields.get(i));
      out.append(kind).append(" f").append(i).append("=");
      // Share immutable reference defaults with the bound plan; do not recreate bytes/numbers.
      if (kind.equals("Object") || kind.equals("String") || kind.equals("DataArray"))
        out.append('(').append(kind).append(")plan.field(").append(i).append(").defaultValue()");
      else out.append(ContractPlugin.literal(fields.get(i).defaultValue()));
      out.append(';');
    }
    out.append(
        " while (input.remaining()) { int start=input.position(); int tag=input.tag(); switch(tag)"
            + " {\n");
    for (int i = 0; i < fields.size(); i++) {
      var f = fields.get(i);
      out.append(" case ")
          .append((f.number() << 3) | f.kind().wire)
          .append(" -> { ")
          .append(type(f))
          .append(" value = ")
          .append(read(f))
          .append(';');
      if (f.enumType() != null && !f.enumType().open()) {
        out.append(" if (!(switch ((int)value) {");
        out.append("case ")
            .append(
                String.join(
                    ",",
                    f.enumType().values().values().stream()
                        .distinct()
                        .map(Object::toString)
                        .toList()))
            .append(" -> true; default -> false; })) { input.retain(start); continue; }");
      }
      if (f.oneof() >= 0) {
        for (int j = 0; j < fields.size(); j++) {
          if (j != i && fields.get(j).oneof() == f.oneof()) {
            out.append(" presence &= ~")
                .append(1L << j)
                .append("L; f")
                .append(j)
                .append('=')
                .append(ContractPlugin.literal(fields.get(j).defaultValue()))
                .append(';');
          }
        }
      }
      out.append(" f").append(i).append("=value; presence |= ").append(1L << i).append("L; }\n");
    }
    out.append(" default -> input.unknown(start,tag); } }\n");
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      if (field.required())
        out.append(" if ((presence & ")
            .append(1L << i)
            .append("L)==0) throw new ContractViolation(\"Missing required field\");\n");
      if (!field.explicitPresence()) {
        String zero =
            switch (type(field)) {
              case "long" -> "f" + i + "==0";
              case "double" -> "Double.doubleToRawLongBits(f" + i + ")==0";
              case "boolean" -> "!f" + i;
              case "String" -> "f" + i + ".isEmpty()";
              case "DataArray" -> "f" + i + ".size()==0";
              default -> "f" + i + ".equals(0L)";
            };
        out.append(" if (").append(zero).append(") presence &= ~").append(1L << i).append("L;\n");
      }
    }
    out.append(" return new ").append(name).append("(plan,presence,input.unknownFields()");
    for (int i = 0; i < fields.size(); i++) out.append(",f").append(i);
    out.append("); }\n }\n");
  }
}
