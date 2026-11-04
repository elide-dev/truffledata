package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.generated.*;
import dev.elide.truffledata.protobuf.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reusable schema program shared by native binary, textproto and ProtoJSON ingestion. */
public final class BuffaPlan implements AutoCloseable {
  private final BuffaWire decoder;
  private final ProtoPlan plan;
  private final ReadProfile profile;
  private final List<ProtoPlan> anyTypes;
  private final WireInput schema;
  private final WireInput empty;
  private boolean open = true;

  BuffaPlan(BuffaWire decoder, ProtoPlan plan, int maxSchemaBytes, List<ProtoPlan> anyTypes) {
    this.decoder = decoder;
    this.plan = Objects.requireNonNull(plan);
    this.anyTypes = List.copyOf(anyTypes);
    var types = NativeSchema.types(plan, this.anyTypes, maxSchemaBytes);
    byte[] program = NativeSchema.encode(types, this.anyTypes, maxSchemaBytes);
    profile = new ReadProfile(types);
    schema = decoder.allocate(program.length).put(program);
    try {
      empty = decoder.allocate(0);
    } catch (RuntimeException | Error failure) {
      schema.close();
      throw failure;
    }
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

  @TruffleBoundary
  public TypedMessage decode(WireInput input, int maxNativeBytes) {
    check();
    return parse(input, empty, 0, maxNativeBytes);
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
    if (text.length() > plan.maxWireBytes() / 2) throw new ContractViolation("Text input budget");
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
    WireMessage owner = null;
    try {
      owner = new WireMessage((format == 0 ? input : payload).retain(), 0, plan.limits());
      var backend = decoder.backend;
      if (backend == null) {
        ProtoRow owned =
            format == 0
                ? plan.decode(source.bytes)
                : format == 1
                    ? plan.parseText(
                        StandardCharsets.UTF_8.decode(source.bytes.duplicate()).toString())
                    : plan.parseJson(
                        StandardCharsets.UTF_8.decode(source.bytes.duplicate()).toString(),
                        anyTypes);
        return new TypedMessage(owner, fromJava(owner, owned), format == 0, 0);
      }
      try (var tape = backend.typed(schema, input, format, payload, plan, nativeBytes)) {
        State state =
            new State(plan, new DecodeContext(owner, format, plan.limits().maxCells(), profile));
        state.consume(tape, 0, tape.count);
        return new TypedMessage(owner, state.finish(1), format == 0, tape.allocated);
      }
    } catch (RuntimeException | Error failure) {
      if (owner != null) owner.close();
      throw failure;
    } finally {
      source.release();
    }
  }

  private static TypedRow fromJava(WireMessage owner, ProtoRow row) {
    Object[] values = new Object[row.plan().size()];
    boolean[] present = new boolean[values.length];
    var data = DataLibrary.factory().getUncached();
    for (int i = 0; i < values.length; i++) {
      values[i] = javaValue(owner, data.read(row, i));
      present[i] = row.has(i);
    }
    return new TypedRow(owner, row.plan(), values, present);
  }

  private static Object javaValue(WireMessage owner, Object value) {
    if (value instanceof ProtoRow row) return fromJava(owner, row);
    if (value instanceof DataArray array && !array.isBytes()) {
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
      empty.close();
      schema.close();
    }
  }

  /** Thread-confined, bounded hints: reading a string makes later values eager for that field. */
  private static final class ReadProfile {
    final List<ProtoPlan> types;
    final Map<String, boolean[]> stringReads;

    ReadProfile(List<ProtoPlan> types) {
      this.types = types;
      Map<String, boolean[]> reads = new HashMap<>();
      for (ProtoPlan type : types) reads.put(type.name(), new boolean[type.size()]);
      stringReads = Map.copyOf(reads);
    }
  }

  /** Shared per-decode state; nested messages must charge one cumulative cell budget. */
  private static final class DecodeContext {
    final WireMessage owner;
    final int format;
    final ReadProfile profile;
    long cells;

    DecodeContext(WireMessage owner, int format, long cells, ReadProfile profile) {
      this.owner = owner;
      this.format = format;
      this.cells = cells;
      this.profile = profile;
    }

    void charge(long count) {
      if (count < 0 || count > cells)
        throw new ContractViolation("Typed materialization cell budget");
      cells -= count;
    }
  }

  private static final class State {
    private static final boolean[] NO_SEEN_FIELDS = new boolean[0];
    private static final Object[] EMPTY_ITEMS = new Object[0];
    final ProtoPlan plan;
    final DecodeContext context;
    final boolean[] stringReads;
    final Object[] values;
    final boolean[] present;
    final boolean[] seen;

    State(ProtoPlan plan, DecodeContext context) {
      this.plan = plan;
      this.context = context;
      stringReads = Objects.requireNonNull(context.profile.stringReads.get(plan.name()));
      context.charge(plan.size());
      values = new Object[plan.size()];
      present = new boolean[values.length];
      // Binary protobuf permits duplicate fields; only text formats track duplicates.
      seen = context.format == 0 ? NO_SEEN_FIELDS : new boolean[values.length];
      for (int i = 0; i < values.length; i++) values[i] = initial(plan.field(i));
    }

    static Object initial(ProtoPlan.FieldPlan field) {
      return field.repeated() ? List.of() : field.defaultValue();
    }

    void select(int ordinal) {
      var field = plan.field(ordinal);
      if (field.oneof() >= 0)
        for (int i = 0; i < values.length; i++) {
          if (i != ordinal && plan.field(i).oneof() == field.oneof()) {
            if (context.format != 0 && present[i])
              throw new ContractViolation("Multiple oneof members");
            values[i] = initial(plan.field(i));
            present[i] = false;
          }
        }
    }

    @SuppressWarnings("unchecked")
    void consume(BuffaWire.Tape tape, int start, int end) {
      for (int index = start; index < end; ) {
        context.charge(1);
        int at = index * Buffa.Field.SIZE;
        int ordinal = tape.bytes.getInt(at + (int) BuffaFFM.Field_number_OFFSET) - 1;
        int kind = tape.bytes.getInt(at + (int) BuffaFFM.Field_wire_OFFSET);
        long value = tape.bytes.getLong(at + (int) BuffaFFM.Field_value_OFFSET);
        int length = Math.toIntExact(tape.bytes.getLong(at + (int) BuffaFFM.Field_length_OFFSET));
        int next = Math.toIntExact(tape.bytes.getLong(at + (int) BuffaFFM.Field_end_OFFSET));
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
          State payload = new State(target, context);
          payload.consume(tape, index + 1, next);
          byte[] encoded = target.encode(payload.finish(1).snapshot());
          context.charge(encoded.length);
          values[ordinal] = DataArray.copyOfBytes(encoded);
          present[ordinal] = encoded.length != 0;
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
                  : new State(plan.nested(ordinal), context);
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
                      && !stringReads[ordinal])
                    yield length == 0
                        ? ""
                        : new TypedRow.Utf8(
                            context.owner, Math.toIntExact(value), length, stringReads, ordinal);
                  context.owner.lease.check();
                  int offset = Math.toIntExact(value);
                  Objects.checkFromIndexSize(offset, length, context.owner.lease.bytes.capacity());
                  byte[] utf8 = new byte[length];
                  context.owner.lease.bytes.get(offset, utf8);
                  yield new String(utf8, StandardCharsets.UTF_8);
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
          List<Object> items;
          if (values[ordinal] instanceof ArrayList<?>) items = (List<Object>) values[ordinal];
          else {
            items = new ArrayList<>();
            values[ordinal] = items;
          }
          items.add(decoded);
        } else {
          select(ordinal);
          values[ordinal] = decoded;
        }
        present[ordinal] = true;
        index = next;
      }
    }

