package dev.elide.truffledata.yaml.tests;

import java.util.Random;

/** Deterministic bounded documents covering each scalar style and mixed container forms. */
final class YamlFuzz {
  private final Random random;
  YamlFuzz(long seed) { random = new Random(seed); }

  String next(int index) {
    String number = switch (random.nextInt(7)) {
      case 0 -> Integer.toString(random.nextInt());
      case 1 -> "0x" + Integer.toHexString(random.nextInt(65536));
      case 2 -> "0o" + Integer.toOctalString(random.nextInt(65536));
      case 3 -> "1.25e" + random.nextInt(20);
      case 4 -> ".nan";
      case 5 -> ".inf";
      default -> "18446744073709551615";
    };
    String flow = "{id: " + index + ", n: " + number
        + ", values: [true, false, null, 'it''s YAML', \"snow \\u2603\"]}";
    return switch (index % 5) {
      case 0 -> flow + " # flow comment\n";
      case 1 -> "# block comment\nid: " + index + "\nn: " + number
          + "\ntext: |-\n  literal line\n  second line\nfolded: >-\n  folded\n  words\n";
      case 2 -> "base: &a " + flow + "\nalias: *a\nitems:\n- *a\n- {x: [one, two]}\n";
      case 3 -> "str: !!str true\nint: !!int 42\nfloat: !!float 2.5\nbool: !!bool true\nnull: null\n"
          + "date: 2001-12-14\nn: " + number + "\nquoted: \"line\\nnext\"\n";
      default -> "- " + flow + "\n- [plain, 'single', \"double\", ~]\n- text: >\n    a folded\n    scalar\n";
    };
  }
}
