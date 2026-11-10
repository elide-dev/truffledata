package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.TargetAbi;
import dev.elide.truffledata.csv.kernel.generated.CsvNativeAbi;
import dev.elide.truffledata.csv.kernel.jvm.FfmIndexer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Myna 0.14.0 target ABI classes for the native CSV kernel: the bindings carry each class's
 * fingerprint, Linux AArch64 selects its own, and a library of the other class is rejected.
 */
final class CsvNativeAbiTest {
  static Path runfile(String path) {
    return Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), path);
  }

  static String fingerprintOf(String abiClass) throws Exception {
    return Files.readString(runfile("csv/native/generated/abi/" + abiClass + "/seam.abi")).strip();
  }

  @Test
  void abiClassFingerprintsFollowTheGeneratedClasses() throws Exception {
    assertEquals(fingerprintOf("lp64_little"), CsvNativeAbi.LP64_LITTLE);
    assertEquals(fingerprintOf("lp64_little_aapcs64"), CsvNativeAbi.LP64_LITTLE_AAPCS64);
    assertNotEquals(CsvNativeAbi.LP64_LITTLE, CsvNativeAbi.LP64_LITTLE_AAPCS64);
    assertEquals(
        CsvNativeAbi.LP64_LITTLE_AAPCS64,
        CsvNativeAbi.fingerprint(TargetAbi.of("Linux", "aarch64").charUnsigned()));
    assertEquals(
        CsvNativeAbi.LP64_LITTLE,
        CsvNativeAbi.fingerprint(TargetAbi.of("Linux", "amd64").charUnsigned()));
    assertEquals(
        CsvNativeAbi.LP64_LITTLE,
        CsvNativeAbi.fingerprint(TargetAbi.of("Mac OS X", "aarch64").charUnsigned()));
  }

  /**
   * A library of the other ABI class (Linux AArch64's fingerprint here, the shared x86-64/Apple one
   * there) is rejected before any downcall is bound, never loaded as a fallback; this host's is
   * accepted.
   */
  @Test
  void otherAbiClassLibraryIsRejected() {
    Path other = runfile("csv/native/other_abi_class.so");
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> FfmIndexer.Library.load(other));
    assertTrue(failure.getMessage().contains("ABI fingerprint mismatch"), failure.getMessage());
    FfmIndexer.Library accepted = FfmIndexer.Library.load(runfile("csv/native/csv_shared.so"));
    assertTrue(accepted.kernel() >= 0);
  }
}
