package dev.elide.truffledata.buffa.tests;

import dev.elide.truffledata.buffa.*;
import java.nio.file.*;
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
    Path library =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "protobuf-buffa/tests/wrong_abi.so");
    Acceptance.rejects(
        () -> BuffaWire.open(BuffaWire.Mode.NATIVE, library), IllegalStateException.class);
  }
}
