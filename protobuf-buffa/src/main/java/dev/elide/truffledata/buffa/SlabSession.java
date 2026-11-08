package dev.elide.truffledata.buffa;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

/** Thread-confined reusable native slab arena. The next decode invalidates every prior borrow. */
public final class SlabSession implements AutoCloseable {
  private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
  private final Thread thread = Thread.currentThread();
  private final BuffaWire decoder;
  private final BuffaWire.Backend backend;
  final Layout layout;
  final @Nullable Projection projection;
  private final @Nullable WireInput projectionInput;
  private WireInput.@Nullable Lease source;
  private long sourceAddress;
  private int materializedBytes;
  private @Nullable IdentityHashMap<Object, Object> materialized;
  private final SlabLimits limits;
  private final long compiled;
  private final Object callHandle;
  private @Nullable WireInput arena;
  private @Nullable ByteBuffer bytes;
  private long address;
  private long epoch;
  private int extent;
  private int pins;
  private boolean open = true;
  private Object @Nullable [] handles;
  private final Map<Integer, Object> other = new HashMap<>();
  private long cells;
  private long payload;

  SlabSession(BuffaWire decoder, ProtoPlan plan, int maxSchemaBytes, SlabLimits limits) {
    this(decoder, plan, maxSchemaBytes, limits, null);
  }

  SlabSession(
      BuffaWire decoder,
      ProtoPlan plan,
      int maxSchemaBytes,
      SlabLimits limits,
      @Nullable Projection projection) {
    this.projection = projection;
    if (projection != null) projection.requireLayout(plan.layout());
    this.decoder = decoder;
    backend =
        Objects.requireNonNull(decoder.backend, "Native slab sessions require a native backend");
    layout = plan.layout();
    this.limits =
        new SlabLimits(Math.min(limits.maxDepth(), plan.maxDepth() - 1), limits.maxSlabBytes(), 0);
    byte[] schema =
        NativeSchema.encode(
            NativeSchema.types(plan, List.of(), maxSchemaBytes), List.of(), maxSchemaBytes);
    try (var input = decoder.allocate(schema.length).put(schema)) {
      compiled = backend.compileLayout(input);
    }
    try {
      callHandle = backend.prepareSlab(compiled);
      if (projection == null) projectionInput = null;
      else {
        byte[] program = projection.program();
        projectionInput = decoder.allocate(program.length).put(program);
        projectionInput.retain().release();
      }
    } catch (RuntimeException | Error failure) {
      backend.releaseLayout(compiled);
      throw failure;
    }
  }

  private void thread() {
    if (Thread.currentThread() != thread) throw wrongThread();
  }

  void check(long expected) {
    thread();
    if (!open || extent == 0 || expected != epoch) throw expired();
  }

  @TruffleBoundary
  private static IllegalStateException wrongThread() {
    return new IllegalStateException("Slab session is thread-confined");
  }

  @TruffleBoundary
  private static IllegalStateException expired() {
    return new IllegalStateException("Borrowed slab expired");
  }

  @TruffleBoundary
  private static IndexOutOfBoundsException outside() {
    return new IndexOutOfBoundsException("Slab load outside valid extent");
  }

  private void span(int at, int length) {
    if (at < 0 || at > extent - length) throw outside();
  }

  private void writable() {
    thread();
    if (!open) throw new IllegalStateException("Slab session closed");
    if (pins != 0) throw new IllegalStateException("Slab session is pinned");
  }

