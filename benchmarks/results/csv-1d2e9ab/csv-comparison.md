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
| index | index-native | native-avx512-svm | 8.043 (7.838–8.248) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 7.479 (7.213–7.744) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 7.444 | — |
| index | index-native-small | native-avx512-svm-leaf | 6.970 (6.925–7.015) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 6.292 (6.081–6.502) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.077 | — |
| index | index-swar | java-swar | 1.839 (1.684–1.994) | 0.0 |
| index | native-index-swar (native) | swar | 1.681 | — |
| index | index-native-swar | native-swar-svm | 1.588 (1.443–1.733) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.732 | — |
| index | index-table | java-table | 0.636 (0.539–0.732) | 0.0 |
| index | index-scalar | java-scalar | 0.632 (0.617–0.647) | 0.0 |
| index | index-vector | java-vector64 | 0.060 (0.056–0.064) | 2279.7 |
| scan | native-indexed-scan (native) | avx512 | 2.130 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.416 (1.341–1.490) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.352 (1.344–1.359) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.100 (1.038–1.162) | 207.3 |
| scan | csv-core-scan (native) |  | 1.031 | — |
| scan | kernel-swar | java-swar | 0.874 (0.849–0.899) | 0.0 |
| scan | csv-byte-record (native) |  | 0.829 | — |
| scan | truffledata-scanner | java-swar | 0.738 (0.730–0.746) | 13.0 |
| scan | truffledata-scanner-bytes | per-byte | 0.728 (0.685–0.771) | 0.0 |
| scan | fastcsv |  | 0.432 (0.400–0.465) | 2.6 |
| scan | jackson |  | 0.181 (0.177–0.184) | 256.0 |
| scan | kernel-vector | java-vector64 | 0.059 (0.056–0.062) | 2279.7 |
| strings | csv-string-record (native) |  | 0.491 | — |
| strings | kernel-swar | java-swar | 0.333 (0.327–0.339) | 256.0 |
| strings | kernel-native | native-avx512-svm | 0.288 (0.272–0.303) | 256.0 |
| strings | fastcsv |  | 0.268 (0.267–0.269) | 322.7 |
| strings | univocity |  | 0.189 (0.184–0.194) | 505.8 |
| strings | jackson |  | 0.168 (0.165–0.171) | 256.0 |
| strings | truffledata-batch-bytes | per-byte | 0.161 (0.161–0.161) | 404.3 |
| strings | truffledata-batch | java-swar | 0.154 (0.146–0.163) | 417.2 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.149 (0.142–0.156) | 611.5 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.135 (0.130–0.139) | 739.6 |
| strings | truffledata-mapped | java-swar | 0.118 (0.115–0.121) | 545.3 |
| strings | commons-csv |  | 0.068 (0.066–0.069) | 345.6 |
| typed | csv-typed (native) |  | 0.422 | — |
| typed | kernel-native | native-avx512-svm | 0.408 (0.382–0.434) | 0.0 |
| typed | kernel-swar | java-swar | 0.338 (0.331–0.344) | 0.0 |
| typed | truffledata-plan | java-swar | 0.197 (0.184–0.210) | 153.3 |
| typed | truffledata-plan-bytes | per-byte | 0.178 (0.156–0.201) | 140.3 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.124 (0.108–0.140) | 347.5 |
| typed | fastcsv |  | 0.090 (0.090–0.091) | 442.7 |
| typed | jackson |  | 0.085 (0.079–0.091) | 376.0 |
| typed | univocity |  | 0.069 (0.064–0.075) | 625.8 |
| typed | commons-csv |  | 0.045 (0.044–0.047) | 465.6 |
| utf8 | simdutf-validate (native) |  | 90.147 | — |

## csv-comparison: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 5.305 (5.301–5.308) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 5.299 (5.259–5.340) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 5.233 (5.181–5.285) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 5.163 | — |
| index | index-native-small | native-avx512-svm-leaf | 4.844 (4.445–5.243) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.678 | — |
| index | index-swar | java-swar | 1.616 (1.563–1.668) | 0.0 |
| index | native-index-swar (native) | swar | 1.292 | — |
| index | index-native-swar | native-swar-svm | 1.259 (1.242–1.276) | 0.0 |
| index | index-table | java-table | 0.618 (0.612–0.623) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.610 | — |
| index | index-scalar | java-scalar | 0.341 (0.339–0.342) | 0.0 |
| index | index-vector | java-vector64 | 0.037 (0.034–0.041) | 10188.3 |
| scan | native-indexed-scan (native) | avx512 | 4.098 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 3.881 (3.866–3.895) | 0.0 |
| scan | kernel-native | native-avx512-svm | 3.754 (3.753–3.755) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 3.008 (2.764–3.252) | 926.4 |
| scan | kernel-swar | java-swar | 1.353 (1.217–1.490) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.109 (1.084–1.135) | 58.2 |
| scan | csv-core-scan (native) |  | 0.469 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.464 (0.436–0.491) | 0.2 |
| scan | csv-byte-record (native) |  | 0.422 | — |
| scan | jackson |  | 0.194 (0.193–0.195) | 284.3 |
| scan | fastcsv |  | 0.184 (0.178–0.190) | 11.9 |
| scan | kernel-vector | java-vector64 | 0.046 (0.045–0.047) | 10188.3 |
| strings | csv-string-record (native) |  | 0.386 | — |
| strings | kernel-native | native-avx512-svm | 0.334 (0.333–0.334) | 284.2 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.230 (0.221–0.240) | 1764.0 |
| strings | kernel-swar | java-swar | 0.227 (0.173–0.281) | 284.1 |
| strings | jackson |  | 0.196 (0.195–0.197) | 284.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.179 (0.174–0.184) | 2255.5 |
| strings | truffledata-batch | java-swar | 0.177 (0.132–0.221) | 895.7 |
| strings | truffledata-batch-bytes | per-byte | 0.170 (0.164–0.175) | 837.8 |
| strings | fastcsv |  | 0.144 (0.124–0.164) | 344.1 |
| strings | truffledata-mapped | java-swar | 0.134 (0.120–0.147) | 1387.2 |
| strings | univocity |  | 0.115 (0.112–0.118) | 1245.5 |
| strings | commons-csv |  | 0.071 (0.064–0.078) | 363.4 |
| utf8 | simdutf-validate (native) |  | 60.964 | — |

## csv-comparison: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 12.142 (12.099–12.185) | 0.1 |
| index | index-native-small | native-avx512-svm-leaf | 12.098 (11.697–12.500) | 0.1 |
| index | native-index-avx512 (native) | avx512 | 11.913 | — |
| index | index-native | native-avx512-svm | 10.866 (9.584–12.148) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 10.667 | — |
| index | index-native-avx2 | native-avx2-svm | 9.229 (8.001–10.456) | 0.1 |
| index | index-swar | java-swar | 1.831 (1.820–1.843) | 0.0 |
| index | native-index-swar (native) | swar | 1.506 | — |
| index | index-native-swar | native-swar-svm | 1.471 (1.464–1.478) | 0.1 |
| index | native-index-scalar (native) | scalar | 1.249 | — |
| index | index-table | java-table | 0.664 (0.655–0.673) | 0.0 |
| index | index-scalar | java-scalar | 0.517 (0.512–0.522) | 0.0 |
| index | index-vector | java-vector64 | 0.051 (0.051–0.052) | 29278.5 |
| scan | kernel-native | native-avx512-svm | 8.863 (8.808–8.917) | 0.1 |
| scan | native-indexed-scan (native) | avx512 | 8.653 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 7.695 (7.227–8.162) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 6.227 (5.290–7.164) | 2662.2 |
| scan | truffledata-scanner | java-swar | 1.685 (1.674–1.696) | 167.1 |
| scan | truffledata-scanner-bytes | per-byte | 1.566 (1.558–1.575) | 0.6 |
| scan | kernel-swar | java-swar | 1.434 (1.160–1.708) | 0.0 |
| scan | csv-core-scan (native) |  | 0.843 | — |
| scan | csv-byte-record (native) |  | 0.784 | — |
| scan | fastcsv |  | 0.546 (0.541–0.552) | 34.2 |
| scan | jackson |  | 0.203 (0.201–0.205) | 768.9 |
| scan | kernel-vector | java-vector64 | 0.049 (0.046–0.051) | 29278.5 |
| strings | kernel-native | native-avx512-svm | 1.038 (1.026–1.051) | 768.5 |
| strings | csv-string-record (native) |  | 0.767 | — |
| strings | kernel-swar | java-swar | 0.662 (0.662–0.662) | 768.4 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.556 (0.510–0.603) | 4224.6 |
| strings | truffledata-batch-bytes | per-byte | 0.468 (0.468–0.468) | 1562.9 |
| strings | truffledata-batch | java-swar | 0.405 (0.359–0.450) | 1729.5 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.403 (0.401–0.406) | 5595.1 |
| strings | fastcsv |  | 0.371 (0.371–0.372) | 850.9 |
| strings | truffledata-mapped | java-swar | 0.334 (0.325–0.342) | 3100.0 |
| strings | jackson |  | 0.208 (0.206–0.209) | 768.9 |
| strings | univocity |  | 0.182 (0.178–0.186) | 3486.1 |
| strings | commons-csv |  | 0.111 (0.111–0.111) | 863.1 |
| utf8 | simdutf-validate (native) |  | 59.273 | — |

## csv-comparison: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-svm-leaf | 7.812 (7.679–7.945) | 0.0 |
| index | index-native | native-avx512-svm | 7.736 (7.695–7.777) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 7.344 | — |
| index | index-native-avx512 | native-avx512-svm | 6.973 (6.122–7.823) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.724 | — |
| index | index-native-avx2 | native-avx2-svm | 5.226 (3.292–7.159) | 0.0 |
| index | index-swar | java-swar | 1.976 (1.966–1.985) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.809 (1.752–1.867) | 0.0 |
| index | native-index-swar (native) | swar | 1.808 | — |
| index | native-index-scalar (native) | scalar | 0.763 | — |
| index | index-table | java-table | 0.742 (0.725–0.758) | 0.0 |
| index | index-scalar | java-scalar | 0.610 (0.609–0.612) | 0.0 |
| index | index-vector | java-vector64 | 0.068 (0.065–0.070) | 10106.4 |
| scan | native-indexed-scan (native) | avx512 | 2.520 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 2.065 (2.041–2.090) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.979 (1.922–2.036) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.495 (1.457–1.533) | 918.9 |
| scan | kernel-swar | java-swar | 1.080 (1.053–1.107) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.028 (1.012–1.043) | 57.7 |
| scan | csv-core-scan (native) |  | 0.966 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.789 (0.776–0.802) | 0.3 |
| scan | csv-byte-record (native) |  | 0.644 | — |
| scan | fastcsv |  | 0.436 (0.433–0.438) | 11.6 |
| scan | jackson |  | 0.210 (0.207–0.213) | 856.3 |
| scan | kernel-vector | java-vector64 | 0.065 (0.064–0.067) | 10106.4 |
| strings | csv-string-record (native) |  | 0.424 | — |
| strings | kernel-native | native-avx512-svm | 0.404 (0.396–0.411) | 898.6 |
| strings | kernel-swar | java-swar | 0.329 (0.321–0.336) | 898.5 |
| strings | fastcsv |  | 0.257 (0.244–0.271) | 995.9 |
| strings | univocity |  | 0.224 (0.220–0.228) | 1890.2 |
| strings | jackson |  | 0.215 (0.207–0.223) | 856.3 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.215 (0.212–0.217) | 2281.0 |
| strings | truffledata-batch-bytes | per-byte | 0.203 (0.201–0.205) | 1362.3 |
| strings | truffledata-batch | java-swar | 0.195 (0.195–0.195) | 1419.7 |
| strings | truffledata-mapped | java-swar | 0.157 (0.157–0.157) | 2135.9 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.157 (0.149–0.165) | 2997.2 |
| strings | commons-csv |  | 0.079 (0.077–0.081) | 1015.6 |
| typed | kernel-native | native-avx512-svm | 0.736 (0.731–0.742) | 0.0 |
| typed | kernel-swar | java-swar | 0.511 (0.494–0.529) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.385 (0.379–0.390) | 1366.7 |
| typed | csv-typed (native) |  | 0.349 | — |
| typed | truffledata-plan | java-swar | 0.337 (0.323–0.350) | 505.5 |
| typed | truffledata-plan-bytes | per-byte | 0.309 (0.292–0.326) | 448.0 |
| typed | fastcsv |  | 0.163 (0.161–0.166) | 1239.1 |
| typed | jackson |  | 0.140 (0.139–0.141) | 1099.5 |
| typed | univocity |  | 0.135 (0.134–0.137) | 2133.4 |
| typed | commons-csv |  | 0.060 (0.059–0.061) | 1258.8 |
| utf8 | simdutf-validate (native) |  | 64.305 | — |

