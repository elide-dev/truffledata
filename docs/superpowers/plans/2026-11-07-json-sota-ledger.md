# Implementation ledger — JSON producer plan

Plan: `2026-11-07-json-sota.md`. Work continues in the shared followup worktree after the compact-reader cutover.

Implementation defaults selected under the user’s authorization to proceed directly through the followup work (2026-11-07): use simdjson 5.1.1’s public parser implementation member and structural index surface, with the differential oracle and pinned version; build directories when retaining a document that omitted them; keep the baseline ISA and adopt dispatched wider tiers only when their measured gates pass; move typed plans only on the specified ≥1.1× paired measurement; use 32 keys × 32 bytes at 32 depths for shape templates. These are implementation selections, not verbatim user rulings. Any simdjson pin change requalifies the structural and number-parser contracts.

Task 1 implementation: a session owns one key-shape cache; one-shot documents use a temporary stack cache and do not retain templates with the document. A hit checks every decoded key and the final count, then writes the directory using the checked template’s rank permutation. Misses materialize prior hashes before sorting and duplicate checks. The null writer uses the same templates and keeps unread scratch slots on a hit. Native `member_key` and `end_rescan` remain available to the YAML rewrite producer. Clean JSON keys use bounded-by-padding SSE2/NEON comparisons; other callers explicitly take the bounded comparison.

