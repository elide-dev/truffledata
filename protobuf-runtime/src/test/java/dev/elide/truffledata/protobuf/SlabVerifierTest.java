package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Limits;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The owning-slab walk (bitmap handles, folded logical budget) against the reference verifier
 * followed by the exact {@code validate} accounting it replaces: same accept/reject decisions, same
 * exception messages, same logical usage.
 */
class SlabVerifierTest {
  private static final SlabLimits LIMITS = new SlabLimits(31, 1 << 20);

  /** Reference: the object verifier, then the exact budget and required-field walk. */
  private static String reference(ProtoPlan plan, byte[] slab, SlabLimits limits) {
    try {
      SlabVerifier.verify(slab, slab.length, plan.layout(), limits);
      return accepted(SlabMessage.owning(plan.layout(), slab, slab.length, SlabVerifier.EXACT));
    } catch (ContractViolation rejected) {
      return "rejected: " + rejected.getMessage();
    }
  }

  /** The production path, optionally forcing identity tracking for small slabs. */
  private static String owning(ProtoPlan plan, byte[] slab, SlabLimits limits, boolean identity) {
    try {
      int exact = SlabVerifier.verifyOwning(slab, slab.length, plan.layout(), limits, identity);
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
        SlabCodec.decode(
            plan.layout(), LayoutTest.wire().message(2, entry1).message(2, entry2).bytes());
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
  void aliasedRecursiveRecordsKeepPerVisitBudgets() {
    var plan =
        LayoutTest.model(LayoutTest.type("Node", LayoutTest.message("children", 1, "Node", true)))
            .type("Node");
    // A bounded reproducer for shared-subgraph amplification: the physical wire is a chain
    // with one empty sibling at each level. Repoint each sibling to its nonempty brother,
    // obtaining an exponentially larger logical tree without increasing the slab extent.
    // Keep this reference-comparison fixture small; the stress case below uses depth 28.
    int depth = 10;
    var wire = LayoutTest.wire();
    for (int i = 0; i < depth; i++)
      wire = LayoutTest.wire().message(1, wire).message(1, LayoutTest.wire());
    byte[] slab = SlabCodec.decode(plan.layout(), wire.bytes());
    int record = ROOT;
    for (int i = 0; i < depth; i++) {
      int list = getInt(slab, record + plan.layout().slot(0, 0));
      int child = getInt(slab, listElement(list, 4, 0));
      putInt(slab, listElement(list, 4, 1), child);
      record = child;
    }
    long cells = 4L * (1L << depth) - 3; // One field per record, plus two cells per list.
    assertEquals(
        cells, SlabMessage.copyOf(plan, ByteBuffer.wrap(slab), LIMITS).logicalUsage().cells());
    assertAgree(plan, slab, LIMITS, "recursive shared graph");
    var bounded = withLimits(plan, new Limits(1024, 256, cells - 1, 1 << 20));
    assertAgree(bounded, slab, LIMITS, "recursive shared graph budget");
    assertEquals("rejected: Protobuf cell budget exceeded", copied(bounded, slab, LIMITS));
  }

  private static ProtoPlan sharedPlan() {
    return LayoutTest.model(
            LayoutTest.type("Node",
                LayoutTest.message("children", 1, "Node", true),
                LayoutTest.scalar("text", 2, ProtoPlan.Kind.STRING, false)))
        .type("Node");
  }

  private static byte[] sharedSlab(ProtoPlan plan, int depth) {
    var wire = LayoutTest.wire().string(2, "x");
    for (int i = 0; i < depth; i++)
      wire = LayoutTest.wire().message(1, wire).message(1, LayoutTest.wire()).string(2, "x");
    byte[] slab = SlabCodec.decode(plan.layout(), wire.bytes());
    int record = ROOT;
    for (int i = 0; i < depth; i++) {
      int list = getInt(slab, record + plan.layout().slot(0, 0));
      int child = getInt(slab, listElement(list, 4, 0));
      putInt(slab, listElement(list, 4, 1), child);
      record = child;
    }
    return slab;
  }

  @Test
  void exponentialAliasesAreBoundedAndStillChargedPerOccurrence() {
    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      int depth = 28;
      long cells = 6L * (1L << depth) - 4;
      long bytes = 2L * ((1L << (depth + 1)) - 1);
      var source = sharedPlan();
      byte[] slab = sharedSlab(source, depth);
      assertTrue(slab.length < 8192);
      var exact = withLimits(source, new Limits(1024, 256, cells, bytes));
      var message = SlabMessage.copyOf(exact, ByteBuffer.wrap(slab), LIMITS);
      assertEquals(new SlabMessage.Usage(cells, bytes), message.logicalUsage());
      var children = (dev.elide.truffledata.OwnedArray) message.get(0);
      assertSame(children.get(0), children.get(1));
      int child = getInt(slab, listElement(getInt(slab, ROOT + exact.layout().slot(0, 0)), 4, 0));
      assertEquals("x", SlabMessage.copyRecordOf(exact, ByteBuffer.wrap(slab), LIMITS, 0, child).get(1));
      assertEquals("rejected: Protobuf cell budget exceeded",
          copied(withLimits(source, new Limits(1024, 256, cells - 1, bytes)), slab, LIMITS));
      assertEquals("rejected: Protobuf payload budget exceeded",
          copied(withLimits(source, new Limits(1024, 256, cells, bytes - 1)), slab, LIMITS));
      // Projected external graphs need the same bounded accounting.
      byte[] projected = slab.clone();
      putInt(projected, HEADER_FLAGS, OWNING | PROJECTED);
      assertEquals(message.logicalUsage(), SlabMessage.copyProjectedOf(exact,
          ByteBuffer.wrap(projected), ByteBuffer.allocate(0), Projection.all(exact), LIMITS)
          .logicalUsage());
      // This fault follows the exponential prefix, but must outrank even an immediate budget error.
      int text = getInt(slab, ROOT + source.layout().slot(0, 1));
      putInt(slab, text + STRING_HANDLE, -1);
      var tiny = withLimits(source, new Limits(1024, 256, 2, 1));
      assertEquals("rejected: Invalid slab structure", copied(tiny, slab, LIMITS));
    });
  }

