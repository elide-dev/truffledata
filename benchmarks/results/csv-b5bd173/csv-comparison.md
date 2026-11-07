# CSV comparison

GB/s is input bytes per nanosecond: the median of per-fork medians (range of fork medians).
Java B/row is Java heap allocation per data row. Shared-host timings are advisory.

## Hosts

| Binary | Group | CPU |
|---|---|---|
| csv-comparison | fixture escaped 1048576 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv-comparison | fixture escaped 134217728 | AMD EPYC |
| csv-comparison | fixture mixed 1048576 | AMD EPYC |
| csv-comparison | fixture mixed 134217728 | AMD EPYC |
| csv-comparison | fixture numeric 1048576 | AMD EPYC |
| csv-comparison | fixture numeric 134217728 | Intel(R) Xeon(R) Processor @ 3.10GHz |
| csv-comparison | fixture quoted 1048576 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv-comparison | fixture quoted 134217728 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv-comparison | sweep | AMD EPYC |
| csv-comparison-v3 | fixture escaped 1048576 | AMD EPYC |
| csv-comparison-v3 | fixture escaped 134217728 | AMD EPYC |
| csv-comparison-v3 | fixture mixed 1048576 | AMD EPYC |
| csv-comparison-v3 | fixture mixed 134217728 | AMD EPYC |
| csv-comparison-v3 | fixture numeric 1048576 | AMD EPYC |
| csv-comparison-v3 | fixture numeric 134217728 | AMD EPYC |
| csv-comparison-v3 | fixture quoted 1048576 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv-comparison-v3 | fixture quoted 134217728 | AMD EPYC |
| csv-comparison-v3 | sweep | AMD EPYC |
| csv_jvm | fixture escaped 1048576 | AMD EPYC |
| csv_jvm | fixture escaped 134217728 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv_jvm | fixture mixed 1048576 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv_jvm | fixture mixed 134217728 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv_jvm | fixture numeric 1048576 | AMD EPYC |
| csv_jvm | fixture numeric 134217728 | AMD EPYC |
| csv_jvm | fixture quoted 1048576 | AMD EPYC |
| csv_jvm | fixture quoted 134217728 | AMD EPYC |
| csv_jvm | sweep | AMD EPYC |

## csv-comparison: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.733 | — |
| index | index-native-avx512 | native-avx512-svm | 8.624 (8.486–8.762) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 8.616 (8.574–8.657) | 0.0 |
| index | index-native | native-avx512-svm | 8.585 (8.479–8.692) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.647 | — |
| index | index-native-avx2 | native-avx2-svm | 6.497 (6.399–6.595) | 0.0 |
| index | index-swar | java-swar | 2.045 (2.037–2.053) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.759 (1.749–1.770) | 0.0 |
| index | native-index-swar (native) | swar | 1.635 | — |
| index | index-table | java-table | 0.817 (0.784–0.850) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.808 | — |
| index | index-scalar | java-scalar | 0.594 (0.585–0.603) | 0.0 |
| index | index-vector | java-vector64 | 0.068 (0.066–0.069) | 2279.7 |
| scan | native-indexed-scan (native) | avx512 | 2.464 | — |
| scan | kernel-native | native-avx512-svm | 1.678 (1.675–1.681) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.475 (1.428–1.521) | 0.1 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.463 (1.445–1.481) | 0.0 |
| scan | csv-core-scan (native) |  | 1.077 | — |
| scan | kernel-swar | java-swar | 1.040 (1.039–1.041) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.971 (0.962–0.980) | 0.1 |
| scan | csv-byte-record (native) |  | 0.821 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.769 (0.759–0.779) | 0.0 |
| scan | fastcsv |  | 0.491 (0.482–0.500) | 2.6 |
| scan | jackson |  | 0.183 (0.182–0.184) | 256.0 |
| scan | kernel-vector | java-vector64 | 0.066 (0.066–0.067) | 2279.7 |
| strings | csv-string-record (native) |  | 0.745 | — |
| strings | kernel-native | native-avx512-svm | 0.431 (0.427–0.435) | 256.0 |
| strings | kernel-swar | java-swar | 0.355 (0.342–0.369) | 256.0 |
| strings | fastcsv |  | 0.260 (0.243–0.277) | 322.7 |
| strings | univocity |  | 0.222 (0.218–0.226) | 505.8 |
| strings | jackson |  | 0.198 (0.195–0.200) | 256.0 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.195 (0.188–0.203) | 404.3 |
| strings | truffledata-batch | java-swar | 0.178 (0.169–0.187) | 404.3 |
| strings | truffledata-batch-bytes | per-byte | 0.178 (0.175–0.181) | 404.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.147 (0.145–0.150) | 532.4 |
| strings | truffledata-mapped | java-swar | 0.141 (0.138–0.143) | 532.3 |
| strings | commons-csv |  | 0.079 (0.078–0.079) | 345.6 |
| typed | csv-typed (native) |  | 0.457 | — |
| typed | kernel-native | native-avx512-svm | 0.436 (0.434–0.437) | 0.0 |
| typed | kernel-swar | java-swar | 0.380 (0.376–0.384) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.245 (0.238–0.251) | 140.4 |
| typed | truffledata-plan | java-swar | 0.226 (0.222–0.230) | 140.3 |
| typed | truffledata-plan-bytes | per-byte | 0.212 (0.210–0.214) | 140.3 |
| typed | fastcsv |  | 0.109 (0.108–0.109) | 442.7 |
| typed | univocity |  | 0.098 (0.098–0.099) | 625.8 |
| typed | jackson |  | 0.097 (0.097–0.097) | 376.0 |
| typed | commons-csv |  | 0.052 (0.052–0.053) | 465.6 |
| utf8 | simdutf-validate (native) |  | 107.805 | — |

## csv-comparison: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 5.366 (5.326–5.407) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 5.362 (5.357–5.367) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 5.326 (5.308–5.343) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 5.271 (5.269–5.274) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.706 | — |
| index | native-index-avx2 (native) | avx2 | 4.314 | — |
| index | index-swar | java-swar | 1.642 (1.621–1.662) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.243 (1.207–1.279) | 0.0 |
| index | native-index-swar (native) | swar | 1.023 | — |
| index | native-index-scalar (native) | scalar | 0.594 | — |
| index | index-table | java-table | 0.553 (0.487–0.619) | 0.0 |
| index | index-scalar | java-scalar | 0.328 (0.327–0.329) | 0.0 |
| index | index-vector | java-vector64 | 0.043 (0.043–0.044) | 10188.3 |
| scan | native-indexed-scan (native) | avx512 | 4.224 | — |
| scan | kernel-native | native-avx512-svm | 3.793 (3.672–3.915) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 3.648 (3.640–3.657) | 0.4 |
| scan | kernel-native-small | native-avx512-svm-leaf | 3.456 (3.035–3.876) | 0.0 |
| scan | kernel-swar | java-swar | 1.481 (1.477–1.485) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.348 (1.263–1.432) | 0.3 |
| scan | csv-core-scan (native) |  | 0.475 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.473 (0.445–0.501) | 0.2 |
| scan | csv-byte-record (native) |  | 0.465 | — |
| scan | jackson |  | 0.196 (0.195–0.196) | 284.3 |
| scan | fastcsv |  | 0.169 (0.136–0.201) | 11.9 |
| scan | kernel-vector | java-vector64 | 0.043 (0.043–0.044) | 10188.3 |
| strings | csv-string-record (native) |  | 0.427 | — |
| strings | kernel-native | native-avx512-svm | 0.336 (0.335–0.336) | 284.2 |
| strings | kernel-swar | java-swar | 0.287 (0.286–0.288) | 284.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.244 (0.227–0.261) | 838.1 |
| strings | truffledata-batch | java-swar | 0.220 (0.212–0.229) | 837.9 |
| strings | truffledata-mapped | java-swar | 0.179 (0.176–0.183) | 1329.3 |
| strings | truffledata-batch-bytes | per-byte | 0.178 (0.177–0.178) | 837.8 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.170 (0.141–0.199) | 1329.6 |
| strings | jackson |  | 0.166 (0.155–0.177) | 284.3 |
| strings | fastcsv |  | 0.155 (0.144–0.166) | 344.1 |
| strings | univocity |  | 0.117 (0.117–0.117) | 1245.5 |
| strings | commons-csv |  | 0.081 (0.079–0.082) | 363.4 |
| utf8 | simdutf-validate (native) |  | 61.652 | — |

## csv-comparison: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 12.486 (12.411–12.562) | 0.1 |
| index | index-native-small | native-avx512-svm-leaf | 12.396 (12.244–12.548) | 0.1 |
| index | native-index-avx512 (native) | avx512 | 12.388 | — |
| index | index-native-avx512 | native-avx512-svm | 12.356 (12.221–12.491) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 10.840 | — |
| index | index-native-avx2 | native-avx2-svm | 10.824 (10.803–10.846) | 0.1 |
| index | index-swar | java-swar | 1.848 (1.846–1.850) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.506 (1.506–1.507) | 0.1 |
| index | native-index-swar (native) | swar | 1.505 | — |
| index | native-index-scalar (native) | scalar | 1.249 | — |
| index | index-table | java-table | 0.666 (0.663–0.670) | 0.0 |
| index | index-scalar | java-scalar | 0.521 (0.517–0.525) | 0.0 |
| index | index-vector | java-vector64 | 0.046 (0.045–0.046) | 29278.5 |
| scan | native-indexed-scan (native) | avx512 | 9.911 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 8.999 (8.973–9.025) | 0.1 |
| scan | kernel-native | native-avx512-svm | 8.946 (8.874–9.017) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 8.133 (7.980–8.285) | 1.3 |
| scan | kernel-swar | java-swar | 1.747 (1.745–1.748) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.685 (1.668–1.702) | 0.8 |
| scan | truffledata-scanner-bytes | per-byte | 1.579 (1.578–1.581) | 0.6 |
| scan | csv-core-scan (native) |  | 0.850 | — |
| scan | csv-byte-record (native) |  | 0.831 | — |
| scan | fastcsv |  | 0.537 (0.525–0.550) | 34.2 |
| scan | jackson |  | 0.196 (0.184–0.207) | 768.9 |
| scan | kernel-vector | java-vector64 | 0.047 (0.047–0.047) | 29278.5 |
| strings | kernel-native | native-avx512-svm | 0.907 (0.761–1.054) | 768.5 |
| strings | csv-string-record (native) |  | 0.784 | — |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.677 (0.660–0.695) | 1563.9 |
| strings | kernel-swar | java-swar | 0.660 (0.635–0.685) | 768.4 |
| strings | truffledata-batch-bytes | per-byte | 0.505 (0.495–0.514) | 1562.9 |
| strings | truffledata-batch | java-swar | 0.495 (0.494–0.496) | 1563.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.424 (0.411–0.437) | 2934.4 |
| strings | fastcsv |  | 0.377 (0.374–0.380) | 850.9 |
| strings | truffledata-mapped | java-swar | 0.356 (0.356–0.356) | 2933.7 |
| strings | jackson |  | 0.211 (0.211–0.211) | 768.9 |
| strings | univocity |  | 0.148 (0.108–0.188) | 3486.1 |
| strings | commons-csv |  | 0.106 (0.106–0.107) | 863.1 |
| utf8 | simdutf-validate (native) |  | 61.648 | — |

