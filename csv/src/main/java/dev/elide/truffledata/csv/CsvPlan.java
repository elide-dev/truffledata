package dev.elide.truffledata.csv;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * How records become batches: a format plus, for known-schema CSV, the schema. Typed plans convert
 * fields during the scan (exact grammars, no inference); every batch from a typed plan has {@code
 * schema() == plan.schema()}. Generic plans derive an all-STRING schema from the header (or name
 * columns {@code "0".."n-1"}). Plans are immutable and reusable across threads.
 */
public final class CsvPlan {
  static final byte STRING = 0;
  static final byte INT64 = 1;
  static final byte FLOAT64 = 2;
  static final byte BOOLEAN = 3;

  private final @Nullable Schema schema; // null: generic
  private final CsvFormat format;
  final Dialect dialect;

  /** Typed plans with named columns: field index by name (case-insensitive with ignoreHeaderCase). */
  private final Map<String, Integer> byName;

  /** The binding fixed by the plan (typed positional, or explicit header names), or null. */
  private final @Nullable Binding fixed;

  private CsvPlan(@Nullable Schema schema, CsvFormat format, Map<String, Integer> byName) {
    this.schema = schema;
    this.format = format;
    this.dialect = Dialect.of(format);
    this.byName = byName;
    List<String> explicit = format.headerNames();
    if (explicit != null) this.fixed = named(explicit.toArray(new String[0]));
    else if (schema != null && !format.header())
      this.fixed = new Binding(schema, schema.size(), identity(schema.size()));
    else this.fixed = null;
  }

  static CsvPlan generic(CsvFormat format) {
    return new CsvPlan(null, Objects.requireNonNull(format), Map.of());
  }

  /**
   * A known-schema plan. Supports INT64 (with integer width and range constraints), FLOAT64 (with
   * {@code floatBits}), BOOLEAN and STRING (without length constraints); rejects other kinds. With
   * named columns, field names must be unique (ignoring case with {@code ignoreHeaderCase}): header
   * names map to fields by name.
   */
  public static CsvPlan compile(Schema schema, CsvFormat format) {
    Objects.requireNonNull(format);
    Map<String, Integer> byName =
        format.ignoreHeaderCase() ? new TreeMap<>(String.CASE_INSENSITIVE_ORDER) : new HashMap<>();
    for (int f = 0; f < schema.size(); f++) {
      Field field = schema.field(f);
      switch (field.kind()) {
        case INT64, FLOAT64, BOOLEAN -> {}
        case STRING -> {
          if (field.constraints().length() != null)
            throw unsupported(field, "STRING length constraints are not supported");
        }
        default -> throw unsupported(field, field.kind() + " is not supported");
      }
      if (format.named() && byName.put(field.name(), f) != null)
        throw unsupported(field, "duplicate field name with a header");
    }
    return new CsvPlan(
        schema,
        format,
        format.ignoreHeaderCase() ? java.util.Collections.unmodifiableMap(byName) : Map.copyOf(byName));
  }

  private static IllegalArgumentException unsupported(Field field, String reason) {
    return new IllegalArgumentException("CSV plan: Field '" + field.name() + "': " + reason);
  }

  public Schema schema() {
    Schema s = schema;
    if (s == null) throw new IllegalStateException("Generic plans derive their schema per input");
    return s;
  }

  public CsvFormat format() {
    return format;
  }

  public CsvBatch parse(byte[] utf8) {
    return parse(utf8, CsvLimits.DEFAULT);
  }

  /** Checks maxBatchBytes, copies the input once, and retains the copy. */
  public CsvBatch parse(byte[] utf8, CsvLimits limits) {
    if (utf8.length > limits.maxBatchBytes()) throw CsvScanner.limit("maxBatchBytes");
    byte[] input = utf8.clone();
    int from = hasBom(input, input.length) ? 3 : 0;
    CsvScanner scanner = new CsvScanner(format, limits);
    Binder binder = new Binder(this);
    binder.bind(scanner, input, from, input.length, true);
    long base = binder.consumed;
    scanner.recordBase = base;
    scanner.stopAfter = stopAfter(base);
    BatchBuilder builder =
        new BatchBuilder(
            Objects.requireNonNull(binder.binding), input, limits, dialect, 0, base, 0, 16);
    scanner.scan(input, binder.end, input.length, true, builder);
    if (builder.full()) throw CsvScanner.limit("maxBatchRows");
    return builder.build();
  }