  @Test
  void repeatedTextHeadersReuseValidationButKeepAccountingAndIdentityChecks() {
    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      int count = 4096;
      String text = "λ😀".repeat(4096);
      var source = LayoutTest.texts();
      var wire = LayoutTest.wire().string(6, text);
      for (int i = 1; i < count; i++) wire.string(6, "");
      byte[] slab = SlabCodec.decode(source.layout(), wire.bytes());
      int list = getInt(slab, ROOT + source.layout().slot(0, 5));
      int first = getInt(slab, listElement(list, 4, 0));
      int second = getInt(slab, listElement(list, 4, 1));
      for (int i = 1; i < count; i++) putInt(slab, listElement(list, 4, i), first);
      long cells = source.size() + count, bytes = 2L * text.length() * count;
      var plan = withLimits(source, new Limits(1024, 256, cells, bytes));
      var accepted = SlabMessage.copyOf(plan, ByteBuffer.wrap(slab), LIMITS);
      assertEquals(new SlabMessage.Usage(cells, bytes), accepted.logicalUsage());
      var values = (dev.elide.truffledata.OwnedArray) accepted.get(5);
      assertSame(values.get(0), values.get(count - 1));
      assertEquals("rejected: Protobuf payload budget exceeded",
          copied(withLimits(source, new Limits(1024, 256, cells, bytes - 1)), slab, LIMITS));
      byte[] broken = slab.clone();
      broken[payload(broken, first)] = (byte) 0xff;
      assertTrue(copied(plan, broken, LIMITS).startsWith("rejected:"));
      // A reused handle at another header cannot borrow the first header's UTF-8 proof.
      putInt(slab, second + STRING_HANDLE, stringHandle(slab, first));
      putInt(slab, listElement(list, 4, count - 1), second);
      assertEquals("rejected: Invalid slab structure", copied(plan, slab, LIMITS));
    });
  }

  @Test
  void sharedAccountingKeepsTheFirstCellOrPayloadFailure() {
    var plan = sharedPlan();
    byte[] slab = sharedSlab(plan, 2);
    // Both totals can overflow their budgets. A cached aggregate may only skip a subtree
    // when both fit, otherwise the original field/list traversal decides which fails first.
    for (long cells = 2; cells <= 20; cells++)
      for (long bytes = 1; bytes <= 14; bytes++)
        assertAgree(withLimits(plan, new Limits(1024, 256, cells, bytes)), slab, LIMITS,
            "shared budget order " + cells + "/" + bytes);
  }

  @Test
  void sharedProjectionKeepsLastDfsSelectionAndChecksExpandedContexts() {
    var plan = LayoutTest.model(
        LayoutTest.type("Root", LayoutTest.message("first", 1, "Child", false),
            LayoutTest.message("second", 2, "Child", false)),
        LayoutTest.type("Child", LayoutTest.scalar("text", 1, ProtoPlan.Kind.STRING, false)))
        .type("Root");
    byte[] slab = SlabCodec.decode(plan.layout(), LayoutTest.wire()
        .message(1, LayoutTest.wire().string(1, "x"))
        .message(2, LayoutTest.wire().string(1, "y")).bytes());
    int child = getInt(slab, ROOT + plan.layout().slot(0, 0));
    int childType = plan.layout().childType(0, 0);
    putInt(slab, ROOT + plan.layout().slot(0, 1), child);
    putInt(slab, HEADER_FLAGS, OWNING | PROJECTED);
    for (boolean lastExpanded : new boolean[] {false, true}) {
      var projection = Projection.builder(plan).select(lastExpanded ? "first" : "second")
          .build().lazyChildren();
      assertEquals(lastExpanded,
          SlabVerifier.verifyRecord(slab, plan.layout(), LIMITS, projection, new byte[0], childType, child));
      var selected = SlabMessage.copyProjectedRecordOf(plan, ByteBuffer.wrap(slab),
          ByteBuffer.allocate(0), projection, LIMITS, childType, child);
      if (lastExpanded) assertEquals("x", selected.get(0));
      else assertThrows(ContractViolation.class, () -> selected.get(0));
    }
    // A missing payload is legal for the first masked reach, illegal for the expanded reach.
    putInt(slab, child + plan.layout().slot(childType, 0), 0);
    var projection = Projection.builder(plan).select("first").build().lazyChildren();
    assertEquals("Invalid slab structure", assertThrows(ContractViolation.class,
        () -> SlabMessage.copyProjectedOf(plan, ByteBuffer.wrap(slab), ByteBuffer.allocate(0),
            projection, LIMITS)).getMessage());
  }

  @Test
  void sharedLazyRecordRechecksAbsoluteDepth() {
    var plan = LayoutTest.model(
        LayoutTest.type("Root", LayoutTest.message("direct", 1, "Child", false),
            LayoutTest.message("bridge", 2, "Bridge", false)),
        LayoutTest.type("Bridge", LayoutTest.message("child", 1, "Child", false)),
        LayoutTest.type("Child", LayoutTest.message("next", 1, "Child", false)))
        .type("Root");
    var projection = Projection.builder(plan).select("direct", "bridge")
        .selectType("Bridge", "child").build().lazyChildren();
    var childWire = LayoutTest.wire().message(1, LayoutTest.wire());
    byte[] wire = LayoutTest.wire().message(1, childWire)
        .message(2, LayoutTest.wire().message(1, childWire)).bytes();
    byte[] slab = SlabMessage.decode(plan, wire, 0, wire.length, LIMITS, projection).slabCopy();
    int child = getInt(slab, ROOT + plan.layout().slot(0, 0));
    int bridge = getInt(slab, ROOT + plan.layout().slot(0, 1));
    int bridgeType = plan.layout().childType(0, 1);
    putInt(slab, bridge + plan.layout().slot(bridgeType, 0), child);
    // The same Child is legal at depth 2; its lazy header must not be reused at depth 3.
    assertEquals("Invalid slab structure", assertThrows(ContractViolation.class,
        () -> SlabMessage.copyProjectedOf(plan, ByteBuffer.wrap(slab), ByteBuffer.wrap(wire),
            projection, LIMITS)).getMessage());
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
