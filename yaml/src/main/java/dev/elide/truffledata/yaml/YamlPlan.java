package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.tape.TypedRecord;
import java.util.Map;

/**
 * Reusable, thread-confined native typed plan: a closed root record plus the record types it
 * reaches. Results own their storage independently of the plan. Compile through {@link
 * YamlSession#compile(Schema, Map)} on either backend, or {@link #compile(Schema, Map)} on SVM.
 */
public final class YamlPlan implements AutoCloseable {
  static final TapeFormat FORMAT = TapeFormat.of("YAML");
  static final long MAX_NAME_BYTES = TypedLayout.MAX_NAME_BYTES;

  /**
   * Backend half of a plan: the native plan handle and typed decode. Each backend artifact supplies its own.
   */
  abstract static class Backend {
    /** Decodes bounded input into a new owner, which takes the native result. */
    abstract TypedOwner decode(byte[] utf8, Schema[] schemas);

    /** Releases the native plan; called once, on the plan's thread. */
    abstract void release();
  }

  private final long maxInputBytes;
  private final Schema[] schemas;
  private final Backend backend;
  private final Thread thread = Thread.currentThread();
  private boolean open = true;

  YamlPlan(Schema[] schemas, Backend backend, long maxInputBytes) {
    this.maxInputBytes = maxInputBytes;
    this.schemas = schemas;
    this.backend = backend;
  }

  /** Compiles a closed scalar schema; unsupported contracts fail before allocation. */
  @TruffleBoundary
  public static YamlPlan compile(Schema schema) {
    return compile(schema, Map.of());
  }

  /**
   * Compiles a closed root schema with the default options on {@link YamlSession#open()}'s backend
   * (the linked static archive in an image, else the Java kernel); the plan outlives the session it
   * was compiled by. RECORD fields, and ARRAY fields with
   * RECORD elements, name their schema by {@code Constraints.recordType()}: a key of {@code
   * recordTypes} whose schema has that same {@code typeName}. ARRAY fields take their element
   * contract from {@code Constraints.element()}; elements are scalar or RECORD, never null, missing
   * or nested arrays. Recursive record types and nesting deeper than 32 records are rejected.
   */
  @TruffleBoundary
  public static YamlPlan compile(Schema root, Map<String, Schema> recordTypes) {
    try (YamlSession session = YamlSession.open()) {
      return session.compile(root, recordTypes);
    }
  }

  private void checkThread() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("YAML plan thread mismatch");
  }

  /**
   * Decodes one document into a scoped root record that outlives this plan. The plan and its
   * results remain valid after the compiling session closes.
   */
  @TruffleBoundary
  public TypedRecord decode(byte[] utf8) {
    checkThread();
    if (!open) throw new IllegalStateException("YAML plan closed");
    if (utf8.length == 0 || utf8.length > maxInputBytes)
      throw new IllegalArgumentException("YAML input limit");
    return backend.decode(utf8, schemas).root();
  }

  @Override
  @TruffleBoundary
  public void close() {
    checkThread();
    if (open) {
      open = false;
      backend.release();
    }
  }
}
