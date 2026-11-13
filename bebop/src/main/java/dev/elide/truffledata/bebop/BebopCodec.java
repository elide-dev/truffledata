package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopException.limit;
import static dev.elide.truffledata.bebop.BebopException.malformed;
import static dev.elide.truffledata.bebop.BebopException.unsupported;
import static dev.elide.truffledata.bebop.BebopException.value;

import dev.elide.truffledata.bebop.BebopSchema.Branch;
import dev.elide.truffledata.bebop.BebopSchema.Definition;
import dev.elide.truffledata.bebop.BebopSchema.EnumDef;
import dev.elide.truffledata.bebop.BebopSchema.MessageDef;
import dev.elide.truffledata.bebop.BebopSchema.StructDef;
import dev.elide.truffledata.bebop.BebopSchema.TaggedField;
import dev.elide.truffledata.bebop.BebopSchema.UnionDef;
import dev.elide.truffledata.bebop.BebopType.Scalar;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An encoder and decoder for one root type of one schema under one profile.
 *
 * <p>Host values: {@code Boolean}; {@code Integer} for {@code byte}, {@code int8}, {@code int16},
 * {@code uint16} and {@code int32}; {@code Long} for {@code uint32}, {@code int64} and (as bits)
 * {@code uint64}; {@code BigInteger} for the 128-bit integers; {@code Float} for {@code float16},
 * {@code bfloat16} and {@code float32}; {@code Double}; {@code String}; {@code UUID}; {@link
 * BebopTimestamp}; {@link BebopDuration}; {@code byte[]} for arrays of {@code byte}; {@code List}
 * for other arrays; {@code Map}; {@link BebopRecord} for structs and messages; {@link BebopUnion};
 * and the integer value for enums.
 *
 * <p>Both profiles reject trailing bytes, fields that do not fill their indexed range, invalid
 * UTF-8, wrong string terminators, duplicate map keys and non-canonical message indexes. The
 * strict profile also rejects everything that has a second encoding: see {@link BebopProfile}.
 */
public final class BebopCodec {
  private final BebopSchema schema;
  private final BebopProfile profile;
  private final BebopLimits limits;
  private final BebopType rootType;
  final Node root;
  final Layouts layouts = new Layouts();
  private byte @Nullable [] program;

  private BebopCodec(
      BebopSchema schema, BebopProfile profile, BebopLimits limits, BebopType rootType, Node root) {
    this.schema = schema;
    this.profile = profile;
    this.limits = limits;
    this.rootType = rootType;
    this.root = root;
  }

  /** Compiles a codec, rejecting schemas and types the profile does not support. */
  public static BebopCodec compile(
      BebopSchema schema, BebopType root, BebopProfile profile, BebopLimits limits) {
    Compiler compiler = new Compiler(schema, profile);
    Node node = compiler.node(Objects.requireNonNull(root));
    for (Node n : compiler.all) n.min();
    return new BebopCodec(schema, profile, Objects.requireNonNull(limits), root, node);
  }

  public BebopSchema schema() {
    return schema;
  }

  public BebopProfile profile() {
    return profile;
  }

  public BebopLimits limits() {
    return limits;
  }

  public BebopType rootType() {
    return rootType;
  }

  /**
   * The exact identity of what this codec reads and writes, apart from the profile: the wire
   * revision, every definition of the schema, and the root type with its set/sequence choices and
   * refinements. Two codecs over one schema with different roots have different identities.
   */
  public String schemaIdentity() {
    return BebopSchema.digest(
        "wire=" + BebopProfile.WIRE_REVISION
            + "\nschema=" + schema.fingerprint()
            + "\nroot=" + BebopSchema.render(rootType));
  }

  /** {@link #schemaIdentity()} bound to the profile's identity: everything that decides acceptance. */
  public String identity() {
    return BebopSchema.digest("schema=" + schemaIdentity() + "\nprofile=" + profile.identity());
  }

  /** Encodes a value. Under a strict profile the result is the value's only accepted encoding. */
  public byte[] encode(Object value) {
    Enc e = new Enc(new Writer(64, limits.maxBytes()), profile, limits);
    root.encode(e, Objects.requireNonNull(value));
    return e.w.toByteArray();
  }

  public Object decode(byte[] bytes) {
    return decode(bytes, 0, bytes.length);
  }

  /** Decodes exactly {@code [offset, offset + length)}; no byte outside it is read. */
  public Object decode(byte[] bytes, int offset, int length) {
    return decode(bytes, offset, length, profile.strict());
  }

  /**
   * The reference validator of a strict profile: decodes without the canonical-form checks,
   * re-encodes, and accepts only if the bytes are reproduced. {@link #decode} must accept exactly
   * the same inputs.
   */
  public Object decodeReference(byte[] bytes) {
    Object decoded = decode(bytes, 0, bytes.length, false);
    if (profile.strict()) {
      byte[] again;
      try {
        again = encode(decoded);
      } catch (BebopException e) {
        // A value the strict encoder refuses has no canonical encoding at all.
        throw e.kind() == BebopException.Kind.VALUE
            ? malformed("Not a canonical encoding: " + e.getMessage())
            : e;
      }
      if (!Arrays.equals(again, bytes)) throw malformed("Not the canonical encoding");
    }
    return decoded;
  }

  /** Checks the bytes exactly as {@link #decode} does, without building the value. */
  public void validate(byte[] bytes) {
    validate(bytes, 0, bytes.length);
  }

  /** Checks exactly {@code [offset, offset + length)}; no byte outside it is read. */
  public void validate(byte[] bytes, int offset, int length) {
    Dec d = decoder(bytes, offset, length, profile.strict());
    root.validate(d);
    if (d.pos != d.end) throw malformed("Trailing bytes");
  }

