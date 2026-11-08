package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * Bounded, native-free producer of TDSLAB1 slabs. Wire order determines record and string handles;
 * lists and merged records are finalized in field order, matching the native producer. The ordinary
 * result owns all payload bytes. Explicit projections may emit input-relative references, which
 * require the matching immutable input window when the slab is consumed.
 */
public final class SlabCodec {
  private final Layout layout;
  private final int maxDepth;
  private final int maxBytes;
  private final FieldLookup lookup;
  private byte[] out;
  private int top = HEADER_BYTES;
  private int handles;
  private int records;
  private long pendingBytes;
  private boolean certified = true;
  private @Nullable Projection projection;
  private byte @Nullable [] input;
  private int inputOffset;
  private boolean external;

  /** (record, unknown span) pairs, in close order; sorted by record into the unknown table. */
  private final List<int[]> unknowns = new ArrayList<>();

  private SlabCodec(Layout layout, SlabLimits limits, FieldLookup lookup) {
    this.layout = layout;
    this.maxDepth = Math.min(limits.maxDepth(), layout.plan(0).maxDepth() - 1);
    this.maxBytes = limits.maxSlabBytes();
    this.lookup = lookup;
    out = new byte[Math.min(256, maxBytes)];
  }

  /** Generated schemas replace number lookup with a switch; runtime schemas use the plan. */
  @FunctionalInterface
  public interface FieldLookup {
    int ordinal(int type, int number);

    /** Generated layouts supply literal slot offsets; -1 selects the runtime layout table. */
    default int slot(int type, int ordinal) {
      return -1;
    }
  }

  /** Decode the complete input under its plan's wire and nesting limits. */
  public static byte[] decode(Layout layout, byte[] wire) {
    return decode(layout, wire, 0, wire.length, SlabLimits.forInput(layout, wire.length));
  }

  public static byte[] decode(
      Layout layout, byte[] wire, int offset, int length, SlabLimits limits) {
    return decode(
        layout, wire, offset, length, limits, (type, number) -> layout.plan(type).ordinal(number));
  }

  /** Entry for generated decoders whose lookup is bound to this exact layout. */
  public static byte[] decode(
      Layout layout, byte[] wire, int offset, int length, SlabLimits limits, FieldLookup lookup) {
    return decode(layout, wire, offset, length, limits, lookup, null);
  }

  /**
   * Decode a selected view. External payload offsets are relative to the supplied input window;
   * callers must retain that window unchanged while using a slab whose flags include BORROWING.
   */
  public static byte[] decode(
      Layout layout,
      byte[] wire,
      int offset,
      int length,
      SlabLimits limits,
      Projection projection) {
    Objects.requireNonNull(projection).requireLayout(layout);
    return decode(
        layout,
        wire,
        offset,
        length,
        limits,
        (type, number) -> layout.plan(type).ordinal(number),
        projection);
  }

  private static byte[] decode(
      Layout layout,
      byte[] wire,
      int offset,
      int length,
      SlabLimits limits,
      FieldLookup lookup,
      @Nullable Projection projection) {
    Objects.requireNonNull(layout);
    Objects.requireNonNull(limits);
    Objects.requireNonNull(lookup);
    Objects.checkFromIndexSize(offset, length, wire.length);
    if (length > layout.plan(0).maxWireBytes()) throw bad("Wire byte budget exceeded");
    var producer = new SlabCodec(layout, limits, lookup);
    producer.projection = projection;
    producer.input = wire;
    producer.inputOffset = offset;
    var input = new Reader(wire);
    input.pos = offset;
    var root = producer.record(0, input, offset + length, 0, 0);
    if (root.at != ROOT) throw new AssertionError("root placement");
    int table = 0;
    if (!producer.unknowns.isEmpty()) {
      // The unknown table is the last allocation: ascending record offsets (= handle order).
      producer.unknowns.sort(Comparator.comparingInt(pair -> pair[0]));
      int count = producer.unknowns.size();
      table = producer.alloc(UNKNOWN_TABLE_ENTRIES + UNKNOWN_TABLE_ENTRY_BYTES * count, 8);
      putInt(producer.out, table + UNKNOWN_TABLE_COUNT, count);
      for (int i = 0; i < count; i++) {
        int entry = table + UNKNOWN_TABLE_ENTRIES + UNKNOWN_TABLE_ENTRY_BYTES * i;
        putInt(producer.out, entry, producer.unknowns.get(i)[0]);
        putInt(producer.out, entry + 4, producer.unknowns.get(i)[1]);
      }
    }
    int bytes = producer.aligned(producer.top, 8);
    if (bytes > producer.maxBytes) throw bad("Slab byte budget exceeded");
    byte[] slab = Arrays.copyOf(producer.out, bytes);
    putInt(slab, HEADER_MAGIC, MAGIC);
    putInt(
        slab,
        HEADER_FLAGS,
        (producer.external ? BORROWING : OWNING)
            | (producer.certified ? CERTIFIED : 0)
            | (projection != null ? PROJECTED : 0)
            | (projection != null && projection.laxValidation() ? LAX : 0));
    putInt(slab, HEADER_BYTES_USED, bytes);
    putInt(slab, HEADER_RECORDS, producer.records);
    putInt(slab, HEADER_HANDLES, producer.handles);
    putInt(slab, HEADER_ROOT, ROOT);
    putInt(slab, HEADER_LAYOUT, layout.fingerprint());
    putInt(slab, HEADER_UNKNOWNS, table);
    return slab;
  }

