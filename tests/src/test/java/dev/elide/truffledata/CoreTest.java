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
