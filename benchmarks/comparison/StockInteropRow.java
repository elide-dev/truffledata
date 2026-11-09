package dev.elide.truffledata.comparison;

import com.google.protobuf.Type;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.ProtoPlan;
import org.graalvm.nativeimage.ImageInfo;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Benchmark-only schema bridge: every value comes from protobuf-java through host interop.
 * Immutable child wrappers and list sizes are cached, as for slab readers. Scalar/string values
 * continue through cached host messages during every consumer execution.
 */
@NullMarked
@ExportLibrary(DataLibrary.class)
final class StockInteropRow extends AbstractRow {
  final Object host;
  private final ProtoPlan plan;
  final int kind;
  private final @Nullable Object[] cached;

  private StockInteropRow(Object host, ProtoPlan plan, int kind) {
    super(plan.schema());
    this.host = host;
    this.plan = plan;
    this.kind = kind;
    cached = new Object[plan.size()];
  }

  static StockInteropRow create(Type value, ProtoPlan plan) {
    return new StockInteropRow(ComparisonLanguage.guest(value), plan, 0);
  }

  private Object readValue(int kind, int index, InteropLibrary hosts, InteropLibrary values) {
    try {
      if (kind == 0)
        return switch (index) {
          case 0 -> values.asString(invoke(hosts, host, "getName"));
          case 1 -> array(index, plan.nested(index));
          case 2 -> array(index, null);
          case 4 -> context(index);
          case 5 -> values.asLong(invoke(hosts, host, "getSyntaxValue"));
          default -> throw new IllegalArgumentException("Unused Type benchmark field");
        };
      if (kind == 2) return values.asString(invoke(hosts, host, "getFileName"));
      return switch (index) {
        case 0 -> values.asLong(invoke(hosts, host, "getKindValue"));
        case 1 -> values.asLong(invoke(hosts, host, "getCardinalityValue"));
        case 2 -> values.asLong(invoke(hosts, host, "getNumber"));
        case 3 -> values.asString(invoke(hosts, host, "getName"));
        case 6 -> values.asBoolean(invoke(hosts, host, "getPacked"));
        default -> throw new IllegalArgumentException("Unused Field benchmark field");
      };
    } catch (InteropException failure) {
      throw failed(failure);
    }
  }

  private static Object invoke(InteropLibrary hosts, Object host, String method)
      throws InteropException {
    return ImageInfo.inImageCode()
        ? nativeHostGetter(hosts, host, method)
        : hosts.invokeMember(host, method);
  }

  private static Object invokeIndexed(InteropLibrary hosts, Object host, String method, int index)
      throws InteropException {
    return ImageInfo.inImageCode()
        ? nativeHostIndexedGetter(hosts, host, method, index)
        : hosts.invokeMember(host, method, index);
  }

  // Native Image's reflective host stub cannot enter the guest JIT. Keep that exact host call
  // explicit and measured; the shared guest read nodes and result conversion still compile.
  @TruffleBoundary
  private static Object nativeHostGetter(InteropLibrary hosts, Object host, String method)
      throws InteropException {
    return hosts.invokeMember(host, method);
  }

  @TruffleBoundary
  private static Object nativeHostIndexedGetter(
      InteropLibrary hosts, Object host, String method, int index) throws InteropException {
    return hosts.invokeMember(host, method, index);
  }

  @TruffleBoundary
  private static IllegalStateException failed(Exception failure) {
    return new IllegalStateException("Stock host-interop bridge failed", failure);
  }

  private Object array(int index, @Nullable ProtoPlan child) {
    Object result = cached[index];
    return result == null ? materializeArray(index, child) : result;
  }

  @TruffleBoundary
  private Object materializeArray(int index, @Nullable ProtoPlan child) {
    try {
      return cached[index] = new Array(host, child);
    } catch (InteropException failure) {
      throw failed(failure);
    }
  }

  private Object context(int index) {
    Object result = cached[index];
    return result == null ? materializeContext(index) : result;
  }

