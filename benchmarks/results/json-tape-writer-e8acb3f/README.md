# JSON presized tape writer (performance plan Task A2), measured on `e8acb3f` + Task A2

`native.txt` is the output of the retained probe `perf_probe_test.cc` (identical to the one in
`json-lean-walk-d9e8873`), run once as a temporary `cc_test` in `json/native` (removed before
commit) on a shared BuildBuddy RBE worker (Intel Xeon, simdjson `icelake`). Invocation:
<https://app.buildbuddy.io/invocation/14a28f49-f5ee-4073-af7a-cab750ab8ffa> (ASan/UBSan lanes and
probe ran back to back; the probe log is in the same session). `B0` is the `861dff1` decoder
compiled into the same binary; `D0`/`A0` are the production decoder (lean walk, `tape_writer`,
`block_arena`). Timings are advisory: one worker, one run, best of 7 rounds; compare rows within
this file only.

## Task A2 acceptance (hypotheses from the plan)

| Hypothesis | Measured (same run) | Result |
|---|---|---|
| small `D0` ≤ 1.5 × `W3` | 693 / 485 ns = 1.43 | met |
| (small `D0` vs `861dff1`) | 693 / 1,828 ns = 0.38 | — |
| big ≥ 280 MB/s | 395 MB/s (`B0` 162 MB/s) | met |
| `index_allocations` ≤ 3 (small) | 1 (medium 1, big 4; baseline 33 / 371 / 17,047) | met |

Peak index bytes rise where presizing over-reserves: small 4,096 B (one block) against 1,784 B,
big 2,089,556 B against 1,463,192 B (the node hint is one node per four input bytes; the fixture
has one per 7.4). The C ABI round trip (`A0`, decode + release) is 722 ns small and 407 MB/s big.
