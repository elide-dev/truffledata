package dev.elide.truffledata.buffa.tests;

import dev.elide.truffledata.buffa.BuffaWire;
import org.junit.jupiter.api.Test;

final class NativeDeniedTest {
  @Test
  void unavailableNativeAccessIsAnExplicitFallback() throws Exception {
    var library = AcceptanceTest.library();
    try (var decoder = BuffaWire.open(BuffaWire.Mode.AUTO, library)) {
      Acceptance.check(decoder.selected() == BuffaWire.Selected.JAVA, "denied FFM falls back");
      Acceptance.parity(decoder);
    }
    Acceptance.rejects(
        () -> BuffaWire.open(BuffaWire.Mode.NATIVE, library), IllegalCallerException.class);
  }
}
