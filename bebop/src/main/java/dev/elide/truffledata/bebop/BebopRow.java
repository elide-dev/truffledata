package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.Layouts.LE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.AbstractRow;
import dev.elide.truffledata.Accessor;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.ExactNumber;
import dev.elide.truffledata.Missing;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.bebop.BebopCodec.Dec;
import dev.elide.truffledata.bebop.Layouts.RowLayout;
import dev.elide.truffledata.bebop.Layouts.Slot;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * A struct, message or union read in place from a validated {@link BebopDocument}. Rows are
 * immutable and keep their document alive. Absent message fields and inactive union branches
 * read as {@link Missing}.
 *
 * <p>Guest values: {@code Boolean}; {@code Long} for integers to 63 bits and {@code int64};
 * {@link ExactNumber} for {@code uint64} and the 128-bit integers; {@code Double} for every
 * floating-point type; {@code String} for strings and UUIDs; rows for structs, messages, unions,
 * timestamps ({@code seconds}, {@code nanos}, {@code offset_ms}) and durations; {@link BebopArray}
 * for arrays, sets and maps (as {@code key}/{@code value} rows). A union row has a {@code
 * discriminator} and one member per branch, of which at most one is present.
 */
@ExportLibrary(DataLibrary.class)
public final class BebopRow extends AbstractRow {
  final BebopDocument document;
  final RowLayout layout;
  /** STRUCT: first byte. MESSAGE: first byte after the length. UNION: the discriminator. */
  final int start;

  /** MESSAGE: the byte after the control byte. */
  final int end;

  BebopRow(BebopDocument document, RowLayout layout, int start, int end) {
    super(layout.schema);
    this.document = document;
    this.layout = layout;
    this.start = start;
    this.end = end;
  }

  public BebopDocument document() {
    return document;
  }

  @Override
  public Accessor accessor(int ordinal) {
    RowLayout bound = layout;
    return descriptor.accessor(BebopRow.class, ordinal, i -> new Bound(bound, i));
  }

  /** The value of field {@code ordinal}, or {@link Missing}. */
  public Object get(int ordinal) {
    int at = locate(layout, ordinal);
    return at < 0 ? Missing.INSTANCE : value(document, layout.slots[ordinal], at);
  }

  public boolean present(int ordinal) {
    return locate(layout, ordinal) >= 0;
  }

  /** Offset of field {@code ordinal}, or -1 when it is absent. */
  int locate(RowLayout layout, int ordinal) {
    byte[] b = document.bytes;
    switch (layout.form) {
      case RowLayout.STRUCT -> {
        int offset = layout.offsets[ordinal];
        return offset >= 0 ? start + offset : skipTo(layout, ordinal);
      }
      case RowLayout.MESSAGE -> {
        return find(b, start, end, layout.tags[ordinal]);
      }
      default -> {
        if (ordinal == 0) return start;
        return (b[start] & 0xFF) == layout.tags[ordinal] ? start + 1 : -1;
      }
    }
  }

  /** A bounded structural pass over the variable-width fields before {@code ordinal}. */
  @TruffleBoundary
  private int skipTo(RowLayout layout, int ordinal) {
    int from = ordinal;
    while (layout.offsets[from] < 0) from--;
    byte[] b = document.bytes;
    BebopCodec codec = document.codec();
    Dec d = new Dec(b, start + layout.offsets[from], b.length, false, codec.profile(), codec.limits());
    for (int i = from; i < ordinal; i++) layout.nodes[i].skip(d);
    return d.pos;
  }

  /**
   * Start of the field with {@code tag} in the validated message content {@code [start, end)}, or
   * -1 when it is absent. Reads the control byte, the directory and one boundary; allocates
   * nothing.
   */
  static int find(byte[] b, int start, int end, int tag) {
    int control = b[end - 1] & 0xFF;
    int width = 1 << (control & 3);
    int kind = control >>> 2 & 7;
    int dirEnd = end - 1;
    int rank;
    int count;
    int dirBytes;
    if (kind == 0) {
      return -1;
    } else if (kind <= 3) {
      count = dirBytes = kind;
      rank = -1;
      for (int i = 0; i < kind; i++) if ((b[dirEnd - kind + i] & 0xFF) == tag) rank = i;
      if (rank < 0) return -1;
    } else if (kind <= 6) {
      dirBytes = 1 << (kind - 4);
      long mask = load(b, dirEnd - dirBytes, dirBytes);
      if (tag > dirBytes * 8 || (mask >>> (tag - 1) & 1) == 0) return -1;
      count = Long.bitCount(mask);
      rank = Long.bitCount(mask & (1L << (tag - 1)) - 1);
    } else {
      int blocks = b[dirEnd - 1] & 0xFF;
      dirBytes = 1 + 5 * Integer.bitCount(blocks);
      int block = (tag - 1) >>> 5;
      if ((blocks >>> block & 1) == 0) return -1;
      int entry = dirEnd - dirBytes + 5 * Integer.bitCount(blocks & (1 << block) - 1);
      int mask = LE.getInt(b, entry + 1);
      int bit = (tag - 1) & 31;
      if ((mask >>> bit & 1) == 0) return -1;
      rank = (b[entry] & 0xFF) + Integer.bitCount(mask & (1 << bit) - 1);
      int last = dirEnd - 6;
      count = (b[last] & 0xFF) + Integer.bitCount(LE.getInt(b, last + 1));
    }
    if (rank == 0) return start;
    int boundaries = dirEnd - dirBytes - (count - 1) * width;
    return start + (int) load(b, boundaries + (rank - 1) * width, width);
  }

