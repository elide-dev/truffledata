package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.SegmentTape;
import dev.elide.truffledata.tape.TapeBorrow;
import dev.elide.truffledata.tape.TapeTextArena;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.yaml.generated.YamlFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

final class JvmSessionBackend extends YamlSession.Backend {
  private final YamlOptions options;
  private final YamlFFM.LibraryBinding calls;
  private final Arena library;
  private final Arena arena = Arena.ofConfined();
  private final MemorySegment session;
  private final MemorySegment result;
  private Arena stagingArena = Arena.ofConfined();
  private MemorySegment staging = MemorySegment.NULL;
  private int references = 1;

  JvmSessionBackend(YamlOptions options, YamlFFM.LibraryBinding calls, Arena library) {
    this.options = options;
    this.calls = calls;
    this.library = library;
    try {
      MemorySegment o = arena.allocate(YamlFFM.YamlOptionsStruct_LAYOUT);
      writeOptions(o, options);
      result = arena.allocate(YamlFFM.YamlResult_LAYOUT);
      try {
        session = calls.session_create(o);
      } catch (Throwable failure) {
        throw YamlJvm.callFailure(failure);
      }
      if (session.address() == 0) throw new OutOfMemoryError("YAML session allocation");
    } catch (RuntimeException | Error failure) {
      stagingArena.close();
      arena.close();
      throw failure;
    }
  }

  private MemorySegment stage(byte[] bytes, int offset, int length) {
    if (staging.byteSize() < Math.max(1, length)) {
      Arena grown = Arena.ofConfined();
      MemorySegment replacement;
      try {
        replacement = grown.allocate(Math.max(1, length), 8);
      } catch (RuntimeException | Error failure) {
        grown.close();
        throw failure;
      }
      stagingArena.close();
      stagingArena = grown;
      staging = replacement;
    }
    MemorySegment.copy(bytes, offset, staging, ValueLayout.JAVA_BYTE, 0, length);
    return staging;
  }

  static void writeOptions(MemorySegment o, YamlOptions options) {
    YamlLimits l = options.limits();
    long[] budgets = {
      l.maxInputBytes(),
      l.maxIndexBytes(),
      l.maxNodes(),
      l.maxTextBytes(),
      l.maxAliases(),
      l.maxAliasExpansion()
    };
    for (int i = 0; i < budgets.length; i++) o.set(ValueLayout.JAVA_LONG, 8L * i, budgets[i]);
    int[] policies = {
      l.maxDepth(),
      options.schema().ordinal(),
      options.duplicateKeys().ordinal(),
      options.mergeKeys() ? 1 : 0,
      options.aliases().ordinal(),
      options.unknownTags().ordinal(),
      options.versionDirective().ordinal(),
      0
    };
    for (int i = 0; i < policies.length; i++)
      o.set(ValueLayout.JAVA_INT, 48L + 4L * i, policies[i]);
  }

  private void reference() {
    references++;
  }

  @Override
  YamlPlan compile(TypedLayout layout, YamlOptions options) {
    return new YamlPlan(
        layout.records,
        new JvmPlanBackend(calls, layout, options, this::reference, this::dereference),
        options.limits().maxInputBytes());
  }

  @Override
  protected void decode(
      byte[] bytes, int offset, int length, int encoding, @Nullable TapeTextArena text) {
    byte[] utf8 = YamlSvm.utf8(bytes, offset, length, encoding);
    call(stage(utf8, 0, utf8.length), utf8.length);
  }

  @Override
  protected void decode(MemorySegment data, long length) {
    if (!data.isNative() || data.address() == 0 || length < 0 || length > data.byteSize())
      throw new IllegalArgumentException("Expected a bounded native YAML segment");
    call(data.asSlice(0, length), length);
  }

  private void call(MemorySegment data, long length) {
    int status;
    try {
      status =
          calls.session_decode(
              session,
              data,
              length,
              options.retainSource() ? 2 : 0,
              MemorySegment.NULL,
              0L,
              result);
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    }
    check(status, result);
  }

  private static void check(int status, MemorySegment result) {
    if (status == 2 || status == 4 || status == 6 || status == 7) {
      int length = result.get(ValueLayout.JAVA_INT, 184);
      String detail =
          new String(
              result.asSlice(192, Math.min(255, length)).toArray(ValueLayout.JAVA_BYTE),
              StandardCharsets.UTF_8);
      YamlSvm.checkDetailed(
          status,
          detail,
          result.get(ValueLayout.JAVA_LONG, 168),
          result.get(ValueLayout.JAVA_LONG, 176));
    }
    YamlSvm.checkStatus(status);
  }

  @Override
  protected @Nullable TapeTextArena textArena(byte[] bytes) {
    return null;
  }

