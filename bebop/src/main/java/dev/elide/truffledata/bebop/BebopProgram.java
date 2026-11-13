package dev.elide.truffledata.bebop;

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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The schema program a native validator interprets: a codec's types, profile and limits as
 * little-endian 32-bit words (format {@code BBP1}).
 *
 * <pre>
 * header, 16 words:
 *   0 magic 'BBP1'   1 flags (1 = strict)   2 root node   3 maxBytes   4 maxCount   5 maxDepth
 *   6-7 maxWork   8-9 maxAllocation   10 nan16   11 nanBrain16   12 nan32   13-14 nan64
 *   15 total words
 * nodes, each at the word offset others refer to it by (0 = none):
 *   1 SCALAR  kind                         (ordinal of BebopType.Scalar; bit 8 = UTC instant)
 *   2 ENUM    base kind, n, n members      (64-bit each, ascending as signed)
 *   3 BYTES   length                       (-1 = counted)
 *   4 ARRAY   element, set, length, min    (length -1 = counted; min = least element size)
 *   5 MAP     key, value, min, boolean key (min = least entry size)
 *   6 STRUCT  n, n fields
 *   7 MESSAGE 256 fields by tag
 *   8 UNION   256 branches by discriminator
 * </pre>
 */
final class BebopProgram {
  private BebopProgram() {}

  static final int MAGIC = 0x31504242;
  private static final int HEADER = 16;

  static byte[] of(BebopCodec codec) {
    Map<Node, Integer> offsets = new IdentityHashMap<>();
    List<Node> order = new ArrayList<>();
    int words = place(codec.root, HEADER, offsets, order);
    ByteBuffer out = ByteBuffer.allocate(words * 4).order(ByteOrder.LITTLE_ENDIAN);
    BebopProfile profile = codec.profile();
    BebopLimits limits = codec.limits();
    out.putInt(MAGIC).putInt(profile.strict() ? 1 : 0).putInt(HEADER);
    out.putInt(limits.maxBytes()).putInt(limits.maxCount()).putInt(limits.maxDepth());
    out.putLong(limits.maxWork()).putLong(limits.maxAllocation());
    out.putInt(profile.nan16).putInt(profile.nanBrain16).putInt(profile.nan32).putLong(profile.nan64);
    out.putInt(words);
    for (Node node : order) {
      switch (node) {
        case ScalarNode s -> out.putInt(1).putInt(s.kind.ordinal() | (s.utc ? 0x100 : 0));
        case EnumNode e -> {
          out.putInt(2).putInt(e.base.kind.ordinal()).putInt(e.members.length);
          for (long member : e.members) out.putLong(member);
        }
        case BytesNode b -> out.putInt(3).putInt(b.length);
        case ArrayNode a ->
            out.putInt(4)
                .putInt(at(offsets, a.element))
                .putInt(a.set ? 1 : 0)
                .putInt(a.length)
                .putInt(a.element.min());
        case MapNode m ->
            out.putInt(5)
                .putInt(at(offsets, m.key))
                .putInt(at(offsets, m.value))
                .putInt(m.key.min() + m.value.min())
                .putInt(m.key instanceof ScalarNode s && s.kind == Scalar.BOOL ? 1 : 0);
        case StructNode s -> {
          out.putInt(6).putInt(s.fields.length);
          for (Node field : s.fields) out.putInt(at(offsets, field));
        }
        case MessageNode m -> {
          out.putInt(7);
          for (int tag = 0; tag < 256; tag++)
            out.putInt(m.ordinalByTag[tag] < 0 ? 0 : at(offsets, m.fields[m.ordinalByTag[tag]]));
        }
        case UnionNode u -> {
          out.putInt(8);
          for (Node branch : u.branches) out.putInt(branch == null ? 0 : at(offsets, branch));
        }
      }
    }
    return out.array();
  }

  private static int at(Map<Node, Integer> offsets, Node node) {
    return java.util.Objects.requireNonNull(offsets.get(node));
  }

  /** Assigns {@code node} and everything it reaches their offsets; returns the next free word. */
  private static int place(Node node, int next, Map<Node, Integer> offsets, List<Node> order) {
    if (offsets.containsKey(node)) return next;
    offsets.put(node, next);
    order.add(node);
    switch (node) {
      case ScalarNode s -> next += 2;
      case EnumNode e -> next += 3 + 2 * e.members.length;
      case BytesNode b -> next += 2;
      case ArrayNode a -> next = place(a.element, next + 5, offsets, order);
      case MapNode m -> next = place(m.value, place(m.key, next + 5, offsets, order), offsets, order);
      case StructNode s -> {
        next += 2 + s.fields.length;
        for (Node field : s.fields) next = place(field, next, offsets, order);
      }
      case MessageNode m -> {
        next += 257;
        for (Node field : m.fields) next = place(field, next, offsets, order);
      }
      case UnionNode u -> {
        next += 257;
        for (Node branch : u.branches) if (branch != null) next = place(branch, next, offsets, order);
      }
    }
    return next;
  }
}
