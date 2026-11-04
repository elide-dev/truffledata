package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.*;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.*;

/** Schema-stable native typed view. The enclosing message owns every borrowed payload. */
@ExportLibrary(DataLibrary.class)
public final class TypedRow extends AbstractRow {
  private final WireMessage owner;
  private final ProtoPlan plan;

  @CompilationFinal(dimensions = 1)
  private final Object[] values;

  @CompilationFinal(dimensions = 1)
  private final boolean[] present;

  TypedRow(WireMessage owner, ProtoPlan plan, Object[] values, boolean[] present) {
    super(plan.schema());
    this.owner = owner;
    this.plan = plan;
    this.values = values;
    this.present = present;
  }

  public ProtoPlan plan() {
    return plan;
  }

  public boolean has(int ordinal) {
    owner.check();
    return present[ordinal];
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(int index, @Cached("create()") WireMessage.CheckOpenNode check) {
    check.execute(owner);
    return values[index];
  }

  @TruffleBoundary
  ProtoRow snapshot() {
    owner.check();
    var builder = plan.newBuilder();
    for (int i = 0; i < values.length; i++) if (present[i]) builder.set(i, detach(values[i]));
    return builder.build();
  }

  static Object detach(Object value) {
    if (value instanceof BorrowedArray array) return array.snapshot();
    if (value instanceof TypedRow row) return row.snapshot();
    return value;
  }
}
