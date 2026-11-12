# TruffleData 0.12.0

This release moves to Myna 0.18.0 and adopts its ownership model in the opt-in Myna Truffle
cells of all four native formats. Default routes, flags and APIs are unchanged from 0.11.0.

**Upgrade native attachments and generated bindings together.** Myna 0.18.0 regenerates every
ABI fingerprint. Each bindings jar carries the fingerprints of both ABI classes (LP64 and
AArch64 Linux), and a consumer verifies a native library against its own platform's class, so a
0.11.0 native library is rejected by 0.12.0 bindings and vice versa. The C ABI itself (function
signatures, struct layouts, value ABI v3, binding ABI 2) is unchanged.

## Myna Truffle cells (opt-in)

The cells routes stay opt-in (`-Dtruffledata.<format>.cells=true`, default off). What changes is
what a cell knows about the native calls it makes, which matters to a Truffle language calling
the generated cells from guest code:

- **Borrows.** A byte buffer is one guest argument: the cell derives its length, lends native
  memory in place, and copies or pins heap bytes for exactly the call. Nullable buffers take
  `null`. On HotSpot a `NativeBytes` segment is passed to the downcall itself, so the FFM linker
  checks and holds its scope for the call.
- **Resources.** Native handles become guest owners with exactly-once release and thread
  confinement: JSON and YAML sessions and streams (a stream keeps its session open), compiled JSON
  and YAML plans, typed results, and Buffa compiled layouts. Creates that report a status raise
  `NativeStatusException` carrying it and leave nothing to release. Results initialized in a
  caller's struct (JSON `decode_adopted`, YAML `session_retain`, Buffa `decode`) are owners too.
- **Held borrow.** JSON `td_json_decode_adopted` keeps its input until its result is released;
  the cell now keeps a copied or pinned guest input alive until then. Before this release a guest
  call through that cell could leave the native result reading a freed copy.

The ownership lines exist only in the cells' derived seams. The production seams, bindings and
natives are untouched by them, and `//bazel:myna_contract` checks that each derived descriptor
differs from production only by those declarations. Every explicit contract they need
(`nounwind`, `nocapture`, `readonly`) was checked against the native source; the evidence is in
each `<format>/cells/ownership.seam`.

TruffleData's own routes are unchanged on Native Image: they call each cell's static `invoke`
(or, for a resource's destroy, which has no `invoke` in Myna 0.18, the raw binding), with no new
allocation. On HotSpot the CSV route lends `NativeBytes` views, and the Buffa heap entry passes
eight arguments instead of ten.

**Known limits** (filed on Myna): one struct type can have only one create, so the guest-facing
JSON `release` cell accepts only `decode_adopted` results and Buffa's `typed`/`typedReuse`
results stay plain pointers (elide-dev/myna#102); a held borrow on the FFM backend does not check
a lent segment's scope, and copied heap buffers carry no 2-byte alignment guarantee
(elide-dev/myna#120).

## Build and CI

- Myna 0.18.0 (from 0.16.0).
- Correctness CI builds use a shared remote cache (#94).
- Codecov upload failures no longer fail CI; the tests and the coverage contract remain the gates.

## Performance

No performance work is in this release, and every cells flag stays off, so the default routes run
the same code as 0.11.0 apart from regenerated bindings. The README benchmark cards are a fresh
single-host capture of the release code (`c378d6d8`) on unclemax with the 0.11.0 method; see
`docs/COMPARISON_BENCHMARKS.md`.

## Not in this release

- Maven Central publication remains deferred under
  [#28](https://github.com/elide-dev/truffledata/issues/28) (quota resets 2026-12-01).
- The rapidyaml fuzz finding recorded in the 0.11.0 receipts
  (`parse_engine.def.hpp:8310`, UBSan) is not fixed.
- No cells flag changed its default; the JSON cells route is still untimed.
