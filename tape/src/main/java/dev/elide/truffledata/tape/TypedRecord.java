package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import java.math.BigInteger;
import java.util.List;

/**
 * One record entry of a typed result: validated slots with allocation-free primitive reads. Every
 * record and array of a result shares one {@link TypedOwner}; closing any record closes that
 * owner and invalidates all of them. {@link #snapshot()} detaches an owned immutable copy.
 */
@ExportLibrary(DataLibrary.class)
public final class TypedRecord extends AbstractRow implements AutoCloseable {
  /** Snapshot budgets: typed results are already bounded by the native input and index caps. */
  static final Limits SNAPSHOT_LIMITS = new Limits(Integer.MAX_VALUE, 64, Long.MAX_VALUE, Long.MAX_VALUE);

  final TypedOwner owner;
  final int entry;
  // Immutable copies of the native entry, read once while the owner was open.
  private final long base;
  private final long present;
  private final long nulls;

  TypedRecord(TypedOwner owner, int entry) {
    super(owner.schemas[owner.plan(entry)]);
    this.owner = owner;
    this.entry = entry;
    this.base = Integer.toUnsignedLong(owner.slotBase(entry));
    this.present = owner.present(entry);
    this.nulls = owner.nulls(entry);
  }

  /** The result owner shared by every record and array of this decode. */
  public TypedOwner owner() {
    return owner;
  }

  @Override
  protected boolean scoped() {
    return true;
  }

  @ExportMessage
  @Idempotent
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(int index) {
    if (!isPresent(index)) return Missing.INSTANCE;
    if (isNull(index)) return DataNull.INSTANCE;
    return switch (descriptor.field(index).kind()) {
      case INT64 -> longValue(index);
      case FLOAT64 -> doubleValue(index);
      case BOOLEAN -> booleanValue(index);
      case STRING -> stringValue(index);
      case EXACT -> Values.normalize(TypedOwner.unsigned(slotBits(index, Field.Kind.EXACT)));
      case RECORD -> record(index);
      case ARRAY -> array(index);
      default -> throw new IllegalStateException("Unsupported typed kind");
    };
  }

  static TypedAccess access(Schema schema, int index) {
    return new TypedAccess(schema, index);
  }

  @ExportMessage
  static class ReadLong {
    @Specialization(
        guards = {
          "row.getSchema() == schema",
          "index == cachedIndex",
          "schema.requiredLong(cachedIndex)"
        },
        limit = "3")
    static long cached(
        TypedRecord row,
        int index,
        @Cached("row.getSchema()") Schema schema,
        @Cached("index") int cachedIndex,
        @Cached("access(schema, cachedIndex)") TypedAccess binding) {
      return binding.longValue(row);
    }

    @Specialization(replaces = "cached")
    static long generic(TypedRecord row, int index) throws UnexpectedResultException {
      Object value = row.read(index);
      if (value instanceof Long result) return result;
      throw new UnexpectedResultException(value);
    }
  }

  @ExportMessage
  static class ReadDouble {
    @Specialization(
        guards = {
          "row.getSchema() == schema",
          "index == cachedIndex",
          "schema.requiredDouble(cachedIndex)"
        },
        limit = "3")
    static double cached(
        TypedRecord row,
        int index,
        @Cached("row.getSchema()") Schema schema,
        @Cached("index") int cachedIndex,
        @Cached("access(schema, cachedIndex)") TypedAccess binding) {
      return binding.doubleValue(row);
    }

    @Specialization(replaces = "cached")
    static double generic(TypedRecord row, int index) throws UnexpectedResultException {
      Object value = row.read(index);
      if (value instanceof Double result) return result;
      throw new UnexpectedResultException(value);
    }
  }

  @ExportMessage
  static class ReadBoolean {
    @Specialization(
        guards = {
          "row.getSchema() == schema",
          "index == cachedIndex",
          "schema.requiredBoolean(cachedIndex)"
        },
        limit = "3")
    static boolean cached(
        TypedRecord row,
        int index,
        @Cached("row.getSchema()") Schema schema,
        @Cached("index") int cachedIndex,
        @Cached("access(schema, cachedIndex)") TypedAccess binding) {
      return binding.booleanValue(row);
    }

    @Specialization(replaces = "cached")
    static boolean generic(TypedRecord row, int index) throws UnexpectedResultException {
      Object value = row.read(index);
      if (value instanceof Boolean result) return result;
      throw new UnexpectedResultException(value);
    }
  }

  private void check(int index) {
    owner.check();
    if (index < 0 || index >= descriptor.size()) throw new IndexOutOfBoundsException(index);
  }

  @ExportMessage
  public boolean isPresent(int index) {
    check(index);
    return (present & (1L << index)) != 0;
  }

  public boolean isNull(int index) {
    check(index);
    return (nulls & (1L << index)) != 0;
  }

  /** Slot payload of a present, non-null field of the given kind. */
  private long slotBits(int index, Field.Kind kind) {
    return owner.bits(slot(index, kind));
  }