  /** The schema program native validators interpret; built once and never modified. */
  byte[] program() {
    byte[] p = program;
    if (p == null) program = p = BebopProgram.of(this);
    return p;
  }

  /** Validates the bytes and retains a private copy of them for rows and bulk views. */
  public BebopDocument open(byte[] bytes) {
    return adopt(bytes.clone());
  }

  /** {@link #open(byte[])} with the given validator in place of the built-in one. */
  public BebopDocument open(byte[] bytes, BebopValidator validator) {
    return adopt(bytes.clone(), validator);
  }

  /** {@link #adopt(byte[])} with the given validator in place of the built-in one. */
  public BebopDocument adopt(byte[] bytes, BebopValidator validator) {
    validator.validate(this, bytes, 0, bytes.length);
    return new BebopDocument(this, bytes);
  }

  /**
   * Validates the bytes and retains the array itself. The caller transfers ownership and must not
   * write to it again.
   */
  public BebopDocument adopt(byte[] bytes) {
    validate(bytes);
    return new BebopDocument(this, bytes);
  }

  private Object decode(byte[] bytes, int offset, int length, boolean strict) {
    Dec d = decoder(bytes, offset, length, strict);
    Object out = root.decode(d);
    if (d.pos != d.end) throw malformed("Trailing bytes");
    return out;
  }

  private Dec decoder(byte[] bytes, int offset, int length, boolean strict) {
    Objects.checkFromIndexSize(offset, length, bytes.length);
    if (length > limits.maxBytes()) throw limit("Input exceeds the byte limit");
    return new Dec(bytes, offset, offset + length, strict, profile, limits);
  }

  // ---------------------------------------------------------------------------------------------

  static final class Enc {
    Writer w;
    final boolean strict;
    final BebopProfile profile;
    final BebopLimits limits;
    int depth;

    Enc(Writer w, BebopProfile profile, BebopLimits limits) {
      this.w = w;
      this.strict = profile.strict();
      this.profile = profile;
      this.limits = limits;
    }

    void enter() {
      if (++depth > limits.maxDepth()) throw limit("Nesting exceeds the depth limit");
    }

    void leave() {
      depth--;
    }

    Writer scratch() {
      return new Writer(64, limits.maxBytes());
    }
  }

  static final class Dec {
    final byte[] buf;
    int pos;
    int end;
    final boolean strict;
    final BebopProfile profile;
    final BebopLimits limits;
    private int depth;
    private long work;
    private long allocation;

    Dec(byte[] buf, int pos, int end, boolean strict, BebopProfile profile, BebopLimits limits) {
      this.buf = buf;
      this.pos = pos;
      this.end = end;
      this.strict = strict;
      this.profile = profile;
      this.limits = limits;
    }

    /** Consumes {@code n} bytes of the current region and returns their offset. */
    int take(int n) {
      int at = pos;
      if (n > end - at) throw malformed("Truncated value");
      pos = at + n;
      return at;
    }

    long u32() {
      return (int) Writer.I32.get(buf, take(4)) & 0xFFFFFFFFL;
    }

    /** A length prefix of a region that must fit what remains. */
    int length(int min) {
      long length = u32();
      if (length < min || length > end - pos) throw malformed("Length is out of range");
      return (int) length;
    }

    /**
     * A collection count. Counts that cannot fit the remaining bytes are malformed whatever the
     * local limits are; elements that take no bytes are bounded by the count and work limits.
     */
    int count(int minElementSize) {
      long count = u32();
      if (minElementSize > 0 && count > (end - pos) / minElementSize)
        throw malformed("Count exceeds the remaining bytes");
      if (count > limits.maxCount()) throw limit("Collection exceeds the count limit");
      work(count);
      return (int) count;
    }

    void work(long units) {
      if ((work += units) > limits.maxWork()) throw limit("Work limit reached");
    }

    void allocate(long bytes) {
      if ((allocation += bytes) > limits.maxAllocation()) throw limit("Allocation limit reached");
    }

    void enter() {
      if (++depth > limits.maxDepth()) throw limit("Nesting exceeds the depth limit");
    }

    void leave() {
      depth--;
    }
  }

  // ---------------------------------------------------------------------------------------------

  abstract static sealed class Node {
    private int min = -1;
    private int fixed = -2;

    abstract void encode(Enc e, Object v);

    abstract Object decode(Dec d);

    /** Checks one value exactly as {@link #decode} does, retaining nothing. */
    abstract void validate(Dec d);

    /** Advances past one value that is already known to be valid. */
    abstract void skip(Dec d);

    abstract long computeMin();

    /** The exact size if every value of the type has one, else -1. */
    int computeFixed() {
      return -1;
    }

    /** The least number of bytes a value takes. */
    final int min() {
      if (min == -2) throw unsupported("A struct contains itself");
      if (min < 0) {
        min = -2;
        min = (int) Math.min(computeMin(), Integer.MAX_VALUE);
      }
      return min;
    }

    final int fixed() {
      if (fixed == -2) fixed = computeFixed();
      return fixed;
    }
  }

  private static final class Compiler {
    private final BebopSchema schema;
    private final BebopProfile profile;
    private final Map<String, Node> named = new HashMap<>();
    final List<Node> all = new ArrayList<>();

    Compiler(BebopSchema schema, BebopProfile profile) {
      this.schema = Objects.requireNonNull(schema);
      this.profile = Objects.requireNonNull(profile);
    }

    private <N extends Node> N add(N node) {
      all.add(node);
      return node;
    }

    private ScalarNode scalar(Scalar kind) {
      if (!profile.admits(kind))
        throw unsupported("The profile does not admit " + kind);
      return add(new ScalarNode(kind));
    }