## csv-comparison: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 8.029 (7.990–8.068) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 7.751 (7.723–7.780) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 7.732 | — |
| index | index-native | native-avx512-svm | 7.687 (7.614–7.760) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 7.044 (6.988–7.101) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.783 | — |
| index | index-swar | java-swar | 2.000 (1.996–2.004) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.879 (1.855–1.904) | 0.0 |
| index | native-index-swar (native) | swar | 1.743 | — |
| index | index-table | java-table | 0.763 (0.755–0.770) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.672 | — |
| index | index-scalar | java-scalar | 0.544 (0.541–0.547) | 0.0 |
| index | index-vector | java-vector64 | 0.069 (0.066–0.071) | 10106.4 |
| scan | native-indexed-scan (native) | avx512 | 2.746 | — |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.952 (1.886–2.017) | 0.5 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.702 (1.306–2.098) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.347 (0.626–2.068) | 0.0 |
| scan | kernel-swar | java-swar | 1.137 (1.103–1.172) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.116 (1.108–1.123) | 0.3 |
| scan | csv-core-scan (native) |  | 1.034 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.823 (0.821–0.826) | 0.3 |
| scan | csv-byte-record (native) |  | 0.783 | — |
| scan | fastcsv |  | 0.479 (0.473–0.485) | 11.6 |
| scan | jackson |  | 0.212 (0.210–0.214) | 856.3 |
| scan | kernel-vector | java-vector64 | 0.068 (0.067–0.069) | 10106.4 |
| strings | csv-string-record (native) |  | 0.438 | — |
| strings | kernel-native | native-avx512-svm | 0.410 (0.395–0.424) | 898.6 |
| strings | kernel-swar | java-swar | 0.333 (0.319–0.347) | 898.5 |
| strings | fastcsv |  | 0.274 (0.273–0.276) | 995.9 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.239 (0.226–0.251) | 1362.6 |
| strings | univocity |  | 0.234 (0.232–0.235) | 1890.2 |
| strings | jackson |  | 0.221 (0.221–0.222) | 856.3 |
| strings | truffledata-batch | java-swar | 0.217 (0.215–0.219) | 1362.3 |
| strings | truffledata-batch-bytes | per-byte | 0.204 (0.202–0.206) | 1362.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.181 (0.180–0.182) | 2078.8 |
| strings | truffledata-mapped | java-swar | 0.156 (0.153–0.159) | 2078.5 |
| strings | commons-csv |  | 0.083 (0.083–0.083) | 1015.6 |
| typed | kernel-native | native-avx512-svm | 0.813 (0.812–0.813) | 0.0 |
| typed | kernel-swar | java-swar | 0.528 (0.524–0.532) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.438 (0.421–0.455) | 448.3 |
| typed | csv-typed (native) |  | 0.374 | — |
| typed | truffledata-plan | java-swar | 0.347 (0.335–0.360) | 448.0 |
| typed | truffledata-plan-bytes | per-byte | 0.330 (0.317–0.343) | 448.0 |
| typed | fastcsv |  | 0.166 (0.163–0.169) | 1239.1 |
| typed | jackson |  | 0.146 (0.145–0.146) | 1099.5 |
| typed | univocity |  | 0.139 (0.130–0.147) | 2133.4 |
| typed | commons-csv |  | 0.063 (0.058–0.069) | 1258.8 |
| utf8 | simdutf-validate (native) |  | 73.584 | — |

## csv-comparison: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx2 | native-avx2-svm | 2.670 (2.622–2.718) | 0.0 |
| index | index-native | native-avx512-svm | 2.651 (2.610–2.693) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 2.511 (2.397–2.625) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 2.261 (2.071–2.451) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 2.235 | — |
| index | native-index-avx512 (native) | avx512 | 2.176 | — |
| index | index-swar | java-swar | 1.128 (0.996–1.261) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.084 (0.934–1.233) | 0.0 |
| index | native-index-swar (native) | swar | 1.000 | — |
| index | index-table | java-table | 0.490 (0.488–0.493) | 0.0 |
| index | index-scalar | java-scalar | 0.355 (0.355–0.355) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.342 | — |
| index | index-vector | java-vector64 | 0.039 (0.039–0.040) | 2372.2 |
| scan | kernel-native-small | native-avx512-svm-leaf | 0.779 (0.775–0.783) | 0.0 |
| scan | kernel-native | native-avx512-svm | 0.714 (0.659–0.769) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 0.674 (0.673–0.675) | 0.0 |
| scan | kernel-swar | java-swar | 0.629 (0.629–0.630) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 0.562 | — |
| scan | truffledata-scanner | java-swar | 0.553 (0.519–0.586) | 0.0 |
| scan | csv-core-scan (native) |  | 0.484 | — |
| scan | csv-byte-record (native) |  | 0.422 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.398 (0.361–0.436) | 0.0 |
| scan | fastcsv |  | 0.281 (0.259–0.303) | 0.6 |
| scan | jackson |  | 0.089 (0.087–0.091) | 256.0 |
| scan | kernel-vector | java-vector64 | 0.037 (0.037–0.038) | 2372.2 |
| strings | csv-string-record (native) |  | 0.246 | — |
| strings | kernel-native | native-avx512-svm | 0.206 (0.182–0.230) | 256.0 |
| strings | kernel-swar | java-swar | 0.192 (0.171–0.214) | 256.0 |
| strings | fastcsv |  | 0.127 (0.109–0.145) | 320.6 |
| strings | univocity |  | 0.125 (0.125–0.125) | 297.7 |
| strings | truffledata-batch | java-swar | 0.102 (0.101–0.103) | 473.0 |
| strings | jackson |  | 0.095 (0.094–0.095) | 256.0 |
| strings | truffledata-batch-bytes | per-byte | 0.089 (0.082–0.095) | 473.0 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.086 (0.064–0.107) | 473.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.074 (0.074–0.074) | 548.4 |
| strings | truffledata-mapped | java-swar | 0.073 (0.071–0.075) | 548.4 |
| strings | commons-csv |  | 0.035 (0.034–0.036) | 344.3 |
| typed | kernel-native | native-avx512-svm | 0.243 (0.236–0.250) | 0.0 |
| typed | kernel-swar | java-swar | 0.214 (0.192–0.235) | 0.0 |
| typed | csv-typed (native) |  | 0.145 | — |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.092 (0.071–0.112) | 201.9 |
| typed | truffledata-plan | java-swar | 0.090 (0.069–0.112) | 201.8 |
| typed | truffledata-plan-bytes | per-byte | 0.074 (0.072–0.076) | 201.8 |
| typed | univocity |  | 0.046 (0.046–0.046) | 417.7 |
| typed | fastcsv |  | 0.042 (0.031–0.054) | 440.6 |
| typed | jackson |  | 0.037 (0.037–0.038) | 376.0 |
| typed | commons-csv |  | 0.024 (0.022–0.025) | 464.3 |
| utf8 | simdutf-validate (native) |  | 11.393 | — |

## csv-comparison: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 4.951 (4.946–4.956) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 4.864 (4.829–4.899) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.814 | — |
| index | index-native-avx2 | native-avx2-svm | 4.793 (4.785–4.801) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.784 | — |
| index | index-native | native-avx512-svm | 4.741 (4.575–4.906) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.715 (1.712–1.718) | 0.0 |
| index | index-swar | java-swar | 1.712 (1.709–1.716) | 0.0 |
| index | native-index-swar (native) | swar | 1.698 | — |
| index | index-table | java-table | 0.761 (0.758–0.764) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.596 | — |
| index | index-scalar | java-scalar | 0.540 (0.537–0.544) | 0.0 |
| index | index-vector | java-vector64 | 0.070 (0.069–0.070) | 10184.3 |
| scan | native-indexed-scan (native) | avx512 | 2.193 | — |
| scan | kernel-native | native-avx512-svm | 1.810 (1.798–1.823) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.788 (1.766–1.811) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.737 (1.736–1.737) | 0.2 |
| scan | kernel-swar | java-swar | 1.091 (1.085–1.096) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.061 (1.055–1.068) | 0.0 |
| scan | csv-core-scan (native) |  | 1.010 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.832 (0.829–0.834) | 0.0 |
| scan | csv-byte-record (native) |  | 0.810 | — |
| scan | fastcsv |  | 0.464 (0.455–0.473) | 2.6 |
| scan | jackson |  | 0.208 (0.205–0.210) | 855.8 |
| scan | kernel-vector | java-vector64 | 0.069 (0.068–0.069) | 10184.3 |
| strings | csv-string-record (native) |  | 0.459 | — |
| strings | kernel-swar | java-swar | 0.341 (0.340–0.342) | 898.1 |
| strings | fastcsv |  | 0.296 (0.293–0.298) | 986.5 |
| strings | kernel-native | native-avx512-svm | 0.286 (0.156–0.415) | 898.1 |
| strings | univocity |  | 0.259 (0.256–0.262) | 967.2 |
| strings | jackson |  | 0.218 (0.216–0.219) | 855.8 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.211 (0.210–0.213) | 1362.4 |
| strings | truffledata-batch | java-swar | 0.191 (0.190–0.192) | 1362.1 |
| strings | truffledata-batch-bytes | per-byte | 0.186 (0.185–0.188) | 1362.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.176 (0.173–0.179) | 1622.5 |
| strings | truffledata-mapped | java-swar | 0.158 (0.154–0.162) | 1622.2 |
| strings | commons-csv |  | 0.080 (0.079–0.082) | 1009.2 |
| typed | kernel-native | native-avx512-svm | 0.740 (0.739–0.741) | 0.0 |
| typed | kernel-swar | java-swar | 0.514 (0.500–0.527) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.406 (0.405–0.408) | 449.1 |
| typed | csv-typed (native) |  | 0.370 | — |
| typed | truffledata-plan | java-swar | 0.341 (0.339–0.343) | 448.9 |
| typed | truffledata-plan-bytes | per-byte | 0.289 (0.277–0.302) | 448.9 |
| typed | fastcsv |  | 0.169 (0.167–0.171) | 1229.7 |
| typed | univocity |  | 0.162 (0.161–0.163) | 1210.4 |
| typed | jackson |  | 0.144 (0.143–0.145) | 1099.0 |
| typed | commons-csv |  | 0.067 (0.067–0.068) | 1252.4 |
| utf8 | simdutf-validate (native) |  | 17.877 | — |