  /** Reuse the arena. Failed attempts also invalidate previous results; pinned attempts do not. */
  @TruffleBoundary
  public BorrowedSlabMessage decode(WireInput input) {
    writable();
    decoder.check();
    invalidate();
    if (input.size() > layout.plan(0).maxWireBytes())
      throw new ContractViolation("Typed input budget");
    var retained = input.retain();
    boolean transferred = false;
    try {
      if (arena == null) grow((int) Math.min(limits.maxSlabBytes(), 4L * input.size() + 256));
      WireInput output = Objects.requireNonNull(arena);
      int written =
          backend.writeSlab(
              callHandle,
              input,
              output,
              limits,
              projection == null ? 1 : projection.decodeFlags(),
              projectionInput);
      if (written < 0) {
        grow(-written);
        written =
            backend.writeSlab(
                callHandle,
                input,
                Objects.requireNonNull(arena),
                limits,
                projection == null ? 1 : projection.decodeFlags(),
                projectionInput);
      }
      if (written <= 0) throw new ContractViolation("Slab capacity retry failed");
      extent = written;
      if ((word(HEADER_FLAGS) & BORROWING) != 0) {
        source = retained;
        transferred = true;
        sourceAddress =
            ImageInfo.inImageCode()
                ? ((BuffaSvm.Address) Objects.requireNonNull(input.lease.attachment)).value()
                : FfmBackend.nativeAddress(input);
      }
      try {
        validate();
        return new BorrowedSlabMessage(this, epoch);
      } catch (RuntimeException | Error failure) {
        invalidate();
        throw failure;
      }
    } finally {
      if (!transferred) retained.release();
    }
  }

  private void grow(int capacity) {
    WireInput next = backend.allocate(capacity);
    long nativeAddress =
        ImageInfo.inImageCode()
            ? ((BuffaSvm.Address) Objects.requireNonNull(next.lease.attachment)).value()
            : FfmBackend.nativeAddress(next);
    if (ImageInfo.inImageCode() && nativeAddress == 0) {
      next.close();
      throw new IllegalStateException("Native Image session arena has no native address");
    }
    var previous = arena;
    arena = next;
    bytes = next.lease.bytes.order(ByteOrder.LITTLE_ENDIAN);
    address = nativeAddress;
    if (previous != null) previous.close();
  }

  private void invalidate() {
    if (epoch == Long.MAX_VALUE) throw new IllegalStateException("Slab session epoch exhausted");
    epoch++;
    extent = 0;
    if (handles != null) Arrays.fill(handles, null);
    other.clear();
    if (materialized != null) materialized.clear();
    materializedBytes = 0;
    if (source != null) {
      source.release();
      source = null;
    }
    sourceAddress = 0;
  }

  /** Explicit reset invalidates borrows and retains arena capacity for the next decode. */
  @TruffleBoundary
  public void reset() {
    writable();
    invalidate();
  }

  /** Keep this borrow alive across code that could otherwise recycle the arena. */
  @TruffleBoundary
  Pin pin(long expected) {
    check(expected);
    pins = Math.addExact(pins, 1);
    return new Pin(this, expected);
  }

  public static final class Pin implements AutoCloseable {
    private final SlabSession session;
    private final long epoch;
    private boolean open = true;

    private Pin(SlabSession session, long epoch) {
      this.session = session;
      this.epoch = epoch;
    }

    @Override
    public void close() {
      session.thread();
      if (open) {
        session.check(epoch);
        session.pins--;
        open = false;
      }
    }
  }

  void release(long expected) {
    thread();
    if (open && expected == epoch && extent != 0) {
      writable();
      invalidate();
    }
  }

  public int arenaBytes() {
    thread();
    return arena == null ? 0 : arena.size();
  }

  int extent(long expected) {
    check(expected);
    return extent;
  }

  @TruffleBoundary
  SlabMessage retain(long expected) {
    check(expected);
    return projection == null
        ? SlabMessage.copyOf(layout.plan(0), Objects.requireNonNull(bytes).slice(0, extent), limits)
        : SlabMessage.copyProjectedOf(
            layout.plan(0),
            Objects.requireNonNull(bytes).slice(0, extent),
            sourceBytes(),
            projection,
            limits);
  }

  @TruffleBoundary
  SlabRow retain(long expected, int type, int record) {
    check(expected);
    return projection == null
        ? SlabMessage.copyRecordOf(
            layout.plan(0), Objects.requireNonNull(bytes).slice(0, extent), limits, type, record)
        : SlabMessage.copyProjectedRecordOf(
            layout.plan(0),
            Objects.requireNonNull(bytes).slice(0, extent),
            sourceBytes(),
            projection,
            limits,
            type,
            record);
  }

