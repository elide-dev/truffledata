# Projection qualification after redundant-work removal

Invocation: <https://app.buildbuddy.io/invocation/d0c3e664-9f56-466e-8460-4443a1e231e9>.
Three fresh forks per cell on one worker per runtime, after `111b9b0`. Raw complete timing,
allocation, capacity, tier and checksum records are retained alongside the gate verdicts.

| Required ratio | Ceiling | JVM | SVM |
|---|---:|---:|---:|
| Selected small / full sparse resident | 0.85× | 0.829× pass | 0.667× pass |
| Selected holdout / full holdout resident | 0.50× | 0.459× pass | 0.343× pass |
| Lazy holdout / full holdout resident | 0.50× | 0.796× miss | 0.506× miss |

The removal of Java's redundant external verifier closes both selected-field JVM gates.
The owning native wrapper change saves 40 observed Java bytes per message on this SVM build;
the lazy native ratio remains above its unchanged ceiling. Session lazy ratios remain
informational (0.565× JVM, 0.557× SVM). E1 is still open, and the complete earlier miss is
retained in `../protobuf-projection-a0c08060/`.

Next candidates are primitive external-verifier handle tracking and unboxed table-list
finalization, followed by generated Java projection support. None of those changes is included
in this run, and no favorable isolated rerun has replaced a complete result.