Task 1 evidence: `shape_cache_rejects_duplicates` tests insertions at every position for 2–32 keys, changed equal-count sequences, shorter prefixes, long keys and depths31/32. `permuted_directories_match_sorted` changes value widths and member order. Session retained and validate-only tests warm templates, change escape spellings, inject duplicates and reuse the cache after rejection. Native api/stream/decoder/compact-tape tests pass in [a7505c96](https://app.buildbuddy.io/invocation/a7505c96-3cd6-4664-9f55-7e54ac622409).

Task 1 performance: RBE same-process retained ratios1.068×/1.064× and validate ratios1.176×/1.168× on big/huge pass the specified1.05×/1.15× gates. Results and probe source: `benchmarks/results/json-sota-shape-followup/`. Cache storage is43,144B, correcting the plan’s12KB estimate; its key bytes alone require32KiB. Sanitizer and full-repository gates remain pending, and the Java compact-reader graph gate from the predecessor plan remains open.

Task 2 implementation checkpoint: stage1.h pins simdjson 5.1.1 and stage2.h now drives generic one-shot and session decoding. The grammar is iterative with a 128-frame bound; clean strings reference input, escaped strings decode into the retained pool, numeric spans trim only trailing JSON whitespace, and scalar-root numbers receive the whitespace terminator required by the pinned numeric parser. Numeric callbacks can precede parser failure, so numeric words remain provisional until grammar success and roll back before exact DECIMAL fallback. Limits are charged on commit, preserving malformed-token versus value-limit status precedence. Streams and typed plans still use On-Demand. Shared grammar/null-writer helpers moved to decoder_common.h.

Task 2 storage: session arrays retain capacity after ordinary reuse, with separate validate-only scratch and a reusable text-table array. Retain detaches the published arena/input, and inputs above the retained limit discard their arrays/parser capacity before reuse. The API gate performs 10,000 alternating-size retained decodes and another 10,000 validate-only decodes after each mode's warm-up, requiring zero upstream index allocations. Structural presizing makes the dense-document peak equal retained index bytes; exact-budget and one-byte-below tests remain enforced.

Task 2 evidence: [523278e7](https://app.buildbuddy.io/invocation/523278e7-9130-44a2-8203-83f623a0a9e1) passes native API, stream, decoder, stage2 differential, JSON JVM and tape JVM (six targets); the two Java performance failures are recorded in the compact-reader ledger. Differential tests compare acceptance and exact words/pool/directories over the 200-document corpus, 60 malformed documents, added grammar/embedded-NUL cases and number-token whitespace. A further 1,000,000 deterministic generated key sequences agree with a sorted decoded-key oracle in retained and validate-only modes, including escaped spellings, duplicates and changing nested shapes; this passes RBE in [10257ff4](https://app.buildbuddy.io/invocation/10257ff4-4dbc-44ff-bb5f-c86dcbe87b46). Local Apple clang ASan+UBSan passes the initial structural corpus and million-key test. Hermetic RBE sanitizer qualification and all repository gates remain pending.

Task 2 performance is NOT accepted: the initial RBE paired probe in 10257ff4 measured retained 1.086/1.118/1.091/1.093× and validate 1.221/1.271/1.302/1.239× (small/medium/big/huge), below the required gates. Inlining hot token kernels, outlining cold shape misses and committing numeric callbacks directly are being remeasured. No throughput target or graph/allocation budget has been relaxed. Tasks 3–8 remain open.

Task 2 followup: the checked writer remains the shared YAML/On-Demand default. A separate structural specialization appends within a reservation proven by the complete index (at most two words per structural token; at most one key per two tokens). It retains the ABI word limit and every value/text/depth limit. The validate-only writer defers scratch slots for a matching template until a miss. Cached clean keys compare their copied bytes and a bounded closing quote before emitting; ASCII/clean flags share the existing key-length storage, so the cache remains43,144B. Escaped strings carry the initial scan and accumulate ASCII while decoding instead of rescanning.

Ordinary numeric shortcuts preserve typing and limit classification: signed integers with at most18 digits fit I64 (negative zero remains F64); validate-only fixed-point tokens with at most18 digits cannot overflow or underflow binary64. Every other token still uses the pinned number parser and exact DECIMAL fallback. Boundary-number, repeated escaped-key and every-prefix truncation differentials pass. Local ASan+UBSan passes the structural corpus and million-key test after reserved appends/deferred scratch, and the corpus again after the carried-string scan change.

The paired RBE result in [c8037437](https://app.buildbuddy.io/invocation/c8037437-590f-4105-b0dc-564b25845ab5) is retained1.349/1.460/1.447/1.394× and validate1.491/1.738/1.719/1.646×. Small validate and huge retained still miss their unchanged targets, so Task2 remains open. Results and harness: `benchmarks/results/json-sota-structural-followup/`. The earlier [d6a84c7b](https://app.buildbuddy.io/invocation/d6a84c7b-b2d0-415a-bfca-4b982f54b6cf) also passes the native API, compact writer and million-key gates; current corpus and Java Native Image acceptance pass in c8037437. Directories opt-in, structural streams, typed qualification, ISA and unclemax measurements remain unimplemented.

Validate-only escaped value strings now validate every escape, Unicode surrogate pair and decoded
length without copying bytes into a discarded pool. Escaped keys still materialize for duplicate
comparison, and retained strings keep their original emission. Local Apple clang ASan+UBSan
structural differentials pass; paired RBE measurement remains pending, so the small-validate and
huge-retained gaps above are still open.

Task 2 later qualification: [69bedb3d](https://app.buildbuddy.io/invocation/69bedb3d-bba4-40aa-9bc8-fe0e31d946ee) measures retained1.443/1.514/1.486/1.455× and validate1.629/1.762/1.732/1.749×, now clearing their unchanged paired throughput floors. The same invocation passes native API, structural differential, stream, compact writer, JSON/tape JVM and JSON Native Image acceptance. Full repository/sanitizer and remaining plan gates still apply; this does not close the entire producer plan.

Task 3 implementation: JSON ABI9 adds WANT_DIRECTORIES32; compact descriptor bit4 denotes no directories. Sink and stream paths omit them, direct decode requests them, and retaining a directoryless result builds them by a checked rescan before detaching storage. Rebuild failure rolls back every published directory pointer and permits retry. Validation independently checks duplicates on directoryless objects, and Java linear lookup, snapshots and Myna interchange preserve those documents. Shared writer defaults keep YAML directories enabled.

Task 3 evidence in69bedb3d: native API/stream/compact writer and Java JSON/tape tests pass. The directoryless paired speed is1.033/1.039× on big/huge, short of1.04×. Logical words+pool are1.871/1.688/1.599/1.510× input for small/medium/big/huge, above the1.5× memory requirement; physical reserved storage is separately reported and higher. Task3 remains open. A followup skips pending-key offset writes on complete directoryless shape hits, reconstructing the closed prefix on the first mismatch; local ASan+UBSan13 compact-writer groups pass, and the million-sequence oracle now also covers directoryless mode. Formal remeasurement is pending.

Evidence is in `benchmarks/results/json-sota-directories-followup/`. Native read graphs remain over budget: retained2536/2479 and snapshot2179/2123 nodes, versus2000. Export653/597, zero calls/allocations and allocation budgets pass. Postcapture exact-byte-only hint checks and ByteArraySupport heap reads reduce local JVM snapshot graphs to1919/1862; Native Image qualification is pending. Structural streams, typed qualification, oracle retirement, ISA selection and unclemax final measurements remain open.

Task 4 implementation checkpoint: streams now run stage1 once per bounded batch and the structural stage2 per document, with rebased spans into the session's own input copy. Partial/final sentinel handling preserves UTF-8 tails, root-scalar holdback, BOM rules, backpressure and skip-after-document-error. Session On-Demand decoder storage is removed and structural arrays persist across streamed documents. The prior stream/session is a test-only paired oracle. Native API/stream/stage2 tests pass in [dab2dbc5](https://app.buildbuddy.io/invocation/dab2dbc5-ae9d-4a8e-b29c-5d43be2cadc3).

Task 4's initial paired steady remote ratios1.340/1.200/1.327× miss1.45×. A subsequent four-entry structural-count hint skips the preliminary boundary scan when its index-derived extent passes the whole stage2 grammar. It attempts at most one candidate and requires existing writer/input capacities, preventing a bad extent from causing allocator growth or false memory-limit failures; all candidate state is cleared on fallback. Full local stream/API ASan+UBSan passes, including new shorter/longer/malformed-root fallback cases and existing allocation-failure coverage. Local paired median1.490× is encouraging but noisy and does not close the gate. Evidence: `benchmarks/results/json-sota-stream-followup/`.

Reader followup: class-constant confinement keeps mandatory thread/close checks and reduces remote retained graphs to2181/2124 in dab2; snapshots2089/2033 still miss2000. Subsequent immutable initial rank caches and a checked fused-owner storage identity produce local JVM confined graphs1985/1928 (separate storage) and1981/1924 (fused), with1loop,0calls,1harnessbox. The rank cache retains no owner and changed key sets take the ordinary checked miss path;61JSON+19tape local tests pass, including missing-first-key and shape changes. Native requalification remains pending. Raw evidence: `benchmarks/results/tape-reader-guard-followup/`. Task3 memory, stream qualification, typed/ISA decisions and unclemax final measurements remain open.

Task 4 remote qualification in [a0c08060](https://app.buildbuddy.io/invocation/a0c08060-b429-4a00-829e-4d2b4c726df6) passes the unchanged 1.45× paired stream gate in all five rounds: 1.516/1.518/1.502/1.511/1.504×. JSON Native Image comparison also passes all 24 compilation and nine allocation cells: retained 1,971/1,915 nodes, snapshot 1,961/1,905, one loop, no calls or allocations. The separate Native Image acceptance test exposed a generic-host confinement dispatch residual; a narrow final-check/exact-class-check split passes 19 tape + 61 JSON local tests and awaits native recapture. Raw logs are preserved in the reader-guard and stream evidence directories.

Task 3 memory incompatibility is structural, not a reservation artifact. Under the unchanged compact layout, the directoryless fixtures require 44/862/43,002/860,002 words plus 41/415/21,445/444,445 decoded pool bytes. Their logical lower bounds are therefore (8×words+pool)/input = 1.871/1.688/1.599/1.510×. Even an allocator with zero overhead cannot meet 1.5× on these exact inputs. The layout and fixtures are preserved; the criterion stays explicitly unmet pending coordinator disposition. Physical reservation reduction remains useful but cannot close that logical bound.

Task 3 physical-reservation followup reduces directoryless allocated index/input to39.010/6.321/2.713/2.179× (previously39.010/8.379/6.428/6.113×), preserving exact words/pool. The coordinator disposition is **not met (format constraint)** for the1.5× logical criterion, with unchanged layout/fixtures and the lower-bound proof above. No passing memory claim replaces it.

Task 5 adopts a bounded flat-scalar specialization: immutable schema-key positions and unique first-byte buckets avoid repeated map lookup after exact byte checks; escapes/collisions/long keys keep decoded-name lookup. All slots, scalar contracts and retained ownership are unchanged. Recursive/repeated plans continue through On-Demand. Existing typed tests pass unchanged;143,054 exact oracle cases pass under ASan+UBSan. The native unclemax scalar pair is1.758/1.708/1.772/1.643/1.630×, above1.1×. The typed ABI now explicitly distinguishes structural syntax errors from contract failures. Formal RBE and final Java pairs remain pending.

Task 6 moves the generic On-Demand visitor and its parser-pool aliases into test-only headers. Production generic documents, sessions and streams use structural stage2; the nested/repeated typed fallback remains production and is documented, so this is not full removal of simdjson On-Demand.

Task 7 measures target-attributed baseline/AVX2/AVX-512 walk instantiations on unclemax. Each passes30,280 exact/malformed comparisons. AVX2 reaches only1.007–1.013× big and1.018–1.026× huge; AVX-512 regresses to0.944–0.952× big and0.960–0.963× huge relative toAVX2. Both fail the unchanged3% adoption gate and remain test-only; production baseline is retained. RBE confirmation and Task8 complete image pairs remain pending. Evidence: `benchmarks/results/json-sota-native-qualification/`.


Task 8 completed-image evidence and pinned native qualification are preserved in `benchmarks/results/json-sota-final-qualification/`. The exact Bazel probes on unclemax pass directoryless (big/huge1.055×), streams (five rounds1.499–1.517×), and typed (1.681–1.882×). Retained small1.346×/medium1.426× pass their1.3×/1.4× gates, but big1.383×/huge1.377× miss1.4×. Validation medium1.660×/big1.610× pass1.6×; small1.486×/huge1.583× miss. Fresh S3n/S3x duplicate-price qualification remains pending. ISA parity passes but neither wider tier reaches3%, so baseline remains selected. These supersede the host-Clang diagnostic timings for qualification, without deleting that evidence.

The complete compatibility/v3 image measurements expose remaining costs: compatibility generic ingest820.176ns, JavaSink peak1182.964ns, TruffleSink ingest1902.973ns, stream interop1289.081ns, snapshot2059.874ns miss the original absolute limits. Typed ingest144.919ns and ten-field peak27.124ns improve; generic144B/library ingest, stream64.75B/library ingest, and snapshot1183.5B pass allocation goals. Full before/after cells and two baseline runs are recorded; Task8 is not marked wholly green.

Post-measurement ABI10 gives each stream document its access intent: borrowed by-key roots request directories, sink-only exports do not. A 200-document native mode-switch/invalid-flag test passes ASan+UBSan; JVM/SVM regression checks verify both nested directories after alternating sink/borrow reads. Snapshot copying now uses checked word/bulk array copies and one loaded header per range/relocation pass, preserving subtree extents and the qualified YAML-only WIDE raw-span semantics. Local DSL compilation,21tape+62JSONJVM tests and native stream/API sanitizer tests pass. Formal native acceptance/graphs and image remeasurement remain pending.


Text-arena fusion qualification candidate: stage2 writes into existing session table capacity with one bounded zero pass; incomplete table/output capacity falls back only after full grammar validation, without speculative allocations or changed error precedence. Local exact/status parity3,070 cases and API/stream/stage2/typed sanitizer gates pass. Local native paired gains are1.014–1.058× single records,1.058–1.073× medium and1.068–1.097× big (cold small0.999×); the initial per-gap-zero prototype regressed and was rejected. Pinned qualification and end-to-end timing remain open; evidence: `benchmarks/results/json-text-fusion-followup/`.

Stream access intent is now propagated by the shared Backend default overload, which calls the existing `directories` hook then the one-argument runtime method. Legacy subclasses preserve their no-op directory policy; YAML keeps its independent retainSource flag. Redundant SVM boolean overrides/exemption are removed, rather than adding exemptions to JVM-callable methods. Local63JSON+22tape tests pass, including legacy source compatibility.


Correctness blocker found during the final audit and fixed: a validate-only shape miss materialized unchanged prefix keys as views of the old template, and `key.set` zeroed that same storage before copying it. After warming `aa,bb,cc`, then `aa,bb,dd`, duplicate escaped-NUL keys could match the corrupted prefix and be accepted. The setter now memmoves source bytes before clearing only its tail. Before/after reproduction,16compact-writer ASan+UBSan groups, the million-sequence oracle in all3modes with decoded NUL keys, and stage2 corpus200/malformed60 pass. Shared/YAML template-copy audit is recorded in `benchmarks/results/json-shape-alias-fix/`. Formal recapture remains required; no timing experiment is part of the fix.

Text-arena fusion disposition: the pinned RBE pair at5002235 measures median0.969×/0.964×/0.993× for single/medium/big, including a retained6.265× medium outlier. Adoption is rejected. Production uses the checked postpass again; the observer/session survive only under test-only headers with their reproducer and pair. Exact/status parity now compares the isolated candidate to the production session. See `benchmarks/results/json-text-fusion-followup/` for all rounds and sanitizer evidence. This closes the experiment with a measured regression, not a performance pass.

Task2 duplicate-price comparator is now reproducible as `//json/native:duplicate_probe`: the test-only unchecked wrapper removes key announcements/templates/rejection while preserving grammar, numeric/text checks, limits and exact output. Valid corpus200 and duplicate discriminators2 pass ASan+UBSan; local paired medians1.029/1.067/1.086/1.058× remain diagnostic until pinned unclemax qualification. No unchecked production API was added. Evidence: `benchmarks/results/json-duplicate-price/`.


Resume 2026-11-08 — CodSpeed regression closure (local, no push/CI): recovered the worker's
post-checkpoint edits and 76 raw simulation measurements, then replayed main `223edf7`,
`7e0167b^` (`0ff76f6`), `7e0167b` and the saved candidate under amd64 Valgrind 3.19.0;
all eight export/read counts and checksums reproduced exactly. The directory-policy
hypothesis is rejected for these cases: parent→commit export +0.14%, interop -0.99%.
The final rebuilt source uses one header load per export node/text event, native short-key
hint loads with the same validated-node contract, interpreter final-field owner checks
while preserving compiled hooks, and installs shape templates only from the second
eligible object at each depth. Unused owner wrappers were removed; tests pin hook/field
agreement and native key lengths, rank/type/missing/escaped-key behavior in both readers.
The shape policy adds one startup cache miss per depth; no steady session throughput
claim is made, and no prior retained/validation/logical-memory gate is waived.

Final simulation Ir vs main: Java export 71,313,261→60,138,974 (-15.67%); interop read
34,545,961→31,656,185 (-8.36%); snapshot 55,956,519→53,497,031 (-4.40%). Gson control
90,812,315→90,849,603 (+0.04%). These are instructions, not wall-clock throughput.
The recovered other 24 cases move at most +0.61% (YAML Truffle export; other increases
≤0.17%). Final JVM/native/compact/million-sequence and both smoke checks pass 9/9 in
[cdcbc5cc](https://app.buildbuddy.io/invocation/cdcbc5cc-eddc-4c27-98e5-39b5666ea180):
24 compiled graphs and nine allocation cells retain their original gates. Interop graphs
1,954/1,898 nodes; snapshots 1,969/1,913; one loop, no calls/allocations. Generic ingest
384 B/document (144 library + 240 harness) and snapshot 1,183.5 B/document. Evidence,
binary/source hashes, commands and raw results: `benchmarks/results/json-codspeed-20261108/`.
The earlier unclemax full-image timing table still predates the intervening source changes;
only the simulation and final correctness/graph/allocation results above are fresh here.

2026-11-08 — Nested/repeated typed SVM tranche: admit the bounded structural record/array
writer specifically for typed ingestion. On-Demand remains the independent test oracle and
failure-only diagnostic replay; failed structural validation cannot publish a result.
The [final admission report](../../../benchmarks/results/json-typed-tranche-20261108/admission.md)
records production `9a1dcec` versus `a966cf2` with identical fixtures, three fresh forks per
image/cell and paired CPU affinity in invocation
[950032e5](https://app.buildbuddy.io/invocation/950032e5-dc4f-40f9-a67f-0a73f3b5dce3).
Ingest medians (ns/document) are nested 829.097→700.302 (**1.184×**), repeated
4,209.473→3,639.223 (**1.157×**), mixed 1,677.520→1,517.453 (**1.105×**), and churn
2,639.500→2,286.197 (**1.155×**). Every candidate ingest fork beats every baseline fork
in these four workloads, including the retained churn outlier.

The separate generic control regresses 866.279→885.299 ns/document (**+2.20%**, observed
fork ranges do not overlap); flat typed ingest moves **+1.50%** with overlapping ranges.
Read-tier changes and noisy Gson controls remain disclosed without normalization or
mixed-workload competitor comparisons. All **34 Java allocation cells match**; the existing
**24 compiled graph checks and nine allocation budgets pass unchanged**, alongside native
differential/sanitizer, JVM correctness and SVM acceptance checks. The historical scalar
≥1.1× admission threshold is unchanged. Earlier generic throughput/absolute-time gaps,
the unmet 1.5× logical-memory criterion and physical-memory goals remain open; this is
neither a universal JSON speedup nor full release qualification. No PGO, ABI, retained-layout
or prebuilt-native distribution contract changes.
