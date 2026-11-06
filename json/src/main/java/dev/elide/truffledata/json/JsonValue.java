package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;
import dev.elide.myna.values.ValueTape;
import dev.elide.truffledata.DataArray;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.NoSuchElementException;

/**
 * Reusable scoped index view. Every operation checks its message before touching native memory.
 * Guests see objects as ordered members, arrays as elements, and scalars with exact number
 * contracts: integers fit only where representable, -0 fits no integer type, DECIMAL tokens
 * (integers beyond 64 bits, magnitudes beyond binary64) fit a BigInteger when integral and at most
 * {@value #MAX_INTEGER_DIGITS} digits long, and fit no primitive type otherwise.
 */
@ExportLibrary(InteropLibrary.class)
public final class JsonValue implements TruffleObject {
  private final JsonOwner owner;
  private final long node;

  JsonValue(JsonOwner owner, long node) {
    this.owner = owner;
    this.node = node;
  }

  public enum Kind {
    NULL,
    BOOLEAN,
    NUMBER,
    STRING,
    ARRAY,
    OBJECT
  }

  public Kind kind() {
    owner.check();
    return switch (owner.tag(node)) {
      case ValueTape.NULL -> Kind.NULL;
      case ValueTape.BOOL -> Kind.BOOLEAN;
      case ValueTape.STRING -> Kind.STRING;
      case ValueTape.ARRAY -> Kind.ARRAY;
      case ValueTape.OBJECT -> Kind.OBJECT;
      case ValueTape.I64, ValueTape.U64, ValueTape.F64, ValueTape.DECIMAL -> Kind.NUMBER;
      default -> throw new IllegalStateException("Unknown JSON node kind");
    };
  }

  public boolean booleanValue() {
    owner.check();
    return owner.tape.booleanValue(node);
  }

  /** Signed 64-bit integer node; other number kinds throw rather than convert. */
  public long longValue() {
    owner.check();
    return owner.tape.signedValue(node);
  }

  /** Binary64 node; integer nodes throw rather than widen. */
  public double doubleValue() {
    owner.check();
    return owner.tape.doubleValue(node);
  }

  @TruffleBoundary
  public String stringValue() {
    owner.check();
    return owner.tape.stringValue(node);
  }

  /** Exact lexical token; no bounded decimal or floating-point conversion. */
  @TruffleBoundary
  public String numberText() {
    if (kind() != Kind.NUMBER) throw new IllegalStateException("Not a JSON number");
    return owner.lexicalText(node);
  }

  /** Source spelling of a string, key or number, including quotes and escapes. */
  @TruffleBoundary
  public String rawText() {
    Kind kind = kind();
    if (kind != Kind.NUMBER && kind != Kind.STRING)
      throw new IllegalStateException("JSON containers, booleans and null have no raw text");
    return owner.lexicalText(node);
  }

  @TruffleBoundary
  public long size() {
    Kind kind = kind();
    if (kind != Kind.ARRAY && kind != Kind.OBJECT)
      throw new IllegalStateException("Not a JSON container");
    return owner.tape.length(node);
  }

  /** Prepare and retain a key with ValueTape.Utf8Key.of for repeated lookup. */
  @TruffleBoundary
  public JsonValue member(ValueTape.Utf8Key key) {
    owner.check();
    long found = owner.tape.find(node, key);
    if (found < 0) throw new NoSuchElementException("Missing JSON member");
    return new JsonValue(owner, found);
  }

  @TruffleBoundary
  public JsonValue member(String key) {
    return member(ValueTape.Utf8Key.of(key));
  }

  /** Single-pass export of this subtree; see {@link JsonSink} and {@link JsonExportNode}. */
  @TruffleBoundary
  public void export(JsonSink sink) {
    JsonExportNode.walk(owner, node, sink);
  }

  JsonOwner owner() {
    return owner;
  }

  long node() {
    return node;
  }

    /** Independently owned immutable copy of this subtree, re-based to node 0. */
  @TruffleBoundary
  public JsonSnapshot snapshot() {
    return owner.snapshot(node);
  }

  /** Key of the i-th member in source order. */
  @TruffleBoundary
  public JsonValue keyAt(long index) {
    if (kind() != Kind.OBJECT) throw new IllegalStateException("Not a JSON object");
    long count = owner.tape.length(node);
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException("JSON member index");
    long key = owner.tape.first(node);
    for (long i = 0; i < index; ++i) key = owner.tape.next(key + 1);
    return new JsonValue(owner, key);
  }

  /** Value of the i-th member in source order. */
  @TruffleBoundary
  public JsonValue memberAt(long index) {
    JsonValue key = keyAt(index);
    return new JsonValue(owner, key.node + 1);
  }

