package dev.elide.truffledata.json.tests;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.AbstractRow;
import dev.elide.truffledata.Constraints;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataArray;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.DataNull;
import dev.elide.truffledata.ExactNumber;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Missing;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.JsonAccess;
import dev.elide.truffledata.json.JsonArray;
import dev.elide.truffledata.json.JsonDecoder;
import dev.elide.truffledata.json.JsonJvm;
import dev.elide.truffledata.json.JsonMessage;
import dev.elide.truffledata.json.JsonPlan;
import dev.elide.truffledata.json.JsonRecord;
import dev.elide.truffledata.json.JsonSession;
import dev.elide.truffledata.json.JsonValue;
import dev.elide.truffledata.json.generated.JsonFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class JvmScopedTest {
  static Path library() {
    return Path.of(
        System.getenv("TEST_SRCDIR"),
        System.getenv("TEST_WORKSPACE"),
        "json/native/json_shared.so");
  }

  static byte[] utf8(String source) {
    return source.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void retainsChildrenAfterInputMutationAndDecoderClose() {
    byte[] input = utf8("{\"a\":[true,null,{\"s\":\"retained child beyond sixteen\"}],\"n\":1e9999999999999999}");
    JsonMessage message;
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      message = decoder.decode(input);
    }
    Arrays.fill(input, (byte) 'x');
    try (message) {
      JsonValue root = message.root();
      assertEquals(JsonValue.Kind.OBJECT, root.kind());
      assertEquals("retained child beyond sixteen", root.member("a").element(2).member("s").stringValue());
      assertEquals("\"retained child beyond sixteen\"", root.member("a").element(2).member("s").rawText());
      assertTrue(root.member("a").element(0).booleanValue());
      assertEquals(JsonValue.Kind.NULL, root.member("a").element(1).kind());
      assertEquals("1e9999999999999999", root.member("n").numberText());
    }
  }

  @Test
  void rejectsDuplicateKeysAndMalformedInput() {
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      assertThrows(IllegalArgumentException.class, () -> decoder.decode(utf8("{\"a\":1,\"\\u0061\":2}")));
      assertThrows(IllegalArgumentException.class, () -> decoder.decode(utf8("[1,]")));
      assertThrows(IllegalArgumentException.class, () -> decoder.decode(new byte[0]));
    }
  }

  @Test
  void confinesMessagesToTheirThreadAndClosesIdempotently() throws Exception {
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message = decoder.decode(utf8("[1,2]"))) {
      JsonValue root = message.root();
      AtomicInteger rejected = new AtomicInteger();
      Thread other =
          new Thread(
              () -> {
                try {
                  root.size();
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
      assertEquals(2, rejected.get());
      assertEquals(2, root.size());
      var alive = message.assumption();
      assertTrue(alive.isValid());
      message.close();
      message.close();
      assertThrows(IllegalStateException.class, root::size);
      assertFalse(alive.isValid());
      assertThrows(IllegalStateException.class, message::assumption);
    }
  }

  @Test
  void siblingsAreIndependent() {
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      JsonMessage first = decoder.decode(utf8("{\"k\":\"first value is long enough\"}"));
      JsonMessage second = decoder.decode(utf8("{\"k\":\"second value is long enough\"}"));
      first.close();
      assertEquals("second value is long enough", second.root().member("k").stringValue());
      second.close();
    }
  }

  @Test
  void adoptedSegmentReleasesExactlyOnceAfterClose() {
    byte[] source = utf8("{\"s\":\"abcdefghijklmnopqrstuvwxyz0123456789\"}");
    AtomicInteger released = new AtomicInteger();
    try (JsonDecoder decoder = JsonJvm.open(library()); Arena arena = Arena.ofConfined()) {
      MemorySegment padded = arena.allocate(source.length + 64);
      MemorySegment.copy(source, 0, padded, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, source.length);
      JsonMessage message = decoder.adopt(padded, source.length, released::incrementAndGet);
      assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", message.root().member("s").stringValue());
      assertEquals(0, released.get());
      message.close();
      message.close();
      assertEquals(1, released.get());
      MemorySegment unpadded = arena.allocate(source.length);
      MemorySegment.copy(source, 0, unpadded, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, source.length);
      try (JsonMessage copied = decoder.adopt(unpadded, source.length, released::incrementAndGet)) {
        assertEquals(JsonValue.Kind.STRING, copied.root().member("s").kind());
      }
      assertEquals(2, released.get());
    }
  }

  @Test
  void adoptingAnotherThreadsConfinedSegmentFailsInTheDowncall() throws Exception {
    byte[] source = utf8("{\"a\":1}");
    MemorySegment[] foreign = new MemorySegment[1];
    // The owning thread ends without closing its arena, so the segment stays alive but confined
    // to a dead thread (a deliberate, test-only leak of a few bytes).
    Thread owner =
        new Thread(
            () -> {
              MemorySegment segment = Arena.ofConfined().allocate(source.length + 64);
              MemorySegment.copy(source, 0, segment, ValueLayout.JAVA_BYTE, 0, source.length);
              foreign[0] = segment;
            });
    owner.start();
    owner.join();
    AtomicInteger released = new AtomicInteger();
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      // Passes the adopt checks; the FFM downcall itself rejects the foreign confined scope.
      assertThrows(
          WrongThreadException.class,
          () -> decoder.adopt(foreign[0], source.length, released::incrementAndGet));
      assertEquals(0, released.get());
      try (JsonMessage message = decoder.decode(source)) {
        assertEquals(1, message.root().member("a").longValue());
      }
    }
  }

  @Test
  void longKeysAndNestedSnapshotsReadEveryTextRegion() {
    String plain = "a plain member name longer than sixteen bytes";
    String escaped = "an escaped \u00e9 member name, also long";
    byte[] input =
        utf8(
            "{\""
                + plain
                + "\":{\"an escaped \\u00e9 member name, also long\":[1,2]},\"d\":2.5}");
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message = decoder.decode(input)) {
      dev.elide.truffledata.json.JsonSnapshot snapshot = message.snapshot();
      assertSame(snapshot, snapshot.snapshot());
      // Message reads go through the FFM tape, snapshot reads through the heap tape.
      for (JsonValue root : new JsonValue[] {message.root(), snapshot.root()}) {
        assertEquals(2.5, root.member("d").doubleValue());
        JsonValue inner = root.member(plain);
        assertEquals(2, inner.member(escaped).element(1).longValue());
        // A subtree snapshot copies from the owner's own tape, including a snapshot's heap tape.
        dev.elide.truffledata.json.JsonSnapshot nested = inner.snapshot();
        assertEquals(5, nested.nodeCount());
        assertEquals(1, nested.root().member(escaped).element(0).longValue());
        assertEquals(escaped, nested.root().keyAt(0).stringValue());
      }
    }
  }

  @Test
  void checkOpenNodeCachesLiveOwnersThenChecksGenerically() {
    var check = dev.elide.truffledata.json.JsonOwner.CheckOpenNode.create();
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      JsonMessage[] messages = new JsonMessage[4];
      for (int i = 0; i < messages.length; i++) messages[i] = decoder.decode(utf8("[" + i + "]"));
      // Three owners fill the cache; the fourth replaces it with the generic check.
      for (JsonMessage message : messages) check.execute(message);
      check.execute(messages[0].snapshot());
      messages[0].close();
      assertThrows(IllegalStateException.class, () -> check.execute(messages[0]));
      var fresh = dev.elide.truffledata.json.JsonOwner.CheckOpenNode.create();
      fresh.execute(messages[1]);
      messages[1].close();
      assertThrows(IllegalStateException.class, () -> fresh.execute(messages[1]));
      for (int i = 2; i < messages.length; i++) messages[i].close();
    }
  }

  @Test
  void limitsAreValueRecords() {
    var same =
        new dev.elide.truffledata.json.JsonLimits(64 << 20, 64 << 20, 1_000_000, 64 << 20, 128);
    assertEquals(dev.elide.truffledata.json.JsonLimits.DEFAULT, same);
    assertEquals(dev.elide.truffledata.json.JsonLimits.DEFAULT.hashCode(), same.hashCode());
    assertNotEquals(same, new dev.elide.truffledata.json.JsonLimits(1, 1, 1, 1, 1));
    assertTrue(same.toString().contains("maxDepth=128"), same.toString());
    assertThrows(
        IllegalArgumentException.class,
        () -> new dev.elide.truffledata.json.JsonLimits(0, 1, 1, 1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new dev.elide.truffledata.json.JsonLimits(1, 1, 1, 1, 129));
  }

  @Test
  void snapshotsOutliveMessageAndDecoderAndCrossThreads() throws Exception {
    dev.elide.truffledata.json.JsonSnapshot snapshot;
    dev.elide.truffledata.json.JsonSnapshot subtree;
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message =
            decoder.decode(
                utf8("{\"k\\u0041\":[1,-0,\"escaped \\u4e2d text that is long\",\"plain text that is long enough\"],\"n\":1E400}"))) {
      snapshot = message.snapshot();
      subtree = message.root().member("kA").snapshot();
    }
    JsonValue root = snapshot.root();
    assertEquals(JsonValue.Kind.OBJECT, root.kind());
    assertEquals("\"k\\u0041\"", root.keyAt(0).rawText());
    JsonValue array = root.member("kA");
    assertEquals(4, array.size());
    assertEquals("1", array.element(0).numberText());
    assertEquals("-0", array.element(1).numberText());
    assertEquals("escaped 中 text that is long", array.element(2).stringValue());
    assertEquals("plain text that is long enough", array.element(3).stringValue());
    assertEquals("\"plain text that is long enough\"", array.element(3).rawText());
    assertEquals("1E400", root.member("n").numberText());
    assertEquals(JsonValue.Kind.ARRAY, subtree.root().kind());
    assertEquals(5, subtree.nodeCount());
    assertEquals("plain text that is long enough", subtree.root().element(3).stringValue());
    assertTrue(snapshot.assumption().isValid());
    String[] fromOtherThread = new String[1];
    Thread other = new Thread(() -> fromOtherThread[0] = snapshot.root().member("n").numberText());
    other.start();
    other.join();
    assertEquals("1E400", fromOtherThread[0]);
  }

  @Test
  void indexesLargeArraysOnceForRandomAccess() {
    StringBuilder source = new StringBuilder("[");
    for (int i = 0; i < 10_000; i++) source.append(i == 0 ? "" : ",").append(i);
    source.append(",[\"nested\"]]");
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message = decoder.decode(utf8(source.toString()))) {
      JsonValue array = message.root();
      assertEquals(0, message.indexedArrays());
      assertEquals("9999", array.element(9_999).numberText());
      assertEquals(1, message.indexedArrays());
      assertEquals("9999", array.element(9_999).numberText());
      assertEquals("nested", array.element(10_000).element(0).stringValue());
      assertEquals(1, message.indexedArrays(), "small arrays are walked, not indexed");
      assertThrows(IndexOutOfBoundsException.class, () -> array.element(10_001));
      long count = 0;
      for (JsonValue element : array.elements()) count++;
      assertEquals(10_001, count);
      assertEquals(1, message.indexedArrays(), "sequential traversal needs no index");
    }
  }

  @Test
  void ordersObjectMembersBySource() {
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message = decoder.decode(utf8("{\"z\":1,\"a\":[2],\"m\":3}"))) {
      JsonValue root = message.root();
      assertEquals("z", root.keyAt(0).stringValue());
      assertEquals("a", root.keyAt(1).stringValue());
      assertEquals("m", root.keyAt(2).stringValue());
      assertEquals("3", root.memberAt(2).numberText());
      assertEquals(JsonValue.Kind.ARRAY, root.memberAt(1).kind());
      assertThrows(IndexOutOfBoundsException.class, () -> root.keyAt(3));
    }
  }

  @Test
  void snapshotArrayIndexIsSafeAcrossThreads() throws Exception {
    StringBuilder source = new StringBuilder("[");
    for (int i = 0; i < 1_000; i++) source.append(i == 0 ? "" : ",").append(i);
    source.append("]");
    dev.elide.truffledata.json.JsonSnapshot snapshot;
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonMessage message = decoder.decode(utf8(source.toString()))) {
      snapshot = message.snapshot();
    }
    Thread[] readers = new Thread[8];
    String[] seen = new String[readers.length];
    for (int t = 0; t < readers.length; t++) {
      int slot = t;
      readers[t] = new Thread(() -> seen[slot] = snapshot.root().element(999 - slot).numberText());
      readers[t].start();
    }
    for (Thread reader : readers) reader.join();
    for (int t = 0; t < readers.length; t++) assertEquals(Integer.toString(999 - t), seen[t]);
    assertEquals(1, snapshot.indexedArrays());
  }

  @Test
  void staticBackendIsUnavailableOnTheJvm() {
    assertThrows(UnsupportedOperationException.class, dev.elide.truffledata.json.JsonSvm::open);
    // JVM sessions arrive with Task E1; until then a session needs the static image.
    assertThrows(
        UnsupportedOperationException.class, () -> dev.elide.truffledata.json.JsonSession.open());
    assertThrows(
        UnsupportedOperationException.class,
        () -> JsonPlan.compile(Schema.of(Field.requiredLong("n"))));
  }

  // Typed plans: mirrors SvmAcceptance.typed(), schemaTyped(), accessorContracts() and nested().

  /** Generated FFM layouts and handles drive the raw typed ABI exactly as the SVM bindings do. */
  @Test
  void generatedTypedBindingsDecodeAFlatPlan() throws Throwable {
    try (Arena arena = Arena.ofConfined()) {
      SymbolLookup symbols = SymbolLookup.libraryLookup(library().toAbsolutePath(), arena);
      MethodHandle create = JsonFFM.plan_create(symbols);
      MethodHandle planRelease = JsonFFM.plan_release(symbols);
      MethodHandle decode = JsonFFM.typed_decode(symbols);
      MethodHandle release = JsonFFM.typed_release(symbols);
      MemorySegment fields = arena.allocate(JsonFFM.JsonScalarField_LAYOUT);
      MemorySegment records = arena.allocate(JsonFFM.JsonRecordPlan_LAYOUT);
      MemorySegment plan = arena.allocate(JsonFFM.JsonPlan_LAYOUT);
      MemorySegment result = arena.allocate(JsonFFM.JsonTypedResult_LAYOUT);
      assertEquals(88, JsonFFM.JsonScalarField_LAYOUT.byteSize());
      assertEquals(112, JsonFFM.JsonTypedResult_LAYOUT.byteSize());
      try {
        fields.set(ValueLayout.ADDRESS, JsonFFM.JsonScalarField_name_OFFSET, arena.allocateFrom("n"));
        fields.set(ValueLayout.JAVA_LONG, JsonFFM.JsonScalarField_name_length_OFFSET, 1);
        fields.set(ValueLayout.JAVA_INT, JsonFFM.JsonScalarField_kind_OFFSET, 1);
        fields.set(ValueLayout.JAVA_INT, JsonFFM.JsonScalarField_flags_OFFSET, 1);
        fields.set(ValueLayout.JAVA_LONG, JsonFFM.JsonScalarField_minimum_OFFSET, -128);
        fields.set(ValueLayout.JAVA_LONG, JsonFFM.JsonScalarField_maximum_OFFSET, 127);
        fields.set(ValueLayout.JAVA_INT, JsonFFM.JsonScalarField_child_OFFSET, -1);
        records.set(ValueLayout.JAVA_INT, JsonFFM.JsonRecordPlan_field_count_OFFSET, 1);
        assertEquals(0, (int) create.invokeExact(records, 1L, fields, 1L, 1L, plan));
        byte[] input = utf8("{\"n\":127}");
        MemorySegment data = arena.allocate(input.length);
        MemorySegment.copy(input, 0, data, ValueLayout.JAVA_BYTE, 0, input.length);
        assertEquals(
            0, (int) decode.invokeExact(plan, data, (long) input.length, 1024L, 1024L, result));
        planRelease.invokeExact(plan);
        assertEquals(1, result.get(ValueLayout.JAVA_LONG, JsonFFM.JsonTypedResult_present_OFFSET));
        assertEquals(0, result.get(ValueLayout.JAVA_LONG, JsonFFM.JsonTypedResult_nulls_OFFSET));
        assertEquals(1, result.get(ValueLayout.JAVA_LONG, JsonFFM.JsonTypedResult_count_OFFSET));
        MemorySegment slots =
            result
                .get(ValueLayout.ADDRESS, JsonFFM.JsonTypedResult_slots_OFFSET)
                .reinterpret(JsonFFM.JsonScalarSlot_LAYOUT.byteSize());
        assertEquals(127, slots.get(ValueLayout.JAVA_LONG, JsonFFM.JsonScalarSlot_bits_OFFSET));
        assertEquals(
            1, result.get(ValueLayout.JAVA_LONG, JsonFFM.JsonTypedResult_index_allocations_OFFSET));
        release.invokeExact(result);
        release.invokeExact(result);
        assertEquals(
            0, result.get(ValueLayout.ADDRESS, JsonFFM.JsonTypedResult_owner_OFFSET).address());
      } finally {
        release.invokeExact(result);
        planRelease.invokeExact(plan);
      }
    }
  }

  @Test
  void typedScalarPlanEnforcesContractsAndOwnership() throws Exception {
    DataLibrary data = DataLibrary.factory().getUncached();
    Schema schema =
        Schema.of(
            Field.integer("n", 8, true, false, false),
            new Field(
                "s",
                Field.Kind.STRING,
                true,
                true,
                Constraints.NONE.withLength(
                    new Constraints.Length(1, 1, Constraints.LengthUnit.CODE_POINTS))),
            Field.integer("u", 64, false, false, false));
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      JsonPlan plan = decoder.compile(schema);
      byte[] input = utf8("{\"n\":127,\"s\":\"😀\",\"u\":18446744073709551615}");
      JsonRecord record = plan.decode(input);
      Arrays.fill(input, (byte) 'x');
      for (String bad : new String[] {"{\"n\":128,\"u\":0}", "{\"n\":0,\"s\":\"ab\",\"u\":0}", "{\"n\":0}"})
        assertThrows(IllegalArgumentException.class, () -> plan.decode(utf8(bad)), bad);
      try (JsonRecord missing = plan.decode(utf8("{\"n\":0,\"u\":0}"))) {
        assertFalse(missing.isPresent(1));
        assertSame(Missing.INSTANCE, data.read(missing, 1));
        assertFalse(data.isPresent(missing, 1));
        assertEquals(0, missing.unsignedValue(2).signum());
      }
      try (JsonRecord nullable = plan.decode(utf8("{\"n\":0,\"s\":null,\"u\":1}"))) {
        assertTrue(nullable.isPresent(1) && nullable.isNull(1));
        assertSame(DataNull.INSTANCE, data.read(nullable, 1));
        assertTrue(data.isPresent(nullable, 1));
      }
      plan.close();
      // Records outlive their plan.
      assertEquals(127, record.longValue(0));
      assertSame(schema, data.getSchema(record));
      assertEquals(127, data.readLong(record, 0));
      assertEquals("😀", InteropLibrary.getUncached().readMember(record, "s"));
      assertInstanceOf(ExactNumber.class, data.read(record, 2));
      assertThrows(ContractViolation.class, () -> new DataArray(new Object[] {record}));
      UnexpectedResultException wrong =
          assertThrows(UnexpectedResultException.class, () -> data.readLong(record, 1));
      assertEquals("😀", wrong.getResult());
      assertEquals("😀", record.stringValue(1));
      assertEquals("18446744073709551615", record.unsignedValue(2).toString());
      assertEquals(2, rejectedOnOtherThread(() -> record.longValue(0), record::close));
      record.close();
      record.close();
      assertThrows(IllegalStateException.class, () -> record.longValue(0));
      assertThrows(IllegalStateException.class, () -> plan.decode(new byte[] {'0'}));
    }
  }

  @Test
  void typedAccessorsPreserveMissingAndNull() throws Exception {
    DataLibrary data = DataLibrary.factory().getUncached();
    Schema schema = Schema.of(new Field("n", Field.Kind.INT64, true, true));
    JsonAccess access = new JsonAccess(schema, 0);
    JsonAccess wrong = new JsonAccess(Schema.of(Field.requiredLong("n")), 0);
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonPlan plan = decoder.compile(schema)) {
      for (String input : new String[] {"{}", "{\"n\":null}"}) {
        try (JsonRecord record = plan.decode(utf8(input))) {
          assertSame(
              input.equals("{}") ? Missing.INSTANCE : DataNull.INSTANCE, data.read(record, 0));
          assertThrows(UnexpectedResultException.class, () -> data.readLong(record, 0));
          assertThrows(IllegalStateException.class, () -> access.longValue(record));
        }
      }
      try (JsonRecord record = plan.decode(utf8("{\"n\":7}"))) {
        assertEquals(7, access.longValue(record));
        assertEquals(7, data.readLong(record, 0));
        assertThrows(IllegalArgumentException.class, () -> wrong.longValue(record));
        assertThrows(IllegalArgumentException.class, () -> access.doubleValue(record));
        assertEquals(1, rejectedOnOtherThread(() -> data.read(record, 0)));
        record.close();
        assertThrows(IllegalStateException.class, () -> data.read(record, 0));
      }
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

  @Test
  void typedNestedAndRepeatedRecords() throws Exception {
    DataLibrary data = DataLibrary.factory().getUncached();
    InteropLibrary interop = InteropLibrary.getUncached();
    Schema point =
        new Schema(
            "Point",
            List.of(
                Field.requiredLong("x"),
                new Field("y", Field.Kind.FLOAT64, false, false),
                new Field("tag", Field.Kind.STRING, true, true)));
    Schema line =
        new Schema(
            "Line", List.of(record("a", "Point", false, false), record("b", "Point", true, true)));
    Schema root =
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
    Map<String, Schema> types = Map.of("Point", point, "Line", line);
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      JsonPlan plan = decoder.compile(root, types);
      byte[] input =
          utf8(
              "{\"id\":7,\"line\":{\"a\":{\"x\":1,\"y\":2.5},\"b\":null},"
                  + "\"points\":[{\"x\":10,\"y\":0.5,\"tag\":\"p0\"},{\"x\":20,\"y\":1.5},"
                  + "{\"x\":30,\"y\":2.5,\"tag\":null}],\"scores\":[1,2,100],\"names\":[]}");
      JsonRecord record = plan.decode(input);
      Arrays.fill(input, (byte) 'x');
      for (String bad :
          new String[] {
            "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1}},\"points\":[],\"scores\":[101]}",
            "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1}},\"points\":[null]}",
            "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1}},\"points\":null}",
            "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1,\"z\":0}},\"points\":[]}",
            "{\"id\":0,\"line\":{\"a\":{\"x\":1}},\"points\":[]}",
            "{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1}},\"points\":{}}",
            "{\"id\":0,\"line\":{},\"points\":[]}"
          })
        assertThrows(IllegalArgumentException.class, () -> plan.decode(utf8(bad)), bad);
      try (JsonRecord nullable =
          plan.decode(
              utf8("{\"id\":0,\"line\":{\"a\":{\"x\":1,\"y\":1}},\"points\":[],\"scores\":null}"))) {
        assertTrue(nullable.isNull(3) && !nullable.isPresent(4));
        assertSame(DataNull.INSTANCE, data.read(nullable, 3));
        assertSame(Missing.INSTANCE, data.read(nullable, 4));
        assertEquals(0, nullable.array(2).size());
      }
      plan.close();

      // Nested primitives through JsonAccess paths: no intermediate JsonRecord.
      JsonAccess x = new JsonAccess(root, types, 1, 0, 0);
      JsonAccess y = new JsonAccess(root, types, 1, 0, 1);
      JsonAccess bx = new JsonAccess(root, types, 1, 1, 0);
      assertEquals(1, x.longValue(record));
      assertEquals(2.5, y.doubleValue(record));
      assertThrows(IllegalStateException.class, () -> bx.longValue(record));
      assertThrows(IllegalArgumentException.class, () -> new JsonAccess(root, types, 2, 0));
      assertThrows(IllegalArgumentException.class, () -> x.longValue(record.record(1)));

      // Records and arrays through Java, DataLibrary and interop.
      JsonRecord lineRecord = record.record(1);
      assertSame(line, lineRecord.schema());
      assertSame(point, lineRecord.record(0).schema());
      assertEquals(1, lineRecord.record(0).longValue(0));
      assertTrue(lineRecord.isNull(1));
      assertInstanceOf(JsonRecord.class, data.read(record, 1));
      assertInstanceOf(JsonArray.class, data.read(record, 2));
      JsonArray points = record.array(2);
      assertEquals(3, points.size());
      long xs = 0;
      for (int i = 0; i < points.size(); i++) xs += points.record(i).longValue(0);
      assertEquals(60, xs);
      assertEquals("p0", points.record(0).stringValue(2));
      assertTrue(!points.record(1).isPresent(2) && points.record(2).isNull(2));
      assertInstanceOf(JsonRecord.class, points.element(1));
      JsonArray scores = record.array(3);
      assertEquals(3, scores.size());
      assertEquals(100, scores.longValue(2));
      assertEquals(1L, scores.element(0));
      JsonArray names = (JsonArray) data.read(record, 4);
      assertEquals(0, names.size());
      assertEquals(0, names.snapshot().size());
      Object guestPoints = interop.readMember(record, "points");
      assertTrue(interop.hasArrayElements(guestPoints));
      assertEquals(3, interop.getArraySize(guestPoints));
      assertEquals(20L, interop.readMember(interop.readArrayElement(guestPoints, 1), "x"));
      assertEquals(2L, interop.readArrayElement(interop.readMember(record, "scores"), 1));
      assertEquals(
          2.5,
          interop.readMember(interop.readMember(interop.readMember(record, "line"), "a"), "y"));
      assertThrows(InvalidArrayIndexException.class, () -> interop.readArrayElement(guestPoints, 3));
      assertThrows(ContractViolation.class, () -> new DataArray(new Object[] {points}));
      assertEquals(2, rejectedOnOtherThread(points::size, () -> x.longValue(record)));

      // Snapshots are owned rows and arrays that outlive the owner.
      var owner = record.owner();
      var alive = owner.assumption();
      AbstractRow snapshot = record.snapshot();
      DataArray pointsSnapshot = points.snapshot();
      record.close();
      record.close();
      assertTrue(!alive.isValid() && !owner.isOpen());
      assertEquals(7L, data.read(snapshot, 0));
      AbstractRow snapLine = (AbstractRow) data.read(snapshot, 1);
      assertSame(line, snapLine.schema());
      assertSame(DataNull.INSTANCE, data.read(snapLine, 1));
      assertEquals(2.5, data.read(data.read(snapLine, 0), 1));
      DataArray snapPoints = (DataArray) data.read(snapshot, 2);
      assertEquals(3, snapPoints.size());
      assertEquals(3, pointsSnapshot.size());
      assertEquals("p0", data.read(snapPoints.get(0), 2));
      assertSame(Missing.INSTANCE, data.read(pointsSnapshot.get(1), 2));
      assertSame(DataNull.INSTANCE, data.read(pointsSnapshot.get(2), 2));
      assertEquals(100L, ((DataArray) data.read(snapshot, 3)).get(2));
      assertEquals(0, ((DataArray) data.read(snapshot, 4)).size());
      assertEquals(30L, interop.readMember(interop.readArrayElement(snapPoints, 2), "x"));
      assertEquals(1, new DataArray(new Object[] {snapshot}).size());
      for (Executable closed :
          new Executable[] {
            points::snapshot,
            points::size,
            () -> points.record(0),
            () -> record.record(1),
            record::snapshot,
            () -> x.longValue(record),
            () -> lineRecord.isNull(1)
          }) assertThrows(IllegalStateException.class, closed);

      // Plan contracts: recursion, unknown or misnamed types, nullable and nested-array elements.
      Schema node = new Schema("Node", List.of(record("next", "Node", true, true)));
      rejectsPlan(decoder, Schema.of(record("head", "Node", false, false)), Map.of("Node", node));
      rejectsPlan(decoder, Schema.of(record("p", "Missing", false, false)), types);
      rejectsPlan(
          decoder,
          Schema.of(record("p", "Point", false, false)),
          Map.of("Point", new Schema("Other", List.of(Field.requiredLong("x")))));
      rejectsPlan(
          decoder,
          Schema.of(array("a", new Field("e", Field.Kind.INT64, true, false), false, false)),
          Map.of());
      rejectsPlan(
          decoder,
          Schema.of(
              array(
                  "a",
                  array("e", new Field("i", Field.Kind.INT64, false, false), false, false),
                  false,
                  false)),
          Map.of());
      // Depth 32 (the root plus 31 nested records) compiles and reads; depth 33 is rejected.
      for (int depth : new int[] {32, 33}) {
        var chain = new HashMap<String, Schema>();
        for (int i = 1; i < depth; i++)
          chain.put(
              "T" + i,
              new Schema(
                  "T" + i,
                  i + 1 < depth
                      ? List.of(record("c", "T" + (i + 1), false, false))
                      : List.of(Field.requiredLong("leaf"))));
        Schema chainRoot = Schema.of(record("c", "T1", false, false));
        if (depth == 33) {
          rejectsPlan(decoder, chainRoot, chain);
          continue;
        }
        try (JsonPlan deep = decoder.compile(chainRoot, chain)) {
          String json = "{\"c\":".repeat(depth - 1) + "{\"leaf\":5}" + "}".repeat(depth - 1);
          try (JsonRecord deepRecord = deep.decode(utf8(json))) {
            assertEquals(5, new JsonAccess(chainRoot, chain, new int[depth]).longValue(deepRecord));
          }
        }
      }
    }
  }

  @Test
  void typedScalarArraysAndCachedLibraryReads() throws Exception {
    Schema schema =
        Schema.of(
            Field.requiredLong("n"),
            new Field("d", Field.Kind.FLOAT64, false, false),
            new Field("b", Field.Kind.BOOLEAN, false, false),
            array("ds", new Field("e", Field.Kind.FLOAT64, false, false), false, false),
            array("bs", new Field("e", Field.Kind.BOOLEAN, false, false), false, false),
            array("ss", new Field("e", Field.Kind.STRING, false, false), false, false));
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonPlan plan = decoder.compile(schema);
        JsonRecord record =
            plan.decode(
                utf8(
                    "{\"n\":3,\"d\":1.5,\"b\":true,\"ds\":[0.5,2.0],\"bs\":[true,false],"
                        + "\"ss\":[\"x\",\"a string longer than sixteen\"]}"))) {
      assertTrue(record.booleanValue(2));
      assertTrue(new JsonAccess(schema, 2).booleanValue(record));
      // A cached library binds JsonAccess per (schema, index); the uncached one reads generically.
      DataLibrary cached = DataLibrary.factory().create(record);
      new com.oracle.truffle.api.nodes.RootNode(null) {
        @Child DataLibrary library = cached;

        {
          adoptChildren();
        }

        @Override
        public Object execute(com.oracle.truffle.api.frame.VirtualFrame frame) {
          return library;
        }
      };
      assertEquals(3, cached.readLong(record, 0));
      assertEquals(1.5, cached.readDouble(record, 1));
      assertTrue(cached.readBoolean(record, 2));
      DataLibrary uncached = DataLibrary.factory().getUncached();
      assertEquals(1.5, uncached.readDouble(record, 1));
      assertTrue(uncached.readBoolean(record, 2));
      assertThrows(UnexpectedResultException.class, () -> uncached.readDouble(record, 0));
      assertThrows(UnexpectedResultException.class, () -> uncached.readBoolean(record, 0));
      JsonArray doubles = record.array(3);
      assertEquals(Field.Kind.FLOAT64, doubles.elementField().kind());
      assertEquals(2.0, doubles.doubleValue(1));
      assertEquals(0.5, doubles.element(0));
      JsonArray booleans = record.array(4);
      assertFalse(booleans.booleanValue(1));
      assertEquals(true, booleans.element(0));
      JsonArray strings = record.array(5);
      assertEquals("a string longer than sixteen", strings.stringValue(1));
      assertEquals("x", strings.element(0));
      assertThrows(IllegalArgumentException.class, () -> strings.longValue(0));
      DataArray copied = doubles.snapshot();
      assertEquals(List.of(0.5, 2.0), List.of(copied.get(0), copied.get(1)));
    }
  }

  @Test
  void plansRejectDecodingAfterTheirDecoderCloses() {
    Schema schema = Schema.of(Field.requiredLong("n"));
    JsonDecoder decoder = JsonJvm.open(library());
    JsonPlan plan = decoder.compile(schema);
    JsonRecord record = plan.decode(utf8("{\"n\":5}"));
    decoder.close();
    assertThrows(IllegalStateException.class, () -> plan.decode(utf8("{\"n\":6}")));
    assertThrows(IllegalStateException.class, () -> decoder.compile(schema));
    // Records already returned, and the plan's own close, outlive the decoder.
    assertEquals(5, record.longValue(0));
    plan.close();
    assertEquals(5, record.longValue(0));
    record.close();
    assertThrows(IllegalStateException.class, () -> record.longValue(0));
    // A sibling decoder's plan is independent of the closed one.
    try (JsonDecoder other = JsonJvm.open(library());
        JsonPlan sibling = other.compile(schema);
        JsonRecord fresh = sibling.decode(utf8("{\"n\":7}"))) {
      assertEquals(7, fresh.longValue(0));
    }
  }

  @Test
  void plansAreConfinedToTheirThread() throws Exception {
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonPlan plan = decoder.compile(Schema.of(Field.requiredLong("n")))) {
      assertEquals(
          2, rejectedOnOtherThread(() -> plan.decode(utf8("{\"n\":1}")), plan::close));
      try (JsonRecord record = plan.decode(utf8("{\"n\":1}"))) {
        assertEquals(1, record.longValue(0));
      }
    }
  }

  private static void rejectsPlan(JsonDecoder decoder, Schema root, Map<String, Schema> types) {
    assertThrows(IllegalArgumentException.class, () -> decoder.compile(root, types).close());
  }

  /** Runs each action on a fresh thread and counts thread-confinement rejections. */
  private static int rejectedOnOtherThread(Executable... actions) throws InterruptedException {
    AtomicInteger rejected = new AtomicInteger();
    Thread other =
        new Thread(
            () -> {
              for (Executable action : actions) {
                try {
                  action.execute();
                } catch (IllegalStateException expected) {
                  rejected.incrementAndGet();
                } catch (Throwable unexpected) {
                  throw new AssertionError(unexpected);
                }
              }
            });
    other.start();
    other.join();
    return rejected.get();
  }
  @Test
  void elementIterablesCheckTheOwnerWhenIterated() throws Exception {
    try (JsonDecoder decoder = JsonJvm.open(library())) {
      JsonMessage message = decoder.decode(utf8("[1,2,3]"));
      Iterable<JsonValue> elements = message.root().elements();
      var crossThread = new java.util.concurrent.atomic.AtomicReference<Throwable>();
      Thread other = new Thread(() -> {
        try {
          assertNotNull(elements.iterator());
        } catch (Throwable failure) {
          crossThread.set(failure);
        }
      });
      other.start();
      other.join();
      assertInstanceOf(IllegalStateException.class, crossThread.get(), "iterator() on another thread");
      message.close();
      assertThrows(
          IllegalStateException.class, () -> assertNotNull(elements.iterator()), "iterator() after close");
    }
  }

  @Test
  void nestedAccessorsRejectRecordTypesFromAnotherPlan() throws Exception {
    Schema root = Schema.of(record("t", "T", false, false));
    Schema narrow = new Schema("T", List.of(Field.requiredLong("a")));
    Schema wide =
        new Schema(
            "T", List.of(new Field("b", Field.Kind.FLOAT64, false, false), Field.requiredLong("a")));
    try (JsonDecoder decoder = JsonJvm.open(library());
        JsonPlan narrowPlan = decoder.compile(root, Map.of("T", narrow));
        JsonPlan widePlan = decoder.compile(root, Map.of("T", wide))) {
      var access = new JsonAccess(root, Map.of("T", narrow), 0, 0);
      try (JsonRecord ok = narrowPlan.decode(utf8("{\"t\":{\"a\":5}}"));
          JsonRecord other = widePlan.decode(utf8("{\"t\":{\"b\":1.5,\"a\":6}}"))) {
        assertEquals(5, access.longValue(ok));
        // Same root Schema object, different record layout: the hop must not index the wrong slots.
        assertThrows(IllegalArgumentException.class, () -> access.longValue(other));
        assertEquals(6, new JsonAccess(root, Map.of("T", wide), 0, 1).longValue(other));
      }
    }
  }

  // ---- JVM sessions (performance plan Task E1): mirrors SvmAcceptance.sessions() ----

  private static Map<?, ?> tree(dev.elide.truffledata.json.JavaSink sink) {
    return (Map<?, ?>) java.util.Objects.requireNonNull(sink.result());
  }

  @Test
  void jvmSessionsBorrowRetainAndRejectMisuse() throws Exception {
    byte[] first = utf8("{\"id\":1,\"note\":\"retained text longer than inline storage\",\"a\":[1,2]}");
    byte[] second = utf8("{\"id\":2,\"note\":\"second\"}");
    JsonMessage kept;
    JsonSession session = JsonJvm.openSession(library(), dev.elide.truffledata.json.JsonLimits.DEFAULT);
    try {
      // borrow_invalidated_by_next_parse: a captured view throws once the borrow ends and after
      // the next parse; the session itself stays usable.
      var escaped = new JsonValue[1];
      long id =
          session.with(
              first,
              root -> {
                escaped[0] = root.member("note");
                assertEquals("1", root.member("id").numberText());
                assertEquals("\"retained text longer than inline storage\"", escaped[0].rawText());
                return root.member("id").longValue();
              });
      assertEquals(1, id);
      assertThrows(IllegalStateException.class, () -> escaped[0].stringValue());
      var sink = new dev.elide.truffledata.json.JavaSink();
      session.parse(second, sink);
      assertEquals(2L, tree(sink).get("id"));
      assertThrows(IllegalStateException.class, () -> escaped[0].stringValue());
      byte[] framed = utf8("xx{\"id\":3}yy");
      var slice = new dev.elide.truffledata.json.JavaSink();
      session.parse(framed, 2, framed.length - 4, slice);
      assertEquals(3L, tree(slice).get("id"));
      // Retained decodes outlive later decodes and the session.
      kept = session.decode(first);
      session.parse(second, new dev.elide.truffledata.json.JavaSink());
      assertEquals("retained text longer than inline storage", kept.root().member("note").stringValue());
      try (JsonMessage sliced = session.decode(framed, 2, framed.length - 4)) {
        assertEquals(3L, sliced.root().member("id").longValue());
      }
      // Re-entering the session from a sink event or a reader is rejected; the session survives.
      assertThrows(
          IllegalStateException.class,
          () ->
              session.parse(
                  first,
                  new dev.elide.truffledata.json.JsonSink() {
                    public void beginObject(long members) {
                      session.parse(second, new dev.elide.truffledata.json.JavaSink());
                    }

                    public void beginArray(long elements) {}

                    public void key(dev.elide.truffledata.json.JsonText name) {}

                    public void end() {}

                    public void nullValue() {}

                    public void booleanValue(boolean value) {}

                    public void longValue(long value) {}

                    public void doubleValue(double value) {}

                    public void unsignedValue(long bits) {}

                    public void decimalValue(dev.elide.truffledata.json.JsonText token) {}

                    public void stringValue(dev.elide.truffledata.json.JsonText text) {}
                  }));
      assertThrows(IllegalStateException.class, () -> session.with(first, root -> session.decode(second)));
      assertThrows(
          IllegalStateException.class,
          () ->
              session.with(
                  first,
                  root -> {
                    session.close();
                    return Boolean.TRUE;
                  }));
      // Shared-text sinks work on the JVM too; their strings are copied (no text arena).
      var truffle = new dev.elide.truffledata.json.TruffleSink(new dev.elide.truffledata.json.TruffleSink.KeyCache());
      session.parse(first, truffle);
      assertEquals(
          com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
              "retained text longer than inline storage", com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16),
          ((Map<?, ?>) java.util.Objects.requireNonNull(truffle.result()))
              .get(
                  com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
                      "note", com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16)));
      // Invalid input publishes nothing and leaves the session reusable.
      for (String bad : new String[] {"[1,]", "{\"a\":1,\"a\":2}", "[\"\\q\"]", "01"}) {
        assertThrows(IllegalArgumentException.class, () -> session.parse(utf8(bad), new dev.elide.truffledata.json.JavaSink()));
        assertThrows(IllegalArgumentException.class, () -> session.decode(utf8(bad)));
      }
      assertThrows(IllegalArgumentException.class, () -> session.validate(new byte[0]));
      session.validate(first);
      // Guest strings (Task D3): ASCII, Latin-1 and UTF-16 storage, and UTF-8 TruffleStrings.
      for (String source :
          new String[] {"{\"id\":1}", "{\"café\":\"ÿ\"}", "{\"clé\":\"中 😀\"}"}) {
        Object expected;
        var fromBytes = new dev.elide.truffledata.json.JavaSink();
        session.parse(utf8(source), fromBytes);
        expected = tree(fromBytes);
        for (var encoding :
            new com.oracle.truffle.api.strings.TruffleString.Encoding[] {
              com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16,
              com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8
            }) {
          var guest = new dev.elide.truffledata.json.JavaSink();
          session.parse(com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(source, encoding), guest);
          assertEquals(expected, tree(guest));
        }
      }
      assertThrows(
          IllegalArgumentException.class,
          () ->
              session.parse(
                  com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
                      "[\"\ud800\"]", com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16),
                  new dev.elide.truffledata.json.JavaSink()));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              session.parse(
                  com.oracle.truffle.api.strings.TruffleString.fromByteArrayUncached(
                      new byte[] {'[', '"', (byte) 0xff, '"', ']'},
                      com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8,
                      false),
                  new dev.elide.truffledata.json.JavaSink()));
      // Native segments: borrowed for the call and copied natively; heap, closed and other
      // threads' segments are rejected before entry.
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment native_ = arena.allocate(first.length + 8);
        MemorySegment.copy(first, 0, native_, ValueLayout.JAVA_BYTE, 0, first.length);
        var fromSegment = new dev.elide.truffledata.json.JavaSink();
        session.parse(native_, first.length, fromSegment);
        assertEquals(1L, tree(fromSegment).get("id"));
        assertThrows(
            IllegalArgumentException.class,
            () -> session.parse(MemorySegment.ofArray(first), first.length, new dev.elide.truffledata.json.JavaSink()));
        assertThrows(
            IllegalArgumentException.class,
            () -> session.parse(native_, native_.byteSize() + 1, new dev.elide.truffledata.json.JavaSink()));
        assertThrows(
            IllegalArgumentException.class, () -> session.parse(native_, 0, new dev.elide.truffledata.json.JavaSink()));
      }
      MemorySegment closed;
      try (Arena arena = Arena.ofConfined()) {
        closed = arena.allocate(8);
      }
      assertThrows(IllegalStateException.class, () -> session.parse(closed, 2, new dev.elide.truffledata.json.JavaSink()));
      var other = new MemorySegment[1];
      Thread allocator = new Thread(() -> other[0] = Arena.ofConfined().allocate(8));
      allocator.start();
      allocator.join();
      assertThrows(IllegalStateException.class, () -> session.parse(other[0], 2, new dev.elide.truffledata.json.JavaSink()));
      // Sessions are confined to their opening thread.
      var failure = new Throwable[1];
      Thread intruder =
          new Thread(
              () -> {
                try {
                  session.parse(first, new dev.elide.truffledata.json.JavaSink());
                } catch (Throwable expected) {
                  failure[0] = expected;
                }
              });
      intruder.start();
      intruder.join();
      assertInstanceOf(IllegalStateException.class, failure[0]);
      // Default sessions decode inputs of at most 4 KiB through bounded critical calls (Task E2).
      assertTrue(session.criticalDecodes() > 0);
    } finally {
      session.close();
    }
    session.close();
    assertThrows(IllegalStateException.class, () -> session.parse(first, new dev.elide.truffledata.json.JavaSink()));
    assertThrows(IllegalStateException.class, () -> session.criticalDecodes());
    // Limits apply per document.
    try (JsonSession limited =
        JsonJvm.openSession(library(), new dev.elide.truffledata.json.JsonLimits(8, 1 << 20, 1000, 1 << 20, 128))) {
      limited.validate(utf8("[1,2]"));
      assertThrows(IllegalArgumentException.class, () -> limited.validate(utf8("[1,2,3,4]")));
    }
    assertEquals("retained text longer than inline storage", kept.root().member("note").stringValue());
    kept.close();
    assertThrows(IllegalStateException.class, () -> kept.root());
  }

  // ---- JVM critical decode (performance plan Task E2) ----

  private static long collections() {
    long total = 0;
    for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans())
      total += Math.max(0, gc.getCollectionCount());
    return total;
  }

  private static double bestNanos(Runnable body, int reps) {
    double best = Double.MAX_VALUE;
    for (int round = 0; round < 5; round++) {
      long start = System.nanoTime();
      for (int i = 0; i < reps; i++) body.run();
      best = Math.min(best, (System.nanoTime() - start) / (double) reps);
    }
    return best;
  }

  @Test
  void jvmCriticalDecodeIsSizeGated() throws Exception {
    byte[] limit = utf8("[" + " ".repeat(JsonSession.CRITICAL_LIMIT - 2) + "]");
    byte[] over = utf8("[" + " ".repeat(JsonSession.CRITICAL_LIMIT - 1) + "]");
    assertEquals(4096, limit.length);
    byte[] small =
        utf8(
            "{\"id\":1007,\"name\":\"item-1\",\"price\":1.25,\"active\":false,\"tags\":[\"t0\",\"t1\"],"
                + "\"dims\":{\"w\":3,\"h\":1.5,\"unit\":\"cm\"},\"scores\":[1,2,3,4],"
                + "\"note\":\"line \\\"1\\\" with \\\\ escapes\\n\\t\\u00e9 and \\ud83d\\ude00 tail\",\"parent\":null}");
    try (JsonSession session = JsonJvm.openSession(library(), dev.elide.truffledata.json.JsonLimits.DEFAULT);
        JsonSession normal = JsonJvm.openSession(library(), dev.elide.truffledata.json.JsonLimits.DEFAULT, 0)) {
      session.validate(limit);
      assertEquals(1, session.criticalDecodes(), "4,096 bytes take the critical path");
      session.validate(over);
      assertEquals(1, session.criticalDecodes(), "4,097 bytes take the normal path");
      // Same results either way, borrowed and retained.
      var a = new dev.elide.truffledata.json.JavaSink();
      session.parse(small, a);
      var b = new dev.elide.truffledata.json.JavaSink();
      normal.parse(small, b);
      assertEquals(tree(b), tree(a));
      try (JsonMessage kept = session.decode(small)) {
        assertEquals(1007L, kept.root().member("id").longValue());
      }
      assertEquals(0, normal.criticalDecodes());
      assertThrows(IllegalArgumentException.class, () -> session.validate(utf8("[1,]")));
      // UTF-16 guest strings count their UTF-16 bytes against the limit.
      var guest = new dev.elide.truffledata.json.JavaSink();
      long before = session.criticalDecodes();
      session.parse(
          com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
              "{\"中\":1}", com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16),
          guest);
      assertEquals(before + 1, session.criticalDecodes());
      assertEquals(1L, tree(guest).get("中"));
      // A collector running on another thread does not deadlock against critical decodes.
      var stop = new java.util.concurrent.atomic.AtomicBoolean();
      var churned = new java.util.concurrent.atomic.AtomicLong();
      Thread churn =
          new Thread(
              () -> {
                Object[] keep = new Object[64];
                for (int i = 0; !stop.get(); i++) {
                  keep[i & 63] = new byte[1 << 16];
                  churned.incrementAndGet();
                }
              });
      long gcBefore = collections();
      churn.start();
      long deadline = System.nanoTime() + 2_000_000_000L;
      long decodes = 0;
      while (System.nanoTime() < deadline) {
        session.parse(small, new dev.elide.truffledata.json.JavaSink());
        decodes++;
      }
      stop.set(true);
      churn.join(10_000);
      assertFalse(churn.isAlive());
      assertTrue(decodes > 0 && churned.get() > 0);
      assertTrue(collections() > gcBefore, "the stress thread forced collections during critical decodes");
      // Diagnostic timing only (not evidence; recorded in the ledger): validate of the 210-byte
      // fixture document, critical against normal transitions, best of 5 rounds.
      double criticalNs = bestNanos(() -> session.validate(small), 20_000);
      double normalNs = bestNanos(() -> normal.validate(small), 20_000);
      System.out.printf(
          "JSON E2 diagnostic: %d B validate critical %.0f ns, normal %.0f ns (%d decodes under GC stress)%n",
          small.length, criticalNs, normalNs, decodes);
    }
    for (int bad : new int[] {-1, JsonSession.CRITICAL_LIMIT + 1})
      assertThrows(
          IllegalArgumentException.class,
          () -> JsonJvm.openSession(library(), dev.elide.truffledata.json.JsonLimits.DEFAULT, bad));
  }
}
