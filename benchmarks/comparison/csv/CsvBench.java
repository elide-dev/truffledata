package dev.elide.truffledata.csv;

/**
 * Benchmark-only access to package-private CSV internals: the strict scanner, the UTF-8 validator
 * and the number parsers, so the comparison can time the current scanner on its own and give the
 * kernel paths the same validation and conversions as {@link CsvPlan}. Lives in the benchmark
 * target only; it adds nothing to the published library.
 */
public final class CsvBench {
  private CsvBench() {}

  /** Receives every record of a {@link #scan}. */
  public interface Records {
    void record(long[] spans, int count, int start, int end);
  }

  /** The strict scanner over the whole input (EOF), from {@code from}. */
  public static int scan(CsvFormat format, CsvLimits limits, byte[] input, int from, Records records) {
    return new CsvScanner(format, limits)
        .scan(
            input,
            from,
            input.length,
            true,
            (spans, count, start, end) -> {
              records.record(spans, count, start, end);
              return true;
            });
  }

  /** The strict scanner with an explicit record base (the strict side of a kernel fallback). */
  public static int scanOne(
      CsvFormat format, CsvLimits limits, byte[] input, int from, int to, boolean eof, long recordBase, Records records) {
    CsvScanner scanner = new CsvScanner(format, limits);
    scanner.recordBase = recordBase;
    boolean[] first = {true};
    return scanner.scan(
        input,
        from,
        to,
        eof,
        (spans, count, start, end) -> {
          if (!first[0]) return false;
          first[0] = false;
          records.record(spans, count, start, end);
          return true;
        });
  }

  public static int invalidUtf8(byte[] bytes, int from, int to) {
    return Utf8.invalidOffset(bytes, from, to);
  }

  public static long parseLong(byte[] bytes, int offset, int length) {
    return Numbers.parseLong(bytes, offset, length);
  }

  public static double parseDouble(byte[] bytes, int offset, int length) {
    return Numbers.parseDouble(bytes, offset, length);
  }

  /** FLOAT64 conversions that left the exact fast path (Double.parseDouble fallbacks). */
  public static long floatFallbacks() {
    return Numbers.fallbacks();
  }

  /** Unescaped UTF-8 content of a span (doubled quotes collapsed) into {@code target}. */
  public static int unescape(byte[] input, long span, byte quote, byte[] target) {
    return CsvScanner.unescape(input, span, quote, target, 0);
  }
}
