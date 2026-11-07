# Myna value ABI v4, the JSON gaps and the JSON scoreboard (ledger V1–V3), measured on `86a691a` + V1–V3

Every number here comes from one host, **unclemax** (AMD Ryzen Threadripper PRO 9965WX, Linux 7.0,
`environment.txt`), pinned to CPUs 2–9 with `taskset`, one process at a time, nothing else
scheduled there. Each pair below ran back to back on that host from binaries built by Bazel (RBE)
and copied over; `environment.txt` holds their SHA-256s. **Timings are advisory** (one host, a few
runs); compare rows within a pair. RBE numbers in earlier results (`json-reads-14df695`,
`json-export-2c95139`, `json-truffle-b3987bc`, `json-stream-238c826`) came from slower shared
workers, so the plan's absolute targets (2.4 µs, 1.5 µs) are read here as the ratio they asked for
against the same host's baseline and Gson.

| Directory / file | What it is |
|---|---|
| `baseline-86a691a/`, `baseline-86a691a-rerun/` | `json_measure.py` (as of `86a691a`) over `//benchmarks/comparison:json_svm` built at `86a691a`, run before and after the candidate |
| `candidate/` | `json_measure.py` over the same target built from the V1–V3 tree (`-march=compatibility`, as the baseline) |
| `scoreboard-x86-64-v3/` | The same tree's `json_svm_v3` (`-march=x86-64-v3`): every engine including simdjson-java, which needs 256-bit vector species |
| `native-probe.txt`, `v4_probe.cc` | Native decode throughput and text export, one process (probe built as a temporary `cc_binary` in `json/native`, removed before commit) |
| `jvm-critical.jsonl`, `CriticalProbe.java` | JVM critical vs normal FFM decode, interleaved rounds, raw per-round samples (temporary `java_binary`, removed before commit) |
| `pairs.py` | Prints the before/after table below from the retained JSON reports |

## Gaps (generic workload, ns or Java bytes per document; medians of three fork medians)

`python3 pairs.py baseline-86a691a/json-comparison.json baseline-86a691a-rerun/json-comparison.json candidate/json-comparison.json`

