# Protobuf interpreter dispatch floor — WS5 (2026-11-09)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` WS5 (interpreter dispatch floor,
B4 Phase 2 proper); rulings and measurements in `2026-11-08-beat-stock-svm-ledger.md` under
"WS5 (interpreter dispatch worker)". Diagnosis and probes only: **no production change is
admitted** by this workstream.

Before: `5815c56` (`perf/beat-stock-svm` tip). Its consumer images are byte-identical to the
WS1 after-images (`truffledata-consumer` `81f5b58b…`, `truffledata-consumer-profile`
`6b18aadf…`; RBE invocation `47ca75ea`, a remote cache hit), so WS1's `aot-after/` and
`p02-after/` describe this before-image exactly. Images: Linux x86-64, pinned SDK
`25.5.5-elide.27` (compiler `3e74f559`), `-O2`, `-march=compatibility`, serial GC, no PGO;
preserved with manifests in the worktree's ignored `build/before-5815c56/`, `build/cand-a/`,
`build/cand-c/`. SVM is the performance target; no JVM timing was taken.

## Attribution: instructions per row (callgrind, not timing)

The raw data is in `callgrind/` as `<cell>-<image>.cg.gz`, with `before`, `a` and `c` naming
the image. It was collected on the profile images with valgrind 3.24 in the amd64 trixie
container. Collection is toggled on `SharedRoot_execute`/`StockRoot_execute`, i.e. the guest
root's AOT interpreter body and everything it calls. JFR sampling overhead is ~2% of the
totals and listed separately in the raw files. The tools are in `tools/`: `cg_consumer.sh`
runs a cell, `cg_summary.py` and `cg_edges.py` give per-row self Ir and call edges, and
`cg_annotate.py` annotates per instruction. The callgrind runs themselves are repeatable to
0.3% (5,686 vs 5,669 Ir/row on two runs of the same image).

| Full interpreter cell | Ir/row | AOT calls/row | ns/row (pair a, before) |
|---|---:|---:|---:|
| stock (AOT getters behind one boundary) | 1,394 | 38.4 | 61.3 |
| slab | 5,669 | 73.6 | 217.4 |
| generated-owned | 5,871 | 99.4 | 216.0 |
| stock-interop | 44,014 | 962 | 1,738 |

Slab full interpreter, self Ir per row:

| Body | Ir/row | Calls/row | Owner |
|---|---:|---:|---|
| `FieldAudit.read` (five inlined DSL inline readers per call) | 1,681 | 5.38 | harness |
| `SharedRoot.score` (three `ReadLengthNode`, one `ReadLongNode` inlined) | 720 | 1.00 | harness |
| `InteropLibraryGen$CachedDispatch.readArrayElement` / `.getArraySize` | 302 / 84 | 6.88 / 2.00 | Truffle dispatch |
| `DataLibraryGen$CachedDispatch.read` | 172 | 3.66 | Truffle dispatch |
| export `accepts` (`SlabArray` / `SlabRow`) | 169 / 80 | 8.88 / 3.66 | protocol, per representation |
| `SlabArray` `readArrayElement` export (all of `get` inlined) | 599 | 6.88 | library |
| generated accessor `*Bound` reads (string length, bool, three longs, root reads) | 1,192 | 25.5 | library |
| `SlabMessage_Type.get` / `SourceContext.get` / `Cached.read` | 266 / 43 / 146 | 3 / 0.66 / 3.66 | library |

Harness plus Truffle dispatch is 2,987 Ir/row and identical for slab and generated-owned
(`FieldAudit.read` 1,681 and `score` 720 to the instruction); with the export `accepts`
calls it is 3,236 Ir/row. Every library body is already minimal: per-instruction annotation
shows `SlabArray` `accepts` is 19 Ir of which ~12 are the AOT prologue, stack check and
return safepoint poll around a five-instruction hub compare; a generated `readLongBound` is
41 Ir (prologue/epilogue, exact cast, two `byte[]` bounds checks, presence bit, load); the
inline-reader dispatch in `FieldAudit.read` is ~55 Ir per read (state bits, cache checkcast,
index compare, exact hub plus descriptor compare, vtable call). The cost is the number of
AOT calls, not their bodies.

## Host-inlining audit

