package dev.elide.truffledata.yaml;

import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.HeapTape;
import dev.elide.truffledata.tape.TapeReader;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.yaml.ScalarResolver.Kind;
import dev.elide.truffledata.yaml.ScalarResolver.Scalar;
import dev.elide.truffledata.yaml.ScalarResolver.Tag;
import dev.elide.truffledata.yaml.events.YamlEventSource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Typed plans of the Java kernel, a port of {@code yaml/native/typed.h} over {@code
 * tape/native/tape_typed.h}: the document is decoded with its source retained, then a closed
 * record walk fills the same fixed slots and record entries the native plan publishes, with the
 * same contract checks, alias-expansion budget and messages.
 */
final class JavaPlanBackend extends YamlPlan.Backend {
  private static final int BOOLEAN = 0, INT64 = 1, UINT64 = 2, FLOAT64 = 3, STRING = 4, RECORD = 5;
  private static final int MAX_PLAN_DEPTH = 32;

  private final YamlEventSource source;
  private final TypedLayout layout;
  private final YamlOptions options;
  private final Map<String, Integer>[] ordinals;

  @SuppressWarnings("unchecked")
  JavaPlanBackend(YamlEventSource source, TypedLayout layout, YamlOptions options) {
    this.source = source;
    this.layout = layout;
    this.options = options;
    ordinals = new Map[layout.records.length];
    for (int r = 0; r < layout.records.length; r++) {
      Map<String, Integer> names = new HashMap<>();
      for (int i = 0; i < layout.records[r].size(); i++) {
        int at = layout.fieldsOffset[r] + i;
        names.put(new String(layout.names[at], StandardCharsets.UTF_8), i);
        boolean bad =
            layout.minimums[at] > layout.maximums[at]
                || (layout.floatBits[at] != 0 && layout.floatBits[at] != 32 && layout.floatBits[at] != 64)
                || (layout.floatBits[at] != 0 && layout.kinds[at] != FLOAT64)
                || (layout.lengthUnits[at] != 0 && (layout.kinds[at] != STRING
                    || Long.compareUnsigned(layout.minimumLengths[at], layout.maximumLengths[at]) > 0))
                || ((layout.kinds[at] == RECORD) != (layout.children[at] != TypedLayout.NO_CHILD));
        if (bad) YamlSvm.checkStatus(1);
      }
      ordinals[r] = names;
    }
    byte[] depths = new byte[layout.records.length];
    for (int r = 0; r < layout.records.length; r++) measure(r, 1, depths);
  }

  // Recursive plans and plans deeper than 32 records are rejected, as native plan creation does.
  private int measure(int record, int level, byte[] depths) {
    if (level > MAX_PLAN_DEPTH) YamlSvm.checkStatus(1);
    final byte visiting = -1;
    if (depths[record] == visiting) YamlSvm.checkStatus(1);
    if (depths[record] == 0) {
      depths[record] = visiting;
      int measured = 1;
      for (int i = 0; i < layout.records[record].size(); i++) {
        int at = layout.fieldsOffset[record] + i;
        if (layout.kinds[at] == RECORD)
          measured = Math.max(measured, 1 + measure(layout.children[at], level + 1, depths));
      }
      depths[record] = (byte) measured;
    }
    if (level - 1 + depths[record] > MAX_PLAN_DEPTH) YamlSvm.checkStatus(1);
    return depths[record];
  }

  @Override
  TypedOwner decode(byte[] utf8, Schema[] schemas) {
    // Closed typed records always reject duplicate fields, whatever the generic policy.
    JavaDecoder.Document document;
    try {
      document = JavaSessionBackend.decodeDocument(source, utf8, options, true, false);
    } catch (JavaFailure failure) {
      throw failure.raise();
    }
    try {
      return new Walk(document).run(schemas);
    } catch (JavaFailure failure) {
      throw failure.raise();
    }
  }

  @Override
  void release() {}

  private static JavaFailure contract(String message) {
    return new JavaFailure(JavaFailure.CONTRACT, message, 0, 0, false);
  }

  private static JavaFailure limit(String message) {
    return new JavaFailure(JavaFailure.LIMIT, "YAML resource limit: " + message, 0, 0, false);
  }

  /** One typed decode: the slot arena and record table of tape_typed. */
  private final class Walk {
    private final HeapTape tape;
    private final int[] meta;
    private final YamlSchema schema;
    private long remaining;
    private long[] bits = new long[16];
    private String[] text = new String[16];
    private int slots;
    private long[] present = new long[4], nulls = new long[4];
    private int[] slotBase = new int[4], plan = new int[4];
    private int records;

