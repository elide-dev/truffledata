package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.SvmValueTape;
import dev.elide.truffledata.tape.TapeBorrow;
import dev.elide.truffledata.tape.TapeTextArena;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.yaml.generated.Yaml;
import java.lang.foreign.MemorySegment;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

final class SvmSessionBackend extends YamlSession.Backend {
  private final YamlOptions options;
  private final VoidPointer session;
  private final VoidPointer result;

  SvmSessionBackend(YamlOptions options) {
    this.options = options;
    Pointer o = UnmanagedMemory.calloc(Yaml.YamlOptionsStruct.SIZE);
    if (o.isNull()) throw new OutOfMemoryError("YAML options allocation");
    VoidPointer allocated = WordFactory.nullPointer();
    try {
      allocated = UnmanagedMemory.calloc(Yaml.YamlResult.SIZE);
      if (allocated.isNull()) throw new OutOfMemoryError("YAML result allocation");
      writeOptions(o, options);
      session = Yaml.session_create((VoidPointer) o);
      if (session.isNull()) throw new OutOfMemoryError("YAML session allocation");
      result = allocated;
    } catch (RuntimeException | Error failure) {
      if (allocated.isNonNull()) UnmanagedMemory.free(allocated);
      throw failure;
    } finally {
      UnmanagedMemory.free(o);
    }
  }

  static void writeOptions(Pointer o, YamlOptions options) {
    YamlLimits l = options.limits();
    long[] budgets = {
      l.maxInputBytes(),
      l.maxIndexBytes(),
      l.maxNodes(),
      l.maxTextBytes(),
      l.maxAliases(),
      l.maxAliasExpansion()
    };
    for (int i = 0; i < budgets.length; i++) o.writeLong(i * 8, budgets[i]);
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
    for (int i = 0; i < policies.length; i++) o.writeInt(48 + i * 4, policies[i]);
  }

  @Override
  YamlPlan compile(TypedLayout layout, YamlOptions options) {
    return YamlPlan.create(layout, options);
  }

  static void checkTyped(int status, VoidPointer result) {
    if (status == 2 || status == 4 || status == 6 || status == 7) {
      Pointer data = (Pointer) result;
      int length = Math.min(255, data.readInt(128));
      byte[] message = new byte[length];
      for (int i = 0; i < length; i++) message[i] = data.readByte(136 + i);
      YamlSvm.checkDetailed(
          status,
          new String(message, java.nio.charset.StandardCharsets.UTF_8),
          data.readLong(112),
          data.readLong(120));
    }
    YamlSvm.checkStatus(status);
  }

  @Override
  protected void decode(
      byte[] data, int offset, int length, int encoding, @Nullable TapeTextArena text) {
    byte[] input = YamlSvm.utf8(data, offset, length, encoding);
    // A non-null address is required even when YAML's input is empty.
    byte[] pinnedBytes = input.length == 0 ? new byte[1] : input;
    int status;
    try (PinnedObject pin = PinnedObject.create(pinnedBytes)) {
      status =
          Yaml.session_decode(
              session,
              pin.addressOfArrayElement(0),
              input.length,
              options.retainSource() ? 2 : 0,
              WordFactory.nullPointer(),
              0,
              result);
    }
    check(status, result);
  }

  private static void check(int status, VoidPointer result) {
    if (status == 2 || status == 4 || status == 6 || status == 7) {
      Pointer data = (Pointer) result;
      int length = Math.min(255, data.readInt(224));
      byte[] message = new byte[length];
      for (int i = 0; i < length; i++) message[i] = data.readByte(232 + i);
      YamlSvm.checkDetailed(
          status,
          new String(message, java.nio.charset.StandardCharsets.UTF_8),
          data.readLong(208),
          data.readLong(216));
    }
    YamlSvm.checkStatus(status);
  }

  @Override
  protected void decode(MemorySegment data, long length) {
    throw new UnsupportedOperationException("Static YAML sessions take byte arrays");
  }

  @Override
  protected @Nullable TapeTextArena textArena(byte[] array) {
    return null;
  }

