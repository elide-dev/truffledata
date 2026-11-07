# JSON state-of-the-art probe (plan `docs/superpowers/plans/2026-11-07-json-sota.md`), measured at `ce7399f`

What a state-of-the-art producer costs for every JSON use case with the strictness kept (duplicate
keys rejected after unescaping, exact number tokens, strict UTF-8, nothing published before the
whole document passes), against the shipped compact-tape decoder (`V5`, tape-gbps Task 2) and
simdjson's own DOM (`D0`). The fixture is the comparison harness's: one record, 20, 1,000 and
20,000 records in an array (210 B, 4.3 KB, 228 KB, 4.85 MB) and the same 20,000 records as NDJSON.

**Host and caveats.** Every row ran on a shared BuildBuddy RBE Linux amd64 executor (Firecracker
microVM; simdjson 5.1.1, stage 1 `icelake`), as a temporary `cc_test` (`//json/native:sota_probe`,
source retained here as `sota_probe.cc`, removed before the commit) built with the repository's
hermetic toolchain at `-O3` and ThinLTO, one process per round, best of 9 rounds of N repetitions
per row, rows of one document back to back. Workers differ between rounds: compare rows within a
round. Unclemax (Threadripper PRO 9965WX) runs this code ≈ 1.35–1.4× faster (`V4` is 450–466 ns
small / 9.3 ms huge here against 340 ns / 7.05 ms in `json-v4-86a691a`).

| File | What it is |
|---|---|
| `round1-sse2.txt` | the repository's default build (no `-march`: plain x86-64; simdjson's On-Demand "builtin" implementation is `fallback`) — invocation `5078efd2-cb03-4910-8e98-155e1c514371` |
| `round2-avx2.txt` | the same probe built with `--copt=-march=x86-64-v3` (the probe's own scans 32 bytes wide; simdjson's builtin stays `fallback` because x86-64-v3 lacks PCLMUL, which `haswell`/`westmere` require) plus the `S3x` row |
| `round3-sse2-lean-hit.txt` | **the headline round**: the default build with the lean shape-cache hit path (one 16-byte SIMD compare per key, nothing pushed on a hit; a miss materializes the earlier keys from the template) |
| `sota_probe.cc` | the probe as run in round 3 (rounds 1–2 differ only in the hit path: masked 8-byte loads and a push of hash, bytes and offset per key) |

## Rows

