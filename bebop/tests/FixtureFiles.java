package dev.elide.truffledata.bebop;

import dev.elide.truffledata.bebop.BebopSchema.Branch;
import dev.elide.truffledata.bebop.BebopSchema.Definition;
import dev.elide.truffledata.bebop.BebopSchema.EnumDef;
import dev.elide.truffledata.bebop.BebopSchema.Field;
import dev.elide.truffledata.bebop.BebopSchema.Member;
import dev.elide.truffledata.bebop.BebopSchema.MessageDef;
import dev.elide.truffledata.bebop.BebopSchema.StructDef;
import dev.elide.truffledata.bebop.BebopSchema.TaggedField;
import dev.elide.truffledata.bebop.BebopSchema.UnionDef;
import dev.elide.truffledata.bebop.BebopType.Scalar;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Reads the shared fixture files ({@code bebop/fixtures}, format {@code bebop-fixtures/1}). */
final class FixtureFiles {
  private FixtureFiles() {}

  /** One case. Host values are already mapped to the codec's host types. */
  record Case(
      String name,
      String kind,
      @Nullable BebopType root,
      byte @Nullable [] bytes,
      @Nullable Object value,
      @Nullable Object host,
      @Nullable String error,
      boolean canonical,
      @Nullable String operation,
      @Nullable BebopLimits limits) {}

  record File(String name, BebopSchema schema, BebopProfile profile, List<Case> cases) {}

  static final String[] NAMES = {"general.json", "strict.json", "host.json"};

  static File load(String name) {
    Path path =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "bebop/fixtures",
            name);
    Map<?, ?> doc;
    try {
      doc = (Map<?, ?>) new Json(Files.readString(path, StandardCharsets.UTF_8)).value();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    require(doc.get("format").equals("bebop-fixtures/1"), "format");
    require(doc.get("wire").equals(BebopProfile.WIRE_REVISION), "wire revision");
    BebopProfile profile = profile((Map<?, ?>) doc.get("profile"));
    BebopSchema.Builder builder = BebopSchema.builder();
    for (Object def : (List<?>) doc.get("definitions")) builder.add(definition((Map<?, ?>) def));
    BebopSchema schema = builder.build();
    List<Case> cases = new ArrayList<>();
    for (Object item : (List<?>) doc.get("cases")) {
      Map<?, ?> c = (Map<?, ?>) item;
      String kind = (String) c.get("kind");
      BebopType root = c.containsKey("root") ? type(c.get("root")) : Scalar.DURATION;
      Map<?, ?> limits = (Map<?, ?>) c.get("limits");
      cases.add(
          new Case(
              (String) c.get("name"),
              kind,
              root,
              c.containsKey("bytes") ? HexFormat.of().parseHex((String) c.get("bytes")) : null,
              c.get("value") == null ? null : host(schema, root, c.get("value")),
              c.get("host") == null ? null : host(schema, root, c.get("host")),
              (String) c.get("error"),
              !Boolean.FALSE.equals(c.get("canonical")),
              (String) c.get("operation"),
              limits == null
                  ? null
                  : new BebopLimits(
                      ((Long) limits.get("maxBytes")).intValue(),
                      ((Long) limits.get("maxCount")).intValue(),
                      ((Long) limits.get("maxDepth")).intValue(),
                      Long.parseLong((String) limits.get("maxWork")),
                      Long.parseLong((String) limits.get("maxAllocation")))));
    }
    return new File(name, schema, profile, cases);
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new IllegalArgumentException("Fixture " + what);
  }

  private static BebopProfile profile(Map<?, ?> p) {
    if (p.get("name").equals("general")) return BebopProfile.GENERAL;
    require(p.get("admit").equals("all"), "admit");
    int nan16 = Integer.parseInt((String) p.get("nan16"), 16);
    int nanBrain16 = Integer.parseInt((String) p.get("nanBrain16"), 16);
    int nan32 = Integer.parseUnsignedInt((String) p.get("nan32"), 16);
    long nan64 = Long.parseUnsignedLong((String) p.get("nan64"), 16);
    String rules = (String) p.get("rules");
    // The named rules revision fixes these four patterns; anything else is a custom profile.
    if (nan16 == 0x7E00 && nanBrain16 == 0x7FC0 && nan32 == 0x7FC00000 && nan64 == 0x7FF8000000000000L)
      return BebopProfile.rules011(rules);
    return BebopProfile.strict(rules)
        .admitAll()
        .canonicalNaN16(nan16)
        .canonicalNaNBrain16(nanBrain16)
        .canonicalNaN32(nan32)
        .canonicalNaN64(nan64)
        .build();
  }

