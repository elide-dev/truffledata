package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.SvmTape;
import dev.elide.truffledata.tape.TapeBorrow;
import dev.elide.truffledata.tape.TapeTextArena;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.yaml.generated.Yaml;
import java.lang.foreign.MemorySegment;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

final class SvmSessionBackend extends YamlSession.Backend {
  private final YamlOptions options;
  private boolean directories = true;
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
    return SvmPlanBackend.create(layout, options);
  }

  static void checkTyped(int status, VoidPointer result) {
    if (status == 2 || status == 4 || status == 6 || status == 7) {
      Pointer data = (Pointer) result;
      int length = Math.min(255, data.readInt(128));
      byte[] message = new byte[length];
      for (int i = 0; i < length; i++) message[i] = data.readByte(136 + i);
      YamlStatus.checkDetailed(
          status,
          new String(message, java.nio.charset.StandardCharsets.UTF_8),
          data.readLong(112),
          data.readLong(120));
    }
    YamlStatus.checkStatus(status);
  }

  @Override
  protected void directories(boolean enabled) { directories = enabled; }

  // Hooks for the opt-in Myna cells route (yaml/cells, CellsSvmSessionBackend), which wraps this
  // backend and makes the decode-path calls through cells. Nothing in this class calls them; they
  // are unreachable in an image built without -Dtruffledata.yaml.cells=true.

  VoidPointer sessionHandle() {
    return session;
  }

  VoidPointer resultSlot() {
    return result;
  }

  YamlOptions sessionOptions() {
    return options;
  }

  int decodeFlags() {
    return (options.retainSource() ? 2 : 0) | (directories ? 4 : 0);
  }

  @Override
  protected void decode(
      byte[] data, int offset, int length, int encoding, @Nullable TapeTextArena text) {
    byte[] input = YamlStatus.utf8(data, offset, length, encoding);
    // A non-null address is required even when YAML's input is empty.
    byte[] pinnedBytes = input.length == 0 ? new byte[1] : input;
    int status;
    try (PinnedObject pin = PinnedObject.create(pinnedBytes)) {
      status =
          Yaml.session_decode(
              session,
              pin.addressOfArrayElement(0),
              input.length,
              (options.retainSource() ? 2 : 0) | (directories ? 4 : 0),
              WordFactory.nullPointer(),
              0,
              result);
    }
    check(status, result);
    if (text != null) ((NativeText) text).write(session);
  }

  static void check(int status, VoidPointer result) {
    if (status == 2 || status == 4 || status == 6 || status == 7) {
      Pointer data = (Pointer) result;
      int length = Math.min(255, data.readInt(184));
      byte[] message = new byte[length];
      for (int i = 0; i < length; i++) message[i] = data.readByte(192 + i);
      YamlStatus.checkDetailed(
          status,
          new String(message, java.nio.charset.StandardCharsets.UTF_8),
          data.readLong(168),
          data.readLong(176));
    }
    YamlStatus.checkStatus(status);
  }

  @Override
  protected void decode(MemorySegment data, long length) {
    throw new UnsupportedOperationException("Static YAML sessions take byte arrays");
  }

  @Override
  protected TapeTextArena textArena(byte[] array) {
    return new NativeText(array);
  }

  private static final class NativeText extends TapeTextArena {
    private Pointer table = WordFactory.nullPointer();
    NativeText(byte[] bytes) { super(bytes); }

    void write(VoidPointer session) {
      VoidPointer output = StackValue.get(Yaml.YamlTextResult.SIZE);
      for (;;) {
        int status;
        byte[] bytes = array();
        try (PinnedObject pin = PinnedObject.create(bytes)) {
          status = Yaml.session_write_text(session, pin.addressOfArrayElement(0), bytes.length, output);
        }
        if (status == 10) {
          long required = Yaml.YamlTextResult.required_capacity(output);
          if (required > Integer.MAX_VALUE || required <= bytes.length)
            throw new IllegalArgumentException("YAML text arena capacity");
          ensureCapacity((int) required);
          continue;
        }
        YamlStatus.checkStatus(status);
        table = (Pointer) Yaml.YamlTextResult.table(output);
        return;
      }
    }

    @Override protected long entry(long node) {
      return table.readLong(WordFactory.signed(node * Long.BYTES));
    }
  }

  static final ResultMetadata.Memory MEMORY =
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

  private static SvmTape tape(VoidPointer r) {
    return new SvmTape((Pointer) Yaml.YamlResult.view(r));
  }

  private static class Storage implements YamlMessage.Storage {
    final SvmTape tape;
    final Pointer source;
    final long length;

    Storage(SvmTape tape, VoidPointer r) {
      this.tape = tape;
      source = (Pointer) Yaml.YamlResult.source(r);
      length = Yaml.YamlResult.source_length(r);
    }

    public long span(long node) {
      return tape.span(node);
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
    SvmTape tape = tape(result);
    return new TapeBorrow(
        tape,
        new Storage(tape, result),
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
      YamlStatus.checkStatus(Yaml.session_retain(session, owned));
      Pointer descriptor = (Pointer) Yaml.YamlResult.view(owned);
      long count = SvmYamlMessage.validate(descriptor);
      return SvmYamlMessage.create(
          descriptor,
          owned.rawValue(),
          ResultMetadata.read(
              MEMORY, owned.rawValue(), count, options.limits().maxAliasExpansion()));
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
    int status = Yaml.stream_next(WordFactory.pointer(stream), (options.retainSource() ? 2 : 0) | (directories ? 4 : 0), result);
    if (status != 8 && status != 9) check(status, result);
    return status;
  }

  @Override
  protected void streamRelease(long stream) {
    Yaml.stream_release(WordFactory.pointer(stream));
  }
}
