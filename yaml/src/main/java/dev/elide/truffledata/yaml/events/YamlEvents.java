package dev.elide.truffledata.yaml.events;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Receiver of a {@link YamlEventSource}'s events. Every node event carries its anchor name (without
 * {@code &}) and resolved tag URI, or null, and the position where its own token starts: for a
 * scalar the first byte of its token (the quote, or the {@code |}/{@code >} indicator of a block
 * scalar), not of the properties before it. {@code end} is the byte offset just past a scalar's
 * token (after the closing quote; past the last content line of a block scalar, before trailing
 * blank lines and comments). An empty scalar (a missing value) has no token: {@code empty} is true
 * and both marks name the position it was inferred at.
 */
public interface YamlEvents {
  default void streamStart() {}

  default void streamEnd() {}

  /**
   * A document begins. {@code version} is {@code major << 16 | minor} from a {@code %YAML}
   * directive, or 0; {@code tags} maps each {@code %TAG} handle of this document to its prefix.
   */
  void documentStart(boolean explicit, int version, Map<String, String> tags, YamlMark mark);

  void documentEnd(boolean explicit, YamlMark mark);

  void mappingStart(@Nullable String anchor, @Nullable String tag, boolean flow, YamlMark mark);

  void mappingEnd(YamlMark mark);

  void sequenceStart(@Nullable String anchor, @Nullable String tag, boolean flow, YamlMark mark);

  void sequenceEnd(YamlMark mark);

  void scalar(
      String value,
      YamlScalarStyle style,
      boolean empty,
      @Nullable String anchor,
      @Nullable String tag,
      YamlMark start,
      int end);

  /** An alias {@code *name} ({@code name} without the {@code *}). */
  void alias(String name, YamlMark mark);
}
