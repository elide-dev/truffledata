package dev.elide.truffledata.bebop;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.memory.ByteArraySupport;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.bebop.BebopCodec.ArrayNode;
import dev.elide.truffledata.bebop.BebopCodec.BytesNode;
import dev.elide.truffledata.bebop.BebopCodec.EnumNode;
import dev.elide.truffledata.bebop.BebopCodec.MapNode;
import dev.elide.truffledata.bebop.BebopCodec.MessageNode;
import dev.elide.truffledata.bebop.BebopCodec.Node;
import dev.elide.truffledata.bebop.BebopCodec.ScalarNode;
import dev.elide.truffledata.bebop.BebopCodec.StructNode;
import dev.elide.truffledata.bebop.BebopCodec.UnionNode;
import dev.elide.truffledata.bebop.BebopType.Scalar;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * How rows and arrays read a codec's types in place. Layouts are immutable once built and are
 * partial-evaluation constants of compiled reads.
 */
final class Layouts {
  /** Bounded little-endian loads that guest compilation can inline. */
  static final ByteArraySupport LE = ByteArraySupport.littleEndian();

  /** How one value is read at its offset. */
  static final class Slot {
    static final int BOOL = 0;
    static final int LONG = 1;
    static final int DOUBLE = 2;
    static final int EXACT64 = 3;
    static final int EXACT128 = 4;
    static final int STRING = 5;
    static final int UUID = 6;
    static final int ROW = 7;
    static final int ARRAY = 8;

    final int shape;
    /** The primitive read by BOOL, LONG, DOUBLE, EXACT64 and EXACT128 slots. */
    final Scalar scalar;

    final @Nullable RowLayout row;
    final @Nullable ArrayLayout array;

    Slot(int shape, Scalar scalar, @Nullable RowLayout row, @Nullable ArrayLayout array) {
      this.shape = shape;
      this.scalar = scalar;
      this.row = row;
      this.array = array;
    }

    long loadLong(byte[] b, int at) {
      return switch (scalar) {
        case BYTE -> b[at] & 0xFF;
        case INT8 -> b[at];
        case INT16 -> LE.getShort(b, at);
        case UINT16 -> LE.getShort(b, at) & 0xFFFF;
        case INT32 -> LE.getInt(b, at);
        case UINT32 -> LE.getInt(b, at) & 0xFFFFFFFFL;
        default -> LE.getLong(b, at);
      };
    }

    double loadDouble(byte[] b, int at) {
      return switch (scalar) {
        case FLOAT16 -> BebopCodec.fromFloat16(LE.getShort(b, at));
        case BFLOAT16 -> Float.intBitsToFloat((LE.getShort(b, at) & 0xFFFF) << 16);
        case FLOAT32 -> Float.intBitsToFloat(LE.getInt(b, at));
        default -> Double.longBitsToDouble(LE.getLong(b, at));
      };
    }

    Field field(String name, boolean optional) {
      return switch (shape) {
        case BOOL -> new Field(name, Field.Kind.BOOLEAN, false, optional);
        case LONG, EXACT64 ->
            Field.integer(
                name,
                scalar.width() * 8,
                scalar == Scalar.INT8
                    || scalar == Scalar.INT16
                    || scalar == Scalar.INT32
                    || scalar == Scalar.INT64,
                false,
                optional);
        case DOUBLE -> new Field(name, Field.Kind.FLOAT64, false, optional);
        case EXACT128 -> new Field(name, Field.Kind.EXACT, false, optional);
        case STRING, UUID -> new Field(name, Field.Kind.STRING, false, optional);
        case ROW -> new Field(name, Field.Kind.RECORD, false, optional);
        default -> new Field(name, Field.Kind.ARRAY, false, optional);
      };
    }
  }

  /** The fields of a struct, message or union and where they are. */
  static final class RowLayout {
    static final int STRUCT = 0;
    static final int MESSAGE = 1;
    static final int UNION = 2;

    final int form;
    final Schema schema;
    @CompilationFinal(dimensions = 1)
    final Slot[] slots;