    Walk(JavaDecoder.Document document) {
      tape = document.tape();
      meta = document.metadata().words;
      schema = document.schema11() ? YamlSchema.YAML_1_1 : options.schema();
      remaining = options.limits().maxAliasExpansion();
    }

    TypedOwner run(Schema[] schemas) {
      growSlots(layout.records[0].size());
      boolean flat = true;
      for (int i = 0; i < layout.records[0].size(); i++)
        if (layout.kinds[layout.fieldsOffset[0] + i] == RECORD) flat = false;
      if (!flat) growRecords(1);
      long[] masks = members(visit(0), 0, 0);
      if (!flat) {
        present[0] = masks[0];
        nulls[0] = masks[1];
        slotBase[0] = 0;
        plan[0] = 0;
      } else {
        records = 1;
        present[0] = masks[0];
        nulls[0] = masks[1];
      }
      return new JavaTypedOwner(
          schemas,
          Arrays.copyOf(bits, slots),
          Arrays.copyOf(text, slots),
          Arrays.copyOf(present, records),
          Arrays.copyOf(nulls, records),
          Arrays.copyOf(slotBase, records),
          Arrays.copyOf(plan, records));
    }

    private int growSlots(int count) {
      int first = slots;
      if (count > 0xffffffffL - first || (long) first + count > Integer.MAX_VALUE - 8)
        throw limit("typed slot arena limit");
      if (first + count > bits.length) {
        int capacity = Math.max(first + count, bits.length * 2);
        bits = Arrays.copyOf(bits, capacity);
        text = Arrays.copyOf(text, capacity);
      }
      slots += count;
      return first;
    }

    private int growRecords(int count) {
      int first = records;
      if ((long) first + count > Integer.MAX_VALUE - 8) throw limit("typed record table limit");
      if (first + count > present.length) {
        int capacity = Math.max(first + count, present.length * 2);
        present = Arrays.copyOf(present, capacity);
        nulls = Arrays.copyOf(nulls, capacity);
        slotBase = Arrays.copyOf(slotBase, capacity);
        plan = Arrays.copyOf(plan, capacity);
      }
      records += count;
      return first;
    }

    private long visit(long node) {
      if (remaining == 0) throw limit("typed alias expansion limit");
      remaining--;
      long w = tape.word(node);
      while (TapeReader.kind(w) == 10) {
        long target = TapeReader.low(w);
        if (target >= node) throw contract("invalid typed YAML alias");
        node = target;
        w = tape.word(node);
      }
      return node;
    }

    private int kind(long node) {
      return switch (TapeReader.kind(tape.word(node))) {
        case 0 -> 0; // null
        case 8 -> 2; // array
        case 9 -> 3; // object
        default -> 1; // scalar
      };
    }

    private long[] members(long node, int record, int first) {
      if (kind(node) != 3) throw contract("typed field requires a mapping");
      long presentMask = 0, nullMask = 0;
      Map<String, Integer> names = ordinals[record];
      long key = tape.first(node);
      for (long i = 0, count = tape.length(node); i < count; i++) {
        Integer ordinal = names.get(tape.stringValue(key));
        if (ordinal == null) throw contract("Unknown YAML field");
        long bit = 1L << ordinal;
        if ((presentMask & bit) != 0) throw contract("duplicate typed field");
        presentMask |= bit;
        int field = layout.fieldsOffset[record] + ordinal;
        long original = tape.next(key);
        long value = visit(original);
        int index = first + ordinal;
        int flags = layout.flags[field];
        if (kind(value) == 0) {
          if ((flags & 2) == 0) throw contract("unexpected typed null");
          nullMask |= bit;
        } else if ((flags & 4) != 0) {
          long packed = elements(value, field);
          bits[index] = packed;
        } else if (layout.kinds[field] == RECORD) {
          long entry = child(value, layout.children[field]);
          bits[index] = entry;
        } else {
          if (kind(value) != 1) throw contract("typed field requires a scalar");
          scalar(value, field, index);
        }
        key = tape.next(original);
      }
      long required = 0;
      for (int i = 0; i < layout.records[record].size(); i++)
        if ((layout.flags[layout.fieldsOffset[record] + i] & 1) != 0) required |= 1L << i;
      if ((presentMask & required) != required) throw contract("missing required typed field");
      return new long[] {presentMask, nullMask};
    }

    private long child(long node, int ordinal) {
      int entry = growRecords(1);
      int first = growSlots(layout.records[ordinal].size());
      long[] masks = members(node, ordinal, first);
      present[entry] = masks[0];
      nulls[entry] = masks[1];
      slotBase[entry] = first;
      plan[entry] = ordinal;
      return entry;
    }