    Node node(BebopType type) {
      return switch (type) {
        case Scalar s -> scalar(s);
        case BebopType.Ref r -> named(r.name());
        case BebopType.Refined r -> {
          if (r.base() != Scalar.TIMESTAMP || r.refinement() != BebopType.Refinement.UTC_INSTANT)
            throw unsupported("No validation of " + r.refinement() + " on " + BebopSchema.render(r.base()));
          if (!profile.admits(Scalar.TIMESTAMP))
            throw unsupported("The profile does not admit " + Scalar.TIMESTAMP);
          yield add(new ScalarNode(Scalar.TIMESTAMP, true));
        }
        case BebopType.Array a -> {
          if (a.element() == Scalar.BYTE && !a.set()) {
            scalar(Scalar.BYTE);
            yield add(new BytesNode(-1));
          }
          yield add(new ArrayNode(node(a.element()), a.set(), -1));
        }
        case BebopType.FixedArray f -> {
          if (f.element() == Scalar.BYTE) {
            scalar(Scalar.BYTE);
            yield add(new BytesNode(f.length()));
          }
          yield add(new ArrayNode(node(f.element()), false, f.length()));
        }
        case BebopType.MapOf m -> {
          Node key = node(m.key());
          boolean keyed =
              key instanceof EnumNode
                  || key instanceof ScalarNode s
                      && (s.kind.integer()
                          || s.kind == Scalar.BOOL
                          || s.kind == Scalar.STRING
                          || s.kind == Scalar.UUID);
          if (!keyed) throw unsupported("Map keys must be integers, bool, string, uuid or enums");
          yield add(new MapNode(key, node(m.value())));
        }
      };
    }

