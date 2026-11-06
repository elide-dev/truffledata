package dev.elide.truffledata.csv;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seeded random record sets and their RFC 4180 encoding (test helper). */
final class RecordGenerator {
  private static final String[] PIECES = {
    "a", "b", "xyz", ",", "\"", "\r", "\n", "\r\n", " ", "\t", "é", "中", "😀", "0", "-1.5"
  };

  record Encoded(List<List<String>> records, byte[] bytes) {}

  private RecordGenerator() {}

  static Encoded generate(Random random) {
    int recordCount = random.nextInt(9);
    int width = 1 + random.nextInt(5);
    boolean crlf = random.nextBoolean();
    List<List<String>> records = new ArrayList<>();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int r = 0; r < recordCount; r++) {
      List<String> record = new ArrayList<>();
      for (int c = 0; c < width; c++) record.add(field(random));
      records.add(record);
      for (int c = 0; c < width; c++) {
        if (c > 0) out.write(',');
        String value = record.get(c);
        // A record of one unquoted empty field would be an empty line, which the scanner skips.
        boolean needs =
            value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0
                || (width == 1 && value.isEmpty());
        if (needs || random.nextInt(10) < 3) {
          out.writeBytes(
              ("\"" + value.replace("\"", "\"\"") + "\"").getBytes(StandardCharsets.UTF_8));
        } else out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
      }
      boolean last = r == recordCount - 1;
      if (!last || random.nextInt(10) < 7)
        out.writeBytes((crlf ? "\r\n" : "\n").getBytes(StandardCharsets.UTF_8));
    }
    return new Encoded(records, out.toByteArray());
  }

  private static String field(Random random) {
    int pieces = random.nextInt(5);
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < pieces; i++) builder.append(PIECES[random.nextInt(PIECES.length)]);
    return builder.toString();
  }
}
