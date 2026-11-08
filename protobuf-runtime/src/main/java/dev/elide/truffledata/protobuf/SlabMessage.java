package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Durable owning slab result. Closing is a no-op and snapshot preserves identity. */
public final class SlabMessage extends SlabRow implements ProtoMessage {
  private static final VarHandle HANDLE_SLOT = MethodHandles.arrayElementVarHandle(Object[].class);

  // The valid extent is the verified header word HEADER_BYTES_USED, not a field: the message
  // object stays at 48 bytes on SVM (compressed references, 8-byte header).
  final byte[] bytes;
  private final @Nullable Projected projected;

  private static final class Projected {
    final Projection selection;
    final byte[] input;
    final SlabLimits limits;
    final ProtoRow.Budget budget;
    int materializedBytes;

    Projected(Projection selection, byte[] input, SlabLimits limits) {
      this.selection = selection;
      this.input = input;
      this.limits = limits;
      budget = new ProtoRow.Budget(selection.layout().plan(0).limits());
    }
  }

  // Publishing the table is separate from publishing each lazily initialized entry.
  private volatile Object @Nullable [] handles;
  /** Lazy views: a list at its slab offset, a boxed unsigned 64-bit value at {@code ~slot}. */
  private @Nullable Map<Integer, Object> views;

  private SlabMessage(Layout layout, byte[] owned) {
    this(layout, owned, owned.length, null);
  }

  /** A verified slab; {@link SlabVerifier#EXACT} requests full logical accounting. */
  private SlabMessage(Layout layout, byte[] owned, int extent, int verification) {
    super(layout);
    projected = null;
    bytes = owned;
    if ((verification & SlabVerifier.EXACT) != 0)
      validate(
          0, ROOT, new ProtoRow.Budget(plan().limits()), 1,
          (verification & SlabVerifier.SHARED) != 0 ? new HashMap<>() : null);
  }

  /** Publish an owning slab already verified by {@link SlabVerifier#verifyOwning}. */
  static SlabMessage owning(Layout layout, byte[] verified, int extent, int verification) {
    return new SlabMessage(layout, verified, extent, verification);
  }

  private SlabMessage(Layout layout, byte[] owned, int extent, @Nullable Projected projected) {
    this(layout, owned, extent, projected, false);
  }

  private SlabMessage(
      Layout layout, byte[] owned, int extent, @Nullable Projected projected, boolean shared) {
    super(layout);
    this.projected = projected;
    bytes = owned;
    check(bytes, extent, layout);
    validate(
        0, ROOT, projected == null ? new ProtoRow.Budget(plan().limits()) : projected.budget, 1,
        shared ? new HashMap<>() : null);
  }

  @TruffleBoundary
  public static SlabMessage decode(ProtoPlan plan, byte[] input) {
    Layout layout = plan.layout();
    return new SlabMessage(
        layout,
        plan.decodeSlabBytes(input, 0, input.length, SlabLimits.forInput(layout, input.length)));
  }

  @TruffleBoundary
  public static SlabMessage decode(
      ProtoPlan plan, byte[] input, int offset, int length, SlabLimits limits) {
    Layout layout = plan.layout();
    return new SlabMessage(layout, plan.decodeSlabBytes(input, offset, length, limits));
  }

  /** Decode a selected view. Deferred payloads retain an immutable copy of this input window. */
  @TruffleBoundary
  public static SlabMessage decode(
      ProtoPlan plan,
      byte[] input,
      int offset,
      int length,
      SlabLimits limits,
      Projection projection) {
    projection.requireLayout(plan.layout());
    java.util.Objects.checkFromIndexSize(offset, length, input.length);
    if (length > plan.maxWireBytes()) throw new ContractViolation("Wire byte budget exceeded");
    byte[] source =
        projection.requiresBorrow() ? Arrays.copyOfRange(input, offset, offset + length) : input;
    int start = projection.requiresBorrow() ? 0 : offset;
    byte[] slab = plan.decodeSlabBytes(source, start, length, limits, projection);
    byte[] retained = (getInt(slab, HEADER_FLAGS) & BORROWING) != 0 ? source : new byte[0];
    // Like ordinary Java decode, the internal producer already checked wire structure and
    // bounds. The constructor still checks required fields and aggregate logical budgets.
    // External slabs enter through copyProjectedOf, which also verifies every offset/handle.
    return new SlabMessage(
        plan.layout(), slab, slab.length, new Projected(projection, retained, limits));
  }

