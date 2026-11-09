package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Myna 0.14.0 target ABI classes: Linux AArch64 (AAPCS64, unsigned char) is its own class. */
class TargetAbiTest {
  @Test
  void linuxAarch64IsTheUnsignedCharClass() {
    assertEquals(TargetAbi.LP64_LITTLE_AAPCS64, TargetAbi.of("Linux", "aarch64"));
    assertEquals(TargetAbi.LP64_LITTLE_AAPCS64, TargetAbi.of("linux", "arm64"));
    assertTrue(TargetAbi.LP64_LITTLE_AAPCS64.charUnsigned());
  }

  @Test
  void everyOtherReleaseTargetIsTheSignedCharClass() {
    assertEquals(TargetAbi.LP64_LITTLE, TargetAbi.of("Linux", "amd64"));
    assertEquals(TargetAbi.LP64_LITTLE, TargetAbi.of("Linux", "x86_64"));
    assertEquals(TargetAbi.LP64_LITTLE, TargetAbi.of("Mac OS X", "aarch64"));
    assertEquals(TargetAbi.LP64_LITTLE, TargetAbi.of("Mac OS X", "x86_64"));
    assertEquals(TargetAbi.LP64_LITTLE, TargetAbi.of("", ""));
    assertFalse(TargetAbi.LP64_LITTLE.charUnsigned());
  }

  @Test
  void hostIsDecidedOnceFromTheSystemProperties() {
    assertSame(
        TargetAbi.of(System.getProperty("os.name"), System.getProperty("os.arch")),
        TargetAbi.HOST);
    assertSame(TargetAbi.HOST, TargetAbi.HOST);
  }
}
