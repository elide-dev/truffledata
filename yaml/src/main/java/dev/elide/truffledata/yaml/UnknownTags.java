package dev.elide.truffledata.yaml;

import dev.elide.truffledata.yaml.generated.YamlConstants;

/** YAML decode policy. */
public enum UnknownTags {
  PRESERVE(YamlConstants.UnknownTags.PRESERVE),
  REJECT(YamlConstants.UnknownTags.REJECT);

  /** The native code of this policy: yaml.seam {@code enum UnknownTags}. */
  final int code;

  UnknownTags(int code) {
    this.code = code;
  }
}