    private Node named(String name) {
      Node known = named.get(name);
      if (known != null) return known;
      Definition def = schema.definition(name);
      if (def == null) throw unsupported("Unknown type " + name);
      switch (def) {
        case EnumDef e -> {
          if (e.flags() && profile.strict())
            throw unsupported("Flags enum " + name + " has no strict rule");
          long[] members = e.members().stream().mapToLong(BebopSchema.Member::value).sorted().toArray();
          EnumNode node = add(new EnumNode(scalar(e.base()), members));
          named.put(name, node);
          return node;
        }
        case StructDef s -> {
          StructNode node = add(new StructNode(name, s.fields().size()));
          named.put(name, node);
          for (int i = 0; i < node.fields.length; i++) {
            node.names[i] = s.fields().get(i).name();
            node.fields[i] = node(s.fields().get(i).type());
          }
          return node;
        }
        case MessageDef m -> {
          MessageNode node = add(new MessageNode(name, m.fields().size()));
          named.put(name, node);
          for (int i = 0; i < node.fields.length; i++) {
            TaggedField f = m.fields().get(i);
            if (node.ordinalByTag[f.tag()] >= 0) throw unsupported("Duplicate tag in " + name);
            node.ordinalByTag[f.tag()] = i;
            node.tags[i] = f.tag();
            node.names[i] = f.name();
            node.fields[i] = node(f.type());
          }
          for (int tag = 1, k = 0; tag < 256; tag++)
            if (node.ordinalByTag[tag] >= 0) node.order[k++] = node.ordinalByTag[tag];
          return node;
        }
        case UnionDef u -> {
          UnionNode node = add(new UnionNode(name));
          named.put(name, node);
          for (Branch b : u.branches()) {
            if (node.branches[b.discriminator()] != null)
              throw unsupported("Duplicate discriminator in " + name);
            node.names[b.discriminator()] = b.name();
            node.branches[b.discriminator()] = node(b.type());
          }
          return node;
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------

  private static long integer(Object v, long min, long max) {
    long x;
    if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
      x = ((Number) v).longValue();
    } else if (v instanceof BigInteger b && b.bitLength() <= 63) {
      x = b.longValue();
    } else {
      throw value("Expected an integer");
    }
    if (x < min || x > max) throw value("Integer is out of range");
    return x;
  }

  private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);
  private static final String UTC_OFFSET = "Timestamp has an offset under a UTC-instant schema";

  /**
   * float16 to float, exactly. NaNs keep their sign, quiet bit and payload, which the library
   * conversion does not promise for signaling NaNs.
   */
  static float fromFloat16(short bits) {
    if ((bits & 0x7C00) == 0x7C00 && (bits & 0x3FF) != 0)
      return Float.intBitsToFloat((bits & 0x8000) << 16 | 0x7F800000 | (bits & 0x3FF) << 13);
    return Float.float16ToFloat(bits);
  }

  static BigInteger integer128(byte[] b, int at, boolean unsigned) {
    long low = (long) Writer.I64.get(b, at);
    long high = (long) Writer.I64.get(b, at + 8);
    BigInteger top = BigInteger.valueOf(high);
    if (unsigned && high < 0) top = top.add(TWO_64);
    BigInteger bottom = BigInteger.valueOf(low);
    if (low < 0) bottom = bottom.add(TWO_64);
    return top.shiftLeft(64).add(bottom);
  }

  /** Strict timestamps: nanoseconds in [0, 999999999] and an offset within one day. */
  static boolean canonicalTimestamp(int nanos, int offsetMillis) {
    return nanos >= 0 && nanos <= 999_999_999 && offsetMillis >= -86_400_000 && offsetMillis <= 86_400_000;
  }

  /**
   * Strict durations: under a second of nanoseconds, never opposite in sign to the seconds, so
   * every span has one form.
   */
  static boolean canonicalDuration(long seconds, int nanos) {
    return nanos > -1_000_000_000
        && nanos < 1_000_000_000
        && !(seconds > 0 && nanos < 0)
        && !(seconds < 0 && nanos > 0);
  }

  private static int toFloat16(float f) {
    int raw = Float.floatToRawIntBits(f);
    if (Float.isNaN(f)) {
      if ((raw & 0x1FFF) != 0) throw value("Not representable as float16");
      return raw >>> 16 & 0x8000 | 0x7C00 | raw >>> 13 & 0x3FF;
    }
    short bits = Float.floatToFloat16(f);
    if (Float.floatToRawIntBits(Float.float16ToFloat(bits)) != raw)
      throw value("Not representable as float16");
    return bits;
  }

  static final class ScalarNode extends Node {
    final Scalar kind;
    /** A timestamp that must have a zero offset. */
    final boolean utc;

    ScalarNode(Scalar kind) {
      this(kind, false);
    }

    ScalarNode(Scalar kind, boolean utc) {
      this.kind = kind;
      this.utc = utc;
    }

    @Override
    long computeMin() {
      return kind == Scalar.STRING ? 5 : kind.width();
    }

    @Override
    int computeFixed() {
      return kind.width();
    }

    @Override
    void skip(Dec d) {
      if (kind == Scalar.STRING) d.take(d.length(0) + 1);
      else d.take(kind.width());
    }

    @Override
    void encode(Enc e, Object v) {
      Writer w = e.w;
      switch (kind) {
        case BOOL -> {
          if (!(v instanceof Boolean b)) throw value("Expected a boolean");
          w.u8(b ? 1 : 0);
        }
        case BYTE -> w.u8((int) integer(v, 0, 0xFF));
        case INT8 -> w.u8((int) integer(v, Byte.MIN_VALUE, Byte.MAX_VALUE));
        case INT16 -> w.u16((int) integer(v, Short.MIN_VALUE, Short.MAX_VALUE));
        case UINT16 -> w.u16((int) integer(v, 0, 0xFFFF));
        case INT32 -> w.u32((int) integer(v, Integer.MIN_VALUE, Integer.MAX_VALUE));
        case UINT32 -> w.u32((int) integer(v, 0, 0xFFFFFFFFL));
        case INT64 -> w.u64(integer(v, Long.MIN_VALUE, Long.MAX_VALUE));
        case UINT64 -> {
          if (v instanceof Long bits) {
            w.u64(bits);
          } else if (v instanceof BigInteger b) {
            if (b.signum() < 0 || b.bitLength() > 64) throw value("Integer is out of range");
            w.u64(b.longValue());
          } else {
            w.u64(integer(v, 0, Long.MAX_VALUE));
          }
        }
        case INT128, UINT128 -> {
          BigInteger b =
              v instanceof BigInteger big
                  ? big
                  : BigInteger.valueOf(integer(v, Long.MIN_VALUE, Long.MAX_VALUE));
          boolean fits =
              kind == Scalar.INT128 ? b.bitLength() <= 127 : b.signum() >= 0 && b.bitLength() <= 128;
          if (!fits) throw value("Integer is out of range");
          w.u64(b.longValue());
          w.u64(b.shiftRight(64).longValue());
        }
        case FLOAT16 -> {
          float f = real(v);
          w.u16(e.strict && Float.isNaN(f) ? e.profile.nan16 : toFloat16(f));
        }
        case BFLOAT16 -> {
          float f = real(v);
          int bits = Float.floatToRawIntBits(f);
          if (e.strict && Float.isNaN(f)) {
            w.u16(e.profile.nanBrain16);
          } else {
            if ((bits & 0xFFFF) != 0) throw value("Not representable as bfloat16");
            w.u16(bits >>> 16);
          }
        }
        case FLOAT32 -> {
          float f = real(v);
          w.u32(e.strict && Float.isNaN(f) ? e.profile.nan32 : Float.floatToRawIntBits(f));
        }
        case FLOAT64 -> {
          if (!(v instanceof Double f)) throw value("Expected a double");
          w.u64(e.strict && Double.isNaN(f) ? e.profile.nan64 : Double.doubleToRawLongBits(f));
        }
        case UUID -> {
          if (!(v instanceof UUID u)) throw value("Expected a UUID");
          w.b64(u.getMostSignificantBits());
          w.b64(u.getLeastSignificantBits());
        }
        case TIMESTAMP -> {
          if (!(v instanceof BebopTimestamp t)) throw value("Expected a timestamp");
          if (e.strict && !canonicalTimestamp(t.nanos(), t.offsetMillis()))
            throw value("Timestamp is outside the canonical range");
          if (utc && t.offsetMillis() != 0) throw value(UTC_OFFSET);
          w.u64(t.seconds());
          w.u32(t.nanos());
          w.u32(t.offsetMillis());
        }
        case DURATION -> {
          if (!(v instanceof BebopDuration t)) throw value("Expected a duration");
          if (e.strict && !canonicalDuration(t.seconds(), t.nanos()))
            throw value("Duration is not in canonical form");
          w.u64(t.seconds());
          w.u32(t.nanos());
        }
        case STRING -> {
          if (!(v instanceof String s)) throw value("Expected a string");
          if (!Utf8.encodable(s)) throw value("String has an unpaired surrogate");
          byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
          w.u32(utf8.length);
          w.bytes(utf8, 0, utf8.length);
          w.u8(0);
        }
      }
    }

    private static float real(Object v) {
      if (!(v instanceof Float f)) throw value("Expected a float");
      return f;
    }

    /** Checks the value at {@code d.pos} and consumes it. Returns its offset. */
    private int check(Dec d) {
      byte[] b = d.buf;
      if (kind == Scalar.STRING) {
        long length = d.u32();
        if (length >= d.end - d.pos) throw malformed("String exceeds the remaining bytes");
        int at = d.take((int) length + 1);
        if (b[at + (int) length] != 0) throw malformed("String terminator is not NUL");
        if (!Utf8.valid(b, at, at + (int) length)) throw malformed("String is not valid UTF-8");
        return at;
      }
      int at = d.take(kind.width());
      if (!d.strict) {
        if (utc && (int) Writer.I32.get(b, at + 12) != 0) throw malformed(UTC_OFFSET);
        return at;
      }
      switch (kind) {
        case BOOL -> {
          if ((b[at] & 0xFE) != 0) throw malformed("Boolean is neither 00 nor 01");
        }
        case FLOAT16 -> {
          int bits = (short) Writer.I16.get(b, at) & 0xFFFF;
          if ((bits & 0x7C00) == 0x7C00 && (bits & 0x3FF) != 0 && bits != d.profile.nan16)
            throw malformed("NaN is not the canonical pattern");
        }
        case BFLOAT16 -> {
          int bits = (short) Writer.I16.get(b, at) & 0xFFFF;
          if ((bits & 0x7F80) == 0x7F80 && (bits & 0x7F) != 0 && bits != d.profile.nanBrain16)
            throw malformed("NaN is not the canonical pattern");
        }
        case FLOAT32 -> {
          int bits = (int) Writer.I32.get(b, at);
          if (Float.isNaN(Float.intBitsToFloat(bits)) && bits != d.profile.nan32)
            throw malformed("NaN is not the canonical pattern");
        }
        case FLOAT64 -> {
          long bits = (long) Writer.I64.get(b, at);
          if (Double.isNaN(Double.longBitsToDouble(bits)) && bits != d.profile.nan64)
            throw malformed("NaN is not the canonical pattern");
        }
        case TIMESTAMP -> {
          if (!canonicalTimestamp((int) Writer.I32.get(b, at + 8), (int) Writer.I32.get(b, at + 12)))
            throw malformed("Timestamp is outside the canonical range");
          if (utc && (int) Writer.I32.get(b, at + 12) != 0) throw malformed(UTC_OFFSET);
        }
        case DURATION -> {
          if (!canonicalDuration((long) Writer.I64.get(b, at), (int) Writer.I32.get(b, at + 8)))
            throw malformed("Duration is not in canonical form");
        }
        default -> {}
      }
      return at;
    }

    @Override
    void validate(Dec d) {
      check(d);
    }

    /** The integer at {@code at}, as {@link #decode} would box it. For integer kinds to 64 bits. */
    long integerAt(byte[] b, int at) {
      return switch (kind) {
        case BYTE -> b[at] & 0xFF;
        case INT8 -> b[at];
        case INT16 -> (short) Writer.I16.get(b, at);
        case UINT16 -> (short) Writer.I16.get(b, at) & 0xFFFF;
        case INT32 -> (int) Writer.I32.get(b, at);
        case UINT32 -> (int) Writer.I32.get(b, at) & 0xFFFFFFFFL;
        default -> (long) Writer.I64.get(b, at);
      };
    }

    @Override
    Object decode(Dec d) {
      byte[] b = d.buf;
      int at = check(d);
      switch (kind) {
        case BOOL -> {
          return b[at] != 0;
        }
        case BYTE, INT8, INT16, UINT16, INT32 -> {
          return (int) integerAt(b, at);
        }
        case UINT32, INT64, UINT64 -> {
          return integerAt(b, at);
        }
        case INT128, UINT128 -> {
          d.allocate(40);
          return integer128(b, at, kind == Scalar.UINT128);
        }
        case FLOAT16 -> {
          return fromFloat16((short) Writer.I16.get(b, at));
        }
        case BFLOAT16 -> {
          return Float.intBitsToFloat(((short) Writer.I16.get(b, at) & 0xFFFF) << 16);
        }
        case FLOAT32 -> {
          return Float.intBitsToFloat((int) Writer.I32.get(b, at));
        }
        case FLOAT64 -> {
          return Double.longBitsToDouble((long) Writer.I64.get(b, at));
        }
        case UUID -> {
          return new UUID((long) Writer.B64.get(b, at), (long) Writer.B64.get(b, at + 8));
        }
        case TIMESTAMP -> {
          return new BebopTimestamp(
              (long) Writer.I64.get(b, at),
              (int) Writer.I32.get(b, at + 8),
              (int) Writer.I32.get(b, at + 12));
        }
        case DURATION -> {
          return new BebopDuration((long) Writer.I64.get(b, at), (int) Writer.I32.get(b, at + 8));
        }
        default -> {
          int length = d.pos - at - 1;
          d.allocate(length);
          return new String(b, at, length, StandardCharsets.UTF_8);
        }
      }
    }
  }

  static final class EnumNode extends Node {
    final ScalarNode base;
    final long[] members;

    EnumNode(ScalarNode base, long[] members) {
      this.base = base;
      this.members = members;
    }

    @Override
    long computeMin() {
      return base.min();
    }

    @Override
    int computeFixed() {
      return base.fixed();
    }

    @Override
    void skip(Dec d) {
      base.skip(d);
    }

    @Override
    void encode(Enc e, Object v) {
      base.encode(e, v);
      if (e.strict && Arrays.binarySearch(members, ((Number) v).longValue()) < 0)
        throw value("Not a member of the enum");
    }

    @Override
    void validate(Dec d) {
      int at = d.take(base.kind.width());
      if (d.strict && Arrays.binarySearch(members, base.integerAt(d.buf, at)) < 0)
        throw malformed("Not a member of the enum");
    }

    @Override
    Object decode(Dec d) {
      int at = d.pos;
      validate(d);
      d.pos = at;
      return base.decode(d);
    }
  }

  /** An array of {@code byte}, dynamic ({@code length < 0}) or fixed: a {@code byte[]}. */
  static final class BytesNode extends Node {
    final int length;

    BytesNode(int length) {
      this.length = length;
    }

    @Override
    long computeMin() {
      return length < 0 ? 4 : length;
    }

    @Override
    int computeFixed() {
      return length;
    }

    @Override
    void skip(Dec d) {
      d.take(length < 0 ? d.length(0) : length);
    }

    @Override
    void encode(Enc e, Object v) {
      if (!(v instanceof byte[] bytes)) throw value("Expected a byte array");
      if (length < 0) e.w.u32(bytes.length);
      else if (bytes.length != length) throw value("Fixed array length differs");
      e.w.bytes(bytes, 0, bytes.length);
    }

    @Override
    void validate(Dec d) {
      d.take(length < 0 ? d.length(0) : length);
    }

    @Override
    Object decode(Dec d) {
      int n = length < 0 ? d.length(0) : length;
      int at = d.take(n);
      d.allocate(n);
      return Arrays.copyOfRange(d.buf, at, at + n);
    }
  }

  /** A dynamic ({@code length < 0}) or fixed array, or a set: a {@code List}. */
  static final class ArrayNode extends Node {
    final Node element;
    final boolean set;
    final int length;

    ArrayNode(Node element, boolean set, int length) {
      this.element = element;
      this.set = set;
      this.length = length;
    }

    @Override
    long computeMin() {
      return length < 0 ? 4 : (long) length * element.min();
    }

    @Override
    int computeFixed() {
      if (length < 0 || element.fixed() < 0) return -1;
      long size = (long) length * element.fixed();
      return size > Integer.MAX_VALUE ? -1 : (int) size;
    }

    @Override
    void skip(Dec d) {
      long n = length < 0 ? d.u32() : length;
      int size = element.fixed();
      if (size >= 0) {
        d.take((int) (n * size));
      } else {
        for (long i = 0; i < n; i++) element.skip(d);
      }
    }

    @Override
    void encode(Enc e, Object v) {
      if (!(v instanceof List<?> list)) throw value("Expected a list");
      e.enter();
      if (length >= 0) {
        if (list.size() != length) throw value("Fixed array length differs");
        for (Object item : list) element.encode(e, item(item));
      } else if (set && e.strict) {
        // Canonical sets: distinct elements in ascending order of their encoded bytes.
        Writer out = e.w;
        Writer scratch = e.w = e.scratch();
        int n = list.size();
        int[] ends = new int[n];
        for (int i = 0; i < n; i++) {
          element.encode(e, item(list.get(i)));
          ends[i] = scratch.pos;
        }
        e.w = out;
        Integer[] order = sorted(scratch.buf, ends, ends);
        int distinct = 0;
        for (int i = 0; i < n; i++)
          if (i == 0 || compare(scratch.buf, ends, ends, order[i - 1], order[i]) != 0) distinct++;
        out.u32(distinct);
        for (int i = 0; i < n; i++) {
          if (i > 0 && compare(scratch.buf, ends, ends, order[i - 1], order[i]) == 0) continue;
          int from = order[i] == 0 ? 0 : ends[order[i] - 1];
          out.bytes(scratch.buf, from, ends[order[i]] - from);
        }
      } else {
        e.w.u32(list.size());
        for (Object item : list) element.encode(e, item(item));
      }
      e.leave();
    }

    private static Object item(@Nullable Object item) {
      if (item == null) throw value("Arrays have no null elements");
      return item;
    }

    private int count(Dec d) {
      if (length < 0) return d.count(element.min());
      d.work(length);
      return length;
    }

    @Override
    void validate(Dec d) {
      int n = count(d);
      d.enter();
      boolean ordered = set && d.strict;
      int prevStart = 0;
      int prevEnd = 0;
      for (int i = 0; i < n; i++) {
        int start = d.pos;
        element.validate(d);
        if (ordered) {
          if (i > 0 && Arrays.compareUnsigned(d.buf, prevStart, prevEnd, d.buf, start, d.pos) >= 0)
            throw malformed("Set elements are not distinct and ascending");
          prevStart = start;
          prevEnd = d.pos;
        }
      }
      d.leave();
    }

    @Override
    Object decode(Dec d) {
      int n = count(d);
      d.enter();
      d.allocate(8L * n + 24);
      ArrayList<Object> out = new ArrayList<>(n);
      boolean ordered = set && d.strict;
      int prevStart = 0;
      int prevEnd = 0;
      for (int i = 0; i < n; i++) {
        int start = d.pos;
        out.add(element.decode(d));
        if (ordered) {
          if (i > 0 && Arrays.compareUnsigned(d.buf, prevStart, prevEnd, d.buf, start, d.pos) >= 0)
            throw malformed("Set elements are not distinct and ascending");
          prevStart = start;
          prevEnd = d.pos;
        }
      }
      d.leave();
      return Collections.unmodifiableList(out);
    }
  }

  /** Indexes of spans ordered by their bytes; span {@code i} is {@code [starts(i), keyEnds[i])}. */
  private static Integer[] sorted(byte[] buf, int[] entryEnds, int[] keyEnds) {
    Integer[] order = new Integer[entryEnds.length];
    for (int i = 0; i < order.length; i++) order[i] = i;
    Arrays.sort(order, (a, b) -> compare(buf, entryEnds, keyEnds, a, b));
    return order;
  }

  private static int compare(byte[] buf, int[] entryEnds, int[] keyEnds, int a, int b) {
    return Arrays.compareUnsigned(
        buf, a == 0 ? 0 : entryEnds[a - 1], keyEnds[a], buf, b == 0 ? 0 : entryEnds[b - 1], keyEnds[b]);
  }

  static final class MapNode extends Node {
    final Node key;
    final Node value;

    MapNode(Node key, Node value) {
      this.key = key;
      this.value = value;
    }

    @Override
    long computeMin() {
      return 4;
    }

    @Override
    void skip(Dec d) {
      long n = d.u32();
      for (long i = 0; i < n; i++) {
        key.skip(d);
        value.skip(d);
      }
    }

    @Override
    void encode(Enc e, Object v) {
      if (!(v instanceof Map<?, ?> map)) throw value("Expected a map");
      e.enter();
      Writer out = e.w;
      Writer scratch = e.w = e.scratch();
      int n = map.size();
      int[] keyEnds = new int[n];
      int[] entryEnds = new int[n];
      int i = 0;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() == null || entry.getValue() == null)
          throw value("Maps have no null keys or values");
        key.encode(e, entry.getKey());
        keyEnds[i] = scratch.pos;
        value.encode(e, entry.getValue());
        entryEnds[i++] = scratch.pos;
      }
      e.w = out;
      // Distinct host keys can share one encoding (Integer 1 and Long 1): that is an error, not
      // something to merge.
      Integer[] order = sorted(scratch.buf, entryEnds, keyEnds);
      for (int k = 1; k < n; k++)
        if (compare(scratch.buf, entryEnds, keyEnds, order[k - 1], order[k]) == 0)
          throw value("Distinct map keys have the same encoding");
      out.u32(n);
      if (e.strict) {
        for (int k = 0; k < n; k++) {
          int from = order[k] == 0 ? 0 : entryEnds[order[k] - 1];
          out.bytes(scratch.buf, from, entryEnds[order[k]] - from);
        }
      } else {
        out.bytes(scratch.buf, 0, scratch.pos);
      }
      e.leave();
    }

