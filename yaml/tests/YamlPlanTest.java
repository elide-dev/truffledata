package dev.elide.truffledata.yaml.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.Constraints;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.yaml.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class YamlPlanTest {
  @Test
  void sharedTypedContracts() {
    try (var session = YamlJvm.openSession(YamlIntegrationTest.library())) {
      TypedAcceptance.verify(session);
    }
  }

  @Test
  void plansAndResultsKeepLibraryAlive() throws Exception {
    Schema schema = Schema.of(Field.requiredLong("n"));
    var session = YamlJvm.openSession(YamlIntegrationTest.library());
    var plan = session.compile(schema, Map.of());
    session.close();
    var row = plan.decode(SvmAcceptance.bytes("n: 42"));
    var failure = new AtomicReference<Throwable>();
    Thread thread =
        new Thread(
            () -> {
              try {
                plan.decode(SvmAcceptance.bytes("n: 1"));
              } catch (Throwable caught) {
                failure.set(caught);
              }
            });
    thread.start();
    thread.join();
    assertInstanceOf(IllegalStateException.class, failure.get());
    plan.close();
    plan.close();
    assertThrows(IllegalStateException.class, () -> plan.decode(SvmAcceptance.bytes("n: 1")));
    try (row) {
      assertEquals(42, row.longValue(0));
    }
    assertTrue(
        assertThrows(IllegalStateException.class, () -> row.longValue(0))
            .getMessage()
            .contains("YAML"));
    assertThrows(IllegalStateException.class, () -> session.compile(schema, Map.of()));
  }

  @Test
  void optionsAndContractsApplyBeforePublication() {
    Schema number = Schema.of(Field.integer("n", 8, true, false, false));
    try (var session = YamlJvm.openSession(YamlIntegrationTest.library(), YamlOptions.yaml11());
        var plan = session.compile(number, Map.of());
        var row = plan.decode(SvmAcceptance.bytes("'n': 0b11"))) {
      assertEquals(3, row.longValue(0));
      assertThrows(
          IllegalArgumentException.class, () -> plan.decode(SvmAcceptance.bytes("n: 128")));
      assertThrows(IllegalArgumentException.class, () -> plan.decode(SvmAcceptance.bytes("n: [")));
      assertThrows(IllegalArgumentException.class, () -> plan.decode(new byte[0]));
    }
    Schema word =
        Schema.of(
            new Field(
                "word",
                Field.Kind.STRING,
                false,
                false,
                Constraints.NONE.withLength(
                    new Constraints.Length(1, 1, Constraints.LengthUnit.CODE_POINTS))));
    try (var session = YamlJvm.openSession(YamlIntegrationTest.library());
        var plan = session.compile(word, Map.of());
        var row = plan.decode(SvmAcceptance.bytes("word: '😀'"))) {
      assertEquals("😀", row.stringValue(0));
      assertThrows(
          IllegalArgumentException.class, () -> plan.decode(SvmAcceptance.bytes("word: xx")));
    }
    // The static compile runs on YamlSession.open()'s backend: the Java kernel on this JVM.
    try (var plan = YamlPlan.compile(number); var row = plan.decode(SvmAcceptance.bytes("n: 5"))) {
      assertEquals(5, row.longValue(0));
    }
    assertThrows(UnsupportedOperationException.class, () -> YamlSvm.compile(number, Map.of()));
  }
}