  /** Random access: small arrays are walked; larger ones use an index cached by the owner. */
  @TruffleBoundary
  public JsonValue element(long index) {
    if (kind() != Kind.ARRAY) throw new IllegalStateException("Not a JSON array");
    long count = owner.tape.length(node);
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException("JSON array index");
    if (count > JsonOwner.INDEX_THRESHOLD) return new JsonValue(owner, owner.arrayIndex(node)[(int) index]);
    long child = owner.tape.first(node);
    for (long i = 0; i < index; ++i) child = owner.tape.next(child);
    return new JsonValue(owner, child);
  }

  /** Sequential traversal in source order without building an index. */
  @TruffleBoundary
  public Iterable<JsonValue> elements() {
    if (kind() != Kind.ARRAY) throw new IllegalStateException("Not a JSON array");
    return () ->
        new java.util.Iterator<>() {
          private long remaining = owner.tape.length(node);
          private long child = remaining == 0 ? -1 : owner.tape.first(node);

          @Override
          public boolean hasNext() {
            return remaining > 0;
          }

          @Override
          public JsonValue next() {
            owner.check();
            if (remaining == 0) throw new java.util.NoSuchElementException();
            JsonValue value = new JsonValue(owner, child);
            if (--remaining > 0) child = owner.tape.next(child);
            return value;
          }
        };
  }

  // ---- InteropLibrary: containers ----

  private int tag() {
    owner.check();
    return owner.tag(node);
  }

  @ExportMessage
  boolean hasMembers() {
    return tag() == ValueTape.OBJECT;
  }

  @ExportMessage
  @TruffleBoundary
  Object getMembers(@SuppressWarnings("unused") boolean includeInternal)
      throws UnsupportedMessageException {
    if (tag() != ValueTape.OBJECT) throw UnsupportedMessageException.create();
    long count = owner.tape.length(node);
    Object[] keys = new Object[Math.toIntExact(count)];
    long key = count == 0 ? -1 : owner.tape.first(node);
    for (int i = 0; i < keys.length; i++) {
      keys[i] = owner.tape.stringValue(key);
      if (i + 1 < keys.length) key = owner.tape.next(key + 1);
    }
    return new DataArray(keys);
  }

  /** Encoded member name, or null when the name has an unpaired surrogate and matches no key. */
  @TruffleBoundary
  static ValueTape.@org.jspecify.annotations.Nullable Utf8Key keyOf(String name) {
    try {
      return ValueTape.Utf8Key.of(name);
    } catch (IllegalArgumentException unpaired) {
      return null;
    }
  }

  /** Member value node, or -1; checks the receiver inline so the view itself never escapes. */
  long memberNode(ValueTape.@org.jspecify.annotations.Nullable Utf8Key key) {
    if (tag() != ValueTape.OBJECT || key == null) return -1;
    return find(owner, node, key);
  }

  @TruffleBoundary
  private static long find(JsonOwner owner, long object, ValueTape.Utf8Key key) {
    return owner.tape.find(object, key);
  }

  // Guests usually read members by constant names: cache the encoded key per name identity so a
  // compiled read neither re-encodes nor allocates.

  @ExportMessage
  static final class IsMemberReadable {
    @Specialization(guards = "name == cachedName", limit = "3")
    static boolean cached(
        JsonValue receiver,
        String name,
        @Cached(value = "name", neverDefault = true) String cachedName,
        @Cached("keyOf(name)") ValueTape.@org.jspecify.annotations.Nullable Utf8Key key) {
      return receiver.memberNode(key) >= 0;
    }

    @Specialization(replaces = "cached")
    static boolean generic(JsonValue receiver, String name) {
      return receiver.memberNode(keyOf(name)) >= 0;
    }
  }

  @ExportMessage
  static final class ReadMember {
    @Specialization(guards = "name == cachedName", limit = "3")
    static Object cached(
        JsonValue receiver,
        String name,
        @Cached(value = "name", neverDefault = true) String cachedName,
        @Cached("keyOf(name)") ValueTape.@org.jspecify.annotations.Nullable Utf8Key key)
        throws UnknownIdentifierException {
      return receiver.readFound(receiver.memberNode(key), name);
    }

    @Specialization(replaces = "cached")
    static Object generic(JsonValue receiver, String name) throws UnknownIdentifierException {
      return receiver.readFound(receiver.memberNode(keyOf(name)), name);
    }
  }

  private Object readFound(long found, String name) throws UnknownIdentifierException {
    if (found < 0) throw unknown(name);
    return new JsonValue(owner, found);
  }

  @TruffleBoundary
  private static UnknownIdentifierException unknown(String name) {
    return UnknownIdentifierException.create(name);
  }

  @ExportMessage
  boolean hasArrayElements() {
    return tag() == ValueTape.ARRAY;
  }