    /** Whether two encodings of a key can be one value: only a non-strict boolean. */
    private boolean booleanKey() {
      return key instanceof ScalarNode s && s.kind == Scalar.BOOL;
    }

    @Override
    void validate(Dec d) {
      int n = d.count(key.min() + value.min());
      d.enter();
      if (d.strict) {
        int prevStart = 0;
        int prevEnd = 0;
        for (int i = 0; i < n; i++) {
          int start = d.pos;
          key.validate(d);
          if (i > 0 && Arrays.compareUnsigned(d.buf, prevStart, prevEnd, d.buf, start, d.pos) >= 0)
            throw malformed("Map keys are not distinct and ascending");
          prevStart = start;
          prevEnd = d.pos;
          value.validate(d);
        }
      } else if (booleanKey()) {
        int seen = 0;
        for (int i = 0; i < n; i++) {
          int bit = d.buf[d.take(1)] != 0 ? 2 : 1;
          value.validate(d);
          // As in decoding, an entry is a duplicate only once it is whole.
          if ((seen & bit) != 0) throw malformed("Duplicate map key");
          seen |= bit;
        }
      } else {
        // Every other key type has one encoding per value: keys are equal iff their bytes are.
        // An open-addressed table of key spans finds duplicates without an object per entry.
        d.allocate(12L * n);
        int[] starts = new int[n];
        int[] ends = new int[n];
        int mask = Math.max(2, Integer.highestOneBit(Math.max(n, 1)) << 2) - 1;
        int[] table = new int[mask + 1];
        for (int i = 0; i < n; i++) {
          int start = starts[i] = d.pos;
          key.validate(d);
          int end = ends[i] = d.pos;
          int hash = 1;
          for (int p = start; p < end; p++) hash = hash * 31 + d.buf[p];
          value.validate(d);
          for (int slot = (hash ^ hash >>> 15) & mask; ; slot = (slot + 1) & mask) {
            int other = table[slot] - 1;
            if (other < 0) {
              table[slot] = i + 1;
              break;
            }
            if (Arrays.equals(d.buf, starts[other], ends[other], d.buf, start, end))
              throw malformed("Duplicate map key");
          }
        }
      }
      d.leave();
    }