  private static Definition definition(Map<?, ?> d) {
    if (d.containsKey("enum")) {
      List<Member> members = new ArrayList<>();
      for (Object m : (List<?>) d.get("members")) {
        List<?> pair = (List<?>) m;
        members.add(new Member((String) pair.get(0), new BigInteger((String) pair.get(1)).longValue()));
      }
      return new EnumDef((String) d.get("enum"), (Scalar) type(d.get("base")), members);
    }
    if (d.containsKey("struct")) {
      List<Field> fields = new ArrayList<>();
      for (Object f : (List<?>) d.get("fields")) {
        List<?> pair = (List<?>) f;
        fields.add(new Field((String) pair.get(0), type(pair.get(1))));
      }
      return new StructDef((String) d.get("struct"), fields);
    }
    if (d.containsKey("message")) {
      List<TaggedField> fields = new ArrayList<>();
      for (Object f : (List<?>) d.get("fields")) {
        List<?> triple = (List<?>) f;
        fields.add(new TaggedField(((Long) triple.get(0)).intValue(), (String) triple.get(1), type(triple.get(2))));
      }
      return new MessageDef((String) d.get("message"), fields);
    }
    List<Branch> branches = new ArrayList<>();
    for (Object b : (List<?>) d.get("branches")) {
      List<?> triple = (List<?>) b;
      branches.add(new Branch(((Long) triple.get(0)).intValue(), (String) triple.get(1), type(triple.get(2))));
    }
    return new UnionDef((String) d.get("union"), branches);
  }

  static BebopType type(Object json) {
    if (json instanceof String name)
      return name.equals("utc-instant") ? BebopType.utcInstant() : Scalar.valueOf(name.toUpperCase(Locale.ROOT));
    Map<?, ?> t = (Map<?, ?>) json;
    if (t.containsKey("array")) return BebopType.array(type(t.get("array")));
    if (t.containsKey("set")) return BebopType.set(type(t.get("set")));
    if (t.containsKey("ref")) return BebopType.ref((String) t.get("ref"));
    if (t.containsKey("fixed")) {
      List<?> pair = (List<?>) t.get("fixed");
      return BebopType.fixed(type(pair.get(0)), ((Long) pair.get(1)).intValue());
    }
    List<?> pair = (List<?>) t.get("map");
    return BebopType.map(type(pair.get(0)), type(pair.get(1)));
  }

  /** A typed fixture value as the host value the codec takes and returns for {@code type}. */
  static Object host(BebopSchema schema, BebopType type, Object json) {
    Map<?, ?> v = (Map<?, ?>) json;
    switch (type) {
      case Scalar s -> {
        return scalar(s, v);
      }
      case BebopType.Refined r -> {
        return host(schema, r.base(), json);
      }
      case BebopType.Ref ref -> {
        switch (Objects.requireNonNull(schema.definition(ref.name()))) {
          case EnumDef e -> {
            return scalar(e.base(), v);
          }
          case StructDef s -> {
            List<?> fields = (List<?>) v.get("record");
            @Nullable Object[] out = new Object[fields.size()];
            for (int i = 0; i < out.length; i++) out[i] = host(schema, s.fields().get(i).type(), fields.get(i));
            return new BebopRecord(s.name(), out);
          }
          case MessageDef m -> {
            List<?> fields = (List<?>) v.get("record");
            @Nullable Object[] out = new Object[fields.size()];
            for (int i = 0; i < out.length; i++)
              out[i] = fields.get(i) == null ? null : host(schema, m.fields().get(i).type(), fields.get(i));
            return new BebopRecord(m.name(), out);
          }
          case UnionDef u -> {
            List<?> pair = (List<?>) v.get("union");
            int discriminator = ((Long) pair.get(0)).intValue();
            for (Branch b : u.branches())
              if (b.discriminator() == discriminator)
                return new BebopUnion(discriminator, host(schema, b.type(), pair.get(1)));
            return new BebopUnion(discriminator, HexFormat.of().parseHex((String) ((Map<?, ?>) pair.get(1)).get("raw")));
          }
        }
      }
      case BebopType.Array a -> {
        return list(schema, a.element(), v);
      }
      case BebopType.FixedArray f -> {
        return list(schema, f.element(), v);
      }
      case BebopType.MapOf m -> {
        Map<Object, Object> out = new LinkedHashMap<>();
        for (Object entry : (List<?>) v.get("map")) {
          List<?> pair = (List<?>) entry;
          out.put(host(schema, m.key(), pair.get(0)), host(schema, m.value(), pair.get(1)));
        }
        return out;
      }
    }
  }

  private static Object list(BebopSchema schema, BebopType element, Map<?, ?> v) {
    if (v.containsKey("bytes")) return HexFormat.of().parseHex((String) v.get("bytes"));
    List<Object> out = new ArrayList<>();
    for (Object item : (List<?>) (v.containsKey("seq") ? v.get("seq") : v.get("set"))) out.add(host(schema, element, item));
    return out;
  }

