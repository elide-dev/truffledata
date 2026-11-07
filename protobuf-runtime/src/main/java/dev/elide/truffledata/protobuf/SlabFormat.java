package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.memory.ByteArraySupport;
import dev.elide.truffledata.ContractViolation;

/**
 * Constants and little-endian accessors of the {@code TDSLAB1} slab format ({@code docs/SLAB.md}).
 * Accessors are bounds-checked loads over a {@code byte[]}; they never allocate.
 */
public final class SlabFormat {
  private SlabFormat() {}

  private static final ByteArraySupport LE = ByteArraySupport.littleEndian();

  /** {@code 'TDS1'} as a little-endian word. */
  public static final int MAGIC = 0x31534454;

  public static final int HEADER_BYTES = 32;

  // Header word offsets.
  public static final int HEADER_MAGIC = 0;
  public static final int HEADER_FLAGS = 4;
  public static final int HEADER_BYTES_USED = 8;
  public static final int HEADER_RECORDS = 12;
  public static final int HEADER_HANDLES = 16;
  public static final int HEADER_ROOT = 20;
  public static final int HEADER_LAYOUT = 24;
  /** Offset of the unknown table (records with unknown fields), 0 when no record has any. */
  public static final int HEADER_UNKNOWNS = 28;

  // Header flags.
  /** No string or bytes header references input bytes. */
  public static final int OWNING = 1;

  /** At least one string or bytes header references input bytes (an input lease is retained). */
  public static final int BORROWING = 2;

  /** Strings in projected-away subtrees were not validated (Phase E). */
  public static final int LAX = 4;

  /** Every record's required fields are present and no singular message was merged. */
  public static final int CERTIFIED = 8;

  /** The root record always follows the header. */
  public static final int ROOT = HEADER_BYTES;

  // Records.
  public static final int RECORD_HANDLE = Layout.HANDLE_OFFSET;
  public static final int RECORD_PRESENCE = Layout.PRESENCE_OFFSET;

  // String and bytes headers: length · handle · utf16 (only when ASCII is clear) · payload.
  public static final int STRING_LENGTH = 0;
  public static final int STRING_HANDLE = 4;

  /** UTF-16 units of a non-ASCII string (0 for bytes); absent from ASCII headers. */
  public static final int STRING_UTF16 = 8;

  /** Header bytes before the payload (or input offset) of an ASCII string. */
  public static final int ASCII_HEADER_BYTES = 8;

  /** Header bytes before the payload (or input offset) of a non-ASCII string or of bytes. */
  public static final int STRING_HEADER_BYTES = 12;

  /** Length-word bit: every byte is below 0x80 (strings only; never set for bytes). */
  public static final int ASCII = 0x8000_0000;

  /** Length-word bit: the payload is in the input at the {@code u32} offset after the handle. */
  public static final int EXTERNAL = 0x4000_0000;

  public static final int LENGTH_MASK = 0x3FFF_FFFF;

  // Lists.
  public static final int LIST_COUNT = 0;
  public static final int LIST_ELEMENTS = 8;

  // Unknown spans.
  public static final int UNKNOWN_LENGTH = 0;
  public static final int UNKNOWN_BYTES = 4;

  // Unknown table: count · 0 · count × (record offset, span offset), ascending by record.
  public static final int UNKNOWN_TABLE_COUNT = 0;
  public static final int UNKNOWN_TABLE_ENTRIES = 8;
  public static final int UNKNOWN_TABLE_ENTRY_BYTES = 8;

  public static int getInt(byte[] slab, int offset) {
    return LE.getInt(slab, offset);
  }

  public static long getLong(byte[] slab, int offset) {
    return LE.getLong(slab, offset);
  }

  public static int getUnsignedShort(byte[] slab, int offset) {
    return LE.getShort(slab, offset) & 0xFFFF;
  }

  public static int getUnsignedByte(byte[] slab, int offset) {
    return LE.getByte(slab, offset) & 0xFF;
  }

  public static void putInt(byte[] slab, int offset, int value) {
    LE.putInt(slab, offset, value);
  }

  public static void putLong(byte[] slab, int offset, long value) {
    LE.putLong(slab, offset, value);
  }

  public static void putShort(byte[] slab, int offset, int value) {
    LE.putShort(slab, offset, (short) value);
  }