    TypedRow finish(int depth) {
      if (depth > plan.maxDepth()) throw new ContractViolation("Typed nesting budget");
      if (plan.definition().mapEntry()
          && plan.field(1).kind().message()
          && values[1] == Missing.INSTANCE) {
        values[1] = new State(plan.nested(1), context);
        present[1] = true;
      }
      for (int i = 0; i < values.length; i++) {
        var field = plan.field(i);
        if (values[i] instanceof State child) values[i] = child.finish(depth + 1);
        else if (values[i] instanceof List<?> list) {
          Object[] items = list.isEmpty() ? EMPTY_ITEMS : new Object[list.size()];
          Map<Object, Object> keys = field.map() && !list.isEmpty() ? new LinkedHashMap<>() : null;
          for (int j = 0; j < items.length; j++) {
            Object item = list.get(j);
            items[j] = item instanceof State child ? child.finish(depth + 1) : item;
            if (keys != null) {
              Object key = DataLibrary.factory().getUncached().read(items[j], 0);
              if (key instanceof ExactNumber number) key = number.decimal().toBigIntegerExact();
              if (keys.put(key, items[j]) != null && context.format == 2)
                throw new ContractViolation("Duplicate JSON map key");
            }
          }
          values[i] = new TypedArray(context.owner, keys != null ? keys.values().toArray() : items);
        }
        if (!field.explicitPresence()) present[i] = nonDefault(field, values[i]);
        if (field.required() && !present[i]) throw new ContractViolation("Missing required field");
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
      return new TypedRow(context.owner, plan, values, present);
    }

    private static boolean nonDefault(ProtoPlan.FieldPlan field, Object value) {
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
