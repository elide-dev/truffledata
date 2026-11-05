package dev.elide.truffledata.comparison;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import jdk.jfr.Recording;

/** Diagnostic-only steady-state recording; never use this executable for comparison scores. */
@org.jspecify.annotations.NullMarked
public final class ConsumerProfile {
  public static void main(String[] args) throws Exception {
    if (args.length != 5 || !args[3].equals("time"))
      throw new IllegalArgumentException("engine mode stage time recording.jfr");
    try (var recording = new Recording()) {
      recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
      recording.enable("jdk.ObjectAllocationSample").withStackTrace().with("throttle", "150/s");
      recording.setDestination(Path.of(args[4]));
      ConsumerComparison.beforeSamples = recording::start;
      ConsumerComparison.sampleNanos = 1_000_000_000L;
      try {
        ConsumerComparison.main(Arrays.copyOf(args, 4));
      } finally {
        ConsumerComparison.beforeSamples = () -> {};
        ConsumerComparison.sampleNanos = 50_000_000L;
        if (recording.getState() == jdk.jfr.RecordingState.RUNNING) recording.stop();
      }
    }
  }
}