  private int slot(int type, int ordinal) {
    int constant = lookup.slot(type, ordinal);
    return constant < 0 ? layout.slot(type, ordinal) : constant;
  }

  private int aligned(int value, int alignment) {
    long result = ((long) value + alignment - 1) & -alignment;
    if (result > maxBytes) throw bad("Slab byte budget exceeded");
    return (int) result;
  }

  private int alloc(int size, int alignment) {
    int at = aligned(top, alignment);
    long end = (long) at + size;
    if (size < 0 || end > maxBytes) throw bad("Slab byte budget exceeded");
    if (end > out.length)
      out = Arrays.copyOf(out, (int) Math.min(maxBytes, Math.max(end, 2L * out.length)));
    top = (int) end;
    return at;
  }

  static ContractViolation bad(String message) {
    return new ContractViolation(message);
  }

  static final class Reader {
    final byte[] in;
    int pos;

    Reader(byte[] in) {
      this.in = in;
    }

    long varint(int end) {
      long value = 0;
      for (int shift = 0; shift < 64; shift += 7) {
        if (pos >= end) throw bad("Truncated varint");
        int b = in[pos++] & 255;
        if (shift == 63 && (b & 254) != 0) throw bad("Varint overflow");
        value |= (long) (b & 127) << shift;
        if ((b & 128) == 0) return value;
      }
      throw bad("Malformed varint");
    }

    int length(int end) {
      long length = varint(end);
      if (length < 0 || length > end - pos) throw bad("Truncated or oversized field");
      return (int) length;
    }

    long fixed(int width, int end) {
      if (end - pos < width) throw bad("Truncated fixed-width field");
      long value = 0;
      for (int i = 0; i < width; i++) value |= (long) (in[pos++] & 255) << (i * 8);
      return value;
    }

    int tag(int end) {
      long tag = varint(end);
      if (tag < 0 || tag > 0xffffffffL || (tag >>> 3) == 0 || (tag & 7) > 5)
        throw bad("Invalid wire tag");
      return (int) tag;
    }

    void skip(int tag, int end, int depth, int maxDepth) {
      switch (tag & 7) {
        case 0 -> varint(end);
        case 1 -> fixed(8, end);
        case 2 -> {
          int length = length(end);
          pos += length;
        }
        case 3 -> {
          if (depth >= maxDepth) throw bad("Unknown group nesting budget exceeded");
          while (true) {
            if (pos >= end) throw bad("Unterminated unknown group");
            int child = tag(end);
            if ((child & 7) == 4) {
              if ((child >>> 3) != (tag >>> 3)) throw bad("Mismatched group");
              return;
            }
            skip(child, end, depth + 1, maxDepth);
          }
        }
        case 5 -> fixed(4, end);
        default -> throw bad("Unexpected end group");
      }
    }
  }

  /** Per-record decode state; everything here is written to the slab when the record closes. */
  private static final class Open {
    final int type;
    final int at;
    final ProtoPlan plan;
    @Nullable Pending @Nullable [] pending;
    @Nullable List<@Nullable List<Payload>> merges;
    @Nullable ByteArrayOutputStream unknown;
    boolean invalidMapEnum;
    int bodyEnd;
    final boolean full;

