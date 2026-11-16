package dev.elide.truffledata.yaml;

import dev.elide.truffledata.yaml.generated.YamlConstants;

/** YAML decode policy. */
public enum VersionDirective {
  IGNORE(YamlConstants.VersionDirective.IGNORE),
  HONOR(YamlConstants.VersionDirective.HONOR);

  /** The native code of this policy: yaml.seam {@code enum VersionDirective}. */
  final int code;

  VersionDirective(int code) {
    this.code = code;
  }
}
