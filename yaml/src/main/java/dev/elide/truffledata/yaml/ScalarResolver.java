package dev.elide.truffledata.yaml;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.jspecify.annotations.Nullable;

/**
 * YAML scalar resolution, a line-for-line port of {@code yaml/native/scalars.h} and the tag
 * classification of {@code yaml/native/tags.h}. The Java kernel resolves scalars with it, and the
 * emitter asks it whether a string may be written plain under a schema, so both agree with the
 * native decoder by construction; {@code ScalarResolverTest} checks the agreement against native.
 *
 * <p>Numbers map as the native decoder maps them: int64 to {@link Kind#I64}, above int64 within
 * uint64 to {@link Kind#U64}, larger to {@link Kind#DECIMAL} (canonical decimal digits), {@code -0}
 * to F64 -0.0, finite floats to {@link Kind#F64}, floats overflowing binary64 to DECIMAL (JSON
 * grammar), underflow to a signed zero, and {@code .inf}/{@code .nan} to non-finite F64.
 */
final class ScalarResolver {
  private ScalarResolver() {}

  static final String CORE_PREFIX = "tag:yaml.org,2002:";

  /** Myna value kinds a scalar resolves to. */
  enum Kind {
    NULL,
    BOOL,
    I64,
    U64,
    F64,
    DECIMAL,
    STRING
  }

  /** Tags the decoder knows; {@code OTHER} is any other tag. */
  enum Tag {
    NONE,
    NONSPECIFIC,
    STR,
    INT,
    FLOAT,
    BOOL,
    NULL,
    SEQ,
    MAP,
    BINARY,
    TIMESTAMP,
    SET,
    OMAP,
    PAIRS,
    MERGE,
    VALUE,
    OTHER
  }

  /**
   * A resolved scalar. {@code bits} carries BOOL (0/1), I64/U64 (two's complement) and F64 (raw
   * bits) payloads; {@code text} the STRING text or DECIMAL digits. {@code timestamp} marks a YAML
   * 1.1 plain string that resolves to an implicit {@code !!timestamp}.
   */
  record Scalar(Kind kind, KeyKind key, long bits, @Nullable String text, boolean timestamp) {
    boolean nonfinite() {
      return kind == Kind.F64 && !Double.isFinite(Double.longBitsToDouble(bits));
    }

    double doubleValue() {
      return Double.longBitsToDouble(bits);
    }
  }

  /** Thrown for a schema or tag violation; the decoder maps it to a YAML syntax error. */
  static final class ResolveException extends IllegalArgumentException {
    final boolean limit;

    ResolveException(String message, boolean limit) {
      super(message);
      this.limit = limit;
    }
  }

  private static ResolveException syntax(String message) {
    return new ResolveException(message, false);
  }

  private static ResolveException limit(String which) {
    return new ResolveException("YAML resource limit: " + which, true);
  }

  /** Largest radix-converted integer, in source digits (native max_radix_digits). */
  static final int MAX_RADIX_DIGITS = 1000;

  private static final Scalar NULL = new Scalar(Kind.NULL, KeyKind.NULL, 0, null, false);
  private static final Scalar TRUE = new Scalar(Kind.BOOL, KeyKind.BOOL, 1, null, false);
  private static final Scalar FALSE = new Scalar(Kind.BOOL, KeyKind.BOOL, 0, null, false);
  private static final long NEGATIVE_ZERO = Double.doubleToRawLongBits(-0.0);

  // ---- tags ---------------------------------------------------------------------------------

  /** Classifies a resolved tag URI ({@code tag:yaml.org,2002:str}, {@code !local}, {@code !}). */
  static Tag classify(@Nullable String uri) {
    if (uri == null) return Tag.NONE;
    if (uri.equals("!")) return Tag.NONSPECIFIC;
    if (!uri.startsWith(CORE_PREFIX)) return Tag.OTHER;
    return switch (uri.substring(CORE_PREFIX.length())) {
      case "str" -> Tag.STR;
      case "int" -> Tag.INT;
      case "seq" -> Tag.SEQ;
      case "map" -> Tag.MAP;
      case "set" -> Tag.SET;
      case "bool" -> Tag.BOOL;
      case "null" -> Tag.NULL;
      case "omap" -> Tag.OMAP;
      case "float" -> Tag.FLOAT;
      case "pairs" -> Tag.PAIRS;
      case "merge" -> Tag.MERGE;
      case "value" -> Tag.VALUE;
      case "binary" -> Tag.BINARY;
      case "timestamp" -> Tag.TIMESTAMP;
      default -> Tag.OTHER;
    };
  }

