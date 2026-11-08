package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.generated.*;
import dev.elide.truffledata.protobuf.*;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** Reusable schema program shared by native binary, textproto and ProtoJSON ingestion. */
public final class BuffaPlan implements AutoCloseable {
  private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
  private static final boolean STRICT = Boolean.getBoolean("truffledata.strict");
  private final BuffaWire decoder;
  private final ProtoPlan plan;
  private final ReadProfile profile;
  private final List<ProtoPlan> anyTypes;
  private final WireInput schema;

  /** Native compiled slab layout, owned by this plan; zero before first native decode. */
  private long slabLayout;

  private int slabCapacity;

  /** Immutable default limits of the last {@code maxSlabBytes}, reused instead of per decode. */
  private @Nullable SlabLimits slabLimits;

  private final int maxSchemaBytes;
  private final @Nullable Projection projection;
  private @Nullable SlabSession projectedSession;
  private @Nullable SlabLimits projectedLimits;

  private boolean open = true;

  BuffaPlan(BuffaWire decoder, ProtoPlan plan, int maxSchemaBytes, List<ProtoPlan> anyTypes) {
    this(decoder, plan, maxSchemaBytes, anyTypes, null);
  }

  private BuffaPlan(
      BuffaWire decoder,
      ProtoPlan plan,
      int maxSchemaBytes,
      List<ProtoPlan> anyTypes,
      @Nullable Projection projection) {
    this.projection = projection;
    this.decoder = decoder;
    this.maxSchemaBytes = maxSchemaBytes;
    this.plan = Objects.requireNonNull(plan);
    this.anyTypes = List.copyOf(anyTypes);
    var types = NativeSchema.types(plan, this.anyTypes, maxSchemaBytes);
    byte[] program = NativeSchema.encode(types, this.anyTypes, maxSchemaBytes);
    profile = new ReadProfile(types);
    schema = decoder.allocate(program.length).put(program);
    // Schema input is immutable for the lifetime of this plan.
    schema.retain().release();
  }

  public ProtoPlan contract() {
    return plan;
  }

  private void check() {
    decoder.check();
    if (!open) throw new IllegalStateException("Native plan closed");
  }

  /** Cache immutable default limits for the current byte bound. */
  private SlabLimits slabLimits(int maxSlabBytes) {
    SlabLimits limits = slabLimits;
    if (limits == null || limits.maxSlabBytes() != maxSlabBytes)
      slabLimits = limits = new SlabLimits(plan.maxDepth() - 1, maxSlabBytes);
    return limits;
  }

  /**
   * First-attempt slab capacity. The retained heap array is the message's own storage, so once a
   * message of this plan has been decoded the learned extent is reused exactly; a larger message
   * retries once with the extent the producer reports. The first decode sizes from the input.
   */
  private int firstCapacity(int maxSlabBytes, int inputBytes) {
    long guess = slabCapacity > 0 ? slabCapacity : 4L * inputBytes + 256;
    return (int) Math.min(maxSlabBytes, guess);
  }

  @TruffleBoundary
  public SlabMessage decode(WireInput input, int maxSlabBytes) {
    return decodeSlab(input, maxSlabBytes);
  }

  @TruffleBoundary
  public SlabMessage decode(byte[] input, int maxSlabBytes) {
    return decodeSlab(input, maxSlabBytes);
  }

  /** Bind one immutable selection in an independently closeable compiled plan. */
  @TruffleBoundary
  public BuffaPlan project(Projection selection) {
    check();
    selection.requireLayout(plan.layout());
    return new BuffaPlan(decoder, plan, maxSchemaBytes, anyTypes, selection);
  }

  /** Create a native arena session; Java-only consumers use the durable decode methods. */
  @TruffleBoundary
  public SlabSession newSession(int maxSlabBytes) {
    check();
    if (decoder.backend == null)
      throw new UnsupportedOperationException("Slab sessions require native memory");
    return new SlabSession(
        decoder,
        plan,
        maxSchemaBytes,
        new SlabLimits(plan.maxDepth() - 1, maxSlabBytes, 0),
        projection);
  }

