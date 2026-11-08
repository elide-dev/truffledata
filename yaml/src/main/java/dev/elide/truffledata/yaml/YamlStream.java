package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.TapeStream;

/** Incremental YAML document stream; retained messages outlive the stream and session. */
public final class YamlStream extends TapeStream<YamlMessage> {
  private final YamlStreamOptions options;

  YamlStream(YamlSession session, long handle, YamlStreamOptions options) {
    super(session, handle, options.maxDocumentBytes() * 2);
    this.options = options;
  }

  public YamlStreamOptions options() {
    return options;
  }
}