  private static long load(byte[] b, int at, int width) {
    return switch (width) {
      case 1 -> b[at] & 0xFFL;
      case 2 -> LE.getShort(b, at) & 0xFFFFL;
      default -> LE.getInt(b, at) & 0xFFFFFFFFL;
    };
  }

  /** The guest value of {@code slot} at {@code at}. */
  static Object value(BebopDocument document, Slot slot, int at) {
    byte[] b = document.bytes;
    return switch (slot.shape) {
      case Slot.BOOL -> b[at] != 0;
      case Slot.LONG -> slot.loadLong(b, at);
      case Slot.DOUBLE -> slot.loadDouble(b, at);
      case Slot.EXACT64 -> exact64(b, at);
      case Slot.EXACT128 -> exact128(b, at, slot);
      case Slot.STRING -> string(b, at);
      case Slot.UUID -> uuid(b, at);
      case Slot.ROW -> row(document, Objects.requireNonNull(slot.row), at);
      default -> BebopArray.at(document, Objects.requireNonNull(slot.array), at);
    };
  }

  static BebopRow row(BebopDocument document, RowLayout layout, int at) {
    if (layout.form == RowLayout.STRUCT) return new BebopRow(document, layout, at, at);
    int content = at + 4;
    return new BebopRow(document, layout, content, content + LE.getInt(document.bytes, at));
  }

  @TruffleBoundary
  private static Object exact64(byte[] b, int at) {
    return new ExactNumber(new BigDecimal(Long.toUnsignedString(LE.getLong(b, at))));
  }

  @TruffleBoundary
  private static Object exact128(byte[] b, int at, Slot slot) {
    return new ExactNumber(
        new BigDecimal(BebopCodec.integer128(b, at, slot.scalar == BebopType.Scalar.UINT128)));
  }

  @TruffleBoundary
  private static Object string(byte[] b, int at) {
    return new String(b, at + 4, LE.getInt(b, at), StandardCharsets.UTF_8);
  }

  @TruffleBoundary
  private static Object uuid(byte[] b, int at) {
    return new UUID(Long.reverseBytes(LE.getLong(b, at)), Long.reverseBytes(LE.getLong(b, at + 8)))
        .toString();
  }

  private long longAt(RowLayout layout, int ordinal) throws UnexpectedResultException {
    int at = locate(layout, ordinal);
    if (at < 0) throw new UnexpectedResultException(Missing.INSTANCE);
    return layout.slots[ordinal].loadLong(document.bytes, at);
  }

  private double doubleAt(RowLayout layout, int ordinal) throws UnexpectedResultException {
    int at = locate(layout, ordinal);
    if (at < 0) throw new UnexpectedResultException(Missing.INSTANCE);
    return layout.slots[ordinal].loadDouble(document.bytes, at);
  }

