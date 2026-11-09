# Protobuf State-of-the-Art Revision (amends `2026-11-07-protobuf-performance.md`)

> **For agentic workers:** this revision replaces the protobuf plan's producer strategy (Phase A, Task E3) and its §8 targets where stated below; everything it does not mention stands. Record rulings in `2026-11-07-protobuf-performance-ledger.md` under the task names used here. Evidence is in `benchmarks/results/protobuf-sota-probe-ce7399f/` (README, the probes as run, raw logs).

**Question answered.** Task A2's table-driven Rust slab decoder measured 2.57× the hand-written decoder on small messages, 3.92× `hand_novf` on the holdout and 740 MB/s on large messages, against ≤ 1.3×, ≤ 1.5× and ≥ 1.5 GB/s; a worker is restructuring it (explicit record stack, upb-style fast table, cold paths out of line, branch-free varints) and will escalate to Task E3 (per-schema native codegen) if it misses by more than 1.25×. The user asks for the architecture that beats stock protobuf-java *by far* on every dimension — decode (small and large), projected and full ingestion, first access, interpreter and peak reads, detach, allocation, the JVM and the pure-Java path — without PGO, and which in-flight work to stop or redirect.

**Answer.** The slab is the right representation; only the *producer* was open, and three measurements settle it.