Options of the pinned SDK (`native-image --expert-options-all`, `host-inlining/expert-options.txt`):
`-H:±TruffleHostInlining` (on), `TruffleHostInliningBaseBudget=5000`,
`ByteCodeInterpreterBudget=100000` (`@BytecodeInterpreterSwitch`), `ByteCodeHandlerBudget=100000`
and `ByteCodeHandlerMaxSubtreeInvokes=256` (`@BytecodeInterpreterHandler`),
`ExploreBudget=10000`, `MaxExplorationDepth=1000`, `MaxSubtreeInvokes=32`, `MinFrequency`
(no minimum on SVM), `PrintExplored`. All apply at image build time to runtime-compilable
methods, which includes every frame of the interpreter cell. Neither core, protobuf-runtime nor
the harness carries `@BytecodeInterpreterSwitch`, `@BytecodeInterpreterHandler` or
`@InliningCutoff` (only `csv` does); no TruffleData artifact ships `native-image.properties`,
so a flag changes only the image it is passed to.

Decision log (`-H:Log=HostInliningPhase` restricted to the hot roots, diagnostic image
`0f8ce966`; `host-inlining/host-inlining-decisions.txt`): `ReadLengthNodeGen.execute`,
`ReadLongNodeGen.execute`, all five `*InlineReaderNodeGen$Inlined.execute`, `Accessor.bound`
and every `accessed` specialization are already inlined into `score`/`FieldAudit.read`. Of 331
cutoffs, 185 are polymorphic virtual calls (177 `not direct call: no type profile` — the
`Accessor.*Bound` calls and every `DataLibrary`/`InteropLibrary`/`Library.accepts` message — and
8 `polymorphic inlining not supported`), which only a profile could devirtualize; 144 are
deliberate (boundaries, unwind, `transferToInterpreter`, not-inlinable throw helpers); and the
remaining two are one budget decision: `FieldAudit.read` from
`score` and from `FieldAuditNodeGen.execute`, `call has too many fast-path invokes` (33 > 32).
The callgrind edges agree: of 73.6 calls/row the only hot direct call is `score → FieldAudit.read`.

## Mechanisms

Each candidate is one isolated change against the same preserved before-image, measured
with `//benchmarks/comparison:consumer_measure` (ten remote shards, three fresh forks per cell,
checksums, tiers and the 256-node AST ceiling checked in every cell; baseline engines
`stock,stock-interop,generated-owned,slab`). Times are ns/row, medians of fork medians with
observed ranges (not confidence intervals). Shared-worker timings are advisory: the same
before-image reads 217.4 and 219.7 ns/row in the two runs, so only same-run pairs are compared.
Full tables: `svm-a/paired.md`, `svm-c/paired.md`; raw `svm-*/merged/results.json`.

### (a) Host-inlining budgets — measured, not admitted

`-H:TruffleHostInliningMaxSubtreeInvokes=64 -H:TruffleHostInliningBaseBudget=20000` on both
consumer images (`2c75adc`, reverted `0915621`; images `6cb96801…`/`2ac9ce27…`, decision log
`host-inlining/host-inlining-decisions-a.txt`). The only decision that changes is
`FieldAudit.read` → INLINE into `score`; every library symbol keeps its size and call count.
Callgrind: slab full 5,669 → 5,419 Ir/row (−4.4%), generated-owned 5,871 → 5,574 (−5.1%),
calls/row 73.6 → 68.5; sparse cells unchanged within ±2 Ir/row. Pair `19af2a83`:

| Cell | Before | After | Change |
|---|---:|---:|---|
| Full slab interpreter | 217.398 (217.081–221.425) | 209.373 (208.753–209.723) | −3.7%, disjoint |
| Full generated-owned interpreter | 215.978 (215.005–217.743) | 209.822 (208.116–209.885) | −2.9%, disjoint |
| Full stock interpreter (control) | 61.326 (61.080–61.508) | 61.401 (61.351–64.131) | +0.1%, overlap |
| Full stock-interop interpreter | 1,738.305 (1,735.362–1,755.396) | 1,601.553 (1,590.927–1,612.613) | −7.9%, disjoint |
| Sparse slab interpreter | 31.978 (31.840–32.157) | 31.999 (31.924–34.947) | +0.1%, overlap |
| Sparse generated-owned interpreter | 31.636 (31.546–31.707) | 34.795 (32.147–35.717) | **+10.0%, disjoint** |
| Full slab / generated-owned / stock peak | 102.361 / 49.287 / 106.079 | 101.828 / 49.270 / 104.942 | −0.5% / −0.0% / −1.1%, overlap |
| Sparse slab / generated-owned peak | 14.667 / 10.081 | 14.123 / 10.087 | −3.7% / +0.1%, overlap |
| Holdout slab / generated-owned peak | 125.760 / 55.621 | 125.584 / 59.616 | −0.1% / +7.2%, overlap |
| Large slab / stock peak | 16,316.247 / 16,050.871 | 16,160.918 / 16,590.980 | −1.0% / +3.4%, both disjoint |