## csv-comparison: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 7.814 (7.795–7.833) | 0.0 |
| index | index-native | native-avx512-svm | 7.756 (7.686–7.825) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 7.678 (7.625–7.731) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 7.623 | — |
| index | native-index-avx512 (native) | avx512 | 7.395 | — |
| index | index-native-avx2 | native-avx2-svm | 7.359 (7.352–7.367) | 0.0 |
| index | index-swar | java-swar | 2.483 (2.406–2.559) | 0.0 |
| index | index-native-swar | native-swar-svm | 2.224 (2.195–2.252) | 0.0 |
| index | native-index-swar (native) | swar | 2.138 | — |
| index | native-index-scalar (native) | scalar | 1.084 | — |
| index | index-table | java-table | 0.992 (0.982–1.002) | 0.0 |
| index | index-scalar | java-scalar | 0.626 (0.595–0.657) | 0.0 |
| index | index-vector | java-vector64 | 0.076 (0.074–0.078) | 10225.2 |
| scan | native-indexed-scan (native) | avx512 | 6.405 | — |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 5.412 (5.401–5.423) | 0.2 |
| scan | kernel-native-small | native-avx512-svm-leaf | 5.345 (4.962–5.728) | 0.0 |
| scan | kernel-native | native-avx512-svm | 4.324 (2.872–5.776) | 0.0 |
| scan | kernel-swar | java-swar | 2.305 (2.297–2.314) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.292 (2.266–2.317) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 1.055 (1.031–1.078) | 0.0 |
| scan | csv-core-scan (native) |  | 1.039 | — |
| scan | csv-byte-record (native) |  | 0.880 | — |
| scan | fastcsv |  | 0.392 (0.389–0.395) | 2.8 |
| scan | jackson |  | 0.373 (0.362–0.383) | 283.1 |
| scan | kernel-vector | java-vector64 | 0.083 (0.083–0.083) | 10225.2 |
| strings | csv-string-record (native) |  | 0.802 | — |
| strings | kernel-native | native-avx512-svm | 0.629 (0.624–0.635) | 283.1 |
| strings | kernel-swar | java-swar | 0.519 (0.498–0.541) | 283.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.441 (0.435–0.447) | 836.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.400 (0.364–0.436) | 867.7 |
| strings | truffledata-batch | java-swar | 0.400 (0.400–0.400) | 835.8 |
| strings | truffledata-mapped | java-swar | 0.377 (0.362–0.393) | 867.4 |
| strings | jackson |  | 0.377 (0.376–0.377) | 283.1 |
| strings | fastcsv |  | 0.325 (0.307–0.343) | 333.9 |
| strings | univocity |  | 0.232 (0.229–0.236) | 314.4 |
| strings | truffledata-batch-bytes | per-byte | 0.221 (0.107–0.335) | 835.8 |
| strings | commons-csv |  | 0.127 (0.120–0.133) | 356.5 |
| utf8 | simdutf-validate (native) |  | 35.142 | — |

## csv-comparison: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 6.251 (6.242–6.260) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 6.209 (6.129–6.288) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 6.037 (5.815–6.258) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 6.023 | — |
| index | native-index-avx2 (native) | avx2 | 5.666 | — |
| index | index-native-avx2 | native-avx2-svm | 5.464 (5.194–5.734) | 0.0 |
| index | index-swar | java-swar | 1.767 (1.741–1.793) | 0.0 |
| index | native-index-swar (native) | swar | 1.472 | — |
| index | index-native-swar | native-swar-svm | 1.464 (1.460–1.468) | 0.0 |
| index | native-index-scalar (native) | scalar | 1.240 | — |
| index | index-table | java-table | 0.657 (0.646–0.668) | 0.0 |
| index | index-scalar | java-scalar | 0.507 (0.501–0.514) | 0.0 |
| index | index-vector | java-vector64 | 0.045 (0.045–0.045) | 29345.7 |
| scan | native-indexed-scan (native) | avx512 | 5.317 | — |
| scan | kernel-native | native-avx512-svm | 5.146 (5.128–5.163) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 5.040 (4.994–5.085) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 4.517 (4.002–5.032) | 0.5 |
| scan | kernel-swar | java-swar | 1.697 (1.688–1.707) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.586 (1.547–1.625) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 1.544 (1.516–1.571) | 0.0 |
| scan | csv-core-scan (native) |  | 0.846 | — |
| scan | csv-byte-record (native) |  | 0.771 | — |
| scan | fastcsv |  | 0.470 (0.414–0.526) | 8.0 |
| scan | jackson |  | 0.211 (0.210–0.211) | 768.0 |
| scan | kernel-vector | java-vector64 | 0.045 (0.045–0.045) | 29345.7 |
| strings | kernel-native | native-avx512-svm | 0.817 (0.710–0.924) | 768.0 |
| strings | csv-string-record (native) |  | 0.739 | — |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.624 (0.618–0.631) | 1614.7 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.561 (0.554–0.569) | 1561.8 |
| strings | kernel-swar | java-swar | 0.540 (0.486–0.594) | 767.9 |
| strings | truffledata-mapped | java-swar | 0.455 (0.445–0.465) | 1613.9 |
| strings | truffledata-batch-bytes | per-byte | 0.433 (0.403–0.463) | 1561.0 |
| strings | truffledata-batch | java-swar | 0.427 (0.384–0.470) | 1561.0 |
| strings | fastcsv |  | 0.363 (0.362–0.363) | 824.0 |
| strings | univocity |  | 0.190 (0.180–0.201) | 813.1 |
| strings | jackson |  | 0.176 (0.150–0.202) | 768.0 |
| strings | commons-csv |  | 0.105 (0.105–0.105) | 844.0 |
| utf8 | simdutf-validate (native) |  | 12.396 | — |

## csv-comparison-v3: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 8.608 (8.425–8.791) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 8.532 | — |
| index | index-native-small | native-avx512-svm-leaf | 8.512 (8.497–8.528) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 8.312 (8.133–8.490) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 6.687 (6.658–6.715) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.617 | — |
| index | index-vector | java-vector256 | 5.305 (5.204–5.406) | 0.0 |
| index | index-swar | java-swar | 2.177 (2.163–2.190) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.698 (1.684–1.712) | 0.0 |
| index | native-index-swar (native) | swar | 1.682 | — |
| index | index-table | java-table | 0.878 (0.877–0.878) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.822 | — |
| index | index-scalar | java-scalar | 0.716 (0.706–0.726) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.383 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.647 (1.647–1.648) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.607 (1.589–1.625) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.485 (1.483–1.488) | 0.1 |
| scan | kernel-vector | java-vector256 | 1.426 (1.403–1.448) | 0.0 |
| scan | kernel-swar | java-swar | 1.009 (0.950–1.069) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.955 (0.926–0.983) | 0.1 |
| scan | csv-core-scan (native) |  | 0.951 | — |
| scan | csv-byte-record (native) |  | 0.763 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.746 (0.730–0.762) | 0.0 |
| scan | fastcsv |  | 0.581 (0.567–0.595) | 2.6 |
| scan | jackson |  | 0.184 (0.179–0.189) | 256.0 |
| strings | csv-string-record (native) |  | 0.722 | — |
| strings | kernel-native | native-avx512-svm | 0.417 (0.415–0.418) | 256.0 |
| strings | kernel-swar | java-swar | 0.359 (0.354–0.364) | 256.0 |
| strings | fastcsv |  | 0.324 (0.324–0.324) | 322.7 |
| strings | univocity |  | 0.226 (0.218–0.235) | 505.8 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.201 (0.199–0.203) | 404.3 |
| strings | jackson |  | 0.194 (0.191–0.197) | 256.0 |
| strings | truffledata-batch | java-swar | 0.185 (0.185–0.186) | 404.3 |
| strings | truffledata-batch-bytes | per-byte | 0.173 (0.173–0.173) | 404.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.141 (0.140–0.142) | 532.4 |
| strings | truffledata-mapped | java-swar | 0.132 (0.125–0.140) | 532.3 |
| strings | commons-csv |  | 0.078 (0.077–0.079) | 345.6 |
| typed | csv-typed (native) |  | 0.466 | — |
| typed | kernel-native | native-avx512-svm | 0.441 (0.436–0.447) | 0.0 |
| typed | kernel-swar | java-swar | 0.392 (0.381–0.403) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.256 (0.254–0.259) | 140.4 |
| typed | truffledata-plan | java-swar | 0.227 (0.224–0.230) | 140.3 |
| typed | truffledata-plan-bytes | per-byte | 0.216 (0.212–0.219) | 140.3 |
| typed | fastcsv |  | 0.111 (0.110–0.112) | 442.7 |
| typed | univocity |  | 0.098 (0.098–0.099) | 625.8 |
| typed | jackson |  | 0.094 (0.093–0.094) | 376.0 |
| typed | commons-csv |  | 0.051 (0.048–0.053) | 465.6 |
| utf8 | simdutf-validate (native) |  | 103.263 | — |

## csv-comparison-v3: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 10.495 | — |
| index | index-native-avx512 | native-avx512-svm | 10.473 (10.351–10.595) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 10.463 (10.443–10.483) | 0.0 |
| index | index-native | native-avx512-svm | 10.419 (10.370–10.468) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 10.137 (10.131–10.144) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 9.063 | — |
| index | index-vector | java-vector256 | 5.783 (5.669–5.896) | 0.0 |
| index | index-swar | java-swar | 2.419 (2.372–2.467) | 0.0 |
| index | native-index-swar (native) | swar | 1.965 | — |
| index | index-native-swar | native-swar-svm | 1.079 (0.574–1.584) | 0.0 |
| index | index-table | java-table | 0.847 (0.841–0.852) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.838 | — |
| index | index-scalar | java-scalar | 0.579 (0.569–0.589) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 7.638 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 6.811 (6.772–6.851) | 0.0 |
| scan | kernel-native | native-avx512-svm | 6.809 (6.746–6.871) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 6.142 (5.931–6.353) | 0.4 |
| scan | kernel-vector | java-vector256 | 4.542 (4.480–4.604) | 0.0 |
| scan | kernel-swar | java-swar | 2.165 (2.156–2.175) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.877 (1.850–1.903) | 0.3 |
| scan | truffledata-scanner-bytes | per-byte | 0.882 (0.873–0.891) | 0.2 |
| scan | csv-core-scan (native) |  | 0.800 | — |
| scan | csv-byte-record (native) |  | 0.587 | — |
| scan | fastcsv |  | 0.365 (0.364–0.366) | 11.9 |
| scan | jackson |  | 0.331 (0.325–0.336) | 284.3 |
| strings | kernel-native | native-avx512-svm | 0.667 (0.657–0.677) | 284.2 |
| strings | csv-string-record (native) |  | 0.561 | — |
| strings | kernel-swar | java-swar | 0.536 (0.528–0.545) | 284.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.479 (0.479–0.479) | 838.1 |
| strings | truffledata-batch | java-swar | 0.409 (0.400–0.418) | 837.9 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.366 (0.356–0.376) | 1329.6 |
| strings | jackson |  | 0.339 (0.336–0.341) | 284.3 |
| strings | truffledata-mapped | java-swar | 0.324 (0.323–0.325) | 1329.3 |
| strings | truffledata-batch-bytes | per-byte | 0.321 (0.307–0.335) | 837.8 |
| strings | fastcsv |  | 0.317 (0.311–0.323) | 344.1 |
| strings | univocity |  | 0.210 (0.207–0.213) | 1245.5 |
| strings | commons-csv |  | 0.111 (0.106–0.116) | 363.4 |
| utf8 | simdutf-validate (native) |  | 102.912 | — |