  @ExportMessage
  long getArraySize() throws UnsupportedMessageException {
    if (tag() != ValueTape.ARRAY) throw UnsupportedMessageException.create();
    return owner.tape.length(node);
  }

  @ExportMessage
  boolean isArrayElementReadable(long index) {
    return tag() == ValueTape.ARRAY && index >= 0 && index < owner.tape.length(node);
  }

  @ExportMessage
  Object readArrayElement(long index) throws InvalidArrayIndexException, UnsupportedMessageException {
    if (tag() != ValueTape.ARRAY) throw UnsupportedMessageException.create();
    if (index < 0 || index >= owner.tape.length(node)) throw InvalidArrayIndexException.create(index);
    return element(index);
  }

  // ---- InteropLibrary: scalars ----

  @ExportMessage
  boolean isNull() {
    return tag() == ValueTape.NULL;
  }

  @ExportMessage
  boolean isBoolean() {
    return tag() == ValueTape.BOOL;
  }

  @ExportMessage
  boolean asBoolean() throws UnsupportedMessageException {
    if (tag() != ValueTape.BOOL) throw UnsupportedMessageException.create();
    return owner.tape.booleanValue(node);
  }

  @ExportMessage
  boolean isString() {
    return tag() == ValueTape.STRING;
  }

  @ExportMessage
  @TruffleBoundary
  String asString() throws UnsupportedMessageException {
    if (tag() != ValueTape.STRING) throw UnsupportedMessageException.create();
    return owner.tape.stringValue(node);
  }

  /** UTF-8 bytes copied once into a TruffleString; no intermediate Java String. */
  @ExportMessage
  @TruffleBoundary
  TruffleString asTruffleString() throws UnsupportedMessageException {
    if (tag() != ValueTape.STRING) throw UnsupportedMessageException.create();
    byte[] bytes = new byte[Math.toIntExact(owner.tape.length(node))];
    owner.tape.copyStringUtf8(node, bytes, 0);
    return TruffleString.fromByteArrayUncached(bytes, 0, bytes.length, TruffleString.Encoding.UTF_8, false);
  }

  private static boolean isNumberTag(int tag) {
    return tag == ValueTape.I64 || tag == ValueTape.U64 || tag == ValueTape.F64 || tag == ValueTape.DECIMAL;
  }

  @ExportMessage
  boolean isNumber() {
    return isNumberTag(tag());
  }

  // Number conversions keep the common kind (I64 for integers, F64 for doubles) inline and send
  // every other kind to static boundaries over (owner, node), so a view created by readMember or
  // readArrayElement never escapes into a boundary call and compiled guest reads do not allocate.

  private static final long NEGATIVE_ZERO = Double.doubleToRawLongBits(-0.0);

  /** Integral DECIMAL tokens longer than this fit no BigInteger; short tokens like 1E999999999 stay cheap. */
  static final int MAX_INTEGER_DIGITS = 4096;

  /** Exact integer of an integral decimal within the digit bound; never expands a large exponent. */
  private static @org.jspecify.annotations.Nullable BigInteger boundedInteger(BigDecimal value) {
    if (value.signum() == 0) return BigInteger.ZERO;
    // Cost is proportional to the token's significant digits, not to its exponent.
    BigDecimal stripped = value.stripTrailingZeros();
    if (stripped.scale() > 0) return null;
    if ((long) stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS) return null;
    return stripped.toBigIntegerExact();
  }

  /** Exact decimal of a DECIMAL token; null for tokens no bounded decimal can hold. */
  @TruffleBoundary
  private static @org.jspecify.annotations.Nullable BigDecimal decimal(JsonOwner owner, long node) {
    try {
      return new BigDecimal(owner.lexicalText(node));
    } catch (NumberFormatException | ArithmeticException overflow) {
      return null;
    }
  }

  /** Exact integer when the number is integral and in range, else null (also for non-numbers). */
  @TruffleBoundary
  private static @org.jspecify.annotations.Nullable BigInteger exactInteger(JsonOwner owner, long node) {
    return switch (owner.tag(node)) {
      case ValueTape.I64 -> BigInteger.valueOf(owner.tape.signedValue(node));
      case ValueTape.U64 -> {
        long bits = owner.tape.unsignedBits(node);
        yield bits >= 0 ? BigInteger.valueOf(bits) : BigInteger.valueOf(bits & Long.MAX_VALUE).setBit(63);
      }
      case ValueTape.F64 -> {
        double d = owner.tape.doubleValue(node);
        // -0.0 fits no integer type: converting it would lose the sign (Truffle's double contract).
        boolean integral = Double.isFinite(d) && d == Math.rint(d) && Double.doubleToRawLongBits(d) != NEGATIVE_ZERO;
        yield integral ? new BigDecimal(d).toBigInteger() : null;
      }
      case ValueTape.DECIMAL -> {
        BigDecimal d = decimal(owner, node);
        yield d == null ? null : boundedInteger(d);
      }
      default -> null;
    };
  }

