# CSV comparison

GB/s is input bytes per nanosecond: the median of per-fork medians (range of fork medians).
Java B/row is Java heap allocation per data row. Shared-host timings are advisory.

## Hosts

| Binary | Group | CPU |
|---|---|---|
| csv-comparison | fixture escaped 1048576 | AMD EPYC |
| csv-comparison | fixture escaped 134217728 | AMD EPYC |
| csv-comparison | fixture mixed 1048576 | AMD EPYC |
| csv-comparison | fixture mixed 134217728 | AMD EPYC |
| csv-comparison | fixture numeric 1048576 | AMD EPYC |
| csv-comparison | fixture numeric 134217728 | Intel(R) Xeon(R) Processor @ 3.10GHz |
| csv-comparison | fixture quoted 1048576 | AMD EPYC |
| csv-comparison | fixture quoted 134217728 | AMD EPYC |
| csv-comparison | sweep | AMD EPYC |
| csv-comparison-v3 | fixture escaped 1048576 | AMD EPYC |
| csv-comparison-v3 | fixture escaped 134217728 | AMD EPYC |
| csv-comparison-v3 | fixture mixed 1048576 | AMD EPYC |
| csv-comparison-v3 | fixture mixed 134217728 | AMD EPYC |
| csv-comparison-v3 | fixture numeric 1048576 | AMD EPYC |
| csv-comparison-v3 | fixture numeric 134217728 | AMD EPYC |
| csv-comparison-v3 | fixture quoted 1048576 | AMD EPYC |
| csv-comparison-v3 | fixture quoted 134217728 | AMD EPYC |
| csv-comparison-v3 | sweep | AMD EPYC |
| csv_jvm | fixture escaped 1048576 | AMD EPYC |
| csv_jvm | fixture escaped 134217728 | AMD EPYC |
| csv_jvm | fixture mixed 1048576 | AMD EPYC |
| csv_jvm | fixture mixed 134217728 | AMD EPYC |
| csv_jvm | fixture numeric 1048576 | AMD EPYC |
| csv_jvm | fixture numeric 134217728 | AMD EPYC |
| csv_jvm | fixture quoted 1048576 | AMD EPYC |
| csv_jvm | fixture quoted 134217728 | AMD EPYC |
| csv_jvm | sweep | AMD EPYC |

## csv-comparison: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 8.810 (8.788–8.884) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 8.804 | — |
| index | index-native | native-avx512-svm | 8.736 (8.381–8.790) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 8.664 (7.301–8.727) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.744 | — |
| index | index-native-avx2 | native-avx2-svm | 6.670 (6.507–6.754) | 0.0 |
| index | index-swar | java-swar | 2.087 (2.072–2.090) | 0.0 |
| index | native-index-swar (native) | swar | 1.748 | — |
| index | index-native-swar | native-swar-svm | 1.740 (1.733–1.752) | 0.0 |
| index | index-table | java-table | 0.847 (0.826–0.850) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.791 | — |
| index | index-scalar | java-scalar | 0.651 (0.641–0.657) | 0.0 |
| index | index-vector | java-vector64 | 0.070 (0.070–0.071) | 2279.7 |
| scan | native-indexed-scan (native) | avx512 | 2.645 | — |
| scan | kernel-native | native-avx512-svm | 1.656 (1.576–1.666) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.656 (1.647–1.679) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.496 (1.460–1.501) | 0.1 |
| scan | csv-core-scan (native) |  | 1.124 | — |
| scan | kernel-swar | java-swar | 1.026 (1.019–1.028) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.973 (0.965–0.973) | 0.1 |
| scan | csv-byte-record (native) |  | 0.885 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.782 (0.733–0.788) | 0.0 |
| scan | fastcsv |  | 0.461 (0.298–0.497) | 2.6 |
| scan | jackson |  | 0.189 (0.186–0.190) | 256.0 |
| scan | kernel-vector | java-vector64 | 0.068 (0.065–0.068) | 2279.7 |
| strings | csv-string-record (native) |  | 0.790 | — |
| strings | kernel-native | native-avx512-svm | 0.417 (0.415–0.435) | 256.0 |
| strings | kernel-swar | java-swar | 0.372 (0.349–0.373) | 256.0 |
| strings | fastcsv |  | 0.242 (0.223–0.270) | 322.7 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.232 (0.197–0.236) | 404.3 |
| strings | univocity |  | 0.217 (0.133–0.226) | 505.8 |
| strings | truffledata-batch | java-swar | 0.209 (0.203–0.214) | 404.3 |
| strings | truffledata-batch-bytes | per-byte | 0.201 (0.196–0.207) | 404.3 |
| strings | jackson |  | 0.197 (0.197–0.200) | 256.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.137 (0.114–0.139) | 404.4 |
| strings | truffledata-mapped | java-swar | 0.133 (0.131–0.136) | 404.3 |
| strings | commons-csv |  | 0.062 (0.050–0.079) | 345.6 |
| typed | csv-typed (native) |  | 0.475 | — |
| typed | kernel-native | native-avx512-svm | 0.448 (0.447–0.450) | 0.0 |
| typed | kernel-swar | java-swar | 0.379 (0.378–0.380) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.313 (0.309–0.314) | 88.6 |
| typed | truffledata-plan | java-swar | 0.278 (0.278–0.279) | 88.5 |
| typed | truffledata-plan-bytes | per-byte | 0.231 (0.227–0.233) | 88.5 |
| typed | fastcsv |  | 0.105 (0.098–0.109) | 442.7 |
| typed | univocity |  | 0.099 (0.098–0.101) | 625.8 |
| typed | jackson |  | 0.096 (0.091–0.096) | 376.0 |
| typed | commons-csv |  | 0.052 (0.050–0.053) | 465.6 |
| utf8 | simdutf-validate (native) |  | 107.365 | — |

## csv-comparison: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-svm-leaf | 10.599 (10.209–10.602) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 10.519 (10.477–10.533) | 0.0 |
| index | index-native | native-avx512-svm | 10.508 (9.570–10.541) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 10.248 | — |
| index | native-index-avx2 (native) | avx2 | 10.089 | — |
| index | index-native-avx2 | native-avx2-svm | 10.087 (10.062–10.185) | 0.0 |
| index | index-swar | java-swar | 2.423 (2.369–2.456) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.976 (1.891–1.999) | 0.0 |
| index | native-index-swar (native) | swar | 1.956 | — |
| index | native-index-scalar (native) | scalar | 0.872 | — |
| index | index-table | java-table | 0.832 (0.829–0.838) | 0.0 |
| index | index-scalar | java-scalar | 0.575 (0.549–0.580) | 0.0 |
| index | index-vector | java-vector64 | 0.069 (0.069–0.069) | 10188.3 |
| scan | native-indexed-scan (native) | avx512 | 7.921 | — |
| scan | kernel-native | native-avx512-svm | 6.898 (6.879–6.910) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 6.869 (6.856–6.889) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 6.194 (5.092–6.347) | 0.4 |
| scan | kernel-swar | java-swar | 2.161 (2.159–2.177) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.095 (2.067–2.106) | 0.3 |
| scan | truffledata-scanner-bytes | per-byte | 0.902 (0.684–0.904) | 0.2 |
| scan | csv-core-scan (native) |  | 0.807 | — |
| scan | csv-byte-record (native) |  | 0.643 | — |
| scan | fastcsv |  | 0.328 (0.275–0.330) | 11.9 |
| scan | jackson |  | 0.310 (0.298–0.311) | 284.3 |
| scan | kernel-vector | java-vector64 | 0.068 (0.042–0.068) | 10188.3 |
| strings | csv-string-record (native) |  | 0.609 | — |
| strings | kernel-native | native-avx512-svm | 0.358 (0.349–0.367) | 284.2 |
| strings | kernel-swar | java-swar | 0.321 (0.320–0.326) | 284.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.315 (0.311–0.326) | 594.4 |
| strings | jackson |  | 0.299 (0.278–0.314) | 284.3 |
| strings | truffledata-batch | java-swar | 0.286 (0.271–0.287) | 594.2 |
| strings | fastcsv |  | 0.279 (0.278–0.282) | 344.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.250 (0.247–0.256) | 826.2 |
| strings | truffledata-batch-bytes | per-byte | 0.240 (0.235–0.244) | 594.1 |
| strings | truffledata-mapped | java-swar | 0.234 (0.231–0.235) | 825.9 |
| strings | univocity |  | 0.187 (0.180–0.188) | 1245.5 |
| strings | commons-csv |  | 0.109 (0.066–0.113) | 363.4 |
| utf8 | simdutf-validate (native) |  | 87.329 | — |

## csv-comparison: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 17.779 (17.159–17.784) | 0.1 |
| index | index-native-avx512 | native-avx512-svm | 17.597 (17.585–17.764) | 0.1 |
| index | native-index-avx512 (native) | avx512 | 17.277 | — |
| index | index-native-small | native-avx512-svm-leaf | 17.191 (17.176–17.529) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 15.645 | — |
| index | index-native-avx2 | native-avx2-svm | 15.522 (15.391–15.687) | 0.1 |
| index | index-swar | java-swar | 2.493 (2.481–2.494) | 0.0 |
| index | native-index-swar (native) | swar | 2.228 | — |
| index | index-native-swar | native-swar-svm | 2.226 (2.127–2.238) | 0.1 |
| index | native-index-scalar (native) | scalar | 1.688 | — |
| index | index-scalar | java-scalar | 0.908 (0.826–0.913) | 0.0 |
| index | index-table | java-table | 0.894 (0.893–0.895) | 0.0 |
| index | index-vector | java-vector64 | 0.073 (0.073–0.075) | 29278.5 |
| scan | native-indexed-scan (native) | avx512 | 13.991 | — |
| scan | kernel-native | native-avx512-svm | 12.725 (12.541–12.882) | 0.1 |
| scan | kernel-native-small | native-avx512-svm-leaf | 12.702 (12.533–12.767) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 11.633 (11.620–11.779) | 1.3 |
| scan | truffledata-scanner-bytes | per-byte | 2.706 (2.660–2.712) | 0.6 |
| scan | truffledata-scanner | java-swar | 2.345 (2.326–2.350) | 0.8 |
| scan | kernel-swar | java-swar | 2.330 (2.325–2.400) | 0.0 |
| scan | csv-core-scan (native) |  | 1.394 | — |
| scan | csv-byte-record (native) |  | 1.042 | — |
| scan | fastcsv |  | 0.909 (0.845–0.927) | 34.2 |
| scan | jackson |  | 0.331 (0.331–0.332) | 768.9 |
| scan | kernel-vector | java-vector64 | 0.075 (0.074–0.075) | 29278.5 |
| strings | kernel-native | native-avx512-svm | 1.510 (1.099–1.534) | 768.5 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 1.050 (1.037–1.068) | 1496.9 |
| strings | csv-string-record (native) |  | 1.014 | — |
| strings | kernel-swar | java-swar | 0.971 (0.923–0.978) | 768.4 |
| strings | truffledata-batch-bytes | per-byte | 0.788 (0.784–0.791) | 1495.9 |
| strings | truffledata-batch | java-swar | 0.744 (0.742–0.750) | 1496.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.716 (0.690–0.722) | 2162.9 |
| strings | fastcsv |  | 0.653 (0.651–0.663) | 850.9 |
| strings | truffledata-mapped | java-swar | 0.555 (0.548–0.560) | 2162.1 |
| strings | jackson |  | 0.329 (0.325–0.331) | 768.9 |
| strings | univocity |  | 0.316 (0.309–0.318) | 3486.1 |
| strings | commons-csv |  | 0.149 (0.148–0.150) | 863.1 |
| utf8 | simdutf-validate (native) |  | 92.859 | — |

