# Per-document text arenas and `TruffleSink` (performance plan Task D2), measured on `b3987bc` + Task D2

One `json_measure` run of the `json_svm` image built from `b3987bc` (Task D1) plus the Task D2
working tree, on a shared BuildBuddy RBE worker. Invocation (standing lanes, release consumers and
measure): <https://app.buildbuddy.io/invocation/ca16abcc-fa86-47b8-937c-1b43029c54c5>; the smoke
trace in [`json-compilation.log`](json-compilation.log) is from
<https://app.buildbuddy.io/invocation/55bc4474-3b6b-475c-b345-133a8918d5e8> (same sources).
[`json-comparison.md`](json-comparison.md) has every cell and [`json-comparison.json`](json-comparison.json)
the raw samples. **Shared-host timings are advisory**; compare rows within this run. Allocation
counts are exact.

New cell `simdjson-session-truffle` (ingest only): borrowed `JsonSession.parse(input, TruffleSink)`;
the session writes the document's text arena in the decode call, each string value is a UTF-16
`TruffleString` over that array (no copy), keys come from a `TruffleSink.KeyCache`, and the
`TruffleString`-keyed tree is read for the fixture checksum.

| Hypothesis / gate (plan Task D2) | Measured | Result |
|---|---|---|
| per-document budget recorded in `JSON.md` | 1,772.5 B/doc (gate ≤ 1,800): tree, one `TruffleString` per value string, one `2 × length + 16` text array | set |
| ingest ≤ 3.0 µs/doc | 5,049 ns/doc (4,579–6,079); `simdjson-session-java` 4,458, `gson-tree` 4,733 in this run | **not met** |
| graph gate `(1000, 2, None)` | not applicable: the cell is ingest-only (no compiled read root), like `simdjson-session-java` | — |

The text arena makes strings share one array, but the cell is slower than the `JavaSink` cell:
building each `TruffleString` through the uncached factory (`fromByteArrayUncached` plus
`switchEncodingUncached`) and hashing `TruffleString` keys into `LinkedHashMap` cost more than
`String`s here. A guest sink with cached `TruffleString` nodes (Elide's `JSON.parse`) avoids the
uncached path; the native cost of the arena itself is attributed in the D3 probe
(`json-utf16-*`, `T0` against `S0`).
