package dev.elide.truffledata.protobuf;

/** Emits schema-specific sizes and recursive writes into one bounded output cursor. */
final class EncoderGenerator {
  private static final String OUTPUT = "dev.elide.truffledata.protobuf.GeneratedOutput";

  private EncoderGenerator() {}

  private static String message(ProtoPlan.FieldPlan field, String value) {
    return "((Message_" + field.typeName().replace('.', '$') + ")" + value + ")";
  }

  private static String element(ProtoPlan.FieldPlan field, String value) {
    String type =
        switch (field.kind()) {
          case STRING -> "String";
          case BYTES -> "DataArray";
          case MESSAGE, GROUP, UINT64, FIXED64 -> "Object";
          case FLOAT, DOUBLE -> "Double";
          case BOOL -> "Boolean";
          default -> "Long";
        };
    String cast = "((" + type + ")" + value + ")";
    return cast
        + switch (type) {
          case "Long" -> ".longValue()";
          case "Double" -> ".doubleValue()";
          case "Boolean" -> ".booleanValue()";
          default -> "";
        };
  }

  private static int tagSize(ProtoPlan.FieldPlan field) {
    long tag = ((long) field.number() << 3) | field.kind().wire;
    return (70 - Long.numberOfLeadingZeros(tag)) / 7;
  }

  /** Adds the scalar payload only; packed values do not have individual tags. */
  private static void scalarSize(StringBuilder out, ProtoPlan.FieldPlan field, String value) {
    if (field.kind() == ProtoPlan.Kind.STRING || field.kind() == ProtoPlan.Kind.BYTES) {
      out.append(" int length=")
          .append(
              field.kind() == ProtoPlan.Kind.STRING
                  ? OUTPUT + ".utf8Length(" + value + ")"
                  : value + ".size()")
          .append("; size += (long)length + ")
          .append(OUTPUT)
          .append(".varintSize(length);\n");
    } else if (field.kind().wire == 1 || field.kind().wire == 5)
      out.append(" size += ").append(field.kind().wire == 1 ? 8 : 4).append("L;\n");
    else
      out.append(" size += ")
          .append(OUTPUT)
          .append(".varintSize(")
          .append(OwnedGenerator.wireValue(field, value))
          .append(");\n");
    out.append(" checkedSize(size);\n");
  }

  private static void scalarWrite(StringBuilder out, ProtoPlan.FieldPlan field, String value) {
    if (field.kind() == ProtoPlan.Kind.STRING)
      out.append(" output.utf8(")
          .append(value)
          .append(',')
          .append(OUTPUT)
          .append(".utf8Length(")
          .append(value)
          .append("));\n");
    else if (field.kind() == ProtoPlan.Kind.BYTES)
      out.append(" output.bytes(").append(value).append(");\n");
    else if (field.kind().wire == 1 || field.kind().wire == 5)
      out.append(" output.fixed(")
          .append(OwnedGenerator.wireValue(field, value))
          .append(',')
          .append(field.kind().wire == 1 ? 8 : 4)
          .append(");\n");
    else
      out.append(" output.varint(").append(OwnedGenerator.wireValue(field, value)).append(");\n");
  }

  private static void fieldSize(StringBuilder out, ProtoPlan.FieldPlan field, String value) {
    out.append(" size += ").append(tagSize(field)).append("L;\n");
    if (field.kind().message()) {
      out.append(" int length=")
          .append(message(field, value))
          .append(".sizeForGraph();")
          .append(" size += (long)length + ")
          .append(
              field.kind() == ProtoPlan.Kind.GROUP
                  ? Integer.toString(tagSize(field))
                  : OUTPUT + ".varintSize(length)")
          .append("; checkedSize(size);\n");
    } else scalarSize(out, field, value);
  }

  private static void fieldWrite(StringBuilder out, ProtoPlan.FieldPlan field, String value) {
    out.append(" output.varint(")
        .append(((long) field.number() << 3) | field.kind().wire)
        .append("L);\n");
    if (field.kind().message()) {
      String child = message(field, value);
      if (field.kind() == ProtoPlan.Kind.MESSAGE)
        out.append(" output.varint(").append(child).append(".sizeForGraph());\n");
      out.append(' ').append(child).append(".writeForGraph(output);\n");
      if (field.kind() == ProtoPlan.Kind.GROUP)
        out.append(" output.varint(").append(((long) field.number() << 3) | 4).append("L);\n");
    } else scalarWrite(out, field, value);
  }

  private static String value(ProtoPlan.FieldPlan field, int ordinal) {
    return field.kind().message() ? "f" + ordinal : OwnedGenerator.storedValue(field, ordinal);
  }

  static void append(StringBuilder out, ProtoPlan.Type schema, boolean encodeRoot) {
    var fields = schema.fields();
    if (encodeRoot)
      out.append(
          " @Override @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary"
              + " public byte[] encode() { var output=new "
              + OUTPUT
              + "(allocateOutput(sizeForGraph())); writeForGraph(output); return output.finish();"
              + " }\n");
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      if (!field.repeated() || !field.packed()) continue;
      out.append(" private int packedSize")
          .append(i)
          .append("() { long size=0; for(int n=0;n<f")
          .append(i)
          .append(".size();n++) {\n");
      scalarSize(out, field, element(field, "f" + i + ".get(n)"));
      out.append(" } return checkedSize(size); }\n");
    }
    out.append(" private int sizeForGraph() {\n");
    if (OwnedGenerator.inlineSupported(schema)) out.append(" return encodedSize(); }\n");
    else {
      out.append(" long size=unknownSize();\n");
      for (int i = 0; i < fields.size(); i++) {
        var field = fields.get(i);
        out.append(" if(isSet(").append(1L << i).append("L)) {\n");
        if (field.repeated()) {
          if (field.packed())
            out.append(" int length=packedSize")
                .append(i)
                .append("(); size += ")
                .append(tagSize(field))
                .append("L + length + ")
                .append(OUTPUT)
                .append(".varintSize(length); checkedSize(size);\n");
          else {
            out.append(" for(int n=0;n<f").append(i).append(".size();n++) {\n");
            fieldSize(out, field, element(field, "f" + i + ".get(n)"));
            out.append(" }\n");
          }
        } else fieldSize(out, field, value(field, i));
        out.append(" }\n");
      }
      out.append(" return checkedSize(size); }\n");
    }
    out.append(" private void writeForGraph(").append(OUTPUT).append(" output) {\n");
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      out.append(" if(isSet(").append(1L << i).append("L)) {\n");
      if (field.repeated()) {
        if (field.packed()) {
          out.append(" output.varint(")
              .append(((long) field.number() << 3) | 2)
              .append("L); output.varint(packedSize")
              .append(i)
              .append("());\n")
              .append(" for(int n=0;n<f")
              .append(i)
              .append(".size();n++) {\n");
          scalarWrite(out, field, element(field, "f" + i + ".get(n)"));
        } else {
          out.append(" for(int n=0;n<f").append(i).append(".size();n++) {\n");
          fieldWrite(out, field, element(field, "f" + i + ".get(n)"));
        }
        out.append(" }\n");
      } else fieldWrite(out, field, value(field, i));
      out.append(" }\n");
    }
    out.append(" writeUnknown(output); }\n");
  }
}
