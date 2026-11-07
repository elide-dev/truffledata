# JSON streams in the comparison harness (performance plan Task F2), measured on `238c826` + Task F2

One `json_measure` run of the `json_svm` image built from `238c826` (Task F1) plus the Task F2
working tree, on a shared BuildBuddy RBE worker. Invocation (standing lanes, release consumers,
`myna_contract` and `json_measure`):
<https://app.buildbuddy.io/invocation/33e4a57a-16c6-453d-b3a4-5f42e025ff09>. `json-comparison.json`
holds every raw sample (three fresh-process forks per cell and metric) and the worker's
`/proc/cpuinfo`; `json-comparison.md` is the generated summary. **Timings are advisory** (one
worker, one run); compare cells within the run.

New ingest-only cells, each one NDJSON buffer of the 32 fixture documents per operation (results
per document):

| Cell | What it measures |
|---|---|
| `gson-stream-lines` | Split at newlines, Gson tree per line, read ten members |
| `simdjson-stream-java` | One `JsonStream` per run (never finished): feed, `next(JavaSink)` per document, read the tree |
| `simdjson-stream-interop` | As above: `next()` lends each root, ten AOT interop reads |

| Hypothesis | Measured (median of fork medians) | Result |
|---|---|---|
| F2: `simdjson-stream-interop` ingest ≤ 1.5 µs/doc | 2,798 ns/doc (`gson-stream-lines` 4,615; per-document session interop ingest 3,003) | not met |
| F2: stream `JavaSink` within the Task D1 budget | 1,338.5 B/doc (≤ 1,400 gate) at 4,116 ns/doc | met |
| (stream interop allocation) | 432.75 B/doc (≤ 440 gate; plan's 1.0 B/doc not reachable with a borrow owner per document) | recorded |

The remaining stream-interop time is the per-document decode on this worker plus the ten AOT
interop reads and the borrow's Java objects, as for session ingest; the native stream alone runs at
577 ns/doc for the fixture's documents (`json-stream-native-fac31c3`). Other cells are unchanged
from Task E2 within noise (session JavaSink 4,422 ns, 1,360 B; TruffleSink 1,772.5 B; peak interop
1,370 ns/doc, 1.0 B/doc).
