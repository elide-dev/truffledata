package dev.elide.truffledata.comparison;

/** Longer diagnostic sessions without changing the ordinary comparison image or scoring loop. */
public final class Profile {
  private Profile() {}

  public static void main(String[] args) throws Exception {
    for (int round = 0; round < 8; round++) Comparison.main(args);
  }
}
