package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.*;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Executable model of docs/SLAB.md: a deliberately plain owning-slab producer (no performance work)
 * and a generic slab reader that compares a slab with protobuf-java-equivalent {@link ProtoRow}
 * semantics. The Rust and Java producers (Tasks A2/A3) must match its bytes.
 */
final class SlabReference {
  private final Layout layout;
  private final int maxDepth;
  private byte[] out = new byte[256];
  private int top = HEADER_BYTES;
  private int handles;
  private int records;
  private boolean certified = true;

  /** (record, unknown span) pairs, in close order; sorted by record into the unknown table. */
  private final List<int[]> unknowns = new ArrayList<>();

  private SlabReference(Layout layout, int maxDepth) {
    this.layout = layout;
    this.maxDepth = maxDepth;
  }

  /** Decodes {@code wire} into an owning slab of {@code layout} (root type 0). */
  static byte[] decode(Layout layout, byte[] wire) {
    return decode(layout, wire, layout.plan(0).maxDepth());
  }

  static byte[] decode(Layout layout, byte[] wire, int maxDepth) {
    var producer = new SlabReference(layout, maxDepth);
    var root = producer.record(0, new Reader(wire), wire.length, 0, 0);
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
    int bytes = align8(producer.top);
    byte[] slab = Arrays.copyOf(producer.out, bytes);
    putInt(slab, HEADER_MAGIC, MAGIC);
    putInt(slab, HEADER_FLAGS, OWNING | (producer.certified ? CERTIFIED : 0));
    putInt(slab, HEADER_BYTES_USED, bytes);
    putInt(slab, HEADER_RECORDS, producer.records);
    putInt(slab, HEADER_HANDLES, producer.handles);
    putInt(slab, HEADER_ROOT, ROOT);
    putInt(slab, HEADER_LAYOUT, layout.fingerprint());
    putInt(slab, HEADER_UNKNOWNS, table);
    return slab;
  }

