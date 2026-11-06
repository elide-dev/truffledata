package dev.elide.truffledata.comparison;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Deterministic CSV fixtures: RFC 4180, UTF-8, a header row, LF terminators, generated from a fixed
 * seed until the requested size is reached (the last record is complete). Nothing is downloaded or
 * checked in; the same (dataset, bytes) always yields the same bytes, and {@code --fixture} writes
 * them for the native probes, which compare SHA-256 digests with the Java runs.
 *
 * <ul>
 *   <li>{@code numeric}: eight short numeric and boolean columns (about 5.6 B per field).
 *   <li>{@code mixed}: a wide 24-column table of integers, decimals, booleans, dates, timestamps,
 *       short text (some quoted with delimiters), empty fields and non-ASCII text.
 *   <li>{@code quoted}: long quoted text (200 to 1,200 B) with delimiters, newlines and rare
 *       doubled quotes.
 *   <li>{@code escaped}: escape-heavy quoted fields (embedded JSON and dialogue full of doubled
 *       quotes).
 * </ul>
 */
@NullMarked
public final class CsvFixture {
  public static final List<String> DATASETS = List.of("numeric", "mixed", "quoted", "escaped");
  public static final int SMALL = 1 << 20;
  public static final int LARGE = 128 << 20;

  private static final String[] WORDS = {
    "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit", "sed", "do",
    "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore", "magna", "aliqua", "data",
    "column", "value", "record", "stream", "kernel", "vector", "quote", "field", "batch", "index",
  };
  private static final String[] CITIES = {
    "Berlin", "Paris", "Zürich", "東京", "São Paulo", "Kraków", "New York", "Reykjavík", "Seoul",
    "Montréal", "Αθήνα", "Москва", "Lisboa", "Cairo", "Oslo",
  };
  private static final String[] NAMES = {
    "Smith, John", "Müller", "O'Brien", "García, Ana", "Lee", "Nguyễn", "Kowalski", "Rossi",
    "Dubois, Marie", "Tanaka", "Ivanova", "Okafor", "Björk", "Chen, Wei",
  };

  private final byte[] bytes;
  private int size;

  private CsvFixture(int capacity) {
    bytes = new byte[capacity];
  }

  /** The fixture bytes: at least {@code target} bytes, ending with a complete record. */
  public static byte[] generate(String dataset, int target) {
    // Rows are short next to the slack, so one allocation fits the whole fixture.
    CsvFixture out = new CsvFixture(target + (64 << 10));
    SplittableRandom random = new SplittableRandom(0x5eed_c5f0L ^ dataset.hashCode());
    switch (dataset) {
      case "numeric" -> {
        out.ascii("id,qty,code,price,ratio,delta,active,score\n");
        for (long id = 1; out.size < target; id++) out.numeric(random, id);
      }
      case "mixed" -> {
        out.ascii(
            "id,name,city,country,email,age,balance,rate,active,joined,updated,status,score,"
                + "tags,note,code,lat,lon,visits,ratio,flag,empty,level,comment\n");
        for (long id = 1; out.size < target; id++) out.mixed(random, id);
      }
      case "quoted" -> {
        out.ascii("id,title,body,tags\n");
        for (long id = 1; out.size < target; id++) out.quoted(random, id);
      }
      case "escaped" -> {
        out.ascii("id,dialogue,payload\n");
        for (long id = 1; out.size < target; id++) out.escaped(random, id);
      }
      default -> throw new IllegalArgumentException("dataset " + dataset);
    }
    return Arrays.copyOf(out.bytes, out.size);
  }

  /** The typed schema for a dataset's typed workload, or null when it has no typed columns. */
  public static @Nullable Schema typedSchema(String dataset) {
    return switch (dataset) {
      case "numeric" ->
          Schema.of(
              Field.requiredLong("id"),
              Field.requiredLong("qty"),
              Field.requiredLong("code"),
              new Field("price", Field.Kind.FLOAT64, false, false),
              new Field("ratio", Field.Kind.FLOAT64, false, false),
              Field.requiredLong("delta"),
              new Field("active", Field.Kind.BOOLEAN, false, false),
              new Field("score", Field.Kind.FLOAT64, false, false));
      case "mixed" -> {
        String[] names = {
          "id", "name", "city", "country", "email", "age", "balance", "rate", "active", "joined",
          "updated", "status", "score", "tags", "note", "code", "lat", "lon", "visits", "ratio",
          "flag", "empty", "level", "comment",
        };
        Field[] fields = new Field[names.length];
        for (int i = 0; i < names.length; i++) {
          Field.Kind kind =
              switch (names[i]) {
                case "id", "age", "code", "visits", "level" -> Field.Kind.INT64;
                case "balance", "rate", "score", "lat", "lon", "ratio" -> Field.Kind.FLOAT64;
                case "active", "flag" -> Field.Kind.BOOLEAN;
                default -> Field.Kind.STRING;
              };
          fields[i] = new Field(names[i], kind, false, false);
        }
        yield Schema.of(fields);
      }
      default -> null;
    };
  }

