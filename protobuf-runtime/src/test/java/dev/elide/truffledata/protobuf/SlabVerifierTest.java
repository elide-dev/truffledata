package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Limits;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The owning-slab walk (bitmap handles, folded logical budget) against the reference verifier
 * followed by the exact {@code validate} accounting it replaces: same accept/reject decisions,
 * same exception messages, same logical usage.
 */
class SlabVerifierTest {
  private static final SlabLimits LIMITS = new SlabLimits(31, 1 << 20);

  /** Reference: the object verifier, then the exact budget and required-field walk. */
  private static String reference(ProtoPlan plan, byte[] slab, SlabLimits limits) {
    try {
      SlabVerifier.verify(slab, slab.length, plan.layout(), limits);
      return accepted(SlabMessage.owning(plan.layout(), slab, slab.length, true));
    } catch (ContractViolation rejected) {
      return "rejected: " + rejected.getMessage();
    }
  }

  /** The production path, optionally forcing identity tracking for small slabs. */
  private static String owning(ProtoPlan plan, byte[] slab, SlabLimits limits, boolean identity) {
    try {
      boolean exact =
          SlabVerifier.verifyOwning(slab, slab.length, plan.layout(), limits, identity);
      return accepted(SlabMessage.owning(plan.layout(), slab, slab.length, exact));
    } catch (ContractViolation rejected) {
      return "rejected: " + rejected.getMessage();
    }
  }

  private static String copied(ProtoPlan plan, byte[] slab, SlabLimits limits) {
    try {
      return accepted(SlabMessage.copyOf(plan, ByteBuffer.wrap(slab.clone()), limits));
    } catch (ContractViolation rejected) {
      return "rejected: " + rejected.getMessage();
    }
  }

  private static String accepted(SlabMessage message) {
    return "accepted: " + message.logicalUsage();
  }

  private static void assertAgree(ProtoPlan plan, byte[] slab, SlabLimits limits, String label) {
    String expected = reference(plan, slab, limits);
    assertEquals(expected, owning(plan, slab, limits, false), label + " (bitmap)");
    assertEquals(expected, owning(plan, slab, limits, true), label + " (identity)");
    // copyOf first rejects an over-budget extent with its own message.
    if (slab.length <= limits.maxSlabBytes())
      assertEquals(expected, copied(plan, slab, limits), label + " (copyOf)");
  }

  static ProtoPlan withLimits(ProtoPlan original, Limits limits) {
    var definitions = new ArrayList<ProtoPlan.Type>();
    for (int i = 0; i < original.layout().types(); i++)
      definitions.add(original.layout().plan(i).definition());
    return new ProtoPlan.Model(null, definitions, limits, original.maxDepth(), 1 << 24)
        .type(original.name());
  }

  @Test
  void owningWalkAgreesWithReferenceUnderMutation() {
    Random random = new Random(7);
    int rejected = 0, acceptedCount = 0;
    for (ProtoPlan plan :
        List.of(
            LayoutTest.timestamp(),
            LayoutTest.choice(),
            LayoutTest.unknowns(),
            LayoutTest.merge(),
            LayoutTest.maps(),
            LayoutTest.lists(),
            LayoutTest.texts(),
            LayoutTest.legacy())) {
      Layout layout = plan.layout();
      for (int round = 0; round < 300; round++) {
        byte[] wire = LayoutTest.randomWire(random, plan, 0);
        byte[] slab;
        try {
          slab = SlabCodec.decode(layout, wire);
        } catch (ContractViolation malformed) {
          continue;
        }
        assertAgree(plan, slab, LIMITS, plan.name() + " original");
        for (int mutation = 0; mutation < 6; mutation++) {
          byte[] mutated = slab.clone();
          int edits = 1 + random.nextInt(2);
          for (int e = 0; e < edits; e++) {
            int at = ROOT + 4 * random.nextInt(Math.max(1, (mutated.length - ROOT) / 4));
            int value =
                switch (random.nextInt(6)) {
                  case 0 -> 0;
                  case 1 -> -1;
                  case 2 -> random.nextInt(8);
                  case 3 -> ROOT + 8 * random.nextInt(Math.max(1, mutated.length / 8));
                  case 4 -> getInt(mutated, ROOT + 4 * random.nextInt((mutated.length - ROOT) / 4));
                  default -> random.nextInt();
                };
            putInt(mutated, at, value);
          }
          String label = plan.name() + " " + java.util.HexFormat.of().formatHex(mutated);
          assertAgree(plan, mutated, LIMITS, label);
          if (reference(plan, mutated, LIMITS).startsWith("rejected")) rejected++;
          else acceptedCount++;
        }
      }
    }
    assertTrue(rejected > 1000, "too few rejected mutations: " + rejected);
    assertTrue(acceptedCount > 500, "too few accepted mutations: " + acceptedCount);
  }

