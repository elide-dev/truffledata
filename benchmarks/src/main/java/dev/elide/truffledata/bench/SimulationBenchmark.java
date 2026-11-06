package dev.elide.truffledata.bench;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Type;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.*;
import dev.elide.truffledata.*;
import dev.elide.truffledata.comparison.JsonFixture;
import dev.elide.truffledata.jdbc.*;
import dev.elide.truffledata.json.JavaSink;
import dev.elide.truffledata.json.JsonMessage;
import dev.elide.truffledata.json.JsonSnapshot;
import dev.elide.truffledata.json.JsonSvm;
import dev.elide.truffledata.protobuf.ProtobufAdapter;
import java.sql.*;
import java.util.*;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;

/**
 * Fixed-work native simulation cases. Setup and warmup are outside the measured region. No guest
 * JIT is enabled; JVM JIT/allocation gates live in //tests:compiled.
 */
public final class SimulationBenchmark {
  public static final int ROWS = 256;
  public static final int ITERATIONS = 8;
  private static final long SUM = (long) ROWS * (ROWS - 1) / 2;
  private static volatile Object retained = new Object();
  private static volatile long checksum;

  private SimulationBenchmark() {}

  @FunctionalInterface
  interface Work {
    long run() throws Exception;
  }

  record Case(String name, long expected, Work work) {
    void check() {
      try {
        long actual = work.run();
        checksum = actual;
        if (actual != expected) throw new AssertionError(name + ": " + actual + " != " + expected);
      } catch (Exception e) {
        throw new IllegalStateException(name, e);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--jit-check")) {
      JitMatrix.run(true);
      return;
    }
    boolean smoke = args.length == 1 && args[0].equals("--smoke");
    if (!smoke && args.length != 1)
      throw new IllegalArgumentException("Expected case name or --smoke");
    try (Context context =
        Context.newBuilder("td-bench")
            .allowExperimentalOptions(true)
            .option("engine.Compilation", "false")
            .build()) {
      context.initialize("td-bench");
      context.enter();
      try {
        List<Case> cases = cases();
        int count = 0;
        for (Case test : cases) {
          if (!smoke && !test.name().equals(args[0])) continue;
          for (int warmup = 0; warmup < 4; warmup++) test.check();
          Runnable measured =
              () -> {
                for (int iteration = 0; iteration < ITERATIONS; iteration++) test.check();
              };
          if (smoke) measured.run();
          else
            NativeHooks.measure(
                "benchmarks/cases.json::native-aot/"
                    + test.name()
                    + "[rows="
                    + ROWS
                    + ",iterations="
                    + ITERATIONS
                    + "]",
                measured);
          System.out.println(
              "{\"case\":\""
                  + test.name()
                  + "\",\"rows\":"
                  + ROWS
                  + ",\"iterations\":"
                  + ITERATIONS
                  + ",\"checksum\":"
                  + checksum
                  + "}");
          count++;
        }
        // The JSON cases link the static simdjson backend, so the JVM smoke runs the other 20.
        int all = ImageInfo.inImageRuntimeCode() ? 24 : 20;
        if (count != (smoke ? all : 1)) throw new AssertionError("Unexpected case count " + count);
      } finally {
        context.leave();
      }
    }
  }