  public static int align4(int offset) {
    return (offset + 3) & ~3;
  }

  public static int align8(int offset) {
    return (offset + 7) & ~7;
  }

  /** Checks magic, length, root and layout fingerprint; returns the flags. */
  public static int check(byte[] slab, Layout layout) {
    if (slab.length < HEADER_BYTES
        || getInt(slab, HEADER_MAGIC) != MAGIC
        || getInt(slab, HEADER_BYTES_USED) != slab.length
        || getInt(slab, HEADER_ROOT) != ROOT
        || getInt(slab, HEADER_LAYOUT) != layout.fingerprint()
        || slab.length < ROOT + layout.recordBytes(0))
      throw new ContractViolation("Not a slab of this layout");
    int flags = getInt(slab, HEADER_FLAGS);
    if (Integer.bitCount(flags & (OWNING | BORROWING)) != 1)
      throw new ContractViolation("Slab ownership flags");
    return flags;
  }

  /** Presence bit {@code ordinal} of the record at {@code record}. */
  public static boolean present(byte[] slab, int record, int ordinal) {
    return (getInt(slab, record + RECORD_PRESENCE + 4 * (ordinal >>> 5)) & (1 << ordinal)) != 0;
  }

  public static int handle(byte[] slab, int record) {
    return getInt(slab, record + RECORD_HANDLE);
  }

  /** Offset of the record's unknown span, or 0: a binary search of the slab's unknown table. */
  public static int unknown(byte[] slab, int record) {
    int table = getInt(slab, HEADER_UNKNOWNS);
    if (table == 0) return 0;
    int low = 0, high = getInt(slab, table + UNKNOWN_TABLE_COUNT) - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      int entry = table + UNKNOWN_TABLE_ENTRIES + UNKNOWN_TABLE_ENTRY_BYTES * middle;
      int at = getInt(slab, entry);
      if (at == record) return getInt(slab, entry + 4);
      if (Integer.compareUnsigned(at, record) < 0) low = middle + 1;
      else high = middle - 1;
    }
    return 0;
  }

  /** Ordinal of the set member of a oneof, or -1. */
  public static int oneofCase(byte[] slab, int record, Layout layout, int type, int oneof) {
    return getUnsignedShort(slab, record + layout.caseOffset(type, oneof)) - 1;
  }

  /** Byte length of a string or bytes payload. */
  public static int stringLength(byte[] slab, int header) {
    return getInt(slab, header + STRING_LENGTH) & LENGTH_MASK;
  }

  public static boolean ascii(byte[] slab, int header) {
    return (getInt(slab, header + STRING_LENGTH) & ASCII) != 0;
  }

  public static boolean external(byte[] slab, int header) {
    return (getInt(slab, header + STRING_LENGTH) & EXTERNAL) != 0;
  }

  /** UTF-16 units of a validated string without materializing it. */
  public static int utf16Length(byte[] slab, int header) {
    return ascii(slab, header) ? stringLength(slab, header) : getInt(slab, header + STRING_UTF16);
  }

  public static int stringHandle(byte[] slab, int header) {
    return getInt(slab, header + STRING_HANDLE);
  }

  /** Header bytes before the payload: 8 for ASCII strings, 12 otherwise. */
  public static int headerBytes(byte[] slab, int header) {
    return ascii(slab, header) ? ASCII_HEADER_BYTES : STRING_HEADER_BYTES;
  }

  /** Slab offset of an owned payload (not external). */
  public static int payload(byte[] slab, int header) {
    if (external(slab, header)) throw new ContractViolation("External slab string");
    return header + headerBytes(slab, header);
  }

  /** Input offset of an external payload. */
  public static int inputOffset(byte[] slab, int header) {
    if (!external(slab, header)) throw new ContractViolation("Owned slab string");
    return getInt(slab, header + headerBytes(slab, header));
  }

  public static int listCount(byte[] slab, int list) {
    return getInt(slab, list + LIST_COUNT);
  }

  /** Offset of element {@code index} of a list whose elements are {@code width} bytes. */
  public static int listElement(int list, int width, int index) {
    return list + LIST_ELEMENTS + width * index;
  }

  public static int unknownLength(byte[] slab, int span) {
    return getInt(slab, span + UNKNOWN_LENGTH);
  }
}