## csv-comparison: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.105 | — |
| index | index-native-small | native-avx512-svm-leaf | 8.073 (8.005–8.078) | 0.0 |
| index | index-native | native-avx512-svm | 8.024 (7.948–8.040) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 7.926 (7.732–8.048) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 7.176 (6.044–7.223) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 7.172 | — |
| index | index-swar | java-swar | 2.040 (2.003–2.042) | 0.0 |
| index | native-index-swar (native) | swar | 1.895 | — |
| index | index-native-swar | native-swar-svm | 1.866 (1.824–1.892) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.810 | — |
| index | index-table | java-table | 0.759 (0.756–0.769) | 0.0 |
| index | index-scalar | java-scalar | 0.563 (0.552–0.576) | 0.0 |
| index | index-vector | java-vector64 | 0.070 (0.070–0.071) | 10106.4 |
| scan | native-indexed-scan (native) | avx512 | 2.789 | — |
| scan | kernel-native | native-avx512-svm | 2.131 (2.117–2.133) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 2.096 (2.082–2.129) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 2.029 (2.011–2.032) | 0.5 |
| scan | kernel-swar | java-swar | 1.133 (0.919–1.136) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.121 (1.108–1.126) | 0.3 |
| scan | csv-core-scan (native) |  | 1.052 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.829 (0.792–0.831) | 0.3 |
| scan | csv-byte-record (native) |  | 0.783 | — |
| scan | fastcsv |  | 0.480 (0.047–0.488) | 11.6 |
| scan | jackson |  | 0.215 (0.212–0.219) | 856.3 |
| scan | kernel-vector | java-vector64 | 0.069 (0.067–0.069) | 10106.4 |
| strings | csv-string-record (native) |  | 0.468 | — |
| strings | kernel-native | native-avx512-svm | 0.434 (0.422–0.435) | 898.6 |
| strings | kernel-swar | java-swar | 0.343 (0.329–0.348) | 898.5 |
| strings | fastcsv |  | 0.297 (0.295–0.298) | 995.9 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.284 (0.283–0.290) | 1362.7 |
| strings | truffledata-batch | java-swar | 0.241 (0.236–0.247) | 1362.4 |
| strings | univocity |  | 0.238 (0.235–0.239) | 1890.2 |
| strings | truffledata-batch-bytes | per-byte | 0.234 (0.230–0.237) | 1362.4 |
| strings | jackson |  | 0.221 (0.219–0.222) | 856.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.178 (0.175–0.180) | 1592.5 |
| strings | truffledata-mapped | java-swar | 0.163 (0.162–0.163) | 1592.3 |
| strings | commons-csv |  | 0.083 (0.079–0.084) | 1015.6 |
| typed | kernel-native | native-avx512-svm | 0.796 (0.783–0.819) | 0.0 |
| typed | kernel-swar | java-swar | 0.536 (0.529–0.541) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.510 (0.495–0.512) | 448.4 |
| typed | truffledata-plan | java-swar | 0.392 (0.010–0.403) | 448.2 |
| typed | csv-typed (native) |  | 0.378 | — |
| typed | truffledata-plan-bytes | per-byte | 0.343 (0.332–0.349) | 448.1 |
| typed | fastcsv |  | 0.174 (0.163–0.176) | 1239.1 |
| typed | univocity |  | 0.149 (0.147–0.152) | 2133.4 |
| typed | jackson |  | 0.145 (0.142–0.149) | 1099.5 |
| typed | commons-csv |  | 0.069 (0.067–0.069) | 1258.8 |
| utf8 | simdutf-validate (native) |  | 78.598 | — |

## csv-comparison: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx2 | native-avx2-svm | 2.742 (2.718–2.745) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 2.724 | — |
| index | index-native-small | native-avx512-svm-leaf | 2.689 (2.682–2.692) | 0.0 |
| index | index-native | native-avx512-svm | 2.682 (2.569–2.689) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 2.676 | — |
| index | index-native-avx512 | native-avx512-svm | 2.670 (2.250–2.691) | 0.0 |
| index | index-swar | java-swar | 1.285 (1.252–1.286) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.239 (1.233–1.240) | 0.0 |
| index | native-index-swar (native) | swar | 1.200 | — |
| index | index-table | java-table | 0.501 (0.500–0.502) | 0.0 |
| index | index-scalar | java-scalar | 0.359 (0.335–0.360) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.339 | — |
| index | index-vector | java-vector64 | 0.040 (0.040–0.041) | 2372.2 |
| scan | native-indexed-scan (native) | avx512 | 0.893 | — |
| scan | kernel-native | native-avx512-svm | 0.788 (0.783–0.790) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 0.785 (0.775–0.786) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 0.684 (0.683–0.686) | 0.0 |
| scan | kernel-swar | java-swar | 0.635 (0.625–0.644) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.592 (0.592–0.593) | 0.0 |
| scan | csv-core-scan (native) |  | 0.526 | — |
| scan | csv-byte-record (native) |  | 0.465 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.434 (0.423–0.436) | 0.0 |
| scan | fastcsv |  | 0.303 (0.303–0.304) | 0.6 |
| scan | jackson |  | 0.097 (0.091–0.098) | 256.0 |
| scan | kernel-vector | java-vector64 | 0.039 (0.036–0.039) | 2372.2 |
| strings | csv-string-record (native) |  | 0.398 | — |
| strings | kernel-native | native-avx512-svm | 0.232 (0.230–0.233) | 256.0 |
| strings | kernel-swar | java-swar | 0.216 (0.212–0.217) | 256.0 |
| strings | fastcsv |  | 0.142 (0.141–0.142) | 320.6 |
| strings | univocity |  | 0.128 (0.125–0.129) | 297.7 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.127 (0.124–0.127) | 473.1 |
| strings | truffledata-batch | java-swar | 0.120 (0.106–0.123) | 473.0 |
| strings | truffledata-batch-bytes | per-byte | 0.111 (0.108–0.113) | 473.0 |
| strings | jackson |  | 0.098 (0.098–0.099) | 256.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.074 (0.073–0.074) | 420.4 |
| strings | truffledata-mapped | java-swar | 0.073 (0.072–0.073) | 420.4 |
| strings | commons-csv |  | 0.037 (0.037–0.037) | 344.3 |
| typed | kernel-native | native-avx512-svm | 0.249 (0.242–0.250) | 0.0 |
| typed | kernel-swar | java-swar | 0.233 (0.233–0.234) | 0.0 |
| typed | csv-typed (native) |  | 0.231 | — |
| typed | truffledata-plan | java-swar | 0.145 (0.144–0.145) | 147.9 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.141 (0.137–0.142) | 148.0 |
| typed | truffledata-plan-bytes | per-byte | 0.114 (0.113–0.115) | 147.9 |
| typed | fastcsv |  | 0.053 (0.046–0.054) | 440.6 |
| typed | univocity |  | 0.050 (0.049–0.050) | 417.7 |
| typed | jackson |  | 0.038 (0.038–0.038) | 376.0 |
| typed | commons-csv |  | 0.025 (0.025–0.026) | 464.3 |
| utf8 | simdutf-validate (native) |  | 12.487 | — |

## csv-comparison: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 4.955 | — |
| index | index-native-avx512 | native-avx512-svm | 4.934 (4.916–4.973) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 4.924 (4.857–4.985) | 0.0 |
| index | index-native | native-avx512-svm | 4.791 (4.754–5.005) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 4.732 (4.448–4.762) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.497 | — |
| index | index-swar | java-swar | 1.738 (1.661–1.740) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.709 (1.682–1.727) | 0.0 |
| index | native-index-swar (native) | swar | 1.612 | — |
| index | native-index-scalar (native) | scalar | 0.769 | — |
| index | index-table | java-table | 0.765 (0.757–0.770) | 0.0 |
| index | index-scalar | java-scalar | 0.574 (0.545–0.586) | 0.0 |
| index | index-vector | java-vector64 | 0.070 (0.070–0.070) | 10184.3 |
| scan | native-indexed-scan (native) | avx512 | 2.279 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.817 (1.806–1.824) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.817 (1.737–1.819) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.762 (1.752–1.764) | 0.2 |
| scan | kernel-swar | java-swar | 1.079 (1.070–1.082) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.055 (1.048–1.062) | 0.0 |
| scan | csv-core-scan (native) |  | 0.998 | — |
| scan | csv-byte-record (native) |  | 0.824 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.823 (0.780–0.840) | 0.0 |
| scan | fastcsv |  | 0.385 (0.179–0.470) | 2.6 |
| scan | jackson |  | 0.210 (0.208–0.213) | 855.8 |
| scan | kernel-vector | java-vector64 | 0.068 (0.066–0.069) | 10184.3 |
| strings | csv-string-record (native) |  | 0.475 | — |
| strings | kernel-native | native-avx512-svm | 0.424 (0.416–0.426) | 898.1 |
| strings | kernel-swar | java-swar | 0.350 (0.333–0.350) | 898.1 |
| strings | fastcsv |  | 0.292 (0.247–0.296) | 986.5 |
| strings | univocity |  | 0.270 (0.267–0.270) | 967.2 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.236 (0.232–0.239) | 1362.4 |
| strings | jackson |  | 0.216 (0.210–0.216) | 855.8 |
| strings | truffledata-batch | java-swar | 0.201 (0.195–0.205) | 1362.1 |
| strings | truffledata-batch-bytes | per-byte | 0.196 (0.140–0.203) | 1362.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.149 (0.115–0.157) | 1136.4 |
| strings | truffledata-mapped | java-swar | 0.147 (0.144–0.156) | 1136.1 |
| strings | commons-csv |  | 0.082 (0.081–0.083) | 1009.2 |
| typed | kernel-native | native-avx512-svm | 0.748 (0.733–0.748) | 0.0 |
| typed | kernel-swar | java-swar | 0.537 (0.535–0.538) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.447 (0.442–0.453) | 449.1 |
| typed | csv-typed (native) |  | 0.377 | — |
| typed | truffledata-plan | java-swar | 0.361 (0.353–0.365) | 448.9 |
| typed | truffledata-plan-bytes | per-byte | 0.320 (0.192–0.321) | 448.9 |
| typed | fastcsv |  | 0.170 (0.167–0.172) | 1229.7 |
| typed | univocity |  | 0.161 (0.160–0.163) | 1210.4 |
| typed | jackson |  | 0.140 (0.140–0.145) | 1099.0 |
| typed | commons-csv |  | 0.067 (0.066–0.068) | 1252.4 |
| utf8 | simdutf-validate (native) |  | 18.661 | — |

