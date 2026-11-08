# Compact YAML qualification measurements

These samples track work leading to and following checkpoint `799c465`. They are
qualification evidence, not a claim that the performance work is complete. The
mandatory records-block ratio is **0.715 × rapidyaml's integer-handler scan in the
same run**. The 0.80 ratio is a hypothesis. The Linux samples below miss the floor.

The native harness parses the same bytes through every engine, copies the mutable
input for each decode, and reports the best of seven rounds per cell over three
passes. Retained decode releases its result each iteration. Throughput comparisons
are meaningful within a run; different executors and local host activity change
absolute timings. None of the local measurements override the pinned RBE gate.

| Sample | Environment and source variant | Result |
| --- | --- | --- |
| `rbe-announced-keys.txt` | RBE x86-64, invocation `4bb84dea-d72c-4dda-84c5-43f6c45f1910`; strict key announcements and fused duplicate detection, before scalar header cleanup | 0.590 × scan; pass ratios 0.588–0.593 |
| `rbe-single-header-write.txt` | RBE x86-64, invocation prefix `78b45781`; scalar source spans emitted once by the writer | 0.603 × scan; pass ratios 0.603–0.606 |
| `rbe-sparse-metadata.txt` | RBE x86-64, `e8eea551-968c-49ab-beec-114d475484ce`; strict metadata reservation zeroed once, only nonzero metadata stored | 0.616 × scan; pass ratios 0.616–0.622; floor still missed |
| `rbe-duplicate-fusion.txt` | Same-process alternating incremental and fused duplicate handling, five passes, invocation `4bb84dea…` | Best 1,338,440 → 1,294,013 ns, a 3.43% improvement |
| `rbe-typed.txt` | The 271,039-byte records fixture wrapped in a `rows` mapping, invocation `4bb84dea…` | Generic retained 1,513,763 ns; typed 2,050,214 ns; isolated walk 508,605 ns |
| `local-*.txt` | Exploratory macOS arm64, Homebrew clang 23.1.1, `-O2 -DNDEBUG`; shared host | Separate files identify announced-key, single-header-write and short-string variants |

Typed decode is 1.354 × generic retained decode, missing the 1.2 hypothesis. The
walk is 24.8% of typed decode, above the plan's 15% threshold: the deferred direct
handler investigation is open.

The short-string variant reuses ASCII information and input-span validation
already established by the scalar fast path. Its checksum and all 9,093 sanitizer
parity cases pass. The subsequent sparse-metadata experiment prezeros strict
mappings' metadata reservation and writes only nonzero styles/tags/anchors;
rewriting policies retain incremental metadata. Its local ratio is 0.759, but the pinned Linux result is 0.616: this
improvement does not carry across architectures. Native API, semantic and 9,093
parity cases pass in that same Linux invocation; the nonexistent conformance
target supplied to that invocation was skipped and is not a passing gate.

## Profile

Invocation `6b2e3ee6-6bd8-448d-8014-551a4b98a0f6` uses the announced-key variant
before the scalar-header cleanup, five CPU seconds per engine and fixture, with
`--copt=-gline-tables-only`. Each cell contains 1,249 samples. Records-block ran
3,461 TruffleData decodes and 6,383 integer-handler scans.

`profiles/*.gz` contains raw load-relative stack addresses; the folded records
stacks and summary were symbolized against the exact downloaded executable with
LLVM's symbolizer. These addresses require that executable. The source-tree
`yaml/native/sampler.h` and the scripts in
`yaml-decoder-20e3d44/profiles/` describe the sampling and categorization method.

Compared with the earlier `20e3d44` profile, samples per 1,000 decodes are:

| Category | Earlier Myna producer | Compact producer |
| --- | ---: | ---: |
| rapidyaml engine and remainder | 216 | 215 |
| handler dispatch, frames and collections | 44 | 60 |
| metadata and array operations | 10 | 24 |
| tape writes | 9 | 22 |

The engine cost is stable; the regression is in our per-node work. The profile is
shorter than the original 40-second sample and is suitable for identifying the
large categories, not for ranking small changes by a few samples.
