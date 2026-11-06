package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.util.Map;

/**
 * Immutable schema/path specialization. Keep as a final AST field to expose validated facts to
 * partial evaluation. Contains no native addresses and can be prepared at image build time.
 *
 * <p>A path is zero or more RECORD field ordinals (hops from the root record into nested records)
 * followed by one INT64, FLOAT64 or BOOLEAN ordinal. Nested reads walk the record table directly,
 * without materializing an intermediate {@link JsonRecord}.
 */
public final class JsonAccess {
  final Schema schema;
  // Leaf ordinal within the last record of the path.
  final int index;
  final Field.Kind kind;
  final boolean optional;
  final boolean nullable;

  // RECORD ordinals walked before the leaf, and their presence checks (bit 0 optional, bit 1
  // nullable). Empty for a root-level primitive.
  @CompilationFinal(dimensions = 1)
  final int[] hops;

  @CompilationFinal(dimensions = 1)
  final byte[] hopChecks;

  /** Root-level primitive at {@code index}. */
  @TruffleBoundary
  public JsonAccess(Schema schema, int index) {
    this(schema, Map.of(), new int[] {index});
  }

  /**
   * Nested primitive reached by {@code path}: RECORD ordinals, each resolved through {@code
   * recordTypes} by its {@code recordType}, then a primitive ordinal. Pass the same map given to
   * {@link JsonPlan#compile(Schema, Map)}.
   */
  @TruffleBoundary
  public JsonAccess(Schema schema, Map<String, Schema> recordTypes, int... path) {
    if (path.length == 0) throw new IllegalArgumentException("Empty JSON accessor path");
    if (path.length > JsonPlan.MAX_DEPTH)
      throw new IllegalArgumentException("JSON accessor path exceeds plan depth");
    int[] hops = new int[path.length - 1];
    byte[] checks = new byte[hops.length];
    Schema current = schema;
    for (int i = 0; i < hops.length; i++) {
      Field hop = current.field(path[i]);
      if (hop.kind() != Field.Kind.RECORD)
        throw new IllegalArgumentException("JSON accessor path hop requires RECORD");
      String type = hop.constraints().recordType();
      Schema next = type == null ? null : recordTypes.get(type);
      if (next == null) throw new IllegalArgumentException("Unknown JSON record type: " + type);
      hops[i] = path[i];
      checks[i] = (byte) ((hop.optional() ? 1 : 0) | (hop.nullable() ? 2 : 0));
      current = next;
    }
    int leaf = path[path.length - 1];
    Field field = current.field(leaf);
    if (field.kind() != Field.Kind.INT64
        && field.kind() != Field.Kind.FLOAT64
        && field.kind() != Field.Kind.BOOLEAN)
      throw new IllegalArgumentException(
          "Primitive JSON accessor requires INT64, FLOAT64 or BOOLEAN");
    this.schema = schema;
    this.index = leaf;
    this.kind = field.kind();
    this.optional = field.optional();
    this.nullable = field.nullable();
    this.hops = hops;
    this.hopChecks = checks;
  }

  public long longValue(JsonRecord row) {
    requireKind(Field.Kind.INT64);
    return row.bits(this);
  }

  public double doubleValue(JsonRecord row) {
    requireKind(Field.Kind.FLOAT64);
    return Double.longBitsToDouble(row.bits(this));
  }

  public boolean booleanValue(JsonRecord row) {
    requireKind(Field.Kind.BOOLEAN);
    return row.bits(this) != 0;
  }

  private void requireKind(Field.Kind expected) {
    if (kind != expected) throw new IllegalArgumentException("JSON accessor kind mismatch");
  }
}
