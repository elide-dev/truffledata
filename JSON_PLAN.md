# Native JSON and Java facade implementation plan

Status: Myna foundation shipped in v0.7.0; TruffleData implementation planned, 2026-11-04. Myna supplies the producer-neutral native value ABI and readers. TruffleData owns the C++ simdjson backend, schema contracts, scoped guest objects, and Java JSON facade adapters. Existing Buffa support remains an independent protobuf backend.

The goal is direct JSON parsing into typed results or a stable Myna structural index, with adopted input and borrowed text wherever their lifetimes permit. The system must support arbitrary JSON and known schemas, and preserve enough information to implement JEP 540 faithfully.

## Architecture and responsibilities

```
leased, padded JSON input -> C++ simdjson -> typed slots and presence masks
                                        -> generic Myna value index
                            -> scoped TruffleData views -> guest execution
                            -> durable facade values / explicit snapshots
```

Myna owns:

- Canonical C layouts and C++ producer helpers, independently usable without Rust or Serde.
- A versioned ABI distinguishing inline text from region-backed text, including borrowed short strings.
- Exact JSON-number tokens alongside i64/u64/f64 values; no mandatory decimal-to-double conversion.
- Rust and SVM readers, SIMD Unicode integration, layout/fingerprint checks, and producer conformance tests.
- Explicit storage/lifetime contracts and packaging for downstream builds.

TruffleData owns:

- Pinned C++ simdjson, its parser pool, input padding, JSON validity and semantic policies.
- `JsonInput`, `JsonPlan`, and `JsonMessage` APIs (proposed names), native result ownership, snapshots, and Truffle invalidation.
- Known-schema slot layouts and generated/runtime-compiled schema plans.
- JSON object/array/scalar interop and JDK/Jackson/JSON-P/Gson adapter artifacts.
- Bazel integration, native attachments, backend selection, and application-facing error translation.

The current Myna Bazel overlay compiles `src/main/java/**/*.java` as the generator. Myna 0.7.0 preserves that source boundary and puts value readers in `src/values/java/**/*.java`, packaged as `myna-values.jar`. Add a separate Bazel runtime target with explicit Graal Native Image SDK dependencies, plus a C++ headers target for `include/myna/values.h` and `values.hpp`. Pin the released Myna source revision and update source archive checksums; do not depend on a developer checkout.

## Storage and zero-copy contract

| Operation | Intended behavior |
| --- | --- |
| Padded native input | Adopt a sealed input allocation with an explicit release callback |
| Heap or insufficiently padded input | Copy explicitly into suitable storage; report copied bytes |
| Validated unescaped text | Borrow input slices through retained input regions where the pinned parser API supports it |
| Escaped text | Decode once into retained storage; avoid a second copy into Myna |
| Short strings and keys | Inline by default; permit explicit borrowing at any length |
| Numbers | Preserve exact token text where required; decode typed numeric slots only under checked contracts |
| Structure | Write an index or typed result metadata; this work is not zero-copy |
| Java String / snapshot | Materialize explicitly under the facade's ownership contract |

The Myna value descriptor references multiple immutable regions, not a single owned string buffer. Region identity and offsets are explicit; no raw pointer is disguised as an offset. Region bounds and total limits are checked, while the owner remains responsible for valid allocations and lifetimes. The storage discriminator is independent of text length. A new value ABI version is required; v2 consumers must not silently interpret it.

The adapter must use public simdjson APIs and verify their lifetime rules against a pinned release. `get_string()` is not assumed to return input-backed data: decoded strings may reside in parser scratch. Preferred retained results write escapes into message-owned storage so parsers can be returned to a pool. Borrowing parser-owned storage instead retains the entire parser until all dependent views are released. A region registry must not assume parser buffers are contiguous unless the public API guarantees it.

Parsing requires `SIMDJSON_PADDING` accessible bytes beyond the logical input extent for the padded API. Neither capacity nor padding may be inferred from a bare address/length. Input adoption records allocation capacity separately. Unpadded handling, if offered, is explicit and benchmarked. Native parsing uses normal VM transitions, never an unbounded no-transition leaf.

## Known-schema and arbitrary JSON paths

### Known schema

Compile reusable JSON plans from TruffleData schemas, at build time or runtime. The parser traverses each object once, handles field order and escaped member names correctly, validates types/presence/nullability and bounds, and writes typed slots plus presence/null masks. Start with flat scalar records using Buffa's fixed-result precedent, then add nested objects and repeated values. Do not require generic Myna tape construction for simple fixed records.