The full slab interpreter clears the 3% kill line (−3.7%, disjoint) and reaches 3.41× stock
(from 3.54×). It is not admitted, for four reasons:

1. It is an image-wide build flag, and no TruffleData artifact ships native-image arguments, so
   a consumer image (Elide, TruffleCEL) gets nothing unless it sets the flag itself.
2. Its only effect on this cell is inlining the harness's own `FieldAudit.read` (a benchmark
   node) into the harness's `score`.
3. It moves the stock-interop control by −6 to −16% in every cell, so it changes the
   comparison's own denominators.
4. Sparse generated-owned interpretation regresses +10.0% with disjoint ranges although its
   instruction count is unchanged (959.6 → 960.8 Ir/row). The cause (code layout of the larger
   `score` body, or worker drift) is not established.

Shipping it via `META-INF/native-image/.../native-image.properties` would change every consumer
image's global inlining policy and needs a ruling. Allocation is unchanged (2.00/1.00 B/row).

### (b) Node-side dispatch — no candidate within the constraints

The read nodes already reach the exact-class accessor in one guard and one call. The decision
log shows `ReadLongNodeGen.execute`, the inline readers, `Accessor.bound` (exact hub plus
descriptor compare) and every `accessed` specialization inlined. The callgrind edges show no
`Accessor_bound`, no `accepts` and no `DataLibrary` call from any read node in this cell: the
library specializations are never taken, and the 3.66 `DataLibrary.read` calls per row are the
harness's own.

What is left is the vtable call to the final generated accessor. Its receiver is the cached
`Accessor` field; Native Image's static analysis sees every accessor subclass there, and without
a type profile (no PGO) it cannot devirtualize the call. Core cannot name representation
classes to dispatch on.

The only other node-side cost is the guard itself, ~25 Ir per read: index compare,
`AccessedInlineData` checkcast, accessor and exact-class null checks, hub compare, `AbstractRow`
cast, descriptor compare. The parts that are not the layout-safety check (the checkcast and the
cast, ~6 Ir × 21.5 reads/row ≈ 2% of the cell) are below the kill line, and dropping the
descriptor compare would weaken invariant I1. Not built.

### (c) Interop array messages — measured, killed

The `SlabArray` export is already a cached, monomorphic, fully inlined body: 87 Ir per element
for bounds, `omitted`, the category jump table, two slab loads and a view-table hit. Its
`accepts` is an exact class compare.

Splitting `SlabArray` by element category would put two classes behind the harness's dispatched
`InteropLibrary` (fields are MESSAGE, oneofs STRING) and add an `accepts` call to whichever
entry is probed second. So the one isolated candidate removes the duplicated index check
instead: one unsigned compare in `readArrayElement`, with the element body shared with
`get(int)`. Commits `65e99f2` (reverted `0bd880b`); images `ce41e573…`/`7a56487d…`;
`//protobuf-runtime:test` passes (`e686536a`). Callgrind: export 599 → 585 Ir/row, slab full
5,669 → 5,658 (−0.2%). Pair `569a253c`:

| Cell | Before | After | Change |
|---|---:|---:|---|
| Full slab interpreter | 219.678 (218.696–229.910) | 219.319 (217.952–222.797) | −0.2%, overlap |
| Full generated-owned interpreter (untouched) | 230.163 (216.926–232.567) | 217.236 (215.606–226.534) | −5.6%, overlap |
| Full stock / stock-interop interpreter | 63.659 / 1,776.447 | 61.690 / 1,815.276 | −3.1% overlap / +2.2% disjoint |
| Sparse slab / generated-owned interpreter | 32.208 / 31.832 | 33.052 / 31.603 | +2.6% / −0.7%, overlap |
| Full slab / generated-owned / stock peak | 102.633 / 49.087 / 106.624 | 103.033 / 48.901 / 109.313 | +0.4% / −0.4% / +2.5%, overlap |
| Holdout slab / large slab peak | 126.045 / 16,189.581 | 126.489 / 16,302.623 | +0.4% / +0.7%, overlap |