## csv-comparison-v3: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 12.591 | — |
| index | index-native-small | native-avx512-svm-leaf | 12.453 (12.401–12.505) | 0.1 |
| index | index-native | native-avx512-svm | 12.384 (12.310–12.459) | 0.1 |
| index | index-native-avx512 | native-avx512-svm | 12.330 (12.126–12.533) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 10.884 | — |
| index | index-native-avx2 | native-avx2-svm | 10.793 (10.727–10.859) | 0.1 |
| index | index-vector | java-vector256 | 5.063 (5.059–5.067) | 0.0 |
| index | index-swar | java-swar | 1.881 (1.880–1.882) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.503 (1.501–1.505) | 0.1 |
| index | native-index-swar (native) | swar | 1.497 | — |
| index | native-index-scalar (native) | scalar | 1.252 | — |
| index | index-table | java-table | 0.803 (0.793–0.813) | 0.0 |
| index | index-scalar | java-scalar | 0.442 (0.356–0.527) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 9.940 | — |
| scan | kernel-native | native-avx512-svm | 9.014 (8.989–9.039) | 0.1 |
| scan | kernel-native-small | native-avx512-svm-leaf | 8.916 (8.915–8.918) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 8.226 (8.214–8.239) | 1.3 |
| scan | kernel-vector | java-vector256 | 4.364 (4.356–4.371) | 0.0 |
| scan | kernel-swar | java-swar | 1.764 (1.756–1.772) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.746 (1.736–1.756) | 0.8 |
| scan | truffledata-scanner-bytes | per-byte | 1.567 (1.565–1.569) | 0.6 |
| scan | fastcsv |  | 0.934 (0.930–0.939) | 34.2 |
| scan | csv-core-scan (native) |  | 0.843 | — |
| scan | csv-byte-record (native) |  | 0.830 | — |
| scan | jackson |  | 0.238 (0.237–0.238) | 768.9 |
| strings | kernel-native | native-avx512-svm | 1.855 (1.855–1.855) | 768.5 |
| strings | kernel-swar | java-swar | 0.931 (0.928–0.935) | 768.4 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.926 (0.925–0.927) | 1563.9 |
| strings | csv-string-record (native) |  | 0.787 | — |
| strings | fastcsv |  | 0.731 (0.721–0.741) | 850.9 |
| strings | truffledata-batch | java-swar | 0.613 (0.611–0.615) | 1563.1 |
| strings | truffledata-batch-bytes | per-byte | 0.608 (0.605–0.611) | 1562.9 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.531 (0.529–0.532) | 2934.4 |
| strings | truffledata-mapped | java-swar | 0.408 (0.401–0.416) | 2933.7 |
| strings | jackson |  | 0.238 (0.237–0.239) | 768.9 |
| strings | univocity |  | 0.234 (0.229–0.239) | 3486.1 |
| strings | commons-csv |  | 0.125 (0.125–0.125) | 863.1 |
| utf8 | simdutf-validate (native) |  | 61.759 | — |

## csv-comparison-v3: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 8.051 (8.045–8.057) | 0.0 |
| index | index-native | native-avx512-svm | 7.754 (7.499–8.008) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 7.414 | — |
| index | index-native-small | native-avx512-svm-leaf | 7.214 (7.163–7.264) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 7.046 (6.998–7.094) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.809 | — |
| index | index-vector | java-vector256 | 4.293 (4.291–4.295) | 0.0 |
| index | index-swar | java-swar | 2.128 (2.123–2.133) | 0.0 |
| index | native-index-swar (native) | swar | 1.870 | — |
| index | index-native-swar | native-swar-svm | 0.945 (0.568–1.321) | 0.0 |
| index | index-table | java-table | 0.787 (0.780–0.794) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.756 | — |
| index | index-scalar | java-scalar | 0.618 (0.608–0.627) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.681 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 2.123 (2.111–2.135) | 0.0 |
| scan | kernel-native | native-avx512-svm | 2.123 (2.115–2.131) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.959 (1.920–1.999) | 0.5 |
| scan | kernel-vector | java-vector256 | 1.663 (1.630–1.697) | 0.0 |
| scan | kernel-swar | java-swar | 1.176 (1.146–1.205) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.151 (1.135–1.167) | 0.3 |
| scan | csv-core-scan (native) |  | 0.964 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.828 (0.823–0.834) | 0.3 |
| scan | csv-byte-record (native) |  | 0.780 | — |
| scan | fastcsv |  | 0.411 (0.366–0.456) | 11.6 |
| scan | jackson |  | 0.217 (0.214–0.221) | 856.3 |
| strings | csv-string-record (native) |  | 0.463 | — |
| strings | kernel-native | native-avx512-svm | 0.446 (0.441–0.450) | 898.6 |
| strings | kernel-swar | java-swar | 0.351 (0.343–0.358) | 898.5 |
| strings | fastcsv |  | 0.308 (0.302–0.313) | 995.9 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.259 (0.258–0.260) | 1362.6 |
| strings | jackson |  | 0.219 (0.209–0.228) | 856.3 |
| strings | truffledata-batch-bytes | per-byte | 0.217 (0.216–0.219) | 1362.3 |
| strings | univocity |  | 0.212 (0.176–0.248) | 1890.2 |
| strings | truffledata-batch | java-swar | 0.205 (0.176–0.233) | 1362.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.182 (0.178–0.185) | 2078.8 |
| strings | truffledata-mapped | java-swar | 0.166 (0.164–0.167) | 2078.5 |
| strings | commons-csv |  | 0.080 (0.078–0.082) | 1015.6 |
| typed | kernel-native | native-avx512-svm | 0.845 (0.842–0.848) | 0.0 |
| typed | kernel-swar | java-swar | 0.562 (0.558–0.567) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.463 (0.448–0.477) | 448.3 |
| typed | csv-typed (native) |  | 0.375 | — |
| typed | truffledata-plan | java-swar | 0.367 (0.356–0.378) | 448.0 |
| typed | truffledata-plan-bytes | per-byte | 0.342 (0.335–0.349) | 448.0 |
| typed | univocity |  | 0.147 (0.147–0.148) | 2133.4 |
| typed | jackson |  | 0.140 (0.137–0.143) | 1099.5 |
| typed | fastcsv |  | 0.138 (0.108–0.167) | 1239.1 |
| typed | commons-csv |  | 0.066 (0.065–0.067) | 1258.8 |
| utf8 | simdutf-validate (native) |  | 64.350 | — |

## csv-comparison-v3: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 8.499 (8.464–8.533) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 8.450 (8.369–8.530) | 0.0 |
| index | index-native | native-avx512-svm | 7.908 (7.481–8.334) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 7.753 | — |
| index | index-native-avx2 | native-avx2-svm | 6.535 (6.458–6.612) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.203 | — |
| index | index-vector | java-vector256 | 3.393 (1.339–5.448) | 0.0 |
| index | index-swar | java-swar | 2.274 (2.245–2.303) | 0.0 |
| index | native-index-swar (native) | swar | 1.581 | — |
| index | index-table | java-table | 0.880 (0.872–0.889) | 0.0 |
| index | index-native-swar | native-swar-svm | 0.821 (0.567–1.074) | 0.0 |
| index | index-scalar | java-scalar | 0.739 (0.734–0.744) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.659 | — |
| scan | native-indexed-scan (native) | avx512 | 2.075 | — |
| scan | kernel-native | native-avx512-svm | 1.724 (1.714–1.733) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.713 (1.710–1.716) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.490 (1.453–1.526) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.433 (1.372–1.494) | 0.0 |
| scan | csv-core-scan (native) |  | 1.120 | — |
| scan | kernel-swar | java-swar | 1.101 (1.093–1.109) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.025 (1.009–1.041) | 0.0 |
| scan | csv-byte-record (native) |  | 0.861 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.758 (0.708–0.808) | 0.0 |
| scan | fastcsv |  | 0.580 (0.579–0.580) | 0.6 |
| scan | jackson |  | 0.190 (0.189–0.191) | 256.0 |
| strings | csv-string-record (native) |  | 0.684 | — |
| strings | kernel-native | native-avx512-svm | 0.415 (0.397–0.432) | 256.0 |
| strings | kernel-swar | java-swar | 0.380 (0.375–0.386) | 256.0 |
| strings | fastcsv |  | 0.313 (0.307–0.318) | 320.6 |
| strings | univocity |  | 0.270 (0.263–0.277) | 297.7 |
| strings | jackson |  | 0.197 (0.196–0.198) | 256.0 |
| strings | truffledata-batch | java-swar | 0.177 (0.176–0.178) | 473.0 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.174 (0.160–0.188) | 473.1 |
| strings | truffledata-batch-bytes | per-byte | 0.163 (0.163–0.164) | 473.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.140 (0.135–0.144) | 548.4 |
| strings | truffledata-mapped | java-swar | 0.134 (0.128–0.140) | 548.4 |
| strings | commons-csv |  | 0.075 (0.074–0.076) | 344.3 |
| typed | kernel-native | native-avx512-svm | 0.481 (0.476–0.486) | 0.0 |
| typed | csv-typed (native) |  | 0.441 | — |
| typed | kernel-swar | java-swar | 0.404 (0.394–0.413) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.223 (0.209–0.237) | 201.9 |
| typed | truffledata-plan | java-swar | 0.196 (0.171–0.221) | 201.8 |
| typed | truffledata-plan-bytes | per-byte | 0.153 (0.093–0.213) | 201.8 |
| typed | fastcsv |  | 0.107 (0.102–0.112) | 440.6 |
| typed | univocity |  | 0.103 (0.102–0.104) | 417.7 |
| typed | jackson |  | 0.092 (0.091–0.093) | 376.0 |
| typed | commons-csv |  | 0.052 (0.050–0.053) | 464.3 |
| utf8 | simdutf-validate (native) |  | 31.171 | — |

## csv-comparison-v3: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 4.869 | — |
| index | index-native-small | native-avx512-svm-leaf | 4.854 (4.822–4.886) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 4.823 (4.771–4.875) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 4.762 (4.743–4.781) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.761 | — |
| index | index-native | native-avx512-svm | 4.729 (4.635–4.822) | 0.0 |
| index | index-vector | java-vector256 | 3.278 (3.267–3.290) | 0.0 |
| index | index-swar | java-swar | 1.821 (1.821–1.821) | 0.0 |
| index | native-index-swar (native) | swar | 1.692 | — |
| index | index-native-swar | native-swar-svm | 1.498 (1.329–1.667) | 0.0 |
| index | index-table | java-table | 0.788 (0.787–0.789) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.753 | — |
| index | index-scalar | java-scalar | 0.627 (0.617–0.636) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.260 | — |
| scan | kernel-native | native-avx512-svm | 1.755 (1.727–1.783) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.751 (1.745–1.757) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.745 (1.741–1.749) | 0.2 |
| scan | kernel-vector | java-vector256 | 1.514 (1.501–1.527) | 0.0 |
| scan | kernel-swar | java-swar | 1.101 (1.079–1.124) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.071 (1.058–1.083) | 0.0 |
| scan | csv-core-scan (native) |  | 1.027 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.819 (0.814–0.825) | 0.0 |
| scan | csv-byte-record (native) |  | 0.811 | — |
| scan | fastcsv |  | 0.403 (0.360–0.446) | 2.6 |
| scan | jackson |  | 0.207 (0.205–0.209) | 855.8 |
| strings | csv-string-record (native) |  | 0.470 | — |
| strings | kernel-native | native-avx512-svm | 0.432 (0.423–0.440) | 898.1 |
| strings | kernel-swar | java-swar | 0.361 (0.359–0.363) | 898.1 |
| strings | fastcsv |  | 0.300 (0.299–0.302) | 986.5 |
| strings | univocity |  | 0.275 (0.274–0.275) | 967.2 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.213 (0.211–0.214) | 1362.4 |
| strings | jackson |  | 0.213 (0.210–0.215) | 855.8 |
| strings | truffledata-batch-bytes | per-byte | 0.193 (0.192–0.193) | 1362.1 |
| strings | truffledata-batch | java-swar | 0.186 (0.182–0.190) | 1362.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.176 (0.171–0.181) | 1622.5 |
| strings | truffledata-mapped | java-swar | 0.156 (0.151–0.162) | 1622.2 |
| strings | commons-csv |  | 0.079 (0.077–0.081) | 1009.2 |
| typed | kernel-native | native-avx512-svm | 0.780 (0.773–0.787) | 0.0 |
| typed | kernel-swar | java-swar | 0.557 (0.553–0.561) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.416 (0.409–0.423) | 449.1 |
| typed | csv-typed (native) |  | 0.398 | — |
| typed | truffledata-plan | java-swar | 0.351 (0.351–0.351) | 448.9 |
| typed | truffledata-plan-bytes | per-byte | 0.321 (0.319–0.323) | 448.9 |
| typed | fastcsv |  | 0.163 (0.159–0.167) | 1229.7 |
| typed | univocity |  | 0.160 (0.159–0.160) | 1210.4 |
| typed | jackson |  | 0.143 (0.143–0.144) | 1099.0 |
| typed | commons-csv |  | 0.065 (0.065–0.065) | 1252.4 |
| utf8 | simdutf-validate (native) |  | 18.427 | — |

