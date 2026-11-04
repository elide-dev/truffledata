package dev.elide.truffledata.comparison;

/** Longer diagnostic sessions without changing the ordinary comparison image or scoring loop. */
@org.jspecify.annotations.NullMarked
public final class Profile {
  private Profile() {}

  public static void main(String[] args) throws Exception {
    int rounds = Integer.parseInt(System.getProperty("truffledata.profile.rounds", "8"));
    if (rounds < 1 || rounds > 8) throw new IllegalArgumentException("Profile rounds must be 1..8");
    for (int round = 0; round < rounds; round++) Comparison.main(args);
  }
}
