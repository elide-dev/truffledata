package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;
import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.generated.Buffa;
import dev.elide.truffledata.buffa.generated.BuffaFFM;
import dev.elide.truffledata.protobuf.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** Binary view over an owned native index. Java child objects are created only when accessed. */
final class NativeRow extends TypedRow {
  private static final Object[] NO_VALUES = new Object[0];
  private static final boolean[] NO_PRESENCE = new boolean[0];
  private final BuffaWire.Tape tape;
  private final int start;
  private final int end;

  @CompilationFinal(dimensions = 1)
  private int @Nullable [] offsets;

  // Cache contents are mutable and must not be treated as compilation-final.
  @CompilationFinal(dimensions = 0)
  private Object @Nullable [] cache;

  NativeRow(ViewOwner owner, ProtoPlan plan, BuffaWire.Tape tape, int start, int end) {
    super(owner, plan, NO_VALUES, 0, NO_PRESENCE);
    this.tape = tape;
    this.start = start;
    this.end = end;
  }

  static boolean supports(List<ProtoPlan> types) {
    for (var plan : types) {
      if (plan.size() > Long.SIZE || plan.definition().mapEntry()) return false;
      for (int i = 0; i < plan.size(); i++) {
        var field = plan.field(i);
        if (field.map()
            || (field.required() && !field.explicitPresence())
            || !plan.schema().field(i).equals(field.contract())) return false;
        // Proto3 optional fields have singleton synthetic oneofs, with ordinary presence semantics.
        if (field.oneof() >= 0)
          for (int j = 0; j < i; j++) if (plan.field(j).oneof() == field.oneof()) return false;
      }
    }
    return true;
  }

  /** Validate without constructing rows, values, repeated arrays, or deferred-string objects. */
  static boolean validate(ProtoPlan plan, BuffaWire.Tape tape, ViewOwner owner) {
    return validate(plan, tape, owner, 0, tape.count, 1, new long[] {plan.limits().maxCells()});
  }

  private static boolean validate(
      ProtoPlan plan,
      BuffaWire.Tape tape,
      ViewOwner owner,
      int start,
      int end,
      int depth,
      long[] cells) {
    if (depth > plan.maxDepth()) throw new ContractViolation("Typed nesting budget");
    long seen = 0;
    // Detect singular-message merges before validating a potentially incomplete first fragment.
    for (int i = start; i < end; ) {
      int ordinal = ordinal(tape, i), next = next(tape, i), length = length(tape, i);
      if (ordinal < 0 || ordinal >= plan.size() || next <= i || next > end || length < 0)
        throw new IllegalStateException("Invalid native typed index");
      var field = plan.field(ordinal);
      if (kind(tape, i) != field.kind().ordinal() + 1)
        throw new IllegalStateException("Native kind mismatch");
      if (!field.kind().message() && next != i + 1)
        throw new IllegalStateException("Invalid scalar subtree");
      long bit = 1L << ordinal;
      if (field.kind().message() && !field.repeated() && (seen & bit) != 0) return false;
      seen |= bit;
      i = next;
    }
    charge(cells, plan.size());
    for (int i = 0; i < plan.size(); i++)
      if (plan.field(i).required() && (seen & (1L << i)) == 0)
        throw new ContractViolation("Missing required field");
    for (int i = start; i < end; ) {
      charge(cells, 1);
      int ordinal = ordinal(tape, i), next = next(tape, i);
      var field = plan.field(ordinal);
      if (field.kind().message()) {
        if (!validate(plan.nested(ordinal), tape, owner, i + 1, next, depth + 1, cells))
          return false;
      } else if (field.kind() == ProtoPlan.Kind.STRING || field.kind() == ProtoPlan.Kind.BYTES) {
        Objects.checkFromIndexSize(
            Math.toIntExact(bits(tape, i)), length(tape, i), owner.lease.bytes.capacity());
        if (field.kind() == ProtoPlan.Kind.BYTES) charge(cells, length(tape, i));
      }
      i = next;
    }
    return true;
  }