## csv-comparison-v3: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx2 | native-avx2-svm | 4.926 (4.912–4.940) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.874 | — |
| index | index-native-small | native-avx512-svm-leaf | 4.776 (4.745–4.808) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 4.752 (4.735–4.768) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.737 | — |
| index | index-vector | java-vector256 | 3.392 (3.382–3.403) | 0.0 |
| index | index-native | native-avx512-svm | 2.714 (0.650–4.778) | 0.0 |
| index | native-index-swar (native) | swar | 1.428 | — |
| index | index-swar | java-swar | 1.408 (1.408–1.408) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.308 (1.189–1.426) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.629 | — |
| index | index-table | java-table | 0.561 (0.554–0.567) | 0.0 |
| index | index-scalar | java-scalar | 0.395 (0.395–0.395) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 3.817 | — |
| scan | kernel-native | native-avx512-svm | 3.334 (3.260–3.408) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 3.150 (3.102–3.198) | 0.2 |
| scan | kernel-native-small | native-avx512-svm-leaf | 2.734 (2.069–3.399) | 0.0 |
| scan | kernel-vector | java-vector256 | 2.616 (2.600–2.631) | 0.0 |
| scan | kernel-swar | java-swar | 1.260 (1.255–1.265) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.237 (1.235–1.239) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 0.650 (0.627–0.673) | 0.0 |
| scan | csv-core-scan (native) |  | 0.568 | — |
| scan | csv-byte-record (native) |  | 0.527 | — |
| scan | fastcsv |  | 0.251 (0.249–0.253) | 2.8 |
| scan | jackson |  | 0.224 (0.224–0.225) | 283.1 |
| strings | csv-string-record (native) |  | 0.477 | — |
| strings | kernel-native | native-avx512-svm | 0.436 (0.432–0.440) | 283.1 |
| strings | kernel-swar | java-swar | 0.364 (0.362–0.365) | 283.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.329 (0.326–0.332) | 836.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.288 (0.279–0.298) | 867.7 |
| strings | truffledata-mapped | java-swar | 0.241 (0.230–0.252) | 867.4 |
| strings | jackson |  | 0.225 (0.224–0.225) | 283.1 |
| strings | fastcsv |  | 0.223 (0.220–0.227) | 333.9 |
| strings | truffledata-batch | java-swar | 0.205 (0.138–0.273) | 835.8 |
| strings | truffledata-batch-bytes | per-byte | 0.149 (0.067–0.231) | 835.8 |
| strings | univocity |  | 0.148 (0.145–0.151) | 314.4 |
| strings | commons-csv |  | 0.079 (0.079–0.079) | 356.5 |
| utf8 | simdutf-validate (native) |  | 29.829 | — |

## csv-comparison-v3: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 12.250 | — |
| index | index-native-avx512 | native-avx512-svm | 12.145 (11.944–12.347) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 12.107 | — |
| index | index-native-small | native-avx512-svm-leaf | 12.092 (12.087–12.097) | 0.0 |
| index | index-native | native-avx512-svm | 12.038 (11.914–12.162) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 11.050 (10.869–11.230) | 0.0 |
| index | index-vector | java-vector256 | 6.589 (6.245–6.934) | 0.0 |
| index | index-swar | java-swar | 2.448 (2.408–2.488) | 0.0 |
| index | native-index-scalar (native) | scalar | 2.249 | — |
| index | native-index-swar (native) | swar | 2.224 | — |
| index | index-native-swar | native-swar-svm | 1.977 (1.752–2.201) | 0.0 |
| index | index-scalar | java-scalar | 0.990 (0.936–1.044) | 0.0 |
| index | index-table | java-table | 0.875 (0.874–0.877) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 10.316 | — |
| scan | kernel-native | native-avx512-svm | 9.040 (9.003–9.078) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 8.667 (8.552–8.782) | 0.5 |
| scan | kernel-native-small | native-avx512-svm-leaf | 7.858 (6.899–8.818) | 0.0 |
| scan | kernel-vector | java-vector256 | 5.694 (5.633–5.755) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 2.650 (2.597–2.703) | 0.0 |
| scan | kernel-swar | java-swar | 2.243 (2.159–2.327) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.204 (2.097–2.312) | 0.0 |
| scan | csv-core-scan (native) |  | 1.401 | — |
| scan | fastcsv |  | 1.192 (1.172–1.213) | 8.0 |
| scan | csv-byte-record (native) |  | 1.093 | — |
| scan | jackson |  | 0.336 (0.324–0.348) | 768.0 |
| strings | kernel-native | native-avx512-svm | 2.726 (2.660–2.791) | 768.0 |
| strings | kernel-swar | java-swar | 1.387 (1.376–1.398) | 767.9 |
| strings | csv-string-record (native) |  | 1.007 | — |
| strings | fastcsv |  | 0.975 (0.962–0.987) | 824.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.912 (0.862–0.961) | 1614.7 |
| strings | truffledata-batch | java-swar | 0.849 (0.841–0.857) | 1561.0 |
| strings | truffledata-batch-bytes | per-byte | 0.769 (0.678–0.860) | 1561.0 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.719 (0.219–1.219) | 1561.8 |
| strings | truffledata-mapped | java-swar | 0.675 (0.667–0.683) | 1613.9 |
| strings | univocity |  | 0.441 (0.440–0.443) | 813.1 |
| strings | jackson |  | 0.343 (0.341–0.345) | 768.0 |
| strings | commons-csv |  | 0.155 (0.155–0.155) | 844.0 |
| utf8 | simdutf-validate (native) |  | 33.643 | — |

## csv_jvm: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.727 | — |
| index | index-native-small | native-avx512-ffm | 8.716 (8.710–8.722) | 0.0 |
| index | index-native | native-avx512-ffm | 6.797 (6.779–6.816) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 6.793 (6.774–6.812) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.716 | — |
| index | index-vector | java-vector512 | 6.611 (6.547–6.675) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.478 (5.464–5.492) | 0.0 |
| index | index-swar | java-swar | 2.571 (2.544–2.598) | 0.0 |
| index | native-index-swar (native) | swar | 1.759 | — |
| index | index-native-swar | native-swar-ffm | 1.639 (1.632–1.647) | 0.0 |
| index | index-scalar | java-scalar | 1.315 (1.291–1.339) | 0.0 |
| index | index-table | java-table | 0.959 (0.939–0.979) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.798 | — |
| scan | kernel-native-small | native-avx512-ffm | 3.002 (2.991–3.014) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-ffm | 2.882 (2.873–2.892) | 0.2 |
| scan | kernel-vector | java-vector512 | 2.869 (2.818–2.920) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.500 | — |
| scan | kernel-swar | java-swar | 1.689 (1.681–1.698) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 1.579 (0.250–2.908) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.520 (1.499–1.541) | 0.1 |
| scan | csv-core-scan (native) |  | 1.121 | — |
| scan | fastcsv |  | 1.095 (1.087–1.103) | 2.0 |
| scan | truffledata-scanner-bytes | per-byte | 1.018 (1.017–1.019) | 0.0 |
| scan | csv-byte-record (native) |  | 0.879 | — |
| scan | jackson |  | 0.354 (0.296–0.413) | 384.1 |
| strings | kernel-native | native-avx512-ffm | 0.891 (0.885–0.898) | 0.0 |
| strings | csv-string-record (native) |  | 0.772 | — |
| strings | kernel-swar | java-swar | 0.713 (0.692–0.735) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.623 (0.619–0.627) | 124.5 |
| strings | truffledata-batch | java-swar | 0.492 (0.474–0.511) | 124.3 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.366 (0.337–0.395) | 316.5 |
| strings | truffledata-batch-bytes | per-byte | 0.331 (0.219–0.444) | 124.3 |
| strings | truffledata-mapped | java-swar | 0.236 (0.115–0.357) | 316.4 |
| strings | fastcsv |  | 0.198 (0.016–0.380) | 466.1 |
| strings | commons-csv |  | 0.186 (0.186–0.187) | 481.3 |
| strings | jackson |  | 0.158 (0.014–0.302) | 384.1 |
| strings | univocity |  | 0.143 (0.010–0.275) | 641.8 |
| typed | kernel-native | native-avx512-ffm | 0.688 (0.686–0.690) | 0.0 |
| typed | kernel-swar | java-swar | 0.596 (0.592–0.601) | 0.0 |
| typed | csv-typed (native) |  | 0.405 | — |
| typed | truffledata-plan-native | native-avx512-ffm | 0.260 (0.243–0.276) | 140.5 |
| typed | truffledata-plan-bytes | per-byte | 0.218 (0.209–0.227) | 140.3 |
| typed | univocity |  | 0.215 (0.215–0.215) | 713.8 |
| typed | truffledata-plan | java-swar | 0.207 (0.199–0.215) | 140.3 |
| typed | jackson |  | 0.202 (0.195–0.210) | 456.1 |
| typed | fastcsv |  | 0.178 (0.142–0.214) | 538.1 |
| typed | commons-csv |  | 0.134 (0.133–0.134) | 553.3 |
| utf8 | simdutf-validate (native) |  | 107.712 | — |

## csv_jvm: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 10.514 | — |
| index | index-native-small | native-avx512-ffm | 10.488 (10.467–10.509) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 10.100 | — |
| index | index-native-avx512 | native-avx512-ffm | 8.734 (8.721–8.748) | 0.0 |
| index | index-native | native-avx512-ffm | 8.591 (8.514–8.668) | 0.0 |
| index | index-vector | java-vector512 | 8.504 (8.434–8.575) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 7.931 (7.466–8.397) | 0.0 |
| index | index-swar | java-swar | 2.756 (2.742–2.770) | 0.0 |
| index | native-index-swar (native) | swar | 2.006 | — |
| index | index-native-swar | native-swar-ffm | 1.871 (1.869–1.874) | 0.0 |
| index | index-table | java-table | 0.960 (0.952–0.969) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.912 | — |
| index | index-scalar | java-scalar | 0.801 (0.798–0.804) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 8.192 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 7.648 (7.251–8.044) | 0.7 |
| scan | kernel-vector | java-vector512 | 7.167 (7.081–7.254) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 7.147 (7.125–7.169) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 6.078 (4.055–8.101) | 0.1 |
| scan | truffledata-scanner | java-swar | 2.535 (2.531–2.539) | 0.2 |
| scan | kernel-swar | java-swar | 2.534 (2.513–2.554) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 1.086 (1.080–1.092) | 0.2 |
| scan | csv-core-scan (native) |  | 0.827 | — |
| scan | csv-byte-record (native) |  | 0.655 | — |
| scan | fastcsv |  | 0.417 (0.417–0.418) | 9.1 |
| scan | jackson |  | 0.376 (0.334–0.418) | 332.3 |
| strings | csv-string-record (native) |  | 0.605 | — |
| strings | kernel-native | native-avx512-ffm | 0.437 (0.433–0.442) | 0.0 |
| strings | jackson |  | 0.413 (0.412–0.413) | 332.3 |
| strings | kernel-swar | java-swar | 0.405 (0.403–0.407) | 0.0 |
| strings | fastcsv |  | 0.361 (0.359–0.364) | 405.4 |
| strings | truffledata-batch | java-swar | 0.344 (0.329–0.358) | 545.9 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.337 (0.331–0.342) | 1062.2 |
| strings | truffledata-mapped | java-swar | 0.321 (0.318–0.324) | 1061.3 |
| strings | truffledata-batch-bytes | per-byte | 0.291 (0.273–0.310) | 545.8 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.277 (0.152–0.402) | 546.7 |
| strings | commons-csv |  | 0.247 (0.229–0.265) | 418.8 |
| strings | univocity |  | 0.171 (0.029–0.314) | 1301.5 |
| utf8 | simdutf-validate (native) |  | 108.613 | — |

