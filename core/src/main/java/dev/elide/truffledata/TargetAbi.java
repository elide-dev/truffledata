package dev.elide.truffledata;

import java.util.Locale;

/**
 * Myna's target ABI classes (Myna 0.14.0). A native library reports the ABI fingerprint of the
 * class it was built for, and a binding verifies a library against the fingerprint of the class it
 * runs on; the generated {@code <Class>Abi} constants of each bindings jar hold both. The LP64
 * little-endian targets split by the signedness of C {@code char}: signed on x86-64 and Apple
 * arm64, unsigned on AAPCS64 Linux, so Linux AArch64 natives carry their own fingerprint.
 *
 * <p>{@link #HOST} is for JVM (FFM) consumers. Native Image consumers select their class at image
 * build time instead ({@code Platform.includedIn(Platform.LINUX_AARCH64.class)}), so a static image
 * folds exactly one class's constant.
 */
public enum TargetAbi {
  /** LP64, little-endian, signed {@code char}: x86_64-unknown-linux-gnu, aarch64-apple-darwin. */
  LP64_LITTLE(false, "LP64, little-endian"),
  /** LP64, little-endian, unsigned {@code char} (AAPCS64): aarch64-unknown-linux-gnu. */
  LP64_LITTLE_AAPCS64(true, "LP64, little-endian, unsigned char");

  /** The class of the running JVM's host, decided once from {@code os.name} and {@code os.arch}. */
  public static final TargetAbi HOST =
      of(System.getProperty("os.name"), System.getProperty("os.arch"));

  private final boolean charUnsigned;
  private final String description;

  TargetAbi(boolean charUnsigned, String description) {
    this.charUnsigned = charUnsigned;
    this.description = description;
  }

  /** Whether this class's C {@code char} is unsigned (the class criterion). */
  public boolean charUnsigned() {
    return charUnsigned;
  }

  /**
   * The class of a host: Linux on AArch64 is AAPCS64; every other supported host (x86-64 Linux,
   * Apple silicon) is the signed-{@code char} class. Myna generates for these targets only.
   */
  public static TargetAbi of(String osName, String osArch) {
    boolean linux = osName != null && osName.toLowerCase(Locale.ROOT).startsWith("linux");
    boolean aarch64 =
        osArch != null && (osArch.equalsIgnoreCase("aarch64") || osArch.equalsIgnoreCase("arm64"));
    return linux && aarch64 ? LP64_LITTLE_AAPCS64 : LP64_LITTLE;
  }

  @Override
  public String toString() {
    return description;
  }
}
