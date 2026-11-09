# Beat stock Protobuf, JSON and CSV on SVM: evidence-triaged plan (2026-11-08)

> **For agentic workers:** this plan is a *triage*, not a committed delivery. Every number is
> labelled MEASURED (repo evidence, cited), PRIOR-ART (external, cited) or HYPOTHESIS. Record
> rulings in `2026-11-08-beat-stock-svm-ledger.md` (create at Task P0.1). SVM / Native Image is
> the only admission target (user, 2026-11-08: "I care most about performance under SubstrateVM,
> with JVM trailing"); JVM correctness, API, lifetime and coverage tests stay mandatory and JVM
> timings are recorded, never gating. No PGO. Prebuilt natives only; no consumer toolchain.
> Follow the `2026-11-08-svm-next-tranche.md` admission protocol (paired images, three fresh
> forks, pinned CPU, preserved before-image, x86 claims only). Do not start this tranche without
> a user instruction (AGENTS.md release completion note).

**Baseline:** `main` at `2e0df58` (0.10.0 released from `32a5f19`; integrated performance
baseline `7fca64b` on top of `9a1dcec`). Preserved images: `build/svm-tranche-baseline/`.

**Method used to produce this plan:** five repo readers (benchmarks, protobuf, JSON, CSV, git
history), five external researchers (protobuf, JSON, CSV, SVM runtime, cross-format), three
independently designed plans (representation-first, kernel-first, runtime-first), each
adversarially critiqued against the ledgers and source. Killed or re-scoped items are listed in
§6 so they are not re-proposed.

## 1. Where we stand (MEASURED, paired x86 SVM, pinned SDK 25.5.5-elide.27, -O2, no PGO)

Source unless noted: `benchmarks/results/svm-tranche-20261108/README.md`.

| Cell | TruffleData | Stock / best competitor | Standing |
|---|---:|---:|---|
| Protobuf full slab interpreter read | 351.17 ns/row | stock AOT getters 62.14; stock via interop 1,783.76 | 5.652× loss (beats stock-interop 5×) |
| Protobuf full slab peak read | 210.80 | 105.37 | 2.001× loss; **generated-owned 48.48 = 0.46× win** |
| Protobuf full generated-owned interpreter | 215.66 | 62.14 | 3.471× loss |
| Protobuf full slab decode/release | 972.15 | 790.84 | 1.23× loss; scoped session 783.23 = parity |
| Protobuf resident decode+read+release | 1,311.06 | 861.75 | 1.52× loss |
| Protobuf decode Java allocation | 808 B/row (session 40 B + 855.6 B native) | 1,629.5 | win (Java heap only) |
| JSON generic ingest + ten reads | 885.30 ns/doc | fastjson2 650.56; DSL-JSON 801.91; jsoniter 997; Jackson 1,305; Gson 2,665 | 1.36× / 1.10× loss; wins the rest |
| JSON generic compiled reads | 28.79 | Jackson 66.99 | 2.3× win |
| JSON typed scalar ingest | 144.9–178 | Gson streaming 894 | ~5× win (`benchmarks/comparison/JSON.md`) |
| JSON typed peak / interpreter read | 1.5–4.4 / 3.7 ns | owned record 0.95–1.36 / 1.3 | loss (`json-typed-tranche-20261108/paired.md`, JSON.md) |
| JSON snapshot ingest | 2,060 | own baseline 1,472 | regression never recaptured (`json-sota-final-qualification`) |
| CSV typed mixed 1 MiB | 0.4774 GB/s | FastCSV 0.1696; kernel-native bound 0.8217; Rust csv numeric 1.5–1.6× us | win vs Java, 0.58× own bound, loss vs Rust |
| CSV strings, copied input | 0.2740 | FastCSV 0.3013; Jackson 0.2207; kernel-native 0.4336 | 0.91× loss (owned input 0.3206 wins) |
| CSV scan | 2.3–19× every Java parser | C++ index consumer 0.57–0.85× | win vs Java (`benchmarks/comparison/CSV.md`) |

**Diagnosis.** The native producers are at or near the frontier: the Rust slab decoder is
1.4–2.4× faster than upb/C++ generated on the same bytes (`2026-11-07-protobuf-sota.md` §2.3),
the JSON stage 2 is 1.35–1.74× simdjson On-Demand (`json-sota-final-qualification`), the CSV
indexer beats every Java parser. Every remaining loss is on the Java side of the boundary:

- **Protobuf reads:** a slab field read is five non-inlined AOT calls (three virtual) and three to
  four layout checks, plus a synchronized boxed `HashMap` for lists (`2026-11-08-protobuf-b4-design.md`
  §1), while generated-owned reads the same facts at 0.46× stock. The bytes are right; the binding is wrong.
- **Protobuf decode:** `SvmBackend.java:243-280` allocates a zeroed `byte[capacity]`, pins input and
  output, may `Arrays.copyOf` the trim, then `SlabMessage.takeOwnership` runs the full Java
  `SlabVerifier.verifyOwning` walk over a slab our own in-process decoder just wrote. The session
  path (no owner, no verifier, no output array) is 783 ns, so ~190 ns/row of the 972 is this
  publication work (MEASURED delta). The remaining ~600 ns above the standalone Rust probe
  (182 ns small, `protobuf-slab-native-26b26e0`) is **unattributed**; the bounded seam entry is a
  native leaf, so transition cost is not the explanation.
- **Interpreter floor (structural):** ~32% of slab interpreter samples are harness frames
  (`SharedRoot.score`, `DataLibraryGen` dispatch, `FieldAudit`, interop `readArrayElement`/`getArraySize`),
  and stock's 62 ns is AOT-compiled plain getters with no node dispatch. Even zero-cost slab reads
  leave the interpreter cell near 2× stock. The ≤2.5× B4 gate needs harness-visible dispatch work
  (B4 Phase 2 proper) in addition to read-path work; "beat stock" on reads is decided by the peak
  and read-aot cells, with stock-interop (already beaten 5×) as the like-for-like interpreter cell.
- **JSON:** generic ingest's native decode is ~100–130 ns of the 885 ns cell; the rest is
  per-document Java work (session/owner publication, directory-rank reads, ten interop reads at
  158.8 ns AOT vs 28.8 compiled). Typed reads pay owner/confinement checks per access on raw
  `org.graalvm.word` loads (`SvmTypedOwner.java:43-45`); the MemorySegment penalty in oracle/graal#14908
  applies to the JVM path only. Snapshot: `HeapTape.copy` is 25.97% of instructions (callgrind,
  `json-codspeed-20261108/final`).
- **CSV:** framing is SoTA-class; typed and strings lose to batch retention, per-cell `BatchBuilder.put`
  dispatch, number conversion fused into a 7,832-instruction/295-call framing loop (plain framing
  is 1,108/20, `csv-3f23d0c/probes/frame-disassembly.md`) and one `java.lang.String` per cell.
  No instruction-level profile of the typed/strings SVM paths exists.

## 2. Global constraints (unchanged)

Full validation semantics (no validate-only-used-fields); ownership/lifetime checks on borrowed rows
(`owner.check(epoch)`, `requireValue`); 48 B owner, 168/136 B scalar ingest floors, ≤0.05 B/read
compiled; 10,000-node graph and 256-node AST budgets; JSON/CSV graph and allocation gates as in
their comparison docs; public ABI/layout checks for external slabs stay mandatory; no PGO, no
consumer-side native build; `-march=compatibility` images with runtime ISA dispatch only.

## 3. P0 — attribution first (days 1–2; nothing in §4 starts before its profile exists)

- [ ] **P0.1 Protobuf decode pipeline ablation.** In `benchmarks/comparison` add probe engines that
  stop the SVM pipeline after each stage: native decode into reusable pinned scratch only; + fresh
  zeroed `byte[capacity]`; + the two `PinnedObject`s; + `copyOf` trim; + `verifyOwning`; +
  `takeOwnership`/owner publication; + harness loop. Three forks each, full and holdout fixtures,
  plus `perf record` of decode and session cells. Output: ns/row per stage. This decides WS2/WS3.
  The session-vs-slab delta (190 ns) is the upper bound for verifier+allocation+trim+owner; the
  ~600 ns session residual above the standalone decoder needs its own probes: `@CFunction`
  transition alone, `PinnedObject` create/close alone, `WireInput`/lease handling, the harness
  `prepare()` loop, and a same-worker standalone Rust run on the *consumer* fixture (the probe's
  182 ns is the 150 B "small" shape; the 510 B holdout decodes at 552 ns standalone, so fixture
  size alone may explain much of the residual). Include a copy-instead-of-pin variant for inputs and
  outputs below Myna's measured 256 B borrow crossover (0.12.0 release notes; `SvmBackend.java:246-247`
  pins both on every message, and the consumer fixtures are 122–510 B).
