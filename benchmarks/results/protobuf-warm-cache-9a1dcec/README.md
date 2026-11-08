# Protobuf slab warm cache — admitted for SVM

Baseline: `9a1dcec`, with B4 Phase 1 restored and the slab allocation work integrated.
SVM is the performance target; JVM runs validate semantics and coverage. No PGO.

**Refined candidate `e4cec9f` is admitted as an incremental x86 SVM warm-read win.**
Full slab interpretation improves 16.2% and peak reads 32.1% in the final complete
pair. All allocation/retention observations and existing correctness/native gates
hold. B4 remains open: full slab interpretation is still 5.652× stock. The
initial candidate, its cold-read cost and the refinement are preserved below.

## Restored baseline profile

The [profile summary](baseline-profile/summary.json) comes from a fresh
`consumer_interpreter_profile` run (`8bad3434`). The four original JFRs (gzip), JSONL
records, binary checksum and compressed symbols are retained alongside it.
Each engine has 3,000 application execution samples. These samples identify
where to experiment; they are not elapsed-time speedups.

Generated interpretation no longer attributes a leading leaf cost to
`Accessor.bound`. The restored baseline slab image attributes 647/3,000 leaf samples (21.6%) there.
Slab cache paths account for 281 inclusive samples in `row` (9.4%), 102 in
`text` (3.4%) and 128 in `array` (4.3%). These inclusive counts may overlap.

The first isolated candidate removes synchronization and Truffle boundaries from
warm record/text/blob handle hits. A volatile table reference publishes the table;
acquire/release element operations publish each entry. Synchronized miss paths
recheck the slot before construction, and lazy children publish only after budget
and retained-byte accounting commit. The owner gains no instance fields.
The boxed list/unsigned map remains synchronized, and its representation is
unchanged. Public layout, projection and borrowed lifetime checks are unchanged.

New concurrency tests race cold and warm row/text/blob/list reads, check stable
identity and initialized contents, and ensure a lazy child charges its budget
once. The existing boundary count remains 16: three boundaries move to misses.

## Reproduction

The exact preserved baseline image is described by
[baseline-provenance.json](baseline-provenance.json). The image and its Bazel
alias live in ignored `build/protobuf-warm-before`; invocation-only baseline
configuration is not committed.

```sh
bzl test //benchmarks/comparison:consumer_measure \
  --//benchmarks/comparison:consumer_svm_baseline=//build/protobuf-warm-before:consumer_svm \
  --test_env=TRUFFLEDATA_CONSUMER_BASELINE_ENGINES=stock,stock-interop,generated-owned,slab,slab-native,slab-projected,slab-lazy,slab-session,slab-session-projected \
  --test_env=TRUFFLEDATA_CONSUMER_BASELINE_MODES=sparse,full,holdout,holdout-sparse,large \
  '--test_env=TRUFFLEDATA_BENCH_REVISION=baseline 9a1dcec; candidate f045f5c; SDK 25.5.5-elide.27'
python3 tools/bench/consumer.py merge --output /new/merged shard_*/results.json
```

## Correctness checkpoint

Candidate `f045f5c` passes all seven focused targets in
`4097845d-7c64-4600-b5d9-1214fbd26045`: core, protobuf runtime, Buffa JVM strict and
non-strict, compiled allocation, native allocation and consumer native smoke.
[Logs and XML](correctness/4097845d/) are preserved. All 36 runtime tests pass,
including the concurrent cold/warm and lazy-publication tests. The consumer smoke
passes all 132 cells and unchanged graph/AST gates. Native scalar allocation
remains 168 B/message generic and 136 B/message typed/generated, with a 56 B slab
and 112 B publication (64 B array + 48 B owner). Compiled accessor allocation
remains 0.000044 B/read generated and 0.000556 B/read for other representations,
below 0.05. Parent will run combined nine-target method coverage after admission.

## Initial candidate: complete paired SVM matrix

The common preserved-image invocation `950032e5-dc4f-40f9-a67f-0a73f3b5dce3`
passes all ten consumer shards. Both hashes in every report match the
[pair manifest](svm/pair-manifest.json). The [complete report](svm/merged/results.md)
and [raw JSON](svm/merged/results.json.gz) contain **264 summary cells / 1,584 raw
records**, all nine engines and five supported modes, three fresh forks per cell.
[Assessment](assessment.json.gz) preserves every before/after and stock ratio.
No Java-allocation or retained-memory metric changes in any of the 132 pairs.

