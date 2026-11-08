package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What an owner knows about the format that produced its tape: the name used in its messages
 * ({@code "JSON message closed"}) and, for documents whose tapes hold alias nodes (YAML), the
 * alias-resolution tables. JSON shares one instance without tables, so every owner carries a
 * single extra reference and the alias hook is one null check that JSON never takes.
 *
 * <p>Alias tables follow the YAML decoder's side tables: one {@code yaml_meta} word per node, whose
 * bit 4 marks an alias and whose bits 8-31 then hold the alias ordinal, and one target node index
 * per ordinal ({@code alias_targets}). A target is always a complete earlier subtree.
 */
public final class TapeFormat {
  /** {@code yaml_meta} bit 4: the node is an alias (a NULL node standing for its target). */
  public static final int ALIAS = 1 << 4;

  /** {@code yaml_meta} bits 8-31 of an alias: its ordinal into the alias target table. */
  public static final int ORDINAL_SHIFT = 8;

  final String name;
  // Precomputed so liveness and confinement checks in compiled reads throw constant-shaped
  // exceptions without string concatenation.
  final String closed;
  final String threadMismatch;
  final String recordClosed;
  final String recordThreadMismatch;
  final long @Nullable [] aliasTargets;
  final int @Nullable [] meta;
  final long[] anchorNodes;
  final String[] tagUris;
  final String[] anchorNames;
  final long maxAliasExpansion;

  private TapeFormat(String name, long @Nullable [] aliasTargets, int @Nullable [] meta) {
    this(name, aliasTargets, meta, new long[0], new String[0], new String[0], 1_000_000);
  }

  private TapeFormat(String name, long @Nullable [] aliasTargets, int @Nullable [] meta,
      long[] anchorNodes, String[] anchorNames, String[] tagUris, long maxAliasExpansion) {
    this.anchorNodes = anchorNodes;
    this.anchorNames = anchorNames;
    this.tagUris = tagUris;
    this.maxAliasExpansion = maxAliasExpansion;
    this.name = Objects.requireNonNull(name);
    this.closed = name + " message closed";
    this.threadMismatch = name + " message thread mismatch";
    this.recordClosed = name + " record closed";
    this.recordThreadMismatch = name + " record thread mismatch";
    this.aliasTargets = aliasTargets;
    this.meta = meta;
  }

  /** A format without alias nodes, named {@code name} in messages. */
  public static TapeFormat of(String name) {
    return new TapeFormat(name, null, null);
  }

  /**
   * This format for one document whose tape holds alias nodes: {@code meta} has one word per node
   * and {@code aliasTargets} one node index per alias ordinal. The arrays are not copied.
   */
  public TapeFormat withAliases(long[] aliasTargets, int[] meta) {
    return new TapeFormat(name, Objects.requireNonNull(aliasTargets), Objects.requireNonNull(meta),
        anchorNodes, anchorNames, tagUris, maxAliasExpansion);
  }

  /** Metadata for a compact tape whose value aliases are native REF words. */
  public TapeFormat withMetadata(int[] meta) {
    return new TapeFormat(name, null, Objects.requireNonNull(meta),
        anchorNodes, anchorNames, tagUris, maxAliasExpansion);
  }

  /** Attach detached YAML anchor/tag tables and the per-export expansion budget. */
  public TapeFormat withEvents(long[] anchors, String[] names, String[] tags, long expansionBudget) {
    if (expansionBudget <= 0) throw new IllegalArgumentException("Alias expansion budget must be positive");
    if (anchors.length != names.length) throw new IllegalArgumentException("Anchor metadata length mismatch");
    return new TapeFormat(name, aliasTargets, meta, anchors.clone(), names.clone(), tags.clone(), expansionBudget);
  }

  TapeFormat slice(long root, int count) {
    int[] metadata = meta;
    long[] aliases = aliasTargets;
    if (metadata == null) return this;
    int[] slice = java.util.Arrays.copyOfRange(metadata, Math.toIntExact(root), Math.toIntExact(root) + count);
    long[] rebased = aliases == null ? null : aliases.clone();
    if (rebased != null) for (int i = 0; i < rebased.length; i++) rebased[i] -= root;
    long[] anchors = anchorNodes.clone();
    for (int i = 0; i < anchors.length; i++) anchors[i] -= root;
    return new TapeFormat(name, rebased, slice, anchors, anchorNames, tagUris, maxAliasExpansion);
  }

  int anchorId(long node) {
    for (int i = 0; i < anchorNodes.length; i++) if (anchorNodes[i] == node) return i;
    return -1;
  }

  /** Raw producer metadata, or zero when none is present. */
  public int metadataWord(long node) { return meta == null ? 0 : meta[Math.toIntExact(node)]; }

  /** Type tag for a source node; aliases carry no tag of their own. */
  public @Nullable String tagUri(long node) {
    int word = metadataWord(node);
    int index = word >>> ORDINAL_SHIFT;
    return (word & ALIAS) != 0 || index == 0 ? null : tagUris[index - 1];
  }

  /** Source anchor name, or null. */
  public @Nullable String anchorName(long node) {
    int id = anchorId(node);
    return id < 0 ? null : anchorNames[id];
  }

  /** The format's name, as used in messages. */
  public String name() {
    return name;
  }

  /** Whether documents of this instance can hold alias nodes. */
  public boolean hasAliases() {
    return meta != null;
  }

  /**
   * The node an alias stands for, or {@code node} itself; O(1): one side-word load, a branch and a
   * target load. Throws when the alias names no node of a tape of {@code nodeCount} nodes.
   */
  long alias(int[] meta, long node, long nodeCount) {
    int word = meta[(int) node];
    if ((word & ALIAS) == 0) return node;
    long[] targets = aliasTargets;
    int ordinal = word >>> ORDINAL_SHIFT;
    if (targets == null || ordinal >= targets.length) throw outside(this);
    long target = targets[ordinal];
    if (target < 0 || target >= nodeCount) throw outside(this);
    return target;
  }

  @TruffleBoundary
  private static IllegalStateException outside(TapeFormat format) {
    return new IllegalStateException(format.name + " alias target outside the tape");
  }
}
