package dev.elide.truffledata.tape;

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
public class TapeValue implements TruffleObject {
  private final TapeOwner owner;
  private final long node;

  TapeValue(TapeOwner owner, long node) {
    this.owner = owner;
    this.node = node;
  }

  // YAML child views carry provenance; ordinary JSON views keep their existing layout.
  private static final class AliasValue extends TapeValue {
    private final long source;
    AliasValue(TapeOwner owner, long node, long source) { super(owner, node); this.source = source; }
    @Override long sourceNode() { return source; }
  }

  private static TapeValue view(TapeOwner owner, long source) {
    return owner.format().hasAliases() ? aliasView(owner, source) : new TapeValue(owner, source);
  }

  private static TapeValue aliasView(TapeOwner owner, long source) {
    long resolved = owner.resolve(source);
    return owner.format().hasAliases() ? new AliasValue(owner, resolved, source) : new TapeValue(owner, resolved);
  }

  long sourceNode() { return node; }

  /** Format-specific metadata of the source node, preserving alias provenance. */
  public int metadataWord() { owner.check(); return owner.format().metadataWord(sourceNode()); }

  /** Explicit or resolved type tag, when the producer provides one. */
  public @org.jspecify.annotations.Nullable String tagUri() {
    owner.check(); return owner.format().tagUri(node);
  }

  /** Source anchor name, when the producer provides one. */
  public @org.jspecify.annotations.Nullable String anchorName() {
    owner.check(); return owner.format().anchorName(sourceNode());
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
      default -> throw new IllegalStateException("Unknown tape node kind");
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
    if (kind() != Kind.NUMBER) throw new IllegalStateException("Not a " + owner.format().name + " number");
    return owner.numberText(node);
  }

  /** Source spelling of a scalar with a retained lexical token, including quotes and escapes. */
  @TruffleBoundary
  public String rawText() {
    Kind kind = kind();
    if (kind != Kind.NUMBER && kind != Kind.STRING
        && ((kind != Kind.BOOLEAN && kind != Kind.NULL) || owner.lexical(node) == 0))
      throw new IllegalStateException(owner.format().name + " value has no raw text");
    return owner.lexicalText(node);
  }

  @TruffleBoundary
  public long size() {
    Kind kind = kind();
    if (kind != Kind.ARRAY && kind != Kind.OBJECT)
      throw new IllegalStateException("Not a " + owner.format().name + " container");
    return owner.tape.length(node);
  }

  /** Prepare and retain a key with ValueTape.Utf8Key.of for repeated lookup. */
  @TruffleBoundary
  public TapeValue member(ValueTape.Utf8Key key) {
    return member(TapeKey.of(key));
  }

  @TruffleBoundary
  public TapeValue member(String key) {
    return member(TapeKey.of(key));
  }

  /** Member by prepared key: through the object's Myna v4 directory, else a hash-filtered scan. */
  @TruffleBoundary
  public TapeValue member(TapeKey key) {
    if (kind() != Kind.OBJECT) throw new IllegalStateException("Not a " + owner.format().name + " object");
    long found = owner.find(node, key, null);
    if (found < 0) throw new NoSuchElementException("Missing " + owner.format().name + " member");
    return aliasView(owner, found);
  }

  /** Single-pass export of this subtree; see {@link TapeSink} and {@link TapeExportNode}. */
  @TruffleBoundary
  public void export(TapeSink sink) {
    TapeExportNode.walk(owner, node, sink);
  }

  TapeOwner owner() {
    return owner;
  }

  long node() {
    return node;
  }

  /** Independently owned immutable copy of this subtree, re-based to node 0. */
  @TruffleBoundary
  public TapeSnapshot snapshot() {
    return owner.snapshot(node);
  }

  /** Key of the i-th member in source order. */
  @TruffleBoundary
  public TapeValue keyAt(long index) {
    if (kind() != Kind.OBJECT) throw new IllegalStateException("Not a " + owner.format().name + " object");
    long count = owner.tape.length(node);
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException(owner.format().name + " member index");
    long key = owner.tape.first(node);
    for (long i = 0; i < index; ++i) key = owner.tape.next(owner.tape.next(key));
    return new TapeValue(owner, key);
  }

  /** Value of the i-th member in source order. */
  @TruffleBoundary
  public TapeValue memberAt(long index) {
    TapeValue key = keyAt(index);
    return aliasView(owner, owner.tape.next(key.node));
  }

