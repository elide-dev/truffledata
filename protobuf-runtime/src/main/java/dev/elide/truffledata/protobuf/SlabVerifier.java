package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import dev.elide.truffledata.ContractViolation;
import java.util.HashMap;
import java.util.Map;

/** Structural checks at the external slab boundary, before offsets or handle ids are exposed. */
final class SlabVerifier {
  private final byte[] bytes;
  private final Layout layout;
  private final int handles;
  private final int extent;
  private final int maxDepth;
  private final Map<Integer, Long> usedHandles = new HashMap<>();

  private SlabVerifier(byte[] bytes, int extent, Layout layout, SlabLimits limits) {
    this.bytes = bytes;
    this.extent = extent;
    this.layout = layout;
    maxDepth = Math.min(layout.plan(0).maxDepth(), limits.maxDepth() + 1);
    int flags = check(bytes, extent, layout);
    handles = getInt(bytes, HEADER_HANDLES);
    if ((flags & (OWNING | BORROWING | LAX)) != OWNING
        || handles < 1
        || handles > extent / 4
        || getInt(bytes, HEADER_RECORDS) < 1
        || getInt(bytes, HEADER_RECORDS) > handles) throw bad();
  }

  static void verify(byte[] bytes, Layout layout, SlabLimits limits) {
    verify(bytes, bytes.length, layout, limits);
  }

  static void verify(byte[] bytes, int extent, Layout layout, SlabLimits limits) {
    try {
      var verifier = new SlabVerifier(bytes, extent, layout, limits);
      verifier.unknowns();
      verifier.record(0, ROOT, 1);
    } catch (IndexOutOfBoundsException failure) {
      throw bad();
    }
  }

  static void verifyRecord(byte[] bytes, Layout layout, SlabLimits limits, int type, int record) {
    try {
      var verifier = new SlabVerifier(bytes, bytes.length, layout, limits);
      verifier.unknowns();
      verifier.record(0, ROOT, 1);
      long identity = ((long) type << 32) | Integer.toUnsignedLong(record);
      if (type < 0 || !verifier.usedHandles.containsValue(identity)) throw bad();
    } catch (IndexOutOfBoundsException failure) {
      throw bad();
    }
  }

  private static ContractViolation bad() {
    return new ContractViolation("Invalid owning slab structure");
  }

  private void span(int at, long size, int alignment) {
    if (at < ROOT || (at & (alignment - 1)) != 0 || size < 0 || size > extent - (long) at)
      throw bad();
  }

  private void handle(int id, int at, int kind) {
    if (id < 0 || id >= handles) throw bad();
    long identity = ((long) kind << 32) | Integer.toUnsignedLong(at);
    Long previous = usedHandles.putIfAbsent(id, identity);
    if (previous != null && previous != identity) throw bad();
  }

  private void unknowns() {
    int table = getInt(bytes, HEADER_UNKNOWNS);
    if (table == 0) return;
    span(table, 8, 8);
    int count = getInt(bytes, table);
    if (count < 0) throw bad();
    span(table, 8L + 8L * count, 8);
    int previous = 0;
    for (int i = 0; i < count; i++) {
      int record = getInt(bytes, table + 8 + i * 8);
      if (record <= previous) throw bad();
      span(record, 4, 8);
      int unknown = getInt(bytes, table + 12 + i * 8);
      span(unknown, 4, 4);
      int length = unknownLength(bytes, unknown);
      if (length < 0) throw bad();
      span(unknown, 4L + length, 4);
      previous = record;
    }
  }

  private void record(int type, int record, int depth) {
    if (depth > maxDepth) throw bad();
    span(record, layout.recordBytes(type), 8);
    handle(SlabFormat.handle(bytes, record), record, type);
    var plan = layout.plan(type);
    for (int ordinal = 0; ordinal < plan.size(); ordinal++) {
      if (!present(bytes, record, ordinal)) continue;
      int oneof = layout.oneofIndex(type, ordinal);
      if (oneof >= 0 && oneofCase(bytes, record, layout, type, oneof) != ordinal) throw bad();
      int slot = record + layout.slot(type, ordinal);
      if (plan.field(ordinal).repeated()) {
        int list = getInt(bytes, slot);
        span(list, 8, 8);
        int count = listCount(bytes, list), width = layout.kind(type, ordinal).element;
        if (count < 0) throw bad();
        span(list, 8L + (long) count * width, 8);
        for (int i = 0; i < count; i++) value(type, ordinal, listElement(list, width, i), depth);
      } else value(type, ordinal, slot, depth);
    }
  }

  private void value(int type, int ordinal, int at, int depth) {
    switch (layout.plan(type).field(ordinal).kind()) {
      case MESSAGE, GROUP -> record(layout.childType(type, ordinal), getInt(bytes, at), depth + 1);
      case STRING, BYTES -> {
        int header = getInt(bytes, at);
        span(header, ASCII_HEADER_BYTES, 4);
        if (external(bytes, header)) throw bad();
        int length = stringLength(bytes, header);
        span(header, (long) headerBytes(bytes, header) + length, 4);
        boolean string = layout.plan(type).field(ordinal).kind() == ProtoPlan.Kind.STRING;
        handle(stringHandle(bytes, header), header, string ? -1 : -2);
        if (string) {
          int units = SlabCodec.utf16(bytes, payload(bytes, header), length);
          if (units != utf16Length(bytes, header)) throw bad();
        } else if (ascii(bytes, header) || getInt(bytes, header + STRING_UTF16) != 0) throw bad();
      }
      default -> {}
    }
  }
}
