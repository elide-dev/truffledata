package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.SegmentTape;
import dev.elide.truffledata.tape.TapeBorrow;
import dev.elide.truffledata.tape.TapeTextArena;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.yaml.generated.YamlConstants;
import dev.elide.truffledata.yaml.generated.YamlConstants.DecodeFlags;
import dev.elide.truffledata.yaml.generated.YamlConstants.Status;
import dev.elide.truffledata.yaml.generated.YamlFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class JvmSessionBackend extends YamlSession.Backend {
  private final YamlOptions options;
  private boolean directories = true;
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

  MemorySegment stage(byte[] bytes, int offset, int length) {
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

  /** Fills an options record (complex_keys stays reject, as YamlOptions has no pairs). */
  static void writeOptions(MemorySegment o, YamlOptions options) {
    YamlLimits l = options.limits();
    o.set(ValueLayout.JAVA_LONG, YamlFFM.YamlOptionsStruct_max_input_bytes_OFFSET, l.maxInputBytes());
    o.set(ValueLayout.JAVA_LONG, YamlFFM.YamlOptionsStruct_max_index_bytes_OFFSET, l.maxIndexBytes());
    o.set(ValueLayout.JAVA_LONG, YamlFFM.YamlOptionsStruct_max_nodes_OFFSET, l.maxNodes());
    o.set(ValueLayout.JAVA_LONG, YamlFFM.YamlOptionsStruct_max_text_bytes_OFFSET, l.maxTextBytes());
    o.set(ValueLayout.JAVA_LONG, YamlFFM.YamlOptionsStruct_max_aliases_OFFSET, l.maxAliases());
    o.set(
        ValueLayout.JAVA_LONG,
        YamlFFM.YamlOptionsStruct_max_alias_expansion_OFFSET,
        l.maxAliasExpansion());
    o.set(ValueLayout.JAVA_INT, YamlFFM.YamlOptionsStruct_max_depth_OFFSET, l.maxDepth());
    o.set(ValueLayout.JAVA_INT, YamlFFM.YamlOptionsStruct_schema_OFFSET, options.schema().code);
    o.set(
        ValueLayout.JAVA_INT,
        YamlFFM.YamlOptionsStruct_duplicate_keys_OFFSET,
        options.duplicateKeys().code);
    o.set(
        ValueLayout.JAVA_INT, YamlFFM.YamlOptionsStruct_merge_keys_OFFSET, options.mergeKeys() ? 1 : 0);
    o.set(ValueLayout.JAVA_INT, YamlFFM.YamlOptionsStruct_aliases_OFFSET, options.aliases().code);
    o.set(
        ValueLayout.JAVA_INT,
        YamlFFM.YamlOptionsStruct_unknown_tags_OFFSET,
        options.unknownTags().code);
    o.set(
        ValueLayout.JAVA_INT,
        YamlFFM.YamlOptionsStruct_version_directive_OFFSET,
        options.versionDirective().code);
    o.set(
        ValueLayout.JAVA_INT,
        YamlFFM.YamlOptionsStruct_complex_keys_OFFSET,
        YamlConstants.ComplexKeys.REJECT);
  }

  void reference() {
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
  protected void directories(boolean enabled) { directories = enabled; }

  // Hooks for the opt-in Myna cells route (yaml/cells, CellsJvmSessionBackend), which wraps this
  // backend and makes the decode-path calls through cells; nothing in this class calls them.

  YamlFFM.LibraryBinding calls() {
    return calls;
  }

  MemorySegment sessionHandle() {
    return session;
  }

  MemorySegment resultSlot() {
    return result;
  }

  YamlOptions sessionOptions() {
    return options;
  }

  /** The flags every decode of this backend passes: the same expression as its own calls. */
  int decodeFlags() {
    return (options.retainSource() ? DecodeFlags.RETAIN_SOURCE : 0)
        | (directories ? DecodeFlags.WANT_DIRECTORIES : 0);
  }

  @Override
  protected void decode(
      byte[] bytes, int offset, int length, int encoding, @Nullable TapeTextArena text) {
    byte[] utf8 = YamlStatus.utf8(bytes, offset, length, encoding);
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
              decodeFlags(),
              MemorySegment.NULL,
              0L,
              result);
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    }
    check(status, result);
  }

  static void check(int status, MemorySegment result) {
    if (YamlStatus.detailed(status))
      YamlStatus.checkDetailed(
          status,
          message(
              result,
              YamlFFM.YamlResult_error_message_OFFSET,
              result.get(ValueLayout.JAVA_INT, YamlFFM.YamlResult_error_message_length_OFFSET)),
          result.get(ValueLayout.JAVA_LONG, YamlFFM.YamlResult_error_line_OFFSET),
          result.get(ValueLayout.JAVA_LONG, YamlFFM.YamlResult_error_column_OFFSET));
    YamlStatus.checkStatus(status);
  }

  /** The NUL-terminated error message of a failed result, at most capacity - 1 bytes. */
  static String message(MemorySegment result, long offset, int published) {
    int length = Math.min(YamlConstants.ERROR_MESSAGE_CAPACITY - 1, published);
    return new String(
        result.asSlice(offset, length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
  }

  @Override
  protected @Nullable TapeTextArena textArena(byte[] bytes) {
    return null;
  }

  /**
   * Copies a live result's metadata through the generated counted views, which bound every table
   * and string read by its count. {@code r} is a result record segment of the record's size.
   */
  static YamlMetadata metadata(MemorySegment r, long nodeCount, long expansionBudget) {
    int[] words =
        r.get(ValueLayout.ADDRESS, YamlFFM.YamlResult_yaml_meta_OFFSET)
            .reinterpret(Math.multiplyExact(Integer.BYTES, Math.toIntExact(nodeCount)))
            .toArray(ValueLayout.JAVA_INT);
    MemorySegment strings = YamlFFM.YamlResult_strings(r);
    MemorySegment tagTable = YamlFFM.YamlResult_tags(r);
    String[] tags = ResultMetadata.texts(Math.toIntExact(YamlFFM.YamlResult_tags_count(r)));
    for (int i = 0; i < tags.length; i++) {
      MemorySegment tag = YamlFFM.YamlResult_tags_at(tagTable, i);
      tags[i] =
          text(
              strings,
              tag.get(ValueLayout.JAVA_INT, YamlFFM.YamlSpan_offset_OFFSET),
              tag.get(ValueLayout.JAVA_INT, YamlFFM.YamlSpan_length_OFFSET));
    }
    MemorySegment anchorTable = YamlFFM.YamlResult_anchors(r);
    int anchorCount = Math.toIntExact(YamlFFM.YamlResult_anchors_count(r));
    long[] anchors = ResultMetadata.anchors(anchorCount);
    String[] names = ResultMetadata.texts(anchorCount);
    for (int i = 0; i < anchorCount; i++) {
      MemorySegment anchor = YamlFFM.YamlResult_anchors_at(anchorTable, i);
      anchors[i] = anchor.get(ValueLayout.JAVA_LONG, YamlFFM.YamlAnchor_node_OFFSET);
      names[i] =
          text(
              strings,
              anchor.get(ValueLayout.JAVA_INT, YamlFFM.YamlAnchor_offset_OFFSET),
              anchor.get(ValueLayout.JAVA_INT, YamlFFM.YamlAnchor_length_OFFSET));
    }
    MemorySegment directiveTable = YamlFFM.YamlResult_directives(r);
    int directiveCount = Math.toIntExact(YamlFFM.YamlResult_directives_count(r));
    Map<String, String> directives = ResultMetadata.directives(directiveCount);
    for (int i = 0; i < directiveCount; i++) {
      MemorySegment directive = YamlFFM.YamlResult_directives_at(directiveTable, i);
      directives.put(
          text(
              strings,
              directive.get(ValueLayout.JAVA_INT, YamlFFM.YamlTagDirective_handle_offset_OFFSET),
              directive.get(ValueLayout.JAVA_INT, YamlFFM.YamlTagDirective_handle_length_OFFSET)),
          text(
              strings,
              directive.get(ValueLayout.JAVA_INT, YamlFFM.YamlTagDirective_prefix_offset_OFFSET),
              directive.get(ValueLayout.JAVA_INT, YamlFFM.YamlTagDirective_prefix_length_OFFSET)));
    }
    return ResultMetadata.create(
        words,
        tags,
        anchors,
        names,
        directives,
        r.get(ValueLayout.JAVA_INT, YamlFFM.YamlResult_yaml_version_OFFSET),
        expansionBudget);
  }

  /** {@code length} bytes of the string table at the unsigned {@code offset}. */
  private static String text(MemorySegment strings, int offset, int length) {
    return new String(
        strings.asSlice(Integer.toUnsignedLong(offset), length).toArray(ValueLayout.JAVA_BYTE),
        StandardCharsets.UTF_8);
  }

  static SegmentTape tape(MemorySegment r) {
    return new SegmentTape(r.get(ValueLayout.ADDRESS, YamlFFM.YamlResult_view_OFFSET));
  }

  static class Storage implements YamlMessage.Storage {
    final SegmentTape tape;
    final MemorySegment source;

    Storage(SegmentTape tape, MemorySegment r) {
      this.tape = tape;
      source = YamlFFM.YamlResult_source(r);
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
    YamlMetadata meta = metadata(result, tape.nodeCount(), options.limits().maxAliasExpansion());
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
      YamlMetadata meta = metadata(owned, tape.nodeCount(), options.limits().maxAliasExpansion());
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

  void dereference() {
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
      int status = calls.stream_next(MemorySegment.ofAddress(stream), decodeFlags(), result);
      if (status != Status.STREAM_EMPTY && status != Status.STREAM_TRUNCATED) check(status, result);
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