  /** Decode binary protobuf into a durable slab that retains neither its input nor this plan. */
  @TruffleBoundary
  public SlabMessage decodeSlab(WireInput input, int maxSlabBytes) {
    check();
    if (input.size() > plan.maxWireBytes()) throw new ContractViolation("Typed input budget");
    var limits = slabLimits(maxSlabBytes);
    if (projection != null) return projected(input, limits);
    var source = input.retain();
    try {
      var backend = decoder.backend;
      if (backend == null) return javaSlab(source.bytes, limits);
      long compiled = slabLayout();
      int capacity = firstCapacity(maxSlabBytes, input.size());
      SlabMessage result = backend.slab(compiled, input, plan, limits, capacity);
      slabCapacity = Math.max(slabCapacity, result.slabBytes());
      return result;
    } finally {
      source.release();
    }
  }

  @TruffleBoundary
  public SlabMessage decodeSlab(byte[] input, int maxSlabBytes) {
    return decode(input, slabLimits(maxSlabBytes));
  }

  /** Decode with explicit slab and critical-call bounds; a zero critical bound disables pinning. */
  @TruffleBoundary
  public SlabMessage decode(byte[] input, SlabLimits limits) {
    check();
    if (limits.maxDepth() >= plan.maxDepth())
      limits = new SlabLimits(plan.maxDepth() - 1, limits.maxSlabBytes(), limits.criticalBytes());
    if (input.length > plan.maxWireBytes()) throw new ContractViolation("Typed input budget");
    int maxSlabBytes = limits.maxSlabBytes();
    if (projection != null) {
      if (decoder.backend == null)
        return SlabMessage.decode(plan, input, 0, input.length, limits, projection);
      try (var nativeInput = decoder.allocate(input.length).put(input)) {
        decoder.backend.recordSlabInputCopy(input.length);
        return projected(nativeInput, limits);
      }
    }
    if (decoder.backend == null) return SlabMessage.decode(plan, input, 0, input.length, limits);
    int capacity = firstCapacity(maxSlabBytes, input.length);
    SlabMessage result = decoder.backend.slab(slabLayout(), input, plan, limits, capacity);
    slabCapacity = Math.max(slabCapacity, result.slabBytes());
    return result;
  }

  private SlabMessage projected(WireInput input, SlabLimits limits) {
    Projection selection = Objects.requireNonNull(projection);
    if (decoder.backend == null) {
      byte[] bytes = new byte[input.size()];
      input.lease.bytes.duplicate().get(bytes);
      return SlabMessage.decode(plan, bytes, 0, bytes.length, limits, selection);
    }
    SlabSession session = projectedSession;
    if (session == null || !limits.equals(projectedLimits)) {
      if (session != null) session.close();
      projectedSession =
          session = new SlabSession(decoder, plan, maxSchemaBytes, limits, selection);
      projectedLimits = limits;
    }
    return session.decodeRetained(input);
  }

  private long slabLayout() {
    var backend = Objects.requireNonNull(decoder.backend);
    if (slabLayout == 0) {
      if (anyTypes.isEmpty()) slabLayout = backend.compileLayout(schema);
      else {
        // Binary Any payloads stay bytes; the slab fingerprint contains only reachable types.
        byte[] program =
            NativeSchema.encode(
                NativeSchema.types(plan, List.of(), maxSchemaBytes), List.of(), maxSchemaBytes);
        try (var binarySchema = decoder.allocate(program.length).put(program)) {
          slabLayout = backend.compileLayout(binarySchema);
        }
      }
    }
    return slabLayout;
  }

  /**
   * @deprecated Binary decoding now returns a durable owning slab. Use {@link #decode(WireInput,
   *     int)}.
   */
  @Deprecated(forRemoval = true)
  @TruffleBoundary
  public SlabMessage decodeView(WireInput input, int maxSlabBytes) {
    return decode(input, maxSlabBytes);
  }

