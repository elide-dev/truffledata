package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataArray;
import dev.elide.truffledata.Limits;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Structural checks at the external slab boundary, before offsets or handle ids are exposed.
 *
 * <p>Owning slabs ({@link #verifyOwning}) take a static walk over primitive state that also
 * decides the logical budget and required fields, so publishing a message walks it once and
 * allocates nothing for up to {@value #BITMAP_HANDLES} handles. Projected slabs and record
 * selection keep the instance walk below.
 */
final class SlabVerifier {
  private static final byte[] NO_INPUT = new byte[0];

  /** Handle ids tracked in bits 0..61 of the walk's result; more use an identity array. */
  static final int BITMAP_HANDLES = 62;

  /** Result bit: some reachable record lacks a required field. */
  private static final long MISSING_REQUIRED = 1L << 62;

  /** Result (sign bit): a handle id recurred; only identity tracking can tell the cases apart. */
  private static final long RECURRED = Long.MIN_VALUE;

  /** Internal publication decisions; never retained by the message. */
  static final int EXACT = 1, SHARED = 2;

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

  private record Visit(int handle, int depth, boolean expanded) {}

  private @Nullable Map<Visit, Integer> completed;

  private SlabVerifier(byte[] bytes, int extent, Layout layout, SlabLimits limits) {
    this(bytes, extent, layout, limits, null, NO_INPUT);
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

  /** The instance walk of an owning slab: the reference {@link #verifyOwning} must agree with. */
  static void verify(byte[] bytes, int extent, Layout layout, SlabLimits limits) {
    try {
      var verifier = new SlabVerifier(bytes, extent, layout, limits);
      verifier.unknowns();
      verifier.walk();
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
      int match = verifier.walk();
      if (type < 0 || match == 0) throw bad();
    } catch (IndexOutOfBoundsException failure) {
      throw bad();
    }
  }

  static boolean verify(
      byte[] bytes,
      int extent,
      Layout layout,
      SlabLimits limits,
      Projection projection,
      byte[] input) {
    try {
      var verifier = new SlabVerifier(bytes, extent, layout, limits, projection, input);
      verifier.unknowns();
      verifier.walk();
      return verifier.completed != null;
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
      int match = verifier.walk();
      if (type < 0 || match == 0) throw bad();
      return match == 2;
    } catch (IndexOutOfBoundsException failure) {
      throw bad();
    }
  }

  private static ContractViolation bad() {
    return new ContractViolation("Invalid slab structure");
  }

  private static void span(int extent, int at, long size, int alignment) {
    if (at < ROOT || (at & (alignment - 1)) != 0 || size < 0 || size > extent - (long) at)
      throw bad();
  }

  private void span(int at, long size, int alignment) {
    span(extent, at, size, alignment);
  }

  /**
   * Verify an owning slab exactly as the instance walk does (header, extent, flags, unknown table,
   * every reachable offset, kind, oneof case, UTF-8 payload and handle identity) and decide its
   * logical budget and required fields in the same walk. Returns {@link #EXACT} when the caller must
   * run the exact accounting ({@code SlabMessage.validate}): a required field is missing, or the
   * budget may be exceeded; that accounting then reports the precise violation, after every
   * structural fault has already been reported here. Nothing is allocated when the slab has at
   * most {@value #BITMAP_HANDLES} handles and no record or string is reached twice. Shared records
   * add {@link #SHARED}, requesting memoized per-occurrence accounting after full structural proof.
   */
  static int verifyOwning(byte[] bytes, int extent, Layout layout, SlabLimits limits) {
    return verifyOwning(bytes, extent, layout, limits, false);
  }

  /** As above; {@code identity} forces the identity-array walk (tests compare both). */
  static int verifyOwning(
      byte[] bytes, int extent, Layout layout, SlabLimits limits, boolean identity) {
    try {
      int maxDepth = Math.min(layout.plan(0).maxDepth(), limits.maxDepth() + 1);
      int flags = check(bytes, extent, layout);
      if (extent > limits.maxSlabBytes()
          || (flags & ~(OWNING | CERTIFIED)) != 0
          || (flags & OWNING) == 0) throw bad();
      int handles = getInt(bytes, HEADER_HANDLES), records = getInt(bytes, HEADER_RECORDS);
      if (handles < 1 || handles > extent / 4 || records < 1 || records > handles) throw bad();
      unknowns(bytes, extent);
      Limits budget = layout.plan(0).limits();
      if (handles <= BITMAP_HANDLES && !identity) {
        long used = owned(bytes, extent, layout, handles, maxDepth, null, 0, ROOT, 1, 0);
        if (used >= 0)
          return (used & MISSING_REQUIRED) != 0 || !layout.withinBudget(handles, extent, budget)
              ? EXACT
              : 0;
      }
      // Identity per handle id (as the instance walk), then the exact cell and byte totals.
      long[] state = new long[handles + 2];
      long result = owned(bytes, extent, layout, handles, maxDepth, state, 0, ROOT, 1, 0);
      if (result < 0) {
        // A legal alias must not expand into an exponentially large structural walk. Verify
        // the complete graph before exact accounting can report any budget/required fault.
        var verifier = new SlabVerifier(bytes, extent, layout, limits);
        verifier.completed = new HashMap<>();
        verifier.record(0, ROOT, 1, false);
        return EXACT | SHARED;
      }
      return (result & MISSING_REQUIRED) != 0
              || state[handles] > budget.maxCells()
              || state[handles + 1] > budget.maxStringBytes()
          ? EXACT
          : 0;
    } catch (IndexOutOfBoundsException failure) {
      throw bad();
    }
  }

  private static void unknowns(byte[] bytes, int extent) {
    int table = getInt(bytes, HEADER_UNKNOWNS);
    if (table == 0) return;
    span(extent, table, 8, 8);
    int count = getInt(bytes, table);
    if (count < 0) throw bad();
    span(extent, table, 8L + 8L * count, 8);
    int previous = 0;
    for (int i = 0; i < count; i++) {
      int record = getInt(bytes, table + 8 + i * 8);
      if (record <= previous) throw bad();
      span(extent, record, 4, 8);
      int unknown = getInt(bytes, table + 12 + i * 8);
      span(extent, unknown, 4, 4);
      int length = unknownLength(bytes, unknown);
      if (length < 0) throw bad();
      span(extent, unknown, 4L + length, 4);
      previous = record;
    }
  }

  /**
   * Claim handle {@code id} for the record or string at {@code at}. Without an identity array the
   * id's bit in {@code used} is set, or {@link #RECURRED} returned if it already was; with one, a
   * recurring id must name the same offset and kind, as in {@link #handle}. Shared records request
   * the memoized structural fallback before their descendants can amplify the walk.
   */
  private static long claim(
      long @Nullable [] identities, int handles, long used, int id, int at, int kind) {
    if (id < 0 || id >= handles) throw bad();
    if (identities == null) {
      long bit = 1L << id;
      return (used & bit) != 0 ? RECURRED : used | bit;
    }
    long identity = ((long) kind << 32) | Integer.toUnsignedLong(at);
    long previous = identities[id];
    if (previous != 0 && previous != identity) throw bad();
    identities[id] = identity;
    return previous != 0 && kind >= 0 ? RECURRED : used;
  }

  /** Saturating add to an exact budget total (shared subgraphs may be counted many times). */
  private static void count(long[] state, int slot, long amount) {
    long total = state[slot] + amount;
    state[slot] = total < 0 ? Long.MAX_VALUE : total;
  }

  /**
   * The owning-slab record walk: the checks of {@link #record} with no projection, plus required
   * fields and, with an identity array ({@code state}: ids, then cells and bytes), the exact
   * accounting of {@code SlabMessage.validate}. Returns the handle bitmap or {@link #RECURRED}
   * without an identity array, and {@link #MISSING_REQUIRED} or 0 with one; shared records return
   * {@link #RECURRED} in either mode.
   */
  private static long owned(
      byte[] bytes,
      int extent,
      Layout layout,
      int handles,
      int maxDepth,
      long @Nullable [] state,
      int type,
      int record,
      int depth,
      long used) {
    if (depth > maxDepth) throw bad();
    span(extent, record, layout.recordBytes(type), 8);
    used = claim(state, handles, used, SlabFormat.handle(bytes, record), record, type);
    if (used < 0) return used;
    var plan = layout.plan(type);
    if (state != null) {
      count(state, handles, plan.size());
      int unknown = unknown(bytes, record);
      if (unknown != 0) count(state, handles + 1, unknownLength(bytes, unknown));
    }
    // Slab words are out-of-line loads on SVM (P0.1 ablation): read each presence word once per
    // 32 ordinals rather than once per ordinal. The record span above covers every word.
    int presence = 0;
    for (int ordinal = 0; ordinal < plan.size(); ordinal++) {
      if ((ordinal & 31) == 0)
        presence = getInt(bytes, record + RECORD_PRESENCE + 4 * (ordinal >>> 5));
      if ((presence & (1 << ordinal)) == 0) {
        var field = plan.field(ordinal);
        if (field.required()) used |= MISSING_REQUIRED;
        if (state != null && !field.repeated()) {
          if (field.defaultValue() instanceof String text)
            count(state, handles + 1, 2L * text.length());
          if (field.defaultValue() instanceof DataArray blob) count(state, handles, blob.size());
        }
        continue;
      }
      int oneof = layout.oneofIndex(type, ordinal);
      if (oneof >= 0 && oneofCase(bytes, record, layout, type, oneof) != ordinal) throw bad();
      int slot = record + layout.slot(type, ordinal);
      if (plan.field(ordinal).repeated()) {
        int list = getInt(bytes, slot);
        span(extent, list, 8, 8);
        int count = listCount(bytes, list), width = layout.kind(type, ordinal).element;
        // Counts-only lists exist only in projected slabs.
        if (count < 0 || getInt(bytes, list + 4) != 0) throw bad();
        span(extent, list, 8L + (long) count * width, 8);
        if (state != null) count(state, handles, count);
        for (int i = 0; i < count; i++) {
          used =
              ownedValue(
                  bytes, extent, layout, handles, maxDepth, state, type, ordinal,
                  listElement(list, width, i), depth, used);
          if (used < 0) return used;
        }
      } else {
        used =
            ownedValue(
                bytes, extent, layout, handles, maxDepth, state, type, ordinal, slot, depth, used);
        if (used < 0) return used;
      }
    }
    return used;
  }

  private static long ownedValue(
      byte[] bytes,
      int extent,
      Layout layout,
      int handles,
      int maxDepth,
      long @Nullable [] state,
      int type,
      int ordinal,
      int at,
      int depth,
      long used) {
    var kind = layout.plan(type).field(ordinal).kind();
    switch (kind) {
      case MESSAGE, GROUP -> {
        int child = getInt(bytes, at);
        // Lazy (negative) children exist only in projected borrowing slabs.
        if (child < 0) throw bad();
        return owned(
            bytes, extent, layout, handles, maxDepth, state, layout.childType(type, ordinal),
            child, depth + 1, used);
      }
      case STRING, BYTES -> {
        int header = getInt(bytes, at);
        span(extent, header, ASCII_HEADER_BYTES, 4);
        // One load of the length word decides length, ASCII and external (the SlabFormat
        // accessors would each reload it).
        int word = getInt(bytes, header + STRING_LENGTH), length = word & LENGTH_MASK;
        boolean string = kind == ProtoPlan.Kind.STRING, ascii = (word & ASCII) != 0;
        // External payloads exist only in projected borrowing slabs.
        if ((word & EXTERNAL) != 0) throw bad();
        int payload = header + (ascii ? ASCII_HEADER_BYTES : STRING_HEADER_BYTES);
        span(extent, header, (long) (payload - header) + length, 4);
        int id = getInt(bytes, header + STRING_HANDLE);
        boolean verified = state != null && id >= 0 && id < handles && state[id] != 0;
        used = claim(state, handles, used, id, header, string ? -1 : -2);
        if (used < 0) return used;
        if (string) {
          int units = ascii ? length : getInt(bytes, header + STRING_UTF16);
          // A prior claim completed UTF-8 validation or threw. Identity/span checks above
          // still run, and repeated payloads are charged on every logical occurrence.
          if (!verified && units != SlabCodec.utf16(bytes, payload, length)) throw bad();
          if (state != null) count(state, handles + 1, 2L * units);
        } else {
          if (ascii || getInt(bytes, header + STRING_UTF16) != 0) throw bad();
          if (state != null) count(state, handles, length);
        }
        return used;
      }
      default -> {
        return used;
      }
    }
  }

  private boolean handle(int id, int at, int kind) {
    if (id < 0 || id >= handles) throw bad();
    long identity = ((long) kind << 32) | Integer.toUnsignedLong(at);
    long previous = usedHandles[id];
    if (previous != 0 && previous != identity) throw bad();
    usedHandles[id] = identity;
    return previous != 0;
  }

  private void unknowns() {
    unknowns(bytes, extent);
  }

  /** Retry only shared graphs with context-sensitive memoization. Ordinary walks allocate no map. */
  private int walk() {
    int match = record(0, ROOT, 1, false);
    if (match >= 0) return match;
    completed = new HashMap<>();
    return record(0, ROOT, 1, false);
  }

  /** Result: last DFS selection match (0 absent, 1 masked, 2 expanded), or -1 to retry. */
  private int record(int type, int record, int depth, boolean expanded) {
    if (depth > maxDepth) throw bad();
    span(record, layout.recordBytes(type), 8);
    int id = SlabFormat.handle(bytes, record);
    boolean repeated = handle(id, record, type); // Identity is checked even on a cache hit.
    Visit visit = null;
    if (completed == null) {
      if (repeated) return -1;
    } else {
      // Lazy headers encode absolute depth; projection also changes structural legality.
      visit = new Visit(id, depth, expanded);
      Integer previous = completed.get(visit);
      if (previous != null) return previous;
    }
    int match = type == soughtType && record == soughtRecord ? (expanded ? 2 : 1) : 0;
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
        for (int i = 0; i < count; i++) {
          int child = value(type, ordinal, listElement(list, width, i), depth, expanded);
          if (child < 0) return child;
          if (child != 0) match = child;
        }
      } else {
        int child = value(type, ordinal, slot, depth, expanded);
        if (child < 0) return child;
        if (child != 0) match = child;
      }
    }
    if (completed != null) completed.put(visit, match);
    return match;
  }

  private void inputSpan(int offset, int length) {
    if (offset < 0 || length < 0 || offset > input.length - length) throw bad();
  }

  private int value(int type, int ordinal, int at, int depth, boolean expanded) {
    boolean omitted = projection != null && !expanded && !projection.wants(type, ordinal);
    switch (layout.plan(type).field(ordinal).kind()) {
      case MESSAGE, GROUP -> {
        int child = getInt(bytes, at);
        if (child == 0
            && omitted
            && projection != null
            && !projection.requiresEagerChild(type, ordinal)
            && !projection.lazyChildrenEnabled()) return 0;
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
          return 0;
        }
        return record(
            layout.childType(type, ordinal),
            child,
            depth + 1,
            expanded || (omitted && projection != null && projection.lazyChildrenEnabled()));
      }
      case STRING, BYTES -> {
        int header = getInt(bytes, at);
        if (header == 0 && omitted) return 0;
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
        boolean verified = handle(stringHandle(bytes, header), header, string ? -1 : -2);
        if (string) {
          // Context-dependent checks precede this identity-based payload proof.
          if (!verified
              && SlabCodec.utf16(bytes, payload(bytes, header), length) != utf16Length(bytes, header))
            throw bad();
        } else if (ascii(bytes, header) || getInt(bytes, header + STRING_UTF16) != 0) throw bad();
      }
      default -> {}
    }
    return 0;
  }
}