    private long elements(long node, int field) {
      if (kind(node) != 2) throw contract("typed field requires an array");
      int count = Math.toIntExact(tape.length(node));
      if (count == 0) return 0;
      boolean records = layout.kinds[field] == RECORD;
      int first = records ? growRecords(count) : growSlots(count);
      long current = tape.first(node);
      for (int i = 0; i < count; i++) {
        long value = visit(current);
        if (records) {
          int ordinal = layout.children[field];
          int base = growSlots(layout.records[ordinal].size());
          long[] masks = members(value, ordinal, base);
          present[first + i] = masks[0];
          nulls[first + i] = masks[1];
          slotBase[first + i] = base;
          plan[first + i] = ordinal;
        } else {
          if (kind(value) != 1) throw contract("typed array requires non-null scalar elements");
          scalar(value, field, first + i);
        }
        current = tape.next(current);
      }
      return (long) first << 32 | count;
    }

    private String token(long node) {
      long span = tape.span(node);
      int length = (int) span;
      byte[] bytes = new byte[length];
      for (int i = 0; i < length; i++) bytes[i] = tape.inputByte((span >>> 32) + i);
      return new String(bytes, StandardCharsets.UTF_8);
    }

    private void scalar(long node, int field, int slot) {
      long w = tape.word(node);
      int compact = TapeReader.kind(w);
      int fieldKind = layout.kinds[field];
      if (fieldKind == STRING) {
        // Strings keep the decoded text; other scalars their original token (1.0, 0x1F exactly).
        String value = compact == 7 ? tape.stringValue(node) : token(node);
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > TypedLayout.MAX_UTF8_BYTES) throw limit("YAML string byte limit");
        int unit = layout.lengthUnits[field];
        if (unit != 0) {
          long length = unit == 1 ? value.length() : value.codePointCount(0, value.length());
          if (Long.compareUnsigned(length, layout.minimumLengths[field]) < 0
              || Long.compareUnsigned(length, layout.maximumLengths[field]) > 0)
            throw contract("YAML string length contract");
        }
        text[slot] = value;
        return;
      }
      if ((meta[(int) node] & JavaDecoder.STYLE_MASK) != 0)
        throw contract("quoted YAML scalar requires a string field");
      Tag tag = switch (fieldKind) {
        case BOOLEAN -> Tag.BOOL;
        case INT64, UINT64 -> Tag.INT;
        case FLOAT64 -> Tag.FLOAT;
        default -> throw contract("YAML scalar field kind");
      };
      Kind kind;
      long value;
      boolean exact = (fieldKind == BOOLEAN && (compact == 1 || compact == 2))
          || ((fieldKind == INT64 || fieldKind == UINT64) && (compact == 3 || compact == 4))
          || (fieldKind == FLOAT64 && compact == 5);
      if (exact) {
        // Generic resolution already produced this kind; only range and width remain.
        kind = compact == 1 || compact == 2 ? Kind.BOOL : compact == 3 ? Kind.I64 : compact == 4 ? Kind.U64 : Kind.F64;
        value = kind == Kind.BOOL ? (compact == 2 ? 1 : 0) : tape.word(node + 1);
      } else {
        Scalar r;
        try {
          r = ScalarResolver.resolveTagged(token(node), tag, schema);
        } catch (ScalarResolver.ResolveException failure) {
          String message = String.valueOf(failure.getMessage());
          throw failure.limit
              ? new JavaFailure(JavaFailure.LIMIT, message, 0, 0, false)
              : new JavaFailure(JavaFailure.SYNTAX, message, 0, 0, false);
        }
        kind = r.kind();
        value = r.bits();
      }
      if (fieldKind == BOOLEAN) {
        bits[slot] = value;
        return;
      }
      if ((fieldKind == INT64 || fieldKind == UINT64) && kind == Kind.F64 && value == Long.MIN_VALUE) {
        kind = Kind.I64; // an integer contract has no signed zero
        value = 0;
      }
      if (fieldKind == INT64) {
        if (kind != Kind.I64 || value < layout.minimums[field] || value > layout.maximums[field])
          throw contract("YAML integer range");
        bits[slot] = value;
        return;
      }
      if (fieldKind == UINT64) {
        if ((kind != Kind.U64 && kind != Kind.I64) || (kind == Kind.I64 && value < 0)
            || Long.compareUnsigned(value, TypedLayout.UNSIGNED_MAXIMUM) > 0)
          throw contract("YAML unsigned range");
        bits[slot] = value;
        return;
      }
      if (kind != Kind.F64) throw contract("YAML floating range");
      double number = Double.longBitsToDouble(value);
      if (layout.floatBits[field] == 32 && !Double.isNaN(number) && (double) (float) number != number)
        throw contract("YAML floating width");
      bits[slot] = value;
    }
  }
}
