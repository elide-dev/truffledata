package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.*;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Schema-stable native typed view. The enclosing message owns every borrowed payload. */
@ExportLibrary(DataLibrary.class)
public final class TypedRow extends AbstractRow {
  private final WireMessage owner;
  private final ProtoPlan plan;

  @CompilationFinal(dimensions = 1)
  private final Object[] values;

  @CompilationFinal(dimensions = 1)
  private final boolean[] present;

  private final long presenceBits;

  TypedRow(
      WireMessage owner, ProtoPlan plan, Object[] values, long presenceBits, boolean[] present) {
    super(plan.schema());
    this.owner = owner;
    this.plan = plan;
    this.values = values;
    this.present = present;
    this.presenceBits = presenceBits;
  }

  public ProtoPlan plan() {
    return plan;
  }

  public boolean has(int ordinal) {
    owner.check();
    Objects.checkIndex(ordinal, values.length);
    return present(ordinal);
  }

  private boolean present(int ordinal) {
    return present.length == 0 ? (presenceBits & (1L << ordinal)) != 0 : present[ordinal];
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(int index, @Cached("create()") WireMessage.CheckOpenNode check) {
    check.execute(owner);
    return resolved(index);
  }

  private Object resolved(int index) {
    Object value = values[index];
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
    for (int i = 0; i < values.length; i++) if (present(i)) builder.set(i, detach(resolved(i)));
    return builder.build();
  }

  static Object detach(Object value) {
    if (value instanceof BorrowedArray array) return array.snapshot();
    if (value instanceof TypedRow row) return row.snapshot();
    return value;
  }
}
