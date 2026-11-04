package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Schema-stable native typed view. The enclosing message owns every borrowed payload. */
@ExportLibrary(DataLibrary.class)
public final class TypedRow extends AbstractRow implements ProtoAccess {
  private final WireMessage owner;
  private final ProtoPlan plan;

  private final Object storage;

  private static final class Scalars {
    final long first;
    final long second;

    Scalars(long first, long second) {
      this.first = first;
      this.second = second;
    }
  }

  @CompilationFinal(dimensions = 1)
  private final boolean[] present;

  private final long presenceBits;

  TypedRow(
      WireMessage owner, ProtoPlan plan, Object[] values, long presenceBits, boolean[] present) {
    super(plan.schema());
    this.owner = owner;
    this.plan = plan;
    this.storage = values;
    this.present = present;
    this.presenceBits = presenceBits;
  }

  TypedRow(WireMessage owner, ProtoPlan plan, long first, long second, long presenceBits) {
    super(plan.schema());
    this.owner = owner;
    this.plan = plan;
    storage = new Scalars(first, second);
    this.presenceBits = presenceBits;
    present = NO_PRESENCE_ARRAY;
  }

  private static final boolean[] NO_PRESENCE_ARRAY = new boolean[0];

  private long scalar(Scalars values, int ordinal) {
    Objects.checkIndex(ordinal, descriptor.size());
    return ordinal == 0 ? values.first : values.second;
  }

  @Override
  public long getLong(int ordinal) {
    owner.check();
    if (storage instanceof Scalars scalars) return scalar(scalars, ordinal);
    Object value = ((Object[]) storage)[ordinal];
    if (value instanceof Long number) return number;
    throw new ContractViolation("Expected INT64");
  }

  public ProtoPlan plan() {
    return plan;
  }

  public boolean has(int ordinal) {
    owner.check();
    Objects.checkIndex(ordinal, descriptor.size());
    return present(ordinal);
  }

  @Override
  public Object get(int ordinal) {
    owner.check();
    Objects.checkIndex(ordinal, descriptor.size());
    return resolved(ordinal);
  }

  @Override
  public int getSize(int ordinal) {
    Object value = get(ordinal);
    if (value instanceof TypedArray array) return array.size();
    if (value instanceof BorrowedBytes bytes) return bytes.size();
    if (value instanceof DataArray array) return array.size();
    throw new ContractViolation("Expected array");
  }

  private boolean present(int ordinal) {
    return present.length == 0 ? (presenceBits & (1L << ordinal)) != 0 : present[ordinal];
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(
      int index, @Cached.Shared("owner") @Cached("create()") WireMessage.CheckOpenNode check) {
    check.execute(owner);
    return resolved(index);
  }

  @ExportMessage
  long readLong(
      int index, @Cached.Shared("owner") @Cached("create()") WireMessage.CheckOpenNode check)
      throws UnexpectedResultException {
    check.execute(owner);
    if (storage instanceof Scalars scalars) return scalar(scalars, index);
    Object value = ((Object[]) storage)[index];
    if (value instanceof Long number) return number;
    throw new UnexpectedResultException(resolved(index));
  }

  @ExportMessage
  double readDouble(
      int index, @Cached.Shared("owner") @Cached("create()") WireMessage.CheckOpenNode check)
      throws UnexpectedResultException {
    check.execute(owner);
    Object value = resolved(index);
    if (value instanceof Double number) return number;
    throw new UnexpectedResultException(value);
  }

  @ExportMessage
  boolean readBoolean(
      int index, @Cached.Shared("owner") @Cached("create()") WireMessage.CheckOpenNode check)
      throws UnexpectedResultException {
    check.execute(owner);
    Object value = resolved(index);
    if (value instanceof Boolean flag) return flag;
    throw new UnexpectedResultException(value);
  }

  private Object resolved(int index) {
    if (storage instanceof Scalars scalars) return scalar(scalars, index);
    Object value = ((Object[]) storage)[index];
    return value instanceof Utf8 text ? text.value(owner) : value;
  }

  /** Internal placeholder: never exported, and only backed by sealed, native-validated UTF-8. */
  static final class Utf8 {
    final boolean[] readProfile;
    final int ordinal;
    final int offset;
    final int length;
    private @Nullable String cached;

    Utf8(WireMessage owner, int offset, int length, boolean[] readProfile, int ordinal) {
      owner.lease.check();
      Objects.checkFromIndexSize(offset, length, owner.lease.bytes.capacity());
      Objects.checkIndex(ordinal, readProfile.length);
      this.readProfile = readProfile;
      this.ordinal = ordinal;
      this.offset = offset;
      this.length = length;
    }

    String value(WireMessage owner) {
      String result = cached;
      return result == null ? decode(owner) : result;
    }

    @TruffleBoundary
    private String decode(WireMessage owner) {
      owner.check();
      byte[] bytes = new byte[length];
      owner.lease.bytes.get(offset, bytes);
      String result = new String(bytes, StandardCharsets.UTF_8);
      readProfile[ordinal] = true;
      cached = result;
      return result;
    }
  }

  @TruffleBoundary
  ProtoRow snapshot() {
    owner.check();
    var builder = plan.newBuilder();
    for (int i = 0; i < descriptor.size(); i++) if (present(i)) builder.set(i, detach(resolved(i)));
    return builder.build();
  }

  static Object detach(Object value) {
    if (value instanceof BorrowedArray array) return array.snapshot();
    if (value instanceof TypedRow row) return row.snapshot();
    return value;
  }
}
