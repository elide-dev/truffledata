package dev.elide.truffledata.buffa.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.*;
import dev.elide.truffledata.buffa.generated.BuffaAbi;
import dev.elide.truffledata.protobuf.*;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

final class AcceptanceTest {
  static Path library() throws Exception {
    Path root =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "protobuf-buffa/native");
    try (var files = Files.list(root)) {
      return files
          .filter(p -> p.toString().endsWith(".so") || p.toString().endsWith(".dylib"))
          .findFirst()
          .orElseThrow();
    }
  }

  @Test
  void adoptionRejectsInvalidMemoryWithoutTakingOwnership() {
    var released = new java.util.concurrent.atomic.AtomicInteger();
    Acceptance.rejects(
        () ->
            BuffaJvm.adopt(
                java.lang.foreign.MemorySegment.ofArray(new byte[1]), released::incrementAndGet),
        IllegalArgumentException.class);
    Acceptance.rejects(
        () -> BuffaJvm.adopt(java.lang.foreign.MemorySegment.NULL, released::incrementAndGet),
        IllegalArgumentException.class);
    var arena = java.lang.foreign.Arena.ofConfined();
    var closed = arena.allocate(1);
    arena.close();
    Acceptance.rejects(
        () -> BuffaJvm.adopt(closed, released::incrementAndGet), IllegalArgumentException.class);
    Acceptance.rejects(
        () -> BuffaSvm.adopt(1, 1, released::incrementAndGet), UnsupportedOperationException.class);
    Acceptance.check(released.get() == 0, "rejected adoption leaves ownership with producer");
  }

  @Test
  void projectedOwningValidationFailureReleasesAdoptedInput() throws Exception {
    var released = new java.util.concurrent.atomic.AtomicInteger();
    var contract = TypedAcceptance.projectedBudgetPlan();
    var projection =
        dev.elide.truffledata.protobuf.Projection.builder(contract)
            .select("id", "text", "blob")
            .build()
            .lazyChildren()
            .borrowBytes(1);
    try (var decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, library());
        var compiled = decoder.compile(contract, 65536);
        var selected = compiled.project(projection);
        var arena = java.lang.foreign.Arena.ofConfined()) {
      byte[] invalid = {18, 1, 120, 34, 0};
      var segment = arena.allocate(invalid.length);
      segment.copyFrom(java.lang.foreign.MemorySegment.ofArray(invalid));
      try (var input = BuffaJvm.adopt(segment, released::incrementAndGet)) {
        Acceptance.rejects(
            () -> selected.decode(input, 65536), dev.elide.truffledata.ContractViolation.class);
        Acceptance.check(released.get() == 0, "caller still owns failed input");
      }
      Acceptance.check(released.get() == 1, "failed owning validation released its source lease");
      var recovered = selected.decode(new byte[] {8, 7, 34, 0}, 65536);
      Acceptance.check(
          recovered.getLong(0) == 7, "cached projected session recovers after failure");
    }
    Acceptance.check(released.get() == 1, "adopted source released exactly once");
  }

  @Test
  void cachedLifetimeChecksSurviveCloseAndCacheSaturation() throws Exception {
    try (var decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, library())) {
      CompiledReads.cachedLifetimes(decoder);
    }
  }

  @Test
  void nativeJvmAcceptance() throws Exception {
    Acceptance.run(true, library());
  }

  @Test
  void javaFallbackAcceptance() throws Exception {
    Acceptance.run(false, null);
  }

  @Test
  void unavailableLibraryFallsBackOnlyBeforeParsing() throws Exception {
    try (var decoder = BuffaWire.open(BuffaWire.Mode.AUTO, Path.of("/does/not/exist"))) {
      Acceptance.check(decoder.selected() == BuffaWire.Selected.JAVA, "missing native library");
    }
    try (var decoder = BuffaWire.open(BuffaWire.Mode.AUTO, library())) {
      Acceptance.check(
          decoder.selected() == BuffaWire.Selected.JAVA, "JVM AUTO selects Java slab producer");
      Acceptance.randomized(decoder);
    }
  }

  @Test
  void jvmNativeSelectionCanBeExplicitlyEnabled() throws Exception {
    String previous = System.getProperty("truffledata.buffa.jvmNative");
    try {
      System.setProperty("truffledata.buffa.jvmNative", "true");
      try (var decoder = BuffaWire.open(BuffaWire.Mode.AUTO, library())) {
        Acceptance.check(decoder.selected() == BuffaWire.Selected.JVM_FFM, "JVM native override");
      }
    } finally {
      if (previous == null) System.clearProperty("truffledata.buffa.jvmNative");
      else System.setProperty("truffledata.buffa.jvmNative", previous);
    }
  }

  @Test
  @org.junit.jupiter.api.Tag("allocation")
  void nativeAllocation() throws Exception {
    try (var decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, library())) {
      Acceptance.allocation(decoder);
      WellKnownAcceptance.allocation(decoder);
      var bean =
          (com.sun.management.ThreadMXBean)
              java.lang.management.ManagementFactory.getThreadMXBean();
      bean.setThreadAllocatedMemoryEnabled(true);
      long thread = Thread.currentThread().threadId();
      byte[] wire = new byte[65540];
      wire[0] = 26;
      wire[1] = (byte) 128;
      wire[2] = (byte) 128;
      wire[3] = 4;
      try (var plan = decoder.compile(TypedAcceptance.plan("Sample"), 65536);
          var input = decoder.allocate(wire.length).put(wire)) {
        for (int i = 0; i < 2000; i++)
          try (var message = plan.decode(input, 131072)) {
            message.row();
          }
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1000; i++)
          try (var message = plan.decode(input, 131072)) {
            message.row();
          }
        long perMessage = (bean.getThreadAllocatedBytes(thread) - before) / 1000;
        System.out.println("BUFFA typed bytes/message=" + perMessage);
        Acceptance.check(perMessage < 100000, "owning slab retains one payload copy");
      }
    }
  }

  @Test
  @org.junit.jupiter.api.Tag("jit")
  void compiledNativeReads() throws Exception {
    try (var decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, library())) {
      CompiledReads.run(decoder);
    }
  }

  @Test
  void wrongAbiDoesNotFallBack() {
    Path library = runfile("protobuf-buffa/tests/wrong_abi.so");
    Acceptance.rejects(
        () -> BuffaWire.open(BuffaWire.Mode.NATIVE, library), IllegalStateException.class);
  }

  static Path runfile(String path) {
    return Path.of(
        Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
        Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
        path);
  }

  static String fingerprintOf(String abiClass) throws Exception {
    return Files.readString(
            runfile("protobuf-buffa/native/generated/abi/" + abiClass + "/seam.abi"))
        .strip();
  }

  /** Myna 0.14.0: the bindings carry each ABI class's fingerprint; Linux AArch64 selects its own. */
  @Test
  void abiClassFingerprintsFollowTheGeneratedClasses() throws Exception {
    assertEquals(fingerprintOf("lp64_little"), BuffaAbi.LP64_LITTLE);
    assertEquals(fingerprintOf("lp64_little_aapcs64"), BuffaAbi.LP64_LITTLE_AAPCS64);
    assertNotEquals(BuffaAbi.LP64_LITTLE, BuffaAbi.LP64_LITTLE_AAPCS64);
    assertEquals(
        BuffaAbi.LP64_LITTLE_AAPCS64,
        BuffaAbi.fingerprint(TargetAbi.of("Linux", "aarch64").charUnsigned()));
    assertEquals(
        BuffaAbi.LP64_LITTLE, BuffaAbi.fingerprint(TargetAbi.of("Linux", "amd64").charUnsigned()));
    assertEquals(
        BuffaAbi.LP64_LITTLE,
        BuffaAbi.fingerprint(TargetAbi.of("Mac OS X", "aarch64").charUnsigned()));
  }

  /**
   * A library of the other ABI class (Linux AArch64's fingerprint here, the shared x86-64/Apple one
   * there) is rejected like any wrong ABI, never loaded as a fallback; this host's is accepted.
   */
  @Test
  void otherAbiClassDoesNotFallBack() throws Exception {
    Path other = runfile("protobuf-buffa/native/other_abi_class.so");
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class, () -> BuffaWire.open(BuffaWire.Mode.NATIVE, other));
    assertTrue(failure.getMessage().contains("ABI fingerprint mismatch"), failure.getMessage());
    try (var accepted = BuffaWire.open(BuffaWire.Mode.NATIVE, library())) {
      assertNotNull(accepted);
    }
  }

  @Test
  void borrowedSlabAccessorsRemainBoundPerLayoutWhenNestedSchemasAreShared() throws Exception {
    var midType =
        new ProtoPlan.Type(
            "Mid",
            false,
            List.of(
                new ProtoPlan.FieldPlan(
                    "n",
                    1,
                    ProtoPlan.Kind.INT32,
                    false,
                    false,
                    false,
                    false,
                    false,
                    -1,
                    "",
                    null,
                    0L)));
    var root1Type =
        new ProtoPlan.Type(
            "Root1",
            false,
            List.of(
                new ProtoPlan.FieldPlan(
                    "mid",
                    1,
                    ProtoPlan.Kind.MESSAGE,
                    false,
                    false,
                    true,
                    false,
                    false,
                    -1,
                    "Mid",
                    null,
                    Missing.INSTANCE)));
    var root2Type =
        new ProtoPlan.Type(
            "Root2",
            false,
            List.of(
                new ProtoPlan.FieldPlan(
                    "mid",
                    1,
                    ProtoPlan.Kind.MESSAGE,
                    false,
                    false,
                    true,
                    false,
                    false,
                    -1,
                    "Mid",
                    null,
                    Missing.INSTANCE)));
    var model =
        new ProtoPlan.Model(null, List.of(root1Type, root2Type, midType), Limits.DEFAULT, 16, 65536);
    var plan1 = model.type("Root1");
    var plan2 = model.type("Root2");
    assertSame(plan1.nested(0), plan2.nested(0), "shared nested plan");
    var midPlan = plan1.nested(0);
    byte[] wire =
        plan1.encode(
            plan1.newBuilder().set(0, midPlan.newBuilder().set(0, 42L).build()).build());
    try (var decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, library());
        var compiled1 = decoder.compile(plan1, 65536);
        var compiled2 = decoder.compile(plan2, 65536);
        var session1 = compiled1.newSession(65536);
        var session2 = compiled2.newSession(65536);
        var input1 = decoder.allocate(wire.length).put(wire);
        var input2 = decoder.allocate(wire.length).put(wire)) {
      var nested1 = (BorrowedSlabRow) session1.decode(input1).get(0);
      var nested2 = (BorrowedSlabRow) session2.decode(input2).get(0);
      assertSame(nested1.schema(), nested2.schema(), "shared Mid schema across layouts");
      assertSame(nested1.plan(), nested2.plan(), "shared Mid plan across layouts");
      for (boolean session1First : new boolean[] {false, true}) {
        var first = session1First ? nested1 : nested2;
        var second = session1First ? nested2 : nested1;
        var a = first.accessor(0);
        var b = second.accessor(0);
        assertTrue(a.accepts(first), "first accessor accepts first row");
        assertTrue(b.accepts(second), "second accessor accepts second row");
        assertNotSame(a, b, "accessors are distinct per layout");
        assertSame(a, first.accessor(0), "accessor identity-stable per layout");
        assertEquals(first.getLong(0), a.readLong(first), "first row reads through its accessor");
        assertEquals(second.getLong(0), b.readLong(second), "second row reads through its accessor");
        assertFalse(a.accepts(second), "first accessor rejects second row");
        assertFalse(b.accepts(first), "second accessor rejects first row");
        assertThrows(ContractViolation.class, () -> a.readLong(second), "foreign read throws");
        assertThrows(ContractViolation.class, () -> b.readLong(first), "foreign read throws");
      }
    }
  }
}