  private static List<Case> cases() {
    var language = BenchmarkLanguage.current().language();
    Schema narrow = Schema.of(Field.integer("a", 16, false, false, false));
    Schema mixed =
        Schema.of(
            Field.integer("a", 32, true, false, false),
            new Field("b", Field.Kind.INT64, true, false),
            new Field(
                "c",
                Field.Kind.STRING,
                false,
                false,
                Constraints.NONE.withLength(
                    new Constraints.Length(4, 4, Constraints.LengthUnit.UTF16))),
            new Field("d", Field.Kind.BOOLEAN, false, false));
    List<Field> wideFields = new ArrayList<>();
    for (int i = 0; i < 16; i++) wideFields.add(Field.requiredLong("c" + i));
    Schema wide = new Schema(wideFields);
    List<Object[]> narrowInput = new ArrayList<>(),
        mixedInput = new ArrayList<>(),
        wideInput = new ArrayList<>();
    for (int i = 0; i < ROWS; i++) {
      narrowInput.add(new Object[] {(long) i});
      mixedInput.add(
          new Object[] {(long) i, i % 2 == 0 ? DataNull.INSTANCE : (long) i, "Anne", i % 2 == 0});
      Object[] row = new Object[16];
      Arrays.fill(row, (long) i);
      wideInput.add(row);
    }
    Batch narrowBatch = Batch.copyOf(narrow, narrowInput, Limits.DEFAULT);
    Batch mixedBatch = Batch.copyOf(mixed, mixedInput, Limits.DEFAULT);
    Batch wideBatch = Batch.copyOf(wide, wideInput, Limits.DEFAULT);
    StaticLayout narrowLayout = new StaticLayout(language, narrow, Limits.DEFAULT);
    StaticLayout mixedLayout = new StaticLayout(language, mixed, Limits.DEFAULT);
    AbstractRow[] batchRows = new AbstractRow[ROWS],
        mixedRows = new AbstractRow[ROWS],
        wideRows = new AbstractRow[ROWS],
        staticRows = new AbstractRow[ROWS],
        staticMixed = new AbstractRow[ROWS],
        dynamicRows = new AbstractRow[ROWS];
    Shape shape = Shape.newBuilder().layout(BenchmarkObject.class).build();
    for (int i = 0; i < ROWS; i++) {
      batchRows[i] = narrowBatch.row(i);
      mixedRows[i] = mixedBatch.row(i);
      wideRows[i] = wideBatch.row(i);
      staticRows[i] = narrowLayout.create(narrowInput.get(i));
      staticMixed[i] = mixedLayout.create(mixedInput.get(i));
      DynamicObject object = new BenchmarkObject(shape);
      DynamicObject.PutNode.getUncached().execute(object, "a", (long) i);
      dynamicRows[i] = new DynamicRecord(object, narrow);
    }
    List<Case> cases = new ArrayList<>();
    cases.add(read("batch-required-sum", batchRows, 0, SUM));
    cases.add(read("batch-nullable-sum", mixedRows, 1, (long) ROWS * ROWS / 4));
    cases.add(read("batch-wide-project-one", wideRows, 0, SUM));
    cases.add(read("static-required-sum", staticRows, 0, SUM));
    cases.add(read("static-mixed-projection", staticMixed, 2, SUM + ROWS * 4 + ROWS / 2));
    cases.add(read("dynamic-required-sum", dynamicRows, 0, SUM));
    cases.add(read("interop-member-sum", batchRows, 3, SUM));
    cases.add(read("interop-ordinal-sum", batchRows, 4, SUM));
    cases.add(read("static-exact-string-length", staticMixed, 5, ROWS * 4));
    cases.add(
        new Case(
            "ingest-narrow-batch",
            ROWS,
            () -> {
              Batch batch = Batch.copyOf(narrow, narrowInput, Limits.DEFAULT);
              retained = batch;
              return batch.size();
            }));
    cases.add(
        new Case(
            "ingest-wide-batch",
            ROWS,
            () -> {
              Batch batch = Batch.copyOf(wide, wideInput, Limits.DEFAULT);
              retained = batch;
              return batch.size();
            }));
    cases.add(
        new Case(
            "ingest-static-mixed",
            ROWS,
            () -> {
              for (Object[] row : mixedInput) retained = mixedLayout.create(row);
              return ROWS;
            }));
    cases.add(
        new Case(
            "retain-row-views",
            ROWS,
            () -> {
              for (int i = 0; i < ROWS; i++) retained = narrowBatch.row(i);
              return ROWS;
            }));
    Type message =
        Type.newBuilder()
            .setName("Anne")
            .addFields(com.google.protobuf.Field.newBuilder().setName("id").setNumber(1))
            .addFields(com.google.protobuf.Field.newBuilder().setName("name").setNumber(2))
            .build();
    ProtobufAdapter columnar = new ProtobufAdapter(Type.getDescriptor(), Limits.DEFAULT, 16);
    ProtobufAdapter statics =
        new ProtobufAdapter(language, Type.getDescriptor(), Limits.DEFAULT, 16);
    byte[] wire = message.toByteArray();
    cases.add(
        new Case(
            "protobuf-generated-batch",
            ROWS * 4,
            () -> {
              long sum = 0;
              for (int i = 0; i < ROWS; i++) {
                AbstractRow row = columnar.snapshot(message);
                retained = row;
                sum += ((String) DataLibrary.factory().getUncached().read(row, 0)).length();
              }
              return sum;
            }));
    cases.add(
        new Case(
            "protobuf-generated-static",
            ROWS * 4,
            () -> {
              long sum = 0;
              for (int i = 0; i < ROWS; i++) {
                AbstractRow row = statics.snapshot(message);
                retained = row;
                sum += ((String) DataLibrary.factory().getUncached().read(row, 0)).length();
              }
              return sum;
            }));
    cases.add(
        new Case(
            "protobuf-wire-static",
            ROWS * 4,
            () -> {
              long sum = 0;
              for (int i = 0; i < ROWS; i++) {
                AbstractRow row =
                    statics.snapshot(DynamicMessage.parseFrom(Type.getDescriptor(), wire));
                retained = row;
                sum += ((String) DataLibrary.factory().getUncached().read(row, 0)).length();
              }
              return sum;
            }));
    for (boolean mysql : new boolean[] {true, false}) {
      Schema contract =
          Schema.of(
              Field.integer("a", 32, !mysql, false, false),
              mixed.field(1),
              mixed.field(2),
              mixed.field(3));
      StaticLayout layout = new StaticLayout(language, contract, Limits.DEFAULT);
      Object[][] jdbcRows = new Object[ROWS][];
      for (int i = 0; i < ROWS; i++) {
        // JDBC returns Java null and Integer; the adapter must normalize both.
        jdbcRows[i] = new Object[] {i, i % 2 == 0 ? DataNull.INSTANCE : i, "Anne", i % 2 == 0};
      }
      Connection connection = JdbcFixture.connect(jdbcRows);
      for (boolean useStatic : new boolean[] {false, true}) {
        cases.add(
            new Case(
                (mysql ? "mysql" : "postgres") + "-fixture-" + (useStatic ? "static" : "batch"),
                SUM,
                () -> {
                  try (JdbcCursor cursor =
                      useStatic
                          ? JdbcSource.executeStatic(
                              connection,
                              "SELECT a,b,c,d FROM fixture WHERE a >= ?",
                              List.of(0),
                              layout,
                              Limits.DEFAULT)
                          : JdbcSource.execute(
                              connection,
                              "SELECT a,b,c,d FROM fixture WHERE a >= ?",
                              List.of(0),
                              contract,
                              Limits.DEFAULT)) {
                    long sum = 0;
                    if (useStatic) {
                      AbstractRow row;
                      while ((row = cursor.nextRecord()) != null) {
                        retained = row;
                        sum += (Long) DataLibrary.factory().getUncached().read(row, 0);
                      }
                    } else {
                      Batch batch;
                      while ((batch = cursor.nextBatch()) != null) {
                        retained = batch;
                        for (int i = 0; i < batch.size(); i++)
                          sum += (Long) DataLibrary.factory().getUncached().read(batch.row(i), 0);
                      }
                    }
                    return sum;
                  }
                }));
      }
    }
    if (ImageInfo.inImageRuntimeCode()) json(cases);
    return cases;
  }