  @TruffleBoundary
  public TypedMessage parseText(String text, int maxNativeBytes) {
    return text(text, 1, maxNativeBytes);
  }

  @TruffleBoundary
  public TypedMessage parseJson(String json, int maxNativeBytes) {
    return text(json, 2, maxNativeBytes);
  }

  private TypedMessage text(String text, int format, int nativeBytes) {
    check();
    if (nativeBytes < 0 || text.length() > plan.maxWireBytes() / 2)
      throw new ContractViolation("Text input budget");
    // String.getBytes replaces malformed UTF-16. Validate scalar values and bound the exact
    // UTF-8 expansion first, before allocating either heap or native input/payload storage.
    if (GeneratedOutput.utf8Length(text) > plan.maxWireBytes())
      throw new ContractViolation("Text wire byte budget");
    byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
    try (var input = decoder.allocate(utf8.length).put(utf8);
        var payload = decoder.allocate(Math.multiplyExact(utf8.length, 2))) {
      return parse(input, payload, format, nativeBytes);
    }
  }

  private TypedMessage parse(WireInput input, WireInput payload, int format, int nativeBytes) {
    if (nativeBytes < 0 || input.size() > plan.maxWireBytes())
      throw new ContractViolation("Typed input budget");
    WireInput.Lease source = input.retain();
    TypedMessage owner = null;
    try {
      owner = new TypedMessage(payload.retain());
      var backend = decoder.backend;
      if (backend == null) {
        String text = StandardCharsets.UTF_8.decode(source.bytes.duplicate()).toString();
        ProtoAccess owned = format == 1 ? plan.parseText(text) : plan.parseJson(text, anyTypes);
        return owner.finish(fromJava(owner, owned), 0);
      }
      try (var tape = backend.typed(schema, input, format, payload, plan, nativeBytes)) {
        State state =
            new State(
                plan,
                profile.hints[0],
                new DecodeContext(owner, format, plan.limits().maxCells(), profile));
        state.consume(tape, 0, tape.count());
        return owner.finish(state.finish(1), tape.allocated);
      }
    } catch (RuntimeException | Error failure) {
      if (owner != null) owner.close();
      throw failure;
    } finally {
      source.release();
    }
  }

  private SlabMessage javaSlab(java.nio.ByteBuffer source, SlabLimits limits) {
    var bytes = source.duplicate();
    if (bytes.hasArray())
      return SlabMessage.decode(
          plan, bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining(), limits);
    byte[] copy = new byte[bytes.remaining()];
    bytes.get(copy);
    return SlabMessage.decode(plan, copy, 0, copy.length, limits);
  }

  private static TypedRow fromJava(ViewOwner owner, ProtoAccess row) {
    Object[] values = new Object[row.plan().size()];
    boolean[] present =
        values.length > Long.SIZE ? new boolean[values.length] : State.NO_SEEN_FIELDS;
    long presenceBits = 0;
    var data = DataLibrary.factory().getUncached();
    for (int i = 0; i < values.length; i++) {
      values[i] = javaValue(owner, data.read(row, i));
      if (row.has(i)) {
        if (present.length == 0) presenceBits |= 1L << i;
        else present[i] = true;
      }
    }
    return new TypedRow.ArrayRow(owner, row.plan(), values, presenceBits, present);
  }

  private static Object javaValue(ViewOwner owner, Object value) {
    if (value instanceof ProtoAccess row) return fromJava(owner, row);
    if (value instanceof OwnedArray array) {
      if (array.isBytes()) {
        if (array instanceof DataArray) return array;
        byte[] bytes = new byte[array.size()];
        array.copyBytesTo(bytes, 0);
        return DataArray.copyOfBytes(bytes);
      }
      Object[] items = new Object[array.size()];
      for (int i = 0; i < items.length; i++) items[i] = javaValue(owner, array.get(i));
      return new TypedArray(owner, items);
    }
    return value;
  }