- [ ] **P0.2 Fresh interpreter/peak profile of the shipped image.** Every protobuf share cited
  in the ledgers (`Accessor.bound` 21.6%, `HashMap` 4.3%, `SlabMessage.array` 4.3%) predates the
  warm cache (`svm-next-tranche.md` says so). Re-run `consumer_interpreter_profile` on `7fca64b`
  and attribute harness frames separately from library frames.
- [ ] **P0.3 JSON generic ingest split** (callgrind tooling exists): native decode vs session/owner
  publication vs directory-rank reads vs the ten interop reads vs harness views (240 of 384 B).
- [ ] **P0.4 JSON current-source recapture.** Two shipped fixes (ABI 10 stream intent `5002235`,
  bulk snapshot copy `7a0c27f`) were never measured end to end against the preserved image; the
  2,060 ns snapshot and 1,289 ns stream-interop cells may already be stale.
- [ ] **P0.5 CSV typed/strings callgrind** on mixed and numeric 1 MiB: framing vs `put` vs
  conversion vs column grow/trim vs `getString`.
- [ ] **P0.6 AOT symbol gate.** `tools/bench/aot_symbols.py`: objdump/nm over a Native Image,
  reporting instruction count, call count and frame size for named symbols (generated getters,
  `SlabFormat.*`, `Accessor.*`, `SlabRow$*Slot.*`, `IndexScan.frame*`, `BatchBuilder` writers,
  JSON typed readers), with budgets asserted in native smoke. This is the admission check for
  every read-path change below (it is the method that diagnosed the CSV fused-loop regression).

