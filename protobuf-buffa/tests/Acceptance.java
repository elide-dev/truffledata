package dev.elide.truffledata.buffa.tests;

import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.*;
import dev.elide.truffledata.protobuf.*;
import java.lang.foreign.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.nativeimage.*;
import org.graalvm.nativeimage.c.type.*;
import org.jspecify.annotations.Nullable;

/** Shared JVM and SVM acceptance: execute the same ownership and semantic checks in both images. */
public final class Acceptance {
  private static final DataLibrary DATA = DataLibrary.factory().getUncached();
  private static final Limits LIMITS = new Limits(1024, 256, 4_000_000, 8_000_000);
  private static final byte[] SAMPLE = {
    8, (byte) 150, 1, 18, 3, 1, 2, (byte) 255, 27, 32, 9, 28, 45, 1, 2, 3, 4
  };

  private Acceptance() {}

  public static void main(String[] args) throws Exception {
    boolean nativeExpected = args.length > 0 && args[0].equals("native");
    Path library = args.length > 1 ? Path.of(args[1]) : null;
    run(nativeExpected, library);
    try (var decoder =
        BuffaWire.open(nativeExpected ? BuffaWire.Mode.NATIVE : BuffaWire.Mode.AUTO, library)) {
      allocation(decoder);
      CompiledReads.cachedLifetimes(decoder);
      CompiledReads.run(decoder);
    }
  }

  static void run(boolean nativeExpected, @Nullable Path library) throws Exception {
    try (var decoder =
        BuffaWire.open(nativeExpected ? BuffaWire.Mode.NATIVE : BuffaWire.Mode.AUTO, library)) {
      check((decoder.selected() != BuffaWire.Selected.JAVA) == nativeExpected, "backend selection");
      TypedAcceptance.run(decoder);
      WellKnownAcceptance.run(decoder);
      typed(decoder);
      parity(decoder);
      budgets(decoder);
      ownership(decoder);
      randomized(decoder);
      if (nativeExpected) adoption(decoder);
      try (var input = decoder.allocate(SAMPLE.length).put(SAMPLE);
          var message = decoder.decode(input, LIMITS, 16, 100, 4096);
          var unicode = TypedAcceptance.retainedUnicode(decoder)) {
        decoder.close();
        check(
            unicode
                .row()
                .get(unicode.row().plan().schema().indexOf("text"))
                .equals("é中😀".repeat(1024)),
            "bulk Unicode remains readable after input, plan and decoder closure");
        equal(ProtoWire.decode(SAMPLE, LIMITS, 16, 100), message.snapshot());
        rejects(() -> decoder.allocate(1), IllegalStateException.class);
      }
    }
    try (var java = BuffaWire.open(BuffaWire.Mode.JAVA, Path.of("/does/not/exist"))) {
      check(java.selected() == BuffaWire.Selected.JAVA, "explicit Java");
      parity(java);
    }
    if (!nativeExpected)
      rejects(
          () -> BuffaWire.open(BuffaWire.Mode.NATIVE, null), UnsupportedOperationException.class);
    if (nativeExpected) NativeTapeAcceptance.run(library);
    System.out.println(
        "BUFFA acceptance passed native=" + nativeExpected + " svm=" + ImageInfo.inImageCode());
  }

  static void typed(BuffaWire decoder) {
    var field =
        new ProtoPlan.FieldPlan(
            "count", 1, ProtoPlan.Kind.INT32, false, false, false, false, false, -1, "", null, 0L);
    var plan =
        new ProtoPlan.Model(
                null, List.of(new ProtoPlan.Type("Typed", false, List.of(field))), LIMITS, 16, 1024)
            .type("Typed");
    byte[] wire = plan.encode(plan.parseText("count: 150"));
    check(
        Arrays.equals(wire, plan.encode(plan.parseJson("{\"count\":150}"))),
        "descriptor-free JSON");
    ProtoRow row;
    try (var input = decoder.allocate(wire.length).put(wire)) {
      row = decoder.decodeTyped(input, plan);
      rejects(() -> input.put(0, (byte) 0), IllegalStateException.class);
    }
    check(DATA.read(row, 0).equals(150L), "typed row owns storage after input closes");
    var invalid = decoder.allocate(1).put(new byte[] {8});
    rejects(() -> decoder.decodeTyped(invalid, plan), ContractViolation.class);
    invalid.close();
    rejects(() -> decoder.decodeTyped(invalid, plan), IllegalStateException.class);
  }

