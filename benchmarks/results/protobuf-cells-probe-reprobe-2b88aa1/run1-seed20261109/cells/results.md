# WS11 Myna cell probe

Binary sha256 `6ea28d3ce89f640213365ff1ce4c3dea28900564bbf614950af3564f6160b412`, source `2b88aa1`, CPU `AMD EPYC`, affinity [6], load before/after (0.0, 0.0, 0.0) / (0.421875, 0.09765625, 0.0322265625).

ns per decoded message, median of per-fork medians (fork range); Java bytes per message; graph facts from the root's last compilation (AST nodes, Graal nodes after partial evaluation / final, code bytes, invoke-class node counts).

| Route | ns/row | range | vs boundary | Java B/row | AST | IR after PE / final | code B | invokes |
|---|---:|---|---:|---:|---:|---|---:|---|
| `boundary` | 420.1 | 418.5–426.9 | 1.000× | 832.2 | 2 | 52 / 1467 | 5129 | DeoptimizeNode 32, ForeignCallNode 3, InvokeWithExceptionNode 32 |
| `boundary-body` | 404.8 | 402.1–412.4 | 0.964× | 832.2 | 2 | 51 / 1466 | 5001 | DeoptimizeNode 32, ForeignCallNode 3, InvokeWithExceptionNode 32 |
| `direct` | 447.0 | 405.8–453.9 | 1.064× | 832.2 | 2 | 396 / 1147 | 5203 | DeoptimizeNode 7, DynamicDeoptimizeNode 2, ForeignCallNode 16, InvokeWithExceptionNode 12 |
| `cell` | 508.7 | 426.8–510.7 | 1.211× | 1000.2 | 14 | 711 / 2422 | 11304 | DeoptimizeNode 17, DynamicDeoptimizeNode 2, ForeignCallNode 45, InvokeWithExceptionNode 25 |
| `cell-interop` | 450.3 | 435.2–451.3 | 1.072× | 1000.2 | 7 | 724 / 2829 | 12632 | DeoptimizeNode 26, DynamicDeoptimizeNode 3, ForeignCallNode 51, InvokeWithExceptionNode 32 |