## 4. Workstreams, ordered by expected value per day

### WS1 — Protobuf one-load reads: generated per-contract slab row classes (keystone)

- **Target cells:** full/holdout/sparse slab peak (2.0×/1.55×/1.8× stock), slab interpreter, first access.
- **Mechanism:** extend `protobuf/src/main/java/dev/elide/truffledata/protobuf/SlabGenerator.java`
  (ContractPlugin) to emit, per contract message type, a final `SlabRow_<Type>` (root
  `SlabMessage_<Type>`) whose identity is (root layout fingerprint, type index), with final
  per-field accessor subclasses. A read is: exact-class compare, presence-bit test,
  `ByteArraySupport` load at a constant offset, inline. This removes the
  `bound`→`accepts`→`accessed`→`readLongBound`→`longOf`→`requireValue`/`repeated()`/kind switch/
  `SlabFormat.getInt` chain. B4 Phase 1 could not apply an exact-class guard to slab rows because
  one `Schema` spans several root layouts; a class per (layout, type) is exactly the fix. Borrowed
  rows keep `owner.check(epoch)`; projected slabs keep `requireValue`; runtime plans keep generic `Slot`.
  The per-contract `SlabAccess_<Type>` was in the original protobuf plan and never built.
- **Prior art:** rkyv / FlatBuffers / Cap'n Proto access discipline (one load per field after a
  one-time bind); upb / hyperpb schema-shaped records.
- **Hypothesis:** slab peak from 2.0× to ≤0.5× stock (basis: generated-owned 0.46× on identical
  facts, MEASURED); slab interpreter −25–35% (HYPOTHESIS; P0.2 replaces this number). Interpreter
  ≤2.5× is *not* promised by WS1 alone (see interpreter floor).
- **Pre-check (half a day):** dump the peak-tier graph of one slab long read. `ReadLongNode`
  asserts `partialEvaluationConstant(accessor)`, so `repeated()`/`kind()`/`defaultValue()` are
  expected to fold already; the 211-vs-48 ns peak gap then lives in the virtual `accepts`,
  `requireValue`, the row/text/list flyweight caches and non-inlined helper symbols, which class
  identity removes. If the dump shows otherwise, retarget WS1 at interpreter and first access and
  withdraw the peak claim; the kill rule then applies to the interpreter cell.