  /** Signed value within [min, max] when the number is an exact integer there. */
  @TruffleBoundary
  private static boolean fitsSigned(JsonOwner owner, long node, long min, long max) {
    BigInteger exact = exactInteger(owner, node);
    return exact != null && exact.bitLength() < 64 && exact.longValue() >= min && exact.longValue() <= max;
  }

  private boolean fits(long min, long max) {
    int tag = tag();
    if (tag == ValueTape.I64) {
      long v = owner.tape.signedValue(node);
      return v >= min && v <= max;
    }
    return isNumberTag(tag) && fitsSigned(owner, node, min, max);
  }

  /** Binary64 value when exactly representable, else NaN (also for non-numbers). */
  @TruffleBoundary
  private static double exactDouble(JsonOwner owner, long node) {
    return switch (owner.tag(node)) {
      case ValueTape.F64 -> owner.tape.doubleValue(node);
      case ValueTape.I64 -> {
        long v = owner.tape.signedValue(node);
        yield v >= -(1L << 53) && v <= (1L << 53) ? (double) v : Double.NaN;
      }
      case ValueTape.U64 -> {
        BigInteger exact = exactInteger(owner, node);
        yield exact != null && exact.bitLength() <= 53 ? exact.doubleValue() : Double.NaN;
      }
      case ValueTape.DECIMAL -> {
        BigDecimal d = decimal(owner, node);
        if (d == null) yield Double.NaN;
        double v = d.doubleValue();
        yield Double.isFinite(v) && new BigDecimal(v).compareTo(d) == 0 ? v : Double.NaN;
      }
      default -> Double.NaN;
    };
  }

  private double doubleOrNaN() {
    if (tag() == ValueTape.F64) return owner.tape.doubleValue(node);
    return exactDouble(owner, node);
  }

  @ExportMessage
  boolean fitsInByte() {
    return fits(Byte.MIN_VALUE, Byte.MAX_VALUE);
  }

  @ExportMessage
  boolean fitsInShort() {
    return fits(Short.MIN_VALUE, Short.MAX_VALUE);
  }

  @ExportMessage
  boolean fitsInInt() {
    return fits(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  @ExportMessage
  boolean fitsInLong() {
    return fits(Long.MIN_VALUE, Long.MAX_VALUE);
  }

  @ExportMessage
  boolean fitsInBigInteger() {
    int tag = tag();
    return tag == ValueTape.I64 || (isNumberTag(tag) && exactInteger(owner, node) != null);
  }

  @ExportMessage
  boolean fitsInDouble() {
    return !Double.isNaN(doubleOrNaN());
  }

  @ExportMessage
  boolean fitsInFloat() {
    double d = doubleOrNaN();
    return !Double.isNaN(d) && (double) (float) d == d;
  }

  @ExportMessage
  byte asByte() throws UnsupportedMessageException {
    if (!fitsInByte()) throw UnsupportedMessageException.create();
    return (byte) asLong();
  }

  @ExportMessage
  short asShort() throws UnsupportedMessageException {
    if (!fitsInShort()) throw UnsupportedMessageException.create();
    return (short) asLong();
  }

  @ExportMessage
  int asInt() throws UnsupportedMessageException {
    if (!fitsInInt()) throw UnsupportedMessageException.create();
    return (int) asLong();
  }

  @ExportMessage
  long asLong() throws UnsupportedMessageException {
    if (tag() == ValueTape.I64) return owner.tape.signedValue(node);
    return exactLong(owner, node);
  }

  @TruffleBoundary
  private static long exactLong(JsonOwner owner, long node) throws UnsupportedMessageException {
    BigInteger exact = exactInteger(owner, node);
    if (exact == null || exact.bitLength() >= 64) throw UnsupportedMessageException.create();
    return exact.longValue();
  }

  @ExportMessage
  BigInteger asBigInteger() throws UnsupportedMessageException {
    tag(); // liveness and thread check before the static boundary reads the tape
    BigInteger exact = exactInteger(owner, node);
    if (exact == null) throw UnsupportedMessageException.create();
    return exact;
  }

  @ExportMessage
  float asFloat() throws UnsupportedMessageException {
    if (!fitsInFloat()) throw UnsupportedMessageException.create();
    return (float) doubleOrNaN();
  }

  @ExportMessage
  double asDouble() throws UnsupportedMessageException {
    double d = doubleOrNaN();
    if (Double.isNaN(d)) throw UnsupportedMessageException.create();
    return d;
  }
}
