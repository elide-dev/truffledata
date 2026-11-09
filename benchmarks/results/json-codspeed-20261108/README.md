# JSON simulation instruction counts — 2026-11-08

The resumed JSON candidate removes the two reported instruction-count regressions in a
local amd64 Valgrind reproduction. These are instruction counts, not elapsed-time or
session-throughput results. No CI run or push was used.

## Measurements

Each case uses 256 rows and 8 iterations, with collection toggled around
`__codspeed_root_frame__databench`. The `.2` callgrind part is the benchmark client dump;
its `totals` equals the process's `Collected` count. Checksums agree for each case across
all measured versions. `counts.json` contains 76 recovered measurements and `final/counts.json` the four final measurements, `builds.json` identifies
source bases, build invocations, and exact executable SHA-256 values, and `callgrind/`
contains the original compressed raw output and console logs.

| Build | Java export Ir | Interop read Ir | Snapshot Ir |
| --- | ---: | ---: | ---: |
| main `223edf7` | 71,313,261 | 34,545,961 | 55,956,519 |
| `7e0167b^` (`0ff76f6`) | 80,532,968 | 38,039,775 | 68,337,713 |
| `7e0167b` | 80,647,316 | 37,662,661 | 69,616,007 |
| pushed `a94ec5b` | 83,337,768 | 41,055,455 | — |
| before `1cfcb37` (`head`) | 83,767,648 | 41,485,335 | 59,985,867 |
| final candidate | 60,138,974 | 31,656,185 | 53,497,031 |

Candidate export/read counts are 15.67%/8.36% below main, and 28.21%/23.69% below
the local before image. Snapshot is 4.40% below main. Final Gson control moves +0.04%.
The original directory-policy hypothesis is rejected for these two cases: `7e0167b`
changes export by +0.14% and interop by -0.99% relative to its parent. Recovered
intermediate counts place an export increase between `6af2de0` (72,336,954) and the
owner-fusion commit `4a3c682` (80,538,604); this comparison alone does not isolate every
later interop cost.

The other 24 existing simulation cases were also recovered. Against `head`, YAML Java
export/interop/snapshot improve 4.27%/4.61%/1.02%; YAML Truffle export increases 0.61%.
All other cases increase by at most 0.17%. These small changes are not throughput claims.

## Change and review

- Export loads each compact header once, reuses it for event text, and loads the node
  count once while the owner is pinned. Scalar payload loads retain extent checks.
- Native directory hints use the same validated-object contract as `TapeReader`'s raw
  `readWord` path. Key/rank/type/clean-string checks and full short-key equality remain.
- Interpreter owner reads use the final storage/confinement fields; partially evaluated
  reads keep the class-specific hooks. Current overrides satisfy the documented identity
  and confinement equivalence. Owner close/thread checks remain on cached paths.
- Shape templates install on the second eligible object at a depth, so one-shot documents
  avoid installing an unusable template. Repeated shapes start hitting on the third object.
  This adds one startup miss per depth; two-object workloads can lose their former second
  hit. No session-throughput claim or throughput gate waiver is made here.
- Text kinds 6/7 use their literal 24-bit lengths. Only container kinds 8/9 use the
  `MAX_COUNT` sentinel; native writers reject larger text before publication.

The source checkpoint `57a4215` omitted three edits already present at resumption:
removing concrete `word` overrides (preserving compiled generic-reader inlining) and
checking export words against its cached count. Those edits are retained. At resumption the cached
simulation output SHA-256 matched the recovered `fix4` executable exactly. Review then
removed unused owner hint/ASCII wrappers and added hook-equivalence tests; the final build
and replay below qualify that cleanup separately.

## Validation and reproduction

The recovered final-source run passed 31 JVM/native JSON, tape and YAML tests plus
`//benchmarks:native_smoke` in invocation
`7d966a93-589f-4bed-8c24-4a38282e0d3d`. Resumption adds native directory-hint edge tests
for standalone and fused storage, covering empty/7/8/9/15/16/17-byte keys, non-ASCII,
escaped keys, rank misses, high-word mismatches and non-object nodes; native-only
coverage exclusions point to these tests. Fresh validation is recorded below.

Build `//benchmarks:simulation` at each source with the repository's serialized Bazel
wrapper and GraalVM 25. Preserve the executable before changing source. Build the local
container with this directory's `Dockerfile` (`docker build --platform linux/amd64 -t
td-valgrind .`). Run each preserved executable with:

```sh
docker run --rm --platform linux/amd64 \
  -v /absolute/binary-directory:/b:ro -v /absolute/output-directory:/out td-valgrind \
  valgrind --tool=callgrind --toggle-collect=__codspeed_root_frame__databench \
  --callgrind-out-file=/out/cg.LABEL.CASE /b/truffledata-simulation CASE
```

Use `json-generic-java-export` and `json-generic-interop-read` for the two reported
regressions, plus `json-generic-snapshot` and `json-generic-gson-tree` for controls.
The container uses Valgrind 3.19.0 on Debian bookworm under amd64 emulation; timing is
not suitable for a latency comparison. Raw instruction counts and checksums are the
reported evidence.

Fresh resumption replay: all eight main/parent/7e0167b/candidate export/read counts and
checksums exactly reproduce the original records. Raw replay output, counts and host/container
metadata are in `replay/`. This replay used preserved executable hashes from `builds.json`
while unrelated builds ran remotely; it invoked no Bazel or remote timing jobs.

Final build/test invocation `cdcbc5cc-eddc-4c27-98e5-39b5666ea180` passes all nine
requested tests: JSON/YAML JVM and native acceptance, tape JVM, compact writer,
million-sequence shape oracle, benchmark native smoke, and JSON comparison native smoke.
The latter passes 24 compilation graphs and nine allocation cells without changing gates:
retained interop 1,954/1,898 nodes and snapshot 1,969/1,913, each one loop, zero invokes
and zero allocations; generic ingest 384 B/document and snapshot ingest 1,183.5 B/document.
Final compiled source hashes are in `source-manifest.json`; final binary identity, raw
replay data and test logs are archived. The rebuilt image retains the export improvement
and differs by only -14 interop instructions from the recovered candidate. No benchmark
or Bazel process remains active at this checkpoint.
