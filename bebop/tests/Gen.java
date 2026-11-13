package dev.elide.truffledata.bebop;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ExactNumber;
import dev.elide.truffledata.Missing;
import dev.elide.truffledata.bebop.BebopSchema.Branch;
import dev.elide.truffledata.bebop.BebopSchema.EnumDef;
import dev.elide.truffledata.bebop.BebopSchema.Field;
import dev.elide.truffledata.bebop.BebopSchema.MessageDef;
import dev.elide.truffledata.bebop.BebopSchema.StructDef;
import dev.elide.truffledata.bebop.BebopSchema.TaggedField;
import dev.elide.truffledata.bebop.BebopSchema.UnionDef;
import dev.elide.truffledata.bebop.BebopType.Scalar;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.jspecify.annotations.Nullable;

/** Random values of any type of {@link Fixtures#SCHEMA}, and the guest-view comparison. */
final class Gen {
  private Gen() {}

  private static final String[] TEXT = {
    "", "a", "b", "aa", "\u0000", "é", "z😀", "abcdefghijklmnop", "中文"
  };

  static Object value(Random r, BebopType type, int depth) {
    return switch (type) {
      case Scalar s -> scalar(r, s);
      case BebopType.Refined refined -> new BebopTimestamp(r.nextLong(), r.nextInt(1_000_000_000), 0);
      case BebopType.Ref ref ->
          switch (Objects.requireNonNull(Fixtures.SCHEMA.definition(ref.name()))) {
            case EnumDef e -> {
              long member = e.members().get(r.nextInt(e.members().size())).value();
              yield e.base().width() <= 4 && e.base() != Scalar.UINT32 ? (Object) (int) member : (Object) member;
            }
            case StructDef s -> {
              @Nullable Object[] v = new Object[s.fields().size()];
              for (int i = 0; i < v.length; i++) v[i] = value(r, s.fields().get(i).type(), depth + 1);
              yield new BebopRecord(s.name(), v);
            }
            case MessageDef m -> {
              @Nullable Object[] v = new Object[m.fields().size()];
              for (int i = 0; i < v.length; i++)
                if (r.nextInt(depth == 0 ? 2 : 3) == 0 && depth < 4)
                  v[i] = value(r, m.fields().get(i).type(), depth + 1);
              yield new BebopRecord(m.name(), v);
            }
            case UnionDef u -> {
              Branch b = u.branches().get(r.nextInt(u.branches().size()));
              yield new BebopUnion(b.discriminator(), value(r, b.type(), depth + 1));
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
        for (int i = 0; i < n; i++) out.add(value(r, a.element(), depth + 1));
        yield out;
      }
      case BebopType.FixedArray f -> {
        if (f.element() == Scalar.BYTE) {
          byte[] bytes = new byte[f.length()];
          r.nextBytes(bytes);
          yield bytes;
        }
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < f.length(); i++) out.add(value(r, f.element(), depth + 1));
        yield out;
      }
      case BebopType.MapOf m -> {
        Map<Object, Object> out = new LinkedHashMap<>();
        for (int i = depth > 3 ? 0 : r.nextInt(4); i > 0; i--)
          out.put(value(r, m.key(), depth + 1), value(r, m.value(), depth + 1));
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
      case INT32 -> r.nextInt(8) == 0 ? r.nextInt(3) - 1 : r.nextInt();
      case UINT32 -> r.nextLong() & 0xFFFFFFFFL;
      case INT64, UINT64 -> r.nextInt(8) == 0 ? -1L : r.nextLong();
      case INT128 -> new BigInteger(127, r).multiply(BigInteger.valueOf(r.nextBoolean() ? 1 : -1));
      case UINT128 -> new BigInteger(128, r);
      case FLOAT16 -> BebopCodec.fromFloat16((short) r.nextInt());
      case BFLOAT16 -> Float.intBitsToFloat(r.nextInt() << 16);
      case FLOAT32 ->
          switch (r.nextInt(6)) {
            case 0 -> Float.NaN;
            case 1 -> -0.0f;
            case 2 -> Float.intBitsToFloat(r.nextInt());
            default -> r.nextFloat();
          };
      case FLOAT64 ->
          switch (r.nextInt(6)) {
            case 0 -> Double.NaN;
            case 1 -> -0.0;
            case 2 -> Double.longBitsToDouble(r.nextLong());
            default -> r.nextGaussian();
          };
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

  /** Asserts that the guest view {@code guest} shows exactly the decoded {@code host} value. */
  static void same(BebopType type, Object host, Object guest, String path) {
    switch (type) {
      case Scalar s -> scalar(s, host, guest, path);
      case BebopType.Refined refined -> same(refined.base(), host, guest, path);
      case BebopType.Ref ref -> {
        switch (Objects.requireNonNull(Fixtures.SCHEMA.definition(ref.name()))) {
          case EnumDef e -> scalar(e.base(), host, guest, path);
          case StructDef s -> {
            BebopRow row = (BebopRow) guest;
            BebopRecord record = (BebopRecord) host;
            assertEquals(s.fields().size(), row.schema().size(), path);
            for (int i = 0; i < s.fields().size(); i++) {
              Field f = s.fields().get(i);
              assertTrue(row.present(i), path);
              same(f.type(), Objects.requireNonNull(record.get(i)), row.get(i), path + "." + f.name());
            }
          }
          case MessageDef m -> {
            BebopRow row = (BebopRow) guest;
            BebopRecord record = (BebopRecord) host;
            for (int i = 0; i < m.fields().size(); i++) {
              TaggedField f = m.fields().get(i);
              Object field = record.get(i);
              assertEquals(field != null, row.present(i), path + "." + f.name());
              if (field == null) assertSame(Missing.INSTANCE, row.get(i), path + "." + f.name());
              else same(f.type(), field, row.get(i), path + "." + f.name());
            }
          }
          case UnionDef u -> {
            BebopRow row = (BebopRow) guest;
            BebopUnion union = (BebopUnion) host;
            assertEquals((long) union.discriminator(), row.get(0), path);
            List<Branch> sorted = new ArrayList<>(u.branches());
            sorted.sort(Comparator.comparingInt(Branch::discriminator));
            for (int i = 0; i < sorted.size(); i++) {
              Branch b = sorted.get(i);
              boolean active = b.discriminator() == union.discriminator();
              assertEquals(active, row.present(i + 1), path + "." + b.name());
              if (active) same(b.type(), union.value(), row.get(i + 1), path + "." + b.name());
            }
          }
        }
      }
      case BebopType.Array a -> array(a.element(), host, guest, path);
      case BebopType.FixedArray f -> array(f.element(), host, guest, path);
      case BebopType.MapOf m -> {
        BebopArray array = (BebopArray) guest;
        Map<?, ?> map = (Map<?, ?>) host;
        assertEquals(map.size(), array.size(), path);
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          BebopRow row = (BebopRow) array.get(i);
          same(m.key(), entry.getKey(), row.get(0), path + "[" + i + "].key");
          same(m.value(), entry.getValue(), row.get(1), path + "[" + i + "].value");
          i++;
        }
      }
    }
  }

  private static void array(BebopType element, Object host, Object guest, String path) {
    BebopArray array = (BebopArray) guest;
    if (host instanceof byte[] bytes) {
      assertEquals(bytes.length, array.size(), path);
      assertTrue(array.isBytes(), path);
      byte[] copy = new byte[bytes.length + 2];
      array.copyBytesTo(copy, 1);
      for (int i = 0; i < bytes.length; i++) {
        assertEquals((long) (bytes[i] & 0xFF), array.get(i), path);
        assertEquals(bytes[i], copy[i + 1], path);
      }
      return;
    }
    List<?> list = (List<?>) host;
    assertEquals(list.size(), array.size(), path);
    // Backwards, so variable-width elements are reached through the offset pass, not in order.
    for (int i = list.size() - 1; i >= 0; i--) same(element, list.get(i), array.get(i), path + "[" + i + "]");
  }

  private static void scalar(Scalar kind, Object host, Object guest, String path) {
    switch (kind) {
      case BOOL, STRING -> assertEquals(host, guest, path);
      case BYTE, INT8, INT16, UINT16, INT32, UINT32, INT64 ->
          assertEquals(((Number) host).longValue(), guest, path);
      case UINT64 ->
          assertEquals(Long.toUnsignedString((Long) host), ((ExactNumber) guest).decimal().toPlainString(), path);
      case INT128, UINT128 -> assertEquals(host, ((ExactNumber) guest).decimal().toBigIntegerExact(), path);
      case FLOAT16, BFLOAT16, FLOAT32 ->
          assertEquals(
              Double.doubleToLongBits(((Float) host).doubleValue()), Double.doubleToLongBits((Double) guest), path);
      case FLOAT64 -> assertEquals(Double.doubleToRawLongBits((Double) host), Double.doubleToRawLongBits((Double) guest), path);
      case UUID -> assertEquals(host.toString(), guest, path);
      case TIMESTAMP -> {
        BebopTimestamp t = (BebopTimestamp) host;
        BebopRow row = (BebopRow) guest;
        assertEquals(t.seconds(), row.get(0), path);
        assertEquals((long) t.nanos(), row.get(1), path);
        assertEquals((long) t.offsetMillis(), row.get(2), path);
      }
      case DURATION -> {
        BebopDuration t = (BebopDuration) host;
        BebopRow row = (BebopRow) guest;
        assertEquals(t.seconds(), row.get(0), path);
        assertEquals((long) t.nanos(), row.get(1), path);
      }
    }
  }

  /** A mutation of valid bytes: bit flips, byte replacement, truncation, extension, splices. */
  static byte[] mutate(Random r, byte[] bytes) {
    byte[] mutant = bytes.clone();
    switch (r.nextInt(6)) {
      case 0 -> mutant[r.nextInt(mutant.length)] ^= (byte) (1 << r.nextInt(8));
      case 1 -> mutant[r.nextInt(mutant.length)] = (byte) r.nextInt(256);
      case 2 -> mutant = java.util.Arrays.copyOf(mutant, mutant.length - 1 - r.nextInt(Math.min(4, mutant.length)));
      case 3 -> {
        mutant = java.util.Arrays.copyOf(mutant, mutant.length + 1);
        mutant[mutant.length - 1] = (byte) r.nextInt(256);
      }
      case 4 -> {
        int a = r.nextInt(mutant.length);
        int b = r.nextInt(mutant.length);
        byte t = mutant[a];
        mutant[a] = mutant[b];
        mutant[b] = t;
      }
      default -> mutant[r.nextInt(mutant.length)] = (byte) (r.nextBoolean() ? 0 : 0xFF);
    }
    return mutant;
  }
}