  @Override
  @TruffleBoundary
  public void close() {
    schema.lease.thread();
    if (open) {
      if (projectedSession != null) projectedSession.close();
      open = false;
      long layout = slabLayout;
      slabLayout = 0;
      try {
        if (layout != 0) Objects.requireNonNull(decoder.backend).releaseLayout(layout);
      } finally {
        schema.close();
      }
    }
  }

  /** Thread-confined, bounded hints: reading a string makes later values eager for that field. */
  private static final class ReadProfile {
    final List<ProtoPlan> types;
    final TypeProfile[] hints;

    ReadProfile(List<ProtoPlan> types) {
      this.types = types;
      hints = new TypeProfile[types.size()];
      Map<String, TypeProfile> profiles = new HashMap<>();
      for (int i = 0; i < types.size(); i++) {
        var type = types.get(i);
        hints[i] = new TypeProfile(type);
        profiles.put(type.name(), hints[i]);
      }
      // Link only after every type exists: recursive schemas must not recursively construct
      // profiles. Resolve names once here, never while materializing nested messages.
      for (int i = 0; i < types.size(); i++) {
        var type = types.get(i);
        for (int field = 0; field < type.size(); field++)
          if (type.field(field).kind().message())
            hints[i].children[field] =
                Objects.requireNonNull(profiles.get(type.nested(field).name()));
      }
    }
  }

  /**
   * Exact standard contracts are established by schema-directed decoding; richer facts revalidate.
   */
  private static final class TypeProfile {
    final boolean[] stringReads;
    final boolean[] standardContracts;
    final Object[] defaults;
    final @Nullable TypeProfile[] children;
    final long finishMask;

    TypeProfile(ProtoPlan plan) {
      stringReads = new boolean[plan.size()];
      standardContracts = new boolean[plan.size()];
      defaults = new Object[plan.size()];
      children = new TypeProfile[plan.size()];
      long mask = 0;
      for (int i = 0; i < plan.size(); i++) {
        standardContracts[i] = plan.schema().field(i).equals(plan.field(i).contract());
        defaults[i] = State.initial(plan.field(i));
        // These fields need work even when absent. Other standard defaults are already valid.
        if (i < Long.SIZE
            && (plan.field(i).required() || plan.field(i).repeated() || !standardContracts[i]))
          mask |= 1L << i;
      }
      finishMask = mask;
    }
  }

  /** Shared per-decode state; nested messages must charge one cumulative cell budget. */
  private static final class DecodeContext {
    final ViewOwner owner;
    final int format;
    final ReadProfile profile;
    long cells;
    private @Nullable TypedArray emptyArray;

    DecodeContext(ViewOwner owner, int format, long cells, ReadProfile profile) {
      this.owner = owner;
      this.format = format;
      this.cells = cells;
      this.profile = profile;
    }

    TypedArray emptyArray() {
      TypedArray result = emptyArray;
      if (result == null) emptyArray = result = new TypedArray(owner, State.EMPTY_ITEMS);
      return result;
    }

    void charge(long count) {
      if (count < 0 || count > cells)
        throw new ContractViolation("Typed materialization cell budget");
      cells -= count;
    }
  }

  /** Private staging storage is transferred once after all protobuf merges finish. */
  private static final class Repeated {
    Object[] items = State.EMPTY_ITEMS;
    int size;

