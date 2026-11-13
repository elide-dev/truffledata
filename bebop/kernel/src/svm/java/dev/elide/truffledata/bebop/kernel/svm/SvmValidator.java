package dev.elide.truffledata.bebop.kernel.svm;

import dev.elide.truffledata.bebop.BebopCodec;
import dev.elide.truffledata.bebop.BebopValidator;
import dev.elide.truffledata.bebop.kernel.generated.BebopNative;
import dev.elide.truffledata.bebop.kernel.generated.BebopNativeAbi;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

/**
 * The native validator in a native image, linked statically. The schema program and the input
 * are pinned and read in place. Inputs of at most {@link #LEAF_BYTES} take the leaf call, which
 * has no thread-state transition; larger ones take the ordinary call so a long validation never
 * holds up a safepoint.
 */
public final class SvmValidator extends BebopValidator {
  public static final int LEAF_BYTES = 1 << 20;

  private static boolean verified;

  public SvmValidator() {
    if (!verified) {
      // The image's ABI class (Platform.includedIn folds at image build time): one constant.
      String expected = BebopNativeAbi.fingerprint(Platform.includedIn(Platform.LINUX_AARCH64.class));
      // The library's fingerprint is 64 hex characters without a terminator.
      byte[] actual = new byte[expected.length()];
      CTypeConversion.asByteBuffer(BebopNative.fingerprint(), actual.length).get(actual);
      if (!new String(actual, StandardCharsets.US_ASCII).equals(expected))
        throw new IllegalStateException("Myna/Bebop ABI fingerprint mismatch");
      verified = true;
    }
  }

  @Override
  public String name() {
    return "native-svm";
  }

  @Override
  public void validate(BebopCodec codec, byte[] bytes, int offset, int length) {
    Objects.checkFromIndexSize(offset, length, bytes.length);
    byte[] program = program(codec);
    long status;
    try (PinnedObject schema = PinnedObject.create(program);
        PinnedObject input = PinnedObject.create(bytes)) {
      CCharPointer p = schema.addressOfArrayElement(0);
      CCharPointer d = input.addressOfArrayElement(offset);
      status =
          length <= LEAF_BYTES
              ? BebopNative.validate_leaf(p, program.length, d, length)
              : BebopNative.validate(p, program.length, d, length);
    }
    report(status);
  }
}
