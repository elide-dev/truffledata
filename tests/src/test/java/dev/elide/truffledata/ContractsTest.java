package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.*;
import java.math.BigDecimal;
import java.util.*;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

class ContractsTest {
  @Test
  void directStaticIngestionPreservesBudgetsAndFailureIdentity() throws Exception {
    try (Context context = Context.newBuilder("td-test").build()) {
      context.initialize("td-test");
      context.enter();
      try {
        Schema schema =
            Schema.of(
                Field.integer("id", 16, true, false, false),
                new Field("text", Field.Kind.STRING, false, false));
        StaticLayout layout =
            new StaticLayout(TestLanguage.current().language(), schema, new Limits(1, 2, 2, 6));
        PayloadBudget parent = new PayloadBudget(new Limits(2, 2, 4, 12));
        int[] calls = {0};
        StaticLayout.Source<Exception> source =
            column -> {
              assertEquals(calls[0]++ % 2, column);
              return column == 0 ? 1000L : "abc";
            };
        var data = DataLibrary.factory().getUncached();
        var first = layout.read(source, parent);
        layout.read(source, parent);
        assertEquals(4, calls[0]);
        assertThrows(ContractViolation.class, () -> layout.read(source, parent));
        assertEquals(4, calls[0], "Exhausted parent rejects before reading fields");
        assertEquals(1000L, data.readLong(first, 0));
        assertEquals("abc", data.read(first, 1));
        assertThrows(
            ContractViolation.class,
            () -> layout.read(column -> column == 0 ? 40000L : "abc", null));
        assertThrows(
            ContractViolation.class,
            () -> layout.read(column -> column == 0 ? 1000L : "abcd", null));
        assertThrows(
            ContractViolation.class, () -> layout.read(column -> column == 0 ? 1000L : null, null));
        Exception failure = new Exception("source failed");
        assertSame(
            failure,
            assertThrows(
                Exception.class,
                () ->
                    layout.read(
                        column -> {
                          throw failure;
                        },
                        null)));
      } finally {
        context.leave();
      }
    }
  }

  @Test
  void staticWidthsPresenceAndLengthFacts() throws Exception {
    try (Context context = Context.newBuilder("td-test").build()) {
      context.initialize("td-test");
      context.enter();
      try {
        Field text =
            new Field(
                "text",
                Field.Kind.STRING,
                false,
                false,
                Constraints.NONE.withLength(
                    new Constraints.Length(3, 3, Constraints.LengthUnit.UTF16)));
        Schema schema =
            Schema.of(
                Field.integer("n", 16, true, false, false),
                new Field(
                    "f", Field.Kind.FLOAT64, false, false, Constraints.NONE.withFloatBits(32)),
                new Field("b", Field.Kind.BOOLEAN, false, false),
                text,
                Field.integer("maybe", 32, true, true, true));
        StaticLayout layout =
            new StaticLayout(TestLanguage.current().language(), schema, Limits.DEFAULT);
        assertEquals(StaticLayout.Storage.SHORT, layout.storageKind(0));
        assertEquals(StaticLayout.Storage.FLOAT, layout.storageKind(1));
        assertEquals(StaticLayout.Storage.BOOLEAN, layout.storageKind(2));
        assertEquals(StaticLayout.Storage.INT, layout.storageKind(4));
        var row = layout.create(1000L, 1.5, true, "abc", Missing.INSTANCE);
        var data = DataLibrary.factory().getUncached();
        assertEquals(1000L, data.readLong(row, 0));
        assertEquals(1.5, data.readDouble(row, 1));
        assertTrue(data.readBoolean(row, 2));
        assertFalse(data.isPresent(row, 4));
        assertEquals(
            DataNull.INSTANCE,
            data.read(layout.create(1000L, 1.5, true, "abc", DataNull.INSTANCE), 4));
        assertThrows(ContractViolation.class, () -> layout.create(40000L, 1.5, true, "abc", 1L));
        assertThrows(ContractViolation.class, () -> layout.create(1000L, 0.1, true, "abc", 1L));
        assertThrows(ContractViolation.class, () -> layout.create(1000L, 1.5, true, "abcd", 1L));
        assertThrows(ContractViolation.class, () -> layout.create(1000L, 1.5, true, "ab", 1L));
      } finally {
        context.leave();
      }
    }
  }

  @Test
  void arrayElementsDecimalAndUnsignedBounds() {
    Field numbers =
        new Field(
            "items",
            Field.Kind.ARRAY,
            false,
            false,
            Constraints.NONE
                .withLength(new Constraints.Length(2, 2, Constraints.LengthUnit.ELEMENTS))
                .withElement(Field.integer("item", 8, false, false, false)));
    Values.validate(numbers, new DataArray(new Object[] {0L, 255L}));
    assertThrows(
        ContractViolation.class,
        () -> Values.validate(numbers, new DataArray(new Object[] {0L, 256L})));
    assertThrows(
        ContractViolation.class,
        () -> Values.validate(numbers, new DataArray(new Object[] {0L, DataNull.INSTANCE})));
    Field money =
        new Field(
            "money",
            Field.Kind.EXACT,
            false,
            false,
            Constraints.NONE.withDecimal(new Constraints.Decimal(5, 2)));
    Values.validate(money, new ExactNumber(new BigDecimal("999.99")));
    assertThrows(
        ContractViolation.class,
        () -> Values.validate(money, new ExactNumber(new BigDecimal("1000.00"))));
    assertThrows(
        ContractViolation.class,
        () -> Values.validate(money, new ExactNumber(new BigDecimal("1.001"))));
    Field unsigned = Field.integer("u", 64, false, false, false);
    Values.validate(unsigned, Values.normalize(new java.math.BigInteger("18446744073709551615")));
    assertThrows(
        ContractViolation.class,
        () ->
            Values.validate(
                unsigned, Values.normalize(new java.math.BigInteger("18446744073709551616"))));
    assertThrows(
        ContractViolation.class,
        () -> Values.validate(unsigned, Values.normalize(new BigDecimal("-1"))));
  }

