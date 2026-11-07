package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.Nullable;

/**
 * Runs one input through Commons CSV 1.14.1 and through our parser (whole input and streamed) and
 * asserts identical records, nulls and errors, or one of the documented differences (docs/CSV.md):
 * a record wider than the header or first record is rejected here; an empty record that
 * trailingDelimiter empties is rejected here where Commons stops silently.
 */
final class Differential {
  /** Records with nulls, the header names (or null), or the failure. */
  record Result(
      @Nullable List<List<@Nullable String>> records,
      @Nullable List<String> header,
      @Nullable RuntimeException error) {
    boolean ok() {
      return error == null;
    }
  }

  /** Outcome counters, per label. */
  final Map<String, int[]> counts = new TreeMap<>();

  void count(String key) {
    counts.computeIfAbsent(key, k -> new int[1])[0]++;
  }

  int count(String key, int dummy) {
    int[] c = counts.get(key);
    return c == null ? 0 : c[0];
  }

  static Result commons(String input, CSVFormat format) {
    try (CSVParser parser = CSVParser.parse(input, format)) {
      List<List<@Nullable String>> records = new ArrayList<>();
      for (CSVRecord record : parser.getRecords()) {
        List<@Nullable String> values = new ArrayList<>();
        for (int i = 0; i < record.size(); i++) values.add(record.get(i));
        records.add(values);
      }
      List<String> header = parser.getHeaderMap() == null ? null : parser.getHeaderNames();
      return new Result(records, header, null);
    } catch (IOException e) {
      return new Result(null, null, new UncheckedIOException(e));
    } catch (RuntimeException e) {
      return new Result(null, null, e);
    }
  }

  static List<List<@Nullable String>> rows(CsvBatch batch, List<List<@Nullable String>> into) {
    for (int r = 0; r < batch.size(); r++) {
      List<@Nullable String> values = new ArrayList<>();
      for (int c = 0; c < batch.columns.length; c++) {
        byte state = batch.columns[c].state(r);
        if (state == Column.MISSING) break;
        values.add(state == Column.NULL ? null : batch.row(r).getString(c));
      }
      into.add(values);
    }
    return into;
  }

  static List<String> names(CsvBatch batch) {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < batch.schema().size(); i++) names.add(batch.schema().field(i).name());
    return names;
  }

  static Result ours(byte[] input, CsvFormat format) {
    try {
      CsvBatch batch = Csv.parse(input, format);
      return new Result(rows(batch, new ArrayList<>()), names(batch), null);
    } catch (RuntimeException e) {
      return new Result(null, null, e);
    }
  }

  static Result streamed(byte[] input, CsvFormat format, int batchBytes) {
    try (CsvReader reader =
        Csv.open(
            new ChunkedInput(input, 1 + batchBytes / 3, 1, 7), format, CsvLimits.DEFAULT, batchBytes)) {
      List<List<@Nullable String>> rows = new ArrayList<>();
      List<String> header = null;
      for (CsvBatch b; (b = reader.next()) != null; ) {
        rows(b, rows);
        header = names(b);
      }
      return new Result(rows, header, null);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (RuntimeException e) {
      return new Result(null, null, e);
    }
  }

  /** Commons' first record without a header (nulls included), or null; later records are not read. */
  private static @Nullable List<@Nullable String> firstRecord(String text, CSVFormat commonsFormat) {
    CSVFormat raw =
        commonsFormat.builder().setHeader((String[]) null).setSkipHeaderRecord(false).setMaxRows(0).get();
    try (CSVParser parser = CSVParser.parse(text, raw)) {
      java.util.Iterator<CSVRecord> it = parser.iterator();
      if (!it.hasNext()) return null;
      CSVRecord record = it.next();
      List<@Nullable String> values = new ArrayList<>();
      for (int i = 0; i < record.size(); i++) values.add(record.get(i));
      return values;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /** The header record as Commons reads it, with null names as "". */
  private static List<String> rawHeader(String text, CSVFormat commonsFormat) {
    List<String> names = new ArrayList<>();
    List<@Nullable String> first = firstRecord(text, commonsFormat);
    if (first != null) for (String name : first) names.add(name == null ? "" : name);
    return names;
  }

  /** Our expected width: explicit names, the header record, or the first data record. */
  private static int width(String text, Result theirs, CsvFormat format, CSVFormat commonsFormat) {
    List<String> names = format.headerNames();
    if (names != null) return names.size();
    if (format.header()) {
      // The raw header record (Commons header names omit null names).
      List<@Nullable String> first = firstRecord(text, commonsFormat);
      return first == null ? 0 : first.size();
    }
    List<List<@Nullable String>> records = theirs.records;
    return records == null || records.isEmpty() ? 0 : records.get(0).size();
  }

  /** Compares one input; returns the outcome label. */
  String check(String text, CsvFormat format, CSVFormat commonsFormat, int batchBytes) {
    byte[] input = text.getBytes(StandardCharsets.UTF_8);
    Result theirs = commons(text, commonsFormat);
    Result ours = ours(input, format);
    String context = "format " + format + "\ninput " + escape(text);
    String label;
    if (!theirs.ok()) {
      assertFalse(ours.ok(), () -> "Commons failed (" + theirs.error + ") but we parsed\n" + context + "\nours " + ours.records);
      assertInstanceOf(IllegalArgumentException.class, ours.error, context);
      label = "both-error";
    } else if (!ours.ok()) {
      String message = String.valueOf(ours.error.getMessage());
      int width = width(text, theirs, format, commonsFormat);
      boolean wider = theirs.records.stream().anyMatch(r -> r.size() > width);
      if (message.contains("empty record with a trailing delimiter")) label = "diff-trailing-empty";
      else {
        assertTrue(
            wider && message.contains("fields, expected"),
            () -> "we failed (" + ours.error + ") but Commons parsed\n" + context + "\ncommons " + theirs.records);
        label = "diff-wider";
      }
    } else {
      assertEquals(theirs.records, ours.records, context);
      if (theirs.header != null && format.header())
        assertEquals(rawHeader(text, commonsFormat), ours.header, context);
      label = theirs.records.isEmpty() ? "both-empty" : "equal";
    }
    // Streaming in small buffers equals the whole parse.
    Result streamed = streamed(input, format, batchBytes);
    if (ours.ok()) {
      assertTrue(streamed.ok(), () -> "stream failed: " + streamed.error + "\n" + context);
      assertEquals(ours.records, streamed.records, () -> "streamed at " + batchBytes + "\n" + context);
    } else assertFalse(streamed.ok(), () -> "stream parsed what the whole parse rejected\n" + context);
    count(label);
    return label;
  }

  static String escape(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      if (c == '\n') sb.append("\\n");
      else if (c == '\r') sb.append("\\r");
      else if (c == '\t') sb.append("\\t");
      else if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int) c));
      else if (c == '"' || c == '\\') sb.append('\\').append(c);
      else sb.append(c);
    }
    return sb.append('"').toString();
  }
}
