package dev.elide.truffledata.comparison;

import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Recording;
import org.jspecify.annotations.NullMarked;

/** Diagnostic-only entry point: record steady-state work after benchmark warmup. */
@NullMarked
public final class JsonProfile {
  public static void main(String[] args) throws Exception {
    if (args.length != 4) throw new IllegalArgumentException("engine stage time recording-path");
    try (var recording = new Recording()) {
      recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
      recording.enable("jdk.ObjectAllocationSample").withStackTrace().with("throttle", "150/s");
      recording.setDestination(Path.of(args[3]));
      JsonComparison.beforeSamples = recording::start;
      JsonComparison.sampleNanos = 1_000_000_000L;
      try {
        JsonComparison.main(new String[] {args[0], args[1], args[2]});
      } finally {
        if (recording.getState() == jdk.jfr.RecordingState.RUNNING) recording.stop();
      }
    }
  }
}