  /** Column kinds by ordinal: 'L' INT64, 'D' FLOAT64, 'B' BOOLEAN, 'S' STRING (skipped). */
  public static char[] kinds(String dataset) {
    Schema schema = typedSchema(dataset);
    if (schema == null) throw new IllegalArgumentException("no typed schema for " + dataset);
    char[] kinds = new char[schema.size()];
    for (int i = 0; i < kinds.length; i++)
      kinds[i] =
          switch (schema.field(i).kind()) {
            case INT64 -> 'L';
            case FLOAT64 -> 'D';
            case BOOLEAN -> 'B';
            default -> 'S';
          };
    return kinds;
  }

  private void numeric(SplittableRandom r, long id) {
    integer(id);
    comma();
    integer(r.nextInt(1000));
    comma();
    integer(10_000 + r.nextInt(990_000));
    comma();
    decimal(r.nextInt(1_000_000), 2);
    comma();
    decimal(r.nextInt(1_000_000), 6);
    comma();
    integer(r.nextInt(1001) - 500);
    comma();
    ascii(r.nextBoolean() ? "true" : "false");
    comma();
    decimal(r.nextInt(10_000_000), 3);
    newline();
  }

  private void mixed(SplittableRandom r, long id) {
    integer(id);
    comma();
    quotedIfNeeded(NAMES[r.nextInt(NAMES.length)]);
    comma();
    utf8(CITIES[r.nextInt(CITIES.length)]);
    comma();
    ascii(new String[] {"DE", "FR", "CH", "JP", "BR", "PL", "US", "IS"}[r.nextInt(8)]);
    comma();
    ascii(WORDS[r.nextInt(WORDS.length)]);
    ascii(".");
    ascii(WORDS[r.nextInt(WORDS.length)]);
    ascii("@example.com");
    comma();
    integer(18 + r.nextInt(70));
    comma();
    decimal(r.nextLong(100_000_000L) - 20_000_000L, 2);
    comma();
    decimal(r.nextInt(100_000), 4);
    comma();
    ascii(r.nextBoolean() ? "true" : "false");
    comma();
    date(r);
    comma();
    date(r);
    ascii("T");
    two(r.nextInt(24));
    ascii(":");
    two(r.nextInt(60));
    ascii(":");
    two(r.nextInt(60));
    ascii("Z");
    comma();
    ascii(new String[] {"active", "pending", "closed", "suspended"}[r.nextInt(4)]);
    comma();
    decimal(r.nextInt(10_000), 1);
    comma();
    if (r.nextInt(3) == 0) {
      ascii("\"");
      ascii(WORDS[r.nextInt(WORDS.length)]);
      ascii(",");
      ascii(WORDS[r.nextInt(WORDS.length)]);
      ascii("\"");
    } else {
      ascii(WORDS[r.nextInt(WORDS.length)]);
    }
    comma();
    if (r.nextInt(4) == 0) {
      ascii("\"");
      words(r, 4 + r.nextInt(8), false);
      ascii(", ");
      words(r, 2, false);
      ascii("\"");
    } else {
      words(r, 1 + r.nextInt(3), false);
    }
    comma();
    integer(100_000 + r.nextInt(900_000));
    comma();
    decimal(r.nextInt(180_000_000) - 90_000_000, 6);
    comma();
    decimal(r.nextInt(360_000_000) - 180_000_000, 6);
    comma();
    integer(r.nextInt(100_000));
    comma();
    decimal(r.nextInt(1_000_000), 6);
    comma();
    ascii(r.nextInt(5) == 0 ? "true" : "false");
    comma();
    // empty column
    comma();
    integer(r.nextInt(10));
    comma();
    if (r.nextInt(6) == 0) utf8("naïve café ✓");
    else words(r, 2 + r.nextInt(4), false);
    newline();
  }

