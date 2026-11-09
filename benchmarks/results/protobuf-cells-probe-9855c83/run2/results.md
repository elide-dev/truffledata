# WS11 Myna cell probe

Binary sha256 `25bbb2c9807dd650a1616bcc612c0ee39f3750e913b07a00f7aa3b34cc9f6684`, source `9855c83`, CPU `AMD EPYC`, affinity [6], load before/after [0.0, 0.0, 0.0] / [0.341796875, 0.0810546875, 0.02685546875].

ns per decoded message, median of per-fork medians (fork range); Java bytes per message; graph facts from the root's last compilation (AST nodes, Graal nodes after partial evaluation / final, code bytes, invoke-class node counts).

| Route | ns/row | range | vs boundary | Java B/row | AST | IR after PE / final | code B | invokes |
|---|---:|---|---:|---:|---:|---|---:|---|
| `boundary` | 937.3 | 900.4–967.5 | 1.000× | 832.2 | 2 | 52 / 1467 | 5129 | DeoptimizeNode 32, ForeignCallNode 3, InvokeWithExceptionNode 32 |
| `boundary-body` | 914.6 | 891.3–984.1 | 0.976× | 832.2 | 2 | 51 / 1466 | 5001 | DeoptimizeNode 32, ForeignCallNode 3, InvokeWithExceptionNode 32 |
| `direct` | 889.0 | 887.6–5341.6 | 0.948× | 832.2 | 2 | 387 / 1138 | 5139 | DeoptimizeNode 7, DynamicDeoptimizeNode 2, ForeignCallNode 16, InvokeWithExceptionNode 12 |
| `cell` | 916.8 | 908.6–1133.1 | 0.978× | 1000.2 | 14 | 702 / 2413 | 11240 | DeoptimizeNode 17, DynamicDeoptimizeNode 2, ForeignCallNode 45, InvokeWithExceptionNode 25 |
| `cell-interop` | 1019.0 | 965.3–1077.7 | 1.087× | 1000.2 | 7 | 715 / 2820 | 12600 | DeoptimizeNode 26, DynamicDeoptimizeNode 3, ForeignCallNode 51, InvokeWithExceptionNode 32 |
