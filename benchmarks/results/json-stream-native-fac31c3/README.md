# Native JSON streams (performance plan Task F1), measured on `fac31c3` + Task F1

`native.txt` is the output of the retained probe [`stream_probe_test.cc`](stream_probe_test.cc),
run once as a temporary `cc_test` in `json/native` (removed before commit) on a shared BuildBuddy
RBE worker (simdjson `icelake`). Invocation:
<https://app.buildbuddy.io/invocation/a0f7c474-191b-4861-a74b-9daf60825533>. Best of 7 rounds of
200 passes over 1,000 NDJSON lines of the comparison fixture's documents (228,484 B, the
`json-perf-probe-861dff1` generator), all through the C ABI on one session. **Timings are advisory**
(one worker, one run); compare rows within the file.

| Row | What it measures |
|---|---|
| `N0` | per-line `td_json_session_decode` + `td_json_session_reset` over lines split in advance (the caller-side splitting baseline; splitting itself is free here) |
| `N1` | `td_json_stream_feed` of the whole buffer, then `td_json_stream_next` per document (batch 1 MiB) |
| `N2` | 64 KiB feeds, draining after each (carry-over at every chunk boundary) |
| `N3` | `N2` with a 64 KiB `iterate_many` batch and maximum document |
| `N4` | `N1` with stream create/release per pass |
| `I0` | `iterate_many` + `source()` per document without decoding: the boundary cost alone |

| Hypothesis | Measured | Result |
|---|---|---|
| F1: ≥ 300 MB/s for ~228 B lines with a full tape per document | `N1` 396 MB/s (577 ns/doc); `N2` 374 MB/s; `N3` 367 MB/s | met |
| (stream overhead versus pre-split per-line decodes) | `N1 − N0` = +43 ns/doc (`I0` 83 ns/doc includes stage 1, which `N0` also pays per line) | recorded |

Steady state: no index allocation per streamed document (`index_allocations=0` after warm-up),
matching `stream_test`'s 10,000-document check that the session's upstream allocator is not called
after warm-up.
