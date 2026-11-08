# B4 interpreter profile — 4065dd6f

Diagnostic profile of isolated snapshot `5002235`, invocation `4065dd6f-2b23-40f9-bb50-bfec5e1a448f`. The profile binary SHA is `4e014586af9bcf86a440d580e74314452f085b86195c508d1bc79ee161274bd4`. Four JFR recordings and JSONL workload outputs cover full interpreter consumption for generated-owned, slab, stock and stock-interop. `symbols.txt.gz` preserves symbols; `summary.json` records Java-visible CPU and allocation samples. Sampling counts are not allocated bytes and do not include native allocations.

The generated-owned profile has 2,999 application CPU samples. Leaf samples include `Accessor.bound` 561 (18.7%), interop array dispatch 336 (11.2%), `ReadLongNode.accessed` 260 (8.7%), `GeneratedMessage.Generated.readLong` 253 (8.4%), and `Accessor.length` 205 (6.8%). The committed inline DSL reader prototype therefore still spends substantial time in accessor validation and dispatch. The next proposed investigation was a protected checked-layout hook after the existing core guard, preserving checked public reads and scoped lifetime checks; **it was not implemented before the requested pause**.

The [paired G1 run](../protobuf-slab-4065dd6f/README.md) measures generated-owned/stock full interpretation at 4.358× and slab/stock at 6.729× on SVM, both missing B4's ≤2.5 gate. Generated-owned SVM full peak is 1.090× historical and slab/native-view replacement full peak is 2.275×. The unchanged consumer smoke allocation/graph checks passed. B4 remains open; a profile and an inline-node implementation are not evidence that its gates passed.

Reproduce the saved summary (requires a JDK with `jfr` on PATH):

```sh
python3 tools/bench/json_profile_summary.py benchmarks/results/protobuf-interpreter-4065dd6f dev.elide.truffledata.comparison.ConsumerComparison
```

Resume checks already known at pause: CI reports uncovered `ReadBooleanNode$InlineReader::accessedInline(Object,int,int,Accessor)`; the legacy `//benchmarks/comparison:native_smoke` reports compact scalar allocation 416 B above its unchanged 128 B limit. The broad qualification covered `consumer_native_smoke`, not that separate legacy target. No workaround or budget increase was applied.