## csv_jvm: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 17.841 | — |
| index | native-index-avx2 (native) | avx2 | 15.918 | — |
| index | index-native-small | native-avx512-ffm | 14.985 (13.738–16.232) | 0.2 |
| index | index-native | native-avx512-ffm | 12.708 (12.417–12.999) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 12.096 (10.836–13.356) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 11.995 (11.823–12.166) | 0.0 |
| index | index-vector | java-vector512 | 7.887 (7.112–8.662) | 0.0 |
| index | index-swar | java-swar | 2.767 (2.660–2.875) | 0.0 |
| index | index-scalar | java-scalar | 2.368 (2.360–2.375) | 0.0 |
| index | native-index-swar (native) | swar | 2.222 | — |
| index | index-native-swar | native-swar-ffm | 2.073 (2.070–2.075) | 0.0 |
| index | native-index-scalar (native) | scalar | 2.051 | — |
| index | index-table | java-table | 0.959 (0.949–0.968) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 14.462 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 13.853 (13.716–13.990) | 2.0 |
| scan | kernel-native-small | native-avx512-ffm | 13.187 (12.998–13.375) | 0.2 |
| scan | kernel-native | native-avx512-ffm | 11.184 (11.165–11.204) | 0.0 |
| scan | kernel-vector | java-vector512 | 7.605 (6.910–8.300) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 4.380 (4.271–4.488) | 0.5 |
| scan | kernel-swar | java-swar | 2.765 (2.749–2.781) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.744 (2.654–2.835) | 0.4 |
| scan | fastcsv |  | 1.825 (1.799–1.851) | 26.2 |
| scan | csv-core-scan (native) |  | 1.489 | — |
| scan | csv-byte-record (native) |  | 1.049 | — |
| scan | jackson |  | 0.379 (0.371–0.387) | 833.0 |
| strings | kernel-swar | java-swar | 1.558 (1.549–1.566) | 0.0 |
| strings | kernel-native | native-avx512-ffm | 1.508 (0.313–2.703) | 0.0 |
| strings | fastcsv |  | 1.264 (1.234–1.294) | 923.0 |
| strings | csv-string-record (native) |  | 1.038 | — |
| strings | truffledata-batch-bytes | per-byte | 0.877 (0.259–1.494) | 771.6 |
| strings | truffledata-batch | java-swar | 0.842 (0.484–1.200) | 771.8 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.714 (0.297–1.132) | 774.2 |
| strings | truffledata-mapped | java-swar | 0.515 (0.068–0.963) | 2174.3 |
| strings | commons-csv |  | 0.307 (0.272–0.341) | 933.4 |
| strings | jackson |  | 0.200 (0.047–0.354) | 833.0 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.041 (0.030–0.052) | 2176.7 |
| strings | univocity |  | 0.030 (0.018–0.042) | 3558.3 |
| utf8 | simdutf-validate (native) |  | 109.075 | — |

## csv_jvm: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 3.664 | — |
| index | index-native-small | native-avx512-ffm | 3.549 (3.544–3.554) | 0.1 |
| index | native-index-avx512 (native) | avx512 | 3.347 | — |
| index | index-vector | java-vector512 | 3.164 (3.037–3.291) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 2.943 (2.939–2.947) | 0.0 |
| index | index-native | native-avx512-ffm | 2.912 (2.908–2.915) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 2.860 (2.822–2.898) | 0.0 |
| index | index-swar | java-swar | 1.538 (1.534–1.542) | 0.0 |
| index | native-index-swar (native) | swar | 1.144 | — |
| index | index-native-swar | native-swar-ffm | 1.061 (1.060–1.063) | 0.0 |
| index | index-table | java-table | 0.738 (0.737–0.739) | 0.0 |
| index | index-scalar | java-scalar | 0.553 (0.540–0.565) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.373 | — |
| scan | kernel-native-small | native-avx512-ffm | 1.692 (1.689–1.696) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-ffm | 1.655 (1.654–1.657) | 0.9 |
| scan | kernel-vector | java-vector512 | 1.629 (1.613–1.645) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 1.539 (1.538–1.540) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 1.477 | — |
| scan | kernel-swar | java-swar | 1.046 (1.043–1.049) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.025 (1.015–1.035) | 0.3 |
| scan | csv-byte-record (native) |  | 0.591 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.589 (0.589–0.589) | 0.3 |
| scan | csv-core-scan (native) |  | 0.567 | — |
| scan | fastcsv |  | 0.389 (0.380–0.398) | 9.1 |
| scan | jackson |  | 0.216 (0.215–0.216) | 1227.9 |
| strings | kernel-native | native-avx512-ffm | 0.487 (0.479–0.495) | 46.3 |
| strings | kernel-swar | java-swar | 0.394 (0.390–0.398) | 46.3 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.361 (0.356–0.366) | 487.3 |
| strings | csv-string-record (native) |  | 0.328 | — |
| strings | truffledata-batch | java-swar | 0.284 (0.279–0.288) | 486.5 |
| strings | truffledata-batch-bytes | per-byte | 0.236 (0.235–0.236) | 486.4 |
| strings | fastcsv |  | 0.218 (0.193–0.242) | 1380.9 |
| strings | univocity |  | 0.205 (0.197–0.214) | 2269.9 |
| strings | jackson |  | 0.205 (0.199–0.212) | 1227.9 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.204 (0.202–0.205) | 2029.3 |
| strings | truffledata-mapped | java-swar | 0.156 (0.130–0.181) | 2028.4 |
| strings | commons-csv |  | 0.119 (0.116–0.121) | 1393.9 |
| typed | kernel-native | native-avx512-ffm | 0.650 (0.650–0.651) | 0.0 |
| typed | kernel-swar | java-swar | 0.503 (0.502–0.505) | 0.0 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.414 (0.395–0.434) | 426.7 |
| typed | truffledata-plan | java-swar | 0.336 (0.335–0.336) | 425.8 |
| typed | truffledata-plan-bytes | per-byte | 0.274 (0.273–0.276) | 425.6 |
| typed | csv-typed (native) |  | 0.260 | — |
| typed | fastcsv |  | 0.169 (0.166–0.173) | 1720.1 |
| typed | jackson |  | 0.165 (0.164–0.167) | 1567.1 |
| typed | univocity |  | 0.128 (0.092–0.165) | 2417.1 |
| typed | commons-csv |  | 0.083 (0.068–0.098) | 1733.1 |
| utf8 | simdutf-validate (native) |  | 21.669 | — |

## csv_jvm: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.294 | — |
| index | index-native-small | native-avx512-ffm | 7.633 (7.045–8.220) | 0.0 |
| index | index-vector | java-vector512 | 6.895 (6.875–6.915) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.665 | — |
| index | index-native-avx512 | native-avx512-ffm | 6.196 (6.170–6.223) | 0.0 |
| index | index-native | native-avx512-ffm | 6.152 (6.143–6.161) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 4.862 (4.636–5.089) | 0.0 |
| index | index-swar | java-swar | 2.416 (2.381–2.450) | 0.0 |
| index | native-index-swar (native) | swar | 1.741 | — |
| index | index-native-swar | native-swar-ffm | 1.597 (1.591–1.602) | 0.0 |
| index | index-scalar | java-scalar | 1.245 (1.244–1.246) | 0.0 |
| index | index-table | java-table | 0.980 (0.973–0.988) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.829 | — |
| scan | kernel-native-small | native-avx512-ffm | 3.317 (3.310–3.323) | 0.0 |
| scan | kernel-vector | java-vector512 | 3.014 (3.014–3.015) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 2.725 (2.672–2.779) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-ffm | 2.494 (2.446–2.541) | 0.1 |
| scan | native-indexed-scan (native) | avx512 | 2.478 | — |
| scan | kernel-swar | java-swar | 1.694 (1.636–1.753) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.528 (1.494–1.562) | 0.0 |
| scan | csv-core-scan (native) |  | 1.152 | — |
| scan | truffledata-scanner-bytes | per-byte | 1.014 (0.986–1.042) | 0.0 |
| scan | csv-byte-record (native) |  | 0.922 | — |
| scan | fastcsv |  | 0.874 (0.869–0.880) | 0.0 |
| scan | jackson |  | 0.314 (0.303–0.325) | 384.0 |
| strings | kernel-native | native-avx512-ffm | 0.876 (0.855–0.897) | 0.0 |
| strings | csv-string-record (native) |  | 0.810 | — |
| strings | kernel-swar | java-swar | 0.744 (0.731–0.758) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.567 (0.565–0.568) | 193.2 |
| strings | fastcsv |  | 0.462 (0.435–0.490) | 464.0 |
| strings | truffledata-batch-bytes | per-byte | 0.434 (0.433–0.434) | 193.0 |
| strings | univocity |  | 0.413 (0.404–0.421) | 433.7 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.373 (0.362–0.385) | 332.6 |
| strings | truffledata-mapped | java-swar | 0.350 (0.349–0.352) | 332.4 |
| strings | jackson |  | 0.300 (0.297–0.303) | 384.0 |
| strings | commons-csv |  | 0.187 (0.186–0.187) | 480.0 |
| strings | truffledata-batch | java-swar | 0.119 (0.023–0.215) | 193.0 |
| typed | kernel-native | native-avx512-ffm | 0.704 (0.701–0.707) | 0.0 |
| typed | kernel-swar | java-swar | 0.612 (0.606–0.619) | 0.0 |
| typed | csv-typed (native) |  | 0.480 | — |
| typed | truffledata-plan-native | native-avx512-ffm | 0.298 (0.281–0.316) | 202.0 |
| typed | truffledata-plan | java-swar | 0.277 (0.267–0.286) | 201.8 |
| typed | fastcsv |  | 0.243 (0.241–0.244) | 536.0 |
| typed | univocity |  | 0.215 (0.208–0.222) | 601.7 |
| typed | jackson |  | 0.191 (0.188–0.194) | 504.0 |
| typed | commons-csv |  | 0.125 (0.123–0.127) | 648.0 |
| typed | truffledata-plan-bytes | per-byte | 0.115 (0.112–0.117) | 201.8 |
| utf8 | simdutf-validate (native) |  | 35.216 | — |

