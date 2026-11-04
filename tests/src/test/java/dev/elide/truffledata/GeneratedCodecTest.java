package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.generated.TruffleDataContracts;
import dev.elide.truffledata.protobuf.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GeneratedCodecTest {
  private static ProtoPlan.Model model(Limits limits, int depth, int bytes) {
    return TruffleDataContracts.create(null, limits, depth, bytes);
  }

  @Test
  void generatedBuildersValidateAndKeepSnapshotsIndependent() {
    var plan = model(Limits.DEFAULT, 32, 1000000).type("truffledata.lossless.Flat");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Flat(plan);
    var builder = access.newBuilder().set_i32(123).set_text("hello €").set_selected(7);
    var first = builder.build();
    var second = builder.set_i32(456).set_selected_text("second").build();
    assertEquals(123, first.get_i32());
    assertEquals(456, second.get_i32());
    assertEquals(7, first.get_selected());
    assertEquals(0, second.get_selected());
    assertFalse(second.has(17));
    assertTrue(second.has(18));
    assertArrayEquals(
        plan.encode(plan.parseText("i32: 123 text: 'hello €' selected: 7")), first.encode());
    assertThrows(ContractViolation.class, () -> builder.set_i32(Long.MAX_VALUE));
    assertThrows(ContractViolation.class, () -> builder.set_u32(-1));
    assertThrows(ContractViolation.class, () -> builder.set_text("\uD800"));
    assertEquals("", builder.clear_text().build().get_text());
    var required =
        new TruffleDataContracts.Access_truffledata$lossless$FlatDefaults(
                model(Limits.DEFAULT, 32, 1000).type("truffledata.lossless.FlatDefaults"))
            .newBuilder();
    assertThrows(ContractViolation.class, required::build);
    assertThrows(ContractViolation.class, () -> required.set_status(999));
    assertEquals("hello", required.set_count(0).build().get_text());
    assertThrows(ContractViolation.class, () -> required.clear_count().build());
    var tiny =
        new TruffleDataContracts.Access_truffledata$lossless$Flat(
                model(new Limits(1024, 256, 262144, 2), 32, 1000).type(plan.name()))
            .newBuilder();
    assertThrows(ContractViolation.class, () -> tiny.set_text("long").build());
  }

  @Test
  void primitiveByteCopiesDoNotExposeStorage() {
    byte[] source = {1, 2, (byte) 255};
    var bytes = DataArray.copyOfBytes(source);
    byte[] output = new byte[5];
    bytes.copyBytesTo(output, 1);
    assertArrayEquals(new byte[] {0, 1, 2, (byte) 255, 0}, output);
    output[1] = 9;
    source[0] = 8;
    assertEquals(1L, bytes.get(0));
    assertThrows(IndexOutOfBoundsException.class, () -> bytes.copyBytesTo(output, 3));
    assertThrows(IndexOutOfBoundsException.class, () -> bytes.copyBytesTo(output, -1));
    assertThrows(
        ContractViolation.class, () -> new DataArray(new Object[] {1L}).copyBytesTo(output, 0));
  }

  @Test
  void scalarWireKindsAndOneofMatchOwnedCodec() throws Exception {
    var model = model(Limits.DEFAULT, 32, 1000000);
    var plan = model.type("truffledata.lossless.Flat");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Flat(plan);
    var generic =
        plan.parseText(
            "i32: -123 i64: -9223372036854775808 u32: 4294967295 u64: 18446744073709551615 s32:"
                + " -123 s64: -456 f32: 4294967295 f64: 18446744073709551615 sf32: -789 sf64: -987"
                + " flag: true single: -0.0 real: 2.5 text: '€😀' blob: '\\000\\377' status: 123"
                + " present: 0 selected_text: 'last'");
    byte[] wire = plan.encode(generic);
    var actual = access.decode(wire);
    assertArrayEquals(wire, actual.encode());
    assertEquals(-123, actual.get_i32());
    assertEquals(Long.MIN_VALUE, actual.get_i64());
    assertEquals(4294967295L, actual.get_u32());
    assertEquals(-123, actual.get_s32());
    assertEquals(-456, actual.get_s64());
    assertEquals(-789, actual.get_sf32());
    assertEquals(-987, actual.get_sf64());
    assertEquals("€😀", actual.get_text());
    assertEquals(2, actual.getSize(14));
    assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(actual.get_single()));
    for (int i = 0; i < plan.size(); i++) assertEquals(generic.has(i), actual.has(i));
    var data = DataLibrary.factory().getUncached();
    assertSame(plan.schema(), data.getSchema(actual));
    assertEquals(-123, data.readLong(actual, 0));
    assertEquals(2.5, data.readDouble(actual, 12));
    assertTrue(data.readBoolean(actual, 10));
    assertEquals("€😀", data.read(actual, 13));
    assertThrows(UnexpectedResultException.class, () -> data.readLong(actual, 13));
    assertThrows(UnexpectedResultException.class, () -> data.readDouble(actual, 0));
    assertThrows(UnexpectedResultException.class, () -> data.readBoolean(actual, 0));
    assertThrows(ContractViolation.class, () -> actual.getLong(13));
    assertThrows(ContractViolation.class, () -> actual.getDouble(0));
    assertThrows(ContractViolation.class, () -> actual.getBoolean(0));
    assertThrows(ContractViolation.class, () -> actual.get(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> actual.has(64));
    byte[] switched = plan.encode(plan.parseText("selected: 7"));
    byte[] both = Arrays.copyOf(wire, wire.length + switched.length);
    System.arraycopy(switched, 0, both, wire.length, switched.length);
    var last = access.decode(both);
    assertEquals(7, last.get_selected());
    assertEquals("", last.get_selected_text());
    assertTrue(last.has(17));
    assertFalse(last.has(18));
    assertArrayEquals(plan.encode(plan.decode(both)), last.encode());
    var defaults = access.decode(new byte[0]);
    for (int i = 0; i < plan.size(); i++) assertFalse(defaults.has(i));
    assertArrayEquals(new byte[0], defaults.encode());
    assertThrows(ContractViolation.class, () -> defaults.getLong(3));
    assertThrows(UnexpectedResultException.class, () -> data.readLong(defaults, 3));
    var zero = access.decode(new byte[] {32, 0});
    assertEquals(java.math.BigDecimal.ZERO, ((ExactNumber) zero.get_u64()).decimal());
    assertFalse(zero.has(3));
    assertArrayEquals(new byte[0], zero.encode());
    var low = access.decode(new byte[] {32, 123});
    assertEquals(java.math.BigDecimal.valueOf(123), ((ExactNumber) low.get_u64()).decimal());
    assertArrayEquals(new byte[] {32, 123}, low.encode());
  }

  @Test
  void defaultsRequiredAndClosedEnums() {
    var plan = model(Limits.DEFAULT, 32, 1000).type("truffledata.lossless.FlatDefaults");
    var access = new TruffleDataContracts.Access_truffledata$lossless$FlatDefaults(plan);
    assertThrows(ContractViolation.class, () -> access.decode(new byte[0]));
    byte[] wire = {8, 0, 24, (byte) 231, 7}; // Unknown closed-enum value 999.
    var row = access.decode(wire);
    assertTrue(row.has(0));
    assertFalse(row.has(2));
    assertEquals(2, row.get_status());
    assertEquals("hello", row.get_text());
    assertEquals(2, row.get_blob().size());
    assertArrayEquals(wire, row.encode());
    assertArrayEquals(
        plan.encode(plan.decode(new byte[] {8, 7, 24, 1})),
        access.decode(new byte[] {8, 7, 24, 1}).encode());
    assertThrows(ContractViolation.class, () -> row.getDouble(0));
    assertThrows(ContractViolation.class, () -> row.getBoolean(0));
  }

  @Test
  void unknownWireBudgetsAndMalformedInput() {
    var plan = model(Limits.DEFAULT, 4, 1000).type("truffledata.lossless.Empty");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Empty(plan);
    byte[] wire = {8, 1, 17, 0, 0, 0, 0, 0, 0, 0, 0, 26, 1, 9, 35, 8, 3, 36, 45, 0, 0, 0, 0};
    assertArrayEquals(wire, access.decode(wire).encode());
    byte[] encoded = access.decode(wire).encode();
    encoded[0] = 0;
    assertArrayEquals(wire, access.decode(wire).encode());
    assertThrows(ContractViolation.class, () -> access.decode(new byte[0]).get(0));
    assertThrows(ContractViolation.class, () -> access.decode(new byte[0]).getLong(0));
    for (byte[] invalid :
        List.of(
            new byte[] {0},
            new byte[] {8},
            new byte[] {17},
            new byte[] {26, 3, 1},
            new byte[] {35},
            new byte[] {35, 44},
            new byte[] {4},
            new byte[] {45},
            new byte[] {14},
            new byte[] {8, -1, -1, -1, -1, -1, -1, -1, -1, -1, 2},
            new byte[] {-1, -1, -1, -1, 127},
            new byte[] {26, -1, -1, -1, -1, -1, -1, -1, -1, -1, 1}))
      assertThrows(ContractViolation.class, () -> access.decode(invalid));
    var shallow = model(Limits.DEFAULT, 1, 1000).type(plan.name());
    assertThrows(
        ContractViolation.class,
        () ->
            new TruffleDataContracts.Access_truffledata$lossless$Empty(shallow)
                .decode(new byte[] {35, 36}));
    var small = model(new Limits(1024, 256, 262144, 1), 4, 1000).type(plan.name());
    assertThrows(
        ContractViolation.class,
        () ->
            new TruffleDataContracts.Access_truffledata$lossless$Empty(small)
                .decode(new byte[] {8, 1}));
    var shortWire = model(Limits.DEFAULT, 4, 0).type(plan.name());
    assertThrows(
        ContractViolation.class,
        () ->
            new TruffleDataContracts.Access_truffledata$lossless$Empty(shortWire)
                .decode(new byte[] {8, 1}));
    var input = new GeneratedInput(plan, new byte[0]);
    assertThrows(IllegalArgumentException.class, () -> input.retain(-1));
    assertThrows(ContractViolation.class, () -> input.fixed(3));
  }

  @Test
  void generatedUtf8AndPayloadLimits() {
    var plan = model(Limits.DEFAULT, 8, 1000).type("truffledata.lossless.Flat");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Flat(plan);
    for (byte[] bytes :
        List.of(
            new byte[] {(byte) 0x80},
            new byte[] {(byte) 0xc0, (byte) 0x80},
            new byte[] {(byte) 0xe0, (byte) 0x80, (byte) 0x80},
            new byte[] {(byte) 0xed, (byte) 0xa0, (byte) 0x80},
            new byte[] {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80},
            new byte[] {(byte) 0xc2, 1},
            new byte[] {(byte) 0xf0, (byte) 0x90})) {
      byte[] wire = new byte[bytes.length + 2];
      wire[0] = 114;
      wire[1] = (byte) bytes.length;
      System.arraycopy(bytes, 0, wire, 2, bytes.length);
      assertThrows(ContractViolation.class, () -> access.decode(wire));
    }
    for (String text : List.of("", "ascii", "é", "中", "😀")) {
      byte[] wire = plan.encode(plan.newBuilder().set(13, text).build());
      assertEquals(text, access.decode(wire).get_text());
    }
    var small = model(new Limits(1024, 256, 262144, 0), 8, 1000).type(plan.name());
    assertThrows(
        ContractViolation.class,
        () ->
            new TruffleDataContracts.Access_truffledata$lossless$Flat(small)
                .decode(new byte[] {114, 1, 65}));
  }
}
