# Owning slab verifier: one load per slab word — `954656d` (2026-11-08/09)

Plan `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` P0.1 follow-up (a contract-preserving
verifier implementation change); rulings in the ledger under "P0.1/WS2 (protobuf decode worker)".
Evidence for the mechanism: `../protobuf-decode-ablation-f19af5b/` (the verifier is 600.8 of
972.1 ns/row of the full decode cell; every slab word it reads is an out-of-line
`CheckedByteArraySupport.getInt` call on SVM).

**Change.** `SlabVerifier.owned` reads each record's presence word once per 32 ordinals instead
of once per ordinal, and `ownedValue` reads a string header's length word once instead of up to
five times (length, external, ASCII, header size, UTF-16 length, payload offset). The checks, their
order, their exceptions and the verifier's result bits are unchanged; no format, ABI, API or
verifier-contract change.

**Mechanisms tried and not kept** (SVM CE does not inline any load helper with a bounds check):
verifier-local `VarHandle` loads (52-instruction out-of-line body, still a call per word) and
branch-free byte-composed loads (71 instructions, still a call) — both confirmed by llvm-objdump on
the profile image, neither measured; and `079b186`, which also wrote `span()` and the bitmap
`claim()` out at the string step: same-worker screen verifier stage 505.2 ns vs 484.6 ns for
`954656d` (`screen-079b186-rejected/`, invocation `f3e6e901`), no gain, dropped.

## Same-worker screen (ablation probes, `screen-954656d/`, invocation `b315fc81`)

Verifier stage (`probe-verify` − `probe-trim`): 600.8 → **484.6 ns/row** full (−19%), 1,800.0 →
1,500.1 holdout. Production decode (`probe-plan`) 883.9 (879.7–884.9) full, 3,170.7 holdout,
against 972.1 / 3,479.0 in the P0.1 run (different worker: cross-run, screen only).

## Paired SVM measurement (`svm/`, invocation `31a50094`)

`//benchmarks/comparison:consumer_measure`, ten shards, three fresh forks per cell, x86-64.
Baseline: preserved before-image `81f5b58b…` (`bf06974`, `build/before-bf06974/`), engines
`stock,generated-owned,slab,slab-session`. Candidate: `9410bf02…` (`954656d` merged with the moved
base `b68fb45`; the merge changes no file in the consumer image's source closure, see the ledger).
ns/row, median of fork medians (fork range):

| Cell | Before | After | Change |
|---|---:|---:|---:|
| Full slab decode | 1,006.121 (1,003.601–1,016.121) | 919.867 (917.235–930.279) | **−8.6%, disjoint** |
| Large slab decode | 292,658 (290,485–293,987) | 266,546 (260,994–285,238) | −8.9%, disjoint |
| Large slab resident | 365,127 (349,871–365,320) | 314,244 (309,164–321,845) | −13.9%, disjoint |
| Full slab detach | 1,295.440 (1,282.357–2,766.201) | 1,214.096 (1,207.821–1,218.981) | −6.3%, disjoint |
| Full slab heap | 1,299.217 (1,287.231–1,349.938) | 1,258.340 (1,248.101–1,273.095) | −3.1%, disjoint |
| Sparse slab decode | 1,006.929 (1,006.806–1,013.130) | 942.011 (917.263–1,025.810) | −6.4%, overlapping |
| Holdout slab resident | 3,230.226 (3,228.351–3,262.775) | 3,168.074 (3,068.618–3,182.057) | −1.9%, disjoint |
| Full slab resident | 1,272.053 (1,271.332–1,327.179) | 1,337.997 (1,212.570–18,104.540) | +5.2%, overlapping (one 18 µs fork) |
| Controls: full decode stock / generated-owned | 802.958 / 839.900 | 795.964 / 839.126 | −0.9% / −0.1%, overlapping |
| Full slab-session decode (no owning verifier) | 792.651 | 801.638 | +1.1%, disjoint |

Full slab decode is now 1.16× stock (was 1.25×). Read cells (peak, interpreter) move within their
ranges; disjoint ±1–3% movements also appear in cells the change cannot reach (slab-session
decode +1.1%, slab first access +2.5%, stock heap +2.3%), which is this matrix's noise floor.
Java allocation is identical in every cell (808 B/row full decode, 48 B owner unchanged).

**Verdict against the brief:** a measured SVM improvement with separated forks, but −8.6% on the
full decode cell is below the brief's ≥10% bar for contract-preserving decode changes. It is
committed on the worker branch for the lead's admission decision, not claimed as admitted.

## Gates (same invocation `31a50094`, all pass)

`//protobuf-runtime:test` (goldens, verifier/external-slab tests), `//protobuf-runtime:slab_replay`
(fuzz corpus), `//protobuf-buffa/tests:jvm`, `:jvm_non_strict`, `:jvm_no_native_access`,
`:slab_producers` (differential/failure-injection producers), `//protobuf-buffa/native:test`
(`small_entry_allocates_nothing`), `//tests:test`, `//tests:allocation`,
`//benchmarks/comparison:consumer_smoke` (132 cells, JVM) and `:consumer_native_smoke` (132
cells, SVM), `:native_smoke` (compact-scalar ingest 168.0 / typed 136.0 B per message, at the
floors). No AOT budget symbol is touched (`consumer_aot_budgets.json` pins no `SlabVerifier`
body), so no re-pin.