## csv-comparison: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 7.834 (7.754–7.850) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 7.810 (7.678–7.851) | 0.0 |
| index | index-native | native-avx512-svm | 7.721 (6.781–7.803) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 7.553 (7.103–7.602) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 7.456 | — |
| index | native-index-avx2 (native) | avx2 | 7.096 | — |
| index | index-swar | java-swar | 2.571 (2.529–2.627) | 0.0 |
| index | index-native-swar | native-swar-svm | 2.190 (2.174–2.199) | 0.0 |
| index | native-index-swar (native) | swar | 2.162 | — |
| index | native-index-scalar (native) | scalar | 1.097 | — |
| index | index-table | java-table | 0.990 (0.954–1.008) | 0.0 |
| index | index-scalar | java-scalar | 0.680 (0.656–0.705) | 0.0 |
| index | index-vector | java-vector64 | 0.083 (0.083–0.084) | 10225.2 |
| scan | native-indexed-scan (native) | avx512 | 6.669 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 5.877 (5.819–5.903) | 0.0 |
| scan | kernel-native | native-avx512-svm | 5.771 (5.603–5.860) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 5.505 (4.790–5.530) | 0.2 |
| scan | kernel-swar | java-swar | 2.365 (2.306–2.378) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.318 (2.302–2.321) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 1.096 (1.089–1.098) | 0.0 |
| scan | csv-core-scan (native) |  | 1.038 | — |
| scan | csv-byte-record (native) |  | 0.880 | — |
| scan | fastcsv |  | 0.398 (0.341–0.400) | 2.8 |
| scan | jackson |  | 0.377 (0.359–0.378) | 283.1 |
| scan | kernel-vector | java-vector64 | 0.082 (0.082–0.084) | 10225.2 |
| strings | csv-string-record (native) |  | 0.808 | — |
| strings | kernel-native | native-avx512-svm | 0.445 (0.443–0.446) | 283.1 |
| strings | kernel-swar | java-swar | 0.390 (0.387–0.399) | 283.1 |
| strings | jackson |  | 0.381 (0.372–0.382) | 283.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.363 (0.350–0.369) | 593.4 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.342 (0.341–0.345) | 366.5 |
| strings | fastcsv |  | 0.327 (0.324–0.337) | 333.9 |
| strings | truffledata-batch | java-swar | 0.312 (0.266–0.323) | 593.2 |
| strings | truffledata-mapped | java-swar | 0.311 (0.302–0.313) | 366.2 |
| strings | truffledata-batch-bytes | per-byte | 0.287 (0.085–0.287) | 593.2 |
| strings | univocity |  | 0.235 (0.231–0.246) | 314.4 |
| strings | commons-csv |  | 0.137 (0.135–0.139) | 356.5 |
| utf8 | simdutf-validate (native) |  | 35.578 | — |

## csv-comparison: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-svm-leaf | 12.547 (12.534–12.599) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 12.523 (12.298–12.597) | 0.0 |
| index | index-native | native-avx512-svm | 12.501 (12.321–12.525) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 12.177 | — |
| index | native-index-avx512 (native) | avx512 | 12.016 | — |
| index | index-native-avx2 | native-avx2-svm | 11.669 (11.557–11.835) | 0.0 |
| index | index-swar | java-swar | 2.486 (2.459–2.489) | 0.0 |
| index | native-index-swar (native) | swar | 2.251 | — |
| index | index-native-swar | native-swar-svm | 2.245 (2.244–2.247) | 0.0 |
| index | native-index-scalar (native) | scalar | 1.698 | — |
| index | index-scalar | java-scalar | 0.900 (0.877–0.903) | 0.0 |
| index | index-table | java-table | 0.884 (0.872–0.902) | 0.0 |
| index | index-vector | java-vector64 | 0.076 (0.074–0.076) | 29345.7 |
| scan | native-indexed-scan (native) | avx512 | 10.421 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 9.458 (9.048–9.671) | 0.0 |
| scan | kernel-native | native-avx512-svm | 9.317 (1.819–9.720) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 8.910 (8.778–8.956) | 0.5 |
| scan | truffledata-scanner-bytes | per-byte | 2.769 (2.681–2.789) | 0.0 |
| scan | kernel-swar | java-swar | 2.346 (2.326–2.357) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.272 (2.254–2.296) | 0.0 |
| scan | csv-core-scan (native) |  | 1.487 | — |
| scan | csv-byte-record (native) |  | 1.087 | — |
| scan | fastcsv |  | 0.906 (0.891–0.906) | 8.0 |
| scan | jackson |  | 0.333 (0.306–0.333) | 768.0 |
| scan | kernel-vector | java-vector64 | 0.076 (0.076–0.077) | 29345.7 |
| strings | kernel-native | native-avx512-svm | 1.454 (1.437–1.460) | 768.0 |
| strings | csv-string-record (native) |  | 1.036 | — |
| strings | kernel-swar | java-swar | 0.963 (0.946–0.967) | 767.9 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.796 (0.791–0.798) | 844.0 |
| strings | truffledata-batch-bytes | per-byte | 0.744 (0.097–0.771) | 1494.5 |
| strings | truffledata-batch | java-swar | 0.643 (0.332–0.726) | 1494.5 |
| strings | fastcsv |  | 0.638 (0.613–0.653) | 824.0 |
| strings | truffledata-mapped | java-swar | 0.608 (0.602–0.616) | 843.2 |
| strings | univocity |  | 0.373 (0.356–0.373) | 813.1 |
| strings | jackson |  | 0.317 (0.299–0.330) | 768.0 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.213 (0.062–0.978) | 1495.2 |
| strings | commons-csv |  | 0.148 (0.144–0.148) | 844.0 |
| utf8 | simdutf-validate (native) |  | 33.839 | — |

## csv-comparison-v3: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 8.794 (8.720–8.876) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 8.754 | — |
| index | index-native-avx512 | native-avx512-svm | 8.719 (8.639–8.793) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 8.704 (8.615–8.829) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.748 | — |
| index | index-native-avx2 | native-avx2-svm | 6.739 (6.658–6.759) | 0.0 |
| index | index-vector | java-vector256 | 5.285 (5.268–5.337) | 0.0 |
| index | index-swar | java-swar | 2.232 (2.206–2.245) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.762 (0.657–1.766) | 0.0 |
| index | native-index-swar (native) | swar | 1.751 | — |
| index | index-table | java-table | 0.866 (0.865–0.869) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.839 | — |
| index | index-scalar | java-scalar | 0.627 (0.625–0.628) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.630 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.666 (1.658–1.673) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.664 (1.645–1.678) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.509 (1.306–1.517) | 0.1 |
| scan | kernel-vector | java-vector256 | 1.483 (1.477–1.485) | 0.0 |
| scan | csv-core-scan (native) |  | 1.129 | — |
| scan | kernel-swar | java-swar | 1.077 (1.067–1.077) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.012 (1.007–1.014) | 0.1 |
| scan | csv-byte-record (native) |  | 0.879 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.779 (0.654–0.784) | 0.0 |
| scan | fastcsv |  | 0.598 (0.584–0.600) | 2.6 |
| scan | jackson |  | 0.195 (0.187–0.195) | 256.0 |
| strings | csv-string-record (native) |  | 0.784 | — |
| strings | kernel-native | native-avx512-svm | 0.425 (0.416–0.435) | 256.0 |
| strings | kernel-swar | java-swar | 0.367 (0.365–0.370) | 256.0 |
| strings | fastcsv |  | 0.306 (0.266–0.320) | 322.7 |
| strings | univocity |  | 0.242 (0.230–0.248) | 505.8 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.234 (0.197–0.236) | 404.3 |
| strings | truffledata-batch | java-swar | 0.212 (0.212–0.212) | 404.3 |
| strings | truffledata-batch-bytes | per-byte | 0.205 (0.203–0.205) | 404.3 |
| strings | jackson |  | 0.195 (0.195–0.201) | 256.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.129 (0.114–0.132) | 404.4 |
| strings | truffledata-mapped | java-swar | 0.117 (0.100–0.121) | 404.3 |
| strings | commons-csv |  | 0.081 (0.078–0.082) | 345.6 |
| typed | kernel-native | native-avx512-svm | 0.472 (0.471–0.476) | 0.0 |
| typed | csv-typed (native) |  | 0.470 | — |
| typed | kernel-swar | java-swar | 0.401 (0.399–0.401) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.345 (0.345–0.345) | 88.6 |
| typed | truffledata-plan | java-swar | 0.299 (0.296–0.301) | 88.5 |
| typed | truffledata-plan-bytes | per-byte | 0.242 (0.232–0.243) | 88.5 |
| typed | fastcsv |  | 0.115 (0.115–0.117) | 442.7 |
| typed | univocity |  | 0.103 (0.100–0.107) | 625.8 |
| typed | jackson |  | 0.099 (0.098–0.099) | 376.0 |
| typed | commons-csv |  | 0.055 (0.055–0.055) | 465.6 |
| utf8 | simdutf-validate (native) |  | 108.284 | — |

## csv-comparison-v3: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-svm-leaf | 10.594 (10.476–10.598) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 10.522 | — |
| index | index-native | native-avx512-svm | 10.514 (10.362–10.553) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 10.506 (10.320–10.634) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 10.301 | — |
| index | index-native-avx2 | native-avx2-svm | 10.131 (10.006–10.214) | 0.0 |
| index | index-vector | java-vector256 | 5.967 (5.918–6.039) | 0.0 |
| index | index-swar | java-swar | 2.462 (2.458–2.472) | 0.0 |
| index | native-index-swar (native) | swar | 1.986 | — |
| index | index-native-swar | native-swar-svm | 1.983 (0.576–1.986) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.902 | — |
| index | index-table | java-table | 0.836 (0.832–0.840) | 0.0 |
| index | index-scalar | java-scalar | 0.555 (0.552–0.556) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 8.295 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 6.874 (6.857–6.896) | 0.0 |
| scan | kernel-native | native-avx512-svm | 6.861 (6.773–6.931) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 6.310 (5.493–6.318) | 0.4 |
| scan | kernel-vector | java-vector256 | 4.525 (4.398–4.601) | 0.0 |
| scan | kernel-swar | java-swar | 2.184 (2.184–2.188) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.124 (2.095–2.131) | 0.3 |
| scan | truffledata-scanner-bytes | per-byte | 0.902 (0.901–0.907) | 0.2 |
| scan | csv-core-scan (native) |  | 0.821 | — |
| scan | csv-byte-record (native) |  | 0.656 | — |
| scan | fastcsv |  | 0.374 (0.354–0.378) | 11.9 |
| scan | jackson |  | 0.336 (0.335–0.339) | 284.3 |
| strings | csv-string-record (native) |  | 0.578 | — |
| strings | kernel-native | native-avx512-svm | 0.412 (0.410–0.412) | 284.2 |
| strings | kernel-swar | java-swar | 0.365 (0.364–0.366) | 284.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.348 (0.341–0.361) | 594.4 |
| strings | jackson |  | 0.337 (0.333–0.341) | 284.3 |
| strings | fastcsv |  | 0.337 (0.059–0.339) | 344.1 |
| strings | truffledata-batch | java-swar | 0.312 (0.308–0.315) | 594.2 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.278 (0.271–0.279) | 826.2 |
| strings | truffledata-batch-bytes | per-byte | 0.262 (0.236–0.265) | 594.1 |
| strings | truffledata-mapped | java-swar | 0.248 (0.246–0.254) | 825.9 |
| strings | univocity |  | 0.214 (0.213–0.214) | 1245.5 |
| strings | commons-csv |  | 0.121 (0.120–0.121) | 363.4 |
| utf8 | simdutf-validate (native) |  | 103.896 | — |

