package dev.elide.truffledata.bench;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Type;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.*;
import dev.elide.truffledata.*;
import dev.elide.truffledata.jdbc.*;
import dev.elide.truffledata.protobuf.ProtoAccess;
import dev.elide.truffledata.protobuf.ProtoRow;
import dev.elide.truffledata.protobuf.ProtobufAdapter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import org.graalvm.polyglot.Context;

/** The same operation roots run in correctness, JVM JIT, and Native Image reachability gates. */
public final class JitMatrix {
  private JitMatrix() {}

  enum Failure {
    UNKNOWN,
    INDEX,
    UNSUPPORTED,
    UNEXPECTED,
    CONTRACT,
    STOP,
    ARITY
  }

  enum Op {
    READ,
    ITERATOR,
    IS_ITERATOR,
    HAS_ITERATOR,
    HAS_NEXT,
    NEXT,
    CLOSE,
    INVOCABLE,
    PROTO_NESTED,
    PROTO_HAS,
    DATA_LONG,
    DATA_DOUBLE,
    DATA_BOOLEAN,
    LONG,
    DOUBLE,
    BOOLEAN,
    LENGTH,
    PRESENT,
    MEMBER,
    ORDINAL,
    MEMBERS,
    SIZE,
    READABLE,
    MEMBER_READABLE,
    HAS_MEMBERS,
    HAS_ARRAY,
    NULL,
    DISPLAY,
    NUMBER,
    FIT_BYTE,
    FIT_SHORT,
    FIT_INT,
    FIT_LONG,
    FIT_FLOAT,
    FIT_DOUBLE,
    FIT_BIG,
    BYTE,
    SHORT,
    INT,
    AS_LONG,
    FLOAT,
    AS_DOUBLE,
    BIG,
    NESTED
  }

  record Case(String name, Op op, Object receiver, int index, String member, Object expected) {}

