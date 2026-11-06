# Generic JSON cells — 2026-11-06

This is the first measurement of the generic simdjson path against Gson's tree parser:
one `json_measure` run on a shared BuildBuddy RBE worker (Intel Xeon @ 2.70GHz, 5 vCPU).
**Shared-host timings are advisory.** They come from one worker in one run, are not
confidence intervals, and do not support speedup claims. Java allocation figures are
deterministic across forks. They exclude native memory: the retained input copy,
Myna index, decoded text, lexical spans, parser scratch and allocator overhead.

[`json-comparison.md`](json-comparison.md) has every cell. [`json-comparison.json`](json-comparison.json)
has the raw samples, fork identities, the binary hash and `/proc/cpuinfo`.
[`json-compilation.log`](json-compilation.log) is the Truffle-tier expansion trace from
the smoke run of the same binary.

## Generic documents (32 mixed documents, ten members consumed per document)

| Stage | gson-tree | simdjson-generic-java | simdjson-generic-interop | simdjson-snapshot |
|---|---:|---:|---:|---:|
| ingest, ns/doc | 4,707 | 5,888 | 5,623 | 45,532 |
| ingest, Java B/doc | 13,404 | 3,660 | 528 | 32,770 |
| read-aot, ns/doc | 525 | 2,785 | 2,933 | 28,025 |
| peak, ns/doc | 530 | 2,935 | 2,741 | 28,261 |
| peak, Java B/doc | 81 | 2,357 | 1 | 22,749 |

What the retained samples show, without generalizing beyond this fixture and host:

- **Retained reads are slower than reading Gson trees.** Interop reads of ten
  members take about 2.7 µs per document at peak, against about 0.53 µs for the Gson
  tree, which is a boundary call into plain Java maps. The interop loop compiles to one
  graph with no graph allocations and allocates nothing per document beyond the
  harness's 1 B. Each member read still crosses a `@TruffleBoundary` into
  `ValueTape.find`, a linear key scan. This run does not isolate how much of the cost
  is that scan and how much is the boundary.
- **Ingestion is not faster than Gson here.** Decode plus interop read (5.6 µs) and
  decode plus `JavaSink` export (5.9 µs) are both above Gson's parse plus read
  (4.7 µs). The simdjson cells allocate 528 and 3,660 Java bytes per document,
  against 13,404 for Gson. Native memory is not counted.
- **Snapshot reads allocate heavily.** Peak reads of snapshots allocate 22,749 Java
  bytes per document and take about 28 µs. Interop reads of the same documents on a
  live message allocate 1 B. The graph has no allocations, so they happen behind
  boundaries in `HeapValueTape` reads. Its byte-array-view `VarHandle` accessors are
  the leading suspect; this run does not prove that. This is a defect, not a
  property of snapshots.
- **Export allocation matches its budget.** A compiled export into `JavaSink` allocates
  2,357 B per document. That is the tree, plus the key and string views passed to
  `JavaSink`'s boundary methods, plus the harness's 1 B. The smoke gate's budget is
  2,400 B. Exporting into a constant non-materializing sink (smoke-only
  `simdjson-export-walk`) allocates only the harness byte.

The scalar cells were rerun in the same session for context. Their peak and AOT read
figures differ from earlier hosts (for example, specialized peak 3.58 ns/record here
versus 1.497 on unclemax). Do not compare across hosts.

## Reproduction

- Base source: `c6e7abc346077e1897c4cab0b3eae224394e0362` (`feat/json-completion`).
- [`harness.patch`](harness.patch), SHA-256 `65f2d6d3d4f1422e64c277805592c86104f4ff334747dbd4ebe5995f9225baed`,
  is the benchmark source as built, applied to that base. The commit that adds this
  directory contains the same harness plus documentation.
- Executable SHA-256: `1752c2538bb53629c1776ad47f50b44e9f3049c984a1cf86332c6345dae8d29b`.
- Measurement: <https://app.buildbuddy.io/invocation/9e5ec883-f49d-4db9-9365-c27cc2fe4dec>.
  Smoke with graph and allocation gates: <https://app.buildbuddy.io/invocation/882ab314-bd5c-4135-8be2-6528ae0a0761>.
