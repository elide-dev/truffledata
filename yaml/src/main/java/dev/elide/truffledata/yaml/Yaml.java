package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.tape.TapeValue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** YAML metadata and explicit materialization helpers, including on detached snapshots. */
public final class Yaml {
  private static final Pattern TIMESTAMP =
      Pattern.compile(
          "(\\d{4})-(\\d{1,2})-(\\d{1,2})"
              + "(?:[Tt]|[ \\t]+)(\\d{1,2}):(\\d{2}):(\\d{2})"
              + "(?:\\.(\\d*))?[ \\t]*(Z|[+-]\\d{1,2}(?::\\d{2})?)?");

  private Yaml() {}

  public static @Nullable String tag(TapeValue value) {
    return value.tagUri();
  }

  public static Style style(TapeValue value) {
    return switch (value.metadataWord() & 7) {
      case 1 -> Style.SINGLE;
      case 2 -> Style.DOUBLE;
      case 3 -> Style.LITERAL;
      case 4 -> Style.FOLDED;
      case 5 -> Style.BLOCK;
      case 6 -> Style.FLOW;
      default -> Style.PLAIN;
    };
  }

  public static @Nullable String anchor(TapeValue value) {
    return value.anchorName();
  }

  public static boolean isAlias(TapeValue value) {
    return (value.metadataWord() & 16) != 0;
  }

  public static KeyKind keyKind(TapeValue object, long index) {
    return switch ((object.keyAt(index).metadataWord() >>> 5) & 7) {
      case 1 -> KeyKind.NULL;
      case 2 -> KeyKind.BOOL;
      case 3 -> KeyKind.INT;
      case 4 -> KeyKind.FLOAT;
      default -> KeyKind.STRING;
    };
  }

  /** Materializes an explicitly tagged binary scalar; native parsing has validated its base64. */
  @TruffleBoundary
  public static byte[] binary(TapeValue value) {
    requireTag(value, "binary");
    return Base64.getMimeDecoder().decode(value.stringValue());
  }

  /** Date-only values yield LocalDate; timestamps use UTC when no offset was specified. */
  @TruffleBoundary
  public static Temporal timestamp(TapeValue value) {
    requireTag(value, "timestamp");
    String text = value.stringValue();
    if (text.length() == 10) return LocalDate.parse(text);
    Matcher match = TIMESTAMP.matcher(text);
    if (!match.matches()) throw new IllegalArgumentException("Invalid YAML timestamp");
    String fraction = match.group(7);
    int nanos =
        fraction == null || fraction.isEmpty()
            ? 0
            : Integer.parseInt((fraction + "000000000").substring(0, 9));
    String zone = match.group(8);
    ZoneOffset offset = ZoneOffset.UTC;
    if (zone != null && !zone.equals("Z")) {
      String[] parts = zone.substring(1).split(":");
      int seconds =
          3600 * Integer.parseInt(parts[0])
              + (parts.length == 2 ? 60 * Integer.parseInt(parts[1]) : 0);
      offset = ZoneOffset.ofTotalSeconds(zone.charAt(0) == '-' ? -seconds : seconds);
    }
    return OffsetDateTime.of(
        LocalDateTime.of(
            number(match, 1),
            number(match, 2),
            number(match, 3),
            number(match, 4),
            number(match, 5),
            number(match, 6),
            nanos),
        offset);
  }

  private static int number(Matcher match, int group) {
    return Integer.parseInt(Objects.requireNonNull(match.group(group)));
  }

  private static void requireTag(TapeValue value, String type) {
    if (!Objects.equals(tag(value), "tag:yaml.org,2002:" + type))
      throw new IllegalArgumentException("YAML scalar is not tagged !!" + type);
  }
}