  private int alloc(int size, int alignment) {
    int at = alignment == 8 ? align8(top) : align4(top);
    long end = (long) at + size;
    if (end > Integer.MAX_VALUE - 8) throw bad("Slab budget exceeded");
    if (end > out.length) out = Arrays.copyOf(out, (int) Math.max(end, 2L * out.length));
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
    final List<List<Long>> pending;
    final List<List<byte[]>> merges;
    final ByteArrayOutputStream unknown = new ByteArrayOutputStream();
    boolean invalidMapEnum;
    int bodyEnd;

    Open(int type, int at, ProtoPlan plan) {
      this.type = type;
      this.at = at;
      this.plan = plan;
      pending = new ArrayList<>(Collections.nCopies(plan.size(), null));
      merges = new ArrayList<>(Collections.nCopies(plan.size(), null));
    }
  }

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
        int slot = r.at + layout.slot(r.type, i);
        if (layout.kind(r.type, i).width == 8) putLong(out, slot, 0);
        else putInt(out, slot, 0);
        r.merges.set(i, null);
      }
    putShort(out, r.at + layout.caseOffset(r.type, oneof), ordinal + 1);
  }

  private Open record(int type, Reader r, int end, int group, int depth) {
    if (depth > maxDepth) throw bad("Protobuf nesting budget exceeded");
    ProtoPlan plan = layout.plan(type);
    var open = new Open(type, alloc(layout.recordBytes(type), 8), plan);
    records++;
    putInt(out, open.at + RECORD_HANDLE, handles++);
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
      int ordinal = plan.ordinal(number);
      var field = ordinal < 0 ? null : plan.field(ordinal);
      boolean packed =
          field != null && field.repeated() && field.kind().packable() && wireType == 2;
      if (field == null || (!packed && wireType != field.kind().wire)) {
        r.skip(tag, end, depth, maxDepth);
        open.unknown.write(r.in, start, r.pos - start);
        continue;
      }
      var enumeration = field.enumType();
      if (packed) {
        int packedLength = r.length(end);
        int packedEnd = r.pos + packedLength;
        while (r.pos < packedEnd) {
          long bits = scalar(r, field.kind(), packedEnd);
          if (enumeration != null && !enumeration.accepts((int) bits)) {
            writeVarint(open.unknown, (long) number << 3);
            writeVarint(open.unknown, (int) bits);
          } else pending(open, ordinal).add(bits);
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
        if (field.repeated()) {
          Open nested = record(child, r, childEnd, isGroup ? number : 0, depth + 1);
          if (field.map() && nested.invalidMapEnum) open.unknown.write(r.in, start, r.pos - start);
          else pending(open, ordinal).add((long) nested.at);
          continue;
        }
        select(open, ordinal);
        int slot = open.at + layout.slot(type, ordinal);
        byte[] payload;
        if (getInt(out, slot) == 0) {
          Open nested = record(child, r, childEnd, isGroup ? number : 0, depth + 1);
          putInt(out, slot, nested.at);
          setPresence(open, ordinal, true);
          payload = Arrays.copyOfRange(r.in, bodyStart, isGroup ? nested.bodyEnd : childEnd);
          open.merges.set(ordinal, new ArrayList<>());
        } else {
          // A later occurrence is validated by a full decode walk that writes nothing to this
          // slab (same errors as decoding it), and kept for the merged decode at close.
          Open walked =
              new SlabReference(layout, maxDepth)
                  .record(child, r, childEnd, isGroup ? number : 0, depth + 1);
          payload = Arrays.copyOfRange(r.in, bodyStart, isGroup ? walked.bodyEnd : childEnd);
        }
        Objects.requireNonNull(open.merges.get(ordinal)).add(payload);
        continue;
      }
      long bits = scalar(r, field.kind(), end);
      if (plan.definition().mapEntry() && ordinal == 1 && enumeration != null
          && !enumeration.accepts((int) bits))
        open.invalidMapEnum = true;
      if (enumeration != null && !enumeration.accepts((int) bits)) {
        open.unknown.write(r.in, start, r.pos - start);
        continue;
      }
      if (field.repeated()) {
        pending(open, ordinal).add(bits);
        continue;
      }
      select(open, ordinal);
      int slot = open.at + layout.slot(type, ordinal);
      if (layout.kind(type, ordinal).width == 8) putLong(out, slot, bits);
      else putInt(out, slot, (int) bits);
      setPresence(
          open,
          ordinal,
          field.explicitPresence() || ProtoRow.nonDefault(field, value(field.kind(), bits)));
    }
    if (group != 0 && !closed) throw bad("Unterminated group");
    if (group == 0) open.bodyEnd = end;
    close(open, depth);
    return open;
  }

  private static List<Long> pending(Open r, int ordinal) {
    List<Long> list = r.pending.get(ordinal);
    if (list == null) r.pending.set(ordinal, list = new ArrayList<>());
    return list;
  }

  private void close(Open r, int depth) {
    // 1. Singular messages that occurred more than once: one decode of the concatenation.
    for (int ordinal = 0; ordinal < r.plan.size(); ordinal++) {
      var payloads = r.merges.get(ordinal);
      // One occurrence was decoded in place; the list holds every payload since the last clear.
      if (payloads == null || payloads.size() < 2) continue;
      var joined = new ByteArrayOutputStream();
      for (byte[] payload : payloads) joined.writeBytes(payload);
      byte[] bytes = joined.toByteArray();
      Open merged =
          record(layout.childType(r.type, ordinal), new Reader(bytes), bytes.length, 0, depth + 1);
      putInt(out, r.at + layout.slot(r.type, ordinal), merged.at);
      certified = false;
    }
    // 2. A map entry without its message value gets an empty value record.
    if (r.plan.definition().mapEntry()
        && r.plan.field(1).kind().message()
        && getInt(out, r.at + layout.slot(r.type, 1)) == 0) {
      if (depth >= maxDepth) throw bad("Protobuf nesting budget exceeded");
      Open empty = record(layout.childType(r.type, 1), new Reader(new byte[0]), 0, 0, depth + 1);
      putInt(out, r.at + layout.slot(r.type, 1), empty.at);
      setPresence(r, 1, true);
    }
    // 3. Lists in ordinal order; maps keep first-key order with the last entry per key.
    for (int ordinal = 0; ordinal < r.plan.size(); ordinal++) {
      List<Long> items = r.pending.get(ordinal);
      if (items == null || items.isEmpty()) continue;
      var kind = layout.kind(r.type, ordinal);
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
      putInt(out, r.at + layout.slot(r.type, ordinal), list);
      setPresence(r, ordinal, true);
    }
    // 4. Unknown fields, in wire order.
    if (r.unknown.size() > 0) {
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
    int slot = record + layout.slot(type, 0);
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
    int lengthWord = count, utf16 = 0;
    if (text) {
      boolean ascii = true;
      for (int i = 0; i < count; i++) ascii &= in[at + i] >= 0;
      if (ascii) lengthWord |= ASCII;
      utf16 = utf16(in, at, count);
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

  static int utf16(byte[] in, int at, int count) {
    try {
      CharBuffer chars =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(in, at, count));
      return chars.remaining();
    } catch (CharacterCodingException invalid) {
      throw bad("Invalid UTF-8");
    }
  }

  private static void writeVarint(ByteArrayOutputStream out, long value) {
    while ((value & ~127L) != 0) {
      out.write((int) (value & 127) | 128);
      value >>>= 7;
    }
    out.write((int) value);
  }

  /** The ProtoRow value of non-message slot bits (strings read back from the slab). */
  private Object value(ProtoPlan.Kind kind, long bits) {
    return switch (kind) {
      case STRING, BYTES -> decodeString(out, (int) bits, kind == ProtoPlan.Kind.STRING);
      default -> scalarValue(kind, bits);
    };
  }

  static Object scalarValue(ProtoPlan.Kind kind, long bits) {
    return switch (kind) {
      case INT32, SINT32, SFIXED32, ENUM -> (long) (int) bits;
      case UINT32, FIXED32 -> bits & 0xffffffffL;
      case INT64, SINT64, SFIXED64 -> bits;
      case UINT64, FIXED64 ->
          bits >= 0
              ? Values.normalize(BigInteger.valueOf(bits))
              : Values.normalize(BigInteger.valueOf(bits & Long.MAX_VALUE).setBit(63));
      case FLOAT -> (double) Float.intBitsToFloat((int) bits);
      case DOUBLE -> Double.longBitsToDouble(bits);
      case BOOL -> bits != 0;
      default -> throw new AssertionError(kind);
    };
  }

  static Object decodeString(byte[] slab, int header, boolean text) {
    int length = stringLength(slab, header);
    int payload = payload(slab, header);
    if (!text) return DataArray.copyOfBytes(slab, payload, length);
    return new String(slab, payload, length, StandardCharsets.UTF_8);
  }

  // ---- Generic reader: compare a slab record with ProtoRow semantics. ----

  /** Asserts that the slab's root equals {@code expected} field by field, including unknowns. */
  static void assertMatches(Layout layout, byte[] slab, ProtoRow expected) {
    SlabFormat.check(slab, layout);
    int table = getInt(slab, HEADER_UNKNOWNS);
    if (table != 0) {
      assertEquals(0, table % 8, "unknown table alignment");
      int count = getInt(slab, table + UNKNOWN_TABLE_COUNT);
      assertTrue(count > 0, "empty unknown table");
      assertEquals(0, getInt(slab, table + 4));
      for (int i = 1; i < count; i++)
        assertTrue(
            getInt(slab, table + UNKNOWN_TABLE_ENTRIES + UNKNOWN_TABLE_ENTRY_BYTES * (i - 1))
                < getInt(slab, table + UNKNOWN_TABLE_ENTRIES + UNKNOWN_TABLE_ENTRY_BYTES * i),
            "unknown table order");
    }
    assertEquals(0, getInt(slab, ROOT + RECORD_HANDLE), "root handle");
    assertRecord(layout, slab, 0, ROOT, expected, "$");
  }

  static void assertRecord(
      Layout layout, byte[] slab, int type, int record, ProtoAccess row, String path) {
    ProtoPlan plan = layout.plan(type);
    assertEquals(plan.name(), row.plan().name(), path);
    for (int ordinal = 0; ordinal < plan.size(); ordinal++) {
      var field = plan.field(ordinal);
      String at = path + "." + field.name();
      boolean present = SlabFormat.present(slab, record, ordinal);
      assertEquals(row.has(ordinal), present, at + " presence");
      int oneof = layout.oneofIndex(type, ordinal);
      if (oneof >= 0 && present)
        assertEquals(ordinal, oneofCase(slab, record, layout, type, oneof), at + " case");
      if (!present) continue;
      int slot = record + layout.slot(type, ordinal);
      var kind = layout.kind(type, ordinal);
      Object value = row.get(ordinal);
      if (!kind.list()) {
        assertValue(layout, slab, type, ordinal, slot, value, at);
        continue;
      }
      int list = getInt(slab, slot);
      DataArray items = (DataArray) value;
      assertEquals(items.size(), listCount(slab, list), at + " count");
      for (int i = 0; i < items.size(); i++) {
        int element = listElement(list, kind.element, i);
        if (kind.element == 1) {
          assertEquals(items.get(i), slab[element] != 0, at + "[" + i + "]");
          continue;
        }
        long bits = kind.element == 8 ? getLong(slab, element) : getInt(slab, element);
        Object item = items.get(i);
        switch (kind) {
          case LIST_STRING, LIST_BYTES ->
              assertStringValue(slab, (int) bits, field.kind() == ProtoPlan.Kind.STRING, item, at);
          case LIST_MESSAGE, MAP ->
              assertRecord(
                  layout,
                  slab,
                  layout.childType(type, ordinal),
                  (int) bits,
                  (ProtoAccess) item,
                  at + "[" + i + "]");
          default -> assertValueEquals(item, scalarValue(field.kind(), bits), at + "[" + i + "]");
        }
      }
    }
    byte[] unknown = row instanceof ProtoRow owned ? owned.unknown() : null;
    int span = SlabFormat.unknown(slab, record);
    if (unknown == null || unknown.length == 0) assertEquals(0, span, path + " unknown");
    else {
      assertNotEquals(0, span, path + " unknown");
      assertArrayEquals(
          unknown,
          Arrays.copyOfRange(
              slab, span + UNKNOWN_BYTES, span + UNKNOWN_BYTES + unknownLength(slab, span)),
          path + " unknown");
    }
  }

  private static void assertValue(
      Layout layout, byte[] slab, int type, int ordinal, int slot, Object value, String at) {
    var field = layout.plan(type).field(ordinal);
    switch (layout.kind(type, ordinal)) {
      case MESSAGE ->
          assertRecord(
              layout,
              slab,
              layout.childType(type, ordinal),
              getInt(slab, slot),
              (ProtoAccess) value,
              at);
      case STRING, BYTES ->
          assertStringValue(
              slab, getInt(slab, slot), field.kind() == ProtoPlan.Kind.STRING, value, at);
      case I64, F64 -> assertValueEquals(value, scalarValue(field.kind(), getLong(slab, slot)), at);
      default -> {
        int bits = getInt(slab, slot);
        if (layout.kind(type, ordinal) == Layout.SlotKind.BOOL)
          assertTrue(bits == 0 || bits == 1, at + " bool encoding");
        assertValueEquals(value, scalarValue(field.kind(), bits), at);
      }
    }
  }

  /** Value equality with ExactNumber compared by magnitude (it has identity equality). */
  private static void assertValueEquals(Object expected, Object actual, String at) {
    if (expected instanceof ExactNumber x && actual instanceof ExactNumber y)
      assertEquals(0, x.decimal().compareTo(y.decimal()), at);
    else assertEquals(expected, actual, at);
  }

  private static void assertStringValue(
      byte[] slab, int header, boolean text, Object expected, String at) {
    assertNotEquals(0, header, at);
    Object actual = decodeString(slab, header, text);
    if (text) {
      String string = (String) actual;
      assertEquals(expected, string, at);
      boolean ascii = string.chars().allMatch(c -> c < 128);
      assertEquals(ascii, SlabFormat.ascii(slab, header), at + " ascii");
      assertEquals(string.length(), utf16Length(slab, header), at + " utf16");
    } else {
      assertFalse(SlabFormat.ascii(slab, header), at + " bytes ascii bit");
      assertEquals(0, getInt(slab, header + STRING_UTF16), at + " bytes utf16");
      DataArray bytes = (DataArray) expected, copy = (DataArray) actual;
      assertEquals(bytes.size(), copy.size(), at);
      for (int i = 0; i < bytes.size(); i++) assertEquals(bytes.get(i), copy.get(i), at);
    }
  }
}
