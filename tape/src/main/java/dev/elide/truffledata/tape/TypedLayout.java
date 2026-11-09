package dev.elide.truffledata.tape;

import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral native plan table for typed decoding: records in ordinal order (0 is the root;
 * each type name gets one ordinal on first reach, breadth first) and one descriptor row per field.
 * Every format and backend writes these values unchanged, so all of them hand native code the
 * identical table, and {@link TypedRecord}s read the slots it describes.
 *
 * <p>The arrays are the table itself: format backends read them while writing their native plan
 * structures and must not modify them.
 */
public final class TypedLayout {
  /** Maximum record nesting, counting the root as 1. */
  public static final int MAX_DEPTH = 32;

  /** Maximum number of distinct records (root included) in one plan. */
  public static final int MAX_RECORDS = 65536;

  /** Field-name bytes accepted per plan, enforced here and by the native plan. */
  public static final long MAX_NAME_BYTES = 1 << 20;

  /** Record-table value of a field without a child record. */
  public static final int NO_CHILD = -1;

  public static final long UNSIGNED_MAXIMUM = -1L;
  public static final long MAX_UTF8_BYTES = 64 * 1024 * 1024;

  public final Schema[] records;
  // First field-table row of each record; records partition the table in order.
  public final int[] fieldsOffset;
  public final int fieldCount;
  public final byte[][] names;
  public final int[] kinds;
  public final int[] flags;
  public final int[] children;
  public final long[] minimums;
  public final long[] maximums;
  public final int[] floatBits;
  // 0 when the field has no length contract.
  public final int[] lengthUnits;
  public final long[] minimumLengths;
  public final long[] maximumLengths;

  private final String format;
  private final TypedRecord.@Nullable Scalars scalars;

  /**
   * Lays out a closed root schema and the record types it reaches. RECORD fields, and ARRAY fields
   * with RECORD elements, name their schema by {@code Constraints.recordType()}: a key of {@code
   * recordTypes} whose schema has that same {@code typeName}. ARRAY fields take their element
   * contract from {@code Constraints.element()}; elements are scalar or RECORD, never null, missing
   * or nested arrays. Unsupported contracts throw {@link IllegalArgumentException} naming {@code
   * format} before any native allocation.
   */
  public TypedLayout(Schema root, Map<String, Schema> recordTypes, TapeFormat format) {
    this.format = format.name;
    var order = new ArrayList<Schema>();
    var ordinals = new HashMap<String, Integer>();
    order.add(root);
    int total = 0;
    for (int r = 0; r < order.size(); r++) {
      Schema schema = order.get(r);
      if (schema.size() > 64) throw new IllegalArgumentException(this.format + " scalar field limit");
      total += schema.size();
      var seen = new HashSet<String>();
      for (int i = 0; i < schema.size(); i++) {
        Field f = schema.field(i);
        if (!seen.add(f.name())) throw new IllegalArgumentException("Duplicate " + this.format + " field");
        // Encoding must never silently replace an unpaired surrogate in a field name.
        for (int p = 0; p < f.name().length(); p++) {
          char c = f.name().charAt(p);
          if (Character.isHighSurrogate(c)) {
            if (++p == f.name().length() || !Character.isLowSurrogate(f.name().charAt(p)))
              throw new IllegalArgumentException("Invalid schema Unicode");
          } else if (Character.isLowSurrogate(c))
            throw new IllegalArgumentException("Invalid schema Unicode");
        }
        Field contract = contract(f);
        if (contract.kind() == Field.Kind.RECORD) {
          String type = Objects.requireNonNull(contract.constraints().recordType());
          if (!ordinals.containsKey(type)) {
            Schema child = recordTypes.get(type);
            if (child == null)
              throw new IllegalArgumentException("Unknown " + this.format + " record type: " + type);
            if (!type.equals(child.typeName()))
              throw new IllegalArgumentException(this.format + " record type name differs: " + type);
            if (order.size() == MAX_RECORDS)
              throw new IllegalArgumentException(this.format + " plan record limit");
            ordinals.put(type, order.size());
            order.add(child);
          }
        }
      }
    }
    records = order.toArray(Schema[]::new);
    fieldCount = total;
    fieldsOffset = new int[records.length];
    names = new byte[total][];
    kinds = new int[total];
    flags = new int[total];
    children = new int[total];
    minimums = new long[total];
    maximums = new long[total];
    floatBits = new int[total];
    lengthUnits = new int[total];
    minimumLengths = new long[total];
    maximumLengths = new long[total];
    long nameBytes = 0;
    int next = 0;
    for (int r = 0; r < records.length; r++) {
      Schema schema = records[r];
      fieldsOffset[r] = next;
      for (int i = 0; i < schema.size(); i++, next++) {
        Field f = schema.field(i);
        byte[] name = f.name().getBytes(StandardCharsets.UTF_8);
        nameBytes += name.length;
        if (nameBytes > MAX_NAME_BYTES)
          throw new IllegalArgumentException(this.format + " schema name limit");
        names[next] = name;
        row(next, f, ordinals);
      }
    }
    scalars = scalarOnly() ? new TypedRecord.Scalars(root, format) : null;
  }