## csv_jvm: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 3.043 | — |
| index | native-index-avx512 (native) | avx512 | 3.016 | — |
| index | index-native-small | native-avx512-ffm | 2.590 (2.586–2.593) | 0.0 |
| index | index-vector | java-vector512 | 2.511 (2.509–2.513) | 0.0 |
| index | index-native | native-avx512-ffm | 2.409 (2.392–2.427) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 2.390 (2.370–2.410) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 2.360 (2.337–2.382) | 0.0 |
| index | index-swar | java-swar | 1.452 (1.440–1.464) | 0.0 |
| index | native-index-swar (native) | swar | 1.134 | — |
| index | index-native-swar | native-swar-ffm | 0.995 (0.994–0.996) | 0.0 |
| index | index-table | java-table | 0.738 (0.736–0.740) | 0.0 |
| index | index-scalar | java-scalar | 0.486 (0.463–0.509) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.424 | — |
| scan | kernel-native-small | native-avx512-ffm | 1.646 (1.639–1.652) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-ffm | 1.489 (1.433–1.545) | 0.5 |
| scan | kernel-native | native-avx512-ffm | 1.425 (1.421–1.428) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 1.413 | — |
| scan | kernel-vector | java-vector512 | 1.107 (0.715–1.499) | 0.0 |
| scan | kernel-swar | java-swar | 1.023 (1.022–1.023) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.993 (0.992–0.994) | 0.0 |
| scan | csv-core-scan (native) |  | 0.611 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.594 (0.593–0.594) | 0.0 |
| scan | csv-byte-record (native) |  | 0.574 | — |
| scan | fastcsv |  | 0.341 (0.311–0.371) | 0.1 |
| scan | jackson |  | 0.216 (0.215–0.217) | 1227.4 |
| strings | kernel-swar | java-swar | 0.391 (0.384–0.398) | 46.0 |
| strings | kernel-native | native-avx512-ffm | 0.363 (0.259–0.467) | 46.0 |
| strings | csv-string-record (native) |  | 0.325 | — |
| strings | truffledata-batch-native | native-avx512-ffm | 0.246 (0.219–0.273) | 486.8 |
| strings | fastcsv |  | 0.243 (0.228–0.258) | 1371.4 |
| strings | truffledata-batch | java-swar | 0.226 (0.196–0.256) | 486.0 |
| strings | univocity |  | 0.222 (0.222–0.222) | 1346.7 |
| strings | truffledata-batch-bytes | per-byte | 0.218 (0.208–0.229) | 486.0 |
| strings | jackson |  | 0.215 (0.214–0.216) | 1227.4 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.202 (0.201–0.203) | 1572.9 |
| strings | truffledata-mapped | java-swar | 0.164 (0.159–0.170) | 1572.1 |
| strings | commons-csv |  | 0.116 (0.113–0.119) | 1387.4 |
| typed | kernel-native | native-avx512-ffm | 0.587 (0.585–0.589) | 0.0 |
| typed | kernel-swar | java-swar | 0.487 (0.484–0.491) | 0.0 |
| typed | truffledata-plan | java-swar | 0.322 (0.313–0.330) | 424.9 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.315 (0.305–0.325) | 425.6 |
| typed | csv-typed (native) |  | 0.251 | — |
| typed | truffledata-plan-bytes | per-byte | 0.239 (0.232–0.245) | 424.9 |
| typed | fastcsv |  | 0.176 (0.170–0.182) | 1710.6 |
| typed | univocity |  | 0.165 (0.161–0.169) | 1493.9 |
| typed | jackson |  | 0.165 (0.164–0.165) | 1566.5 |
| typed | commons-csv |  | 0.095 (0.092–0.098) | 1726.6 |
| utf8 | simdutf-validate (native) |  | 8.477 | — |

## csv_jvm: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 3.847 | — |
| index | native-index-avx512 (native) | avx512 | 3.841 | — |
| index | index-vector | java-vector512 | 3.783 (3.771–3.794) | 0.0 |
| index | index-native-small | native-avx512-ffm | 3.546 (3.210–3.883) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 3.220 (3.211–3.230) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 3.207 (2.997–3.417) | 0.0 |
| index | index-native | native-avx512-ffm | 3.198 (3.002–3.393) | 0.0 |
| index | index-swar | java-swar | 1.722 (1.716–1.728) | 0.0 |
| index | native-index-swar (native) | swar | 1.277 | — |
| index | index-native-swar | native-swar-ffm | 1.132 (1.128–1.136) | 0.0 |
| index | index-table | java-table | 0.854 (0.843–0.866) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.599 | — |
| index | index-scalar | java-scalar | 0.412 (0.404–0.420) | 0.0 |
| scan | kernel-vector | java-vector512 | 3.285 (3.285–3.286) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 3.231 (3.230–3.231) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-ffm | 3.154 (3.136–3.172) | 0.5 |
| scan | native-indexed-scan (native) | avx512 | 3.102 | — |
| scan | kernel-native | native-avx512-ffm | 2.583 (2.553–2.612) | 0.0 |
| scan | kernel-swar | java-swar | 1.612 (1.598–1.626) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.577 (1.562–1.592) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 0.664 (0.652–0.677) | 0.0 |
| scan | csv-core-scan (native) |  | 0.476 | — |
| scan | csv-byte-record (native) |  | 0.454 | — |
| scan | jackson |  | 0.324 (0.321–0.327) | 331.1 |
| scan | fastcsv |  | 0.266 (0.266–0.267) | 0.1 |
| strings | csv-string-record (native) |  | 0.417 | — |
| strings | jackson |  | 0.326 (0.326–0.326) | 331.1 |
| strings | kernel-native | native-avx512-ffm | 0.313 (0.312–0.313) | 0.0 |
| strings | kernel-swar | java-swar | 0.292 (0.291–0.293) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.273 (0.273–0.274) | 545.5 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.265 (0.265–0.266) | 601.2 |
| strings | truffledata-batch | java-swar | 0.248 (0.246–0.250) | 544.7 |
| strings | truffledata-mapped | java-swar | 0.243 (0.242–0.243) | 600.3 |
| strings | univocity |  | 0.231 (0.231–0.231) | 370.4 |
| strings | fastcsv |  | 0.218 (0.204–0.231) | 395.1 |
| strings | truffledata-batch-bytes | per-byte | 0.198 (0.188–0.208) | 544.7 |
| strings | commons-csv |  | 0.189 (0.184–0.194) | 412.7 |
| utf8 | simdutf-validate (native) |  | 12.088 | — |

## csv_jvm: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 8.959 | — |
| index | native-index-avx512 (native) | avx512 | 8.670 | — |
| index | index-native-small | native-avx512-ffm | 8.203 (7.663–8.743) | 0.1 |
| index | index-native | native-avx512-ffm | 5.975 (5.938–6.011) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.925 (5.905–5.946) | 0.0 |
| index | index-vector | java-vector512 | 5.890 (5.804–5.977) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 5.723 (5.400–6.045) | 0.0 |
| index | native-index-swar (native) | swar | 1.705 | — |
| index | index-swar | java-swar | 1.601 (1.515–1.687) | 0.0 |
| index | index-native-swar | native-swar-ffm | 1.492 (1.417–1.566) | 0.0 |
| index | index-scalar | java-scalar | 1.125 (1.121–1.130) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.900 | — |
| index | index-table | java-table | 0.582 (0.581–0.583) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 7.299 | — |
| scan | kernel-native-small | native-avx512-ffm | 7.058 (6.952–7.165) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-ffm | 6.063 (5.431–6.695) | 1.5 |
| scan | kernel-vector | java-vector512 | 5.389 (5.362–5.416) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 2.791 (0.463–5.118) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 2.591 (2.341–2.841) | 0.0 |
| scan | kernel-swar | java-swar | 1.620 (1.613–1.626) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.616 (1.610–1.622) | 0.0 |
| scan | fastcsv |  | 1.269 (1.262–1.276) | 0.2 |
| scan | csv-core-scan (native) |  | 0.931 | — |
| scan | csv-byte-record (native) |  | 0.833 | — |
| scan | jackson |  | 0.316 (0.292–0.341) | 832.0 |
| strings | kernel-native | native-avx512-ffm | 1.560 (1.552–1.567) | 0.0 |
| strings | kernel-swar | java-swar | 0.955 (0.951–0.959) | 0.0 |
| strings | fastcsv |  | 0.904 (0.903–0.905) | 896.2 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.860 (0.690–1.030) | 772.0 |
| strings | csv-string-record (native) |  | 0.772 | — |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.758 (0.311–1.205) | 857.0 |
| strings | truffledata-mapped | java-swar | 0.735 (0.732–0.738) | 854.7 |
| strings | truffledata-batch-bytes | per-byte | 0.666 (0.343–0.989) | 769.8 |
| strings | truffledata-batch | java-swar | 0.533 (0.289–0.778) | 769.8 |
| strings | univocity |  | 0.390 (0.389–0.390) | 885.0 |
| strings | jackson |  | 0.307 (0.276–0.339) | 832.0 |
| strings | commons-csv |  | 0.178 (0.154–0.202) | 912.1 |
| utf8 | simdutf-validate (native) |  | 29.133 | — |

## Size sweep (kernel crossovers; GB/s, one fork)