    /** STRUCT: offset from the row start, or -1 after a variable-width field. */
    @CompilationFinal(dimensions = 1)
    final int[] offsets;

    /** MESSAGE: field tags. UNION: branch discriminators (ordinal 0 is the discriminator itself). */
    @CompilationFinal(dimensions = 1)
    final int[] tags;

    /** STRUCT: the field types, for the structural pass over variable-width predecessors. */
    final Node[] nodes;

    RowLayout(int form, Schema schema, Slot[] slots, int[] offsets, int[] tags, Node[] nodes) {
      this.form = form;
      this.schema = schema;
      this.slots = slots;
      this.offsets = offsets;
      this.tags = tags;
      this.nodes = nodes;
    }

    @Idempotent
    boolean isLong(int ordinal) {
      return slots[ordinal].shape == Slot.LONG;
    }

    @Idempotent
    boolean isDouble(int ordinal) {
      return slots[ordinal].shape == Slot.DOUBLE;
    }

    @Idempotent
    boolean isBoolean(int ordinal) {
      return slots[ordinal].shape == Slot.BOOL;
    }
  }

  /** The elements of an array, set or map. */
  static final class ArrayLayout {
    final Slot element;
    /** Bytes per element, or -1 when elements vary in size. */
    final int stride;

    /** Element count of a fixed array, or -1 when a count precedes the elements. */
    final int length;

    /** The types that make up one element, in order. */
    final Node[] parts;

    ArrayLayout(Slot element, int stride, int length, Node[] parts) {
      this.element = element;
      this.stride = stride;
      this.length = length;
      this.parts = parts;
    }
  }

  private static final Node[] NO_NODES = new Node[0];
  private static final int[] NO_INTS = new int[0];

  static final RowLayout TIMESTAMP =
      fixed("timestamp", new String[] {"seconds", "nanos", "offset_ms"}, Scalar.INT64, Scalar.INT32, Scalar.INT32);
  static final RowLayout DURATION =
      fixed("duration", new String[] {"seconds", "nanos"}, Scalar.INT64, Scalar.INT32);

  private static RowLayout fixed(String type, String[] names, Scalar... kinds) {
    Slot[] slots = new Slot[kinds.length];
    int[] offsets = new int[kinds.length];
    Node[] nodes = new Node[kinds.length];
    List<Field> fields = new ArrayList<>();
    int offset = 0;
    for (int i = 0; i < kinds.length; i++) {
      slots[i] = new Slot(Slot.LONG, kinds[i], null, null);
      nodes[i] = new ScalarNode(kinds[i]);
      offsets[i] = offset;
      offset += kinds[i].width();
      fields.add(slots[i].field(names[i], false));
    }
    return new RowLayout(RowLayout.STRUCT, new Schema(type, fields), slots, offsets, NO_INTS, nodes);
  }

  private final Map<Node, RowLayout> rows = new IdentityHashMap<>();

  /** The layout of a struct, message or union node. */
  synchronized RowLayout row(Node node) {
    RowLayout known = rows.get(node);
    if (known != null) return known;
    // Recursive types reach themselves through arrays and messages: the layout is registered
    // before its slots are filled.
    if (node instanceof StructNode s) {
      Slot[] slots = new Slot[s.fields.length];
      int[] offsets = new int[slots.length];
      int offset = 0;
      for (int i = 0; i < slots.length; i++) {
        offsets[i] = offset;
        if (offset >= 0) offset = s.fields[i].fixed() < 0 ? -1 : offset + s.fields[i].fixed();
      }
      return fill(node, RowLayout.STRUCT, s.name, s.names, s.fields, slots, offsets, NO_INTS, false);
    }
    if (node instanceof MessageNode m) {
      Slot[] slots = new Slot[m.fields.length];
      return fill(node, RowLayout.MESSAGE, m.name, m.names, m.fields, slots, NO_INTS, m.tags, true);
    }
    UnionNode u = (UnionNode) node;
    int n = 1;
    for (Node branch : u.branches) if (branch != null) n++;
    String[] names = new String[n];
    Node[] nodes = new Node[n];
    int[] tags = new int[n];
    names[0] = "discriminator";
    nodes[0] = new ScalarNode(Scalar.BYTE);
    for (int d = 0, k = 1; d < 256; d++) {
      Node branch = u.branches[d];
      if (branch == null) continue;
      names[k] = String.valueOf(u.names[d]);
      nodes[k] = branch;
      tags[k++] = d;
    }
    return fill(node, RowLayout.UNION, u.name, names, nodes, new Slot[n], NO_INTS, tags, true);
  }