## csv-comparison-v3: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 17.831 | — |
| index | index-native-small | native-avx512-svm-leaf | 17.812 (17.651–17.896) | 0.1 |
| index | index-native | native-avx512-svm | 17.652 (17.188–17.809) | 0.1 |
| index | index-native-avx512 | native-avx512-svm | 17.432 (17.206–17.488) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 16.111 | — |
| index | index-native-avx2 | native-avx2-svm | 16.039 (15.630–16.067) | 0.1 |
| index | index-vector | java-vector256 | 7.164 (7.087–7.168) | 0.0 |
| index | index-swar | java-swar | 2.505 (0.923–2.521) | 0.0 |
| index | native-index-swar (native) | swar | 2.281 | — |
| index | native-index-scalar (native) | scalar | 1.723 | — |
| index | index-scalar | java-scalar | 0.915 (0.906–0.916) | 0.0 |
| index | index-table | java-table | 0.904 (0.901–0.908) | 0.0 |
| index | index-native-swar | native-swar-svm | 0.589 (0.586–2.183) | 0.1 |
| scan | native-indexed-scan (native) | avx512 | 14.507 | — |
| scan | kernel-native | native-avx512-svm | 12.719 (12.648–12.796) | 0.1 |
| scan | kernel-native-small | native-avx512-svm-leaf | 12.665 (12.344–12.799) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 11.618 (11.537–11.647) | 1.3 |
| scan | kernel-vector | java-vector256 | 6.181 (6.050–6.209) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 2.703 (2.602–2.730) | 0.6 |
| scan | kernel-swar | java-swar | 2.364 (1.103–2.398) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.340 (2.278–2.363) | 0.8 |
| scan | csv-core-scan (native) |  | 1.500 | — |
| scan | fastcsv |  | 1.286 (1.279–1.306) | 34.2 |
| scan | csv-byte-record (native) |  | 1.055 | — |
| scan | jackson |  | 0.345 (0.339–0.359) | 768.9 |
| strings | kernel-native | native-avx512-svm | 3.125 (3.068–3.169) | 768.5 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 1.626 (1.611–1.638) | 1496.9 |
| strings | kernel-swar | java-swar | 1.406 (1.404–1.413) | 768.4 |
| strings | truffledata-batch-bytes | per-byte | 1.056 (1.016–1.060) | 1495.9 |
| strings | fastcsv |  | 1.048 (1.045–1.057) | 850.9 |
| strings | csv-string-record (native) |  | 1.042 | — |
| strings | truffledata-batch | java-swar | 0.978 (0.974–0.980) | 1496.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.921 (0.910–0.925) | 2162.9 |
| strings | truffledata-mapped | java-swar | 0.663 (0.662–0.665) | 2162.1 |
| strings | univocity |  | 0.386 (0.379–0.389) | 3486.1 |
| strings | jackson |  | 0.342 (0.335–0.360) | 768.9 |
| strings | commons-csv |  | 0.169 (0.168–0.169) | 863.1 |
| utf8 | simdutf-validate (native) |  | 109.337 | — |

## csv-comparison-v3: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 8.091 (8.062–8.091) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 8.078 (8.014–8.081) | 0.0 |
| index | index-native | native-avx512-svm | 8.030 (8.009–8.067) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 7.902 | — |
| index | native-index-avx2 (native) | avx2 | 6.908 | — |
| index | index-native-avx2 | native-avx2-svm | 6.903 (6.326–7.151) | 0.0 |
| index | index-vector | java-vector256 | 3.962 (3.919–4.021) | 0.0 |
| index | index-swar | java-swar | 2.077 (2.075–2.077) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.873 (0.567–1.904) | 0.0 |
| index | native-index-swar (native) | swar | 1.716 | — |
| index | index-table | java-table | 0.774 (0.774–0.776) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.771 | — |
| index | index-scalar | java-scalar | 0.543 (0.530–0.547) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.795 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 2.118 (1.802–2.119) | 0.0 |
| scan | kernel-native | native-avx512-svm | 2.102 (2.006–2.138) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 2.028 (2.005–2.050) | 0.5 |
| scan | kernel-vector | java-vector256 | 1.623 (1.616–1.628) | 0.0 |
| scan | kernel-swar | java-swar | 1.160 (1.149–1.167) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.156 (1.152–1.157) | 0.3 |
| scan | csv-core-scan (native) |  | 1.028 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.840 (0.830–0.845) | 0.3 |
| scan | csv-byte-record (native) |  | 0.730 | — |
| scan | fastcsv |  | 0.457 (0.434–0.491) | 11.6 |
| scan | jackson |  | 0.220 (0.219–0.223) | 856.3 |
| strings | kernel-native | native-avx512-svm | 0.441 (0.434–0.441) | 898.6 |
| strings | csv-string-record (native) |  | 0.440 | — |
| strings | kernel-swar | java-swar | 0.358 (0.335–0.358) | 898.5 |
| strings | fastcsv |  | 0.331 (0.323–0.331) | 995.9 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.294 (0.223–0.295) | 1362.7 |
| strings | truffledata-batch | java-swar | 0.255 (0.234–0.256) | 1362.4 |
| strings | univocity |  | 0.249 (0.247–0.253) | 1890.2 |
| strings | truffledata-batch-bytes | per-byte | 0.235 (0.235–0.240) | 1362.4 |
| strings | jackson |  | 0.228 (0.226–0.230) | 856.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.157 (0.156–0.160) | 1592.5 |
| strings | truffledata-mapped | java-swar | 0.147 (0.146–0.152) | 1592.3 |
| strings | commons-csv |  | 0.087 (0.087–0.088) | 1015.6 |
| typed | kernel-native | native-avx512-svm | 0.818 (0.805–0.840) | 0.0 |
| typed | kernel-swar | java-swar | 0.567 (0.555–0.567) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.551 (0.550–0.564) | 448.4 |
| typed | truffledata-plan | java-swar | 0.431 (0.422–0.434) | 448.2 |
| typed | truffledata-plan-bytes | per-byte | 0.360 (0.352–0.361) | 448.1 |
| typed | csv-typed (native) |  | 0.341 | — |
| typed | fastcsv |  | 0.178 (0.171–0.180) | 1239.1 |
| typed | univocity |  | 0.154 (0.153–0.154) | 2133.4 |
| typed | jackson |  | 0.151 (0.150–0.152) | 1099.5 |
| typed | commons-csv |  | 0.070 (0.070–0.071) | 1258.8 |
| utf8 | simdutf-validate (native) |  | 79.133 | — |

## csv-comparison-v3: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 8.565 (7.455–8.593) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 8.534 | — |
| index | index-native-small | native-avx512-svm-leaf | 8.529 (8.131–8.570) | 0.0 |
| index | index-native | native-avx512-svm | 8.464 (7.358–8.526) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 6.649 (6.590–6.658) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.616 | — |
| index | index-vector | java-vector256 | 5.253 (4.945–5.725) | 0.0 |
| index | index-swar | java-swar | 2.131 (2.121–2.301) | 0.0 |
| index | native-index-swar (native) | swar | 1.756 | — |
| index | index-native-swar | native-swar-svm | 1.422 (0.527–1.710) | 0.0 |
| index | index-table | java-table | 0.855 (0.841–0.884) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.847 | — |
| index | index-scalar | java-scalar | 0.624 (0.604–0.633) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.699 | — |
| scan | kernel-native | native-avx512-svm | 1.722 (1.068–1.730) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.702 (1.641–1.718) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.555 (1.452–1.559) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.450 (1.407–1.493) | 0.0 |
| scan | csv-core-scan (native) |  | 1.118 | — |
| scan | kernel-swar | java-swar | 1.102 (1.089–1.106) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.039 (1.003–1.051) | 0.0 |
| scan | csv-byte-record (native) |  | 0.884 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.802 (0.739–0.806) | 0.0 |
| scan | fastcsv |  | 0.605 (0.588–0.605) | 0.6 |
| scan | jackson |  | 0.189 (0.188–0.191) | 256.0 |
| strings | csv-string-record (native) |  | 0.794 | — |
| strings | kernel-native | native-avx512-svm | 0.439 (0.438–0.444) | 256.0 |
| strings | kernel-swar | java-swar | 0.388 (0.373–0.390) | 256.0 |
| strings | fastcsv |  | 0.332 (0.327–0.336) | 320.6 |
| strings | univocity |  | 0.282 (0.255–0.285) | 297.7 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.193 (0.182–0.200) | 473.1 |
| strings | truffledata-batch | java-swar | 0.192 (0.167–0.194) | 473.0 |
| strings | jackson |  | 0.192 (0.188–0.196) | 256.0 |
| strings | truffledata-batch-bytes | per-byte | 0.169 (0.127–0.184) | 473.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.121 (0.082–0.124) | 420.4 |
| strings | truffledata-mapped | java-swar | 0.119 (0.111–0.122) | 420.4 |
| strings | commons-csv |  | 0.080 (0.075–0.081) | 344.3 |
| typed | kernel-native | native-avx512-svm | 0.485 (0.481–0.490) | 0.0 |
| typed | csv-typed (native) |  | 0.484 | — |
| typed | kernel-swar | java-swar | 0.397 (0.380–0.418) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.313 (0.307–0.318) | 148.0 |
| typed | truffledata-plan | java-swar | 0.286 (0.284–0.290) | 147.9 |
| typed | truffledata-plan-bytes | per-byte | 0.226 (0.213–0.236) | 147.9 |
| typed | fastcsv |  | 0.110 (0.108–0.116) | 440.6 |
| typed | univocity |  | 0.110 (0.106–0.112) | 417.7 |
| typed | jackson |  | 0.097 (0.096–0.100) | 376.0 |
| typed | commons-csv |  | 0.051 (0.050–0.055) | 464.3 |
| utf8 | simdutf-validate (native) |  | 34.757 | — |

## csv-comparison-v3: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 4.985 (4.881–4.989) | 0.0 |
| index | index-native | native-avx512-svm | 4.937 (4.797–4.960) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.870 | — |
| index | index-native-avx2 | native-avx2-svm | 4.748 (4.620–4.836) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 4.692 (4.613–4.940) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.635 | — |
| index | index-vector | java-vector256 | 3.240 (3.211–3.251) | 0.0 |
| index | index-swar | java-swar | 1.807 (1.784–1.819) | 0.0 |
| index | native-index-swar (native) | swar | 1.677 | — |
| index | index-native-swar | native-swar-svm | 1.109 (0.550–1.581) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.773 | — |
| index | index-table | java-table | 0.771 (0.760–0.779) | 0.0 |
| index | index-scalar | java-scalar | 0.548 (0.540–0.549) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.103 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.821 (1.817–1.829) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.811 (1.752–1.833) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.776 (1.653–1.779) | 0.2 |
| scan | kernel-vector | java-vector256 | 1.516 (1.490–1.537) | 0.0 |
| scan | kernel-swar | java-swar | 1.110 (1.100–1.116) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.089 (1.086–1.095) | 0.0 |
| scan | csv-core-scan (native) |  | 1.014 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.841 (0.791–0.843) | 0.0 |
| scan | csv-byte-record (native) |  | 0.689 | — |
| scan | fastcsv |  | 0.472 (0.441–0.479) | 2.6 |
| scan | jackson |  | 0.201 (0.189–0.206) | 855.8 |
| strings | csv-string-record (native) |  | 0.485 | — |
| strings | kernel-native | native-avx512-svm | 0.441 (0.409–0.446) | 898.1 |
| strings | kernel-swar | java-swar | 0.365 (0.357–0.368) | 898.1 |
| strings | fastcsv |  | 0.319 (0.314–0.320) | 986.5 |
| strings | univocity |  | 0.270 (0.201–0.272) | 967.2 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.237 (0.232–0.240) | 1362.4 |
| strings | jackson |  | 0.223 (0.208–0.227) | 855.8 |
| strings | truffledata-batch | java-swar | 0.209 (0.181–0.213) | 1362.1 |
| strings | truffledata-batch-bytes | per-byte | 0.192 (0.189–0.205) | 1362.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.145 (0.144–0.146) | 1136.4 |
| strings | truffledata-mapped | java-swar | 0.143 (0.140–0.145) | 1136.1 |
| strings | commons-csv |  | 0.084 (0.082–0.085) | 1009.2 |
| typed | kernel-native | native-avx512-svm | 0.784 (0.764–0.787) | 0.0 |
| typed | kernel-swar | java-swar | 0.563 (0.558–0.565) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.485 (0.463–0.487) | 449.1 |
| typed | truffledata-plan | java-swar | 0.395 (0.374–0.395) | 448.9 |
| typed | csv-typed (native) |  | 0.390 | — |
| typed | truffledata-plan-bytes | per-byte | 0.322 (0.115–0.334) | 448.9 |
| typed | fastcsv |  | 0.180 (0.175–0.181) | 1229.7 |
| typed | univocity |  | 0.166 (0.166–0.167) | 1210.4 |
| typed | jackson |  | 0.144 (0.131–0.145) | 1099.0 |
| typed | commons-csv |  | 0.070 (0.068–0.070) | 1252.4 |
| utf8 | simdutf-validate (native) |  | 17.916 | — |

