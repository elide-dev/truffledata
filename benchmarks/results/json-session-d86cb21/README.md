# JSON sessions on SVM (performance plan Task B2), measured on `d86cb21` + Task B2

One `json_measure` run of the `json_svm` image built from `d86cb21` plus the Task B2 working
tree, on a shared BuildBuddy RBE worker. Invocation (standing lanes, smoke and measure together):
<https://app.buildbuddy.io/invocation/581fb9bc-6858-4b2c-bf6e-c900f134870b>.
[`json-comparison.md`](json-comparison.md) has every cell; [`json-comparison.json`](json-comparison.json)
has the raw samples, fork identities, the binary hash and `/proc/cpuinfo`.
[`json-compilation.log`](json-compilation.log) is the Truffle-tier expansion trace of the smoke run
in the same invocation. **Shared-host timings are advisory**: one worker, one run, medians of three
fresh-process fork medians; Java allocation is deterministic and excludes native memory.

Since Task B2 every simdjson ingest decodes through one explicit `JsonSession` per run
(`JsonSession.decode`, which moves the session's storage into the message), and the new
`simdjson-session-java` cell is a borrowed `JsonSession.parse` into a `JavaSink` (ingest only).

| Hypothesis (plan Task B2) | Measured | Result |
|---|---|---|
| session parse + `JavaSink` ingest below `gson-tree` ingest, same run | 4,269 vs 5,446 ns/doc | met |
| interop decode + close ≤ 0.45 × `861dff1` on the same worker | not measured on the same worker (no baseline binary in this run); interop ingest 5,018 ns/doc here against 5,623 in `json-generic-c6e7abc` (other worker), of which ten AOT interop reads are ≈ 3.2 µs (`read-aot`) | not established |
| interop ingest ≤ 160 B/doc | 472 B/doc (544 before sessions), including 240 B of read views | **not met** |
| session `JavaSink` ingest ≤ 1,900 B/doc | 3,596 B/doc | **not met** (needs Task D1's text cursor) |

New smoke gates fix the measured values: interop ingest ≤ 480 B/doc and session `JavaSink`
ingest ≤ 3,600 B/doc. The compiled borrowed-read gate (`CompiledReads.borrowed`, in
`//json/tests:native_test`) measured 3.2E-5 B/read, and the compiled read fails once the borrow
ends. Read stages are unchanged by this task (interop peak 2,993 ns/doc, 1 B/doc; the interop
graph is 1,754–1,810 nodes with one loop).