    void add(Object value, BuffaWire.Tape tape, int index, int end, int ordinal) {
      if (size == items.length) {
        var buffer = tape.bytes();
        int count = 0;
        for (int cursor = index; cursor < end; ) {
          int at = Math.multiplyExact(cursor, Buffa.Field.SIZE);
          int number = buffer.getInt(at + (int) BuffaFFM.Field_number_OFFSET);
          int next = buffer.getInt(at + (int) BuffaFFM.Field_end_OFFSET);
          if (next <= cursor || next > end)
            throw new IllegalStateException("Invalid repeated index");
          if (number != ordinal + 1) break;
          count++;
          cursor = next;
        }
        if (count == 0) throw new IllegalStateException("Missing repeated index");
        // A later singular-message merge can append another segment to this repeated field.
        int required = Math.addExact(size, count);
        int capacity =
            items.length == 0
                ? required
                : Math.max(required, (int) Math.min(tape.count(), 2L * items.length));
        items = Arrays.copyOf(items, capacity);
      }
      items[size++] = value;
    }

    Object[] finish() {
      // Skipped null markers or invalid map entries can leave unused capacity.
      return size == items.length ? items : Arrays.copyOf(items, size);
    }
  }

  private static final class State {
    private static final boolean[] NO_SEEN_FIELDS = new boolean[0];
    private static final Object[] EMPTY_ITEMS = new Object[0];
    final ProtoPlan plan;
    final DecodeContext context;
    final TypeProfile hints;
    final Object[] values;
    final boolean[] present;
    long presenceBits;
    final boolean[] seen;

    State(ProtoPlan plan, TypeProfile hints, DecodeContext context) {
      this.plan = plan;
      this.context = context;
      this.hints = hints;
      context.charge(plan.size());
      // Copy immutable defaults, not payloads. Repeated placeholders are replaced per owner.
      values = Arrays.copyOf(hints.defaults, hints.defaults.length, Object[].class);
      present = values.length > Long.SIZE ? new boolean[values.length] : NO_SEEN_FIELDS;
      // Binary protobuf permits duplicate fields; only text formats track duplicates.
      seen = context.format == 0 ? NO_SEEN_FIELDS : new boolean[values.length];
    }

    boolean present(int ordinal) {
      return present.length == 0 ? (presenceBits & (1L << ordinal)) != 0 : present[ordinal];
    }

    void present(int ordinal, boolean value) {
      if (present.length != 0) present[ordinal] = value;
      else if (value) presenceBits |= 1L << ordinal;
      else presenceBits &= ~(1L << ordinal);
    }

    static Object initial(ProtoPlan.FieldPlan field) {
      return field.repeated() ? EMPTY_ITEMS : field.defaultValue();
    }

    void select(int ordinal) {
      var field = plan.field(ordinal);
      if (field.oneof() >= 0)
        for (int i = 0; i < values.length; i++) {
          if (i != ordinal && plan.field(i).oneof() == field.oneof()) {
            if (context.format != 0 && present(i))
              throw new ContractViolation("Multiple oneof members");
            values[i] = hints.defaults[i];
            present(i, false);
          }
        }
    }

