package dev.elide.truffledata.yaml;

/** Resolved scalar kind before a mapping key was converted to tape text. */
public enum KeyKind {
  STRING,
  NULL,
  BOOL,
  INT,
  FLOAT
}
