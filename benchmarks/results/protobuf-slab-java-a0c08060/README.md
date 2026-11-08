# Java slab writer qualification

Invocation [a0c08060-b429-4a00-829e-4d2b4c726df6](https://app.buildbuddy.io/invocation/a0c08060-b429-4a00-829e-4d2b4c726df6)
runs `//benchmarks/comparison:slab_writer_measure` on Linux x86-64 RBE with four
requested CPUs. The harness fix is `64f1867`; the generated extent cache is
`3fa2892`. Java/GraalVM is 25.5.5-elide.27. No PGO is used.

All 126 fresh-process cells passed: three forks for each runtime, fixture and
writer variant. Every process checks all 96 heterogeneous fixtures against the
production table and generated producers before measurement. All writer-byte
hashes agree. Warmup and measured samples use the same call site. Processes run
serially in a seeded shuffled order, with seven raw samples per cell. No sample
is removed. `slab-writer.json`, `slab-writer.md` and each process log retain the
results and allocation measurements; timing ratios below use medians of fork
medians.

| JVM ratio | Small | Unicode holdout | Large |
| --- | ---: | ---: | ---: |
| Generated owning / stock | 0.839 | 0.695 | 1.117 |
| Table owning / stock | 2.545 | 2.010 | 3.341 |
| Probe slab with word zeroing / reused spans | 1.141 | 1.099 | 1.276 |

The Step 0 small/holdout writer comparison clears 1.2x, but large does not. The
production generated decoder misses the 0.55x small and 0.8x large hypotheses;
the table decoder misses parity. These results do not establish a completed A3
performance claim. SVM generated results are reported, as required, and remain
slower than the native default.

The per-record zeroing diagnosis applies to the deliberately reused probe
buffer. Production owns a fresh zeroed array, so adding explicit zero stores
there would add work. The production extent cache avoids repeated growth and
trim copies while keeping each output independently owned. It does not resolve
the remaining producer gap. Some stock and writer cells contain substantial
within-fork variation (visible in the report); they are retained rather than
filtered into a stronger claim.

Reproduce with:

```sh
bazel test //benchmarks/comparison:slab_writer_measure
```