  /** Copy an external projected slab and every input byte it may reference. */
  @TruffleBoundary
  public static SlabMessage copyProjectedOf(
      ProtoPlan plan,
      java.nio.ByteBuffer slab,
      java.nio.ByteBuffer input,
      Projection projection,
      SlabLimits limits) {
    projection.requireLayout(plan.layout());
    if (slab.remaining() > limits.maxSlabBytes())
      throw new ContractViolation("Slab byte budget exceeded");
    if (input.remaining() > plan.maxWireBytes())
      throw new ContractViolation("Wire byte budget exceeded");
    byte[] owned = new byte[slab.remaining()];
    slab.duplicate().get(owned);
    if (owned.length < HEADER_BYTES) throw new ContractViolation("Invalid projected slab");
    byte[] retained =
        (getInt(owned, HEADER_FLAGS) & BORROWING) != 0 ? new byte[input.remaining()] : new byte[0];
    if (retained.length != 0) input.duplicate().get(retained);
    boolean shared =
        SlabVerifier.verify(owned, owned.length, plan.layout(), limits, projection, retained);
    return new SlabMessage(
        plan.layout(), owned, owned.length, new Projected(projection, retained, limits), shared);
  }

  /** Copy a reachable projected record, preserving its read mask and deferred payloads. */
  @TruffleBoundary
  public static SlabRow copyProjectedRecordOf(
      ProtoPlan plan,
      java.nio.ByteBuffer slab,
      java.nio.ByteBuffer input,
      Projection projection,
      SlabLimits limits,
      int type,
      int record) {
    SlabMessage owner = copyProjectedOf(plan, slab, input, projection, limits);
    boolean expanded =
        SlabVerifier.verifyRecord(
            owner.bytes,
            owner.layout,
            limits,
            projection,
            java.util.Objects.requireNonNull(owner.projected).input,
            type,
            record);
    return type == 0 && record == ROOT ? owner : owner.row(type, record, expanded);
  }

  /** Copy an externally produced owning slab; the supplied buffer is never retained. */
  @TruffleBoundary
  public static SlabMessage copyOf(ProtoPlan plan, java.nio.ByteBuffer slab, SlabLimits limits) {
    if (slab.remaining() > limits.maxSlabBytes())
      throw new ContractViolation("Slab byte budget exceeded");
    byte[] owned = new byte[slab.remaining()];
    slab.duplicate().get(owned);
    Layout layout = plan.layout();
    return owning(
        layout, owned, owned.length, SlabVerifier.verifyOwning(owned, owned.length, layout, limits));
  }

  /**
   * Copy a reachable child record of an external owning slab. The selected type and offset must
   * occur in the verified root graph; arbitrary offsets and overwritten orphan records are
   * rejected. The returned row retains the copied slab and preserves its own unknown fields.
   */
  @TruffleBoundary
  public static SlabRow copyRecordOf(
      ProtoPlan plan, java.nio.ByteBuffer slab, SlabLimits limits, int type, int record) {
    SlabMessage owned = copyOf(plan, slab, limits);
    SlabVerifier.verifyRecord(owned.bytes, owned.layout, limits, type, record);
    return type == 0 && record == ROOT ? owned : owned.row(type, record);
  }

  /**
   * Transfer exclusive ownership of a freshly produced slab without copying. The caller must
   * discard every writable alias and must never modify or expose the array after this call. All
   * bytes retained, including spare capacity, count against {@code maxSlabBytes}. Untrusted or
   * shared arrays must use {@link #copyOf} instead.
   */
  @TruffleBoundary
  public static SlabMessage takeOwnership(
      ProtoPlan plan, byte[] slab, int extent, SlabLimits limits) {
    if (slab.length > limits.maxSlabBytes())
      throw new ContractViolation("Retained slab byte budget exceeded");
    Layout layout = plan.layout();
    // One walk verifies structure and decides the logical budget and required fields; the exact
    // accounting runs only when that walk cannot rule a violation out.
    return owning(layout, slab, extent, SlabVerifier.verifyOwning(slab, extent, layout, limits));
  }

  public SlabMessage row() {
    return this;
  }

  public boolean slabBacked() {
    return true;
  }

  public SlabMessage snapshot() {
    return this;
  }

  public int slabBytes() {
    return getInt(bytes, HEADER_BYTES_USED);
  }

  /** Retained array capacity, including unused bytes after the valid slab extent. */
  public int retainedSlabBytes() {
    return bytes.length + (projected == null ? 0 : projected.materializedBytes);
  }