  private static final ResultMetadata.Memory MEMORY =
      new ResultMetadata.Memory() {
        private MemorySegment at(long base, long offset, long size) {
          return MemorySegment.ofAddress(base + offset).reinterpret(size);
        }

        public long pointer(long base, int offset) {
          return word(base, offset);
        }

        public long word(long base, long offset) {
          return at(base, offset, 8).get(ValueLayout.JAVA_LONG, 0);
        }

        public int integer(long base, long offset) {
          return at(base, offset, 4).get(ValueLayout.JAVA_INT, 0);
        }

        public byte octet(long base, long offset) {
          return at(base, offset, 1).get(ValueLayout.JAVA_BYTE, 0);
        }
      };

  private static SegmentTape tape(MemorySegment r) {
    return new SegmentTape(r.get(ValueLayout.ADDRESS, 8));
  }

  private static class Storage implements YamlMessage.Storage {
    final SegmentTape tape;
    final MemorySegment source;

    Storage(SegmentTape tape, MemorySegment r) {
      this.tape = tape;
      source = r.get(ValueLayout.ADDRESS, 144).reinterpret(r.get(ValueLayout.JAVA_LONG, 152));
    }

    public long span(long node) {
      return tape.span(node);
    }

    public byte inputByte(long offset) {
      return source.get(ValueLayout.JAVA_BYTE, offset);
    }

    public void run() {}
  }

  @Override
  protected TapeBorrow borrow() {
    SegmentTape tape = tape(result);
    YamlMetadata meta =
        ResultMetadata.read(
            MEMORY, result.address(), tape.nodeCount(), options.limits().maxAliasExpansion());
    return new TapeBorrow(tape, new Storage(tape, result), meta.format);
  }

  @Override
  protected YamlMessage retain() {
    Arena ownedArena = Arena.ofConfined();
    MemorySegment owned = ownedArena.allocate(YamlFFM.YamlResult_LAYOUT);
    boolean retained = false;
    try {
      int status;
      try {
        status = calls.session_retain(session, owned);
      } catch (Throwable failure) {
        throw YamlJvm.callFailure(failure);
      }
      check(status, owned);
      SegmentTape tape = tape(owned);
      YamlMetadata meta =
          ResultMetadata.read(
              MEMORY, owned.address(), tape.nodeCount(), options.limits().maxAliasExpansion());
      YamlMessage message =
          YamlMessage.create(
              tape,
              new Storage(tape, owned) {
                @Override
                public void run() {
                  try {
                    calls.release(owned);
                  } catch (Throwable failure) {
                    throw YamlJvm.callFailure(failure);
                  } finally {
                    ownedArena.close();
                    dereference();
                  }
                }
              },
              meta);
      references++;
      retained = true;
      return message;
    } finally {
      if (!retained) {
        try {
          calls.release(owned);
          calls.session_reset(session);
        } catch (Throwable failure) {
          throw YamlJvm.callFailure(failure);
        } finally {
          ownedArena.close();
        }
      }
    }
  }

  private void dereference() {
    if (--references == 0) library.close();
  }

  @Override
  protected void reset() {
    try {
      calls.session_reset(session);
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    }
  }

  @Override
  protected void release() {
    try {
      calls.session_release(session);
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    } finally {
      stagingArena.close();
      arena.close();
      dereference();
    }
  }

  @Override
  protected long streamCreate(int maxDocumentBytes, int growth) {
    try {
      MemorySegment stream = calls.stream_create(session, maxDocumentBytes, growth);
      if (stream.address() == 0) throw new OutOfMemoryError("YAML stream allocation");
      return stream.address();
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    }
  }

  @Override
  protected int streamFeed(long stream, byte[] bytes, int offset, int length) {
    try {
      return (int)
          calls.stream_feed(MemorySegment.ofAddress(stream), stage(bytes, offset, length), length);
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    }
  }

  @Override
  protected long streamFeed(long stream, MemorySegment data) {
    if (!data.isNative() || data.address() == 0)
      throw new IllegalArgumentException("Expected native YAML input");
    try {
      return calls.stream_feed(MemorySegment.ofAddress(stream), data, data.byteSize());
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    }
  }

  @Override
  protected void streamFinish(long stream) {
    try {
      calls.stream_finish(MemorySegment.ofAddress(stream));
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    }
  }

  @Override
  protected int streamNext(long stream) {
    try {
      int status = calls.stream_next(MemorySegment.ofAddress(stream), result);
      if (status != 8 && status != 9) check(status, result);
      return status;
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    }
  }

  @Override
  protected void streamRelease(long stream) {
    try {
      calls.stream_release(MemorySegment.ofAddress(stream));
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    }
  }
}
