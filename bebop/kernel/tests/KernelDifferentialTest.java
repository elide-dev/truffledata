package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;
import static dev.elide.truffledata.bebop.BebopType.ref;
import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.bebop.BebopException.Kind;
import dev.elide.truffledata.bebop.kernel.jvm.FfmValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The native validator against the Java reference: the same inputs accepted, the same failure
 * reported, for every type under both profiles.
 */
final class KernelDifferentialTest {
  private static final FfmValidator NATIVE = FfmValidator.load(library());

  private static Path library() {
    Path dir = Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), "bebop/native");
    try (var files = Files.list(dir)) {
      return files
          .filter(f -> f.getFileName().toString().matches("lib.*\\.(so|dylib)"))
          .findFirst()
          .orElseThrow();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static final BebopType[] ROOTS = {
    ref("Everything"),
    ref("Columns"),
    ref("Table"),
    ref("Tree"),
    ref("Shape"),
    ref("Strict"),
    ref("Skippy"),
    BebopType.map(STRING, INT32),
    BebopType.map(BOOL, STRING),
    BebopType.map(UUID, ref("Point")),
    BebopType.array(ref("Empty")),
    BebopType.set(BebopType.array(BYTE)),
    BebopType.fixed(ref("Level"), 3),
    BebopType.set(ref("Status")),
    BebopType.array(ref("Level")),
    STRING,
    TIMESTAMP,
    DURATION,
    FLOAT16,
    BFLOAT16,
    FLOAT32,
    FLOAT64,
    BOOL,
    UINT128,
  };

  private static String outcome(BebopValidator validator, BebopCodec codec, byte[] bytes) {
    try {
      validator.validate(codec, bytes, 0, bytes.length);
      return "accepted";
    } catch (BebopException e) {
      return e.kind() + ": " + e.getMessage();
    }
  }

  private static int check(BebopCodec codec, byte[] bytes) {
    String expected = outcome(BebopValidator.JAVA, codec, bytes);
    assertEquals(expected, outcome(NATIVE, codec, bytes), () -> codec.rootType() + " " + codec.profile() + " " + Fixtures.hex(bytes));
    return expected.equals("accepted") ? 1 : 0;
  }

  @Test
  void agreesWithTheReference() {
    Random r = new Random(0x4B45);
    BebopLimits tight = new BebopLimits(1 << 16, 64, 6, 400, 600);
    int accepted = 0;
    int total = 0;
    for (BebopType root : ROOTS) {
      for (BebopProfile profile : new BebopProfile[] {BebopProfile.GENERAL, Fixtures.STRICT}) {
        BebopCodec codec = BebopCodec.compile(Fixtures.SCHEMA, root, profile, BebopLimits.DEFAULT);
        BebopCodec limited = BebopCodec.compile(Fixtures.SCHEMA, root, profile, tight);
        for (int i = 0; i < 200; i++) {
          byte[] bytes = codec.encode(Gen.value(r, root, 0));
          accepted += check(codec, bytes);
          check(limited, bytes);
          total++;
          for (int m = 0; m < 16; m++) {
            byte[] mutant = Gen.mutate(r, bytes);
            accepted += check(codec, mutant);
            check(limited, mutant);
            total++;
          }
        }
        check(codec, new byte[0]);
      }
    }
    assertTrue(accepted > total / 20 && accepted < total, accepted + " of " + total + " accepted");
  }

  /** Every wire and resource case of the shared fixtures, through the native validator. */
  @Test
  void sharedFixtures() {
    int ran = 0;
    for (String name : FixtureFiles.NAMES) {
      FixtureFiles.File file = FixtureFiles.load(name);
      for (FixtureFiles.Case c : file.cases()) {
        if (c.bytes() == null || c.root() == null) continue;
        BebopCodec codec =
            BebopCodec.compile(file.schema(), c.root(), file.profile(), c.limits() == null ? BebopLimits.DEFAULT : c.limits());
        String outcome = outcome(NATIVE, codec, c.bytes());
        String label = name + ": " + c.name();
        assertEquals(outcome(BebopValidator.JAVA, codec, c.bytes()), outcome, label);
        switch (c.kind()) {
          case "wire-accept", "host-normalize" -> assertEquals("accepted", outcome, label);
          case "wire-reject" -> assertTrue(outcome.startsWith("MALFORMED"), label + " " + outcome);
          default -> {
            // Resource cases name their operation: only the validating ones bind this validator.
            if ("validate".equals(c.operation()))
              assertEquals("none".equals(c.error()) ? "accepted" : "LIMIT", outcome.split(":")[0], label);
          }
        }
        ran++;
      }
    }
    assertTrue(ran >= 160, ran + " cases");
  }

  /** All 65,536 patterns of each 16-bit float, with ample budgets, against the Java validator. */
  @Test
  void reducedFloatsExhaustively() {
    for (BebopType kind : new BebopType[] {FLOAT16, BFLOAT16}) {
      for (BebopProfile profile : new BebopProfile[] {BebopProfile.GENERAL, Fixtures.STRICT}) {
        BebopCodec codec = BebopCodec.compile(Fixtures.SCHEMA, kind, profile, BebopLimits.DEFAULT);
        int accepted = 0;
        for (int bits = 0; bits < 0x10000; bits++) accepted += check(codec, new byte[] {(byte) bits, (byte) (bits >>> 8)});
        int nans = kind == FLOAT16 ? 2 * 1023 : 2 * 127;
        assertEquals(profile.strict() ? 65536 - nans + 1 : 65536, accepted, kind + " " + profile);
      }
    }
  }

  @Test
  void utcInstants() {
    for (BebopProfile profile : new BebopProfile[] {BebopProfile.GENERAL, Fixtures.STRICT}) {
      BebopCodec utc = BebopCodec.compile(Fixtures.SCHEMA, BebopType.utcInstant(), profile, BebopLimits.DEFAULT);
      BebopCodec any = BebopCodec.compile(Fixtures.SCHEMA, TIMESTAMP, profile, BebopLimits.DEFAULT);
      byte[] zero = any.encode(new BebopTimestamp(5, 7, 0));
      byte[] offset = any.encode(new BebopTimestamp(5, 7, -1));
      assertEquals(1, check(utc, zero));
      assertEquals(0, check(utc, offset));
      assertEquals(1, check(any, offset));
      assertEquals(
          "MALFORMED: Timestamp has an offset under a UTC-instant schema", outcome(NATIVE, utc, offset));
    }
  }

  @Test
  void callShapes() {
    BebopCodec codec = Fixtures.general(BebopType.array(BYTE));
    assertEquals("native-ffm", NATIVE.name());
    assertEquals("native-ffm", NATIVE.toString());
    assertEquals("java", BebopValidator.JAVA.toString());
    // In place at an offset: nothing outside the range is read.
    byte[] padded = new byte[32];
    Arrays.fill(padded, (byte) 0xEE);
    System.arraycopy(Fixtures.hex("03000000 010203"), 0, padded, 5, 7);
    NATIVE.validate(codec, padded, 5, 7);
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> NATIVE.validate(codec, padded, 5, 8)).kind());
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> NATIVE.validate(codec, padded, 5, 6)).kind());
    assertThrows(IndexOutOfBoundsException.class, () -> NATIVE.validate(codec, padded, 30, 7));
    // Past the critical bound the input is copied for an ordinary downcall.
    byte[] large = codec.encode(new byte[FfmValidator.CRITICAL_BYTES + 5]);
    NATIVE.validate(codec, large, 0, large.length);
    large[0]++;
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> NATIVE.validate(codec, large, 0, large.length)).kind());
    BebopCodec small = BebopCodec.compile(Fixtures.SCHEMA, BebopType.array(BYTE), BebopProfile.GENERAL, new BebopLimits(8, 8, 8, 8, 8));
    assertEquals(Kind.LIMIT, assertThrows(BebopException.class, () -> NATIVE.validate(small, new byte[9], 0, 9)).kind());
    // Documents can be opened through it.
    BebopCodec table = Fixtures.general(ref("Table"));
    byte[] bytes = table.encode(new BebopRecord("Table", null, List.of("a"), List.of(5L), null, null));
    assertEquals(5L, ((BebopArray) table.open(bytes, NATIVE).row().get(2)).get(0));
    assertEquals(5L, ((BebopArray) table.adopt(bytes.clone(), BebopValidator.JAVA).row().get(2)).get(0));
    byte[] bad = bytes.clone();
    bad[bad.length - 1] = 0x7F;
    assertEquals(Kind.MALFORMED, assertThrows(BebopException.class, () -> table.open(bad, NATIVE)).kind());
    assertThrows(IllegalArgumentException.class, () -> FfmValidator.load(Path.of("/nonexistent/libbebop.so")));
  }
}
