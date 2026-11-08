package dev.elide.truffledata.comparison;

import org.jspecify.annotations.NullMarked;

/** Adds JVM tree/event competitors to the same fixture and sampler used by the static image. */
@NullMarked
public final class YamlJvmComparison {
  public static void main(String[] args) throws Exception {
    YamlComparison.foreign = YamlCompetitors::new;
    YamlComparison.main(args);
  }
}
