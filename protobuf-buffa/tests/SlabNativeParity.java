package dev.elide.truffledata.protobuf;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.buffa.NativeTapeAcceptance;
import dev.elide.truffledata.buffa.generated.BuffaFFM;
import java.lang.foreign.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Live cross-language producer agreement, including overwritten/orphaned record bytes. */
final class SlabNativeParity {
  @Test
  void producersAgreeByteForByte() throws Throwable {
    Path root =
        Path.of(
            System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), "protobuf-buffa/native");
    Path library;
    try (var files = Files.list(root)) {
      library =
          files
              .filter(p -> p.toString().endsWith(".so") || p.toString().endsWith(".dylib"))
              .findFirst()
              .orElseThrow();
    }
    try (Arena arena = Arena.ofConfined();
        var decoder =
            dev.elide.truffledata.buffa.BuffaWire.open(
                dev.elide.truffledata.buffa.BuffaWire.Mode.NATIVE, library)) {
      var symbols = SymbolLookup.libraryLookup(library, arena);
      var compile = BuffaFFM.compileLayout(symbols);
      var release = BuffaFFM.releaseLayout(symbols);
      var decode = BuffaFFM.decodeSlab(symbols);
      var result = arena.allocate(32, 8);
      var input = arena.allocate(1 << 20, 8);
      var output = arena.allocate(1 << 20, 8);
      var random = new Random(0x51ab);
      for (var plan :
          List.of(
              LayoutTest.choice(),
              LayoutTest.unknowns(),
              LayoutTest.merge(),
              LayoutTest.maps(),
              LayoutTest.lists(),
              LayoutTest.texts(),
              LayoutTest.legacy())) {
        byte[] program = NativeTapeAcceptance.program(plan, List.of(), 1 << 20);
        var schema = arena.allocate(program.length, 8);
        schema.copyFrom(MemorySegment.ofArray(program));
        assertEquals(0, (int) compile.invokeExact(schema, (long) program.length, result));
        var compiled = result.get(ValueLayout.ADDRESS, 0);
        try (var bridge = decoder.compile(plan, 1 << 20)) {
          var layout = plan.layout();
          for (int i = 0; i < 8000; i++) {
            byte[] wire = LayoutTest.randomWire(random, plan, 0);
            if (wire.length > 0 && i % 7 == 0)
              wire = Arrays.copyOf(wire, random.nextInt(wire.length));
            input.asSlice(0, wire.length).copyFrom(MemorySegment.ofArray(wire));
            byte[] expected = null;
            try {
              expected =
                  SlabCodec.decode(layout, wire, 0, wire.length, new SlabLimits(32, 1 << 20));
            } catch (ContractViolation rejected) {
              // The native call must also reject this malformed input.
            }
            int status =
                (int)
                    decode.invokeExact(
                        compiled,
                        input,
                        (long) wire.length,
                        1,
                        MemorySegment.NULL,
                        output,
                        1L << 20,
                        32,
                        1L << 20,
                        result);
            if (expected == null) assertNotEquals(0, status, plan.name() + "/" + i);
            else {
              assertEquals(0, status, plan.name() + "/" + i);
              long bytes = result.get(ValueLayout.JAVA_LONG, 0);
              assertEquals(expected.length, bytes);
              // Exercise the reusable production bridge as well as the raw seam oracle.
              if (i < 32) {
                byte[] bridgeWire = wire;
                ProtoRow oracle = null;
                try {
                  oracle = plan.decode(wire);
                } catch (ContractViolation required) {
                  // Low-level producers flag missing required fields; owning rows reject them.
                }
                if (oracle == null)
                  assertThrows(
                      ContractViolation.class, () -> bridge.decodeSlab(bridgeWire, 1 << 20));
                else {
                  var message = bridge.decodeSlab(wire, 1 << 20);
                  assertArrayEquals(expected, message.slabCopy());
                  assertArrayEquals(plan.encode(oracle), message.encode());
                }
              }
              assertArrayEquals(
                  expected,
                  output.asSlice(0, bytes).toArray(ValueLayout.JAVA_BYTE),
                  plan.name() + "/" + i);
            }
          }
        } finally {
          release.invokeExact(compiled);
        }
      }
    }
  }
}