Bind generated getters to stable plans and field ordinals. Keep contract and lifetime checks in compiled guest reads. Add fused parse/project operations after the full ingestion path is correct; projection must not silently relax whole-document validation.

### Arbitrary JSON

Traverse simdjson once and write Myna nodes directly without an intermediate Rust Serde value, C++ DOM, or Java object tree. The completed index supports repeated/out-of-order guest reads. Do not expose On-Demand cursors as reusable guest values. Arrays, objects, and scalars are all valid roots.

Keep the underlying entry sequence available for a preservation mode. Named lookup and duplicate handling are explicit policies. Cache or index lookups only within the limits and lifetime of the document. Avoid one native call per field; ingestion crosses once per document or batch and guest access reads stable data.

## Ownership and guest execution

Reuse the Buffa ownership model: sealed input leases, thread-confined scoped messages, explicit detachment, and Truffle assumptions invalidated on close. `ViewOwner` is currently sealed and package-specific; extract shared mechanics only where both adapters need them.

- A message retains input, index, decoded strings, and any parser resources independently of the decoder handle.
- Closing the input or decoder does not invalidate a retained message.
- Closing a message invalidates all descendant scoped objects/arrays/strings, including cached compiled reads.
- Sibling messages retain independent lifetimes. Parser reuse cannot overwrite live data.
- `snapshot()` yields an independently owned immutable result.
- Initial scoped owners are confined; asynchronous or cross-thread transfer requires a separate explicit protocol.

Do not place scoped payloads into ordinary owned batches without detaching them. Account for input, structural index, unescape storage, parser scratch, and transient growth overlap separately. Caller allocators control result storage; do not claim they control simdjson's internal allocations unless its pinned public API supports that configuration.

## JSON semantics and JEP 540