  /** Immutable source capacity retained for external byte or lazy child references. */
  public int retainedInputBytes() {
    return projected == null ? 0 : projected.input.length;
  }

  public boolean projected() {
    return projected != null;
  }

  /** Logical contract budget already materialized, without allocating child or text views. */
  public record Usage(long cells, long stringBytes) {}

  @TruffleBoundary
  public synchronized Usage logicalUsage() {
    if (projected != null)
      return new Usage(projected.budget.cellCount(), projected.budget.byteCount());
    var budget = new ProtoRow.Budget(plan().limits());
    validate(0, ROOT, budget, 1, new HashMap<>());
    return new Usage(budget.cellCount(), budget.byteCount());
  }

  public byte[] slabCopy() {
    return Arrays.copyOf(bytes, slabBytes());
  }

  @Override
  public void close() {}

  /** Miss paths hold this message's monitor while allocating the table and publishing entries. */
  private Object[] cache() {
    Object[] values = handles;
    if (values == null) handles = values = new Object[getInt(bytes, HEADER_HANDLES)];
    return values;
  }

  private @Nullable Object cached(int handle) {
    Object[] values = handles;
    return values == null ? null : HANDLE_SLOT.getAcquire(values, handle);
  }

  SlabRow row(int type, int record) {
    return row(type, record, false);
  }

  SlabRow row(int type, int record, boolean expanded) {
    int handle =
        record < 0 ? getInt(bytes, (record & ~LAZY_CHILD) + 8) : handle(bytes, record);
    if (cached(handle) instanceof SlabRow row) return row;
    return createRow(type, record, expanded, handle);
  }

  @TruffleBoundary
  private synchronized SlabRow createRow(int type, int record, boolean expanded, int handle) {
    if (record < 0) return realize(type, record & ~LAZY_CHILD, handle);
    Object[] values = cache();
    if (values[handle] instanceof SlabRow row) return row;
    SlabRow row = new SlabRow(this, type, record, expanded);
    HANDLE_SLOT.setRelease(values, handle, row);
    return row;
  }

  String text(int header) {
    int handle = stringHandle(bytes, header);
    if (cached(handle) instanceof String text) return text;
    return createText(header, handle);
  }

  @TruffleBoundary
  private synchronized String createText(int header, int handle) {
    Object[] values = cache();
    if (values[handle] instanceof String text) return text;
    String text =
        new String(
            bytes,
            payload(bytes, header),
            SlabFormat.stringLength(bytes, header),
            StandardCharsets.UTF_8);
    HANDLE_SLOT.setRelease(values, handle, text);
    return text;
  }

  SlabBytes blob(int header) {
    int handle = stringHandle(bytes, header);
    if (cached(handle) instanceof SlabBytes array) return array;
    return createBlob(header, handle);
  }

  @TruffleBoundary
  private synchronized SlabBytes createBlob(int header, int handle) {
    Object[] values = cache();
    if (values[handle] instanceof SlabBytes array) return array;
    SlabBytes array = new SlabBytes(this, header);
    HANDLE_SLOT.setRelease(values, handle, array);
    return array;
  }

  private Map<Integer, Object> views() {
    Map<Integer, Object> values = views;
    if (values == null) views = values = new HashMap<>();
    return values;
  }

  @TruffleBoundary
  synchronized SlabArray array(int type, int ordinal, int list, boolean expanded) {
    Map<Integer, Object> values = views();
    if (values.get(list) instanceof SlabArray found) return found;
    SlabArray result = new SlabArray(this, type, ordinal, list, expanded);
    values.put(list, result);
    return result;
  }

  @TruffleBoundary
  synchronized Object unsigned(int at, long bits) {
    Map<Integer, Object> values = views();
    // List offsets are positive, so complemented slot offsets never collide with them.
    Object result = values.get(~at);
    if (result == null) {
      result = SlabRow.unsigned(bits);
      values.put(~at, result);
    }
    return result;
  }

