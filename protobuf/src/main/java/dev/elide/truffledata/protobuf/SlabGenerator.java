package dev.elide.truffledata.protobuf;

import java.util.List;

/** Emits schema-checked slab decoders with constant field-number dispatch for each record type. */
final class SlabGenerator {
  private SlabGenerator() {}

  static void append(StringBuilder out, List<ProtoPlan.Type> types) {
    var model =
        new ProtoPlan.Model(null, types, dev.elide.truffledata.Limits.DEFAULT, 128, 1 << 20);
    out.append(
            " private static dev.elide.truffledata.protobuf.SlabCodec.FieldLookup"
                + " slabLookup(ProtoPlan plan) {\n")
        .append(" switch(plan.name()) {\n");
    for (int t = 0; t < types.size(); t++) {
      var type = types.get(t);
      out.append(" case ")
          .append(ContractPlugin.quote(type.name()))
          .append(":\n")
          .append(" if(!plan.matchesDefinition(type")
          .append(t)
          .append("())) throw new ContractViolation(\"Generated slab contract mismatch\");\n")
          .append(
              " return new dev.elide.truffledata.protobuf.SlabCodec.FieldLookup() { public int"
                  + " ordinal(int ignored,int number) { return switch(number) {\n");
      for (int f = 0; f < type.fields().size(); f++)
        out.append(" case ")
            .append(type.fields().get(f).number())
            .append(" -> ")
            .append(f)
            .append(";\n");
      out.append(
          " default -> -1; }; } public int slot(int ignored,int ordinal) { return switch(ordinal)"
              + " {\n");
      var layout = model.type(type.name()).layout();
      for (int f = 0; f < type.fields().size(); f++)
        out.append(" case ").append(f).append(" -> ").append(layout.slot(0, f)).append(";\n");
      out.append(" default -> -1; }; } };\n");
    }
    out.append(" default: throw new ContractViolation(\"Unknown generated slab type\");\n }\n }\n");
    out.append(" private static ProtoPlan.SlabDecoder generatedSlabDecoder(ProtoPlan plan) {\n")
        .append(" return switch(plan.name()) {\n");
    for (var type : types)
      out.append(" case ")
          .append(ContractPlugin.quote(type.name()))
          .append(" -> new SlabDecoder_")
          .append(type.name().replace('.', '$'))
          .append("(plan);\n");
    out.append(
        " default -> throw new ContractViolation(\"Unknown generated slab type\");\n };\n }\n");
    appendFastMethods(out, types, model, false);
    appendFastMethods(out, types, model, true);
    appendScanMethods(out, types);
    out.append(
            " private static int[] slabTypeIds(dev.elide.truffledata.protobuf.Layout layout) {"
                + " int[] ids=new int[")
        .append(types.size())
        .append(
            "]; java.util.Arrays.fill(ids,-1); for(int i=0;i<layout.types();i++)"
                + " switch(layout.plan(i).name()) {\n");
    for (int t = 0; t < types.size(); t++)
      out.append("case ")
          .append(ContractPlugin.quote(types.get(t).name()))
          .append(" -> ids[")
          .append(t)
          .append("]=i;\n");
    out.append(
        "default -> throw new ContractViolation(\"Generated slab type mismatch\"); } return ids;"
            + " }\n");
    // A bounded length/extent cache avoids repeated growth and trim copies for heterogeneous
    // messages. Atomic opaque entries keep each key/value pair coherent across concurrent callers;
    // hints retain no input or output and never replace the writer's bounds checks.
    for (int t = 0; t < types.size(); t++) {
      var type = types.get(t);
      String name = "SlabDecoder_" + type.name().replace('.', '$');
      out.append(" public static final class ")
          .append(name)
          .append(
              " implements ProtoPlan.SlabDecoder {\n"
                  + " private final dev.elide.truffledata.protobuf.Layout layout;\n")
          .append(" private final int[] projectionTypeIds;\n")
          .append(" private final dev.elide.truffledata.protobuf.SlabCodec.FieldLookup lookup;\n")
          .append(
              " private final java.util.concurrent.atomic.AtomicLongArray slabHints="
                  + "new java.util.concurrent.atomic.AtomicLongArray(64);\n")
          .append(
              " private final java.util.concurrent.atomic.AtomicLongArray projectedHints=new"
                  + " java.util.concurrent.atomic.AtomicLongArray(64);\n")
          .append(" public ")
          .append(name)
          .append("(ProtoPlan plan) {\n")
          .append(" if(!plan.matchesDefinition(type")
          .append(t)
          .append("())) throw new ContractViolation(\"Generated slab root mismatch\");\n")
          .append(
              " layout=plan.layout(); projectionTypeIds=slabTypeIds(layout);\n"
                  + " var entries=new"
                  + " dev.elide.truffledata.protobuf.SlabCodec.FieldLookup[layout.types()];\n")
          .append(" for(int i=0;i<entries.length;i++) entries[i]=slabLookup(layout.plan(i));\n")
          .append(
              " lookup=new dev.elide.truffledata.protobuf.SlabCodec.FieldLookup() { public int"
                  + " ordinal(int type,int number) { return entries[type].ordinal(type,number); }"
                  + " public int slot(int type,int ordinal) { return"
                  + " entries[type].slot(type,ordinal); } };\n"
                  + " }\n")
          .append(
              " public byte[] decode(byte[] input) { return"
                  + " decode(input,0,input.length,dev.elide.truffledata.protobuf.SlabLimits.forInput(layout,input.length));"
                  + " }\n")
          .append(
              " public byte[] decode(byte[] input,int offset,int"
                  + " length,dev.elide.truffledata.protobuf.SlabLimits limits) {\n")
          .append(
              " int bucket=(length^(length>>>6))&63; long hint=slabHints.getOpaque(bucket); var"
                  + " writer=new ")
          .append(WRITER)
          .append(
              "(layout,input,offset,length,limits,(int)(hint>>>32)==length?(int)hint:0);\n"
                  + " byte[] result;\n")
          .append(" try { slabRead")
          .append(t)
          .append("(writer,offset+length,0); result=writer.finish(); }\n")
          .append(" catch(")
          .append(WRITER)
          .append(".Fallback restart) { result=")
          .append(
              "dev.elide.truffledata.protobuf.SlabCodec.decode(layout,input,offset,length,limits,lookup);"
                  + " }\n")
          .append(" slabHints.setOpaque(bucket,((long)length<<32)|result.length); return result;\n")
          .append(" }\n")
          .append(
              " public byte[] decodeProjected(dev.elide.truffledata.protobuf.Layout expected,byte[]"
                  + " input,int offset,int length,dev.elide.truffledata.protobuf.SlabLimits"
                  + " limits,dev.elide.truffledata.protobuf.Projection projection) {\n")
          .append(
              " if(expected!=layout) throw new ContractViolation(\"Generated projection layout"
                  + " mismatch\"); projection.requireLayout(layout);\n")
          .append(
              " if(projection.borrowBytesThreshold()!=0) return"
                  + " dev.elide.truffledata.protobuf.SlabCodec.decode(layout,input,offset,length,limits,projection);\n")
          .append(
              " int bucket=(length^(length>>>6))&63; long hint=projectedHints.getOpaque(bucket);"
                  + " var writer=new ")
          .append(WRITER)
          .append(
              "(layout,input,offset,length,limits,(int)(hint>>>32)==length?(int)hint:"
                  + "(projection.lazyChildrenEnabled()?0:256)); byte[]"
                  + " result;\n")
          .append(" try { slabProjectedRead")
          .append(t)
          .append(
              "(writer,offset+length,0,projection,projectionTypeIds);"
                  + " result=writer.finishProjected(projection.laxValidation()); }\n")
          .append(" catch(")
          .append(WRITER)
          .append(
              ".Fallback restart) {"
                  + " result=dev.elide.truffledata.protobuf.SlabCodec.decode(layout,input,offset,length,limits,projection);"
                  + " }\n")
          .append(
              " projectedHints.setOpaque(bucket,((long)length<<32)|result.length); return"
                  + " result;\n")
          .append(" }\n }\n");
    }
    appendRowFactory(out, types);
    for (var type : types) {
      var layout = model.type(type.name()).layout();
      appendRowClass(out, type, layout, false);
      appendRowClass(out, type, layout, true);
    }
  }