## csv-comparison: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 2.736 | — |
| index | native-index-avx512 (native) | avx512 | 2.674 | — |
| index | index-native | native-avx512-svm | 2.670 (2.663–2.677) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 2.483 (2.411–2.554) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 2.430 (2.197–2.662) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 2.204 (2.140–2.267) | 0.0 |
| index | native-index-swar (native) | swar | 1.227 | — |
| index | index-native-swar | native-swar-svm | 1.169 (1.126–1.211) | 0.0 |
| index | index-swar | java-swar | 1.127 (0.978–1.276) | 0.0 |
| index | index-table | java-table | 0.498 (0.496–0.500) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.395 | — |
| index | index-scalar | java-scalar | 0.347 (0.342–0.352) | 0.0 |
| index | index-vector | java-vector64 | 0.038 (0.036–0.040) | 2372.2 |
| scan | native-indexed-scan (native) | avx512 | 0.825 | — |
| scan | kernel-native | native-avx512-svm | 0.779 (0.772–0.786) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 0.735 (0.685–0.786) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 0.579 (0.559–0.599) | 1.7 |
| scan | kernel-swar | java-swar | 0.523 (0.427–0.619) | 0.0 |
| scan | csv-core-scan (native) |  | 0.509 | — |
| scan | truffledata-scanner | java-swar | 0.480 (0.384–0.576) | 0.1 |
| scan | csv-byte-record (native) |  | 0.442 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.422 (0.412–0.433) | 0.0 |
| scan | fastcsv |  | 0.285 (0.267–0.302) | 0.6 |
| scan | jackson |  | 0.078 (0.072–0.083) | 256.0 |
| scan | kernel-vector | java-vector64 | 0.037 (0.037–0.038) | 2372.2 |
| strings | csv-string-record (native) |  | 0.393 | — |
| strings | kernel-native | native-avx512-svm | 0.227 (0.226–0.227) | 256.0 |
| strings | kernel-swar | java-swar | 0.210 (0.210–0.211) | 256.0 |
| strings | univocity |  | 0.126 (0.125–0.128) | 297.7 |
| strings | fastcsv |  | 0.122 (0.102–0.141) | 320.6 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.105 (0.105–0.105) | 474.7 |
| strings | truffledata-batch-bytes | per-byte | 0.100 (0.099–0.101) | 473.0 |
| strings | truffledata-batch | java-swar | 0.087 (0.085–0.090) | 473.1 |
| strings | truffledata-mapped | java-swar | 0.071 (0.069–0.074) | 548.5 |
| strings | jackson |  | 0.063 (0.057–0.069) | 256.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.062 (0.052–0.072) | 550.1 |
| strings | commons-csv |  | 0.028 (0.024–0.031) | 344.3 |
| typed | kernel-swar | java-swar | 0.230 (0.226–0.234) | 0.0 |
| typed | csv-typed (native) |  | 0.225 | — |
| typed | kernel-native | native-avx512-svm | 0.217 (0.198–0.235) | 0.0 |
| typed | truffledata-plan | java-swar | 0.100 (0.081–0.119) | 201.9 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.099 (0.076–0.123) | 203.5 |
| typed | truffledata-plan-bytes | per-byte | 0.097 (0.084–0.109) | 201.8 |
| typed | univocity |  | 0.044 (0.039–0.049) | 417.7 |
| typed | fastcsv |  | 0.043 (0.035–0.052) | 440.6 |
| typed | jackson |  | 0.036 (0.035–0.037) | 376.0 |
| typed | commons-csv |  | 0.023 (0.021–0.025) | 464.3 |
| utf8 | simdutf-validate (native) |  | 11.836 | — |

## csv-comparison: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 4.839 (4.830–4.848) | 0.0 |
| index | index-native | native-avx512-svm | 4.815 (4.689–4.942) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.811 | — |
| index | index-native-avx2 | native-avx2-svm | 4.756 (4.748–4.764) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.702 | — |
| index | index-native-small | native-avx512-svm-leaf | 4.611 (4.472–4.750) | 0.0 |
| index | index-swar | java-swar | 1.741 (1.731–1.750) | 0.0 |
| index | native-index-swar (native) | swar | 1.700 | — |
| index | index-native-swar | native-swar-svm | 1.694 (1.688–1.701) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.784 | — |
| index | index-table | java-table | 0.778 (0.774–0.781) | 0.0 |
| index | index-scalar | java-scalar | 0.586 (0.560–0.612) | 0.0 |
| index | index-vector | java-vector64 | 0.058 (0.048–0.069) | 10184.3 |
| scan | native-indexed-scan (native) | avx512 | 2.285 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.826 (1.820–1.832) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.803 (1.776–1.830) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.440 (1.189–1.692) | 7.2 |
| scan | csv-core-scan (native) |  | 1.073 | — |
| scan | truffledata-scanner | java-swar | 1.036 (1.034–1.037) | 0.5 |
| scan | kernel-swar | java-swar | 0.880 (0.737–1.024) | 0.0 |
| scan | csv-byte-record (native) |  | 0.841 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.821 (0.817–0.824) | 0.0 |
| scan | fastcsv |  | 0.365 (0.306–0.423) | 2.6 |
| scan | jackson |  | 0.206 (0.204–0.208) | 855.8 |
| scan | kernel-vector | java-vector64 | 0.068 (0.066–0.069) | 10184.3 |
| strings | csv-string-record (native) |  | 0.490 | — |
| strings | kernel-native | native-avx512-svm | 0.413 (0.391–0.435) | 898.1 |
| strings | kernel-swar | java-swar | 0.338 (0.329–0.347) | 898.1 |
| strings | fastcsv |  | 0.287 (0.284–0.289) | 986.5 |
| strings | univocity |  | 0.254 (0.238–0.270) | 967.2 |
| strings | jackson |  | 0.214 (0.212–0.217) | 855.8 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.193 (0.181–0.206) | 1369.4 |
| strings | truffledata-batch | java-swar | 0.178 (0.167–0.189) | 1362.6 |
| strings | truffledata-batch-bytes | per-byte | 0.173 (0.165–0.180) | 1362.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.171 (0.169–0.172) | 1629.5 |
| strings | truffledata-mapped | java-swar | 0.122 (0.093–0.150) | 1622.7 |
| strings | commons-csv |  | 0.078 (0.077–0.078) | 1009.2 |
| typed | kernel-native | native-avx512-svm | 0.567 (0.564–0.570) | 0.0 |
| typed | kernel-swar | java-swar | 0.525 (0.517–0.533) | 0.0 |
| typed | csv-typed (native) |  | 0.397 | — |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.351 (0.349–0.353) | 456.1 |
| typed | truffledata-plan-bytes | per-byte | 0.295 (0.284–0.305) | 448.9 |
| typed | truffledata-plan | java-swar | 0.252 (0.203–0.300) | 449.3 |
| typed | fastcsv |  | 0.160 (0.158–0.162) | 1229.7 |
| typed | univocity |  | 0.159 (0.157–0.161) | 1210.4 |
| typed | jackson |  | 0.135 (0.126–0.144) | 1099.0 |
| typed | commons-csv |  | 0.065 (0.064–0.066) | 1252.4 |
| utf8 | simdutf-validate (native) |  | 18.203 | — |

## csv-comparison: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 7.740 | — |
| index | index-native | native-avx512-svm | 7.738 (7.690–7.786) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 7.704 (7.600–7.807) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 7.599 (7.409–7.789) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 7.571 | — |
| index | index-native-avx2 | native-avx2-svm | 7.563 (7.483–7.643) | 0.0 |
| index | index-swar | java-swar | 2.637 (2.623–2.650) | 0.0 |
| index | native-index-swar (native) | swar | 2.253 | — |
| index | index-native-swar | native-swar-svm | 2.216 (2.172–2.260) | 0.0 |
| index | native-index-scalar (native) | scalar | 1.091 | — |
| index | index-table | java-table | 1.009 (0.989–1.029) | 0.0 |
| index | index-scalar | java-scalar | 0.675 (0.666–0.685) | 0.0 |
| index | index-vector | java-vector64 | 0.076 (0.068–0.085) | 10225.2 |
| scan | native-indexed-scan (native) | avx512 | 6.411 | — |
| scan | kernel-native | native-avx512-svm | 5.781 (5.780–5.782) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 5.456 (5.415–5.496) | 7.3 |
| scan | kernel-native-small | native-avx512-svm-leaf | 5.140 (4.669–5.611) | 0.0 |
| scan | kernel-swar | java-swar | 2.369 (2.349–2.388) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.015 (1.983–2.048) | 0.5 |
| scan | truffledata-scanner-bytes | per-byte | 1.090 (1.088–1.093) | 0.0 |
| scan | csv-core-scan (native) |  | 1.005 | — |
| scan | csv-byte-record (native) |  | 0.879 | — |
| scan | fastcsv |  | 0.383 (0.375–0.392) | 2.8 |
| scan | jackson |  | 0.375 (0.372–0.377) | 283.1 |
| scan | kernel-vector | java-vector64 | 0.080 (0.075–0.085) | 10225.2 |
| strings | csv-string-record (native) |  | 0.806 | — |
| strings | kernel-native | native-avx512-svm | 0.535 (0.459–0.612) | 283.1 |
| strings | kernel-swar | java-swar | 0.468 (0.408–0.528) | 283.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.388 (0.355–0.421) | 874.7 |
| strings | truffledata-mapped | java-swar | 0.360 (0.342–0.379) | 867.9 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.359 (0.338–0.380) | 843.0 |
| strings | truffledata-batch | java-swar | 0.351 (0.305–0.396) | 836.2 |
| strings | jackson |  | 0.343 (0.315–0.370) | 283.1 |
| strings | fastcsv |  | 0.304 (0.299–0.308) | 333.9 |
| strings | univocity |  | 0.240 (0.230–0.251) | 314.4 |
| strings | truffledata-batch-bytes | per-byte | 0.216 (0.094–0.337) | 835.8 |
| strings | commons-csv |  | 0.136 (0.136–0.136) | 356.5 |
| utf8 | simdutf-validate (native) |  | 34.888 | — |