  // ---- entry points -------------------------------------------------------------------------

  /**
   * Resolves a scalar: tagged scalars by their tag, untagged plain scalars by the schema, every
   * other untagged scalar is a string.
   */
  static Scalar resolve(String text, boolean plain, YamlSchema schema, Tag tag) {
    if (tag != Tag.NONE) return resolveTagged(text, tag, schema);
    if (!plain) return string(text);
    return resolvePlain(text, schema);
  }

  /**
   * Whether {@code text} written as an untagged plain scalar reads back as this same string under
   * {@code schema} (resolution only; the emitter separately checks YAML syntax hazards).
   */
  static boolean plainIsString(String text, YamlSchema schema) {
    if (schema == YamlSchema.FAILSAFE) return true;
    if (schema == YamlSchema.JSON) return false; // every JSON-schema plain scalar is non-string
    try {
      Scalar r = resolvePlain(text, schema);
      return r.kind == Kind.STRING && !r.timestamp;
    } catch (ResolveException e) {
      return false;
    }
  }

  /** Implicit resolution of a plain scalar (no tag). The JSON schema rejects non-JSON forms. */
  static Scalar resolvePlain(String text, YamlSchema schema) {
    switch (schema) {
      case FAILSAFE:
        return string(text);
      case JSON:
        if (text.equals("null")) return NULL;
        if (text.equals("true")) return TRUE;
        if (text.equals("false")) return FALSE;
        if (jsonInt(text)) {
          boolean negative = text.charAt(0) == '-';
          return decimalInteger(text, negative ? text.substring(1) : text, negative);
        }
        if (jsonFloat(text)) return floatToken(text);
        throw syntax("YAML JSON schema: plain scalar is not a JSON null, boolean or number");
      case YAML_1_1:
        {
          if (isNull(text)) return NULL;
          int b = isBool11(text);
          if (b >= 0) return b == 1 ? TRUE : FALSE;
          char c = text.charAt(0);
          if (digit(c) || c == '-' || c == '+' || c == '.') {
            Scalar r = int11(text);
            if (r != null) return r;
            r = float11(text);
            if (r != null) return r;
            if (validTimestamp(text)) return new Scalar(Kind.STRING, KeyKind.STRING, 0, text, true);
          }
          return string(text);
        }
      default:
        break;
    }
    if (text.isEmpty()) return NULL;
    char c = text.charAt(0);
    if (c == 'n' || c == 'N' || c == '~') return isNull(text) ? NULL : string(text);
    if (c == 't' || c == 'T' || c == 'f' || c == 'F') {
      int b = isBool(text);
      return b < 0 ? string(text) : b == 1 ? TRUE : FALSE;
    }
    if (digit(c) || c == '-' || c == '+' || c == '.') {
      Scalar r = coreInt(text);
      if (r != null) return r;
      r = coreFloatValue(text);
      if (r != null) return r;
    }
    return string(text);
  }

  /** Resolution with an explicit tag (any style). Mismatches are syntax errors. */
  static Scalar resolveTagged(String text, Tag tag, YamlSchema schema) {
    boolean v11 = schema == YamlSchema.YAML_1_1;
    Scalar r;
    switch (tag) {
      case STR, NONSPECIFIC, OTHER:
        return string(text);
      case NULL:
        if (isNull(text)) return NULL;
        throw syntax("YAML !!null tag on a non-null scalar");
      case BOOL:
        {
          int b = v11 ? isBool11(text) : isBool(text);
          if (b >= 0) return b == 1 ? TRUE : FALSE;
          throw syntax("YAML !!bool tag on a non-boolean scalar");
        }
      case INT:
        r = v11 ? int11(text) : coreInt(text);
        if (r != null) return r;
        throw syntax("YAML !!int tag on a non-integer scalar");
      case FLOAT:
        r = v11 ? float11(text) : coreFloatValue(text);
        if (r != null) return r;
        if (v11) {
          r = int11(text);
          if (r != null) return asFloat(r);
        }
        throw syntax("YAML !!float tag on a non-float scalar");
      case SEQ, MAP:
        throw syntax("YAML collection tag on a scalar");
      default:
        throw syntax("YAML tag not supported on a scalar");
    }
  }

