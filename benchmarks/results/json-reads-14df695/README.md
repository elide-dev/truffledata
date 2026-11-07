# Position-hinted JSON member reads (performance plan Task C1), measured on `14df695` + Task C1

One `json_measure` run of the `json_svm` image built from `14df695` plus the Task C1 working tree,
on a shared BuildBuddy RBE worker. Invocation (standing lanes and measure):
<https://app.buildbuddy.io/invocation/a06c86e1-3b49-44f6-8c7f-ed105e2b0cd9>; the smoke trace in
[`json-compilation.log`](json-compilation.log) is from
<https://app.buildbuddy.io/invocation/2b6716f9-b422-428f-bf35-c75320441040> (same sources).
[`json-comparison.md`](json-comparison.md) has every cell and [`json-comparison.json`](json-comparison.json)
the raw samples. **Shared-host timings are advisory, and this worker was noisy**: `gson-tree` peak
read 836 ns/doc here against 536 in `json-session-d86cb21`, and several fork ranges are wide.
Compare rows within this run.

| Hypothesis / gate (plan Task C1) | Measured | Result |
|---|---|---|
| `json_smoke` `GenericReads[simdjson-generic-interop]` keeps (2000, 1, 0) | 1,916–1,972 nodes, 1 loop, 0 invokes, 0 allocations | met |
| `CompiledReads` interop root ≤ 0.05 B/read | 3.2E-5 (message, borrowed root; array reads unchanged) | met |
| peak interop ≤ 0.6 µs/doc for ten reads | 1,696 ns/doc (snapshot 1,562; `gson-tree` 836 in this run) | **not met** |

Before this task (`json-session-d86cb21`, another worker) peak interop was 2,993 ns/doc with
`gson-tree` at 536, i.e. 5.6 × Gson; here it is 2.0 × Gson. The remaining gap is expected from the
fixture: odd documents reverse member order, and each call site holds one hint per member name,
so every other document misses and pays the static scan boundary. A two-entry hint per site (or
a native shape id) is the follow-up the plan anticipates; a second inlined probe costs another
tape call per read against the 2,000-node graph budget, so it was not added here.