  /** Random access: small arrays are walked; larger ones use an index cached by the owner. */
  @TruffleBoundary
  public TapeValue element(long index) {
    if (kind() != Kind.ARRAY) throw new IllegalStateException("Not a " + owner.format().name + " array");
    return view(owner, elementNode(owner, node, index));
  }

  /**
   * Source child node of an array; static so compiled interop reads never pass a fresh
   * view to a boundary.
   */
  @TruffleBoundary
  private static long elementNode(TapeOwner owner, long array, long index) {
    long count = owner.tape.length(array);
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException(owner.format().name + " array index");
    if (count > TapeOwner.INDEX_THRESHOLD) {
      long child = owner.arrayIndex(array)[(int) index];
      return owner.format().hasAliases() ? child : owner.resolve(child);
    }
    long child = owner.tape.first(array);
    for (long i = 0; i < index; ++i) child = owner.tape.next(child);
    return owner.format().hasAliases() ? child : owner.resolve(child);
  }

  /** Sequential traversal in source order without building an index. */
  @TruffleBoundary
  public Iterable<TapeValue> elements() {
    if (kind() != Kind.ARRAY) throw new IllegalStateException("Not a " + owner.format().name + " array");
    return () -> {
      // The Iterable may outlive its owner or cross threads: check before touching the tape.
      owner.check();
      return new java.util.Iterator<>() {
          private long remaining = owner.tape.length(node);
          private long child = remaining == 0 ? -1 : owner.tape.first(node);

          @Override
          public boolean hasNext() {
            return remaining > 0;
          }

          @Override
          public TapeValue next() {
            owner.check();
            if (remaining == 0) throw new java.util.NoSuchElementException();
            TapeValue value = aliasView(owner, child);
            if (--remaining > 0) child = owner.tape.next(child);
            return value;
          }
        };
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
      if (i + 1 < keys.length) key = owner.tape.next(owner.tape.next(key));
    }
    return new DataArray(keys);
  }

  /** Encoded member name, or null when the name has an unpaired surrogate and matches no key. */
  @TruffleBoundary
  static @org.jspecify.annotations.Nullable TapeKey keyOf(String name) {
    try {
      return TapeKey.of(name);
    } catch (IllegalArgumentException unpaired) {
      return null;
    }
  }

  /**
   * Alias-resolved member value node, or -1; checks the receiver inline so the view itself never
   * escapes. A hinted hit checks the receiver's kind on the word it loads anyway; everything else
   * checks the kind first.
   */
  long memberNode(@org.jspecify.annotations.Nullable TapeKey key, int[] hint) {
    owner.check();
    if (key == null) return -1;
    return memberNode(owner, node, key, hint);
  }

  /**
   * Directory-hinted lookup over primitives (Myna ABI v4, elide-dev/myna#45). {@code hint[0]} is
   * one plus the rank of this site's key in the object's directory where it was last found (0:
   * none). Directories are sorted by hash, so the rank is the same for every member order of one
   * key set: documents that alternate member orders hit on every read, where the earlier position
   * hint (a key's offset, validated by a native side table) missed on every other one. The hint is
   * validated by the receiver's kind and directory size, the entry's hash and the key node's tag,
   * length and inline bytes; never by owner identity, so a stale hint only costs a miss. The hit
   * path is one tape call of straight-line word loads and compares; misses (and keys longer than
   * 16 bytes) search the directory behind a static boundary and update the hint.
   *
   * <p>Aliases: a miss resolves the value it found inside its boundary, so reads of formats without
   * aliases compile with no resolution code at all. A hit returns the value node unresolved, so it
   * must never land on an alias: tapes of formats with aliases reject a key whose value is an alias
   * in {@link TapeSource#keyNodeEquals}, and misses on such owners never teach a hint.
   */
  static long memberNode(TapeOwner owner, long object, TapeKey key, int[] hint) {
    int rank = hint[0] - 1;
    if (rank >= 0 && key.inline()) {
      long k = owner.hinted(object, rank, key);
      if (k >= 0) return k + 1;
    }
    return miss(owner, object, key, hint);
  }

  /** Kind check, scan, hint update and alias resolution of a missed lookup: one call in compiled code. */
  @TruffleBoundary
  private static long miss(TapeOwner owner, long object, TapeKey key, int[] hint) {
    if (owner.tag(object) != ValueTape.OBJECT) return -1;
    boolean aliases = owner.format().hasAliases();
    // Misses on owners with aliases never teach a hint (a hit returns its value unresolved).
    long found = owner.find(object, key, aliases ? null : hint);
    if (found < 0) return found;
    return aliases ? owner.resolve(found) : found;
  }

