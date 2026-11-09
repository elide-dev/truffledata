package dev.elide.truffledata.yaml.events;

/**
 * A source position: {@code offset} is a byte offset into the UTF-8 input handed to the event
 * source; {@code line} and {@code column} are 1-based, the column counting Unicode code points.
 * Line and column are 0 when unknown.
 */
public record YamlMark(int offset, int line, int column) {
  public static final YamlMark UNKNOWN = new YamlMark(0, 0, 0);

  public YamlMark {
    if (offset < 0 || line < 0 || column < 0) throw new IllegalArgumentException("Invalid YAML mark");
  }
}
