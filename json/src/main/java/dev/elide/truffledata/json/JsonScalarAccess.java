package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;

/**
 * Immutable schema/ordinal specialization. Keep as a final AST field to expose validated facts to
 * partial evaluation. Contains no native addresses and can be prepared at image build time.
 */
public final class JsonScalarAccess {
  final Schema schema;
  final int index;
  final Field.Kind kind;
  final boolean optional;
  final boolean nullable;

  @TruffleBoundary
  public JsonScalarAccess(Schema schema, int index) {
    Field field = schema.field(index);
    if (field.kind() != Field.Kind.INT64
        && field.kind() != Field.Kind.FLOAT64
        && field.kind() != Field.Kind.BOOLEAN)
      throw new IllegalArgumentException(
          "Primitive JSON accessor requires INT64, FLOAT64 or BOOLEAN");
    this.schema = schema;
    this.index = index;
    this.kind = field.kind();
    this.optional = field.optional();
    this.nullable = field.nullable();
  }

  public long longValue(JsonScalarRecord row) {
    requireKind(Field.Kind.INT64);
    return row.bits(this);
  }

  public double doubleValue(JsonScalarRecord row) {
    requireKind(Field.Kind.FLOAT64);
    return Double.longBitsToDouble(row.bits(this));
  }

  public boolean booleanValue(JsonScalarRecord row) {
    requireKind(Field.Kind.BOOLEAN);
    return row.bits(this) != 0;
  }

  private void requireKind(Field.Kind expected) {
    if (kind != expected) throw new IllegalArgumentException("JSON accessor kind mismatch");
  }
}
