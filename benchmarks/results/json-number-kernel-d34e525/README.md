# JSON number kernel under target attributes: WS8 (2026-11-09)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` WS8. Sam's ruling and every entry are
in `2026-11-08-beat-stock-svm-ledger.md` under "WS8 (JSON number kernel worker)". SVM / Native
Image is the admission target, and no JVM timing was taken. **Verdict: killed and reverted
(`343b536`).** The dispatched kernel is slower than the inline builtin parser in every paired SVM
observation and every native probe run. The numeric fixture and its cell family stay
(`a20fb0a`, `4d2f63a`). Production `json/`, `tape/` and `bazel/` equal the base `6a15955`.

## Admission identity (Sam, 2026-11-09)

Paired SVM, disjoint forks: ≥3% on a numeric-heavy (canada-style) fixture **and** no loss on
big/huge mixed. Kill: <3% on the numeric fixture, or any big/huge loss.

## Fixture (`a20fb0a`)

`JsonFixture.numericDocument(i)`, i = 0..31, produces one GeoJSON `Feature` per document. Each
has a closed `Polygon` ring of `160 + 4·(i mod 8)` `[lon,lat]` pairs. The coordinates are
integer microdegrees drawn from a 64-bit LCG (Knuth MMIX constants), with longitude in
[-141, -52) and latitude in [41, 84), which is canada.json's extent. Each is spelled the way
canada.json spells its numbers: the exact binary value of the nearest double, rounded to 17
significant digits (`-54.738137000000002`, so 14–15 fraction digits). Document 0 is 7.2 KB, and
the 32 documents total 231,660 B, comparable to the native big fixture (1,000 mixed documents).

Provenance: generated, not copied. There is no third-party data and no license obligation.
`json_typed_pair.py`/`json_measure.py` re-derive the checksum independently in Python
(−2,150,921,114 over 32 documents). Python and the JVM generator agree.

All engines are ingest-only. Each document is parsed in full and the same six members are read:
`properties.id`, the ring size, and the first and last points rounded to microdegrees.

| Engine | Ingest |
| --- | --- |
| `simdjson-numeric-interop` | `JsonSession.decode`, six cached-interop reads, close |
| `simdjson-numeric-decode` | `JsonSession.decode` + close (no read; checksum = 1 per document) |
| `gson-numeric` | `JsonParser.parseReader` tree, then read (Gson keeps `LazilyParsedNumber`: only the read coordinates are converted) |
| `jackson-numeric` | `ObjectMapper.readTree(byte[])` (`DoubleNode` per coordinate) |
| `fastjson2-numeric` | `JSON.parseObject(byte[])` (default `BigDecimal` decimals) |

Smoke (`json_native_smoke`, `gates/json_native_smoke-numeric.txt`) gives these Java B/document:
interop 2,144, decode 120, Gson 59,398.5, Jackson 106,516.5, fastjson2 23,201. These figures
were not pinned as budgets. The paired runner gains a ninth shard group (index 8): the numeric
family plus the mixed `simdjson-generic-interop`/`simdjson-generic-decode` ingest cells as
same-host controls.

## Change measured (`d34e525`, reverted)

`json/native/number_kernel.cc` included simdjson 5.1.1's unmodified
`include/simdjson/generic/{base,jsoncharutils,atomparsing,numberparsing}.h`. The headers came
through a new `@simdjson//:generic_numberparsing` library on the pinned archive, and the Cargo
vendor copy carried them byte-identical together with simdjson's `LICENSE`/`LICENSE-MIT`. The
headers were included twice:

- under `SIMDJSON_IMPLEMENTATION td_num_westmere` inside `SIMDJSON_TARGET_REGION("sse4.2,pclmul,popcnt")`;
- under `td_num_haswell` inside `("avx2,bmi,bmi2,pclmul,lzcnt,popcnt")`.

