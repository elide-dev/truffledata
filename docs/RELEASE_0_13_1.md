# TruffleData 0.13.1

A fixes-only release. No library, API, flag, default or native ABI is added or changed, and no
benchmark capture was taken: the README cards are those of 0.12.0 (the 0.12.0 cards and capture
receipts land here too, #98).

## Fixes

- **YAML: handler exceptions propagate unchanged** (#78, #83). An exception thrown by a handler
  while the SnakeYAML event source was driving it was reported as a YAML syntax error and the
  original exception was lost. It now reaches the caller as thrown.
- **YAML: no phantom newline on keep-chomped scalars at EOF** (#74, #79). The Java kernel appended a
  trailing newline to `|+` and `>+` scalars that end the stream.
- **YAML: unreachable point in rapidyaml** (#72, #73). The session fuzzer found 22 bytes that made
  rapidyaml v0.16.0 reach an unreachable point when annotations (anchors, tags) sat on earlier
  lines than the current token. With assertions compiled out that was undefined behaviour on
  untrusted input; it is now a parse error, through a second patch to the pinned rapidyaml
  (`bazel/patches/rapidyaml-same-line-annotations.patch`). The reproducer is in the session replay
  corpus. 0.10.0 through 0.13.0 are affected. Not yet reported upstream.
- **CSV: mapped rows can be handed on** (#76, #81). `CsvRow` values produced by a mapped batch are
  now marked scoped, so ownership transfer works and a row can no longer be stored in an owned
  container and fail after the mapping is closed.
- **JDBC: `Types.BIT` is BOOLEAN** (#77, #82). Boolean columns that a driver reports as `BIT` were
  inferred as `ANY`.
- **Tape: mismatched `begin()`/`end()` throws** (#75, #80). `TapeBuilder.end()` could spin forever
  on an unbalanced close; it now throws.

## Documentation

Corrections to BAZEL.md (Codecov upload failures), SLAB.md (seam ABI version), the CSV native README
(Commons empty-line framing) and the YAML comparison benchmark (SDK and Myna versions); the 0.13.0
release receipts (#103).

## Publication

Published on Elide Maven and GitHub. Maven Central remains deferred under
[#28](https://github.com/elide-dev/truffledata/issues/28) until the quota resets (2026-12-01).