    void consume(BuffaWire.Tape tape, int start, int end) {
      var buffer = tape.bytes();
      for (int index = start; index < end; ) {
        context.charge(1);
        int at = index * Buffa.Field.SIZE;
        // ABI 7 stores two adjacent int pairs; retain checked loads while reading each pair once.
        long tag = buffer.getLong(at + (int) BuffaFFM.Field_number_OFFSET);
        long value = buffer.getLong(at + (int) BuffaFFM.Field_value_OFFSET);
        long extent = buffer.getLong(at + (int) BuffaFFM.Field_length_OFFSET);
        int ordinal = (int) (LITTLE_ENDIAN ? tag : tag >>> 32) - 1;
        int kind = (int) (LITTLE_ENDIAN ? tag >>> 32 : tag);
        int length = (int) (LITTLE_ENDIAN ? extent : extent >>> 32);
        int next = (int) (LITTLE_ENDIAN ? extent >>> 32 : extent);
        if (ordinal < 0 || ordinal >= plan.size() || next <= index || next > end || length < 0)
          throw new IllegalStateException("Invalid native typed index");
        var f = plan.field(ordinal);
        if (kind == 100) {
          if (context.format != 2 || !f.repeated() || seen[ordinal])
            throw new ContractViolation("Duplicate JSON field");
          seen[ordinal] = true;
          index = next;
          continue;
        }
        boolean nullEnum =
            f.enumType() != null && f.enumType().name().equals("google.protobuf.NullValue");
        if (kind == 0) {
          if (context.format != 2) throw new IllegalStateException("Unexpected null marker");
          if (length == 0 && (f.repeated() || !nullEnum)) {
            if (seen[ordinal]) throw new ContractViolation("Duplicate JSON field");
            seen[ordinal] = true;
            index = next;
            continue;
          }
          if (!nullEnum) throw new ContractViolation("Null JSON element");
        }
        if (!f.repeated() && context.format != 0 && seen[ordinal])
          throw new ContractViolation("Duplicate singular field");
        if (context.format != 0) seen[ordinal] = true;
        if (kind == 101) {
          if (context.format != 2
              || plan.jsonType() != ProtoJsonType.ANY
              || f.number() != 2
              || value < 0
              || value >= context.profile.types.size())
            throw new IllegalStateException("Invalid Any index");
          ProtoPlan target = context.profile.types.get((int) value);
          State payload = new State(target, context.profile.hints[(int) value], context);
          payload.consume(tape, index + 1, next);
          byte[] encoded = target.encode(payload.finish(1).snapshot());
          context.charge(encoded.length);
          values[ordinal] = DataArray.copyOfBytes(encoded);
          present(ordinal, encoded.length != 0);
          index = next;
          continue;
        }
        if (kind != 0 && kind != f.kind().ordinal() + 1)
          throw new IllegalStateException("Native kind mismatch");
        if (f.map() && context.format == 0 && length != 0) {
          index = next;
          continue;
        }
        if (f.kind() == ProtoPlan.Kind.BYTES) context.charge(length);
        Object decoded;
        if (f.kind().message()) {
          if (!f.repeated()) select(ordinal);
          State child =
              !f.repeated() && values[ordinal] instanceof State old
                  ? old
                  : new State(
                      plan.nested(ordinal),
                      Objects.requireNonNull(hints.children[ordinal]),
                      context);
          child.consume(tape, index + 1, next);
          decoded = child;
        } else if (kind == 0) decoded = 0L;
        else
          decoded =
              switch (f.kind()) {
                case STRING -> {
                  // Singular unconstrained strings can remain in sealed source memory until read.
                  // Repeated strings still materialize for element-contract validation below.
                  if (!f.repeated()
                      && ((String) f.defaultValue()).isEmpty()
                      && !hints.stringReads[ordinal])
                    yield length == 0
                        ? ""
                        : new TypedRow.Utf8(
                            context.owner,
                            Math.toIntExact(value),
                            length,
                            hints.stringReads,
                            ordinal);
                  yield context.owner.utf8(Math.toIntExact(value), length);
                }
                case BYTES -> new BorrowedBytes(context.owner, Math.toIntExact(value), length);
                case DOUBLE -> Double.longBitsToDouble(value);
                case FLOAT -> (double) Float.intBitsToFloat((int) value);
                case BOOL -> value != 0;
                case INT32, SINT32, SFIXED32, ENUM -> (long) (int) value;
                case UINT32, FIXED32 -> value & 0xffffffffL;
                case UINT64, FIXED64 ->
                    Values.normalize(
                        value >= 0
                            ? BigInteger.valueOf(value)
                            : BigInteger.valueOf(value & Long.MAX_VALUE).setBit(63));
                default -> value;
              };
        if (f.repeated()) {
          Repeated items;
          if (values[ordinal] instanceof Repeated existing) items = existing;
          else {
            items = new Repeated();
            values[ordinal] = items;
          }
          items.add(decoded, tape, index, end, ordinal);
        } else {
          select(ordinal);
          values[ordinal] = decoded;
        }
        present(ordinal, true);
        index = next;
      }
    }

