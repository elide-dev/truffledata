package dev.elide.truffledata.protobuf;

import java.util.List;
import java.util.Random;

/** Test-only bridge to the slab layout fixtures (split package; never in a published jar). */
public final class CellsFixtures {
  private CellsFixtures() {}

  /** Every shape the native producer parity suite covers, plus a scalar and a >64-field type. */
  public static List<ProtoPlan> plans() {
    return List.of(
        LayoutTest.timestamp(),
        LayoutTest.choice(),
        LayoutTest.unknowns(),
        LayoutTest.merge(),
        LayoutTest.maps(),
        LayoutTest.lists(),
        LayoutTest.texts(),
        LayoutTest.wide(),
        LayoutTest.legacy());
  }

  public static byte[] randomWire(Random random, ProtoPlan plan) {
    return LayoutTest.randomWire(random, plan, 0);
  }
}
