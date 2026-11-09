package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable read path for one field of one row layout. An accessor is bound to the layout's
 * identity (a static shape, a generated class, a protobuf plan, a column representation), never to
 * schema identity alone: two layouts may share one {@link Schema}. Instances are identity-stable
 * per (layout, ordinal), so read nodes cache them as partial-evaluation constants and a compiled
 * read is one {@link #accepts} guard plus the representation's own loads.
 *
 * <p>Every method other than {@link #accepts} requires a row the accessor accepts; given any other
 * row it throws {@link ContractViolation} and never reads from it. Results follow {@link
 * DataLibrary} exactly: {@code read} returns the field value (or {@link Missing}/{@link DataNull}),
 * the primitive reads throw {@link UnexpectedResultException} with that value when it is not of the
 * requested type, and {@link #length} is the contract length {@link ReadLengthNode} reports.
 */
public abstract class Accessor {
  static final boolean STRICT = Boolean.getBoolean("truffledata.strict");

  private final Schema schema;
  private final int ordinal;
  private final Field field;

  /**
   * When set, this accessor's layout is exactly (row class, schema identity): it accepts a row iff
   * {@code row.getClass() == exactRowClass} and the row's descriptor is {@link #schema}. Read nodes
   * then guard with that hub compare and one field compare instead of a virtual {@link #accepts}.
   */
  final @Nullable Class<? extends AbstractRow> exactRowClass;

  protected Accessor(Schema schema, int ordinal) {
    this.schema = Objects.requireNonNull(schema);
    this.ordinal = Objects.checkIndex(ordinal, schema.size());
    this.field = schema.field(ordinal);
    this.exactRowClass = null;
  }

  /**
   * An accessor whose layout is exactly (exact row class, schema identity): {@link #accepts} must
   * hold for a row iff its class is {@code exactRowClass} and its descriptor is {@code schema}.
   * Only representations whose schema identity is unique per layout qualify; layouts that share a
   * schema (several static layouts, several slab root layouts) must use the two-argument
   * constructor. Binding verifies the claim against {@link #accepts} on every specialization.
   */
  protected Accessor(Schema schema, int ordinal, Class<? extends AbstractRow> exactRowClass) {
    this.schema = Objects.requireNonNull(schema);
    this.ordinal = Objects.checkIndex(ordinal, schema.size());
    this.field = schema.field(ordinal);
    this.exactRowClass = Objects.requireNonNull(exactRowClass);
  }

  public final Schema schema() {
    return schema;
  }

  public final int ordinal() {
    return ordinal;
  }

  public final Field field() {
    return field;
  }

  /** Whether {@code row} has exactly this accessor's layout. Never throws. */
  public abstract boolean accepts(Object row);

  public abstract Object read(Object row);

  public boolean present(Object row) {
    return read(row) != Missing.INSTANCE;
  }

  public long readLong(Object row) throws UnexpectedResultException {
    Object value = read(row);
    if (value instanceof Long n) return n;
    throw new UnexpectedResultException(value);
  }

  public double readDouble(Object row) throws UnexpectedResultException {
    Object value = read(row);
    if (value instanceof Double n) return n;
    throw new UnexpectedResultException(value);
  }

  public boolean readBoolean(Object row) throws UnexpectedResultException {
    Object value = read(row);
    if (value instanceof Boolean b) return b;
    throw new UnexpectedResultException(value);
  }

  /**
   * {@link #readLong} of a row read nodes have already bound to this accessor ({@code bound(this,
   * row)} held). Called only by the read nodes' layout-bound specializations; overrides may skip
   * the layout re-check and nothing else (lifetime, projection, presence and type checks stay).
   * Strict mode asserts the guard.
   */
  protected long readLongBound(Object row) throws UnexpectedResultException {
    assertAccepted(row);
    return readLong(row);
  }

  /** {@link #readDouble} of a bound row; see {@link #readLongBound}. */
  protected double readDoubleBound(Object row) throws UnexpectedResultException {
    assertAccepted(row);
    return readDouble(row);
  }

  /** {@link #readBoolean} of a bound row; see {@link #readLongBound}. */
  protected boolean readBooleanBound(Object row) throws UnexpectedResultException {
    assertAccepted(row);
    return readBoolean(row);
  }

  /** {@link #length} of a bound row; see {@link #readLongBound}. */
  protected int lengthBound(Object row) {
    assertAccepted(row);
    return length(row);
  }

  /** Strict-mode audit that a bound hook received a row this accessor accepts. */
  protected final void assertAccepted(Object row) {
    if (STRICT && !accepts(row)) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      throw new AssertionError("Bound accessor read of a row it does not accept");
    }
  }

  /**
   * Contract length of a present, non-null string (UTF-16 units unless the field declares code
   * points) or array (elements). Exact length contracts fold to their bound after the read, so
   * scoped rows still enforce their lifetime.
   */
  public int length(Object row) {
    return lengthOf(read(row));
  }

  /**
   * {@link #length} of a value already read through this accessor. Strings and {@link DataArray}
   * stay inline; any other array (including a {@link BorrowedArray}, whose size is an interface
   * call) goes through uncached interop behind a boundary, so representations with their own array
   * class override {@link #length} to read its size directly.
   */
  protected final int lengthOf(Object value) {
    if (value instanceof String text) return stringLength(text);
    if (value instanceof DataArray array) return arrayLength(array.size());
    return arrayLength(foreignLength(value));
  }

  /** UTF-16 units (or code points when the contract says so) of a string field value. */
  protected final int stringLength(String text) {
    var bounds = field.constraints().length();
    if (bounds == null) return text.length();
    if (bounds.exact()) return bounds.min();
    return Values.stringLength(text, bounds.unit());
  }

  /** Element count of an array field value, folded to an exact contract length. */
  protected final int arrayLength(int size) {
    var bounds = field.constraints().length();
    return bounds != null && bounds.exact() ? bounds.min() : size;
  }

  /** Rejection of a row this accessor does not accept; the caller transfers to the interpreter. */
  protected final ContractViolation mismatch() {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    return new ContractViolation("Accessor layout mismatch");
  }

  // Other interop arrays: keep the uncached dispatch out of guest compilation.
  @TruffleBoundary
  private static int foreignLength(Object value) {
    InteropLibrary arrays = InteropLibrary.getUncached(value);
    if (!arrays.hasArrayElements(value))
      throw new ContractViolation("Expected a present non-null string or array");
    try {
      return Math.toIntExact(arrays.getArraySize(value));
    } catch (UnsupportedMessageException e) {
      throw new ContractViolation("Array length unavailable");
    }
  }

  /**
   * The representation-bound accessor read nodes cache for {@code row}, or null when the row only
   * offers the library-backed default (those rows keep their {@link DataLibrary} specializations).
   */
  @TruffleBoundary
  static @Nullable Accessor bind(Object row, int ordinal) {
    if (!(row instanceof AbstractRow r) || ordinal < 0 || ordinal >= r.schema().size()) return null;
    Accessor accessor = r.accessor(ordinal);
    if (accessor instanceof LibraryAccessor) return null;
    boolean accepted = accessor.accepts(row);
    var exact = accessor.exactRowClass;
    if (exact != null && accepted != (r.getClass() == exact && r.descriptor == accessor.schema))
      throw new IllegalStateException("Exact-layout accessor guard disagrees with accepts");
    return accepted ? accessor : null;
  }

  /**
   * The read nodes' layout guard. Exact-layout accessors compile to a hub compare and one field
   * compare with no virtual call; others ask {@link #accepts}.
   */
  static boolean bound(@Nullable Accessor accessor, Object row) {
    if (accessor == null) return false;
    var exact = accessor.exactRowClass;
    if (exact != null)
      return row.getClass() == exact && ((AbstractRow) row).descriptor == accessor.schema;
    return accessor.accepts(row);
  }

  /** Strict-mode contract audit of an accessor result, mirroring DataLibrary's assertions. */
  @TruffleBoundary
  static void audit(Accessor accessor, Object value) {
    try {
      Values.validate(accessor.field, value);
    } catch (ContractViolation e) {
      throw new AssertionError("Accessor result contradicts its field contract", e);
    }
  }
}