| Binary | Workload | Dataset | Engine | 256 B | 1 KiB | 4 KiB | 16 KiB | 64 KiB | 256 KiB | 1024 KiB | 4096 KiB | 16384 KiB |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| csv-comparison | index | mixed | index-native | 1.14 | 2.20 | 3.88 | 4.63 | 4.77 | 4.86 | 4.78 | 3.37 | 3.24 |
| csv-comparison | index | mixed | index-native-small | 1.17 | 2.19 | 3.86 | 4.62 | 4.32 | 4.85 | 4.67 | 3.37 | 3.26 |
| csv-comparison | index | mixed | index-swar | 0.69 | 0.96 | 1.26 | 1.32 | 1.33 | 1.33 | 1.30 | 1.15 | 1.15 |
| csv-comparison | index | numeric | index-native | 0.93 | 2.00 | 3.50 | 4.07 | 4.27 | 4.36 | 4.24 | 4.26 | 4.29 |
| csv-comparison | index | numeric | index-native-small | 0.95 | 2.25 | 3.47 | 3.97 | 4.27 | 4.30 | 4.23 | 4.25 | 4.29 |
| csv-comparison | index | numeric | index-swar | 0.63 | 1.06 | 1.27 | 1.28 | 1.39 | 1.35 | 1.32 | 1.18 | 1.30 |
| csv-comparison | scan | mixed | kernel-native-small | 0.62 | 0.92 | 1.19 | 1.29 | 1.30 | 1.25 | 0.78 | 1.08 | 1.10 |
| csv-comparison | scan | mixed | sweep-native | 0.35 | 0.66 | 1.03 | 1.08 | 1.19 | 1.21 | 1.20 | 1.10 | 1.09 |
| csv-comparison | scan | mixed | sweep-swar | 0.34 | 0.53 | 0.66 | 0.69 | 0.73 | 0.74 | 0.69 | 0.66 | 0.68 |
| csv-comparison | scan | mixed | truffledata-scanner-bytes | 0.39 | 0.52 | 0.64 | 0.65 | 0.63 | 0.53 | 0.51 | 0.52 | 0.52 |
| csv-comparison | scan | numeric | kernel-native-small | 0.51 | 0.75 | 0.85 | 0.87 | 0.90 | 0.91 | 0.80 | 0.92 | 0.93 |
| csv-comparison | scan | numeric | sweep-native | 0.32 | 0.58 | 0.76 | 0.83 | 0.84 | 0.86 | 0.84 | 0.88 | 0.89 |
| csv-comparison | scan | numeric | sweep-swar | 0.32 | 0.49 | 0.47 | 0.20 | 0.59 | 0.61 | 0.60 | 0.60 | 0.60 |
| csv-comparison | scan | numeric | truffledata-scanner-bytes | 0.34 | 0.45 | 0.49 | 0.51 | 0.47 | 0.46 | 0.44 | 0.45 | 0.42 |
| csv-comparison | strings | mixed | sweep-batch-native | 0.05 | 0.10 | 0.16 | 0.19 | 0.20 | 0.18 | 0.17 | 0.15 | 0.14 |
| csv-comparison | strings | mixed | sweep-batch-swar | 0.05 | 0.10 | 0.14 | 0.16 | 0.16 | 0.15 | 0.16 | 0.11 | 0.13 |
| csv-comparison | strings | mixed | truffledata-batch-bytes | 0.06 | 0.11 | 0.16 | 0.17 | 0.12 | 0.15 | 0.15 | 0.12 | 0.12 |
| csv-comparison | strings | numeric | sweep-batch-native | 0.06 | 0.10 | 0.11 | 0.14 | 0.14 | 0.12 | 0.13 | 0.13 | 0.12 |
| csv-comparison | strings | numeric | sweep-batch-swar | 0.07 | 0.10 | 0.12 | 0.13 | 0.12 | 0.13 | 0.12 | 0.12 | 0.11 |
| csv-comparison | strings | numeric | truffledata-batch-bytes | 0.07 | 0.11 | 0.12 | 0.13 | 0.13 | 0.12 | 0.11 | 0.12 | 0.10 |
| csv-comparison-v3 | index | mixed | index-native | 1.66 | 3.27 | 6.35 | 6.98 | 7.91 | 8.04 | 7.72 | 4.98 | 4.70 |
| csv-comparison-v3 | index | mixed | index-native-small | 1.78 | 3.40 | 6.25 | 6.70 | 7.38 | 7.98 | 7.71 | 5.00 | 4.85 |
| csv-comparison-v3 | index | mixed | index-swar | 1.13 | 1.63 | 2.12 | 2.19 | 2.28 | 2.28 | 2.10 | 1.80 | 1.79 |
| csv-comparison-v3 | index | numeric | index-native | 1.07 | 3.74 | 6.53 | 8.08 | 8.56 | 8.54 | 8.54 | 8.81 | 7.69 |
| csv-comparison-v3 | index | numeric | index-native-small | 1.47 | 3.78 | 6.27 | 7.94 | 8.66 | 6.71 | 8.53 | 8.09 | 8.68 |
| csv-comparison-v3 | index | numeric | index-swar | 1.06 | 1.86 | 2.19 | 2.28 | 2.29 | 2.10 | 2.20 | 2.09 | 2.14 |
| csv-comparison-v3 | scan | mixed | kernel-native-small | 1.04 | 1.59 | 1.95 | 2.17 | 2.10 | 1.96 | 2.05 | 1.75 | 1.68 |
| csv-comparison-v3 | scan | mixed | sweep-native | 0.59 | 1.08 | 1.70 | 1.94 | 1.70 | 2.03 | 2.01 | 1.72 | 1.67 |
| csv-comparison-v3 | scan | mixed | sweep-swar | 0.60 | 0.86 | 1.07 | 1.19 | 1.22 | 1.05 | 1.16 | 1.07 | 1.03 |
| csv-comparison-v3 | scan | mixed | truffledata-scanner-bytes | 0.69 | 0.99 | 1.07 | 1.05 | 0.88 | 0.84 | 0.84 | 0.81 | 0.77 |
| csv-comparison-v3 | scan | numeric | kernel-native-small | 0.81 | 1.28 | 1.50 | 1.58 | 1.63 | 1.59 | 1.53 | 1.57 | 1.66 |
| csv-comparison-v3 | scan | numeric | sweep-native | 0.51 | 0.96 | 1.13 | 1.40 | 1.45 | 1.46 | 1.45 | 1.49 | 1.41 |
| csv-comparison-v3 | scan | numeric | sweep-swar | 0.52 | 0.60 | 0.92 | 0.95 | 0.93 | 0.94 | 0.98 | 0.99 | 1.02 |
| csv-comparison-v3 | scan | numeric | truffledata-scanner-bytes | 0.58 | 0.75 | 0.74 | 0.79 | 0.75 | 0.78 | 0.79 | 0.77 | 0.49 |
| csv-comparison-v3 | strings | mixed | sweep-batch-native | 0.08 | 0.15 | 0.22 | 0.26 | 0.23 | 0.26 | 0.27 | 0.22 | 0.19 |
| csv-comparison-v3 | strings | mixed | sweep-batch-swar | 0.08 | 0.14 | 0.20 | 0.22 | 0.22 | 0.22 | 0.22 | 0.18 | 0.17 |
| csv-comparison-v3 | strings | mixed | truffledata-batch-bytes | 0.09 | 0.15 | 0.21 | 0.23 | 0.22 | 0.20 | 0.21 | 0.19 | 0.16 |
| csv-comparison-v3 | strings | numeric | sweep-batch-native | 0.09 | 0.14 | 0.17 | 0.20 | 0.21 | 0.21 | 0.19 | 0.20 | 0.17 |
| csv-comparison-v3 | strings | numeric | sweep-batch-swar | 0.09 | 0.13 | 0.17 | 0.16 | 0.15 | 0.19 | 0.17 | 0.19 | 0.15 |
| csv-comparison-v3 | strings | numeric | truffledata-batch-bytes | 0.10 | 0.15 | 0.15 | 0.18 | 0.19 | 0.19 | 0.15 | 0.14 | 0.16 |
| csv_jvm | index | mixed | index-native | 2.28 | 4.23 | 6.29 | 8.92 | 8.64 | 9.00 | 7.97 | 5.11 | 5.09 |
| csv_jvm | index | mixed | index-native-small | 2.34 | 4.25 | 7.85 | 7.49 | 9.64 | 9.75 | 9.32 | 6.08 | 5.73 |
| csv_jvm | index | mixed | index-swar | 1.70 | 2.40 | 3.08 | 3.24 | 3.25 | 3.31 | 2.91 | 2.50 | 2.39 |
| csv_jvm | index | numeric | index-native | 1.90 | 4.51 | 7.76 | 9.13 | 9.12 | 9.18 | 8.36 | 1.35 | 7.82 |
| csv_jvm | index | numeric | index-native-small | 1.73 | 4.58 | 8.28 | 9.89 | 10.13 | 10.75 | 10.64 | 10.68 | 10.25 |
| csv_jvm | index | numeric | index-swar | 1.49 | 2.57 | 3.18 | 3.38 | 3.37 | 3.21 | 3.11 | 3.18 | 3.16 |
| csv_jvm | scan | mixed | kernel-native-small | 1.65 | 2.51 | 3.57 | 3.84 | 4.08 | 4.11 | 3.71 | 3.29 | 3.18 |
| csv_jvm | scan | mixed | sweep-native | 1.16 | 2.44 | 3.48 | 4.38 | 4.40 | 4.00 | 3.72 | 3.06 | 2.83 |
| csv_jvm | scan | mixed | sweep-swar | 1.14 | 1.59 | 2.13 | 2.29 | 2.14 | 2.15 | 1.88 | 1.76 | 1.71 |
| csv_jvm | scan | mixed | truffledata-scanner-bytes | 1.17 | 0.08 | 2.03 | 2.45 | 2.49 | 1.75 | 1.14 | 1.18 | 1.07 |
| csv_jvm | scan | numeric | kernel-native-small | 1.40 | 2.51 | 2.98 | 3.68 | 3.73 | 3.67 | 3.67 | 3.90 | 4.15 |
| csv_jvm | scan | numeric | sweep-native | 0.33 | 1.97 | 2.88 | 3.82 | 3.39 | 3.29 | 3.31 | 3.18 | 3.05 |
| csv_jvm | scan | numeric | sweep-swar | 0.97 | 1.62 | 1.67 | 1.92 | 2.07 | 2.10 | 1.85 | 1.81 | 1.78 |
| csv_jvm | scan | numeric | truffledata-scanner-bytes | 1.17 | 1.73 | 1.93 | 1.95 | 1.77 | 1.40 | 1.22 | 1.21 | 1.23 |
| csv_jvm | strings | mixed | sweep-batch-native | 0.00 | 0.36 | 0.18 | 0.86 | 0.90 | 0.84 | 0.70 | 0.66 | 0.49 |
| csv_jvm | strings | mixed | sweep-batch-swar | 0.16 | 0.29 | 0.47 | 0.60 | 0.65 | 0.57 | 0.54 | 0.53 | 0.47 |
| csv_jvm | strings | mixed | truffledata-batch-bytes | 0.05 | 0.33 | 0.61 | 0.66 | 0.75 | 0.47 | 0.48 | 0.45 | 0.36 |
| csv_jvm | strings | numeric | sweep-batch-native | 0.23 | 0.46 | 0.71 | 0.73 | 0.74 | 0.70 | 0.75 | 0.64 | 0.55 |
| csv_jvm | strings | numeric | sweep-batch-swar | 0.01 | 0.37 | 0.52 | 0.56 | 0.55 | 0.60 | 0.65 | 0.54 | 0.04 |
| csv_jvm | strings | numeric | truffledata-batch-bytes | 0.24 | 0.43 | 0.58 | 0.61 | 0.60 | 0.57 | 0.54 | 0.50 | 0.03 |

## Retained typed reads (32 numeric rows, 8 typed columns read per row)

| Binary | Engine | Stage | ns/row (range) | Java B/row |
|---|---|---|---:|---:|
| csv-comparison | batch-fastcsv | interpret | 69.33 (68.60–70.07) | 2.00 |
| csv-comparison | batch-fastcsv | peak | 12.21 (10.64–13.77) | 1.00 |
| csv-comparison | batch-fastcsv | read-aot | 70.30 (68.62–71.98) | 0.00 |
| csv-comparison | truffledata-plan | interpret | 67.18 (66.01–68.35) | 2.00 |
| csv-comparison | truffledata-plan | peak | 7.32 (6.66–7.98) | 1.00 |
| csv-comparison | truffledata-plan | read-aot | 64.89 (64.83–64.94) | 0.00 |
| csv-comparison-v3 | batch-fastcsv | interpret | 77.17 (69.56–84.78) | 2.00 |
| csv-comparison-v3 | batch-fastcsv | peak | 11.36 (10.93–11.80) | 1.00 |
| csv-comparison-v3 | batch-fastcsv | read-aot | 67.96 (67.69–68.22) | 0.00 |
| csv-comparison-v3 | truffledata-plan | interpret | 67.00 (65.89–68.11) | 2.00 |
| csv-comparison-v3 | truffledata-plan | peak | 6.75 (6.71–6.79) | 1.00 |
| csv-comparison-v3 | truffledata-plan | read-aot | 67.49 (65.54–69.45) | 0.00 |
| csv_jvm | batch-fastcsv | interpret | 30.05 (28.60–31.51) | 1.50 |
| csv_jvm | batch-fastcsv | peak | 10.00 (10.00–10.00) | 1.50 |
| csv_jvm | batch-fastcsv | read-aot | 28.44 (28.34–28.53) | 0.00 |
| csv_jvm | truffledata-plan | interpret | 31.40 (30.70–32.10) | 1.50 |
| csv_jvm | truffledata-plan | peak | 6.80 (6.72–6.87) | 1.50 |
| csv_jvm | truffledata-plan | read-aot | 29.17 (28.49–29.85) | 0.00 |