  private static final ResultMetadata.Memory MEMORY =
      new ResultMetadata.Memory() {
        public long pointer(long base, int offset) {
          return word(base, offset);
        }

        public long word(long base, long offset) {
          return WordFactory.<Pointer>pointer(base).readLong(WordFactory.signed(offset));
        }

        public int integer(long base, long offset) {
          return WordFactory.<Pointer>pointer(base).readInt(WordFactory.signed(offset));
        }

        public byte octet(long base, long offset) {
          return WordFactory.<Pointer>pointer(base).readByte(WordFactory.signed(offset));
        }
      };

  private static SvmValueTape tape(VoidPointer r) {
    return new SvmValueTape(
        (Pointer) Yaml.YamlResult.view(r), (Pointer) Yaml.YamlResult.key_hashes(r));
  }

  private static class Storage implements YamlMessage.Storage {
    final Pointer spans;
    final Pointer source;
    final long length;

    Storage(VoidPointer r) {
      spans = (Pointer) Yaml.YamlResult.lexical(r);
      source = (Pointer) Yaml.YamlResult.source(r);
      length = Yaml.YamlResult.source_length(r);
    }

    public long span(long node) {
      return spans.readLong(WordFactory.signed(node * 8));
    }

    public byte inputByte(long offset) {
      if (offset < 0 || offset >= length)
        throw new IndexOutOfBoundsException("YAML lexical offset");
      return source.readByte(WordFactory.signed(offset));
    }

    public void run() {}
  }

  @Override
  protected TapeBorrow borrow() {
    SvmValueTape tape = tape(result);
    return new TapeBorrow(
        tape,
        new Storage(result),
        ResultMetadata.read(
                MEMORY, result.rawValue(), tape.nodeCount(), options.limits().maxAliasExpansion())
            .format);
  }

  @Override
  protected YamlMessage retain() {
    VoidPointer owned = UnmanagedMemory.calloc(Yaml.YamlResult.SIZE);
    if (owned.isNull()) {
      reset();
      throw new OutOfMemoryError("YAML retained result allocation");
    }
    try {
      YamlSvm.checkStatus(Yaml.session_retain(session, owned));
      SvmValueTape tape = tape(owned);
      return new YamlMessage(
          tape,
          new Storage(owned) {
            @Override
            public void run() {
              Yaml.release(owned);
              UnmanagedMemory.free(owned);
            }
          },
          ResultMetadata.read(
              MEMORY, owned.rawValue(), tape.nodeCount(), options.limits().maxAliasExpansion()));
    } catch (RuntimeException | Error failure) {
      Yaml.release(owned);
      UnmanagedMemory.free(owned);
      reset();
      throw failure;
    }
  }

  @Override
  protected void reset() {
    Yaml.session_reset(session);
  }

  @Override
  protected void release() {
    Yaml.session_release(session);
    UnmanagedMemory.free(result);
  }

  @Override
  protected long streamCreate(int maxDocumentBytes, int growth) {
    VoidPointer stream = Yaml.stream_create(session, maxDocumentBytes, growth);
    if (stream.isNull()) throw new OutOfMemoryError("YAML stream allocation");
    return stream.rawValue();
  }

  @Override
  protected int streamFeed(long stream, byte[] bytes, int offset, int length) {
    try (PinnedObject pin = PinnedObject.create(bytes)) {
      return (int)
          Yaml.stream_feed(WordFactory.pointer(stream), pin.addressOfArrayElement(offset), length);
    }
  }

  @Override
  protected long streamFeed(long stream, MemorySegment data) {
    throw new UnsupportedOperationException("Static YAML streams take byte arrays");
  }

  @Override
  protected void streamFinish(long stream) {
    Yaml.stream_finish(WordFactory.pointer(stream));
  }

  @Override
  protected int streamNext(long stream) {
    int status = Yaml.stream_next(WordFactory.pointer(stream), result);
    if (status != 8 && status != 9) check(status, result);
    return status;
  }

  @Override
  protected void streamRelease(long stream) {
    Yaml.stream_release(WordFactory.pointer(stream));
  }
}
