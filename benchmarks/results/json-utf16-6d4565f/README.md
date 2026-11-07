# UTF-16 and Latin-1 input, text-arena cost (performance plan Tasks D3 and D2), measured on `6d4565f` + Task D3

`native.txt` is the output of the retained probe [`session_probe_test.cc`](session_probe_test.cc),
run once as a temporary `cc_test` in `json/native` (removed before commit) on a shared BuildBuddy
RBE worker (simdjson and simdutf `icelake`). Invocation:
<https://app.buildbuddy.io/invocation/0e9f9b70-52d7-4fb9-bcff-5ac2585da7c0>. Best of 7 rounds
(200,000 / 20,000 / 200 iterations for the 210 B, 4.3 KB and 228 KB fixture documents), all through
the C ABI on one session. **Timings are advisory** (one worker, one run); compare rows within the
file.

| Row | What it measures |
|---|---|
| `S0` | UTF-8 `td_json_session_decode` + `td_json_session_reset` (Task B1's borrowed path, re-measured) |
| `T0` | `S0` with `TD_JSON_WANT_TEXT`: the Task D2 text arena written in the same call |
| `U0` | UTF-16LE input (`TD_JSON_UTF16_INPUT`): simdutf validation and transcoding into the padded buffer |
| `U1` | `U0` with the text arena |
| `L0` | Latin-1 input (`TD_JSON_LATIN1_INPUT`) |
| `C0` | UTF-16 validation + transcoding alone, to attribute `U0 − S0` |

| Hypothesis | Measured | Result |
|---|---|---|
| D3: UTF-16 transcode + parse of the 210 B document ≤ UTF-8 + 150 ns | `U0` 528 ns vs `S0` 504 ns: +24 ns (`C0` 13 ns) | met |
| B1 carry-over: small decode + reset ≤ 700 ns | `S0` 504 ns on this worker (745 ns on B1's slower worker) | met here |
| (D2 native cost of the text arena) | `T0 − S0`: +126 ns small, +1.7 µs medium, +63 µs big (≈ 12–17%) | recorded |

Steady state: no index allocation per decode for the small and medium documents; the 228 KB
document makes three large allocations per decode (the presized arrays and the text table above
the arena's 32 KiB large-allocation threshold, freed before the next decode by design, Task A2).