Killed (<3% with overlapping forks). This run's shard is noisier than pair (a)'s (stock −3.1%,
the untouched generated-owned −5.6%), and no cell the change touches moves outside its range.

JSON/CSV: neither probe touches core, tape, JSON or CSV code. Probe (a) changes only the
consumer images' flags and probe (c) only `protobuf-runtime`, so their interpreter cells were
not re-measured; their last controls stand (`json-reads-a1d8782`, `csv-writers-afa4aa5`).

## AOT symbols (x86-64 profile images, shape only)

`aot/dispatch-*.md` (llvm-objdump, local; budgets `aot/budgets-*.md`):

| Symbol | Before (5815c56) instr / calls / frame | (a) flags | (c) SlabArray |
|---|---:|---:|---:|
| `ConsumerComparison$SharedRoot_score` | 2,993 / 197 / 136 B | 5,583 / 366 / 168 B | 2,993 / 197 / 136 B |
| `ConsumerComparison$FieldAudit_read` | 2,297 / 139 / 104 B | 2,297 / 139 (still emitted for `FieldAuditNodeGen`) | same |
| `DataLibraryGen$CachedDispatch_read` | 91 / 4 | same | same |
| `InteropLibraryGen$CachedDispatch_readArrayElement` | 100 / 4 | same | same |
| `InteropLibraryGen$CachedDispatch_getArraySize` | 104 / 4 | same | same |
| `SlabArray` `Cached_readArrayElement` export | 915 / 46 | same | 901 / 46 |
| `SlabArray` `Cached_accepts` export | 47 / 0 | same | same |
| `SlabRow` `Cached_read` / `Cached_accepts` exports | 57 / 5, 67 / 3 | same | same |
| `SlabArray_get` | 859 / 43 | same | same |
| `Accessor_bound` (out-of-line copy; inlined at every read site) | 85 / 3 | same | same |

`consumer_aot_budgets.json` passes unchanged on all three profile images. No pinned symbol
moved, so nothing is re-pinned. Per-row execution counts (which AOT calls actually run) are in
the callgrind section, not in this static table.

## What remains between the interpreter cells and the B4 gate

Before-image, full interpreter: slab 217.4 ns/row = 3.54× stock (61.3), generated-owned 3.52×.
The B4 gate is ≤2.5×, i.e. ≤153 ns/row, a 29% cut. In instructions (the slab cell runs at
~0.038 ns/Ir, stock at ~0.044 ns/Ir) the gate is ≈4,000 Ir/row against 5,669 today.

- **Harness and protocol floor.** Harness plus Truffle dispatch is 2,987 Ir/row and identical
  for every representation. With the protocol's export `accepts` calls it is 3,236 Ir/row,
  ≈124 ns, ≈2.0× stock even with zero-cost library reads.
- **Library budget left.** That leaves the library ≈770 Ir/row for all its bodies to meet the
  gate. It spends ≈2,330 today over ~42 AOT calls/row, of which ~500 Ir is call
  prologue/epilogue alone. No library-side change can close that without removing calls.
- **Calls cannot be inlined away.** Calls are removed only by inlining, and the hot ones are
  polymorphic virtual calls through DSL caches and library dispatch (185 of 331 cutoffs,
  including every hot one). Without a type profile (PGO is excluded) Native Image cannot
  devirtualize them; budgets reach only the one direct call (probe a).
- **Remaining levers are hypotheses outside this workstream's constraints:**
  - a representation-independent read protocol that core can inline, for example accessors
    exposing a final constant-offset slot descriptor instead of a virtual read (an
    API/representation change);
  - a harness that reads lists in bulk (changes the shared guest program, which the brief
    excludes);
  - the image-wide inlining flag of probe (a), which needs a ruling and is worth ~4% here.

Stock-interop, the like-for-like interpreter cell, is already beaten 8×.

## Gates

No production change is admitted. The production source at the end of this workstream is
`5815c56`: probe commits `2c75adc`/`65e99f2` are reverted by `0915621`/`0bd880b`. The lead's
gates on that revision stand and were not repeated (AGENTS.md: no re-validation without a
change).

Probe-level checks:

- `//protobuf-runtime:test` passes on the (c) source (`e686536a`).
- Both paired runs executed all 132 consumer cells per binary with checksums, compiled-tier
  assertions and the 256-node AST ceiling.
- `consumer_aot_budgets.json` passes on the before, (a) and (c) profile images (local
  `tools/bench/aot_symbols.py`).

No JVM timing was taken.
