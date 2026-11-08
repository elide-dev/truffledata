# CSV span-only retained construction experiment

Status: correctness and native gates pass; the complete paired x86 SVM run
measures a modest improvement. This is an isolated all-STRING specialization. Typed conversion,
column representation, plain framing and the native structural indexer are unchanged.

## Source and binaries

- Baseline production: `9a1dcec`; expanded harness: `d3f40cf` (includes the same
  owned retained-string engines as `530dd4f`).
- Candidate production/tests: `234304c`; owned harness: `530dd4f`; paired harness
  through `80055a3`. The paired runner may evolve independently of the image.
- Baseline SHA256: `d06b6f24ac6ad419deee364a7deac583fe397037c62fd3f5cb4be84ccbb8cc24`.
- Candidate SHA256: `70011b64c52761f25192669443cbbd4cfbdc2a882f0e130583733a5d17e1f27a`.
- Both images use the pinned Native Image SDK, O2, serial GC and x86 compatibility
  code. Candidate build reports Java `25.0.4.1.1+1`, GraalVM CE `25.5.5-dev+1.1`.
  No PGO. Arm performance is not established by this experiment.

The candidate selects the span-only loop once at scan entry for fusable plans
whose retained fields are all STRING. It reuses the field map and writes spans;
the existing width/UTF-8 commit and per-record fallback remain intact. The new
loop was mechanically compared with `frameFused`: only method/writer names differ.
This keeps numeric parser code out of the text-only host-inlining root.

## Validation

