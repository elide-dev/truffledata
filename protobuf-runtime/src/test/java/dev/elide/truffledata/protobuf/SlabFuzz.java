package dev.elide.truffledata.protobuf;

import dev.elide.truffledata.ContractViolation;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

/** Arbitrary external slabs plus structure-preserving mutations of valid slabs. */
public final class SlabFuzz {
  private static final SlabLimits LIMITS = new SlabLimits(31, 1 << 20);
  private static final ProtoPlan[] PLANS = {
    LayoutTest.timestamp(), LayoutTest.choice(), LayoutTest.unknowns(), LayoutTest.merge(),
    LayoutTest.maps(), LayoutTest.lists(), LayoutTest.texts(), LayoutTest.legacy(),
    LayoutTest.model(LayoutTest.type("Node", LayoutTest.message("children", 1, "Node", true))).type("Node")
  };
  private static final byte[][] SEEDS = seeds();

  private static byte[][] seeds() {
    byte[][] seeds = new byte[PLANS.length][];
    Random random = new Random(7);
    for (int i = 0; i < PLANS.length; i++) {
      for (int attempt = 0; attempt < 100; attempt++) {
        try {
          seeds[i] = SlabCodec.decode(PLANS[i].layout(), LayoutTest.randomWire(random, PLANS[i], 0));
          break;
        } catch (ContractViolation rejected) {
          // Required fields and generated wire constraints can reject a fixture.
        }
      }
      if (seeds[i] == null) throw new AssertionError("No valid slab seed for " + PLANS[i].name());
    }
    return seeds;
  }

  private static String outcome(ProtoPlan plan, byte[] slab, int path) {
    try {
      SlabMessage message;
      if (path == 0) {
        SlabVerifier.verify(slab, slab.length, plan.layout(), LIMITS);
        message = SlabMessage.owning(plan.layout(), slab, slab.length, SlabVerifier.EXACT);
      } else if (path == 1 || path == 2) {
        int exact = SlabVerifier.verifyOwning(slab, slab.length, plan.layout(), LIMITS, path == 2);
        message = SlabMessage.owning(plan.layout(), slab, slab.length, exact);
      } else {
        message = SlabMessage.copyOf(plan, ByteBuffer.wrap(slab), LIMITS);
      }
      return "accepted: " + message.logicalUsage();
    } catch (ContractViolation rejected) {
      return "rejected: " + rejected.getMessage();
    }
  }

  public static void fuzzerTestOneInput(byte[] input) {
    if (input.length < 2 || input.length > 65536) return;
    int model = Byte.toUnsignedInt(input[0]) % PLANS.length;
    byte[] slab;
    if ((input[1] & 1) == 0) {
      slab = Arrays.copyOfRange(input, 2, input.length);
    } else {
      slab = SEEDS[model].clone();
      // Six-byte edits reach every byte, including magic, extent, handles and offsets.
      for (int i = 2; i + 5 < input.length; i += 6) {
        int at = ((Byte.toUnsignedInt(input[i]) << 8) | Byte.toUnsignedInt(input[i + 1])) % slab.length;
        for (int j = 0; j < 4 && at + j < slab.length; j++) slab[at + j] = input[i + 2 + j];
      }
    }
    String expected = outcome(PLANS[model], slab, 0);
    for (int path = 1; path <= 3; path++) {
      String actual = outcome(PLANS[model], slab, path);
      if (!expected.equals(actual))
        throw new AssertionError("slab path " + path + ": " + expected + " != " + actual);
    }
  }

  public static void main(String[] args) throws Exception {
    for (int model = 0; model < PLANS.length; model++)
      fuzzerTestOneInput(new byte[] {(byte) model, 1});
    for (String file : args) fuzzerTestOneInput(Files.readAllBytes(Path.of(file)));
  }
}