  @Test
  void integerRangesAreEnforcedAndSelectNarrowStorage() {
    Field age =
        Field.requiredLong("age")
            .withConstraints(
                Constraints.NONE.withIntegerRange(new Constraints.IntegerRange(0, 120)));
    Values.validate(age, 120L);
    assertThrows(ContractViolation.class, () -> Values.validate(age, -1L));
    assertThrows(ContractViolation.class, () -> Values.validate(age, 121L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Field.integer("x", 8, true, false, false)
                .withConstraints(
                    Constraints.NONE
                        .withInteger(new Constraints.IntegerWidth(8, true))
                        .withIntegerRange(new Constraints.IntegerRange(200, 300))));
    try (Context context = Context.newBuilder("td-test").build()) {
      context.initialize("td-test");
      context.enter();
      try {
        var layout =
            new StaticLayout(TestLanguage.current().language(), Schema.of(age), Limits.DEFAULT);
        assertEquals(StaticLayout.Storage.BYTE, layout.storageKind(0));
        assertEquals(120L, ReadLongNodeGen.getUncached().execute(layout.create(120L), 0));
      } finally {
        context.leave();
      }
    }
    DynamicObject object = new DynamicObject(Shape.newBuilder().build()) {};
    var put = DynamicObject.PutNode.getUncached();
    put.execute(object, "age", 120L);
    var view = new DynamicRecord(object, Schema.of(age));
    var read = new CoreTest.ReadRoot();
    assertEquals(120L, read.getCallTarget().call(view));
    put.execute(object, "age", 121L);
    assertThrows(ContractViolation.class, () -> read.getCallTarget().call(view));
  }

  @Test
  void unicodeLengthUnitsAndNestedPayloadBudgets() {
    String text = "🦉x";
    Field unicode =
        new Field(
            "s",
            Field.Kind.STRING,
            false,
            false,
            Constraints.NONE.withLength(
                new Constraints.Length(2, 2, Constraints.LengthUnit.CODE_POINTS)));
    Values.validate(unicode, text);
    assertThrows(
        ContractViolation.class,
        () ->
            Values.validate(
                unicode.withConstraints(
                    Constraints.NONE.withLength(
                        new Constraints.Length(2, 2, Constraints.LengthUnit.UTF16))),
                text));
    Schema schema = Schema.of(new Field("items", Field.Kind.ARRAY, false, false));
    DataArray nested = new DataArray(new Object[] {new DataArray(new Object[] {"abc"})});
    assertThrows(
        ContractViolation.class,
        () ->
            Batch.copyOf(
                schema,
                Collections.singletonList(new Object[] {nested}),
                new Limits(1, 1, 2, 100)));
    assertThrows(
        ContractViolation.class,
        () ->
            Batch.copyOf(
                schema, Collections.singletonList(new Object[] {nested}), new Limits(1, 1, 10, 4)));
    assertThrows(
        ContractViolation.class,
        () ->
            Values.validate(
                new Field(
                    "n",
                    Field.Kind.EXACT,
                    false,
                    false,
                    Constraints.NONE.withDecimal(new Constraints.Decimal(5, 2))),
                new ExactNumber(new BigDecimal("1e1000000000"))));
  }

  @Test
  void cachedFactsCannotHideLiveMutation() {
    DynamicObject object = new DynamicObject(Shape.newBuilder().build()) {};
    var put = DynamicObject.PutNode.getUncached();
    put.execute(object, "n", 127L);
    var number = new DynamicRecord(object, Schema.of(Field.integer("n", 8, true, false, false)));
    var reader = new CoreTest.ReadRoot();
    for (int i = 0; i < 10000; i++) assertEquals(127L, reader.getCallTarget().call(number));
    put.execute(object, "n", 128L);
    assertThrows(ContractViolation.class, () -> reader.getCallTarget().call(number));
    put.execute(object, "s", "abc");
    Field text =
        new Field(
            "s",
            Field.Kind.STRING,
            false,
            false,
            Constraints.NONE.withLength(
                new Constraints.Length(3, 3, Constraints.LengthUnit.UTF16)));
    var row = new DynamicRecord(object, Schema.of(text));
    var root =
        new RootNode(null) {
          @Child private ReadLengthNode length = ReadLengthNode.create();

          @Override
          public Object execute(VirtualFrame frame) {
            return length.execute(frame.getArguments()[0], 0);
          }
        };
    for (int i = 0; i < 10000; i++) assertEquals(3, root.getCallTarget().call(row));
    put.execute(object, "s", "abcd");
    assertThrows(ContractViolation.class, () -> root.getCallTarget().call(row));
    put.execute(object, "s", DataNull.INSTANCE);
    assertThrows(ContractViolation.class, () -> root.getCallTarget().call(row));
  }
}