## csv-comparison-v3: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 5.016 | — |
| index | index-native-avx2 | native-avx2-svm | 4.972 (4.737–4.975) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 4.799 (4.571–4.801) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 4.794 (4.777–4.811) | 0.0 |
| index | index-native | native-avx512-svm | 4.782 (4.683–4.803) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.774 | — |
| index | index-vector | java-vector256 | 3.353 (3.335–3.356) | 0.0 |
| index | native-index-swar (native) | swar | 1.437 | — |
| index | index-swar | java-swar | 1.403 (1.397–1.412) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.781 | — |
| index | index-native-swar | native-swar-svm | 0.710 (0.661–1.419) | 0.0 |
| index | index-table | java-table | 0.566 (0.546–0.566) | 0.0 |
| index | index-scalar | java-scalar | 0.356 (0.355–0.357) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 3.873 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 3.423 (3.419–3.433) | 0.0 |
| scan | kernel-native | native-avx512-svm | 3.412 (3.307–3.425) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 3.201 (2.986–3.226) | 0.2 |
| scan | kernel-vector | java-vector256 | 2.634 (2.586–2.649) | 0.0 |
| scan | kernel-swar | java-swar | 1.245 (1.242–1.267) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.235 (1.232–1.242) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 0.677 (0.667–0.680) | 0.0 |
| scan | csv-core-scan (native) |  | 0.566 | — |
| scan | csv-byte-record (native) |  | 0.527 | — |
| scan | fastcsv |  | 0.251 (0.233–0.256) | 2.8 |
| scan | jackson |  | 0.224 (0.209–0.224) | 283.1 |
| strings | csv-string-record (native) |  | 0.479 | — |
| strings | kernel-native | native-avx512-svm | 0.287 (0.283–0.287) | 283.1 |
| strings | kernel-swar | java-swar | 0.244 (0.228–0.246) | 283.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.237 (0.236–0.237) | 593.4 |
| strings | fastcsv |  | 0.225 (0.216–0.227) | 333.9 |
| strings | jackson |  | 0.224 (0.224–0.225) | 283.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.205 (0.199–0.211) | 366.5 |
| strings | truffledata-mapped | java-swar | 0.179 (0.177–0.187) | 366.2 |
| strings | truffledata-batch | java-swar | 0.169 (0.148–0.202) | 593.2 |
| strings | truffledata-batch-bytes | per-byte | 0.165 (0.063–0.181) | 593.2 |
| strings | univocity |  | 0.149 (0.144–0.152) | 314.4 |
| strings | commons-csv |  | 0.077 (0.071–0.078) | 356.5 |
| utf8 | simdutf-validate (native) |  | 35.568 | — |

## csv-comparison-v3: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 12.477 (12.470–12.521) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 12.392 | — |
| index | index-native-small | native-avx512-svm-leaf | 12.389 (12.159–12.488) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 12.131 | — |
| index | index-native-avx512 | native-avx512-svm | 12.031 (11.955–12.374) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 11.892 (11.830–11.981) | 0.0 |
| index | index-vector | java-vector256 | 6.915 (6.016–6.968) | 0.0 |
| index | index-swar | java-swar | 2.437 (0.289–2.483) | 0.0 |
| index | native-index-swar (native) | swar | 2.254 | — |
| index | index-native-swar | native-swar-svm | 2.204 (1.996–2.233) | 0.0 |
| index | native-index-scalar (native) | scalar | 1.688 | — |
| index | index-scalar | java-scalar | 0.913 (0.908–0.923) | 0.0 |
| index | index-table | java-table | 0.901 (0.884–0.906) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 10.593 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 9.511 (9.269–9.573) | 0.0 |
| scan | kernel-native | native-avx512-svm | 9.505 (9.312–9.642) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 8.895 (8.836–8.919) | 0.5 |
| scan | kernel-vector | java-vector256 | 5.754 (5.741–5.755) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 2.783 (2.780–2.789) | 0.0 |
| scan | kernel-swar | java-swar | 2.339 (2.190–2.345) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.305 (2.126–2.316) | 0.0 |
| scan | csv-core-scan (native) |  | 1.487 | — |
| scan | fastcsv |  | 1.245 (1.234–1.248) | 8.0 |
| scan | csv-byte-record (native) |  | 1.097 | — |
| scan | jackson |  | 0.359 (0.359–0.361) | 768.0 |
| strings | kernel-native | native-avx512-svm | 2.922 (2.898–2.924) | 768.0 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 1.385 (1.377–1.420) | 1495.2 |
| strings | kernel-swar | java-swar | 1.339 (1.288–1.390) | 767.9 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 1.068 (1.038–1.088) | 844.0 |
| strings | fastcsv |  | 1.028 (1.028–1.032) | 824.0 |
| strings | csv-string-record (native) |  | 1.021 | — |
| strings | truffledata-batch-bytes | per-byte | 0.989 (0.115–0.993) | 1494.5 |
| strings | truffledata-batch | java-swar | 0.902 (0.890–0.929) | 1494.5 |
| strings | truffledata-mapped | java-swar | 0.753 (0.683–0.756) | 843.2 |
| strings | univocity |  | 0.460 (0.438–0.461) | 813.1 |
| strings | jackson |  | 0.361 (0.352–0.364) | 768.0 |
| strings | commons-csv |  | 0.165 (0.165–0.169) | 844.0 |
| utf8 | simdutf-validate (native) |  | 34.404 | — |

## csv_jvm: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-ffm | 8.799 (8.739–8.853) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 8.234 | — |
| index | index-native-avx512 | native-avx512-ffm | 6.849 (6.442–6.880) | 0.0 |
| index | index-native | native-avx512-ffm | 6.814 (6.544–6.858) | 0.0 |
| index | index-vector | java-vector512 | 6.634 (0.704–6.668) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.523 | — |
| index | index-native-avx2 | native-avx2-ffm | 5.406 (4.823–5.447) | 0.0 |
| index | index-swar | java-swar | 2.573 (2.556–2.602) | 0.0 |
| index | native-index-swar (native) | swar | 1.691 | — |
| index | index-native-swar | native-swar-ffm | 1.630 (1.607–1.643) | 0.0 |
| index | index-scalar | java-scalar | 1.327 (1.322–1.334) | 0.0 |
| index | index-table | java-table | 0.980 (0.978–0.987) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.815 | — |
| scan | kernel-native-small | native-avx512-ffm | 2.972 (2.934–3.004) | 0.0 |
| scan | kernel-vector | java-vector512 | 2.834 (2.777–2.914) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 2.818 (2.671–2.934) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-ffm | 2.713 (2.540–2.880) | 0.2 |
| scan | native-indexed-scan (native) | avx512 | 2.477 | — |
| scan | kernel-swar | java-swar | 1.716 (1.696–1.721) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.493 (0.153–1.539) | 0.1 |
| scan | fastcsv |  | 1.117 (0.964–1.132) | 2.0 |
| scan | csv-core-scan (native) |  | 1.051 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.975 (0.945–1.015) | 0.0 |
| scan | csv-byte-record (native) |  | 0.855 | — |
| scan | jackson |  | 0.294 (0.288–0.298) | 384.1 |
| strings | kernel-native | native-avx512-ffm | 0.889 (0.875–0.899) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.743 (0.736–0.749) | 124.5 |
| strings | kernel-swar | java-swar | 0.734 (0.724–0.739) | 0.0 |
| strings | csv-string-record (native) |  | 0.731 | — |
| strings | truffledata-batch | java-swar | 0.587 (0.042–0.592) | 124.3 |
| strings | truffledata-batch-bytes | per-byte | 0.548 (0.533–0.549) | 124.3 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.407 (0.399–0.412) | 124.5 |
| strings | univocity |  | 0.397 (0.390–0.399) | 641.8 |
| strings | truffledata-mapped | java-swar | 0.345 (0.343–0.347) | 124.4 |
| strings | jackson |  | 0.341 (0.298–0.407) | 384.1 |
| strings | fastcsv |  | 0.318 (0.175–0.469) | 466.1 |
| strings | commons-csv |  | 0.188 (0.185–0.189) | 481.3 |
| typed | kernel-native | native-avx512-ffm | 0.702 (0.693–0.702) | 0.0 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.602 (0.599–0.608) | 64.7 |
| typed | kernel-swar | java-swar | 0.595 (0.593–0.603) | 0.0 |
| typed | truffledata-plan | java-swar | 0.506 (0.487–0.512) | 64.5 |
| typed | csv-typed (native) |  | 0.453 | — |
| typed | truffledata-plan-bytes | per-byte | 0.440 (0.437–0.455) | 64.5 |
| typed | fastcsv |  | 0.229 (0.227–0.232) | 538.1 |
| typed | univocity |  | 0.217 (0.212–0.221) | 713.8 |
| typed | jackson |  | 0.202 (0.200–0.208) | 456.1 |
| typed | commons-csv |  | 0.135 (0.131–0.136) | 553.3 |
| utf8 | simdutf-validate (native) |  | 86.415 | — |

## csv_jvm: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 10.636 | — |
| index | index-native-small | native-avx512-ffm | 10.483 (0.678–10.583) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 9.926 | — |
| index | index-native-avx512 | native-avx512-ffm | 8.873 (8.797–8.878) | 0.0 |
| index | index-native | native-avx512-ffm | 8.835 (8.325–8.847) | 0.0 |
| index | index-vector | java-vector512 | 8.658 (8.585–8.731) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 8.511 (8.445–8.534) | 0.0 |
| index | index-swar | java-swar | 2.733 (2.555–2.806) | 0.0 |
| index | native-index-swar (native) | swar | 2.007 | — |
| index | index-native-swar | native-swar-ffm | 1.905 (1.878–1.910) | 0.0 |
| index | index-table | java-table | 0.960 (0.959–0.965) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.903 | — |
| index | index-scalar | java-scalar | 0.810 (0.773–0.818) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 8.277 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 8.138 (7.717–8.139) | 0.7 |
| scan | kernel-native-small | native-avx512-ffm | 8.134 (0.369–8.167) | 0.1 |
| scan | kernel-native | native-avx512-ffm | 7.076 (6.791–7.212) | 0.0 |
| scan | kernel-vector | java-vector512 | 7.035 (0.406–7.063) | 0.0 |
| scan | kernel-swar | java-swar | 2.586 (2.571–2.600) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.568 (2.541–2.569) | 0.2 |
| scan | truffledata-scanner-bytes | per-byte | 1.055 (1.045–1.094) | 0.2 |
| scan | csv-core-scan (native) |  | 0.824 | — |
| scan | csv-byte-record (native) |  | 0.644 | — |
| scan | jackson |  | 0.420 (0.406–0.426) | 332.3 |
| scan | fastcsv |  | 0.407 (0.397–0.429) | 9.1 |
| strings | kernel-native | native-avx512-ffm | 0.694 (0.687–0.694) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.649 (0.643–0.661) | 287.0 |
| strings | csv-string-record (native) |  | 0.606 | — |
| strings | kernel-swar | java-swar | 0.585 (0.585–0.595) | 0.0 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.538 (0.411–0.544) | 518.7 |
| strings | truffledata-batch | java-swar | 0.535 (0.532–0.540) | 286.2 |
| strings | truffledata-mapped | java-swar | 0.462 (0.456–0.463) | 517.9 |
| strings | truffledata-batch-bytes | per-byte | 0.419 (0.393–0.437) | 286.1 |
| strings | jackson |  | 0.414 (0.398–0.420) | 332.3 |
| strings | fastcsv |  | 0.370 (0.367–0.374) | 405.4 |
| strings | univocity |  | 0.341 (0.329–0.345) | 1301.5 |
| strings | commons-csv |  | 0.268 (0.245–0.269) | 418.1 |
| utf8 | simdutf-validate (native) |  | 102.659 | — |