  /**
   * Canonical STRING text of a resolved scalar used as a mapping key: null to "", booleans to
   * true/false, integers to decimal, floats to the shortest round-trip form in simdjson's spelling
   * without a trailing ".0", non-finite floats to .inf/-.inf/.nan, DECIMAL to its digits.
   */
  static String keyText(Scalar r) {
    return switch (r.kind) {
      case NULL -> "";
      case BOOL -> r.bits != 0 ? "true" : "false";
      case I64 -> Long.toString(r.bits);
      case U64 -> Long.toUnsignedString(r.bits);
      case F64 -> {
        double d = r.doubleValue();
        if (Double.isNaN(d)) yield ".nan";
        if (Double.isInfinite(d)) yield d < 0 ? "-.inf" : ".inf";
        String s = formatDouble(d);
        yield s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
      }
      default -> java.util.Objects.requireNonNull(r.text);
    };
  }

  /**
   * simdjson's {@code internal::to_chars}: shortest round-trip digits (Dragonbox; Java's {@link
   * Double#toString} produces the same shortest, closest digits), printed fixed in [1e-4, 1e15) and
   * otherwise as {@code d.ddde+XX} with at least two exponent digits.
   */
  static String formatDouble(double value) {
    StringBuilder out = new StringBuilder(26);
    if ((Double.doubleToRawLongBits(value) & Long.MIN_VALUE) != 0) {
      out.append('-');
      value = -value;
    }
    if (value == 0) return out.append("0.0").toString();
    BigDecimal exact = new BigDecimal(Double.toString(value)).stripTrailingZeros();
    // Double.toString prints at least two digits ("4.9E-324" where "5e-324" round-trips).
    // Dragonbox's result is the shortest round-tripping spelling, closest to the exact value.
    for (int p = 1; p < exact.precision(); p++) {
      BigDecimal shorter =
          new BigDecimal(value).round(new java.math.MathContext(p, java.math.RoundingMode.HALF_EVEN));
      if (shorter.doubleValue() == value) {
        exact = shorter.stripTrailingZeros();
        break;
      }
    }
    String digits = exact.unscaledValue().toString();
    int k = digits.length();
    int n = k - exact.scale(); // decimal point position relative to the digits
    final int minExp = -4, maxExp = 15;
    if (k <= n && n <= maxExp) {
      out.append(digits).append("0".repeat(n - k)).append(".0");
    } else if (0 < n && n <= maxExp) {
      out.append(digits, 0, n).append('.').append(digits, n, k);
    } else if (minExp < n && n <= 0) {
      out.append("0.").append("0".repeat(-n)).append(digits);
    } else {
      out.append(digits.charAt(0));
      if (k > 1) out.append('.').append(digits, 1, k);
      int e = n - 1;
      out.append('e').append(e < 0 ? '-' : '+');
      int a = Math.abs(e);
      if (a < 10) out.append('0');
      out.append(a);
    }
    return out.toString();
  }

  // ---- grammar ------------------------------------------------------------------------------

  static boolean digit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean octal(char c) {
    return c >= '0' && c <= '7';
  }

