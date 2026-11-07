package dev.elide.truffledata.tape;

/**
 * Receiver for a single-pass export of a tape subtree (a JSON or YAML document) in source order. Containers announce their
 * child count up front so a sink can presize; every {@link #beginObject} or {@link #beginArray} is
 * matched by one {@link #end}. Inside an object each member is a {@link #key} followed by exactly
 * one value event.
 *
 * <p>String, key and decimal payloads arrive through one reusable {@link TapeText} cursor per
 * export, so the sink chooses how to materialize them ({@code String}, {@code TruffleString},
 * {@code BigDecimal}, raw bytes) and an export allocates nothing per string. The cursor is valid
 * only during the event that passes it; {@link TapeText#view()} gives a view that lives as long as
 * the owner. The owner cannot be closed while an export is running.
 *
 * <p>YAML documents add three events that JSON never emits, all with no-op defaults: {@link #tag}
 * and {@link #anchor} precede the node they apply to, and {@link #alias} stands for a reference to
 * an earlier anchored node.
 */
public interface TapeSink {
  void beginObject(long members);

  void key(TapeText key);

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
   * remains available through {@link TapeValue#numberText}. {@link TapeText#toJavaString()} of a
   * decimal is its exact source token.
   */
  void decimalValue(TapeText token);

  void stringValue(TapeText text);

  /**
   * Whether a {@link TapeSession} parse into this sink should also write the document's text once,
   * natively, into a per-document array that {@link TapeText#toTruffleString} shares instead of
   * copying (performance plan Task D2). For sinks that build a {@code TruffleString} per string;
   * default false.
   */
  default boolean sharedText() {
    return false;
  }

  /** The resolved tag URI (for example {@code tag:yaml.org,2002:binary}) of the next node. */
  default void tag(TapeText uri) {}

  /** The next node is anchor {@code id}; ids number a document's anchor definitions densely. */
  default void anchor(int id) {}

  /**
   * A reference to anchor {@code id}. Return true when the sink reused the object it built for
   * {@code anchor(id)} during this export (O(1)); false (the default) makes the exporter expand the
   * target, charged against the format's expansion budget.
   */
  default boolean alias(int id) {
    return false;
  }
}
