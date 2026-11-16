package dev.elide.truffledata.yaml;

import dev.elide.truffledata.yaml.generated.YamlConstants;

/** YAML decode policy. */
public enum Aliases {
  ALLOW(YamlConstants.Aliases.ALLOW),
  REJECT(YamlConstants.Aliases.REJECT);

  /** The native code of this policy: yaml.seam {@code enum Aliases}. */
  final int code;

  Aliases(int code) {
    this.code = code;
  }
}