All times below are ns/row, lower is better. Ranges are observed ranges of three
fork medians, not confidence intervals; shared-worker timings remain advisory.

| SVM workload | Baseline | Initial candidate | Change |
|---|---:|---:|---:|
| Slab full interpreter | 412.31 [412.13, 412.52] | 350.79 [346.99, 353.84] | −14.9% |
| Slab full peak | 311.37 [311.19, 527.98] | 206.95 [204.74, 271.55] | −33.5% |
| Slab sparse interpreter | 52.30 [52.23, 52.81] | 44.78 [43.94, 46.47] | −14.4% |
| Slab sparse peak | 29.50 [29.37, 744.22] | 21.02 [20.95, 21.11] | −28.7% |
| Native slab large peak | 42,159.29 | 23,407.64 | −44.5% |
| Projected slab sparse first read | 52.47 [52.10, 53.73] | 57.02 [54.82, 58.43] | +8.7% |

The native/lazy/projected warm-read variants also improve. Full generated
interpretation is stable at 217.60 → 216.11 ns; generated sparse peak changes
9.723 → 9.774 ns (+0.52%, narrowly disjoint ranges), while its stock control rises
0.92%. The full interpreter/stock ratios remain **5.698× slab and 3.510× generated**,
so the absolute B4 ceiling of 2.5× remains open.

The direct cold-read tradeoff is coherent across sparse variants: first read is
+3.9% slab (wide overlapping ranges), +4.3% native, +8.7% projected and +4.5% lazy.
The initial implementation decodes the handle before the acquire, then decodes
it again on the synchronized miss. A separate handle-forwarding refinement is
being qualified; the initial production commit/image and complete measurements
remain preserved. **Initial candidate `f045f5c` is not yet admitted.**

Other observed changes require a separate attribution: native sparse decode is
+5.25%, unprojected session full resident +5.64%, and stock-interop full interpreter
+5.47%, all with disjoint observed ranges. Native decode never calls the changed
row/text/blob helpers; unprojected session reads use the unchanged SlabSession
caches. SVM slab and slab-native decode use the same producer path, yet sparse
slab decode is −0.37%. Same-shard controls are mixed (sparse decode stock +0.86%,
generated −5.65%; full resident stock −1.18%, generated −4.98%). These observations
remain disclosed without attributing them directly to cache-hit execution.
Full slab resident is +18.2% with wide overlapping ranges, including a 19.8 µs
candidate fork; native slab resident is +0.23%. No noisy fork is discarded.

The initial image is 43,978,864 bytes versus 43,126,896 baseline (+1.98%). Both
preserved images remain ignored. These are x86 SVM timings; final shared JVM and
x86/Arm native acceptance, including cold/warm publication races, is separate.


## Handle-forwarding refinement checkpoint

Separate production commit `e4cec9f` forwards the already decoded handle into the
synchronized row/text/blob miss helpers and lazy realization. Acquire/release
publication, synchronized rechecks, validation, and the warm-hit branch remain
unchanged. An independent review verified every forwarded handle uses the same
immutable-slab offset as the removed reload. The initial `f045f5c` image remains
preserved; no initial measurements are silently reassigned to this refinement.

All seven correctness/allocation/native targets pass again in
`4902bb19-0778-4e32-96cc-4a4fee73f708`, including 132 consumer native cells and the
new shared `TypedAcceptance` cold/warm races on native-backed and Java-backed
JVM decoders in strict and non-strict modes. Scalar allocation remains 168/136 B,
publication 112 B (64 B array + 48 B owner), compiled reads 0.000044/0.000556 B.
[Refined source/image provenance](refined-candidate-provenance.json) and
[validation logs](correctness/4902bb19/) are preserved. Final x86/Arm acceptance
and combined method coverage remain the integration lane's responsibility.
The refined image was independently remeasured in the final pair below.

## Final refined pair and admission

Invocation `1159ca42-a11f-41d3-8f5f-4ef20be794c2` passes all ten consumer shards:
**264 summary cells / 1,584 raw records**, complete, all nine engines/five modes,
three fresh forks. Every shard matches the [refined manifest](refined-svm/pair-manifest.json).
[Final report](refined-svm/merged/results.md), [raw JSON](refined-svm/merged/results.json.gz)
and [assessment](refined-assessment.json.gz) retain every cell, range, checksum and
memory observation. No fork is discarded. All 132 allocation/retention pairs are
exactly unchanged. All four primary slab ranges below are disjoint.

