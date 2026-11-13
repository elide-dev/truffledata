package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;
import static dev.elide.truffledata.bebop.BebopType.ref;

import dev.elide.truffledata.bebop.kernel.svm.SvmValidator;
import java.util.Random;

/**
 * The statically linked native validator in a native image, against the Java reference: the same
 * inputs accepted and the same failure reported, through both call shapes.
 */
public final class KernelAcceptance {
  private KernelAcceptance() {}

  private static String outcome(BebopValidator validator, BebopCodec codec, byte[] bytes) {
    try {
      validator.validate(codec, bytes, 0, bytes.length);
      return "accepted";
    } catch (BebopException e) {
      return e.kind() + ": " + e.getMessage();
    }
  }

  public static void main(String[] args) {
    SvmValidator kernel = new SvmValidator();
    BebopType[] roots = {
      ref("Everything"), ref("Columns"), ref("Tree"), ref("Skippy"), BebopType.map(STRING, INT32), TIMESTAMP,
    };
    Random r = new Random(0x53564D);
    int accepted = 0;
    int total = 0;
    for (BebopType root : roots) {
      for (BebopProfile profile : new BebopProfile[] {BebopProfile.GENERAL, Fixtures.STRICT}) {
        BebopCodec codec = BebopCodec.compile(Fixtures.SCHEMA, root, profile, BebopLimits.DEFAULT);
        for (int i = 0; i < 300; i++) {
          byte[] bytes = codec.encode(Gen.value(r, root, 0));
          for (int m = 0; m < 12; m++) {
            byte[] input = m == 0 ? bytes : Gen.mutate(r, bytes);
            String expected = outcome(BebopValidator.JAVA, codec, input);
            String actual = outcome(kernel, codec, input);
            if (!expected.equals(actual))
              throw new AssertionError(
                  root + " " + profile + ": java " + expected + ", native " + actual + " for " + Fixtures.hex(input));
            if (expected.equals("accepted")) accepted++;
            total++;
          }
        }
      }
    }
    if (accepted < total / 20 || accepted == total) throw new AssertionError(accepted + " of " + total);
    // Past the leaf bound the ordinary call is taken.
    BebopCodec blob = Fixtures.general(BebopType.array(BYTE));
    byte[] large = blob.encode(new byte[SvmValidator.LEAF_BYTES + 3]);
    kernel.validate(blob, large, 0, large.length);
    large[0]++;
    if (!outcome(kernel, blob, large).equals(outcome(BebopValidator.JAVA, blob, large)))
      throw new AssertionError("large input");
    BebopCodec table = Fixtures.general(ref("Tree"));
    byte[] tree = table.encode(new BebopRecord("Tree", 7, null));
    if (!table.adopt(tree, kernel).row().get(0).equals(7L)) throw new AssertionError("document");
    System.out.println(
        "SVM Bebop kernel acceptance passed: " + kernel.name() + ", " + accepted + " of " + total + " accepted");
  }
}
