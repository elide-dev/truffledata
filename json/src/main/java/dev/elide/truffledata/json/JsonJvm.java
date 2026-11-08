package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.generated.JsonFFM;
import dev.elide.truffledata.tape.SegmentTape;
import dev.elide.truffledata.tape.TapeLexical;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.tape.TypedOwner;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * JVM FFM backend. Its arena owns only the library; every message, typed plan and typed result owns
 * its native storage and keeps the library loaded until it closes.
 */
public final class JsonJvm implements JsonDecoder {
  private static final long PADDING = 64;
  private final Arena libraryArena = Arena.ofConfined();
  private final Thread thread = Thread.currentThread();
  private final MethodHandle decode;
  private final MethodHandle decodeAdopted;
  private final MethodHandle release;
  private final MethodHandle planCreate;
  private final MethodHandle planRelease;
  private final MethodHandle typedDecode;
  private final MethodHandle typedRelease;
  private final SymbolLookup symbols;
  private boolean open = true;
  // Messages, typed plans and typed results not yet closed.
  private int live;

  private JsonJvm(Path library) {
    try {
      symbols = SymbolLookup.libraryLookup(library.toAbsolutePath(), libraryArena);
      MemorySegment fingerprint;
      try {
        fingerprint = (MemorySegment) JsonFFM.fingerprint(symbols).invokeExact();
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      String actual = fingerprint.reinterpret(JsonFFM.ABI_FINGERPRINT.length() + 1).getString(0);
      if (!actual.equals(JsonFFM.ABI_FINGERPRINT))
        throw new IllegalStateException("Myna/JSON ABI fingerprint mismatch");
      decode = JsonFFM.decode(symbols);
      decodeAdopted = JsonFFM.decode_adopted(symbols);
      release = JsonFFM.release(symbols);
      planCreate = JsonFFM.plan_create(symbols);
      planRelease = JsonFFM.plan_release(symbols);
      typedDecode = JsonFFM.typed_decode(symbols);
      typedRelease = JsonFFM.typed_release(symbols);
    } catch (RuntimeException | Error failure) {
      libraryArena.close();
      throw failure;
    }
  }

  /** Loads the shared JSON library; the decoder is confined to the opening thread. */
  @TruffleBoundary
  public static JsonDecoder open(Path library) {
    return new JsonJvm(library);
  }

  private void check() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON decoder thread mismatch");
    if (!open) throw new IllegalStateException("JSON decoder closed");
  }

  @Override
  @TruffleBoundary
  public JsonMessage decode(byte[] utf8, JsonLimits limits) {
    check();
    if (utf8.length == 0) throw new IllegalArgumentException("Empty JSON input");
    if (utf8.length > limits.maxInputBytes()) throw new IllegalArgumentException("JSON input limit");
    Arena inputArena = Arena.ofConfined();
    try {
      MemorySegment padded = inputArena.allocate(utf8.length + PADDING);
      MemorySegment.copy(utf8, 0, padded, ValueLayout.JAVA_BYTE, 0, utf8.length);
      return publish(padded, utf8.length, limits, inputArena::close);
    } catch (RuntimeException | Error failure) {
      inputArena.close();
      throw failure;
    }
  }

  @Override
  @TruffleBoundary
  public JsonMessage adopt(MemorySegment data, long length, Runnable release) {
    check();
    Objects.requireNonNull(release);
    if (!data.isNative() || !data.scope().isAlive() || data.address() == 0)
      throw new IllegalArgumentException("Expected a live native region");
    if (length <= 0 || length > data.byteSize())
      throw new IllegalArgumentException("JSON length exceeds adopted capacity");
    return publish(data, length, JsonLimits.DEFAULT, release);
  }

