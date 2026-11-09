# Directory opt-in checkpoint

The paired RBE run is [69bedb3d](https://app.buildbuddy.io/invocation/69bedb3d-bba4-40aa-9bc8-fe0e31d946ee), before the later directoryless shape-hit scratch optimization. Producer harness: `json/native/stage2_probe.cc`; strictness oracle: `json/native/stage2_test.cc` and `shape_fuzz_test.cc`.

| Fixture bytes | Retained / old decoder | Validate / old validator | No-directory / retained speed |
|---:|---:|---:|---:|
| 210 | 1.443 | 1.629 | 1.018 |
| 4,330 | 1.514 | 1.762 | 1.032 |
| 228,485 | 1.486 | 1.732 | 1.033 |
| 4,850,894 | 1.455 | 1.749 | 1.039 |

The retained and validate-only throughput floors now pass. The directoryless 1.04x big/huge gate remains short. Logical words plus pool are 1.871/1.688/1.599/1.510 times input, above the required 1.5x borrowed-index limit; arena reservations cost still more. The memory diagnostic reports these separately. These results do not satisfy the complete producer plan.

Native JSON compiled graphs are 2,536/2,479 nodes for retained interop, 2,179/2,123 for snapshots and 653/597 for export (first/warm). All have zero calls and allocations; reads have one loop and export two. Read graphs still exceed 2,000. Native allocation checks retain 144 library bytes per retained root, 1,183.5 bytes per snapshot, and 1,177 bytes for compiled JavaSink export. The smoke result is FAILED on graph size.

`local-postcapture-graph.log` measures the later exact-byte hint and ByteArraySupport changes on the JVM over 32 rotating heap snapshots: 1,919/1,862 nodes, one loop, zero calls, and the harness's one result box. It is diagnostic evidence, not the Native Image gate.