    Open(int type, int at, ProtoPlan plan, boolean full) {
      this.type = type;
      this.at = at;
      this.plan = plan;
      this.full = full;
    }
  }

  private record Payload(byte[] input, int start, int end) {}

  private void setPresence(Open r, int ordinal, boolean present) {
    int word = r.at + RECORD_PRESENCE + 4 * (ordinal >>> 5);
    int bits = getInt(out, word);
    putInt(out, word, present ? bits | (1 << ordinal) : bits & ~(1 << ordinal));
  }

  private boolean present(Open r, int ordinal) {
    return SlabFormat.present(out, r.at, ordinal);
  }

  /** Oneof last-wins: clear every other member's bit and slot, record this member's case. */
  private void select(Open r, int ordinal) {
    int oneof = layout.oneofIndex(r.type, ordinal);
    if (oneof < 0) return;
    for (int i = 0; i < r.plan.size(); i++)
      if (i != ordinal && layout.oneofIndex(r.type, i) == oneof) {
        setPresence(r, i, false);
        int slot = r.at + slot(r.type, i);
        if (layout.kind(r.type, i).width == 8) putLong(out, slot, 0);
        else putInt(out, slot, 0);
        if (r.merges != null) r.merges.set(i, null);
      }
    putShort(out, r.at + layout.caseOffset(r.type, oneof), ordinal + 1);
  }

  private Open record(int type, Reader r, int end, int group, int depth) {
    return record(type, r, end, group, depth, false);
  }

  private Open record(int type, Reader r, int end, int group, int depth, boolean full) {
    Open open = open(type, depth, full);
    body(open, r, end, group, depth);
    close(open, depth);
    return open;
  }

  private Open open(int type, int depth, boolean full) {
    if (depth > maxDepth) throw bad("Protobuf nesting budget exceeded");
    ProtoPlan plan = layout.plan(type);
    var open = new Open(type, alloc(layout.recordBytes(type), 8), plan, full);
    records++;
    putInt(out, open.at + RECORD_HANDLE, handles++);
    return open;
  }