## csv_jvm: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 21.919 | — |
| index | index-native-small | native-avx512-ffm | 21.584 (21.496–21.912) | 0.2 |
| index | native-index-avx2 (native) | avx2 | 19.668 | — |
| index | index-native | native-avx512-ffm | 16.424 (15.534–16.467) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 16.422 (16.336–16.537) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 14.719 (10.838–15.037) | 0.0 |
| index | index-vector | java-vector512 | 11.223 (9.753–11.465) | 0.0 |
| index | index-swar | java-swar | 3.532 (3.163–3.558) | 0.0 |
| index | index-scalar | java-scalar | 3.049 (3.046–3.051) | 0.0 |
| index | native-index-swar (native) | swar | 2.783 | — |
| index | index-native-swar | native-swar-ffm | 2.620 (2.615–2.635) | 0.0 |
| index | native-index-scalar (native) | scalar | 2.101 | — |
| index | index-table | java-table | 1.224 (1.199–1.225) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 18.363 (18.274–18.609) | 0.2 |
| scan | truffledata-scanner-native | native-avx512-ffm | 17.234 (15.970–17.852) | 2.0 |
| scan | native-indexed-scan (native) | avx512 | 17.064 | — |
| scan | kernel-native | native-avx512-ffm | 13.901 (13.429–13.998) | 0.0 |
| scan | kernel-vector | java-vector512 | 10.183 (9.770–10.221) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 5.654 (5.641–5.706) | 0.5 |
| scan | kernel-swar | java-swar | 3.420 (3.257–3.466) | 0.0 |
| scan | truffledata-scanner | java-swar | 3.359 (3.206–3.527) | 0.4 |
| scan | fastcsv |  | 2.338 (2.259–2.481) | 26.2 |
| scan | csv-core-scan (native) |  | 1.796 | — |
| scan | csv-byte-record (native) |  | 1.290 | — |
| scan | jackson |  | 0.464 (0.442–0.477) | 833.0 |
| strings | kernel-native | native-avx512-ffm | 6.615 (0.481–6.792) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 5.424 (5.103–5.517) | 706.5 |
| strings | truffledata-mapped-native | native-avx512-ffm | 3.127 (2.943–3.164) | 1372.3 |
| strings | truffledata-batch-bytes | per-byte | 2.885 (2.719–2.885) | 703.8 |
| strings | kernel-swar | java-swar | 2.572 (2.546–2.613) | 0.0 |
| strings | truffledata-batch | java-swar | 2.057 (1.915–2.105) | 704.1 |
| strings | fastcsv |  | 1.694 (1.600–1.718) | 923.0 |
| strings | truffledata-mapped | java-swar | 1.678 (1.512–1.686) | 1370.0 |
| strings | csv-string-record (native) |  | 1.195 | — |
| strings | univocity |  | 0.658 (0.022–0.754) | 3558.3 |
| strings | jackson |  | 0.457 (0.436–0.483) | 833.0 |
| strings | commons-csv |  | 0.437 (0.426–0.450) | 931.3 |
| utf8 | simdutf-validate (native) |  | 130.924 | — |

## csv_jvm: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 7.995 | — |
| index | index-native-small | native-avx512-ffm | 7.994 (7.934–8.018) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 7.034 | — |
| index | index-native | native-avx512-ffm | 6.477 (6.459–6.508) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 6.441 (4.839–6.467) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.975 (5.771–5.976) | 0.0 |
| index | index-vector | java-vector512 | 5.191 (0.005–5.325) | 0.0 |
| index | index-swar | java-swar | 2.393 (2.387–2.417) | 0.0 |
| index | native-index-swar (native) | swar | 1.868 | — |
| index | index-native-swar | native-swar-ffm | 1.775 (1.768–1.790) | 0.0 |
| index | index-scalar | java-scalar | 0.931 (0.891–0.937) | 0.0 |
| index | index-table | java-table | 0.889 (0.868–0.894) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.765 | — |
| scan | kernel-native-small | native-avx512-ffm | 3.186 (3.174–3.192) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-ffm | 3.114 (3.102–3.175) | 0.8 |
| scan | kernel-native | native-avx512-ffm | 2.871 (2.697–2.931) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.802 | — |
| scan | kernel-vector | java-vector512 | 2.344 (0.062–2.406) | 0.0 |
| scan | kernel-swar | java-swar | 1.580 (1.557–1.593) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.509 (1.498–1.540) | 0.3 |
| scan | csv-core-scan (native) |  | 1.042 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.937 (0.922–0.949) | 0.3 |
| scan | csv-byte-record (native) |  | 0.785 | — |
| scan | fastcsv |  | 0.584 (0.227–0.627) | 9.1 |
| scan | jackson |  | 0.302 (0.280–0.305) | 1227.9 |
| strings | kernel-native | native-avx512-ffm | 0.774 (0.578–0.785) | 46.3 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.690 (0.690–0.692) | 487.4 |
| strings | kernel-swar | java-swar | 0.597 (0.587–0.597) | 46.3 |
| strings | truffledata-batch | java-swar | 0.502 (0.497–0.506) | 486.6 |
| strings | csv-string-record (native) |  | 0.473 | — |
| strings | truffledata-batch-bytes | per-byte | 0.426 (0.029–0.454) | 486.5 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.402 (0.401–0.407) | 854.7 |
| strings | fastcsv |  | 0.393 (0.387–0.400) | 1380.9 |
| strings | univocity |  | 0.356 (0.353–0.359) | 2269.9 |
| strings | truffledata-mapped | java-swar | 0.328 (0.326–0.332) | 853.9 |
| strings | jackson |  | 0.301 (0.295–0.304) | 1227.9 |
| strings | commons-csv |  | 0.183 (0.180–0.191) | 1393.9 |
| typed | kernel-native | native-avx512-ffm | 1.126 (1.122–1.127) | 0.0 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.803 (0.802–0.813) | 425.3 |
| typed | kernel-swar | java-swar | 0.781 (0.777–0.790) | 0.0 |
| typed | truffledata-plan | java-swar | 0.591 (0.591–0.598) | 424.4 |
| typed | truffledata-plan-bytes | per-byte | 0.496 (0.494–0.500) | 424.4 |
| typed | csv-typed (native) |  | 0.385 | — |
| typed | fastcsv |  | 0.282 (0.280–0.288) | 1721.7 |
| typed | univocity |  | 0.266 (0.261–0.268) | 2417.1 |
| typed | jackson |  | 0.232 (0.232–0.235) | 1567.1 |
| typed | commons-csv |  | 0.146 (0.141–0.147) | 1733.1 |
| utf8 | simdutf-validate (native) |  | 76.929 | — |

## csv_jvm: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.674 | — |
| index | index-native-small | native-avx512-ffm | 8.568 (8.521–8.625) | 0.0 |
| index | index-vector | java-vector512 | 6.990 (6.928–7.019) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.678 | — |
| index | index-native | native-avx512-ffm | 6.225 (6.095–6.243) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 6.169 (6.130–6.184) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.014 (4.898–5.030) | 0.0 |
| index | index-swar | java-swar | 2.576 (2.548–2.621) | 0.0 |
| index | native-index-swar (native) | swar | 1.748 | — |
| index | index-native-swar | native-swar-ffm | 1.598 (1.597–1.604) | 0.0 |
| index | index-scalar | java-scalar | 1.251 (1.231–1.271) | 0.0 |
| index | index-table | java-table | 0.984 (0.982–0.986) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.848 | — |
| scan | kernel-native-small | native-avx512-ffm | 3.164 (3.106–3.227) | 0.0 |
| scan | kernel-vector | java-vector512 | 2.936 (2.379–3.001) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 2.678 (0.358–2.807) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.656 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 2.532 (2.527–2.630) | 0.1 |
| scan | kernel-swar | java-swar | 1.749 (1.748–1.759) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.519 (1.503–1.544) | 0.0 |
| scan | csv-core-scan (native) |  | 1.163 | — |
| scan | truffledata-scanner-bytes | per-byte | 1.029 (0.976–1.045) | 0.0 |
| scan | csv-byte-record (native) |  | 0.915 | — |
| scan | fastcsv |  | 0.871 (0.851–0.928) | 0.0 |
| scan | jackson |  | 0.324 (0.305–0.334) | 384.0 |
| strings | kernel-native | native-avx512-ffm | 0.881 (0.880–0.885) | 0.0 |
| strings | csv-string-record (native) |  | 0.813 | — |
| strings | kernel-swar | java-swar | 0.748 (0.669–0.756) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.671 (0.661–0.686) | 193.2 |
| strings | truffledata-batch | java-swar | 0.551 (0.304–0.598) | 193.0 |
| strings | truffledata-batch-bytes | per-byte | 0.515 (0.508–0.517) | 193.0 |
| strings | fastcsv |  | 0.494 (0.490–0.497) | 464.0 |
| strings | univocity |  | 0.426 (0.415–0.433) | 433.7 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.391 (0.390–0.396) | 140.6 |
| strings | truffledata-mapped | java-swar | 0.338 (0.138–0.339) | 140.4 |
| strings | jackson |  | 0.325 (0.310–0.332) | 384.0 |
| strings | commons-csv |  | 0.189 (0.185–0.191) | 480.0 |
| typed | kernel-native | native-avx512-ffm | 0.695 (0.692–0.699) | 0.0 |
| typed | kernel-swar | java-swar | 0.618 (0.510–0.624) | 0.0 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.570 (0.560–0.592) | 124.1 |
| typed | truffledata-plan | java-swar | 0.486 (0.481–0.490) | 123.9 |
| typed | csv-typed (native) |  | 0.482 | — |
| typed | truffledata-plan-bytes | per-byte | 0.440 (0.433–0.449) | 123.9 |
| typed | fastcsv |  | 0.250 (0.248–0.250) | 536.0 |
| typed | univocity |  | 0.223 (0.192–0.224) | 601.7 |
| typed | jackson |  | 0.198 (0.182–0.198) | 456.0 |
| typed | commons-csv |  | 0.129 (0.125–0.129) | 648.0 |
| utf8 | simdutf-validate (native) |  | 35.841 | — |

