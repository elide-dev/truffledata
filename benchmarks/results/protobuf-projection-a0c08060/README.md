# Initial projected resident qualification

Invocation: <https://app.buildbuddy.io/invocation/a0c08060-b429-4a00-829e-4d2b4c726df6>.
Candidate `53bf978`, GraalVM `25.5.5-elide.27`, Myna `0.13.0`. Each runtime's target runs
all selected groups on one worker, with three fresh processes per cell and randomized order.
Both time and allocation records, fingerprints, checksums, tier proofs, input/slab capacities
and every fork are retained in `jvm/` and `svm/`. Ratios use the same-run full slab reference;
this is the E1 experiment, not the separate historical G1 comparison.

| Required comparison | Ceiling | JVM | SVM |
|---|---:|---:|---:|
| Selected small / full sparse resident | 0.85× | 1.201× (miss) | 0.673× (pass) |
| Selected holdout / full holdout resident | 0.50× | 0.553× (miss) | 0.343× (pass) |
| Lazy holdout / full holdout resident | 0.50× | 0.902× (miss) | 0.503× (miss) |

The additional session-lazy comparison is informational, at 0.588× JVM / 0.556× SVM. It
was not promoted into a new release threshold. Both formal E1 targets fail this run, and E1
remains open. Selected JVM cells use the native-free table producer, whereas full JVM cells
use the contract's generated producer. The native full/lazy cells share the same native
producer. Retaining the lazily readable input and validating the externally copied native
slab are included in the measured operation.

The next concrete changes remove an external structural verification pass from Java's own
projected output (its producer and logical budget checks remain active) and avoid constructing
a temporary borrow wrapper when a native projected plan immediately makes an owning copy.
These results precede both changes; they must not be described as passing or replaced by a
favorable single rerun. External slab import remains fully verified.
