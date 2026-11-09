package dev.elide.truffledata.yaml.events;

/** Presentation of a scalar in the source; ordinals match the decoder's metadata style bits. */
public enum YamlScalarStyle {
  PLAIN,
  SINGLE_QUOTED,
  DOUBLE_QUOTED,
  LITERAL,
  FOLDED
}