  /** Mixed generic JSON documents, consumed the same way by each engine (see JsonFixture). */
  private static void json(List<Case> cases) {
    byte[][] inputs = new byte[ROWS][];
    long expected = 0;
    for (int i = 0; i < ROWS; i++) {
      inputs[i] = JsonFixture.document(i);
      expected += JsonFixture.checksum(i);
    }
    String[] names = {"gson-tree", "java-export", "interop-read", "snapshot"};
    for (int mode = 0; mode < names.length; mode++) {
      CallTarget target = new JsonIngestRoot(mode).getCallTarget();
      cases.add(new Case("json-generic-" + names[mode], expected, () -> (Long) target.call((Object) inputs)));
    }
  }

  /** Decode (or parse), consume and release each document; interop reads use adopted caches. */
  private static final class JsonIngestRoot extends RootNode {
    private final int mode;
    @Child private JsonFixture.InteropReader reader = new JsonFixture.InteropReader();

    JsonIngestRoot(int mode) {
      super(BenchmarkLanguage.current().language());
      this.mode = mode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      long sum = 0;
      for (byte[] input : (byte[][]) frame.getArguments()[0]) sum += ingest(input);
      return sum;
    }

    private long ingest(byte[] input) {
      if (mode == 0) {
        Object tree = JsonFixture.gsonParse(input);
        retained = tree;
        return JsonFixture.gsonRead(tree);
      }
      JsonMessage message = JsonSvm.decode(input);
      if (mode == 3) {
        JsonSnapshot snapshot;
        try {
          snapshot = message.snapshot();
        } finally {
          message.close();
        }
        retained = snapshot;
        return reader.read(snapshot.root());
      }
      try {
        if (mode == 1) {
          JavaSink sink = new JavaSink();
          message.export(sink);
          Object tree = Objects.requireNonNull(sink.result());
          retained = tree;
          return JsonFixture.javaRead(tree);
        }
        retained = message;
        return reader.read(message.root());
      } finally {
        message.close();
      }
    }
  }