  /** A call site's directory-rank hint, cached with its key; 0 means no hint yet. */
  static int[] newHint() {
    return new int[1];
  }

  // Guests usually read members by constant names: cache the encoded key per name identity so a
  // compiled read neither re-encodes nor allocates.

  @ExportMessage
  static final class IsMemberReadable {
    @Specialization(guards = "name == cachedName", limit = "3")
    static boolean cached(
        TapeValue receiver,
        String name,
        @Cached(value = "name", neverDefault = true) String cachedName,
        @Cached("keyOf(name)") @org.jspecify.annotations.Nullable TapeKey key,
        @Cached(value = "newHint()", dimensions = 0, neverDefault = true) int[] hint) {
      return receiver.memberNode(key, hint) >= 0;
    }

    @Specialization(replaces = "cached")
    static boolean generic(TapeValue receiver, String name) {
      return receiver.memberNode(keyOf(name), new int[1]) >= 0;
    }
  }

  @ExportMessage
  static final class ReadMember {
    @Specialization(guards = {"name == cachedName", "ownerClass(receiver) == ownerType"}, limit = "3")
    static Object cached(
        TapeValue receiver,
        String name,
        @Cached(value = "name", neverDefault = true) String cachedName,
        @Cached("keyOf(name)") @org.jspecify.annotations.Nullable TapeKey key,
        @Cached(value = "newHint()", dimensions = 0, neverDefault = true) int[] hint,
        @Cached(value = "ownerClass(receiver)", neverDefault = true) Class<? extends TapeOwner> ownerType)
        throws UnknownIdentifierException {
      TapeOwner owner = com.oracle.truffle.api.CompilerDirectives.castExact(receiver.owner, ownerType);
      owner.check();
      long found = key == null ? -1 : memberNode(owner, receiver.node, key, hint);
      return readFound(owner, receiver.node, found, name, key);
    }

    @Specialization(replaces = "cached")
    static Object generic(TapeValue receiver, String name) throws UnknownIdentifierException {
      TapeKey key = keyOf(name);
      TapeOwner owner = receiver.owner;
      owner.check();
      long found = key == null ? -1 : memberNode(owner, receiver.node, key, new int[1]);
      return readFound(owner, receiver.node, found, name, key);
    }
  }

  static Class<? extends TapeOwner> ownerClass(TapeValue receiver) { return receiver.owner.getClass(); }

  private static Object readFound(TapeOwner owner, long object, long found, String name, @org.jspecify.annotations.Nullable TapeKey key) throws UnknownIdentifierException {
    if (found < 0) throw unknown(name);
    return owner.format().hasAliases() ? new AliasValue(owner, found, sourceMember(owner, object, key)) : new TapeValue(owner, found);
  }