  private static final String WRITER = "dev.elide.truffledata.protobuf.GeneratedSlabWriter";
  private static final String RUNTIME = "dev.elide.truffledata.protobuf.";
  private static final String URE = "com.oracle.truffle.api.nodes.UnexpectedResultException";

  static String rowClass(ProtoPlan.Type type, boolean root) {
    return (root ? "SlabMessage_" : "SlabRow_") + type.name().replace('.', '$');
  }

  /**
   * Binds the generated row classes to a layout whose every type matches a generated definition by
   * name and structure; any other layout keeps the generic rows (plan 2026-11-08-beat-stock-svm
   * WS1). Child type indices are the layout's, read from the row's owner at run time, because one
   * generated child class serves every root layout containing its type: record offsets depend on
   * the type alone.
   */
  private static void appendRowFactory(StringBuilder out, List<ProtoPlan.Type> types) {
    out.append(
            " private static ProtoPlan.@org.jspecify.annotations.Nullable SlabRows"
                + " generatedSlabRows(")
        .append(RUNTIME)
        .append("Layout layout) {\n int[] ids=new int[layout.types()];\n")
        .append(" for(int i=0;i<ids.length;i++) { var plan=layout.plan(i); switch(plan.name()) {\n");
    for (int t = 0; t < types.size(); t++)
      out.append(" case ")
          .append(ContractPlugin.quote(types.get(t).name()))
          .append(" -> { if(!plan.matchesDefinition(type")
          .append(t)
          .append("())) return null; ids[i]=")
          .append(t)
          .append("; }\n");
    out.append(" default -> { return null; }\n } }\n")
        .append(" return new ProtoPlan.SlabRows() {\n")
        .append(" @Override public ")
        .append(RUNTIME)
        .append("SlabMessage root(")
        .append(RUNTIME)
        .append("Layout l,byte[] slab,int extent,int verification) { return switch(ids[0]) {\n");
    for (int t = 0; t < types.size(); t++)
      out.append(" case ")
          .append(t)
          .append(" -> new ")
          .append(rowClass(types.get(t), true))
          .append("(l,slab,extent,verification);\n");
    out.append(" default -> throw new ContractViolation(\"Generated slab root mismatch\"); }; }\n")
        .append(" @Override public ")
        .append(RUNTIME)
        .append("SlabRow child(")
        .append(RUNTIME)
        .append("SlabMessage owner,int type,int record) { return switch(ids[type]) {\n");
    for (int t = 0; t < types.size(); t++)
      out.append(" case ")
          .append(t)
          .append(" -> new ")
          .append(rowClass(types.get(t), false))
          .append("(owner,type,record);\n");
    out.append(" default -> throw new ContractViolation(\"Generated slab type mismatch\"); }; }\n")
        .append(" };\n }\n");
  }