  @Override
  @TruffleBoundary
  public void close() {
    thread();
    if (open) {
      writable();
      invalidate();
      open = false;
      try {
        if (arena != null) arena.close();
        if (projectionInput != null) projectionInput.close();
      } finally {
        backend.releaseLayout(compiled);
      }
    }
  }

  private ByteBuffer sourceBytes() {
    return source == null ? ByteBuffer.allocate(0) : source.bytes.asReadOnlyBuffer();
  }

  /** Native input capacity retained by this decode, separate from the slab arena. */
  public int retainedInputBytes() {
    thread();
    return source == null ? 0 : source.bytes.capacity();
  }

  int word(int at) {
    if (ImageInfo.inImageCode()) {
      span(at, 4);
      int value = WordFactory.<Pointer>pointer(address + at).readInt(0);
      return LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
    }
    span(at, 4);
    int value = FfmBackend.readAddressInt(address + at);
    java.lang.ref.Reference.reachabilityFence(this);
    return value;
  }

  long wide(int at) {
    if (ImageInfo.inImageCode()) {
      span(at, 8);
      long value = WordFactory.<Pointer>pointer(address + at).readLong(0);
      return LITTLE_ENDIAN ? value : Long.reverseBytes(value);
    }
    span(at, 8);
    long value = FfmBackend.readAddressLong(address + at);
    java.lang.ref.Reference.reachabilityFence(this);
    return value;
  }

  int octet(int at) {
    if (ImageInfo.inImageCode()) {
      span(at, 1);
      return WordFactory.<Pointer>pointer(address + at).readByte(0) & 255;
    }
    span(at, 1);
    int value = FfmBackend.readAddressByte(address + at);
    java.lang.ref.Reference.reachabilityFence(this);
    return value;
  }

  boolean present(int record, int ordinal) {
    return (word(record + RECORD_PRESENCE + 4 * (ordinal >>> 5)) & (1 << ordinal)) != 0;
  }

  int length(int header) {
    return word(header) & LENGTH_MASK;
  }

  int utf16(int header) {
    return word(header) < 0 ? length(header) : word(header + STRING_UTF16);
  }

  int payloadAt(int header) {
    return header + (word(header) < 0 ? ASCII_HEADER_BYTES : STRING_HEADER_BYTES);
  }

  private Object[] cache() {
    int count = word(HEADER_HANDLES);
    if (handles == null || handles.length < count) handles = new Object[count];
    return handles;
  }

  @TruffleBoundary
  Object text(int header) {
    Object[] cache = cache();
    int handle = word(header + STRING_HANDLE);
    Object value = cache[handle];
    if (value == null) {
      byte[] raw = copy(payloadAt(header), length(header));
      cache[handle] = value = new String(raw, StandardCharsets.UTF_8);
    }
    return value;
  }

  @TruffleBoundary
  Object blob(long expected, int header) {
    Object[] cache = cache();
    int handle = word(header + STRING_HANDLE);
    Object value = cache[handle];
    if (value == null)
      cache[handle] = value = new BorrowedSlabArray(this, expected, -1, -1, header);
    return value;
  }

  @TruffleBoundary
  Object row(long expected, int type, int record, boolean expanded) {
    if (record < 0) return realize(expected, type, record & ~LAZY_CHILD);
    Object[] cache = cache();
    int handle = word(record);
    Object value = cache[handle];
    if (value == null)
      cache[handle] = value = new BorrowedSlabRow(this, expected, type, record, expanded);
    return value;
  }

  @TruffleBoundary
  Object array(long expected, int type, int ordinal, int list, boolean expanded) {
    Object value = other.get(list);
    if (value == null) {
      value = new BorrowedSlabArray(this, expected, type, ordinal, list, expanded);
      other.put(list, value);
    }
    return value;
  }