  private long slot(int index, Field.Kind kind) {
    check(index);
    if (descriptor.field(index).kind() != kind)
      throw new IllegalArgumentException("Field kind mismatch");
    long bit = 1L << index;
    if ((present & bit) == 0 || (nulls & bit) != 0)
      throw new IllegalStateException("Field missing or null");
    return base + index;
  }

  long bits(TypedAccess access) {
    owner.check();
    if (descriptor != access.schema)
      throw new IllegalArgumentException("Accessor schema mismatch");
    if (access.hops.length == 0) return leaf(owner, base, present, nulls, access);
    return CompilerDirectives.isPartialEvaluationConstant(access)
        ? walkExploded(access)
        : walk(access);
  }

  private static long leaf(
      TypedOwner owner, long base, long present, long nulls, TypedAccess access) {
    long bit = 1L << access.index;
    if ((access.optional && (present & bit) == 0) || (access.nullable && (nulls & bit) != 0))
      throw new IllegalStateException("Scalar missing or null");
    return owner.bits(base + access.index);
  }

  // Constant accessors unroll the hop walk; each hop is one slot load and one entry load.
  @ExplodeLoop
  private long walkExploded(TypedAccess access) {
    long base = this.base, present = this.present, nulls = this.nulls;
    for (int i = 0; i < access.hops.length; i++) {
      int ordinal = access.hops[i];
      long bit = 1L << ordinal;
      byte checks = access.hopChecks[i];
      if (((checks & 1) != 0 && (present & bit) == 0) || ((checks & 2) != 0 && (nulls & bit) != 0))
        throw new IllegalStateException("Record missing or null");
      long child = owner.bits(base + ordinal);
      if (owner.schemas[owner.plan(child)] != access.hopSchemas[i]) throw hopMismatch();
      base = Integer.toUnsignedLong(owner.slotBase(child));
      present = owner.present(child);
      nulls = owner.nulls(child);
    }
    return leaf(owner, base, present, nulls, access);
  }

  @TruffleBoundary
  private static IllegalArgumentException hopMismatch() {
    return new IllegalArgumentException("Accessor record type mismatch");
  }

  private long walk(TypedAccess access) {
    long base = this.base, present = this.present, nulls = this.nulls;
    for (int i = 0; i < access.hops.length; i++) {
      int ordinal = access.hops[i];
      long bit = 1L << ordinal;
      byte checks = access.hopChecks[i];
      if (((checks & 1) != 0 && (present & bit) == 0) || ((checks & 2) != 0 && (nulls & bit) != 0))
        throw new IllegalStateException("Record missing or null");
      long child = owner.bits(base + ordinal);
      if (owner.schemas[owner.plan(child)] != access.hopSchemas[i]) throw hopMismatch();
      base = Integer.toUnsignedLong(owner.slotBase(child));
      present = owner.present(child);
      nulls = owner.nulls(child);
    }
    return leaf(owner, base, present, nulls, access);
  }

  public long longValue(int index) {
    return slotBits(index, Field.Kind.INT64);
  }

  public double doubleValue(int index) {
    return Double.longBitsToDouble(slotBits(index, Field.Kind.FLOAT64));
  }

  public boolean booleanValue(int index) {
    return slotBits(index, Field.Kind.BOOLEAN) != 0;
  }

  /** Unsigned 64-bit value, materialized only when requested. */
  public BigInteger unsignedValue(int index) {
    return TypedOwner.unsigned(slotBits(index, Field.Kind.EXACT));
  }

  public String stringValue(int index) {
    return owner.text(slot(index, Field.Kind.STRING));
  }

  /** Nested record of a present, non-null RECORD field; shares this record's owner. */
  public TypedRecord record(int index) {
    return new TypedRecord(owner, Math.toIntExact(slotBits(index, Field.Kind.RECORD)));
  }

  /** Scoped elements of a present, non-null ARRAY field; valid until the owner closes. */
  public TypedArray array(int index) {
    long bits = slotBits(index, Field.Kind.ARRAY);
    Field element = descriptor.field(index).constraints().element();
    if (element == null) throw new IllegalStateException("Array without element contract");
    return new TypedArray(owner, element, bits >>> 32, (int) bits);
  }

  /**
   * Owned immutable copy of this record and everything beneath it, as {@link Batch} rows and
   * {@link DataArray}s. It survives the owner's close and is safe to share across threads.
   */
  @TruffleBoundary
  public AbstractRow snapshot() {
    return Batch.copyOf(descriptor, List.<Object[]>of(snapshotValues()), SNAPSHOT_LIMITS).row(0);
  }

  /** Field values with nested records and arrays already detached. */
  @TruffleBoundary
  Object[] snapshotValues() {
    owner.check();
    Object[] values = new Object[descriptor.size()];
    for (int i = 0; i < values.length; i++) {
      Object value = read(i);
      if (value instanceof TypedRecord record) value = record.snapshot();
      else if (value instanceof TypedArray array) value = array.snapshot();
      values[i] = value;
    }
    return values;
  }

  /** Closes the shared owner: every record and array of this result becomes unreadable. */
  @Override
  @TruffleBoundary
  public void close() {
    owner.close();
  }
}
