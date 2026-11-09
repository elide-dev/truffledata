package dev.elide.truffledata.yaml.events;

/**
 * A source position: {@code offset} is a byte offset into the UTF-8 input handed to the event
 * source; {@code line} and {@code column} are 1-based, the column counting Unicode code points.
 * Line and column are 0 when unknown.
 */
public final class YamlMark {
  public static final YamlMark UNKNOWN = new YamlMark(0, 0, 0);

  private final int offset, line, column;

  public YamlMark(int offset, int line, int column) {
    if (offset < 0 || line < 0 || column < 0) throw new IllegalArgumentException("Invalid YAML mark");
    this.offset = offset;
    this.line = line;
    this.column = column;
  }

  public int offset() {
    return offset;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }
}
