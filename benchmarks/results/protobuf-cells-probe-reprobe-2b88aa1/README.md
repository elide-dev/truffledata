# WS11 Myna cells re-probe after WS2 (2026-11-09)

Lead re-run of the WS11 probe harness (`benchmarks/results/protobuf-cells-probe-9855c83/`) on the
tree with WS2 trusted in-process publication admitted (`2b88aa1`: production = WS2 `4b79edd` +
docs; the probe's `publish` now calls `SlabMessage.produced` with the result slot's flags and
logical totals, exactly as `SvmBackend.slab` does, so every route pays the same publication).
Same harness, same five routes, three fresh forks per route, one RBE worker per run (AMD EPYC,
pinned to CPU 6), probe image sha256 `6ea28d3c…b412`. The runner gained `--seed`
(`TRUFFLEDATA_CELLS_SEED`) so the second run starts with a different route, as the WS11 ledger asked.

The WS11 ruling's premise for re-probing: after WS2 the cell would be ≈350 ns, so the ≈40 ns the
`direct` route saved at medians before (−1.3 to −5.2%, overlapping) would be ≈10%.

| Route | run 1 (seed 20261109) ns/row (range) | vs boundary | run 2 (seed 20261110) ns/row (range) | vs boundary | Java B/row |
|---|---:|---:|---:|---:|---:|
| `boundary` (production `BuffaPlan.decodeSlab(byte[])`) | 420.1 (418.5–426.9) | 1.000× | 439.2 (425.0–531.9) | 1.000× | 832.2 |
| `boundary-body` | 404.8 (402.1–412.4) | 0.964× | 432.0 (428.6–471.5) | 0.984× | 832.2 |
| `direct` (PE-visible body, direct transition) | 447.0 (405.8–453.9) | 1.064× | 483.2 (423.1–534.5) | 1.100× | 832.2 |
| `cell` (Myna `NativeCell` child node) | 508.7 (426.8–510.7) | 1.211× | 498.7 (469.4–575.6) | 1.136× | 1,000.2 |
| `cell-interop` | 450.3 (435.2–451.3) | 1.072× | 486.3 (440.7–519.7) | 1.107× | 1,000.2 |

Graph facts are unchanged from the WS11 runs (`direct` 396/1,147 IR nodes with 16 foreign calls
and 12 invokes against the boundary's 52/1,467 with 3 and 32; `cell` 711/2,422, 11.3 KB code).

**Reading.** The boundary route reproduces the WS2 paired cell (423–449 ns/row). With the verifier
gone, `direct` is *slower* than the boundary in both runs (+6.4%, +10.0%; its range reaches the
boundary's in run 1), and both Myna cell routes are 7–21% slower with +168 B/row. The pre-WS2
`direct` saving (≈20–40 ns at medians) does not reappear; what the PE-visible body adds (pins,
transition, extent/trim/status checks as compiled code with 7 deopt and 16 foreign-call nodes)
costs about as much as the one boundary call it removes, and at 420 ns the cell is dominated by the
native decode (≈267 ns standalone) plus allocation and publication. No route beats the boundary
in either run; WS11 remains killed. Harness note: the first run on each worker again shows a wide
first-route range (run 2's `boundary` 425–532, run 1's `direct` 405.8–453.9), consistent with the
early-process stall the WS11 ledger recorded; the seed rotation moved it between routes as intended.