  private void body(Open open, Reader r, int end, int group, int depth) {
    int type = open.type;
    ProtoPlan plan = open.plan;
    boolean closed = false;
    while (r.pos < end) {
      int start = r.pos;
      int tag = r.tag(end), number = tag >>> 3, wireType = tag & 7;
      if (wireType == 4) {
        if (group != number) throw bad("Unexpected end group");
        closed = true;
        open.bodyEnd = start;
        break;
      }
      int ordinal = lookup.ordinal(type, number);
      var field = ordinal < 0 ? null : plan.field(ordinal);
      boolean packed =
          field != null && field.repeated() && field.kind().packable() && wireType == 2;
      if (field == null || (!packed && wireType != field.kind().wire)) {
        r.skip(tag, end, depth, maxDepth);
        unknown(open).write(r.in, start, r.pos - start);
        continue;
      }
      var enumeration = field.enumType();
      boolean omitted = omitted(open, ordinal);
      // Match the native producer's fast-field selection. Oneofs, maps, groups, repeated
      // strings, and children with required descendants retain their eager representation.
      if (omitted
          && field.kind() == ProtoPlan.Kind.MESSAGE
          && !field.map()
          && layout.oneofIndex(type, ordinal) < 0
          && !Objects.requireNonNull(projection).requiresEagerChild(type, ordinal)) {
        int length = r.length(end), startBody = r.pos, childEnd = r.pos + length;
        walk(
            layout.childType(type, ordinal), r, childEnd, 0, depth + 1, projection.laxValidation());
        int slot = open.at + slot(type, ordinal);
        if (projection.lazyChildrenEnabled()) {
          if (field.repeated())
            pending(open, ordinal).append(lazyChild(startBody, length, depth + 1));
          else {
            if (getInt(out, slot) == 0) putInt(out, slot, lazyChild(startBody, length, depth + 1));
            var payloads = merges(open).get(ordinal);
            if (payloads == null) merges(open).set(ordinal, payloads = new ArrayList<>());
            payloads.add(new Payload(r.in, startBody, childEnd));
          }
        } else if (field.repeated()) {
          int count = getInt(out, slot);
          if (count == Integer.MAX_VALUE) throw bad("Slab list budget exceeded");
          putInt(out, slot, count + 1);
        }
        setPresence(open, ordinal, true);
        continue;
      }
      if (omitted
          && !field.repeated()
          && layout.oneofIndex(type, ordinal) < 0
          && (field.kind() == ProtoPlan.Kind.STRING || field.kind() == ProtoPlan.Kind.BYTES)) {
        int length = r.length(end);
        if (field.kind() == ProtoPlan.Kind.STRING
            && !Objects.requireNonNull(projection).laxValidation()) utf16(r.in, r.pos, length);
        r.pos += length;
        setPresence(open, ordinal, field.explicitPresence() || length != 0);
        continue;
      }
      if (packed) {
        int packedLength = r.length(end);
        int packedEnd = r.pos + packedLength;
        while (r.pos < packedEnd) {
          long bits = scalar(r, field.kind(), packedEnd);
          if (enumeration != null && !enumeration.accepts((int) bits)) {
            writeVarint(unknown(open), (long) number << 3);
            writeVarint(unknown(open), (int) bits);
          } else pending(open, ordinal).append(bits);
        }
        continue;
      }
      if (field.kind().message()) {
        if (depth >= maxDepth) throw bad("Protobuf nesting budget exceeded");
        int child = layout.childType(type, ordinal);
        boolean isGroup = field.kind() == ProtoPlan.Kind.GROUP;
        int childLength = isGroup ? 0 : r.length(end);
        int childEnd = isGroup ? end : r.pos + childLength;
        int bodyStart = r.pos;
        boolean full =
            open.full || (omitted && Objects.requireNonNull(projection).lazyChildrenEnabled());
        if (field.repeated()) {
          Open nested = record(child, r, childEnd, isGroup ? number : 0, depth + 1, full);
          if (field.map() && nested.invalidMapEnum) unknown(open).write(r.in, start, r.pos - start);
          else pending(open, ordinal).append((long) nested.at);
          continue;
        }
        select(open, ordinal);
        int slot = open.at + slot(type, ordinal);
        Payload payload;
        if (getInt(out, slot) == 0) {
          Open nested = record(child, r, childEnd, isGroup ? number : 0, depth + 1, full);
          putInt(out, slot, nested.at);
          setPresence(open, ordinal, true);
          payload = new Payload(r.in, bodyStart, isGroup ? nested.bodyEnd : childEnd);
          merges(open).set(ordinal, new ArrayList<>());
        } else {
          // A later occurrence is validated by a full decode walk that writes nothing to this
          // slab (same errors as decoding it), and kept for the merged decode at close.
          Open walked =
              new SlabCodec(layout, new SlabLimits(maxDepth, maxBytes), lookup)
                  .record(child, r, childEnd, isGroup ? number : 0, depth + 1);
          payload = new Payload(r.in, bodyStart, isGroup ? walked.bodyEnd : childEnd);
        }
        Objects.requireNonNull(merges(open).get(ordinal)).add(payload);
        continue;
      }
      long bits = scalar(r, field.kind(), end);
      if (plan.definition().mapEntry() && ordinal == 1 && enumeration != null)
        open.invalidMapEnum = !enumeration.accepts((int) bits);
      if (enumeration != null && !enumeration.accepts((int) bits)) {
        unknown(open).write(r.in, start, r.pos - start);
        continue;
      }
      if (field.repeated()) {
        pending(open, ordinal).append(bits);
        continue;
      }
      select(open, ordinal);
      int slot = open.at + slot(type, ordinal);
      if (layout.kind(type, ordinal).width == 8) putLong(out, slot, bits);
      else putInt(out, slot, (int) bits);
      setPresence(open, ordinal, field.explicitPresence() || nonDefault(field.kind(), bits));
    }
    if (group != 0 && !closed) throw bad("Unterminated group");
    if (group == 0) open.bodyEnd = end;
  }

  private boolean omitted(Open open, int ordinal) {
    return projection != null
        && !open.full
        && !open.plan.definition().mapEntry()
        && !projection.wants(open.type, ordinal);
  }

  private boolean countsOnly(Open open, int ordinal) {
    var field = open.plan.field(ordinal);
    return omitted(open, ordinal)
        && !Objects.requireNonNull(projection).lazyChildrenEnabled()
        && field.kind() == ProtoPlan.Kind.MESSAGE
        && field.repeated()
        && !field.map()
        && layout.oneofIndex(open.type, ordinal) < 0
        && !projection.requiresEagerChild(open.type, ordinal);
  }

