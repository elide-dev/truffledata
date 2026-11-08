package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import dev.elide.truffledata.ContractViolation;
import org.jspecify.annotations.Nullable;

/** Structural checks at the external slab boundary, before offsets or handle ids are exposed. */
final class SlabVerifier {
  private final byte[] bytes;
  private final Layout layout;
  private final int handles;
  private final int extent;
  private final int maxDepth;
  private final @Nullable Projection projection;
  private final byte[] input;
  private final int flags;
  private final long[] usedHandles;
  private int soughtType = -1;
  private int soughtRecord;
  private boolean foundRecord;
  private boolean expandedRecord;

  private SlabVerifier(byte[] bytes, int extent, Layout layout, SlabLimits limits) {
    this(bytes, extent, layout, limits, null, new byte[0]);
  }

  private SlabVerifier(
      byte[] bytes,
      int extent,
      Layout layout,
      SlabLimits limits,
      @Nullable Projection projection,
      byte[] input) {
    this.projection = projection;
    this.input = input;
    this.bytes = bytes;
    this.extent = extent;
    this.layout = layout;
    maxDepth = Math.min(layout.plan(0).maxDepth(), limits.maxDepth() + 1);
    flags = check(bytes, extent, layout);
    if (extent > limits.maxSlabBytes()) throw bad();
    if (projection == null) {
      if ((flags & ~(OWNING | CERTIFIED)) != 0 || (flags & OWNING) == 0) throw bad();
    } else {
      projection.requireLayout(layout);
      if ((flags & PROJECTED) == 0
          || (flags & ~(OWNING | BORROWING | LAX | CERTIFIED | PROJECTED)) != 0
          || ((flags & LAX) != 0) != projection.laxValidation()
          || ((flags & BORROWING) != 0 && !projection.requiresBorrow())) throw bad();
    }
    handles = getInt(bytes, HEADER_HANDLES);
    if (handles < 1
        || handles > extent / 4
        || getInt(bytes, HEADER_RECORDS) < 1
        || getInt(bytes, HEADER_RECORDS) > handles) throw bad();
    // IDs are dense producer indices, bounded above by the slab extent before allocating.
    usedHandles = new long[handles];
  }

  static void verify(byte[] bytes, Layout layout, SlabLimits limits) {
    verify(bytes, bytes.length, layout, limits);
  }

  static void verify(byte[] bytes, int extent, Layout layout, SlabLimits limits) {
    try {
      var verifier = new SlabVerifier(bytes, extent, layout, limits);
      verifier.unknowns();
      verifier.record(0, ROOT, 1, false);
    } catch (IndexOutOfBoundsException failure) {
      throw bad();
    }
  }

  static void verifyRecord(byte[] bytes, Layout layout, SlabLimits limits, int type, int record) {
    try {
      var verifier = new SlabVerifier(bytes, bytes.length, layout, limits);
      verifier.soughtType = type;
      verifier.soughtRecord = record;
      verifier.unknowns();
      verifier.record(0, ROOT, 1, false);
      if (type < 0 || !verifier.foundRecord) throw bad();
    } catch (IndexOutOfBoundsException failure) {
      throw bad();
    }
  }

  static void verify(
      byte[] bytes,
      int extent,
      Layout layout,
      SlabLimits limits,
      Projection projection,
      byte[] input) {
    try {
      var verifier = new SlabVerifier(bytes, extent, layout, limits, projection, input);
      verifier.unknowns();
      verifier.record(0, ROOT, 1, false);
    } catch (IndexOutOfBoundsException failure) {
      throw bad();
    }
  }

  static boolean verifyRecord(
      byte[] bytes,
      Layout layout,
      SlabLimits limits,
      Projection projection,
      byte[] input,
      int type,
      int record) {
    try {
      var verifier = new SlabVerifier(bytes, bytes.length, layout, limits, projection, input);
      verifier.soughtType = type;
      verifier.soughtRecord = record;
      verifier.unknowns();
      verifier.record(0, ROOT, 1, false);
      if (type < 0 || !verifier.foundRecord) throw bad();
      return verifier.expandedRecord;
    } catch (IndexOutOfBoundsException failure) {
      throw bad();
    }
  }