  /**
   * The scanner's stop bound in physical records: {@code maxRows} data records after the {@code
   * consumed} header record (Commons restarts its record count after the header).
   */
  long stopAfter(long consumed) {
    long max = format.maxRows();
    return max > 0 ? consumed + max : Long.MAX_VALUE;
  }

  /** Streams batches of about 1 MiB of input each. The reader owns the stream. */
  public CsvReader open(InputStream input) {
    return open(input, CsvLimits.DEFAULT, Csv.DEFAULT_BATCH_BYTES);
  }

  public CsvReader open(InputStream input, CsvLimits limits, int batchBytes) {
    return new CsvReader(input, this, limits, batchBytes);
  }

  static boolean hasBom(byte[] input, int length) {
    return length >= 3
        && input[0] == (byte) 0xEF
        && input[1] == (byte) 0xBB
        && input[2] == (byte) 0xBF;
  }

  private static int[] identity(int size) {
    int[] identity = new int[size];
    for (int i = 0; i < size; i++) identity[i] = i;
    return identity;
  }

  /**
   * Commons {@code CSVParser.createHeaders} checks: a blank name needs {@code
   * allowMissingColumnNames}, and duplicates follow the duplicate-header mode (ignoring case with
   * {@code ignoreHeaderCase}). Null names (null tokens) count as blank.
   */
  private void checkHeader(@Nullable String[] names) {
    Set<String> seen =
        format.ignoreHeaderCase() ? new TreeSet<>(String.CASE_INSENSITIVE_ORDER) : new HashSet<>();
    boolean observedMissing = false;
    CsvFormat.DuplicateHeaderMode mode = format.duplicateHeaderMode();
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      boolean blank = CsvFormat.blank(name);
      if (blank && !format.allowMissingColumnNames())
        throw new IllegalArgumentException("CSV header: missing name at column " + i);
      boolean duplicate = blank ? observedMissing : seen.contains(name);
      if (duplicate
          && mode != CsvFormat.DuplicateHeaderMode.ALLOW_ALL
          && !(blank && mode == CsvFormat.DuplicateHeaderMode.ALLOW_EMPTY))
        throw new IllegalArgumentException("CSV header: duplicate name at column " + i);
      observedMissing |= blank;
      if (name != null) seen.add(name);
    }
  }

  /** Binding for header names (decoded and UTF-8 validated); a null name reads as "". */
  Binding named(@Nullable String[] names) {
    checkHeader(names);
    String[] plain = new String[names.length];
    for (int i = 0; i < names.length; i++) plain[i] = names[i] == null ? "" : names[i];
    Schema s = schema;
    if (s == null) return genericBinding(plain);
    int[] fieldToColumn = new int[s.size()];
    Arrays.fill(fieldToColumn, -1);
    for (int column = 0; column < plain.length; column++) {
      Integer field = byName.get(plain[column]);
      if (field == null) continue; // projection: columns outside the schema are skipped
      if (fieldToColumn[field] >= 0) throw header(s.field(field), "matched by two columns");
      fieldToColumn[field] = column;
    }
    for (int f = 0; f < fieldToColumn.length; f++)
      if (fieldToColumn[f] < 0 && !s.field(f).optional()) throw header(s.field(f), "missing");
    return new Binding(s, plain.length, fieldToColumn);
  }

  private static ContractViolation header(Field field, String reason) {
    return new ContractViolation("CSV header: Field '" + field.name() + "': " + reason);
  }

  /** Binding without names: generic plans take the first record's width. */
  Binding positional(int width) {
    Binding f = fixed;
    if (f != null) return f;
    String[] names = new String[width];
    for (int i = 0; i < width; i++) names[i] = Integer.toString(i);
    return genericBinding(names);
  }

  /** Whether binding reads the first record (a header, a skipped record, or a generic width). */
  boolean needsFirstRecord() {
    return fixed == null || format.consumesFirstRecord();
  }

  private Binding genericBinding(String[] names) {
    List<Field> fields = new ArrayList<>(names.length);
    for (String name : names)
      fields.add(
          new Field(name, Field.Kind.STRING, dialect.nulls.configured(), format.flexible()));
    return new Binding(new Schema(fields), names.length, identity(names.length));
  }

  /** A plan resolved against one input's header (or width): the batch schema and column mapping. */
  static final class Binding {
    final Schema schema;
    final Field[] fields;

    /** Expected record width. */
    final int width;

    /** Per schema field, its record column, or -1 when absent from the header (always missing). */
    final int[] fieldToColumn;

    /** Per field: {@link #STRING}, {@link #INT64}, {@link #FLOAT64} or {@link #BOOLEAN}. */
    final byte[] kinds;

    final boolean[] nullable;
    final boolean[] stateful;

    /** INT64 fields with width or range constraints. */
    final boolean[] constrained;

    /** FLOAT64 fields that must be exact binary32 values. */
    final boolean[] binary32;

    Binding(Schema schema, int width, int[] fieldToColumn) {
      this.schema = schema;
      this.width = width;
      this.fieldToColumn = fieldToColumn;
      int size = schema.size();
      fields = new Field[size];
      kinds = new byte[size];
      nullable = new boolean[size];
      stateful = new boolean[size];
      constrained = new boolean[size];
      binary32 = new boolean[size];
      for (int f = 0; f < size; f++) {
        Field field = schema.field(f);
        fields[f] = field;
        kinds[f] =
            switch (field.kind()) {
              case INT64 -> INT64;
              case FLOAT64 -> FLOAT64;
              case BOOLEAN -> BOOLEAN;
              default -> STRING;
            };
        nullable[f] = field.nullable();
        stateful[f] = field.nullable() || field.optional();
        constrained[f] =
            field.constraints().integer() != null || field.constraints().integerRange() != null;
        binary32[f] = field.constraints().floatBits() == 32;
      }
    }
  }

  /** Captures (and with a header or a skipped record, consumes) the first record to bind the plan. */
  static final class Binder implements CsvScanner.Records {
    private final CsvPlan plan;
    private byte[] input = new byte[0];
    private long[] first = new long[0];
    private int count;
    private long recordBase;
    private long byteBase;

    @Nullable Binding binding;

    /**
     * Offset after the bytes the binding consumed (the header and any empty lines); without a
     * binding, the offset scanning can resume from.
     */
    int end;

    /** Physical records the binding consumed (0 or 1). */
    long consumed;

    Binder(CsvPlan plan) {
      this.plan = plan;
    }

    /** False when more input is needed (only without eof). */
    boolean bind(CsvScanner scanner, byte[] input, int from, int to, boolean eof) {
      CsvFormat format = plan.format;
      if (!plan.needsFirstRecord()) {
        binding = Objects.requireNonNull(plan.fixed);
        end = from;
        return true;
      }
      this.input = input;
      count = -1;
      recordBase = scanner.recordBase;
      byteBase = scanner.byteBase;
      int stop = scanner.scan(input, from, to, eof, this);
      end = stop;
      if (count < 0) {
        if (!eof) return false;
        if (format.header()) {
          // Commons yields no header names and no records; the strict contract needs a header.
          if (format.strict()) throw new IllegalArgumentException("CSV header missing");
          binding = plan.named(new String[0]);
        } else binding = plan.positional(0);
        return true;
      }
      if (format.consumesFirstRecord()) consumed = 1;
      if (format.header()) {
        @Nullable String[] names = new String[count];
        Nulls nulls = plan.dialect.nulls;
        for (int i = 0; i < count; i++) {
          String name = plan.dialect.string(input, first[i]);
          // Commons passes header values through the null rule too; a null name is blank.
          names[i] =
              nulls.configured() && nulls.isNull(name, CsvScanner.quoted(first[i])) ? null : name;
        }
        binding = plan.named(names);
      } else binding = plan.positional(count);
      return true;
    }

    @Override
    public boolean record(long[] spans, int count, int start, int end) {
      if (this.count >= 0) return false;
      boolean consume = plan.format.consumesFirstRecord();
      if (consume) {
        int bad = Utf8.invalidOffset(input, start, end);
        if (bad >= 0) throw CsvScanner.syntax(recordBase + 1, byteBase + bad, "invalid UTF-8");
      }
      first = Arrays.copyOf(spans, count);
      this.count = count;
      return consume;
    }
  }
}
