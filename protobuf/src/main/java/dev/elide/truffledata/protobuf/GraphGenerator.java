package dev.elide.truffledata.protobuf;

import java.util.*;

/**
 * Schema-specific graph states; nested merge storage is finished directly into concrete records.
 */
final class GraphGenerator {
  private final Set<String> supported = new HashSet<>();

  GraphGenerator(List<ProtoPlan.Type> types) {
    for (var type : types) if (type.fields().size() <= 64) supported.add(type.name());
    boolean changed;
    do {
      changed = false;
      for (var type : types)
        if (supported.contains(type.name()))
          for (var field : type.fields())
            if (field.kind().message() && !supported.contains(field.typeName())) {
              supported.remove(type.name());
              changed = true;
              break;
            }
    } while (changed);
  }

  boolean supports(ProtoPlan.Type type) {
    return supported.contains(type.name()) && !OwnedGenerator.supports(type);
  }

  private static String message(String name) {
    return "Message_" + name.replace('.', '$');
  }

  private static String state(String name) {
    return "Decode_" + name.replace('.', '$');
  }

  private static String type(ProtoPlan.FieldPlan f) {
    if (f.repeated()) return "DataArray";
    return switch (f.kind()) {
      case FLOAT, DOUBLE -> "double";
      case BOOL -> "boolean";
      case STRING -> "String";
      case BYTES -> "DataArray";
      case MESSAGE, GROUP, UINT64, FIXED64 -> "Object";
      default -> "long";
    };
  }

  private static String mutable(ProtoPlan.FieldPlan f) {
    return f.repeated()
        ? "java.util.@org.jspecify.annotations.Nullable ArrayList<Object>"
        : f.kind().message()
            ? "@org.jspecify.annotations.Nullable " + state(f.typeName())
            : storage(f);
  }

  private static String fallback(ProtoPlan.FieldPlan f, int i) throws Exception {
    if (f.repeated() || f.kind().message()) return "null";
    String type = type(f);
    String value =
        type.equals("Object") || type.equals("DataArray") || type.equals("String")
            ? "(" + type + ")plan.field(" + i + ").defaultValue()"
            : ContractPlugin.literal(f.defaultValue());
    return storage(f).equals(type) ? value : "(" + storage(f) + ")(" + value + ")";
  }

  private static String read(ProtoPlan.FieldPlan f) {
    return switch (f.kind()) {
      case INT32, ENUM -> "(long)(int)input.varint()";
      case INT64 -> "input.varint()";
      case UINT32 -> "input.varint() & 0xffffffffL";
      case SINT32 -> "(long)dev.elide.truffledata.protobuf.GeneratedInput.zigzag32(input.varint())";
      case SINT64 -> "dev.elide.truffledata.protobuf.GeneratedInput.zigzag64(input.varint())";
      case FIXED32 -> "input.fixed(4)";
      case SFIXED32 -> "(long)(int)input.fixed(4)";
      case SFIXED64 -> "input.fixed(8)";
      case UINT64 -> "dev.elide.truffledata.protobuf.GeneratedInput.unsigned(input.varint())";
      case FIXED64 -> "dev.elide.truffledata.protobuf.GeneratedInput.unsigned(input.fixed(8))";
      case FLOAT -> "(double)Float.intBitsToFloat((int)input.fixed(4))";
      case DOUBLE -> "Double.longBitsToDouble(input.fixed(8))";
      case BOOL -> "input.varint()!=0";
      case STRING -> "input.string()";
      case BYTES -> "input.bytes()";
      default -> throw new IllegalArgumentException("Structured read");
    };
  }

  private static void select(StringBuilder out, ProtoPlan.Type t, int ordinal) throws Exception {
    var field = t.fields().get(ordinal);
    if (field.oneof() >= 0)
      for (int i = 0; i < t.fields().size(); i++)
        if (i != ordinal && t.fields().get(i).oneof() == field.oneof())
          out.append(" f")
              .append(i)
              .append('=')
              .append(fallback(t.fields().get(i), i))
              .append("; presence &= ~")
              .append(1L << i)
              .append("L;\n");
  }

