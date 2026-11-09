package dev.elide.truffledata.yaml.events;

/**
 * Invalid YAML reported by an event source: {@code detail} is the parser's description and
 * {@code mark} where it was detected. The kernel reports it as a YAML syntax error at that line and
 * column.
 */
public final class YamlSyntaxException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final String detail;
  private final YamlMark mark;

  public YamlSyntaxException(String detail, YamlMark mark) {
    super(detail + " at line " + mark.line() + ", column " + mark.column());
    this.detail = detail;
    this.mark = mark;
  }

  public String detail() {
    return detail;
  }

  public YamlMark mark() {
    return mark;
  }
}
