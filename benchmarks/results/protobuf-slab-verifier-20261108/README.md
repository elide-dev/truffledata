# External slab alias verification

This release fix bounds verification work for externally supplied shared record graphs and
repeated string payloads. It does not change the accepted format or add a new graph limit.

The existing primitive owning walk remains allocation-free for ordinary small slabs. A
repeated record switches to structural memoization keyed by handle, absolute depth and
expanded projection state. Every hit still checks offset/kind identity, span and depth;
only completed structural proofs are reused. Selected-record lookup preserves the existing
last DFS occurrence's projection result. Identical strings reuse a completed UTF-8 proof
only after identity, span and projection checks.

Structure is checked completely before logical-budget or required-field errors. The
shared-graph accounting walk caches successful subtree deltas and charges every logical
occurrence. Both deltas must fit the remaining budgets before a cached subtree can be
skipped; otherwise the original traversal determines the first error. Memoization is
transient: the owner has no new fields. Owning `logicalUsage()` also uses transient
memoization so an accepted shared graph cannot re-expand during that query.

Focused tests cover a depth-28 graph occupying less than 8 KiB but representing
1,610,612,732 cells and 1,073,741,822 payload bytes; exact and one-below budgets;
shared text identity and invalid UTF-8; cell-versus-payload error ordering; a late structural
fault preceding budget errors; last DFS selection; expanded projection revisits; and lazy
headers reached at different absolute depths. The existing mutation corpus remains active.

## Validation

Pending local `//protobuf-runtime:test` and `//tests:allocation` through the serialized
release wrapper. The exact release candidate's normal CI supplies native correctness and
`//benchmarks/comparison:native_smoke` (including the SVM allocation floor); it is deliberately
not duplicated by this worker. No performance timing matrix is required for this boundary fix.
