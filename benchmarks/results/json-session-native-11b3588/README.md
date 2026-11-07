# Native JSON sessions (performance plan Task B1), measured on `11b3588` + Task B1

`native.txt` is the output of the retained probe `perf_probe_test.cc`, run once as a temporary
`cc_test` in `json/native` (removed before commit) on a shared BuildBuddy RBE worker (Intel Xeon,
simdjson `icelake`). Invocation: <https://app.buildbuddy.io/invocation/8e25d5ee-8bad-45f9-a5f4-52b4808c193c>.
The probe is the one in `json-tape-writer-e8acb3f` plus two session variants through the C ABI:

- `S0 session decode+reset (borrowed)`: `td_json_session_decode` + `td_json_session_reset`.
- `S1 session decode+retain+release`: decode, `td_json_session_retain` into a fresh `JsonResult`,
  `td_json_release`.

The `session steady state: index_allocations` line is the decode that follows the `S1` loop: after
a retain the session has handed its blocks away, so that one decode acquires a block (small: 1).
Steady state without retains is pinned at zero allocations by `//json/native:api_test`
(10,000 decodes). Timings are advisory: one worker, one run, best of 7 rounds. **This worker was
slower than the previous runs** (`W3` small 629 ns here against 485 ns in
`json-tape-writer-e8acb3f`); compare rows within this file only.

## Task B1 acceptance (hypothesis from the plan)

| Hypothesis | Measured (same run) | Result |
|---|---|---|
| small session decode + reset ≤ 700 ns native | 745 ns (`W3` 629, `D0` 807, `A0` 862) | **not met** on this worker; 1.18 × `W3` |
| (retained round trip) | `S1` 899 ns small, 261 MB/s big | — |

The absolute target was set on the faster worker class; in this run the session path is 0.86 ×
the C ABI decode + release (`A0`) and 0.92 × the per-call document (`D0`). The remaining
difference to `W3` is the tape, spans and hashes (A2) plus the input copy.
