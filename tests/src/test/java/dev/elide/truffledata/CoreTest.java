package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.object.*;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CoreTest {
  @Test
  void listSnapshotsOwnAndValidateTheirElements() {
    var source = new java.util.ArrayList<Object>(java.util.List.of(7L, "value"));
    var snapshot = DataArray.copyOf(source);
    source.set(0, 9L);
    source.clear();
    assertEquals(2, snapshot.size());
    assertEquals(7L, snapshot.get(0));
    assertEquals("value", snapshot.get(1));
    assertEquals(0, DataArray.copyOf(java.util.List.of()).size());
    assertThrows(ContractViolation.class, () -> DataArray.copyOf(java.util.List.of(new Object())));
  }

  @Test
  void cumulativeBudgetSharesThePayloadWalkAndDoesNotReset() {
    var nested = new UncheckedRow(new Field("s", Field.Kind.STRING, false, false), "abc");
    var schema = Schema.of(new Field("row", Field.Kind.RECORD, false, false));
    var parent = new PayloadBudget(new Limits(10, 10, 10, 6));
    var source =
        new Batch.Source<RuntimeException>() {
          public boolean next() {
            return true;
          }

          public Object read(int column) {
            return nested;
          }
        };
    assertEquals(1, Batch.read(schema, 1, Limits.DEFAULT, source, parent).size());
    assertEquals(1, nested.reads, "Parent accounting must not traverse the row again");
    assertThrows(
        ContractViolation.class, () -> Batch.read(schema, 1, Limits.DEFAULT, source, parent));
    assertEquals(2, nested.reads);
    var cells = new PayloadBudget(new Limits(1, 1, Long.MAX_VALUE, 0));
    cells.cells(Long.MAX_VALUE);
    assertThrows(ContractViolation.class, () -> cells.cells(1));
  }

  @Test
  void directBatchFillingPreservesRetentionNullityAndSourcePosition() throws Exception {
    Schema schema =
        Schema.of(Field.requiredLong("n"), new Field("s", Field.Kind.STRING, true, false));
    var source =
        new Batch.Source<Exception>() {
          int row = -1;
          int reads;

          public boolean next() {
            return ++row < 3;
          }

          public Object read(int column) {
            reads++;
            return column == 0 ? (Object) (long) row : row == 1 ? DataNull.INSTANCE : "ok";
          }
        };
    Batch first = Batch.read(schema, 2, Limits.DEFAULT, source);
    assertEquals(1, source.row, "Full batches must not consume a lookahead row");
    Batch last = Batch.read(schema, 2, Limits.DEFAULT, source);
    assertEquals(2, first.size());
    assertEquals(1, last.size());
    assertEquals(6, source.reads);
    var data = DataLibrary.factory().getUncached();
    assertEquals(0L, data.readLong(first.row(0), 0));
    assertSame(DataNull.INSTANCE, data.read(first.row(1), 1));
    assertEquals(2L, data.readLong(last.row(0), 0));
    assertThrows(IndexOutOfBoundsException.class, () -> last.row(1));
  }

  @Test
  void directBatchFillingEnforcesStructuralPayloadAndValueContracts() {
    var source =
        new Batch.Source<RuntimeException>() {
          int advances;

          public boolean next() {
            advances++;
            return true;
          }

          public Object read(int column) {
            return "abc";
          }
        };
    Schema strings = Schema.of(new Field("s", Field.Kind.STRING, false, false));
    assertThrows(
        ContractViolation.class, () -> Batch.read(strings, 2, new Limits(1, 1, 1, 10), source));
    assertEquals(0, source.advances);
    assertThrows(
        ContractViolation.class, () -> Batch.read(strings, 1, new Limits(1, 1, 1, 5), source));
    assertThrows(
        ContractViolation.class,
        () -> Batch.read(Schema.of(Field.requiredLong("n")), 1, Limits.DEFAULT, source));
    var failure = new java.sql.SQLException("source failed");
    var broken =
        new Batch.Source<java.sql.SQLException>() {
          public boolean next() throws java.sql.SQLException {
            throw failure;
          }

          public Object read(int column) {
            throw new AssertionError("unreachable");
          }
        };
    assertSame(
        failure,
        assertThrows(
            java.sql.SQLException.class, () -> Batch.read(strings, 1, Limits.DEFAULT, broken)));
  }

  @com.oracle.truffle.api.library.ExportLibrary(DataLibrary.class)
  static final class UncheckedRow extends AbstractRow {
    final Object value;
    int reads;

    UncheckedRow(Field field, Object value) {
      super(Schema.of(field));
      this.value = value;
    }

    @com.oracle.truffle.api.library.ExportMessage
    Schema getSchema() {
      return descriptor;
    }

    @com.oracle.truffle.api.library.ExportMessage
    Object read(int index) {
      reads++;
      return value;
    }

    @com.oracle.truffle.api.library.ExportMessage
    boolean isPresent(int index) {
      return value != Missing.INSTANCE;
    }
  }

  @Test
  void strictAssertionsCatchBrokenExportsWithoutRereadingValues() throws Exception {
    assertTrue(
        Boolean.getBoolean("truffledata.strict"), "Correctness suite must enable strict mode");
    var data = DataLibrary.factory().getUncached();
    for (Object invalid : List.of(DataNull.INSTANCE, Missing.INSTANCE, "wrong type", 128L)) {
      var broken = new UncheckedRow(Field.integer("a", 8, true, false, false), invalid);
      assertThrows(AssertionError.class, () -> data.read(broken, 0));
      assertEquals(1, broken.reads);
      assertThrows(AssertionError.class, () -> data.readLong(broken, 0));
      assertEquals(2, broken.reads);
    }
    var valid = new UncheckedRow(Field.requiredLong("a"), 1000L);
    assertEquals(1000L, data.readLong(valid, 0));
    assertEquals(1, valid.reads);
    var length =
        new Field("a", Field.Kind.STRING, false, false)
            .withConstraints(
                Constraints.NONE.withLength(
                    new Constraints.Length(3, 3, Constraints.LengthUnit.UTF16)));
    var brokenLength = new UncheckedRow(length, "x");
    assertThrows(
        AssertionError.class, () -> ReadLengthNodeGen.getUncached().execute(brokenLength, 0));
    assertEquals(1, brokenLength.reads);
    var nullable =
        new UncheckedRow(new Field("a", Field.Kind.INT64, true, true), DataNull.INSTANCE);
    assertSame(DataNull.INSTANCE, data.read(nullable, 0));
    assertThrows(UnexpectedResultException.class, () -> data.readLong(nullable, 0));
  }

  @Test
  void compactBytesOwnTheirSnapshotAndPreserveUnsignedGuestValues() throws Exception {
    byte[] input = {0, 127, (byte) 128, (byte) 255};
    DataArray bytes = DataArray.copyOfBytes(input);
    input[3] = 0;
    assertTrue(bytes.isBytes());
    assertEquals(255L, bytes.get(3));
    assertEquals(128L, InteropLibrary.getUncached().readArrayElement(bytes, 2));
    assertThrows(InvalidArrayIndexException.class, () -> bytes.readArrayElement(4));
    var buffer = java.nio.ByteBuffer.wrap(input);
    buffer.position(1);
    buffer.limit(3);
    DataArray slice = DataArray.copyOfBytes(buffer);
    assertEquals(1, buffer.position());
    assertEquals(2, slice.size());
    input[1] = 0;
    assertEquals(127L, slice.get(0));
    new PayloadBudget(new Limits(1, 1, 4, 0)).accept(bytes);
    assertThrows(
        ContractViolation.class, () -> new PayloadBudget(new Limits(1, 1, 3, 0)).accept(bytes));
    var field =
        new Field("bytes", Field.Kind.ARRAY, false, false)
            .withConstraints(
                Constraints.NONE.withElement(Field.integer("byte", 8, true, false, false)));
    assertThrows(ContractViolation.class, () -> Values.validate(field, bytes));
    DataArray signed = DataArray.copyOfBytes(new byte[] {0, 127});
    assertSame(signed, Values.validate(field, signed));
    var narrow =
        field.withConstraints(
            Constraints.NONE.withElement(
                Field.requiredLong("byte")
                    .withConstraints(
                        Constraints.NONE.withIntegerRange(new Constraints.IntegerRange(1, 255)))));
    assertThrows(ContractViolation.class, () -> Values.validate(narrow, bytes));
    DataArray empty = DataArray.copyOfBytes(new byte[0]);
    assertSame(empty, Values.validate(narrow, empty));
  }

  @Test
  void compactBytesPreserveNestingBudgets() {
    Object compact = DataArray.copyOfBytes(new byte[] {1});
    Object boxed = new DataArray(new Object[] {1L});
    Field field =
        new Field("array", Field.Kind.ARRAY, false, false)
            .withConstraints(Constraints.NONE.withElement(Field.requiredLong("item")));
    for (int i = 0; i < 128; i++) {
      compact = new DataArray(new Object[] {compact});
      boxed = new DataArray(new Object[] {boxed});
      field =
          new Field("array", Field.Kind.ARRAY, false, false)
              .withConstraints(Constraints.NONE.withElement(field));
    }
    Field contract = field;
    for (Object value : new Object[] {compact, boxed}) {
      assertThrows(ContractViolation.class, () -> Values.validate(contract, value));
      assertThrows(ContractViolation.class, () -> new PayloadBudget(Limits.DEFAULT).accept(value));
    }
  }

  private static Batch.Row row(Schema schema, Object... values) {
    return Batch.copyOf(schema, Collections.singletonList(values), Limits.DEFAULT).row(0);
  }

  @Test
  void validatesAndRetainsSnapshot() throws Exception {
    Schema schema = Schema.of(Field.requiredLong("a"));
    Object[] input = {1000L};
    Batch.Row row = row(schema, input);
    input[0] = 9L;
    assertEquals(1000L, DataLibrary.factory().getUncached().readLong(row, 0));
    assertThrows(ContractViolation.class, () -> row(schema, DataNull.INSTANCE));
    assertThrows(ContractViolation.class, () -> row(schema, Missing.INSTANCE));
    assertThrows(ContractViolation.class, () -> row(schema, 1.5));
    assertThrows(ContractViolation.class, () -> row(schema, new BigInteger("9223372036854775808")));
  }

  @Test
  void absenceNullAndDuplicateLabels() throws Exception {
    Schema schema = Schema.of(new Field("a", Field.Kind.INT64, true, true));
    var nullRow = row(schema, DataNull.INSTANCE);
    var missingRow = row(schema, Missing.INSTANCE);
    InteropLibrary io = InteropLibrary.getUncached();
    assertTrue(io.isNull(io.readMember(nullRow, "a")));
    assertFalse(io.isMemberReadable(missingRow, "a"));
    assertThrows(UnknownIdentifierException.class, () -> io.readMember(missingRow, "a"));
    assertThrows(InvalidArrayIndexException.class, () -> io.readArrayElement(missingRow, 0));
    assertThrows(
        InvalidArrayIndexException.class, () -> io.readArrayElement(nullRow, Long.MAX_VALUE));
    var duplicate = row(Schema.of(Field.requiredLong("a"), Field.requiredLong("a")), 1L, 2L);
    assertFalse(io.isMemberReadable(duplicate, "a"));
    assertEquals(2L, io.readArrayElement(duplicate, 1));
  }

  @Test
  void picFallsBackCorrectlyBeyondBudget() {
    ReadRoot root = new ReadRoot();
    for (int i = 0; i < 10; i++) {
      var row = row(Schema.of(Field.requiredLong("a")), (long) i);
      assertEquals((long) i, root.getCallTarget().call(row));
    }
    assertTrue(NodeUtil.countNodes(root) <= 128, "Read PIC node budget exceeded");
    var specializations = com.oracle.truffle.api.dsl.Introspection.getSpecializations(root.read);
    for (var specialization : specializations) assertTrue(specialization.getInstances() <= 3);
    assertTrue(
        specializations.stream()
            .anyMatch(info -> info.getMethodName().equals("generic") && info.isActive()));
    var nullable = row(Schema.of(new Field("a", Field.Kind.INT64, true, false)), DataNull.INSTANCE);
    assertThrows(ContractViolation.class, () -> root.getCallTarget().call(nullable));
  }

  @Test
  void dynamicShapesAndTypeMutationCannotLeakStaleFacts() {
    DynamicObject object = new DynamicObject(Shape.newBuilder().build()) {};
    DynamicObject.PutNode put = DynamicObject.PutNode.getUncached();
    put.execute(object, "a", 1000L);
    DynamicRecord row = new DynamicRecord(object, Schema.of(Field.requiredLong("a")));
    ReadRoot root = new ReadRoot();
    for (int i = 0; i < 10000; i++) assertEquals(1000L, root.getCallTarget().call(row));
    put.execute(object, "extra", true);
    put.execute(object, "a", 2000L);
    assertEquals(2000L, root.getCallTarget().call(row));
    put.execute(object, "a", DataNull.INSTANCE);
    assertThrows(ContractViolation.class, () -> root.getCallTarget().call(row));
    put.execute(object, "a", 3.5);
    assertThrows(ContractViolation.class, () -> root.getCallTarget().call(row));
    put.execute(object, "a", 3000L);
    assertEquals(3000L, root.getCallTarget().call(row));
    DynamicObject.RemoveKeyNode.getUncached().execute(object, "a");
    assertThrows(ContractViolation.class, () -> root.getCallTarget().call(row));
  }

  @Test
  void structuralAndPayloadBudgetsFailClosed() {
    Schema schema = Schema.of(new Field("s", Field.Kind.STRING, false, false));
    assertThrows(
        ContractViolation.class,
        () ->
            Batch.copyOf(
                schema, Collections.singletonList(new Object[] {"abcd"}), new Limits(1, 1, 1, 2)));
    assertThrows(
        ContractViolation.class,
        () ->
            Batch.copyOf(
                schema, List.of(new Object[] {"a"}, new Object[] {"b"}), new Limits(1, 1, 1, 100)));
  }

  @Test
  void exactNumbersDoNotSilentlyRound() throws Exception {
    InteropLibrary io = InteropLibrary.getUncached();
    Object decimal = Values.normalize(new BigDecimal("0.1"));
    assertFalse(io.fitsInDouble(decimal));
    assertThrows(UnsupportedMessageException.class, () -> io.asDouble(decimal));
    Object unsigned = Values.normalize(new BigInteger("18446744073709551615"));
    assertFalse(io.fitsInLong(unsigned));
    assertEquals(new BigInteger("18446744073709551615"), io.asBigInteger(unsigned));
  }

  static final class ReadRoot extends RootNode {
    @Child private ReadLongNode read = ReadLongNode.create();

    ReadRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return read.execute(frame.getArguments()[0], 0);
    }
  }
}