  private static Object scalar(Scalar kind, Map<?, ?> v) {
    switch (kind) {
      case BOOL -> {
        return v.get("bool");
      }
      case BYTE, INT8, INT16, UINT16, INT32 -> {
        BigInteger i = new BigInteger((String) v.get("int"));
        return i.bitLength() <= 31 ? (Object) i.intValueExact() : i;
      }
      case UINT32, INT64 -> {
        BigInteger i = new BigInteger((String) v.get("int"));
        return i.bitLength() <= 63 ? (Object) i.longValueExact() : i;
      }
      case UINT64 -> {
        // The host container of uint64 is a long holding the bits; out-of-range stays a BigInteger.
        BigInteger i = new BigInteger((String) v.get("int"));
        return i.signum() >= 0 && i.bitLength() <= 64 ? (Object) i.longValue() : i;
      }
      case INT128, UINT128 -> {
        return new BigInteger((String) v.get("int"));
      }
      case FLOAT16 -> {
        if (v.containsKey("bits32")) return Float.intBitsToFloat(Integer.parseUnsignedInt((String) v.get("bits32"), 16));
        int bits = Integer.parseInt((String) v.get("bits"), 16);
        // binary16 widens exactly; a NaN keeps its sign, quiet bit and payload.
        if ((bits & 0x7C00) == 0x7C00 && (bits & 0x3FF) != 0)
          return Float.intBitsToFloat((bits & 0x8000) << 16 | 0x7F800000 | (bits & 0x3FF) << 13);
        return Float.float16ToFloat((short) bits);
      }
      case BFLOAT16 -> {
        if (v.containsKey("bits32")) return Float.intBitsToFloat(Integer.parseUnsignedInt((String) v.get("bits32"), 16));
        return Float.intBitsToFloat(Integer.parseInt((String) v.get("bits"), 16) << 16);
      }
      case FLOAT32 -> {
        return Float.intBitsToFloat(Integer.parseUnsignedInt((String) v.get("bits"), 16));
      }
      case FLOAT64 -> {
        return Double.longBitsToDouble(Long.parseUnsignedLong((String) v.get("bits"), 16));
      }
      case UUID -> {
        return java.util.UUID.fromString((String) v.get("uuid"));
      }
      case TIMESTAMP -> {
        List<?> t = (List<?>) v.get("timestamp");
        return new BebopTimestamp(Long.parseLong((String) t.get(0)), Integer.parseInt((String) t.get(1)), Integer.parseInt((String) t.get(2)));
      }
      case DURATION -> {
        List<?> t = (List<?>) v.get("duration");
        // Adapter inputs may exceed the wire's nanosecond field; those stay as a seconds/nanos pair.
        long nanos = Long.parseLong((String) t.get(1));
        return nanos == (int) nanos
            ? new BebopDuration(Long.parseLong((String) t.get(0)), (int) nanos)
            : List.of(Long.parseLong((String) t.get(0)), nanos);
      }
      default -> {
        return v.get("string");
      }
    }
  }

  /** A JSON reader sufficient for the fixtures: objects, arrays, strings, integers, literals. */
  private static final class Json {
    private final String text;
    private int at;

    Json(String text) {
      this.text = text;
    }

    private char peek() {
      while (Character.isWhitespace(text.charAt(at))) at++;
      return text.charAt(at);
    }

    @Nullable Object value() {
      char c = peek();
      if (c == '{') {
        at++;
        Map<String, @Nullable Object> out = new LinkedHashMap<>();
        while (peek() != '}') {
          String key = (String) Objects.requireNonNull(value());
          if (peek() != ':') throw new IllegalArgumentException("Expected ':' at " + at);
          at++;
          out.put(key, value());
          if (peek() == ',') at++;
        }
        at++;
        return out;
      }
      if (c == '[') {
        at++;
        List<@Nullable Object> out = new ArrayList<>();
        while (peek() != ']') {
          out.add(value());
          if (peek() == ',') at++;
        }
        at++;
        return out;
      }
      if (c == '"') {
        at++;
        StringBuilder out = new StringBuilder();
        while (text.charAt(at) != '"') {
          char ch = text.charAt(at++);
          if (ch != '\\') {
            out.append(ch);
            continue;
          }
          char escape = text.charAt(at++);
          switch (escape) {
            case 'n' -> out.append('\n');
            case 't' -> out.append('\t');
            case 'r' -> out.append('\r');
            case 'b' -> out.append('\b');
            case 'f' -> out.append('\f');
            case 'u' -> {
              out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
              at += 4;
            }
            default -> out.append(escape);
          }
        }
        at++;
        return out.toString();
      }
      int start = at;
      while (at < text.length() && "{}[],: \n\r\t".indexOf(text.charAt(at)) < 0) at++;
      String token = text.substring(start, at);
      return switch (token) {
        case "true" -> Boolean.TRUE;
        case "false" -> Boolean.FALSE;
        case "null" -> null;
        default -> Long.parseLong(token);
      };
    }
  }
}
