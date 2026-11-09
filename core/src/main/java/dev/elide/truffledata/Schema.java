package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;

/** Identity is the cache key. Immutable and owned by the adapter, never globally interned. */
public final class Schema {
  @CompilationFinal(dimensions = 1)
  private final Field[] fields;

  private final DataArray names;
  private final Map<String, Integer> ordinals;
  private final @org.jspecify.annotations.Nullable String typeName;

  // Accessor tables, filled on first request (slow path only): the library-backed defaults, and
  // representation-bound tables as (key, Accessor[]) pairs. Never read by compiled code.
  private @Nullable Accessor @Nullable [] defaults;
  private Object[] bound = new Object[0];

  public Schema(List<Field> fields) {
    this(null, fields);
  }

  public Schema(@org.jspecify.annotations.Nullable String typeName, List<Field> fields) {
    this.typeName = typeName;
    this.fields = fields.toArray(Field[]::new);
    this.names = new DataArray(fields.stream().map(Field::name).distinct().toArray());
    Map<String, Integer> index = new HashMap<>();
    for (int i = 0; i < this.fields.length; i++) {
      String name = this.fields[i].name();
      index.merge(name, i, (previous, next) -> -1);
    }
    this.ordinals = index;
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

  /**
   * The default accessor for {@code ordinal}: reads through the uncached {@link DataLibrary} and
   * accepts any row of this schema. Representations bind their own layout accessors through {@link
   * AbstractRow#accessor(int)}; this one keeps today's behaviour for those that do not.
   */
  @TruffleBoundary
  public synchronized Accessor accessor(int ordinal) {
    var table = defaults;
    if (table == null) defaults = table = new Accessor[fields.length];
    var accessor = table[ordinal];
    if (accessor == null) table[ordinal] = accessor = new LibraryAccessor(this, ordinal);
    return accessor;
  }

  /**
   * The accessor a representation binds for {@code ordinal}, created once per (this schema, {@code
   * representation}) and identity-stable afterwards. {@code representation} identifies the layout:
   * a row class whose layout follows from the schema alone, or a layout object. The factory must
   * return an accessor of this schema and ordinal.
   */
  @TruffleBoundary
  public synchronized Accessor accessor(
      Object representation, int ordinal, IntFunction<? extends Accessor> factory) {
    java.util.Objects.checkIndex(ordinal, fields.length);
    @Nullable Accessor[] table = null;
    for (int i = 0; i < bound.length; i += 2)
      if (bound[i] == representation) table = (@Nullable Accessor[]) bound[i + 1];
    if (table == null) {
      table = new Accessor[fields.length];
      bound = Arrays.copyOf(bound, bound.length + 2);
      bound[bound.length - 2] = representation;
      bound[bound.length - 1] = table;
    }
    var accessor = table[ordinal];
    if (accessor == null) {
      accessor = factory.apply(ordinal);
      if (accessor.schema() != this || accessor.ordinal() != ordinal)
        throw new IllegalArgumentException("Accessor bound to another schema or ordinal");
      table[ordinal] = accessor;
    }
    return accessor;
  }

  /** Duplicate labels remain available by ordinal; named access must be unambiguous. */
  @TruffleBoundary
  public int indexOf(String name) {
    return ordinals.getOrDefault(name, -1);
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