  private int lazyChild(int start, int length, int depth) {
    int at = alloc(16, 8);
    putInt(out, at, start - inputOffset);
    putInt(out, at + 4, length);
    putInt(out, at + 8, handles++);
    putInt(out, at + 12, depth);
    external = true;
    return at | LAZY_CHILD;
  }

  /** Wire-only validation for omitted children: no slab records, payload copies, or lists. */
  private void walk(int type, Reader r, int end, int group, int depth, boolean lax) {
    if (depth > maxDepth) throw bad("Protobuf nesting budget exceeded");
    var plan = layout.plan(type);
    boolean closed = false, mapValue = false;
    while (r.pos < end) {
      int tag = r.tag(end), number = tag >>> 3, wire = tag & 7;
      if (wire == 4) {
        if (group != number) throw bad("Unexpected end group");
        closed = true;
        break;
      }
      int ordinal = lookup.ordinal(type, number);
      var field = ordinal < 0 ? null : plan.field(ordinal);
      boolean packed = field != null && field.repeated() && field.kind().packable() && wire == 2;
      if (field == null || (!packed && field.kind().wire != wire)) {
        r.skip(tag, end, depth, maxDepth);
        continue;
      }
      if (packed) {
        int length = r.length(end), packedEnd = r.pos + length;
        while (r.pos < packedEnd) scalar(r, field.kind(), packedEnd);
      } else if (field.kind().message()) {
        if (field.kind() == ProtoPlan.Kind.GROUP)
          walk(layout.childType(type, ordinal), r, end, number, depth + 1, lax);
        else {
          int length = r.length(end);
          walk(layout.childType(type, ordinal), r, r.pos + length, 0, depth + 1, lax);
        }
        if (ordinal == 1) mapValue = true;
      } else if (field.kind() == ProtoPlan.Kind.STRING || field.kind() == ProtoPlan.Kind.BYTES) {
        int length = r.length(end);
        if (!lax && field.kind() == ProtoPlan.Kind.STRING) utf16(r.in, r.pos, length);
        r.pos += length;
      } else scalar(r, field.kind(), end);
    }
    if (group != 0 && !closed) throw bad("Unterminated group");
    if (plan.definition().mapEntry()
        && plan.field(1).kind().message()
        && !mapValue
        && depth >= maxDepth) throw bad("Protobuf nesting budget exceeded");
  }

  private ByteArrayOutputStream unknown(Open record) {
    if (record.unknown == null) record.unknown = new BoundedOutput(maxBytes);
    return record.unknown;
  }

  private List<@Nullable List<Payload>> merges(Open record) {
    if (record.merges == null)
      record.merges = new ArrayList<>(Collections.nCopies(record.plan.size(), null));
    return record.merges;
  }

  private Pending pending(Open record, int ordinal) {
    if (record.pending == null) record.pending = new Pending[record.plan.size()];
    Pending list = record.pending[ordinal];
    if (list == null) {
      var kind = layout.kind(record.type, ordinal);
      record.pending[ordinal] = list = new Pending(kind == Layout.SlotKind.MAP ? 0 : kind.element);
    }
    return list;
  }

  /** Primitive scratch: one stored word per pending value, no boxed object per occurrence. */
  private final class Pending extends AbstractList<Long> {
    private final int width;
    private long[] values = new long[4];
    private int size;

    Pending(int width) {
      this.width = width;
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public Long get(int index) {
      return values[Objects.checkIndex(index, size)];
    }

    @Override
    public boolean add(Long value) {
      append(value);
      return true;
    }

    void append(long value) {
      if ((long) top + pendingBytes + width > maxBytes) throw bad("Slab byte budget exceeded");
      if (size == values.length) {
        long next = Math.min(Integer.MAX_VALUE - 8L, 2L * size);
        if (next <= size) throw bad("Slab list budget exceeded");
        values = Arrays.copyOf(values, (int) next);
      }
      values[size++] = value;
      pendingBytes += width;
    }
  }

