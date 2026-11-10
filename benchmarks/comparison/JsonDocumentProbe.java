package dev.elide.truffledata.comparison;

import com.sun.management.ThreadMXBean;
import dev.elide.truffledata.json.JsonMessage;
import dev.elide.truffledata.json.JsonSvm;
import java.lang.management.ManagementFactory;

/**
 * One-shot JSON document decode on Native Image: {@code JsonSvm.decode(bytes)} and {@code close()}
 * over the comparison fixture's 32 documents. This is the path the Myna Truffle cells route covers
 * ({@code td_json_decode} and {@code td_json_release}); the JSON comparison's ingest cells decode
 * through a {@code JsonSession}, which the route does not touch, so they cannot tell the two routes
 * apart. One process prints one record: three timed samples (ns per document) and three allocation
 * samples (Java heap bytes per document).
 */
public final class JsonDocumentProbe {
  private static final int ROWS = 32;

  private JsonDocumentProbe() {}

  private static long round(byte[][] inputs) {
    long bytes = 0;
    for (byte[] input : inputs) {
      try (JsonMessage message = JsonSvm.decode(input)) {
        if (message.root() == null) throw new AssertionError("no root");
        bytes += input.length;
      }
    }
    return bytes;
  }

  public static void main(String[] args) {
    byte[][] inputs = new byte[ROWS][];
    long expected = 0;
    for (int i = 0; i < ROWS; i++) {
      inputs[i] = JsonFixture.document(i);
      expected += inputs[i].length;
    }
    var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported())
      throw new AssertionError("Allocation counter unavailable");
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    long until = System.nanoTime() + 2_000_000_000L;
    do {
      if (round(inputs) != expected) throw new AssertionError("checksum");
    } while (System.nanoTime() < until);
    double[] times = new double[3], allocated = new double[3];
    for (int sample = 0; sample < 3; sample++) {
      long rounds = 0, start = System.nanoTime(), end = start + 300_000_000L, now;
      do {
        if (round(inputs) != expected) throw new AssertionError("checksum");
        rounds++;
      } while ((now = System.nanoTime()) < end);
      times[sample] = (double) (now - start) / (rounds * ROWS);
      long before = bean.getThreadAllocatedBytes(thread);
      for (long r = 0; r < rounds; r++)
        if (round(inputs) != expected) throw new AssertionError("checksum");
      allocated[sample] = (double) (bean.getThreadAllocatedBytes(thread) - before) / (rounds * ROWS);
    }
    System.out.printf(
        "{\"probe\":\"json-document-decode\",\"runtime\":\"svm\",\"rows\":%d,\"checksum\":%d,"
            + "\"ns_per_operation\":[%.3f,%.3f,%.3f],"
            + "\"allocated_bytes_per_operation\":[%.3f,%.3f,%.3f]}%n",
        ROWS, expected, times[0], times[1], times[2], allocated[0], allocated[1], allocated[2]);
  }
}