1. **The slab format is cheap.** A hand-written decoder that writes the production `TDSLAB1` bytes (payload copies, 8/12-byte string headers, handles, zeroed records, lists) costs 7% more than the span-based `hand_ascii` sketch on the same worker (98.1 vs 91.9 ns small; 1.92 vs 2.44 GB/s large). The production decoder's 2.3× gap to it is interpretation, not the format.
2. **Table-driven interpretation has a floor of about 1.4× the per-schema decoder on this schema, whatever its shape.** An explicit-stack loop with upb's register discipline (`fast2`: pointer cursors, record base pointer, hasbits in a register, packed 16-byte entries with a predicted next entry, one-word returns from cold paths) measures 140 ns; the same handlers chained with Rust's `become` (upb's `musttail` shape, `fast_tail`) measure 159 ns; a plain explicit-stack loop over slice offsets (`fast`) 177 ns; the production decoder 226 ns. All four write byte-identical slabs. The in-flight restructure is therefore worth finishing — it should recover about 1.6× (226 → ~140 ns; 0.76 → ~1.3 GB/s) — but it cannot meet a 1.25×-of-hand gate, and that gate asked the wrong question: a 2-byte varint field costs ~15 instructions specialised and ~27 interpreted (entry load, next-entry pointer, slot add, variable-shift presence bit, kind dispatch), and no dispatch shape removes those.
3. **Per-schema specialisation is where the last 1.4× lives, and where to get it differs by platform.** On the JVM it is generated Java: the span form measured 0.47× `Type.parseFrom` on x86 at `fecb39f` (89 vs 191 ns) and the slab-writing form measures 0.49× on an M-series host (94 vs 188–201 ns) — but the slab-writing form is only 0.8–0.93× stock on x86 HotSpot (205 vs 221 ns) and slower than stock on large messages there, so the Java *writer* has an x86 problem to fix before the JVM claim is complete (§2.2); a native decode cannot beat generated Java on the JVM either way (downcall + copy). On Native Image the same source, AOT-compiled without profiles, measures 397 ns — 0.53× stock (743 ns), but **twice the Rust fast-table cell** (≈ 200 ns with transition) — so the native decoder stays the SVM producer and generated Java is the no-native fallback there. Generated *Rust* (Task E3) would put a Rust toolchain into every consumer's build, which the release matrix (`2026-11-07-release-matrix.md`: prebuilt natives, "no build of their own") rules out, for a gain of 1.3–1.45× over the fast table on SVM only. Table-driven Java is 1.8× the generated form on the JIT (0.9× stock) and 1.5× under AOT (0.79× stock), so the pure-Java path for *runtime* plans, and the pure-Java path on SVM generally, needs runtime compilation to be "far beyond": a Truffle-compiled decoder root (Task A4) is the one mechanism that gives JIT-quality per-schema code on both platforms without a build step.

**Recommended architecture.** One slab, three producers, chosen by plan kind and platform: (i) **generated Java** (`SlabDecoder_<Type>`, Task A3) for contract types on the JVM, and the no-native fallback on SVM; (ii) the **Rust fast-table decoder** (the restructure, with the `fast2` discipline, Task A2b) as the SVM default for every plan and the runtime-plan producer natively; (iii) **table-driven Java** (`SlabCodec`) for runtime plans without the native library, with a **Truffle-compiled per-plan decoder** (Task A4, new, evidence-gated) to replace both Java shapes at runtime wherever it measures faster — on SVM that may include contracts. **Task E3 is withdrawn.** Reads, first access, detach and allocation are properties of the slab and of Tasks B1/B2/B4, not of the producer; §4 states what each dimension should reach and why.

**Baseline:** `ce7399f` on `feat/perf-v4` (Tasks A1, A2, B2 landed). Probes: `benchmarks/results/protobuf-sota-probe-ce7399f/`.

---

## 1. Candidates studied

| Candidate | What it does | What it means here | Verdict |
|---|---|---|---|
| **upb fast table** (`decode_fast.c`, `fasttable_enabled`) | Each handler is a function over `(decoder, ptr, msg, table, hasbits, data)` chained with `musttail`; dispatch indexes a per-message table by the tag's low bits; `data` packs the expected tag, slot offset and hasbit so a handler is one compare, one read, one store, one `or`; a miss tail-calls the mini-table interpreter. State lives in six registers, never in memory between fields. | The *discipline* is exactly what the A2 profile said the production loop lacks (fifteen live values across nested-record and string calls, spilled). The tail-call *shape* adds nothing over a loop with the same discipline here (`fast2` 140 ns vs `fast_tail` 159 ns, §2.1). | **Adopt the discipline in the restructure; do not adopt `become`** (a nightly feature, measured slower). The fast-table competitor number itself is in §2.3 if the build succeeded. |
| **hyperpb** (Buf, Go) | A VM interpreting the wire as bytecode over schema-compiled per-type structs ("table-driven parsing pioneered by upb"), memory reuse through a `Shared` arena, read-only results, optional offline/online PGO re-laying types out from observed data (+50–100%); ~3× generated Go, ~10× `dynamicpb`. | Confirms the slab direction: compile the schema to fixed per-type layouts, parse into them with bump allocation, make results immutable. Its headline wins are over Go's allocation-heavy generated code and its PGO is out of scope here. | **Already the architecture.** The lesson to take is the one above: the interpreter must be register-disciplined. |
| **prost / quick-protobuf** (Rust) | Generated per-message `match tag` decoders into owned structs (`prost`) or `Cow` borrows (`quick-protobuf`). | Generated code is the fast shape (our `hand_*` rows are this shape); their object graphs are what the slab avoids. | Confirms per-schema codegen; nothing to copy. |
| **protobuf-java full / lite** | Full: generated `parsePartialFrom` over `CodedInputStream` building objects (the stock cell). Lite: `MessageSchema`, a table interpreter over `Unsafe` field offsets. | Stock's cost is objects and `String`s (1,630 B/row), not parsing; lite's interpreter is our `java_table` shape and is not faster than full. | The baseline; nothing to take. |
| **Vectorised varints** (masked-vbyte, Stream VByte) | SIMD extraction of packed varints. | Measured at `fecb39f` and again here: a byte loop decodes packed varints at 0.64–0.75 ns each (3.1–3.7 GB/s), `pext` at 2.2–2.6 ns; protobuf's 7-bit continuation layout defeats these schemes and Stream VByte needs its own control-byte format. | **Rejected** (unchanged). |
| **Lazy decoding / skip-scan** | Keep the wire; index or decode a sub-message on first access. | `table_none` (validate, materialise nothing) is 64 ns against 226 ns: lazy sub-message decode is worth 2–3× for sparse consumers. But ABI 9 views paid exactly this on first access (8.5× stock) and the full consumer reads everything. | **Keep as parse-time projection** (Task E1, schema-declared), plus an opt-in lazy-children `Projection` mode (§6). Not the default representation. |
| **Per-schema codegen, native (Task E3)** | Emit Rust per contract into the Bazel build. | Closes the 1.4× interpretation gap natively (hand 98 vs `fast2` 140 ns) but requires every consumer to compile Rust and contradicts the prebuilt-native release matrix; on the JVM a native decode saves nothing (downcall + copy vs 94 ns in Java). | **Withdrawn.** |
| **Per-schema codegen, Java (Task A3)** | `SlabDecoder_<Type>` with constant tags, slots and presence bits. | 94 ns on HotSpot (0.49× stock); AOT-compiled per schema on SVM by the consumer's image build. | **Primary producer for contracts.** |
| **Runtime specialisation on the JVM** (bytecode generation per plan) | Emit a decoder class per `ProtoPlan` at runtime (ClassFile API). | Would give runtime plans the generated shape on HotSpot, but not on SVM (closed world) — two code paths for one job. | **Not pursued**; Task A4 (Truffle) covers both platforms. |
| **Truffle-compiled decoder per plan (Task A4, new)** | A decoder root whose field tables are PE-constant: Graal partial evaluation folds the table into the generated shape at runtime, on the JVM and on SVM. | The only runtime route to per-schema code on SVM without a build step; TruffleData already ships the Truffle runtime in every image. Risk: graph size on wide schemas; compile latency. | **Evidence-gated Phase A task** (§6). |
| **Representation alternatives** (arena pointer graphs as upb/C++, lazy indexes as ABI 9, columnar batches) | — | Pointer graphs cannot be read from Java without `MemorySegment` indirection and carry native lifetimes; indexes pay on every read; columns help only repeated-message analytics. | **Slab stays.** 3.3–3.7× input is the price of 4-byte slots and copied payloads; large messages still decode into it at 1.9 GB/s (hand) and 1.3 GB/s (table). |

## 2. Evidence

All numbers are advisory (shared RBE workers, one run, best of 7; the JVM rows best of 10 × 3 warm passes); compare within a file. Every slab-writing variant (`hand_slab`, `fast`, `fast2`, `fast_tail`, `java_gen`, `java_table`) produces byte-identical output (17,256 B for the 32 small messages, 52,176 B holdout, 5,528,440 B large; the production decoder writes 17,512 / 52,432 / 5,528,696 B, the difference being the unknown table and the `options` count slots the probes simplify). The probes skip unknown fields instead of preserving them and implement no merges, oneofs or maps; the production decoder does all of that off the hot path, so the ratios bound it from below by a few percent, not more (the A2 profile put record open/close at 15–20% and the hot loop at 60–70%).

### 2.1 Native decoder shapes (one RBE worker, `rust.txt`; ns per message, MB/s of wire)

| Variant | small 150 B | holdout 510 B (Unicode names) | large 52 KB | small ÷ `hand_slab` |
|---|---:|---:|---:|---:|
| `scan_flat` (structural floor) | 14.8 · 10,095 | 42.7 · 11,952 | 3,323 · 15,595 | 0.15 |
| ABI 9 `view` (production before A2) | 514.1 · 292 | 1,530.5 · 333 | 166,772 · 311 | 5.24 |
| **`slab` (production A2 decoder)** | **226.0 · 663** | **636.2 · 802** | **67,821 · 764** | **2.30** |
| `table` (A2's naive sketch) | 218.0 · 688 | 664.4 · 768 | 77,872 · 666 | 2.22 |
| `fast` (explicit stack, slice offsets) | 177.3 · 846 | 509.8 · 1,000 | 53,206 · 974 | 1.81 |
| `fast_tail` (`fast2` handlers via `become`) | 159.4 · 940 | 451.0 · 1,131 | 42,759 · 1,212 | 1.62 |
| **`fast2` (explicit stack, upb discipline)** | **140.3 · 1,069** | **391.7 · 1,302** | **38,910 · 1,332** | **1.43** |
| **`hand_slab` (per-schema, production slab bytes)** | **98.1 · 1,529** | **281.4 · 1,813** | **26,969 · 1,922** | **1.00** |
| `hand_ascii` (per-schema, spans, old validator) | 91.9 · 1,631 | 550.1 · 927 | 21,211 · 2,443 | 0.94 |
| `hand_novf` (per-schema, no validation) | 66.0 · 2,271 | 175.0 · 2,915 | 19,325 · 2,682 | 0.67 |

Reading it:

- **Format tax: 7% small, 27% large** (`hand_slab` vs `hand_ascii`): payload copies and record zeroing are memory traffic the span sketch avoids; at 1.9 GB/s of input the slab writes 6.4 GB/s. This is the cost of a result with no input dependency and identity snapshots.
- **Interpretation tax: 1.43× with the best discipline**, 1.6× with tail calls, 1.8× with offsets, 2.3× in production. `fast2` is 0.62× the production time on every shape (0.57× large), so the restructure's expected landing is **~140 ns small, ~390 ns holdout, ~1.3 GB/s large** on this worker class if it adopts the `fast2` discipline fully.
- **The holdout was mis-baselined.** A2's "3.92× `hand_novf`" compared validated against unvalidated work. Against `hand_slab` — which uses the production `validate_utf16_units` (ASCII words, then a scalar DFA counting UTF-16 units) — the holdout ratio is 2.26×, the same as small. That validator is also 2× faster than the `fecb39f` probe's `utf16_length` on Unicode names (281 vs 550 ns), so the A2 string path is already the right one.
- **Varints and strings are not the problem.** Packed varints 0.64 ns each in a byte loop; a 9-byte ASCII name validates in 0.8 ns. The A2 diagnosis (register pressure, ~100 instructions per field) is confirmed by the `fast`→`fast2` step: changing nothing but the state representation (offsets → pointers, record offset → base pointer, `Out` struct → locals, 3-word string return → 1 word) removes 37 ns of 177.
- **`become` is not a lever.** Rust nightly-2026-10-09 compiles `explicit_tail_calls` and guarantees the tail call (a 10M-deep chain ran in constant stack), but the handler chain is 14% slower than the loop on x86-64 and equal on an M-series host: each handler re-derives the next entry pointer and the `Ctx` loads the loop keeps in registers.

### 2.2 Java decoder shapes

**JVM, HotSpot with the Graal JIT** (`jvm-local-m-series.txt`, Apple M-series, three warm passes; the RBE shards are discussed below the SVM table):

| Variant | small | holdout | large | ÷ stock |
|---|---:|---:|---:|---:|
| `stock_parse` (`Type.parseFrom`) | 187–201 ns · 746–801 MB/s | 721–768 · 664–707 | 43.4–44.3 µs · 1,169–1,195 | 1.00 |
| **`java_gen` (generated shape, production slab bytes)** | **94 · 1,590** | **306 · 1,668** | **29.7–31.0 µs · 1,675–1,746** | **0.49 / 0.42 / 0.70** |
| `java_table` (table-driven, explicit stack) | 166–172 · 871–901 | 513–612 · 833–994 | 68.4–70.9 µs · 731–757 | 0.89 / 0.74 / 1.58 |

**JVM, HotSpot with the Graal JIT, x86-64 RBE worker with 4 vCPUs reserved** (`jvm-rbe-shard2.txt`, third warm pass; shard 1 without the reservation is in `jvm-rbe-shard1.txt` and agrees):

| Variant | small | holdout | large | ÷ stock |
|---|---:|---:|---:|---:|
| `stock_parse` | 221 ns · 679 MB/s | 916 · 557 | 60.0 µs · 864 | 1.00 |
| **`java_gen`** | **205 · 732** | **602 · 847** | **70.4 µs · 736** | **0.93 / 0.66 / 1.17** |
| `java_table` | 330 · 454 | 1,028 · 496 | 117.2 µs · 442 | 1.49 / 1.12 / 1.95 |

- **The generated Java decoder's margin over stock on HotSpot is host-dependent, and the `byte[]` slab writer is the suspect.** On the M-series host it is 0.49× stock small and 0.70× large; on two x86-64 RBE shards it is 0.80–0.93× small, 0.66–0.71× holdout and 1.13–1.17× large (slower than stock). The `fecb39f` x86 shard measured the *span* form (`long[]` words, no payload copies, no `VarHandle`) at 0.47× (89 vs 191 ns), so on x86 the slab-writing form is ≈ 2.3× the span form while the Rust format tax is 7%: the Java writer (little-endian `VarHandle` stores, `System.arraycopy` per 8–12-byte string, `Arrays.fill` per 32–48-byte record, the `VarHandle` word read in the validator) hits something the x86 JIT handles badly and the aarch64 JIT does not. **Task A3's JVM claim therefore stands for the shape, not yet for the writer**: its Step 4 must diagnose on x86 (hypothesis: replace `arraycopy`/`fill` with explicit word stores for short lengths and keep `pos` out of fields) before the ≤ 0.55× hypothesis is accepted, with the span form as the reference.
- Table-driven Java is **1.6–1.8× the generated form** on every host — the same interpretation tax as native — and 0.9–1.5× stock. A runtime-plan consumer on the pure-Java path gets parity at best unless Task A4 lands.

**Native Image (AOT, `-O2`, `-march=compatibility`, no profiles; one RBE worker, `svm.txt`):**

| Variant | small | holdout | large | ÷ stock |
|---|---:|---:|---:|---:|
| `stock_parse` (`Type.parseFrom` on SVM) | 743 ns · 202 MB/s | 2,336 · 218 | 191 µs · 272 | 1.00 (the harness's 772 ns decode cell) |
| **`java_gen`** | **397 · 377** | **1,118 · 456** | **122.5 µs · 423** | **0.53 / 0.48 / 0.64** |
| `java_table` | 586 · 256 | 1,477 · 345 | 166.5 µs · 311 | 0.79 / 0.63 / 0.87 |

- **AOT costs the generated Java decoder 4.2× against HotSpot's JIT** (397 vs 94 ns) on the same source, as the plan's risk section feared (the CSV cycle saw the same). Stock loses only 3.7–3.9× (743 vs 190), so the Java producer's lead over stock shrinks from 0.49× to 0.53× small and from 0.70× to 0.64× large — still well below stock, but **twice the Rust fast-table cell** (≈ 140 + 60 ns of transition, pin and owner ≈ 200 ns) and 1.4× even the *current* production Rust cell (226 + 60). **Q2's rule ("Java on SVM when within 1.1× of Rust") is therefore not met; Rust stays the SVM default**, and the generated Java decoder is the SVM *fallback* (no native library), where it is 0.53× stock rather than the native path's ≈ 0.26×.
- The interpretation tax under AOT is 1.47× (table vs gen), smaller than the JIT's 1.8× because AOT compiles both shapes less well; table-driven Java on SVM is 0.79× stock small.
- **What this implies for the pure-Java path on SVM:** the only way to get JIT-quality per-schema code into a Native Image without profiles is to let Graal compile it at runtime, which TruffleData already does for guest code: a Truffle-compiled decoder root (Task A4) is PE-compiled with runtime profiles on SVM exactly as on HotSpot. Hypothesis: A4 lands near the HotSpot `java_gen` figure (≈ 100–150 ns on this worker class), i.e. faster than AOT `java_gen` (397) and competitive with the native path (≈ 200) with no native library. This is the most valuable open experiment for the pure-Java path, and the §6 gate is set accordingly.

**The same writer under AOT** carries the same suspect: part of the 397 ns on SVM may be the x86 writer pathology rather than AOT alone (the span form was never measured on SVM). Task A3's SVM measurement must run both forms.

### 2.3 Competitors (`cpp.txt` at `fecb39f`; upb fast table: `upb.txt` here if built)

| Decoder (faster C++ worker, ≈ 1.3× the Rust worker on `scan_flat`) | small | holdout | large |
|---|---:|---:|---:|
| protobuf C++ 33.4 generated, reused message | 289 ns · 519 MB/s | 839 · 608 | 82.9 µs · 625 |
| upb 33.4 mini-table interpreter | 366 · 409 | 1,076 · 474 | 124.9 µs · 415 |
| *(this cycle, a third worker 1.66× faster on `scan_flat`, `upb.txt`)* protobuf C++ generated, reused | 166 · 905 | 476 · 1,072 | 43.6 µs · 1,190 |
| *(same run)* upb 33.4 with `fasttable_enabled=true` | 209 · 718 | 609 · 838 | 63.3 µs · 819 |

The fast-table run (Q8) is **inconclusive**: the upb-to-C++ ratio is identical to the mini-table run (1.26× vs 1.27×) and both rows moved exactly with the worker, so either the fast table did not engage (unverified: the generated mini-tables need fast-table entries and `UPB_FASTTABLE` must be defined) or it does nothing on this fixture. Calibrated through `scan_flat` to the Rust worker (×1.15), upb ≈ 240 ns and C++ generated ≈ 190 ns against `hand_slab` 98 and `fast2` 140: the slab producers are 1.4–2.4× faster than both on the same bytes because they build no objects, copy strings once into a bump region and write presence bits instead of calling setters; on large messages C++ generated (1.19 GB/s) is close to `fast2` (1.33) and below `hand_slab` (1.92).

## 3. Architecture per use case

| Use case | Producer | Representation | Expected decode (this fixture) |
|---|---|---|---|
| Contract types, JVM | generated Java (`SlabDecoder_<Type>`) | owning heap slab (`SlabMessage`) | 0.45–0.5× stock small; 0.7× large (1.7 GB/s); no native library, no downcall |
| Contract types, SVM, native available | Rust fast table through pinned arrays (Task B3); AOT Java gen measured 2× slower (§2.2), so Q2's rule keeps Rust | owning heap slab | ~140 ns + ~60 ns transition/pin/owner ≈ 200 ns ≈ 0.26× stock (772 ns); large ≈ 1.3 GB/s ≈ 0.18× |
| Contract types, SVM, no native (the pure-Java release gate) | generated Java (AOT); Task A4 root if it beats it | owning heap slab | 397 ns ≈ 0.53× stock; large 423 MB/s ≈ 0.64×; A4 hypothesis ≈ 0.2× |
| Runtime plans (schema at runtime), native available | Rust fast table | owning heap slab | as the native row above |
| Runtime plans, pure Java | table-driven `SlabCodec`; Task A4 Truffle-compiled decoder when it passes its gate | owning heap slab | table: ≈ 0.9× stock small on HotSpot, 0.79× on SVM; A4 hypothesis ≤ 0.6× (JVM), ≤ 0.3× (SVM) |
| Parse-consume-release hot loop | any producer into a session arena (`SlabSession`, Task D1) | borrowed session slab | decode cost minus the slab allocation; 0 Java B/row for `decode` |
| Sparse consumer with known projection | any producer with `Projection` (Task E1) | heap slab, skipped subtrees counted | `table_sparse` showed −18% at `fecb39f`; `table_none` bounds it at 0.28× full decode |
| Huge payloads (`bytes` ≥ 4 KiB) | any | borrowing slab with `EXTERNAL` headers (Q1) | avoids the 27% large-message copy tax on those fields |
| Text / ProtoJSON | eager `State` (unchanged) | `ArrayRow` | not a throughput target |

## 4. Dimension by dimension

Ratios are to stock protobuf-java on the SVM shared consumer unless marked JVM; "today" is the best engine at `fecb39f`/`a58a36d`; "expected" is the hypothesis this revision sets for the harness, derived from the probes in §2 plus the measured fixed costs (≈ 56–60 ns of transition, lease and owner per message on SVM; 7.9 ns downcall on the JVM).

| Dimension | Today | Mechanism | Expected (gate) | Basis |
|---|---:|---|---:|---|
| Decode only, small (SVM) | 0.89× (view) | Rust fast table (`fast2` discipline) + pinned arrays | **≤ 0.35×** (stretch 0.25×); no-native fallback (AOT Java gen) ≤ 0.6× | 140 + 60 ns vs 772 ns; AOT gen 397 ns |
| Decode only, large (SVM) | 0.89–1.25× | same; packed fixed = memcpy; varint byte loop | **≤ 0.25×, ≥ 1.0 GB/s** (1.5 GB/s needs E3 or A4; open question 1) | 1.33 GB/s `fast2`, 1.92 `hand_slab` vs 229 MB/s stock; AOT gen 423 MB/s |
| Decode only, small / large (JVM) | 1.25× / 1.07× (owned) | generated Java, no native hop; writer fixed on x86 (A3 Step 4) | **≤ 0.55× / ≤ 0.8×** on x86 (hypothesis; measured 0.93× / 1.17× with today's writer, 0.47× for the span form) | 89 vs 191 ns span form on x86; 94 vs 190 slab form on aarch64 |
| Projected ingestion | 1.02× | decode gain + lengths from string headers (no `String`) + Task E1 projection | **≤ 0.5×** | decode is ≈ 90% of the cell today |
| Full ingestion + consumption | 1.04× (owned) / 1.44× (view) | decode gain + flyweight rows + lazily materialised strings cached per handle | **≤ 0.6×** | decode gain; reads at generated-owned cost |
| Full, heap input | 1.00× (owned) | pinned `byte[]` input, no copy (B3); Java producer needs no pin | **≤ 0.6×** | heap cell ≤ resident + 5% |
| First full access | 0.52× (owned) / 8.5× (view) | one `Object[handles]` + ≈ 8 flyweights; child offsets already in the slab | **≤ 1.0×** (stretch 0.8×) | ≈ 100 ns of allocation vs 64.6 ns stock; views' 552 ns of discovery is gone |
| Interpreter reads (full) | 4.18× (owned, after B2) | Task B4: inlined DSL nodes, monomorphic accessor calls, no exception paths, fewer layers | **≤ 2.5×** (unchanged) | AOT dispatch quality; independent of the producer |
| Peak reads (full) | 0.42–0.45× (owned) / 1.6–2.1× (view) | presence-bit test + `ByteArraySupport` load at a constant offset; no lifetime check on heap slabs | **≤ 0.5×** | same instruction count as generated-owned's field load |
| Detach + consume | 1.01× (owned) / 6.6× (view) | owning slabs: `snapshot()` is identity; session rows: one `byte[]` copy | **≤ 0.8×** | no reparse |
| Allocation, Java B/row (full / sparse / decode) | 1,132 / 1,017 / 336 (view); stock 1,630 | slab + handles array + flyweights; lengths without `String` | **≤ 700 / 450 / 560** (unchanged) | 448 B slab + 16 + 4 × handles + 8 × 24 B |
| JVM full resident / peak / interpret / detach | 1.42× / 0.96× / 1.36× / 1.33× (owned) | Java producer; no `int[]` copy, no `ByteBuffer` | **≤ 0.8× / ≤ 1.0× / ≤ 2.0× / ≤ 1.0×** (unchanged) | 94 ns decode in a 282 ns stock cell |
| Pure-Java path, contracts | n/a (eager `ProtoCodec`, 1.25× JVM) | generated Java decoder | **= the JVM rows above**; SVM no-native **≤ 0.6× small, ≤ 0.7× large** (A4 stretch ≤ 0.3×) | same code; AOT 397 ns / 423 MB/s |
| Pure-Java path, runtime plans | n/a | table-driven `SlabCodec`; Task A4 | **≤ 1.0× (table)**; A4 gate ≤ 0.6× | 166–172 ns table vs 188–201 stock on HotSpot |

Where "far beyond" is not reachable on this evidence: **large messages on the JVM** sit at 0.7× because `CodedInputStream` is already tight at 1.2 GB/s and strict validation costs the Java decoder 30% (`novf` 2.4 GB/s); and **interpreter reads** are an AOT code-quality problem that no producer changes (Task B4 owns it, and the like-for-like `stock-interop` cell of Task G1 is needed before the ratio means anything).

## 5. In-flight work: stop, redirect, keep

| Work | Ruling | Why |
|---|---|---|
| **Decoder restructure** (explicit stack, fast table, cold paths out of line, branch-free varints) | **Keep, with a new gate and a stop rule.** Adopt the `fast2` discipline exactly (§6, Task A2b). Gate: small ≤ 1.5× `hand_slab` and large ≥ 1.2 GB/s on the same worker (≈ 150 ns / 1.2 GB/s on this one). Stop rule: if after one measured iteration it is still > 1.25× `fast2` (> 175 ns small), ship what exists; do **not** escalate. | `fast2` is the measured ceiling of the shape (1.43× hand); the production loop's gap to it is register traffic and record open/close, both addressed by the discipline. 1.25×-of-hand was never reachable by any interpreter. |
| **Task E3** (per-schema native codegen) | **Withdraw.** | Buys 1.4× over `fast2` natively at the price of a Rust toolchain in every consumer's build, contradicting the prebuilt-native release matrix; irrelevant on the JVM (Java gen is faster than a downcall + native decode). |
| **Task A3** (Java producers) | **Promote to the critical path and widen.** The generated decoder is the JVM producer and the SVM no-native fallback; its Step 4 becomes an x86 investigation first (span form 0.47× vs slab form 0.93× on the same worker class: `arraycopy`/`fill`/`VarHandle` on short lengths, `pos` as a field), then the SVM AOT measurement of both forms, then the validator fast path. | The JVM lead is proven for the shape on x86 and for the writer on aarch64 only; the pure-Java path is a release gate. |
| **Q2 ruling** (Rust on SVM until AOT Java is within 1.1×) | **Decided by §2.2: Rust stays the SVM default.** AOT `java_gen` is 397 ns against ≈ 200 ns for the fast-table cell (2.0×) and ≈ 286 ns for today's production cell (1.4×). Task B3 (pinned arrays) stays on the critical path; the Java producer is the SVM fallback and the JVM default. Revisit only if Task A4 beats the native cell on SVM. | Measured, not estimated. |
| **Task B4** (interpreter reads) | **Keep, unchanged.** | Independent of the producer. |
| **Task E1** (parse-time projection) | **Keep; add a lazy-children mode.** | `table_none` at 0.28× of a full decode bounds the sparse win. |
| **Task A4** (new: Truffle-compiled decoder for runtime plans) | **Add, evidence-gated** (§6). | The pure-Java runtime-plan path is at parity with stock on HotSpot and slower on SVM; A4 is the only runtime route to the generated shape on both. |
| **`become` / tail calls** | **Do not adopt.** | Measured 14% slower than the loop; nightly-only. |
| **upb fast-table reference (Q8)** | **Closed as inconclusive** (§2.3: built with the flag, ratio to C++ unchanged, engagement unverified). | A competitor number, not a design input: the discipline is already taken from its source; a verified run would need the generated mini-table inspected for fast-table entries. |

## 6. Concrete task changes

### Task A2b — Restructure the Rust slab decoder to the `fast2` discipline (replaces the restructure's current brief)

**Files:** `protobuf-buffa/native/slab.rs` (decoder core), `slab_tests.rs` (unchanged expectations: goldens, differential corpus, failure injection, allocation probe), `benchmarks/results/protobuf-slab-native-<sha>/` (re-measure with the retained probe, same run as `hand_slab` and `fast2`).

**Shape (from `probe.rs` `run2`/`Fast2`):**
- State of the current record in locals: `p: *const u8`, `limit: *const u8`, `base: *mut u8`, `hasbits: u32`, `e: *const FieldEntry` (the predicted next entry), `mark` (pending length at record open). Nothing else live across fields. The decoder context (`out`, `cursor`, `handles`, `records`, frames, pending, merges, unknown) is one struct reached through one pointer; cold paths take `&mut Ctx` and return one word.
- One `FieldEntry` of 16 bytes per field: `tag u32`, `slot u16`, `child u8`, `kind u8`, `ordinal u8`, `next u8`, flags (`explicit`, `packable`, `special`). The hot `match` is on `kind` with ≤ 12 arms; every rare case (`special`: oneof member, map key/value, closed enum, repeated scalar) is one arm that calls out of line.
- Explicit record stack (`Frame` of 32 bytes: parent `t`, `base`, `limit`, `hasbits`, `next`, `mark`, `ordinal`, `singular`, `slot`); `message` pushes and continues in the same loop; close pops and either writes the parent slot and bit or pushes to pending. Depth is the frame index; `maxDepth` is checked at push.
- Record open: aligned bump, zero with 8-byte stores, handle write, in ≤ 10 instructions; record close in the common case (no merges, no unknowns, no required fields, no map) is `write hasbits; if pending > mark { lists }`.
- Lists: runs of one ordinal in pending are written directly (one pass); interleaved ordinals fall to the existing two-pass path.
- Strings: `put_string(ctx, src, n) -> Result<u32>` out of line, returning the header offset; ASCII word check and the scalar DFA stay as in A2.
- `NEEDS_CAPACITY`, sizing walks, merges, map dedup, unknown table: unchanged semantics; the frame-kept-record path stays for allocations that do not fit.

**Gate (same worker, retained probe):** `slab ≤ 1.5 × hand_slab` small and holdout; large ≥ 1.2 GB/s; byte-identical goldens and corpus. Stop rule: one measured iteration past the first; if `slab > 1.25 × fast2` on small, record and ship.

**Commit** — `perf: decode protobuf slabs with an explicit record stack and register-resident field state`

### Task A3 — Java producers (amended)

Add a Step 0 (x86 writer diagnosis, probe-first, retained under `benchmarks/results/protobuf-slab-java-<sha>/`): run the `SotaProbe` span form and slab form side by side on an x86 RBE worker with a CPU reservation; bisect the 2.3× between them (`System.arraycopy` for ≤ 16-byte payloads vs two unaligned word stores; `Arrays.fill` per record vs explicit 8-byte stores; `VarHandle` view stores vs `Unsafe`-free plain stores; `pos` as a field vs returned). Gate for proceeding: slab form ≤ 1.2× span form on x86. Add to Step 1: `svm_generated_decoder_measured` (a `native_image` of both forms, `svm.txt`, against the Rust cell on the same worker). Add to Step 3: the validator's 8-byte ASCII word check and whatever Step 0 found. Step 4 hypotheses become: generated ≤ 0.55× `Type.parseFrom` small on x86 HotSpot (span form measured 0.47×), ≤ 0.8× large; table-driven ≤ 1.0×; SVM generated reported, not gated (Q2 is decided: Rust stays).

### Task A4 — Truffle-compiled slab decoder for runtime plans (new, evidence-gated)

**Files:** `protobuf-runtime/.../SlabDecodeNode.java` (a `RootNode` per `ProtoPlan` type: `@ExplodeLoop` over the type's PE-constant `FieldEntry[]`, comparing the tag against each entry's constant and executing the constant-slot store; nested types as direct-call children; the explicit record stack of A2b in a frame-local `int[]`), `SlabCodec.java` (selects the compiled root after `N` decodes of a plan, falls back to the table), tests `compiled_decoder_matches_table_byte_for_byte`, `graph_size_bounded` (≤ the 10,000-node budget for a 64-field type).

**Gate:** probe first (a `java_test` + `native_image` in the `SotaProbe` style): compiled-root decode ≤ 1.2× `java_gen` on HotSpot and on SVM for `Type`; graph size within budget. If missed, the task closes with the table-driven path as the runtime-plan floor and the ruling recorded.

**Commit** — `perf: compile runtime protobuf plans into Truffle decoder roots`

**Measured ruling (2026-11-07):** A4 is closed under this stop rule. Both runtimes pass
Type/wide64 compilation and parity, but compiled/generated is 1.164× on JVM and 16.367×
on SVM, so the joint ≤ 1.2× gate fails. Table-driven Java remains the runtime-plan floor;
no automatic compiled decoder is adopted. See [retained evidence](../../../benchmarks/results/protobuf-compiled-decoder-dab2dbc5/README.md).

### Task E1 — Projection (amended)

Add a `Projection.lazyChildren()` mode: unwanted singular/repeated message fields keep `(input offset, length)` in the slab under an `EXTERNAL`-like child header and are decoded into the slab (appended) on first access; the slab is then `BORROWING` until every lazy child is realised or `retain()` copies the input. Gate: sparse resident ≤ 0.5× full resident on the holdout.

### Task E3 — removed. Task G2 — document the producer matrix of §3 in `docs/BUFFA.md` and `docs/SLAB.md`.

### §8 targets — replaced by §4 above.

## 7. Open questions for the user

1. **Withdraw Task E3 outright, or keep it as an opt-in** for consumers who already build Rust (a `rust_library` per contract set, never required)? It is the only *measured* route to ≥ 1.5 GB/s on large messages on SVM (`hand_slab` 1.92 GB/s vs `fast2` 1.33) and buys 1.3–1.45× on SVM decode-only; it buys nothing on the JVM. Recommended: withdraw, and let Task A4 (which could reach the same shape at runtime) be the experiment that decides whether 1.5 GB/s matters enough to revisit.
2. **SVM default producer.** §2.2 measured AOT `java_gen` at 2× the expected Rust cell, so Rust stays the SVM default under the Q2 rule and generated Java is the no-native fallback (0.53× stock there). Confirm, and confirm that Task A4 (Truffle-compiled decoder) may *replace* the native path on SVM if it measures faster — that would make the pure-Java path the universal one.
3. **Runtime plans on the pure-Java path.** Accept table-driven parity with stock (≈ 0.9× small on HotSpot, slower on SVM) as the floor and fund Task A4 (Truffle-compiled decoders), or accept parity and close? Recommended: fund A4, probe-first.
4. **Large messages on the JVM.** 0.7× stock is the measured ceiling for strictly validated decode in Java (stock is already 1.2 GB/s there); lax validation would reach 2.4 GB/s but Q3 chose strict by default. Is 0.7× acceptable for this cell, or should `Projection.lax()` be surfaced as the documented way to go faster?
5. **The restructure's stop rule** (one measured iteration; ship at > 1.25× `fast2`): confirm, or allow a second iteration.
6. **The Java writer on x86.** The slab-writing Java decoder is 0.93× stock on x86 HotSpot but 0.49× on aarch64 and 0.47× for the span form on x86. Is Task A3's Step 0 (bisect the writer on x86 before building the producer) the right order, or should A3 build the producer now and tune after? Recommended: Step 0 first; it is a one-day probe and it decides whether the JVM targets are 0.5× or 0.9×.
7. **Allocation for the JVM `decode` cell** stays ≤ 560 B/row (slab + owner); a session (Task D1) is the route to 0 B/row. Confirm that D1 stays after B/C rather than moving ahead of B3.
