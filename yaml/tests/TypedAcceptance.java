package dev.elide.truffledata.yaml.tests;

import dev.elide.truffledata.Constraints;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TypedAccess;
import dev.elide.truffledata.yaml.YamlSession;
import java.util.List;
import java.util.Map;

/** Identical typed contract and lifetime checks on the JVM and in a static image. */
final class TypedAcceptance {
  private TypedAcceptance() {}

  private static void check(boolean value) {
    if (!value) throw new AssertionError("typed YAML");
  }

  static void verify(YamlSession session) {
    Schema flat =
        Schema.of(
            new Field("s", Field.Kind.STRING, false, false),
            Field.requiredLong("n"),
            new Field("f", Field.Kind.FLOAT64, false, false),
            new Field("b", Field.Kind.BOOLEAN, false, false));
    try (var plan = session.compile(flat, Map.of());
        var row = plan.decode(SvmAcceptance.bytes("s: 1.0\nn: 0x1f\nf: .inf\nb: true\n"))) {
      check(row.stringValue(0).equals("1.0") && row.longValue(1) == 31);
      check(row.doubleValue(2) == Double.POSITIVE_INFINITY && row.booleanValue(3));
      check(new TypedAccess(flat, 1).longValue(row) == 31);
      for (String invalid :
          List.of(
              "s: x\nn: '12'\nf: 1.0\nb: true",
              "s: x\nn: 1\nf: 1.0",
              "s: x\nn: 1\nf: 1.0\nb: true\nextra: 0",
              "s: x\nn: 1\nn: 2\nf: 1.0\nb: true")) {
        try (var ignored = plan.decode(SvmAcceptance.bytes(invalid))) {
          throw new AssertionError("invalid typed YAML accepted");
        } catch (IllegalArgumentException expected) {
        }
      }
    }
    Schema point =
        new Schema(
            "Point",
            List.of(Field.requiredLong("n"), new Field("tag", Field.Kind.STRING, true, true)));
    Field child =
        new Field("a", Field.Kind.RECORD, false, false, Constraints.NONE.withRecordType("Point"));
    Schema root =
        Schema.of(
            child,
            new Field("b", Field.Kind.RECORD, false, false, child.constraints()),
            new Field(
                "items", Field.Kind.ARRAY, false, false, Constraints.NONE.withElement(child)));
    Map<String, Schema> types = Map.of("Point", point);
    var plan = session.compile(root, types);
    var row =
        plan.decode(SvmAcceptance.bytes("a: &a {n: 7, tag: null}\nb: *a\nitems: [*a, {n: 9}]\n"));
    plan.close();
    try (row) {
      check(new TypedAccess(root, types, 1, 0).longValue(row) == 7);
      check(row.record(0).isNull(1) && row.record(1).isNull(1));
      check(row.array(2).size() == 2 && row.array(2).record(1).longValue(0) == 9);
      var snapshot = row.snapshot();
      row.close();
      check(snapshot != null);
      try {
        row.record(0);
        throw new AssertionError("closed typed YAML read");
      } catch (IllegalStateException expected) {
      }
    }
  }
}