  private void close(Open r, int depth) {
    // 1. Singular messages that occurred more than once: one decode of the concatenation.
    for (int ordinal = 0; ordinal < r.plan.size(); ordinal++) {
      var payloads = r.merges == null ? null : r.merges.get(ordinal);
      // One occurrence was decoded in place; the list holds every payload since the last clear.
      if (payloads == null || payloads.size() < 2) continue;
      boolean full =
          r.full
              || (omitted(r, ordinal) && Objects.requireNonNull(projection).lazyChildrenEnabled());
      Open merged = open(layout.childType(r.type, ordinal), depth + 1, full);
      for (Payload payload : payloads) {
        Reader input = new Reader(payload.input);
        input.pos = payload.start;
        body(merged, input, payload.end, 0, depth + 1);
      }
      close(merged, depth + 1);
      putInt(out, r.at + slot(r.type, ordinal), merged.at);
      certified = false;
    }
    // 2. A map entry without its message value gets an empty value record.
    if (r.plan.definition().mapEntry()
        && r.plan.field(1).kind().message()
        && getInt(out, r.at + slot(r.type, 1)) == 0) {
      if (depth >= maxDepth) throw bad("Protobuf nesting budget exceeded");
      Open empty =
          record(layout.childType(r.type, 1), new Reader(new byte[0]), 0, 0, depth + 1, r.full);
      putInt(out, r.at + slot(r.type, 1), empty.at);
      setPresence(r, 1, true);
    }
    // 3. Lists in ordinal order; maps keep first-key order with the last entry per key.
    for (int ordinal = 0; ordinal < r.plan.size(); ordinal++) {
      if (countsOnly(r, ordinal)) {
        int slot = r.at + slot(r.type, ordinal), count = getInt(out, slot);
        if (count != 0) {
          int list = alloc(LIST_ELEMENTS, 8);
          putInt(out, list + LIST_COUNT, count);
          putInt(out, list + 4, COUNTS_ONLY);
          putInt(out, slot, list);
        }
        continue;
      }
      List<Long> items = r.pending == null ? null : r.pending[ordinal];
      if (items == null || items.isEmpty()) continue;
      var kind = layout.kind(r.type, ordinal);
      pendingBytes -= (long) items.size() * (kind == Layout.SlotKind.MAP ? 0 : kind.element);
      if (kind == Layout.SlotKind.MAP) items = dedup(layout.childType(r.type, ordinal), items);
      int list = alloc(LIST_ELEMENTS + kind.element * items.size(), 8);
      putInt(out, list + LIST_COUNT, items.size());
      for (int i = 0; i < items.size(); i++) {
        int at = listElement(list, kind.element, i);
        long bits = items.get(i);
        switch (kind.element) {
          case 1 -> out[at] = (byte) bits;
          case 4 -> putInt(out, at, (int) bits);
          default -> putLong(out, at, bits);
        }
      }
      putInt(out, r.at + slot(r.type, ordinal), list);
      setPresence(r, ordinal, true);
    }
    // 4. Unknown fields, in wire order.
    if (r.unknown != null && r.unknown.size() > 0) {
      byte[] bytes = r.unknown.toByteArray();
      int span = alloc(UNKNOWN_BYTES + bytes.length, 4);
      putInt(out, span + UNKNOWN_LENGTH, bytes.length);
      System.arraycopy(bytes, 0, out, span + UNKNOWN_BYTES, bytes.length);
      unknowns.add(new int[] {r.at, span});
    }
    for (int ordinal = 0; ordinal < r.plan.size(); ordinal++)
      if (r.plan.field(ordinal).required() && !present(r, ordinal)) certified = false;
  }

  /** Map entries keyed by the entry's field 0 (absent: the default key). */
  private List<Long> dedup(int entryType, List<Long> entries) {
    Map<Object, Integer> positions = new LinkedHashMap<>();
    List<Long> result = new ArrayList<>();
    for (long entry : entries) {
      Object key = mapKey(entryType, (int) entry);
      Integer at = positions.get(key);
      if (at == null) {
        positions.put(key, result.size());
        result.add(entry);
      } else result.set(at, entry);
    }
    return result;
  }

  private Object mapKey(int type, int record) {
    if (!SlabFormat.present(out, record, 0)) {
      // Implicit or absent keys are the default; strings compare by value.
      return layout.kind(type, 0) == Layout.SlotKind.STRING ? "" : 0L;
    }
    int slot = record + slot(type, 0);
    return switch (layout.kind(type, 0)) {
      case STRING -> {
        int header = getInt(out, slot);
        yield new String(
            out, payload(out, header), stringLength(out, header), StandardCharsets.UTF_8);
      }
      case I64 -> getLong(out, slot);
      default -> (long) getInt(out, slot);
    };
  }