    @Override
    Object decode(Dec d) {
      int n = d.count(key.min() + value.min());
      d.enter();
      d.allocate(48L * n + 48);
      LinkedHashMap<Object, Object> out = LinkedHashMap.newLinkedHashMap(n);
      int prevStart = 0;
      int prevEnd = 0;
      for (int i = 0; i < n; i++) {
        int start = d.pos;
        Object k = key.decode(d);
        int keyEnd = d.pos;
        if (d.strict) {
          if (i > 0 && Arrays.compareUnsigned(d.buf, prevStart, prevEnd, d.buf, start, keyEnd) >= 0)
            throw malformed("Map keys are not distinct and ascending");
          prevStart = start;
          prevEnd = keyEnd;
        }
        if (out.put(k, value.decode(d)) != null) throw malformed("Duplicate map key");
      }
      d.leave();
      return Collections.unmodifiableMap(out);
    }
  }

  static final class StructNode extends Node {
    final String name;
    final String[] names;
    final Node[] fields;

    StructNode(String name, int count) {
      this.name = name;
      this.names = new String[count];
      this.fields = new Node[count];
    }

    @Override
    long computeMin() {
      long sum = 0;
      for (Node field : fields) sum += field.min();
      return sum;
    }

    @Override
    int computeFixed() {
      long sum = 0;
      for (Node field : fields) {
        if (field.fixed() < 0) return -1;
        sum += field.fixed();
      }
      return sum > Integer.MAX_VALUE ? -1 : (int) sum;
    }

