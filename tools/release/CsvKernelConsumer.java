import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.csv.Csv;
import dev.elide.truffledata.csv.CsvBatch;
import dev.elide.truffledata.csv.CsvFormat;
import dev.elide.truffledata.csv.CsvIndexing;
import dev.elide.truffledata.csv.CsvLimits;
import dev.elide.truffledata.csv.CsvPlan;
import dev.elide.truffledata.csv.CsvReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.nativeimage.ImageInfo;

/**
 * Uses only publication jars and attachments: truffledata-csv with the optional native kernel,
 * truffledata-csv-native (static image, linked archive) or truffledata-csv-jvm plus the published
 * shared library (JVM). Every result must equal the built-in java-swar kernel's.
 */
public final class CsvKernelConsumer {
  private static void check(boolean condition, String what) {
    if (!condition) throw new AssertionError("Published CSV kernel " + what);
  }

  /** At least 64 KiB of CSV (the index path starts at 16 KiB) with every structural case. */
  private static byte[] input() {
    StringBuilder text = new StringBuilder("id,name,price,active,note\r\n");
    for (int i = 0; text.length() < 96 << 10; i++) {
      text.append(i).append(',');
      text.append(i % 3 == 0 ? "\"trüffle, \"\"" + i + "\"\"\"" : "plain" + i).append(',');
      text.append(i % 7 == 0 ? "-" : "").append(i).append('.').append(i % 100).append(',');
      text.append(i % 2 == 0 ? "true" : "FALSE").append(',');
      text.append(i % 5 == 0 ? "\"two\nlines\"" : "").append(i % 4 == 0 ? "\n" : "\r\n");
    }
    return text.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static List<String> strings(CsvBatch batch) {
    List<String> cells = new ArrayList<>();
    for (int r = 0; r < batch.size(); r++)
      for (int c = 0; c < batch.schema().size(); c++) cells.add(batch.row(r).getString(c));
    return cells;
  }

  private static List<Object> typed(CsvBatch batch) {
    List<Object> cells = new ArrayList<>();
    for (int r = 0; r < batch.size(); r++) {
      cells.add(batch.row(r).getLong(0));
      cells.add(batch.row(r).getString(1));
      cells.add(batch.row(r).getDouble(2));
      cells.add(batch.row(r).getBoolean(3));
    }
    return cells;
  }

  private static String failure(byte[] input) {
    try {
      Csv.parse(input, CsvFormat.RFC4180);
      return "accepted";
    } catch (IllegalArgumentException e) {
      return e.getMessage();
    }
  }

  private static List<Object> results(byte[] input, CsvPlan plan) throws IOException {
    List<Object> out = new ArrayList<>();
    out.add(strings(Csv.parse(input, CsvFormat.RFC4180)));
    out.add(typed(plan.parse(input)));
    try (CsvReader reader =
        Csv.open(new ByteArrayInputStream(input), CsvFormat.RFC4180, CsvLimits.DEFAULT, 32 << 10)) {
      List<String> streamed = new ArrayList<>();
      for (CsvBatch batch; (batch = reader.next()) != null; ) streamed.addAll(strings(batch));
      out.add(streamed);
    }
    String text = new String(input, StandardCharsets.ISO_8859_1);
    byte[] broken = input.clone();
    broken[text.indexOf("plain", input.length / 2) + 2] = '"'; // a quote in an unquoted field
    out.add(failure(broken));
    byte[] invalid = input.clone();
    invalid[text.indexOf("plain", input.length / 3)] = (byte) 0xC3; // a truncated sequence
    out.add(failure(invalid));
    try {
      plan.parse(new String(input, StandardCharsets.UTF_8).replace(",FALSE,", ",maybe,").getBytes(StandardCharsets.UTF_8));
      out.add("accepted");
    } catch (ContractViolation e) {
      out.add(e.getMessage());
    }
    return out;
  }

  public static void main(String[] args) throws IOException {
    boolean image = ImageInfo.inImageCode();
    // Nothing native is loaded or installed until the application asks.
    check(CsvIndexing.kernel().equals("java-swar"), "default kernel " + CsvIndexing.kernel());
    if (!image) {
      try {
        CsvIndexing.loadNative();
        throw new AssertionError("The JVM backend loaded without a library path");
      } catch (IllegalStateException expected) {
        // The FFM backend needs the published shared library.
      }
    }
    CsvIndexing.Provider provider = CsvIndexing.loadNative(image ? null : Path.of(args[0]));
    check(provider.name().startsWith("native-"), "provider " + provider.name());
    check(CsvIndexing.kernel().equals("java-swar"), "loading installed the kernel");

    byte[] input = input();
    Schema schema =
        Schema.of(
            Field.requiredLong("id"),
            new Field("name", Field.Kind.STRING, false, false),
            new Field("price", Field.Kind.FLOAT64, false, false),
            new Field("active", Field.Kind.BOOLEAN, false, false));
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    List<Object> java = results(input, plan);
    CsvIndexing.install(provider);
    check(CsvIndexing.kernel().equals(provider.name()), "installed kernel");
    List<Object> nativeResults = results(input, plan);
    CsvIndexing.install(CsvIndexing.JAVA);
    check(java.equals(nativeResults), "results differ from java-swar");
    check(!java.get(3).equals("accepted") && !java.get(4).equals("accepted"), "malformed input accepted");
    System.out.println(
        "Published CSV native kernel "
            + provider.name()
            + " matched java-swar on parse, typed plans, streams and errors; SVM="
            + image);
  }
}