  /** Root of a Texts slab with {@code count} repeated strings: one handle each plus the root. */
  private static byte[] manyStrings(ProtoPlan plan, int count) {
    var w = LayoutTest.wire();
    for (int i = 0; i < count; i++) w.string(6, "s" + i);
    return SlabCodec.decode(plan.layout(), w.bytes());
  }

  private static int element(ProtoPlan plan, byte[] slab, int index) {
    int list = getInt(slab, ROOT + plan.layout().slot(0, 5));
    return getInt(slab, listElement(list, 4, index));
  }

  @Test
  void duplicateAndOutOfRangeHandlesRejectOnBothSidesOfTheBitmapBound() {
    var plan = LayoutTest.texts();
    for (int count : new int[] {3, 61, 62, 70}) {
      byte[] slab = manyStrings(plan, count);
      assertEquals(count + 1, getInt(slab, HEADER_HANDLES));
      assertAgree(plan, slab, LIMITS, "valid " + count);
      assertTrue(copied(plan, slab, LIMITS).startsWith("accepted"), "valid " + count);
      int first = element(plan, slab, 0), second = element(plan, slab, count - 1);
      byte[] duplicate = slab.clone();
      putInt(duplicate, second + STRING_HANDLE, getInt(slab, first + STRING_HANDLE));
      assertAgree(plan, duplicate, LIMITS, "duplicate " + count);
      assertEquals("rejected: Invalid slab structure", copied(plan, duplicate, LIMITS));
      byte[] rootDuplicate = slab.clone();
      putInt(rootDuplicate, second + STRING_HANDLE, 0);
      assertEquals("rejected: Invalid slab structure", copied(plan, rootDuplicate, LIMITS));
      for (int id : new int[] {count + 1, -1, Integer.MAX_VALUE}) {
        byte[] range = slab.clone();
        putInt(range, second + STRING_HANDLE, id);
        assertAgree(plan, range, LIMITS, "range " + count + " " + id);
        assertEquals("rejected: Invalid slab structure", copied(plan, range, LIMITS));
      }
    }
  }

  @Test
  void sharedSubgraphsKeepTheirIdentitySemantics() {
    var plan = LayoutTest.maps();
    var entry1 = LayoutTest.wire().field(1, 1).message(2, LayoutTest.wire().field(1, 7));
    var entry2 = LayoutTest.wire().field(1, 2);
    byte[] slab =
        SlabCodec.decode(plan.layout(), LayoutTest.wire().message(2, entry1).message(2, entry2).bytes());
    int list = getInt(slab, ROOT + plan.layout().slot(0, 1));
    assertEquals(2, listCount(slab, list));
    // The same record reached twice keeps one identity: accepted, and counted per visit.
    byte[] shared = slab.clone();
    putInt(shared, listElement(list, 4, 1), getInt(slab, listElement(list, 4, 0)));
    assertAgree(plan, shared, LIMITS, "shared entry");
    assertTrue(copied(plan, shared, LIMITS).startsWith("accepted"));
    // A different record under a used handle is rejected.
    byte[] collision = slab.clone();
    int second = getInt(slab, listElement(list, 4, 1));
    putInt(collision, second + RECORD_HANDLE, getInt(slab, ROOT + RECORD_HANDLE));
    assertAgree(plan, collision, LIMITS, "collision");
    assertEquals("rejected: Invalid slab structure", copied(plan, collision, LIMITS));
  }

