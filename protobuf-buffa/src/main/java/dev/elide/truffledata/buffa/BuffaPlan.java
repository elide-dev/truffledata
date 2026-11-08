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
  private final boolean compactScalars;
  private final NativeRow.@Nullable Validation nativeViews;
  private final List<ProtoPlan> anyTypes;
  private final WireInput schema;
  private final WireInput empty;

  /** ABI 9 compiled view schema, owned by this plan; zero without a native view backend. */
  private long views;

  private long slabLayout;
  private int slabCapacity;
  private final int maxSchemaBytes;

  private boolean open = true;

  BuffaPlan(BuffaWire decoder, ProtoPlan plan, int maxSchemaBytes, List<ProtoPlan> anyTypes) {
    this.decoder = decoder;
    this.maxSchemaBytes = maxSchemaBytes;
    this.plan = Objects.requireNonNull(plan);
    this.anyTypes = List.copyOf(anyTypes);
    var types = NativeSchema.types(plan, this.anyTypes, maxSchemaBytes);
    byte[] program = NativeSchema.encode(types, this.anyTypes, maxSchemaBytes);
    profile = new ReadProfile(types);
    nativeViews = NativeRow.validation(types);
    boolean compact = plan.size() > 0 && plan.size() <= 2;
    for (int i = 0; i < plan.size(); i++) {
      var field = plan.field(i);
      compact &=
          !field.repeated()
              && field.oneof() < 0
              && (field.kind() == ProtoPlan.Kind.INT32 || field.kind() == ProtoPlan.Kind.INT64)
              && field.defaultValue() instanceof Long
              && plan.schema().field(i).equals(field.contract());
    }
    compactScalars = compact;
    schema = decoder.allocate(program.length).put(program);
    try {
      empty = decoder.allocate(0);
    } catch (RuntimeException | Error failure) {
      schema.close();
      throw failure;
    }
    // Schema input is immutable for the lifetime of this plan.
    schema.retain().release();
    var backend = decoder.backend;
    if (backend != null && nativeViews != null) {
      try {
        views = backend.compileView(schema);
      } catch (RuntimeException | Error failure) {
        empty.close();
        schema.close();
        throw failure;
      }
    }
  }

  public ProtoPlan contract() {
    return plan;
  }

  private void check() {
    decoder.check();
    if (!open) throw new IllegalStateException("Native plan closed");
  }

  @TruffleBoundary
  public SlabMessage decode(WireInput input, int maxSlabBytes) {
    return decodeSlab(input, maxSlabBytes);
  }

  @TruffleBoundary
  public SlabMessage decode(byte[] input, int maxSlabBytes) {
    return decodeSlab(input, maxSlabBytes);
  }

  /** Create a native arena session; Java-only consumers use the durable decode methods. */
  @TruffleBoundary
  public SlabSession newSession(int maxSlabBytes) {
    check();
    if (decoder.backend == null)
      throw new UnsupportedOperationException("Slab sessions require native memory");
    return new SlabSession(
        decoder, plan, maxSchemaBytes, new SlabLimits(plan.maxDepth() - 1, maxSlabBytes, 0));
  }

  /** Transitional benchmark baseline; removed with the ABI 9 index implementation. */
  @Deprecated(forRemoval = true)
  @TruffleBoundary
  public TypedMessage decodeLegacy(WireInput input, int maxNativeBytes, boolean view) {
    check();
    return parse(input, empty, 0, maxNativeBytes, view);
  }

  /** Decode binary protobuf into a durable slab that retains neither its input nor this plan. */
  @TruffleBoundary
  public SlabMessage decodeSlab(WireInput input, int maxSlabBytes) {
    check();
    if (input.size() > plan.maxWireBytes()) throw new ContractViolation("Typed input budget");
    var limits = new SlabLimits(plan.maxDepth() - 1, maxSlabBytes);
    var source = input.retain();
    try {
      var backend = decoder.backend;
      if (backend == null) return javaSlab(source.bytes, limits);
      long compiled = slabLayout();
      int capacity = (int) Math.min(maxSlabBytes, Math.max(slabCapacity, 4L * input.size() + 256));
      SlabMessage result = backend.slab(compiled, input, plan, limits, capacity);
      slabCapacity = Math.max(slabCapacity, result.slabBytes());
      return result;
    } finally {
      source.release();
    }
  }

  @TruffleBoundary
  public SlabMessage decodeSlab(byte[] input, int maxSlabBytes) {
    return decode(input, new SlabLimits(plan.maxDepth() - 1, maxSlabBytes));
  }

  /** Decode with explicit slab and critical-call bounds; a zero critical bound disables pinning. */
  @TruffleBoundary
  public SlabMessage decode(byte[] input, SlabLimits limits) {
    check();
    if (limits.maxDepth() >= plan.maxDepth())
      limits = new SlabLimits(plan.maxDepth() - 1, limits.maxSlabBytes(), limits.criticalBytes());
    if (input.length > plan.maxWireBytes()) throw new ContractViolation("Typed input budget");
    int maxSlabBytes = limits.maxSlabBytes();
    if (decoder.backend == null) return SlabMessage.decode(plan, input, 0, input.length, limits);
    int capacity = (int) Math.min(maxSlabBytes, Math.max(slabCapacity, 4L * input.length + 256));
    SlabMessage result = decoder.backend.slab(slabLayout(), input, plan, limits, capacity);
    slabCapacity = Math.max(slabCapacity, result.slabBytes());
    return result;
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
      return parse(input, payload, format, nativeBytes, false);
    }
  }

  private TypedMessage parse(
      WireInput input, WireInput payload, int format, int nativeBytes, boolean view) {
    if (nativeBytes < 0 || input.size() > plan.maxWireBytes())
      throw new ContractViolation("Typed input budget");
    WireInput.Lease source = input.retain();
    TypedMessage owner = null;
    try {
      owner = new TypedMessage((format == 0 ? input : payload).retain(), format == 0);
      var backend = decoder.backend;
      if (backend == null) {
        ProtoAccess owned =
            format == 0
                ? javaSlab(source.bytes)
                : format == 1
                    ? plan.parseText(
                        StandardCharsets.UTF_8.decode(source.bytes.duplicate()).toString())
                    : plan.parseJson(
                        StandardCharsets.UTF_8.decode(source.bytes.duplicate()).toString(),
                        anyTypes);
        return owner.finish(fromJava(owner, owned), 0);
      }
      if (format == 0 && compactScalars)
        return compact(owner, backend.scalars(schema, input, plan));
      boolean useView = view && views != 0 && format == 0;
      if (!view && views != 0 && format == 0) {
        // Default decode of a view-eligible schema: native decoding writes field directories, so
        // Java builds the owned row tree directly instead of rediscovering fields. The transient
        // view is released before returning. A view that exceeds the budget only through its
        // directories falls back to the index-only route, which accepts exactly what it did.
        var directed = backend.view(views, input, plan, nativeBytes, true);
        if (directed != null) {
          try {
            if (directed.viewReady() && !STRICT)
              return owner.finish(
                  directed(
                      new DecodeContext(owner, 0, plan.limits().maxCells(), profile),
                      plan,
                      profile.hints[0],
                      directed,
                      0,
                      directed.count(),
                      0,
                      1),
                  directed.allocated);
            // Required fields, merges and strict audits keep the general semantic path.
            State state =
                new State(
                    plan,
                    profile.hints[0],
                    new DecodeContext(owner, format, plan.limits().maxCells(), profile));
            state.consume(directed, 0, directed.count());
            return owner.finish(state.finish(1), directed.allocated);
          } finally {
            directed.close();
          }
        }
      }
      var tape =
          useView
              ? Objects.requireNonNull(backend.view(views, input, plan, nativeBytes, false))
              : backend.typed(schema, input, format, payload, plan, nativeBytes);
      boolean retained = false;
      try {
        if (useView
            && (tape.viewReady()
                || NativeRow.validate(Objects.requireNonNull(nativeViews), tape))) {
          if (STRICT) {
            var audit =
                new State(
                    plan,
                    profile.hints[0],
                    new DecodeContext(owner, format, plan.limits().maxCells(), profile));
            audit.consume(tape, 0, tape.count());
            audit.finish(1);
          }
          var row = new NativeRow(owner, plan, tape, 0, tape.count(), 0);
          owner.retainIndex(tape);
          retained = true;
          return owner.finish(row, tape.allocated);
        }
        State state =
            new State(
                plan,
                profile.hints[0],
                new DecodeContext(owner, format, plan.limits().maxCells(), profile));
        state.consume(tape, 0, tape.count());
        return owner.finish(state.finish(1), tape.allocated);
      } finally {
        if (!retained) tape.close();
      }
    } catch (RuntimeException | Error failure) {
      if (owner != null) owner.close();
      throw failure;
    } finally {
      source.release();
    }
  }

  /**
   * Build one owned row from a certified view directory: no merges, no required fields and only
   * standard contracts (view eligibility), so the State staging, oneof clearing and finish pass
   * have nothing to do. Cells were charged by the native decoder under the same limit with the same
   * per-type, per-entry and byte charges as State, so Java does not charge them twice.
   */
  private static TypedRow directed(
      DecodeContext context,
      ProtoPlan plan,
      TypeProfile hints,
      BuffaWire.Tape tape,
      int start,
      int end,
      int directory,
      int depth) {
    if (depth > plan.maxDepth()) throw new ContractViolation("Typed nesting budget");
    Object[] values = Arrays.copyOf(hints.defaults, hints.defaults.length, Object[].class);
    long presence = 0;
    for (int i = 0; i < values.length; i++) {
      var field = plan.field(i);
      int word = start == end ? 0 : NativeRow.word(tape, directory + i);
      if (field.repeated()) {
        if (word == 0) {
          values[i] = context.emptyArray();
          continue;
        }
        int count = NativeRow.word(tape, word);
        Object[] items = new Object[count];
        for (int k = 0; k < count; k++)
          items[k] =
              directedValue(
                  context, plan, hints, tape, i, NativeRow.word(tape, word + 1 + k), true, depth);
        values[i] = new TypedArray(context.owner, items);
        presence |= 1L << i;
      } else if (word != 0) {
        Object value = directedValue(context, plan, hints, tape, i, word - 1, false, depth);
        values[i] = value;
        if (field.explicitPresence() || State.nonDefault(field, value)) presence |= 1L << i;
      }
    }
    return new TypedRow.ArrayRow(context.owner, plan, values, presence, State.NO_SEEN_FIELDS);
  }

  private static Object directedValue(
      DecodeContext context,
      ProtoPlan plan,
      TypeProfile hints,
      BuffaWire.Tape tape,
      int ordinal,
      int index,
      boolean repeated,
      int depth) {
    var field = plan.field(ordinal);
    var buffer = tape.bytes();
    int at = Math.multiplyExact(index, Buffa.Field.SIZE);
    long value = buffer.getLong(at + (int) BuffaFFM.Field_value_OFFSET);
    int length = buffer.getInt(at + (int) BuffaFFM.Field_length_OFFSET);
    int kind = buffer.getInt(at + (int) BuffaFFM.Field_wire_OFFSET);
    if (kind != field.kind().ordinal() + 1 || length < 0)
      throw new IllegalStateException("Native kind mismatch");
    if (field.kind().message()) {
      int next = buffer.getInt(at + (int) BuffaFFM.Field_end_OFFSET);
      return directed(
          context,
          plan.nested(ordinal),
          Objects.requireNonNull(hints.children[ordinal]),
          tape,
          index + 1,
          next,
          Math.toIntExact(value),
          depth + 1);
    }
    return switch (field.kind()) {
      case STRING -> {
        // Same deferral rule as State: singular, empty default and not yet read by this plan.
        if (!repeated && ((String) field.defaultValue()).isEmpty() && !hints.stringReads[ordinal])
          yield length == 0
              ? ""
              : new TypedRow.Utf8(
                  context.owner, Math.toIntExact(value), length, hints.stringReads, ordinal);
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
  }

  /** Fixed native scalar output avoids allocating or materializing an index. */
  private TypedMessage compact(TypedMessage owner, BuffaWire.Scalars values) {
    long present = values.present();
    long first = (present & 1) != 0 ? values.first() : (Long) plan.field(0).defaultValue();
    long second =
        plan.size() == 2
            ? ((present & 2) != 0 ? values.second() : (Long) plan.field(1).defaultValue())
            : 0;
    for (int i = 0; i < plan.size(); i++) {
      var field = plan.field(i);
      if (field.required() && (present & (1L << i)) == 0)
        throw new ContractViolation("Missing required field");
      if (!field.explicitPresence()) {
        long value = i == 0 ? first : second;
        if (value == (Long) field.defaultValue()) present &= ~(1L << i);
        else present |= 1L << i;
      }
    }
    return owner.finish(new ScalarRow(owner, plan, first, second, present), 0);
  }

  private SlabMessage javaSlab(java.nio.ByteBuffer source) {
    var bytes = source.duplicate();
    var limits = SlabLimits.forInput(plan.layout(), bytes.remaining());
    return javaSlab(bytes, limits);
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
      open = false;
      long compiled = views;
      long layout = slabLayout;
      views = 0;
      slabLayout = 0;
      try {
        try {
          if (compiled != 0) Objects.requireNonNull(decoder.backend).releaseCompiled(compiled);
        } finally {
          if (layout != 0) Objects.requireNonNull(decoder.backend).releaseLayout(layout);
        }
      } finally {
        empty.close();
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
