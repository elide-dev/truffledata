package dev.elide.truffledata.bebop;

import dev.elide.truffledata.bebop.BebopSchema.Branch;
import dev.elide.truffledata.bebop.BebopSchema.EnumDef;
import dev.elide.truffledata.bebop.BebopSchema.MessageDef;
import dev.elide.truffledata.bebop.BebopSchema.StructDef;
import dev.elide.truffledata.bebop.BebopSchema.UnionDef;
import dev.elide.truffledata.bebop.BebopType.Scalar;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The reference for value preservation: maps a host value of a schema type to its abstract value
 * under a profile, as plain Java collections compared with {@code equals}. It is driven by the
 * schema and the documented host mappings alone and never calls the codec, so {@code
 * of(decode(encode(v)))} equal to {@code of(v)} shows the codec lost nothing.
 *
 * <p>Abstract values: integers are their mathematical value whatever box held them ({@code uint64}
 * given as a {@code Long} is its unsigned reading); a float is its bit pattern at the type's
 * width, except that under a strict profile every NaN is the one NaN; a set is unordered under a
 * strict profile and a sequence otherwise; a map is unordered; an absent message field is absent.
 */
final class Abstract {
  private Abstract() {}

  private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);

  static Object of(BebopSchema schema, BebopType type, Object host, boolean strict) {
    return switch (type) {
      case Scalar s -> scalar(s, host, strict);
      case BebopType.Refined r -> of(schema, r.base(), host, strict);
      case BebopType.Ref ref ->
          switch (Objects.requireNonNull(schema.definition(ref.name()))) {
            case EnumDef e -> scalar(e.base(), host, strict);
            case StructDef s -> {
              BebopRecord record = (BebopRecord) host;
              List<Object> out = new ArrayList<>(List.of("struct", s.name()));
              for (int i = 0; i < s.fields().size(); i++)
                out.add(of(schema, s.fields().get(i).type(), Objects.requireNonNull(record.get(i)), strict));
              yield out;
            }
            case MessageDef m -> {
              BebopRecord record = (BebopRecord) host;
              List<Object> out = new ArrayList<>(List.of("message", m.name()));
              for (int i = 0; i < m.fields().size(); i++) {
                Object field = record.get(i);
                out.add(field == null ? "absent" : List.of("present", of(schema, m.fields().get(i).type(), field, strict)));
              }
              yield out;
            }
            case UnionDef u -> {
              BebopUnion union = (BebopUnion) host;
              for (Branch b : u.branches())
                if (b.discriminator() == union.discriminator())
                  yield List.of("union", union.discriminator(), of(schema, b.type(), union.value(), strict));
              yield List.of("union", union.discriminator(), HexFormat.of().formatHex((byte[]) union.value()));
            }
          };
      case BebopType.Array a -> collection(schema, a.element(), host, strict, a.set() && strict);
      case BebopType.FixedArray f -> collection(schema, f.element(), host, strict, false);
      case BebopType.MapOf m -> {
        Map<Object, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) host).entrySet()) {
          Object previous = out.put(of(schema, m.key(), e.getKey(), strict), of(schema, m.value(), e.getValue(), strict));
          if (previous != null) throw new IllegalArgumentException("Host keys collapse to one abstract key");
        }
        yield out;
      }
    };
  }

  private static Object collection(BebopSchema schema, BebopType element, Object host, boolean strict, boolean set) {
    List<Object> items = new ArrayList<>();
    if (host instanceof byte[] bytes) {
      for (byte b : bytes) items.add(BigInteger.valueOf(b & 0xFF));
    } else {
      for (Object item : (List<?>) host) items.add(of(schema, element, item, strict));
    }
    return set ? new HashSet<>(items) : items;
  }

  private static Object scalar(Scalar kind, Object host, boolean strict) {
    return switch (kind) {
      case BOOL, STRING -> host;
      case BYTE, INT8, INT16, UINT16, INT32, UINT32, INT64, INT128, UINT128 -> integer(host);
      case UINT64 -> {
        BigInteger v = integer(host);
        yield host instanceof Long && v.signum() < 0 ? v.add(TWO_64) : v;
      }
      case FLOAT32 -> {
        float f = (Float) host;
        yield strict && Float.isNaN(f) ? "NaN" : "f32:" + Integer.toHexString(Float.floatToRawIntBits(f));
      }
      case FLOAT64 -> {
        double f = (Double) host;
        yield strict && Double.isNaN(f) ? "NaN" : "f64:" + Long.toHexString(Double.doubleToRawLongBits(f));
      }
      case FLOAT16 -> {
        float f = (Float) host;
        if (Float.isNaN(f)) {
          int raw = Float.floatToRawIntBits(f);
          yield strict ? "NaN" : "f16:" + Integer.toHexString(raw >>> 16 & 0x8000 | 0x7C00 | raw >>> 13 & 0x3FF);
        }
        yield "f16:" + Integer.toHexString(Float.floatToFloat16(f) & 0xFFFF);
      }
      case BFLOAT16 -> {
        float f = (Float) host;
        yield strict && Float.isNaN(f) ? "NaN" : "bf16:" + Integer.toHexString(Float.floatToRawIntBits(f) >>> 16);
      }
      case UUID -> host.toString();
      case TIMESTAMP -> {
        BebopTimestamp t = (BebopTimestamp) host;
        yield List.of("timestamp", t.seconds(), t.nanos(), t.offsetMillis());
      }
      case DURATION -> {
        BebopDuration d = (BebopDuration) host;
        yield List.of("duration", d.seconds(), d.nanos());
      }
    };
  }

  private static BigInteger integer(Object host) {
    return host instanceof BigInteger b ? b : BigInteger.valueOf(((Number) host).longValue());
  }
}