    TypedRow finish(int depth) {
      if (depth > plan.maxDepth()) throw new ContractViolation("Typed nesting budget");
      if (plan.definition().mapEntry()
          && plan.field(1).kind().message()
          && values[1] == Missing.INSTANCE) {
        values[1] = new State(plan.nested(1), Objects.requireNonNull(hints.children[1]), context);
        present(1, true);
      }
      boolean sparse = !STRICT && values.length <= Long.SIZE;
      long pending = presenceBits | hints.finishMask;
      for (int cursor = 0; cursor < values.length; ) {
        int i;
        if (sparse) {
          if (pending == 0) break;
          i = Long.numberOfTrailingZeros(pending);
          pending &= pending - 1;
          cursor = i + 1;
        } else i = cursor++;
        var field = plan.field(i);
        if (!STRICT && !present(i) && hints.standardContracts[i]) {
          if (field.required()) throw new ContractViolation("Missing required field");
          // Defaults are already validated and absent by definition. Repeated defaults must
          // nevertheless be bound to this decode's owner, never shared across messages.
          if (field.repeated()) values[i] = context.emptyArray();
          continue;
        }
        if (values[i] instanceof State child) values[i] = child.finish(depth + 1);
        else if (field.repeated()) {
          Object[] items = values[i] instanceof Repeated repeated ? repeated.finish() : EMPTY_ITEMS;
          Map<Object, Object> keys =
              field.map() && items.length != 0 ? new LinkedHashMap<>() : null;
          for (int j = 0; j < items.length; j++) {
            Object item = items[j];
            items[j] = item instanceof State child ? child.finish(depth + 1) : item;
            if (keys != null) {
              Object key = DataLibrary.factory().getUncached().read(items[j], 0);
              if (key instanceof ExactNumber number) key = number.decimal().toBigIntegerExact();
              if (keys.put(key, items[j]) != null && context.format == 2)
                throw new ContractViolation("Duplicate JSON map key");
            }
          }
          values[i] =
              items.length == 0
                  ? context.emptyArray()
                  : new TypedArray(context.owner, keys != null ? keys.values().toArray() : items);
        }
        if (!field.explicitPresence()) present(i, nonDefault(field, values[i]));
        if (field.required() && !present(i)) throw new ContractViolation("Missing required field");
        // consume checks native kind/ordinal/bounds and converts to the exact Java scalar
        // representation; children finish recursively and every repeated element takes that
        // same route. FieldPlan validated defaults. Rewalking a completed standard array adds
        // no new contract facts. Keep richer schemas on the full validator and audit the
        // native/Java cooperation in strict mode, including deferred strings.
        if (hints.standardContracts[i]) {
          if (STRICT) {
            Object value = values[i];
            if (value instanceof TypedRow.Utf8 text) value = text.value(context.owner);
            Values.validate(plan.schema().field(i), value);
          }
          continue;
        }
        if (values[i] instanceof TypedRow.Utf8 text) {
          // FieldPlan supplies the standard STRING contract, and native parsing has already
          // validated its type, UTF-8 and cumulative payload budget. Never bypass richer facts.
          var contract = plan.schema().field(i);
          if (contract.kind() != Field.Kind.STRING
              || !contract.constraints().equals(Constraints.NONE))
            Values.validate(contract, text.value(context.owner));
        } else {
          Values.validate(plan.schema().field(i), values[i]);
        }
      }
      return new TypedRow.ArrayRow(context.owner, plan, values, presenceBits, present);
    }

    static boolean nonDefault(ProtoPlan.FieldPlan field, Object value) {
      // Deferred strings are nonempty and only used with the empty default.
      if (value instanceof TypedRow.Utf8) return true;
      if (value instanceof BorrowedArray a) return a.size() != 0;
      if (value instanceof DataArray a) return a.size() != 0;
      if (value instanceof ExactNumber n && field.defaultValue() instanceof ExactNumber d)
        return n.decimal().compareTo(d.decimal()) != 0;
      return !value.equals(field.defaultValue());
    }
  }
}