  @TruffleBoundary
  private static long sourceMember(TapeOwner owner, long object, @org.jspecify.annotations.Nullable TapeKey key) {
    return owner.find(object, java.util.Objects.requireNonNull(key), null);
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
    return view(owner, elementNode(owner, node, index));
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

  boolean booleanInterop() throws UnsupportedMessageException {
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

  /** DECIMAL tokens longer than this are not converted by interop at all. */
  static final int MAX_DECIMAL_TOKEN_BYTES = 2 * MAX_INTEGER_DIGITS;

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
  private static @org.jspecify.annotations.Nullable BigDecimal decimal(TapeOwner owner, long node) {
    // BigDecimal parsing is superlinear in digits: tokens past the bound fit no type and are never
    // parsed here (numberText still returns them exactly).
    if ((int) owner.lexical(node) > MAX_DECIMAL_TOKEN_BYTES) return null;
    try {
      return new BigDecimal(owner.numberText(node));
    } catch (NumberFormatException | ArithmeticException overflow) {
      return null;
    }
  }

  /** Exact integer when the number is integral and in range, else null (also for non-numbers). */
  @TruffleBoundary
  private static @org.jspecify.annotations.Nullable BigInteger exactInteger(TapeOwner owner, long node) {
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
  private static boolean fitsSigned(TapeOwner owner, long node, long min, long max) {
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
  private static double exactDouble(TapeOwner owner, long node) {
    return switch (owner.tag(node)) {
      case ValueTape.F64 -> owner.tape.doubleValue(node);
      case ValueTape.I64 -> {
        long v = owner.tape.signedValue(node);
        double d = (double) v;
        // Exact when the round trip is lossless; 2^63 saturates back to Long.MAX_VALUE, so exclude it.
        yield v != Long.MAX_VALUE && (long) d == v ? d : Double.NaN;
      }
      case ValueTape.U64 -> {
        BigInteger exact = exactInteger(owner, node);
        if (exact == null) yield Double.NaN;
        double d = exact.doubleValue();
        yield new BigDecimal(d).toBigIntegerExact().equals(exact) ? d : Double.NaN;
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
    return tag() == ValueTape.F64 || !Double.isNaN(doubleOrNaN());
  }

  @ExportMessage
  boolean fitsInFloat() {
    double d = doubleOrNaN();
    return (tag() == ValueTape.F64 && Double.isNaN(d)) || !Double.isNaN(d) && (double) (float) d == d;
  }

  @ExportMessage
  byte asByte() throws UnsupportedMessageException {
    if (!fitsInByte()) throw UnsupportedMessageException.create();
    return (byte) longInterop();
  }

  @ExportMessage
  short asShort() throws UnsupportedMessageException {
    if (!fitsInShort()) throw UnsupportedMessageException.create();
    return (short) longInterop();
  }

  @ExportMessage
  int asInt() throws UnsupportedMessageException {
    if (!fitsInInt()) throw UnsupportedMessageException.create();
    return (int) longInterop();
  }

  long longInterop() throws UnsupportedMessageException {
    if (tag() == ValueTape.I64) return owner.tape.signedValue(node);
    return exactLong(owner, node);
  }

  @TruffleBoundary
  private static long exactLong(TapeOwner owner, long node) throws UnsupportedMessageException {
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

  double doubleInterop() throws UnsupportedMessageException {
    double d = doubleOrNaN();
    if (Double.isNaN(d) && tag() != ValueTape.F64) throw UnsupportedMessageException.create();
    return d;
  }
  static Class<? extends TapeReader> readerClass(TapeValue receiver) { return receiver.owner.tape.getClass(); }

  @ExportMessage
  static final class AsLong {
    @Specialization(guards = "readerClass(receiver) == tapeClass", limit = "3")
    static long cached(TapeValue receiver,
        @Cached(value = "readerClass(receiver)", neverDefault = true) Class<? extends TapeReader> tapeClass)
        throws UnsupportedMessageException {
      receiver.owner.check();
      TapeReader tape = com.oracle.truffle.api.CompilerDirectives.castExact(receiver.owner.tape, tapeClass);
      if (tape.kindAt(receiver.node) == ValueTape.I64) return tape.signedValue(receiver.node);
      return exactLong(receiver.owner, receiver.node);
    }
    @Specialization(replaces = "cached")
    static long generic(TapeValue receiver) throws UnsupportedMessageException { return receiver.longInterop(); }
  }

  @ExportMessage
  static final class AsDouble {
    @Specialization(guards = "readerClass(receiver) == tapeClass", limit = "3")
    static double cached(TapeValue receiver,
        @Cached(value = "readerClass(receiver)", neverDefault = true) Class<? extends TapeReader> tapeClass)
        throws UnsupportedMessageException {
      receiver.owner.check();
      TapeReader tape = com.oracle.truffle.api.CompilerDirectives.castExact(receiver.owner.tape, tapeClass);
      int tag = tape.kindAt(receiver.node);
      if (tag == ValueTape.F64) return tape.doubleValue(receiver.node);
      double value = exactDouble(receiver.owner, receiver.node);
      if (Double.isNaN(value)) throw UnsupportedMessageException.create();
      return value;
    }
    @Specialization(replaces = "cached")
    static double generic(TapeValue receiver) throws UnsupportedMessageException { return receiver.doubleInterop(); }
  }

  @ExportMessage
  static final class AsBoolean {
    @Specialization(guards = "readerClass(receiver) == tapeClass", limit = "3")
    static boolean cached(TapeValue receiver,
        @Cached(value = "readerClass(receiver)", neverDefault = true) Class<? extends TapeReader> tapeClass)
        throws UnsupportedMessageException {
      receiver.owner.check();
      TapeReader tape = com.oracle.truffle.api.CompilerDirectives.castExact(receiver.owner.tape, tapeClass);
      if (tape.kindAt(receiver.node) != ValueTape.BOOL) throw UnsupportedMessageException.create();
      return tape.booleanValue(receiver.node);
    }
    @Specialization(replaces = "cached")
    static boolean generic(TapeValue receiver) throws UnsupportedMessageException { return receiver.booleanInterop(); }
  }
}