  private static void accept(StringBuilder out, ProtoPlan.Type type, int i) throws Exception {
    var field = type.fields().get(i);
    if (field.repeated())
      out.append(" input.cells(1); if(f")
          .append(i)
          .append("==null) f")
          .append(i)
          .append("=new ArrayList<>(4); f")
          .append(i)
          .append(".add(value);\n");
    else {
      select(out, type, i);
      out.append(" f").append(i).append('=');
      if (!field.kind().message()) out.append('(').append(storage(field)).append(')');
      out.append("value;\n");
    }
    out.append(" presence |= ").append(1L << i).append("L;\n");
  }

  private static void scalar(StringBuilder out, ProtoPlan.Type type, int i, boolean packed)
      throws Exception {
    var f = type.fields().get(i);
    if (f.kind().wire == 0) out.append(" long encoded=input.varint();\n");
    out.append(f.repeated() ? scalarType(f) : type(f))
        .append(" value=")
        .append(f.kind().wire == 0 ? read(f).replace("input.varint()", "encoded") : read(f))
        .append(";\n");
    switch (f.kind()) {
      case INT32, ENUM, UINT32 -> out.append(" if(value!=encoded) input.discardEncodedSize();\n");
      case SINT32 ->
          out.append(" if(encoded!=(encoded&0xffffffffL)) input.discardEncodedSize();\n");
      case BOOL -> out.append(" if(encoded!=0 && encoded!=1) input.discardEncodedSize();\n");
      default -> {}
    }
    if (f.enumType() != null && !f.enumType().open()) {
      out.append(" boolean accepted=switch((int)value) {case ")
          .append(
              String.join(
                  ",",
                  f.enumType().values().values().stream()
                      .distinct()
                      .map(Object::toString)
                      .toList()))
          .append(" -> true; default -> false;};\n");
      if (type.mapEntry() && i == 1) out.append(" invalidMapEnum=!accepted;\n");
      out.append(" if(!accepted) { unknown=input.")
          .append(
              packed ? "enumeration(unknown," + f.number() + ",value)" : "retain(unknown,start)")
          .append("; } else {\n");
      accept(out, type, i);
      out.append(" }\n");
    } else accept(out, type, i);
  }

  private static String scalarType(ProtoPlan.FieldPlan f) {
    return switch (f.kind()) {
      case FLOAT, DOUBLE -> "double";
      case BOOL -> "boolean";
      case STRING -> "String";
      case BYTES -> "DataArray";
      case UINT64, FIXED64 -> "Object";
      default -> "long";
    };
  }

