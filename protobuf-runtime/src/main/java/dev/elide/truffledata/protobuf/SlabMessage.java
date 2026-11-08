package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Durable owning slab result. Closing is a no-op and snapshot preserves identity. */
public final class SlabMessage extends SlabRow implements ProtoMessage {
  final byte[] bytes;
  private final int extent;
  private Object @Nullable [] handles;
  private @Nullable Map<Integer, SlabArray> arrays;
  private @Nullable Map<Integer, Object> unsigned;

  private SlabMessage(Layout layout, byte[] owned) {
    this(layout, owned, owned.length);
  }

  private SlabMessage(Layout layout, byte[] owned, int extent) {
    super(layout);
    bytes = owned;
    this.extent = extent;
    check(bytes, extent, layout);
    validate(0, ROOT, new ProtoRow.Budget(plan().limits()), 1);
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

  /** Copy an externally produced owning slab; the supplied buffer is never retained. */
  @TruffleBoundary
  public static SlabMessage copyOf(ProtoPlan plan, java.nio.ByteBuffer slab, SlabLimits limits) {
    if (slab.remaining() > limits.maxSlabBytes())
      throw new ContractViolation("Slab byte budget exceeded");
    byte[] owned = new byte[slab.remaining()];
    slab.duplicate().get(owned);
    Layout layout = plan.layout();
    SlabVerifier.verify(owned, layout, limits);
    return new SlabMessage(layout, owned);
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
    SlabVerifier.verify(slab, extent, layout, limits);
    return new SlabMessage(layout, slab, extent);
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
    return extent;
  }

  /** Retained array capacity, including unused bytes after the valid slab extent. */
  public int retainedSlabBytes() {
    return bytes.length;
  }

  public byte[] slabCopy() {
    return Arrays.copyOf(bytes, extent);
  }

  @Override
  public void close() {}

  private Object[] cache() {
    Object[] values = handles;
    if (values == null) handles = values = new Object[getInt(bytes, HEADER_HANDLES)];
    return values;
  }

  @TruffleBoundary
  synchronized SlabRow row(int type, int record) {
    int handle = handle(bytes, record);
    Object[] values = cache();
    if (values[handle] instanceof SlabRow row) return row;
    SlabRow row = new SlabRow(this, type, record);
    values[handle] = row;
    return row;
  }

  @TruffleBoundary
  synchronized String text(int header) {
    int handle = stringHandle(bytes, header);
    Object[] values = cache();
    if (values[handle] instanceof String text) return text;
    String text =
        new String(
            bytes,
            payload(bytes, header),
            SlabFormat.stringLength(bytes, header),
            StandardCharsets.UTF_8);
    values[handle] = text;
    return text;
  }

  @TruffleBoundary
  synchronized SlabBytes blob(int header) {
    int handle = stringHandle(bytes, header);
    Object[] values = cache();
    if (values[handle] instanceof SlabBytes array) return array;
    SlabBytes array = new SlabBytes(this, header);
    values[handle] = array;
    return array;
  }

  @TruffleBoundary
  synchronized SlabArray array(int type, int ordinal, int list) {
    Map<Integer, SlabArray> values = arrays;
    if (values == null) arrays = values = new HashMap<>();
    SlabArray found = values.get(list);
    if (found != null) return found;
    SlabArray result = new SlabArray(this, type, ordinal, list);
    values.put(list, result);
    return result;
  }

  @TruffleBoundary
  synchronized Object unsigned(int at, long bits) {
    Map<Integer, Object> values = unsigned;
    if (values == null) unsigned = values = new HashMap<>();
    Object result = values.get(at);
    if (result == null) {
      result = SlabRow.unsigned(bits);
      values.put(at, result);
    }
    return result;
  }

  /** Every reachable record is checked; overwritten orphan records do not constrain the result. */
  private void validate(int type, int record, ProtoRow.Budget budget, int depth) {
    if (depth > plan().maxDepth()) throw new ContractViolation("Protobuf nesting budget exceeded");
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
        int width = layout.kind(type, ordinal).element;
        for (int i = 0; i < count; i++)
          validateValue(type, ordinal, listElement(list, width, i), budget, depth);
      } else validateValue(type, ordinal, slot, budget, depth);
    }
  }

  private void validateValue(int type, int ordinal, int at, ProtoRow.Budget budget, int depth) {
    var field = layout.plan(type).field(ordinal);
    switch (field.kind()) {
      case MESSAGE, GROUP ->
          validate(layout.childType(type, ordinal), getInt(bytes, at), budget, depth + 1);
      case STRING -> budget.bytes(2L * utf16Length(bytes, getInt(bytes, at)));
      case BYTES -> budget.cells(SlabFormat.stringLength(bytes, getInt(bytes, at)));
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
        output.write(bytes, payload(bytes, header), length);
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