    @Override
    void skip(Dec d) {
      int size = fixed();
      if (size >= 0) d.take(size);
      else for (Node field : fields) field.skip(d);
    }

    @Override
    void encode(Enc e, Object v) {
      BebopRecord r = record(v, name, fields.length);
      e.enter();
      for (int i = 0; i < fields.length; i++) {
        Object field = r.get(i);
        if (field == null) throw value("Struct fields are never absent");
        fields[i].encode(e, field);
      }
      e.leave();
    }

    @Override
    void validate(Dec d) {
      d.enter();
      d.work(fields.length);
      for (Node field : fields) field.validate(d);
      d.leave();
    }

    @Override
    Object decode(Dec d) {
      d.enter();
      d.work(fields.length);
      d.allocate(8L * fields.length + 32);
      @Nullable Object[] values = new Object[fields.length];
      for (int i = 0; i < fields.length; i++) values[i] = fields[i].decode(d);
      d.leave();
      return BebopRecord.owning(name, values);
    }
  }

  private static BebopRecord record(Object v, String name, int size) {
    if (!(v instanceof BebopRecord r) || !r.type().equals(name) || r.size() != size)
      throw value("Expected a record of " + name);
    return r;
  }

  static final class MessageNode extends Node {
    final String name;
    final String[] names;
    final Node[] fields;
    final int[] tags;
    /** Ordinals in ascending tag order. */
    final int[] order;