  void append(StringBuilder out, ProtoPlan.Type type) throws Exception {
    if (!supported.contains(type.name())) return;
    if (supports(type)) record(out, type);
    var fields = type.fields();
    String state = state(type.name());
    out.append(" private static final class ")
        .append(state)
        .append(
            " { private final ProtoPlan plan; private long presence; private boolean"
                + " invalidMapEnum;\n")
        .append(" private long cellCount,byteCount;\n")
        .append(
            " private java.io.@org.jspecify.annotations.Nullable ByteArrayOutputStream unknown;\n");
    for (int i = 0; i < fields.size(); i++)
      out.append(" private ").append(mutable(fields.get(i))).append(" f").append(i).append(";\n");
    out.append(" private ")
        .append(state)
        .append(
            "(ProtoPlan plan, dev.elide.truffledata.protobuf.GeneratedGraphInput input, int depth)"
                + " { this.plan=plan; input.message(")
        .append(fields.size())
        .append(",depth);\n");
    for (int i = 0; i < fields.size(); i++)
      if (!fields.get(i).repeated() && !fields.get(i).kind().message())
        out.append(" f").append(i).append('=').append(fallback(fields.get(i), i)).append(";\n");
    out.append(
            " }\n"
                + " private void consume(dev.elide.truffledata.protobuf.GeneratedGraphInput"
                + " input,int group,int depth) {\n")
        .append(" int bodyStart=input.position();\n")
        .append(
            " while(input.remaining()) { int start=input.position(); int tag=input.tag();"
                + " if((tag&7)==4) { if(group==0 || group!=(tag>>>3)) throw"
                + " dev.elide.truffledata.protobuf.GeneratedGraphInput.invalid(\"Unexpected end"
                + " group\"); return; } switch(tag) {\n");
    for (int i = 0; i < fields.size(); i++) {
      var f = fields.get(i);
      out.append(" case ").append((f.number() << 3) | f.kind().wire).append(" -> {\n");
      if (!f.repeated()) {
        long mask = 1L << i;
        if (f.oneof() >= 0)
          for (int n = 0; n < fields.size(); n++)
            if (fields.get(n).oneof() == f.oneof()) mask |= 1L << n;
        out.append(" if((presence&").append(mask).append("L)!=0) input.discardEncodedSize();\n");
      } else if (f.packed()) out.append(" input.discardEncodedSize();\n");
      if (f.map() || f.kind() == ProtoPlan.Kind.GROUP) out.append(" input.discardEncodedSize();\n");
      if (f.kind().message()) {
        if (!f.repeated()) select(out, type, i);
        out.append(" var child=");
        if (!f.repeated()) out.append("f").append(i).append("!=null?f").append(i).append(':');
        out.append("new ")
            .append(state(f.typeName()))
            .append("(plan.nested(")
            .append(i)
            .append("),input,depth+1);\n");
        if (f.kind() == ProtoPlan.Kind.MESSAGE)
          out.append(
              " int parent=input.limit(); int end=input.end(); input.limit(end);"
                  + " child.consume(input,0,depth+1); input.limit(parent);\n");
        else out.append(" child.consume(input,").append(f.number()).append(",depth+1);\n");
        if (f.map())
          out.append(" if(child.invalidMapEnum) {unknown=input.retain(unknown,start);} else {\n");
        out.append(" var value=child;\n");
        accept(out, type, i);
        if (f.map()) out.append(" }\n");
      } else scalar(out, type, i, false);
      out.append(" }\n");
      if (f.repeated() && f.kind().packable()) {
        out.append(" case ")
            .append((f.number() << 3) | 2)
            .append(
                " -> { int parent=input.limit(); int end=input.end(); input.limit(end);"
                    + " if(!input.remaining()) input.discardEncodedSize();\n");
        if (f.packed())
          out.append(" if((presence&")
              .append(1L << i)
              .append("L)!=0) input.discardEncodedSize();\n");
        else out.append(" input.discardEncodedSize();\n");
        out.append(" while(input.remaining()) {\n");
        scalar(out, type, i, true);
        out.append(" } input.limit(parent); }\n");
      }
    }
    out.append(
        " default -> unknown=input.unknown(unknown,start,tag,depth); } } if(group!=0) throw"
            + " dev.elide.truffledata.protobuf.GeneratedGraphInput.invalid(\"Unterminated"
            + " group\");\n");
    if (fields.size() <= 32)
      out.append(
          " presence=(presence&0xffffffffL)" + " | ((input.position()-bodyStart+1L)<<32);\n");
    out.append(" }\n");
    out.append(" private ")
        .append(message(type.name()))
        .append(" finish(dev.elide.truffledata.protobuf.GeneratedGraphInput input,int depth) {\n")
        .append(" cellCount=")
        .append(fields.size())
        .append("; byteCount=input.retainedBytes(0,unknown==null?0:unknown.size());\n");
    if (type.mapEntry() && fields.get(1).kind().message())
      out.append(" if(f1==null) f1=new ")
          .append(state(fields.get(1).typeName()))
          .append("(plan.nested(1),input,depth+1); presence|=2L;\n");
    for (int i = 0; i < fields.size(); i++) {
      var f = fields.get(i);
      if (f.required())
        out.append(" if((presence & ")
            .append(1L << i)
            .append(
                "L)==0) throw dev.elide.truffledata.protobuf.GeneratedGraphInput.invalid(\"Missing"
                    + " required field\");\n");
      if (f.repeated()) {
        out.append(" DataArray value")
            .append(i)
            .append("; if(f")
            .append(i)
            .append("==null) value")
            .append(i)
            .append("=EMPTY_REPEATED; else {\n");
        if (!f.map()) {
          out.append(" cellCount=input.retainedCells(cellCount,f").append(i).append(".size());\n");
          if (!f.kind().message()) {
            if (f.kind() == ProtoPlan.Kind.STRING || f.kind() == ProtoPlan.Kind.BYTES) {
              out.append(" for(Object element:f").append(i).append(") {\n");
              footprint(out, f, "element", true);
              out.append(" }\n");
            }
          }
        }
        if (f.kind().message())
          out.append(" for(int index=0;index<f")
              .append(i)
              .append(".size();index++) { var child=((")
              .append(state(f.typeName()))
              .append(")f")
              .append(i)
              .append(".get(index)); f")
              .append(i)
              .append(".set(index,child.finish(input,depth+1));\n")
              .append(f.map() ? "" : childFootprint("child"))
              .append(" }\n");
        out.append(" value")
            .append(i)
            .append(
                f.map()
                    ? "=dev.elide.truffledata.protobuf.GeneratedGraphInput.map(f"
                    : "=DataArray.copyOf(f")
            .append(i)
            .append("); }\n");
        if (f.map())
          out.append(" var footprint")
              .append(i)
              .append("=input.mapFootprint(value")
              .append(i)
              .append(",depth); cellCount=input.retainedCells(cellCount,footprint")
              .append(i)
              .append(".cells()); byteCount=input.retainedBytes(byteCount,footprint")
              .append(i)
              .append(".bytes());\n");
        out.append(" if(value")
            .append(i)
            .append(".size()==0) { if((presence&")
            .append(1L << i)
            .append("L)!=0) input.discardEncodedSize(); presence &= ~")
            .append(1L << i)
            .append("L; }\n");
      } else if (f.kind().message()) {
        out.append(" Object value")
            .append(i)
            .append("=f")
            .append(i)
            .append("==null?Missing.INSTANCE:f")
            .append(i)
            .append(".finish(input,depth+1);\n");
        out.append(" if(f")
            .append(i)
            .append("!=null) {\n")
            .append(childFootprint("f" + i))
            .append(" }\n");
      } else {
        footprint(out, f, "f" + i, false);
        if (!f.explicitPresence()) {
          String zero =
              switch (type(f)) {
                case "long" -> "f" + i + "==0";
                case "double" -> "Double.doubleToRawLongBits(f" + i + ")==0";
                case "boolean" -> "!f" + i;
                case "String" -> "f" + i + ".isEmpty()";
                case "DataArray" -> "f" + i + ".size()==0";
                default -> "((ExactNumber)f" + i + ").decimal().signum()==0";
              };
          out.append(" if(")
              .append(zero)
              .append(") { if((presence&")
              .append(1L << i)
              .append("L)!=0) input.discardEncodedSize(); presence &= ~")
              .append(1L << i)
              .append("L; }\n");
        }
      }
    }
    if (fields.size() <= 32)
      out.append(" if(!input.encodedSizeReusable()) presence&=0xffffffffL;\n");
    out.append(" return new ")
        .append(message(type.name()))
        .append(
            "(plan,presence,dev.elide.truffledata.protobuf.GeneratedGraphInput.unknownBytes(unknown)");
    for (int i = 0; i < fields.size(); i++)
      out.append(',')
          .append(
              fields.get(i).repeated() || fields.get(i).kind().message()
                  ? "value" + i
                  : storedValue(fields.get(i), i));
    out.append("); } }\n");
  }