  private static boolean inlineDefault(ProtoPlan.FieldPlan field) {
    return field.repeated()
        || switch (field.kind()) {
          case STRING, BYTES, UINT64, FIXED64 -> false;
          default -> true;
        };
  }

  /** Java literal of a scalar default (long/double/boolean kinds; messages are Missing). */
  private static String scalarDefault(ProtoPlan.FieldPlan field) {
    try {
      return ContractPlugin.literal(field.defaultValue());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** The value read of a present field through the protected slot readers of {@code r}. */
  private static String presentValue(ProtoPlan.FieldPlan field, int ordinal, int slot, String r) {
    String at = r + "int32At(" + slot + ")";
    String wide = r + "int64At(" + slot + ")";
    if (field.repeated()) return r + "listAt(" + slot + "," + ordinal + ")";
    return switch (field.kind()) {
      case MESSAGE, GROUP -> r + "rowAt(" + slot + "," + ordinal + ")";
      case STRING -> r + "textAt(" + slot + ")";
      case BYTES -> r + "blobAt(" + slot + ")";
      case UINT64, FIXED64 -> r + "unsignedAt(" + slot + ")";
      case UINT32, FIXED32 -> "Integer.toUnsignedLong(" + at + ")";
      case INT64, SINT64, SFIXED64 -> wide;
      case FLOAT -> "(double) Float.intBitsToFloat(" + at + ")";
      case DOUBLE -> "Double.longBitsToDouble(" + wide + ")";
      case BOOL -> at + " != 0";
      default -> "(long) " + at;
    };
  }

  /** The default of an absent field as an expression inside the row class. */
  private static String absentValue(ProtoPlan.FieldPlan field, int ordinal) {
    if (field.repeated()) return "emptyList()";
    if (field.kind().message()) return "Missing.INSTANCE";
    return inlineDefault(field) ? scalarDefault(field) : "D" + ordinal;
  }

  private static boolean longKind(ProtoPlan.FieldPlan f) {
    return !f.repeated()
        && switch (f.kind()) {
          case INT32, SINT32, SFIXED32, ENUM, UINT32, FIXED32, INT64, SINT64, SFIXED64 -> true;
          default -> false;
        };
  }

  private static boolean doubleKind(ProtoPlan.FieldPlan f) {
    return !f.repeated() && (f.kind() == ProtoPlan.Kind.FLOAT || f.kind() == ProtoPlan.Kind.DOUBLE);
  }

  /**
   * One final row class per (type, root-or-child): constant-ordinal dispatch for the host reads
   * and a nested exact-class accessor per field for the read nodes. Semantics mirror SlabRow and
   * SlabRow.Slot for an unprojected owner (no projection check, which the owner guarantees).
   */
  private static void appendRowClass(
      StringBuilder out, ProtoPlan.Type type, Layout layout, boolean root) {
    String name = rowClass(type, root);
    var fields = type.fields();
    int count = fields.size();
    out.append(" public static final class ")
        .append(name)
        .append(" extends ")
        .append(RUNTIME)
        .append(root ? "SlabMessage" : "SlabRow")
        .append(" {\n");
    if (root)
      out.append(" public ")
          .append(name)
          .append("(")
          .append(RUNTIME)
          .append("Layout layout,byte[] slab,int extent,int verification) {"
                  + " super(layout,slab,extent,verification); }\n");
    else
      out.append(" public ")
          .append(name)
          .append("(")
          .append(RUNTIME)
          .append("SlabMessage owner,int type,int record) { super(owner,type,record,false); }\n");
    for (int f = 0; f < count; f++)
      if (!inlineDefault(fields.get(f)) && !fields.get(f).kind().message())
        out.append(" private static final Object D")
            .append(f)
            .append("=")
            .append(scalarDefault(fields.get(f)))
            .append(";\n");
    out.append(" @Override public boolean has(int ordinal) { java.util.Objects.checkIndex(ordinal,")
        .append(count)
        .append("); return present(ordinal); }\n");
    // Host reads: constant-ordinal dispatch; a kind with no field of that type is one throw.
    var get = new StringBuilder();
    var getLong = new StringBuilder();
    var getDouble = new StringBuilder();
    var getBoolean = new StringBuilder();
    var getSize = new StringBuilder();
    var stringLength = new StringBuilder();
    for (int f = 0; f < count; f++) {
      var field = fields.get(f);
      int slot = layout.slot(0, f);
      String present = " case " + f + " -> present(" + f + ") ? ";
      get.append(present)
          .append(presentValue(field, f, slot, ""))
          .append(" : ")
          .append(absentValue(field, f))
          .append(";\n");
      if (longKind(field))
        getLong
            .append(present)
            .append(presentValue(field, f, slot, ""))
            .append(" : ")
            .append(scalarDefault(field))
            .append(";\n");
      if (doubleKind(field))
        getDouble
            .append(present)
            .append(presentValue(field, f, slot, ""))
            .append(" : ")
            .append(scalarDefault(field))
            .append(";\n");
      if (!field.repeated() && field.kind() == ProtoPlan.Kind.BOOL)
        getBoolean
            .append(present)
            .append("int32At(")
            .append(slot)
            .append(") != 0 : ")
            .append(scalarDefault(field))
            .append(";\n");
      if (field.repeated())
        getSize.append(present).append("listCountAt(").append(slot).append(") : 0;\n");
      else if (field.kind() == ProtoPlan.Kind.BYTES)
        getSize
            .append(present)
            .append("bytesLengthAt(")
            .append(slot)
            .append(") : ((DataArray) D")
            .append(f)
            .append(").size();\n");
      if (!field.repeated() && field.kind() == ProtoPlan.Kind.STRING)
        stringLength
            .append(present)
            .append("utf16LengthAt(")
            .append(slot)
            .append(") : ((String) D")
            .append(f)
            .append(").length();\n");
    }
    dispatch(out, "public Object get(int ordinal)", get,
        "new IndexOutOfBoundsException(\"Field ordinal \"+ordinal)");
    dispatch(out, "public long getLong(int ordinal)", getLong,
        "new ContractViolation(\"Expected INT64\")");
    dispatch(out, "public double getDouble(int ordinal)", getDouble,
        "new ContractViolation(\"Expected FLOAT64\")");
    dispatch(out, "public boolean getBoolean(int ordinal)", getBoolean,
        "new ContractViolation(\"Expected BOOLEAN\")");
    dispatch(out, "public int getSize(int ordinal)", getSize,
        "new ContractViolation(\"Expected array\")");
    dispatch(out, "public int stringLength(int ordinal)", stringLength,
        "new ContractViolation(\"Expected string\")");
    // accessor
    if (count == 0)
      out.append(
          " @Override public Accessor accessor(int ordinal) { throw new"
              + " IndexOutOfBoundsException(\"Field ordinal \"+ordinal); }\n");
    else {
      out.append(" @Override public Accessor accessor(int ordinal) { return schema().accessor(")
          .append(name)
          .append(".class,ordinal,i -> switch(i) {\n");
      for (int f = 0; f < count; f++)
        out.append(" case ")
            .append(f)
            .append(" -> (Accessor) new A")
            .append(f)
            .append("(plan());\n");
      out.append(" default -> throw new IndexOutOfBoundsException(\"Field ordinal \"+i); }); }\n");
    }
    for (int f = 0; f < count; f++) appendAccessor(out, name, fields.get(f), f, layout.slot(0, f));
    out.append(" }\n");
  }

  /** An ordinal switch over {@code cases}, or the fallback throw alone when there are none. */
  private static void dispatch(
      StringBuilder out, String signature, StringBuilder cases, String fallback) {
    out.append(" @Override ").append(signature);
    if (cases.isEmpty()) out.append(" { throw ").append(fallback).append("; }\n");
    else
      out.append(" { return switch(ordinal) {\n")
          .append(cases)
          .append(" default -> throw ")
          .append(fallback)
          .append("; }; }\n");
  }

  /** Exact-class accessor of one field: layout is (row class, plan schema). */
  private static void appendAccessor(
      StringBuilder out, String row, ProtoPlan.FieldPlan field, int f, int slot) {
    String r = "r.";
    out.append(" private static final class A")
        .append(f)
        .append(" extends Accessor {\n")
        .append(" private final boolean plainText;\n")
        .append(" A")
        .append(f)
        .append("(ProtoPlan plan) { super(plan.schema(),")
        .append(f)
        .append(",")
        .append(row)
        .append(".class); plainText=field().constraints().length()==null; }\n")
        .append(" @Override public boolean accepts(Object row) { return row.getClass()==")
        .append(row)
        .append(".class && ((")
        .append(row)
        .append(") row).schema()==schema(); }\n")
        .append(" private ")
        .append(row)
        .append(" row(Object value) { if(!accepts(value)) throw mismatch(); return (")
        .append(row)
        .append(") value; }\n")
        .append(" private ")
        .append(row)
        .append(" trusted(Object value) { assertAccepted(value); return"
                + " com.oracle.truffle.api.CompilerDirectives.castExact(value,")
        .append(row)
        .append(".class); }\n")
        .append(" @Override public Object read(Object value) { return row(value).get(")
        .append(f)
        .append("); }\n")
        .append(" @Override public long readLong(Object value) throws ")
        .append(URE)
        .append(" { return longOf(row(value)); }\n")
        .append(" @Override protected long readLongBound(Object value) throws ")
        .append(URE)
        .append(" { return longOf(trusted(value)); }\n")
        .append(" private static long longOf(")
        .append(row)
        .append(" r) throws ")
        .append(URE)
        .append(" { ");
    if (longKind(field))
      out.append("return r.present(")
          .append(f)
          .append(") ? ")
          .append(presentValue(field, f, slot, r))
          .append(" : ")
          .append(scalarDefault(field))
          .append(";");
    else out.append("throw new ").append(URE).append("(r.get(").append(f).append("));");
    out.append(" }\n")
        .append(" @Override public double readDouble(Object value) throws ")
        .append(URE)
        .append(" { return doubleOf(row(value)); }\n")
        .append(" @Override protected double readDoubleBound(Object value) throws ")
        .append(URE)
        .append(" { return doubleOf(trusted(value)); }\n")
        .append(" private static double doubleOf(")
        .append(row)
        .append(" r) throws ")
        .append(URE)
        .append(" { ");
    if (doubleKind(field))
      out.append("return r.present(")
          .append(f)
          .append(") ? ")
          .append(presentValue(field, f, slot, r))
          .append(" : ")
          .append(scalarDefault(field))
          .append(";");
    else out.append("throw new ").append(URE).append("(r.get(").append(f).append("));");
    out.append(" }\n")
        .append(" @Override public boolean readBoolean(Object value) throws ")
        .append(URE)
        .append(" { return booleanOf(row(value)); }\n")
        .append(" @Override protected boolean readBooleanBound(Object value) throws ")
        .append(URE)
        .append(" { return booleanOf(trusted(value)); }\n")
        .append(" private static boolean booleanOf(")
        .append(row)
        .append(" r) throws ")
        .append(URE)
        .append(" { ");
    if (!field.repeated() && field.kind() == ProtoPlan.Kind.BOOL)
      out.append("return r.present(")
          .append(f)
          .append(") ? r.int32At(")
          .append(slot)
          .append(") != 0 : ")
          .append(scalarDefault(field))
          .append(";");
    else out.append("throw new ").append(URE).append("(r.get(").append(f).append("));");
    out.append(" }\n")
        .append(" @Override public int length(Object value) { return lengthIn(row(value)); }\n")
        .append(
            " @Override protected int lengthBound(Object value) { return lengthIn(trusted(value));"
                + " }\n")
        .append(" private int lengthIn(")
        .append(row)
        .append(" r) { ");
    if (field.repeated())
      out.append("return arrayLength(r.present(")
          .append(f)
          .append(") ? r.listCountAt(")
          .append(slot)
          .append(") : 0);");
    else if (field.kind() == ProtoPlan.Kind.BYTES)
      out.append("return arrayLength(r.present(")
          .append(f)
          .append(") ? r.bytesLengthAt(")
          .append(slot)
          .append(") : ((DataArray) ")
          .append(row)
          .append(".D")
          .append(f)
          .append(").size());");
    else if (field.kind() == ProtoPlan.Kind.STRING)
      out.append("if(plainText) return r.present(")
          .append(f)
          .append(") ? r.utf16LengthAt(")
          .append(slot)
          .append(") : ((String) ")
          .append(row)
          .append(".D")
          .append(f)
          .append(").length(); return lengthOf(r.get(")
          .append(f)
          .append("));");
    else out.append("return lengthOf(r.get(").append(f).append("));");
    out.append(" }\n }\n");
  }

  private static void appendFastMethods(
      StringBuilder out, List<ProtoPlan.Type> types, ProtoPlan.Model model, boolean projected) {
    var indices = new java.util.HashMap<String, Integer>();
    for (int i = 0; i < types.size(); i++) indices.put(types.get(i).name(), i);
    for (int t = 0; t < types.size(); t++) {
      var type = types.get(t);
      var layout = model.type(type.name()).layout();
      out.append(
              projected ? " private static int slabProjectedRead" : " private static int slabRead")
          .append(t)
          .append("(")
          .append(WRITER)
          .append(" w,int end,int depth")
          .append(
              projected
                  ? ",dev.elide.truffledata.protobuf.Projection projection,int[] typeIds"
                  : "")
          .append(") {\n");
      // Large schemas avoid a JVM method-size cliff; map roots need map-entry close semantics.
      if (type.mapEntry()
          || type.fields().size() > 128
          || (projected
              && type.fields().stream()
                  .anyMatch(
                      f ->
                          f.required()
                              || f.map()
                              || f.oneof() >= 0
                              || f.kind() == ProtoPlan.Kind.GROUP))) {
        out.append(" throw ").append(WRITER).append(".fallback();\n }\n");
        continue;
      }
      out.append(" int record=w.record(").append(layout.recordBytes(0)).append(",depth);\n");
      for (int word = 0; word < layout.presenceWords(0); word++)
        out.append(" int presence").append(word).append("=0;\n");
      for (int f = 0; f < type.fields().size(); f++) {
        var field = type.fields().get(f);
        if (field.repeated() && !field.map())
          out.append(" long[] p")
              .append(f)
              .append("=")
              .append(WRITER)
              .append(".EMPTY; int n")
              .append(f)
              .append("=0;\n");
      }
      out.append(" while(w.position<end) { switch(w.tag(end)) {\n");
      for (int f = 0; f < type.fields().size(); f++) {
        var field = type.fields().get(f);
        int tag = (field.number() << 3) | field.kind().wire;
        out.append(" case ").append(tag).append(" -> {\n");
        if (field.map() || field.kind() == ProtoPlan.Kind.GROUP) {
          out.append(" throw ").append(WRITER).append(".fallback();\n");
        } else if (field.kind().message()) {
          out.append(" w.descend(depth);\n");
          if (!field.repeated()) {
            appendSelection(out, type, layout, f);
            out.append(" if(w.get32(record+")
                .append(layout.slot(0, f))
                .append(")!=0) throw ")
                .append(WRITER)
                .append(".fallback();\n");
          }
          if (projected) {
            int child = java.util.Objects.requireNonNull(indices.get(field.typeName()));
            out.append(
                    " int length=w.length(end),start=w.position,childEnd=start+length; long"
                        + " bits;\n")
                .append(" if(!projection.wants(typeIds[")
                .append(t)
                .append("],")
                .append(f)
                .append(")) { if(projection.requiresEagerChild(typeIds[")
                .append(t)
                .append("],")
                .append(f)
                .append(")) throw ")
                .append(WRITER)
                .append(".fallback(); slabScan")
                .append(child)
                .append("(w,childEnd,depth+1,projection.laxValidation());\n")
                .append(" if(!projection.lazyChildrenEnabled()) {\n");
            if (field.repeated())
              out.append(" if(n")
                  .append(f)
                  .append("==Integer.MAX_VALUE) throw ")
                  .append(WRITER)
                  .append(".fallback(); n")
                  .append(f)
                  .append("++;\n");
            out.append(" presence")
                .append(f >>> 5)
                .append("|=")
                .append(1 << f)
                .append("; continue; }\n")
                .append(" bits=w.lazyChild(start,length,depth+1); }\n")
                .append(" else bits=slabProjectedRead")
                .append(child)
                .append("(w,childEnd,depth+1,projection,typeIds);\n");
          } else {
            out.append(
                    " int length=w.length(end); int childEnd=w.position+length;\n"
                        + " long bits=slabRead")
                .append(indices.get(field.typeName()))
                .append("(w,childEnd,depth+1);\n");
          }
          appendStore(out, field, layout, f, true);
        } else {
          boolean omitPayload =
              projected
                  && !field.repeated()
                  && (field.kind() == ProtoPlan.Kind.STRING
                      || field.kind() == ProtoPlan.Kind.BYTES);
          if (omitPayload) {
            int word = f >>> 5, mask = 1 << f;
            out.append(" if(!projection.wants(typeIds[")
                .append(t)
                .append("],")
                .append(f)
                .append(")) { int length=w.skipString(end,")
                .append(field.kind() == ProtoPlan.Kind.STRING)
                .append(",projection.laxValidation());\n");
            if (field.explicitPresence())
              out.append(" presence").append(word).append("|=").append(mask).append(";\n");
            else
              out.append(" presence")
                  .append(word)
                  .append("=length!=0 ? presence")
                  .append(word)
                  .append("|")
                  .append(mask)
                  .append(" : presence")
                  .append(word)
                  .append("&~(")
                  .append(mask)
                  .append(");\n");
            out.append(" } else {\n");
          }
          appendScalar(out, field, "end");
          appendEnumCheck(out, field);
          if (!field.repeated()) appendSelection(out, type, layout, f);
          appendStore(out, field, layout, f, false);
          if (omitPayload) out.append(" }\n");
        }
        out.append(" }\n");
        if (field.repeated() && field.kind().packable()) {
          out.append(" case ")
              .append((field.number() << 3) | 2)
              .append(" -> {\n")
              .append(" int length=w.length(end); int packedEnd=w.position+length;\n")
              .append(" while(w.position<packedEnd) {\n");
          appendScalar(out, field, "packedEnd");
          appendEnumCheck(out, field);
          appendStore(out, field, layout, f, false);
          out.append(" } }\n");
        }
      }
      out.append(" default -> throw ").append(WRITER).append(".fallback();\n } }\n");
      for (int f = 0; f < type.fields().size(); f++) {
        var field = type.fields().get(f);
        if (field.repeated() && !field.map()) {
          out.append(" if(n")
              .append(f)
              .append("!=0) { w.put32(record+")
              .append(layout.slot(0, f))
              .append(',');
          if (projected && field.kind() == ProtoPlan.Kind.MESSAGE)
            out.append("!projection.lazyChildrenEnabled() && !projection.wants(typeIds[")
                .append(t)
                .append("],")
                .append(f)
                .append(") ? w.countsOnly(n")
                .append(f)
                .append(") : ");
          out.append("w.list(p")
              .append(f)
              .append(",n")
              .append(f)
              .append(',')
              .append(layout.kind(0, f).element)
              .append(")); presence")
              .append(f >>> 5)
              .append("|=")
              .append(1 << f)
              .append("; }\n");
        }
        if (field.required())
          out.append(" if((presence")
              .append(f >>> 5)
              .append('&')
              .append(1 << f)
              .append(")==0) w.uncertified();\n");
      }
      for (int word = 0; word < layout.presenceWords(0); word++)
        out.append(" w.put32(record+")
            .append(Layout.PRESENCE_OFFSET + 4 * word)
            .append(",presence")
            .append(word)
            .append(");\n");
      out.append(" return record;\n }\n");
    }
  }

  /** The validation-only half of a lazy generated decode never allocates child records. */
  private static void appendScanMethods(StringBuilder out, List<ProtoPlan.Type> types) {
    var indices = new java.util.HashMap<String, Integer>();
    for (int i = 0; i < types.size(); i++) indices.put(types.get(i).name(), i);
    for (int t = 0; t < types.size(); t++) {
      var type = types.get(t);
      out.append(" private static void slabScan")
          .append(t)
          .append("(")
          .append(WRITER)
          .append(" w,int end,int depth,boolean lax) {\n");
      if (type.mapEntry()
          || type.fields().size() > 128
          || type.fields().stream()
              .anyMatch(
                  f ->
                      f.required()
                          || f.map()
                          || f.oneof() >= 0
                          || f.kind() == ProtoPlan.Kind.GROUP)) {
        out.append(" throw ").append(WRITER).append(".fallback(); }\n");
        continue;
      }
      out.append(" while(w.position<end) switch(w.tag(end)) {\n");
      for (var field : type.fields()) {
        out.append(" case ").append((field.number() << 3) | field.kind().wire).append(" -> {\n");
        if (field.kind().message()) {
          out.append(" w.descend(depth); int length=w.length(end); slabScan")
              .append(indices.get(field.typeName()))
              .append("(w,w.position+length,depth+1,lax);\n");
        } else if (field.kind() == ProtoPlan.Kind.STRING || field.kind() == ProtoPlan.Kind.BYTES) {
          out.append(" w.skipString(end,")
              .append(field.kind() == ProtoPlan.Kind.STRING)
              .append(",lax);\n");
        } else appendScalar(out, field, "end");
        out.append(" }\n");
        if (field.repeated() && field.kind().packable()) {
          out.append(" case ")
              .append((field.number() << 3) | 2)
              .append(
                  " -> { int length=w.length(end), packedEnd=w.position+length;"
                      + " while(w.position<packedEnd) {\n");
          appendScalar(out, field, "packedEnd");
          out.append(" } }\n");
        }
      }
      out.append(" default -> throw ").append(WRITER).append(".fallback(); } }\n");
    }
  }

  private static void appendSelection(
      StringBuilder out, ProtoPlan.Type type, Layout layout, int ordinal) {
    int oneof = type.fields().get(ordinal).oneof();
    if (oneof < 0) return;
    for (int f = 0; f < type.fields().size(); f++) {
      if (f == ordinal || type.fields().get(f).oneof() != oneof) continue;
      out.append(" presence")
          .append(f >>> 5)
          .append("&=~(")
          .append(1 << f)
          .append("); w.put")
          .append(layout.kind(0, f).width == 8 ? "64" : "32")
          .append("(record+")
          .append(layout.slot(0, f))
          .append(",0);\n");
    }
    out.append(" w.case16(record+")
        .append(layout.caseOffset(0, oneof))
        .append(',')
        .append(ordinal)
        .append(");\n");
  }

  private static void appendScalar(StringBuilder out, ProtoPlan.FieldPlan field, String end) {
    String expression =
        switch (field.kind()) {
          case INT32, UINT32, ENUM -> "(int)w.varint(" + end + ")";
          case INT64, UINT64 -> "w.varint(" + end + ")";
          case FIXED32, SFIXED32, FLOAT -> "w.fixed32(" + end + ")";
          case FIXED64, SFIXED64, DOUBLE -> "w.fixed64(" + end + ")";
          case BOOL -> "w.varint(" + end + ")!=0?1:0";
          case STRING, BYTES ->
              "w.string(" + end + "," + (field.kind() == ProtoPlan.Kind.STRING) + ")";
          case SINT32 -> {
            out.append(" int zigzag=(int)w.varint(").append(end).append(");\n");
            yield "(zigzag>>>1)^-(zigzag&1)";
          }
          case SINT64 -> {
            out.append(" long zigzag=w.varint(").append(end).append(");\n");
            yield "(zigzag>>>1)^-(zigzag&1)";
          }
          default -> throw new IllegalArgumentException("Structured scalar " + field.kind());
        };
    out.append(" long bits=").append(expression).append(";\n");
  }

  private static void appendEnumCheck(StringBuilder out, ProtoPlan.FieldPlan field) {
    var enumeration = field.enumType();
    if (enumeration == null || enumeration.open()) return;
    out.append(" switch((int)bits) {\n");
    for (int value : new java.util.TreeSet<>(enumeration.values().values()))
      out.append(" case ").append(value).append(" -> {}\n");
    out.append(" default -> throw ").append(WRITER).append(".fallback();\n }\n");
  }

  private static void appendStore(
      StringBuilder out, ProtoPlan.FieldPlan field, Layout layout, int ordinal, boolean message) {
    if (field.repeated()) {
      out.append(" p")
          .append(ordinal)
          .append("=w.append(p")
          .append(ordinal)
          .append(",n")
          .append(ordinal)
          .append("++,bits,")
          .append(layout.kind(0, ordinal).element)
          .append(");\n");
      return;
    }
    boolean wide = layout.kind(0, ordinal).width == 8;
    out.append(" w.put")
        .append(wide ? "64" : "32")
        .append("(record+")
        .append(layout.slot(0, ordinal))
        .append(',')
        .append(wide ? "bits" : "(int)bits")
        .append(");\n");
    int word = ordinal >>> 5, mask = 1 << ordinal;
    if (message || field.explicitPresence())
      out.append(" presence").append(word).append("|=").append(mask).append(";\n");
    else {
      String present =
          field.kind() == ProtoPlan.Kind.STRING || field.kind() == ProtoPlan.Kind.BYTES
              ? "w.stringLength((int)bits)!=0"
              : "bits!=0";
      out.append(" presence")
          .append(word)
          .append('=')
          .append(present)
          .append(" ? presence")
          .append(word)
          .append("|")
          .append(mask)
          .append(" : presence")
          .append(word)
          .append("&~(")
          .append(mask)
          .append(");\n");
    }
  }
}
