package dev.elide.truffledata.protobuf;

import java.util.*;

/** Emits owned field storage and wire dispatch for non-repeated scalar schemas. */
final class OwnedGenerator {
  private OwnedGenerator() {}

  static boolean supports(ProtoPlan.Type type) {
    return type.fields().size() <= 64
        && type.fields().stream().noneMatch(f -> f.repeated() || f.kind().message());
  }

  static boolean inlineSupported(ProtoPlan.Type schema) {
    return supports(schema)
        && schema.fields().size() <= 8
        && schema.fields().stream()
            .allMatch(f -> Set.of("long", "double", "boolean").contains(type(f)));
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

  static String storage(ProtoPlan.FieldPlan field) {
    return switch (field.kind()) {
      case INT32, UINT32, SINT32, FIXED32, SFIXED32, ENUM -> "int";
      case FLOAT -> "float";
      default -> type(field);
    };
  }

  static String storedValue(ProtoPlan.FieldPlan field, int ordinal) {
    String value = "f" + ordinal;
    return switch (field.kind()) {
      case UINT32, FIXED32 -> "Integer.toUnsignedLong(" + value + ")";
      case INT32, SINT32, SFIXED32, ENUM -> "(long)" + value;
      case FLOAT -> "(double)" + value;
      default -> value;
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

  private static String wireValue(ProtoPlan.FieldPlan field, String value) {
    return switch (field.kind()) {
      case UINT64, FIXED64 ->
          "dev.elide.truffledata.protobuf.GeneratedOutput.integer(" + value + ")";
      case SINT32 ->
          "Integer.toUnsignedLong(((int)(" + value + ") << 1) ^ ((int)(" + value + ") >> 31))";
      case SINT64 -> "((" + value + " << 1) ^ (" + value + " >> 63))";
      case FLOAT -> "Float.floatToRawIntBits((float)(" + value + "))";
      case DOUBLE -> "Double.doubleToRawLongBits(" + value + ")";
      case BOOL -> "(" + value + " ? 1L : 0L)";
      default -> value;
    };
  }

  /** Primitive records mirror stock's memoized sizing and need no output cursor allocation. */
  private static void primitiveEncoder(StringBuilder out, ProtoPlan.Type schema) {
    var fields = schema.fields();
    out.append(
        " private int memoizedSize=-1;\n"
            + " private int encodedSize() { int cached=memoizedSize; if(cached>=0) return cached;"
            + " long size=unknownSize();\n");
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      long tag = ((long) field.number() << 3) | field.kind().wire;
      int tagSize = (70 - Long.numberOfLeadingZeros(tag)) / 7;
      out.append(" if(isSet(")
          .append(1L << i)
          .append("L)) size += ")
          .append(tagSize)
          .append("L + ");
      if (field.kind().wire == 1 || field.kind().wire == 5)
        out.append(field.kind().wire == 1 ? 8 : 4);
      else
        out.append("dev.elide.truffledata.protobuf.GeneratedOutput.varintSize(")
            .append(wireValue(field, "(" + storedValue(field, i) + ")"))
            .append(')');
      out.append(";\n");
    }
    out.append(" return memoizedSize=checkedSize(size); }\n")
        .append(
            " @Override @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary public byte[]"
                + " encode() { byte[] output=allocateOutput(encodedSize()); int position=0;\n");
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      out.append(" if(isSet(").append(1L << i).append("L)) {\n");
      long tag = ((long) field.number() << 3) | field.kind().wire;
      while ((tag & ~127L) != 0) {
        out.append(" output[position++]=(byte)").append((tag & 127) | 128).append(";\n");
        tag >>>= 7;
      }
      out.append(" output[position++]=(byte)").append(tag).append(";\n");
      String value = wireValue(field, "(" + storedValue(field, i) + ")");
      if (field.kind().wire == 1 || field.kind().wire == 5) {
        out.append(" long bits=").append(value).append(";\n");
        for (int octet = 0; octet < (field.kind().wire == 1 ? 8 : 4); octet++)
          out.append(" output[position++]=(byte)(bits >>> ").append(octet * 8).append(");\n");
      } else
        out.append(
                " position=dev.elide.truffledata.protobuf.GeneratedOutput.varint(output,position,")
            .append(value)
            .append(");\n");
      out.append(" }\n");
    }
    out.append(" return finishEncoded(output,position); }\n");
  }

  private static void encoder(StringBuilder out, ProtoPlan.Type schema) {
    var fields = schema.fields();
    out.append(
        " @Override @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary public byte[]"
            + " encode() { long size=unknownSize();\n");
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      boolean string = field.kind() == ProtoPlan.Kind.STRING,
          bytes = field.kind() == ProtoPlan.Kind.BYTES;
      out.append(string || bytes ? " int" : " long")
          .append(" wire")
          .append(i)
          .append("=0;\n")
          .append(" if (isSet(")
          .append(1L << i)
          .append("L)) { wire")
          .append(i)
          .append('=');
      if (string)
        out.append("dev.elide.truffledata.protobuf.GeneratedOutput.utf8Length(f")
            .append(i)
            .append(')');
      else if (bytes) out.append("f").append(i).append(".size()");
      else out.append(wireValue(field, "(" + storedValue(field, i) + ")"));
      long tag = ((long) field.number() << 3) | field.kind().wire;
      int tagSize = (70 - Long.numberOfLeadingZeros(tag)) / 7;
      out.append("; size += ").append(tagSize).append("L + ");
      if (field.kind().wire == 1) out.append('8');
      else if (field.kind().wire == 5) out.append('4');
      else {
        out.append("dev.elide.truffledata.protobuf.GeneratedOutput.varintSize(wire")
            .append(i)
            .append(')');
        if (string || bytes) out.append(" + (long)wire").append(i);
      }
      out.append("; }\n");
    }
    out.append(
        " var output = new"
            + " dev.elide.truffledata.protobuf.GeneratedOutput(allocateOutput(size));\n");
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      out.append(" if (isSet(")
          .append(1L << i)
          .append("L)) { output.varint(")
          .append(((long) field.number() << 3) | field.kind().wire)
          .append("L); ");
      if (field.kind() == ProtoPlan.Kind.STRING)
        out.append("output.utf8(f").append(i).append(",wire").append(i).append(");");
      else if (field.kind() == ProtoPlan.Kind.BYTES)
        out.append("output.bytes(f").append(i).append(");");
      else if (field.kind().wire == 1 || field.kind().wire == 5)
        out.append("output.fixed(wire")
            .append(i)
            .append(',')
            .append(field.kind().wire == 1 ? 8 : 4)
            .append(");");
      else out.append("output.varint(wire").append(i).append(");");
      out.append(" }\n");
    }
    out.append(" writeUnknown(output); return output.finish(); }\n");
  }

  static String builderName(ProtoPlan.Type schema) {
    return "Builder_" + schema.name().replace('.', '$');
  }

  private static String fallback(ProtoPlan.FieldPlan field, int ordinal) throws Exception {
    String kind = type(field);
    return kind.equals("Object") || kind.equals("String") || kind.equals("DataArray")
        ? "(" + kind + ")plan.field(" + ordinal + ").defaultValue()"
        : ContractPlugin.literal(field.defaultValue());
  }

  private static String zero(ProtoPlan.FieldPlan field, int ordinal) {
    String value = "f" + ordinal;
    return switch (type(field)) {
      case "long" -> value + "==0";
      case "double" -> "Double.doubleToRawLongBits(" + value + ")==0";
      case "boolean" -> "!" + value;
      case "String" -> value + ".isEmpty()";
      case "DataArray" -> value + ".size()==0";
      default -> "((ExactNumber)" + value + ").decimal().signum()==0";
    };
  }

  private static void builder(StringBuilder out, ProtoPlan.Type schema) throws Exception {
    String builder = builderName(schema);
    var fields = schema.fields();
    out.append(" public static final class ")
        .append(builder)
        .append(" { private final ProtoPlan plan; private long presence;\n");
    for (int i = 0; i < fields.size(); i++)
      out.append(" private ").append(type(fields.get(i))).append(" f").append(i).append(";\n");
    out.append(" private ").append(builder).append("(ProtoPlan plan) { this.plan=plan;");
    for (int i = 0; i < fields.size(); i++)
      out.append(" f").append(i).append('=').append(fallback(fields.get(i), i)).append(';');
    out.append(" }\n");
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      out.append(" public ")
          .append(builder)
          .append(" set_")
          .append(field.name())
          .append('(')
          .append(type(field))
          .append(" value) {")
          .append(" dev.elide.truffledata.protobuf.GeneratedMessage.validateField(plan,")
          .append(i)
          .append(",value);");
      if (field.oneof() >= 0) {
        for (int j = 0; j < fields.size(); j++)
          if (j != i && fields.get(j).oneof() == field.oneof())
            out.append(" clear_").append(fields.get(j).name()).append("();");
      }
      out.append(" f")
          .append(i)
          .append("=value; presence |= ")
          .append(1L << i)
          .append("L; return this; }\n");
      out.append(" public ")
          .append(builder)
          .append(" clear_")
          .append(field.name())
          .append("() { f")
          .append(i)
          .append('=')
          .append(fallback(field, i))
          .append("; presence &= ~")
          .append(1L << i)
          .append("L; return this; }\n");
    }
    out.append(" @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary public ")
        .append(name(schema))
        .append(" build() { long bits=presence; long cells=")
        .append(fields.size())
        .append("L; long payload=0;\n");
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      if (field.required())
        out.append(" if ((bits & ")
            .append(1L << i)
            .append(
                "L)==0) {"
                    + " com.oracle.truffle.api.CompilerDirectives.transferToInterpreterAndInvalidate();"
                    + " throw new ContractViolation(\"Missing required field\"); }\n");
      if (!field.explicitPresence())
        out.append(" if (")
            .append(zero(field, i))
            .append(") bits &= ~")
            .append(1L << i)
            .append("L;\n");
      if (field.kind() == ProtoPlan.Kind.STRING)
        out.append(" payload += 2L * f").append(i).append(".length();\n");
      if (field.kind() == ProtoPlan.Kind.BYTES)
        out.append(" cells += f").append(i).append(".size();\n");
    }
    out.append(" if (cells > plan.limits().maxCells() || payload > plan.limits().maxStringBytes())")
        .append(" throw new ContractViolation(\"Generated build budget\");\n")
        .append(" return new ")
        .append(name(schema))
        .append("(plan,bits,")
        .append(name(schema))
        .append(".EMPTY");
    for (int i = 0; i < fields.size(); i++) out.append(",f").append(i);
    out.append("); }\n }\n");
  }

  static void append(StringBuilder out, ProtoPlan.Type schema, int index) throws Exception {
    if (!supports(schema)) return;
    builder(out, schema);
    String name = name(schema);
    var fields = schema.fields();
    out.append(" public static final class ")
        .append(name)
        .append(
            " extends dev.elide.truffledata.protobuf.GeneratedMessage {\n"
                + " private static final byte[] EMPTY = new byte[0];\n");
    for (int i = 0; i < fields.size(); i++)
      out.append(" private final ")
          .append(storage(fields.get(i)))
          .append(" f")
          .append(i)
          .append(";\n");
    out.append(" private ").append(name).append("(ProtoPlan plan, long presence, byte[] unknown");
    for (int i = 0; i < fields.size(); i++)
      out.append(", ").append(type(fields.get(i))).append(" f").append(i);
    out.append(") { super(plan, presence, unknown);");
    for (int i = 0; i < fields.size(); i++)
      out.append(" this.f")
          .append(i)
          .append("=(")
          .append(storage(fields.get(i)))
          .append(")f")
          .append(i)
          .append(";");
    out.append(" }\n");
    for (int i = 0; i < fields.size(); i++)
      out.append(" public ")
          .append(type(fields.get(i)))
          .append(" get_")
          .append(fields.get(i).name())
          .append("() { return ")
          .append(storedValue(fields.get(i), i))
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
          out.append(" case ")
              .append(i)
              .append(" -> ")
              .append(storedValue(fields.get(i), i))
              .append(';');
      out.append(" default -> ")
          .append(
              returnType.equals("Object")
                  ? "throw new ContractViolation(\"Invalid generated field/type\")"
                  : "super." + method + "(ordinal)")
          .append("; }; }\n");
    }
    if (inlineSupported(schema)) primitiveEncoder(out, schema);
    else encoder(out, schema);
    if (inlineSupported(schema)) {
      out.append(" @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary private static ")
          .append(name)
          .append(
              " slowDecode(ProtoPlan plan, byte[] wire) { return decodeInput(plan,wire,false); }\n")
          .append(" private static ")
          .append(name)
          .append(
              " decode(ProtoPlan plan, byte[] wire) { return decodeInput(plan,wire,false); }\n");
    }
    out.append(" private static ")
        .append(name)
        .append(
            inlineSupported(schema)
                ? " decodeInput(ProtoPlan plan, byte[] wire, boolean inline) {\n"
                : " decode(ProtoPlan plan, byte[] wire) {\n")
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
            .append(" -> true; default -> false; })) { ")
            .append(inlineSupported(schema) ? "if (inline) return slowDecode(plan,wire); " : "")
            .append("input.retain(start); continue; }");
      }
      if (f.oneof() >= 0) {
        for (int j = 0; j < fields.size(); j++) {
          if (j != i && fields.get(j).oneof() == f.oneof()) {
            out.append(" presence &= ~")
                .append(1L << j)
                .append("L; f")
                .append(j)
                .append('=')
                .append(fallback(fields.get(j), j))
                .append(';');
          }
        }
      }
      out.append(" f").append(i).append("=value; presence |= ").append(1L << i).append("L; }\n");
    }
    out.append(" default -> { ")
        .append(inlineSupported(schema) ? "if (inline) return slowDecode(plan,wire); " : "")
        .append("input.unknown(start,tag); } } }\n");
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      if (field.required())
        out.append(" if ((presence & ")
            .append(1L << i)
            .append(
                "L)==0) {"
                    + " com.oracle.truffle.api.CompilerDirectives.transferToInterpreterAndInvalidate();"
                    + " throw new ContractViolation(\"Missing required field\"); }\n");
      if (!field.explicitPresence()) {
        String zero =
            switch (type(field)) {
              case "long" -> "f" + i + "==0";
              case "double" -> "Double.doubleToRawLongBits(f" + i + ")==0";
              case "boolean" -> "!f" + i;
              case "String" -> "f" + i + ".isEmpty()";
              case "DataArray" -> "f" + i + ".size()==0";
              default -> "((ExactNumber)f" + i + ").decimal().signum()==0";
            };
        out.append(" if (").append(zero).append(") presence &= ~").append(1L << i).append("L;\n");
      }
    }
    out.append(" return new ").append(name).append("(plan,presence,input.unknownFields()");
    for (int i = 0; i < fields.size(); i++) out.append(",f").append(i);
    out.append("); }\n }\n");
  }
}