## csv_jvm: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 5.018 | — |
| index | index-native-small | native-avx512-ffm | 4.843 (4.817–4.907) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.790 | — |
| index | index-native-avx512 | native-avx512-ffm | 4.091 (4.072–4.109) | 0.0 |
| index | index-native | native-avx512-ffm | 4.060 (4.043–4.074) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 3.999 (3.943–4.002) | 0.0 |
| index | index-vector | java-vector512 | 3.724 (3.585–3.730) | 0.0 |
| index | index-swar | java-swar | 2.055 (2.004–2.076) | 0.0 |
| index | native-index-swar (native) | swar | 1.720 | — |
| index | index-native-swar | native-swar-ffm | 1.582 (1.580–1.585) | 0.0 |
| index | index-scalar | java-scalar | 0.904 (0.829–0.910) | 0.0 |
| index | index-table | java-table | 0.894 (0.891–0.894) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.776 | — |
| scan | kernel-native-small | native-avx512-ffm | 2.683 (2.621–2.686) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-ffm | 2.513 (2.507–2.524) | 0.5 |
| scan | kernel-native | native-avx512-ffm | 2.339 (2.322–2.343) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.326 | — |
| scan | kernel-vector | java-vector512 | 2.222 (2.215–2.224) | 0.0 |
| scan | kernel-swar | java-swar | 1.472 (1.460–1.495) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.437 (1.433–1.459) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 0.966 (0.226–0.968) | 0.0 |
| scan | csv-byte-record (native) |  | 0.779 | — |
| scan | fastcsv |  | 0.614 (0.580–0.614) | 0.1 |
| scan | jackson |  | 0.301 (0.295–0.303) | 1227.4 |
| scan | csv-core-scan (native) |  | 0.195 | — |
| strings | kernel-native | native-avx512-ffm | 0.736 (0.735–0.744) | 46.0 |
| strings | kernel-swar | java-swar | 0.608 (0.603–0.617) | 46.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.608 (0.608–0.611) | 486.8 |
| strings | csv-string-record (native) |  | 0.492 | — |
| strings | truffledata-batch | java-swar | 0.475 (0.469–0.480) | 486.0 |
| strings | truffledata-batch-bytes | per-byte | 0.415 (0.366–0.420) | 486.0 |
| strings | fastcsv |  | 0.406 (0.394–0.407) | 1371.4 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.368 (0.362–0.381) | 398.1 |
| strings | univocity |  | 0.364 (0.359–0.366) | 1346.7 |
| strings | truffledata-mapped | java-swar | 0.304 (0.271–0.320) | 397.3 |
| strings | jackson |  | 0.303 (0.302–0.307) | 1227.4 |
| strings | commons-csv |  | 0.179 (0.171–0.191) | 1387.4 |
| typed | kernel-native | native-avx512-ffm | 1.006 (1.001–1.009) | 0.0 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.795 (0.766–0.801) | 425.6 |
| typed | kernel-swar | java-swar | 0.789 (0.775–0.791) | 0.0 |
| typed | truffledata-plan | java-swar | 0.588 (0.552–0.593) | 424.9 |
| typed | truffledata-plan-bytes | per-byte | 0.479 (0.475–0.485) | 424.9 |
| typed | csv-typed (native) |  | 0.394 | — |
| typed | fastcsv |  | 0.277 (0.272–0.280) | 1710.6 |
| typed | univocity |  | 0.260 (0.260–0.269) | 1493.9 |
| typed | jackson |  | 0.228 (0.224–0.239) | 1566.5 |
| typed | commons-csv |  | 0.143 (0.047–0.147) | 1726.6 |
| utf8 | simdutf-validate (native) |  | 19.318 | — |

## csv_jvm: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 6.399 | — |
| index | index-native-small | native-avx512-ffm | 6.392 (4.714–6.399) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.246 | — |
| index | index-vector | java-vector512 | 5.497 (5.477–5.506) | 0.0 |
| index | index-native | native-avx512-ffm | 5.148 (5.108–5.163) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 5.091 (5.008–5.158) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.014 (4.883–5.080) | 0.0 |
| index | index-swar | java-swar | 2.411 (2.396–2.430) | 0.0 |
| index | native-index-swar (native) | swar | 1.830 | — |
| index | index-native-swar | native-swar-ffm | 1.688 (1.612–1.689) | 0.0 |
| index | index-table | java-table | 0.960 (0.947–0.964) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.896 | — |
| index | index-scalar | java-scalar | 0.636 (0.628–0.637) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 5.440 (5.275–5.499) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 5.378 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 5.144 (5.136–5.173) | 0.5 |
| scan | kernel-vector | java-vector512 | 4.893 (4.849–4.933) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 4.471 (4.450–4.483) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.263 (2.255–2.279) | 0.0 |
| scan | kernel-swar | java-swar | 2.246 (2.157–2.289) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 1.082 (1.074–1.091) | 0.0 |
| scan | csv-core-scan (native) |  | 0.838 | — |
| scan | csv-byte-record (native) |  | 0.722 | — |
| scan | jackson |  | 0.503 (0.470–0.513) | 331.1 |
| scan | fastcsv |  | 0.407 (0.402–0.417) | 0.1 |
| strings | csv-string-record (native) |  | 0.662 | — |
| strings | kernel-native | native-avx512-ffm | 0.658 (0.649–0.669) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.634 (0.629–0.640) | 286.9 |
| strings | kernel-swar | java-swar | 0.595 (0.595–0.596) | 0.0 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.567 (0.550–0.572) | 84.0 |
| strings | truffledata-batch | java-swar | 0.552 (0.526–0.553) | 286.1 |
| strings | jackson |  | 0.510 (0.397–0.511) | 331.1 |
| strings | truffledata-mapped | java-swar | 0.492 (0.489–0.493) | 83.2 |
| strings | truffledata-batch-bytes | per-byte | 0.443 (0.438–0.446) | 286.1 |
| strings | fastcsv |  | 0.375 (0.363–0.375) | 395.1 |
| strings | univocity |  | 0.346 (0.334–0.366) | 370.4 |
| strings | commons-csv |  | 0.289 (0.288–0.290) | 411.2 |
| utf8 | simdutf-validate (native) |  | 33.239 | — |

## csv_jvm: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-ffm | 8.638 (0.442–8.840) | 0.1 |
| index | native-index-avx512 (native) | avx512 | 8.343 | — |
| index | native-index-avx2 (native) | avx2 | 8.213 | — |
| index | index-native-avx2 | native-avx2-ffm | 6.140 (6.028–6.150) | 0.0 |
| index | index-native | native-avx512-ffm | 6.044 (5.937–6.045) | 0.0 |
| index | index-vector | java-vector512 | 5.989 (5.213–6.047) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 2.731 (1.706–6.015) | 0.0 |
| index | index-swar | java-swar | 1.702 (1.651–1.704) | 0.0 |
| index | native-index-swar (native) | swar | 1.632 | — |
| index | index-native-swar | native-swar-ffm | 1.572 (1.504–1.575) | 0.0 |
| index | native-index-scalar (native) | scalar | 1.267 | — |
| index | index-scalar | java-scalar | 1.138 (1.124–1.138) | 0.0 |
| index | index-table | java-table | 0.584 (0.582–0.584) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 7.193 (7.118–7.406) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-ffm | 7.146 (7.042–7.150) | 1.5 |
| scan | native-indexed-scan (native) | avx512 | 6.995 | — |
| scan | kernel-vector | java-vector512 | 5.375 (5.367–5.488) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 5.325 (5.114–5.394) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 2.813 (1.718–2.847) | 0.0 |
| scan | kernel-swar | java-swar | 1.616 (1.390–1.634) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.612 (1.608–1.627) | 0.0 |
| scan | fastcsv |  | 1.141 (1.051–1.197) | 0.2 |
| scan | csv-core-scan (native) |  | 0.924 | — |
| scan | csv-byte-record (native) |  | 0.807 | — |
| scan | jackson |  | 0.362 (0.292–0.365) | 832.0 |
| strings | kernel-native | native-avx512-ffm | 2.477 (2.340–2.514) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 2.297 (2.294–2.308) | 704.8 |
| strings | truffledata-mapped-native | native-avx512-ffm | 1.966 (1.852–1.974) | 53.6 |
| strings | truffledata-batch-bytes | per-byte | 1.359 (1.145–1.482) | 702.6 |
| strings | kernel-swar | java-swar | 1.221 (1.213–1.227) | 0.0 |
| strings | truffledata-batch | java-swar | 1.048 (1.048–1.052) | 702.6 |
| strings | truffledata-mapped | java-swar | 0.967 (0.962–0.972) | 51.3 |
| strings | fastcsv |  | 0.940 (0.076–0.966) | 896.2 |
| strings | csv-string-record (native) |  | 0.757 | — |
| strings | univocity |  | 0.417 (0.404–0.417) | 885.0 |
| strings | jackson |  | 0.338 (0.293–0.362) | 832.0 |
| strings | commons-csv |  | 0.217 (0.203–0.219) | 912.1 |
| utf8 | simdutf-validate (native) |  | 31.394 | — |

## Size sweep (kernel crossovers; GB/s, one fork)

