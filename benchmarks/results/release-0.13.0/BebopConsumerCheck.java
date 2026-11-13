import dev.elide.truffledata.bebop.*;
import dev.elide.truffledata.bebop.BebopSchema.*;
import java.util.*;

public final class Check {
  public static void main(String[] args) {
    BebopSchema schema =
        BebopSchema.builder()
            .struct("Row", new Field("id", BebopType.Scalar.UINT32), new Field("score", BebopType.Scalar.FLOAT64))
            .message("Table", new TaggedField(1, "rows", BebopType.array(BebopType.ref("Row"))),
                new TaggedField(2, "at", BebopType.utcInstant()))
            .build();
    BebopProfile strict = BebopProfile.rules011("consumer-check");
    for (BebopProfile profile : new BebopProfile[] {BebopProfile.GENERAL, strict}) {
      BebopCodec codec = BebopCodec.compile(schema, BebopType.ref("Table"), profile, BebopLimits.DEFAULT);
      BebopRecord value = new BebopRecord("Table",
          List.of(new BebopRecord("Row", 7L, 0.5), new BebopRecord("Row", 8L, Double.NaN)), new BebopTimestamp(5, 6, 0));
      byte[] bytes = codec.encode(value);
      if (!BebopValues.equal(value, codec.decode(bytes))) throw new AssertionError("round trip");
      if (!Arrays.equals(bytes, codec.encode(codec.decode(bytes)))) throw new AssertionError("re-encode");
      codec.validate(bytes);
      BebopDocument doc = codec.open(bytes);
      BebopSpan ids = Objects.requireNonNull(doc.span("rows", "id"));
      if (ids.count() != 2 || ids.getLong(1) != 8) throw new AssertionError("span");
      BebopRow row = doc.row();
      if (!((BebopRow) ((BebopArray) row.get(0)).get(0)).get(0).equals(7L)) throw new AssertionError("row");
      bytes[bytes.length - 1] ^= 0x20;
      try { codec.decode(bytes); throw new AssertionError("accepted a reserved control bit"); }
      catch (BebopException e) { if (e.kind() != BebopException.Kind.MALFORMED) throw new AssertionError(e); }
      System.out.println(profile.identity().split(";")[0] + ": ok, " + codec.identity().substring(0, 12));
    }
    System.out.println("truffledata-bebop 0.13.0 public consumer check passed");
  }
}