## csv-comparison: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 6.412 (6.401–6.423) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 6.294 (6.290–6.297) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 6.282 (6.170–6.394) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 6.051 | — |
| index | index-native-avx2 | native-avx2-svm | 5.889 (5.733–6.045) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 5.790 | — |
| index | index-swar | java-swar | 1.661 (1.532–1.791) | 0.0 |
| index | native-index-swar (native) | swar | 1.465 | — |
| index | index-native-swar | native-swar-svm | 1.380 (1.284–1.476) | 0.0 |
| index | native-index-scalar (native) | scalar | 1.216 | — |
| index | index-table | java-table | 0.673 (0.671–0.674) | 0.0 |
| index | index-scalar | java-scalar | 0.531 (0.526–0.536) | 0.0 |
| index | index-vector | java-vector64 | 0.046 (0.041–0.050) | 29345.7 |
| scan | native-indexed-scan (native) | avx512 | 5.285 | — |
| scan | kernel-native | native-avx512-svm | 5.186 (5.178–5.193) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 5.140 (5.139–5.140) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 4.847 (4.776–4.918) | 20.9 |
| scan | kernel-swar | java-swar | 1.694 (1.691–1.697) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.663 (1.651–1.675) | 1.3 |
| scan | truffledata-scanner-bytes | per-byte | 1.561 (1.549–1.573) | 0.0 |
| scan | csv-core-scan (native) |  | 0.836 | — |
| scan | csv-byte-record (native) |  | 0.768 | — |
| scan | fastcsv |  | 0.528 (0.526–0.529) | 8.0 |
| scan | jackson |  | 0.212 (0.211–0.212) | 768.0 |
| scan | kernel-vector | java-vector64 | 0.051 (0.050–0.052) | 29345.7 |
| strings | kernel-native | native-avx512-svm | 0.945 (0.945–0.945) | 768.0 |
| strings | csv-string-record (native) |  | 0.735 | — |
| strings | kernel-swar | java-swar | 0.654 (0.654–0.655) | 767.9 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.618 (0.609–0.628) | 1634.8 |
| strings | truffledata-batch | java-swar | 0.469 (0.466–0.473) | 1562.3 |
| strings | truffledata-mapped | java-swar | 0.452 (0.438–0.465) | 1615.2 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.437 (0.293–0.580) | 1581.9 |
| strings | truffledata-batch-bytes | per-byte | 0.431 (0.400–0.462) | 1561.0 |
| strings | fastcsv |  | 0.368 (0.366–0.370) | 824.0 |
| strings | jackson |  | 0.210 (0.209–0.210) | 768.0 |
| strings | univocity |  | 0.198 (0.186–0.211) | 813.1 |
| strings | commons-csv |  | 0.112 (0.111–0.112) | 844.0 |
| utf8 | simdutf-validate (native) |  | 9.805 | — |

## csv-comparison-v3: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.655 | — |
| index | index-native-avx512 | native-avx512-svm | 8.594 (8.490–8.699) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 8.522 (8.456–8.587) | 0.0 |
| index | index-native | native-avx512-svm | 7.816 (7.093–8.539) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.711 | — |
| index | index-native-avx2 | native-avx2-svm | 6.434 (6.174–6.693) | 0.0 |
| index | index-vector | java-vector256 | 5.048 (4.947–5.150) | 0.0 |
| index | index-swar | java-swar | 2.187 (2.176–2.198) | 0.0 |
| index | native-index-swar (native) | swar | 1.754 | — |
| index | index-table | java-table | 0.855 (0.842–0.868) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.834 | — |
| index | index-native-swar | native-swar-svm | 0.787 (0.554–1.019) | 0.0 |
| index | index-scalar | java-scalar | 0.645 (0.642–0.648) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.452 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.603 (1.539–1.667) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.551 (1.529–1.572) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.437 (1.406–1.468) | 0.0 |
| scan | csv-core-scan (native) |  | 1.127 | — |
| scan | kernel-swar | java-swar | 0.960 (0.950–0.970) | 0.0 |
| scan | csv-byte-record (native) |  | 0.873 | — |
| scan | truffledata-scanner | java-swar | 0.832 (0.779–0.885) | 13.0 |
| scan | truffledata-scanner-bytes | per-byte | 0.763 (0.754–0.772) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 0.755 (0.286–1.224) | 207.3 |
| scan | fastcsv |  | 0.594 (0.589–0.599) | 2.6 |
| scan | jackson |  | 0.188 (0.185–0.190) | 256.0 |
| strings | csv-string-record (native) |  | 0.785 | — |
| strings | kernel-native | native-avx512-svm | 0.422 (0.418–0.427) | 256.0 |
| strings | fastcsv |  | 0.320 (0.320–0.321) | 322.7 |
| strings | kernel-swar | java-swar | 0.263 (0.152–0.373) | 256.0 |
| strings | univocity |  | 0.235 (0.227–0.243) | 505.8 |
| strings | jackson |  | 0.186 (0.173–0.199) | 256.0 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.175 (0.158–0.192) | 611.5 |
| strings | truffledata-batch-bytes | per-byte | 0.175 (0.174–0.176) | 404.3 |
| strings | truffledata-batch | java-swar | 0.171 (0.156–0.186) | 417.2 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.138 (0.138–0.139) | 739.6 |
| strings | truffledata-mapped | java-swar | 0.134 (0.133–0.136) | 545.3 |
| strings | commons-csv |  | 0.077 (0.075–0.080) | 345.6 |
| typed | csv-typed (native) |  | 0.473 | — |
| typed | kernel-native | native-avx512-svm | 0.471 (0.469–0.472) | 0.0 |
| typed | kernel-swar | java-swar | 0.391 (0.384–0.398) | 0.0 |
| typed | truffledata-plan | java-swar | 0.229 (0.227–0.231) | 153.3 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.201 (0.184–0.218) | 347.5 |
| typed | truffledata-plan-bytes | per-byte | 0.199 (0.188–0.209) | 140.3 |
| typed | fastcsv |  | 0.106 (0.102–0.110) | 442.7 |
| typed | jackson |  | 0.096 (0.096–0.096) | 376.0 |
| typed | univocity |  | 0.094 (0.091–0.097) | 625.8 |
| typed | commons-csv |  | 0.051 (0.048–0.054) | 465.6 |
| utf8 | simdutf-validate (native) |  | 102.659 | — |

## csv-comparison-v3: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 10.602 | — |
| index | index-native | native-avx512-svm | 10.467 (10.415–10.520) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 10.344 (10.166–10.523) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 10.144 (9.975–10.312) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 9.964 | — |
| index | index-native-avx2 | native-avx2-svm | 9.856 (9.807–9.906) | 0.0 |
| index | index-vector | java-vector256 | 5.630 (5.284–5.975) | 0.0 |
| index | index-swar | java-swar | 2.397 (2.352–2.443) | 0.0 |
| index | native-index-swar (native) | swar | 1.998 | — |
| index | index-native-swar | native-swar-svm | 1.143 (0.572–1.715) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.922 | — |
| index | index-table | java-table | 0.814 (0.795–0.833) | 0.0 |
| index | index-scalar | java-scalar | 0.552 (0.548–0.555) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 8.170 | — |
| scan | kernel-native | native-avx512-svm | 6.845 (6.788–6.902) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 6.742 (6.549–6.934) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 5.557 (5.357–5.756) | 926.4 |
| scan | kernel-vector | java-vector256 | 4.405 (4.215–4.595) | 0.0 |
| scan | kernel-swar | java-swar | 2.099 (2.031–2.166) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.063 (2.041–2.085) | 58.2 |
| scan | truffledata-scanner-bytes | per-byte | 0.876 (0.856–0.896) | 0.2 |
| scan | csv-core-scan (native) |  | 0.827 | — |
| scan | csv-byte-record (native) |  | 0.644 | — |
| scan | fastcsv |  | 0.361 (0.357–0.365) | 11.9 |
| scan | jackson |  | 0.314 (0.297–0.332) | 284.3 |
| strings | kernel-native | native-avx512-svm | 0.651 (0.648–0.655) | 284.2 |
| strings | csv-string-record (native) |  | 0.606 | — |
| strings | kernel-swar | java-swar | 0.536 (0.535–0.537) | 284.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.415 (0.398–0.431) | 1764.0 |
| strings | truffledata-batch | java-swar | 0.401 (0.395–0.406) | 895.7 |
| strings | jackson |  | 0.333 (0.330–0.337) | 284.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.331 (0.317–0.344) | 2255.5 |
| strings | truffledata-batch-bytes | per-byte | 0.320 (0.313–0.327) | 837.8 |
| strings | truffledata-mapped | java-swar | 0.314 (0.311–0.317) | 1387.2 |
| strings | fastcsv |  | 0.304 (0.286–0.323) | 344.1 |
| strings | univocity |  | 0.198 (0.191–0.206) | 1245.5 |
| strings | commons-csv |  | 0.112 (0.111–0.113) | 363.4 |
| utf8 | simdutf-validate (native) |  | 112.296 | — |

## csv-comparison-v3: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 12.252 | — |
| index | index-native | native-avx512-svm | 12.176 (12.050–12.302) | 0.1 |
| index | index-native-avx512 | native-avx512-svm | 12.124 (11.726–12.522) | 0.1 |
| index | index-native-small | native-avx512-svm-leaf | 11.458 (10.641–12.274) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 10.526 | — |
| index | index-native-avx2 | native-avx2-svm | 8.628 (6.703–10.553) | 0.1 |
| index | index-vector | java-vector256 | 4.975 (4.964–4.986) | 0.0 |
| index | index-swar | java-swar | 1.860 (1.843–1.878) | 0.0 |
| index | native-index-swar (native) | swar | 1.448 | — |
| index | index-native-swar | native-swar-svm | 1.355 (1.267–1.442) | 0.1 |
| index | native-index-scalar (native) | scalar | 1.020 | — |
| index | index-table | java-table | 0.802 (0.802–0.802) | 0.0 |
| index | index-scalar | java-scalar | 0.375 (0.276–0.475) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 9.580 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 8.891 (8.815–8.967) | 0.1 |
| scan | kernel-native | native-avx512-svm | 8.831 (8.753–8.909) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 6.952 (6.824–7.079) | 2662.2 |
| scan | kernel-vector | java-vector256 | 3.359 (2.681–4.036) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.668 (1.637–1.699) | 167.1 |
| scan | kernel-swar | java-swar | 1.611 (1.457–1.765) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 1.507 (1.491–1.522) | 0.6 |
| scan | csv-byte-record (native) |  | 0.818 | — |
| scan | csv-core-scan (native) |  | 0.796 | — |
| scan | fastcsv |  | 0.731 (0.730–0.732) | 34.2 |
| scan | jackson |  | 0.237 (0.236–0.239) | 768.9 |
| strings | kernel-native | native-avx512-svm | 1.797 (1.771–1.823) | 768.5 |
| strings | kernel-swar | java-swar | 0.906 (0.896–0.917) | 768.4 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.795 (0.791–0.798) | 4224.6 |
| strings | csv-string-record (native) |  | 0.769 | — |
| strings | fastcsv |  | 0.710 (0.706–0.715) | 850.9 |
| strings | truffledata-batch-bytes | per-byte | 0.523 (0.455–0.590) | 1562.9 |
| strings | truffledata-batch | java-swar | 0.485 (0.378–0.593) | 1729.5 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.391 (0.314–0.468) | 5595.1 |
| strings | truffledata-mapped | java-swar | 0.326 (0.271–0.381) | 3100.0 |
| strings | jackson |  | 0.227 (0.218–0.237) | 768.9 |
| strings | univocity |  | 0.221 (0.219–0.223) | 3486.1 |
| strings | commons-csv |  | 0.118 (0.117–0.118) | 863.1 |
| utf8 | simdutf-validate (native) |  | 61.365 | — |

