package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.ProtoPlan.Kind.*;
import static dev.elide.truffledata.protobuf.SlabFormat.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.OwnedArray;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectionTest {
  static ProtoPlan plan() {
    return LayoutTest.model(
            LayoutTest.type(
                "Projected",
                LayoutTest.scalar("id", 1, INT32, false),
                LayoutTest.scalar("name", 2, STRING, false),
                LayoutTest.message("child", 3, "Child", false),
                LayoutTest.message("children", 4, "Child", true),
                LayoutTest.scalar("blob", 5, BYTES, false)),
            LayoutTest.type(
                "Child",
                LayoutTest.scalar("value", 1, INT32, false),
                LayoutTest.scalar("text", 2, STRING, false),
                LayoutTest.message("next", 3, "Child", false)))
        .type("Projected");
  }

  static byte[] input() {
    var child = LayoutTest.wire().field(1, 42).string(2, "λ😀");
    return LayoutTest.wire()
        .field(1, 7)
        .string(2, "hidden")
        .message(3, child)
        .message(4, child)
        .message(4, child)
        .bytes(5, new byte[] {1, 2, 3, 4})
        .bytes();
  }

  static SlabMessage decode(ProtoPlan plan, byte[] input, Projection projection) {
    return SlabMessage.decode(plan, input, 0, input.length, new SlabLimits(16, 65536), projection);
  }

  @Test
  void boundSizeReadsSupportConcreteAndSlabBackedDurableArrays() {
    var plan = plan();
    var slab = SlabMessage.decode(plan, input());
    var row = plan.newBuilder().set(3, slab.get(3)).build();
    assertSame(slab.get(3), row.get(3));
    assertEquals(2, plan.getSize(row, 3));
    assertEquals(2, plan.getSize(slab, 3));
    assertEquals(0, plan.getSize(plan.newBuilder().build(), 3));
    assertThrows(ContractViolation.class, () -> plan.getSize(row, 0));
    assertThrows(ContractViolation.class, () -> plan().getSize(row, 3));
  }

  @Test
  void sparseReadsPreservePresenceAndCountsButRejectOmittedValues() throws Exception {
    var plan = plan();
    var input = input();
    var projection = Projection.builder(plan).select("id").build();
    var message = decode(plan, input, projection);
    assertEquals(7, message.getLong(0));
    assertTrue(message.has(1));
    assertTrue(message.has(2));
    assertEquals(2, message.getSize(3));
    var array = (OwnedArray) message.get(3);
    assertEquals(2, array.size());
    assertThrows(ContractViolation.class, () -> array.get(0));
    assertThrows(ContractViolation.class, () -> message.get(1));
    assertThrows(ContractViolation.class, () -> message.stringLength(1));
    assertThrows(ContractViolation.class, () -> message.accessor(1).length(message));
    assertThrows(ContractViolation.class, () -> message.get(2));
    assertThrows(ContractViolation.class, () -> message.getSize(4));
    assertThrows(ContractViolation.class, message::encode);
    assertTrue(message.slabBytes() < SlabMessage.decode(plan, input).slabBytes());
    assertEquals(0, message.retainedInputBytes());
    var imported =
        SlabMessage.copyProjectedOf(
            plan,
            ByteBuffer.wrap(message.slabCopy()),
            ByteBuffer.wrap(input),
            projection,
            new SlabLimits(16, 65536));
    assertEquals(2, imported.getSize(3));
    assertThrows(
        ContractViolation.class,
        () ->
            SlabMessage.copyOf(
                plan, ByteBuffer.wrap(message.slabCopy()), new SlabLimits(16, 65536)));
  }

  @Test
  void lazyChildrenAreFullCachedDurableAndBounded() throws Exception {
    var plan = plan();
    byte[] wire = input();
    var selection =
        Projection.builder(plan).select("id", "blob").build().lazyChildren().borrowBytes(2);
    byte[] original = wire.clone();
    var message = decode(plan, wire, selection);
    assertEquals(wire.length, message.retainedInputBytes());
    Arrays.fill(wire, (byte) 0); // No caller alias remains after an owning projected decode.
    int retained = message.retainedSlabBytes();
    var child = (SlabRow) message.get(2);
    assertEquals(42, child.getLong(0));
    assertEquals("λ😀", child.get(1));
    assertSame(child, message.get(2));
    assertTrue(message.retainedSlabBytes() > retained);
    var children = (OwnedArray) message.get(3);
    assertEquals("λ😀", ((SlabRow) children.get(1)).get(1));
    var blob = (OwnedArray) message.get(4);
    byte[] copied = new byte[4];
    blob.copyBytesTo(copied, 0);
    assertArrayEquals(new byte[] {1, 2, 3, 4}, copied);
    message.close();
    assertEquals(42, child.getLong(0));
    var imported =
        SlabMessage.copyProjectedOf(
            plan,
            ByteBuffer.wrap(message.slabCopy()),
            ByteBuffer.wrap(original),
            selection,
            new SlabLimits(16, 65536));
    Arrays.fill(original, (byte) 0);
    assertEquals("λ😀", ((SlabRow) imported.get(2)).get(1));
    var raw = message.slabCopy();
    int lazy = getInt(raw, ROOT + plan.layout().slot(0, 2)) & ~LAZY_CHILD;
    putInt(raw, lazy + 12, 0); // An attacker cannot reset absolute nesting depth.
    assertThrows(
        ContractViolation.class,
        () ->
            SlabMessage.copyProjectedOf(
                plan,
                ByteBuffer.wrap(raw),
                ByteBuffer.wrap(input()),
                selection,
                new SlabLimits(16, 65536)));
    var bounded =
        SlabMessage.decode(
            plan, input(), 0, input().length, new SlabLimits(16, message.slabBytes()), selection);
    assertThrows(ContractViolation.class, () -> bounded.get(2));
  }

  @Test
  void laxSkipsOnlyUtf8AndLazyReadValidatesStrictly() {
    var plan = plan();
    var projection = Projection.builder(plan).select("id").build().lazyChildren();
    byte[] invalid =
        LayoutTest.wire().message(3, LayoutTest.wire().bytes(2, new byte[] {(byte) 0xc0})).bytes();
    assertThrows(ContractViolation.class, () -> decode(plan, invalid, projection));
    var deferred = decode(plan, invalid, projection.lax());
    assertThrows(ContractViolation.class, () -> deferred.get(2));
    assertThrows(ContractViolation.class, () -> deferred.get(2));
    byte[] malformed = {26, 2, 18, 4};
    assertThrows(ContractViolation.class, () -> decode(plan, malformed, projection.lax()));
  }

  @Test
  void internalProjectionEnforcesRequiredFieldsAndLogicalBudgets() {
    var root =
        LayoutTest.type(
            "Root",
            LayoutTest.required("id", 1, INT32),
            LayoutTest.scalar("name", 2, STRING, false),
            LayoutTest.message("child", 3, "Child", false));
    var child = LayoutTest.type("Child", LayoutTest.required("value", 1, INT32));
    var plan = LayoutTest.model(root, child).type("Root");
    var selection = Projection.builder(plan).select("name").build().lazyChildren();
    assertThrows(ContractViolation.class, () -> decode(plan, new byte[0], selection));
    byte[] missingChild = LayoutTest.wire().field(1, 1).message(3, LayoutTest.wire()).bytes();
    assertThrows(ContractViolation.class, () -> decode(plan, missingChild, selection));
    var valid = LayoutTest.wire().field(1, 1).string(2, "abc");
    var limited =
        new ProtoPlan.Model(null, List.of(root, child), new Limits(16, 16, 16, 5), 8, 1024)
            .type("Root");
    assertThrows(
        ContractViolation.class,
        () -> decode(limited, valid.bytes(), Projection.builder(limited).select("name").build()));
    var lowCells =
        new ProtoPlan.Model(
                null,
                List.of(LayoutTest.type("Values", LayoutTest.repeated("values", 1, INT32, false))),
                new Limits(16, 16, 2, 1024),
                8,
                1024)
            .type("Values");
    assertThrows(
        ContractViolation.class,
        () ->
            decode(
                lowCells,
                LayoutTest.wire().field(1, 1).field(1, 2).bytes(),
                Projection.all(lowCells)));
    byte[] nested = valid.message(3, LayoutTest.wire().field(1, 7)).bytes();
    assertThrows(
        ContractViolation.class,
        () ->
            SlabMessage.decode(
                plan, nested, 0, nested.length, new SlabLimits(0, 65536), selection));
  }

  @Test
  void fullSelectionRoundTripsExternalBytesAndWindowCopy() {
    var plan = plan();
    var projection = Projection.all(plan).borrowBytes(2);
    byte[] input = input(), padded = new byte[input.length + 17];
    System.arraycopy(input, 0, padded, 9, input.length);
    var owner =
        SlabMessage.decode(plan, padded, 9, input.length, new SlabLimits(16, 65536), projection);
    Arrays.fill(padded, (byte) 0);
    assertEquals(input.length, owner.retainedInputBytes());
    assertArrayEquals(SlabMessage.decode(plan, input).encode(), owner.encode());
    byte[] bad = owner.slabCopy();
    int header = getInt(bad, ROOT + plan.layout().slot(0, 4));
    putInt(bad, header + 12, input.length);
    assertThrows(
        ContractViolation.class,
        () ->
            SlabMessage.copyProjectedOf(
                plan,
                ByteBuffer.wrap(bad),
                ByteBuffer.wrap(input),
                projection,
                new SlabLimits(16, 65536)));
  }
}