[Invocation b7b8fae5](https://app.buildbuddy.io/invocation/b7b8fae5-72dd-4dc7-9263-a639a0cbca99), exit 0:

- `//csv/tests:jvm`: 121 tests, zero failures/errors.
- `//csv/tests:jvm_indexed`: 121 tests, zero failures/errors, including forced
  indexed framing over small chunks and per-byte differential behavior.
- `//benchmarks/comparison:csv_native_smoke`: 162 throughput cells agree;
  truffledata typed-read compiled graphs have 439/383 nodes, one loop, zero
  invokes, zero allocation sites (budgets 1000/1/0/0). Peak allocated bytes/row
  are `[1.0, 1.0, 1.0]`, within the 1.0 harness-inclusive gate.

The added semantic cases cover reordered string projections, discarded malformed
fields, growth and partial-row fallback, copied/owned parses, row limits and
UTF-8 versus width error order. Existing suites cover Commons dialects, configured
nulls, readers, mapping and typed values. JVM timing is not an admission gate.
Compressed original test logs/XML are adjacent to this file.

[Invocation 8a5373d6](https://app.buildbuddy.io/invocation/8a5373d6-b622-45fb-bb12-ad87b7df19c2)
downloaded the successful candidate image, exit 0. Both images are preserved in
ignored local `build/csv-span-{baseline,candidate}/csv-comparison` paths.


## Paired end-to-end SVM results

[Invocation 07bb26fb](https://app.buildbuddy.io/invocation/07bb26fb-e67e-4d68-b0fe-a2eb4cdf5d8b),
exit 0, elapsed 854.9 seconds. Eight fixture/size shards ran 468 fresh processes:
three forks per binary for each of 78 cells. Each process reports five ingest time
samples (three samples for typed reads) and three allocation samples. Results below
use the median of the three fork medians; ranges retain the fork minima/maxima.
The paired runner shuffles/interleaves binaries within each shard, pins each pair
to one allowed CPU, and spreads shard affinities. Each pair runs on the same worker.
Workers report AMD EPYC, Linux x86_64; the selected native kernel is
`native-avx512-svm-leaf`, while the image's Java code uses x86 compatibility ISA.
Both Java-SWAR and native-indexed SVM library paths are measured. This does not
establish AVX2-only, v3-image or Arm performance.

All checksums, input hashes, row counts and execution tiers agree. Every cell has
identical baseline/candidate allocated bytes per row. `summary.csv` records all
78 cells, raw medians/ranges and allocation values; compressed original reports,
raw samples, provenance and logs are in `pair/`. `source-hashes.json` records
source and SDK archive hashes. No outlier forks were dropped.

The geometric mean candidate/baseline elapsed-time ratios across the eight string
fixture/size cells are 0.9821 for copied/native, 0.9802 for owned/native, 0.9853
for copied/Java-SWAR and 0.9836 for owned/Java-SWAR. These unweighted aggregates
indicate roughly 1.5–2.0% lower elapsed time, **not a step-change improvement**.
No JVM timing was used as a gate. The owned-input API predates this candidate;
its larger saving over copied input is not attributed to this change.

### Native-indexed retained string consumption

All values are ns/byte. Each bracket contains the range of three fork medians.
A ratio below 1 means less elapsed time. Both variants build and retain the batch,
then read every String cell; the owned variant transfers immutable input storage.

| Fixture | MiB | Input | Baseline median [range] | Candidate median [range] | Ratio |
|---|---:|---|---:|---:|---:|
| numeric | 1 | copied | 4.3808 [4.3557–4.4900] | 4.3015 [4.2809–4.4272] | 0.9819 |
| numeric | 1 | owned | 3.9011 [3.8995–3.9357] | 3.8704 [3.8451–4.0073] | 0.9921 |
| mixed | 1 | copied | 3.7278 [3.7237–3.7783] | 3.6495 [3.6293–3.6531] | 0.9790 |
| mixed | 1 | owned | 3.2381 [3.1848–3.3079] | 3.1187 [3.0896–3.1454] | 0.9631 |
| quoted | 1 | copied | 1.1556 [1.1555–1.1636] | 1.1555 [1.1547–1.1608] | 0.9999 |
| quoted | 1 | owned | 0.7212 [0.7132–0.7256] | 0.7087 [0.7011–0.7088] | 0.9827 |
| escaped | 1 | copied | 2.3480 [2.2990–2.3580] | 2.3565 [2.3331–2.3612] | 1.0036 |
| escaped | 1 | owned | 1.9874 [1.9766–1.9937] | 1.9700 [1.9626–2.0109] | 0.9913 |
| numeric | 128 | copied | 4.9379 [4.9252–4.9891] | 4.8512 [4.8412–4.8577] | 0.9824 |
| numeric | 128 | owned | 4.4608 [4.4487–7.8029] | 4.3328 [4.3025–4.5290] | 0.9713 |
| mixed | 128 | copied | 6.4720 [6.4179–6.6372] | 6.4326 [6.2456–6.5965] | 0.9939 |
| mixed | 128 | owned | 5.7469 [5.6826–5.8552] | 5.5824 [5.5049–7.4287] | 0.9714 |
| quoted | 128 | copied | 1.2177 [1.2068–1.2282] | 1.1985 [1.1982–1.2105] | 0.9842 |
| quoted | 128 | owned | 0.7597 [0.7594–0.7689] | 0.7623 [0.7520–0.7683] | 1.0033 |
| escaped | 128 | copied | 3.2515 [2.9275–3.2612] | 3.0343 [2.9284–3.0415] | 0.9332 |
| escaped | 128 | owned | 2.5820 [2.5302–2.5926] | 2.4979 [2.4959–2.7439] | 0.9674 |

The cleanest favorable ranges include mixed 1 MiB copied and owned, numeric
128 MiB copied, and quoted 1 MiB owned. Other cells overlap, including the apparent
6.7% large escaped/copied median improvement; it should not be presented as a
uniform gain. Large allocating forks remain noisy (for example, numeric
128 MiB owned baseline 4.449–7.803 ns/byte; mixed 128 MiB owned candidate
5.505–7.429 ns/byte). Reference engines and all their ranges remain in the report.

### Typed and read controls

The typed/native workload has a 0.9797 aggregate ratio across its four
fixture/size cells; typed/Java-SWAR is 0.9959. Its conversion loop is unchanged, but
BatchBuilder fields/initialization and scan-entry selection also affect this path:
it is not an untouched control. These shifts limit attribution of every small
timing change specifically to span dispatch. No typed writer specialization was
implemented. The truffledata retained-read ratios are 1.0044 for read-aot, 1.0143
for interpreter and 0.9941 for peak (see exact values/ranges
in `summary.csv`); allocation is unchanged at 0/2/1 B per row respectively.
The native compiled-graph and allocation gates above passed. Read ranges overlap:
read-aot 71.141–72.249 versus 71.094–72.060 ns/row; interpreter 71.673–72.256
versus 71.761–84.853; peak 6.544–6.604 versus 6.556–6.587.

The external reference implementations are unchanged. Their geometric-mean time
ratios and per-cell ratio ranges are:

| Workload | Reference | Cells | Geometric mean | Per-cell ratio range |
|---|---|---:|---:|---:|
| strings | FastCSV | 8 | 1.0002 | 0.9935–1.0092 |
| strings | Jackson | 8 | 1.0003 | 0.9840–1.0155 |
| strings | kernel-native | 8 | 1.0042 | 0.9966–1.0108 |
| typed | FastCSV | 4 | 1.0031 | 0.9942–1.0125 |
| typed | kernel-native | 4 | 1.0071 | 0.9958–1.0344 |

These stable aggregate references support a modest overall SVM effect. Individual
cells still carry shared-host noise and image-layout effects; instruction-count
history alone is not evidence of a larger speedup.

Admission recommendation: retain as an incremental x86 SVM optimization, subject
to final integration coverage and x86/Arm semantic acceptance. Copied/owned and
Java/native-indexed aggregates all favor the candidate, selected fork ranges
separate, allocations are identical, compiled-read gates pass, and the completed
read controls do not establish a regression. Do not call this a step-change or
claim an Arm speedup. The duplicated framing loop is a maintenance cost: keep it
in step with frameFused and retain the forced-index differential suites. The
measured evidence does not justify proceeding immediately to more typed writer
specialization without another focused profile or experiment.

A separate native acceptance addition, `6992a00`, exercises 1024 retained string
rows above the index threshold with projections, reordered columns, escaped
quotes/newlines, UTF-8, copied/owned input and malformed/width failures. It does
not affect either measured image. The integration lane owns final x86/Arm
acceptance and global coverage qualification.

## Reproduction

The baseline image is exported by the ignored local target
`//build/csv-span-baseline:csv-comparison`. It must match the SHA256 above.
Run through the repository's serialized `bzl` wrapper:

```sh
bzl test //benchmarks/comparison:csv_pair \
  --//benchmarks/comparison:csv_svm_baseline=//build/csv-span-baseline:csv-comparison \
  --test_env=CSV_PAIR_BASELINE_REVISION=9a1dcec+expanded-harness-d3f40cf \
  --test_env=CSV_PAIR_CANDIDATE_REVISION=459625b+production-234304c \
  --nocache_test_results --remote_download_outputs=minimal --test_output=errors
```

The measured runner includes the shard affinity fix at `459625b` (parent commit
`32d5245`). Both images and the original source commits remain local; nothing
was pushed or published.