Both target strings are the regions simdjson uses for its own kernels. Each instantiation used
its kernel's `parse_eight_digits_unrolled` (SSE `pmaddubsw`/`pmaddwd`/`packusdw`),
`leading_zeroes` (haswell `lzcnt`) and `full_multiplication` (`mulx` under bmi2). These were
copied from `{westmere,haswell}/{numberparsing_defs,bitmanipulation}.h` with only the namespace
changed. The private namespaces avoid any ODR overlap with `simdjson.cpp`. The objdump of the
candidate shows the VEX eight-digit step, `lzcntq` and `mulxq` in `parse_haswell`.

Dispatch: `number_kernel::active()` reads one relaxed byte per document. On first use it maps
simdjson's active implementation, which is the same detection that already chose stage 1 and
honors `SIMDJSON_FORCE_IMPLEMENTATION`:

- haswell or icelake → haswell kernel;
- westmere → westmere kernel;
- anything else → the inline builtin.

`stage2_walk::number()` called the kernel out of line. The kernel returned the recorded writer
event and status in two registers, and `replay` re-issued that event into the unchanged
`number_writer`. Token rules, the provisional-emission rollback, the EOF padding copy and the
`integer18`/`bounded_number` fast paths did not change. Typed `decimal_ref` kept the builtin
`parse_double`. The ABI and fingerprint were unchanged.

## Parity (`gates/number_kernel_test.log`, invocation `bf4de4dd`)

The test ran on an AMD EPYC RBE worker with AVX2 and AVX-512. simdjson's active implementation
was `icelake`, so the detected tier was haswell. All three kernels (builtin, westmere, haswell)
ran. Every kernel matched the builtin parser exactly (emitted kind, bits and status) on the
following tokens:

- 18,878 tokens from every fixture: the 200-document tape corpus, 64 mixed fixture documents and
  the 32 numeric documents;
- 305 edge tokens: 1e308, 1.7976931348623157/8/9e308, 1e309, 1e-324, 4.9e-324, the
  half-min-subnormal pair, 2^53±1 as integers and decimals, 2^63/2^64 boundaries, 19–22-digit
  integers, `-0`/`-0.0`, leading zeros, trailing dots, bare `-`, `.5`, `+1`, malformed
  exponents, huge exponent digit counts, 400-digit mantissas, and every fraction length 0–40;
- 161,740 random tokens: random doubles in five formats, random integers, and 50,000
  grammar-fuzz strings.

Each token was tested with 13 terminators (including dirty ends) × 3 padding fills, for
7,055,997 cases. On top of that, 100,134 whole stage-2 decodes per tier (structural tape and
validate-only writer, every token as a root, in an array, in an object and with trailing input)
gave identical tapes and statuses. A local Rosetta check confirmed that the test catches injected
kernel bugs (an off-by-one in the eight-digit step and a u64→s64 event).

## Images (`manifests/`)

All images are x86-64, pinned `25.5.5-elide.27`, `-O2`, `-march=compatibility`, serial GC, no
PGO, built on RBE and preserved under the worktree's ignored `build/`.

- Before: `4d2f63a` (base `6a15955` + numeric harness) `json-comparison`
  `24d634a9…5f9e`, invocation `2dc07e1b`.
- After: `d34e525` `json-comparison` `8049476a…c3cf` and `json-comparison-profile`
  `12481022…05c3`, invocation `bf4de4dd`.

## Paired SVM (`json_typed_pair`, three fresh forks per image per cell, interleaved, one pinned CPU)

Medians of fork medians. The ranges are fork-median min–max. Ratio = before/after time, so a
value below 1 means the candidate is slower. Every shard ran on a worker with AVX2 and AVX-512,
so the haswell kernel ran in every candidate process.

| Cell | pair1 shard 8 (Xeon, `7db85a5f`) | pair2 run 1 (Xeon, `2a76e974`) | pair2 run 2 (EPYC, `2a76e974`) |
|---|---|---|---|
| **numeric decode** | 12,520 → 13,449 ns, **0.931×, disjoint** | 13,664 → 14,535, 0.940×, overlap | 9,638 → 10,804, 0.892×, overlap |
| **numeric interop** | 15,811 → 16,554, 0.955×, overlap | 16,393 → 17,372, **0.944×, disjoint** | 11,410 → 12,505, 0.912×, overlap |
| Gson numeric (control) | 0.989× | 1.060× | 1.807× (one contended baseline fork) |
| Jackson / fastjson2 numeric (controls) | 0.845× / 0.876× | 0.710× / 1.010× | 0.948× / 1.522× |
| mixed generic interop ingest | 1.065× | 1.145× | 0.966× |
| mixed generic decode ingest | 0.867× | **0.952×, disjoint** | 1.052× |

