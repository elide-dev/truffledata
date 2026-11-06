package dev.elide.truffledata.csv;

import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * How records become batches: a format plus, for known-schema CSV, the schema. Generic plans derive
 * an all-STRING schema from the header (or name columns {@code "0".."n-1"}). Plans are immutable and
 * reusable across threads.
 */
public final class CsvPlan {
  private final @Nullable Schema schema; // null: generic
  private final CsvFormat format;

  private CsvPlan(@Nullable Schema schema, CsvFormat format) {
    this.schema = schema;
    this.format = format;
  }

  static CsvPlan generic(CsvFormat format) {
    return new CsvPlan(null, Objects.requireNonNull(format));
  }

  CsvFormat format() {
    return format;
  }

  CsvBatch parse(byte[] utf8, CsvLimits limits) {
    if (utf8.length > limits.maxBatchBytes()) throw CsvScanner.limit("maxBatchBytes");
    byte[] input = utf8.clone();
    int from = hasBom(input, input.length) ? 3 : 0;
    CsvScanner scanner = new CsvScanner(format, limits);
    Binder binder = new Binder(this);
    binder.bind(scanner, input, from, input.length, true);
    long base = format.header() ? 1 : 0;
    scanner.recordBase = base;
    BatchBuilder builder =
        new BatchBuilder(Objects.requireNonNull(binder.binding), input, limits, format, 0, base, 0, 16);
    scanner.scan(input, binder.end, input.length, true, builder);
    if (builder.full()) throw CsvScanner.limit("maxBatchRows");
    return builder.build();
  }

  static boolean hasBom(byte[] input, int length) {
    return length >= 3
        && input[0] == (byte) 0xEF
        && input[1] == (byte) 0xBB
        && input[2] == (byte) 0xBF;
  }

  /** Binding for header names (already unescaped, trimmed and UTF-8 validated). */
  Binding named(String[] names) {
    if (schema == null) return genericBinding(names);
    throw new UnsupportedOperationException("typed plans land with CsvPlan.compile");
  }

  /** Binding without a header, given the first record's width (generic plans only). */
  Binding positional(int width) {
    if (schema != null) throw new UnsupportedOperationException("typed plans land with CsvPlan.compile");
    String[] names = new String[width];
    for (int i = 0; i < width; i++) names[i] = Integer.toString(i);
    return genericBinding(names);
  }

  private Binding genericBinding(String[] names) {
    List<Field> fields = new ArrayList<>(names.length);
    for (String name : names) fields.add(new Field(name, Field.Kind.STRING, false, format.flexible()));
    int[] identity = new int[names.length];
    for (int i = 0; i < identity.length; i++) identity[i] = i;
    return new Binding(new Schema(fields), names.length, identity);
  }

  /** A plan resolved against one input's header (or width): the batch schema and column mapping. */
  static final class Binding {
    final Schema schema;
    final Field[] fields;
    /** Expected record width. */
    final int width;
    /** Per schema field, its record column, or -1 when absent from the header (always missing). */
    final int[] fieldToColumn;

    final boolean[] nullable;
    final boolean[] stateful;

    Binding(Schema schema, int width, int[] fieldToColumn) {
      this.schema = schema;
      this.width = width;
      this.fieldToColumn = fieldToColumn;
      fields = new Field[schema.size()];
      nullable = new boolean[fields.length];
      stateful = new boolean[fields.length];
      for (int f = 0; f < fields.length; f++) {
        Field field = schema.field(f);
        fields[f] = field;
        nullable[f] = field.nullable();
        stateful[f] = field.nullable() || field.optional();
      }
    }
  }

  /** Captures (and with a header, consumes) the first record of an input to bind the plan. */
  static final class Binder implements CsvScanner.Records {
    private final CsvPlan plan;
    private byte[] input = new byte[0];
    private long[] first = new long[0];
    private int count;
    private long recordBase;
    private long byteBase;

    @Nullable Binding binding;

    /** Offset after the bytes the binding consumed (the header and any empty lines). */
    int end;

    Binder(CsvPlan plan) {
      this.plan = plan;
    }

    /** False when more input is needed (only without eof). */
    boolean bind(CsvScanner scanner, byte[] input, int from, int to, boolean eof) {
      this.input = input;
      count = -1;
      recordBase = scanner.recordBase;
      byteBase = scanner.byteBase;
      int stop = scanner.scan(input, from, to, eof, this);
      boolean header = plan.format.header();
      if (count < 0) {
        if (!eof) return false;
        if (header) throw new IllegalArgumentException("CSV header missing");
        binding = plan.positional(0);
      } else if (header) {
        String[] names = new String[count];
        for (int i = 0; i < count; i++)
          names[i] = CsvBatch.string(input, first[i], plan.format.quote());
        binding = plan.named(names);
      } else {
        binding = plan.positional(count);
      }
      end = stop;
      return true;
    }

    @Override
    public boolean record(long[] spans, int count, int start, int end) {
      if (this.count >= 0) return false;
      boolean consume = plan.format.header();
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
