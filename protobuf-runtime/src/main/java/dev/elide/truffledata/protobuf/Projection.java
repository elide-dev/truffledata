package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.ContractViolation;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Immutable field selection for one slab layout. Unselected repeated fields retain their count. */
public final class Projection {
  public static final int MAGIC = 0x31504454;
  public static final int HEADER_BYTES = 24;
  public static final int LAX = 1;
  public static final int LAZY_CHILDREN = 2;
  public static final int BORROW_BYTES = 4;
  public static final int DEFAULT_BORROW_BYTES = 4096;

  private final Layout layout;

  @CompilationFinal(dimensions = 2)
  private final int[][] wanted;

  private final int flags;
  private final int borrowBytes;
  private final byte[] program;

  @CompilationFinal(dimensions = 1)
  private final boolean[] requiredClosure;

  private final boolean complete;

  private Projection(Layout layout, int[][] wanted, int flags, int borrowBytes) {
    this.layout = layout;
    this.wanted = Arrays.stream(wanted).map(int[]::clone).toArray(int[][]::new);
    this.flags = flags;
    this.borrowBytes = borrowBytes;
    requiredClosure = new boolean[layout.types()];
    boolean changed;
    do {
      changed = false;
      for (int type = 0; type < layout.types(); type++) {
        if (requiredClosure[type]) continue;
        var plan = layout.plan(type);
        for (int ordinal = 0; ordinal < plan.size(); ordinal++) {
          var field = plan.field(ordinal);
          if (field.required()
              || (field.kind().message() && requiredClosure[layout.childType(type, ordinal)])) {
            requiredClosure[type] = true;
            changed = true;
            break;
          }
        }
      }
    } while (changed);
    int size = HEADER_BYTES;
    for (int[] words : this.wanted)
      size = Math.addExact(size, 4 + Math.multiplyExact(4, words.length));
    var output = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    output
        .putInt(MAGIC)
        .putInt(size)
        .putInt(layout.fingerprint())
        .putInt(flags)
        .putInt(layout.types())
        .putInt(borrowBytes);
    for (int type = 0; type < this.wanted.length; type++) {
      output.putInt(layout.fields(type));
      for (int word : this.wanted[type]) output.putInt(word);
    }
    program = output.array();
    boolean selected = true;
    for (int type = 0; type < layout.types(); type++)
      for (int ordinal = 0; ordinal < layout.fields(type); ordinal++)
        selected &= wants(type, ordinal);
    complete = selected;
  }

  /** Start with fields omitted; map-entry key/value fields are always structurally selected. */
  public static Builder builder(ProtoPlan plan) {
    return new Builder(plan.layout());
  }

  /** Select every field, useful when enabling deferred payload borrowing alone. */
  public static Projection all(ProtoPlan plan) {
    var builder = builder(plan);
    for (int type = 0; type < builder.layout.types(); type++)
      builder.all(builder.layout.plan(type).name());
    return builder.build();
  }

  public Layout layout() {
    return layout;
  }

  public boolean wants(int type, int ordinal) {
    if (type < 0 || type >= wanted.length || ordinal < 0 || ordinal >= layout.fields(type))
      throw invalidField();
    return (wanted[type][ordinal >>> 5] & (1 << ordinal)) != 0;
  }

  @TruffleBoundary
  private static IndexOutOfBoundsException invalidField() {
    return new IndexOutOfBoundsException("Projection field");
  }

  /** Whether all fields of every reachable type are selected. */
  public boolean complete() {
    return complete;
  }

  /** Native decode flags; their bit assignments differ from slab-result flags. */
  public int decodeFlags() {
    return (requiresBorrow() ? 8 : 1) | (laxValidation() ? 2 : 0);
  }

  /** An omitted message list can retain its cardinality without element slots. */
  public boolean countsOnly(int type, int ordinal) {
    var field = layout.plan(type).field(ordinal);
    return !wants(type, ordinal)
        && !lazyChildrenEnabled()
        && field.repeated()
        && field.kind() == ProtoPlan.Kind.MESSAGE
        && !field.map()
        && field.oneof() < 0
        && !requiresEagerChild(type, ordinal);
  }

  /** Permit skipped subtrees to defer UTF-8 validation until their first lazy read. */
  public Projection lax() {
    return new Projection(layout, wanted, flags | LAX, borrowBytes);
  }

  /** Preserve omitted child wire spans for strict decoding on first access. Requires a borrow. */
  public Projection lazyChildren() {
    return new Projection(layout, wanted, flags | LAZY_CHILDREN, borrowBytes);
  }

  public Projection borrowBytes() {
    return borrowBytes(DEFAULT_BORROW_BYTES);
  }

  /** Borrow bytes payloads at or above this size; zero disables payload borrowing. */
  public Projection borrowBytes(int threshold) {
    if (threshold < 0) throw new IllegalArgumentException("Negative bytes borrowing threshold");
    return new Projection(
        layout, wanted, threshold == 0 ? flags & ~BORROW_BYTES : flags | BORROW_BYTES, threshold);
  }

  /** Required descendants keep eager representation so later oneof/map replacement stays legal. */
  public boolean requiresEagerChild(int type, int ordinal) {
    return layout.plan(type).field(ordinal).kind().message()
        && requiredClosure[layout.childType(type, ordinal)];
  }

  public boolean laxValidation() {
    return (flags & LAX) != 0;
  }

  public boolean lazyChildrenEnabled() {
    return (flags & LAZY_CHILDREN) != 0;
  }

  public int borrowBytesThreshold() {
    return borrowBytes;
  }

  public boolean requiresBorrow() {
    return (flags & (LAZY_CHILDREN | BORROW_BYTES)) != 0;
  }

  /** Defensive TDP1 program copy, in the layout's closed type order. */
  public byte[] program() {
    return program.clone();
  }

  public void requireLayout(Layout expected) {
    if (expected != layout) throw new ContractViolation("Projection layout identity mismatch");
  }

  public static final class Builder {
    private final Layout layout;
    private final int[][] wanted;

    private Builder(Layout layout) {
      this.layout = layout;
      wanted = new int[layout.types()][];
      for (int type = 0; type < wanted.length; type++) {
        wanted[type] = new int[(layout.fields(type) + 31) >>> 5];
        if (layout.plan(type).definition().mapEntry()) all(layout.plan(type).name());
      }
    }

    public Builder selectType(String typeName, String... fields) {
      int type = type(typeName);
      for (String name : fields) {
        int ordinal = layout.plan(type).schema().indexOf(name);
        if (ordinal < 0)
          throw new IllegalArgumentException("Unknown projected field: " + typeName + "." + name);
        wanted[type][ordinal >>> 5] |= 1 << ordinal;
      }
      return this;
    }

    public Builder select(String... rootFields) {
      return selectType(layout.plan(0).name(), rootFields);
    }

    public Builder all(String typeName) {
      int type = type(typeName);
      for (int ordinal = 0; ordinal < layout.fields(type); ordinal++)
        wanted[type][ordinal >>> 5] |= 1 << ordinal;
      return this;
    }

    private int type(String name) {
      for (int type = 0; type < layout.types(); type++)
        if (layout.plan(type).name().equals(name)) return type;
      throw new IllegalArgumentException("Unknown projected type: " + name);
    }

    public Projection build() {
      return new Projection(layout, wanted, 0, 0);
    }
  }
}