- **Gates:** 0.05 B/read, graph/AST budgets, 48 B owner, all 132 consumer cells, contract test that a
  child type reached through a different root layout is rejected. **Kill:** <5% peak with overlapping forks.
- **Effort:** 5–7 days.

### WS2 — Protobuf trusted in-process publication (decode cell)

- **Target cells:** full slab decode/release 1.23×, resident 1.52×, heap input.
- **Mechanism (after P0.1):** slabs produced by our own native decoder in-process skip the Java
  `verifyOwning` walk: the Rust decoder already computes `CERTIFIED`, required presence and exact
  capacity, so extend `SlabResult` to report the logical-budget/required outcome and publish via
  `owning(...)` directly; `copyOf`/external slabs keep the full verifier (the shared-subgraph
  amplification remediation stays separate safety work). Reuse capacity instead of a fresh zeroed
  array where the owner contract allows (session-style high-water mark), drop the trim copy by
  decoding into exact extent on the bounded path.
- **Hypothesis:** −150–190 ns/row (basis: 972 − 783 session delta, MEASURED), i.e. decode at
  ~parity with stock; anything below needs WS3. Trusting in-process output changes the verifier
  contract for internally produced slabs only and needs a user ruling before implementation.
- **Gates:** goldens, differential corpus, failure-injection and `small_entry_allocates_nothing`;
  external-slab verifier tests unchanged. **Kill:** <10% on decode with overlapping forks.
- **Effort:** 2–3 days.

### WS3 — Protobuf batched scoped decode (conditional)

- Only if P0.1 attributes ≥250 ns/row to per-call fixed cost on the *session* path (pins, result
  struct, limits, lease). Seam entry `td_buffa_decode_many` decoding N messages into one session
  arena with a multi-root table; one call per batch. Does not touch the Rust hot loop (A2b stop
  rule). Opt-in API; per-message errors, budgets, reset invalidation preserved.
- **Hypothesis:** decode ≤0.6× stock (HYPOTHESIS; the standalone decoder at 182 ns bounds it).
- **Effort:** 4–6 days.

### WS4 — Protobuf list views without an ABI change

- Lists have no handle ids, so the warm cache stopped at rows/text/blobs. First, no format change:
  replace the synchronized boxed `HashMap<Integer,Object>` (`SlabMessage.java:328-345`) with an
  open-addressed int-keyed table in the same `views` field (key = list offset plus expanded bit;
  projected/lazy owners keep the map), acquire/release hits and synchronized misses as in `7fca64b`;
  `SlabArray` gains final kind/child type/width/expanded so `get(i)` is a bounds check, one load and
  `cached(handle)`. Only if that is insufficient, use the list header's existing zero pad word for a
  handle id (0 extra bytes) carrying type, ordinal and projection identity so aliased external
  slabs stay verifiable (TDSLAB2, goldens regenerated).
- **Hypothesis:** −10–15% slab interpreter (interop `readArrayElement` dispatch is harness cost
  that generated-owned pays too and is *not* removed here). **Effort:** 3–5 days, shares goldens
  regeneration with WS2.

### WS5 — Interpreter dispatch floor (B4 Phase 2 proper, cross-format)

- Audit host-inlining budgets (`TruffleHostInlining*`) and the `DataLibrary`/interop dispatch
  reached from `ReadLongNodeGen` and friends with P0.6; test raising the budget for the read
  nodes and giving the shared root bulk array reads. Cheap, no-PGO lever. **Kill:** <3%.
  This is the only item that can move the interpreter cells below ~2× stock.

### WS6 — JSON typed reads: hoist lifetime checks to record bind

- Probe (1 day): specialized typed reads with `checkThread`/owner check hoisted once per record on
  the existing raw word loads; then, if it pays, an owned heap record for scalar plans (the 56 B
  record copied once, no native retention, read like a generated-owned row).
