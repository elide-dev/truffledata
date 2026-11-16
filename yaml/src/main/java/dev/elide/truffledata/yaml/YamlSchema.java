package dev.elide.truffledata.yaml;

import dev.elide.truffledata.yaml.generated.YamlConstants;

/** YAML decode policy. */
public enum YamlSchema {
  CORE(YamlConstants.Schema.CORE),
  JSON(YamlConstants.Schema.JSON),
  FAILSAFE(YamlConstants.Schema.FAILSAFE),
  YAML_1_1(YamlConstants.Schema.YAML_1_1);

  /** The native code of this policy: yaml.seam {@code enum Schema}. */
  final int code;

  YamlSchema(int code) {
    this.code = code;
  }
}