## csv-comparison-v3: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 8.096 (8.089–8.103) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 7.889 | — |
| index | index-native-small | native-avx512-svm-leaf | 7.878 (7.717–8.038) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 7.447 (7.370–7.525) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 7.090 | — |
| index | index-native-avx2 | native-avx2-svm | 7.027 (6.824–7.230) | 0.0 |
| index | index-vector | java-vector256 | 3.925 (3.755–4.095) | 0.0 |
| index | index-swar | java-swar | 2.053 (2.047–2.060) | 0.0 |
| index | native-index-swar (native) | swar | 1.875 | — |
| index | index-native-swar | native-swar-svm | 1.408 (0.976–1.841) | 0.0 |
| index | index-table | java-table | 0.750 (0.736–0.764) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.746 | — |
| index | index-scalar | java-scalar | 0.557 (0.547–0.566) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.752 | — |
| scan | kernel-native | native-avx512-svm | 2.088 (2.080–2.096) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 2.024 (1.917–2.132) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.614 (1.599–1.629) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.596 (1.589–1.604) | 918.9 |
| scan | kernel-swar | java-swar | 1.163 (1.160–1.166) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.083 (1.077–1.090) | 57.7 |
| scan | csv-core-scan (native) |  | 1.046 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.800 (0.771–0.829) | 0.3 |
| scan | csv-byte-record (native) |  | 0.784 | — |
| scan | fastcsv |  | 0.469 (0.461–0.478) | 11.6 |
| scan | jackson |  | 0.217 (0.215–0.219) | 856.3 |
| strings | csv-string-record (native) |  | 0.445 | — |
| strings | kernel-native | native-avx512-svm | 0.437 (0.434–0.440) | 898.6 |
| strings | kernel-swar | java-swar | 0.354 (0.350–0.359) | 898.5 |
| strings | fastcsv |  | 0.312 (0.300–0.323) | 995.9 |
| strings | univocity |  | 0.243 (0.243–0.244) | 1890.2 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.235 (0.234–0.236) | 2281.0 |
| strings | jackson |  | 0.224 (0.221–0.227) | 856.3 |
| strings | truffledata-batch | java-swar | 0.222 (0.216–0.229) | 1419.7 |
| strings | truffledata-batch-bytes | per-byte | 0.210 (0.209–0.211) | 1362.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.167 (0.163–0.171) | 2997.2 |
| strings | truffledata-mapped | java-swar | 0.165 (0.165–0.165) | 2135.9 |
| strings | commons-csv |  | 0.081 (0.079–0.083) | 1015.6 |
| typed | kernel-native | native-avx512-svm | 0.761 (0.718–0.804) | 0.0 |
| typed | kernel-swar | java-swar | 0.550 (0.545–0.554) | 0.0 |
| typed | csv-typed (native) |  | 0.391 | — |
| typed | truffledata-plan | java-swar | 0.370 (0.369–0.371) | 505.5 |
| typed | truffledata-plan-bytes | per-byte | 0.347 (0.345–0.349) | 448.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.324 (0.225–0.424) | 1366.7 |
| typed | fastcsv |  | 0.176 (0.171–0.180) | 1239.1 |
| typed | univocity |  | 0.153 (0.150–0.155) | 2133.4 |
| typed | jackson |  | 0.143 (0.137–0.150) | 1099.5 |
| typed | commons-csv |  | 0.067 (0.064–0.069) | 1258.8 |
| utf8 | simdutf-validate (native) |  | 74.085 | — |

## csv-comparison-v3: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 8.474 (8.387–8.562) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 8.211 | — |
| index | index-native-small | native-avx512-svm-leaf | 8.049 (7.729–8.370) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 8.024 (7.786–8.262) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 6.549 (6.514–6.585) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 5.875 | — |
| index | index-vector | java-vector256 | 5.435 (5.343–5.526) | 0.0 |
| index | index-swar | java-swar | 2.261 (2.249–2.272) | 0.0 |
| index | native-index-swar (native) | swar | 1.575 | — |
| index | index-native-swar | native-swar-svm | 1.073 (0.512–1.634) | 0.0 |
| index | index-table | java-table | 0.856 (0.848–0.863) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.770 | — |
| index | index-scalar | java-scalar | 0.648 (0.639–0.658) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.404 | — |
| scan | kernel-native | native-avx512-svm | 1.671 (1.657–1.685) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.657 (1.638–1.675) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.537 (1.535–1.539) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.455 (1.405–1.505) | 1.7 |
| scan | csv-core-scan (native) |  | 1.156 | — |
| scan | kernel-swar | java-swar | 1.085 (1.085–1.085) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.971 (0.954–0.987) | 0.1 |
| scan | csv-byte-record (native) |  | 0.891 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.778 (0.759–0.796) | 0.0 |
| scan | fastcsv |  | 0.521 (0.507–0.535) | 0.6 |
| scan | jackson |  | 0.178 (0.169–0.187) | 256.0 |
| strings | csv-string-record (native) |  | 0.734 | — |
| strings | kernel-native | native-avx512-svm | 0.434 (0.421–0.446) | 256.0 |
| strings | kernel-swar | java-swar | 0.360 (0.346–0.375) | 256.0 |
| strings | fastcsv |  | 0.310 (0.296–0.325) | 320.6 |
| strings | univocity |  | 0.280 (0.278–0.282) | 297.7 |
| strings | jackson |  | 0.190 (0.184–0.197) | 256.0 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.179 (0.179–0.180) | 474.7 |
| strings | truffledata-batch | java-swar | 0.166 (0.162–0.170) | 473.1 |
| strings | truffledata-batch-bytes | per-byte | 0.158 (0.151–0.165) | 473.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.141 (0.140–0.143) | 550.1 |
| strings | truffledata-mapped | java-swar | 0.138 (0.136–0.139) | 548.5 |
| strings | commons-csv |  | 0.075 (0.070–0.080) | 344.3 |
| typed | kernel-native | native-avx512-svm | 0.465 (0.457–0.474) | 0.0 |
| typed | csv-typed (native) |  | 0.445 | — |
| typed | kernel-swar | java-swar | 0.417 (0.414–0.419) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.237 (0.234–0.239) | 203.5 |
| typed | truffledata-plan | java-swar | 0.170 (0.142–0.198) | 201.9 |
| typed | truffledata-plan-bytes | per-byte | 0.144 (0.086–0.203) | 201.8 |
| typed | fastcsv |  | 0.113 (0.109–0.116) | 440.6 |
| typed | univocity |  | 0.106 (0.101–0.112) | 417.7 |
| typed | jackson |  | 0.087 (0.080–0.094) | 376.0 |
| typed | commons-csv |  | 0.051 (0.050–0.053) | 464.3 |
| utf8 | simdutf-validate (native) |  | 29.734 | — |

## csv-comparison-v3: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 4.819 (4.666–4.971) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.798 | — |
| index | index-native-avx512 | native-avx512-svm | 4.796 (4.715–4.877) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.774 | — |
| index | index-native-small | native-avx512-svm-leaf | 4.649 (4.448–4.849) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 4.630 (4.606–4.654) | 0.0 |
| index | index-vector | java-vector256 | 3.142 (3.050–3.234) | 0.0 |
| index | index-swar | java-swar | 1.734 (1.700–1.767) | 0.0 |
| index | index-table | java-table | 0.726 (0.701–0.751) | 0.0 |
| index | native-index-swar (native) | swar | 0.639 | — |
| index | index-native-swar | native-swar-svm | 0.543 (0.537–0.550) | 0.0 |
| index | index-scalar | java-scalar | 0.470 (0.383–0.556) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.411 | — |
| scan | native-indexed-scan (native) | avx512 | 2.199 | — |
| scan | kernel-native | native-avx512-svm | 1.763 (1.758–1.768) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.743 (1.694–1.793) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.550 (1.437–1.664) | 7.2 |
| scan | kernel-vector | java-vector256 | 1.358 (1.318–1.399) | 0.0 |
| scan | kernel-swar | java-swar | 1.062 (1.036–1.089) | 0.0 |
| scan | csv-core-scan (native) |  | 1.051 | — |
| scan | truffledata-scanner | java-swar | 1.016 (0.985–1.048) | 0.5 |
| scan | truffledata-scanner-bytes | per-byte | 0.843 (0.839–0.848) | 0.0 |
| scan | csv-byte-record (native) |  | 0.842 | — |
| scan | fastcsv |  | 0.460 (0.447–0.473) | 2.6 |
| scan | jackson |  | 0.208 (0.207–0.210) | 855.8 |
| strings | csv-string-record (native) |  | 0.488 | — |
| strings | kernel-native | native-avx512-svm | 0.437 (0.429–0.445) | 898.1 |
| strings | kernel-swar | java-swar | 0.327 (0.320–0.335) | 898.1 |
| strings | fastcsv |  | 0.307 (0.301–0.313) | 986.5 |
| strings | univocity |  | 0.259 (0.242–0.276) | 967.2 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.212 (0.206–0.218) | 1369.4 |
| strings | jackson |  | 0.212 (0.212–0.212) | 855.8 |
| strings | truffledata-batch | java-swar | 0.182 (0.180–0.184) | 1362.6 |
| strings | truffledata-batch-bytes | per-byte | 0.163 (0.149–0.178) | 1362.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.161 (0.153–0.169) | 1629.5 |
| strings | truffledata-mapped | java-swar | 0.151 (0.143–0.159) | 1622.7 |
| strings | commons-csv |  | 0.079 (0.078–0.079) | 1009.2 |
| typed | kernel-native | native-avx512-svm | 0.736 (0.735–0.738) | 0.0 |
| typed | kernel-swar | java-swar | 0.506 (0.492–0.520) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.410 (0.402–0.419) | 456.1 |
| typed | csv-typed (native) |  | 0.395 | — |
| typed | truffledata-plan | java-swar | 0.335 (0.333–0.337) | 449.3 |
| typed | truffledata-plan-bytes | per-byte | 0.304 (0.296–0.312) | 448.9 |
| typed | fastcsv |  | 0.167 (0.157–0.176) | 1229.7 |
| typed | univocity |  | 0.162 (0.160–0.164) | 1210.4 |
| typed | jackson |  | 0.138 (0.134–0.142) | 1099.0 |
| typed | commons-csv |  | 0.065 (0.064–0.067) | 1252.4 |
| utf8 | simdutf-validate (native) |  | 18.481 | — |

