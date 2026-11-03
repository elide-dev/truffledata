package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.List;

/** Identity is the cache key. Immutable and owned by the adapter, never globally interned. */
public final class Schema {
  @CompilationFinal(dimensions = 1)
  private final Field[] fields;

  private final DataArray names;
  private final @org.jspecify.annotations.Nullable String typeName;

  public Schema(List<Field> fields) {
    this(null, fields);
  }

  public Schema(@org.jspecify.annotations.Nullable String typeName, List<Field> fields) {
    this.typeName = typeName;
    this.fields = fields.toArray(Field[]::new);
    this.names = new DataArray(fields.stream().map(Field::name).distinct().toArray());
  }

  public static Schema of(Field... fields) {
    return new Schema(List.of(fields));
  }

  public @org.jspecify.annotations.Nullable String typeName() {
    return typeName;
  }

  public int size() {
    return fields.length;
  }

  public Field field(int index) {
    return fields[index];
  }

  public DataArray names() {
    return names;
  }

  /** Duplicate labels remain available by ordinal; named access must be unambiguous. */
  @TruffleBoundary
  public int indexOf(String name) {
    int found = -1;
    for (int i = 0; i < fields.length; i++) {
      if (fields[i].name().equals(name)) {
        if (found >= 0) return -1;
        found = i;
      }
    }
    return found;
  }

  @com.oracle.truffle.api.dsl.Idempotent
  public boolean requiredDouble(int index) {
    Field field = fields[index];
    return field.kind() == Field.Kind.FLOAT64 && !field.nullable() && !field.optional();
  }

  @com.oracle.truffle.api.dsl.Idempotent
  public boolean requiredBoolean(int index) {
    Field field = fields[index];
    return field.kind() == Field.Kind.BOOLEAN && !field.nullable() && !field.optional();
  }

  @com.oracle.truffle.api.dsl.Idempotent
  public boolean requiredLong(int index) {
    Field field = fields[index];
    return field.kind() == Field.Kind.INT64 && !field.nullable() && !field.optional();
  }
}
