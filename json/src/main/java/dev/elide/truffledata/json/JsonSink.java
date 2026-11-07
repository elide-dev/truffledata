package dev.elide.truffledata.json;

/**
 * Receiver for a single-pass export of a JSON subtree in source order. Containers announce their
 * child count up front so a sink can presize; every {@link #beginObject} or {@link #beginArray} is
 * matched by one {@link #end}. Inside an object each member is a {@link #key} followed by exactly
 * one value event.
 *
 * <p>String, key and decimal payloads arrive through one reusable {@link JsonText} cursor per
 * export, so the sink chooses how to materialize them ({@code String}, {@code TruffleString},
 * {@code BigDecimal}, raw bytes) and an export allocates nothing per string. The cursor is valid
 * only during the event that passes it; {@link JsonText#view()} gives a view that lives as long as
 * the owner. The owner cannot be closed while an export is running.
 */
public interface JsonSink {
  void beginObject(long members);

  void key(JsonText key);

  void beginArray(long elements);

  void end();

  void nullValue();

  void booleanValue(boolean value);

  /** I64 token. */
  void longValue(long value);

  /** F64 token, including {@code -0}. */
  void doubleValue(double value);

  /** U64 token above {@link Long#MAX_VALUE}; {@code bits} is the unsigned two's-complement value. */
  void unsignedValue(long bits);

  /**
   * Integer token beyond the 64-bit ranges, or a token whose magnitude overflows binary64. Tokens
   * that round to a finite binary64 arrive as {@link #doubleValue} instead; their exact spelling
   * remains available through {@link JsonValue#numberText}. {@link JsonText#toJavaString()} of a
   * decimal is its exact source token.
   */
  void decimalValue(JsonText token);

  void stringValue(JsonText text);
}