## csv-comparison-v3: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 4.899 | — |
| index | index-native-avx2 | native-avx2-svm | 4.775 (4.760–4.790) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 4.691 (4.665–4.718) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.684 | — |
| index | index-native-small | native-avx512-svm-leaf | 4.675 (4.556–4.794) | 0.0 |
| index | index-vector | java-vector256 | 3.300 (3.248–3.352) | 0.0 |
| index | index-native | native-avx512-svm | 3.159 (1.720–4.598) | 0.0 |
| index | native-index-swar (native) | swar | 1.431 | — |
| index | index-swar | java-swar | 1.401 (1.396–1.406) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.029 (0.657–1.400) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.639 | — |
| index | index-table | java-table | 0.539 (0.536–0.541) | 0.0 |
| index | index-scalar | java-scalar | 0.354 (0.350–0.357) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 3.829 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 3.400 (3.388–3.411) | 0.0 |
| scan | kernel-native | native-avx512-svm | 3.224 (3.152–3.296) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 3.125 (3.068–3.181) | 7.3 |
| scan | kernel-vector | java-vector256 | 2.586 (2.584–2.588) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.219 (1.216–1.223) | 0.5 |
| scan | kernel-swar | java-swar | 1.207 (1.184–1.229) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 0.671 (0.669–0.674) | 0.0 |
| scan | csv-core-scan (native) |  | 0.551 | — |
| scan | csv-byte-record (native) |  | 0.519 | — |
| scan | fastcsv |  | 0.244 (0.238–0.250) | 2.8 |
| scan | jackson |  | 0.217 (0.215–0.220) | 283.1 |
| strings | csv-string-record (native) |  | 0.459 | — |
| strings | kernel-native | native-avx512-svm | 0.443 (0.429–0.457) | 283.1 |
| strings | kernel-swar | java-swar | 0.365 (0.363–0.367) | 283.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.311 (0.307–0.314) | 843.0 |
| strings | truffledata-batch | java-swar | 0.275 (0.274–0.275) | 836.2 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.271 (0.265–0.276) | 874.7 |
| strings | truffledata-mapped | java-swar | 0.241 (0.228–0.253) | 867.9 |
| strings | jackson |  | 0.224 (0.223–0.226) | 283.1 |
| strings | fastcsv |  | 0.221 (0.220–0.222) | 333.9 |
| strings | truffledata-batch-bytes | per-byte | 0.178 (0.120–0.235) | 835.8 |
| strings | univocity |  | 0.146 (0.143–0.148) | 314.4 |
| strings | commons-csv |  | 0.075 (0.074–0.076) | 356.5 |
| utf8 | simdutf-validate (native) |  | 30.122 | — |

## csv-comparison-v3: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 12.664 | — |
| index | index-native | native-avx512-svm | 12.258 (12.198–12.318) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 12.128 | — |
| index | index-native-avx512 | native-avx512-svm | 11.991 (11.895–12.087) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 11.883 (11.577–12.189) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 11.798 (11.701–11.896) | 0.0 |
| index | index-vector | java-vector256 | 6.600 (6.402–6.799) | 0.0 |
| index | index-swar | java-swar | 2.458 (2.451–2.465) | 0.0 |
| index | native-index-swar (native) | swar | 2.233 | — |
| index | native-index-scalar (native) | scalar | 2.075 | — |
| index | index-native-swar | native-swar-svm | 1.965 (1.753–2.178) | 0.0 |
| index | index-table | java-table | 0.888 (0.879–0.898) | 0.0 |
| index | index-scalar | java-scalar | 0.873 (0.843–0.904) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 10.390 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 9.237 (9.130–9.344) | 0.0 |
| scan | kernel-native | native-avx512-svm | 8.664 (8.588–8.740) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 8.556 (8.346–8.766) | 20.9 |
| scan | kernel-vector | java-vector256 | 5.671 (5.621–5.721) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 2.723 (2.720–2.725) | 0.0 |
| scan | kernel-swar | java-swar | 2.333 (2.317–2.350) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.247 (2.216–2.278) | 1.3 |
| scan | csv-core-scan (native) |  | 1.464 | — |
| scan | fastcsv |  | 1.225 (1.206–1.244) | 8.0 |
| scan | csv-byte-record (native) |  | 1.080 | — |
| scan | jackson |  | 0.351 (0.344–0.358) | 768.0 |
| strings | kernel-native | native-avx512-svm | 2.795 (2.742–2.848) | 768.0 |
| strings | kernel-swar | java-swar | 1.371 (1.337–1.405) | 767.9 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 1.215 (1.165–1.265) | 1581.9 |
| strings | csv-string-record (native) |  | 1.030 | — |
| strings | fastcsv |  | 1.012 (1.000–1.025) | 824.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.963 (0.954–0.972) | 1634.8 |
| strings | truffledata-batch-bytes | per-byte | 0.888 (0.832–0.944) | 1561.0 |
| strings | truffledata-batch | java-swar | 0.861 (0.853–0.869) | 1562.3 |
| strings | truffledata-mapped | java-swar | 0.692 (0.681–0.704) | 1615.2 |
| strings | univocity |  | 0.451 (0.446–0.455) | 813.1 |
| strings | jackson |  | 0.361 (0.361–0.361) | 768.0 |
| strings | commons-csv |  | 0.164 (0.159–0.168) | 844.0 |
| utf8 | simdutf-validate (native) |  | 33.918 | — |

## csv_jvm: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-ffm | 8.651 (8.605–8.696) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 8.461 | — |
| index | index-vector | java-vector512 | 6.636 (6.544–6.729) | 0.0 |
| index | index-native | native-avx512-ffm | 6.578 (6.430–6.725) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.436 | — |
| index | index-native-avx512 | native-avx512-ffm | 6.247 (5.903–6.592) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.095 (4.801–5.389) | 0.0 |
| index | index-swar | java-swar | 2.473 (2.424–2.521) | 0.0 |
| index | native-index-swar (native) | swar | 1.752 | — |
| index | index-native-swar | native-swar-ffm | 1.451 (1.418–1.484) | 0.0 |
| index | index-scalar | java-scalar | 1.178 (1.143–1.214) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.838 | — |
| index | index-table | java-table | 0.835 (0.713–0.957) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 2.912 (2.900–2.923) | 0.0 |
| scan | kernel-vector | java-vector512 | 2.891 (2.880–2.902) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 2.652 (2.572–2.731) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.311 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 1.675 (1.665–1.684) | 207.3 |
| scan | kernel-swar | java-swar | 1.648 (1.644–1.653) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.433 (1.433–1.433) | 13.0 |
| scan | csv-core-scan (native) |  | 1.095 | — |
| scan | fastcsv |  | 1.031 (1.017–1.044) | 2.0 |
| scan | truffledata-scanner-bytes | per-byte | 0.940 (0.898–0.983) | 0.0 |
| scan | csv-byte-record (native) |  | 0.885 | — |
| scan | jackson |  | 0.363 (0.336–0.390) | 384.1 |
| strings | kernel-native | native-avx512-ffm | 0.870 (0.854–0.885) | 0.0 |
| strings | csv-string-record (native) |  | 0.771 | — |
| strings | kernel-swar | java-swar | 0.672 (0.622–0.722) | 0.0 |
| strings | truffledata-batch | java-swar | 0.490 (0.480–0.500) | 137.3 |
| strings | truffledata-mapped | java-swar | 0.332 (0.316–0.347) | 329.3 |
| strings | truffledata-batch-bytes | per-byte | 0.286 (0.166–0.405) | 124.3 |
| strings | fastcsv |  | 0.285 (0.171–0.400) | 466.1 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.211 (0.139–0.283) | 523.6 |
| strings | univocity |  | 0.179 (0.011–0.348) | 641.8 |
| strings | jackson |  | 0.172 (0.013–0.332) | 384.1 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.038 (0.035–0.040) | 331.6 |
| strings | commons-csv |  | 0.033 (0.028–0.038) | 481.3 |
| typed | kernel-native | native-avx512-ffm | 0.669 (0.666–0.671) | 0.0 |
| typed | kernel-swar | java-swar | 0.582 (0.569–0.596) | 0.0 |
| typed | csv-typed (native) |  | 0.456 | — |
| typed | truffledata-plan-bytes | per-byte | 0.205 (0.204–0.206) | 140.3 |
| typed | truffledata-plan | java-swar | 0.183 (0.173–0.193) | 153.3 |
| typed | jackson |  | 0.166 (0.143–0.188) | 456.1 |
| typed | fastcsv |  | 0.138 (0.096–0.180) | 538.1 |
| typed | commons-csv |  | 0.119 (0.112–0.127) | 601.3 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.083 (0.037–0.128) | 347.6 |
| typed | univocity |  | 0.071 (0.003–0.139) | 713.9 |
| utf8 | simdutf-validate (native) |  | 106.838 | — |

## csv_jvm: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 10.259 | — |
| index | index-native-small | native-avx512-ffm | 9.313 (8.729–9.897) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 8.892 | — |
| index | index-native-avx512 | native-avx512-ffm | 8.041 (7.958–8.123) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 8.040 (7.784–8.296) | 0.0 |
| index | index-vector | java-vector512 | 7.851 (7.657–8.045) | 0.0 |
| index | index-native | native-avx512-ffm | 7.819 (7.039–8.598) | 0.0 |
| index | index-swar | java-swar | 2.595 (2.522–2.669) | 0.0 |
| index | native-index-swar (native) | swar | 1.987 | — |
| index | index-native-swar | native-swar-ffm | 1.822 (1.806–1.838) | 0.0 |
| index | index-table | java-table | 0.901 (0.875–0.926) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.852 | — |
| index | index-scalar | java-scalar | 0.668 (0.657–0.680) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 7.399 | — |
| scan | kernel-native | native-avx512-ffm | 6.882 (6.851–6.912) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 6.843 (5.938–7.748) | 0.1 |
| scan | kernel-vector | java-vector512 | 6.581 (6.553–6.609) | 0.0 |
| scan | kernel-swar | java-swar | 2.323 (2.164–2.482) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.263 (2.100–2.427) | 58.1 |
| scan | truffledata-scanner-native | native-avx512-ffm | 2.014 (0.023–4.004) | 926.4 |
| scan | truffledata-scanner-bytes | per-byte | 1.058 (1.057–1.060) | 0.2 |
| scan | csv-core-scan (native) |  | 0.711 | — |
| scan | csv-byte-record (native) |  | 0.567 | — |
| scan | fastcsv |  | 0.396 (0.390–0.403) | 9.1 |
| scan | jackson |  | 0.286 (0.244–0.328) | 332.3 |
| strings | csv-string-record (native) |  | 0.531 | — |
| strings | kernel-native | native-avx512-ffm | 0.405 (0.397–0.413) | 0.0 |
| strings | kernel-swar | java-swar | 0.382 (0.378–0.387) | 0.0 |
| strings | jackson |  | 0.370 (0.345–0.396) | 332.3 |
| strings | fastcsv |  | 0.337 (0.307–0.366) | 405.4 |
| strings | truffledata-batch-bytes | per-byte | 0.298 (0.288–0.307) | 545.8 |
| strings | truffledata-mapped | java-swar | 0.273 (0.272–0.275) | 1119.2 |
| strings | commons-csv |  | 0.248 (0.237–0.259) | 418.1 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.240 (0.183–0.297) | 1472.1 |
| strings | univocity |  | 0.172 (0.031–0.314) | 1301.5 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.165 (0.018–0.312) | 1987.6 |
| strings | truffledata-batch | java-swar | 0.159 (0.013–0.305) | 603.7 |
| utf8 | simdutf-validate (native) |  | 99.970 | — |