  private static String childFootprint(String child) {
    return " cellCount=input.retainedCells(cellCount,"
        + child
        + ".cellCount);"
        + " byteCount=input.retainedBytes(byteCount,"
        + child
        + ".byteCount);\n";
  }

  private static void footprint(
      StringBuilder out, ProtoPlan.FieldPlan field, String value, boolean cast) {
    if (field.kind() == ProtoPlan.Kind.STRING)
      out.append(" byteCount=input.retainedBytes(byteCount,2L*")
          .append(cast ? "((String)" + value + ")" : value)
          .append(".length());\n");
    else if (field.kind() == ProtoPlan.Kind.BYTES)
      out.append(" cellCount=input.retainedCells(cellCount,")
          .append(cast ? "((DataArray)" + value + ")" : value)
          .append(".size());\n");
  }

  private static void record(StringBuilder out, ProtoPlan.Type schema) {
    var fields = schema.fields();
    String name = message(schema.name());
    out.append(" public static final class ")
        .append(name)
        .append(" extends dev.elide.truffledata.protobuf.GeneratedMessage {\n");
    for (int i = 0; i < fields.size(); i++)
      out.append(" private final ")
          .append(storage(fields.get(i)))
          .append(" f")
          .append(i)
          .append(";\n");
    out.append(" private ").append(name).append("(ProtoPlan plan,long presence,byte[] unknown");
    for (int i = 0; i < fields.size(); i++)
      out.append(',').append(type(fields.get(i))).append(" f").append(i);
    out.append(") { super(plan,presence,unknown);");
    for (int i = 0; i < fields.size(); i++)
      out.append(" this.f")
          .append(i)
          .append("=(")
          .append(storage(fields.get(i)))
          .append(")f")
          .append(i)
          .append(';');
    out.append(" }\n");
    for (int i = 0; i < fields.size(); i++)
      out.append(" public ")
          .append(type(fields.get(i)))
          .append(" get_")
          .append(fields.get(i).name())
          .append("() { return ")
          .append(storedValue(fields.get(i), i))
          .append("; }\n");
    for (String result : List.of("Object", "long", "double", "boolean")) {
      String method =
          switch (result) {
            case "long" -> "getLong";
            case "double" -> "getDouble";
            case "boolean" -> "getBoolean";
            default -> "get";
          };
      out.append(" @Override public ")
          .append(result)
          .append(' ')
          .append(method)
          .append("(int ordinal) { return switch(ordinal) {\n");
      for (int i = 0; i < fields.size(); i++)
        if (result.equals("Object") || type(fields.get(i)).equals(result))
          out.append(" case ")
              .append(i)
              .append(" -> ")
              .append(storedValue(fields.get(i), i))
              .append(";\n");
      out.append(" default -> ")
          .append(
              result.equals("Object")
                  ? "throw new ContractViolation(\"Invalid generated field\")"
                  : "super." + method + "(ordinal)")
          .append("; }; }\n");
    }
    EncoderGenerator.append(out, schema, true);
    out.append(" private static ")
        .append(name)
        .append(
            " decode(ProtoPlan plan,byte[] wire) { var input=new"
                + " dev.elide.truffledata.protobuf.GeneratedGraphInput(plan,wire); var state=new ")
        .append(state(schema.name()))
        .append("(plan,input,1); state.consume(input,0,1); return state.finish(input,1); }\n }\n");
  }