  /**
   * Owned-record factory when the root is the plan's only record and has only INT64, FLOAT64,
   * BOOLEAN and EXACT fields (missing and null allowed): such results can be copied into an owned
   * {@link TypedRecord} at decode and released at once. Null for any plan with a STRING, RECORD or
   * ARRAY field, whose results stay native.
   */
  public TypedRecord.@Nullable Scalars scalars() {
    return scalars;
  }

  private boolean scalarOnly() {
    if (records.length != 1) return false;
    for (int i = 0; i < records[0].size(); i++) {
      switch (records[0].field(i).kind()) {
        case INT64, FLOAT64, BOOLEAN, EXACT -> {}
        default -> {
          return false;
        }
      }
    }
    return true;
  }

  private void row(int at, Field f, Map<String, Integer> ordinals) {
    Field value = contract(f);
    var c = value.constraints();
    int kind = kind(value);
    kinds[at] = kind;
    flags[at] =
        (f.optional() ? 0 : 1) | (f.nullable() ? 2 : 0) | (f.kind() == Field.Kind.ARRAY ? 4 : 0);
    children[at] =
        kind == 5
            ? Objects.requireNonNull(ordinals.get(Objects.requireNonNull(c.recordType())))
            : NO_CHILD;
    long min = Long.MIN_VALUE, max = Long.MAX_VALUE;
    var width = c.integer();
    if (width != null && value.kind() == Field.Kind.INT64) {
      min = width.minimum();
      max = width.maximum();
    }
    var range = c.integerRange();
    if (range != null) {
      min = Math.max(min, range.min());
      max = Math.min(max, range.max());
    }
    minimums[at] = min;
    maximums[at] = max;
    floatBits[at] = c.floatBits();
    var length = c.length();
    if (length != null) {
      lengthUnits[at] =
          length.unit() == dev.elide.truffledata.Constraints.LengthUnit.UTF16 ? 1 : 2;
      minimumLengths[at] = length.min();
      maximumLengths[at] = length.max();
    }
  }

  /** Value contract of a field: the element contract for ARRAY, else the field itself. */
  private Field contract(Field field) {
    if (field.kind() != Field.Kind.ARRAY) {
      kind(field);
      return field;
    }
    if (field.constraints().length() != null)
      throw new IllegalArgumentException("Unsupported " + format + " array length contract");
    Field element = field.constraints().element();
    if (element == null)
      throw new IllegalArgumentException(format + " array requires an element contract");
    if (element.nullable() || element.optional())
      throw new IllegalArgumentException(format + " array elements are never null or missing");
    kind(element);
    return element;
  }

  private int kind(Field field) {
    return switch (field.kind()) {
      case BOOLEAN -> 0;
      case INT64 -> 1;
      case FLOAT64 -> 3;
      case STRING -> 4;
      case RECORD -> {
        if (field.constraints().recordType() == null)
          throw new IllegalArgumentException(format + " record field requires a record type");
        yield 5;
      }
      case EXACT -> {
        var width = field.constraints().integer();
        if (width == null
            || width.signed()
            || width.bits() != 64
            || field.constraints().decimal() != null)
          throw new IllegalArgumentException("Unsupported " + format + " exact contract");
        yield 2;
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported " + format + " scalar kind: " + field.kind());
    };
  }
}
