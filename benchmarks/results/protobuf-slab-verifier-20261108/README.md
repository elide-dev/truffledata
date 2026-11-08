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

Source fix `7a90ea6` and fixture correction `0a22a11` were integrated as `d37d646` and
`afa0d3b`. Validation ran from the integration checkout through
`build/release-010-tools/bzl`, using the shared release output root:

- `test //protobuf-runtime:test //tests:allocation --test_output=errors`, invocation
  [9a6b979f](https://app.buildbuddy.io/invocation/9a6b979f-e48d-41aa-87e1-410e20b42fc2):
  allocation passed **13/13**. Runtime initially passed 39/41; two new test fixtures selected
  `maxCells=1`, below their schema's two-field construction minimum. No production failure
  occurred. The fixture correction raises only those lower bounds to 2.
- `test //protobuf-runtime:test --test_output=errors`, invocation
  [c61145d9](https://app.buildbuddy.io/invocation/c61145d9-ecf9-4a91-9b80-a06caaa1a4ee):
  runtime passed **41/41**, zero failures/errors. The allocation target was not repeated
  because the follow-up changed only fixtures.

Original test logs and XML, including the initial fixture failures, are stored as deterministic
`gzip` files (`mtime=0`). `artifacts.json` records original sizes and SHA-256 hashes of both
original and compressed bytes.

**Pending exact candidate CI:** native correctness and
`//benchmarks/comparison:native_smoke` (including the SVM allocation floor). Those checks are
deliberately not duplicated by this worker. No performance timing matrix is required for this
boundary fix; the existing SVM allocations have not yet been requalified for these exact sources.