| Binary | Workload | Dataset | Engine | 256 B | 1 KiB | 4 KiB | 16 KiB | 64 KiB | 256 KiB | 1024 KiB | 4096 KiB | 16384 KiB |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| csv-comparison | index | mixed | index-native | 1.15 | 2.08 | 3.96 | 4.60 | 4.76 | 4.85 | 4.81 | 3.38 | 3.20 |
| csv-comparison | index | mixed | index-native-small | 1.16 | 2.18 | 3.81 | 4.24 | 4.74 | 4.83 | 4.81 | 3.40 | 1.63 |
| csv-comparison | index | mixed | index-swar | 0.72 | 0.97 | 1.25 | 1.32 | 1.30 | 1.03 | 1.33 | 1.07 | 1.16 |
| csv-comparison | index | numeric | index-native | 0.94 | 2.27 | 3.48 | 4.08 | 4.31 | 4.24 | 4.31 | 4.23 | 4.30 |
| csv-comparison | index | numeric | index-native-small | 0.34 | 2.05 | 3.53 | 4.08 | 4.30 | 4.29 | 4.17 | 4.27 | 4.25 |
| csv-comparison | index | numeric | index-swar | 0.65 | 1.08 | 1.28 | 1.36 | 1.38 | 1.39 | 1.33 | 1.32 | 1.33 |
| csv-comparison | scan | mixed | kernel-native-small | 0.63 | 0.93 | 1.17 | 1.27 | 1.29 | 1.28 | 1.24 | 1.10 | 1.07 |
| csv-comparison | scan | mixed | sweep-native | 0.35 | 0.66 | 1.06 | 1.21 | 1.14 | 1.24 | 1.16 | 1.09 | 1.10 |
| csv-comparison | scan | mixed | sweep-swar | 0.37 | 0.54 | 0.69 | 0.72 | 0.74 | 0.74 | 0.70 | 0.55 | 0.68 |
| csv-comparison | scan | mixed | truffledata-scanner-bytes | 0.40 | 0.55 | 0.64 | 0.67 | 0.66 | 0.53 | 0.51 | 0.52 | 0.52 |
| csv-comparison | scan | numeric | kernel-native-small | 0.50 | 0.75 | 0.85 | 0.89 | 0.90 | 0.88 | 0.91 | 0.91 | 0.94 |
| csv-comparison | scan | numeric | sweep-native | 0.31 | 0.58 | 0.77 | 0.83 | 0.86 | 0.87 | 0.88 | 0.85 | 0.89 |
| csv-comparison | scan | numeric | sweep-swar | 0.32 | 0.50 | 0.57 | 0.56 | 0.60 | 0.61 | 0.60 | 0.60 | 0.61 |
| csv-comparison | scan | numeric | truffledata-scanner-bytes | 0.35 | 0.45 | 0.49 | 0.48 | 0.49 | 0.46 | 0.45 | 0.46 | 0.45 |
| csv-comparison | strings | mixed | sweep-batch-native | 0.05 | 0.11 | 0.18 | 0.22 | 0.22 | 0.20 | 0.19 | 0.14 | 0.13 |
| csv-comparison | strings | mixed | sweep-batch-swar | 0.06 | 0.10 | 0.15 | 0.17 | 0.15 | 0.16 | 0.17 | 0.14 | 0.14 |
| csv-comparison | strings | mixed | truffledata-batch-bytes | 0.06 | 0.11 | 0.17 | 0.19 | 0.17 | 0.16 | 0.15 | 0.13 | 0.13 |
| csv-comparison | strings | numeric | sweep-batch-native | 0.06 | 0.12 | 0.15 | 0.16 | 0.17 | 0.15 | 0.14 | 0.09 | 0.13 |
| csv-comparison | strings | numeric | sweep-batch-swar | 0.05 | 0.11 | 0.13 | 0.13 | 0.14 | 0.14 | 0.13 | 0.13 | 0.12 |
| csv-comparison | strings | numeric | truffledata-batch-bytes | 0.07 | 0.11 | 0.14 | 0.15 | 0.15 | 0.14 | 0.13 | 0.13 | 0.11 |
| csv-comparison-v3 | index | mixed | index-native | 1.75 | 3.43 | 6.30 | 5.94 | 6.87 | 8.11 | 8.09 | 5.07 | 4.92 |
| csv-comparison-v3 | index | mixed | index-native-small | 1.78 | 3.41 | 6.44 | 7.80 | 7.81 | 8.05 | 8.00 | 5.00 | 4.95 |
| csv-comparison-v3 | index | mixed | index-swar | 1.19 | 1.63 | 2.15 | 2.29 | 2.23 | 2.14 | 2.08 | 1.72 | 1.45 |
| csv-comparison-v3 | index | numeric | index-native | 1.44 | 3.80 | 6.64 | 8.26 | 8.46 | 8.79 | 8.80 | 7.30 | 8.75 |
| csv-comparison-v3 | index | numeric | index-native-small | 1.44 | 3.86 | 6.63 | 8.28 | 8.52 | 8.31 | 8.50 | 6.73 | 8.50 |
| csv-comparison-v3 | index | numeric | index-swar | 1.06 | 1.67 | 2.22 | 2.25 | 2.27 | 2.24 | 2.23 | 2.25 | 2.25 |
| csv-comparison-v3 | scan | mixed | kernel-native-small | 1.06 | 1.59 | 2.06 | 2.13 | 2.09 | 2.14 | 2.11 | 1.80 | 1.83 |
| csv-comparison-v3 | scan | mixed | sweep-native | 0.60 | 1.13 | 1.72 | 1.97 | 1.94 | 2.05 | 1.97 | 1.77 | 1.75 |
| csv-comparison-v3 | scan | mixed | sweep-swar | 0.59 | 0.90 | 1.16 | 1.23 | 1.08 | 1.24 | 1.14 | 0.93 | 1.09 |
| csv-comparison-v3 | scan | mixed | truffledata-scanner-bytes | 0.70 | 0.97 | 1.10 | 1.06 | 0.93 | 0.35 | 0.84 | 0.83 | 0.85 |
| csv-comparison-v3 | scan | numeric | kernel-native-small | 0.84 | 1.30 | 1.51 | 1.59 | 1.60 | 1.66 | 1.68 | 1.68 | 1.71 |
| csv-comparison-v3 | scan | numeric | sweep-native | 0.42 | 0.97 | 1.30 | 1.21 | 1.47 | 1.51 | 1.52 | 1.53 | 1.15 |
| csv-comparison-v3 | scan | numeric | sweep-swar | 0.53 | 0.82 | 0.95 | 0.82 | 1.00 | 1.01 | 1.00 | 0.99 | 1.00 |
| csv-comparison-v3 | scan | numeric | truffledata-scanner-bytes | 0.60 | 0.77 | 0.72 | 0.78 | 0.75 | 0.77 | 0.79 | 0.79 | 0.80 |
| csv-comparison-v3 | strings | mixed | sweep-batch-native | 0.00 | 0.17 | 0.27 | 0.31 | 0.31 | 0.30 | 0.29 | 0.23 | 0.08 |
| csv-comparison-v3 | strings | mixed | sweep-batch-swar | 0.08 | 0.16 | 0.22 | 0.24 | 0.26 | 0.25 | 0.24 | 0.22 | 0.20 |
| csv-comparison-v3 | strings | mixed | truffledata-batch-bytes | 0.09 | 0.17 | 0.25 | 0.27 | 0.26 | 0.23 | 0.24 | 0.21 | 0.18 |
| csv-comparison-v3 | strings | numeric | sweep-batch-native | 0.09 | 0.17 | 0.22 | 0.24 | 0.24 | 0.24 | 0.23 | 0.21 | 0.17 |
| csv-comparison-v3 | strings | numeric | sweep-batch-swar | 0.10 | 0.16 | 0.17 | 0.21 | 0.21 | 0.22 | 0.21 | 0.17 | 0.19 |
| csv-comparison-v3 | strings | numeric | truffledata-batch-bytes | 0.11 | 0.17 | 0.20 | 0.20 | 0.22 | 0.22 | 0.21 | 0.20 | 0.16 |
| csv_jvm | index | mixed | index-native | 2.26 | 4.33 | 7.82 | 9.01 | 8.58 | 8.85 | 7.90 | 5.45 | 4.96 |
| csv_jvm | index | mixed | index-native-small | 2.34 | 4.37 | 8.07 | 9.69 | 9.67 | 9.74 | 7.88 | 6.22 | 5.93 |
| csv_jvm | index | mixed | index-swar | 1.68 | 2.35 | 3.10 | 3.25 | 3.26 | 3.30 | 0.07 | 2.33 | 2.42 |
| csv_jvm | index | numeric | index-native | 1.89 | 4.71 | 7.71 | 9.15 | 9.10 | 9.20 | 8.30 | 8.29 | 7.99 |
| csv_jvm | index | numeric | index-native-small | 1.88 | 4.95 | 8.31 | 10.08 | 10.75 | 10.91 | 10.81 | 10.75 | 10.78 |
| csv_jvm | index | numeric | index-swar | 1.46 | 2.59 | 2.22 | 1.76 | 3.37 | 3.17 | 3.14 | 3.18 | 3.17 |
| csv_jvm | scan | mixed | kernel-native-small | 1.74 | 2.59 | 3.60 | 3.94 | 3.78 | 4.16 | 3.99 | 3.27 | 3.22 |
| csv_jvm | scan | mixed | sweep-native | 1.50 | 2.54 | 3.96 | 4.45 | 4.25 | 4.42 | 3.79 | 3.13 | 3.05 |
| csv_jvm | scan | mixed | sweep-swar | 1.17 | 1.53 | 2.12 | 2.35 | 2.31 | 2.14 | 1.83 | 1.71 | 1.80 |
| csv_jvm | scan | mixed | truffledata-scanner-bytes | 1.25 | 1.92 | 2.35 | 2.46 | 2.52 | 1.79 | 1.10 | 1.17 | 1.15 |
| csv_jvm | scan | numeric | kernel-native-small | 1.38 | 2.58 | 3.13 | 3.71 | 3.68 | 3.68 | 3.54 | 3.26 | 3.88 |
| csv_jvm | scan | numeric | sweep-native | 1.04 | 2.65 | 3.28 | 3.51 | 3.45 | 3.73 | 3.46 | 3.22 | 3.01 |
| csv_jvm | scan | numeric | sweep-swar | 1.02 | 1.63 | 1.97 | 2.08 | 2.13 | 2.08 | 1.65 | 1.74 | 1.87 |
| csv_jvm | scan | numeric | truffledata-scanner-bytes | 1.22 | 1.77 | 1.96 | 1.98 | 1.77 | 1.52 | 1.27 | 1.23 | 1.24 |
| csv_jvm | strings | mixed | sweep-batch-native | 0.15 | 0.36 | 0.72 | 0.95 | 0.92 | 1.05 | 0.86 | 0.74 | 0.43 |
| csv_jvm | strings | mixed | sweep-batch-swar | 0.15 | 0.29 | 0.53 | 0.64 | 0.70 | 0.67 | 0.59 | 0.57 | 0.43 |
| csv_jvm | strings | mixed | truffledata-batch-bytes | 0.16 | 0.34 | 0.63 | 0.78 | 0.81 | 0.56 | 0.55 | 0.50 | 0.37 |
| csv_jvm | strings | numeric | sweep-batch-native | 0.21 | 0.43 | 0.69 | 0.78 | 0.84 | 0.87 | 0.91 | 0.80 | 0.74 |
| csv_jvm | strings | numeric | sweep-batch-swar | 0.19 | 0.39 | 0.52 | 0.55 | 0.58 | 0.68 | 0.73 | 0.70 | 0.65 |
| csv_jvm | strings | numeric | truffledata-batch-bytes | 0.24 | 0.41 | 0.56 | 0.63 | 0.64 | 0.11 | 0.67 | 0.62 | 0.60 |

## Retained typed reads (32 numeric rows, 8 typed columns read per row)

| Binary | Engine | Stage | ns/row (range) | Java B/row |
|---|---|---|---:|---:|
| csv-comparison | batch-fastcsv | interpret | 67.74 (66.72–68.84) | 2.00 |
| csv-comparison | batch-fastcsv | peak | 10.74 (10.61–12.02) | 1.00 |
| csv-comparison | batch-fastcsv | read-aot | 67.83 (67.09–68.31) | 0.00 |
| csv-comparison | truffledata-plan | interpret | 68.05 (67.23–68.80) | 2.00 |
| csv-comparison | truffledata-plan | peak | 6.99 (6.62–7.79) | 1.00 |
| csv-comparison | truffledata-plan | read-aot | 64.93 (64.79–65.47) | 0.00 |
| csv-comparison-v3 | batch-fastcsv | interpret | 67.68 (67.56–73.09) | 2.00 |
| csv-comparison-v3 | batch-fastcsv | peak | 10.90 (10.71–13.20) | 1.00 |
| csv-comparison-v3 | batch-fastcsv | read-aot | 67.94 (67.39–68.70) | 0.00 |
| csv-comparison-v3 | truffledata-plan | interpret | 67.18 (66.25–68.84) | 2.00 |
| csv-comparison-v3 | truffledata-plan | peak | 6.80 (6.59–8.18) | 1.00 |
| csv-comparison-v3 | truffledata-plan | read-aot | 64.95 (64.82–66.81) | 0.00 |
| csv_jvm | batch-fastcsv | interpret | 28.74 (28.45–29.62) | 1.50 |
| csv_jvm | batch-fastcsv | peak | 10.25 (9.95–12.15) | 1.50 |
| csv_jvm | batch-fastcsv | read-aot | 28.28 (28.12–29.14) | 0.00 |
| csv_jvm | truffledata-plan | interpret | 30.47 (30.34–32.25) | 1.50 |
| csv_jvm | truffledata-plan | peak | 6.67 (6.55–6.68) | 1.50 |
| csv_jvm | truffledata-plan | read-aot | 28.44 (28.15–29.65) | 0.00 |