- `S0` simdjson stage 1 (structural index + UTF-8 validation) through `dom_parser_implementation`; `S1` + a classification of every index (the floor of any stage 2).
- `D0` simdjson's DOM parse: its own stage 2 (tape, every string copied, numbers typed, no duplicate check, no token spans).
- `W2c` the shipped On-Demand lean walk into an inline checksum (the honest On-Demand floor).
- `V4` the 0.8.0 Myna-tape decoder; `V5` the shipped compact-tape decoder (`tape_decoder<ctape_writer>`); `V5v` its validate-only walk (`null_writer`).
- `V5s` the same On-Demand walk into a **shape-cached writer**: one key-sequence template per depth (hidden-class style). A key of at most 32 bytes is compared by masked 8-byte loads against the template; an object whose keys match the template in order holds no duplicate (the template was checked) and its directory is written through the template's precomputed permutation with the cached FNV-1a hashes; a miss sorts as `V5` does and installs the object as the template. `V5sn` without directories (what sink consumers need); `V5sv` validate-only.
- `S3` **our own stage 2 over simdjson's structural index** writing the compact tape in the `ctape` layout: SSE2/AVX2 quote-or-backslash scans for clean strings (referenced, no copy), SIMD-copy unescaping into the pool, simdjson's own `parse_number` (bit-identical typing), exact token spans from the index, literals, depth, trailing input, the shape-cached duplicate check and directories, arrays presized from the structural count (no per-push checks) with capacity retained across documents. `S3n` without directories; `S3v` validate-only (no tape); `S3k` with checked pushes from the production hint sizes over a reset block arena (what presizing and retained capacity are worth); `S3h` with the templates cleared before every document (a session's first document of a new shape); `S3x` (round 2) `S3n` with **no duplicate check at all** — the strictness dropped, to price it.
- `N0` NDJSON through `iterate_many` (1 MiB batches) + `V5` per document: the shipped stream path. `N1` one stage 1 over the buffer + `S3` per document; `N1n` without directories.

**Correctness** (printed at the top of each round): over the 200-document corpus `S3` and `V5` produce
bit-identical words, pools and directories (200/200), `ctape::validate` accepts every `S3` tape, and
over the 60 malformed documents both reject all 60.

## Round 3 (default build, lean hit path): MB/s, small in ns

| Row | small 210 B (ns) | medium 4.3 KB | big 228 KB | huge 4.85 MB |
|---|---:|---:|---:|---:|
| S0 stage 1 | 20 | 16,278 | 16,440 | 15,733 |
| D0 simdjson DOM | 145 | 1,794 | 1,734 | 1,765 |
| W2c On-Demand floor | 211 | 1,042 | 1,063 | 1,097 |
| **V5 shipped** | **351** | **656** | **676** | **709** |
| V5v shipped validate-only | 312 | 714 | 751 | 777 |
| V5s shape cache | 313 | 707 | 727 | 760 |
| V5sn shape cache, no directories | 299 | 751 | 766 | 794 |
| V5sv shape cache, validate-only | 256 | 869 | 893 | 925 |
| **S3 own stage 2 + directories** | **252** | **1,016** | **1,022** | **1,064** |
| S3n no directories | 241 | 1,085 | 1,084 | 1,131 |
| S3v validate-only | 206 | 1,323 | 1,327 | 1,384 |
| S3x no duplicate check (strictness dropped) | 218 | 1,290 | 1,305 | 1,327 |
| S3k checked pushes, hint sizes, reset arena | 276 | 951 | 974 | 1,019 |
| S3h templates cleared per document | 309 | 1,004 | 1,024 | 1,059 |
| NDJSON N0 (stream path today) | | | | 681 |
| NDJSON N1 (stage 1 + S3 per document) | | | | 1,074 |
| NDJSON N1n (no directories) | | | | 1,147 |


Ratios (round 3):

| Ratio | small | big | huge |
|---|---:|---:|---:|
| S3 / V5 (retained) | 1.39 | 1.51 | 1.50 |
| S3n / V5 (sink consumers) | 1.46 | 1.60 | 1.60 |
| S3v / V5v (validate-only) | 1.51 | 1.77 | 1.78 |
| S3v / W2c (our walk + check vs On-Demand without a check) | 1.02 | 1.25 | 1.26 |
| S3n / S3x (the duplicate check, shape-cached) | 0.90 | 0.83 | 0.85 |
| S3 / D0 | 0.58 | 0.59 | 0.60 |
| V5s / V5 (shape cache alone, On-Demand) | 1.12 | 1.08 | 1.07 |
| V5sv / V5v | 1.22 | 1.19 | 1.19 |
| S3 / S3k (presizing + retained capacity) | 1.10 | 1.05 | 1.04 |
| N1 / N0, N1n / N0 (NDJSON) | | | 1.58, 1.68 |

Memory: `S3` produces the same tape as `V5` (1.91–2.33 × input with directories); `S3n` drops the
directory (8 B per member: 1.35–1.49 × input).

## Round 1 (default build, first hit path) and round 2 (`-march=x86-64-v3`)

Round 1 `S3` measured 941 / 985 MB/s (big / huge; 1.39 × / 1.38 × `V5`), `S3v` 1,110 / 1,150, `S3n`
981 / 1,011, N1 992; the first hit path (masked 8-byte loads, three pushes per key) cost ≈ 4.4 ns per
key against `S3x` (round 2: 1,265 / 1,295), which round 3's lean path cut to ≈ 2.5–3 ns on the sink
rows and to nothing on validate-only. Round 2 against round 1: `S3` 931 / 963, `S3v` 1,080 / 1,128,
`V5` 672 / 708 — within noise: 32-byte scans do not help the fixture's short strings, and the
`-march` flag does not change simdjson's builtin (still `fallback`), so On-Demand's string and
number kernels stay scalar in every build the repository makes today.
