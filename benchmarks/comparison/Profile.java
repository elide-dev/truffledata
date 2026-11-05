package dev.elide.truffledata.comparison;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import jdk.jfr.Recording;

/** Longer diagnostic sessions without changing the ordinary comparison image or scoring loop. */
@org.jspecify.annotations.NullMarked
public final class Profile {
  private Profile() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 5) {
      if (!args[3].equals("time"))
        throw new IllegalArgumentException("Steady-state profile requires time mode");
      try (var recording = new Recording()) {
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
        recording.enable("jdk.ObjectAllocationSample").withStackTrace().with("throttle", "150/s");
        recording.setDestination(Path.of(args[4]));
        Comparison.beforeSamples = recording::start;
        Comparison.sampleNanos = 1_000_000_000L;
        try {
          Comparison.main(Arrays.copyOf(args, 4));
        } finally {
          Comparison.beforeSamples = () -> {};
          Comparison.sampleNanos = 50_000_000L;
          if (recording.getState() == jdk.jfr.RecordingState.RUNNING) recording.stop();
        }
      }
      return;
    }
    int rounds = Integer.parseInt(System.getProperty("truffledata.profile.rounds", "8"));
    if (rounds < 1 || rounds > 8) throw new IllegalArgumentException("Profile rounds must be 1..8");
    for (int round = 0; round < rounds; round++) Comparison.main(args);
  }
}
