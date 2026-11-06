package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;
import dev.elide.myna.values.ValueTape;
import dev.elide.truffledata.DataArray;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/**
 * Reusable scoped index view. Every operation checks its message before touching native memory.
 * Guests see objects as ordered members, arrays as elements, and scalars with exact number
 * contracts: integers only fit where representable, DECIMAL tokens fit a BigInteger when
 * integral, and tokens outside binary64 fit nothing.
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
    return switch (owner.tape.tag(node)) {
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
    return owner.tape.tag(node);
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

  @TruffleBoundary
  private long memberNode(String name) {
    if (tag() != ValueTape.OBJECT) return -1;
    return owner.tape.find(node, name.getBytes(StandardCharsets.UTF_8));
  }

  @ExportMessage
  boolean isMemberReadable(String name) {
    return memberNode(name) >= 0;
  }

  @ExportMessage
  Object readMember(String name) throws UnknownIdentifierException {
    long found = memberNode(name);
    if (found < 0) throw UnknownIdentifierException.create(name);
    return new JsonValue(owner, found);
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

  @ExportMessage
  boolean isNumber() {
    int tag = tag();
    return tag == ValueTape.I64 || tag == ValueTape.U64 || tag == ValueTape.F64 || tag == ValueTape.DECIMAL;
  }

  /** Exact decimal of a DECIMAL token; null for tokens no bounded decimal can hold. */
  @TruffleBoundary
  private @org.jspecify.annotations.Nullable BigDecimal decimal() {
    try {
      return new BigDecimal(owner.lexicalText(node));
    } catch (NumberFormatException | ArithmeticException overflow) {
      return null;
    }
  }

  /** Exact integer when the number is integral and in range, else null. */
  @TruffleBoundary
  private @org.jspecify.annotations.Nullable BigInteger exactInteger() {
    return switch (tag()) {
      case ValueTape.I64 -> BigInteger.valueOf(owner.tape.signedValue(node));
      case ValueTape.U64 -> {
        long bits = owner.tape.unsignedBits(node);
        yield bits >= 0 ? BigInteger.valueOf(bits) : BigInteger.valueOf(bits & Long.MAX_VALUE).setBit(63);
      }
      case ValueTape.F64 -> {
        double d = owner.tape.doubleValue(node);
        yield d == Math.rint(d) ? new BigDecimal(d).toBigInteger() : null;
      }
      case ValueTape.DECIMAL -> {
        BigDecimal d = decimal();
        if (d == null) yield null;
        try {
          yield d.toBigIntegerExact();
        } catch (ArithmeticException fraction) {
          yield null;
        }
      }
      default -> null;
    };
  }

  @TruffleBoundary
  private boolean fitsSigned(long min, long max) {
    if (tag() == ValueTape.I64) {
      long v = owner.tape.signedValue(node);
      return v >= min && v <= max;
    }
    BigInteger exact = exactInteger();
    return exact != null && exact.bitLength() < 64 && exact.longValue() >= min && exact.longValue() <= max;
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInByte() {
    return isNumber() && fitsSigned(Byte.MIN_VALUE, Byte.MAX_VALUE);
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInShort() {
    return isNumber() && fitsSigned(Short.MIN_VALUE, Short.MAX_VALUE);
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInInt() {
    return isNumber() && fitsSigned(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInLong() {
    return isNumber() && fitsSigned(Long.MIN_VALUE, Long.MAX_VALUE);
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInBigInteger() {
    return isNumber() && exactInteger() != null;
  }

  /** Binary64 value when exactly representable, else NaN (never for valid tokens). */
  @TruffleBoundary
  private double exactDouble() {
    return switch (tag()) {
      case ValueTape.F64 -> owner.tape.doubleValue(node);
      case ValueTape.I64 -> {
        long v = owner.tape.signedValue(node);
        double d = (double) v;
        yield (long) d == v && Math.abs(v) <= (1L << 53) ? d : Double.NaN;
      }
      case ValueTape.U64 -> {
        BigInteger exact = exactInteger();
        yield exact != null && exact.bitLength() <= 53 ? exact.doubleValue() : Double.NaN;
      }
      case ValueTape.DECIMAL -> {
        BigDecimal d = decimal();
        if (d == null) yield Double.NaN;
        double v = d.doubleValue();
        yield Double.isFinite(v) && new BigDecimal(v).compareTo(d) == 0 ? v : Double.NaN;
      }
      default -> Double.NaN;
    };
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInDouble() {
    return isNumber() && !Double.isNaN(exactDouble());
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInFloat() {
    if (!isNumber()) return false;
    double d = exactDouble();
    return !Double.isNaN(d) && (double) (float) d == d;
  }

  @ExportMessage
  @TruffleBoundary
  byte asByte() throws UnsupportedMessageException {
    if (!fitsInByte()) throw UnsupportedMessageException.create();
    return (byte) asLong();
  }

  @ExportMessage
  @TruffleBoundary
  short asShort() throws UnsupportedMessageException {
    if (!fitsInShort()) throw UnsupportedMessageException.create();
    return (short) asLong();
  }

  @ExportMessage
  @TruffleBoundary
  int asInt() throws UnsupportedMessageException {
    if (!fitsInInt()) throw UnsupportedMessageException.create();
    return (int) asLong();
  }

  @ExportMessage
  @TruffleBoundary
  long asLong() throws UnsupportedMessageException {
    if (tag() == ValueTape.I64) return owner.tape.signedValue(node);
    BigInteger exact = isNumber() ? exactInteger() : null;
    if (exact == null || exact.bitLength() >= 64) throw UnsupportedMessageException.create();
    return exact.longValue();
  }

  @ExportMessage
  @TruffleBoundary
  BigInteger asBigInteger() throws UnsupportedMessageException {
    BigInteger exact = isNumber() ? exactInteger() : null;
    if (exact == null) throw UnsupportedMessageException.create();
    return exact;
  }

  @ExportMessage
  @TruffleBoundary
  float asFloat() throws UnsupportedMessageException {
    if (!fitsInFloat()) throw UnsupportedMessageException.create();
    return (float) exactDouble();
  }

  @ExportMessage
  @TruffleBoundary
  double asDouble() throws UnsupportedMessageException {
    if (!isNumber()) throw UnsupportedMessageException.create();
    double d = exactDouble();
    if (Double.isNaN(d)) throw UnsupportedMessageException.create();
    return d;
  }
}