  private static void charge(long[] cells, long count) {
    if (count < 0 || count > cells[0])
      throw new ContractViolation("Typed materialization cell budget");
    cells[0] -= count;
  }

  private static int ordinal(BuffaWire.Tape tape, int index) {
    return tape.bytes.getInt(index * Buffa.Field.SIZE + (int) BuffaFFM.Field_number_OFFSET) - 1;
  }

  private static int kind(BuffaWire.Tape tape, int index) {
    return tape.bytes.getInt(index * Buffa.Field.SIZE + (int) BuffaFFM.Field_wire_OFFSET);
  }

  private static long bits(BuffaWire.Tape tape, int index) {
    return tape.bytes.getLong(index * Buffa.Field.SIZE + (int) BuffaFFM.Field_value_OFFSET);
  }

  private static int length(BuffaWire.Tape tape, int index) {
    return tape.bytes.getInt(index * Buffa.Field.SIZE + (int) BuffaFFM.Field_length_OFFSET);
  }

  private static int next(BuffaWire.Tape tape, int index) {
    return tape.bytes.getInt(index * Buffa.Field.SIZE + (int) BuffaFFM.Field_end_OFFSET);
  }

  private int[] offsets() {
    var result = offsets;
    return result == null ? index() : result;
  }

  @TruffleBoundary
  private int[] index() {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    int[] result = new int[plan().size()];
    Arrays.fill(result, -1);
    for (int i = start; i < end; i = next(tape, i)) result[ordinal(tape, i)] = i;
    offsets = result;
    return result;
  }

  boolean integer(int ordinal) {
    return descriptor.field(ordinal).kind() == Field.Kind.INT64;
  }

  ByteBuffer indexBuffer() {
    return tape.bytes;
  }

  long integerValue(int ordinal) {
    return integerValue(ordinal, tape.bytes);
  }

  long integerValue(int ordinal, ByteBuffer buffer) {
    int index = offsets()[ordinal];
    var field = plan().field(ordinal);
    if (index < 0) return (Long) field.defaultValue();
    long value = buffer.getLong(index * Buffa.Field.SIZE + (int) BuffaFFM.Field_value_OFFSET);
    return switch (field.kind()) {
      case INT32, SINT32, SFIXED32, ENUM -> (long) (int) value;
      case UINT32, FIXED32 -> value & 0xffffffffL;
      default -> value;
    };
  }

  Object value(int ordinal) {
    return value(ordinal, tape.bytes);
  }

  Object value(int ordinal, ByteBuffer buffer) {
    var field = plan().field(ordinal);
    int index = offsets()[ordinal];
    if (!field.repeated() && index < 0) return field.defaultValue();
    if (!field.repeated()
        && !field.kind().message()
        && field.kind() != ProtoPlan.Kind.STRING
        && field.kind() != ProtoPlan.Kind.UINT64
        && field.kind() != ProtoPlan.Kind.FIXED64
        && field.kind() != ProtoPlan.Kind.BYTES)
      return scalar(
          field, buffer.getLong(index * Buffa.Field.SIZE + (int) BuffaFFM.Field_value_OFFSET));
    var values = cache;
    Object result = values == null ? null : values[ordinal];
    return result == null ? materialize(ordinal, index) : result;
  }

  @TruffleBoundary
  private Object materialize(int ordinal, int index) {
    var values = cache;
    if (values == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      cache = values = new Object[plan().size()];
    }
    var field = plan().field(ordinal);
    Object result = field.repeated() ? new NativeArray(this, ordinal) : decode(ordinal, index);
    values[ordinal] = result;
    return result;
  }