pair1 shard 6 (AMD EPYC, retained mixed forms) gave these ratios: snapshot ingest 1.069×, stream-interop ingest **0.932× disjoint**, generic-interop ingest 0.986×, generic-decode ingest 0.928×, all others overlapping. The Java controls swing by up to ±80% on the same shards, so the mixed cells are reported, but they are not resolved to a few percent. The numeric native cells are below 1.0 in all six observations, and two are disjoint losses. Full tables: `pairs/*/paired.md`, raw shard reports and logs next to them.

## Native producer probe (`native-probe/`, manual `//json/native:number_kernel_probe`)

The probe times stage 1 + stage 2 into the structural tape writer. It compares builtin against
the haswell kernel in alternating order over 7 rounds per fixture: small/medium/big/huge (1, 20,
1,000 and 20,000 mixed documents, as in `isa_probe`) and the numeric fixture as one array.
Builtin/kernel medians (above 1 means the kernel is faster):

| Run | small | medium | big | huge | numeric |
|---|---:|---:|---:|---:|---:|
| `bf4de4dd` (Xeon) | 0.907 | 0.958 | 0.992 | 1.002 | 0.914 |
| `7db85a5f` run 1 (EPYC) | 0.884 | 0.856 | 0.887 | 0.999 | 0.979 |
| run 2 (EPYC) | 0.972 | 1.129 | 0.965 | 1.001 | 0.965 |
| run 3 (EPYC) | 1.062 | 1.008 | 0.985 | 0.963 | 0.936 |

These runs shared workers with other tests, and the per-round ranges are wide (for example, big
ran 0.67–1.68). The numeric median is still below 1.0 in all four runs, and big/huge are never
above 1.002. These are native producer timings, not SVM results, and are recorded only as
consistent with the pair.

## Instruction counts

Not measured. `//benchmarks:simulation` has no numeric case. With the time loss consistent
across both measurement kinds, a callgrind split was not worth another image.

## Why it loses (hypothesis, unmeasured)

The builtin parser is fully inlined into `stage2_walk::number()`. The kernel cannot be: its
target attributes forbid inlining into baseline code. Every number therefore pays a call, the
struct return, the replay switch and the loss of cross-call register allocation in the walk.
The SSE eight-digit step (`pmaddubsw`→`pmaddwd`→`packusdw`→`pmaddwd`→`movd`) also has a longer
dependency chain than the SWAR step (three multiplies), and it runs only once or twice per
coordinate. `lzcnt` and `mulx` save a cycle or two in `compute_float_64`. None of this is a
lever large enough to pay for an out-of-line call per number. The only way to get the kernel
inline is to compile the whole walk per ISA, which is the AVX2/AVX-512 stage-2 tier result
already settled in plan §6 (≤2.2%, AVX-512 −5%).

## Gates on the candidate `d34e525` (invocation `bf4de4dd`)

These targets passed: `//json/native:all` (`test`, `api_test`, `myna_oracle_test`, `stage2_test`,
`stream_test`, `typed_test`, `typed_stage2_test`, `unicode_test`, `text_fusion_test`,
`shape_fuzz_test`, `number_kernel_test`), `//json/native:session_replay`,
`//json/native:number_kernel_probe`, `//benchmarks/comparison:json_native_smoke` (24
compilations and nine allocation budgets unchanged, plus the five numeric cells),
`//benchmarks/comparison:json_aot_symbols`, `//json/tests:native_test` and `//json/tests:jvm`.

The Cargo vendor copy built and passed `cargo test` locally on aarch64-apple-darwin and on
x86_64-apple-darwin under Rosetta; the x86 object exports `parse_westmere`/`parse_haswell`. The
harness (`4d2f63a`) passed `json_native_smoke` (`2dc07e1b`). After the revert, the production
sources are byte-identical to the base and every production gate stands as it did there.