  private static boolean hex(char c) {
    return digit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private interface CharTest {
    boolean test(char c);
  }

  private static boolean all(String s, int from, CharTest test) {
    if (from >= s.length()) return false;
    for (int i = from; i < s.length(); i++) if (!test.test(s.charAt(i))) return false;
    return true;
  }

  static boolean isNull(String s) {
    return switch (s.length()) {
      case 0 -> true;
      case 1 -> s.charAt(0) == '~';
      case 4 -> s.equals("null") || s.equals("Null") || s.equals("NULL");
      default -> false;
    };
  }

  static int isBool(String s) {
    if (s.length() == 4) return s.equals("true") || s.equals("True") || s.equals("TRUE") ? 1 : -1;
    if (s.length() == 5)
      return s.equals("false") || s.equals("False") || s.equals("FALSE") ? 0 : -1;
    return -1;
  }

  static int isBool11(String s) {
    return switch (s.length()) {
      case 1 -> {
        char c = s.charAt(0);
        yield c == 'y' || c == 'Y' ? 1 : c == 'n' || c == 'N' ? 0 : -1;
      }
      case 2 ->
          s.equals("on") || s.equals("On") || s.equals("ON")
              ? 1
              : s.equals("no") || s.equals("No") || s.equals("NO") ? 0 : -1;
      case 3 ->
          s.equals("yes") || s.equals("Yes") || s.equals("YES")
              ? 1
              : s.equals("off") || s.equals("Off") || s.equals("OFF") ? 0 : -1;
      default -> isBool(s);
    };
  }

  /** 1 +inf, -1 -inf, 2 nan, 0 none. */
  private static int specialFloat(String s) {
    String body = s;
    int sign = 1;
    if (!body.isEmpty() && (body.charAt(0) == '+' || body.charAt(0) == '-')) {
      sign = body.charAt(0) == '-' ? -1 : 1;
      body = body.substring(1);
    }
    if (body.equals(".inf") || body.equals(".Inf") || body.equals(".INF")) return sign;
    if (s.equals(".nan") || s.equals(".NaN") || s.equals(".NAN")) return 2;
    return 0;
  }

  /** {@code [-+]?(\.[0-9]+|[0-9]+(\.[0-9]*)?)([eE][-+]?[0-9]+)?} */
  private static boolean coreFloat(String s) {
    int i = 0, n = s.length();
    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) ++i;
    int intDigits = 0, fracDigits = 0;
    while (i < n && digit(s.charAt(i))) {
      ++i;
      ++intDigits;
    }
    if (i < n && s.charAt(i) == '.') {
      ++i;
      while (i < n && digit(s.charAt(i))) {
        ++i;
        ++fracDigits;
      }
    }
    if (intDigits == 0 && fracDigits == 0) return false;
    if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
      ++i;
      if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) ++i;
      int exp = 0;
      while (i < n && digit(s.charAt(i))) {
        ++i;
        ++exp;
      }
      if (exp == 0) return false;
    }
    return i == n;
  }

  /** {@code -?(0|[1-9][0-9]*)} */
  private static boolean jsonInt(String s) {
    int i = !s.isEmpty() && s.charAt(0) == '-' ? 1 : 0;
    if (i >= s.length()) return false;
    if (s.charAt(i) == '0') return i + 1 == s.length();
    return all(s, i, ScalarResolver::digit);
  }

  /** {@code -?(0|[1-9][0-9]*)(\.[0-9]*)?([eE][-+]?[0-9]+)?} */
  private static boolean jsonFloat(String s) {
    int i = 0, n = s.length();
    if (i < n && s.charAt(i) == '-') ++i;
    if (i >= n || !digit(s.charAt(i))) return false;
    if (s.charAt(i) == '0') ++i;
    else while (i < n && digit(s.charAt(i))) ++i;
    if (i < n && s.charAt(i) == '.') {
      ++i;
      while (i < n && digit(s.charAt(i))) ++i;
    }
    if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
      ++i;
      if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) ++i;
      int exp = 0;
      while (i < n && digit(s.charAt(i))) {
        ++i;
        ++exp;
      }
      if (exp == 0) return false;
    }
    return i == n;
  }

  /** RFC 8259 number grammar. */
  private static boolean rfc8259(String s) {
    int i = 0, n = s.length();
    if (i < n && s.charAt(i) == '-') ++i;
    if (i >= n || !digit(s.charAt(i))) return false;
    if (s.charAt(i) == '0') ++i;
    else while (i < n && digit(s.charAt(i))) ++i;
    if (i < n && s.charAt(i) == '.') {
      ++i;
      if (i >= n || !digit(s.charAt(i))) return false;
      while (i < n && digit(s.charAt(i))) ++i;
    }
    if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
      ++i;
      if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) ++i;
      if (i >= n || !digit(s.charAt(i))) return false;
      while (i < n && digit(s.charAt(i))) ++i;
    }
    return i == n;
  }

  // ---- numbers ------------------------------------------------------------------------------

  private static Scalar string(String text) {
    return new Scalar(Kind.STRING, KeyKind.STRING, 0, text, false);
  }

  private static Scalar integer(Kind kind, long bits) {
    return new Scalar(kind, KeyKind.INT, bits, null, false);
  }

  private static Scalar negativeZero(KeyKind key) {
    return new Scalar(Kind.F64, key, NEGATIVE_ZERO, null, false);
  }

  private static String stripLeadingZeros(String digits) {
    int z = 0;
    while (z + 1 < digits.length() && digits.charAt(z) == '0') ++z;
    return digits.substring(z);
  }

  /** An integer from its decimal magnitude digits and sign. */
  private static Scalar decimalInteger(String token, String digits, boolean negative) {
    String mag = stripLeadingZeros(digits);
    if (mag.equals("0")) return negative ? negativeZero(KeyKind.INT) : integer(Kind.I64, 0);
    if (mag.length() <= 20) {
      BigInteger v = new BigInteger(mag);
      if (v.bitLength() <= 64) {
        long bits = v.longValue();
        if (!negative && v.bitLength() <= 63) return integer(Kind.I64, bits);
        if (!negative) return integer(Kind.U64, bits);
        if (v.bitLength() <= 63 || v.equals(BigInteger.ONE.shiftLeft(63)))
          return integer(Kind.I64, -bits);
      }
    }
    return new Scalar(Kind.DECIMAL, KeyKind.INT, 0, negative ? "-" + mag : mag, false);
  }

  /** A radix integer (2, 8, 16): digits without prefix or separators. */
  private static Scalar radixInteger(String digits, int base, boolean negative) {
    String mag = stripLeadingZeros(digits);
    if (mag.length() > MAX_RADIX_DIGITS) throw limit("integer digits");
    BigInteger v = new BigInteger(mag, base);
    if (v.signum() == 0) return negative ? negativeZero(KeyKind.INT) : integer(Kind.I64, 0);
    if (v.bitLength() <= 64) {
      long bits = v.longValue();
      if (!negative && v.bitLength() <= 63) return integer(Kind.I64, bits);
      if (!negative) return integer(Kind.U64, bits);
      if (v.bitLength() <= 63 || v.equals(BigInteger.ONE.shiftLeft(63)))
        return integer(Kind.I64, -bits);
    }
    String text = v.toString();
    return new Scalar(Kind.DECIMAL, KeyKind.INT, 0, negative ? "-" + text : text, false);
  }

  /** JSON-grammar text of a core-grammar float token. */
  private static String jsonFloatText(String token) {
    if (rfc8259(token)) return token;
    StringBuilder out = new StringBuilder(token.length() + 3);
    int i = 0, n = token.length();
    if (token.charAt(i) == '+') ++i;
    else if (token.charAt(i) == '-') out.append(token.charAt(i++));
    int start = i;
    while (i < n && digit(token.charAt(i))) ++i;
    out.append(start == i ? "0" : stripLeadingZeros(token.substring(start, i)));
    if (i < n && token.charAt(i) == '.') {
      ++i;
      int f = i;
      while (i < n && digit(token.charAt(i))) ++i;
      out.append('.');
      if (i == f) out.append('0');
      else out.append(token, f, i);
    }
    if (i < n) out.append(token, i, n);
    return out.toString();
  }

  /** A float token in core grammar: F64, DECIMAL on overflow, signed zero on underflow. */
  private static Scalar floatToken(String token) {
    String body = token.charAt(0) == '+' ? token.substring(1) : token;
    double d = Double.parseDouble(body);
    if (Double.isFinite(d))
      return new Scalar(Kind.F64, KeyKind.FLOAT, Double.doubleToRawLongBits(d), null, false);
    return new Scalar(Kind.DECIMAL, KeyKind.FLOAT, 0, jsonFloatText(token), false);
  }

  private static Scalar specialFloatScalar(int which) {
    double d =
        which == 2 ? Double.NaN : which < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    return new Scalar(Kind.F64, KeyKind.FLOAT, Double.doubleToRawLongBits(d), null, false);
  }

  private static @Nullable Scalar coreInt(String s) {
    if (s.length() > 2 && s.charAt(0) == '0' && s.charAt(1) == 'o' && all(s, 2, ScalarResolver::octal))
      return radixInteger(s.substring(2), 8, false);
    if (s.length() > 2 && s.charAt(0) == '0' && s.charAt(1) == 'x' && all(s, 2, ScalarResolver::hex))
      return radixInteger(s.substring(2), 16, false);
    boolean negative = !s.isEmpty() && s.charAt(0) == '-';
    int sign = !s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
    if (all(s, sign, ScalarResolver::digit)) return decimalInteger(s, s.substring(sign), negative);
    return null;
  }

  private static @Nullable Scalar coreFloatValue(String s) {
    int special = specialFloat(s);
    if (special != 0) return specialFloatScalar(special);
    if (coreFloat(s)) return floatToken(s);
    return null;
  }

  // ---- YAML 1.1 -----------------------------------------------------------------------------

  private static boolean digitOrUnderscore(char c) {
    return digit(c) || c == '_';
  }

  private static boolean hasDigit(String s, int from) {
    for (int i = from; i < s.length(); i++) if (s.charAt(i) != '_') return true;
    return false;
  }

  private static String strip(String s) {
    return s.indexOf('_') < 0 ? s : s.replace("_", "");
  }

  /** Sexagesimal {@code [0-9]+(:[0-5]?[0-9])+} with optional fraction; null when no match. */
  private static long @Nullable [] sexagesimal(String s, String[] fraction) {
    int colon = s.indexOf(':');
    if (colon <= 0) return null;
    if (!all(s.substring(0, colon), 0, ScalarResolver::digit)) return null;
    long v = 0;
    for (int i = 0; i < colon; i++) {
      if (Long.compareUnsigned(v, Long.divideUnsigned(-1L - 9, 10)) > 0)
        throw limit("sexagesimal integer");
      v = v * 10 + (s.charAt(i) - '0');
    }
    int i = colon;
    fraction[0] = "";
    while (i < s.length() && s.charAt(i) == ':') {
      ++i;
      int start = i;
      while (i < s.length() && digit(s.charAt(i)) && i - start < 2) ++i;
      String part = s.substring(start, i);
      if (part.isEmpty()) return null;
      if (part.length() == 2 && part.charAt(0) > '5') return null;
      int p = Integer.parseInt(part);
      if (Long.compareUnsigned(v, Long.divideUnsigned(-1L - p, 60)) > 0)
        throw limit("sexagesimal integer");
      v = v * 60 + p;
    }
    if (i < s.length()) {
      if (s.charAt(i) != '.') return null;
      fraction[0] = s.substring(i);
      for (int k = 1; k < fraction[0].length(); k++)
        if (!digit(fraction[0].charAt(k))) return null;
    }
    return new long[] {v};
  }

  private static Scalar signedInteger(long v, boolean negative) {
    if (!negative && v >= 0) return integer(Kind.I64, v);
    if (!negative) return integer(Kind.U64, v);
    if (v == 0) return negativeZero(KeyKind.INT);
    if (v > 0 || v == Long.MIN_VALUE) return integer(Kind.I64, -v);
    throw limit("sexagesimal integer");
  }

  private static @Nullable Scalar int11(String s) {
    boolean negative = !s.isEmpty() && s.charAt(0) == '-';
    int sign = !s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
    String body = s.substring(sign);
    if (body.length() > 2 && body.charAt(0) == '0' && body.charAt(1) == 'b'
        && all(body, 2, c -> c == '0' || c == '1' || c == '_') && hasDigit(body, 2))
      return radixInteger(strip(body.substring(2)), 2, negative);
    if (body.length() > 2 && body.charAt(0) == '0' && body.charAt(1) == 'x'
        && all(body, 2, c -> hex(c) || c == '_') && hasDigit(body, 2))
      return radixInteger(strip(body.substring(2)), 16, negative);
    if (body.length() > 1 && body.charAt(0) == '0'
        && all(body, 1, c -> octal(c) || c == '_') && hasDigit(body, 1))
      return radixInteger(strip(body.substring(1)), 8, negative);
    if (body.equals("0")) return decimalInteger(s, body, negative);
    if (!body.isEmpty() && body.charAt(0) >= '1' && body.charAt(0) <= '9') {
      if (all(body, 0, ScalarResolver::digitOrUnderscore))
        return decimalInteger(s, strip(body), negative);
      String plain = strip(body);
      String[] fraction = new String[1];
      long[] v;
      if (plain.indexOf('.') < 0 && (v = sexagesimal(plain, fraction)) != null)
        return signedInteger(v[0], negative);
    }
    return null;
  }

  private static Scalar asFloat(Scalar r) {
    if (r.kind == Kind.I64 || r.kind == Kind.U64) {
      double d = r.kind == Kind.I64 ? (double) r.bits : unsignedToDouble(r.bits);
      return new Scalar(Kind.F64, KeyKind.FLOAT, Double.doubleToRawLongBits(d), null, false);
    }
    return new Scalar(r.kind, KeyKind.FLOAT, r.bits, r.text, r.timestamp);
  }

  private static double unsignedToDouble(long v) {
    // Same rounding as a C++ uint64 -> double conversion (round to nearest).
    return new BigDecimal(new BigInteger(Long.toUnsignedString(v))).doubleValue();
  }

  private static @Nullable Scalar float11(String s) {
    int special = specialFloat(s);
    if (special != 0) return specialFloatScalar(special);
    int sign = !s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
    String body = s.substring(sign);
    if (!body.isEmpty() && digit(body.charAt(0)) && body.indexOf(':') >= 0) {
      String plain = strip(body);
      String[] fraction = new String[1];
      long[] v = sexagesimal(plain, fraction);
      if (v != null && !fraction[0].isEmpty()) {
        double d = unsignedToDouble(v[0]);
        if (fraction[0].length() > 1) d += Double.parseDouble("0" + fraction[0]);
        if (s.charAt(0) == '-') d = -d;
        return new Scalar(Kind.F64, KeyKind.FLOAT, Double.doubleToRawLongBits(d), null, false);
      }
      return null;
    }
    int i = 0, n = body.length();
    boolean digits = false;
    if (i < n && digit(body.charAt(i)))
      while (i < n && digitOrUnderscore(body.charAt(i))) digits |= body.charAt(i++) != '_';
    if (i >= n || body.charAt(i) != '.') return null;
    ++i;
    while (i < n && digitOrUnderscore(body.charAt(i))) digits |= body.charAt(i++) != '_';
    if (i < n && (body.charAt(i) == 'e' || body.charAt(i) == 'E')) {
      ++i;
      if (i >= n || (body.charAt(i) != '+' && body.charAt(i) != '-')) return null;
      ++i;
      int start = i;
      while (i < n && digit(body.charAt(i))) ++i;
      if (i == start) return null;
    }
    if (i != n || !digits) return null;
    return floatToken(strip(s));
  }

  /** YAML 1.1 timestamp grammar (native valid_timestamp). */
  static boolean validTimestamp(String s) {
    int[] at = {0};
    int n = s.length();
    if (!digits(s, at, 4, 4) || !ch(s, at, '-')) return false;
    int month = at[0];
    if (!digits(s, at, 1, 2)) return false;
    boolean shortMonth = at[0] - month == 1;
    if (!ch(s, at, '-')) return false;
    int day = at[0];
    if (!digits(s, at, 1, 2)) return false;
    boolean shortDay = at[0] - day == 1;
    if (at[0] == n) return !shortMonth && !shortDay;
    char c = s.charAt(at[0]);
    if (c == 'T' || c == 't') at[0]++;
    else if (c == ' ' || c == '\t') {
      while (at[0] < n && (s.charAt(at[0]) == ' ' || s.charAt(at[0]) == '\t')) at[0]++;
    } else return false;
    if (!digits(s, at, 1, 2) || !ch(s, at, ':') || !digits(s, at, 2, 2) || !ch(s, at, ':')
        || !digits(s, at, 2, 2)) return false;
    if (ch(s, at, '.')) while (at[0] < n && digit(s.charAt(at[0]))) at[0]++;
    while (at[0] < n && (s.charAt(at[0]) == ' ' || s.charAt(at[0]) == '\t')) at[0]++;
    if (at[0] == n) return true;
    if (ch(s, at, 'Z')) return at[0] == n;
    if (s.charAt(at[0]) != '+' && s.charAt(at[0]) != '-') return false;
    at[0]++;
    if (!digits(s, at, 1, 2)) return false;
    if (ch(s, at, ':') && !digits(s, at, 2, 2)) return false;
    return at[0] == n;
  }

  private static boolean digits(String s, int[] at, int lo, int hi) {
    int k = 0;
    while (at[0] < s.length() && k < hi && digit(s.charAt(at[0]))) {
      at[0]++;
      k++;
    }
    return k >= lo;
  }

  private static boolean ch(String s, int[] at, char c) {
    if (at[0] < s.length() && s.charAt(at[0]) == c) {
      at[0]++;
      return true;
    }
    return false;
  }
}