  public static void run(boolean requireCompiled) throws Exception {
    try (Context context =
        Context.newBuilder("td-bench")
            .allowExperimentalOptions(true)
            .option("engine.BackgroundCompilation", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .option("engine.SingleTierCompilationThreshold", "100")
            .option("engine.MultiTier", "false")
            .option("engine.Compilation", Boolean.toString(requireCompiled))
            .build()) {
      context.initialize("td-bench");
      context.enter();
      try {
        List<Case> cases = cases(requireCompiled);
        try {
          for (Case test : cases) {
            Access root = new Access(test.op(), test.index(), test.member());
            var target = root.getCallTarget();
            boolean rejects = test.expected() instanceof Failure;
            for (int i = 0; i < (rejects ? 2 : requireCompiled ? 1500 : 32); i++) {
              Object receiver =
                  test.receiver() instanceof Object[] variants
                      ? variants[i % variants.length]
                      : test.receiver();
              Object actual = target.call(receiver);
              if (!test.expected().equals(actual))
                throw new AssertionError(test.name() + ": " + actual + " != " + test.expected());
            }
            if (requireCompiled && !rejects && !root.compiled)
              throw new AssertionError("No compiled execution: " + test.name());
            System.out.println(
                "JIT_MATRIX "
                    + test.name()
                    + " mode="
                    + (rejects ? "rejection" : "success")
                    + " compiled="
                    + root.compiled);
          }
          System.out.println("JIT_MATRIX cases=" + cases.size());
        } finally {
          for (Case test : cases)
            if (test.receiver() instanceof JdbcCursor cursor) cursor.closeFromHost();
        }
      } finally {
        context.leave();
      }
    }
  }

  private static List<Case> cases(boolean requireCompiled) throws Exception {
    List<Case> cases = new ArrayList<>();
    Schema childSchema = Schema.of(Field.requiredLong("child"));
    AbstractRow child =
        Batch.copyOf(childSchema, Collections.singletonList(new Object[] {42L}), Limits.DEFAULT)
            .row(0);
    Schema schema =
        Schema.of(
            Field.integer("i8", 8, true, false, false),
            Field.integer("i16", 16, true, false, false),
            Field.integer("i32", 32, true, false, false),
            Field.requiredLong("i64"),
            new Field("f32", Field.Kind.FLOAT64, false, false, Constraints.NONE.withFloatBits(32)),
            new Field("f64", Field.Kind.FLOAT64, false, false),
            new Field("bool", Field.Kind.BOOLEAN, false, false),
            new Field("utf16", Field.Kind.STRING, false, false),
            new Field(
                "unicode",
                Field.Kind.STRING,
                false,
                false,
                Constraints.NONE.withLength(
                    new Constraints.Length(1, 8, Constraints.LengthUnit.CODE_POINTS))),
            new Field(
                "exactLength",
                Field.Kind.STRING,
                false,
                false,
                Constraints.NONE.withLength(
                    new Constraints.Length(3, 3, Constraints.LengthUnit.UTF16))),
            new Field(
                "array",
                Field.Kind.ARRAY,
                false,
                false,
                Constraints.NONE.withElement(Field.requiredLong("element"))),
            new Field("bytes", Field.Kind.ARRAY, false, false),
            new Field("record", Field.Kind.RECORD, false, false),
            new Field(
                "exact",
                Field.Kind.EXACT,
                false,
                false,
                Constraints.NONE.withDecimal(new Constraints.Decimal(4, 1))),
            new Field("nullable", Field.Kind.INT64, true, false),
            new Field("missing", Field.Kind.INT64, false, true),
            new Field("any", Field.Kind.ANY, false, false));
    Object[] values = {
      12L,
      1000L,
      100000L,
      Long.MAX_VALUE,
      1.5,
      2.25,
      true,
      "abc",
      "😀a",
      "abc",
      new DataArray(new Object[] {42L}),
      DataArray.copyOfBytes(new byte[] {(byte) 255}),
      child,
      new ExactNumber(new BigDecimal("12.5")),
      DataNull.INSTANCE,
      Missing.INSTANCE,
      42L
    };
    var object = new MatrixObject(Shape.newBuilder().layout(MatrixObject.class).build());
    for (int i = 0; i < values.length; i++)
      if (values[i] != Missing.INSTANCE)
        DynamicObject.PutNode.getUncached().execute(object, schema.field(i).name(), values[i]);
    AbstractRow[] rows = {
      Batch.copyOf(schema, Collections.singletonList(values), Limits.DEFAULT).row(0),
      new StaticLayout(BenchmarkLanguage.current().language(), schema, Limits.DEFAULT)
          .create(values),
      new DynamicRecord(object, schema)
    };
    String[] representations = {"batch", "static", "dynamic"};
    for (int r = 0; r < rows.length; r++) {
      AbstractRow row = rows[r];
      String prefix = representations[r];
      for (int i = 0; i < values.length; i++) {
        String member = schema.field(i).name();
        add(cases, prefix + "/read/" + member, Op.READ, row, i, member, values[i]);
        add(
            cases,
            prefix + "/member/" + member,
            Op.MEMBER,
            row,
            i,
            member,
            i == 15 ? Failure.UNKNOWN : values[i]);
        add(
            cases,
            prefix + "/ordinal/" + member,
            Op.ORDINAL,
            row,
            i,
            member,
            i == 15 ? Failure.INDEX : values[i]);
        add(cases, prefix + "/present/" + member, Op.PRESENT, row, i, member, i != 15);
      }
      for (int i = 0; i < 4; i++) add(cases, prefix + "/long/" + i, Op.LONG, row, i, "", values[i]);
      for (int i = 4; i < 6; i++)
        add(cases, prefix + "/double/" + i, Op.DOUBLE, row, i, "", values[i]);
      add(cases, prefix + "/boolean", Op.BOOLEAN, row, 6, "", true);
      for (int i = 7; i < 12; i++)
        add(cases, prefix + "/length/" + i, Op.LENGTH, row, i, "", i == 8 ? 2 : i < 10 ? 3 : 1);
      add(cases, prefix + "/nested", Op.NESTED, row, 12, "", 42L);
      add(cases, prefix + "/null-long", Op.LONG, row, 14, "", Failure.CONTRACT);
      add(cases, prefix + "/wrong-double", Op.DOUBLE, row, 0, "", Failure.CONTRACT);
      add(cases, prefix + "/wrong-boolean", Op.BOOLEAN, row, 0, "", Failure.CONTRACT);
      add(cases, prefix + "/data-long", Op.DATA_LONG, row, 0, "", 12L);
      add(cases, prefix + "/wrong-length", Op.LENGTH, row, 0, "", Failure.CONTRACT);
      add(cases, prefix + "/data-wrong-long", Op.DATA_LONG, row, 4, "", Failure.UNEXPECTED);
      add(cases, prefix + "/data-double", Op.DATA_DOUBLE, row, 4, "", 1.5);
      add(cases, prefix + "/data-boolean", Op.DATA_BOOLEAN, row, 6, "", true);
      add(cases, prefix + "/data-null-long", Op.DATA_LONG, row, 14, "", Failure.UNEXPECTED);
      add(cases, prefix + "/data-wrong-double", Op.DATA_DOUBLE, row, 0, "", Failure.UNEXPECTED);
      add(cases, prefix + "/data-wrong-boolean", Op.DATA_BOOLEAN, row, 0, "", Failure.UNEXPECTED);
      add(cases, prefix + "/members", Op.MEMBERS, row, 0, "", (long) values.length);
      add(cases, prefix + "/size", Op.SIZE, row, 0, "", (long) values.length);
      add(cases, prefix + "/has-members", Op.HAS_MEMBERS, row, 0, "", true);
      add(cases, prefix + "/has-array", Op.HAS_ARRAY, row, 0, "", true);
      add(cases, prefix + "/readable", Op.READABLE, row, 0, "", true);
      add(cases, prefix + "/unreadable", Op.READABLE, row, -1, "", false);
      add(cases, prefix + "/absent-member", Op.MEMBER_READABLE, row, 0, "missing", false);
      add(cases, prefix + "/known-member", Op.MEMBER_READABLE, row, 0, "i8", true);
      add(cases, prefix + "/unknown-member", Op.MEMBER, row, 0, "absent", Failure.UNKNOWN);
      add(cases, prefix + "/invalid-index", Op.ORDINAL, row, -1, "", Failure.INDEX);
    }
    for (boolean useStatic : new boolean[] {false, true}) {
      String prefix = "proto-native/" + (useStatic ? "static" : "batch");
      var plan =
          dev.elide.truffledata.bench.generated.NativeContracts.create(
                  useStatic ? BenchmarkLanguage.current().language() : null,
                  Limits.DEFAULT,
                  16,
                  1024)
              .type("truffledata.benchdata.Native");
      var populated =
          plan.newBuilder()
              .set(0, 1000L)
              .set(1, 1.5)
              .set(2, true)
              .set(3, DataArray.copyOfBytes(new byte[] {(byte) 255}))
              .set(4, 999L)
              .build();
      var row = plan.decode(plan.encode(populated));
      var absent = plan.newBuilder().build();
      Object[] expected = {
        1000L, 1.5, true, DataLibrary.factory().getUncached().read(row, 3), 999L
      };
      for (int i = 0; i < 5; i++) {
        add(cases, prefix + "/read/" + i, Op.READ, row, i, "", expected[i]);
        add(cases, prefix + "/member/" + i, Op.MEMBER, row, i, "f" + i, expected[i]);
        add(cases, prefix + "/has/" + i, Op.PROTO_HAS, row, i, "", true);
        add(cases, prefix + "/default-has/" + i, Op.PROTO_HAS, absent, i, "", false);
        add(cases, prefix + "/default-readable/" + i, Op.PRESENT, absent, i, "", true);
      }
      add(cases, prefix + "/long", Op.DATA_LONG, row, 0, "", 1000L);
      add(cases, prefix + "/double", Op.DATA_DOUBLE, row, 1, "", 1.5);
      add(cases, prefix + "/boolean", Op.DATA_BOOLEAN, row, 2, "", true);
      add(cases, prefix + "/bytes-length", Op.LENGTH, row, 3, "", 1);
      add(cases, prefix + "/enum", Op.DATA_LONG, row, 4, "", 999L);
      ProtoRow[] textRows = {
        plan.parseText("f0: 1000 f1: 1.5 f2: true f3: '\\377' f4: 999"),
        plan.parseJson("{\"f0\":1000,\"f1\":1.5,\"f2\":true,\"f3\":\"/w==\",\"f4\":999}")
      };
      for (int format = 0; format < textRows.length; format++) {
        String textPrefix = prefix + (format == 0 ? "/text" : "/json");
        ProtoRow parsed = textRows[format];
        add(cases, textPrefix + "/long", Op.DATA_LONG, parsed, 0, "", 1000L);
        add(cases, textPrefix + "/double", Op.DATA_DOUBLE, parsed, 1, "", 1.5);
        add(cases, textPrefix + "/boolean", Op.DATA_BOOLEAN, parsed, 2, "", true);
        add(cases, textPrefix + "/bytes-length", Op.LENGTH, parsed, 3, "", 1);
        add(cases, textPrefix + "/enum", Op.DATA_LONG, parsed, 4, "", 999L);
        add(cases, textPrefix + "/presence", Op.PROTO_HAS, parsed, 0, "", true);
      }
      add(cases, prefix + "/wrong-long", Op.DATA_LONG, row, 1, "", Failure.UNEXPECTED);
      add(cases, prefix + "/wrong-double", Op.DATA_DOUBLE, row, 0, "", Failure.UNEXPECTED);
      add(cases, prefix + "/wrong-boolean", Op.DATA_BOOLEAN, row, 0, "", Failure.UNEXPECTED);
    }
    for (boolean useStatic : new boolean[] {false, true}) {
      String prefix = "generated-graph/" + (useStatic ? "static" : "batch");
      var plan =
          dev.elide.truffledata.bench.generated.NativeContracts.create(
                  useStatic ? BenchmarkLanguage.current().language() : null,
                  Limits.DEFAULT,
                  16,
                  1024)
              .type("truffledata.benchdata.Graph");
      var access =
          new dev.elide.truffledata.bench.generated.NativeContracts
              .Access_truffledata$benchdata$Graph(plan);
      var row =
          access.decode(
              plan.encode(
                  plan.parseText(
                      "number: 42 score: 1.5 active: true name: 'graph' payload: 'abc' child {name:"
                          + " 'child'} children {name: 'first'} children {name: 'second'}")));
      var absent = access.decode(new byte[0]);
      add(cases, prefix + "/long", Op.DATA_LONG, row, 0, "", 42L);
      add(cases, prefix + "/double", Op.DATA_DOUBLE, row, 1, "", 1.5);
      add(cases, prefix + "/boolean", Op.DATA_BOOLEAN, row, 2, "", true);
      add(cases, prefix + "/string", Op.READ, row, 3, "", "graph");
      add(cases, prefix + "/bytes-length", Op.LENGTH, row, 4, "", 3);
      add(cases, prefix + "/children-length", Op.LENGTH, row, 6, "", 2);
      add(cases, prefix + "/nested", Op.PROTO_NESTED, row, 6, "", "first");
      add(cases, prefix + "/member", Op.MEMBER, row, 3, "name", "graph");
      add(cases, prefix + "/ordinal", Op.ORDINAL, row, 0, "", 42L);
      add(cases, prefix + "/has", Op.PROTO_HAS, row, 0, "", true);
      add(cases, prefix + "/absent", Op.PROTO_HAS, absent, 5, "", false);
      add(cases, prefix + "/absent-readable", Op.PRESENT, absent, 5, "", false);
      add(cases, prefix + "/missing", Op.READ, absent, 5, "", Missing.INSTANCE);
      add(cases, prefix + "/wrong-long", Op.DATA_LONG, row, 3, "", Failure.UNEXPECTED);
      add(cases, prefix + "/wrong-double", Op.DATA_DOUBLE, row, 0, "", Failure.UNEXPECTED);
      add(cases, prefix + "/wrong-boolean", Op.DATA_BOOLEAN, row, 0, "", Failure.UNEXPECTED);
      add(cases, prefix + "/invalid", Op.ORDINAL, row, -1, "", Failure.INDEX);
    }
    // Reuse one adopted accessor across more schema identities than its PIC can hold.
    List<Field> fields = new ArrayList<>();
    for (int i = 0; i < schema.size(); i++) fields.add(schema.field(i));
    Object[] variants = new Object[8];
    for (int i = 0; i < variants.length; i++) {
      Schema distinct = new Schema(fields);
      variants[i] =
          switch (i % 3) {
            case 0 ->
                Batch.copyOf(distinct, Collections.singletonList(values), Limits.DEFAULT).row(0);
            case 1 ->
                new StaticLayout(BenchmarkLanguage.current().language(), distinct, Limits.DEFAULT)
                    .create(values);
            default -> new DynamicRecord(object, distinct);
          };
    }
    for (int width : new int[] {1, 2, 3, 4, 8}) {
      Object[] receivers = Arrays.copyOf(variants, width);
      add(cases, "polymorphic/long/" + width, Op.LONG, receivers, 0, "", 12L);
      add(cases, "polymorphic/double/" + width, Op.DOUBLE, receivers, 4, "", 1.5);
      add(cases, "polymorphic/boolean/" + width, Op.BOOLEAN, receivers, 6, "", true);
      add(cases, "polymorphic/length/" + width, Op.LENGTH, receivers, 8, "", 2);
      add(cases, "polymorphic/member/" + width, Op.MEMBER, receivers, 0, "i8", 12L);
    }
    for (int i : new int[] {10, 11}) {
      Object array = values[i];
      add(cases, "array/" + i, Op.ORDINAL, array, 0, "", i == 10 ? 42L : 255L);
      add(cases, "array/invalid/" + i, Op.ORDINAL, array, -1, "", Failure.INDEX);
      add(cases, "array/size/" + i, Op.SIZE, array, 0, "", 1L);
    }
    add(cases, "null", Op.NULL, DataNull.INSTANCE, 0, "", true);
    add(cases, "missing-display", Op.DISPLAY, Missing.INSTANCE, 0, "", "<missing>");
    ExactNumber integer = new ExactNumber(BigDecimal.valueOf(42));
    for (Op op :
        new Op[] {
          Op.NUMBER,
          Op.FIT_BYTE,
          Op.FIT_SHORT,
          Op.FIT_INT,
          Op.FIT_LONG,
          Op.FIT_FLOAT,
          Op.FIT_DOUBLE,
          Op.FIT_BIG
        }) add(cases, "exact/" + op, op, integer, 0, "", true);
    Object[] converted = {(byte) 42, (short) 42, 42, 42L, 42f, 42d, BigInteger.valueOf(42), "42"};
    Op[] conversions = {
      Op.BYTE, Op.SHORT, Op.INT, Op.AS_LONG, Op.FLOAT, Op.AS_DOUBLE, Op.BIG, Op.DISPLAY
    };
    for (int i = 0; i < conversions.length; i++)
      add(cases, "exact/" + conversions[i], conversions[i], integer, 0, "", converted[i]);
    ExactNumber fractional = new ExactNumber(new BigDecimal("0.1"));
    for (Op op :
        new Op[] {
          Op.FIT_BYTE,
          Op.FIT_SHORT,
          Op.FIT_INT,
          Op.FIT_LONG,
          Op.FIT_FLOAT,
          Op.FIT_DOUBLE,
          Op.FIT_BIG
        }) add(cases, "fraction/" + op, op, fractional, 0, "", false);
    for (Op op : new Op[] {Op.BYTE, Op.SHORT, Op.INT, Op.AS_LONG, Op.FLOAT, Op.AS_DOUBLE, Op.BIG})
      add(cases, "fraction/" + op, op, fractional, 0, "", Failure.UNSUPPORTED);
    Type message =
        Type.newBuilder()
            .setName("Order")
            .addFields(com.google.protobuf.Field.newBuilder().setName("id").setNumber(1))
            .build();
    for (boolean statics : new boolean[] {false, true}) {
      ProtobufAdapter adapter =
          statics
              ? new ProtobufAdapter(
                  BenchmarkLanguage.current().language(), Type.getDescriptor(), Limits.DEFAULT, 16)
              : new ProtobufAdapter(Type.getDescriptor(), Limits.DEFAULT, 16);
      for (boolean dynamic : new boolean[] {false, true}) {
        AbstractRow row =
            adapter.snapshot(
                dynamic
                    ? DynamicMessage.parseFrom(Type.getDescriptor(), message.toByteArray())
                    : message);
        String prefix = "protobuf/" + statics + "/" + dynamic;
        add(cases, prefix + "/name", Op.MEMBER, row, 0, "name", "Order");
        add(
            cases,
            prefix + "/nested",
            Op.PROTO_NESTED,
            row,
            row.schema().indexOf("fields"),
            "",
            "id");
      }
    }
    Object[][] jdbcRows = new Object[requireCompiled ? 1600 : 64][];
    Arrays.setAll(jdbcRows, i -> new Object[] {42L});
    JdbcCursor cursor =
        JdbcSource.execute(
            JdbcFixture.connect(jdbcRows), "SELECT child", List.of(), childSchema, Limits.DEFAULT);
    add(cases, "cursor/has-iterator", Op.HAS_ITERATOR, cursor, 0, "", true);
    add(cases, "cursor/is-iterator", Op.IS_ITERATOR, cursor, 0, "", true);
    add(cases, "cursor/iterator", Op.ITERATOR, cursor, 0, "", cursor);
    add(cases, "cursor/members", Op.MEMBERS, cursor, 0, "", 1L);
    add(cases, "cursor/invocable", Op.INVOCABLE, cursor, 0, "close", true);
    add(cases, "cursor/has-next", Op.HAS_NEXT, cursor, 0, "", true);
    add(cases, "cursor/next", Op.NEXT, cursor, 0, "", 42L);
    add(cases, "cursor/close", Op.CLOSE, cursor, 0, "close", DataNull.INSTANCE);
    add(cases, "cursor/closed-next", Op.NEXT, cursor, 0, "", Failure.STOP);
    add(cases, "cursor/unknown-invoke", Op.CLOSE, cursor, 0, "unknown", Failure.UNKNOWN);
    return cases;
  }

  private static void add(
      List<Case> cases,
      String name,
      Op op,
      Object receiver,
      int index,
      String member,
      Object expected) {
    cases.add(new Case(name, op, receiver, index, member, expected));
  }

  public static final class MatrixObject extends DynamicObject {
    public MatrixObject(Shape shape) {
      super(shape);
    }
  }

  private static final class Access extends RootNode {
    private final Op op;
    private final int index;
    private final String member;
    @Child private DataLibrary data = DataLibrary.factory().createDispatched(3);
    @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary nested = InteropLibrary.getFactory().createDispatched(3);
    @Child private ReadLongNode integer = ReadLongNode.create();
    @Child private ReadDoubleNode floating = ReadDoubleNode.create();
    @Child private ReadBooleanNode bool = ReadBooleanNode.create();
    @Child private ReadLengthNode length = ReadLengthNode.create();
    boolean compiled;

    Access(Op op, int index, String member) {
      super(BenchmarkLanguage.current().language());
      this.op = op;
      this.index = index;
      this.member = member;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object receiver = frame.getArguments()[0];
      Object result;
      try {
        result =
            switch (op) {
              case ITERATOR -> interop.getIterator(receiver);
              case HAS_ITERATOR -> interop.hasIterator(receiver);
              case IS_ITERATOR -> interop.isIterator(receiver);
              case HAS_NEXT -> interop.hasIteratorNextElement(receiver);
              case NEXT -> nested.readArrayElement(interop.getIteratorNextElement(receiver), 0);
              case CLOSE -> interop.invokeMember(receiver, member);
              case INVOCABLE -> interop.isMemberInvocable(receiver, member);
              case PROTO_NESTED ->
                  nested.readMember(nested.readArrayElement(data.read(receiver, index), 0), "name");
              case PROTO_HAS -> ((ProtoAccess) receiver).has(index);
              case READ -> data.read(receiver, index);
              case DATA_LONG -> data.readLong(receiver, index);
              case DATA_DOUBLE -> data.readDouble(receiver, index);
              case DATA_BOOLEAN -> data.readBoolean(receiver, index);
              case LONG -> integer.execute(receiver, index);
              case DOUBLE -> floating.execute(receiver, index);
              case BOOLEAN -> bool.execute(receiver, index);
              case LENGTH -> length.execute(receiver, index);
              case PRESENT -> data.isPresent(receiver, index);
              case MEMBER -> interop.readMember(receiver, member);
              case ORDINAL -> interop.readArrayElement(receiver, index);
              case MEMBERS -> nested.getArraySize(interop.getMembers(receiver));
              case SIZE -> interop.getArraySize(receiver);
              case READABLE -> interop.isArrayElementReadable(receiver, index);
              case MEMBER_READABLE -> interop.isMemberReadable(receiver, member);
              case HAS_MEMBERS -> interop.hasMembers(receiver);
              case HAS_ARRAY -> interop.hasArrayElements(receiver);
              case NULL -> interop.isNull(receiver);
              case DISPLAY -> interop.toDisplayString(receiver, false);
              case NUMBER -> interop.isNumber(receiver);
              case FIT_BYTE -> interop.fitsInByte(receiver);
              case FIT_SHORT -> interop.fitsInShort(receiver);
              case FIT_INT -> interop.fitsInInt(receiver);
              case FIT_LONG -> interop.fitsInLong(receiver);
              case FIT_FLOAT -> interop.fitsInFloat(receiver);
              case FIT_DOUBLE -> interop.fitsInDouble(receiver);
              case FIT_BIG -> interop.fitsInBigInteger(receiver);
              case BYTE -> interop.asByte(receiver);
              case SHORT -> interop.asShort(receiver);
              case INT -> interop.asInt(receiver);
              case AS_LONG -> interop.asLong(receiver);
              case FLOAT -> interop.asFloat(receiver);
              case AS_DOUBLE -> interop.asDouble(receiver);
              case BIG -> interop.asBigInteger(receiver);
              case NESTED -> nested.readArrayElement(data.read(receiver, index), 0);
            };
      } catch (UnknownIdentifierException e) {
        result = Failure.UNKNOWN;
      } catch (InvalidArrayIndexException e) {
        result = Failure.INDEX;
      } catch (UnsupportedMessageException e) {
        result = Failure.UNSUPPORTED;
      } catch (StopIterationException e) {
        result = Failure.STOP;
      } catch (ArityException | UnsupportedTypeException e) {
        result = Failure.ARITY;
      } catch (ContractViolation e) {
        result = Failure.CONTRACT;
      } catch (UnexpectedResultException e) {
        result = Failure.UNEXPECTED;
      }
      compiled |= CompilerDirectives.inCompiledCode();
      return result;
    }
  }
}