| SVM slab workload (ns/row) | Baseline | Refined | Change | Refined / stock |
|---|---:|---:|---:|---:|
| Full interpreter | 418.926 | 351.174 | −16.17% | 5.652× |
| Full peak | 310.534 | 210.799 | −32.12% | 2.001× |
| Sparse interpreter | 52.867 | 44.072 | −16.64% | 4.103× |
| Sparse peak | 30.053 | 21.134 | −29.68% | 1.795× |

Native and lazy counterparts improve too; native large peak improves 42,109.45 →
23,323.20 ns (−44.61%, disjoint), and native large resident improves 370,253.13 →
340,526.13 ns (−8.03%, disjoint). Ordinary slab large peak improves 41.0% in the
median but its ranges overlap; it is not presented as equally conclusive.

Controls (baseline → refined ns/row): full interpreter stock 61.783 → 62.137,
generated 215.428 → 215.656; full peak stock 106.057 → 105.368, generated 48.605 →
48.481. Sparse interpreter stock 10.796 → 10.742, generated 32.499 → 31.871;
sparse peak stock 11.745 → 11.773, generated 10.916 → 10.749. Generated ranges
all overlap. Sparse stock interpretation has a large outlier (candidate maximum
451.49 ns), so its ratios should not be read as precise confidence bounds.
Full generated interpretation remains 3.471× stock. The absolute B4 2.5× goal
remains open; this is an incremental improvement, not completion of that goal.

The avoidable duplicate handle load is removed. Projected sparse first read now
measures 54.763 → 56.071 ns (+2.39%, ranges overlap), compared with the initial
+8.68% disjoint observation. Small direct cold-read costs remain: native sparse
first 47.255 → 49.376 ns (+4.49%, disjoint), lazy sparse first 56.114 → 57.938 ns
(+3.25%, disjoint). Full native first improves 8.36% with disjoint ranges.
No final pair has a slowdown above 5% with disjoint ranges; that observation does
not erase the smaller costs or create a new gate. Full resident ranges overlap
for all owning variants. The earlier unprojected-session full-resident increase
does not reproduce (now −1.57%, overlap); that path uses unchanged session caches.

**Decision:** admit the refined warm-cache implementation with these cold-read
tradeoffs disclosed. No threshold, lifetime/schema/projection validation, graph
budget, allocation floor, or coverage requirement is relaxed. The list/unsigned
map stays synchronized. JVM timing is non-gating; no JVM timing cycle was run.
Parent integrates production/tests as `de2e498`, `598ce6f`, `4fd49c2`, `7fca64b`
and owns final combined coverage plus x86/Arm native acceptance. These paired
elapsed timings are x86 measurements, not an Arm performance claim.

For the final preserved-image pair, use the integration harness's optional
candidate override with `//build/svm-tranche-pair:consumer-refined` and baseline
`//build/svm-tranche-pair:consumer-baseline`; keep all nine engines and five modes
from the reproduction command above. The revision label is
`protobuf-e4cec9f vs production-9a1dcec`. Invocation-only image labels and payloads
remain ignored. See [decision metadata](decision.json).

## Compact evidence and exact recovery

Raw/merged result JSON, test/invocation logs, XML and JFRs are stored as deterministic
gzip (`mtime=0`, no stored filename). Readable reports/tables and small provenance
files remain plain. [Archive manifest](archive-manifest.json) records both compressed
and original SHA256 hashes and lengths; every artifact was decompressed and checked
before removing its plain duplicate. Both complete initial and refined experiments
remain available. To restore exact originals under ignored `build/` for the merge
or profile-summary tools:

```python
from pathlib import Path
import gzip, shutil
source = Path("benchmarks/results/protobuf-warm-cache-9a1dcec")
output = Path("build/protobuf-warm-evidence")
for item in source.rglob("*"):
    if not item.is_file():
        continue
    relative = item.relative_to(source)
    target = output / (relative.with_suffix("") if item.suffix == ".gz" else relative)
    target.parent.mkdir(parents=True, exist_ok=True)
    if item.suffix == ".gz":
        target.write_bytes(gzip.decompress(item.read_bytes()))
    else:
        shutil.copy2(item, target)
```

Merge the recovered `refined-svm/shards/shard_*/results.json` paths with
`tools/bench/consumer.py merge`. For profile analysis, use the recovered
`baseline-profile` directory with `tools/bench/json_profile_summary.py` and the
consumer main class `dev.elide.truffledata.comparison.ConsumerComparison`.