  static void parity(BuffaWire decoder) {
    try (var input = decoder.allocate(SAMPLE.length).put(SAMPLE);
        var message = decoder.decode(input, LIMITS, 16, 100, 4096)) {
      equal(ProtoWire.decode(SAMPLE, LIMITS, 16, 100), message.snapshot());
      check(message.fields().size() == 4, "wire order and groups");
      check(message.nativeIndexBytes() <= 4096, "bounded native index");
      Object payload = DATA.read(message.fields().get(1), 2);
      var interop = com.oracle.truffle.api.interop.InteropLibrary.getUncached();
      check(interop.hasArrayElements(payload), "binary interop");
      try {
        check(interop.getArraySize(payload) == 3, "binary size");
        check(interop.isArrayElementReadable(payload, 2), "readable byte");
        check(!interop.isArrayElementReadable(payload, 3), "byte bounds");
        check(((Long) interop.readArrayElement(payload, 2)) == 255, "binary interop read");
        try {
          interop.readArrayElement(payload, 3);
          throw new AssertionError("Expected invalid byte index");
        } catch (com.oracle.truffle.api.interop.InvalidArrayIndexException expected) {
        }
      } catch (com.oracle.truffle.api.interop.InteropException failure) {
        throw new AssertionError(failure);
      }

      rejects(() -> input.put(0, (byte) 0), IllegalStateException.class);
    }
  }

  static void randomized(BuffaWire decoder) {
    var random = new Random(714);
    for (int iteration = 0; iteration < 200; iteration++) {
      byte[] wire = new byte[2 + random.nextInt(64)];
      wire[0] = 18;
      wire[1] = (byte) (wire.length - 2);
      for (int i = 2; i < wire.length; i++) wire[i] = (byte) random.nextInt();
      try (var input = decoder.allocate(wire.length).put(wire);
          var message = decoder.decode(input, LIMITS, 16, 1024, 4096)) {
        equal(ProtoWire.decode(wire, LIMITS, 16, 1024), message.snapshot());
      }
    }
    for (int iteration = 0; iteration < 1000; iteration++) {
      byte[] wire = new byte[random.nextInt(96)];
      random.nextBytes(wire);
      try (var input = decoder.allocate(wire.length).put(wire)) {
        DataArray expected;
        try {
          expected = ProtoWire.decode(wire, LIMITS, 16, 1024);
        } catch (ContractViolation malformed) {
          rejects(() -> decoder.decode(input, LIMITS, 16, 1024, 16384), ContractViolation.class);
          continue;
        }
        try (var message = decoder.decode(input, LIMITS, 16, 1024, 16384)) {
          equal(expected, message.snapshot());
        }
      }
    }
    for (byte[] wire :
        List.of(
            new byte[] {8, -1, -1, -1, -1, -1, -1, -1, -1, -1, 1},
            new byte[] {9, -1, -1, -1, -1, -1, -1, -1, -1},
            new byte[] {11, 19, 24, 42, 20, 12})) {
      try (var input = decoder.allocate(wire.length).put(wire);
          var message = decoder.decode(input, LIMITS, 16, 1024, 4096)) {
        equal(ProtoWire.decode(wire, LIMITS, 16, 1024), message.snapshot());
      }
    }
    for (byte[] wire :
        List.of(
            new byte[] {0},
            new byte[] {8, (byte) 128},
            new byte[] {11, 20},
            new byte[] {11},
            new byte[] {12},
            new byte[] {18, 4, 0},
            new byte[] {9, 0},
            new byte[] {8, -1, -1, -1, -1, -1, -1, -1, -1, -1, 2})) {
      try (var input = decoder.allocate(wire.length).put(wire)) {
        rejects(() -> decoder.decode(input, LIMITS, 16, 1024, 4096), ContractViolation.class);
      }
    }
  }

