package dev.elide.truffledata.json.tests;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.myna.values.NativeValueTape;
import dev.elide.myna.values.ValueTape;
import dev.elide.truffledata.*;
import dev.elide.truffledata.json.generated.Json;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;

/** Executes generated static bindings and Myna's direct reader in a real native image. */
public final class SvmAcceptance {
  private static void require(boolean condition) {
    if (!condition) throw new AssertionError("SVM JSON acceptance");
  }

  private static int decode(String source, VoidPointer result) {
    byte[] input = source.getBytes(StandardCharsets.UTF_8);
    try (PinnedObject pinned = PinnedObject.create(input)) {
      int status =
          Json.decode(
              pinned.addressOfArrayElement(0),
              input.length,
              1 << 20,
              1 << 20,
              10000,
              1 << 20,
              128,
              result);
      Arrays.fill(input, (byte) 'x');
      return status;
    }
  }

  private static NativeValueTape tape(VoidPointer result) {
    Pointer descriptor = (Pointer) Json.JsonResult.view(result);
    require(descriptor.readInt(0) == ValueTape.ABI_VERSION_V4);
    NativeValueTape tape = new NativeValueTape(descriptor);
    tape.validate();
    return tape;
  }

  private static void scoped() throws InterruptedException {
    byte[] input =
        "{\"a\":[true, null, {\"s\":\"retained child\"}],\"n\":1e9999999999999999}"
            .getBytes(StandardCharsets.UTF_8);
    var message = dev.elide.truffledata.json.JsonSvm.decode(input);
    var root = message.root();
    var child = root.member("a").element(2).member("s");
    Arrays.fill(input, (byte) 'x');
    require(child.stringValue().equals("retained child"));
    require(root.member("a").element(0).booleanValue());
    require(root.member("n").numberText().equals("1e9999999999999999"));
    java.util.concurrent.atomic.AtomicInteger rejected =
        new java.util.concurrent.atomic.AtomicInteger();
    Thread other =
        new Thread(
            () -> {
              try {
                child.stringValue();
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
              try {
                message.close();
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
            });
    other.start();
    other.join();
    require(rejected.get() == 2);
    require(child.stringValue().equals("retained child"));
    require(child.rawText().equals("\"retained child\""));
    require(root.member("n").rawText().equals("1e9999999999999999"));
    try {
      root.member("a").rawText();
      throw new AssertionError("container raw text");
    } catch (IllegalStateException expected) {
    }
    var interop = InteropLibrary.getUncached();
    require(interop.hasMembers(root) && interop.hasArrayElements(root.member("a")));
    try {
      interop.asLong(interop.readArrayElement(root.member("a"), 2));
      throw new AssertionError("asLong on a JSON object");
    } catch (com.oracle.truffle.api.interop.UnsupportedMessageException expected) {
      // element 2 is an object: asLong is unsupported, which is the contract under test
    } catch (com.oracle.truffle.api.interop.InvalidArrayIndexException failure) {
      throw new AssertionError(failure);
    }
    try {
      require(interop.isNull(interop.readArrayElement(root.member("a"), 1)));
      require(interop.asBoolean(interop.readArrayElement(root.member("a"), 0)));
      require(interop.asString(interop.readMember(interop.readArrayElement(root.member("a"), 2), "s")).equals("retained child"));
      require(!interop.fitsInDouble(interop.readMember(root, "n")) && interop.isNumber(interop.readMember(root, "n")));
    } catch (com.oracle.truffle.api.interop.InteropException failure) {
      throw new AssertionError(failure);
    }
    var assumption = message.assumption();
    require(assumption.isValid());
    var snapshot = message.snapshot();
    message.close();
    message.close();
    require(!assumption.isValid());
    require(snapshot.root().member("a").element(2).member("s").stringValue().equals("retained child"));
    require(snapshot.root().member("n").numberText().equals("1e9999999999999999"));
    require(snapshot.root().member("a").element(2).member("s").rawText().equals("\"retained child\""));
    try {
      child.kind();
      throw new AssertionError("closed child kind readable");
    } catch (IllegalStateException expected) {
    }
    try {
      child.stringValue();
      throw new AssertionError("closed child readable");
    } catch (IllegalStateException expected) {
    }
    try {
      root.member("a");
      throw new AssertionError("closed root readable");
    } catch (IllegalStateException expected) {
    }
  }

  private static void schemaTyped() throws Exception {
    var data = DataLibrary.factory().getUncached();
    var schema =
        dev.elide.truffledata.Schema.of(
            dev.elide.truffledata.Field.integer("n", 8, true, false, false),
            new dev.elide.truffledata.Field(
                "s",
                dev.elide.truffledata.Field.Kind.STRING,
                true,
                true,
                dev.elide.truffledata.Constraints.NONE.withLength(
                    new dev.elide.truffledata.Constraints.Length(
                        1, 1, dev.elide.truffledata.Constraints.LengthUnit.CODE_POINTS))),
            dev.elide.truffledata.Field.integer("u", 64, false, false, false));
    var plan = dev.elide.truffledata.json.JsonPlan.compile(schema);
    byte[] input =
        "{\"n\":127,\"s\":\"😀\",\"u\":18446744073709551615}".getBytes(StandardCharsets.UTF_8);
    var record = plan.decode(input);
    Arrays.fill(input, (byte) 'x');
    for (String bad :
        new String[] {"{\"n\":128,\"u\":0}", "{\"n\":0,\"s\":\"ab\",\"u\":0}", "{\"n\":0}"}) {
      try (var unexpected = plan.decode(bad.getBytes(StandardCharsets.UTF_8))) {
        throw new AssertionError("Expected contract rejection");
      } catch (IllegalArgumentException expected) {
      }
    }
    try (var missing = plan.decode("{\"n\":0,\"u\":0}".getBytes(StandardCharsets.UTF_8))) {
      require(!missing.isPresent(1));
      require(data.read(missing, 1) == Missing.INSTANCE);
      require(!data.isPresent(missing, 1));
      require(missing.unsignedValue(2).signum() == 0);
    }
    try (var nullable =
        plan.decode("{\"n\":0,\"s\":null,\"u\":1}".getBytes(StandardCharsets.UTF_8))) {
      require(nullable.isPresent(1) && nullable.isNull(1));
      require(data.read(nullable, 1) == DataNull.INSTANCE);
      require(data.isPresent(nullable, 1));
    }
    plan.close();
    require(record.longValue(0) == 127);
    require(data.getSchema(record) == schema);
    require(data.readLong(record, 0) == 127);
    require(InteropLibrary.getUncached().readMember(record, "s").equals("😀"));
    require(data.read(record, 2) instanceof ExactNumber);
    try {
      new DataArray(new Object[] {record});
      throw new AssertionError("Scoped JSON record escaped into owned storage");
    } catch (ContractViolation expected) {
      /* explicit ownership */
    }
    try {
      data.readLong(record, 1);
      throw new AssertionError("Wrong shared primitive type");
    } catch (UnexpectedResultException expected) {
      require(expected.getResult().equals("😀"));
    }
    require(record.stringValue(1).equals("😀"));
    require(record.unsignedValue(2).toString().equals("18446744073709551615"));
    var rejected = new java.util.concurrent.atomic.AtomicInteger();
    Thread other =
        new Thread(
            () -> {
              try {
                record.longValue(0);
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
              try {
                record.close();
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
            });
    other.start();
    other.join();
    require(rejected.get() == 2);
    record.close();
    record.close();
    try {
      record.longValue(0);
      throw new AssertionError("Closed read");
    } catch (IllegalStateException expected) {
    }
    try {
      plan.decode(new byte[] {'0'});
      throw new AssertionError("Closed plan");
    } catch (IllegalStateException expected) {
    }
  }

  private static Field record(String name, String type, boolean nullable, boolean optional) {
    return new Field(
        name, Field.Kind.RECORD, nullable, optional, Constraints.NONE.withRecordType(type));
  }

  private static Field array(String name, Field element, boolean nullable, boolean optional) {
    return new Field(
        name, Field.Kind.ARRAY, nullable, optional, Constraints.NONE.withElement(element));
  }

  private static void rejectsPlan(Schema root, java.util.Map<String, Schema> types) {
    try (var unexpected = dev.elide.truffledata.json.JsonPlan.compile(root, types)) {
      throw new AssertionError("Expected plan rejection");
    } catch (IllegalArgumentException expected) {
    }
  }

  private static void nested() throws Exception {
    var data = DataLibrary.factory().getUncached();
    var interop = InteropLibrary.getUncached();
    var point =
        new Schema(
            "Point",
            java.util.List.of(
                Field.requiredLong("x"),
                new Field("y", Field.Kind.FLOAT64, false, false),
                new Field("tag", Field.Kind.STRING, true, true)));
    var line =
        new Schema(
            "Line",
            java.util.List.of(record("a", "Point", false, false), record("b", "Point", true, true)));
    var root =
        Schema.of(
            Field.requiredLong("id"),
            record("line", "Line", false, false),
            array("points", record("p", "Point", false, false), false, false),
            array(
                "scores",
                new Field(
                    "s",
                    Field.Kind.INT64,
                    false,
                    false,
                    Constraints.NONE.withIntegerRange(new Constraints.IntegerRange(0, 100))),
                true,
                true),
            array("names", new Field("n", Field.Kind.STRING, false, false), false, true));
    var types = java.util.Map.of("Point", point, "Line", line);
    var plan = dev.elide.truffledata.json.JsonPlan.compile(root, types);
    byte[] input =
        ("{\"id\":7,\"line\":{\"a\":{\"x\":1,\"y\":2.5},\"b\":null},"
                + "\"points\":[{\"x\":10,\"y\":0.5,\"tag\":\"p0\"},{\"x\":20,\"y\":1.5},"
                + "{\"x\":30,\"y\":2.5,\"tag\":null}],\"scores\":[1,2,100],\"names\":[]}")
            .getBytes(StandardCharsets.UTF_8);
    var record = plan.decode(input);
    Arrays.fill(input, (byte) 'x');
    for (String bad :
        new String[] {
          // out-of-range repeated element
          "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1}},\"points\":[],\"scores\":[101]}",
          // null element
          "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1}},\"points\":[null]}",
          // null non-nullable array
          "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1}},\"points\":null}",
          // unknown nested field
          "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1,\"z\":0}},\"points\":[]}",
          // missing required nested field
          "{\"id\":0,\"line\":{\"a\":{\"x\":1}},\"points\":[]}",
          // object where an array is required
          "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1}},\"points\":{}}",
          // missing required nested record
          "{\"id\":0,\"line\":{},\"points\":[]}"
        }) {
      try (var unexpected = plan.decode(bad.getBytes(StandardCharsets.UTF_8))) {
        throw new AssertionError("Expected nested contract rejection: " + bad);
      } catch (IllegalArgumentException expected) {
      }
    }
    try (var nullable =
        plan.decode(
            "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1}},\"points\":[],\"scores\":null}"
                .getBytes(StandardCharsets.UTF_8))) {
      require(nullable.isNull(3) && !nullable.isPresent(4));
      require(data.read(nullable, 3) == DataNull.INSTANCE);
      require(data.read(nullable, 4) == Missing.INSTANCE);
      require(nullable.array(2).size() == 0);
    }
    plan.close();

    // Nested primitives through TypedAccess paths: no intermediate TypedRecord.
    var x = new dev.elide.truffledata.tape.TypedAccess(root, types, 1, 0, 0);
    var y = new dev.elide.truffledata.tape.TypedAccess(root, types, 1, 0, 1);
    var bx = new dev.elide.truffledata.tape.TypedAccess(root, types, 1, 1, 0);
    require(x.longValue(record) == 1 && y.doubleValue(record) == 2.5);
    try {
      bx.longValue(record);
      throw new AssertionError("Path through a null record");
    } catch (IllegalStateException expected) {
    }
    try {
      new dev.elide.truffledata.tape.TypedAccess(root, types, 2, 0);
      throw new AssertionError("Path hop through an array");
    } catch (IllegalArgumentException expected) {
    }
    try {
      x.longValue(record.record(1));
      throw new AssertionError("Path applied to a nested record");
    } catch (IllegalArgumentException expected) {
    }

    // Records and arrays through Java, DataLibrary and interop.
    var lineRecord = record.record(1);
    require(lineRecord.schema() == line && lineRecord.record(0).schema() == point);
    require(lineRecord.record(0).longValue(0) == 1 && lineRecord.isNull(1));
    require(data.read(record, 1) instanceof dev.elide.truffledata.tape.TypedRecord);
    require(data.read(record, 2) instanceof dev.elide.truffledata.tape.TypedArray);
    var points = record.array(2);
    require(points.size() == 3);
    long xs = 0;
    for (int i = 0; i < points.size(); i++) xs += points.record(i).longValue(0);
    require(xs == 60);
    require(points.record(0).stringValue(2).equals("p0"));
    require(!points.record(1).isPresent(2) && points.record(2).isNull(2));
    require(points.element(1) instanceof dev.elide.truffledata.tape.TypedRecord);
    var scores = record.array(3);
    require(scores.size() == 3 && scores.longValue(2) == 100 && scores.element(0).equals(1L));
    var names = (dev.elide.truffledata.tape.TypedArray) data.read(record, 4);
    require(names.size() == 0 && names.snapshot().size() == 0);
    var guestPoints = interop.readMember(record, "points");
    require(interop.hasArrayElements(guestPoints) && interop.getArraySize(guestPoints) == 3);
    require(interop.readMember(interop.readArrayElement(guestPoints, 1), "x").equals(20L));
    require(interop.readArrayElement(interop.readMember(record, "scores"), 1).equals(2L));
    require(
        interop
            .readMember(interop.readMember(interop.readMember(record, "line"), "a"), "y")
            .equals(2.5));
    try {
      interop.readArrayElement(guestPoints, 3);
      throw new AssertionError("Out-of-range array element");
    } catch (com.oracle.truffle.api.interop.InvalidArrayIndexException expected) {
    }
    try {
      new DataArray(new Object[] {points});
      throw new AssertionError("Scoped JSON array escaped into owned storage");
    } catch (ContractViolation expected) {
    }

    var rejected = new java.util.concurrent.atomic.AtomicInteger();
    Thread other =
        new Thread(
            () -> {
              try {
                points.size();
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
              try {
                x.longValue(record);
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
            });
    other.start();
    other.join();
    require(rejected.get() == 2);

    // Snapshots are owned rows and arrays that outlive the owner.
    var owner = record.owner();
    var alive = owner.assumption();
    var snapshot = record.snapshot();
    var pointsSnapshot = points.snapshot();
    record.close();
    record.close();
    require(!alive.isValid() && !owner.isOpen());
    require(data.read(snapshot, 0).equals(7L));
    var snapLine = (AbstractRow) data.read(snapshot, 1);
    require(snapLine.schema() == line && data.read(snapLine, 1) == DataNull.INSTANCE);
    require(data.read(data.read(snapLine, 0), 1).equals(2.5));
    var snapPoints = (DataArray) data.read(snapshot, 2);
    require(snapPoints.size() == 3 && pointsSnapshot.size() == 3);
    require(data.read(snapPoints.get(0), 2).equals("p0"));
    require(data.read(pointsSnapshot.get(1), 2) == Missing.INSTANCE);
    require(data.read(pointsSnapshot.get(2), 2) == DataNull.INSTANCE);
    require(((DataArray) data.read(snapshot, 3)).get(2).equals(100L));
    require(((DataArray) data.read(snapshot, 4)).size() == 0);
    require(interop.readMember(interop.readArrayElement(snapPoints, 2), "x").equals(30L));
    require(new DataArray(new Object[] {snapshot}).size() == 1);
    for (Runnable closed :
        new Runnable[] {
          points::snapshot,
          points::size,
          () -> points.record(0),
          () -> record.record(1),
          record::snapshot,
          () -> x.longValue(record),
          () -> lineRecord.isNull(1)
        }) {
      try {
        closed.run();
        throw new AssertionError("Closed nested read");
      } catch (IllegalStateException expected) {
      }
    }

    // Plan contracts: recursion, unknown or misnamed types, nullable and nested-array elements.
    var node = new Schema("Node", java.util.List.of(record("next", "Node", true, true)));
    rejectsPlan(Schema.of(record("head", "Node", false, false)), java.util.Map.of("Node", node));
    rejectsPlan(Schema.of(record("p", "Missing", false, false)), types);
    rejectsPlan(
        Schema.of(record("p", "Point", false, false)),
        java.util.Map.of("Point", new Schema("Other", java.util.List.of(Field.requiredLong("x")))));
    rejectsPlan(
        Schema.of(array("a", new Field("e", Field.Kind.INT64, true, false), false, false)),
        java.util.Map.of());
    rejectsPlan(
        Schema.of(
            array(
                "a",
                array("e", new Field("i", Field.Kind.INT64, false, false), false, false),
                false,
                false)),
        java.util.Map.of());
    // Depth 32 (the root plus 31 nested records) compiles and reads; depth 33 is rejected.
    for (int depth : new int[] {32, 33}) {
      var chain = new java.util.HashMap<String, Schema>();
      for (int i = 1; i < depth; i++)
        chain.put(
            "T" + i,
            new Schema(
                "T" + i,
                i + 1 < depth
                    ? java.util.List.of(record("c", "T" + (i + 1), false, false))
                    : java.util.List.of(Field.requiredLong("leaf"))));
      var chainRoot = Schema.of(record("c", "T1", false, false));
      if (depth == 33) {
        rejectsPlan(chainRoot, chain);
        continue;
      }
      try (var deep = dev.elide.truffledata.json.JsonPlan.compile(chainRoot, chain)) {
        String json = "{\"c\":".repeat(depth - 1) + "{\"leaf\":5}" + "}".repeat(depth - 1);
        try (var deepRecord = deep.decode(json.getBytes(StandardCharsets.UTF_8))) {
          var leaf = new dev.elide.truffledata.tape.TypedAccess(chainRoot, chain, new int[depth]);
          require(leaf.longValue(deepRecord) == 5);
        }
      }
    }
  }

  private static void accessorContracts() throws Exception {
    var data = DataLibrary.factory().getUncached();
    var schema =
        dev.elide.truffledata.Schema.of(
            new dev.elide.truffledata.Field(
                "n", dev.elide.truffledata.Field.Kind.INT64, true, true));
    var access = new dev.elide.truffledata.tape.TypedAccess(schema, 0);
    var wrong =
        new dev.elide.truffledata.tape.TypedAccess(
            dev.elide.truffledata.Schema.of(dev.elide.truffledata.Field.requiredLong("n")), 0);
    try (var plan = dev.elide.truffledata.json.JsonPlan.compile(schema)) {
      for (String input : new String[] {"{}", "{\"n\":null}"}) {
        try (var record = plan.decode(input.getBytes(StandardCharsets.UTF_8))) {
          require(
              data.read(record, 0) == (input.equals("{}") ? Missing.INSTANCE : DataNull.INSTANCE));
          try {
            data.readLong(record, 0);
            throw new AssertionError("Missing/null shared primitive");
          } catch (UnexpectedResultException expected) {
            /* null and absence preserved */
          }
          try {
            access.longValue(record);
            throw new AssertionError("Missing/null accessor");
          } catch (IllegalStateException expected) {
          }
        }
      }
      try (var record = plan.decode("{\"n\":7}".getBytes(StandardCharsets.UTF_8))) {
        require(access.longValue(record) == 7);
        require(data.readLong(record, 0) == 7);
        try {
          wrong.longValue(record);
          throw new AssertionError("Wrong schema accessor");
        } catch (IllegalArgumentException expected) {
        }
        try {
          access.doubleValue(record);
          throw new AssertionError("Wrong accessor kind");
        } catch (IllegalArgumentException expected) {
        }
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        var thread =
            new Thread(
                () -> {
                  try {
                    data.read(record, 0);
                  } catch (IllegalStateException expected) {
                    rejected.incrementAndGet();
                  }
                });
        thread.start();
        thread.join();
        require(rejected.get() == 1);
        record.close();
        try {
          data.read(record, 0);
          throw new AssertionError("Closed shared accessor");
        } catch (IllegalStateException expected) {
        }
      }
    }
  }

  private static void typed() {
    VoidPointer fields =
        UnmanagedMemory.calloc(Json.JsonScalarField.SIZE + Json.JsonRecordPlan.SIZE);
    VoidPointer plan = UnmanagedMemory.calloc(Json.JsonPlan.SIZE);
    VoidPointer result = UnmanagedMemory.calloc(Json.JsonTypedResult.SIZE);
    if (fields.isNull() || plan.isNull() || result.isNull()) {
      if (fields.isNonNull()) UnmanagedMemory.free(fields);
      if (plan.isNonNull()) UnmanagedMemory.free(plan);
      if (result.isNonNull()) UnmanagedMemory.free(result);
      throw new OutOfMemoryError();
    }
    try {
      try (var name = CTypeConversion.toCString("n")) {
        Json.JsonScalarField.name(fields, name.get());
        Json.JsonScalarField.name_length(fields, 1);
        Json.JsonScalarField.kind(fields, 1);
        Json.JsonScalarField.flags(fields, 1);
        Json.JsonScalarField.minimum(fields, -128);
        Json.JsonScalarField.maximum(fields, 127);
        Json.JsonScalarField.child(fields, -1);
        VoidPointer root = (VoidPointer) ((Pointer) fields).add(Json.JsonScalarField.SIZE);
        Json.JsonRecordPlan.field_count(root, 1);
        require(Json.plan_create(root, 1, fields, 1, 1, plan) == 0);
      }
      byte[] input = "{\"n\":127}".getBytes(StandardCharsets.UTF_8);
      try (var pinned = PinnedObject.create(input)) {
        require(
            Json.typed_decode(
                    plan, pinned.addressOfArrayElement(0), input.length, 1024, 1024, result)
                == 0);
      }
      Json.plan_release(plan);
      require(Json.JsonTypedResult.present(result) == 1);
      require(Json.JsonTypedResult.nulls(result) == 0);
      require(Json.JsonTypedResult.count(result) == 1);
      require(Json.JsonScalarSlot.bits(Json.JsonTypedResult.slots(result)) == 127);
      require(Json.JsonTypedResult.index_allocations(result) == 1);
      Json.typed_release(result);
      Json.typed_release(result);
      require(Json.JsonTypedResult.owner(result).isNull());
    } finally {
      Json.typed_release(result);
      Json.plan_release(plan);
      UnmanagedMemory.free(result);
      UnmanagedMemory.free(plan);
      UnmanagedMemory.free(fields);
    }
  }

  private static boolean throwsState(Runnable action) {
    try {
      action.run();
      return false;
    } catch (IllegalStateException expected) {
      return true;
    }
  }

  /** Plan Task B2: explicit sessions with borrowed parse, scoped reads and retained decode. */
  private static void sessions() throws Exception {
    byte[] first =
        "{\"id\":1,\"note\":\"retained text longer than inline storage\",\"a\":[1,2]}"
            .getBytes(StandardCharsets.UTF_8);
    byte[] second = "{\"id\":2,\"note\":\"second\"}".getBytes(StandardCharsets.UTF_8);
    dev.elide.truffledata.json.JsonMessage kept;
    var session = dev.elide.truffledata.json.JsonSession.open();
    try {
      // borrow_invalidated_by_next_parse: a captured view throws once the borrow ends and after
      // the next parse; the session itself stays usable.
      var escaped = new dev.elide.truffledata.tape.TapeValue[1];
      long id =
          session.with(
              first,
              root -> {
                escaped[0] = root.member("note");
                return root.member("id").longValue();
              });
      require(id == 1);
      require(throwsState(() -> escaped[0].stringValue()));
      var sink = new dev.elide.truffledata.tape.JavaSink();
      session.parse(second, sink);
      require(throwsState(() -> escaped[0].kind()));
      require("second".equals(((java.util.Map<?, ?>) java.util.Objects.requireNonNull(sink.result())).get("note")));
      // Offsets select a slice of a larger buffer.
      byte[] framed = ("xx" + new String(second, StandardCharsets.UTF_8) + "yy").getBytes(StandardCharsets.UTF_8);
      var slice = new dev.elide.truffledata.tape.JavaSink();
      session.parse(framed, 2, second.length, slice);
      require(Long.valueOf(2).equals(((java.util.Map<?, ?>) java.util.Objects.requireNonNull(slice.result())).get("id")));
      // Re-entering the session from a sink event or a reader is rejected; the session survives.
      var reentrant =
          new dev.elide.truffledata.tape.TapeSink() {
            @Override
            public void beginObject(long members) {
              session.parse(second, new dev.elide.truffledata.tape.JavaSink());
            }

            @Override
            public void beginArray(long elements) {}

            @Override
            public void key(dev.elide.truffledata.tape.TapeText name) {}

            @Override
            public void end() {}

            @Override
            public void nullValue() {}

            @Override
            public void booleanValue(boolean value) {}

            @Override
            public void longValue(long value) {}

            @Override
            public void doubleValue(double value) {}

            @Override
            public void unsignedValue(long bits) {}

            @Override
            public void decimalValue(dev.elide.truffledata.tape.TapeText token) {}

            @Override
            public void stringValue(dev.elide.truffledata.tape.TapeText text) {}
          };
      require(throwsState(() -> session.parse(first, reentrant)));
      require(throwsState(() -> session.with(first, root -> session.decode(second))));
      require(throwsState(() -> session.with(first, root -> {
        session.close();
        return Boolean.TRUE;
      })));
      // Invalid input publishes nothing and leaves the session reusable.
      for (String bad : new String[] {"{\"a\":1,\"a\":2}", "[1,]", "01", "{\"k\\u0041\":1,\"kA\":2}"}) {
        try {
          session.validate(bad.getBytes(StandardCharsets.UTF_8));
          throw new AssertionError("accepted " + bad);
        } catch (IllegalArgumentException expected) {
        }
      }
      session.validate(first);
      // A retained message outlives the session's next decodes and the session itself.
      kept = session.decode(first);
      for (int i = 0; i < 1000; i++) session.validate(second);
      require(session.decode(second).root().member("id").longValue() == 2);
      require(kept.root().member("note").stringValue().equals("retained text longer than inline storage"));
      // Confinement: another thread can neither decode through the session nor close it.
      var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
      Thread other =
          new Thread(
              () -> {
                if (!throwsState(() -> session.validate(second)))
                  failure.set(new AssertionError("cross-thread session decode"));
                if (!throwsState(session::close)) failure.set(new AssertionError("cross-thread close"));
              });
      other.start();
      other.join();
      require(failure.get() == null);
    } finally {
      session.close();
    }
    session.close();
    require(throwsState(() -> session.validate(second)));
    require(kept.root().member("note").stringValue().equals("retained text longer than inline storage"));
    require(kept.root().member("a").element(1).longValue() == 2);
    var limited =
        dev.elide.truffledata.json.JsonSession.open(
            new dev.elide.truffledata.json.JsonLimits(8, 1 << 20, 1000, 1 << 20, 128));
    try {
      limited.validate("[1,2]".getBytes(StandardCharsets.UTF_8));
      try {
        limited.validate("[1,2,3,4]".getBytes(StandardCharsets.UTF_8));
        throw new AssertionError("session input limit");
      } catch (IllegalArgumentException expected) {
      }
    } finally {
      limited.close();
    }
    kept.close();
    require(throwsState(() -> kept.root()));
  }

  /** Plan Task F2: streams through the static-image session backend. */
  private static void streams() {
    StringBuilder ndjson = new StringBuilder();
    long expected = 0;
    for (int i = 0; i < 500; i++) {
      ndjson.append(i % 2 == 0 ? "{\"id\":" + i + ",\"s\":\"caf\u00e9 \\u00e9 text beyond inline\"}\n" : "[" + i + "] ");
      expected += i;
    }
    byte[] input = ndjson.toString().getBytes(StandardCharsets.UTF_8);
    var options = new dev.elide.truffledata.json.JsonStreamOptions(256, 256);
    dev.elide.truffledata.json.JsonMessage kept;
    var session = dev.elide.truffledata.json.JsonSession.open();
    try {
      for (int chunk : new int[] {1, 13, 4096}) {
        try (var stream = session.stream(options)) {
          long sum = 0;
          int at = 0;
          dev.elide.truffledata.tape.TapeValue root;
          while (at < input.length) {
            at += stream.feed(input, at, Math.min(chunk, input.length - at));
            while ((root = stream.next()) != null)
              sum += root.kind() == dev.elide.truffledata.tape.TapeValue.Kind.OBJECT ? root.member("id").longValue() : root.element(0).longValue();
          }
          stream.finish();
          require(stream.next() == null && stream.documents() == 500 && sum == expected);
        }
      }
      try (var stream = session.stream(options)) {
        require(stream.feed(input) == 512);
        // Streams copy strings: a TruffleSink gets no shared text array.
        var truffle = new dev.elide.truffledata.tape.TruffleSink(new dev.elide.truffledata.tape.TruffleSink.KeyCache());
        require(stream.next(truffle));
        var text = (com.oracle.truffle.api.strings.TruffleString) member((java.util.Map<?, ?>) java.util.Objects.requireNonNull(truffle.result()), "s");
        require(text.toJavaStringUncached().equals("caf\u00e9 \u00e9 text beyond inline"));
        var lent = java.util.Objects.requireNonNull(stream.next());
        require(lent.element(0).longValue() == 1);
        kept = stream.retain();
        require(throwsState(lent::kind));
        var next = java.util.Objects.requireNonNull(stream.next());
        session.validate("[1]".getBytes(StandardCharsets.UTF_8));
        require(throwsState(next::kind));
        stream.next(new dev.elide.truffledata.tape.JavaSink());
        stream.feed("[1,]\n[5]\n".getBytes(StandardCharsets.UTF_8));
      }
      try (var stream = session.stream(options)) {
        stream.feed("[1,]\n[5]\n{\"a\":".getBytes(StandardCharsets.UTF_8));
        stream.finish();
        try {
          stream.next();
          throw new AssertionError("accepted [1,]");
        } catch (IllegalArgumentException expected2) {
        }
        require(stream.skip());
        require(java.util.Objects.requireNonNull(stream.next()).element(0).longValue() == 5);
        require(stream.truncatedBytes() == 5);
        try {
          stream.next();
          throw new AssertionError("accepted a truncated document");
        } catch (IllegalArgumentException expected2) {
        }
        require(!stream.skip());
      }
      var open = session.stream(options);
      open.feed("[9]\n".getBytes(StandardCharsets.UTF_8));
      var lent = java.util.Objects.requireNonNull(open.next());
      session.close();
      require(throwsState(lent::kind));
      require(throwsState(() -> open.feed(new byte[] {'1'})));
    } finally {
      session.close();
    }
    require(kept.root().element(0).longValue() == 1);
    kept.close();
  }

  // Link probe of //json/native/bridge (Myna's myna-values static library).
  @org.graalvm.nativeimage.c.function.CFunction("td_json_bridge_present")
  private static native int bridgePresent();

  /**
   * Task D1: the image links Myna's simdutf bridge, and strings of 256 or more non-ASCII bytes
   * (transcoded by myna_utf8_to_utf16_v2) decode exactly like shorter ones (decoded locally),
   * through views, the export cursor and a borrowed session parse.
   */
  private static void textBridge() {
    require(bridgePresent() == 1);
    for (int units : new int[] {125, 126, 150, 2000}) {
      // A 4-byte emoji, units × "é" (2 bytes) and "z": 255 bytes (local), 257, 305 and 4005
      // (bulk). The escaped spelling lands in the decoded text region; the raw one is referenced
      // from the input buffer.
      String text = "😀" + "é".repeat(units) + "z";
      for (String spelled : new String[] {text, "\\ud83d\\ude00" + "\\u00e9".repeat(units) + "z"}) {
        byte[] input = ("{\"s\":\"" + spelled + "\",\"k\":[\"" + spelled + "\"]}").getBytes(StandardCharsets.UTF_8);
        try (var message = dev.elide.truffledata.json.JsonSvm.decode(input)) {
          require(message.root().member("s").stringValue().equals(text));
          var sink = new dev.elide.truffledata.tape.JavaSink(new dev.elide.truffledata.tape.JavaSink.KeyCache());
          message.export(sink);
          var tree = (java.util.Map<?, ?>) java.util.Objects.requireNonNull(sink.result());
          require(text.equals(tree.get("s")) && java.util.List.of(text).equals(tree.get("k")));
        }
        try (var session = dev.elide.truffledata.json.JsonSession.open()) {
          var sink = new dev.elide.truffledata.tape.JavaSink();
          session.parse(input, sink);
          require(text.equals(((java.util.Map<?, ?>) java.util.Objects.requireNonNull(sink.result())).get("s")));
        }
      }
    }
  }

  private static com.oracle.truffle.api.strings.TruffleString utf16(String text) {
    return com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
        text, com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16);
  }

  private static Object member(java.util.Map<?, ?> tree, String name) {
    return java.util.Objects.requireNonNull(tree.get(utf16(name)), name);
  }

  // The array a Latin-1 (or ASCII) TruffleString stores its text in, without copying it.
  private static byte[] latin1Storage(com.oracle.truffle.api.strings.TruffleString text) {
    var latin1 = com.oracle.truffle.api.strings.TruffleString.Encoding.ISO_8859_1;
    return text.switchEncodingUncached(latin1).getInternalByteArrayUncached(latin1).getArray();
  }

  /**
   * Task D2: a TruffleSink session parse builds every string over one per-document text array (no
   * copy; Latin-1 when possible, UTF-16 otherwise), copies strings of 64 KiB or more, and never
   * overwrites an array a string still references.
   */
  private static void textArenas() {
    var keys = new dev.elide.truffledata.tape.TruffleSink.KeyCache();
    String big = "y".repeat(70_000);
    byte[] doc =
        ("{\"id\":\"x\",\"\\u00e9t\\u00e9\":\"caf\\u00e9 au lait beyond sixteen bytes\",\"k\":\"\\ud83d\\ude00 中\","
                + "\"e\":\"\",\"n\":[1,\"plain ascii text\"],\"big\":\"" + big + "\"}")
            .getBytes(StandardCharsets.UTF_8);
    try (var session = dev.elide.truffledata.json.JsonSession.open()) {
      java.util.Map<?, ?> previous = null;
      byte[] previousArena = null;
      for (int round = 0; round < 3; round++) {
        var sink = new dev.elide.truffledata.tape.TruffleSink(keys);
        require(sink.sharedText() && !new dev.elide.truffledata.tape.JavaSink().sharedText());
        session.parse(doc, sink);
        var tree = (java.util.Map<?, ?>) java.util.Objects.requireNonNull(sink.result());
        var id = (com.oracle.truffle.api.strings.TruffleString) member(tree, "id");
        var cafe = (com.oracle.truffle.api.strings.TruffleString) member(tree, "été");
        var emoji = (com.oracle.truffle.api.strings.TruffleString) member(tree, "k");
        var plain = (com.oracle.truffle.api.strings.TruffleString) ((java.util.List<?>) member(tree, "n")).get(1);
        var large = (com.oracle.truffle.api.strings.TruffleString) member(tree, "big");
        require(id.equals(utf16("x")) && cafe.equals(utf16("café au lait beyond sixteen bytes")));
        require(emoji.equals(utf16("😀 中")) && plain.equals(utf16("plain ascii text")));
        require(large.equals(utf16(big)) && utf16("").equals(tree.get(utf16("e"))));
        // ASCII and Latin-1 strings share one array; the UTF-16 one shares it too; 64 KiB copies.
        byte[] arena = latin1Storage(id);
        require(latin1Storage(cafe) == arena && latin1Storage(plain) == arena);
        var utf16 = com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
        require(emoji.getInternalByteArrayUncached(utf16).getArray() == arena);
        require(latin1Storage(large) != arena);
        // The previous document's strings are intact: its array was not reused.
        if (previous != null) {
          require(previousArena != arena);
          require(utf16("café au lait beyond sixteen bytes").equals(previous.get(utf16("été"))));
        }
        previous = tree;
        previousArena = arena;
      }
      require(keys.size() == 6);
      // A JavaSink parse in between needs no arena, and keeps working.
      var plainSink = new dev.elide.truffledata.tape.JavaSink();
      session.parse(doc, plainSink);
      require("x".equals(((java.util.Map<?, ?>) java.util.Objects.requireNonNull(plainSink.result())).get("id")));
    }
  }

  /** Counts events; materializes nothing. */
  private static final class NoopSink implements dev.elide.truffledata.tape.TapeSink {
    long events;

    public void beginObject(long members) { events++; }

    public void beginArray(long elements) { events++; }

    public void key(dev.elide.truffledata.tape.TapeText name) { events++; }

    public void end() {}

    public void nullValue() { events++; }

    public void booleanValue(boolean value) { events++; }

    public void longValue(long value) { events++; }

    public void doubleValue(double value) { events++; }

    public void unsignedValue(long bits) { events++; }

    public void decimalValue(dev.elide.truffledata.tape.TapeText token) { events++; }

    public void stringValue(dev.elide.truffledata.tape.TapeText text) { events++; }
  }

  private static Object javaTree(dev.elide.truffledata.json.JsonSession session, Object input) {
    var sink = new dev.elide.truffledata.tape.JavaSink();
    if (input instanceof byte[] utf8) session.parse(utf8, sink);
    else session.parse((com.oracle.truffle.api.strings.TruffleString) input, sink);
    return java.util.Objects.requireNonNull(sink.result());
  }

  /**
   * Task D3: guest strings parse without a Java copy of the input: ASCII as UTF-8, Latin-1 as
   * Latin-1, other UTF-16 as UTF-16, transcoded natively; the result equals the UTF-8 parse.
   */
  private static void utf16Input() {
    var utf8 = com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;
    String[] sources = {
      "{\"id\":1,\"name\":\"ascii only\",\"a\":[1.5,-0,1E400]}",
      "{\"café\":\"ÿété\",\"n\":[1,2]}",
      "{\"clé\":\"中文 😀\",\"esc\":\"\\u00e9\\ud83d\\ude00\",\"n\":null}",
    };
    try (var session = dev.elide.truffledata.json.JsonSession.open()) {
      for (String source : sources) {
        Object expected = javaTree(session, source.getBytes(StandardCharsets.UTF_8));
        require(expected.equals(javaTree(session, utf16(source))));
        require(expected.equals(javaTree(session, com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(source, utf8))));
        // Shared text from UTF-16 input.
        var sink = new dev.elide.truffledata.tape.TruffleSink(new dev.elide.truffledata.tape.TruffleSink.KeyCache());
        session.parse(utf16(source), sink);
        require(sink.result() instanceof java.util.Map<?, ?> map && map.size() == ((java.util.Map<?, ?>) expected).size());
      }
      // Unpaired surrogates are invalid JSON input, in and outside strings; the session survives.
      for (String bad : new String[] {"[\"\ud800\"]", "[\"x\udc00\"]", "[1]\ud800"}) {
        try {
          session.parse(utf16(bad), new NoopSink());
          throw new AssertionError("unpaired surrogate accepted");
        } catch (IllegalArgumentException expected) {
        }
      }
      byte[] invalid = {'[', '"', (byte) 0xff, '"', ']'};
      try {
        session.parse(com.oracle.truffle.api.strings.TruffleString.fromByteArrayUncached(invalid, utf8, false), new NoopSink());
        throw new AssertionError("invalid UTF-8 accepted");
      } catch (IllegalArgumentException expected) {
      }
      // No Java copy of the input: a 1 MB ASCII, Latin-1 or UTF-16 guest string parses with a few
      // hundred bytes of Java allocation (the borrow's objects).
      var counter = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
      long thread = Thread.currentThread().threadId();
      for (String unit : new String[] {"a", "é", "中"}) {
        var big = utf16("[\"" + unit.repeat(300_000) + "\"]");
        var sink = new NoopSink();
        session.parse(big, sink);
        long before = counter.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 4; i++) session.parse(big, sink);
        long perParse = (counter.getThreadAllocatedBytes(thread) - before) / 4;
        require(sink.events == 10 && perParse < 4096);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    textBridge();
    textArenas();
    utf16Input();
    sessions();
    streams();
    typed();
    schemaTyped();
    accessorContracts();
    nested();
    CompiledReads.run();
    scoped();
    require(CTypeConversion.toJavaString(Json.fingerprint()).equals(Json.ABI_FINGERPRINT));
    VoidPointer first = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    if (first.isNull()) throw new OutOfMemoryError();
    VoidPointer second = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    if (second.isNull()) {
      UnmanagedMemory.free(first);
      throw new OutOfMemoryError();
    }
    try {
      require(
          decode(
                  "{\"text\":\"a long escaped string \\u4e2d"
                      + " 😀\",\"n\":1e999999999999999999,\"a\":[true,null]}",
                  first)
              == 0);
      NativeValueTape view = tape(first);
      long text = view.find(0, ValueTape.Utf8Key.of("text"));
      long number = view.find(0, ValueTape.Utf8Key.of("n"));
      require(view.stringValue(text).equals("a long escaped string 中 😀"));
      require(view.numberText(number).equals("1e999999999999999999"));
      require(view.find(0, ValueTape.Utf8Key.of("absent")) == -1);
      require(
          Json.JsonResult.index_peak_bytes(first) >= Json.JsonResult.index_retained_bytes(first));
      require(decode("[]", first) == 1);
      for (String source :
          new String[] {"true", "null", "\"short\"", "[]", "{}", "-12.5e2", "[0,{\"x\":false}]"}) {
        require(decode(source, second) == 0);
        tape(second);
        Json.release(second);
        Json.release(second);
        require(Json.JsonResult.owner(second).isNull());
      }
      require(view.stringValue(text).equals("a long escaped string 中 😀"));
      for (String source :
          new String[] {"[1,]", "{\"a\":1,\"\\u0061\":2}", "[01]", "{} true", "\"\\uD800\""}) {
        require(decode(source, second) == 4);
        require(Json.JsonResult.owner(second).isNull());
        require(Json.JsonResult.view(second).isNull());
      }
    } finally {
      Json.release(second);
      Json.release(first);
      UnmanagedMemory.free(second);
      UnmanagedMemory.free(first);
    }
    System.out.println("JSON generated SVM binding and Myna reader acceptance passed");
  }
}
