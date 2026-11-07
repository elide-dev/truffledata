package dev.elide.truffledata.csv;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeded generator of adversarial CSV text for a format: structured records (fields quoted or not,
 * doubled quotes, escapes, blanks, Unicode spaces, comments, empty lines, every terminator) mixed
 * with symbol soup over the format's meta characters.
 */
final class CsvFuzz {
  private static final String[] WORDS = {
    "a", "b", "xyz", "42", "-7", "3.5", "1e3", "true", "N", "\\N", "NULL", "é", "中文", "😀", "",
    "A", " ", "#x", "a b"
  };
  private static final String[] SPACES = {" ", "\t", "　", " ", " ", "\u000b", ""};
  private static final String[] EOLS = {"\n", "\r\n", "\r"};

  private final Random random;
  private final CsvFormat format;
  private final List<String> symbols = new ArrayList<>();

  CsvFuzz(Random random, CsvFormat format) {
    this.random = random;
    this.format = format;
    String delimiter = format.delimiterString();
    symbols.add(delimiter);
    symbols.add(delimiter);
    symbols.add(delimiter.substring(0, 1));
    if (format.quoting()) {
      String q = String.valueOf((char) format.quote());
      symbols.add(q);
      symbols.add(q);
      symbols.add(q + q);
    }
    if (format.escape() >= 0) {
      String e = String.valueOf((char) format.escape());
      symbols.add(e);
      symbols.add(e + "n");
      symbols.add(e + "N");
      symbols.add(e + delimiter);
      symbols.add(e);
    }
    if (format.commentMarker() >= 0) symbols.add(String.valueOf((char) format.commentMarker()));
    List<String> nulls = format.nullStrings();
    if (nulls != null) symbols.addAll(nulls);
    for (String s : new String[] {"\n", "\r\n", "\r", " ", "\t", "　", "a", "b", "é", "\"", ",", "\\", "#", "|"})
      symbols.add(s);
  }

  private <T> T pick(T[] values) {
    return values[random.nextInt(values.length)];
  }

  /** One input; multi-byte-delimiter formats with an escape always end in LF (see docs/CSV.md). */
  String next() {
    String text = random.nextInt(3) == 0 ? soup() : structured();
    if (format.delimiterString().length() > 1 && format.escape() >= 0 && !text.endsWith("\n"))
      text += "\n";
    return text;
  }

  private String soup() {
    StringBuilder sb = new StringBuilder();
    int n = random.nextInt(40);
    for (int i = 0; i < n; i++) sb.append(symbols.get(random.nextInt(symbols.size())));
    return sb.toString();
  }

  private String structured() {
    StringBuilder sb = new StringBuilder();
    int records = random.nextInt(6);
    int width = 1 + random.nextInt(4);
    for (int r = 0; r < records; r++) {
      if (random.nextInt(8) == 0) sb.append(pick(EOLS)); // empty line
      if (format.commentMarker() >= 0 && random.nextInt(6) == 0)
        sb.append((char) format.commentMarker()).append(" note").append(pick(EOLS));
      int fields = random.nextInt(5) == 0 ? 1 + random.nextInt(5) : width;
      for (int f = 0; f < fields; f++) {
        if (f > 0) sb.append(format.delimiterString());
        field(sb);
      }
      if (format.trailingDelimiter() && random.nextBoolean()) sb.append(format.delimiterString());
      if (r < records - 1 || random.nextBoolean()) sb.append(pick(EOLS));
    }
    return sb.toString();
  }

  private void field(StringBuilder sb) {
    List<String> nulls = format.nullStrings();
    if (nulls != null && random.nextInt(5) == 0) {
      sb.append(nulls.get(random.nextInt(nulls.size())));
      return;
    }
    if (random.nextInt(4) == 0) sb.append(pick(SPACES));
    String word = pick(WORDS);
    if (format.quoting() && random.nextInt(3) == 0) {
      char q = (char) format.quote();
      sb.append(q);
      if (random.nextInt(3) == 0) word += q + "" + q + "x";
      if (random.nextInt(4) == 0) word += format.delimiterString() + pick(EOLS);
      sb.append(word).append(q);
      if (random.nextInt(6) == 0) sb.append(pick(SPACES));
      if (random.nextInt(12) == 0) sb.append("z"); // trailing data
    } else {
      if (format.escape() >= 0 && random.nextInt(4) == 0)
        word += (char) format.escape() + (random.nextBoolean() ? format.delimiterString() : "t");
      sb.append(word);
    }
    if (random.nextInt(4) == 0) sb.append(pick(SPACES));
  }
}
