# Protobuf slab warm-cache experiment

Baseline: `9a1dcec`, with B4 Phase 1 restored and the slab allocation work integrated.
SVM is the performance target; JVM runs validate semantics and coverage. No PGO.

## Restored baseline profile

The [profile summary](baseline-profile/summary.json) comes from a fresh
`consumer_interpreter_profile` run (`8bad3434`). The four original JFRs, JSONL
records, binary checksum and compressed symbols are retained alongside it.
Each engine has 3,000 application execution samples. These samples identify
where to experiment; they are not elapsed-time speedups.

Generated interpretation no longer attributes a leading leaf cost to
`Accessor.bound`. Slab still attributes 647/3,000 leaf samples (21.6%) there.
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

The paired SVM admission decision is pending; `f045f5c` is explicitly unqualified
for integration until those measurements complete.