  private boolean booleanAt(RowLayout layout, int ordinal) throws UnexpectedResultException {
    int at = locate(layout, ordinal);
    if (at < 0) throw new UnexpectedResultException(Missing.INSTANCE);
    return document.bytes[at] != 0;
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(int field) {
    return get(field);
  }

  @ExportMessage
  boolean isPresent(int field) {
    return present(field);
  }

  @ExportMessage
  static class ReadLong {
    @Specialization(
        guards = {"row.layout == layout", "field == cachedField", "layout.isLong(cachedField)"},
        limit = "3")
    static long cached(
        BebopRow row,
        int field,
        @Cached("row.layout") RowLayout layout,
        @Cached("field") int cachedField)
        throws UnexpectedResultException {
      return row.longAt(layout, cachedField);
    }

    @Specialization(replaces = "cached")
    static long generic(BebopRow row, int field) throws UnexpectedResultException {
      if (row.layout.isLong(field)) return row.longAt(row.layout, field);
      throw new UnexpectedResultException(row.get(field));
    }
  }

  @ExportMessage
  static class ReadDouble {
    @Specialization(
        guards = {"row.layout == layout", "field == cachedField", "layout.isDouble(cachedField)"},
        limit = "3")
    static double cached(
        BebopRow row,
        int field,
        @Cached("row.layout") RowLayout layout,
        @Cached("field") int cachedField)
        throws UnexpectedResultException {
      return row.doubleAt(layout, cachedField);
    }

    @Specialization(replaces = "cached")
    static double generic(BebopRow row, int field) throws UnexpectedResultException {
      if (row.layout.isDouble(field)) return row.doubleAt(row.layout, field);
      throw new UnexpectedResultException(row.get(field));
    }
  }

  @ExportMessage
  static class ReadBoolean {
    @Specialization(
        guards = {"row.layout == layout", "field == cachedField", "layout.isBoolean(cachedField)"},
        limit = "3")
    static boolean cached(
        BebopRow row,
        int field,
        @Cached("row.layout") RowLayout layout,
        @Cached("field") int cachedField)
        throws UnexpectedResultException {
      return row.booleanAt(layout, cachedField);
    }

    @Specialization(replaces = "cached")
    static boolean generic(BebopRow row, int field) throws UnexpectedResultException {
      if (row.layout.isBoolean(field)) return row.booleanAt(row.layout, field);
      throw new UnexpectedResultException(row.get(field));
    }
  }

  /**
   * The read path of one field of one layout. Each layout has its own schema, so the layout is
   * exactly (BebopRow, schema identity) and read nodes guard with a class and a field compare.
   */
  static final class Bound extends Accessor {
    private final RowLayout layout;
    private final Slot slot;

    Bound(RowLayout layout, int ordinal) {
      super(layout.schema, ordinal, BebopRow.class);
      this.layout = layout;
      this.slot = layout.slots[ordinal];
    }

    private BebopRow row(Object row) {
      if (!(row instanceof BebopRow r) || r.layout != layout) throw mismatch();
      return r;
    }

    @Override
    public boolean accepts(Object row) {
      return row instanceof BebopRow r && r.layout == layout;
    }

    @Override
    public Object read(Object row) {
      return row(row).get(ordinal());
    }

    @Override
    public boolean present(Object row) {
      return row(row).locate(layout, ordinal()) >= 0;
    }

    @Override
    public long readLong(Object row) throws UnexpectedResultException {
      BebopRow r = row(row);
      if (slot.shape == Slot.LONG) return r.longAt(layout, ordinal());
      throw new UnexpectedResultException(r.get(ordinal()));
    }

    @Override
    public double readDouble(Object row) throws UnexpectedResultException {
      BebopRow r = row(row);
      if (slot.shape == Slot.DOUBLE) return r.doubleAt(layout, ordinal());
      throw new UnexpectedResultException(r.get(ordinal()));
    }

    @Override
    public boolean readBoolean(Object row) throws UnexpectedResultException {
      BebopRow r = row(row);
      if (slot.shape == Slot.BOOL) return r.booleanAt(layout, ordinal());
      throw new UnexpectedResultException(r.get(ordinal()));
    }

    @Override
    protected long readLongBound(Object row) throws UnexpectedResultException {
      assertAccepted(row);
      BebopRow r = CompilerDirectives.castExact(row, BebopRow.class);
      if (slot.shape == Slot.LONG) return r.longAt(layout, ordinal());
      throw new UnexpectedResultException(r.get(ordinal()));
    }

    @Override
    protected double readDoubleBound(Object row) throws UnexpectedResultException {
      assertAccepted(row);
      BebopRow r = CompilerDirectives.castExact(row, BebopRow.class);
      if (slot.shape == Slot.DOUBLE) return r.doubleAt(layout, ordinal());
      throw new UnexpectedResultException(r.get(ordinal()));
    }

    @Override
    protected boolean readBooleanBound(Object row) throws UnexpectedResultException {
      assertAccepted(row);
      BebopRow r = CompilerDirectives.castExact(row, BebopRow.class);
      if (slot.shape == Slot.BOOL) return r.booleanAt(layout, ordinal());
      throw new UnexpectedResultException(r.get(ordinal()));
    }

    @Override
    public int length(Object row) {
      Object value = row(row).get(ordinal());
      if (value instanceof BebopArray array) return arrayLength(array.size());
      return lengthOf(value);
    }
  }
}