    final int[] ordinalByTag = new int[256];

    MessageNode(String name, int count) {
      this.name = name;
      this.names = new String[count];
      this.fields = new Node[count];
      this.tags = new int[count];
      this.order = new int[count];
      Arrays.fill(ordinalByTag, -1);
    }

    @Override
    long computeMin() {
      return 5;
    }

    @Override
    void skip(Dec d) {
      d.take(d.length(1));
    }

    @Override
    void encode(Enc e, Object v) {
      BebopRecord r = record(v, name, fields.length);
      e.enter();
      Writer w = e.w;
      int lengthAt = w.reserve(4);
      int payload = w.pos;
      int[] present = new int[fields.length];
      int[] starts = new int[fields.length];
      int n = 0;
      for (int ordinal : order) {
        Object field = r.get(ordinal);
        if (field == null) continue;
        present[n] = tags[ordinal];
        starts[n++] = w.pos - payload;
        fields[ordinal].encode(e, field);
      }
      MessageIndex.write(w, payload, present, starts, n);
      Writer.I32.set(w.buf, lengthAt, w.pos - payload);
      e.leave();
    }

    @Override
    void validate(Dec d) {
      walk(d, null);
    }

    @Override
    Object decode(Dec d) {
      d.allocate(8L * fields.length + 32);
      @Nullable Object[] values = new Object[fields.length];
      walk(d, values);
      return BebopRecord.owning(name, values);
    }

    /** Validates one message, decoding its fields into {@code values} when given. */
    private void walk(Dec d, @Nullable Object @Nullable [] values) {
      int length = d.length(1);
      int start = d.pos;
      int regionEnd = start + length;
      MessageIndex index = MessageIndex.parse(d.buf, start, regionEnd);
      d.enter();
      d.work(index.count);
      int outerEnd = d.end;
      for (int rank = 0; rank < index.count; rank++) {
        int ordinal = ordinalByTag[index.tags[rank]];
        if (ordinal < 0) {
          if (d.strict) throw malformed("Unknown field tag");
          continue;
        }
        d.pos = index.start(rank);
        d.end = index.end(rank);
        if (values == null) fields[ordinal].validate(d);
        else values[ordinal] = fields[ordinal].decode(d);
        if (d.pos != d.end) throw malformed("Field does not fill its indexed range");
      }
      d.end = outerEnd;
      d.pos = regionEnd;
      d.leave();
    }
  }

  static final class UnionNode extends Node {
    final @Nullable Node[] branches = new Node[256];
    final @Nullable String[] names = new String[256];
    final String name;

    UnionNode(String name) {
      this.name = name;
    }

    @Override
    long computeMin() {
      return 5;
    }

    @Override
    void skip(Dec d) {
      d.take(d.length(1));
    }

    @Override
    void encode(Enc e, Object v) {
      if (!(v instanceof BebopUnion u)) throw value("Expected a union");
      e.enter();
      Writer w = e.w;
      int lengthAt = w.reserve(4);
      int start = w.pos;
      w.u8(u.discriminator());
      Node branch = branches[u.discriminator()];
      if (branch != null) {
        branch.encode(e, u.value());
      } else if (!e.strict && u.value() instanceof byte[] raw) {
        w.bytes(raw, 0, raw.length);
      } else {
        throw value("Unknown union discriminator");
      }
      Writer.I32.set(w.buf, lengthAt, w.pos - start);
      e.leave();
    }

    @Override
    void validate(Dec d) {
      walk(d, false);
    }

    @Override
    Object decode(Dec d) {
      int start = d.pos;
      Object content = Objects.requireNonNull(walk(d, true));
      return new BebopUnion(d.buf[start + 4] & 0xFF, content);
    }

    private @Nullable Object walk(Dec d, boolean decode) {
      int length = d.length(1);
      int regionEnd = d.pos + length;
      Node branch = branches[d.buf[d.take(1)] & 0xFF];
      d.enter();
      Object content = null;
      if (branch == null) {
        if (d.strict) throw malformed("Unknown union discriminator");
        if (decode) {
          d.allocate(length);
          content = Arrays.copyOfRange(d.buf, d.pos, regionEnd);
        }
        d.pos = regionEnd;
      } else {
        int outerEnd = d.end;
        d.end = regionEnd;
        if (decode) content = branch.decode(d);
        else branch.validate(d);
        if (d.pos != regionEnd) throw malformed("Branch does not fill the union");
        d.end = outerEnd;
      }
      d.leave();
      return content;
    }
  }
}