  static void budgets(BuffaWire decoder) {
    rejects(() -> decoder.allocate(-1), IllegalArgumentException.class);
    try (var incremental = decoder.allocate(2)) {
      rejects(() -> incremental.put(new byte[1]), IllegalArgumentException.class);
      incremental.put(0, (byte) 8).put(1, (byte) 42);
      try (var message = decoder.decode(incremental, LIMITS, 1, 2, 128)) {
        equal(ProtoWire.decode(new byte[] {8, 42}, LIMITS, 1, 2), message.snapshot());
        rejects(() -> DATA.read(message.fields().get(0), 3), IndexOutOfBoundsException.class);
      }
    }
    try (var input = decoder.allocate(SAMPLE.length).put(SAMPLE)) {
      rejects(() -> decoder.decode(input, LIMITS, 16, 1, 4096), ContractViolation.class);
      rejects(
          () -> decoder.decode(input, new Limits(10, 3, 4, 100), 16, 100, 4096),
          ContractViolation.class);
      rejects(() -> decoder.decode(input, LIMITS, 1, 100, 4096), ContractViolation.class);
      if (decoder.selected() != BuffaWire.Selected.JAVA)
        rejects(() -> decoder.decode(input, LIMITS, 16, 100, 31), ContractViolation.class);
      try (var message = decoder.decode(input, LIMITS, 16, 100, 4096)) {
        equal(ProtoWire.decode(SAMPLE, LIMITS, 16, 100), message.snapshot());
      }
    }
    try (var empty = decoder.allocate(0);
        var message = decoder.decode(empty, LIMITS, 1, 0, 0)) {
      check(message.fields().size() == 0, "empty");
    }
  }