  private void quoted(SplittableRandom r, long id) {
    integer(id);
    comma();
    ascii("\"");
    words(r, 3 + r.nextInt(6), true);
    ascii("\"");
    comma();
    ascii("\"");
    int sentences = 3 + r.nextInt(14);
    for (int s = 0; s < sentences; s++) {
      if (s > 0) ascii(r.nextInt(8) == 0 ? "\n" : " ");
      words(r, 5 + r.nextInt(10), true);
      ascii(r.nextInt(3) == 0 ? ", and" : ".");
      if (r.nextInt(100) == 0) ascii(" \"\"cited\"\"");
    }
    ascii("\"");
    comma();
    words(r, 1 + r.nextInt(3), false);
    newline();
  }

  private void escaped(SplittableRandom r, long id) {
    integer(id);
    comma();
    ascii("\"");
    int lines = 1 + r.nextInt(4);
    for (int i = 0; i < lines; i++) {
      if (i > 0) ascii(" ");
      ascii("He said \"\"");
      words(r, 1 + r.nextInt(4), false);
      ascii("\"\", then \"\"");
      words(r, 1 + r.nextInt(3), false);
      ascii("!\"\"");
    }
    ascii("\"");
    comma();
    ascii("\"{");
    int members = 2 + r.nextInt(6);
    for (int i = 0; i < members; i++) {
      if (i > 0) ascii(",");
      ascii("\"\"");
      ascii(WORDS[r.nextInt(WORDS.length)]);
      ascii("\"\":");
      if (r.nextBoolean()) integer(r.nextInt(100_000));
      else {
        ascii("\"\"");
        words(r, 1 + r.nextInt(2), false);
        ascii("\"\"");
      }
    }
    ascii("}\"");
    newline();
  }

  private void words(SplittableRandom r, int count, boolean commas) {
    for (int i = 0; i < count; i++) {
      if (i > 0) ascii(commas && r.nextInt(7) == 0 ? ", " : " ");
      ascii(WORDS[r.nextInt(WORDS.length)]);
    }
  }

  private void date(SplittableRandom r) {
    integer(2000 + r.nextInt(26));
    ascii("-");
    two(1 + r.nextInt(12));
    ascii("-");
    two(1 + r.nextInt(28));
  }

  private void two(int value) {
    bytes[size++] = (byte) ('0' + value / 10);
    bytes[size++] = (byte) ('0' + value % 10);
  }

  private void quotedIfNeeded(String text) {
    if (text.indexOf(',') >= 0) {
      ascii("\"");
      utf8(text);
      ascii("\"");
    } else utf8(text);
  }

  private void comma() {
    bytes[size++] = ',';
  }

  private void newline() {
    bytes[size++] = '\n';
  }

  private void ascii(String text) {
    for (int i = 0; i < text.length(); i++) bytes[size++] = (byte) text.charAt(i);
  }

  private void utf8(String text) {
    byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(encoded, 0, bytes, size, encoded.length);
    size += encoded.length;
  }

  private void integer(long value) {
    if (value < 0) {
      bytes[size++] = '-';
      value = -value;
    }
    int start = size;
    do {
      bytes[size++] = (byte) ('0' + value % 10);
      value /= 10;
    } while (value != 0);
    for (int i = start, j = size - 1; i < j; i++, j--) {
      byte t = bytes[i];
      bytes[i] = bytes[j];
      bytes[j] = t;
    }
  }

  /** {@code value / 10^scale} with exactly {@code scale} decimals (at most 15 digits). */
  private void decimal(long value, int scale) {
    if (value < 0) {
      bytes[size++] = '-';
      value = -value;
    }
    long unit = 1;
    for (int i = 0; i < scale; i++) unit *= 10;
    integer(value / unit);
    bytes[size++] = '.';
    long fraction = value % unit;
    for (long digit = unit / 10; digit > 0; digit /= 10) {
      bytes[size++] = (byte) ('0' + fraction / digit);
      fraction %= digit;
    }
  }

  public static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  /** {@code --fixture dataset bytes path}: writes one fixture and prints its digest. */
  public static void write(String dataset, int target, Path path) throws IOException {
    byte[] data = generate(dataset, target);
    Files.write(path, data);
    System.out.println(
        "{\"fixture\":\"" + dataset + "\",\"target\":" + target + ",\"bytes\":" + data.length
            + ",\"sha256\":\"" + sha256(data) + "\"}");
  }
}
