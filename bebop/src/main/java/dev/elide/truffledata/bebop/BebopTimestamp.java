package dev.elide.truffledata.bebop;

/** A timestamp: seconds and nanoseconds since the Unix epoch, and a zone offset in milliseconds. */
public record BebopTimestamp(long seconds, int nanos, int offsetMillis) {}
