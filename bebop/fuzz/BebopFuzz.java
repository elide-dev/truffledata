package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;
import static dev.elide.truffledata.bebop.BebopType.ref;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

/**
 * Untrusted-input harness. The first byte selects a root type (low seven bits) and the strict
 * profile (high bit); the rest is the candidate encoding. Oracles: the decoder and the
 * allocation-free validator agree on acceptance and on the reason for rejection; under the strict
 * profile the decode-and-reencode reference agrees and accepted bytes re-encode to themselves; and
 * every accepted input reads the same in place as it decodes. Only {@link BebopException} may
 * escape the codec.
 */
public final class BebopFuzz {
  private BebopFuzz() {}

  private static final BebopType[] ROOTS = {
    ref("Everything"),
    ref("Columns"),
    ref("Table"),
    ref("Tree"),
    ref("Shape"),
    ref("Strict"),
    BebopType.map(STRING, INT32),
    BebopType.array(ref("Empty")),
    BebopType.set(BebopType.array(BYTE)),
    ref("Skippy"),
  };

  /** Small limits: campaigns must reach limit paths without long loops or large allocations. */
  private static final BebopLimits LIMITS = new BebopLimits(1 << 16, 1 << 12, 16, 1 << 14, 1 << 20);

  private static final BebopCodec[] CODECS = new BebopCodec[ROOTS.length * 2];

  static {
    for (int i = 0; i < ROOTS.length; i++) {
      CODECS[2 * i] = BebopCodec.compile(Fixtures.SCHEMA, ROOTS[i], BebopProfile.GENERAL, LIMITS);
      CODECS[2 * i + 1] = BebopCodec.compile(Fixtures.SCHEMA, ROOTS[i], Fixtures.STRICT, LIMITS);
    }
  }

  private static void fail(String message, byte[] input) {
    throw new AssertionError(message + ": " + Fixtures.hex(input));
  }

  public static void fuzzerTestOneInput(byte[] input) {
    if (input.length == 0) return;
    int model = (input[0] & 0x7F) % ROOTS.length;
    boolean strict = input[0] < 0;
    BebopCodec codec = CODECS[2 * model + (strict ? 1 : 0)];
    byte[] bytes = Arrays.copyOfRange(input, 1, input.length);
    Object decoded = null;
    BebopException rejected = null;
    try {
      decoded = codec.decode(bytes);
    } catch (BebopException e) {
      rejected = e;
    }
    try {
      codec.validate(bytes);
      if (rejected != null && rejected.kind() != BebopException.Kind.LIMIT)
        fail("validator accepted what the decoder rejected (" + rejected.getMessage() + ")", input);
    } catch (BebopException e) {
      // Decoding also charges an allocation budget, so only it can reach that limit.
      if (rejected == null) fail("validator rejected what the decoder accepted (" + e.getMessage() + ")", input);
      else if (rejected.kind() != BebopException.Kind.LIMIT && !e.getMessage().equals(rejected.getMessage()))
        fail("validator and decoder disagree on the reason", input);
    }
    if (strict) {
      try {
        Object reference = codec.decodeReference(bytes);
        if (rejected != null && rejected.kind() != BebopException.Kind.LIMIT)
          fail("reference accepted what the strict decoder rejected", input);
        if (decoded != null && !BebopValues.equal(decoded, reference)) fail("reference value differs", input);
      } catch (BebopException e) {
        if (rejected == null) fail("reference rejected what the strict decoder accepted", input);
      }
    }
    if (decoded == null) return;
    byte[] again = codec.encode(decoded);
    if (strict && !Arrays.equals(again, bytes)) fail("strict bytes do not re-encode to themselves", input);
    if (!BebopValues.equal(decoded, codec.decode(again))) fail("re-encoded value differs", input);
    try {
      Gen.same(ROOTS[model], decoded, codec.adopt(bytes).root(), "root");
    } catch (BebopException e) {
      fail("document rejected accepted bytes", input);
    }
  }

  /**
   * Loads every class the harness reaches before the campaign starts, so the per-input time
   * limit measures the codec and not first-use class loading and instrumentation.
   */
  public static void fuzzerInitialize() {
    Random r = new Random(1);
    for (int model = 0; model < ROOTS.length; model++) {
      for (int strict = 0; strict < 2; strict++) {
        byte[] body;
        try {
          body = CODECS[2 * model + strict].encode(Gen.value(r, ROOTS[model], 0));
        } catch (BebopException e) {
          continue;
        }
        byte[] input = new byte[body.length + 1];
        input[0] = (byte) (model | strict << 7);
        System.arraycopy(body, 0, input, 1, body.length);
        fuzzerTestOneInput(input);
        input[input.length - 1] ^= 0x55;
        fuzzerTestOneInput(input);
      }
    }
  }

  /** Replays generated seeds and their mutations, then every file given. */
  public static void main(String[] args) throws Exception {
    Random r = new Random(0xF022);
    for (int model = 0; model < ROOTS.length; model++) {
      for (int strict = 0; strict < 2; strict++) {
        BebopCodec codec = CODECS[2 * model + strict];
        for (int i = 0; i < 150; i++) {
          byte[] bytes;
          try {
            bytes = codec.encode(Gen.value(r, ROOTS[model], 0));
          } catch (BebopException e) {
            if (e.kind() != BebopException.Kind.LIMIT) throw e;
            continue;
          }
          for (int m = 0; m < 12; m++) {
            byte[] body = m == 0 ? bytes : Gen.mutate(r, bytes);
            byte[] input = new byte[body.length + 1];
            input[0] = (byte) (model | strict << 7);
            System.arraycopy(body, 0, input, 1, body.length);
            fuzzerTestOneInput(input);
          }
        }
      }
    }
    for (String file : args) fuzzerTestOneInput(Files.readAllBytes(Path.of(file)));
    System.out.println("Bebop replay passed");
  }
}