| Gap | Before (two runs) | After | Result |
|---|---:|---:|---|
| Compiled member reads, alternating member orders: `simdjson-generic-interop` peak | 819.5 / 811.1 ns (3.66 × `gson-tree` 223.9) | **90.9 ns** (0.41 × `gson-tree` 223.8) | target ≤ 1 × Gson **met** |
| … snapshot peak | 824.8 / 813.0 ns | **95.3 ns** | |
| … AOT reads (`read-aot` interop) | 888.5 / 879.0 ns | 158.8 ns | |
| `JavaSink` export (`simdjson-generic-java` peak; RBE 2,867 ns vs ≤ 2.4 µs) | 1,310.4 / 1,395.6 ns | **930.6 ns** (0.71 ×) | the asked-for −16 % **met** (−29 %); still 4.2 × Gson's tree reads |
| `TruffleSink` ingest (`simdjson-session-truffle`; slower than Gson) | 2,001.6 / 2,002.1 ns vs `gson-tree` 1,967.8 | **1,767.9 ns** vs 1,970.8 (0.90 × Gson) | now faster than Gson **met**; 1,772.5 → 1,652.5 B |
| Stream interop (`simdjson-stream-interop`; RBE 2,798 ns vs ≤ 1.5 µs) | 1,479.2 / 1,389.1 ns, 432.75 B | **660.8 ns**, 312.75 B | −55 % **met**; the 1.0 B/doc of a compiled root is not what this ingest cell measures (240 B are its ten AOT views) |
| Interop ingest allocation (`simdjson-generic-interop` ingest; 472 vs 160 B) | 472 B, 1,484.9 ns | 480 B, **769.4 ns** | 160 B **not met** (+8 B: the reader's per-reader SIMD threshold); 240 B are the ten AOT views |
| Session `JavaSink` ingest | 1,937.7 ns, 1,360 B | 1,689.0 ns, 1,240 B | |
| Stream `JavaSink` ingest | 1,880.7 ns, 1,338.5 B | 1,631.1 ns, 1,218.5 B | |
| Snapshot ingest | 2,315.5 ns, 1,997.5 B | 1,551.3 ns, 2,101.5 B | +104 B: snapshots copy their directory |

Gson rows and the scalar workload (typed plans, untouched) are unchanged within noise in all three
runs.

## JVM critical-call gain (Task E2 carry-over)

`jvm-critical.jsonl`: Zulu 25.0.4.1 HotSpot, `JsonJvm.openSession` with the default 4 KiB critical
limit against `criticalLimit = 0`, `validate()` of one document, 15 interleaved rounds (alternating
which path runs first) after warm-up; per-round means.

| Document | Critical (median) | Normal (median) | Gain |
|---:|---:|---:|---:|
| 64 B | 173.3 ns | 182.6 ns | 9.3 ns (5.1 %) |
| 505 B | 1,003.8 ns | 1,005.4 ns | 1.6 ns (0.2 %) |
| 3,271 B | 6,166.6 ns | 6,169.1 ns | 2.4 ns (0.04 %) |

The critical path saves the thread-state transition, about 9 ns per call on the smallest document:
measurable on tiny documents, noise beyond ≈ 500 B.

## Decode throughput against simdjson's own ceiling (`native-probe.txt`)

simdjson 5.1.1 and simdutf select `icelake` on this CPU. Best of 9 rounds. Documents are the
comparison fixture's: one record (small), 20 (medium), 1,000 (big) and 20,000 (huge) records in an
array.

| Row | small 210 B | medium 4.3 KB | big 228 KB | huge 4.85 MB |
|---|---:|---:|---:|---:|
| D0 simdjson DOM parse (ceiling) | 2.12 GB/s | 2.46 GB/s | 2.41 GB/s | 2.44 GB/s |
| D1 simdjson On-Demand walk touching every value | 1.43 GB/s | 1.51 GB/s | 1.52 GB/s | 1.57 GB/s |
| V3 our decoder, ABI v3 tape | 643 MB/s | 665 MB/s | 706 MB/s | 711 MB/s |
| **V4 our decoder, ABI v4 tape (shipped)** | **617 MB/s** | **639 MB/s** | **675 MB/s** | **688 MB/s** |
| S4 `td_json_session_decode` + reset through the C ABI | 595 MB/s | 637 MB/s | 659 MB/s | 675 MB/s |
| B4 the same visitor over Myna's `value_builder` (reserve + v4) | 351 MB/s | 358 MB/s | 381 MB/s | 403 MB/s |

The shipped decode runs at 0.26–0.28 × simdjson's DOM ceiling and 0.42–0.44 × an On-Demand walk
that produces nothing: the remaining cost is the tape itself (a 24-byte node, an 8-byte lexical
span and a 4-byte side-table entry per value, string scans, duplicate-key tables), not stage 1.
v4 directories cost 2.8–4.2 % over v3. Myna's builder with `reserve()` is 1.76–1.78 × slower than
the raw writer, which therefore stays (ledger V1).

Text export into a TruffleString-ready buffer (same tapes):

| Row | small | medium | big | huge |
|---|---:|---:|---:|---:|
| T0 text arena (`tape/native/text_arena.h`) | 293 ns | 3,564 ns | 179.3 µs | 4.01 ms |
| T1 Myna `myna_tape_text_v1` (sizing call excluded) | 251 ns | 5,719 ns | 289.0 µs | 6.22 ms |

## Scoreboard (`scoreboard-x86-64-v3/`, every engine in one x86-64-v3 image)

Same fixture, checksum and stages as every other cell: parse the UTF-8 bytes, read the same ten
members. Competitors run with their default tree (or streaming) APIs: no schema binding, code
generation or reflection mapping.

| Engine | Ingest ns/doc | Ingest B/doc | Peak read ns/doc | Peak B/doc |
|---|---:|---:|---:|---:|
| fastjson2 2.0.66 `JSONObject` | 579.7 | 1,372 | 84.3 | 33 |
| jackson-core 2.22.4 streaming (ingest only) | 671.3 | 808 | — | — |
| **TruffleData `JsonStream` + interop reads** (ingest only) | 676.9 | 312.75 | — | — |
| DSL-JSON 2.0.2 `Map` | 706.5 | 1,080 | 83.1 | 1 |
| **TruffleData session decode + interop reads** (`simdjson-generic-interop`) | 795.0 | 480 | **85.7** | 1 |
| jsoniter 0.9.23 `Any` | 1,120.7 | 1,508 | 115.8 | 81 |
| Jackson databind 2.22.4 `JsonNode` | 1,141.7 | 2,245 | 61.2 | 1 |
| **TruffleData snapshot** | 1,474.1 | 2,101.5 | 101.6 | 1 |
| **TruffleData `JavaSink` (borrowed parse into a Java tree)** | 1,567.6 | 1,240 | — | — |
| **TruffleData `TruffleSink`** | 1,626.4 | 1,652.5 | — | — |
| Gson 2.8.9 `JsonElement` | 1,840.4 | 13,404 | 220.4 | 81 |
| simdjson-java 0.4.0 (ingest only) | 21,885.0 | 33,428 | — | — |

Where we stand: our native decode plus ten interop reads (795 ns) is within 1.4 × of the fastest
JVM tree parser here (fastjson2, 580 ns) and ahead of Jackson databind, jsoniter and Gson, with the
least Java allocation of any tree engine; streams match Jackson's streaming parser. Compiled member
reads (85.7 ns per ten reads) now beat every tree engine except Jackson's `JsonNode` (61.2 ns), which
is a plain field read chain behind one boundary. Materializing a host tree (`JavaSink`,
`TruffleSink`) still costs more than parsing straight into one with the fastest libraries: those
cells are dominated by the Java tree, not the decode. simdjson-java allocates and pads per parse
(33 KB per 210-byte document), so small documents are its worst case.
