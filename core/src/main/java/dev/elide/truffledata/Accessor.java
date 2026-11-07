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

  protected Accessor(Schema schema, int ordinal) {
    this.schema = Objects.requireNonNull(schema);
    this.ordinal = Objects.checkIndex(ordinal, schema.size());
    this.field = schema.field(ordinal);
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
    return accessor instanceof LibraryAccessor || !accessor.accepts(row) ? null : accessor;
  }

  static boolean bound(@Nullable Accessor accessor, Object row) {
    return accessor != null && accessor.accepts(row);
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