  private static ContractViolation bad() {
    return new ContractViolation("Invalid slab structure");
  }

  private void span(int at, long size, int alignment) {
    if (at < ROOT || (at & (alignment - 1)) != 0 || size < 0 || size > extent - (long) at)
      throw bad();
  }

  private void handle(int id, int at, int kind) {
    if (id < 0 || id >= handles) throw bad();
    long identity = ((long) kind << 32) | Integer.toUnsignedLong(at);
    long previous = usedHandles[id];
    if (previous != 0 && previous != identity) throw bad();
    usedHandles[id] = identity;
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

  private void record(int type, int record, int depth, boolean expanded) {
    if (depth > maxDepth) throw bad();
    span(record, layout.recordBytes(type), 8);
    handle(SlabFormat.handle(bytes, record), record, type);
    if (type == soughtType && record == soughtRecord) {
      foundRecord = true;
      expandedRecord = expanded;
    }
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
        int marker = getInt(bytes, list + 4);
        if (count < 0 || (marker != 0 && marker != COUNTS_ONLY)) throw bad();
        if (marker == COUNTS_ONLY) {
          if (projection == null || expanded || !projection.countsOnly(type, ordinal)) throw bad();
          continue;
        }
        span(list, 8L + (long) count * width, 8);
        for (int i = 0; i < count; i++)
          value(type, ordinal, listElement(list, width, i), depth, expanded);
      } else value(type, ordinal, slot, depth, expanded);
    }
  }

  private void inputSpan(int offset, int length) {
    if (offset < 0 || length < 0 || offset > input.length - length) throw bad();
  }

  private void value(int type, int ordinal, int at, int depth, boolean expanded) {
    boolean omitted = projection != null && !expanded && !projection.wants(type, ordinal);
    switch (layout.plan(type).field(ordinal).kind()) {
      case MESSAGE, GROUP -> {
        int child = getInt(bytes, at);
        if (child == 0
            && omitted
            && projection != null
            && !projection.requiresEagerChild(type, ordinal)
            && !projection.lazyChildrenEnabled()) return;
        if (child < 0) {
          if (!omitted
              || projection == null
              || !projection.lazyChildrenEnabled()
              || projection.requiresEagerChild(type, ordinal)
              || (flags & BORROWING) == 0
              || depth >= maxDepth
              || layout.plan(type).field(ordinal).kind() != ProtoPlan.Kind.MESSAGE) throw bad();
          int header = child & ~LAZY_CHILD;
          span(header, LAZY_HEADER_BYTES, 8);
          inputSpan(getInt(bytes, header), getInt(bytes, header + 4));
          if (getInt(bytes, header + 12) != depth) throw bad();
          handle(getInt(bytes, header + 8), header, -3 - layout.childType(type, ordinal));
          return;
        }
        record(
            layout.childType(type, ordinal),
            child,
            depth + 1,
            expanded || (omitted && projection != null && projection.lazyChildrenEnabled()));
      }
      case STRING, BYTES -> {
        int header = getInt(bytes, at);
        if (header == 0 && omitted) return;
        span(header, ASCII_HEADER_BYTES, 4);
        int length = stringLength(bytes, header);
        boolean string = layout.plan(type).field(ordinal).kind() == ProtoPlan.Kind.STRING;
        if (external(bytes, header)) {
          if (string
              || projection == null
              || projection.borrowBytesThreshold() == 0
              || length < projection.borrowBytesThreshold()
              || (flags & BORROWING) == 0) throw bad();
          span(header, 16, 4);
          inputSpan(getInt(bytes, header + STRING_HEADER_BYTES), length);
        } else span(header, (long) headerBytes(bytes, header) + length, 4);
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
