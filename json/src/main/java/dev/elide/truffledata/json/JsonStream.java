package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.tape.TapeStream;

/**
 * Whitespace-separated JSON documents (NDJSON, JSON Lines, concatenated JSON) decoded one at a time
 * through a {@link JsonSession} (performance plan Task F2; json/native/stream.h). Open one with
 * {@link JsonSession#stream(JsonStreamOptions)}; it is confined to the session's thread and closed
 * with the session. The feed/finish/next/retain contract is the shared {@link TapeStream}'s; each
 * {@code feed} offers at most {@code 2 × maxDocumentBytes}.
 *
 * <p>Failures: an invalid document throws {@link IllegalArgumentException} from {@code next}, and
 * the stream stays failed until {@link #skip()} resumes after that document (only document-level
 * errors can be skipped; invalid UTF-8 or raw control bytes fail a whole simdjson batch). A finished
 * stream that ends inside a document throws {@link IllegalArgumentException} ("truncated"), with the
 * incomplete bytes in {@link #truncatedBytes()}.
 */
public final class JsonStream extends TapeStream<JsonMessage> {
  private final JsonStreamOptions options;

  JsonStream(JsonSession session, long handle, JsonStreamOptions options) {
    // The carry-over buffer never holds more than twice the largest document.
    super(session, handle, 2 * options.maxDocumentBytes());
    this.options = options;
  }

  private JsonSession.Backend json() {
    return (JsonSession.Backend) backend();
  }

  /**
   * Resumes a stream that failed on an invalid document after that document. Returns false when the
   * stream has not failed or its failure cannot be skipped (invalid UTF-8 or raw control bytes,
   * truncation, allocation failure, an incomplete document above {@code maxDocumentBytes}).
   */
  @TruffleBoundary
  public boolean skip() {
    return json().streamSkip(handle()) == 0;
  }

  /**
   * Buffered bytes past the last decoded document; after a truncation failure, the incomplete final
   * document.
   */
  @TruffleBoundary
  public long truncatedBytes() {
    return json().streamTruncatedBytes(handle());
  }

  /** The options this stream was opened with. */
  public JsonStreamOptions options() {
    return options;
  }
}
