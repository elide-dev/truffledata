# TruffleData performance, remaining work, and architecture review

**Assessment date:** 2026-11-05. **Source:** local `perf/sparse-finalization` at `bfcf22ffa1885c3f196a0478122ed5bcfec78ca0`.

This report describes the local development implementation, not a new release or a claim about current remote CI. It combines saved measurements, source inspection, and an independent adversarial review by an agent started without conversation history. No builds, benchmarks, publication, or implementation changes were performed to prepare it. Optimization work remains paused. References are relative to the repository root unless they identify an external verification run.

## Assessment

TruffleData has a credible foundation for schema-aware, specialized data access and can already operate without `protobuf-java` at runtime. It does **not** yet meet the objective of matching or beating stock across ingestion, interpreter reads, peak JIT reads, and allocation.

The latest controlled protobuf run puts generated native-view projected ingestion near stock, full consumption at **1.90× stock latency**, interpreter reads at **3.01×**, and peak reads at **1.58×**. Java heap allocation is lower, but total allocation and retained memory have not been established. The latest toolchain alignment erased the preceding projected-ingestion win and introduced a measured 6.1% regression on that fixture.

The strongest architectural concerns are that the fastest paths increasingly use format-specific access APIs, and schema knowledge is not consistently reflected in the physical result representation. The general nested native protobuf path still produces a general index; Java then builds field directories, wrappers, caches, and strings to make it useful. Concrete generated owned records already approach stock ingestion performance, which argues for measuring representation costs before replacing the parser or adding more local packing tricks. Specialized protobuf performance is not, by itself, evidence that the universal guest-access layer is delivering its intended benefit.

The strongest process concern is that evidence has become fragmented across historical reports, hand-maintained coverage inventories, narrow fixtures, and expensive native builds. We need a smaller authoritative acceptance matrix, complete cost accounting, and better isolation of compiler and representation changes.

## 1. Current performance status

### Latest controlled protobuf comparison

The latest cycle uses SVM on unclemax, pinned to CPU 2, with five fresh forks per binary, workload, and metric. Previous/candidate jobs are interleaved; timing and allocation use separate processes. There are 300 sequential process runs, with stock controls in both binaries. Results are medians of fork medians; ranges in the underlying report are **not confidence intervals**. [Method and provenance](benchmarks/results/buffa-native-views/aligned-toolchain/README.md), [raw measurements](benchmarks/results/buffa-native-views/aligned-toolchain/previous.json), [full table](benchmarks/results/buffa-native-views/aligned-toolchain/previous.md).

| Operation | TruffleData native view | Stock protobuf | TD / stock latency |
|---|---:|---:|---:|
| Mixed projected ingestion, generated getters | 659.404 ns/row | 652.602 ns/row | 1.01× |
| Mixed full ingestion and consumption, generated getters | 1,299.548 ns/row | 683.151 ns/row | 1.90× |
| Retained mixed interpreter reads | 16.367 ns/row | 5.444 ns/row | 3.01× |
| Retained mixed peak reads | 8.438 ns/row | 5.352 ns/row | 1.58× |
| Projected-ingestion Java allocation | 341.5 B/row | 1,776.75 B/row | — |
| Full-ingestion Java allocation | 1,406.25 B/row | 1,776.75 B/row | — |

Several qualifications materially affect interpretation:

- **These are opt-in native views.** `BuffaPlan.decodeView()` requests this representation; default `decode()` remains eager. Text parsing also uses the eager route. View support excludes certain schema shapes and falls back for singular-message merges. The latest numbers are not default-backend or arbitrary-schema results. [Selection](protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/BuffaPlan.java#L66), [eligibility](protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/NativeRow.java#L42).
- **Ingestion is parse, consume, and release.** Native input already exists in unmanaged memory; stock receives an existing heap byte array. Plan compilation, context startup, and initial input allocation/copy are outside the measurement. This is appropriate for an adopted-input scenario but does not establish byte-array-to-result parity. [Fixture preparation](benchmarks/comparison/Comparison.java#L414), [timed operation](benchmarks/comparison/Comparison.java#L501).
- **Retained reads are warm reads of 32 reused rows.** They do not measure first access, a streaming sequence of new owners, large working sets, or arbitrary schema churn. [Fixture and sampling](benchmarks/comparison/Comparison.java#L28).
- **Stock mixed guest access crosses an explicit Truffle boundary.** Its projection body is AOT because direct stock collection access is rejected by the pinned runtime’s JIT blocklist. This is an integration comparison, not fully inlined stock versus fully inlined TD code. Ingestion comparisons do not use this guest boundary. [Stock guest root](benchmarks/comparison/Comparison.java#L732).
- **Java allocation is not total allocation.** Native input, index capacity, result metadata, allocator overhead, and compiler/background-thread allocations are excluded. Retained-read measurements include harness allocations: 2 B/row for interpreter and 1 B/row for peak on both sides.

Other cells help locate the problem. Generic native-view ingestion takes **750.236 ns projected** and **1,876.696 ns full**. Our concrete generated-owned records take **661.914 ns projected** and **693.772 ns full**, with 1,800.5 Java B/row. Their retained peak projection takes **2.744 ns**, although the stock boundary caveat prevents treating that as an equivalent direct-getter comparison. Buffa scalar ingestion is **50.113 ns / 80 Java B**; this cycle has no matching stock scalar cell. [Complete current table](benchmarks/results/buffa-native-views/aligned-toolchain/previous.md).

### What the current cycle accomplished

The recent work removed eager Java-tree construction from eligible native-view decoding, specialized JVM index access, separated row representations, and reused field counts for size-only getters. The count-index change improved generated projected ingestion from **702.951 to 616.153 ns/row** and Java allocation from **421.5 to 341.5 B/row** in its own paired run. Full consumption also improved, but wider field directories added 24 Java bytes in that fixture. [Count-index evidence](benchmarks/results/buffa-native-views/index-counts/README.md).

A subsequent compact-directory experiment saved those bytes but regressed latency and was rejected. Its measurements and exact patch remain available. Large nested-count boundary tests were retained. The short-ASCII experiment was only a standalone diagnostic on an older host compiler; it is not an implemented adapter improvement. [Rejected compact representation](benchmarks/results/buffa-native-views/compact-counts/README.md), [ASCII probe limitations](benchmarks/results/buffa-native-views/ascii-probe/README.md).

Build investigation also found that dependency crates had been compiled without production optimization. Defaults now optimize dependencies and enable Rust and C++ ThinLTO. The final cycle aligned Elide toolchain **2026.11.1** with its recommended **Rust nightly-2026-10-09**. Compiler actions verify the selected tools, Buffa/adapter optimization, and target-level LTO. Full sysroot/cross-language LTO is not proven; integration is tracked in [toolchain#13](https://github.com/elide-dev/toolchain/issues/13).

That alignment regressed projected ingestion **621.784 → 659.404 ns/row (+6.1%)**, with non-overlapping fork ranges and nearly unchanged stock controls. Full ingestion rose 1.2% with overlapping ranges. Reads and Java allocation stayed essentially unchanged. The experiment changed both compiler/toolchain and the linked image, so it does not identify a particular compiler pass or prove Buffa caused the regression. The requested alignment was retained with this result documented. [Upgrade evidence](benchmarks/results/buffa-native-views/aligned-toolchain/README.md).

### What profiling says—and does not say

The latest saved native-view profile predates the count-index change and compiler alignment. In it, native `Decoder::binary` accounts for **31.42% of projected** and **14.48% of full** hardware self samples. Full consumption also spends substantial samples in `TypedRow.get/getLong`, `NativeRow.link/entry/index`, array materialization, row decoding, and string construction. These findings implicate both parsing and the Java representation/access layer. [Profile and caveats](benchmarks/results/buffa-native-views/thinlto/profile/README.md).

The count-index work already addressed the profile's size-only wrapper/count-scan problem; it should not be proposed again as if untouched. A new profile is required to explain the upgrade regression and rank today's remaining costs. Hardware samples include startup, JFR allocation samples are counts rather than bytes, and native allocations were not captured. We cannot derive an end-to-end speedup ceiling by simply adding these percentages.

### Other TruffleData use cases

These are relevant earlier measurements, **not remeasurements under the latest toolchain**:

| Area | Evidence | What it establishes |
|---|---|---|
| Typed scalar JSON | At `23f969a`, specialized simdjson ingestion 177.693 ns versus Gson streaming 893.663 ns; interpreter 3.698 versus 1.276 ns; peak 1.497 versus 0.948 ns; ingestion Java allocation 56 versus 11,288 B | Strong ingestion result for three required scalar fields; retained reads still slower. Gson creates a reader through `InputStreamReader`; this is not a general parser ranking. |
| MySQL INT4 | Earlier stable-host decoder fixture: static-row ingestion 65.20 ns / 80 B versus stock decoder result 23.37 ns / 16 B | Cost of decoding plus a named typed row versus a decoded scalar; not JDBC query throughput. |
| PostgreSQL INT4 | Same cycle: static-row ingestion 56.78 ns / 80 B versus stock decoder result 17.07 ns / 16 B | Same limitation; server, network, `ResultSet`, and full adapter lifecycle are outside this benchmark. |
| SQLite | Older macOS JMH query-retain results: plain 401.83, batch 379.46, static 388.61 ns/row, with overlapping intervals; initial allocations 194.35 / 231.47 / 268.43 B | No accepted general query speedup. A later static-only follow-up reduced allocation to 236.53 B/row. |

Sources: [corrected JSON results](benchmarks/results/json-unclemax-23f969a/json-comparison.md), [JSON baseline definitions](benchmarks/comparison/JSON.md), [database decoder results](benchmarks/results/svm-unclemax-7260cb8/comparison.md), [SQLite methodology and follow-ups](docs/BENCHMARKS.md#initial-sqlite-elapsed-baseline).

The JSON work is an instructive process success: profiling discovered that the supposed peak test compiled a wrapper while calling an AOT helper. Correcting class initialization and requiring a compiled marker inside the helper exposed the real behavior. Current corrected JSON traces also check calls, graph allocations, and graph size. Historical incorrect peak interpretations remain unsuitable for comparisons. [Investigation](benchmarks/comparison/JSON.md#profile-before-further-tuning).

### Readiness is broader than these scores

There is substantial existing assurance: strict validation, scoped lifetime checks, bounded specialization, graph/allocation budgets, native reachability checks with negative controls, real-driver acceptance, Codecov/JUnit reporting, and stock-free JVM/SVM publication consumers. `protobuf-runtime` already separates descriptor-free runtime consumption from the optional protobuf-java compatibility bridge. These are assets to preserve. [Truffle assurance scope](docs/TRUFFLE.md), [publication and consumer tests](docs/PUBLISHING.md).

The last upgrade ran **five selected tests** and built the SVM comparison executable; it did not rerun every repository, strict-mode, publication, platform, or guest integration gate. Experimental JSON remains source-only. The new protobuf work uses native ABI 8, whereas published 0.6.0 documents ABI 7; bindings and native libraries must be qualified and released together. [Exact verification](benchmarks/results/buffa-native-views/aligned-toolchain/verification.txt), [ABI declaration](protobuf-buffa/native/buffa.seam#L1), [0.6.0 notes](docs/RELEASE_0_6_0.md).

## 2. What remains to be completed

This is a proposed completion sequence for when optimization resumes. It is not a request to execute these tasks now. “Beat stock in all regards” needs named workload, input, ownership, semantic, and platform contracts; a finite test suite cannot guarantee that statement for every possible workload.

| Priority | Work | Completion evidence |
|---|---|---|
| P0 | Establish one current acceptance matrix | Explicit cells for ingest, first access, retained interpreter/JIT access, full traversal, serialization and detachment; input ownership, schema features, and allowed fallback recorded for each. Include representative Elide/TruffleCEL workloads, not only protobuf well-known-type fixtures. |
| P0 | Test the universal access proposition | Run the same meaningful guest computation over protobuf, JSON, JDBC and live objects using ordinary interop, shared typed access, and specialized access where available. Identify which improvements reach the intended consumer and which require format-specific APIs. |
| P0 | Attribute the upgrade regression | Fresh profile of current code and controlled compatible compiler/linker comparisons, with identical implementation and stock controls. Recover projected performance or make the regression an explicit accepted tradeoff. Preserve the required toolchain alignment unless a supported alternative is chosen. |
| P0 | Make cost accounting complete | Report Java allocation, native requested/peak/live bytes, allocation counts, copied payload bytes, input preparation, retained input amplification, and snapshot cost. State allocator/parser accounting exclusions rather than presenting a partial number as total memory. |
| P0 | Extend assurance to new access surfaces | Add `NativeRow`/`NativeArray` and JSON access methods to an explicit coverage/reachability inventory; require close/thread/bounds/null/presence, host cache mutation, fallback, and compiled recovery cases. Reuse existing strict and negative-control infrastructure. |
| P1 | Reduce the full-consumption and retained-read gaps | Compare direct schema-specific native results, current index views, and concrete generated owned records under the same contracts. Accept changes only with multi-cell latency/allocation evidence and unchanged correctness budgets. |
| P1 | Define and extend native-view eligibility deliberately | Publish support/fallback reasons for wide schemas, maps, real oneofs, custom constraints, required fields/merges, and text routes. Add workloads for supported and fallback paths before broadening a fast-path claim or changing defaults. |
| P1 | Finish protobuf replacement acceptance | Expand differential/property testing across owned/generated/native/Java-fallback binary and text paths, including unknown-field policy, merge/presence behavior, malformed/truncated data, enums, unsigned values, Unicode and serialization. Define the supported compatibility subset and keep unsupported editions/extensions/MessageSet explicit. Runtime independence is already implemented; unrestricted protobuf-java feature equivalence is not. |
| P1 | Complete JSON's product surface | Add the scoped JVM API, snapshots/adopted-input ownership, nested/repeated typed shapes, general guest interop where absent, and parser lifetime/scratch accounting. Facades in `JSON_PLAN.md` need their own conformance work and publication decisions. Do not treat raw FFM ABI tests as a finished JVM guest API. |
| P1 | Qualify real consumers | Exercise new scoped views and generated access in actual Elide/TruffleCEL integration, including context close, mixed schemas, errors, long-lived retention, and fallback. Extend publication consumers to the new view ABI. Existing test-language and Maven smoke tests are useful but not proof of application integration. |
| P2 | Finish release acceptance | Run the required complete pinned-runtime suite, native positive/negative JIT gates, consumer tests and intended platform checks; reconcile docs/graphics with accepted results; then integrate and release a coherent ABI/artifact set. A local benchmark pass is not current-main CI or publication evidence. |

This does not require expanding database connection-management work. Driver correctness exists as a separate integration concern; the shared contracts, representations, lifetimes, and guest execution paths should govern this phase. SQL planning remains the database's responsibility.

## 3. Architecture and development-process review

The findings below distinguish **observed implementation facts**, **measured effects**, and **hypotheses**. They do not establish a new correctness bug simply because an optimization is possible. The independent reviewer challenged the code and evidence without being given the preceding optimization narrative; the final synthesis also checks its findings against documented contracts.

### A. The fastest paths increasingly bypass the shared access abstraction

**High priority; source-backed design gap.** `DataLibrary` supplies schema, boxed access, three primitive reads, and presence, while `ReadLengthNode` reads the value before determining its length. Protobuf has a separate `ProtoAccess.getSize()` operation that can use native repeated-count metadata directly. JSON introduces `JsonRecord` and `JsonAccess`, with accessors bound to that concrete JSON class; the record is not an `AbstractRow` or exported interop object. These paths demonstrate format-specific optimization, but the benefit does not automatically reach arbitrary guests using the ordinary shared protocol. [Core protocol](core/src/main/java/dev/elide/truffledata/DataLibrary.java#L7), [length path](core/src/main/java/dev/elide/truffledata/ReadLengthNode.java#L23), [protobuf size path](protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/TypedRow.java#L83), [JSON record](json/src/main/java/dev/elide/truffledata/json/JsonRecord.java#L13), [JSON accessor](json/src/main/java/dev/elide/truffledata/json/JsonAccess.java#L33).

**Recommendation:** define the smallest backend-neutral access-plan contract that serves real guest computations: typed scalars, presence/null, length, child records, typed sequence traversal, and ownership capabilities. Preserve format-specific storage and generated concrete classes. First prove one representative computation across backends without material overhead; do not respond by adding a large universal intermediate representation. Measure direct stock access, an ordinary guest adapter, generic TruffleData, generated owned records, and scoped native views under appropriate equivalent contracts. If a specialized route is the product feature, document that requirement explicitly.

### B. Schema-aware access still sits on general-purpose storage

**High priority; source-backed mechanism, measured symptom, unproven remedy.** The general typed Rust decoder traverses the schema and writes a `Vec<Field>` structural index; the compact scalar path is an exception. A Java `NativeRow` later builds a field directory on first lookup, materializes nested row/array objects, and promotes a cache after multiple complex fields are read. First array-element access allocates position and value arrays and scans siblings. Full reads eventually create much of the object graph that lazy views were intended to defer. [Native production](protobuf-buffa/native/typed.rs#L142), [field indexing](protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/NativeRow.java#L178), [materialization](protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/NativeRow.java#L268), [arrays](protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/NativeRow.java#L351).

This is a defensible generic fallback, but schema-static execution pays avoidable discovery and indirection costs. Our own concrete generated records are near stock on full ingestion, while native views remain about 1.9× slower. That is stronger evidence for a representation experiment than for blaming Buffa as a library. The native binary implementation is our schema-driven loop using Buffa encoding primitives, not simply a call to a generated Buffa borrowed-message parser. [Imports and decoder](protobuf-buffa/native/typed.rs#L1).

**Recommended experiment:** for one representative nested schema, decode directly into schema-specific scalar slots, presence masks, child spans, and repeated offsets/counts. Bind generated accessors to stable layouts; avoid reconstructing facts already known during parsing. Compare sparse projection, first/full access, repeated reads, input sizes, and retained bytes. Keep the generic tape for arbitrary schemas where it earns its flexibility. Do not roll out a second universal representation before this experiment demonstrates a benefit.

### C. Borrowing is being treated too close to a universal performance strategy

**High priority; demonstrated tradeoff.** Native views reduce copying of payload bytes and delay materialization, but also introduce lifetime checks, pointer/index loads, metadata, retention of backing allocations, and first-access work. A tiny result can retain a large input. Full traversal can pay both index construction and Java materialization. Low Java allocation alone cannot establish that this is a better representation.

Detachment is especially consequential: binary `TypedMessage.snapshot()` reparses the original wire with the owned codec, preserving unknown fields but doing another decode. The cost of handing a scoped result to durable storage or another thread is therefore not represented by the warm scoped-read score. This is a deliberate semantic choice, not evidence of unsafe ownership. [Snapshot implementation](protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/TypedMessage.java#L61).

**Recommendation:** expose three deliberate execution choices: scoped borrowed views for sparse access, concrete owned results for dense/repeated access, and fused decode/project for one-pass consumption. Selection should follow an explicit consumer access/ownership contract or measured stable policy. This is representation selection, not SQL query planning. Projection must still validate unconsumed input as required by the contract. Keep generic/dynamic access available without forcing it into every static fast path.

### D. Ownership and compiler facts need a clearer capability model

**High priority design risk; not a newly proven lifetime defect.** Scoped native rows correctly require explicit detachment before entering ordinary owned storage. Separately, static/batch containers may retain live `DynamicRecord` descendants. That behavior is deliberate and documented: an immutable outer slot does not imply a transitively immutable object graph, and ingestion budgets do not bound future mutation. [Ownership admission](core/src/main/java/dev/elide/truffledata/Values.java#L25), [documented live semantics](docs/TRUFFLE.md#expanded-shared-model-gates).

The risk is that the API's broad notions of “owned,” “immutable,” and “validated” do not themselves encode these distinctions. They matter to cross-thread transfer, retention limits, snapshot semantics, and which facts may safely become compilation assumptions.

**Recommendation:** specify capabilities for scoped borrowed, durable immutable, and live mutable values, including their transitive behavior. Make snapshot/detach boundaries explicit and test them across nested representations. Share proven lease/invalidation mechanics where appropriate, but do not erase real ownership differences just to unify APIs. Existing mutable caches must remain mutable to the compiler; `@CompilationFinal` is not a substitute for invalidation.

### E. The benchmark suite is good at local regressions and weaker at product decisions

**High priority evidence gap.** Current interleaving, checksums, escape of outputs, stock controls, tier checks, and rejection of negative experiments are valuable. However, 32 warmed fixture rows, pre-created native input, one fixed mixed schema, and boundary-limited stock getters answer a narrower question than general application performance. The newest 15-cell cycle omits serialization, WKT text, bytes/aliasing, schema churn, and database/JSON comparisons. Those omissions are reasonable for a bounded experiment but must not become the product's scorecard.

**Recommendation:** maintain two benchmark layers. A small diagnostic layer isolates parse/validate, native transition, index construction, first access, compiled retained access, materialization, and release. A representative end-to-end layer includes ingress preparation, equivalent output/ownership, cold versus warm access, input/retention size variation, and actual guest consumption. Include both native-input and heap-input cohorts, plus a native-only parser/result baseline. Label exact comparison semantics, including the stock boundary.

Use a parameterized holdout corpus that optimization work does not continually reshape: 1/2/3/8/32/65 fields; optional, map, and oneof variants; reordered and duplicate fields; custom validation constraints; sparse/full reads; fresh/retained owners; and larger working sets. The compact scalar route only accepts at most two standard-contract INT32/INT64 fields, matching the Timestamp fixture particularly well. A third field or a map can change representation entirely. Those cliffs need explicit measurements. [Compact eligibility](protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/BuffaPlan.java#L35).

Adopt the corrected JSON helper-level compiled marker and compiler-trace checks for corresponding protobuf cases. A valid last-tier root alone does not prove all expensive access helpers are runtime compiled. Preserve permitted boundaries intentionally rather than optimizing them away indiscriminately. [Protobuf tier check](benchmarks/comparison/Comparison.java#L718), [JSON correction](benchmarks/comparison/JSON.md#profile-before-further-tuning).

### F. Schema identity can cause a specialization cliff in ordinary reuse

**Medium priority; mechanism proven, workload impact unmeasured.** Schema identity is the cache key, caches generally allow three identities, and generic named lookup falls back to a boundary with a linear scan. Recreating logically equivalent schemas for new adapters or query executions can saturate the same guest call site. Existing churn tests establish bounded fallback and correctness, which is valuable; they do not establish acceptable application throughput after churn. [Schema identity and lookup](core/src/main/java/dev/elide/truffledata/Schema.java#L7), [typed cache](core/src/main/java/dev/elide/truffledata/ReadLongNode.java#L19).

**Recommendation:** measure actual schema/adapter creation lifecycles. Prefer explicit reusable caller-owned plans or bounded owner-local canonicalization over unbounded global interning. For wide schemas, evaluate an immutable name-to-ordinal index that preserves ambiguous-name rejection. Any change must keep context ownership and cache-growth bounds intact.

### G. Safety gates are strong but their inventory can lag the implementation

**High priority; concrete process gap.** The required access-method coverage list contains many core and Buffa classes but omits `NativeRow` and its `NativeArray` implementation, as well as JSON access classes. This does **not** mean those classes are untested; it means the mandatory complete-method inventory does not cover the newest hot path. JVM Codecov coverage also does not establish Rust/C++ branch coverage or native memory safety. [Coverage inventory](.github/scripts/coverage_contract.py#L7).

**Recommendation:** maintain one explicit supported-operation manifest that drives coverage requirements, JVM/SVM compiled cases, expected rejection cases, and release acceptance. Keep hand-authored semantic and lifetime tests, randomized/differential corpora, native sanitizer checks, and negative JIT controls independent enough to detect shared mistakes. Test native-produced proofs against an independent strict validator; merely checking the same schema interpretation twice can preserve the same error.

`PolicyTest` also uses literal annotation counts and regular expressions as review triggers, and its module inventory omits Buffa and JSON. Those checks can signal change but cannot prove correct boundary placement or compilation safety. Replace brittle count-based proxies with named contracts and executable checks where practical, while retaining explicit review for new access surfaces. [Policy inventory and checks](tests/src/test/java/dev/elide/truffledata/PolicyTest.java#L77).

The supported JIT guarantee remains conditional on the pinned runtime, roots, and operation inventory. Neither method coverage nor a clean Native Image build proves arbitrary guest compositions are blocklist-free. The report should preserve that scope rather than promise an impossible universal guarantee.

### H. Experimental throughput is constrained by build cost and weak experiment isolation

**High priority process improvement.** The opt-level discovery and combined compiler upgrade show that build configuration can dominate an apparent algorithm result. The full native image is expensive enough that rebuilding it for every speculative micro-change encourages narrow testing and makes causal attribution slow. Hand-copied per-experiment scripts and scattered provenance increase review cost.

**Recommendation:** use a staged loop: form a profile-backed hypothesis; run native-kernel or JVM correctness/diagnostic checks where they answer the question; build one candidate SVM image; compare multiple relevant cells with a preserved baseline; then run the broader release gates only for accepted changes. A kernel win remains diagnostic until measured through the real adapter. Do not mutate build inputs during a live invocation or run simultaneous timing/profiling workloads on the measurement host.

Use one comparison runner with an experiment manifest recording source and binary hashes, dirty patch, exact build flags/tool versions, fixture and ownership policy, CPU/affinity, backend/fallback identity, and test invocation. Preserve rejected variants without leaving them in production code. Separate compiler upgrades from algorithm comparisons; examine generated code when a toolchain change moves a hot fixture. Verify effective optimization and LTO actions automatically. Sysroot LTO remains an upstream integration item, not a reason to assume all libraries are already cross-optimized.

### I. Documentation is no longer a reliable single statement of readiness

**Medium priority; confirmed inconsistency.** `docs/PRODUCTIONIZATION.md` ends with 0.1.0-era increments, while publishing documents describe 0.6.0 and stock-free consumers. README graphics describe older implementations. JSON documentation contains chronological “pending” notes followed by later implemented increments. These documents are useful history but cannot be interpreted as a current checklist without source reconciliation.

**Recommendation:** keep history as history and generate a concise current capability/acceptance ledger from the operation manifest and retained results. Every performance graphic should carry source/toolchain/host/ownership provenance and distinguish published from local work. Track “implemented,” “tested on this platform,” “measured,” and “published” separately. A release can be consumable without satisfying every performance target; a fast local fixture can be unready for release.

## Recommended next decision

When work resumes, first establish the current acceptance matrix, complete memory/input accounting for its leading fixtures, and profile the aligned binary. Then run one bounded architecture experiment comparing the current tape-backed view with a directly populated schema-specific result for nested protobuf. The existing concrete generated-owned path is the control and a viable implementation option, not a benchmark to ignore.

Choose the result by workload-level evidence, including full consumption and lifetime cost. Preserve contracts, strict validation, bounds, thread checks, and supported JIT gates. If the experiment does not improve the intended workload class, reject it and retain the measured implementation. This gives the next cycle a concrete architectural decision and an explicit stop condition instead of another open-ended sequence of local tuning.

The independent review found no basis to allege fabricated measurements or an unsafe native lifetime implementation. Its strongest criticism is the gap between demonstrated specialized improvements and the universal-access proposition, together with unmeasured costs at ownership and guest-language boundaries. The deliberately supported live nested-object semantics were reviewed and are classified here as an API design risk, not an unintended immutability bug.
