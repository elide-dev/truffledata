# JSON snapshot: single source pass in `HeapTape.copy` (WS7, 2026-11-08)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` WS7; rulings in
`2026-11-08-beat-stock-svm-ledger.md` under "JSON (P0.3/P0.4/WS6/WS7 worker)". Images, before
image, host and method are those of [`../json-reads-a1d8782/README.md`](../json-reads-a1d8782/README.md).

## Change (`c92b8d5`)

`HeapTape.copy` read every subtree word twice through the checked virtual `word` hook (once
for the extent scan, once for the rebase) and copied directory entries and metadata one virtual
call at a time; P0.3 charged it 35.0% of the snapshot simulation case (9,066 Ir/document).
`TapeReader` gains `copyWords`/`copyDirectory`/`copyMetadata` with the same bounds discipline
as `copyText` (arraycopy on heap tapes, `MemorySegment.copy` on the JVM segment tape, one
checked native word loop shared by `SvmTape` and the JSON/YAML static messages); the copy reads
the source once and runs both passes over the heap array. Unchanged: the trim to
`[inputFirst, inputLast)`, pool and directory extents, scalar/string/container/reference
rebasing, synthetic-number spans, the alias side table and the alias-format source copy in
`TapeSnapshot.copy`. `CompactTapeTest.bulkCopyHooksMatchTheElementWiseDefaultsAndStayBounded`
pins the bulk hooks against the element-wise defaults for whole-document and subtree roots and
their bounds checks; the existing subtree-directory, directoryless, synthetic-span, alias and
independence-after-close tests pass unchanged.

## Instructions (`callgrind/`, simulation image `c92b8d5`, Valgrind 3.24 amd64 container)

`json-generic-snapshot`: 53,042,752 → 49,541,486 Ir (−6.6%), 25,900 → 24,190 Ir/document.
The copy class (`HeapTape.copy` self, the hooks it calls, now `SvmTape.copyWords`) goes 9,066 →
7,353 Ir/document (−18.9%); `HeapTape.copy` self is 5,802 for the two heap-array passes
(`rawSpan`/`word` no longer appear as calls), `copyWords` 809, `copyText` 629. The
`json-generic-interop-read` control is unchanged (31,201,890 → 31,201,690). The plan's 10–20%
end-to-end Ir hypothesis is not reached on instructions.

## Paired SVM time (`pairs/`, invocation `ef6dde1a`, shard 6 on one AMD EPYC CPU)

| Cell | Before (`9a1dcec` image) | After (`a1d8782`) | Ratio | Ranges | B/doc |
|---|---:|---:|---:|---|---:|
| snapshot ingest (decode, snapshot, close, ten reads) | 1,491.890 (1,479.910–1,505.824) | 1,297.703 (1,294.471–1,316.641) | **1.150×** | disjoint | 1,183.5 → 1,183.5 |
| snapshot peak read | 41.605 (41.124–44.032) | 41.185 (41.022–41.473) | 1.010× | overlap | 1.0 |
| snapshot read-aot / interpret | 165.871 / 167.902 | 166.216 / 170.366 | 0.998× / 0.986× | overlap | 240 / 242 |
| generic-interop ingest (same shard) | 880.635 (867.042–881.346) | 862.821 (860.580–887.282) | 1.021× | overlap | 384 |
| generic-interop peak | 28.431 (28.340–28.444) | 28.361 (28.276–28.421) | 1.002× | overlap | 1.0 |
| stream-interop ingest | 620.549 (617.420–624.007) | 593.623 (592.915–607.643) | 1.045× | disjoint | 304.75 |

Snapshot ingest is 13.0% faster with disjoint fork ranges and identical allocation; the
snapshot's cost above the generic interop cell on the same host falls from 611 to 435 ns/doc.
The stream-interop cell (untouched by WS7: no snapshot on that path) also moved 4.5% with
disjoint ranges on this shard, so part of the shard's movement may be worker drift; the
snapshot gain is three times that control's movement and its Ir fell 6.6%, the control's not at
all. Snapshot reads are unchanged, as the heap tape's read hooks are untouched. A 20 µs outlier
fork in the candidate-only decode cell is recorded in `pairs/shard6.json`.