  /** Reads one value as the raw slot bits; strings and bytes allocate their header here. */
  private long scalar(Reader r, ProtoPlan.Kind kind, int end) {
    return switch (kind) {
      case INT32, ENUM, UINT32 -> (int) r.varint(end);
      case INT64, UINT64 -> r.varint(end);
      case SINT32 -> {
        int n = (int) r.varint(end);
        yield (n >>> 1) ^ -(n & 1);
      }
      case SINT64 -> {
        long n = r.varint(end);
        yield (n >>> 1) ^ -(n & 1);
      }
      case FIXED32, SFIXED32, FLOAT -> (int) r.fixed(4, end);
      case FIXED64, SFIXED64, DOUBLE -> r.fixed(8, end);
      case BOOL -> r.varint(end) != 0 ? 1 : 0;
      case STRING, BYTES -> {
        int count = r.length(end);
        int header = string(r.in, r.pos, count, kind == ProtoPlan.Kind.STRING);
        r.pos += count;
        yield header;
      }
      default -> throw bad("Message must use structured decoder");
    };
  }

  private int string(byte[] in, int at, int count, boolean text) {
    if (count > LENGTH_MASK) throw bad("Slab payload length exceeded");
    if (!text
        && projection != null
        && projection.borrowBytesThreshold() > 0
        && count >= projection.borrowBytesThreshold()
        && in == input) {
      int header = alloc(16, 4);
      putInt(out, header, count | EXTERNAL);
      putInt(out, header + 4, handles++);
      putInt(out, header + 8, 0);
      putInt(out, header + 12, at - inputOffset);
      external = true;
      return header;
    }
    int lengthWord = count, utf16 = 0;
    if (text) {
      utf16 = utf16(in, at, count);
      if (utf16 == count) lengthWord |= ASCII;
    }
    // ASCII strings omit the utf16 word (it equals the length); bytes keep it as 0.
    int headerBytes = (lengthWord & ASCII) != 0 ? ASCII_HEADER_BYTES : STRING_HEADER_BYTES;
    int header = alloc(headerBytes + count, 4);
    putInt(out, header + STRING_LENGTH, lengthWord);
    putInt(out, header + STRING_HANDLE, handles++);
    if (headerBytes == STRING_HEADER_BYTES) putInt(out, header + STRING_UTF16, utf16);
    System.arraycopy(in, at, out, header + headerBytes, count);
    return header;
  }

  @TruffleBoundary
  private static ContractViolation invalidUtf8(int offset) {
    return bad("Invalid UTF-8 at byte " + offset);
  }

  /** Strict UTF-8 validation and UTF-16 sizing, with an eight-byte ASCII fast path. */
  static int utf16(byte[] in, int at, int count) {
    int end = at + count, units = 0;
    while (at < end) {
      if (end - at >= 8 && (getLong(in, at) & 0x8080808080808080L) == 0) {
        at += 8;
        units += 8;
        continue;
      }
      int start = at, first = in[at++] & 255;
      if (first < 128) {
        units++;
        continue;
      }
      int extra =
          first >= 0xC2 && first <= 0xDF
              ? 1
              : first >= 0xE0 && first <= 0xEF ? 2 : first >= 0xF0 && first <= 0xF4 ? 3 : -1;
      if (extra < 0 || end - at < extra) throw invalidUtf8(start);
      int second = in[at] & 255;
      if ((first == 0xE0 && second < 0xA0)
          || (first == 0xED && second >= 0xA0)
          || (first == 0xF0 && second < 0x90)
          || (first == 0xF4 && second >= 0x90)) throw invalidUtf8(start);
      for (int i = 0; i < extra; i++) if ((in[at++] & 0xC0) != 0x80) throw invalidUtf8(start);
      units += extra == 3 ? 2 : 1;
    }
    return units;
  }

  private boolean nonDefault(ProtoPlan.Kind kind, long bits) {
    return switch (kind) {
      case STRING, BYTES -> stringLength(out, (int) bits) != 0;
      // Preserve negative zero as protobuf-java does (compare raw bits).
      default -> bits != 0;
    };
  }

  private static void writeVarint(ByteArrayOutputStream out, long value) {
    while ((value & ~127L) != 0) {
      out.write((int) (value & 127) | 128);
      value >>>= 7;
    }
    out.write((int) value);
  }
}