  static void header(StringBuilder out, List<ProtoPlan.Type> definitions) {
    out.append(" private static final DataArray EMPTY_REPEATED=new DataArray(new Object[0]);\n");
    out.append(
        " @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary private static void"
            + " checkGraph(ProtoPlan root) { var seen=new HashSet<String>(); var pending=new"
            + " ArrayDeque<ProtoPlan>(); pending.add(root); while(!pending.isEmpty()) { var"
            + " plan=pending.removeFirst(); if(!seen.add(plan.name())) continue; ProtoPlan.Type"
            + " expected=switch(plan.name()) {\n");
    for (int i = 0; i < definitions.size(); i++)
      out.append(" case ")
          .append(ContractPlugin.quote(definitions.get(i).name()))
          .append(" -> type")
          .append(i)
          .append("();\n");
    out.append(
        " default -> throw new ContractViolation(\"Unknown generated nested type\"); };"
            + " if(!plan.matchesDefinition(expected)) throw new ContractViolation(\"Generated"
            + " nested contract mismatch\"); for(int i=0;i<plan.size();i++)"
            + " if(plan.field(i).kind().message()) pending.add(plan.nested(i)); } }\n");
  }

  private static String storage(ProtoPlan.FieldPlan field) {
    return field.repeated() || field.kind().message() ? type(field) : OwnedGenerator.storage(field);
  }

  private static String storedValue(ProtoPlan.FieldPlan field, int ordinal) {
    return field.repeated() || field.kind().message()
        ? "f" + ordinal
        : OwnedGenerator.storedValue(field, ordinal);
  }
}
