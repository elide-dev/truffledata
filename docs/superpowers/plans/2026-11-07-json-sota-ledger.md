# Implementation ledger — JSON producer plan

Plan: `2026-11-07-json-sota.md`. Work continues in the shared followup worktree after the compact-reader cutover.

Implementation defaults selected under the user’s authorization to proceed directly through the followup work (2026-11-07): use simdjson 5.1.1’s public parser implementation member and structural index surface, with the differential oracle and pinned version; build directories when retaining a document that omitted them; keep the baseline ISA and adopt dispatched wider tiers only when their measured gates pass; move typed plans only on the specified ≥1.1× paired measurement; use 32 keys × 32 bytes at 32 depths for shape templates. These are implementation selections, not verbatim user rulings. Any simdjson pin change requalifies the structural and number-parser contracts.

Task 1 implementation: a session owns one key-shape cache; one-shot documents use a temporary stack cache and do not retain templates with the document. A hit checks every decoded key and the final count, then writes the directory using the checked template’s rank permutation. Misses materialize prior hashes before sorting and duplicate checks. The null writer uses the same templates and keeps unread scratch slots on a hit. Native `member_key` and `end_rescan` remain available to the YAML rewrite producer. Clean JSON keys use bounded-by-padding SSE2/NEON comparisons; other callers explicitly take the bounded comparison.

Task 1 evidence: `shape_cache_rejects_duplicates` tests insertions at every position for 2–32 keys, changed equal-count sequences, shorter prefixes, long keys and depths31/32. `permuted_directories_match_sorted` changes value widths and member order. Session retained and validate-only tests warm templates, change escape spellings, inject duplicates and reuse the cache after rejection. Native api/stream/decoder/compact-tape tests pass in [a7505c96](https://app.buildbuddy.io/invocation/a7505c96-3cd6-4664-9f55-7e54ac622409).

Task 1 performance: RBE same-process retained ratios1.068×/1.064× and validate ratios1.176×/1.168× on big/huge pass the specified1.05×/1.15× gates. Results and probe source: `benchmarks/results/json-sota-shape-followup/`. Cache storage is43,144B, correcting the plan’s12KB estimate; its key bytes alone require32KiB. Sanitizer and full-repository gates remain pending, and the Java compact-reader graph gate from the predecessor plan remains open.

Task 2 begun: stage1.h pins the exact simdjson5.1.1 structural-index contract, not yet wired into production. No stage-2 result is claimed.