## csv_jvm: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-ffm | 16.752 (16.021–17.483) | 0.2 |
| index | native-index-avx512 (native) | avx512 | 16.347 | — |
| index | index-native-avx512 | native-avx512-ffm | 13.092 (12.864–13.321) | 0.0 |
| index | index-native | native-avx512-ffm | 12.939 (12.532–13.346) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 12.799 | — |
| index | index-native-avx2 | native-avx2-ffm | 10.927 (10.725–11.129) | 0.0 |
| index | index-vector | java-vector512 | 9.051 (9.051–9.052) | 0.0 |
| index | index-swar | java-swar | 2.891 (2.884–2.897) | 0.0 |
| index | index-scalar | java-scalar | 2.388 (2.384–2.391) | 0.0 |
| index | index-native-swar | native-swar-ffm | 2.110 (2.100–2.121) | 0.0 |
| index | native-index-swar (native) | swar | 2.059 | — |
| index | native-index-scalar (native) | scalar | 1.820 | — |
| index | index-table | java-table | 0.939 (0.892–0.985) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 13.931 (13.461–14.400) | 0.2 |
| scan | native-indexed-scan (native) | avx512 | 12.004 | — |
| scan | kernel-native | native-avx512-ffm | 10.590 (9.971–11.210) | 0.0 |
| scan | kernel-vector | java-vector512 | 8.113 (8.025–8.202) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 4.084 (3.649–4.519) | 0.5 |
| scan | truffledata-scanner-native | native-avx512-ffm | 3.304 (3.140–3.467) | 2662.0 |
| scan | truffledata-scanner | java-swar | 2.517 (2.365–2.669) | 167.0 |
| scan | kernel-swar | java-swar | 2.483 (2.276–2.691) | 0.0 |
| scan | fastcsv |  | 1.880 (1.826–1.934) | 26.2 |
| scan | csv-core-scan (native) |  | 1.367 | — |
| scan | csv-byte-record (native) |  | 0.979 | — |
| scan | jackson |  | 0.354 (0.346–0.362) | 833.0 |
| strings | kernel-native | native-avx512-ffm | 1.823 (1.215–2.430) | 0.0 |
| strings | kernel-swar | java-swar | 1.515 (1.503–1.526) | 0.0 |
| strings | truffledata-batch-bytes | per-byte | 1.500 (1.485–1.514) | 771.6 |
| strings | fastcsv |  | 1.147 (0.977–1.317) | 923.0 |
| strings | truffledata-mapped | java-swar | 0.926 (0.925–0.928) | 2340.7 |
| strings | csv-string-record (native) |  | 0.905 | — |
| strings | truffledata-batch | java-swar | 0.846 (0.428–1.264) | 938.1 |
| strings | univocity |  | 0.591 (0.584–0.598) | 3558.3 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.417 (0.149–0.684) | 4836.1 |
| strings | jackson |  | 0.372 (0.370–0.373) | 833.0 |
| strings | commons-csv |  | 0.343 (0.335–0.352) | 933.6 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.047 (0.019–0.074) | 3433.6 |
| utf8 | simdutf-validate (native) |  | 88.292 | — |

## csv_jvm: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 3.659 | — |
| index | native-index-avx512 (native) | avx512 | 3.571 | — |
| index | index-vector | java-vector512 | 2.951 (2.884–3.018) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 2.937 (2.933–2.941) | 0.0 |
| index | index-native | native-avx512-ffm | 2.924 (2.911–2.937) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 2.839 (2.753–2.925) | 0.0 |
| index | index-native-small | native-avx512-ffm | 2.197 (0.866–3.528) | 0.1 |
| index | index-swar | java-swar | 1.508 (1.500–1.516) | 0.0 |
| index | native-index-swar (native) | swar | 1.160 | — |
| index | index-native-swar | native-swar-ffm | 1.059 (1.053–1.064) | 0.0 |
| index | index-table | java-table | 0.743 (0.743–0.743) | 0.0 |
| index | index-scalar | java-scalar | 0.569 (0.569–0.569) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.340 | — |
| scan | kernel-native-small | native-avx512-ffm | 1.681 (1.674–1.688) | 0.1 |
| scan | kernel-vector | java-vector512 | 1.666 (1.620–1.712) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 1.549 (1.548–1.549) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 1.534 | — |
| scan | kernel-swar | java-swar | 1.013 (0.986–1.041) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.935 (0.889–0.981) | 57.8 |
| scan | truffledata-scanner-native | native-avx512-ffm | 0.904 (0.781–1.027) | 919.0 |
| scan | csv-core-scan (native) |  | 0.605 | — |
| scan | csv-byte-record (native) |  | 0.599 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.592 (0.589–0.594) | 0.3 |
| scan | fastcsv |  | 0.403 (0.403–0.403) | 9.1 |
| scan | jackson |  | 0.201 (0.187–0.215) | 1227.9 |
| strings | kernel-native | native-avx512-ffm | 0.494 (0.494–0.494) | 46.3 |
| strings | kernel-swar | java-swar | 0.397 (0.397–0.398) | 46.3 |
| strings | csv-string-record (native) |  | 0.331 | — |
| strings | truffledata-batch | java-swar | 0.291 (0.288–0.294) | 543.9 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.278 (0.271–0.285) | 1405.2 |
| strings | truffledata-batch-bytes | per-byte | 0.235 (0.234–0.235) | 486.4 |
| strings | univocity |  | 0.202 (0.185–0.220) | 2269.9 |
| strings | jackson |  | 0.196 (0.178–0.214) | 1227.9 |
| strings | fastcsv |  | 0.193 (0.144–0.241) | 1380.9 |
| strings | truffledata-mapped | java-swar | 0.171 (0.168–0.173) | 2085.8 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.152 (0.120–0.185) | 2947.2 |
| strings | commons-csv |  | 0.095 (0.069–0.120) | 1393.9 |
| typed | kernel-native | native-avx512-ffm | 0.650 (0.650–0.650) | 0.0 |
| typed | kernel-swar | java-swar | 0.503 (0.502–0.503) | 0.0 |
| typed | truffledata-plan | java-swar | 0.328 (0.327–0.329) | 483.1 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.324 (0.322–0.327) | 1344.2 |
| typed | truffledata-plan-bytes | per-byte | 0.276 (0.274–0.278) | 426.0 |
| typed | csv-typed (native) |  | 0.262 | — |
| typed | jackson |  | 0.154 (0.152–0.157) | 1567.1 |
| typed | univocity |  | 0.142 (0.128–0.157) | 2417.1 |
| typed | fastcsv |  | 0.140 (0.107–0.173) | 1720.9 |
| typed | commons-csv |  | 0.097 (0.097–0.098) | 1733.1 |
| utf8 | simdutf-validate (native) |  | 28.583 | — |

## csv_jvm: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.695 | — |
| index | index-native-small | native-avx512-ffm | 8.566 (8.537–8.594) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.716 | — |
| index | index-vector | java-vector512 | 6.641 (6.443–6.839) | 0.0 |
| index | index-native | native-avx512-ffm | 6.299 (6.236–6.363) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 5.993 (5.891–6.096) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.116 (5.103–5.128) | 0.0 |
| index | index-swar | java-swar | 2.395 (2.235–2.555) | 0.0 |
| index | native-index-swar (native) | swar | 1.763 | — |
| index | index-native-swar | native-swar-ffm | 1.564 (1.512–1.615) | 0.0 |
| index | index-scalar | java-scalar | 1.239 (1.235–1.243) | 0.0 |
| index | index-table | java-table | 0.859 (0.827–0.891) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.835 | — |
| scan | kernel-native-small | native-avx512-ffm | 3.300 (3.281–3.320) | 0.0 |
| scan | kernel-vector | java-vector512 | 2.833 (2.679–2.987) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 2.747 (2.664–2.831) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.444 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 1.930 (1.266–2.595) | 1.7 |
| scan | kernel-swar | java-swar | 1.752 (1.747–1.757) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.528 (1.527–1.528) | 0.1 |
| scan | csv-core-scan (native) |  | 1.135 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.924 (0.824–1.024) | 0.0 |
| scan | fastcsv |  | 0.861 (0.829–0.893) | 0.0 |
| scan | csv-byte-record (native) |  | 0.861 | — |
| scan | jackson |  | 0.303 (0.276–0.331) | 384.0 |
| strings | kernel-native | native-avx512-ffm | 0.887 (0.882–0.892) | 0.0 |
| strings | csv-string-record (native) |  | 0.778 | — |
| strings | kernel-swar | java-swar | 0.748 (0.747–0.749) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.569 (0.548–0.589) | 194.7 |
| strings | truffledata-batch | java-swar | 0.507 (0.507–0.507) | 193.1 |
| strings | fastcsv |  | 0.493 (0.482–0.503) | 464.0 |
| strings | univocity |  | 0.426 (0.424–0.427) | 433.7 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.365 (0.345–0.385) | 334.1 |
| strings | truffledata-mapped | java-swar | 0.361 (0.347–0.374) | 332.5 |
| strings | jackson |  | 0.302 (0.299–0.305) | 384.0 |
| strings | commons-csv |  | 0.185 (0.180–0.189) | 480.0 |
| strings | truffledata-batch-bytes | per-byte | 0.048 (0.019–0.077) | 193.0 |
| typed | kernel-native | native-avx512-ffm | 0.685 (0.680–0.690) | 0.0 |
| typed | kernel-swar | java-swar | 0.598 (0.596–0.601) | 0.0 |
| typed | csv-typed (native) |  | 0.418 | — |
| typed | truffledata-plan | java-swar | 0.320 (0.318–0.321) | 201.9 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.308 (0.305–0.311) | 203.5 |
| typed | fastcsv |  | 0.242 (0.241–0.244) | 536.0 |
| typed | univocity |  | 0.211 (0.204–0.217) | 601.7 |
| typed | jackson |  | 0.189 (0.187–0.190) | 456.0 |
| typed | truffledata-plan-bytes | per-byte | 0.175 (0.094–0.255) | 201.8 |
| typed | commons-csv |  | 0.125 (0.122–0.127) | 648.0 |
| utf8 | simdutf-validate (native) |  | 34.526 | — |