  Object value(long expected, int type, int ordinal, int at, boolean expanded) {
    var kind = layout.plan(type).field(ordinal).kind();
    long bits =
        switch (kind) {
          case DOUBLE, INT64, UINT64, FIXED64, SFIXED64, SINT64 -> wide(at);
          case BOOL -> octet(at);
          default -> word(at);
        };
    return switch (kind) {
      case STRING -> text((int) bits);
      case BYTES -> blob(expected, (int) bits);
      case MESSAGE, GROUP ->
          row(
              expected,
              layout.childType(type, ordinal),
              (int) bits,
              expanded || expandChild(type, ordinal));
      case UINT64, FIXED64 -> unsigned(at, bits);
      case UINT32, FIXED32 -> bits & 0xffffffffL;
      case FLOAT -> (double) Float.intBitsToFloat((int) bits);
      case DOUBLE -> Double.longBitsToDouble(bits);
      case BOOL -> bits != 0;
      default -> bits;
    };
  }

  void requireValue(int type, int ordinal, boolean expanded) {
    Projection selection = projection;
    if (selection != null
        && !expanded
        && !selection.wants(type, ordinal)
        && !(selection.lazyChildrenEnabled() && layout.plan(type).field(ordinal).kind().message()))
      throw omitted();
  }

  boolean expandChild(int type, int ordinal) {
    Projection selection = projection;
    return selection != null && selection.lazyChildrenEnabled() && !selection.wants(type, ordinal);
  }

  @TruffleBoundary
  private static ContractViolation omitted() {
    return new ContractViolation("Field omitted by projection");
  }

  int payloadByte(int header, int index) {
    if ((word(header) & EXTERNAL) == 0) return octet(payloadAt(header) + index);
    int offset = word(header + STRING_HEADER_BYTES) + index;
    var retained = Objects.requireNonNull(source);
    if (offset < 0 || offset >= retained.bytes.capacity()) throw outside();
    if (ImageInfo.inImageCode())
      return WordFactory.<Pointer>pointer(sourceAddress + offset).readByte(0) & 255;
    int value = FfmBackend.readAddressByte(sourceAddress + offset);
    java.lang.ref.Reference.reachabilityFence(this);
    return value;
  }

  @TruffleBoundary
  byte[] copyPayload(int header) {
    int length = length(header);
    if ((word(header) & EXTERNAL) == 0) return copy(payloadAt(header), length);
    byte[] result = new byte[length];
    Objects.requireNonNull(source).slice(word(header + STRING_HEADER_BYTES), length).get(result);
    return result;
  }

  private Object realize(long expected, int type, int header) {
    Object[] cache = cache();
    int handle = word(header + 8);
    Object found = cache[handle];
    if (found != null) return found;
    int depth = word(header + 12);
    var remaining =
        new SlabLimits(
            limits.maxDepth() - depth, limits.maxSlabBytes() - arenaBytes() - materializedBytes, 0);
    byte[] childWire = new byte[word(header + 4)];
    Objects.requireNonNull(source).slice(word(header), childWire.length).get(childWire);
    SlabMessage child =
        SlabMessage.decode(layout.plan(type), childWire, 0, childWire.length, remaining);
    var usage = child.logicalUsage();
    if (usage.cells() > layout.plan(0).limits().maxCells() - cells
        || usage.stringBytes() > layout.plan(0).limits().maxStringBytes() - payload)
      throw new ContractViolation("Deferred protobuf payload budget exceeded");
    cells(usage.cells());
    payload(usage.stringBytes());
    materializedBytes += child.retainedSlabBytes();
    Object wrapped = wrap(expected, child);
    cache[handle] = wrapped;
    return wrapped;
  }

  @TruffleBoundary
  Object wrap(long expected, Object value) {
    check(expected);
    if (!(value instanceof SlabRow) && !(value instanceof OwnedArray)) return value;
    var cache = materialized;
    if (cache == null) materialized = cache = new IdentityHashMap<>();
    Object found = cache.get(value);
    if (found != null) return found;
    Object result =
        value instanceof SlabRow row
            ? new BorrowedMaterializedRow(this, expected, row)
            : new BorrowedMaterializedArray(this, expected, (OwnedArray) value);
    cache.put(value, result);
    return result;
  }