  private SlabMessage realize(int type, int header, int handle) {
    Projected state = java.util.Objects.requireNonNull(projected);
    Object[] cached = cache();
    if (cached[handle] instanceof SlabMessage child) return child;
    int depth = getInt(bytes, header + 12);
    var childLimits =
        new SlabLimits(
            state.limits.maxDepth() - depth, state.limits.maxSlabBytes() - retainedSlabBytes(), 0);
    SlabMessage child =
        decode(
            layout.plan(type),
            state.input,
            getInt(bytes, header),
            getInt(bytes, header + 4),
            childLimits);
    // First account without changing the shared counters, so a rejected read is repeatable.
    var childBudget = new ProtoRow.Budget(plan().limits());
    child.validate(0, ROOT, childBudget, depth + 1);
    if (childBudget.cellCount() > plan().limits().maxCells() - state.budget.cellCount()
        || childBudget.byteCount() > plan().limits().maxStringBytes() - state.budget.byteCount())
      throw new ContractViolation("Deferred protobuf payload budget exceeded");
    state.budget.cells(childBudget.cellCount());
    state.budget.bytes(childBudget.byteCount());
    state.materializedBytes += child.retainedSlabBytes();
    // The release also publishes the committed lazy budget and retained-byte accounting.
    HANDLE_SLOT.setRelease(cached, handle, child);
    return child;
  }

  void requireValue(int type, int ordinal, boolean expanded) {
    Projected state = projected;
    if (state != null
        && !expanded
        && !state.selection.wants(type, ordinal)
        && !(state.selection.lazyChildrenEnabled()
            && layout.plan(type).field(ordinal).kind().message())) throw omitted();
  }

  boolean expandChild(int type, int ordinal) {
    Projected state = projected;
    return state != null
        && state.selection.lazyChildrenEnabled()
        && !state.selection.wants(type, ordinal);
  }

  @TruffleBoundary
  private static ContractViolation omitted() {
    return new ContractViolation("Field omitted by projection");
  }

  byte[] payloadBytes(int header) {
    return external(bytes, header) ? java.util.Objects.requireNonNull(projected).input : bytes;
  }

  int payloadOffset(int header) {
    return external(bytes, header)
        ? getInt(bytes, header + STRING_HEADER_BYTES)
        : payload(bytes, header);
  }

  /** Every reachable record is checked; overwritten orphan records do not constrain the result. */
  private void validate(int type, int record, ProtoRow.Budget budget, int depth) {
    validate(type, record, budget, depth, null);
  }

  private void validate(
      int type, int record, ProtoRow.Budget budget, int depth, @Nullable Map<Long, Usage> completed) {
    if (depth > plan().maxDepth()) throw new ContractViolation("Protobuf nesting budget exceeded");
    long key = ((long) record << 32) | Integer.toUnsignedLong(depth);
    if (completed != null) {
      Usage previous = completed.get(key);
      if (previous != null
          && previous.cells() <= plan().limits().maxCells() - budget.cellCount()
          && previous.stringBytes() <= plan().limits().maxStringBytes() - budget.byteCount()) {
        // Charge each occurrence. If either aggregate does not fit, descend in original order
        // instead, so the first payload/cell/required-field violation stays unchanged.
        budget.cells(previous.cells());
        budget.bytes(previous.stringBytes());
        return;
      }
    }
    long cells = budget.cellCount(), stringBytes = budget.byteCount();
    ProtoPlan plan = layout.plan(type);
    budget.cells(plan.size());
    int unknown = unknown(bytes, record);
    if (unknown != 0) budget.bytes(unknownLength(bytes, unknown));
    for (int ordinal = 0; ordinal < plan.size(); ordinal++) {
      var field = plan.field(ordinal);
      if (!present(bytes, record, ordinal)) {
        if (field.required())
          throw new ContractViolation("Missing required field: " + field.name());
        if (!field.repeated() && field.defaultValue() instanceof String text)
          budget.bytes(2L * text.length());
        if (!field.repeated() && field.defaultValue() instanceof DataArray blob)
          budget.cells(blob.size());
        continue;
      }
      int slot = record + layout.slot(type, ordinal);
      if (field.repeated()) {
        int list = getInt(bytes, slot), count = listCount(bytes, list);
        budget.cells(count);
        if (projected != null && getInt(bytes, list + 4) == COUNTS_ONLY) continue;
        int width = layout.kind(type, ordinal).element;
        for (int i = 0; i < count; i++)
          validateValue(type, ordinal, listElement(list, width, i), budget, depth, completed);
      } else validateValue(type, ordinal, slot, budget, depth, completed);
    }
    // Only successful complete subtrees are reusable; subtraction cannot overflow.
    if (completed != null)
      completed.put(key, new Usage(budget.cellCount() - cells, budget.byteCount() - stringBytes));
  }

