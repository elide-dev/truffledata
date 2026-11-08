package dev.elide.truffledata.protobuf;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ContractViolation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Production Java slabs against the independent format model and native producer goldens. */
class SlabCodecTest {
  @Test
  void goldenSlabsMatchNativeProducer() throws Exception {
    Path directory =
        Path.of(
            System.getenv("TEST_SRCDIR"),
            System.getenv("TEST_WORKSPACE"),
            "protobuf-runtime/src/test/resources/slab");
    for (var golden : LayoutTest.goldens()) {
      byte[] actual = SlabCodec.decode(golden.plan().layout(), golden.wire());
      assertArrayEquals(
          Files.readAllBytes(directory.resolve(golden.name() + ".bin")), actual, golden.name());
      SlabReference.assertMatches(
          golden.plan().layout(), actual, golden.plan().decode(golden.wire()));
    }
  }

  @Test
  void producersAgreeOverEightThousandInputsPerSchema() {
    var random = new Random(0x51ab);
    for (ProtoPlan plan :
        List.of(
            LayoutTest.choice(),
            LayoutTest.unknowns(),
            LayoutTest.merge(),
            LayoutTest.maps(),
            LayoutTest.lists(),
            LayoutTest.texts(),
            LayoutTest.legacy())) {
      Layout layout = plan.layout();
      for (int iteration = 0; iteration < 8000; iteration++) {
        byte[] wire = LayoutTest.randomWire(random, plan, 0);
        if (wire.length != 0 && iteration % 7 == 0)
          wire = Arrays.copyOf(wire, random.nextInt(wire.length));
        byte[] input = wire;
        byte[] expected;
        try {
          expected = SlabReference.decode(layout, input);
        } catch (ContractViolation rejected) {
          assertThrows(ContractViolation.class, () -> SlabCodec.decode(layout, input));
          continue;
        }
        assertArrayEquals(expected, SlabCodec.decode(layout, input), plan.name() + "/" + iteration);
      }
    }
  }

  @Test
  void exactBudgetAndInputSlice() {
    for (var golden : LayoutTest.goldens()) {
      Layout layout = golden.plan().layout();
      byte[] input = golden.wire();
      byte[] expected = SlabCodec.decode(layout, input);
      byte[] padded = new byte[input.length + 9];
      System.arraycopy(input, 0, padded, 4, input.length);
      assertArrayEquals(
          expected,
          SlabCodec.decode(layout, padded, 4, input.length, new SlabLimits(32, expected.length)));
      assertThrows(
          ContractViolation.class,
          () ->
              SlabCodec.decode(
                  layout, input, 0, input.length, new SlabLimits(32, expected.length - 1)));
      assertThrows(
          ContractViolation.class,
          () -> SlabCodec.decode(layout, input, 0, input.length, new SlabLimits(32, 0)));
      assertThrows(
          IndexOutOfBoundsException.class,
          () ->
              SlabCodec.decode(
                  layout, input, 1, input.length, new SlabLimits(32, expected.length)));
    }
  }

  @Test
  void explicitSlabLimitsCannotExpandThePlanDepthBudget() {
    var original = LayoutTest.merge();
    var definitions = new java.util.ArrayList<ProtoPlan.Type>();
    for (int i = 0; i < original.layout().types(); i++)
      definitions.add(original.layout().plan(i).definition());
    var bounded =
        new ProtoPlan.Model(null, definitions, dev.elide.truffledata.Limits.DEFAULT, 1, 65536)
            .type(original.name());
    byte[] nested = original.encode(original.parseText("inner { a: 1 }"));
    assertThrows(
        ContractViolation.class,
        () ->
            SlabCodec.decode(
                bounded.layout(), nested, 0, nested.length, new SlabLimits(128, 65536)));
    assertThrows(ContractViolation.class, () -> SlabMessage.decode(bounded, nested));
    assertThrows(IllegalArgumentException.class, () -> new SlabLimits(16, 65536, 65537));
    assertThrows(IllegalArgumentException.class, () -> new SlabLimits(16, 65536, -1));
  }

  @Test
  void owningSlabsReadAndEncodeWithoutRetainingInput() {
    for (var golden : LayoutTest.goldens()) {
      var plan = golden.plan();
      byte[] wire = golden.wire().clone();
      var expected = plan.decode(wire);
      var message = SlabMessage.decode(plan, wire);
      var imported =
          SlabMessage.copyOf(
              plan, java.nio.ByteBuffer.wrap(message.slabCopy()), new SlabLimits(32, 1 << 20));
      assertArrayEquals(message.encode(), imported.encode());
      Arrays.fill(wire, (byte) 0);
      assertSame(message, message.snapshot());
      message.close();
      assertArrayEquals(plan.encode(expected), message.encode(), golden.name());
      compareRows(expected, message);
      assertArrayEquals(expected.unknownFieldBytes(), message.unknownFieldBytes());
    }
    assertThrows(
        ContractViolation.class, () -> SlabMessage.decode(LayoutTest.legacy(), new byte[0]));
  }

