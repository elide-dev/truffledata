package dev.elide.truffledata;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;

/** Live, thread-confined view. Reads revalidate facts after arbitrary source mutation. */
@ExportLibrary(DataLibrary.class)
public final class DynamicRecord extends AbstractRow {
  private final DynamicObject object;

  public DynamicRecord(DynamicObject object, Schema schema) {
    super(schema);
    this.object = object;
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(int index, @Cached.Shared("get") @Cached DynamicObject.GetNode get) {
    Field field = descriptor.field(index);
    Object value = get.execute(object, field.name(), Missing.INSTANCE);
    return Values.validate(field, value == null ? DataNull.INSTANCE : value);
  }

  @ExportMessage
  long readLong(int index, @Cached.Shared("get") @Cached DynamicObject.GetNode get)
      throws UnexpectedResultException {
    Field field = descriptor.field(index);
    if (field.kind() != Field.Kind.INT64 && field.kind() != Field.Kind.ANY) {
      Object value = get.execute(object, field.name(), Missing.INSTANCE);
      throw new UnexpectedResultException(
          Values.validate(field, value == null ? DataNull.INSTANCE : value));
    }
    try {
      return get.executeLong(object, field.name(), Missing.INSTANCE);
    } catch (UnexpectedResultException e) {
      Object value = e.getResult();
      Object checked = Values.validate(field, value == null ? DataNull.INSTANCE : value);
      if (checked instanceof Long n) return n;
      throw new UnexpectedResultException(checked);
    }
  }

  @ExportMessage
  boolean isPresent(int index, @Cached DynamicObject.ContainsKeyNode contains) {
    return contains.execute(object, descriptor.field(index).name());
  }
}