  private void validateValue(
      int type, int ordinal, int at, ProtoRow.Budget budget, int depth,
      @Nullable Map<Long, Usage> completed) {
    var field = layout.plan(type).field(ordinal);
    switch (field.kind()) {
      case MESSAGE, GROUP -> {
        int child = getInt(bytes, at);
        if (child > 0) validate(layout.childType(type, ordinal), child, budget, depth + 1, completed);
      }
      case STRING -> {
        int header = getInt(bytes, at);
        if (header != 0) budget.bytes(2L * utf16Length(bytes, header));
      }
      case BYTES -> {
        int header = getInt(bytes, at);
        if (header != 0) budget.cells(SlabFormat.stringLength(bytes, header));
      }
      default -> {}
    }
  }

  byte[] unknownCopy(int record) {
    int span = unknown(bytes, record);
    return span == 0
        ? new byte[0]
        : Arrays.copyOfRange(
            bytes, span + UNKNOWN_BYTES, span + UNKNOWN_BYTES + unknownLength(bytes, span));
  }

  /** Re-encode known fields and unknown spans directly; no input retention or reparse. */
  @TruffleBoundary
  public byte[] encode() {
    if (projected != null && !projected.selection.complete())
      throw new ContractViolation("Cannot encode a partial projection");
    return encodeRecord(0, ROOT);
  }

  private byte[] encodeRecord(int type, int record) {
    var output = new BoundedOutput(plan().maxWireBytes());
    ProtoPlan plan = layout.plan(type);
    for (int ordinal = 0; ordinal < plan.size(); ordinal++) {
      if (!present(bytes, record, ordinal)) continue;
      var field = plan.field(ordinal);
      int at = record + layout.slot(type, ordinal);
      if (field.repeated()) {
        int list = getInt(bytes, at), count = listCount(bytes, list);
        int width = layout.kind(type, ordinal).element;
        if (field.packed()) {
          var packed = new BoundedOutput(plan().maxWireBytes());
          for (int i = 0; i < count; i++) scalar(packed, field.kind(), listElement(list, width, i));
          varint(output, ((long) field.number() << 3) | 2);
          varint(output, packed.size());
          output.writeBytes(packed.toByteArray());
        } else
          for (int i = 0; i < count; i++) field(output, type, ordinal, listElement(list, width, i));
      } else field(output, type, ordinal, at);
      if (output.size() > plan().maxWireBytes())
        throw new ContractViolation("Encoded wire budget exceeded");
    }
    output.writeBytes(unknownCopy(record));
    if (output.size() > plan().maxWireBytes())
      throw new ContractViolation("Encoded wire budget exceeded");
    return output.toByteArray();
  }

  private void field(ByteArrayOutputStream output, int type, int ordinal, int at) {
    var field = layout.plan(type).field(ordinal);
    varint(output, ((long) field.number() << 3) | field.kind().wire);
    switch (field.kind()) {
      case STRING, BYTES -> {
        int header = getInt(bytes, at), length = SlabFormat.stringLength(bytes, header);
        varint(output, length);
        output.write(payloadBytes(header), payloadOffset(header), length);
      }
      case MESSAGE, GROUP -> {
        byte[] child = encodeRecord(layout.childType(type, ordinal), getInt(bytes, at));
        if (field.kind() == ProtoPlan.Kind.MESSAGE) varint(output, child.length);
        output.writeBytes(child);
        if (field.kind() == ProtoPlan.Kind.GROUP) varint(output, ((long) field.number() << 3) | 4);
      }
      default -> scalar(output, field.kind(), at);
    }
  }

  private void scalar(ByteArrayOutputStream output, ProtoPlan.Kind kind, int at) {
    long value =
        switch (kind) {
          case INT64, UINT64, SINT64, FIXED64, SFIXED64, DOUBLE -> SlabFormat.getLong(bytes, at);
          case BOOL -> getUnsignedByte(bytes, at);
          case UINT32, FIXED32 -> Integer.toUnsignedLong(getInt(bytes, at));
          default -> getInt(bytes, at);
        };
    if (kind == ProtoPlan.Kind.SINT32)
      value = Integer.toUnsignedLong(((int) value << 1) ^ ((int) value >> 31));
    if (kind == ProtoPlan.Kind.SINT64) value = (value << 1) ^ (value >> 63);
    if (kind.wire == 1 || kind.wire == 5) {
      for (int i = 0; i < (kind.wire == 1 ? 8 : 4); i++) output.write((int) (value >>> (8 * i)));
    } else varint(output, value);
  }

  private static void varint(ByteArrayOutputStream output, long value) {
    while ((value & ~127L) != 0) {
      output.write((int) (value & 127) | 128);
      value >>>= 7;
    }
    output.write((int) value);
  }
}
