package dev.elide.truffledata.yaml;

import java.util.Map;

/** Version and expanded tag directives recorded for one document. */
public record YamlDirectives(int version, Map<String, String> tags) {
  public YamlDirectives {
    tags = Map.copyOf(tags);
  }
}
