package dev.elide.truffledata.yaml.events;

/**
 * A YAML parser (the YAML 1.2 event grammar) for the Java kernel. Implementations are stateless or
 * create their state per call, so one instance may serve every session; the kernel finds one
 * through {@link java.util.ServiceLoader} unless it is given one explicitly.
 *
 * <p>{@link #parse} reports one input stream in order: {@link YamlEvents#streamStart}, then per
 * document {@link YamlEvents#documentStart}, its node events and {@link YamlEvents#documentEnd},
 * and finally {@link YamlEvents#streamEnd}. Tags arrive resolved ({@code tag:yaml.org,2002:str},
 * {@code !local}, or {@code !} for the non-specific tag); scalars arrive decoded (escapes, folding
 * and chomping applied). Invalid input throws {@link YamlSyntaxException}; an exception thrown by
 * the handler propagates unchanged and ends the parse.
 */
public interface YamlEventSource {
  /**
   * Parses {@code length} bytes of {@code utf8}: validated UTF-8 without a byte order mark, in
   * which every byte offset of a {@link YamlMark} lies.
   */
  void parse(byte[] utf8, int length, YamlEvents events);
}
