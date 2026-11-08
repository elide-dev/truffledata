package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Accessor;
import dev.elide.truffledata.ContractViolation;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.zip.CRC32;
import org.jspecify.annotations.Nullable;

/**
 * Fixed slab layout of a closed set of protobuf types (format {@code TDSLAB1}, {@code
 * docs/SLAB.md}). Every type gets a record with a handle word, {@code u32} presence words, oneof
 * case entries, 8-byte slots and then 4-byte slots, so a field read is a constant offset from a
 * record base. Type 0 is the root; message slots name their child type by index. Immutable.
 */
public final class Layout {
  /** Cache identity follows the root closure; a child schema may occur in multiple layouts. */
  @TruffleBoundary
  synchronized Accessor accessor(int type, int ordinal) {
    var table = accessors[Objects.checkIndex(type, accessors.length)];
    Objects.checkIndex(ordinal, table.length);
    var accessor = table[ordinal];
    if (accessor == null) table[ordinal] = accessor = new SlabRow.Slot(this, type, ordinal);
    return accessor;
  }

  /** Slot kind of one field. Codes in the {@code TDL1} program are {@code ordinal() + 1}. */
  public enum SlotKind {
    I32(4, 4),
    I64(8, 8),
    F32(4, 4),
    F64(8, 8),
    BOOL(4, 1),
    STRING(4, 4),
    BYTES(4, 4),
    MESSAGE(4, 4),
    LIST_I32(4, 4),
    LIST_I64(4, 8),
    LIST_F32(4, 4),
    LIST_F64(4, 8),
    LIST_BOOL(4, 1),
    LIST_STRING(4, 4),
    LIST_BYTES(4, 4),
    LIST_MESSAGE(4, 4),
    MAP(4, 4);

    private static final SlotKind[] VALUES = values();

    /** Bytes the slot occupies in its record. */
    public final int width;

    /** Bytes per list element ({@code LIST_*}, {@code MAP}) or of the inline value otherwise. */
    public final int element;

    SlotKind(int width, int element) {
      this.width = width;
      this.element = element;
    }

    public boolean list() {
      return ordinal() >= LIST_I32.ordinal();
    }

    /** True when the slot (or each list element) is a child record offset. */
    public boolean message() {
      return this == MESSAGE || this == LIST_MESSAGE || this == MAP;
    }

    public int code() {
      return ordinal() + 1;
    }

    public static SlotKind of(int code) {
      if (code < 1 || code > VALUES.length) throw new ContractViolation("Invalid slab slot kind");
      return VALUES[code - 1];
    }

    static SlotKind of(ProtoPlan.FieldPlan field) {
      SlotKind scalar =
          switch (field.kind()) {
            case INT32, UINT32, SINT32, ENUM, FIXED32, SFIXED32 -> I32;
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> I64;
            case FLOAT -> F32;
            case DOUBLE -> F64;
            case BOOL -> BOOL;
            case STRING -> STRING;
            case BYTES -> BYTES;
            case MESSAGE, GROUP -> MESSAGE;
          };
      if (field.map()) return MAP;
      if (!field.repeated()) return scalar;
      return VALUES[scalar.ordinal() + LIST_I32.ordinal()];
    }
  }

  /** {@code 'TDL1'} as a little-endian word: the section magic and the program trailer magic. */
  public static final int PROGRAM_MAGIC = 0x314C4454;

  public static final int PROGRAM_VERSION = 1;

  /** Section header: magic, version, type count, fingerprint. */
  public static final int PROGRAM_HEADER_BYTES = 16;

  /** No oneof membership in a program field entry. */
  public static final int NO_ONEOF = -1;

  /** Byte offset of the {@code u32} handle word in every record. */
  public static final int HANDLE_OFFSET = 0;

  /** Byte offset of the first {@code u32} presence word in every record. */
  public static final int PRESENCE_OFFSET = 4;

  /** Largest ordinal a oneof member may have: case entries are {@code u16} holding ordinal + 1. */
  public static final int MAX_ONEOF_ORDINAL = 0xFFFE;

  @CompilationFinal(dimensions = 1)
  private final ProtoPlan[] plans;

  @CompilationFinal(dimensions = 1)
  private final int[] recordBytes;

  @CompilationFinal(dimensions = 1)
  private final int[] presenceWords;

  @CompilationFinal(dimensions = 1)
  private final int[] oneofs;

  @CompilationFinal(dimensions = 2)
  private final int[][] slots;

  @CompilationFinal(dimensions = 2)
  private final SlotKind[][] kinds;

  @CompilationFinal(dimensions = 2)
  private final int[][] oneofIndex;

  @CompilationFinal(dimensions = 2)
  private final int[][] childTypes;

  private final @Nullable Accessor[][] accessors;

  private final byte[] program;
  private final int fingerprint;