  static void ownership(BuffaWire decoder) throws Exception {
    var input = decoder.allocate(SAMPLE.length).put(SAMPLE);
    var message = decoder.decode(input, LIMITS, 16, 100, 4096);
    var second = decoder.decode(input, LIMITS, 16, 100, 4096);
    var snapshot = message.snapshot();
    Object bytes = DATA.read(message.fields().get(1), 2);
    input.close();
    input.close();
    equal(snapshot, message.snapshot());
    rejects(() -> input.size(), IllegalStateException.class);
    AtomicInteger rejected = new AtomicInteger();
    Thread thread =
        new Thread(
            () -> {
              try {
                message.fields();
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
            });
    thread.start();
    thread.join();
    check(rejected.get() == 1, "thread confinement");
    message.close();
    message.close();
    rejects(() -> message.fields(), IllegalStateException.class);
    if (bytes instanceof BorrowedBinary view) {
      rejects(() -> view.get(0), IllegalStateException.class);
      rejects(() -> Values.normalize(view), ContractViolation.class);
      rejects(() -> new DataArray(new Object[] {view}), ContractViolation.class);
    }
    equal(snapshot, second.snapshot());
    second.close();
    equal(ProtoWire.decode(SAMPLE, LIMITS, 16, 100), snapshot);
  }

  static void adoption(BuffaWire decoder) {
    AtomicInteger freed = new AtomicInteger();
    WireInput input;
    if (ImageInfo.inImageCode()) {
      CCharPointer p = UnmanagedMemory.malloc(SAMPLE.length);
      for (int i = 0; i < SAMPLE.length; i++) p.write(i, SAMPLE[i]);
      long address = p.rawValue();
      input =
          BuffaSvm.adopt(
              address,
              SAMPLE.length,
              () -> {
                UnmanagedMemory.free(org.graalvm.word.WordFactory.pointer(address));
                freed.incrementAndGet();
              });
    } else {
      Arena arena = Arena.ofConfined();
      MemorySegment memory = arena.allocate(SAMPLE.length);
      MemorySegment.copy(SAMPLE, 0, memory, ValueLayout.JAVA_BYTE, 0, SAMPLE.length);
      input =
          BuffaJvm.adopt(
              memory,
              () -> {
                arena.close();
                freed.incrementAndGet();
              });
    }
    var view = decoder.decode(input, LIMITS, 16, 100, 4096);
    input.close();
    check(freed.get() == 0, "view retains producer memory");
    check(((BorrowedBinary) DATA.read(view.fields().get(1), 2)).get(2) == 255, "borrowed read");
    view.close();
    check(freed.get() == 1, "last lease releases exactly once");
  }

  private static volatile @Nullable Object sink;

  static void allocation(BuffaWire decoder) {
    int length = 65536;
    byte[] wire = new byte[length + 4];
    wire[0] = 18;
    wire[1] = (byte) 128;
    wire[2] = (byte) 128;
    wire[3] = 4;
    var field =
        new ProtoPlan.FieldPlan(
            "blob",
            2,
            ProtoPlan.Kind.BYTES,
            false,
            false,
            false,
            false,
            false,
            -1,
            "",
            null,
            DataArray.copyOfBytes(java.nio.ByteBuffer.allocate(0)));
    var typed =
        new ProtoPlan.Model(
                null,
                List.of(new ProtoPlan.Type("Payload", false, List.of(field))),
                LIMITS,
                16,
                wire.length)
            .type("Payload");
    try (var input = decoder.allocate(wire.length).put(wire)) {
      for (int i = 0; i < 100; i++) sink = decoder.decodeTyped(input, typed);
      for (int i = 0; i < 100; i++)
        try (var message = decoder.decode(input, LIMITS, 16, wire.length, 128)) {
          sink = message.fields();
        }
      if (!ImageInfo.inImageCode() && decoder.selected() != BuffaWire.Selected.JAVA) {
        var bean =
            (com.sun.management.ThreadMXBean)
                java.lang.management.ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 100; i++)
          try (var message = decoder.decode(input, LIMITS, 16, wire.length, 128)) {
            sink = message.fields();
            check(message.nativeIndexBytes() <= 128, "bounded native index");
          }
        double allocated = (bean.getThreadAllocatedBytes(thread) - before) / 100.0;
        System.out.println("BUFFA borrowed ingestion bytes/message=" + allocated);
        check(allocated < 16384, "borrowed ingestion must not copy the 64KiB payload");
        before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 100; i++) sink = decoder.decodeTyped(input, typed);
        double typedBytes = (bean.getThreadAllocatedBytes(thread) - before) / 100.0;
        System.out.println("BUFFA owned typed ingestion bytes/message=" + typedBytes);
        check(
            typedBytes < length + 16384,
            "typed ingestion copies payload once, not the whole input too");
      }
    }
  }

  static void equal(Object a, Object b) {
    if (a instanceof DataArray x && b instanceof DataArray y) {
      check(x.size() == y.size(), "array size");
      for (int i = 0; i < x.size(); i++) equal(x.get(i), y.get(i));
    } else if (a instanceof AbstractRow && b instanceof AbstractRow) {
      for (int i = 0; i < 3; i++) equal(DATA.read(a, i), DATA.read(b, i));
    } else if (a instanceof ExactNumber x && b instanceof ExactNumber y)
      check(x.decimal().compareTo(y.decimal()) == 0, "unsigned value");
    else check(a.equals(b), "value " + a + " != " + b);
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  static void rejects(Runnable action, Class<? extends Throwable> type) {
    try {
      action.run();
    } catch (Throwable failure) {
      if (type.isInstance(failure)) return;
      throw new AssertionError("Wrong failure", failure);
    }
    throw new AssertionError("Expected " + type.getName());
  }
}