  private Object decode(int ordinal, int index) {
    var field = plan().field(ordinal);
    if (field.kind().message())
      return new NativeRow(owner, plan().nested(ordinal), tape, index + 1, next(tape, index));
    long value = bits(tape, index);
    return switch (field.kind()) {
      case STRING -> owner.utf8(Math.toIntExact(value), length(tape, index));
      case BYTES -> new BorrowedBytes(owner, Math.toIntExact(value), length(tape, index));
      default -> scalar(field, value);
    };
  }

  private static Object scalar(ProtoPlan.FieldPlan field, long value) {
    return switch (field.kind()) {
      case DOUBLE -> Double.longBitsToDouble(value);
      case FLOAT -> (double) Float.intBitsToFloat((int) value);
      case BOOL -> value != 0;
      case INT32, SINT32, SFIXED32, ENUM -> (long) (int) value;
      case UINT32, FIXED32 -> value & 0xffffffffL;
      case UINT64, FIXED64 -> unsigned(value);
      default -> value;
    };
  }

  @TruffleBoundary
  private static Object unsigned(long value) {
    return Values.normalize(
        value >= 0
            ? BigInteger.valueOf(value)
            : BigInteger.valueOf(value & Long.MAX_VALUE).setBit(63));
  }

  boolean present(int ordinal) {
    int index = offsets()[ordinal];
    if (index < 0) return false;
    var field = plan().field(ordinal);
    if (field.explicitPresence() || field.repeated() || field.kind().message()) return true;
    if (field.kind() == ProtoPlan.Kind.BYTES) return length(tape, index) != 0;
    if (field.kind() == ProtoPlan.Kind.STRING) {
      if (field.defaultValue().equals("")) return length(tape, index) != 0;
      return !value(ordinal).equals(field.defaultValue());
    }
    Object value = scalar(field, bits(tape, index));
    if (value instanceof ExactNumber number && field.defaultValue() instanceof ExactNumber fallback)
      return number.decimal().compareTo(fallback.decimal()) != 0;
    return !value.equals(field.defaultValue());
  }

  @ExportLibrary(InteropLibrary.class)
  static final class NativeArray implements BorrowedArray {
    private final NativeRow row;
    private final int ordinal;
    private final int count;

    @CompilationFinal(dimensions = 1)
    private int @Nullable [] entries;

    @CompilationFinal(dimensions = 0)
    private Object @Nullable [] cache;

    NativeArray(NativeRow row, int ordinal) {
      this.row = row;
      this.ordinal = ordinal;
      int n = 0;
      for (int i = row.start; i < row.end; i = next(row.tape, i))
        if (ordinal(row.tape, i) == ordinal) n++;
      count = n;
    }

    public int size() {
      row.owner.check();
      return count;
    }

    public Object element(int index) {
      row.owner.check();
      Objects.checkIndex(index, count);
      var values = cache;
      Object result = values == null ? null : values[index];
      return result == null ? materialize(index) : result;
    }

    @TruffleBoundary
    private Object materialize(int index) {
      var positions = entries;
      var values = cache;
      if (positions == null || values == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        entries = positions = new int[count];
        cache = values = new Object[count];
        int n = 0;
        for (int i = row.start; i < row.end; i = next(row.tape, i))
          if (ordinal(row.tape, i) == ordinal) positions[n++] = i;
      }
      Object result = row.decode(ordinal, positions[index]);
      values[index] = result;
      return result;
    }

    @TruffleBoundary
    public DataArray snapshot() {
      row.owner.check();
      Object[] result = new Object[count];
      for (int i = 0; i < count; i++) result[i] = TypedRow.detach(element(i));
      return new DataArray(result);
    }

    @ExportMessage
    boolean hasArrayElements() {
      return true;
    }

    @ExportMessage
    long getArraySize() {
      return size();
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
      return index >= 0 && index < size();
    }

    @ExportMessage
    Object readArrayElement(long index) throws InvalidArrayIndexException {
      if (!isArrayElementReadable(index)) throw InvalidArrayIndexException.create(index);
      return element((int) index);
    }
  }
}
