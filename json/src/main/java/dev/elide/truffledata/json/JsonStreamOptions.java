package dev.elide.truffledata.json;

/**
 * Options of a {@link JsonStream}: the largest document it accepts (its carry-over buffer holds at
 * most twice that) and simdjson's {@code iterate_many} batch, which must hold the largest document.
 * Documents are separated by whitespace only (NDJSON, JSON Lines, concatenated JSON).
 */
public record JsonStreamOptions(int maxDocumentBytes, int batchBytes) {
  public static final JsonStreamOptions DEFAULT = new JsonStreamOptions(1 << 20, 1 << 20);

  public JsonStreamOptions {
    if (maxDocumentBytes <= 0 || maxDocumentBytes > Integer.MAX_VALUE / 2)
      throw new IllegalArgumentException("maxDocumentBytes must be in 1.." + Integer.MAX_VALUE / 2);
    if (batchBytes < maxDocumentBytes)
      throw new IllegalArgumentException("batchBytes must be at least maxDocumentBytes");
  }
}
