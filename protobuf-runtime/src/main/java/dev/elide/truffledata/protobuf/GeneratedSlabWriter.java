package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bounded storage and wire primitives for schema-generated slab loops. Generated methods supply
 * literal record sizes, scalar operations, field slots and presence bits; this writer never
 * dispatches on a field descriptor. Uncommon wire shapes restart through the table producer.
 */
public final class GeneratedSlabWriter {
  public static final long[] EMPTY = new long[0];

  /** Internal restart signal: no partial slab escapes a generated decode. */
  public static final class Fallback extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private Fallback() {
      super(null, null, false, false);
    }
  }

  private static final Fallback FALLBACK = new Fallback();

  public static Fallback fallback() {
    return FALLBACK;
  }

  private final byte[] input;
  private final int maximum;
  private final int depthLimit;
  private final int fingerprint;
  private byte[] output;
  private int top = HEADER_BYTES;
  private int records;
  private int handles;
  private long pending;
  private boolean certified = true;

  /** Cursor used by the generated loop; bounds are checked by each wire primitive. */
  public int position;

  public GeneratedSlabWriter(
      Layout layout, byte[] input, int offset, int length, SlabLimits limits) {
    this(layout, input, offset, length, limits, 0);
  }

  /** A previous extent for the same wire length can avoid growth and the final trimming copy. */
  public GeneratedSlabWriter(
      Layout layout, byte[] input, int offset, int length, SlabLimits limits, int preferredBytes) {
    Objects.requireNonNull(layout);
    Objects.requireNonNull(limits);
    Objects.checkFromIndexSize(offset, length, input.length);
    if (length > layout.plan(0).maxWireBytes()) throw SlabCodec.bad("Wire byte budget exceeded");
    this.input = input;
    position = offset;
    maximum = limits.maxSlabBytes();
    depthLimit = Math.min(limits.maxDepth(), layout.plan(0).maxDepth() - 1);
    fingerprint = layout.fingerprint();
    long hintBound = Math.max(256L, 16L * length + layout.recordBytes(0));
    long capacity =
        preferredBytes > 0 && preferredBytes <= hintBound
            ? preferredBytes
            : Math.max(256L, 3L * length + HEADER_BYTES);
    output = new byte[(int) Math.min(maximum, capacity)];
  }

  private int allocate(int bytes, int alignment) {
    long at = ((long) top + alignment - 1) & -alignment;
    long end = at + bytes;
    if (bytes < 0 || end > maximum) throw SlabCodec.bad("Slab byte budget exceeded");
    if (end > output.length)
      output = Arrays.copyOf(output, (int) Math.min(maximum, Math.max(end, 2L * output.length)));
    top = (int) end;
    return (int) at;
  }

  public int record(int bytes, int depth) {
    if (depth > depthLimit) throw SlabCodec.bad("Protobuf nesting budget exceeded");
    int at = allocate(bytes, 8);
    putInt(output, at + RECORD_HANDLE, handles++);
    records++;
    return at;
  }

  public void descend(int depth) {
    if (depth >= depthLimit) throw SlabCodec.bad("Protobuf nesting budget exceeded");
  }

  public int tag(int end) {
    long value = varint(end);
    if (value < 0 || value > 0xffffffffL || (value >>> 3) == 0 || (value & 7) > 5)
      throw SlabCodec.bad("Invalid wire tag");
    return (int) value;
  }

  public long varint(int end) {
    if (position >= end) throw SlabCodec.bad("Truncated varint");
    int first = input[position++];
    if (first >= 0) return first;
    long value = first & 127;
    for (int shift = 7; shift < 64; shift += 7) {
      if (position >= end) throw SlabCodec.bad("Truncated varint");
      int next = input[position++] & 255;
      if (shift == 63 && (next & 254) != 0) throw SlabCodec.bad("Varint overflow");
      value |= (long) (next & 127) << shift;
      if ((next & 128) == 0) return value;
    }
    throw SlabCodec.bad("Malformed varint");
  }

  public int length(int end) {
    long value = varint(end);
    if (value < 0 || value > end - position) throw SlabCodec.bad("Truncated or oversized field");
    return (int) value;
  }

  public int fixed32(int end) {
    if (end - position < 4) throw SlabCodec.bad("Truncated fixed-width field");
    int value = getInt(input, position);
    position += 4;
    return value;
  }

  public long fixed64(int end) {
    if (end - position < 8) throw SlabCodec.bad("Truncated fixed-width field");
    long value = getLong(input, position);
    position += 8;
    return value;
  }

  public int string(int end, boolean text) {
    int bytes = length(end);
    if (bytes > LENGTH_MASK) throw SlabCodec.bad("Slab payload length exceeded");
    int units = text ? SlabCodec.utf16(input, position, bytes) : 0;
    int word = bytes | (text && units == bytes ? ASCII : 0);
    int headerBytes = (word & ASCII) != 0 ? ASCII_HEADER_BYTES : STRING_HEADER_BYTES;
    int at = allocate(headerBytes + bytes, 4);
    putInt(output, at + STRING_LENGTH, word);
    putInt(output, at + STRING_HANDLE, handles++);
    if (headerBytes == STRING_HEADER_BYTES) putInt(output, at + STRING_UTF16, units);
    System.arraycopy(input, position, output, at + headerBytes, bytes);
    position += bytes;
    return at;
  }

  public int stringLength(int header) {
    return SlabFormat.stringLength(output, header);
  }

  public void put32(int at, int value) {
    putInt(output, at, value);
  }

  public void put64(int at, long value) {
    putLong(output, at, value);
  }

  public int get32(int at) {
    return getInt(output, at);
  }

  public void case16(int at, int ordinal) {
    putShort(output, at, ordinal + 1);
  }

  public void presence(int record, int ordinal, boolean present) {
    int at = record + RECORD_PRESENCE + 4 * (ordinal >>> 5);
    int bits = getInt(output, at);
    putInt(output, at, present ? bits | (1 << ordinal) : bits & ~(1 << ordinal));
  }

  public void uncertified() {
    certified = false;
  }

  public void required(int record, int ordinal) {
    if (!SlabFormat.present(output, record, ordinal)) certified = false;
  }

  /** Primitive pending values; generated locals avoid a per-record field-state array. */
  public long[] append(long[] values, int count, long value, int width) {
    if ((long) top + pending + width > maximum) throw SlabCodec.bad("Slab byte budget exceeded");
    if (count == values.length) {
      long capacity = Math.max(4, Math.min(Integer.MAX_VALUE - 8L, 2L * count));
      if (capacity <= count) throw SlabCodec.bad("Slab list budget exceeded");
      values = Arrays.copyOf(values, (int) capacity);
    }
    values[count] = value;
    pending += width;
    return values;
  }

  /** Finalize lists in declaration order, exactly as the native/table producers do. */
  public int list(long[] values, int count, int width) {
    pending -= (long) count * width;
    long bytes = LIST_ELEMENTS + (long) count * width;
    if (bytes > Integer.MAX_VALUE) throw SlabCodec.bad("Slab byte budget exceeded");
    int at = allocate((int) bytes, 8);
    putInt(output, at + LIST_COUNT, count);
    for (int i = 0, cursor = at + LIST_ELEMENTS; i < count; i++, cursor += width) {
      if (width == 1) output[cursor] = (byte) values[i];
      else if (width == 4) putInt(output, cursor, (int) values[i]);
      else putLong(output, cursor, values[i]);
    }
    return at;
  }

  public byte[] finish() {
    long aligned = ((long) top + 7) & -8L;
    if (aligned > maximum) throw SlabCodec.bad("Slab byte budget exceeded");
    int bytes = (int) aligned;
    byte[] result = output.length == bytes ? output : Arrays.copyOf(output, bytes);
    putInt(result, HEADER_MAGIC, MAGIC);
    putInt(result, HEADER_FLAGS, OWNING | (certified ? CERTIFIED : 0));
    putInt(result, HEADER_BYTES_USED, bytes);
    putInt(result, HEADER_RECORDS, records);
    putInt(result, HEADER_HANDLES, handles);
    putInt(result, HEADER_ROOT, ROOT);
    putInt(result, HEADER_LAYOUT, fingerprint);
    return result;
  }
}
