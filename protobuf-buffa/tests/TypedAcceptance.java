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

  private static void generatedGetters(BuffaWire decoder) throws Exception {
    var p = plan("Sample");
    var access = new TruffleDataContracts.Access_truffledata$lossless$Sample(p);
    try (var compiled = decoder.compile(p, 65536)) {
      for (int i = 0; i < 3; i++) {
        try (var parsed =
            compiled.parseText("count: 1234 text: 'hello' blob: 'abc' f: 1.5 flag: true", NATIVE)) {
          var row = parsed.row();
          Acceptance.check(
              DATA.readDouble(row, p.schema().indexOf("f")) == 1.5, "primitive double export");
          Acceptance.rejects(
              () -> row.getLong(p.schema().indexOf("text")), ContractViolation.class);
          try {
            DATA.readLong(row, p.schema().indexOf("f"));
            throw new AssertionError("Expected integer type rejection");
          } catch (com.oracle.truffle.api.nodes.UnexpectedResultException failure) {
            Acceptance.check(
                failure.getResult().equals(1.5), "wrong-type integer preserves result");
          }
          Acceptance.check(
              DATA.readBoolean(row, p.schema().indexOf("flag")), "primitive boolean export");
          try {
            DATA.readDouble(row, 0);
            throw new AssertionError("Expected double type rejection");
          } catch (com.oracle.truffle.api.nodes.UnexpectedResultException failure) {
            Acceptance.check(
                failure.getResult().equals(1234L), "wrong-type double preserves result");
          }
          try {
            DATA.readBoolean(row, 0);
            throw new AssertionError("Expected boolean type rejection");
          } catch (com.oracle.truffle.api.nodes.UnexpectedResultException failure) {
            Acceptance.check(
                failure.getResult().equals(1234L), "wrong-type boolean preserves result");
          }
          Acceptance.check(
              access.get_count(row) == 1234 && access.get_f(row) == 1.5 && access.get_flag(row),
              "generated primitive getters");
          Acceptance.check(
              access.get_text(row).equals("hello") && access.size_blob(row) == 3,
              "generated reference getters");
          Acceptance.check(
              Arrays.equals(p.encode(parsed.snapshot()), parsed.encode()),
              "text view semantic serialization");
          parsed.close();
          Acceptance.rejects(parsed::encode, IllegalStateException.class);
          Acceptance.rejects(() -> access.get_count(row), IllegalStateException.class);
          Acceptance.rejects(() -> access.has_count(row), IllegalStateException.class);
        }
      }
    }
  }

  private static void compactScalars(BuffaWire decoder) throws Exception {
    for (boolean explicit : new boolean[] {false, true}) {
      var field =
          new ProtoPlan.FieldPlan(
              "value",
              1,
              ProtoPlan.Kind.INT32,
              false,
              false,
              explicit,
              explicit,
              false,
              -1,
              "",
              null,
              explicit ? 7L : 0L);
      var p =
          new ProtoPlan.Model(
                  null,
                  List.of(
                      new ProtoPlan.Type(
                          "Compact",
                          false,
                          List.of(
                              field,
                              new ProtoPlan.FieldPlan(
                                  "second",
                                  2,
                                  ProtoPlan.Kind.INT64,
                                  false,
                                  false,
                                  false,
                                  false,
                                  false,
                                  -1,
                                  "",
                                  null,
                                  0L)))),
                  Limits.DEFAULT,
                  16,
                  65536)
              .type("Compact");
      try (var compiled = decoder.compile(p, 65536)) {
        try (var leftInput = decoder.allocate(2).put(new byte[] {8, 1});
            var rightInput = decoder.allocate(2).put(new byte[] {8, 2});
            var left = compiled.decode(leftInput, 0);
            var right = compiled.decode(rightInput, 0);
            var sibling = compiled.decode(leftInput, 0)) {
          Acceptance.check(
              left.row().getLong(0) == 1 && right.row().getLong(0) == 2,
              "native scalar scratch is not retained by messages");
          var leftRow = left.row();
          leftInput.close();
          left.close();
          Acceptance.rejects(() -> leftRow.getLong(0), IllegalStateException.class);
          Acceptance.check(
              sibling.row().getLong(0) == 1 && right.row().getLong(0) == 2,
              "closing one message preserves sibling and separate-input views");
        }
        for (byte[] wire :
            List.of(new byte[] {8, 7}, new byte[] {8, 1, 8, 0}, new byte[] {8, 2, 16, 99, 24, 7})) {
          var expected = p.decode(wire);
          try (var input = decoder.allocate(wire.length).put(wire);
              var parsed = compiled.decode(input, 0)) {
            Acceptance.check(
                parsed.nativeIndexBytes() == 0, "scalar decode allocates no native index");
            var row = parsed.row();
            equal(expected, row);
            equal(expected, parsed.snapshot());
            Acceptance.check(
                Arrays.equals(wire, parsed.encode()), "binary forwarding preserves unknowns");
            Acceptance.check(
                row.has(0) == expected.has(0) && row.getLong(0) == expected.getLong(0),
                "compact presence and primitive");
            Acceptance.check(
                DATA.readLong(row, 0) == expected.getLong(0), "primitive library export");
            Acceptance.check(row.get(0).equals(expected.get(0)), "scalar object protocol");
            Acceptance.rejects(() -> row.getSize(0), ContractViolation.class);
            try {
              DATA.readDouble(row, 0);
              throw new AssertionError("Expected scalar double rejection");
            } catch (com.oracle.truffle.api.nodes.UnexpectedResultException failure) {
              Acceptance.check(failure.getResult().equals(expected.get(0)), "scalar double result");
            }
            try {
              DATA.readBoolean(row, 0);
              throw new AssertionError("Expected scalar boolean rejection");
            } catch (com.oracle.truffle.api.nodes.UnexpectedResultException failure) {
              Acceptance.check(
                  failure.getResult().equals(expected.get(0)), "scalar boolean result");
            }
            Acceptance.rejects(() -> row.getLong(2), IndexOutOfBoundsException.class);
            parsed.close();
            Acceptance.rejects(() -> row.getLong(0), IllegalStateException.class);
          }
        }
        try (var empty = decoder.allocate(0);
            var malformed = decoder.allocate(1).put(new byte[] {8})) {
          Acceptance.rejects(() -> compiled.decode(malformed, NATIVE), ContractViolation.class);
          if (explicit)
            Acceptance.rejects(() -> compiled.decode(empty, NATIVE), ContractViolation.class);
          else
            try (var parsed = compiled.decode(empty, NATIVE)) {
              Acceptance.check(
                  !parsed.row().has(0) && parsed.row().getLong(0) == 0, "implicit default");
            }
        }
        // A failed native parse must release the reusable result slot.
        try (var input = decoder.allocate(2).put(new byte[] {8, 9});
            var parsed = compiled.decode(input, 0)) {
          Acceptance.check(parsed.row().getLong(0) == 9, "reuse after failure");
        }
      }
    }
  }

  private static void mergedRepeated(BuffaWire decoder) {
    var numbers =
        new ProtoPlan.FieldPlan(
            "numbers", 1, ProtoPlan.Kind.INT32, true, false, false, false, false, -1, "", null, 0L);
    var child =
        new ProtoPlan.FieldPlan(
            "child",
            2,
            ProtoPlan.Kind.MESSAGE,
            false,
            false,
            true,
            false,
            false,
            -1,
            "RepeatedMerge",
            null,
            Missing.INSTANCE);
    var plan =
        new ProtoPlan.Model(
                null,
                List.of(new ProtoPlan.Type("RepeatedMerge", false, List.of(numbers, child))),
                Limits.DEFAULT,
                16,
                65536)
            .type("RepeatedMerge");
    byte[] merged = {18, 2, 8, 1, 18, 4, 8, 2, 8, 3};
    equal(plan.parseText("child { numbers: [1,2,3] }"), plan.decode(merged));
    var interleaved = new java.io.ByteArrayOutputStream();
    for (int i = 0; i < 33; i++) {
      interleaved.write(8);
      interleaved.write(i);
      interleaved.write(18);
      interleaved.write(0);
    }
    try (var compiled = decoder.compile(plan, 65536)) {
      for (byte[] wire : List.of(merged, interleaved.toByteArray())) {
        var expected = plan.decode(wire);
        try (var input = decoder.allocate(wire.length).put(wire);
            var parsed = compiled.decode(input, NATIVE)) {
          equal(expected, parsed.row());
          equal(expected, parsed.snapshot());
          input.close();
          equal(expected, parsed.row());
        }
      }
    }
  }

  /** Exercise both sides of the packed presence boundary, including the sign bit. */
  private static void presenceWidths(BuffaWire decoder) {
    for (int size : new int[] {0, 1, 63, 64, 65, 129}) {
      var fields = new ArrayList<ProtoPlan.FieldPlan>();
      for (int i = 0; i < size; i++) {
        fields.add(
            new ProtoPlan.FieldPlan(
                "f" + i,
                i + 1,
                ProtoPlan.Kind.INT32,
                false,
                false,
                true,
                false,
                false,
                size == 64 && i >= 62 ? 0 : -1,
                "",
                null,
                0L));
      }
      var plan =
          new ProtoPlan.Model(
                  null,
                  List.of(new ProtoPlan.Type("Presence", false, fields)),
                  Limits.DEFAULT,
                  16,
                  65536)
              .type("Presence");
      var builder = plan.newBuilder();
      // Explicitly present defaults must remain distinguishable from absent defaults.
      for (int i = 0; i < size; i++) if (i % 3 != 1) builder.set(i, 0L);
      var expected = builder.build();
      byte[] wire = plan.encode(expected);
      try (var compiled = decoder.compile(plan, 65536);
          var input = decoder.allocate(wire.length).put(wire);
          var parsed = compiled.decode(input, NATIVE)) {
        equal(expected, parsed.row());
        equal(expected, parsed.snapshot());
        var text = new StringBuilder();
        for (int i = 0; i < size; i++)
          if (expected.has(i)) text.append("f").append(i).append(": 0 ");
        try (var textual = compiled.parseText(text.toString(), NATIVE)) {
          equal(expected, textual.row());
          equal(expected, textual.snapshot());
        }
        for (int i = 0; i < size; i++) {
          Acceptance.check(parsed.row().has(i) == expected.has(i), "presence width " + size);
        }
        Acceptance.rejects(() -> parsed.row().has(-1), IndexOutOfBoundsException.class);
        Acceptance.rejects(() -> parsed.row().has(size), IndexOutOfBoundsException.class);
        if (size == 64) {
          byte[] first = plan.encode(plan.newBuilder().set(62, 1L).build());
          byte[] second = plan.encode(plan.newBuilder().set(63, 0L).build());
          byte[] merged = Arrays.copyOf(first, first.length + second.length);
          System.arraycopy(second, 0, merged, first.length, second.length);
          try (var switchedInput = decoder.allocate(merged.length).put(merged);
              var switched = compiled.decode(switchedInput, NATIVE)) {
            Acceptance.check(
                !switched.row().has(62) && switched.row().has(63),
                "oneof clears and sets top presence bits");
            equal(plan.decode(merged), switched.snapshot());
          }
        }
      }
    }
  }

  private static void scopedRowsRequireDetachmentBeforeEnteringOwnedContainers(BuffaWire decoder) {
    var p = plan("Sample");
    ProtoRow detached;
    Batch retained;
    try (var compiled = decoder.compile(p, 65536);
        var message = compiled.parseText("child { a: 7 b: 'owned later' }", NATIVE)) {
      var row = message.row();
      var child = (TypedRow) DATA.read(row, p.schema().indexOf("child"));
      for (Object scoped : List.of(row, child)) {
        for (Field.Kind kind : List.of(Field.Kind.RECORD, Field.Kind.ANY)) {
          var field = new Field("row", kind, false, false);
          // Guest-side contract checking accepts valid scoped values; host ownership does not.
          Acceptance.check(Values.validate(field, scoped) == scoped, "scoped field validation");
          Acceptance.rejects(() -> Values.normalize(scoped), ContractViolation.class);
          Acceptance.rejects(() -> Values.requireInterop(scoped), ContractViolation.class);
          Acceptance.rejects(() -> new DataArray(new Object[] {scoped}), ContractViolation.class);
          Acceptance.rejects(() -> DataArray.copyOf(List.of(scoped)), ContractViolation.class);
          Acceptance.rejects(
              () ->
                  Batch.copyOf(
                      Schema.of(field),
                      Collections.singletonList(new Object[] {scoped}),
                      Limits.DEFAULT),
              ContractViolation.class);
        }
      }
      int ordinal = p.schema().indexOf("child");
      Acceptance.rejects(() -> p.newBuilder().set(ordinal, child), ContractViolation.class);
      detached = message.snapshot();
      retained =
          Batch.copyOf(
              Schema.of(new Field("row", Field.Kind.RECORD, false, false)),
              Collections.singletonList(new Object[] {detached}),
              Limits.DEFAULT);
      // Snapshot storage accepts nested detached rows through the same ownership paths.
      Values.requireInterop(detached);
      new DataArray(new Object[] {detached});
    }
    Acceptance.check(
        DATA.read(retained.row(0), 0) == detached, "detached ownership survives close");
    var child = (ProtoRow) detached.get(p.schema().indexOf("child"));
    Acceptance.check(
        child.getLong(0) == 7 && child.get(1).equals("owned later"),
        "nested detached contents survive close");
  }

  static TypedMessage retainedUnicode(BuffaWire decoder) {
    var p = plan("Sample");
    byte[] wire =
        p.encode(p.newBuilder().set(p.schema().indexOf("text"), "é中😀".repeat(1024)).build());
    try (var compiled = decoder.compile(p, 65536);
        var input = decoder.allocate(wire.length).put(wire)) {
      return compiled.decode(input, NATIVE);
    }
  }

  private static void textRejectsMalformedUtf16BeforeEncoding(BuffaWire decoder) {
    var p = plan("Sample");
    try (var compiled = decoder.compile(p, 65536)) {
      for (String malformed :
          List.of(
              String.valueOf((char) 0xd800),
              String.valueOf((char) 0xdc00),
              "prefix" + (char) 0xd800 + "suffix")) {
        Acceptance.rejects(
            () -> compiled.parseText("text: '" + malformed + "'", NATIVE), ContractViolation.class);
        Acceptance.rejects(
            () -> compiled.parseJson("{\"text\":\"" + malformed + "\"}", NATIVE),
            ContractViolation.class);
      }
      Acceptance.rejects(() -> compiled.parseText("text: 'valid'", -1), ContractViolation.class);
      Acceptance.rejects(
          () -> compiled.parseJson("{\"text\":\"valid\"}", -1), ContractViolation.class);
      try (var parsed = compiled.parseJson("{\"text\":\"é中😀\"}", NATIVE)) {
        Acceptance.check(
            parsed.row().get(p.schema().indexOf("text")).equals("é中😀"),
            "failed text parsing leaves the plan reusable");
      }
    }
    // Three-byte BMP characters can exceed the wire limit despite satisfying the UTF-16 limit.
    var tiny =
        TruffleDataContracts.create(null, Limits.DEFAULT, 32, 32)
            .type("truffledata.lossless.Sample");
    try (var compiled = decoder.compile(tiny, 65536)) {
      Acceptance.rejects(
          () -> compiled.parseText("text:'" + "中".repeat(9) + "'", NATIVE),
          ContractViolation.class);
    }
    var jsonTiny =
        TruffleDataContracts.create(null, Limits.DEFAULT, 32, 64)
            .type("truffledata.lossless.Sample");
    try (var compiled = decoder.compile(jsonTiny, 65536)) {
      Acceptance.rejects(
          () -> compiled.parseJson("{\"text\":\"" + "中".repeat(21) + "\"}", NATIVE),
          ContractViolation.class);
    }
  }

  private static void unicode(BuffaWire decoder) {
    var field =
        new ProtoPlan.FieldPlan(
            "text", 1, ProtoPlan.Kind.STRING, false, false, false, false, false, -1, "", null, "");
    var p =
        new ProtoPlan.Model(
                null,
                List.of(new ProtoPlan.Type("Unicode", false, List.of(field))),
                Limits.DEFAULT,
                8,
                1000000)
            .type("Unicode");
    try (var compiled = decoder.compile(p, 65536)) {
      for (int length : new int[] {0, 1, 63, 64, 65, 255, 256, 257, 4096}) {
        for (String pattern : List.of("a\u0000", "é", "中", "😀", "aé中😀")) {
          String text = pattern.repeat(length);
          byte[] wire = p.encode(p.newBuilder().set(0, text).build());
          // The first read trains lazy materialization; subsequent decodes use the eager route.
          for (int attempt = 0; attempt < 2; attempt++) {
            var input = decoder.allocate(wire.length).put(wire);
            try (var parsed = compiled.decode(input, NATIVE)) {
              input.close();
              Acceptance.check(
                  parsed.row().get(0).equals(text), "Unicode input ownership and content");
              Acceptance.check(parsed.row().get(0).equals(text), "cached Unicode content");
              Acceptance.check(parsed.snapshot().get(0).equals(text), "Unicode snapshot");
              var row = parsed.row();
              parsed.close();
              Acceptance.rejects(() -> row.get(0), IllegalStateException.class);
            } finally {
              input.close();
            }
          }
        }
      }
    }
    String text = "中😀".repeat(64);
    byte[] wire = p.encode(p.newBuilder().set(0, text).build());
    for (long bytes : new long[] {2L * text.length(), 2L * text.length() - 1}) {
      var bounded =
          new ProtoPlan.Model(
                  null,
                  List.of(new ProtoPlan.Type("Unicode", false, List.of(field))),
                  new Limits(1, 1, 2, bytes),
                  8,
                  1000000)
              .type("Unicode");
      try (var compiled = decoder.compile(bounded, 65536);
          var input = decoder.allocate(wire.length).put(wire)) {
        if (bytes == 2L * text.length()) {
          try (var parsed = compiled.decode(input, NATIVE)) {
            Acceptance.check(parsed.row().get(0).equals(text), "exact UTF-16 payload budget");
          }
        } else Acceptance.rejects(() -> compiled.decode(input, NATIVE), ContractViolation.class);
      }
    }
  }

  private static void indexReusePreservesMessagesAndBudgets(BuffaWire decoder) {
    var plan = plan("Sample");
    var expected = plan.parseText("count: 7 text: 'unread €' numbers: [1, 2, 3] child { a: 42 }");
    byte[] wire = plan.encode(expected);
    try (var compiled = decoder.compile(plan, 65536);
        var input = decoder.allocate(wire.length).put(wire);
        var first = compiled.decode(input, NATIVE)) {
      input.close(); // First message now owns its input independently.
      try (var other =
          compiled.parseJson("{\"count\":9,\"text\":\"other\",\"numbers\":[8]}", NATIVE)) {
        Acceptance.check(other.row().getLong(0) == 9, "reused index has fresh values");
      }
      try (var malformed = decoder.allocate(2).put(new byte[] {8, (byte) 128})) {
        Acceptance.rejects(() -> compiled.decode(malformed, NATIVE), ContractViolation.class);
      }
      try (var valid = decoder.allocate(wire.length).put(wire)) {
        if (decoder.selected() != BuffaWire.Selected.JAVA)
          Acceptance.rejects(() -> compiled.decode(valid, 1), ContractViolation.class);
        try (var recovered = compiled.decode(valid, NATIVE)) {
          Acceptance.check(recovered.row().getLong(0) == 7, "reuse recovers after failure");
        }
        // Switching to the generic API must release any cached typed index before publishing
        // another native result into the shared result slot.
        try (var generic = decoder.decode(valid, Limits.DEFAULT, 16, NATIVE, NATIVE)) {
          Acceptance.check(generic.fields().size() > 0, "generic decode after typed reuse");
        }
        try (var again = compiled.decode(valid, NATIVE)) {
          Acceptance.check(again.row().getLong(0) == 7, "typed decode after generic result");
        }
      }
      try (var emptyInput = decoder.allocate(0);
          var empty = compiled.decode(emptyInput, 0)) {
        Acceptance.check(empty.nativeIndexBytes() == 0, "zero budget discards cached capacity");
        Acceptance.check(!empty.row().has(0), "empty decode does not inherit presence");
      }
      compiled.close();
      equal(expected, first.row());
      equal(expected, first.snapshot());
    }
  }

  static void run(BuffaWire decoder) throws Exception {
    indexReusePreservesMessagesAndBudgets(decoder);
    scopedRowsRequireDetachmentBeforeEnteringOwnedContainers(decoder);
    textRejectsMalformedUtf16BeforeEncoding(decoder);
    unicode(decoder);
    compactScalars(decoder);
    generatedGetters(decoder);
    mergedRepeated(decoder);
    presenceWidths(decoder);
    unreadStrings(decoder);
    stringsRemainFreshAfterProfileWarmup(decoder);
    ProtoPlan plan = plan("Sample");
    Acceptance.rejects(() -> decoder.compile(plan, 1), ContractViolation.class);
    Acceptance.rejects(() -> decoder.compile(plan, 64), ContractViolation.class);
    try (var nativePlan = decoder.compile(plan, 65536)) {
      for (String text :
          List.of(
              "",
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
              "{}",
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
        // Empty storage may be shared, but the view must retain its owner's lifetime.
        var emptyMessage = nativePlan.parseText("", NATIVE);
        var emptyNumbers = (BorrowedArray) DATA.read(emptyMessage.row(), 8);
        Acceptance.check(emptyNumbers.size() == 0, "empty repeated field");
        for (int ordinal = 0; ordinal < plan.size(); ordinal++) {
          if (plan.field(ordinal).repeated()) {
            Acceptance.check(
                DATA.read(emptyMessage.row(), ordinal) == emptyNumbers,
                "one empty view per decoded owner");
          }
        }
        var secondMessage = nativePlan.parseText("", NATIVE);
        var independentEmpty = (BorrowedArray) DATA.read(secondMessage.row(), 8);
        var emptySnapshot = emptyNumbers.snapshot();
        emptyMessage.close();
        Acceptance.rejects(emptyNumbers::size, IllegalStateException.class);
        Acceptance.check(emptySnapshot.size() == 0, "detached empty repeated field");
        Acceptance.check(
            independentEmpty.size() == 0, "empty arrays keep independent message leases");
        secondMessage.close();
        Acceptance.rejects(independentEmpty::size, IllegalStateException.class);
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
      // This is a data-budget test. Unrelated generated fixtures must not exhaust the
      // descriptor budget before any data is decoded.
      Map<String, ProtoPlan.Type> definitions = new LinkedHashMap<>();
      var pending = new ArrayDeque<ProtoPlan>();
      pending.add(plan("Sample"));
      while (!pending.isEmpty()) {
        var current = pending.removeFirst();
        if (definitions.putIfAbsent(current.name(), current.definition()) != null) continue;
        for (int i = 0; i < current.size(); i++)
          if (current.field(i).kind().message()) pending.add(current.nested(i));
      }
      var limited =
          new ProtoPlan.Model(
                  null,
                  List.copyOf(definitions.values()),
                  new Limits(1024, 256, 70, 10000),
                  32,
                  10000)
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
        try (var accepted = compiled.parseText("children {key: 'a'}", NATIVE)) {
          Acceptance.check(accepted.row().getSize(6) == 1, "small map fits retained data budget");
        }
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

  private static void unreadStrings(BuffaWire decoder) {
    var plan = plan("Sample");
    String text = "text: 'hello € 🦊' child { b: 'nested λ' }";
    String json = "{\"text\":\"hello € 🦊\",\"child\":{\"b\":\"nested λ\"}}";
    var expected = plan.parseText(text);
    byte[] wire = plan.encode(expected);
    int ordinal = plan.schema().indexOf("text");
    try (var nativePlan = decoder.compile(plan, 65536)) {
      for (int format = 0; format < 3; format++) {
        try (var input = decoder.allocate(wire.length).put(wire);
            var parsed =
                switch (format) {
                  case 0 -> nativePlan.decode(input, NATIVE);
                  case 1 -> nativePlan.parseText(text, NATIVE);
                  default -> nativePlan.parseJson(json, NATIVE);
                }) {
          var row = parsed.row();
          input.close();
          Object first = DATA.read(row, ordinal);
          Acceptance.check(first.equals("hello € 🦊"), "first string read after input closes");
          Acceptance.check(DATA.read(row, ordinal) == first, "string materializes once");
          // The nested string has not been read: snapshot must resolve it while retaining safety.
          var detached = parsed.snapshot();
          parsed.close();
          Acceptance.rejects(() -> DATA.read(row, ordinal), IllegalStateException.class);
          equal(expected, detached);
        }
      }
    }
  }

  private static void stringsRemainFreshAfterProfileWarmup(BuffaWire decoder) {
    var plan = plan("Sample");
    int ordinal = plan.schema().indexOf("text");
    try (var compiled = decoder.compile(plan, 65536)) {
      for (String value : List.of("first", "second", "", "third 🦊")) {
        try (var parsed = compiled.parseJson("{\"text\":\"" + value + "\"}", NATIVE)) {
          Acceptance.check(
              DATA.read(parsed.row(), ordinal).equals(value), "profile does not cache data");
        }
      }
    }
    try (var compiled = decoder.compile(plan, 65536);
        var parsed = compiled.parseText("text: 'still alive €'", NATIVE)) {
      var row = parsed.row();
      compiled.close();
      Acceptance.check(
          DATA.read(row, ordinal).equals("still alive €"), "unread string outlives plan");
    }
  }

  static void equal(Object expected, Object actual) {
    if (expected instanceof AbstractRow a && actual instanceof AbstractRow b) {
      Acceptance.check(a.schema().size() == b.schema().size(), "typed schema width");
      for (int i = 0; i < a.schema().size(); i++) {
        equal(DATA.read(a, i), DATA.read(b, i));
        if (a instanceof ProtoRow proto && b instanceof TypedRow typed)
          Acceptance.check(proto.has(i) == typed.has(i), "typed presence " + i);
        if (a instanceof ProtoRow proto && b instanceof ProtoRow snapshot)
          Acceptance.check(proto.has(i) == snapshot.has(i), "snapshot presence " + i);
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