  @TruffleBoundary
  private Object materializeContext(int index) {
    try {
      var interop = InteropLibrary.getUncached();
      return cached[index] =
          interop.asBoolean(interop.invokeMember(host, "hasSourceContext"))
              ? new StockInteropRow(
                  interop.invokeMember(host, "getSourceContext"), plan.nested(index), 2)
              : Missing.INSTANCE;
    } catch (InteropException failure) {
      throw failed(failure);
    }
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  static final class Read {
    @Specialization(guards = "row.kind == kind")
    static Object cached(
        StockInteropRow row,
        int index,
        @Cached.Shared("kind")
            @Cached(value = "row.kind", allowUncached = true, neverDefault = false)
            int kind,
        @CachedLibrary("row.host") InteropLibrary hosts,
        @Cached.Shared("values") @CachedLibrary(limit = "3") InteropLibrary values) {
      return row.readValue(kind, index, hosts, values);
    }
  }

  @ExportMessage
  static final class ReadLong {
    @Specialization(guards = "row.kind == kind")
    static long cached(
        StockInteropRow row,
        int index,
        @Cached.Shared("kind")
            @Cached(value = "row.kind", allowUncached = true, neverDefault = false)
            int kind,
        @CachedLibrary("row.host") InteropLibrary hosts,
        @Cached.Shared("values") @CachedLibrary(limit = "3") InteropLibrary values)
        throws UnexpectedResultException {
      Object value = row.readValue(kind, index, hosts, values);
      if (value instanceof Long n) return n;
      throw new UnexpectedResultException(value);
    }
  }

  @ExportMessage
  static final class ReadBoolean {
    @Specialization(guards = "row.kind == kind")
    static boolean cached(
        StockInteropRow row,
        int index,
        @Cached.Shared("kind")
            @Cached(value = "row.kind", allowUncached = true, neverDefault = false)
            int kind,
        @CachedLibrary("row.host") InteropLibrary hosts,
        @Cached.Shared("values") @CachedLibrary(limit = "3") InteropLibrary values)
        throws UnexpectedResultException {
      Object value = row.readValue(kind, index, hosts, values);
      if (value instanceof Boolean b) return b;
      throw new UnexpectedResultException(value);
    }
  }

  @ExportLibrary(InteropLibrary.class)
  static final class Array implements TruffleObject {
    final Object host;
    private final @Nullable ProtoPlan child;
    private final @Nullable Object[] cached;

    Array(Object host, @Nullable ProtoPlan child) throws InteropException {
      this.host = host;
      this.child = child;
      var interop = InteropLibrary.getUncached();
      cached =
          new Object
              [interop.asInt(
                  interop.invokeMember(host, child == null ? "getOneofsCount" : "getFieldsCount"))];
    }

    @ExportMessage
    boolean hasArrayElements() {
      return true;
    }

    @ExportMessage
    long getArraySize() {
      return cached.length;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
      return index >= 0 && index < cached.length;
    }

    @ExportMessage
    static Object readArrayElement(
        Array array,
        long index,
        @CachedLibrary("array.host") InteropLibrary lists,
        @CachedLibrary(limit = "3") InteropLibrary values)
        throws InvalidArrayIndexException {
      if (!array.isArrayElementReadable(index)) throw InvalidArrayIndexException.create(index);
      try {
        return array.child == null
            ? values.asString(invokeIndexed(lists, array.host, "getOneofs", (int) index))
            : array.child((int) index);
      } catch (InteropException failure) {
        throw failed(failure);
      }
    }

    private Object child(int index) {
      Object result = cached[index];
      return result == null ? materializeChild(index) : result;
    }

    @TruffleBoundary
    private Object materializeChild(int index) {
      try {
        return cached[index] =
            new StockInteropRow(
                InteropLibrary.getUncached().invokeMember(host, "getFields", index),
                java.util.Objects.requireNonNull(child),
                1);
      } catch (InteropException failure) {
        throw failed(failure);
      }
    }
  }
}
