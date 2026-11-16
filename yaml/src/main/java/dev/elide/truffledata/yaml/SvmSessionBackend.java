package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.SvmTape;
import dev.elide.truffledata.tape.TapeBorrow;
import dev.elide.truffledata.tape.TapeTextArena;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.yaml.generated.Yaml;
import dev.elide.truffledata.yaml.generated.YamlConstants;
import dev.elide.truffledata.yaml.generated.YamlConstants.DecodeFlags;
import dev.elide.truffledata.yaml.generated.YamlConstants.Status;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

final class SvmSessionBackend extends YamlSession.Backend {
  private final YamlOptions options;
  private boolean directories = true;
  private final Yaml.YamlSession session;
  private final Yaml.YamlResult result;

  SvmSessionBackend(YamlOptions options) {
    this.options = options;
    Yaml.YamlOptionsStruct o = UnmanagedMemory.calloc(Yaml.YamlOptionsStruct.SIZE);
    if (o.isNull()) throw new OutOfMemoryError("YAML options allocation");
    Yaml.YamlResult allocated = WordFactory.nullPointer();
    try {
      allocated = UnmanagedMemory.calloc(Yaml.YamlResult.SIZE);
      if (allocated.isNull()) throw new OutOfMemoryError("YAML result allocation");
      writeOptions(o, options);
      session = Yaml.session_create(o);
      if (session.isNull()) throw new OutOfMemoryError("YAML session allocation");
      result = allocated;
    } catch (RuntimeException | Error failure) {
      if (allocated.isNonNull()) UnmanagedMemory.free(allocated);
      throw failure;
    } finally {
      UnmanagedMemory.free(o);
    }
  }

  /** Fills a zeroed options record (complex_keys stays reject, as YamlOptions has no pairs). */
  static void writeOptions(Yaml.YamlOptionsStruct o, YamlOptions options) {
    YamlLimits l = options.limits();
    o.max_input_bytes(l.maxInputBytes());
    o.max_index_bytes(l.maxIndexBytes());
    o.max_nodes(l.maxNodes());
    o.max_text_bytes(l.maxTextBytes());
    o.max_aliases(l.maxAliases());
    o.max_alias_expansion(l.maxAliasExpansion());
    o.max_depth(l.maxDepth());
    o.schema(options.schema().code);
    o.duplicate_keys(options.duplicateKeys().code);
    o.merge_keys(options.mergeKeys() ? 1 : 0);
    o.aliases(options.aliases().code);
    o.unknown_tags(options.unknownTags().code);
    o.version_directive(options.versionDirective().code);
    o.complex_keys(YamlConstants.ComplexKeys.REJECT);
  }

  @Override
  YamlPlan compile(TypedLayout layout, YamlOptions options) {
    return SvmPlanBackend.create(layout, options);
  }

  static void checkTyped(int status, Yaml.YamlTypedResult result) {
    if (YamlStatus.detailed(status))
      YamlStatus.checkDetailed(
          status,
          message(result.addressOfError_message(), result.error_message_length()),
          result.error_line(),
          result.error_column());
    YamlStatus.checkStatus(status);
  }

  /** The NUL-terminated error message of a failed result, at most capacity - 1 bytes. */
  private static String message(CCharPointer text, int published) {
    int length = Math.min(YamlConstants.ERROR_MESSAGE_CAPACITY - 1, published);
    byte[] message = new byte[length];
    for (int i = 0; i < length; i++) message[i] = text.read(i);
    return new String(message, StandardCharsets.UTF_8);
  }

  @Override
  protected void directories(boolean enabled) { directories = enabled; }

  // Hooks for the opt-in Myna cells route (yaml/cells, CellsSvmSessionBackend), which wraps this
  // backend and makes the decode-path calls through cells. Nothing in this class calls them; they
  // are unreachable in an image built without -Dtruffledata.yaml.cells=true.

  Yaml.YamlSession sessionHandle() {
    return session;
  }

  Yaml.YamlResult resultSlot() {
    return result;
  }

  // session_decode's and stream_next's result stay ptr<void> in yaml.seam (see there): the typed
  // result slot is passed as a VoidPointer, the same address.

