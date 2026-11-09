package dev.elide.truffledata.yaml;

import java.nio.charset.StandardCharsets;

/**
 * Scalar presentation rules for {@link YamlWriter}: when a string may be plain, single quoted or
 * literal, and the escaping, tag and number spellings. Resolution questions go to {@link
 * ScalarResolver}, so the writer and the decoder agree by construction.
 */
final class YamlScalars {
  private YamlScalars() {}

  /**
   * Implicit keys are limited to 1024 characters; longer keys (as written, with some margin for
   * the separator) use the explicit {@code ? key} form.
   */
  static final int IMPLICIT_KEY_LIMIT = 1000;

  /** Rejects text that UTF-8 cannot carry (unpaired surrogates). */
  static void checkText(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) i++;
        else throw new IllegalArgumentException("YAML text has an unpaired surrogate");
      } else if (Character.isLowSurrogate(c)) {
        throw new IllegalArgumentException("YAML text has an unpaired surrogate");
      }
    }
  }

  /**
   * Printable without escaping and not white space or a line break: c-printable minus tab, LF,
   * CR, and NEL/LS/PS (line breaks in YAML 1.1), BOM and the non-characters. Surrogates pass
   * (pairs are validated by {@link #checkText}).
   */
  static boolean printable(char c) {
    if (c >= 0x20 && c < 0x7F) return true;
    return c >= 0xA0 && c != 0x2028 && c != 0x2029 && c != 0xFEFF && c != 0xFFFE && c != 0xFFFF;
  }

  private static boolean flowIndicator(char c) {
    return c == ',' || c == '[' || c == ']' || c == '{' || c == '}';
  }

  private static boolean indicator(char c) {
    return switch (c) {
      case '-', '?', ':', ',', '[', ']', '{', '}', '#', '&', '*', '!', '|', '>', '\'', '"', '%',
          '@', '`' ->
          true;
      default -> false;
    };
  }

  /**
   * Whether {@code s} written plain reads back as this same string under {@code schema}: it must
   * resolve to a string ({@link ScalarResolver#plainIsString}) and be a syntactically safe plain
   * scalar in its context (flow collection or block).
   */
  static boolean plain(String s, YamlSchema schema, boolean flow) {
    return plainSyntax(s, schema, flow) && ScalarResolver.plainIsString(s, schema);
  }

  static boolean plainSyntax(String s, YamlSchema schema, boolean flow) {
    int n = s.length();
    if (n == 0) return false;
    char first = s.charAt(0);
    if (indicator(first)) {
      // "-x", "?x", ":x" are plain in block context; flow keeps the conservative form.
      if (flow || n == 1 || (first != '-' && first != '?' && first != ':')) return false;
      char next = s.charAt(1);
      if (next == ' ' || !printable(next)) return false;
    }
    if (first == ' ' || s.charAt(n - 1) == ' ') return false;
    if ((s.startsWith("---") || s.startsWith("..."))
        && (n == 3 || s.charAt(3) == ' ' || s.charAt(3) == '\t'))
      return false;
    if (s.equals("<<") || (schema == YamlSchema.YAML_1_1 && s.equals("="))) return false;
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      if (!printable(c)) return false;
      if (flow && (flowIndicator(c) || c == ':' || c == '#')) return false;
      if (c == ':' && (i + 1 == n || s.charAt(i + 1) == ' ')) return false;
      if (c == '#' && s.charAt(i - 1) == ' ') return false;
    }
    return true;
  }

  /** Single-line single-quoted form: printable characters and tabs, no line breaks. */
  static boolean singleQuotable(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!printable(c) && c != '\t') return false;
    }
    return true;
  }

  static String singleQuoted(String s) {
    return "'" + s.replace("'", "''") + "'";
  }

  /**
   * Literal block scalar form: at least one character other than white space and line breaks,
   * and otherwise only printable characters and tabs (no CR, NEL, LS, PS or controls,
   * which a block scalar cannot carry exactly).
   */
  static boolean literal(String s) {
    boolean content = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\n') continue;
      if (!printable(c) && c != '\t') return false;
      // Block scalar content of white space alone reads back empty.
      content |= c != ' ' && c != '\t';
    }
    return content;
  }

  /** The first line starts with white space or is empty: auto-detection would misread it. */
  static boolean needsIndentIndicator(String s) {
    char c = s.charAt(0);
    return c == ' ' || c == '\t' || c == '\n';
  }

  /** {@code |}, {@code |-} or {@code |+} by the number of trailing line breaks. */
  static char chomping(String s) {
    int n = s.length();
    if (s.charAt(n - 1) != '\n') return '-';
    return n >= 2 && s.charAt(n - 2) == '\n' ? '+' : 0;
  }

  static String doubleQuoted(String s) {
    StringBuilder b = new StringBuilder(s.length() + 2).append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case 0 -> b.append("\\0");
        case 7 -> b.append("\\a");
        case 8 -> b.append("\\b");
        case '\t' -> b.append("\\t");
        case '\n' -> b.append("\\n");
        case 11 -> b.append("\\v");
        case 12 -> b.append("\\f");
        case '\r' -> b.append("\\r");
        case 27 -> b.append("\\e");
        case 0x85 -> b.append("\\N");
        case 0x2028 -> b.append("\\L");
        case 0x2029 -> b.append("\\P");
        default -> {
          if (c < 0x20 || (c >= 0x7F && c < 0xA0)) {
            b.append("\\x").append(hex(c >> 4)).append(hex(c));
          } else if (c == 0xFEFF || c == 0xFFFE || c == 0xFFFF) {
            b.append("\\u")
                .append(hex(c >> 12))
                .append(hex(c >> 8))
                .append(hex(c >> 4))
                .append(hex(c));
          } else {
            b.append(c);
          }
        }
      }
    }
    return b.append('"').toString();
  }

  private static char hex(int nibble) {
    return "0123456789ABCDEF".charAt(nibble & 15);
  }

  /** Characters a tag may carry unescaped (ns-uri-char without '%'). */
  private static boolean uriChar(char c) {
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || "-#;/?:@&=+$,_.!~*'()[]".indexOf(c) >= 0;
  }

  private static boolean tagChar(char c) {
    return uriChar(c) && c != '!' && !flowIndicator(c);
  }

  /**
   * Shortest valid spelling of a resolved tag URI: {@code !!suffix} for the YAML core prefix,
   * {@code !suffix} for local tags, {@code !} for the non-specific tag and {@code !<uri>}
   * otherwise. Characters outside the URI set are percent-encoded as UTF-8.
   */
  static String tag(String uri) {
    if (uri.isEmpty()) throw new IllegalArgumentException("YAML tag is empty");
    if (uri.equals("!")) return "!";
    if (uri.startsWith(ScalarResolver.CORE_PREFIX)) {
      String suffix = uri.substring(ScalarResolver.CORE_PREFIX.length());
      if (shorthand(suffix)) return "!!" + suffix;
    } else if (uri.charAt(0) == '!' && shorthand(uri.substring(1))) {
      return uri;
    }
    StringBuilder b = new StringBuilder("!<");
    for (int i = 0; i < uri.length(); i++) {
      char c = uri.charAt(i);
      if (uriChar(c) && c != '>') {
        b.append(c);
      } else if (c == '%'
          && i + 2 < uri.length()
          && hexDigit(uri.charAt(i + 1))
          && hexDigit(uri.charAt(i + 2))) {
        b.append(c);
      } else {
        int end = Character.isHighSurrogate(c) && i + 1 < uri.length() ? i + 2 : i + 1;
        for (byte octet : uri.substring(i, end).getBytes(StandardCharsets.UTF_8))
          b.append('%').append(hex(octet >> 4)).append(hex(octet));
        i = end - 1;
      }
    }
    return b.append('>').toString();
  }

  private static boolean hexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static boolean shorthand(String suffix) {
    if (suffix.isEmpty()) return false;
    for (int i = 0; i < suffix.length(); i++) if (!tagChar(suffix.charAt(i))) return false;
    return true;
  }

  /** ns-anchor-char+: printable, no white space, no flow indicators. */
  static void checkAnchor(String name) {
    if (name.isEmpty()) throw new IllegalArgumentException("YAML anchor name is empty");
    checkText(name);
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (!printable(c) || c == ' ' || flowIndicator(c))
        throw new IllegalArgumentException("Invalid YAML anchor name: " + name);
    }
  }

  /**
   * Round-trippable float spelling: {@code .inf}, {@code -.inf}, {@code .nan}, else the shortest
   * round-trip digits; YAML 1.1 floats need a '.' in the mantissa.
   */
  static String floatSpelling(double d, YamlSchema schema) {
    if (Double.isNaN(d)) return ".nan";
    if (Double.isInfinite(d)) return d > 0 ? ".inf" : "-.inf";
    String s = ScalarResolver.formatDouble(d);
    if (schema == YamlSchema.YAML_1_1 && s.indexOf('.') < 0) {
      int e = s.indexOf('e');
      s = e < 0 ? s + ".0" : s.substring(0, e) + ".0" + s.substring(e);
    }
    return s;
  }
}
