## Paired SVM result and admission

Admit specifically for nested/repeated typed ingestion. Invocation
[950032e5](https://app.buildbuddy.io/invocation/950032e5-dc4f-40f9-a67f-0a73f3b5dce3)
runs six JSON groups with three fresh-process forks per image/cell. All report image hashes
match the preserved manifest; exact fixture source hashes, checksums and tier markers agree.
Every candidate ingest fork is faster than every baseline fork for all four new workloads.
The table gives medians of fork medians and ranges of those fork medians; speedup is baseline/candidate.

| Typed workload | Baseline ns/document (range) | Candidate ns/document (range) | Speedup | Latency change | Java B/document, both |
|---|---:|---:|---:|---:|---:|
| nested | 829.097 (815.076–844.231) | 700.302 (700.227–712.283) | 1.184x | -15.53% | 160 |
| repeated | 4,209.473 (4,159.738–4,237.960) | 3,639.223 (3,577.283–3,694.569) | 1.157x | -13.55% | 1568 |
| mixed | 1,677.520 (1,671.169–1,855.103) | 1,517.453 (1,496.451–1,606.759) | 1.105x | -9.54% | 592 |
| churn | 2,639.500 (2,448.454–14,121.394) | 2,286.197 (2,280.528–2,300.926) | 1.155x | -13.39% | 558.5 |

**Measured tradeoffs remain visible.** Generic interop ingest regresses from
866.279 (859.423–875.851) to
885.299 (884.878–889.927) ns/document: **+2.20%**, with nonoverlapping observed fork ranges.
Its compiled read median improves 29.013→28.793 ns (0.76%). The generic producer source is unchanged,
but that does not erase the measured image-level ingest regression. Flat typed ingest moves
229.516 (227.931–235.067)→232.959 (229.670–233.508) ns: **+1.50%**, with overlapping ranges.
New typed read-tier median changes stay within 1.6%; repeated peak is +0.62% (observed fork ranges do not overlap),
mixed peak +0.91%, churn peak +0.52%, and nested peak −1.11%. These are not universal JSON speedups.

All **34 Java allocation cells match exactly**, including the controls. All pre-existing graph/allocation
budgets pass unchanged. The historical flat scalar ≥1.1x On-Demand admission threshold is not lowered;
the retained flat specialization still measures 1.822–1.881x in the revised native probe. Earlier unmet
generic producer throughput and physical-memory goals remain open, not waived or claimed solved.

The Gson ingest controls are noisy and are not used to normalize these results. Scalar baseline fork
medians span 2,075–43,798 ns and candidate 2,066–6,890 ns; generic baseline 2,213–4,498 ns and
candidate 2,204–3,383 ns. Churn has a retained 14,121 ns baseline outlier; even its fastest baseline
fork is slower than the slowest candidate. No sample is removed.

## Current generic JSON competitors

This is the separate `json-generic` fixture, not any nested typed fixture. All rows use the same
candidate compatibility image and pinned worker group. Java heap excludes native input/index storage;
the native generic 384 B also includes 240 B of the ten-read harness. These are ingest allocations,
not total retained-memory comparisons. The Gson point estimate must be read with the noisy ranges above.

| Engine | Ingest ns/document | Compiled reads ns/document | Java B/document, ingest |
|---|---:|---:|---:|
| fastjson2-tree | 650.563 | 85.709 | 1372 |
| dsljson-map | 801.905 | 89.897 | 1080 |
| simdjson-generic-interop | 885.299 | 28.793 | 384 |
| jsoniter-any | 997.095 | 115.951 | 1508 |
| jackson-tree | 1,305.215 | 66.987 | 2245 |
| gson-tree | 2,665.314 | 236.878 | 13404 |

Full stage/control medians, ranges and Java allocation figures: [paired.md](paired.md).
The six original reports, smoke outputs, test logs and XML results are under [paired/](paired/);
`paired/manifest.json` records their hashes, exact image hashes and the invocation.