  /** The flags every decode of this backend passes: the same expression as its own calls. */
  int decodeFlags() {
    return (options.retainSource() ? DecodeFlags.RETAIN_SOURCE : 0)
        | (directories ? DecodeFlags.WANT_DIRECTORIES : 0);
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
              decodeFlags(),
              WordFactory.nullPointer(),
              0,
              (VoidPointer) result);
    }
    check(status, result);
    if (text != null) ((NativeText) text).write(session);
  }

  static void check(int status, Yaml.YamlResult result) {
    if (YamlStatus.detailed(status))
      YamlStatus.checkDetailed(
          status,
          message(result.addressOfError_message(), result.error_message_length()),
          result.error_line(),
          result.error_column());
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
    private CLongPointer table = WordFactory.nullPointer();
    NativeText(byte[] bytes) { super(bytes); }

    void write(Yaml.YamlSession session) {
      Yaml.YamlTextResult output = StackValue.get(Yaml.YamlTextResult.SIZE);
      for (;;) {
        int status;
        byte[] bytes = array();
        try (PinnedObject pin = PinnedObject.create(bytes)) {
          status = Yaml.session_write_text(session, pin.addressOfArrayElement(0), bytes.length, output);
        }
        if (status == Status.TEXT_TOO_SMALL) {
          long required = output.required_capacity();
          if (required > Integer.MAX_VALUE || required <= bytes.length)
            throw new IllegalArgumentException("YAML text arena capacity");
          ensureCapacity((int) required);
          continue;
        }
        YamlStatus.checkStatus(status);
        table = output.table();
        return;
      }
    }

    @Override protected long entry(long node) {
      return table.read(WordFactory.signed(node));
    }
  }

  /** Copies a live result's metadata through the generated record and counted-view accessors. */
  static YamlMetadata metadata(Yaml.YamlResult r, long nodeCount, long expansionBudget) {
    int[] words = new int[Math.toIntExact(nodeCount)];
    CIntPointer meta = r.yaml_meta();
    for (int i = 0; i < words.length; i++) words[i] = meta.read(i);
    int tagCount = Math.toIntExact(r.tag_count());
    String[] tags = ResultMetadata.texts(tagCount);
    for (int i = 0; i < tagCount; i++) {
      Yaml.YamlSpan tag = Yaml.YamlResult_tagsAt(r, i);
      tags[i] = text(r, tag.offset(), tag.length());
    }
    int anchorCount = Math.toIntExact(r.anchor_count());
    long[] anchors = ResultMetadata.anchors(anchorCount);
    String[] names = ResultMetadata.texts(anchorCount);
    for (int i = 0; i < anchorCount; i++) {
      Yaml.YamlAnchor anchor = Yaml.YamlResult_anchorsAt(r, i);
      anchors[i] = anchor.node();
      names[i] = text(r, anchor.offset(), anchor.length());
    }
    int directiveCount = Math.toIntExact(r.directive_count());
    Map<String, String> directives = ResultMetadata.directives(directiveCount);
    for (int i = 0; i < directiveCount; i++) {
      Yaml.YamlTagDirective directive = Yaml.YamlResult_directivesAt(r, i);
      directives.put(
          text(r, directive.handle_offset(), directive.handle_length()),
          text(r, directive.prefix_offset(), directive.prefix_length()));
    }
    return ResultMetadata.create(
        words, tags, anchors, names, directives, r.yaml_version(), expansionBudget);
  }

  /** {@code length} bytes of the result's string table at the unsigned {@code offset}. */
  private static String text(Yaml.YamlResult r, int offset, int length) {
    byte[] utf8 = new byte[length];
    long base = Integer.toUnsignedLong(offset);
    for (int i = 0; i < length; i++) utf8[i] = Yaml.YamlResult_stringsAt(r, base + i);
    return new String(utf8, StandardCharsets.UTF_8);
  }

  private static SvmTape tape(Yaml.YamlResult r) {
    return new SvmTape((Pointer) r.view());
  }

  private static class Storage implements YamlMessage.Storage {
    final SvmTape tape;
    final Pointer source;
    final long length;

    Storage(SvmTape tape, Yaml.YamlResult r) {
      this.tape = tape;
      source = (Pointer) r.source();
      length = r.source_length();
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
        metadata(result, tape.nodeCount(), options.limits().maxAliasExpansion()).format);
  }

  @Override
  protected YamlMessage retain() {
    Yaml.YamlResult owned = UnmanagedMemory.calloc(Yaml.YamlResult.SIZE);
    if (owned.isNull()) {
      reset();
      throw new OutOfMemoryError("YAML retained result allocation");
    }
    try {
      YamlStatus.checkStatus(Yaml.session_retain(session, owned));
      Pointer descriptor = (Pointer) owned.view();
      long count = SvmYamlMessage.validate(descriptor);
      return SvmYamlMessage.create(
          descriptor,
          owned.rawValue(),
          metadata(owned, count, options.limits().maxAliasExpansion()));
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
    Yaml.YamlStream stream = Yaml.stream_create(session, maxDocumentBytes, growth);
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
    int status = Yaml.stream_next(WordFactory.pointer(stream), decodeFlags(), (VoidPointer) result);
    if (status != Status.STREAM_EMPTY && status != Status.STREAM_TRUNCATED) check(status, result);
    return status;
  }

  @Override
  protected void streamRelease(long stream) {
    Yaml.stream_release(WordFactory.pointer(stream));
  }
}
