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
}