  @Test
  void requiredFieldsAndBudgetsKeepTheirMessages() {
    var legacy = LayoutTest.legacy();
    byte[] missing = SlabCodec.decode(legacy.layout(), LayoutTest.wire().field(5, 1).bytes());
    assertEquals("rejected: Missing required field: id", copied(legacy, missing, LIMITS));
    byte[] nested =
        SlabCodec.decode(
            legacy.layout(),
            LayoutTest.wire().field(1, 1).message(4, LayoutTest.wire().field(5, 1)).bytes());
    assertEquals("rejected: Missing required field: id", copied(legacy, nested, LIMITS));
    assertAgree(legacy, nested, LIMITS, "nested required");
    assertEquals(
        "Missing required field: id",
        assertThrows(
                ContractViolation.class,
                () -> SlabMessage.takeOwnership(legacy, missing.clone(), missing.length, LIMITS))
            .getMessage());

    var texts = LayoutTest.texts();
    byte[] slab = manyStrings(texts, 4);
    long cells = SlabMessage.copyOf(texts, ByteBuffer.wrap(slab), LIMITS).logicalUsage().cells();
    long bytes =
        SlabMessage.copyOf(texts, ByteBuffer.wrap(slab), LIMITS).logicalUsage().stringBytes();
    // Exactly at each budget: accepted, although the walk's static bound exceeds it.
    var exact = withLimits(texts, new Limits(1024, 256, cells, bytes));
    assertAgree(exact, slab, LIMITS, "exact budget");
    assertTrue(copied(exact, slab, LIMITS).startsWith("accepted"));
    var cellBound = withLimits(texts, new Limits(1024, 256, cells - 1, bytes));
    assertEquals("rejected: Protobuf cell budget exceeded", copied(cellBound, slab, LIMITS));
    assertAgree(cellBound, slab, LIMITS, "cell budget");
    var byteBound = withLimits(texts, new Limits(1024, 256, cells, bytes - 1));
    assertEquals("rejected: Protobuf payload budget exceeded", copied(byteBound, slab, LIMITS));
    assertAgree(byteBound, slab, LIMITS, "payload budget");
    var many = manyStrings(texts, 70);
    assertAgree(withLimits(texts, new Limits(1024, 256, 40, 1 << 20)), many, LIMITS, "many cells");
    assertAgree(withLimits(texts, new Limits(1024, 256, 1024, 8)), many, LIMITS, "many bytes");
    var ownedBound = withLimits(texts, new Limits(1024, 256, cells - 1, bytes));
    assertEquals(
        "Protobuf cell budget exceeded",
        assertThrows(
                ContractViolation.class,
                () -> SlabMessage.takeOwnership(ownedBound, slab.clone(), slab.length, LIMITS))
            .getMessage());
    // A structural fault outranks a budget overrun, as when verification preceded accounting.
    byte[] broken = slab.clone();
    putInt(broken, element(texts, slab, 3) + STRING_HANDLE, -1);
    assertEquals("rejected: Invalid slab structure", copied(cellBound, broken, LIMITS));
    assertAgree(cellBound, broken, LIMITS, "structure before budget");
  }

  @Test
  void depthAndExtentLimitsStillApply() {
    var legacy = LayoutTest.legacy();
    var w = LayoutTest.wire().field(1, 1);
    for (int i = 0; i < 4; i++) w = LayoutTest.wire().field(1, 1).message(4, w);
    byte[] slab = SlabCodec.decode(legacy.layout(), w.bytes());
    assertAgree(legacy, slab, new SlabLimits(3, 1 << 20), "depth");
    assertTrue(copied(legacy, slab, new SlabLimits(3, 1 << 20)).startsWith("rejected"));
    assertTrue(copied(legacy, slab, new SlabLimits(4, 1 << 20)).startsWith("accepted"));
    assertAgree(legacy, slab, new SlabLimits(31, slab.length - 8), "extent");
  }
}