- **Hypothesis:** peak parity with the Gson owned record (from 1.5–4.4 ns to ≤1.4 ns). The reverted
  slot-address caches kept checked native reads; this removes the checks, not the loads.

### WS7 — JSON snapshot: `HeapTape.copy` root fast path

- After P0.4: a *single-pass root copy*. A whole-document copy is not the identity mapping in
  the current `HeapTape.copy` (`HeapTape.java:57-112`): input is trimmed to `[inputFirst, inputLast)`
  and every scalar/string word is rebased, so keep the trimming (allocation budget unchanged), copy
  pool/directories/metadata in bulk, and rebase words in one pass instead of two. 25.97% Ir
  (MEASURED); 10–20% end-to-end Ir (HYPOTHESIS, untimed).

### WS8 — JSON number kernel under target attributes

- `stage2.h`/`typed.h` call `simdjson::builtin::numberparsing`, which under `-march=compatibility`
  is the fallback kernel; instantiate the SIMD eight-digit step (SSSE3/AVX2) under
  `__attribute__((target(...)))` with runtime dispatch, keeping exact-token rules. Never measured.
  Run only a half-day C++ probe (canada-style numeric fixture plus small/medium/big/huge) first.
  The existing ISA-tier rule (≥3% on big AND huge) cannot admit it because big/huge are the mixed
  fixtures; a numeric-heavy admission identity needs a user ruling before any product change.
  **Kill:** <3% on the numeric probe.

### WS9 — JSON generic ingest vs fastjson2 (attribution-gated)

- Shape-record representations were killed by critique (directory writes are not the dominant
  cost). Proceed only from P0.3: if Java per-document cost excluding the ten reads is ≥200 ns,
  target owner/session publication, the 240 B harness views and boundary count directly.

### WS10 — CSV: monomorphic column writers and plain framing

- **Target cells:** typed mixed/numeric (0.58× own bound, 0.61–0.66× Rust csv), copied strings
  (0.91× FastCSV).
- **Mechanism:** (1) kind-monomorphic column writers selected once per plan (generalizing the
  admitted all-STRING `frameSpans`), hoisting plan/mapping decisions out of per-cell `put`;
  (2) index entries carry a record-end bit so the framing loop is the plain 1,108-instruction shape
  writing spans unconditionally, with per-record width validated in the kernel (csveee's
  Begin/End/Remove masks and pext arity check, PVLDB 19(11)); (3) number conversion moves out of
  the framing loop into per-column passes over spans (SWAR digits, Eisel-Lemire), the layout Sep
  and csveee show is conversion-bound rather than framing-bound; (4) strings built straight from
  index spans as the kernel consumer does. The ASCII-bit idea is already shipped for ≤16 B cells
  (Latin-1 path, +4%) and is not part of this workstream.
- **Order:** first a same-worker probe of (3) alone, reporting batch construction and `CsvRow`
  readback separately (the typed cell is `plan.parse` plus a readback of every cell,
  `CsvComparison.java:464-480`), with a ≥10% numeric 1 MiB kill gate; then a Java-only probe of a
  precomputed separator-kind array before touching the eight kernels for (2).
- **Hypothesis:** typed 0.477 → ≥0.7 GB/s (bound 0.82, MEASURED); numeric vs Rust csv ≥1.0×;
  copied strings ≥0.35 GB/s. **Kill:** <10% typed on paired SVM. **Effort:** 6–8 days.
- **Open ruling:** the short-field strings loss (0.58–0.96× FastCSV, 1/8 vs Rust `StringRecord`)
  is bounded by the one-`String`-per-cell API; (4) is the only in-API lever. A lazy `CsvText`/
  `TruffleString` cell type would be an API decision, not a performance experiment.

### WS11 — Myna Truffle native cells for guest-facing entry points (enabler; 2–3 days)

