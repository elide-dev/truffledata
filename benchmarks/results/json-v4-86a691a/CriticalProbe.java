package dev.elide.truffledata.json.tests;

import dev.elide.truffledata.json.JsonJvm;
import dev.elide.truffledata.json.JsonLimits;
import dev.elide.truffledata.json.JsonSession;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * TEMPORARY probe (not committed as a target; retained under benchmarks/results): the JVM's bounded
 * critical decode path (performance plan Task E2) against the normal FFM transition, on one host,
 * interleaved rounds, raw per-round samples. Usage: {@code CriticalProbe <json_shared.so>}.
 */
public final class CriticalProbe {
  private CriticalProbe() {}

  private static byte[] document(int members) {
    StringBuilder json = new StringBuilder("{");
    for (int i = 0; i < members; i++) {
      json.append(i == 0 ? "" : ",")
          .append("\"k").append(i).append("\":{\"id\":").append(1000 + i)
          .append(",\"name\":\"item-").append(i).append("\",\"price\":").append(i).append(".25,\"tags\":[\"a\",\"b\"]}");
    }
    return json.append('}').toString().getBytes(StandardCharsets.UTF_8);
  }

  public static void main(String[] args) throws Exception {
    Path library = Path.of(args[0]);
    int[] sizes = {1, 8, 50};
    int rounds = 15;
    System.out.println("{\"runtime\":\"" + System.getProperty("java.vm.name") + " " + Runtime.version() + "\"}");
    try (JsonSession critical = JsonJvm.openSession(library, JsonLimits.DEFAULT);
        JsonSession normal = JsonJvm.openSession(library, JsonLimits.DEFAULT, 0)) {
      for (int members : sizes) {
        byte[] doc = document(members);
        if (doc.length > JsonSession.CRITICAL_LIMIT) throw new IllegalStateException("above the critical limit");
        int reps = 200_000 / members;
        for (int w = 0; w < 5; w++) {
          for (int i = 0; i < reps; i++) critical.validate(doc);
          for (int i = 0; i < reps; i++) normal.validate(doc);
        }
        double[] c = new double[rounds], n = new double[rounds];
        for (int r = 0; r < rounds; r++) {
          // Interleaved, alternating which path runs first.
          JsonSession first = (r & 1) == 0 ? critical : normal, second = first == critical ? normal : critical;
          double a = time(first, doc, reps), b = time(second, doc, reps);
          if (first == critical) { c[r] = a; n[r] = b; } else { n[r] = a; c[r] = b; }
        }
        if (critical.criticalDecodes() == 0 || normal.criticalDecodes() != 0) throw new IllegalStateException("paths");
        System.out.printf(Locale.ROOT, "{\"bytes\":%d,\"critical_ns\":%s,\"normal_ns\":%s}%n", doc.length,
            Arrays.toString(c), Arrays.toString(n));
      }
    }
  }

  private static double time(JsonSession session, byte[] doc, int reps) {
    long start = System.nanoTime();
    for (int i = 0; i < reps; i++) session.validate(doc);
    return (System.nanoTime() - start) / (double) reps;
  }
}
