package dev.elide.truffledata.bebop;

import dev.elide.truffledata.bebop.BebopCodec.ArrayNode;
import dev.elide.truffledata.bebop.BebopCodec.BytesNode;
import dev.elide.truffledata.bebop.BebopCodec.Dec;
import dev.elide.truffledata.bebop.BebopCodec.EnumNode;
import dev.elide.truffledata.bebop.BebopCodec.MessageNode;
import dev.elide.truffledata.bebop.BebopCodec.Node;
import dev.elide.truffledata.bebop.BebopCodec.ScalarNode;
import dev.elide.truffledata.bebop.BebopCodec.StructNode;
import dev.elide.truffledata.bebop.BebopType.Scalar;
import org.jspecify.annotations.Nullable;

/**
 * Validated, immutable encoded bytes that rows, arrays and bulk views read in place. The whole
 * value is validated before the document exists. Everything read from a document keeps it, and so
 * its bytes, alive: backing storage lives exactly as long as its views.
 */
public final class BebopDocument {
  private final BebopCodec codec;
  final byte[] bytes;

  BebopDocument(BebopCodec codec, byte[] bytes) {
    this.codec = codec;
    this.bytes = bytes;
  }

  public BebopCodec codec() {
    return codec;
  }

  /**
   * The identity every view of this document is bound to: wire revision, schema definitions and
   * root type ({@link BebopCodec#schemaIdentity()}).
   */
  public String schemaIdentity() {
    return codec.schemaIdentity();
  }

  byte[] bytes() {
    return bytes;
  }

  public int size() {
    return bytes.length;
  }

  public Object decode() {
    return codec.decode(bytes);
  }

  /**
   * The root value for guests: a {@link BebopRow} for a struct, message or union, a {@link
   * BebopArray} for an array, set or map, and the plain value for a primitive or enum.
   */
  public Object root() {
    return BebopRow.value(this, codec.layouts.slot(codec.root), 0);
  }

  /** The root as a row. Throws if the root type is not a struct, message or union. */
  public BebopRow row() {
    if (root() instanceof BebopRow row) return row;
    throw new IllegalStateException("The root type is not a struct, message or union");
  }

  /**
   * A column of fixed-width values. The path names message and struct fields from the root down
   * to an array, then optionally struct fields inside its fixed-size elements, selecting one
   * column of an array of structs. Returns {@code null} when a message field on the path is
   * absent: presence is the schema's, and no sentinel stands in for it.
   */
  public @Nullable BebopSpan span(String... path) {
    byte[] b = bytes();
    Dec d = new Dec(b, 0, b.length, false, codec.profile(), codec.limits());
    int i = descend(d, path);
    if (i < 0) return null;
    if (located instanceof BytesNode bn) {
      if (i != path.length) throw new IllegalArgumentException("Bytes have no fields");
      return new BebopSpan(this, Scalar.BYTE, bn.length < 0 ? d.length(0) : bn.length, 1, d.pos);
    }
    ArrayNode an = (ArrayNode) java.util.Objects.requireNonNull(located);
    int count = an.length < 0 ? (int) d.u32() : an.length;
    Node element = an.element;
    int stride = element.fixed();
    if (stride < 0) throw new IllegalArgumentException("Array elements are not fixed-width");
    int inner = 0;
    for (; i < path.length; i++) {
      if (!(element instanceof StructNode s))
        throw new IllegalArgumentException("No field " + path[i]);
      int ordinal = ordinal(s.names, path[i]);
      for (int f = 0; f < ordinal; f++) inner += s.fields[f].fixed();
      element = s.fields[ordinal];
    }
    Scalar kind =
        element instanceof EnumNode e
            ? e.base.kind
            : element instanceof ScalarNode s
                ? s.kind
                : null;
    if (kind == null) throw new IllegalArgumentException("The path does not end at a primitive");
    return new BebopSpan(this, kind, count, stride, d.pos + inner);
  }

  /**
   * The validated byte ranges of an array of strings, or {@code null} when a message field on the
   * path is absent.
   */
  public @Nullable BebopOffsets strings(String... path) {
    byte[] b = bytes();
    Dec d = new Dec(b, 0, b.length, false, codec.profile(), codec.limits());
    int i = descend(d, path);
    if (i < 0) return null;
    if (i != path.length
        || !(located instanceof ArrayNode an)
        || !(an.element instanceof ScalarNode s)
        || s.kind != Scalar.STRING) throw new IllegalArgumentException("The path is not a string array");
    int count = an.length < 0 ? d.count(5) : an.length;
    d.allocate(8L * count);
    int[] starts = new int[count];
    int[] lengths = new int[count];
    int dataStart = d.pos;
    for (int k = 0; k < count; k++) {
      lengths[k] = d.length(0);
      starts[k] = d.take(lengths[k] + 1);
    }
    return new BebopOffsets(this, starts, lengths, dataStart, d.pos);
  }

  /** The node {@link #descend} stopped at. */
  private @Nullable Node located;

  /**
   * Positions {@code d} at the first array on the path. Returns the index of the first unused path
   * element, or -1 if a message field on the way is absent.
   */
  private int descend(Dec d, String[] path) {
    Node node = codec.root;
    int i = 0;
    while (!(node instanceof ArrayNode) && !(node instanceof BytesNode)) {
      if (i == path.length) throw new IllegalArgumentException("The path does not end at an array");
      if (node instanceof MessageNode m) {
        int ordinal = ordinal(m.names, path[i]);
        int length = d.length(1);
        MessageIndex index = MessageIndex.parse(d.buf, d.pos, d.pos + length);
        int rank = index.rank(m.tags[ordinal]);
        if (rank < 0) return -1;
        d.pos = index.start(rank);
        d.end = index.end(rank);
        node = m.fields[ordinal];
      } else if (node instanceof StructNode s) {
        int ordinal = ordinal(s.names, path[i]);
        for (int f = 0; f < ordinal; f++) s.fields[f].skip(d);
        node = s.fields[ordinal];
      } else {
        throw new IllegalArgumentException("No field " + path[i]);
      }
      i++;
    }
    located = node;
    return i;
  }

  private static int ordinal(String[] names, String name) {
    for (int i = 0; i < names.length; i++) if (names[i].equals(name)) return i;
    throw new IllegalArgumentException("No field " + name);
  }
}