  private JsonMessage publish(MemorySegment data, long length, JsonLimits limits, Runnable onClose) {
    Arena resultArena = Arena.ofConfined();
    MemorySegment result = resultArena.allocate(JsonFFM.JsonResult_LAYOUT);
    boolean published = false;
    try {
      int status;
      try {
        status =
            (int)
                decodeAdopted.invokeExact(
                    data,
                    length,
                    data.byteSize(),
                    limits.maxInputBytes(),
                    limits.maxIndexBytes(),
                    limits.maxNodes(),
                    limits.maxTextBytes(),
                    limits.maxDepth(),
                    result);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      JsonSvm.checkStatus(status);
      JsonMessage message = message(result, resultArena, onClose);
      published = true;
      return message;
    } finally {
      if (!published) {
        free(result);
        resultArena.close();
      }
    }
  }

  /** Reader of the compact descriptor; native result storage owns its input and arrays. */
  static SegmentTape tape(MemorySegment result) {
    return new SegmentTape(result.get(ValueLayout.ADDRESS, JsonFFM.JsonResult_view_OFFSET));
  }

  private JsonMessage message(MemorySegment result, Arena resultArena, Runnable onClose) {
    SegmentTape tape = tape(result);
    live++;
    return new JsonMessage.Managed(tape, () -> releaseResult(result, resultArena, onClose));
  }

  // ---- Sessions (performance plan Task E1) ----

  /**
   * Opens a JVM {@link JsonSession} over the shared library at {@code library}, confined to the
   * calling thread; {@code limits} apply to each document. The library stays loaded until the
   * session and every message it retained are closed.
   */
  @TruffleBoundary
  public static JsonSession openSession(Path library, JsonLimits limits) {
    return openSession(library, limits, JsonSession.CRITICAL_LIMIT);
  }

  /**
   * As {@link #openSession(Path, JsonLimits)}, decoding inputs of at most {@code criticalLimit}
   * bytes (0 to {@link JsonSession#CRITICAL_LIMIT}; 0 disables) through bounded critical calls
   * (performance plan Task E2). Even bounded, a critical call delays safepoints for its duration
   * (at most ≈ 30 µs for 4 KiB at the slowest measured decode rate).
   */
  @TruffleBoundary
  public static JsonSession openSession(Path library, JsonLimits limits, int criticalLimit) {
    Objects.requireNonNull(limits);
    if (criticalLimit < 0 || criticalLimit > JsonSession.CRITICAL_LIMIT)
      throw new IllegalArgumentException("Critical limit outside 0.." + JsonSession.CRITICAL_LIMIT);
    JsonJvm jvm = new JsonJvm(library);
    try {
      return jvm.session(limits, criticalLimit);
    } finally {
      // The session counts as live, so this only marks the decoder closed.
      jvm.close();
    }
  }

  private JsonSession session(JsonLimits limits, int criticalLimit) {
    check();
    JsonSession session =
        new JsonSession(
            limits,
            new JvmSessionBackend(
                this,
                new JsonFFM.LibraryBinding(symbols),
                new JsonFFM.CriticalLibraryBinding(symbols),
                criticalLimit,
                limits));
    live++;
    return session;
  }

  /** Retained session document: {@code retain} moves the session's storage into a fresh result. */
  JsonMessage retain(java.util.function.ToIntFunction<MemorySegment> retain) {
    Arena resultArena = Arena.ofConfined();
    MemorySegment result;
    try {
      result = resultArena.allocate(JsonFFM.JsonResult_LAYOUT);
    } catch (RuntimeException | Error failure) {
      resultArena.close();
      throw failure;
    }
    boolean published = false;
    try {
      JsonSvm.checkStatus(retain.applyAsInt(result));
      JsonMessage message = message(result, resultArena, () -> {});
      published = true;
      return message;
    } finally {
      if (!published) {
        free(result);
        resultArena.close();
      }
    }
  }

  /** A session of this library closed; unload once nothing else uses it. */
  void sessionClosed() {
    live--;
    if (!open && live == 0) libraryArena.close();
  }

  private void releaseResult(MemorySegment result, Arena resultArena, Runnable onClose) {
    try {
      free(result);
    } finally {
      try {
        resultArena.close();
      } finally {
        live--;
        try {
          onClose.run();
        } finally {
          if (!open && live == 0) libraryArena.close();
        }
      }
    }
  }

  private void free(MemorySegment result) {
    try {
      release.invokeExact(result);
    } catch (Throwable failure) {
      throw callFailure(failure);
    }
  }

  /** Releases native storage, then unloads the library if the decoder closed and it was last. */
  private void retire(MethodHandle free, MemorySegment storage, Arena arena) {
    try {
      try {
        free.invokeExact(storage);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
    } finally {
      try {
        arena.close();
      } finally {
        live--;
        if (!open && live == 0) libraryArena.close();
      }
    }
  }

  /**
   * Writes the shared {@link TypedLayout} into FFM memory and creates the native plan. Plan
   * decodes require this decoder to be open; returned records do not.
   */
  @Override
  @TruffleBoundary
  public JsonPlan compile(Schema root, Map<String, Schema> recordTypes) {
    check();
    TypedLayout layout = new TypedLayout(root, recordTypes, JsonMessage.FORMAT);
    long fieldSize = JsonFFM.JsonScalarField_LAYOUT.byteSize();
    long recordSize = JsonFFM.JsonRecordPlan_LAYOUT.byteSize();
    Arena planArena = Arena.ofConfined();
    MemorySegment handle;
    try {
      handle = planArena.allocate(JsonFFM.JsonPlan_LAYOUT);
    } catch (RuntimeException | Error failure) {
      planArena.close();
      throw failure;
    }
    // Counted before creation so a failed plan retires through the same path.
    live++;
    boolean published = false;
    // The native plan copies names and tables; scratch storage ends with this call.
    try (Arena scratch = Arena.ofConfined()) {
      MemorySegment fields =
          scratch.allocate(
              Math.max(1, layout.fieldCount) * fieldSize,
              JsonFFM.JsonScalarField_LAYOUT.byteAlignment());
      MemorySegment records =
          scratch.allocate(
              layout.records.length * recordSize, JsonFFM.JsonRecordPlan_LAYOUT.byteAlignment());
      for (int r = 0; r < layout.records.length; r++) {
        long at = r * recordSize;
        records.set(
            ValueLayout.JAVA_INT,
            at + JsonFFM.JsonRecordPlan_fields_offset_OFFSET,
            layout.fieldsOffset[r]);
        records.set(
            ValueLayout.JAVA_INT,
            at + JsonFFM.JsonRecordPlan_field_count_OFFSET,
            layout.records[r].size());
      }
      for (int i = 0; i < layout.fieldCount; i++) {
        long at = i * fieldSize;
        byte[] name = layout.names[i];
        // An empty key still needs an addressable backing allocation.
        MemorySegment bytes = scratch.allocate(Math.max(1, name.length));
        MemorySegment.copy(name, 0, bytes, ValueLayout.JAVA_BYTE, 0, name.length);
        fields.set(ValueLayout.ADDRESS, at + JsonFFM.JsonScalarField_name_OFFSET, bytes);
        fields.set(
            ValueLayout.JAVA_LONG, at + JsonFFM.JsonScalarField_name_length_OFFSET, name.length);
        fields.set(ValueLayout.JAVA_INT, at + JsonFFM.JsonScalarField_kind_OFFSET, layout.kinds[i]);
        fields.set(
            ValueLayout.JAVA_INT, at + JsonFFM.JsonScalarField_flags_OFFSET, layout.flags[i]);
        fields.set(
            ValueLayout.JAVA_INT, at + JsonFFM.JsonScalarField_child_OFFSET, layout.children[i]);
        fields.set(
            ValueLayout.JAVA_LONG, at + JsonFFM.JsonScalarField_minimum_OFFSET, layout.minimums[i]);
        fields.set(
            ValueLayout.JAVA_LONG, at + JsonFFM.JsonScalarField_maximum_OFFSET, layout.maximums[i]);
        fields.set(
            ValueLayout.JAVA_LONG,
            at + JsonFFM.JsonScalarField_unsigned_maximum_OFFSET,
            TypedLayout.UNSIGNED_MAXIMUM);
        fields.set(
            ValueLayout.JAVA_LONG,
            at + JsonFFM.JsonScalarField_max_utf8_bytes_OFFSET,
            TypedLayout.MAX_UTF8_BYTES);
        fields.set(
            ValueLayout.JAVA_INT,
            at + JsonFFM.JsonScalarField_float_bits_OFFSET,
            layout.floatBits[i]);
        fields.set(
            ValueLayout.JAVA_INT,
            at + JsonFFM.JsonScalarField_length_unit_OFFSET,
            layout.lengthUnits[i]);
        fields.set(
            ValueLayout.JAVA_LONG,
            at + JsonFFM.JsonScalarField_minimum_length_OFFSET,
            layout.minimumLengths[i]);
        fields.set(
            ValueLayout.JAVA_LONG,
            at + JsonFFM.JsonScalarField_maximum_length_OFFSET,
            layout.maximumLengths[i]);
      }
      int status;
      try {
        status =
            (int)
                planCreate.invokeExact(
                    records,
                    (long) layout.records.length,
                    fields,
                    (long) layout.fieldCount,
                    JsonPlan.MAX_NAME_BYTES,
                    handle);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      JsonSvm.checkStatus(status);
      JsonPlan plan = new JsonPlan(layout.records, new Plan(handle, planArena));
      published = true;
      return plan;
    } finally {
      if (!published) {
        // Releasing a zeroed or failed plan is harmless; the arena still owns the handle.
        retire(planRelease, handle, planArena);
      }
    }
  }

  /** FFM typed plan: its arena owns only the plan handle storage. */
  private final class Plan extends JsonPlan.Backend {
    private final MemorySegment handle;
    private final Arena arena;

    Plan(MemorySegment handle, Arena arena) {
      this.handle = handle;
      this.arena = arena;
    }

    @Override
    TypedOwner decode(byte[] utf8, Schema[] schemas) {
      if (!open) throw new IllegalStateException("JSON decoder closed");
      Arena resultArena = Arena.ofConfined();
      MemorySegment result;
      try {
        result = resultArena.allocate(JsonFFM.JsonTypedResult_LAYOUT);
      } catch (RuntimeException | Error failure) {
        resultArena.close();
        throw failure;
      }
      // Counted before decoding so a failed decode retires through the same path.
      live++;
      boolean published = false;
      // The native result retains its own copy of the input.
      try (Arena input = Arena.ofConfined()) {
        MemorySegment data = input.allocate(utf8.length);
        MemorySegment.copy(utf8, 0, data, ValueLayout.JAVA_BYTE, 0, utf8.length);
        int status;
        try {
          status =
              (int)
                  typedDecode.invokeExact(
                      handle,
                      data,
                      (long) utf8.length,
                      JsonPlan.MAX_INPUT_BYTES,
                      JsonPlan.MAX_INDEX_BYTES,
                      result);
        } catch (Throwable failure) {
          throw callFailure(failure);
        }
        JsonSvm.checkStatus(status);
        TypedOwner owner =
            new JvmTypedOwner(
                result, schemas, () -> retire(typedRelease, result, resultArena));
        published = true;
        return owner;
      } finally {
        if (!published) retire(typedRelease, result, resultArena);
      }
    }

    @Override
    void release() {
      retire(planRelease, handle, arena);
    }
  }

  static RuntimeException callFailure(Throwable failure) {
    if (failure instanceof RuntimeException exception) return exception;
    if (failure instanceof Error error) throw error;
    return new IllegalStateException("Native invocation failed", failure);
  }

  /**
   * Messages and typed records already returned stay valid, and plans can still be closed but no
   * longer decode; the library unloads once the last of them closes.
   */
  @Override
  @TruffleBoundary
  public void close() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON decoder thread mismatch");
    if (open) {
      open = false;
      if (live == 0) libraryArena.close();
    }
  }
}