Target [JEP 540, Simple JSON API (Incubator)](https://openjdk.org/jeps/540), integrated for JDK 28 as of this plan. Pin an early-access API revision in its own optional artifact and test lane. Keep TruffleData's Java 25 core independent of incubator classes.

The JDK-compatible profile requires strict JSON, complete validation before publication, duplicate-name rejection after escape decoding, member-order preservation, missing/null distinction, and matching navigation/conversion exceptions. No comments, trailing commas, NaN or infinity. An On-Demand traversal is not by itself proof that unused portions of a document are valid.

Preserve arbitrary-precision numeric tokens. `JsonNumber` is a decimal value, not necessarily representable by i64/u64/f64 or even by a particular bounded decimal implementation. Use validated token storage as the lossless baseline; perform checked integer/decimal conversion or documented floating rounding at access time. Align with `ExactNumber` where its range is sufficient. Add explicit limits for token length and resource consumption without silently rounding accepted inputs.

General JSON and ProtoJSON remain separate semantic layers; protobuf well-known types, default values, enum mappings and numeric-string conventions do not become general JSON rules.

## Durable facade values

JDK `JsonValue` instances are immutable and thread-safe; an externally closable, thread-confined scoped message cannot directly fulfill that contract. Its non-sealed subtype interfaces allow a custom implementation, but lifetime and thread-safety requirements still apply.

Provide two explicit boundaries:

1. Scoped TruffleData views for borrowed native execution.
2. Durable facade values backed initially by detached storage, later by safely retained immutable native ownership where warranted.

Every child facade view must keep its backing owner reachable and valid. Lazy materialization must be thread-safe. Do not expose manual invalidation through the durable API. Parsing via our adapter may return JDK interfaces; replacing calls to the JDK's static `Json.parse` is not promised.

JSON generation is part of compatibility: implement correct escaping, exact number emission, object/array traversal and round-trip tests. Preserve lexical information where the pinned facade requires it: the JDK's [JsonString.toString contract](https://download.java.net/java/early_access/jdk28/docs/api/jdk.incubator.json/jdk/incubator/json/JsonString.html#toString()) preserves parsed string spelling, not just its decoded value. Retain raw token spans in a TruffleData side table keyed by node/field, including original escapes, and carry those spans through snapshots. Do not assume a decoded UTF-8 Myna tape alone can reproduce that spelling.

Keep Java UTF-16 factory semantics separate from the native UTF-8 fast path. JsonString.of documents round-tripping its non-null String argument; the facade must not replace isolated surrogate code units while adapting Java-created values or keys. Use durable Java-owned UTF-16 values for cases outside Myna v3's valid-UTF-8 domain, and differential-test the pinned JDK parser's escaped/raw surrogate acceptance before choosing a native parsing policy. A future fully native UTF-16 representation would require an explicit versioned extension, not labeling invalid UTF-8 as STRING. This compatibility path is distinct from silently retrying malformed input. Support both reading external facade values into our representation and exporting ours where useful, with copying and allocation costs visible.

## Facade priorities

| Priority | Artifact / API | Scope |
| --- | --- | --- |
| Required | JDK JSON / JEP 540 | Strict parsing, native subtype implementations, navigation, conversion, generation |
| First ecosystem adapter | Jackson 3 | Parser/generator adapters and existing databinding integration |
| Demand-driven compatibility | Jackson 2 | Separate artifact/package version boundary |
| Next | Jakarta JSON-P | Provider surface, object model and streaming contracts |
| Optional | Gson | TypeAdapter integration and explicit tree conversion |

Use facade-specific profiles instead of silently inheriting permissive parser defaults or coercions. Streaming APIs over an already indexed document must be described as token iteration, not bounded-memory incremental parsing. Genuine stream/batch parsing requires its own buffering and lifetime design. Jackson reflection-based databinding on SVM requires its normal reachability setup; schema-generated getters are a separate fast path.

## Delivery sequence and gates

1. Myna: ship multi-region text, explicit borrowing, exact-number tokens, canonical C/C++ producer APIs, Rust/SVM consumers, and packaging. Demonstrate a direct C++ simdjson producer in conformance tests; it is not yet the TruffleData backend.
2. Compatibility spike: prove input padding, unescaped input slices, escaped text handling, exact number capture, and parser reuse against the chosen simdjson release.
3. TruffleData arbitrary JSON backend: scoped generic values, static SVM + JVM FFM bindings, explicit snapshots, error and budget policies.
4. Known-schema backend: fixed scalar slots, runtime plans, generated getters, nested/repeated support and full validation.
5. JEP 540 facade: durable values and differential conformance against the pinned JDK implementation, including generation.
6. Jackson, then JSON-P; add Gson where consumer demand justifies it.

### Delivery status (branch `feat/json-completion`)

Steps 2–4 are delivered, executed through `docs/superpowers/plans/2026-11-06-json-completion.md`; every ruling is recorded in its ledger.

| Step | Status | Commits |
| --- | --- | --- |
| 1. Myna foundation | Delivered upstream; now pinned to the Myna 0.9.0 release source | `82bc9a9` |
| 2. Compatibility spike | Delivered: message-owned text and pooled parsers, lexical spans and number classification (ABI 4), adopted padded input | `d4a087e`, `ef737e5`, `74b7ed8` |
| 3. Arbitrary JSON backend | Delivered: owners and assumptions, JVM FFM backend, snapshots, array index, compiled read gates, `InteropLibrary` exports, `JsonSink` export, allocation-free compiled interop/export/snapshot reads | `82d47bb`, `e6ae353`, `e496f75`, `f279a3d`, `eb79330`, `3992b10`, `83ca60f`, `6dcf286`, `500f983` |
| 4. Known-schema backend | Delivered: `JsonPlan`/`JsonRecord`/`JsonAccess`, nested and repeated records natively and in Java, typed plans on SVM and JVM | `c6e7abc`, `c6bd959`, `7427b0c`, `3e2367b` |
| Evidence and release | Generic comparison cells against Gson, coverage and policy inventories, `truffledata-json`, `-json-bindings` and `-json-jvm` artifacts | `c1de21c`, `a326d83`, `fb55da3` |
| 5. JEP 540 facade | Planned (follow-on) | — |
| 6. Jackson, JSON-P, Gson | Planned (follow-on) | — |

Measured performance and the next architecture (sessions, streaming, large documents, faster reads) are in `benchmarks/results/json-generic-c6e7abc/`, `benchmarks/results/json-perf-probe-861dff1/` and `docs/superpowers/plans/2026-11-06-json-performance.md`.

Pin simdjson and Myna in Bazel with checksums and explicit native C++ runtime dependencies. Use the existing native/Java fallback selection conventions; malformed input or ABI mismatch never triggers silent backend fallback. Publish bindings and native artifacts together. Do not claim JEP compliance until the target revision's contract corpus passes.

## Verification and performance

Test C/Rust/Java layouts and version rejection, region bounds, inline/borrowed boundaries, allocation failures and release order. JSON coverage includes malformed/truncated escapes, supplementary Unicode and surrogate handling, escaped duplicate keys, shuffled fields, missing versus null, all root types, numeric boundaries/huge exponents, trailing garbage and malformed unprojected fields. Fuzz parser-to-index adaptation and compare semantic results with reference parsers under matching profiles.

Lifetime tests retain old messages while advancing/reusing parsers, close producers before consumers, release sibling messages independently, and reject cross-thread/use-after-close access in interpreted and compiled guest code. Run ASan/UBSan on C++ producers and allocation failure paths. Test snapshots after all native owners close.

Benchmark parse + validate + adapt + guest consume, separately reporting native and Java allocations, copied payload bytes, structural bytes, retained parser memory, transient growth and detachment cost. Compare known-schema full ingestion and sparse projection; arbitrary traversal and random lookup; short/long/escaped strings; numeric-heavy and deeply nested inputs. Measure JVM, SVM interpreter, and compiled guest reads. Include a handwritten simdjson projection baseline, reference facade implementations, and native-call counts. Retain raw reports; microbenchmarks are not end-to-end guarantees.

## Primary references

- [simdjson On-Demand parser contract](https://github.com/simdjson/simdjson/blob/master/include/simdjson/generic/ondemand/parser.h)
- [simdjson usage and string APIs](https://github.com/simdjson/simdjson/blob/master/doc/basics.md)
- [JDK JSON API](https://download.java.net/java/early_access/jdk28/docs/api/jdk.incubator.json/jdk/incubator/json/package-summary.html)
- [Jackson core](https://github.com/FasterXML/jackson-core)
- [Jakarta JSON Processing](https://jakarta.ee/specifications/jsonp/2.1/)
- [Existing Buffa integration](docs/BUFFA.md)

## SVM performance and operational adoption

Use Myna's generated Native Image C API as the primary baseline for the TruffleData
adapter, matching handwritten `@CFunction` calls with the same native target and
transition policy. Normal transitions and bounded native leaves are separate
contracts. Myna's ARM64 scalar instruction audit found identical generated/manual
call sequences; this does not establish parity for parser callbacks, buffers or
entire JSON ingestion. Extend the retained comparison harness with those shapes
when the downstream adapter lands.

For FFM on SVM, retain Myna's build-time unbound ABI adapters and runtime symbol
addresses. Do not move runtime addresses into the image heap or recreate bound
handles in a hot path. JVM FFM/JNI measurements are secondary to SVM results.
Pin Elide 1.6.2, elide-dev/toolchain 2026.11.5 and elide-dev/graal
25.5.5-elide.24 when reproducing the upstream qualification.

Keep CodSpeed's own-workload regression series separate from uninstrumented
comparison experiments. Preserve raw samples, compiler/runtime settings, source
and binary hashes, CPU identity and warmup evidence. Shared-host wall timings are
advisory; controlled ARM measurement hardware must support the pinned toolchain's
runtime instructions. Re-run native sanitizers and direct parser-to-tape lifetime
checks when adding simdjson ownership adapters.

Myna now preserves matching executable symbol sidecars and has an Elide Cloud
project under `elide` / `myna`, with a managed connection to debuginfo staging.
TruffleData integration should preserve its own matching parser/binding symbols
and ownership diagnostics; Myna's upstream sidecars do not describe downstream
application machine code.

## Myna delivery record

Myna's native foundation shipped as [v0.7.0](https://github.com/elide-dev/myna/releases/tag/v0.7.0), merged through [PR #30](https://github.com/elide-dev/myna/pull/30). Adopt [v0.7.2](https://github.com/elide-dev/myna/releases/tag/v0.7.2), which includes the performance/toolchain work from [PR #31](https://github.com/elide-dev/myna/pull/31) and the verified Linux symbol-ID correction from [PR #32](https://github.com/elide-dev/myna/pull/32). Pin source commit `7401c446ef9ad80d7bd14a9bf92f360a04956b80`.

- Source URL: `https://github.com/elide-dev/myna/archive/7401c446ef9ad80d7bd14a9bf92f360a04956b80.tar.gz`
- Source SHA-256: `20852660cc80ed370779fdc27099bc7f23b511405511323a8344f2ce7502300d`
- Source strip prefix: `myna-7401c446ef9ad80d7bd14a9bf92f360a04956b80`
- Portable SDK: `myna-values-0.7.2.tar.gz` on the release, SHA-256 `90793da28c30486582e04ee0ee5eef12dfc52bf899be4f3e5576bdee1bab661c`
- ABI: v3; nodes 24 bytes, region descriptors 16 bytes, view descriptor 56 bytes.
- C++ conformance parser: simdjson 5.1.1, with checked upstream source hashes in `tests/values_cpp.py`.
- Validation passed on Linux and macOS, including Rust tests, C++ ASan/UBSan, generated bindings and actual SVM consumption; JVM tests and coverage checks also passed.

The production TruffleData parser, known-schema backend, ownership adapters and Java facades remain the subsequent phases above.
