package dev.elide.truffledata.yaml;

import java.util.Objects;

/**
 * Immutable {@link YamlWriter} options.
 *
 * <ul>
 *   <li>{@code schema}: the schema the output is read back under. Strings are quoted exactly when
 *       a plain spelling would resolve to something else under it, and non-string scalars use
 *       spellings that resolve to the same value (with a {@code !!int}/{@code !!float}/... tag when
 *       no plain spelling exists, as for every non-string under FAILSAFE and non-finite floats
 *       under JSON).
 *   <li>{@code indent}: spaces per nested block mapping level (1..9); also the indentation of
 *       sequences under a key when {@code indentSequences}, and of literal block scalar content.
 *       Entries of a sequence nested in a sequence entry align after {@code "- "}.
 *   <li>{@code flowDepth}: collections at this nesting depth or deeper (the root is depth 0) are
 *       written in flow style; {@link Integer#MAX_VALUE} is always block, 0 always flow.
 *   <li>{@code sortKeys}: mapping members in key order ({@link String#compareTo}); the writer
 *       buffers each outermost mapping until it ends.
 *   <li>{@code indentSequences}: a block sequence under a mapping key is indented by {@code indent}
 *       ({@code key:\n  - a}) rather than written at the key's column ({@code key:\n- a}).
 *   <li>{@code quote}: {@link Style#DOUBLE} or {@link Style#SINGLE}, the preferred style for strings
 *       that cannot be plain; strings single quotes cannot represent use double quotes.
 *   <li>{@code blockScalars}: multi-line strings in block context use literal style ({@code |},
 *       {@code |-}, {@code |+}, with an indentation indicator when needed).
 *   <li>{@code tildeNull}: write null as {@code ~} instead of {@code null} (not under JSON).
 *   <li>{@code explicitStart}/{@code explicitEnd}: always write {@code ---} / {@code ...}.
 *   <li>{@code preserveMetadata}: write the tags, anchors and aliases a source exports, and in
 *       {@link YamlEmitter} also scalar/collection styles and mapping key kinds; otherwise tags
 *       are dropped and aliases are expanded.
 * </ul>
 */
public record YamlEmitOptions(
    YamlSchema schema,
    int indent,
    int flowDepth,
    boolean sortKeys,
    boolean indentSequences,
    Style quote,
    boolean blockScalars,
    boolean tildeNull,
    boolean explicitStart,
    boolean explicitEnd,
    boolean preserveMetadata) {
  public static final YamlEmitOptions DEFAULT =
      new YamlEmitOptions(
          YamlSchema.CORE, 2, Integer.MAX_VALUE, false, true, Style.DOUBLE, true, false, false,
          false, false);

  public YamlEmitOptions {
    Objects.requireNonNull(schema);
    Objects.requireNonNull(quote);
    if (indent < 1 || indent > 9) throw new IllegalArgumentException("YAML indent must be 1..9");
    if (flowDepth < 0) throw new IllegalArgumentException("YAML flow depth must be >= 0");
    if (quote != Style.SINGLE && quote != Style.DOUBLE)
      throw new IllegalArgumentException("YAML quote style must be SINGLE or DOUBLE");
  }

  public YamlEmitOptions withSchema(YamlSchema value) {
    return new YamlEmitOptions(
        value, indent, flowDepth, sortKeys, indentSequences, quote, blockScalars, tildeNull,
        explicitStart, explicitEnd, preserveMetadata);
  }

  public YamlEmitOptions withIndent(int value) {
    return new YamlEmitOptions(
        schema, value, flowDepth, sortKeys, indentSequences, quote, blockScalars, tildeNull,
        explicitStart, explicitEnd, preserveMetadata);
  }

  public YamlEmitOptions withFlowDepth(int value) {
    return new YamlEmitOptions(
        schema, indent, value, sortKeys, indentSequences, quote, blockScalars, tildeNull,
        explicitStart, explicitEnd, preserveMetadata);
  }

  /** Always block style. */
  public YamlEmitOptions block() {
    return withFlowDepth(Integer.MAX_VALUE);
  }

  /** Always flow style. */
  public YamlEmitOptions flow() {
    return withFlowDepth(0);
  }

  public YamlEmitOptions withSortKeys(boolean value) {
    return new YamlEmitOptions(
        schema, indent, flowDepth, value, indentSequences, quote, blockScalars, tildeNull,
        explicitStart, explicitEnd, preserveMetadata);
  }

  public YamlEmitOptions withIndentSequences(boolean value) {
    return new YamlEmitOptions(
        schema, indent, flowDepth, sortKeys, value, quote, blockScalars, tildeNull, explicitStart,
        explicitEnd, preserveMetadata);
  }

  public YamlEmitOptions withQuote(Style value) {
    return new YamlEmitOptions(
        schema, indent, flowDepth, sortKeys, indentSequences, value, blockScalars, tildeNull,
        explicitStart, explicitEnd, preserveMetadata);
  }

  public YamlEmitOptions withBlockScalars(boolean value) {
    return new YamlEmitOptions(
        schema, indent, flowDepth, sortKeys, indentSequences, quote, value, tildeNull,
        explicitStart, explicitEnd, preserveMetadata);
  }

  public YamlEmitOptions withTildeNull(boolean value) {
    return new YamlEmitOptions(
        schema, indent, flowDepth, sortKeys, indentSequences, quote, blockScalars, value,
        explicitStart, explicitEnd, preserveMetadata);
  }

  public YamlEmitOptions withExplicitStart(boolean value) {
    return new YamlEmitOptions(
        schema, indent, flowDepth, sortKeys, indentSequences, quote, blockScalars, tildeNull,
        value, explicitEnd, preserveMetadata);
  }

  public YamlEmitOptions withExplicitEnd(boolean value) {
    return new YamlEmitOptions(
        schema, indent, flowDepth, sortKeys, indentSequences, quote, blockScalars, tildeNull,
        explicitStart, value, preserveMetadata);
  }

  public YamlEmitOptions withPreserveMetadata(boolean value) {
    return new YamlEmitOptions(
        schema, indent, flowDepth, sortKeys, indentSequences, quote, blockScalars, tildeNull,
        explicitStart, explicitEnd, value);
  }
}