  private Layout(List<ProtoPlan> types) {
    if (types.isEmpty()) throw new ContractViolation("Empty slab layout");
    plans = types.toArray(ProtoPlan[]::new);
    int count = plans.length;
    accessors = new Accessor[count][];
    recordBytes = new int[count];
    presenceWords = new int[count];
    oneofs = new int[count];
    slots = new int[count][];
    kinds = new SlotKind[count][];
    oneofIndex = new int[count][];
    childTypes = new int[count][];
    Map<String, Integer> indices = new HashMap<>();
    for (int t = 0; t < count; t++)
      if (indices.putIfAbsent(plans[t].name(), t) != null)
        throw new ContractViolation("Duplicate slab layout type: " + plans[t].name());
    long programBytes = PROGRAM_HEADER_BYTES;
    for (int t = 0; t < count; t++) {
      ProtoPlan plan = plans[t];
      int fields = plan.size();
      accessors[t] = new Accessor[fields];
      int words = (fields + 31) >>> 5;
      int cases = 0;
      int[] slot = new int[fields];
      SlotKind[] kind = new SlotKind[fields];
      int[] oneof = new int[fields];
      int[] child = new int[fields];
      for (int ordinal = 0; ordinal < fields; ordinal++) {
        var field = plan.field(ordinal);
        kind[ordinal] = SlotKind.of(field);
        oneof[ordinal] = field.oneof();
        if (field.oneof() >= 0) {
          if (ordinal > MAX_ONEOF_ORDINAL)
            throw new ContractViolation("Oneof member beyond the slab case range");
          cases = Math.max(cases, field.oneof() + 1);
        }
        if (kind[ordinal].message()) {
          Integer index = indices.get(field.typeName());
          if (index == null)
            throw new ContractViolation("Slab layout misses type: " + field.typeName());
          child[ordinal] = index;
        } else child[ordinal] = -1;
      }
      // handle · u32 presence words · u16 case entries · 8-byte slots (aligned to 8) · 4-byte
      // slots.
      boolean wide = false;
      for (SlotKind k : kind) wide |= k.width == 8;
      long cursor = PRESENCE_OFFSET + 4L * words + 2L * cases;
      cursor = wide ? align8(cursor) : (cursor + 3) & ~3L;
      for (int pass = 8; pass >= 4; pass -= 4)
        for (int ordinal = 0; ordinal < fields; ordinal++)
          if (kind[ordinal].width == pass) {
            slot[ordinal] = (int) cursor;
            cursor += pass;
          }
      cursor = align8(cursor);
      if (cursor > Integer.MAX_VALUE) throw new ContractViolation("Slab record budget");
      recordBytes[t] = (int) cursor;
      presenceWords[t] = words;
      oneofs[t] = cases;
      slots[t] = slot;
      kinds[t] = kind;
      oneofIndex[t] = oneof;
      childTypes[t] = child;
      programBytes += 16 + 12L * fields;
    }
    if (programBytes > Integer.MAX_VALUE) throw new ContractViolation("Slab layout program budget");
    ByteBuffer out = ByteBuffer.allocate((int) programBytes).order(ByteOrder.LITTLE_ENDIAN);
    out.putInt(PROGRAM_MAGIC).putInt(PROGRAM_VERSION).putInt(count).putInt(0);
    for (int t = 0; t < count; t++) {
      out.putInt(recordBytes[t]).putInt(presenceWords[t]).putInt(oneofs[t]).putInt(slots[t].length);
      for (int ordinal = 0; ordinal < slots[t].length; ordinal++)
        out.putInt(slots[t][ordinal])
            .putInt(kinds[t][ordinal].code())
            .putInt(oneofIndex[t][ordinal]);
    }
    program = out.array();
    CRC32 crc = new CRC32();
    crc.update(program, PROGRAM_HEADER_BYTES, program.length - PROGRAM_HEADER_BYTES);
    fingerprint = (int) crc.getValue();
    ByteBuffer.wrap(program).order(ByteOrder.LITTLE_ENDIAN).putInt(12, fingerprint);
  }

  private static long align8(long value) {
    return (value + 7) & ~7L;
  }

  /** The root's closure in schema-program order: the root, then nested types breadth-first. */
  @TruffleBoundary
  public static Layout compile(ProtoPlan root) {
    List<ProtoPlan> types = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    types.add(root);
    seen.add(root.name());
    for (int i = 0; i < types.size(); i++) {
      ProtoPlan plan = types.get(i);
      for (int ordinal = 0; ordinal < plan.size(); ordinal++)
        if (plan.field(ordinal).kind().message()) {
          ProtoPlan nested = plan.nested(ordinal);
          if (seen.add(nested.name())) types.add(nested);
        }
    }
    return new Layout(types);
  }

  /**
   * An explicit type order (a schema program's); every nested type must be listed. Type 0 is the
   * root.
   */
  @TruffleBoundary
  public static Layout compile(List<ProtoPlan> types) {
    return new Layout(List.copyOf(types));
  }

  public int types() {
    return plans.length;
  }

  public ProtoPlan plan(int type) {
    return plans[type];
  }

  public int recordBytes(int type) {
    return recordBytes[type];
  }

  /** Number of {@code u32} presence words: {@code ceil(fields / 32)}. */
  public int presenceWords(int type) {
    return presenceWords[type];
  }