  private static void compareRows(ProtoAccess expected, ProtoAccess actual) {
    assertSame(expected.plan(), actual.plan());
    for (int ordinal = 0; ordinal < expected.plan().size(); ordinal++) {
      assertEquals(expected.has(ordinal), actual.has(ordinal));
      compareValue(expected.get(ordinal), actual.get(ordinal));
    }
  }

  private static void compareValue(Object expected, Object actual) {
    if (expected instanceof ProtoAccess row) compareRows(row, (ProtoAccess) actual);
    else if (expected instanceof dev.elide.truffledata.OwnedArray array) {
      var other = (dev.elide.truffledata.OwnedArray) actual;
      assertEquals(array.size(), other.size());
      for (int i = 0; i < array.size(); i++) compareValue(array.get(i), other.get(i));
    } else if (expected instanceof dev.elide.truffledata.ExactNumber number) {
      assertEquals(
          0, number.decimal().compareTo(((dev.elide.truffledata.ExactNumber) actual).decimal()));
    } else assertEquals(expected, actual);
  }

  @Test
  void copiedChildSelectionRejectsNonRecordsAndWrongTypes() {
    var plan = LayoutTest.merge();
    byte[] slab = SlabCodec.decode(plan.layout(), new byte[] {10, 2, 8, 7});
    int child = SlabFormat.getInt(slab, SlabFormat.ROOT + plan.layout().slot(0, 0));
    int type = plan.layout().childType(0, 0);
    var limits = new SlabLimits(32, slab.length);
    var row = SlabMessage.copyRecordOf(plan, java.nio.ByteBuffer.wrap(slab), limits, type, child);
    assertEquals(7, row.getLong(0));
    for (int bad : new int[] {0, child + 4, slab.length})
      assertThrows(
          ContractViolation.class,
          () -> SlabMessage.copyRecordOf(plan, java.nio.ByteBuffer.wrap(slab), limits, type, bad));
    assertThrows(
        ContractViolation.class,
        () -> SlabMessage.copyRecordOf(plan, java.nio.ByteBuffer.wrap(slab), limits, 0, child));
    Arrays.fill(slab, (byte) 0);
    assertEquals(7, row.getLong(0));
  }

  @Test
  void externalSlabsRejectBadOffsetsHandlesAndOwnership() {
    var plan = LayoutTest.texts();
    byte[] slab = SlabCodec.decode(plan.layout(), new byte[] {10, 1, 'x'});
    for (int offset :
        new int[] {
          SlabFormat.HEADER_HANDLES,
          SlabFormat.HEADER_FLAGS,
          SlabFormat.ROOT + plan.layout().slot(0, 0)
        }) {
      byte[] invalid = slab.clone();
      SlabFormat.putInt(invalid, offset, -1);
      assertThrows(
          ContractViolation.class,
          () ->
              SlabMessage.copyOf(
                  plan, java.nio.ByteBuffer.wrap(invalid), new SlabLimits(32, 1 << 20)));
    }
    assertThrows(
        ContractViolation.class,
        () ->
            SlabMessage.copyOf(
                plan, java.nio.ByteBuffer.wrap(slab), new SlabLimits(32, slab.length - 1)));
  }

  @Test
  void slabAccessorRejectsOtherLayoutsAndReadsStringLength() throws Exception {
    var plan = LayoutTest.timestamp();
    var row = SlabMessage.decode(plan, new byte[] {8, 42});
    var accessor = row.accessor(0);
    assertSame(accessor, SlabMessage.decode(plan, new byte[0]).accessor(0));
    assertEquals(42, accessor.readLong(row));
    var other = SlabMessage.decode(LayoutTest.timestamp(), new byte[] {8, 99});
    assertFalse(accessor.accepts(other));
    assertThrows(ContractViolation.class, () -> accessor.readLong(other));
    var text = SlabMessage.decode(LayoutTest.texts(), new byte[] {10, 2, 'h', 'i'});
    assertEquals(2, text.accessor(0).length(text));
    assertEquals("hi", text.get(0));
  }

  @Test
  void utf8ValidationAndUnitsMatchStrictDecoder() {
    for (String text :
        List.of(
            "",
            "a".repeat(255),
            "\u00e9",
            "\u4f60\u597d",
            "\ud83d\ude42",
            "a".repeat(64) + "\u03bb")) {
      byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
      assertEquals(text.length(), SlabCodec.utf16(bytes, 0, bytes.length));
    }
    var random = new Random(61);
    for (int i = 0; i < 10000; i++) {
      byte[] bytes = new byte[random.nextInt(20) + 1];
      random.nextBytes(bytes);
      int units;
      try {
        units = SlabReference.utf16(bytes, 0, bytes.length);
      } catch (ContractViolation rejected) {
        assertThrows(ContractViolation.class, () -> SlabCodec.utf16(bytes, 0, bytes.length));
        continue;
      }
      assertEquals(units, SlabCodec.utf16(bytes, 0, bytes.length));
    }
  }
}