  private RowLayout fill(
      Node node,
      int form,
      String type,
      String[] names,
      Node[] nodes,
      Slot[] slots,
      int[] offsets,
      int[] tags,
      boolean optional) {
    // Field contracts need only each slot's shape, which never recurses.
    List<Field> fields = new ArrayList<>();
    for (int i = 0; i < nodes.length; i++)
      fields.add(
          shapeOnly(nodes[i])
              .field(names[i], optional && !(form == RowLayout.UNION && i == 0)));
    RowLayout layout = new RowLayout(form, new Schema(type, fields), slots, offsets, tags, nodes);
    rows.put(node, layout);
    for (int i = 0; i < nodes.length; i++) slots[i] = slot(nodes[i]);
    return layout;
  }

  private static Slot shapeOnly(Node node) {
    if (node instanceof ScalarNode s) return scalar(s.kind);
    if (node instanceof EnumNode e) return scalar(e.base.kind);
    boolean array = node instanceof ArrayNode || node instanceof BytesNode || node instanceof MapNode;
    return new Slot(array ? Slot.ARRAY : Slot.ROW, Scalar.BYTE, null, null);
  }

  private static Slot scalar(Scalar kind) {
    return switch (kind) {
      case BOOL -> new Slot(Slot.BOOL, kind, null, null);
      case BYTE, INT8, INT16, UINT16, INT32, UINT32, INT64 -> new Slot(Slot.LONG, kind, null, null);
      case UINT64 -> new Slot(Slot.EXACT64, kind, null, null);
      case INT128, UINT128 -> new Slot(Slot.EXACT128, kind, null, null);
      case FLOAT16, BFLOAT16, FLOAT32, FLOAT64 -> new Slot(Slot.DOUBLE, kind, null, null);
      case UUID -> new Slot(Slot.UUID, kind, null, null);
      case TIMESTAMP -> new Slot(Slot.ROW, kind, TIMESTAMP, null);
      case DURATION -> new Slot(Slot.ROW, kind, DURATION, null);
      case STRING -> new Slot(Slot.STRING, kind, null, null);
    };
  }

  /** The slot that reads a value of {@code node}. */
  synchronized Slot slot(Node node) {
    if (node instanceof ScalarNode s) return scalar(s.kind);
    if (node instanceof EnumNode e) return scalar(e.base.kind);
    if (node instanceof BytesNode b)
      return array(new ArrayLayout(scalar(Scalar.BYTE), 1, b.length, NO_NODES));
    if (node instanceof ArrayNode a)
      return array(
          new ArrayLayout(slot(a.element), a.element.fixed(), a.length, new Node[] {a.element}));
    if (node instanceof MapNode m) {
      Node[] parts = {m.key, m.value};
      Slot[] slots = {slot(m.key), slot(m.value)};
      int[] offsets = {0, m.key.fixed()};
      Schema schema =
          new Schema("entry", List.of(slots[0].field("key", false), slots[1].field("value", false)));
      RowLayout entry = new RowLayout(RowLayout.STRUCT, schema, slots, offsets, NO_INTS, parts);
      int stride = m.key.fixed() < 0 || m.value.fixed() < 0 ? -1 : m.key.fixed() + m.value.fixed();
      return array(new ArrayLayout(new Slot(Slot.ROW, Scalar.BYTE, entry, null), stride, -1, parts));
    }
    return new Slot(Slot.ROW, Scalar.BYTE, row(node), null);
  }

  private static Slot array(ArrayLayout layout) {
    return new Slot(Slot.ARRAY, Scalar.BYTE, null, layout);
  }
}