  /** Number of oneof case entries ({@code u16} each) in a record of this type. */
  public int oneofs(int type) {
    return oneofs[type];
  }

  public int fields(int type) {
    return slots[type].length;
  }

  /** Byte offset of the field's slot from the record base. */
  public int slot(int type, int ordinal) {
    return slots[type][ordinal];
  }

  public SlotKind kind(int type, int ordinal) {
    return kinds[type][ordinal];
  }

  /** Oneof index of the field, or {@link #NO_ONEOF}. */
  public int oneofIndex(int type, int ordinal) {
    return oneofIndex[type][ordinal];
  }

  /** Child type index of a message, message-list or map slot, otherwise -1. */
  public int childType(int type, int ordinal) {
    return childTypes[type][ordinal];
  }

  /**
   * Byte offset of the {@code u16} case entry of a oneof (ordinal + 1 of the set member; 0 none).
   */
  public int caseOffset(int type, int oneof) {
    if (oneof < 0 || oneof >= oneofs[type]) throw new ContractViolation("No such oneof");
    return PRESENCE_OFFSET + 4 * presenceWords[type] + 2 * oneof;
  }

  /** CRC-32 of the program's type tables; producers copy it into the slab header. */
  public int fingerprint() {
    return fingerprint;
  }

  /** The {@code TDL1} section (without the schema-program trailer). */
  public byte[] program() {
    return program.clone();
  }

  /** One type's table as written in a {@code TDL1} section. */
  public record TypeTable(
      int recordBytes,
      int presenceWords,
      int oneofs,
      int[] slots,
      SlotKind[] kinds,
      int[] oneofIndex) {
    public TypeTable {
      slots = slots.clone();
      kinds = kinds.clone();
      oneofIndex = oneofIndex.clone();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof TypeTable that
          && recordBytes == that.recordBytes
          && presenceWords == that.presenceWords
          && oneofs == that.oneofs
          && Arrays.equals(slots, that.slots)
          && Arrays.equals(kinds, that.kinds)
          && Arrays.equals(oneofIndex, that.oneofIndex);
    }

    @Override
    public int hashCode() {
      return Objects.hash(recordBytes, presenceWords, oneofs, Arrays.hashCode(slots));
    }

    @Override
    public String toString() {
      return "TypeTable["
          + recordBytes
          + ", "
          + presenceWords
          + ", "
          + oneofs
          + ", "
          + Arrays.toString(slots)
          + ", "
          + Arrays.toString(kinds)
          + ", "
          + Arrays.toString(oneofIndex)
          + "]";
    }
  }

  /** A parsed {@code TDL1} section. */
  public record Program(int fingerprint, List<TypeTable> types) {
    public Program {
      types = List.copyOf(types);
    }
  }

  /** This layout's tables, as {@link #parse} would return them from {@link #program()}. */
  public Program tables() {
    List<TypeTable> tables = new ArrayList<>(plans.length);
    for (int t = 0; t < plans.length; t++)
      tables.add(
          new TypeTable(
              recordBytes[t], presenceWords[t], oneofs[t], slots[t], kinds[t], oneofIndex[t]));
    return new Program(fingerprint, tables);
  }

  /** Parses and checks a {@code TDL1} section starting at {@code offset}. */
  @TruffleBoundary
  public static Program parse(byte[] bytes, int offset) {
    if (offset < 0 || offset > bytes.length)
      throw new ContractViolation("Malformed TDL1 layout section");
    try {
      ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      in.position(offset);
      if (in.getInt() != PROGRAM_MAGIC || in.getInt() != PROGRAM_VERSION)
        throw new ContractViolation("Not a TDL1 layout section");
      int count = in.getInt();
      int fingerprint = in.getInt();
      if (count < 1 || count > in.remaining() / 16)
        throw new ContractViolation("Malformed TDL1 layout section");
      int tables = in.position();
      List<TypeTable> types = new ArrayList<>(count);
      for (int t = 0; t < count; t++) {
        int record = in.getInt(), words = in.getInt(), cases = in.getInt(), fields = in.getInt();
        if (fields < 0 || fields > in.remaining() / 12)
          throw new ContractViolation("Malformed TDL1 layout section");
        int[] slot = new int[fields];
        SlotKind[] kind = new SlotKind[fields];
        int[] oneof = new int[fields];
        for (int ordinal = 0; ordinal < fields; ordinal++) {
          slot[ordinal] = in.getInt();
          kind[ordinal] = SlotKind.of(in.getInt());
          oneof[ordinal] = in.getInt();
        }
        types.add(new TypeTable(record, words, cases, slot, kind, oneof));
      }
      CRC32 crc = new CRC32();
      crc.update(bytes, tables, in.position() - tables);
      if ((int) crc.getValue() != fingerprint)
        throw new ContractViolation("TDL1 layout fingerprint mismatch");
      return new Program(fingerprint, types);
    } catch (java.nio.BufferUnderflowException failure) {
      throw new ContractViolation("Malformed TDL1 layout section");
    }
  }

  @Override
  public String toString() {
    return "Layout[" + plans[0].name() + ", " + plans.length + " types]";
  }
}
