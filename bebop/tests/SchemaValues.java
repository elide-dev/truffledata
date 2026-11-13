package dev.elide.truffledata.bebop;

import dev.elide.truffledata.bebop.BebopSchema.Branch;
import dev.elide.truffledata.bebop.BebopSchema.EnumDef;
import dev.elide.truffledata.bebop.BebopSchema.MessageDef;
import dev.elide.truffledata.bebop.BebopSchema.StructDef;
import dev.elide.truffledata.bebop.BebopSchema.UnionDef;
import dev.elide.truffledata.bebop.BebopType.Scalar;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.jspecify.annotations.Nullable;

/** Random host values of any type of any schema, for schemas loaded from descriptor sets. */
final class SchemaValues {
  private SchemaValues() {}

  private static final String[] TEXT = {"", "a", "b", "aa", "\u0000", "é", "z😀", "中文"};

  static Object value(BebopSchema schema, Random r, BebopType type, int depth) {
    return switch (type) {
      case Scalar s -> scalar(r, s);
      case BebopType.Refined refined -> value(schema, r, refined.base(), depth);
      case BebopType.Ref ref ->
          switch (Objects.requireNonNull(schema.definition(ref.name()))) {
            case EnumDef e -> {
              long member = e.members().get(r.nextInt(e.members().size())).value();
              yield e.base().width() <= 4 && e.base() != Scalar.UINT32 ? (Object) (int) member : (Object) member;
            }
            case StructDef s -> {
              @Nullable Object[] v = new Object[s.fields().size()];
              for (int i = 0; i < v.length; i++) v[i] = value(schema, r, s.fields().get(i).type(), depth + 1);
              yield new BebopRecord(s.name(), v);
            }
            case MessageDef m -> {
              @Nullable Object[] v = new Object[m.fields().size()];
              for (int i = 0; i < v.length; i++)
                if (r.nextInt(depth == 0 ? 2 : 3) == 0 && depth < 4)
                  v[i] = value(schema, r, m.fields().get(i).type(), depth + 1);
              yield new BebopRecord(m.name(), v);
            }
            case UnionDef u -> {
              Branch b = u.branches().get(r.nextInt(u.branches().size()));
              yield new BebopUnion(b.discriminator(), value(schema, r, b.type(), depth + 1));
            }
          };
      case BebopType.Array a -> {
        int n = depth > 3 ? 0 : r.nextInt(4);
        if (a.element() == Scalar.BYTE && !a.set()) {
          byte[] bytes = new byte[n];
          r.nextBytes(bytes);
          yield bytes;
        }
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(value(schema, r, a.element(), depth + 1));
        yield out;
      }
      case BebopType.FixedArray f -> {
        if (f.element() == Scalar.BYTE) {
          byte[] bytes = new byte[f.length()];
          r.nextBytes(bytes);
          yield bytes;
        }
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < f.length(); i++) out.add(value(schema, r, f.element(), depth + 1));
        yield out;
      }
      case BebopType.MapOf m -> {
        Map<Object, Object> out = new LinkedHashMap<>();
        for (int i = depth > 3 ? 0 : r.nextInt(4); i > 0; i--)
          out.put(value(schema, r, m.key(), depth + 1), value(schema, r, m.value(), depth + 1));
        yield out;
      }
    };
  }

  private static Object scalar(Random r, Scalar kind) {
    return switch (kind) {
      case BOOL -> r.nextBoolean();
      case BYTE -> r.nextInt(256);
      case INT8 -> r.nextInt(256) - 128;
      case INT16 -> r.nextInt(65536) - 32768;
      case UINT16 -> r.nextInt(65536);
      case INT32 -> r.nextInt();
      case UINT32 -> r.nextLong() & 0xFFFFFFFFL;
      case INT64, UINT64 -> r.nextLong();
      case INT128 -> new BigInteger(127, r).multiply(BigInteger.valueOf(r.nextBoolean() ? 1 : -1));
      case UINT128 -> new BigInteger(128, r);
      case FLOAT16 -> BebopCodec.fromFloat16((short) r.nextInt());
      case BFLOAT16 -> Float.intBitsToFloat(r.nextInt() << 16);
      case FLOAT32 -> r.nextInt(4) == 0 ? Float.NaN : Float.intBitsToFloat(r.nextInt());
      case FLOAT64 -> r.nextInt(4) == 0 ? Double.NaN : Double.longBitsToDouble(r.nextLong());
      case UUID -> new java.util.UUID(r.nextLong(), r.nextLong());
      case TIMESTAMP ->
          new BebopTimestamp(r.nextLong(), r.nextInt(1_000_000_000), r.nextInt(172_800_001) - 86_400_000);
      case DURATION -> {
        long seconds = r.nextInt(4) == 0 ? 0 : r.nextLong();
        int nanos = r.nextInt(1_000_000_000);
        yield new BebopDuration(seconds, seconds < 0 || seconds == 0 && r.nextBoolean() ? -nanos : nanos);
      }
      case STRING -> TEXT[r.nextInt(TEXT.length)];
    };
  }
}