## csv_jvm: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 3.037 | — |
| index | index-native-small | native-avx512-ffm | 2.972 (2.968–2.976) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 2.938 | — |
| index | index-vector | java-vector512 | 2.407 (2.379–2.436) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 2.398 (2.371–2.424) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 2.387 (2.362–2.412) | 0.0 |
| index | index-native | native-avx512-ffm | 2.343 (2.316–2.370) | 0.0 |
| index | index-swar | java-swar | 1.460 (1.458–1.462) | 0.0 |
| index | native-index-swar (native) | swar | 1.101 | — |
| index | index-native-swar | native-swar-ffm | 0.943 (0.887–1.000) | 0.0 |
| index | index-table | java-table | 0.735 (0.734–0.735) | 0.0 |
| index | index-scalar | java-scalar | 0.499 (0.491–0.508) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.410 | — |
| scan | kernel-native-small | native-avx512-ffm | 1.615 (1.596–1.635) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-ffm | 1.515 (1.498–1.532) | 7.3 |
| scan | native-indexed-scan (native) | avx512 | 1.409 | — |
| scan | kernel-native | native-avx512-ffm | 1.281 (1.138–1.423) | 0.0 |
| scan | kernel-vector | java-vector512 | 1.256 (1.029–1.483) | 0.0 |
| scan | kernel-swar | java-swar | 0.872 (0.764–0.981) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.848 (0.830–0.865) | 0.5 |
| scan | csv-core-scan (native) |  | 0.614 | — |
| scan | csv-byte-record (native) |  | 0.572 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.554 (0.533–0.575) | 0.0 |
| scan | fastcsv |  | 0.378 (0.371–0.385) | 0.1 |
| scan | jackson |  | 0.197 (0.176–0.218) | 1227.4 |
| strings | kernel-native | native-avx512-ffm | 0.461 (0.455–0.468) | 46.0 |
| strings | kernel-swar | java-swar | 0.396 (0.392–0.401) | 46.0 |
| strings | csv-string-record (native) |  | 0.325 | — |
| strings | truffledata-batch-native | native-avx512-ffm | 0.278 (0.250–0.306) | 493.3 |
| strings | truffledata-batch | java-swar | 0.259 (0.256–0.262) | 486.5 |
| strings | fastcsv |  | 0.235 (0.216–0.254) | 1371.4 |
| strings | truffledata-batch-bytes | per-byte | 0.226 (0.222–0.229) | 486.0 |
| strings | univocity |  | 0.222 (0.218–0.225) | 1346.7 |
| strings | jackson |  | 0.200 (0.188–0.212) | 1227.4 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.182 (0.170–0.193) | 1579.4 |
| strings | truffledata-mapped | java-swar | 0.178 (0.176–0.181) | 1572.5 |
| strings | commons-csv |  | 0.124 (0.122–0.126) | 1387.4 |
| typed | kernel-native | native-avx512-ffm | 0.571 (0.559–0.584) | 0.0 |
| typed | kernel-swar | java-swar | 0.480 (0.475–0.486) | 0.0 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.328 (0.264–0.391) | 433.1 |
| typed | truffledata-plan-bytes | per-byte | 0.268 (0.266–0.270) | 424.9 |
| typed | csv-typed (native) |  | 0.259 | — |
| typed | truffledata-plan | java-swar | 0.242 (0.206–0.278) | 449.3 |
| typed | fastcsv |  | 0.169 (0.166–0.172) | 1710.6 |
| typed | univocity |  | 0.169 (0.168–0.169) | 1493.9 |
| typed | jackson |  | 0.135 (0.109–0.162) | 1566.5 |
| typed | commons-csv |  | 0.089 (0.085–0.094) | 1726.6 |
| utf8 | simdutf-validate (native) |  | 8.349 | — |

## csv_jvm: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 3.878 | — |
| index | native-index-avx2 (native) | avx2 | 3.859 | — |
| index | index-native-small | native-avx512-ffm | 3.526 (3.214–3.837) | 0.0 |
| index | index-native | native-avx512-ffm | 3.410 (3.363–3.457) | 0.0 |
| index | index-vector | java-vector512 | 3.407 (3.070–3.744) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 3.102 (2.977–3.228) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 3.079 (2.732–3.426) | 0.0 |
| index | index-swar | java-swar | 1.721 (1.713–1.728) | 0.0 |
| index | native-index-swar (native) | swar | 1.279 | — |
| index | index-native-swar | native-swar-ffm | 1.134 (1.132–1.136) | 0.0 |
| index | index-table | java-table | 0.861 (0.860–0.862) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.594 | — |
| index | index-scalar | java-scalar | 0.418 (0.418–0.418) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 3.251 (3.244–3.258) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 3.190 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 3.024 (3.020–3.028) | 7.3 |
| scan | kernel-vector | java-vector512 | 2.944 (2.595–3.293) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 2.585 (2.565–2.605) | 0.0 |
| scan | kernel-swar | java-swar | 1.559 (1.519–1.599) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.470 (1.341–1.599) | 0.5 |
| scan | truffledata-scanner-bytes | per-byte | 0.688 (0.687–0.689) | 0.0 |
| scan | csv-core-scan (native) |  | 0.474 | — |
| scan | csv-byte-record (native) |  | 0.449 | — |
| scan | jackson |  | 0.310 (0.291–0.329) | 331.1 |
| scan | fastcsv |  | 0.263 (0.260–0.266) | 0.1 |
| strings | csv-string-record (native) |  | 0.412 | — |
| strings | jackson |  | 0.316 (0.311–0.320) | 331.1 |
| strings | kernel-native | native-avx512-ffm | 0.294 (0.287–0.300) | 0.0 |
| strings | kernel-swar | java-swar | 0.276 (0.260–0.292) | 0.0 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.250 (0.246–0.254) | 607.7 |
| strings | truffledata-mapped | java-swar | 0.245 (0.243–0.246) | 600.8 |
| strings | truffledata-batch | java-swar | 0.225 (0.214–0.236) | 545.1 |
| strings | fastcsv |  | 0.220 (0.204–0.237) | 395.1 |
| strings | univocity |  | 0.210 (0.209–0.211) | 370.4 |
| strings | truffledata-batch-bytes | per-byte | 0.208 (0.207–0.208) | 544.7 |
| strings | commons-csv |  | 0.196 (0.195–0.196) | 412.7 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.179 (0.105–0.253) | 552.0 |
| utf8 | simdutf-validate (native) |  | 12.800 | — |

## csv_jvm: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 8.862 | — |
| index | native-index-avx512 (native) | avx512 | 8.524 | — |
| index | index-native-small | native-avx512-ffm | 8.474 (8.391–8.556) | 0.1 |
| index | index-native | native-avx512-ffm | 5.936 (5.929–5.943) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 5.761 (5.537–5.985) | 0.0 |
| index | index-vector | java-vector512 | 5.279 (5.159–5.399) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.169 (4.811–5.527) | 0.0 |
| index | native-index-swar (native) | swar | 1.715 | — |
| index | index-swar | java-swar | 1.582 (1.578–1.586) | 0.0 |
| index | index-native-swar | native-swar-ffm | 1.516 (1.515–1.516) | 0.0 |
| index | index-scalar | java-scalar | 1.047 (0.975–1.119) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.905 | — |
| index | index-table | java-table | 0.580 (0.580–0.581) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 7.249 | — |
| scan | kernel-native-small | native-avx512-ffm | 6.905 (6.662–7.148) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-ffm | 6.376 (6.130–6.623) | 20.9 |
| scan | kernel-vector | java-vector512 | 5.293 (5.171–5.416) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 5.000 (4.756–5.244) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 2.473 (2.223–2.723) | 0.0 |
| scan | kernel-swar | java-swar | 1.610 (1.594–1.625) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.471 (1.327–1.615) | 1.3 |
| scan | fastcsv |  | 0.996 (0.984–1.009) | 0.2 |
| scan | csv-core-scan (native) |  | 0.922 | — |
| scan | csv-byte-record (native) |  | 0.854 | — |
| scan | jackson |  | 0.343 (0.336–0.350) | 832.0 |
| strings | kernel-native | native-avx512-ffm | 1.351 (1.307–1.394) | 0.0 |
| strings | truffledata-mapped-native | native-avx512-ffm | 1.186 (1.183–1.188) | 875.8 |
| strings | truffledata-batch-bytes | per-byte | 1.028 (1.023–1.033) | 769.8 |
| strings | fastcsv |  | 0.898 (0.875–0.921) | 896.2 |
| strings | kernel-swar | java-swar | 0.848 (0.740–0.956) | 0.0 |
| strings | truffledata-batch | java-swar | 0.807 (0.803–0.811) | 771.1 |
| strings | csv-string-record (native) |  | 0.791 | — |
| strings | truffledata-mapped | java-swar | 0.700 (0.684–0.715) | 856.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.672 (0.307–1.037) | 790.8 |
| strings | univocity |  | 0.380 (0.359–0.400) | 885.0 |
| strings | jackson |  | 0.283 (0.280–0.286) | 832.0 |
| strings | commons-csv |  | 0.194 (0.184–0.203) | 912.2 |
| utf8 | simdutf-validate (native) |  | 26.642 | — |

## Size sweep (kernel crossovers; GB/s, one fork)