- **What:** Myna 0.12.0 Truffle native cells (`truffle_cells=true`, `truffle_backend=native`,
  `truffle_transitions=direct`) emit a DSL-free Truffle node per import that runtime-compiled guest
  code calls directly: transitioning bindings reach their AOT stub at ~6 ns (graal#196, needs
  elide.26+; the repo pins elide.27), leaves inline at ~0.9 ns, FFM downcalls are ~11 ns (graal#189).
  The repo pins Myna 0.13.0 but enables cells in no seam. PRIOR-ART numbers from Myna's
  `benchmarks/cells` on unclemax; not yet measured on a TruffleData entry point.
- **Where it applies:** entry points that compiled guest code calls today through a
  `@TruffleBoundary` (`SlabMessage.decode`, `JsonSession`/`JsonPlan` decode and stream calls,
  session reset/retain, string materialization via `utf8_to_utf16`), so partial evaluation sees the
  Java-side publication code and the boundary call disappears. It is also the carrier for WS3
  batched decode and JSON streams driven from guest code. Cells never change leaf status, so the
  bounded-leaf rules in §2 are unaffected.
- **Where it does not apply:** the scoreboard decode stages are host-called and the read paths make
  no native calls, so expect ≤2–5% on the comparison cells (HYPOTHESIS); the value is for real
  guest consumers (TruffleCEL, Elide) and for first-access/ingest-from-guest cells.
- **Cost:** seam fingerprints change; regenerate bindings and natives together (as the 0.13.0
  platform-neutral bindings change already required). Cell classes need build-time initialization.
- **Gate:** a `benchmarks/cells`-style probe calling one TD decode entry from a force-compiled root,
  cell vs current boundary, three forks; adopt per entry point only with a measured gain and
  unchanged allocation/graph gates. **Kill:** no measurable gain on any guest-called entry.

## 5. Sequencing

Day 1–2: P0.1–P0.6 in parallel on separate workers (local Bazel serialized through the wrapper).
Day 3+: WS1 and WS2 are independent and start immediately after their profiles; WS4's no-ABI table
lands with WS1; WS3 only on P0.1 evidence; WS5 after P0.2; WS6/WS7/WS8 after P0.4 on the JSON
worker; WS10 after P0.5 on the CSV worker; WS11 (Myna cells) runs on any worker as a probe first and
lands with the next seam regeneration. One isolated candidate per mechanism, paired
against the preserved image, raw medians and ranges, allocation and graph gates, x86 claims only.

## 6. Settled — do not re-run

Tail-call/`become` decoders (3–14% slower, A2b); SIMD varints (byte loop 0.64 ns beats pext
2.2 ns); Truffle-compiled decoder A4 (16.367× on SVM); per-schema native codegen E3 (release
matrix); reopening the Rust hot loop (A2b stop rule); PGO in any form, including online
re-tabling; JSON AVX2/AVX-512 stage-2 tiers (≤2.2%, AVX-512 −5%); JSON text-arena fusion
(0.964–0.993×); JSON slot/address caches in records (reverted); CSV SWAR quote search; CSV
256-row capacity guess (JVM negative, SVM unmeasured); Vector API on compatibility images (not
intrinsified, 0.05 GB/s); `jdk.internal.misc.Unsafe` leaf for SVM reads (SVM already uses raw
word loads; #14908 is JVM-path only); native views ABI 8/9 (8.5× first access); JSON shape-record
representation (killed by critique pending P0.3); CSV index ASCII bit (already shipped ≤16 B).

## 7. Frontier references consulted

simdjson arXiv:1902.08318; On-Demand arXiv:2312.17149; simdjson 5.0 static-reflection parsing
(2026-09); SFVInt arXiv:2403.06898; Masked VByte arXiv:1503.07387; number parsing
arXiv:2101.11408; UTF-8 validation arXiv:2010.03090; csveee (Ellmann & Neumann, PVLDB 19(11));
Mison PVLDB 10(10); Sparser PVLDB 11(11); Pison PVLDB 14(4); JSONSki ASPLOS 2022; JSON Tiles
SIGMOD 2021; upb musttail (Haberman 2021); hyperpb (2025); Cornflakes SOSP 2023; rkyv /
FlatBuffers access benchmarks; Sep 21 GB/s framing (2025); oracle/graal#14908 (MemorySegment
reads on SVM, 2026-11-04); elide-dev/graal#189/#190/#196 (FFM constant handles, NFI per-call pin
removal, direct AOT-stub transitions from compiled code); Myna 0.12.0 `docs/truffle-cells.md` and
`docs/truffle-guests.md` (cell call costs on unclemax).
