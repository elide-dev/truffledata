package dev.elide.truffledata.buffa.tests;

import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.*;
import dev.elide.truffledata.generated.TruffleDataContracts;
import dev.elide.truffledata.protobuf.*;
import java.util.*;

/** The same typed/native semantic and ownership corpus runs on JVM and both SVM images. */
final class TypedAcceptance {
  private static final DataLibrary DATA = DataLibrary.factory().getUncached();
  private static final int NATIVE = 1_000_000;

  private TypedAcceptance() {}

  static ProtoPlan plan(String name) {
    return TruffleDataContracts.create(null, Limits.DEFAULT, 32, 1_000_000)
        .type("truffledata.lossless." + name);
  }

  static void run(BuffaWire decoder) throws Exception {
    ProtoPlan plan = plan("Sample");
    Acceptance.rejects(() -> decoder.compile(plan, 1), ContractViolation.class);
    Acceptance.rejects(() -> decoder.compile(plan, 64), ContractViolation.class);
    try (var nativePlan = decoder.compile(plan, 65536)) {
      for (String text :
          List.of(
              "count: -2147483648 explicit_count: 0 u32: 4294967295 u64: 18446744073709551615",
              "text: 'hello' ' €' blob: '\\001\\377' blobs: ['x','y']",
              "numbers: [1,-2,03,0x4] numbers: 5 statuses: [READY,999] flag: True",
              "child < a: 1 b: 'ok' > selected_child: { a: 2 } # comment\n",
              "children { key: 'a' value { a: 1 } } children { key: 'a' value { b: 'last' } }",
              "counts { key: 18446744073709551615 value: -1 }",
              "f: -inf d: nan fixed32: 4294967295 sfixed64: -9223372036854775808",
              "custom_value: 7")) {
        ProtoRow expected = plan.parseText(text);
        try (var parsed = nativePlan.parseText(text, NATIVE)) {
          equal(expected, parsed.row());
          equal(expected, parsed.snapshot());
        }
        byte[] wire = plan.encode(expected);
        try (var input = decoder.allocate(wire.length).put(wire);
            var parsed = nativePlan.decode(input, NATIVE)) {
          equal(expected, parsed.row());
          input.close();
          equal(expected, parsed.row());
          equal(expected, parsed.snapshot());
        }
      }
      for (String json :
          List.of(
              "{\"explicitCount\":0,\"u64\":\"18446744073709551615\",\"count\":-2147483648}",
              "{\"text\":\"hello €\",\"blob\":\"Af8=\",\"blobs\":[\"eA\",\"_w\"]}",
              "{\"numbers\":[1,-2,3e0,\"4\"],\"statuses\":[\"READY\",999],\"flag\":true}",
              "{\"child\":{\"a\":1},\"selectedChild\":{\"b\":\"ok\"}}",
              "{\"children\":{\"a\":{\"a\":1}},\"counts\":{\"18446744073709551615\":-1}}",
              "{\"f\":\"-Infinity\",\"d\":\"NaN\",\"count\":null,\"customAlias\":7}")) {
        try (var parsed = nativePlan.parseJson(json, NATIVE)) {
          equal(plan.parseJson(json), parsed.row());
          equal(plan.parseJson(json), parsed.snapshot());
        }
      }
      // Binary merges and oneof switching require semantics beyond a field-by-field decoder.
      for (byte[] wire :
          List.of(
              new byte[] {66, 2, 8, 1, 66, 3, 18, 1, 120},
              new byte[] {90, 2, 8, 1, 80, 2, 90, 3, 18, 1, 121},
              new byte[] {74, 3, 2, 3, 4, 72, 6},
              new byte[] {58, 3, 10, 1, 107})) {
        try (var input = decoder.allocate(wire.length).put(wire);
            var parsed = nativePlan.decode(input, NATIVE)) {
          equal(plan.decode(wire), parsed.row());
        }
      }
      for (byte[] wire :
          List.of(
              new byte[] {26, 5, 1}, new byte[] {122, 1, (byte) 255}, new byte[] {8, (byte) 128})) {
        try (var input = decoder.allocate(wire.length).put(wire)) {
          Acceptance.rejects(() -> nativePlan.decode(input, NATIVE), ContractViolation.class);
        }
      }
      Random random = new Random(89312);
      for (int i = 0; i < 300; i++) {
        byte[] bytes = new byte[random.nextInt(512)];
        random.nextBytes(bytes);
        ProtoRow expected =
            plan.newBuilder()
                .set(0, (long) random.nextInt())
                .set(2, DataArray.copyOfBytes(bytes))
                .set(3, (long) random.nextInt())
                .set(5, Values.normalize(new java.math.BigInteger(64, random)))
                .set(11, (double) Float.intBitsToFloat(random.nextInt()))
                .build();
        byte[] wire = plan.encode(expected);
        try (var input = decoder.allocate(wire.length).put(wire);
            var parsed = nativePlan.decode(input, NATIVE)) {
          equal(expected, parsed.row());
        }
      }
      for (String text :
          List.of(
              "count: 1 count: 2",
              "selected: 1 selected_child {}",
              "unknown: 1",
              "u32: -1",
              "child {",
              "text: '\\xff'",
              "numbers []",
              "count: 2147483648"))
        Acceptance.rejects(() -> nativePlan.parseText(text, NATIVE), ContractViolation.class);
      for (String json :
          List.of(
              "{\"count\":1,\"count\":2}",
              "{\"numbers\":[],\"numbers\":[]}",
              "{\"explicitCount\":1,\"explicit_count\":2}",
              "{\"selected\":1,\"selectedChild\":{}}",
              "{\"count\":1e999999999}",
              "{\"numbers\":[null]}",
              "{\"blob\":\"=\"}",
              "{} {}"))
        Acceptance.rejects(() -> nativePlan.parseJson(json, NATIVE), ContractViolation.class);
      if (decoder.selected() != BuffaWire.Selected.JAVA) {
        var message = nativePlan.parseText("blob: '\\377' numbers: [1,2] child { a: 42 }", NATIVE);
        var row = message.row();
        var bytes = (BorrowedBinary) DATA.read(row, 2);
        var array = (BorrowedArray) DATA.read(row, 8);
        Object child = DATA.read(row, 7);
        Acceptance.check(nativePlan.contract() == plan && row.plan() == plan, "retained contract");
        Acceptance.check(message.nativeIndexBytes() > 0, "native index accounting");
        var interop = com.oracle.truffle.api.interop.InteropLibrary.getUncached();
        try {
          Acceptance.check(
              interop.hasArrayElements(array) && interop.getArraySize(array) == 2,
              "typed array interop");
          Acceptance.check(
              interop.isArrayElementReadable(array, 0)
                  && !interop.isArrayElementReadable(array, -1),
              "array bounds");
          Acceptance.check(interop.readArrayElement(array, 1).equals(2L), "array read");
          try {
            interop.readArrayElement(array, 2);
            throw new AssertionError("out-of-bounds read accepted");
          } catch (com.oracle.truffle.api.interop.InvalidArrayIndexException expected) {
            Acceptance.check(expected.getInvalidIndex() == 2, "invalid index");
          }
        } catch (com.oracle.truffle.api.interop.InteropException e) {
          throw new AssertionError(e);
        }
        Acceptance.rejects(() -> Values.normalize(array), ContractViolation.class);
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        Thread thread =
            new Thread(
                () -> {
                  try {
                    DATA.read(row, 2);
                  } catch (IllegalStateException expected) {
                    rejected.incrementAndGet();
                  }
                });
        thread.start();
        thread.join();
        Acceptance.check(rejected.get() == 1, "typed confinement");
        ProtoRow snapshot = message.snapshot();
        message.close();
        message.close();
        Acceptance.rejects(() -> bytes.get(0), IllegalStateException.class);
        Acceptance.rejects(array::size, IllegalStateException.class);
        Acceptance.rejects(() -> DATA.read(child, 0), IllegalStateException.class);
        Acceptance.rejects(() -> DATA.read(row, 0), IllegalStateException.class);
        Acceptance.check(
            ((DataArray) DATA.read(snapshot, 2)).get(0).equals(255L), "detached native text");
        Acceptance.rejects(() -> nativePlan.parseText("count: 1", 1), ContractViolation.class);
        try (var retained = nativePlan.parseJson("{\"blob\":\"/w==\"}", NATIVE)) {
          nativePlan.close();
          Acceptance.check(
              ((BorrowedBinary) DATA.read(retained.row(), 2)).get(0) == 255,
              "message survives plan closure");
          Acceptance.rejects(() -> nativePlan.parseText("", NATIVE), IllegalStateException.class);
        }
      }
    }
    if (decoder.selected() != BuffaWire.Selected.JAVA) {
      var limited =
          TruffleDataContracts.create(null, new Limits(1024, 256, 70, 10000), 32, 10000)
              .type("truffledata.lossless.Sample");
      byte[] wire = new byte[50];
      StringBuilder text = new StringBuilder();
      for (int i = 0; i < 10; i++) {
        int at = i * 5;
        wire[at] = 58;
        wire[at + 1] = 3;
        wire[at + 2] = 10;
        wire[at + 3] = 1;
        wire[at + 4] = (byte) ('a' + i);
        text.append("children { key: '").append((char) ('a' + i)).append("' } ");
      }
      try (var compiled = decoder.compile(limited, 65536);
          var input = decoder.allocate(wire.length).put(wire)) {
        Acceptance.rejects(() -> compiled.decode(input, NATIVE), ContractViolation.class);
        Acceptance.rejects(
            () -> compiled.parseText(text.toString(), NATIVE), ContractViolation.class);
      }
    }
    for (String name : List.of("Legacy", "Required")) {
      ProtoPlan contract = plan(name);
      try (var compiled = decoder.compile(contract, 65536)) {
        String text =
            name.equals("Legacy")
                ? "Old { value: 9 } status: FIRST"
                : "count: 0 child { count: 42 }";
        try (var parsed = compiled.parseText(text, NATIVE)) {
          equal(contract.parseText(text), parsed.row());
        }
        if (name.equals("Required"))
          Acceptance.rejects(() -> compiled.parseText("", NATIVE), ContractViolation.class);
        if (name.equals("Legacy")) {
          byte[] unknown = {24, 99, 34, 3, 1, 99, 2};
          try (var input = decoder.allocate(unknown.length).put(unknown);
              var parsed = compiled.decode(input, NATIVE)) {
            equal(contract.decode(unknown), parsed.row());
            Acceptance.check(
                Arrays.equals(
                    contract.encode(contract.decode(unknown)), contract.encode(parsed.snapshot())),
                "unknown enum snapshot");
          }
        }
      }
    }
  }

  static void equal(Object expected, Object actual) {
    if (expected instanceof AbstractRow a && actual instanceof AbstractRow b) {
      Acceptance.check(a.schema().size() == b.schema().size(), "typed schema width");
      for (int i = 0; i < a.schema().size(); i++) {
        equal(DATA.read(a, i), DATA.read(b, i));
        if (a instanceof ProtoRow proto && b instanceof TypedRow typed)
          Acceptance.check(proto.has(i) == typed.has(i), "typed presence " + i);
      }
    } else if (expected instanceof DataArray a) {
      int size = actual instanceof DataArray b ? b.size() : ((BorrowedArray) actual).size();
      Acceptance.check(a.size() == size, "typed array length");
      for (int i = 0; i < size; i++)
        equal(
            a.get(i),
            actual instanceof DataArray b ? b.get(i) : ((BorrowedArray) actual).element(i));
    } else if (expected instanceof ExactNumber a && actual instanceof ExactNumber b)
      Acceptance.check(a.decimal().compareTo(b.decimal()) == 0, "typed exact integer");
    else
      Acceptance.check(
          expected.equals(actual), "typed scalar mismatch: " + expected + " / " + actual);
  }
}