  private static Case read(String name, AbstractRow[] rows, int mode, long expected) {
    CallTarget target = new ScanRoot(mode).getCallTarget();
    return new Case(name, expected, () -> (Long) target.call((Object) rows));
  }

  // A concrete layout class lets Truffle prepare dynamic-object metadata during image analysis.
  static final class BenchmarkObject extends DynamicObject {
    BenchmarkObject(Shape shape) {
      super(shape);
    }
  }

  private static final class ScanRoot extends RootNode {
    private final int mode;
    @Child private ReadLongNode integer = ReadLongNode.create();
    @Child private ReadBooleanNode bool = ReadBooleanNode.create();
    @Child private ReadLengthNode length = ReadLengthNode.create();
    @Child private DataLibrary data = DataLibrary.factory().createDispatched(3);
    @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(3);

    ScanRoot(int mode) {
      super(BenchmarkLanguage.current().language());
      this.mode = mode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      AbstractRow[] rows = (AbstractRow[]) frame.getArguments()[0];
      long sum = 0;
      try {
        for (AbstractRow row : rows) {
          sum +=
              switch (mode) {
                case 0 -> integer.execute(row, 0);
                case 1 -> {
                  Object value = data.read(row, 1);
                  yield value == DataNull.INSTANCE ? 0 : (Long) value;
                }
                case 2 ->
                    integer.execute(row, 0)
                        + length.execute(row, 2)
                        + (bool.execute(row, 3) ? 1 : 0);
                case 3 -> (Long) interop.readMember(row, "a");
                case 4 -> (Long) interop.readArrayElement(row, 0);
                case 5 -> length.execute(row, 2);
                default -> throw new AssertionError(mode);
              };
        }
      } catch (com.oracle.truffle.api.interop.InteropException e) {
        throw new IllegalStateException(e);
      }
      return sum;
    }
  }
}
