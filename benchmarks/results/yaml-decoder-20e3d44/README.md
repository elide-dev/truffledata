# Native YAML decoder speed work, measured at `20e3d44`

The YAML plan's Task 2 acceptance hypothesis is session decode ≥ 0.8 × rapidyaml's integer-handler
scan (`ryml-ints(scan)`) on `records-block`, in the same run. At `2c95139` (Task 2) it was missed
at 0.553 (`../yaml-decoder-2c95139`). This directory holds the measurements of the speed work on
`feat/json-completion` after `fac31c3` (Task 3).

**Timings are advisory.** Each figure is the best of 7 rounds of a tight loop, and each run makes
three passes over all fixtures and variants, interleaved per fixture; tables report the best time
of each variant over the passes. Each run lands on one shared BuildBuddy RBE worker (Linux x86-64,
the Elide LLVM toolchain, ThinLTO, `--compilation_mode=opt`); workers differ by several percent
between invocations, so compare rows within one file.

## Files

| File | What |
| --- | --- |
| `rbe-x86-64.txt` | `bazel test //yaml/native:bench --test_output=all` at `20e3d44` (invocation `7dc71106-76a7-41d6-b39a-16faa7f2ed82`) |
| `rbe-x86-64-before-fac31c3.txt` | The same harness (`ebfec2c`'s `bench.cc` and BUILD) with the decoder sources of `fac31c3` checked out (`tape/native/{arena,tape_writer}.h`, `yaml/native/{decoder,input,merge,scalars,session,stream}.h`), invocation `063acc9e-5a79-447c-bd57-48bc27a9bc10` |
| `local-arm64.txt` | The same benchmark built outside Bazel on an Apple M5 Max (Homebrew clang 23, `-O3 -DNDEBUG`, libyaml from the probe archive), at `20e3d44`; the host was busy, so pass-to-pass spread is large |
| `profiles/before-fac31c3/`, `profiles/after-20e3d44/` | `//yaml/native:bench_profile` on RBE: 40 s of `td-session` decode (`td-stream` for `records-stream`) and 40 s of `ryml-ints(scan)` per record fixture, sampled by `yaml/native/sampler.h` (SIGPROF, frame-pointer stacks, 250 samples per CPU second). `samples-*.txt.gz` are the raw load-relative addresses; `folded-*.txt` are stacks symbolized with `llvm-symbolizer --inlining` against the downloaded binary (`--copt=-gline-tables-only`), root first; `summary-*.txt` self and inclusive time; `categories-*.txt` the buckets below; `runs.txt` the decode count per 40 s. Invocations `063acc9e-…` (before) and `7dc71106-…` (after). `fold.py` and `categorize.py` produce them |

Changes since `rbe-x86-64.txt` of `yaml-decoder-2c95139`: the bench links statically, like the
production `yaml_shared.so` (as a dynamically linked test, every dependency was its own shared
object), adds libyaml 0.2.5 and a `td-stream(documents)` row, and makes three passes. Static linking
made both rapidyaml rows faster; the fac31c3 decoder's gate fell from 0.553 to 0.458 under it.

## Results (RBE x86-64)

MB/s, best over three passes; ratios are against the rows of the same run.

| Fixture | ryml-ints(scan) | ryml-tree | libyaml(scan) | TruffleData before (`fac31c3`) | TruffleData after (`20e3d44`) | after / ints | after / tree | after / libyaml |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| records-block (242 KB, the gate) | 295.7 | 240.3 | 74.0 | 142.5 (0.458 × ints) | **211.3** | **0.715** | 0.879 | 2.86 |
| config (3.8 KB) | 391.4 | 346.5 | 93.4 | 189.7 (0.471) | 233.9 | 0.597 | 0.675 | 2.50 |
| records-flow (222 KB, one line) | 352.2 | 283.9 | 67.5 | 157.0 (0.428) | 255.1 | 0.724 | 0.899 | 3.78 |
| records-stream (1,000 documents) | 276.8 | 218.2 | 61.5 | 103.5 (0.372) | 137.6 | 0.497 | 0.631 | 2.24 |
| text-heavy (345 KB) | 721.5 | 813.9 | 260.5 | 520.8 (0.700) | 645.9 | 0.895 | 0.794 | 2.48 |

TruffleData rows are `td-session(decode+reset)` (`td-stream(documents)` for records-stream); the
"before" column's ratio is against its own run's ints row. Retain + release is within noise of
decode + reset in both runs.

**Gate: missed on RBE x86-64.** records-block `td-session / ryml-ints(scan)` = 0.715 (passes
0.704, 0.715, 0.786); before: 0.458 (0.434–0.459). Earlier RBE runs of the same code ranged
0.695–0.723. On local arm64 the same code measured 0.795–0.80 in quiet runs during the work and
0.747 (passes 0.635–0.922) in the busy final run (`local-arm64.txt`). rapidyaml's own tree
builder is 0.813 × ints on RBE; TruffleData's session decode, which also validates the input,
publishes three side tables, checks duplicate keys after resolution and resolves the core schema,
runs at 0.88 × the tree builder.

## Profile breakdown (RBE x86-64, records-block)

Samples per 1,000 decodes (each sample is 4 ms of CPU); the share is of that variant's samples.
The ints scan spends 195–216 samples per 1,000 runs in the engine and 7–8 in its own handler.

| Bucket | before `fac31c3` | after `20e3d44` |
| --- | --- | --- |
| rapidyaml engine (shared with the ints scan) | 209.3 (47.8%) | 216.0 (69.8%) |
| handler: dispatch, frames, collections, fast-path control | 72.9 (16.7%) | 43.9 (14.2%) |
| tape node writes | 42.7 (9.8%) | 8.9 (2.9%) |
| scalar resolution | 42.3 (9.7%) | 8.9 (2.9%) |
| validation: UTF-8, c-printable, LF bitmap, input copy | 22.9 (5.2%) | 6.5 (2.1%) |
| lexical spans | 20.1 (4.6%) | 14.9 (4.8%) |
| key hash and duplicate keys | 14.2 (3.2%) | 0.1 (0.0%) |
| side tables | 13.0 (3.0%) | 9.9 (3.2%) |
| total per 1,000 decodes | 437.8 | 309.8 |

The remaining gap to the ints scan (≈ 100 samples per 1,000 decodes on this worker) is the handler's
fixed work per node: the dispatch and frame bookkeeping around rapidyaml's own stack (`open`/`close`
include rapidyaml's `_stack_push`/`_pop`), the 16-byte node plus 16 bytes of side tables per node,
and the single-line test, all small and spread over every scalar. records-flow and records-stream
are in `profiles/*/categories-*.txt`; the stream additionally pays per-document session setup and
the line splitter.
