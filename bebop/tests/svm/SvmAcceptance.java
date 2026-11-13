package dev.elide.truffledata.bebop.tests;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;

import com.oracle.truffle.api.interop.InteropLibrary;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.Missing;
import dev.elide.truffledata.bebop.BebopCodec;
import dev.elide.truffledata.bebop.BebopDocument;
import dev.elide.truffledata.bebop.BebopException;
import dev.elide.truffledata.bebop.BebopProfile;
import dev.elide.truffledata.bebop.BebopRecord;
import dev.elide.truffledata.bebop.BebopRow;
import dev.elide.truffledata.bebop.BebopSpan;
import dev.elide.truffledata.bebop.BebopValues;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.graalvm.polyglot.Context;

/** Runs codec, strict-profile, bulk-view and guest-read smoke checks in a native image, then the gates. */
public final class SvmAcceptance {
  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError("SVM Bebop acceptance: " + message);
  }

  private static BebopException.Kind rejected(Runnable action) {
    try {
      action.run();
    } catch (BebopException expected) {
      return expected.kind();
    }
    throw new AssertionError("SVM Bebop acceptance: accepted an invalid input");
  }

  static final BebopProfile STRICT =
      BebopProfile.strict("svm-acceptance")
          .admitAll()
          .canonicalNaN16(0x7E00)
          .canonicalNaNBrain16(0x7FC0)
          .canonicalNaN32(0x7FC00000)
          .canonicalNaN64(0x7FF8000000000000L)
          .build();

  private static void codec() {
    BebopCodec general = CompiledReads.codec("Reading", BebopProfile.GENERAL);
    BebopCodec strict = CompiledReads.codec("Reading", STRICT);
    var value = new BebopRecord("Reading", 42L, Float.NaN, true, null);
    byte[] bytes = strict.encode(value);
    // length 17: int64, canonical NaN, true, two boundaries, Mask 8 directory, control.
    require(
        HexFormat.of().formatHex(bytes).equals("110000002a000000000000000000c07f01080c0710"),
        "strict bytes " + HexFormat.of().formatHex(bytes));
    require(BebopValues.equal(value, strict.decode(bytes)), "strict round trip");
    require(Arrays.equals(bytes, strict.encode(strict.decode(bytes))), "strict re-encode");
    strict.validate(bytes);
    strict.decodeReference(bytes);
    byte[] payload = bytes.clone();
    payload[12] = 1; // NaN payload
    require(rejected(() -> strict.decode(payload)) == BebopException.Kind.MALFORMED, "NaN payload");
    require(rejected(() -> strict.validate(payload)) == BebopException.Kind.MALFORMED, "validator");
    require(BebopValues.equal(value, general.decode(payload)), "general keeps the payload");
    byte[] truth = bytes.clone();
    truth[16] = 2; // boolean 02
    require(rejected(() -> strict.decode(truth)) == BebopException.Kind.MALFORMED, "boolean");
    require(general.decode(truth).equals(value), "general boolean");
    require(
        rejected(() -> strict.decode(Arrays.copyOf(bytes, bytes.length + 1)))
            == BebopException.Kind.MALFORMED,
        "trailing byte");
  }

  private static void views() throws Exception {
    BebopCodec codec = CompiledReads.codec("Table", STRICT);
    List<Object> structs =
        List.of(new BebopRecord("Row", 7L, -0.0, true), new BebopRecord("Row", 0xFFFFFFFFL, 2.5, false));
    BebopDocument doc = codec.open(codec.encode(new BebopRecord("Table", structs)));
    BebopSpan ids = java.util.Objects.requireNonNull(doc.span("rows", "id"));
    BebopSpan scores = java.util.Objects.requireNonNull(doc.span("rows", "score"));
    require(ids.count() == 2 && ids.stride() == 13 && ids.getLong(1) == 0xFFFFFFFFL, "id column");
    require(Double.doubleToRawLongBits(scores.getDouble(0)) == Long.MIN_VALUE, "negative zero");
    BebopRow root = doc.row();
    var interop = InteropLibrary.getUncached();
    var data = DataLibrary.factory().getUncached();
    Object rows = interop.readMember(root, "rows");
    require(interop.getArraySize(rows) == 2, "interop rows");
    Object second = interop.readArrayElement(rows, 1);
    require(interop.readMember(second, "id").equals(0xFFFFFFFFL), "interop member");
    require(data.readDouble(second, 1) == 2.5, "DataLibrary double");
    require(!data.readBoolean(second, 2), "DataLibrary boolean");
    BebopCodec reading = CompiledReads.codec("Reading", BebopProfile.GENERAL);
    BebopRow absent =
        reading.open(reading.encode(new BebopRecord("Reading", 1L, null, null, null))).row();
    require(data.read(absent, 1) == Missing.INSTANCE && !data.isPresent(absent, 3), "presence");
    require(absent.accessor(0).readLong(absent) == 1L, "accessor");
  }

  public static void main(String[] args) throws Exception {
    try (var context = Context.create("bebop-test")) {
      context.enter();
      try {
        codec();
        views();
      } finally {
        context.leave();
      }
    }
    CompiledReads.run(STRICT);
    System.out.println("SVM Bebop acceptance passed");
  }
}
