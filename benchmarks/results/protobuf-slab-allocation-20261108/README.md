# Durable slab allocation floor — 2026-11-08

The allocation changes in `d529c56` and `961d479` reduce compact scalar Buffa ingest from
312 to 168 Java heap bytes/message on Linux amd64 SVM (`25.5.5-elide.27`). The
legacy smoke gates are now 168 B for generic ingest and 136 B for typed/generated
ingest. The native heap is outside this counter. The input is already in unmanaged
memory; this measures durable decoding plus the harness's projected read.

## Measured breakdown

The smoke now measures escaping array clones, clone plus `SlabMessage.takeOwnership`,
and typed native ingestion on the same 56-byte Timestamp slab. Each loop runs 256
messages after warmup; measurements use `ThreadMXBean.getThreadAllocatedBytes`.

| Component | Java heap bytes/message | Evidence |
|---|---:|---|
| Owned `byte[56]` | 64 | Escaping `template.clone()` loop |
| SlabMessage publication, excluding array | 48 | Publication 112 minus array 64 |
| Native decode residue | 24 | Typed ingestion 136 minus publication 112 |
| Generic read boxing | 32 | Generic ingestion 168 minus typed 136 |
| Total generic ingestion | 168 | Smoke counter |

The 24-byte native residue is consistent with the output `PinnedObject`: the native
path allocates its output array and pin, reuses result storage and input leases,
then calls `takeOwnership`. This is an inference from the differential measurement
and source inspection, not allocation-class profiling. The pin remains. The
32-byte generic/typed difference is the harness's two boxed `Long` values from
`DataLibrary.read`; engines retain their existing read paths.

The previous estimate of 152/120 B assumed a 72-byte array and no pin residue.
The measured 64-byte array and 24-byte residue supersede those assumptions, including
the older ledger's object-header arithmetic. No throughput or retained-memory gate
is changed by this result.

## Runs and retained evidence

- Baseline `1cfcb37`, invocation
  [ad1f0190](https://app.buildbuddy.io/invocation/ad1f0190-4918-4deb-bb98-c80cfc8fde0a):
  `baseline.txt` preserves the prior worker's test-output excerpt, failing at 312 B.
- Candidate `961d479` plus a comment correction, invocation
  [226e5f97](https://app.buildbuddy.io/invocation/226e5f97-f7d7-4994-b66e-dcdc577089a0):
  `candidate-before-gate.txt`, failing the unchanged 128 B gate at 168 B.
- Candidate plus the breakdown probe and recursive-alias budget test, invocation
  [c07af12f](https://app.buildbuddy.io/invocation/c07af12f-2f94-4f8e-9b9a-6a5493648c09):
  `candidate-breakdown.txt` confirms the table. `//tests:test`, `//protobuf-runtime:test`
  and `//protobuf-buffa/tests:jvm` pass; their test logs/XML are retained compressed.
- Final gate run, invocation
  [7b240f5f](https://app.buildbuddy.io/invocation/7b240f5f-6a9f-40cd-ace9-a26bca831bd4):
  full `//benchmarks/comparison:native_smoke` passes with the measured gates;
  `native-smoke.log.gz` and `native-smoke.xml.gz` retain the result, including
  168 B generic / 136 B typed / 136 B generated ingest.

Commands use the repository's default Linux amd64 RBE profile, serialized locally:

```sh
bazelisk test //benchmarks/comparison:native_smoke --remote_download_minimal --test_output=all
bazelisk test //benchmarks/comparison:native_smoke //protobuf-runtime:test \
  //protobuf-buffa/tests:jvm //tests:test --remote_download_minimal --test_output=all
```

## Separate release blocker: shared graph verification cost

Both the old reference/projected verifier and the new owning verifier traverse
aliased records once per logical occurrence. A recursive `Node { repeated Node
children = 1; }` slab can contain a linear chain with two child slots per record,
then alias both slots to the first child. At depth `d`, the structure walk makes
`2^(d+1)-1` record visits before logical-budget rejection, despite linear physical
storage. This pre-existing denial-of-service risk remains open.

`SlabVerifierTest.aliasedRecursiveRecordsKeepPerVisitBudgets` retains a bounded
reproducer at depth 10: 21 physical records, 2,047 logical visits, 4,093 cells,
and rejection at a 4,092-cell budget. A future memoization fix must preserve
handle identity, maximum path depth, per-visit logical totals, error precedence,
and projected expanded-state rules. This test pins semantics; it does not claim
to close the time-complexity risk.
