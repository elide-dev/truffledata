package dev.elide.truffledata.yaml;

import dev.elide.truffledata.yaml.generated.YamlConstants;

/** YAML decode policy. */
public enum DuplicateKeys {
  REJECT(YamlConstants.DuplicateKeys.REJECT),
  LAST_WINS(YamlConstants.DuplicateKeys.LAST_WINS);

  /** The native code of this policy: yaml.seam {@code enum DuplicateKeys}. */
  final int code;

  DuplicateKeys(int code) {
    this.code = code;
  }
}
