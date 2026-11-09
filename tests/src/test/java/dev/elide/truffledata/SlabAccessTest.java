package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.generated.TruffleDataContracts;
import dev.elide.truffledata.protobuf.*;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SlabAccessTest {
  @Test
  void transferredCapacityAndExtentAreValidatedAndAccounted() {
    var plan =
        TruffleDataContracts.create(null, Limits.DEFAULT, 16, 65536)
            .type("truffledata.lossless.Sample");
    byte[] encoded = SlabCodec.decode(plan.layout(), new byte[] {8, 42});
    int extent = encoded.length;
    byte[] transferred = Arrays.copyOf(encoded, extent + 32);
    var message =
        SlabMessage.takeOwnership(plan, transferred, extent, new SlabLimits(16, extent + 32));
    // Exclusive ownership has transferred: do not retain or mutate transferred after this point.
    transferred = null;
    assertEquals(extent, message.slabBytes());
    assertEquals(extent + 32, message.retainedSlabBytes());
    assertArrayEquals(encoded, message.slabCopy());
    assertEquals(42L, message.getLong(0));
    message.close();
    assertSame(message, message.snapshot());
    assertThrows(
        ContractViolation.class,
        () ->
            SlabMessage.takeOwnership(
                plan, Arrays.copyOf(encoded, extent + 32), extent, new SlabLimits(16, extent)));
    for (int invalid : new int[] {-1, 0, extent - 1, extent + 1})
      assertThrows(
          ContractViolation.class,
          () ->
              SlabMessage.takeOwnership(plan, encoded.clone(), invalid, new SlabLimits(16, 65536)));
    byte[] malformed = encoded.clone();
    SlabFormat.putInt(malformed, SlabFormat.HEADER_HANDLES, Integer.MAX_VALUE);
    assertThrows(
        ContractViolation.class,
        () -> SlabMessage.takeOwnership(plan, malformed, extent, new SlabLimits(16, 65536)));
    byte[] shared = encoded.clone();
    var defensive =
        SlabMessage.copyOf(plan, java.nio.ByteBuffer.wrap(shared), new SlabLimits(16, 65536));
    Arrays.fill(shared, (byte) 0);
    assertEquals(42L, defensive.getLong(0));
  }

  @Test
  void lazyArraysChildrenAndStringsAreDurableAndCached() throws Exception {
    var plan =
        TruffleDataContracts.create(null, Limits.DEFAULT, 16, 65536)
            .type("truffledata.lossless.Sample");
    var original =
        plan.parseText(
            "count: 42 blob: 'abc' text: 'a😀' numbers: [-1, 2] "
                + "child { a: 7 b: 'nested' } children { key: 'k' value { a: 8 } } "
                + "f: 1.5 d: 2.5 flag: true u64: 18446744073709551615");
    byte[] wire = plan.encode(original);
    var row = plan.decodeSlab(wire);
    Arrays.fill(wire, (byte) 0);
    assertSame(row, row.snapshot());
    assertSame(row, Values.normalize(row));
    assertSame(row.get(2), row.get(2));
    assertSame(row.get(7), row.get(7));
    assertSame(row.get(8), row.get(8));
    assertSame(row.get(14), row.get(14));
    assertEquals(3, row.stringLength(14));
    assertEquals(3, row.accessor(14).length(row));
    assertEquals(2, row.getSize(8));
    assertEquals(2, row.accessor(8).length(row));
    assertEquals(3, row.accessor(2).length(row));
    var items = (OwnedArray) row.get(8);
    var bytes = (OwnedArray) row.get(2);
    assertSame(items, Values.normalize(items));
    assertFalse(items.isBytes());
    assertTrue(bytes.isBytes());
    assertEquals(-1L, items.get(0));
    assertEquals(97L, bytes.get(0));
    byte[] copied = new byte[3];
    bytes.copyBytesTo(copied, 0);
    assertArrayEquals(new byte[] {'a', 'b', 'c'}, copied);
    for (Object array : new Object[] {items, bytes}) {
      var io = InteropLibrary.getUncached(array);
      assertTrue(io.hasArrayElements(array));
      assertTrue(io.isArrayElementReadable(array, 0));
      assertFalse(io.isArrayElementReadable(array, -1));
      assertEquals(((OwnedArray) array).size(), io.getArraySize(array));
      assertEquals(((OwnedArray) array).get(0), io.readArrayElement(array, 0));
      assertThrows(InvalidArrayIndexException.class, () -> io.readArrayElement(array, -1));
    }
    row.close();
    assertEquals(42L, CompletableFuture.supplyAsync(() -> row.getLong(0)).get());
    assertArrayEquals(plan.encode(original), row.encode());
    assertArrayEquals(row.encode(), plan.encode(row));
    assertEquals(row.slabBytes(), row.slabCopy().length);
    var copiedSlab =
        SlabMessage.copyOf(
            plan, java.nio.ByteBuffer.wrap(row.slabCopy()), new SlabLimits(32, 65536));
    assertArrayEquals(row.encode(), copiedSlab.encode());
    assertArrayEquals(new byte[0], row.unknownFieldBytes());
    byte[] slabCopy = row.slabCopy();
    Arrays.fill(slabCopy, (byte) 0);
    assertEquals(42L, row.getLong(0));
    var empty = plan.decodeSlab(new byte[0]);
    assertEquals(0L, empty.getLong(0));
    assertEquals(0.0, empty.getDouble(11));
    assertFalse(empty.getBoolean(13));
    assertEquals(0, empty.getSize(8));
    assertEquals(0, empty.accessor(2).length(empty));
    assertEquals(0, empty.stringLength(14));
    assertThrows(ContractViolation.class, () -> row.getSize(0));
    assertThrows(ContractViolation.class, () -> row.stringLength(0));
    assertThrows(ContractViolation.class, () -> row.getLong(8));
    assertThrows(ContractViolation.class, () -> row.getDouble(8));
    assertThrows(ContractViolation.class, () -> row.getBoolean(8));
    assertThrows(UnexpectedResultException.class, () -> row.accessor(14).readLong(row));
    assertThrows(UnexpectedResultException.class, () -> row.accessor(14).readDouble(row));
    assertThrows(UnexpectedResultException.class, () -> row.accessor(14).readBoolean(row));
  }

  /**
   * Generated slab rows (plan 2026-11-08-beat-stock-svm WS1): one final class per (type,
   * root-or-child) of an unprojected owning slab, whose accessors guard with the exact class and
   * the plan schema. Record offsets depend on the type alone, so a child type reached through
   * different root layouts shares its class, its record layout and its accessors; the root of that
   * type, a projected slab's generic rows and a runtime plan's rows are other layouts and are
   * rejected. Every generated accessor agrees with the generic slot accessor and the public reads.
   */
  @Test
  void generatedSlabRowsAreExactClassesPerTypeAndRejectOtherLayouts() throws Exception {
    var model = TruffleDataContracts.create(null, Limits.DEFAULT, 16, 65536);
    var sample = model.type("truffledata.lossless.Sample");
    var childPlan = model.type("truffledata.lossless.Sample.Child");
    byte[] wire =
        sample.encode(
            sample.parseText(
                "count: 1 text: 'λ' child { a: 7 b: 'x' child { a: 9 b: 'yy' } }"
                    + " children { key: 'k' value { a: 8 } }"));
    var root = sample.decodeSlab(wire);
    assertEquals("SlabMessage_truffledata$lossless$Sample", root.getClass().getSimpleName());
    var child = (SlabRow) root.get(7);
    assertEquals("SlabRow_truffledata$lossless$Sample$Child", child.getClass().getSimpleName());
    var grandchild = (SlabRow) child.get(2);
    assertSame(child.getClass(), grandchild.getClass());
    // The child type as its own root: another root layout, the same child class underneath.
    var standalone =
        childPlan.decodeSlab(childPlan.encode(childPlan.parseText("a: 7 b: 'x' child { a: 9 }")));
    assertEquals(
        "SlabMessage_truffledata$lossless$Sample$Child", standalone.getClass().getSimpleName());
    var standaloneChild = (SlabRow) standalone.get(2);
    assertSame(child.getClass(), standaloneChild.getClass());
    assertSame(child.schema(), standaloneChild.schema());
    Layout sampleLayout = sample.layout(), childLayout = childPlan.layout();
    assertNotSame(sampleLayout, childLayout);
    int childType = -1;
    for (int t = 0; t < sampleLayout.types(); t++)
      if (sampleLayout.plan(t) == childPlan) childType = t;
    assertTrue(childType > 0, "child type index in the Sample layout");
    for (int ordinal = 0; ordinal < childPlan.size(); ordinal++) {
      assertEquals(sampleLayout.slot(childType, ordinal), childLayout.slot(0, ordinal));
      assertEquals(sampleLayout.kind(childType, ordinal), childLayout.kind(0, ordinal));
    }
    assertEquals(sampleLayout.recordBytes(childType), childLayout.recordBytes(0));
    // Projected slabs keep the generic classes; runtime plans have no generated classes.
    var projected =
        SlabMessage.decode(
            sample,
            wire,
            0,
            wire.length,
            SlabLimits.forInput(sampleLayout, wire.length),
            Projection.all(sample));
    assertSame(SlabMessage.class, projected.getClass());
    var projectedChild = (SlabRow) projected.get(7);
    assertSame(SlabRow.class, projectedChild.getClass());
    var runtime =
        new ProtoPlan.Model(null, java.util.List.of(childPlan.definition()), Limits.DEFAULT, 16, 65536)
            .type("truffledata.lossless.Sample.Child");
    var runtimeRoot = runtime.decodeSlab(runtime.encode(runtime.parseText("a: 7 b: 'x'")));
    assertSame(SlabMessage.class, runtimeRoot.getClass());
    Object[] rows = {
      child, grandchild, standaloneChild, standalone, root, projected, projectedChild, runtimeRoot
    };
    for (int ordinal = 0; ordinal < childPlan.size(); ordinal++) {
      Accessor exact = child.accessor(ordinal);
      assertSame(exact, standaloneChild.accessor(ordinal));
      assertSame(child.getClass(), exact.exactRowClass);
      assertTrue(exact.accepts(child) && exact.accepts(grandchild));
      assertTrue(exact.accepts(standaloneChild));
      // The root of the same type, projected rows and runtime rows are other layouts.
      Accessor rootExact = standalone.accessor(ordinal);
      assertNotSame(exact, rootExact);
      assertSame(standalone.getClass(), rootExact.exactRowClass);
      for (Object other : new Object[] {standalone, root, projected, projectedChild, runtimeRoot}) {
        assertFalse(exact.accepts(other), other.getClass() + " at " + ordinal);
        assertThrows(ContractViolation.class, () -> exact.read(other));
      }
      assertFalse(rootExact.accepts(child));
      assertFalse(runtimeRoot.accessor(ordinal).accepts(standalone));
      assertFalse(projectedChild.accessor(ordinal).accepts(runtimeRoot));
      for (Object candidate : rows)
        assertEquals(exact.accepts(candidate), Accessor.bound(exact, candidate));
      // The generic slot of the shared layout still accepts every row of its (layout, type); the
      // generated accessor is the strict side, and both agree on every value.
      Accessor generic = projectedChild.accessor(ordinal);
      assertNull(generic.exactRowClass);
      assertTrue(generic.accepts(child));
      // The generic slot is bound to its layout, the generated accessor to the type: the same
      // child type under the Child-root layout is rejected by one and read by the other.
      assertFalse(generic.accepts(standaloneChild));
      assertThrows(ContractViolation.class, () -> generic.read(standaloneChild));
      assertEquals(
          outcome(() -> standaloneChild.get(exact.ordinal())),
          outcome(() -> exact.read(standaloneChild)));
      for (SlabRow r : new SlabRow[] {child, grandchild}) {
        assertEquals(outcome(() -> r.get(exact.ordinal())), outcome(() -> exact.read(r)));
        assertEquals(outcome(() -> generic.read(r)), outcome(() -> exact.read(r)));
        assertEquals(outcome(() -> generic.readLong(r)), outcome(() -> exact.readLong(r)));
        assertEquals(outcome(() -> generic.readDouble(r)), outcome(() -> exact.readDouble(r)));
        assertEquals(outcome(() -> generic.readBoolean(r)), outcome(() -> exact.readBoolean(r)));
        assertEquals(outcome(() -> generic.length(r)), outcome(() -> exact.length(r)));
        assertEquals(r.has(exact.ordinal()), exact.present(r));
      }
    }
    // Root reads agree with the generic root for every Sample ordinal, present or absent.
    for (int ordinal = 0; ordinal < sample.size(); ordinal++) {
      int o = ordinal;
      Accessor exact = root.accessor(o), generic = projected.accessor(o);
      assertSame(root.getClass(), exact.exactRowClass);
      assertEquals(outcome(() -> projected.get(o)), outcome(() -> root.get(o)));
      assertEquals(outcome(() -> projected.getLong(o)), outcome(() -> root.getLong(o)));
      assertEquals(outcome(() -> projected.getDouble(o)), outcome(() -> root.getDouble(o)));
      assertEquals(outcome(() -> projected.getBoolean(o)), outcome(() -> root.getBoolean(o)));
      assertEquals(outcome(() -> projected.getSize(o)), outcome(() -> root.getSize(o)));
      assertEquals(outcome(() -> projected.stringLength(o)), outcome(() -> root.stringLength(o)));
      assertEquals(outcome(() -> generic.read(projected)), outcome(() -> exact.read(root)));
      assertEquals(outcome(() -> generic.readLong(projected)), outcome(() -> exact.readLong(root)));
      assertEquals(outcome(() -> generic.length(projected)), outcome(() -> exact.length(root)));
      assertEquals(projected.has(o), root.has(o));
    }
    assertThrows(IndexOutOfBoundsException.class, () -> root.get(sample.size()));
    assertThrows(IndexOutOfBoundsException.class, () -> root.has(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> root.accessor(sample.size()));
  }

  /** A read's value, or the class of the exception it throws; structural values compare by content. */
  private static Object outcome(java.util.concurrent.Callable<Object> read) {
    try {
      Object value = read.call();
      if (value instanceof OwnedArray array) {
        var items = new Object[array.size()];
        for (int i = 0; i < items.length; i++) { int at = i; items[i] = outcome(() -> array.get(at)); }
        return java.util.Arrays.asList(items);
      }
      if (value instanceof SlabRow row) return row.plan().name() + "@" + row.schema().size();
      if (value instanceof ExactNumber number) return number.decimal().toPlainString();
      return value;
    } catch (Exception e) {
      return e.getClass();
    }
  }
}
