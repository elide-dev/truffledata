/**
 * The parser-neutral YAML event boundary of the Java kernel: a {@link
 * dev.elide.truffledata.yaml.events.YamlEventSource} turns one UTF-8 input into {@link
 * dev.elide.truffledata.yaml.events.YamlEvents} calls carrying resolved tags, styles, anchors and
 * byte/line/column positions. Everything above it (policies, scalar resolution, tape building,
 * sessions) depends only on this package, so a parser is replaced by implementing one interface.
 */
@org.jspecify.annotations.NullMarked
package dev.elide.truffledata.yaml.events;
