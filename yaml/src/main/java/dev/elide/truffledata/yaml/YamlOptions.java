package dev.elide.truffledata.yaml;

import java.util.Objects;

/** Immutable YAML 1.2 core options; compatibility behavior is explicit. */
public record YamlOptions(
    YamlSchema schema,
    DuplicateKeys duplicateKeys,
    boolean mergeKeys,
    Aliases aliases,
    UnknownTags unknownTags,
    VersionDirective versionDirective,
    boolean retainSource,
    YamlLimits limits) {
  public static final YamlOptions DEFAULT =
      new YamlOptions(
          YamlSchema.CORE,
          DuplicateKeys.REJECT,
          false,
          Aliases.ALLOW,
          UnknownTags.PRESERVE,
          VersionDirective.IGNORE,
          false,
          YamlLimits.DEFAULT);

  public YamlOptions {
    Objects.requireNonNull(limits);
    Objects.requireNonNull(schema);
    Objects.requireNonNull(duplicateKeys);
    Objects.requireNonNull(aliases);
    Objects.requireNonNull(unknownTags);
    Objects.requireNonNull(versionDirective);
  }

  public static YamlOptions json() {
    return new YamlOptions(
        YamlSchema.JSON,
        DuplicateKeys.REJECT,
        false,
        Aliases.REJECT,
        UnknownTags.REJECT,
        VersionDirective.IGNORE,
        false,
        YamlLimits.DEFAULT);
  }

  public static YamlOptions yaml11() {
    return new YamlOptions(
        YamlSchema.YAML_1_1,
        DuplicateKeys.REJECT,
        true,
        Aliases.ALLOW,
        UnknownTags.PRESERVE,
        VersionDirective.HONOR,
        false,
        YamlLimits.DEFAULT);
  }

  public YamlOptions withLimits(YamlLimits value) {
    return new YamlOptions(
        schema,
        duplicateKeys,
        mergeKeys,
        aliases,
        unknownTags,
        versionDirective,
        retainSource,
        value);
  }

  public YamlOptions withSchema(YamlSchema value) {
    return new YamlOptions(
        value,
        duplicateKeys,
        mergeKeys,
        aliases,
        unknownTags,
        versionDirective,
        retainSource,
        limits);
  }

  public YamlOptions withDuplicateKeys(DuplicateKeys value) {
    return new YamlOptions(
        schema, value, mergeKeys, aliases, unknownTags, versionDirective, retainSource, limits);
  }

  public YamlOptions withAliases(Aliases value) {
    return new YamlOptions(
        schema,
        duplicateKeys,
        mergeKeys,
        value,
        unknownTags,
        versionDirective,
        retainSource,
        limits);
  }

  public YamlOptions withUnknownTags(UnknownTags value) {
    return new YamlOptions(
        schema, duplicateKeys, mergeKeys, aliases, value, versionDirective, retainSource, limits);
  }

  public YamlOptions withVersionDirective(VersionDirective value) {
    return new YamlOptions(
        schema, duplicateKeys, mergeKeys, aliases, unknownTags, value, retainSource, limits);
  }

  public YamlOptions mergeKeys(boolean value) {
    return new YamlOptions(
        schema, duplicateKeys, value, aliases, unknownTags, versionDirective, retainSource, limits);
  }

  public YamlOptions retainSource(boolean value) {
    return new YamlOptions(
        schema, duplicateKeys, mergeKeys, aliases, unknownTags, versionDirective, value, limits);
  }

  public YamlOptions withMergeKeys(boolean value) {
    return mergeKeys(value);
  }

  public YamlOptions withRetainSource(boolean value) {
    return retainSource(value);
  }
}