  @TruffleBoundary
  private Object unsigned(int at, long bits) {
    // Negative keys cannot collide with list offsets.
    Object value = other.get(-at);
    if (value == null) {
      var number =
          bits >= 0
              ? java.math.BigInteger.valueOf(bits)
              : java.math.BigInteger.valueOf(bits & Long.MAX_VALUE).setBit(63);
      value = Values.normalize(number);
      other.put(-at, value);
    }
    return value;
  }

  @TruffleBoundary
  byte[] copy(int at, int length) {
    Objects.checkFromIndexSize(at, length, extent);
    byte[] result = new byte[length];
    Objects.requireNonNull(bytes).slice(at, length).get(result);
    return result;
  }

  int unknown(int record) {
    int table = word(HEADER_UNKNOWNS);
    if (table == 0) return 0;
    int low = 0, high = word(table) - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1, entry = table + 8 + middle * 8, found = word(entry);
      if (found == record) return word(entry + 4);
      if (found < record) low = middle + 1;
      else high = middle - 1;
    }
    return 0;
  }

  // The internal native producer owns the structural proof. Validate the public contract's
  // reachable required fields and retained budgets before publishing, with no row allocations.
  private void validate() {
    if (extent < ROOT + layout.recordBytes(0)
        || (extent & 7) != 0
        || word(HEADER_MAGIC) != MAGIC
        || word(HEADER_BYTES_USED) != extent
        || word(HEADER_ROOT) != ROOT
        || word(HEADER_LAYOUT) != layout.fingerprint()
        || (projection == null
            ? (word(HEADER_FLAGS) & (OWNING | BORROWING | LAX | PROJECTED)) != OWNING
            : (word(HEADER_FLAGS) & PROJECTED) == 0))
      throw new ContractViolation("Invalid session slab");
    cells = payload = 0;
    validateRecord(0, ROOT, 1);
  }

  private void cells(long count) {
    if (count < 0 || count > layout.plan(0).limits().maxCells() - cells)
      throw new ContractViolation("Protobuf cell budget exceeded");
    cells += count;
  }

  private void payload(long count) {
    if (count < 0 || count > layout.plan(0).limits().maxStringBytes() - payload)
      throw new ContractViolation("Protobuf payload budget exceeded");
    payload += count;
  }

  private void validateRecord(int type, int record, int depth) {
    if (depth > limits.maxDepth() + 1)
      throw new ContractViolation("Protobuf nesting budget exceeded");
    Objects.checkFromIndexSize(record, layout.recordBytes(type), extent);
    var plan = layout.plan(type);
    cells(plan.size());
    int unknown = unknown(record);
    if (unknown != 0) payload(word(unknown));
    for (int ordinal = 0; ordinal < plan.size(); ordinal++) {
      var field = plan.field(ordinal);
      if (!present(record, ordinal)) {
        if (field.required())
          throw new ContractViolation("Missing required field: " + field.name());
        if (!field.repeated() && field.defaultValue() instanceof String text)
          payload(2L * text.length());
        if (!field.repeated() && field.defaultValue() instanceof OwnedArray blob)
          cells(blob.size());
        continue;
      }
      int at = record + layout.slot(type, ordinal);
      if (field.repeated()) {
        int list = word(at), count = word(list);
        cells(count);
        if (projection != null && word(list + 4) == COUNTS_ONLY) continue;
        int width = layout.kind(type, ordinal).element;
        for (int i = 0; i < count; i++)
          validateValue(type, ordinal, listElement(list, width, i), depth);
      } else validateValue(type, ordinal, at, depth);
    }
  }

  private void validateValue(int type, int ordinal, int at, int depth) {
    switch (layout.plan(type).field(ordinal).kind()) {
      case MESSAGE, GROUP -> {
        int child = word(at);
        if (child > 0) validateRecord(layout.childType(type, ordinal), child, depth + 1);
      }
      case STRING -> {
        int header = word(at);
        if (header != 0) payload(2L * utf16(header));
      }
      case BYTES -> {
        int header = word(at);
        if (header != 0) cells(length(header));
      }
      default -> {}
    }
  }
}
