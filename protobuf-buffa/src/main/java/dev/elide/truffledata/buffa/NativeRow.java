package dev.elide.truffledata.buffa;

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
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

/** Binary view over an owned native index. Java child objects are created only when accessed. */
final class NativeRow extends TypedRow {
  private static final ByteBuffer POINTER_PROFILE = ByteBuffer.allocate(0);
  private static final long[] PACKED_OFFSETS = new long[0];
  private final BuffaWire.Tape tape;
  private final int start;
  private final int end;

  private long @Nullable [] offsets;

  private long packedOffsets;

  // Host code can populate these after guest compilation; references and contents are mutable.
  private Object @Nullable [] cache;
  private int firstOrdinal = -1;
  private @Nullable Object firstValue;

  NativeRow(ViewOwner owner, ProtoPlan plan, BuffaWire.Tape tape, int start, int end) {
    super(owner, plan);
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

  /** Immutable schema facts, linked once even for recursive message graphs. */
  static final class Validation {
    final long required;
    final long singularMessages;
    final @Nullable Validation[] children;

    Validation(ProtoPlan plan) {
      children = new Validation[plan.size()];
      long required = 0, singular = 0;
      for (int i = 0; i < plan.size(); i++) {
        var field = plan.field(i);
        if (field.required()) required |= 1L << i;
        if (field.kind().message() && !field.repeated()) singular |= 1L << i;
      }
      this.required = required;
      singularMessages = singular;
    }
  }

  static @Nullable Validation validation(List<ProtoPlan> types) {
    if (!supports(types)) return null;
    Map<String, Validation> profiles = new HashMap<>();
    for (var plan : types) profiles.put(plan.name(), new Validation(plan));
    for (var plan : types) {
      var profile = Objects.requireNonNull(profiles.get(plan.name()));
      for (int i = 0; i < plan.size(); i++)
        if (plan.field(i).kind().message())
          profile.children[i] = Objects.requireNonNull(profiles.get(plan.nested(i).name()));
    }
    return Objects.requireNonNull(profiles.get(types.getFirst().name()));
  }

  private static final int MERGE = 1;
  private static final int MISSING = 2;

  /**
   * The native binary decoder already checks depth, cells, strings, bytes and index construction.
   * Check the remaining view semantics once, without allocating a second budget or row tree.
   * Missing required fields are deferred: incomplete fragments may merge into a valid message.
   * Strict mode independently audits the index through the eager decoder before exposing a view.
   */
  static boolean validate(Validation profile, BuffaWire.Tape tape) {
    int result = validate(profile, tape, 0, tape.count());
    if (result == MERGE) return false;
    if (result == MISSING) throw new ContractViolation("Missing required field");
    return true;
  }

  private static int validate(Validation profile, BuffaWire.Tape tape, int start, int end) {
    long seen = 0;
    int result = 0;
    for (int i = start; i < end; ) {
      long link = link(tape, i);
      int ordinal = (int) link, next = (int) (link >>> 32);
      if (ordinal < 0 || ordinal >= profile.children.length || next <= i || next > end)
        throw new IllegalStateException("Invalid native typed index");
      long bit = 1L << ordinal;
      if ((seen & bit & profile.singularMessages) != 0) return MERGE;
      seen |= bit;
      var child = profile.children[ordinal];
      if (child != null) {
        int nested = validate(child, tape, i + 1, next);
        if (nested == MERGE) return MERGE;
        result |= nested;
      }
      i = next;
    }
    if ((seen & profile.required) != profile.required) result |= MISSING;
    return result;
  }

  /** Callers check the scoped owner before accessing a row; each native load also checks bounds. */
  private static Pointer entry(BuffaWire.Tape tape, int index) {
    Objects.checkIndex(index, tape.count());
    return WordFactory.pointer(tape.nativeAddress + (long) index * Buffa.Field.SIZE);
  }

  /** Read the two traversal fields under one native-entry bounds check. */
  private static long link(BuffaWire.Tape tape, int index) {
    int ordinal, next;
    if (ImageInfo.inImageCode() && tape.nativeAddress != 0) {
      var entry = entry(tape, index);
      ordinal = Buffa.Field.number(entry) - 1;
      next = Buffa.Field.end(entry);
    } else {
      int offset = index * Buffa.Field.SIZE;
      ordinal = tape.bytes().getInt(offset + (int) BuffaFFM.Field_number_OFFSET) - 1;
      next = tape.bytes().getInt(offset + (int) BuffaFFM.Field_end_OFFSET);
    }
    return ((long) next << 32) | Integer.toUnsignedLong(ordinal);
  }

  private static long bits(BuffaWire.Tape tape, int index) {
    return bits(tape, index, profileBuffer(tape));
  }

  private static long bits(BuffaWire.Tape tape, int index, ByteBuffer buffer) {
    if (ImageInfo.inImageCode() && tape.nativeAddress != 0)
      return Buffa.Field.value(entry(tape, index));
    int offset = index * Buffa.Field.SIZE + (int) BuffaFFM.Field_value_OFFSET;
    if (!ImageInfo.inImageCode()) return FfmBackend.readIndexLong(buffer, offset);
    return buffer.getLong(offset);
  }

  private static int length(BuffaWire.Tape tape, int index) {
    if (ImageInfo.inImageCode() && tape.nativeAddress != 0)
      return Buffa.Field.length(entry(tape, index));
    return tape.bytes().getInt(index * Buffa.Field.SIZE + (int) BuffaFFM.Field_length_OFFSET);
  }

  private static int next(BuffaWire.Tape tape, int index) {
    if (ImageInfo.inImageCode() && tape.nativeAddress != 0)
      return Buffa.Field.end(entry(tape, index));
    return tape.bytes().getInt(index * Buffa.Field.SIZE + (int) BuffaFFM.Field_end_OFFSET);
  }

  private int offset(int ordinal) {
    var result = offsets;
    if (result == null) result = index();
    if (result.length != 0) return (int) result[ordinal] - 1;
    int position = (int) (packedOffsets >>> (ordinal * 4)) & 15;
    return position == 0 ? -1 : start + position - 1;
  }

  @TruffleBoundary
  private long[] index() {
    // Four-bit relative positions cover up to sixteen schema fields and fifteen index entries.
    // Zero denotes absence. Unique occurrences also imply a count of one. Duplicates and larger
    // spans use a directory containing both the last position and the occurrence count.
    if (plan().size() <= 16 && end - start <= 15) {
      long packed = 0;
      int i = start;
      for (; i < end; ) {
        long link = link(tape, i);
        int shift = (int) link * 4;
        if ((packed & (15L << shift)) != 0) break;
        packed |= (long) (i - start + 1) << shift;
        i = (int) (link >>> 32);
      }
      if (i == end) {
        packedOffsets = packed;
        offsets = PACKED_OFFSETS;
        return PACKED_OFFSETS;
      }
    }
    long[] result = new long[plan().size()];
    for (int i = start; i < end; ) {
      long link = link(tape, i);
      int ordinal = (int) link;
      long count = (result[ordinal] >>> 32) + 1;
      result[ordinal] = (count << 32) | (i + 1L);
      i = (int) (link >>> 32);
    }
    offsets = result;
    return result;
  }

  /** Caller checks owner and ordinal; counts come from the same traversal as field positions. */
  int repeatedSize(int ordinal) {
    var directory = offsets;
    if (directory == null) directory = index();
    if (directory.length != 0) return (int) (directory[ordinal] >>> 32);
    return ((packedOffsets >>> (ordinal * 4)) & 15) == 0 ? 0 : 1;
  }

  boolean integer(int ordinal) {
    return descriptor.field(ordinal).kind() == Field.Kind.INT64;
  }

  private static ByteBuffer profileBuffer(BuffaWire.Tape tape) {
    // SVM pointer loads do not use a buffer. Keep JVM exact-class profiling without allocating
    // an unused DirectByteBuffer solely to supply this argument on SVM.
    return ImageInfo.inImageCode() && tape.nativeAddress != 0 ? POINTER_PROFILE : tape.bytes();
  }

  ByteBuffer indexBuffer() {
    return profileBuffer(tape);
  }

  long integerValue(int ordinal) {
    return integerValue(ordinal, profileBuffer(tape));
  }

  long integerValue(int ordinal, ByteBuffer buffer) {
    int index = offset(ordinal);
    var field = plan().field(ordinal);
    if (index < 0) return (Long) field.defaultValue();
    long value = bits(tape, index, buffer);
    return switch (field.kind()) {
      case INT32, SINT32, SFIXED32, ENUM -> (long) (int) value;
      case UINT32, FIXED32 -> value & 0xffffffffL;
      default -> value;
    };
  }

  private @Nullable Object cachedValue(int ordinal) {
    var values = cache;
    return values == null ? (firstOrdinal == ordinal ? firstValue : null) : values[ordinal];
  }

  Object value(int ordinal) {
    Object cached = cachedValue(ordinal);
    return cached != null ? cached : uncachedValue(ordinal, profileBuffer(tape));
  }

  Object value(int ordinal, ByteBuffer buffer) {
    Object cached = cachedValue(ordinal);
    return cached != null ? cached : uncachedValue(ordinal, buffer);
  }

  private Object uncachedValue(int ordinal, ByteBuffer buffer) {
    var field = plan().field(ordinal);
    int index = offset(ordinal);
    if (!field.repeated() && index < 0) return field.defaultValue();
    if (!field.repeated()
        && !field.kind().message()
        && field.kind() != ProtoPlan.Kind.STRING
        && field.kind() != ProtoPlan.Kind.UINT64
        && field.kind() != ProtoPlan.Kind.FIXED64
        && field.kind() != ProtoPlan.Kind.BYTES) return scalar(field, bits(tape, index, buffer));
    return materialize(ordinal, index);
  }

  @TruffleBoundary
  private Object materialize(int ordinal, int index) {
    var field = plan().field(ordinal);
    Object result = field.repeated() ? new NativeArray(this, ordinal) : decode(ordinal, index);
    var values = cache;
    if (values == null) {
      Object first = firstValue;
      if (first == null) {
        firstOrdinal = ordinal;
        firstValue = result;
        return result;
      }
      values = new Object[plan().size()];
      values[firstOrdinal] = first;
      cache = values;
      firstValue = null;
      firstOrdinal = -1;
    }
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
    int index = offset(ordinal);
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

    private int @Nullable [] entries;

    private Object @Nullable [] cache;

    NativeArray(NativeRow row, int ordinal) {
      this.row = row;
      this.ordinal = ordinal;
      count = row.repeatedSize(ordinal);
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
        entries = positions = new int[count];
        cache = values = new Object[count];
        int n = 0;
        for (int i = row.start; i < row.end; ) {
          long link = link(row.tape, i);
          if ((int) link == ordinal) positions[n++] = i;
          i = (int) (link >>> 32);
        }
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