| Binary | Workload | Dataset | Engine | 256 B | 1 KiB | 4 KiB | 16 KiB | 64 KiB | 256 KiB | 1024 KiB | 4096 KiB | 16384 KiB |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| csv-comparison | index | mixed | index-native | 1.13 | 2.15 | 3.95 | 0.99 | 4.69 | 4.47 | 4.33 | 3.19 | 3.15 |
| csv-comparison | index | mixed | index-native-small | 1.12 | 1.60 | 3.74 | 4.46 | 4.62 | 4.76 | 4.79 | 3.40 | 3.11 |
| csv-comparison | index | mixed | index-swar | 0.68 | 0.91 | 1.18 | 1.31 | 1.21 | 1.32 | 1.22 | 1.13 | 1.15 |
| csv-comparison | index | numeric | index-native | 0.93 | 2.19 | 3.50 | 3.89 | 4.31 | 4.00 | 4.24 | 3.96 | 4.25 |
| csv-comparison | index | numeric | index-native-small | 0.93 | 2.19 | 3.50 | 3.27 | 4.32 | 4.11 | 4.13 | 4.28 | 4.09 |
| csv-comparison | index | numeric | index-swar | 0.64 | 1.02 | 1.27 | 1.33 | 1.30 | 1.35 | 1.10 | 1.18 | 1.21 |
| csv-comparison | scan | mixed | kernel-native-small | 0.60 | 0.90 | 1.12 | 1.19 | 1.30 | 1.18 | 1.17 | 1.07 | 1.06 |
| csv-comparison | scan | mixed | sweep-native | 0.28 | 0.49 | 0.56 | 0.88 | 0.88 | 0.91 | 0.92 | 0.94 | 0.93 |
| csv-comparison | scan | mixed | sweep-swar | 0.31 | 0.48 | 0.53 | 0.58 | 0.61 | 0.61 | 0.64 | 0.63 | 0.52 |
| csv-comparison | scan | mixed | truffledata-scanner-bytes | 0.39 | 0.53 | 0.64 | 0.62 | 0.53 | 0.49 | 0.48 | 0.51 | 0.52 |
| csv-comparison | scan | numeric | kernel-native-small | 0.50 | 0.71 | 0.83 | 0.87 | 0.90 | 0.87 | 0.89 | 0.79 | 0.91 |
| csv-comparison | scan | numeric | sweep-native | 0.26 | 0.47 | 0.54 | 0.65 | 0.63 | 0.57 | 0.67 | 0.56 | 0.80 |
| csv-comparison | scan | numeric | sweep-swar | 0.29 | 0.32 | 0.44 | 0.46 | 0.49 | 0.48 | 0.55 | 0.54 | 0.54 |
| csv-comparison | scan | numeric | truffledata-scanner-bytes | 0.33 | 0.45 | 0.49 | 0.50 | 0.49 | 0.45 | 0.45 | 0.45 | 0.42 |
| csv-comparison | strings | mixed | sweep-batch-native | 0.05 | 0.10 | 0.16 | 0.18 | 0.19 | 0.17 | 0.16 | 0.03 | 0.13 |
| csv-comparison | strings | mixed | sweep-batch-swar | 0.05 | 0.10 | 0.13 | 0.15 | 0.14 | 0.14 | 0.15 | 0.12 | 0.09 |
| csv-comparison | strings | mixed | truffledata-batch-bytes | 0.06 | 0.10 | 0.15 | 0.17 | 0.15 | 0.15 | 0.14 | 0.12 | 0.11 |
| csv-comparison | strings | numeric | sweep-batch-native | 0.06 | 0.10 | 0.13 | 0.14 | 0.13 | 0.13 | 0.12 | 0.12 | 0.11 |
| csv-comparison | strings | numeric | sweep-batch-swar | 0.06 | 0.10 | 0.11 | 0.11 | 0.12 | 0.13 | 0.09 | 0.12 | 0.11 |
| csv-comparison | strings | numeric | truffledata-batch-bytes | 0.07 | 0.11 | 0.09 | 0.13 | 0.13 | 0.11 | 0.11 | 0.11 | 0.10 |
| csv-comparison-v3 | index | mixed | index-native | 1.61 | 3.37 | 6.18 | 7.77 | 7.90 | 8.05 | 7.68 | 5.06 | 4.85 |
| csv-comparison-v3 | index | mixed | index-native-small | 1.74 | 3.48 | 6.20 | 7.57 | 7.94 | 7.93 | 7.84 | 4.93 | 4.57 |
| csv-comparison-v3 | index | mixed | index-swar | 1.20 | 1.56 | 2.13 | 2.26 | 1.90 | 2.23 | 2.02 | 1.80 | 1.78 |
| csv-comparison-v3 | index | numeric | index-native | 1.43 | 3.59 | 6.54 | 7.99 | 8.06 | 8.37 | 7.77 | 8.24 | 8.00 |
| csv-comparison-v3 | index | numeric | index-native-small | 1.31 | 3.75 | 6.59 | 8.30 | 8.04 | 8.23 | 8.27 | 8.37 | 7.62 |
| csv-comparison-v3 | index | numeric | index-swar | 1.05 | 1.83 | 2.13 | 2.23 | 2.20 | 2.16 | 2.15 | 2.20 | 2.25 |
| csv-comparison-v3 | scan | mixed | kernel-native-small | 1.01 | 1.48 | 2.00 | 2.08 | 2.06 | 2.06 | 2.01 | 1.76 | 1.70 |
| csv-comparison-v3 | scan | mixed | sweep-native | 0.50 | 0.79 | 1.15 | 1.36 | 1.56 | 1.56 | 1.60 | 1.46 | 1.62 |
| csv-comparison-v3 | scan | mixed | sweep-swar | 0.51 | 0.60 | 0.92 | 0.97 | 0.98 | 0.99 | 0.97 | 1.03 | 1.06 |
| csv-comparison-v3 | scan | mixed | truffledata-scanner-bytes | 0.66 | 1.00 | 1.07 | 1.05 | 0.92 | 0.52 | 0.79 | 0.82 | 0.83 |
| csv-comparison-v3 | scan | numeric | kernel-native-small | 0.83 | 1.26 | 1.42 | 1.44 | 1.62 | 1.56 | 1.57 | 1.68 | 1.67 |
| csv-comparison-v3 | scan | numeric | sweep-native | 0.40 | 0.62 | 0.98 | 1.04 | 1.00 | 1.18 | 1.15 | 1.19 | 1.28 |
| csv-comparison-v3 | scan | numeric | sweep-swar | 0.41 | 0.60 | 0.77 | 0.77 | 0.85 | 0.91 | 0.92 | 0.94 | 0.97 |
| csv-comparison-v3 | scan | numeric | truffledata-scanner-bytes | 0.58 | 0.78 | 0.73 | 0.78 | 0.72 | 0.76 | 0.75 | 0.77 | 0.78 |
| csv-comparison-v3 | strings | mixed | sweep-batch-native | 0.08 | 0.12 | 0.22 | 0.24 | 0.25 | 0.24 | 0.20 | 0.19 | 0.19 |
| csv-comparison-v3 | strings | mixed | sweep-batch-swar | 0.08 | 0.12 | 0.18 | 0.20 | 0.21 | 0.16 | 0.21 | 0.18 | 0.17 |
| csv-comparison-v3 | strings | mixed | truffledata-batch-bytes | 0.08 | 0.16 | 0.20 | 0.23 | 0.18 | 0.20 | 0.21 | 0.17 | 0.17 |
| csv-comparison-v3 | strings | numeric | sweep-batch-native | 0.09 | 0.14 | 0.18 | 0.19 | 0.19 | 0.16 | 0.16 | 0.17 | 0.12 |
| csv-comparison-v3 | strings | numeric | sweep-batch-swar | 0.09 | 0.12 | 0.16 | 0.17 | 0.17 | 0.18 | 0.19 | 0.18 | 0.14 |
| csv-comparison-v3 | strings | numeric | truffledata-batch-bytes | 0.10 | 0.15 | 0.17 | 0.18 | 0.16 | 0.18 | 0.17 | 0.16 | 0.16 |
| csv_jvm | index | mixed | index-native | 2.10 | 4.09 | 7.35 | 7.29 | 5.41 | 8.93 | 6.56 | 5.12 | 4.79 |
| csv_jvm | index | mixed | index-native-small | 1.86 | 4.09 | 7.68 | 7.71 | 7.69 | 9.80 | 9.01 | 1.88 | 5.64 |
| csv_jvm | index | mixed | index-swar | 1.60 | 2.40 | 3.01 | 2.96 | 1.61 | 3.26 | 2.93 | 2.08 | 2.33 |
| csv_jvm | index | numeric | index-native | 1.55 | 4.22 | 6.91 | 8.71 | 9.24 | 6.99 | 8.18 | 7.70 | 7.21 |
| csv_jvm | index | numeric | index-native-small | 1.60 | 4.68 | 7.40 | 10.10 | 10.71 | 9.35 | 10.33 | 9.99 | 9.58 |
| csv_jvm | index | numeric | index-swar | 1.47 | 1.84 | 2.75 | 2.57 | 2.84 | 3.23 | 2.89 | 2.55 | 2.42 |
| csv_jvm | scan | mixed | kernel-native-small | 1.36 | 2.20 | 3.04 | 3.80 | 3.87 | 4.07 | 3.86 | 2.88 | 2.91 |
| csv_jvm | scan | mixed | sweep-native | 0.79 | 1.16 | 2.05 | 1.83 | 1.58 | 2.48 | 2.27 | 2.49 | 2.89 |
| csv_jvm | scan | mixed | sweep-swar | 0.67 | 1.11 | 1.23 | 0.63 | 1.62 | 1.78 | 1.62 | 0.63 | 1.72 |
| csv_jvm | scan | mixed | truffledata-scanner-bytes | 1.20 | 0.96 | 2.19 | 2.47 | 2.12 | 1.67 | 1.15 | 1.17 | 1.18 |
| csv_jvm | scan | numeric | kernel-native-small | 1.38 | 2.52 | 3.13 | 2.38 | 3.27 | 3.56 | 3.24 | 3.16 | 3.22 |
| csv_jvm | scan | numeric | sweep-native | 0.01 | 1.25 | 1.68 | 0.05 | 2.24 | 1.89 | 2.08 | 2.18 | 3.10 |
| csv_jvm | scan | numeric | sweep-swar | 0.04 | 1.09 | 1.10 | 0.03 | 1.25 | 1.67 | 1.80 | 1.64 | 1.66 |
| csv_jvm | scan | numeric | truffledata-scanner-bytes | 0.88 | 1.26 | 1.87 | 1.91 | 1.47 | 1.41 | 1.25 | 1.14 | 1.17 |
| csv_jvm | strings | mixed | sweep-batch-native | 0.00 | 0.28 | 0.27 | 0.79 | 0.88 | 0.52 | 0.54 | 0.52 | 0.31 |
| csv_jvm | strings | mixed | sweep-batch-swar | 0.15 | 0.23 | 0.23 | 0.59 | 0.60 | 0.53 | 0.55 | 0.39 | 0.37 |
| csv_jvm | strings | mixed | truffledata-batch-bytes | 0.16 | 0.33 | 0.50 | 0.68 | 0.77 | 0.46 | 0.46 | 0.31 | 0.31 |
| csv_jvm | strings | numeric | sweep-batch-native | 0.14 | 0.23 | 0.53 | 0.67 | 0.60 | 0.53 | 0.62 | 0.61 | 0.58 |
| csv_jvm | strings | numeric | sweep-batch-swar | 0.14 | 0.27 | 0.41 | 0.49 | 0.53 | 0.58 | 0.43 | 0.49 | 0.57 |
| csv_jvm | strings | numeric | truffledata-batch-bytes | 0.23 | 0.34 | 0.46 | 0.38 | 0.52 | 0.41 | 0.57 | 0.51 | 0.03 |

## Retained typed reads (32 numeric rows, 8 typed columns read per row)

| Binary | Engine | Stage | ns/row (range) | Java B/row |
|---|---|---|---:|---:|
| csv-comparison | batch-fastcsv | interpret | 82.29 (68.45–96.13) | 2.00 |
| csv-comparison | batch-fastcsv | peak | 12.16 (11.90–12.42) | 1.00 |
| csv-comparison | batch-fastcsv | read-aot | 72.37 (71.36–73.38) | 0.00 |
| csv-comparison | truffledata-plan | interpret | 74.81 (72.92–76.70) | 2.00 |
| csv-comparison | truffledata-plan | peak | 7.30 (7.27–7.34) | 1.00 |
| csv-comparison | truffledata-plan | read-aot | 81.73 (72.41–91.04) | 0.00 |
| csv-comparison-v3 | batch-fastcsv | interpret | 73.00 (68.72–77.28) | 2.00 |
| csv-comparison-v3 | batch-fastcsv | peak | 13.68 (11.30–16.05) | 1.00 |
| csv-comparison-v3 | batch-fastcsv | read-aot | 70.95 (69.63–72.27) | 0.00 |
| csv-comparison-v3 | truffledata-plan | interpret | 68.39 (66.80–69.97) | 2.00 |
| csv-comparison-v3 | truffledata-plan | peak | 11.95 (6.92–16.98) | 1.00 |
| csv-comparison-v3 | truffledata-plan | read-aot | 120.20 (66.56–173.83) | 0.00 |
| csv_jvm | batch-fastcsv | interpret | 30.21 (29.39–31.02) | 1.50 |
| csv_jvm | batch-fastcsv | peak | 10.55 (10.24–10.86) | 1.50 |
| csv_jvm | batch-fastcsv | read-aot | 28.56 (28.40–28.73) | 0.00 |
| csv_jvm | truffledata-plan | interpret | 31.95 (30.76–33.13) | 1.50 |
| csv_jvm | truffledata-plan | peak | 7.06 (7.04–7.07) | 1.50 |
| csv_jvm | truffledata-plan | read-aot | 30.79 (29.99–31.59) | 0.00 |
