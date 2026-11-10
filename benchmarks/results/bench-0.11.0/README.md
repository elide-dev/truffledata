# 0.11.0 benchmark capture — 2026-11-10

Raw reports behind the README benchmark cards. Methodology, pooling and regeneration:
[Release 0.11.0 benchmark cards](../../../docs/COMPARISON_BENCHMARKS.md#release-0110-benchmark-cards).
Every plotted value with its range: [cards.md](../../../docs/images/bench-0.11.0/cards.md).

- Source: `091f9d0` (released `v0.11.0` code plus release receipts), every cells flag off.
- Images: built on BuildBuddy, invocation `84d35d8e-f27d-41bb-8cc2-cd4509c617fe`; SHA-256 in
  [manifest.json](../../../docs/images/bench-0.11.0/manifest.json).
- Host: `unclemax`, AMD Ryzen Threadripper PRO 9965WX (24 cores, SMT), idle apart from this run.
  `host_run.sh` ran all 31 shards in sequence, pinned with `taskset -c 12`; [progress.log](progress.log)
  records each shard's start, end and load average.

| Directory | Harness | Shards | Forks per cell |
|---|---|---:|---|
| `json/` | `json_typed_pair` | 9 | 6 (same image as both roles, pooled) |
| `protobuf/` | `consumer_measure` | 10 | 3 |
| `yaml/` | `yaml_pair` | 4 | 6 (pooled) |
| `csv/` | `csv_pair` | 8 | 6 (pooled) |

Each report is the harness's unchanged output (`shard-<index>`; large ones gzipped). The
renderer reapplies the harnesses' validators: checksums per engine, execution tier, input
provenance and complete forks.

## Same-image control

Because the paired harnesses ran one image on both sides, the candidate/baseline ratio of each
cell is an A/A control for this host. On the generic JSON shard every cell is within 2%
(0.990–1.019). An earlier capture of the same tree on BuildBuddy workers was discarded: its
shards ran on mixed AMD EPYC and Intel Xeon workers, and the generic JSON interop cell measured
951, 1,147 and 1,604 ns/document on three shards.

## What the cards show

Numbers below are from [cards.md](../../../docs/images/bench-0.11.0/cards.md).

- **JSON generic** (one fixture, ten member reads): parse + reads 764 ns/document against
  fastjson2 646, DSL-JSON 763, jsoniter 983, Jackson 1,203 and Gson 2,008. Compiled reads take
  27.0 ns against Jackson 64.2 and fastjson2 90.4. Java heap is 384 B against 1,080–13,404 B. The
  384 B include 240 B of read views and exclude native input and index memory.
- **JSON number-heavy** (canada-style coordinates): 7.40 µs against fastjson2 16.0, Gson 26.5 and
  Jackson 77.4; 2.14 KB of Java heap against 23.2–107 KB.
- **JSON typed records**: a compiled `JsonPlan` decodes a three-field record in 157 ns against
  894 ns for a hand-written Gson streaming decoder.
- **Protobuf**: owning slab decode + release 307 ns/message against protobuf-java's 615, and
  75.2 µs against 182 µs on the large message; reading every field as well, 523 against 655. The
  slab allocates 808 Java B/message against 1,630. The scoped session allocates 40 Java B and keeps
  its data in a native arena. Generated messages decode in 661 ns (1.07× protobuf-java's time).
  Compiled guest reads of generated messages take 20.4 ns against 44.7 ns for direct protobuf-java
  getters; interpreted reads take 165 ns against 45.1 (3.67×). protobuf-java objects exposed through
  interop take 1,358 ns interpreted and 1,051 ns compiled.
- **YAML** (no other library in the native image): tape + interop reads 204 MB/s on block
  records and 167 MB/s on flow records; the typed plan allocates 144 Java B/record.
- **CSV**: mixed fixture, typed columns: the retained batch 0.736 GB/s at 1 MiB and 0.662 at
  128 MiB against FastCSV 0.226 and 0.222 (3.3× and 3.0×); the index scan, which retains nothing,
  1.144 and 1.101. Every field as a String: copied batch 0.419 / 0.359, owned batch 0.496 / 0.398,
  index scan 0.582 / 0.580, FastCSV 0.410 / 0.393, Jackson CSV 0.271 / 0.265 GB/s (1 / 128 MiB).
  At 128 MiB the copied batch is 0.91× FastCSV and the owned batch is level with it. FastCSV
  (callback handler) and Jackson (token stream) consume each record as it is read; the batches
  retain columns or Strings for later reads. The SIMD index selected AVX-512 on this host.

Direct protobuf-java getters run ahead-of-time behind one Truffle boundary per message, so the
interpreter comparison is not like-for-like with guest code; the interop row is.
