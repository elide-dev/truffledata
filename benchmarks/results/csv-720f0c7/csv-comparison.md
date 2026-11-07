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
| csv_jvm | fixture escaped 134217728 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv_jvm | fixture mixed 1048576 | AMD EPYC |
| csv_jvm | fixture mixed 134217728 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv_jvm | fixture numeric 1048576 | AMD EPYC |
| csv_jvm | fixture numeric 134217728 | AMD EPYC |
| csv_jvm | fixture quoted 1048576 | AMD EPYC |
| csv_jvm | fixture quoted 134217728 | AMD EPYC |
| csv_jvm | sweep | AMD EPYC |

## csv-comparison: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.729 | — |
| index | index-native-avx512 | native-avx512-svm | 8.213 (7.699–8.728) | 0.0 |
| index | index-native | native-avx512-svm | 8.144 (7.708–8.580) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 7.403 (6.107–8.699) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.693 | — |
| index | index-native-avx2 | native-avx2-svm | 6.343 (6.236–6.449) | 0.0 |
| index | index-swar | java-swar | 1.979 (1.896–2.062) | 0.0 |
| index | native-index-swar (native) | swar | 1.764 | — |
| index | index-native-swar | native-swar-svm | 1.626 (1.542–1.711) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.842 | — |
| index | index-table | java-table | 0.824 (0.796–0.853) | 0.0 |
| index | index-scalar | java-scalar | 0.716 (0.708–0.723) | 0.0 |
| index | index-vector | java-vector64 | 0.066 (0.063–0.070) | 2279.7 |
| scan | native-indexed-scan (native) | avx512 | 2.482 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.651 (1.651–1.651) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.641 (1.631–1.651) | 0.0 |
| scan | csv-core-scan (native) |  | 1.137 | — |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.127 (1.119–1.136) | 0.1 |
| scan | kernel-swar | java-swar | 0.951 (0.922–0.980) | 0.0 |
| scan | csv-byte-record (native) |  | 0.887 | — |
| scan | truffledata-scanner | java-swar | 0.762 (0.740–0.784) | 0.1 |
| scan | truffledata-scanner-bytes | per-byte | 0.746 (0.730–0.761) | 0.0 |
| scan | fastcsv |  | 0.491 (0.481–0.501) | 2.6 |
| scan | jackson |  | 0.190 (0.190–0.191) | 256.0 |
| scan | kernel-vector | java-vector64 | 0.064 (0.062–0.067) | 2279.7 |
| strings | csv-string-record (native) |  | 0.793 | — |
| strings | kernel-native | native-avx512-svm | 0.363 (0.353–0.372) | 256.0 |
| strings | kernel-swar | java-swar | 0.335 (0.313–0.357) | 256.0 |
| strings | fastcsv |  | 0.284 (0.283–0.285) | 322.7 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.215 (0.209–0.221) | 404.3 |
| strings | univocity |  | 0.206 (0.194–0.218) | 505.8 |
| strings | truffledata-batch | java-swar | 0.198 (0.189–0.206) | 404.3 |
| strings | truffledata-batch-bytes | per-byte | 0.197 (0.194–0.199) | 404.3 |
| strings | jackson |  | 0.184 (0.174–0.194) | 256.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.140 (0.138–0.141) | 404.4 |
| strings | truffledata-mapped | java-swar | 0.127 (0.126–0.128) | 404.3 |
| strings | commons-csv |  | 0.076 (0.075–0.077) | 345.6 |
| typed | csv-typed (native) |  | 0.473 | — |
| typed | kernel-native | native-avx512-svm | 0.448 (0.448–0.448) | 0.0 |
| typed | kernel-swar | java-swar | 0.363 (0.352–0.374) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.296 (0.278–0.313) | 88.6 |
| typed | truffledata-plan | java-swar | 0.266 (0.266–0.266) | 88.5 |
| typed | truffledata-plan-bytes | per-byte | 0.198 (0.190–0.205) | 88.5 |
| typed | fastcsv |  | 0.097 (0.095–0.100) | 442.7 |
| typed | univocity |  | 0.093 (0.090–0.096) | 625.8 |
| typed | jackson |  | 0.070 (0.045–0.095) | 376.0 |
| typed | commons-csv |  | 0.047 (0.042–0.051) | 465.6 |
| utf8 | simdutf-validate (native) |  | 111.557 | — |

## csv-comparison: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 10.649 | — |
| index | index-native-small | native-avx512-svm-leaf | 10.492 (10.462–10.523) | 0.0 |
| index | index-native | native-avx512-svm | 10.452 (10.344–10.560) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 10.269 | — |
| index | index-native-avx2 | native-avx2-svm | 10.095 (10.049–10.141) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 9.310 (8.589–10.031) | 0.0 |
| index | index-swar | java-swar | 2.409 (2.369–2.449) | 0.0 |
| index | native-index-swar (native) | swar | 1.994 | — |
| index | index-native-swar | native-swar-svm | 1.989 (1.987–1.991) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.892 | — |
| index | index-table | java-table | 0.839 (0.838–0.840) | 0.0 |
| index | index-scalar | java-scalar | 0.589 (0.586–0.591) | 0.0 |
| index | index-vector | java-vector64 | 0.065 (0.062–0.069) | 10188.3 |
| scan | native-indexed-scan (native) | avx512 | 8.186 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 6.920 (6.891–6.949) | 0.0 |
| scan | kernel-native | native-avx512-svm | 6.567 (6.275–6.858) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 5.802 (5.800–5.803) | 0.4 |
| scan | kernel-swar | java-swar | 2.125 (2.098–2.152) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.999 (1.982–2.015) | 0.3 |
| scan | truffledata-scanner-bytes | per-byte | 0.869 (0.866–0.872) | 0.2 |
| scan | csv-core-scan (native) |  | 0.816 | — |
| scan | csv-byte-record (native) |  | 0.653 | — |
| scan | jackson |  | 0.305 (0.304–0.306) | 284.3 |
| scan | fastcsv |  | 0.285 (0.243–0.326) | 11.9 |
| scan | kernel-vector | java-vector64 | 0.067 (0.067–0.067) | 10188.3 |
| strings | csv-string-record (native) |  | 0.607 | — |
| strings | kernel-native | native-avx512-svm | 0.526 (0.523–0.530) | 284.2 |
| strings | kernel-swar | java-swar | 0.432 (0.419–0.444) | 284.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.426 (0.425–0.427) | 594.4 |
| strings | truffledata-batch | java-swar | 0.361 (0.357–0.365) | 594.2 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.317 (0.313–0.320) | 826.2 |
| strings | jackson |  | 0.305 (0.303–0.307) | 284.3 |
| strings | truffledata-batch-bytes | per-byte | 0.290 (0.278–0.301) | 594.1 |
| strings | truffledata-mapped | java-swar | 0.269 (0.256–0.282) | 825.9 |
| strings | fastcsv |  | 0.245 (0.215–0.275) | 344.1 |
| strings | univocity |  | 0.180 (0.172–0.188) | 1245.5 |
| strings | commons-csv |  | 0.112 (0.112–0.112) | 363.4 |
| utf8 | simdutf-validate (native) |  | 104.822 | — |

## csv-comparison: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 17.451 | — |
| index | native-index-avx2 (native) | avx2 | 16.014 | — |
| index | index-native | native-avx512-svm | 15.807 (15.439–16.176) | 0.1 |
| index | index-native-small | native-avx512-svm-leaf | 15.728 (15.153–16.302) | 0.1 |
| index | index-native-avx2 | native-avx2-svm | 15.525 (15.445–15.605) | 0.1 |
| index | index-native-avx512 | native-avx512-svm | 13.274 (10.645–15.903) | 0.1 |
| index | index-swar | java-swar | 2.381 (2.377–2.385) | 0.0 |
| index | native-index-swar (native) | swar | 2.185 | — |
| index | native-index-scalar (native) | scalar | 2.102 | — |
| index | index-native-swar | native-swar-svm | 1.921 (1.613–2.229) | 0.1 |
| index | index-scalar | java-scalar | 0.958 (0.958–0.958) | 0.0 |
| index | index-table | java-table | 0.854 (0.843–0.866) | 0.0 |
| index | index-vector | java-vector64 | 0.071 (0.069–0.072) | 29278.5 |
| scan | native-indexed-scan (native) | avx512 | 14.521 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 11.821 (11.554–12.088) | 0.1 |
| scan | kernel-native | native-avx512-svm | 11.687 (10.638–12.736) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 10.502 (10.376–10.627) | 1.3 |
| scan | truffledata-scanner-bytes | per-byte | 2.551 (2.504–2.598) | 0.6 |
| scan | kernel-swar | java-swar | 2.212 (2.160–2.263) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.147 (2.048–2.246) | 0.8 |
| scan | csv-core-scan (native) |  | 1.489 | — |
| scan | csv-byte-record (native) |  | 1.056 | — |
| scan | fastcsv |  | 0.893 (0.893–0.893) | 34.2 |
| scan | jackson |  | 0.318 (0.316–0.320) | 768.9 |
| scan | kernel-vector | java-vector64 | 0.067 (0.064–0.071) | 29278.5 |
| strings | kernel-native | native-avx512-svm | 1.460 (1.450–1.469) | 768.5 |
| strings | csv-string-record (native) |  | 1.038 | — |
| strings | kernel-swar | java-swar | 0.903 (0.884–0.921) | 768.4 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.851 (0.745–0.956) | 1496.9 |
| strings | truffledata-batch-bytes | per-byte | 0.683 (0.651–0.715) | 1495.9 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.670 (0.661–0.679) | 2162.9 |
| strings | truffledata-batch | java-swar | 0.666 (0.662–0.670) | 1496.1 |
| strings | fastcsv |  | 0.627 (0.618–0.635) | 850.9 |
| strings | truffledata-mapped | java-swar | 0.527 (0.507–0.548) | 2162.1 |
| strings | jackson |  | 0.312 (0.304–0.321) | 768.9 |
| strings | univocity |  | 0.268 (0.254–0.282) | 3486.1 |
| strings | commons-csv |  | 0.138 (0.137–0.140) | 863.1 |
| utf8 | simdutf-validate (native) |  | 106.610 | — |

## csv-comparison: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 7.956 | — |
| index | index-native | native-avx512-svm | 7.936 (7.918–7.954) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 7.859 (7.702–8.016) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 7.111 (6.986–7.236) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.831 | — |
| index | index-native-avx2 | native-avx2-svm | 5.992 (4.846–7.137) | 0.0 |
| index | index-swar | java-swar | 1.804 (1.579–2.029) | 0.0 |
| index | native-index-swar (native) | swar | 1.733 | — |
| index | index-native-swar | native-swar-svm | 1.705 (1.651–1.758) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.726 | — |
| index | index-table | java-table | 0.641 (0.544–0.739) | 0.0 |
| index | index-scalar | java-scalar | 0.614 (0.603–0.625) | 0.0 |
| index | index-vector | java-vector64 | 0.067 (0.066–0.068) | 10106.4 |
| scan | native-indexed-scan (native) | avx512 | 2.804 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 2.089 (2.062–2.116) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.859 (1.729–1.990) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.469 (1.398–1.540) | 0.5 |
| scan | kernel-swar | java-swar | 1.075 (1.062–1.088) | 0.0 |
| scan | csv-core-scan (native) |  | 1.045 | — |
| scan | truffledata-scanner | java-swar | 0.965 (0.965–0.965) | 0.3 |
| scan | csv-byte-record (native) |  | 0.785 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.662 (0.573–0.750) | 0.3 |
| scan | fastcsv |  | 0.349 (0.317–0.381) | 11.6 |
| scan | jackson |  | 0.210 (0.205–0.214) | 856.3 |
| scan | kernel-vector | java-vector64 | 0.064 (0.060–0.068) | 10106.4 |
| strings | csv-string-record (native) |  | 0.427 | — |
| strings | kernel-native | native-avx512-svm | 0.372 (0.310–0.434) | 898.6 |
| strings | kernel-swar | java-swar | 0.284 (0.280–0.288) | 898.5 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.264 (0.254–0.275) | 1362.7 |
| strings | fastcsv |  | 0.250 (0.216–0.285) | 995.9 |
| strings | jackson |  | 0.216 (0.207–0.224) | 856.3 |
| strings | truffledata-batch | java-swar | 0.210 (0.196–0.225) | 1362.4 |
| strings | truffledata-batch-bytes | per-byte | 0.196 (0.185–0.206) | 1362.4 |
| strings | univocity |  | 0.193 (0.187–0.199) | 1890.2 |
| strings | truffledata-mapped | java-swar | 0.153 (0.145–0.161) | 1592.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.135 (0.129–0.141) | 1592.5 |
| strings | commons-csv |  | 0.062 (0.049–0.075) | 1015.6 |
| typed | kernel-native | native-avx512-svm | 0.742 (0.727–0.758) | 0.0 |
| typed | kernel-swar | java-swar | 0.407 (0.327–0.487) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.390 (0.312–0.467) | 448.4 |
| typed | truffledata-plan | java-swar | 0.377 (0.371–0.383) | 448.2 |
| typed | csv-typed (native) |  | 0.364 | — |
| typed | truffledata-plan-bytes | per-byte | 0.317 (0.306–0.328) | 448.1 |
| typed | fastcsv |  | 0.158 (0.149–0.167) | 1239.1 |
| typed | univocity |  | 0.140 (0.138–0.143) | 2133.4 |
| typed | jackson |  | 0.137 (0.129–0.144) | 1099.5 |
| typed | commons-csv |  | 0.063 (0.059–0.067) | 1258.8 |
| utf8 | simdutf-validate (native) |  | 77.048 | — |

## csv-comparison: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 2.665 | — |
| index | index-native-avx512 | native-avx512-svm | 2.654 (2.636–2.672) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 2.630 | — |
| index | index-native | native-avx512-svm | 2.550 (2.513–2.588) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 2.531 (2.461–2.602) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 2.442 (2.315–2.569) | 0.0 |
| index | native-index-swar (native) | swar | 1.210 | — |
| index | index-swar | java-swar | 1.205 (1.147–1.264) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.164 (1.099–1.230) | 0.0 |
| index | index-table | java-table | 0.448 (0.417–0.478) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.357 | — |
| index | index-scalar | java-scalar | 0.326 (0.309–0.343) | 0.0 |
| index | index-vector | java-vector64 | 0.037 (0.036–0.038) | 2372.2 |
| scan | kernel-native | native-avx512-svm | 0.737 (0.720–0.754) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 0.697 (0.684–0.710) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 0.653 | — |
| scan | kernel-swar | java-swar | 0.610 (0.586–0.634) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 0.507 (0.467–0.546) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.487 (0.474–0.501) | 0.0 |
| scan | csv-core-scan (native) |  | 0.471 | — |
| scan | csv-byte-record (native) |  | 0.426 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.413 (0.402–0.424) | 0.0 |
| scan | fastcsv |  | 0.286 (0.274–0.297) | 0.6 |
| scan | jackson |  | 0.090 (0.086–0.093) | 256.0 |
| scan | kernel-vector | java-vector64 | 0.035 (0.035–0.035) | 2372.2 |
| strings | csv-string-record (native) |  | 0.370 | — |
| strings | kernel-native | native-avx512-svm | 0.206 (0.205–0.206) | 256.0 |
| strings | kernel-swar | java-swar | 0.188 (0.180–0.196) | 256.0 |
| strings | fastcsv |  | 0.141 (0.141–0.141) | 320.6 |
| strings | univocity |  | 0.112 (0.107–0.118) | 297.7 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.110 (0.108–0.113) | 473.1 |
| strings | truffledata-batch | java-swar | 0.110 (0.107–0.112) | 473.0 |
| strings | truffledata-batch-bytes | per-byte | 0.105 (0.102–0.108) | 473.0 |
| strings | jackson |  | 0.078 (0.064–0.092) | 256.0 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.071 (0.068–0.074) | 420.4 |
| strings | truffledata-mapped | java-swar | 0.060 (0.050–0.070) | 420.4 |
| strings | commons-csv |  | 0.033 (0.031–0.034) | 344.3 |
| typed | kernel-native | native-avx512-svm | 0.238 (0.227–0.249) | 0.0 |
| typed | kernel-swar | java-swar | 0.224 (0.221–0.227) | 0.0 |
| typed | csv-typed (native) |  | 0.191 | — |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.141 (0.140–0.142) | 148.0 |
| typed | truffledata-plan | java-swar | 0.117 (0.103–0.132) | 147.9 |
| typed | truffledata-plan-bytes | per-byte | 0.101 (0.099–0.103) | 147.9 |
| typed | fastcsv |  | 0.050 (0.048–0.053) | 440.6 |
| typed | univocity |  | 0.047 (0.045–0.049) | 417.7 |
| typed | jackson |  | 0.034 (0.034–0.034) | 376.0 |
| typed | commons-csv |  | 0.023 (0.022–0.024) | 464.3 |
| utf8 | simdutf-validate (native) |  | 11.522 | — |

## csv-comparison: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 4.956 (4.921–4.990) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 4.851 (4.700–5.002) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.838 | — |
| index | native-index-avx2 (native) | avx2 | 4.813 | — |
| index | index-native-avx2 | native-avx2-svm | 4.796 (4.794–4.797) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 2.978 (1.262–4.694) | 0.0 |
| index | index-swar | java-swar | 1.756 (1.747–1.764) | 0.0 |
| index | native-index-swar (native) | swar | 1.721 | — |
| index | index-native-swar | native-swar-svm | 1.712 (1.711–1.712) | 0.0 |
| index | index-table | java-table | 0.779 (0.778–0.780) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.767 | — |
| index | index-scalar | java-scalar | 0.617 (0.606–0.629) | 0.0 |
| index | index-vector | java-vector64 | 0.070 (0.070–0.070) | 10184.3 |
| scan | native-indexed-scan (native) | avx512 | 2.289 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.841 (1.838–1.845) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.822 (1.809–1.834) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.365 (1.358–1.371) | 0.2 |
| scan | csv-core-scan (native) |  | 1.076 | — |
| scan | kernel-swar | java-swar | 1.063 (1.043–1.083) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.912 (0.904–0.920) | 0.0 |
| scan | csv-byte-record (native) |  | 0.859 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.841 (0.840–0.843) | 0.0 |
| scan | fastcsv |  | 0.439 (0.437–0.441) | 2.6 |
| scan | jackson |  | 0.208 (0.206–0.210) | 855.8 |
| scan | kernel-vector | java-vector64 | 0.069 (0.068–0.070) | 10184.3 |
| strings | csv-string-record (native) |  | 0.496 | — |
| strings | kernel-native | native-avx512-svm | 0.429 (0.426–0.432) | 898.1 |
| strings | kernel-swar | java-swar | 0.334 (0.322–0.347) | 898.1 |
| strings | fastcsv |  | 0.283 (0.281–0.286) | 986.5 |
| strings | univocity |  | 0.270 (0.267–0.273) | 967.2 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.226 (0.218–0.234) | 1362.4 |
| strings | jackson |  | 0.219 (0.219–0.220) | 855.8 |
| strings | truffledata-batch-bytes | per-byte | 0.197 (0.194–0.200) | 1362.1 |
| strings | truffledata-batch | java-swar | 0.193 (0.175–0.211) | 1362.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.152 (0.152–0.153) | 1136.4 |
| strings | truffledata-mapped | java-swar | 0.147 (0.143–0.152) | 1136.1 |
| strings | commons-csv |  | 0.078 (0.077–0.078) | 1009.2 |
| typed | kernel-native | native-avx512-svm | 0.721 (0.699–0.743) | 0.0 |
| typed | kernel-swar | java-swar | 0.530 (0.529–0.532) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.449 (0.440–0.457) | 449.1 |
| typed | csv-typed (native) |  | 0.397 | — |
| typed | truffledata-plan | java-swar | 0.371 (0.369–0.373) | 448.9 |
| typed | truffledata-plan-bytes | per-byte | 0.317 (0.313–0.320) | 448.9 |
| typed | fastcsv |  | 0.160 (0.154–0.166) | 1229.7 |
| typed | univocity |  | 0.158 (0.155–0.160) | 1210.4 |
| typed | jackson |  | 0.138 (0.136–0.140) | 1099.0 |
| typed | commons-csv |  | 0.059 (0.053–0.065) | 1252.4 |
| utf8 | simdutf-validate (native) |  | 18.887 | — |

## csv-comparison: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 7.850 (7.822–7.879) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 7.841 (7.814–7.869) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 7.821 (7.808–7.834) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 7.537 | — |
| index | native-index-avx2 (native) | avx2 | 7.315 | — |
| index | index-native-avx2 | native-avx2-svm | 7.229 (6.959–7.499) | 0.0 |
| index | index-swar | java-swar | 2.642 (2.640–2.644) | 0.0 |
| index | index-native-swar | native-swar-svm | 2.272 (2.264–2.280) | 0.0 |
| index | native-index-swar (native) | swar | 2.215 | — |
| index | native-index-scalar (native) | scalar | 1.130 | — |
| index | index-table | java-table | 1.031 (1.027–1.034) | 0.0 |
| index | index-scalar | java-scalar | 0.736 (0.730–0.742) | 0.0 |
| index | index-vector | java-vector64 | 0.086 (0.086–0.087) | 10225.2 |
| scan | native-indexed-scan (native) | avx512 | 6.305 | — |
| scan | kernel-native | native-avx512-svm | 5.335 (4.753–5.917) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 5.007 (4.917–5.097) | 0.2 |
| scan | kernel-native-small | native-avx512-svm-leaf | 4.919 (3.960–5.878) | 0.0 |
| scan | kernel-swar | java-swar | 2.382 (2.381–2.383) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.260 (2.245–2.275) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 1.059 (1.018–1.099) | 0.0 |
| scan | csv-core-scan (native) |  | 1.025 | — |
| scan | csv-byte-record (native) |  | 0.869 | — |
| scan | jackson |  | 0.381 (0.378–0.384) | 283.1 |
| scan | fastcsv |  | 0.376 (0.349–0.403) | 2.8 |
| scan | kernel-vector | java-vector64 | 0.082 (0.081–0.083) | 10225.2 |
| strings | csv-string-record (native) |  | 0.809 | — |
| strings | kernel-native | native-avx512-svm | 0.640 (0.640–0.640) | 283.1 |
| strings | kernel-swar | java-swar | 0.515 (0.513–0.518) | 283.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.442 (0.438–0.447) | 366.5 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.407 (0.327–0.487) | 593.4 |
| strings | truffledata-batch | java-swar | 0.404 (0.381–0.427) | 593.2 |
| strings | truffledata-mapped | java-swar | 0.391 (0.391–0.392) | 366.2 |
| strings | jackson |  | 0.355 (0.337–0.372) | 283.1 |
| strings | fastcsv |  | 0.328 (0.314–0.341) | 333.9 |
| strings | univocity |  | 0.253 (0.252–0.254) | 314.4 |
| strings | truffledata-batch-bytes | per-byte | 0.229 (0.103–0.356) | 593.2 |
| strings | commons-csv |  | 0.137 (0.137–0.137) | 356.5 |
| utf8 | simdutf-validate (native) |  | 31.384 | — |

## csv-comparison: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 15.650 | — |
| index | index-native-small | native-avx512-svm-leaf | 15.156 (14.973–15.340) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 14.936 | — |
| index | index-native-avx2 | native-avx2-svm | 14.601 (14.580–14.623) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 13.732 (12.233–15.231) | 0.0 |
| index | index-native | native-avx512-svm | 13.505 (12.144–14.865) | 0.0 |
| index | index-swar | java-swar | 2.989 (2.924–3.053) | 0.0 |
| index | native-index-swar (native) | swar | 2.751 | — |
| index | native-index-scalar (native) | scalar | 2.665 | — |
| index | index-native-swar | native-swar-svm | 2.533 (2.313–2.753) | 0.0 |
| index | index-scalar | java-scalar | 1.292 (1.285–1.298) | 0.0 |
| index | index-table | java-table | 1.101 (1.100–1.103) | 0.0 |
| index | index-vector | java-vector64 | 0.091 (0.091–0.092) | 29345.7 |
| scan | native-indexed-scan (native) | avx512 | 12.795 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 11.638 (11.631–11.645) | 0.0 |
| scan | kernel-native | native-avx512-svm | 10.522 (9.876–11.168) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 8.902 (7.965–9.838) | 0.5 |
| scan | truffledata-scanner-bytes | per-byte | 3.365 (3.349–3.382) | 0.0 |
| scan | kernel-swar | java-swar | 2.881 (2.865–2.897) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.631 (2.470–2.793) | 0.0 |
| scan | csv-core-scan (native) |  | 1.845 | — |
| scan | csv-byte-record (native) |  | 1.344 | — |
| scan | fastcsv |  | 1.092 (1.090–1.094) | 8.0 |
| scan | jackson |  | 0.393 (0.384–0.402) | 768.0 |
| scan | kernel-vector | java-vector64 | 0.091 (0.090–0.091) | 29345.7 |
| strings | kernel-native | native-avx512-svm | 1.783 (1.757–1.809) | 768.0 |
| strings | csv-string-record (native) |  | 1.277 | — |
| strings | kernel-swar | java-swar | 1.190 (1.185–1.194) | 767.9 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.893 (0.871–0.914) | 844.0 |
| strings | truffledata-batch-bytes | per-byte | 0.788 (0.777–0.800) | 1494.5 |
| strings | fastcsv |  | 0.788 (0.769–0.807) | 824.0 |
| strings | truffledata-mapped | java-swar | 0.747 (0.746–0.748) | 843.2 |
| strings | truffledata-batch | java-swar | 0.744 (0.636–0.852) | 1494.5 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.582 (0.078–1.086) | 1495.2 |
| strings | jackson |  | 0.401 (0.400–0.403) | 768.0 |
| strings | univocity |  | 0.393 (0.329–0.457) | 813.1 |
| strings | commons-csv |  | 0.188 (0.187–0.188) | 844.0 |
| utf8 | simdutf-validate (native) |  | 40.888 | — |

## csv-comparison-v3: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-svm-leaf | 8.376 (8.238–8.514) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 8.268 | — |
| index | index-native-avx512 | native-avx512-svm | 8.175 (8.003–8.347) | 0.0 |
| index | index-native | native-avx512-svm | 8.066 (8.042–8.089) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.489 | — |
| index | index-native-avx2 | native-avx2-svm | 6.291 (6.183–6.399) | 0.0 |
| index | index-vector | java-vector256 | 4.899 (4.881–4.916) | 0.0 |
| index | index-swar | java-swar | 2.019 (1.936–2.102) | 0.0 |
| index | native-index-swar (native) | swar | 1.744 | — |
| index | index-native-swar | native-swar-svm | 1.368 (1.350–1.385) | 0.0 |
| index | index-table | java-table | 0.796 (0.756–0.837) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.713 | — |
| index | index-scalar | java-scalar | 0.598 (0.546–0.649) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.229 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.578 (1.506–1.651) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.488 (1.462–1.513) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.399 (1.398–1.401) | 0.0 |
| scan | csv-core-scan (native) |  | 1.084 | — |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 0.920 (0.813–1.027) | 0.1 |
| scan | kernel-swar | java-swar | 0.863 (0.786–0.940) | 0.0 |
| scan | csv-byte-record (native) |  | 0.839 | — |
| scan | truffledata-scanner | java-swar | 0.719 (0.715–0.723) | 0.1 |
| scan | truffledata-scanner-bytes | per-byte | 0.671 (0.632–0.711) | 0.0 |
| scan | fastcsv |  | 0.530 (0.502–0.559) | 2.6 |
| scan | jackson |  | 0.173 (0.155–0.191) | 256.0 |
| strings | csv-string-record (native) |  | 0.783 | — |
| strings | kernel-native | native-avx512-svm | 0.402 (0.398–0.407) | 256.0 |
| strings | fastcsv |  | 0.303 (0.288–0.319) | 322.7 |
| strings | kernel-swar | java-swar | 0.300 (0.261–0.339) | 256.0 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.209 (0.204–0.215) | 404.3 |
| strings | univocity |  | 0.202 (0.183–0.221) | 505.8 |
| strings | truffledata-batch | java-swar | 0.201 (0.195–0.207) | 404.3 |
| strings | truffledata-batch-bytes | per-byte | 0.194 (0.190–0.198) | 404.3 |
| strings | jackson |  | 0.182 (0.176–0.188) | 256.0 |
| strings | truffledata-mapped | java-swar | 0.125 (0.122–0.127) | 404.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.121 (0.103–0.138) | 404.4 |
| strings | commons-csv |  | 0.069 (0.067–0.070) | 345.6 |
| typed | csv-typed (native) |  | 0.471 | — |
| typed | kernel-native | native-avx512-svm | 0.443 (0.443–0.444) | 0.0 |
| typed | kernel-swar | java-swar | 0.369 (0.361–0.377) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.274 (0.254–0.294) | 88.6 |
| typed | truffledata-plan | java-swar | 0.258 (0.246–0.270) | 88.5 |
| typed | truffledata-plan-bytes | per-byte | 0.225 (0.224–0.225) | 88.5 |
| typed | fastcsv |  | 0.099 (0.096–0.103) | 442.7 |
| typed | jackson |  | 0.093 (0.092–0.093) | 376.0 |
| typed | univocity |  | 0.090 (0.089–0.091) | 625.8 |
| typed | commons-csv |  | 0.052 (0.051–0.052) | 465.6 |
| utf8 | simdutf-validate (native) |  | 73.265 | — |

## csv-comparison-v3: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 10.559 | — |
| index | index-native-small | native-avx512-svm-leaf | 10.499 (10.412–10.586) | 0.0 |
| index | index-native | native-avx512-svm | 10.355 (10.276–10.435) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 10.250 (10.238–10.262) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 10.117 | — |
| index | index-native-avx2 | native-avx2-svm | 10.110 (10.013–10.208) | 0.0 |
| index | index-vector | java-vector256 | 6.033 (6.020–6.046) | 0.0 |
| index | index-swar | java-swar | 2.367 (2.331–2.402) | 0.0 |
| index | native-index-swar (native) | swar | 2.001 | — |
| index | native-index-scalar (native) | scalar | 0.926 | — |
| index | index-table | java-table | 0.827 (0.825–0.828) | 0.0 |
| index | index-native-swar | native-swar-svm | 0.557 (0.539–0.576) | 0.0 |
| index | index-scalar | java-scalar | 0.550 (0.537–0.562) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 8.087 | — |
| scan | kernel-native | native-avx512-svm | 6.643 (6.421–6.866) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 6.474 (6.116–6.831) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 5.625 (5.616–5.635) | 0.4 |
| scan | kernel-vector | java-vector256 | 4.454 (4.285–4.623) | 0.0 |
| scan | kernel-swar | java-swar | 2.153 (2.137–2.168) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.017 (1.971–2.063) | 0.3 |
| scan | truffledata-scanner-bytes | per-byte | 0.854 (0.797–0.910) | 0.2 |
| scan | csv-core-scan (native) |  | 0.820 | — |
| scan | csv-byte-record (native) |  | 0.648 | — |
| scan | fastcsv |  | 0.342 (0.311–0.373) | 11.9 |
| scan | jackson |  | 0.331 (0.328–0.335) | 284.3 |
| strings | kernel-native | native-avx512-svm | 0.655 (0.651–0.659) | 284.2 |
| strings | csv-string-record (native) |  | 0.590 | — |
| strings | kernel-swar | java-swar | 0.512 (0.509–0.515) | 284.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.502 (0.486–0.519) | 594.4 |
| strings | truffledata-batch | java-swar | 0.411 (0.392–0.429) | 594.2 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.355 (0.345–0.366) | 826.2 |
| strings | truffledata-batch-bytes | per-byte | 0.338 (0.337–0.339) | 594.1 |
| strings | fastcsv |  | 0.320 (0.314–0.327) | 344.1 |
| strings | jackson |  | 0.309 (0.283–0.335) | 284.3 |
| strings | truffledata-mapped | java-swar | 0.297 (0.276–0.319) | 825.9 |
| strings | univocity |  | 0.201 (0.185–0.216) | 1245.5 |
| strings | commons-csv |  | 0.115 (0.109–0.121) | 363.4 |
| utf8 | simdutf-validate (native) |  | 106.655 | — |

## csv-comparison-v3: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 17.775 | — |
| index | index-native-avx512 | native-avx512-svm | 17.553 (17.550–17.555) | 0.1 |
| index | index-native-small | native-avx512-svm-leaf | 17.307 (17.058–17.556) | 0.1 |
| index | index-native | native-avx512-svm | 17.217 (16.892–17.543) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 15.767 | — |
| index | index-native-avx2 | native-avx2-svm | 15.159 (14.274–16.043) | 0.1 |
| index | index-vector | java-vector256 | 6.967 (6.809–7.125) | 0.0 |
| index | index-swar | java-swar | 2.477 (2.472–2.482) | 0.0 |
| index | native-index-swar (native) | swar | 2.243 | — |
| index | native-index-scalar (native) | scalar | 2.066 | — |
| index | index-native-swar | native-swar-svm | 1.300 (0.563–2.038) | 0.1 |
| index | index-scalar | java-scalar | 0.909 (0.908–0.911) | 0.0 |
| index | index-table | java-table | 0.874 (0.849–0.898) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 14.494 | — |
| scan | kernel-native | native-avx512-svm | 12.591 (12.403–12.778) | 0.1 |
| scan | kernel-native-small | native-avx512-svm-leaf | 12.536 (12.289–12.783) | 0.1 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 10.037 (9.392–10.682) | 1.3 |
| scan | kernel-vector | java-vector256 | 5.953 (5.881–6.024) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 2.686 (2.654–2.717) | 0.6 |
| scan | kernel-swar | java-swar | 2.342 (2.307–2.377) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.240 (2.219–2.261) | 0.8 |
| scan | csv-core-scan (native) |  | 1.495 | — |
| scan | fastcsv |  | 1.216 (1.173–1.259) | 34.2 |
| scan | csv-byte-record (native) |  | 1.049 | — |
| scan | jackson |  | 0.353 (0.344–0.361) | 768.9 |
| strings | kernel-native | native-avx512-svm | 2.994 (2.972–3.016) | 768.5 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 1.568 (1.544–1.592) | 1496.9 |
| strings | kernel-swar | java-swar | 1.310 (1.263–1.357) | 768.4 |
| strings | truffledata-batch-bytes | per-byte | 1.046 (1.033–1.058) | 1495.9 |
| strings | csv-string-record (native) |  | 1.045 | — |
| strings | fastcsv |  | 1.045 (1.033–1.058) | 850.9 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.927 (0.925–0.929) | 2162.9 |
| strings | truffledata-batch | java-swar | 0.899 (0.841–0.958) | 1496.1 |
| strings | truffledata-mapped | java-swar | 0.644 (0.609–0.678) | 2162.1 |
| strings | univocity |  | 0.339 (0.310–0.368) | 3486.1 |
| strings | jackson |  | 0.327 (0.315–0.339) | 768.9 |
| strings | commons-csv |  | 0.164 (0.163–0.165) | 863.1 |
| utf8 | simdutf-validate (native) |  | 107.308 | — |

## csv-comparison-v3: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-svm-leaf | 8.019 (7.994–8.045) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 8.009 | — |
| index | index-native | native-avx512-svm | 7.955 (7.888–8.021) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 7.466 (7.049–7.883) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 7.178 | — |
| index | index-native-avx2 | native-avx2-svm | 7.164 (7.105–7.222) | 0.0 |
| index | index-vector | java-vector256 | 3.865 (3.777–3.953) | 0.0 |
| index | index-swar | java-swar | 1.993 (1.919–2.067) | 0.0 |
| index | native-index-swar (native) | swar | 1.898 | — |
| index | index-native-swar | native-swar-svm | 1.213 (0.563–1.862) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.779 | — |
| index | index-table | java-table | 0.735 (0.714–0.755) | 0.0 |
| index | index-scalar | java-scalar | 0.564 (0.562–0.566) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.817 | — |
| scan | kernel-native | native-avx512-svm | 1.941 (1.789–2.093) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.822 (1.520–2.124) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.622 (1.614–1.630) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.445 (1.374–1.516) | 0.5 |
| scan | kernel-swar | java-swar | 1.153 (1.153–1.154) | 0.0 |
| scan | csv-core-scan (native) |  | 1.062 | — |
| scan | truffledata-scanner | java-swar | 0.954 (0.950–0.957) | 0.3 |
| scan | truffledata-scanner-bytes | per-byte | 0.821 (0.817–0.825) | 0.3 |
| scan | csv-byte-record (native) |  | 0.787 | — |
| scan | fastcsv |  | 0.484 (0.478–0.490) | 11.6 |
| scan | jackson |  | 0.220 (0.219–0.221) | 856.3 |
| strings | csv-string-record (native) |  | 0.475 | — |
| strings | kernel-native | native-avx512-svm | 0.437 (0.434–0.440) | 898.6 |
| strings | kernel-swar | java-swar | 0.343 (0.341–0.345) | 898.5 |
| strings | fastcsv |  | 0.318 (0.312–0.325) | 995.9 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.295 (0.293–0.296) | 1362.7 |
| strings | truffledata-batch | java-swar | 0.246 (0.242–0.250) | 1362.4 |
| strings | univocity |  | 0.241 (0.236–0.246) | 1890.2 |
| strings | truffledata-batch-bytes | per-byte | 0.236 (0.234–0.239) | 1362.4 |
| strings | jackson |  | 0.226 (0.223–0.229) | 856.3 |
| strings | truffledata-mapped | java-swar | 0.163 (0.162–0.164) | 1592.3 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.159 (0.142–0.176) | 1592.5 |
| strings | commons-csv |  | 0.085 (0.085–0.085) | 1015.6 |
| typed | kernel-native | native-avx512-svm | 0.748 (0.741–0.755) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.558 (0.557–0.559) | 448.4 |
| typed | kernel-swar | java-swar | 0.532 (0.511–0.554) | 0.0 |
| typed | truffledata-plan | java-swar | 0.421 (0.421–0.422) | 448.2 |
| typed | csv-typed (native) |  | 0.391 | — |
| typed | truffledata-plan-bytes | per-byte | 0.353 (0.352–0.354) | 448.1 |
| typed | fastcsv |  | 0.176 (0.170–0.181) | 1239.1 |
| typed | univocity |  | 0.155 (0.153–0.156) | 2133.4 |
| typed | jackson |  | 0.147 (0.145–0.149) | 1099.5 |
| typed | commons-csv |  | 0.070 (0.069–0.071) | 1258.8 |
| utf8 | simdutf-validate (native) |  | 78.351 | — |

## csv-comparison-v3: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.628 | — |
| index | index-native-avx512 | native-avx512-svm | 8.575 (8.574–8.577) | 0.0 |
| index | index-native | native-avx512-svm | 8.350 (8.211–8.488) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 8.138 (7.737–8.540) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.590 | — |
| index | index-native-avx2 | native-avx2-svm | 6.130 (5.785–6.475) | 0.0 |
| index | index-vector | java-vector256 | 5.332 (5.234–5.430) | 0.0 |
| index | index-swar | java-swar | 2.259 (2.254–2.264) | 0.0 |
| index | native-index-swar (native) | swar | 1.761 | — |
| index | index-native-swar | native-swar-svm | 1.718 (1.706–1.730) | 0.0 |
| index | index-table | java-table | 0.867 (0.865–0.868) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.839 | — |
| index | index-scalar | java-scalar | 0.648 (0.633–0.662) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.589 | — |
| scan | kernel-native | native-avx512-svm | 1.717 (1.709–1.726) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.497 (1.481–1.513) | 0.0 |
| scan | csv-core-scan (native) |  | 1.174 | — |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.114 (1.067–1.160) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 0.965 (0.245–1.686) | 0.0 |
| scan | csv-byte-record (native) |  | 0.879 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.809 (0.806–0.812) | 0.0 |
| scan | truffledata-scanner | java-swar | 0.781 (0.718–0.845) | 0.0 |
| scan | kernel-swar | java-swar | 0.704 (0.337–1.071) | 0.0 |
| scan | fastcsv |  | 0.581 (0.568–0.593) | 0.6 |
| scan | jackson |  | 0.185 (0.185–0.186) | 256.0 |
| strings | csv-string-record (native) |  | 0.796 | — |
| strings | kernel-native | native-avx512-svm | 0.428 (0.420–0.437) | 256.0 |
| strings | kernel-swar | java-swar | 0.380 (0.373–0.387) | 256.0 |
| strings | fastcsv |  | 0.326 (0.321–0.332) | 320.6 |
| strings | univocity |  | 0.257 (0.239–0.275) | 297.7 |
| strings | truffledata-batch | java-swar | 0.195 (0.194–0.197) | 473.0 |
| strings | jackson |  | 0.193 (0.188–0.198) | 256.0 |
| strings | truffledata-batch-bytes | per-byte | 0.188 (0.186–0.189) | 473.0 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.184 (0.174–0.193) | 473.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.145 (0.144–0.145) | 420.4 |
| strings | truffledata-mapped | java-swar | 0.131 (0.126–0.135) | 420.4 |
| strings | commons-csv |  | 0.077 (0.075–0.080) | 344.3 |
| typed | kernel-native | native-avx512-svm | 0.484 (0.483–0.486) | 0.0 |
| typed | csv-typed (native) |  | 0.467 | — |
| typed | kernel-swar | java-swar | 0.410 (0.406–0.413) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.314 (0.299–0.328) | 148.0 |
| typed | truffledata-plan | java-swar | 0.257 (0.256–0.257) | 147.9 |
| typed | truffledata-plan-bytes | per-byte | 0.173 (0.152–0.193) | 147.9 |
| typed | fastcsv |  | 0.110 (0.106–0.113) | 440.6 |
| typed | univocity |  | 0.101 (0.094–0.108) | 417.7 |
| typed | jackson |  | 0.094 (0.091–0.097) | 376.0 |
| typed | commons-csv |  | 0.055 (0.055–0.055) | 464.3 |
| utf8 | simdutf-validate (native) |  | 34.974 | — |

## csv-comparison-v3: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 4.942 (4.906–4.978) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 4.897 (4.836–4.959) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 4.819 (4.773–4.865) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.811 | — |
| index | index-native-avx2 | native-avx2-svm | 4.713 (4.596–4.829) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.653 | — |
| index | index-vector | java-vector256 | 3.196 (3.194–3.198) | 0.0 |
| index | index-swar | java-swar | 1.780 (1.775–1.784) | 0.0 |
| index | native-index-swar (native) | swar | 1.690 | — |
| index | index-native-swar | native-swar-svm | 1.038 (0.545–1.530) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.773 | — |
| index | index-table | java-table | 0.768 (0.767–0.770) | 0.0 |
| index | index-scalar | java-scalar | 0.548 (0.524–0.571) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.271 | — |
| scan | kernel-native | native-avx512-svm | 1.823 (1.812–1.833) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.803 (1.774–1.833) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.484 (1.460–1.508) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 1.288 (1.238–1.337) | 0.2 |
| scan | kernel-swar | java-swar | 1.065 (1.057–1.072) | 0.0 |
| scan | csv-core-scan (native) |  | 1.051 | — |
| scan | truffledata-scanner | java-swar | 0.915 (0.909–0.922) | 0.0 |
| scan | csv-byte-record (native) |  | 0.853 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.836 (0.829–0.842) | 0.0 |
| scan | fastcsv |  | 0.454 (0.433–0.476) | 2.6 |
| scan | jackson |  | 0.215 (0.213–0.216) | 855.8 |
| strings | csv-string-record (native) |  | 0.479 | — |
| strings | kernel-native | native-avx512-svm | 0.450 (0.447–0.453) | 898.1 |
| strings | kernel-swar | java-swar | 0.361 (0.356–0.367) | 898.1 |
| strings | fastcsv |  | 0.309 (0.300–0.319) | 986.5 |
| strings | univocity |  | 0.274 (0.272–0.276) | 967.2 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.240 (0.240–0.241) | 1362.4 |
| strings | jackson |  | 0.219 (0.217–0.220) | 855.8 |
| strings | truffledata-batch | java-swar | 0.206 (0.202–0.210) | 1362.1 |
| strings | truffledata-batch-bytes | per-byte | 0.188 (0.178–0.198) | 1362.1 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.153 (0.149–0.158) | 1136.4 |
| strings | truffledata-mapped | java-swar | 0.144 (0.143–0.145) | 1136.1 |
| strings | commons-csv |  | 0.081 (0.079–0.083) | 1009.2 |
| typed | kernel-native | native-avx512-svm | 0.755 (0.739–0.771) | 0.0 |
| typed | kernel-swar | java-swar | 0.517 (0.509–0.525) | 0.0 |
| typed | truffledata-plan-native | native-avx512-svm-leaf | 0.465 (0.450–0.481) | 449.1 |
| typed | csv-typed (native) |  | 0.398 | — |
| typed | truffledata-plan | java-swar | 0.372 (0.352–0.392) | 448.9 |
| typed | truffledata-plan-bytes | per-byte | 0.307 (0.300–0.314) | 448.9 |
| typed | fastcsv |  | 0.175 (0.171–0.178) | 1229.7 |
| typed | univocity |  | 0.160 (0.155–0.165) | 1210.4 |
| typed | jackson |  | 0.145 (0.145–0.145) | 1099.0 |
| typed | commons-csv |  | 0.060 (0.054–0.066) | 1252.4 |
| utf8 | simdutf-validate (native) |  | 18.873 | — |

## csv-comparison-v3: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx2 | native-avx2-svm | 4.921 (4.894–4.949) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 4.770 (4.767–4.773) | 0.0 |
| index | index-native | native-avx512-svm | 4.713 (4.631–4.796) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 4.636 (4.532–4.740) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.590 | — |
| index | native-index-avx512 (native) | avx512 | 4.418 | — |
| index | index-vector | java-vector256 | 3.332 (3.286–3.379) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.416 (1.411–1.420) | 0.0 |
| index | index-swar | java-swar | 1.365 (1.322–1.408) | 0.0 |
| index | native-index-swar (native) | swar | 1.358 | — |
| index | native-index-scalar (native) | scalar | 0.594 | — |
| index | index-table | java-table | 0.568 (0.567–0.568) | 0.0 |
| index | index-scalar | java-scalar | 0.339 (0.337–0.341) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 3.547 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 3.409 (3.397–3.420) | 0.0 |
| scan | kernel-native | native-avx512-svm | 3.312 (3.206–3.419) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 2.910 (2.899–2.922) | 0.2 |
| scan | kernel-vector | java-vector256 | 1.951 (1.258–2.644) | 0.0 |
| scan | kernel-swar | java-swar | 1.264 (1.259–1.268) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.193 (1.190–1.196) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 0.657 (0.639–0.676) | 0.0 |
| scan | csv-core-scan (native) |  | 0.528 | — |
| scan | csv-byte-record (native) |  | 0.506 | — |
| scan | fastcsv |  | 0.254 (0.254–0.254) | 2.8 |
| scan | jackson |  | 0.223 (0.222–0.224) | 283.1 |
| strings | kernel-native | native-avx512-svm | 0.459 (0.455–0.463) | 283.1 |
| strings | csv-string-record (native) |  | 0.436 | — |
| strings | kernel-swar | java-swar | 0.348 (0.335–0.360) | 283.1 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 0.344 (0.341–0.347) | 593.4 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 0.306 (0.306–0.307) | 366.5 |
| strings | truffledata-batch | java-swar | 0.272 (0.268–0.276) | 593.2 |
| strings | truffledata-mapped | java-swar | 0.257 (0.257–0.257) | 366.2 |
| strings | jackson |  | 0.225 (0.225–0.226) | 283.1 |
| strings | fastcsv |  | 0.224 (0.222–0.226) | 333.9 |
| strings | univocity |  | 0.149 (0.146–0.152) | 314.4 |
| strings | truffledata-batch-bytes | per-byte | 0.145 (0.070–0.219) | 593.2 |
| strings | commons-csv |  | 0.075 (0.074–0.077) | 356.5 |
| utf8 | simdutf-validate (native) |  | 28.295 | — |

## csv-comparison-v3: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 12.681 | — |
| index | index-native-small | native-avx512-svm-leaf | 12.320 (12.270–12.370) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 12.190 | — |
| index | index-native-avx512 | native-avx512-svm | 12.103 (12.075–12.132) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 11.814 (11.808–11.820) | 0.0 |
| index | index-native | native-avx512-svm | 11.773 (11.261–12.285) | 0.0 |
| index | index-vector | java-vector256 | 6.652 (6.391–6.913) | 0.0 |
| index | index-swar | java-swar | 2.342 (2.234–2.449) | 0.0 |
| index | native-index-swar (native) | swar | 2.247 | — |
| index | index-native-swar | native-swar-svm | 2.223 (2.213–2.233) | 0.0 |
| index | native-index-scalar (native) | scalar | 2.115 | — |
| index | index-table | java-table | 0.851 (0.815–0.888) | 0.0 |
| index | index-scalar | java-scalar | 0.841 (0.813–0.869) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 10.230 | — |
| scan | kernel-native | native-avx512-svm | 9.205 (9.044–9.367) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 9.124 (9.073–9.175) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-svm-leaf | 7.926 (7.514–8.337) | 0.5 |
| scan | kernel-vector | java-vector256 | 5.751 (5.719–5.782) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 2.703 (2.644–2.763) | 0.0 |
| scan | kernel-swar | java-swar | 2.319 (2.311–2.327) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.059 (1.910–2.207) | 0.0 |
| scan | csv-core-scan (native) |  | 1.469 | — |
| scan | fastcsv |  | 1.217 (1.207–1.227) | 8.0 |
| scan | csv-byte-record (native) |  | 1.078 | — |
| scan | jackson |  | 0.362 (0.361–0.362) | 768.0 |
| strings | kernel-native | native-avx512-svm | 2.732 (2.552–2.912) | 768.0 |
| strings | kernel-swar | java-swar | 1.379 (1.375–1.382) | 767.9 |
| strings | truffledata-batch-native | native-avx512-svm-leaf | 1.268 (1.228–1.308) | 1495.2 |
| strings | truffledata-mapped-native | native-avx512-svm-leaf | 1.112 (1.106–1.118) | 844.0 |
| strings | csv-string-record (native) |  | 1.015 | — |
| strings | fastcsv |  | 1.013 (0.997–1.030) | 824.0 |
| strings | truffledata-batch-bytes | per-byte | 0.972 (0.964–0.981) | 1494.5 |
| strings | truffledata-batch | java-swar | 0.894 (0.863–0.926) | 1494.5 |
| strings | truffledata-mapped | java-swar | 0.777 (0.773–0.781) | 843.2 |
| strings | univocity |  | 0.428 (0.412–0.445) | 813.1 |
| strings | jackson |  | 0.361 (0.358–0.364) | 768.0 |
| strings | commons-csv |  | 0.168 (0.166–0.169) | 844.0 |
| utf8 | simdutf-validate (native) |  | 34.217 | — |

## csv_jvm: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.649 | — |
| index | index-native-small | native-avx512-ffm | 8.531 (8.370–8.691) | 0.0 |
| index | index-native | native-avx512-ffm | 6.819 (6.777–6.860) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.721 | — |
| index | index-vector | java-vector512 | 6.680 (6.551–6.809) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.427 (5.354–5.500) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 3.874 (0.915–6.834) | 0.0 |
| index | index-swar | java-swar | 2.474 (2.375–2.574) | 0.0 |
| index | native-index-swar (native) | swar | 1.754 | — |
| index | index-native-swar | native-swar-ffm | 1.609 (1.592–1.627) | 0.0 |
| index | index-scalar | java-scalar | 1.294 (1.291–1.298) | 0.0 |
| index | index-table | java-table | 0.918 (0.866–0.970) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.846 | — |
| scan | kernel-native-small | native-avx512-ffm | 3.000 (2.980–3.020) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 2.911 (2.907–2.915) | 0.0 |
| scan | kernel-vector | java-vector512 | 2.677 (2.441–2.914) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.508 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 2.437 (2.201–2.673) | 0.2 |
| scan | kernel-swar | java-swar | 1.660 (1.628–1.691) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.470 (1.461–1.479) | 0.1 |
| scan | csv-core-scan (native) |  | 1.131 | — |
| scan | fastcsv |  | 1.087 (1.081–1.093) | 2.0 |
| scan | truffledata-scanner-bytes | per-byte | 1.007 (0.991–1.023) | 0.0 |
| scan | csv-byte-record (native) |  | 0.880 | — |
| scan | jackson |  | 0.047 (0.023–0.070) | 384.1 |
| strings | kernel-native | native-avx512-ffm | 0.872 (0.860–0.885) | 0.0 |
| strings | csv-string-record (native) |  | 0.785 | — |
| strings | kernel-swar | java-swar | 0.726 (0.720–0.732) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.634 (0.524–0.745) | 124.5 |
| strings | truffledata-batch-bytes | per-byte | 0.484 (0.432–0.536) | 124.3 |
| strings | fastcsv |  | 0.410 (0.340–0.479) | 466.1 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.374 (0.372–0.376) | 124.6 |
| strings | truffledata-mapped | java-swar | 0.314 (0.294–0.334) | 124.4 |
| strings | truffledata-batch | java-swar | 0.291 (0.003–0.579) | 133.5 |
| strings | univocity |  | 0.197 (0.045–0.349) | 641.8 |
| strings | jackson |  | 0.175 (0.013–0.336) | 384.1 |
| strings | commons-csv |  | 0.162 (0.137–0.186) | 481.3 |
| typed | kernel-native | native-avx512-ffm | 0.696 (0.696–0.696) | 0.0 |
| typed | kernel-swar | java-swar | 0.603 (0.598–0.609) | 0.0 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.542 (0.528–0.556) | 64.7 |
| typed | truffledata-plan | java-swar | 0.479 (0.477–0.481) | 64.5 |
| typed | csv-typed (native) |  | 0.471 | — |
| typed | truffledata-plan-bytes | per-byte | 0.422 (0.418–0.427) | 64.5 |
| typed | univocity |  | 0.200 (0.199–0.202) | 713.8 |
| typed | fastcsv |  | 0.178 (0.146–0.209) | 538.1 |
| typed | jackson |  | 0.176 (0.161–0.191) | 456.1 |
| typed | commons-csv |  | 0.070 (0.029–0.111) | 601.3 |
| utf8 | simdutf-validate (native) |  | 110.351 | — |

## csv_jvm: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 10.534 | — |
| index | index-native-small | native-avx512-ffm | 10.453 (10.436–10.471) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 10.097 | — |
| index | index-native | native-avx512-ffm | 8.801 (8.740–8.862) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 8.746 (8.720–8.772) | 0.0 |
| index | index-vector | java-vector512 | 8.520 (8.418–8.623) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 8.401 (8.373–8.428) | 0.0 |
| index | index-swar | java-swar | 2.785 (2.763–2.807) | 0.0 |
| index | native-index-swar (native) | swar | 1.977 | — |
| index | index-native-swar | native-swar-ffm | 1.886 (1.885–1.887) | 0.0 |
| index | index-table | java-table | 0.954 (0.952–0.955) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.894 | — |
| index | index-scalar | java-scalar | 0.798 (0.792–0.803) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 8.138 (8.138–8.138) | 0.1 |
| scan | native-indexed-scan (native) | avx512 | 8.114 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 8.045 (8.009–8.081) | 0.7 |
| scan | kernel-native | native-avx512-ffm | 6.942 (6.705–7.179) | 0.0 |
| scan | kernel-vector | java-vector512 | 6.842 (6.566–7.118) | 0.0 |
| scan | kernel-swar | java-swar | 2.557 (2.522–2.592) | 0.0 |
| scan | truffledata-scanner | java-swar | 2.505 (2.465–2.545) | 0.2 |
| scan | truffledata-scanner-bytes | per-byte | 1.079 (1.063–1.096) | 0.2 |
| scan | csv-core-scan (native) |  | 0.813 | — |
| scan | csv-byte-record (native) |  | 0.649 | — |
| scan | fastcsv |  | 0.414 (0.403–0.425) | 9.1 |
| scan | jackson |  | 0.410 (0.409–0.411) | 332.3 |
| strings | csv-string-record (native) |  | 0.603 | — |
| strings | kernel-native | native-avx512-ffm | 0.449 (0.448–0.450) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.419 (0.419–0.419) | 287.0 |
| strings | kernel-swar | java-swar | 0.409 (0.408–0.410) | 0.0 |
| strings | jackson |  | 0.409 (0.401–0.417) | 332.3 |
| strings | truffledata-batch | java-swar | 0.375 (0.375–0.376) | 286.2 |
| strings | fastcsv |  | 0.355 (0.343–0.366) | 405.4 |
| strings | truffledata-mapped | java-swar | 0.322 (0.317–0.328) | 517.9 |
| strings | truffledata-batch-bytes | per-byte | 0.316 (0.315–0.318) | 286.1 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.281 (0.194–0.369) | 518.7 |
| strings | commons-csv |  | 0.233 (0.223–0.243) | 418.1 |
| strings | univocity |  | 0.041 (0.029–0.052) | 1301.5 |
| utf8 | simdutf-validate (native) |  | 107.852 | — |

## csv_jvm: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 17.367 | — |
| index | index-native-small | native-avx512-ffm | 17.282 (16.872–17.691) | 0.2 |
| index | native-index-avx2 (native) | avx2 | 15.987 | — |
| index | index-native | native-avx512-ffm | 13.183 (13.088–13.278) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 12.890 (12.402–13.378) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 12.203 (12.135–12.271) | 0.0 |
| index | index-vector | java-vector512 | 9.121 (9.049–9.193) | 0.0 |
| index | index-swar | java-swar | 2.825 (2.752–2.898) | 0.0 |
| index | index-scalar | java-scalar | 2.397 (2.396–2.399) | 0.0 |
| index | native-index-swar (native) | swar | 2.252 | — |
| index | index-native-swar | native-swar-ffm | 2.137 (2.133–2.140) | 0.0 |
| index | native-index-scalar (native) | scalar | 2.060 | — |
| index | index-table | java-table | 0.995 (0.994–0.996) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 14.526 (14.389–14.664) | 0.2 |
| scan | native-indexed-scan (native) | avx512 | 14.270 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 14.149 (14.079–14.218) | 2.0 |
| scan | kernel-native | native-avx512-ffm | 10.709 (10.250–11.168) | 0.0 |
| scan | kernel-vector | java-vector512 | 8.216 (8.207–8.225) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 4.460 (4.292–4.628) | 0.5 |
| scan | truffledata-scanner | java-swar | 2.792 (2.759–2.825) | 0.4 |
| scan | kernel-swar | java-swar | 2.726 (2.660–2.791) | 0.0 |
| scan | fastcsv |  | 1.613 (1.244–1.981) | 26.2 |
| scan | csv-core-scan (native) |  | 1.461 | — |
| scan | csv-byte-record (native) |  | 1.049 | — |
| scan | jackson |  | 0.376 (0.370–0.382) | 833.0 |
| strings | kernel-native | native-avx512-ffm | 2.606 (2.554–2.658) | 0.0 |
| strings | truffledata-batch-bytes | per-byte | 1.565 (1.550–1.580) | 703.9 |
| strings | truffledata-mapped-native | native-avx512-ffm | 1.469 (1.332–1.606) | 1372.4 |
| strings | kernel-swar | java-swar | 1.467 (1.461–1.473) | 0.0 |
| strings | fastcsv |  | 1.337 (1.299–1.374) | 923.0 |
| strings | truffledata-mapped | java-swar | 1.023 (0.994–1.051) | 1370.0 |
| strings | csv-string-record (native) |  | 1.002 | — |
| strings | truffledata-batch | java-swar | 0.636 (0.086–1.187) | 704.1 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.506 (0.091–0.922) | 706.5 |
| strings | jackson |  | 0.373 (0.367–0.380) | 833.0 |
| strings | univocity |  | 0.336 (0.067–0.605) | 3558.3 |
| strings | commons-csv |  | 0.314 (0.292–0.337) | 933.4 |
| utf8 | simdutf-validate (native) |  | 100.321 | — |

## csv_jvm: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 7.761 | — |
| index | index-native-small | native-avx512-ffm | 7.572 (7.504–7.641) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 6.917 | — |
| index | index-native-avx512 | native-avx512-ffm | 6.383 (6.273–6.494) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.319 (4.964–5.674) | 0.0 |
| index | index-vector | java-vector512 | 5.238 (5.170–5.306) | 0.0 |
| index | index-native | native-avx512-ffm | 4.736 (4.461–5.011) | 0.0 |
| index | index-swar | java-swar | 2.311 (2.224–2.398) | 0.0 |
| index | native-index-swar (native) | swar | 1.806 | — |
| index | index-native-swar | native-swar-ffm | 1.721 (1.670–1.771) | 0.0 |
| index | index-scalar | java-scalar | 0.871 (0.840–0.902) | 0.0 |
| index | index-table | java-table | 0.858 (0.819–0.897) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.505 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 2.907 (2.769–3.044) | 0.8 |
| scan | kernel-native-small | native-avx512-ffm | 2.870 (2.567–3.172) | 0.1 |
| scan | kernel-native | native-avx512-ffm | 2.842 (2.827–2.858) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.670 | — |
| scan | kernel-vector | java-vector512 | 2.193 (2.148–2.239) | 0.0 |
| scan | kernel-swar | java-swar | 1.559 (1.551–1.567) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.446 (1.411–1.482) | 0.3 |
| scan | csv-core-scan (native) |  | 1.011 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.948 (0.945–0.950) | 0.3 |
| scan | csv-byte-record (native) |  | 0.783 | — |
| scan | fastcsv |  | 0.540 (0.532–0.547) | 9.1 |
| scan | jackson |  | 0.290 (0.281–0.299) | 1227.9 |
| strings | kernel-native | native-avx512-ffm | 0.700 (0.698–0.702) | 46.3 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.559 (0.528–0.590) | 487.4 |
| strings | kernel-swar | java-swar | 0.553 (0.549–0.556) | 46.3 |
| strings | truffledata-batch | java-swar | 0.494 (0.478–0.511) | 486.6 |
| strings | csv-string-record (native) |  | 0.466 | — |
| strings | truffledata-batch-bytes | per-byte | 0.426 (0.418–0.433) | 486.5 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.326 (0.269–0.383) | 854.7 |
| strings | truffledata-mapped | java-swar | 0.316 (0.300–0.332) | 853.9 |
| strings | jackson |  | 0.245 (0.195–0.294) | 1227.9 |
| strings | fastcsv |  | 0.177 (0.014–0.341) | 1382.4 |
| strings | commons-csv |  | 0.104 (0.031–0.176) | 1393.9 |
| strings | univocity |  | 0.023 (0.015–0.031) | 2269.9 |
| typed | kernel-native | native-avx512-ffm | 1.103 (1.084–1.121) | 0.0 |
| typed | kernel-swar | java-swar | 0.785 (0.784–0.787) | 0.0 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.687 (0.684–0.690) | 425.3 |
| typed | truffledata-plan | java-swar | 0.549 (0.542–0.556) | 424.4 |
| typed | truffledata-plan-bytes | per-byte | 0.458 (0.444–0.471) | 424.4 |
| typed | csv-typed (native) |  | 0.382 | — |
| typed | fastcsv |  | 0.254 (0.253–0.255) | 1721.7 |
| typed | jackson |  | 0.160 (0.094–0.227) | 1567.1 |
| typed | univocity |  | 0.142 (0.108–0.176) | 2417.1 |
| typed | commons-csv |  | 0.118 (0.103–0.133) | 1733.1 |
| utf8 | simdutf-validate (native) |  | 74.178 | — |

## csv_jvm: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 6.677 | — |
| index | index-vector | java-vector512 | 6.658 (6.324–6.992) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 6.235 (6.209–6.261) | 0.0 |
| index | index-native | native-avx512-ffm | 6.129 (6.040–6.217) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.025 (4.989–5.061) | 0.0 |
| index | index-native-small | native-avx512-ffm | 4.634 (0.659–8.609) | 0.0 |
| index | index-swar | java-swar | 2.590 (2.561–2.619) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 2.426 | — |
| index | native-index-swar (native) | swar | 1.773 | — |
| index | index-native-swar | native-swar-ffm | 1.611 (1.608–1.614) | 0.0 |
| index | index-scalar | java-scalar | 1.249 (1.229–1.268) | 0.0 |
| index | index-table | java-table | 0.980 (0.978–0.983) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.852 | — |
| scan | kernel-native-small | native-avx512-ffm | 3.309 (3.281–3.337) | 0.0 |
| scan | kernel-vector | java-vector512 | 2.898 (2.857–2.938) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 2.820 (2.816–2.824) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.571 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 2.303 (2.186–2.420) | 0.1 |
| scan | kernel-swar | java-swar | 1.739 (1.709–1.770) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.522 (1.510–1.533) | 0.0 |
| scan | csv-core-scan (native) |  | 1.135 | — |
| scan | truffledata-scanner-bytes | per-byte | 1.039 (1.032–1.047) | 0.0 |
| scan | csv-byte-record (native) |  | 0.918 | — |
| scan | fastcsv |  | 0.917 (0.904–0.930) | 0.0 |
| scan | jackson |  | 0.316 (0.301–0.331) | 384.0 |
| strings | kernel-native | native-avx512-ffm | 0.873 (0.868–0.877) | 0.0 |
| strings | csv-string-record (native) |  | 0.818 | — |
| strings | kernel-swar | java-swar | 0.757 (0.753–0.761) | 0.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.650 (0.633–0.666) | 193.2 |
| strings | fastcsv |  | 0.474 (0.431–0.517) | 464.0 |
| strings | univocity |  | 0.429 (0.426–0.432) | 433.7 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.393 (0.389–0.396) | 140.6 |
| strings | truffledata-mapped | java-swar | 0.346 (0.344–0.348) | 140.4 |
| strings | jackson |  | 0.329 (0.328–0.330) | 384.0 |
| strings | truffledata-batch-bytes | per-byte | 0.291 (0.064–0.518) | 193.0 |
| strings | truffledata-batch | java-swar | 0.287 (0.027–0.547) | 193.0 |
| strings | commons-csv |  | 0.179 (0.167–0.191) | 480.0 |
| typed | kernel-native | native-avx512-ffm | 0.697 (0.696–0.698) | 0.0 |
| typed | kernel-swar | java-swar | 0.622 (0.620–0.624) | 0.0 |
| typed | csv-typed (native) |  | 0.482 | — |
| typed | truffledata-plan | java-swar | 0.325 (0.194–0.455) | 123.9 |
| typed | truffledata-plan-bytes | per-byte | 0.312 (0.199–0.426) | 123.9 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.299 (0.080–0.518) | 124.1 |
| typed | fastcsv |  | 0.243 (0.242–0.245) | 536.0 |
| typed | univocity |  | 0.214 (0.205–0.222) | 601.7 |
| typed | jackson |  | 0.191 (0.189–0.193) | 456.0 |
| typed | commons-csv |  | 0.122 (0.117–0.128) | 648.0 |
| utf8 | simdutf-validate (native) |  | 36.479 | — |

## csv_jvm: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 3.034 | — |
| index | index-native-small | native-avx512-ffm | 2.975 (2.974–2.977) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 2.975 | — |
| index | index-vector | java-vector512 | 2.520 (2.505–2.536) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 2.425 (2.401–2.449) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 2.406 (2.398–2.414) | 0.0 |
| index | index-native | native-avx512-ffm | 2.389 (2.365–2.413) | 0.0 |
| index | index-swar | java-swar | 1.468 (1.463–1.474) | 0.0 |
| index | native-index-swar (native) | swar | 1.132 | — |
| index | index-native-swar | native-swar-ffm | 0.996 (0.995–0.996) | 0.0 |
| index | index-table | java-table | 0.736 (0.730–0.742) | 0.0 |
| index | index-scalar | java-scalar | 0.511 (0.509–0.512) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.419 | — |
| scan | kernel-native-small | native-avx512-ffm | 1.653 (1.648–1.658) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-ffm | 1.549 (1.546–1.551) | 0.5 |
| scan | kernel-vector | java-vector512 | 1.472 (1.441–1.504) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 1.432 (1.427–1.437) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 1.416 | — |
| scan | kernel-swar | java-swar | 1.032 (1.030–1.033) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.000 (0.999–1.001) | 0.0 |
| scan | csv-core-scan (native) |  | 0.612 | — |
| scan | truffledata-scanner-bytes | per-byte | 0.594 (0.594–0.594) | 0.0 |
| scan | csv-byte-record (native) |  | 0.568 | — |
| scan | fastcsv |  | 0.388 (0.388–0.389) | 0.1 |
| scan | jackson |  | 0.218 (0.217–0.219) | 1227.4 |
| strings | kernel-native | native-avx512-ffm | 0.469 (0.468–0.469) | 46.0 |
| strings | kernel-swar | java-swar | 0.396 (0.395–0.397) | 46.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.332 (0.326–0.337) | 486.8 |
| strings | csv-string-record (native) |  | 0.320 | — |
| strings | truffledata-batch-bytes | per-byte | 0.235 (0.221–0.249) | 486.0 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.234 (0.228–0.239) | 398.2 |
| strings | fastcsv |  | 0.234 (0.211–0.257) | 1371.4 |
| strings | truffledata-batch | java-swar | 0.226 (0.176–0.276) | 486.0 |
| strings | univocity |  | 0.224 (0.224–0.224) | 1346.7 |
| strings | jackson |  | 0.213 (0.213–0.214) | 1227.4 |
| strings | truffledata-mapped | java-swar | 0.208 (0.205–0.211) | 397.3 |
| strings | commons-csv |  | 0.124 (0.122–0.127) | 1387.4 |
| typed | kernel-native | native-avx512-ffm | 0.591 (0.590–0.592) | 0.0 |
| typed | kernel-swar | java-swar | 0.456 (0.419–0.493) | 0.0 |
| typed | truffledata-plan-native | native-avx512-ffm | 0.427 (0.427–0.427) | 425.6 |
| typed | truffledata-plan | java-swar | 0.350 (0.345–0.354) | 424.9 |
| typed | truffledata-plan-bytes | per-byte | 0.275 (0.270–0.279) | 424.9 |
| typed | csv-typed (native) |  | 0.255 | — |
| typed | fastcsv |  | 0.181 (0.180–0.182) | 1710.6 |
| typed | univocity |  | 0.170 (0.170–0.170) | 1493.9 |
| typed | jackson |  | 0.162 (0.162–0.162) | 1566.5 |
| typed | commons-csv |  | 0.099 (0.098–0.101) | 1726.6 |
| utf8 | simdutf-validate (native) |  | 8.223 | — |

## csv_jvm: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-ffm | 3.913 (3.890–3.936) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 3.899 | — |
| index | native-index-avx512 (native) | avx512 | 3.894 | — |
| index | index-vector | java-vector512 | 3.596 (3.471–3.721) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 3.422 (3.377–3.467) | 0.0 |
| index | index-native | native-avx512-ffm | 3.379 (3.375–3.383) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 3.222 (3.222–3.223) | 0.0 |
| index | index-swar | java-swar | 1.717 (1.715–1.719) | 0.0 |
| index | native-index-swar (native) | swar | 1.279 | — |
| index | index-native-swar | native-swar-ffm | 1.079 (1.025–1.134) | 0.0 |
| index | index-table | java-table | 0.837 (0.833–0.840) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.602 | — |
| index | index-scalar | java-scalar | 0.420 (0.419–0.421) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 3.316 (3.311–3.322) | 0.0 |
| scan | kernel-vector | java-vector512 | 3.286 (3.270–3.301) | 0.0 |
| scan | truffledata-scanner-native | native-avx512-ffm | 3.284 (3.263–3.305) | 0.5 |
| scan | native-indexed-scan (native) | avx512 | 2.999 | — |
| scan | kernel-native | native-avx512-ffm | 2.575 (2.552–2.597) | 0.0 |
| scan | kernel-swar | java-swar | 1.620 (1.612–1.629) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.578 (1.574–1.582) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 0.674 (0.660–0.687) | 0.0 |
| scan | csv-core-scan (native) |  | 0.459 | — |
| scan | csv-byte-record (native) |  | 0.454 | — |
| scan | jackson |  | 0.325 (0.321–0.330) | 331.1 |
| scan | fastcsv |  | 0.243 (0.221–0.266) | 0.1 |
| strings | csv-string-record (native) |  | 0.417 | — |
| strings | jackson |  | 0.327 (0.326–0.328) | 331.1 |
| strings | kernel-native | native-avx512-ffm | 0.313 (0.312–0.314) | 0.0 |
| strings | kernel-swar | java-swar | 0.294 (0.294–0.295) | 0.0 |
| strings | truffledata-mapped-native | native-avx512-ffm | 0.274 (0.274–0.275) | 84.0 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.260 (0.259–0.262) | 286.9 |
| strings | truffledata-mapped | java-swar | 0.252 (0.252–0.253) | 83.2 |
| strings | truffledata-batch | java-swar | 0.239 (0.238–0.240) | 286.1 |
| strings | fastcsv |  | 0.238 (0.237–0.239) | 395.1 |
| strings | univocity |  | 0.229 (0.228–0.230) | 370.4 |
| strings | commons-csv |  | 0.154 (0.127–0.181) | 411.9 |
| strings | truffledata-batch-bytes | per-byte | 0.150 (0.106–0.193) | 286.1 |
| utf8 | simdutf-validate (native) |  | 13.145 | — |

## csv_jvm: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 8.942 | — |
| index | native-index-avx512 (native) | avx512 | 8.792 | — |
| index | index-native-small | native-avx512-ffm | 8.456 (8.128–8.784) | 0.1 |
| index | index-native | native-avx512-ffm | 6.091 (6.052–6.131) | 0.0 |
| index | index-vector | java-vector512 | 5.999 (5.974–6.023) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 5.890 (5.662–6.118) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 5.816 (5.512–6.120) | 0.0 |
| index | native-index-swar (native) | swar | 1.721 | — |
| index | index-swar | java-swar | 1.687 (1.679–1.695) | 0.0 |
| index | index-native-swar | native-swar-ffm | 1.562 (1.559–1.565) | 0.0 |
| index | index-scalar | java-scalar | 1.122 (1.112–1.133) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.913 | — |
| index | index-table | java-table | 0.577 (0.570–0.583) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 7.239 | — |
| scan | truffledata-scanner-native | native-avx512-ffm | 6.924 (6.876–6.971) | 1.5 |
| scan | kernel-native-small | native-avx512-ffm | 6.597 (6.539–6.656) | 0.1 |
| scan | kernel-vector | java-vector512 | 5.430 (5.395–5.465) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 5.232 (5.151–5.312) | 0.0 |
| scan | truffledata-scanner-bytes | per-byte | 2.822 (2.808–2.836) | 0.0 |
| scan | truffledata-scanner | java-swar | 1.622 (1.614–1.630) | 0.0 |
| scan | kernel-swar | java-swar | 1.617 (1.604–1.631) | 0.0 |
| scan | fastcsv |  | 1.267 (1.261–1.272) | 0.2 |
| scan | csv-core-scan (native) |  | 0.904 | — |
| scan | csv-byte-record (native) |  | 0.859 | — |
| scan | jackson |  | 0.321 (0.284–0.357) | 832.0 |
| strings | kernel-native | native-avx512-ffm | 1.597 (1.583–1.611) | 0.0 |
| strings | truffledata-mapped-native | native-avx512-ffm | 1.350 (1.335–1.366) | 53.6 |
| strings | kernel-swar | java-swar | 0.962 (0.961–0.963) | 0.0 |
| strings | fastcsv |  | 0.939 (0.932–0.945) | 896.2 |
| strings | truffledata-batch | java-swar | 0.837 (0.833–0.841) | 702.6 |
| strings | csv-string-record (native) |  | 0.789 | — |
| strings | truffledata-mapped | java-swar | 0.782 (0.781–0.782) | 51.3 |
| strings | univocity |  | 0.416 (0.415–0.416) | 885.0 |
| strings | jackson |  | 0.358 (0.356–0.359) | 832.0 |
| strings | commons-csv |  | 0.176 (0.141–0.211) | 912.1 |
| strings | truffledata-batch-native | native-avx512-ffm | 0.173 (0.103–0.243) | 704.8 |
| strings | truffledata-batch-bytes | per-byte | 0.127 (0.071–0.183) | 702.6 |
| utf8 | simdutf-validate (native) |  | 31.161 | — |

## Size sweep (kernel crossovers; GB/s, one fork)

| Binary | Workload | Dataset | Engine | 256 B | 1 KiB | 4 KiB | 16 KiB | 64 KiB | 256 KiB | 1024 KiB | 4096 KiB | 16384 KiB |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| csv-comparison | index | mixed | index-native | 1.08 | 2.21 | 3.81 | 4.70 | 4.71 | 4.82 | 4.80 | 3.27 | 3.31 |
| csv-comparison | index | mixed | index-native-small | 1.17 | 0.73 | 3.94 | 4.61 | 4.75 | 4.48 | 4.53 | 3.12 | 3.28 |
| csv-comparison | index | mixed | index-swar | 0.72 | 0.91 | 1.23 | 1.32 | 1.32 | 1.25 | 1.32 | 1.15 | 1.09 |
| csv-comparison | index | numeric | index-native | 0.96 | 2.29 | 3.49 | 3.73 | 4.18 | 4.34 | 4.02 | 4.14 | 4.20 |
| csv-comparison | index | numeric | index-native-small | 0.18 | 2.29 | 3.50 | 3.42 | 4.04 | 4.35 | 4.08 | 3.92 | 4.31 |
| csv-comparison | index | numeric | index-swar | 0.65 | 1.08 | 1.27 | 1.35 | 1.38 | 1.29 | 1.18 | 1.31 | 1.32 |
| csv-comparison | scan | mixed | kernel-native-small | 0.63 | 0.93 | 1.19 | 1.28 | 1.22 | 1.26 | 1.20 | 1.10 | 1.09 |
| csv-comparison | scan | mixed | sweep-native | 0.31 | 0.52 | 0.83 | 0.91 | 0.93 | 0.93 | 0.82 | 0.81 | 0.82 |
| csv-comparison | scan | mixed | sweep-swar | 0.32 | 0.45 | 0.59 | 0.62 | 0.58 | 0.50 | 0.55 | 0.56 | 0.58 |
| csv-comparison | scan | mixed | truffledata-scanner-bytes | 0.36 | 0.55 | 0.61 | 0.65 | 0.46 | 0.51 | 0.49 | 0.52 | 0.50 |
| csv-comparison | scan | numeric | kernel-native-small | 0.50 | 0.73 | 0.77 | 0.84 | 0.82 | 0.84 | 0.87 | 0.92 | 0.93 |
| csv-comparison | scan | numeric | sweep-native | 0.28 | 0.44 | 0.57 | 0.57 | 0.54 | 0.64 | 0.63 | 0.65 | 0.64 |
| csv-comparison | scan | numeric | sweep-swar | 0.28 | 0.40 | 0.46 | 0.47 | 0.48 | 0.48 | 0.46 | 0.47 | 0.49 |
| csv-comparison | scan | numeric | truffledata-scanner-bytes | 0.35 | 0.45 | 0.47 | 0.47 | 0.49 | 0.46 | 0.45 | 0.45 | 0.46 |
| csv-comparison | strings | mixed | sweep-batch-native | 0.05 | 0.11 | 0.18 | 0.21 | 0.21 | 0.20 | 0.18 | 0.16 | 0.15 |
| csv-comparison | strings | mixed | sweep-batch-swar | 0.05 | 0.09 | 0.14 | 0.16 | 0.17 | 0.17 | 0.16 | 0.14 | 0.13 |
| csv-comparison | strings | mixed | truffledata-batch-bytes | 0.06 | 0.11 | 0.15 | 0.17 | 0.16 | 0.16 | 0.16 | 0.13 | 0.13 |
| csv-comparison | strings | numeric | sweep-batch-native | 0.06 | 0.10 | 0.15 | 0.16 | 0.16 | 0.01 | 0.15 | 0.15 | 0.13 |
| csv-comparison | strings | numeric | sweep-batch-swar | 0.07 | 0.10 | 0.12 | 0.14 | 0.14 | 0.14 | 0.13 | 0.13 | 0.05 |
| csv-comparison | strings | numeric | truffledata-batch-bytes | 0.06 | 0.12 | 0.14 | 0.15 | 0.14 | 0.14 | 0.13 | 0.12 | 0.11 |
| csv-comparison-v3 | index | mixed | index-native | 1.72 | 3.34 | 6.32 | 7.81 | 7.92 | 8.00 | 8.03 | 5.09 | 4.84 |
| csv-comparison-v3 | index | mixed | index-native-small | 1.79 | 3.47 | 6.27 | 7.74 | 7.82 | 8.00 | 8.03 | 5.07 | 4.28 |
| csv-comparison-v3 | index | mixed | index-swar | 1.20 | 1.63 | 2.11 | 2.23 | 2.22 | 2.24 | 2.06 | 1.83 | 1.78 |
| csv-comparison-v3 | index | numeric | index-native | 1.43 | 3.73 | 6.60 | 8.20 | 8.60 | 8.50 | 8.63 | 8.34 | 8.61 |
| csv-comparison-v3 | index | numeric | index-native-small | 1.44 | 3.85 | 6.65 | 8.25 | 8.52 | 8.35 | 8.75 | 8.62 | 8.70 |
| csv-comparison-v3 | index | numeric | index-swar | 1.06 | 1.86 | 2.18 | 2.16 | 2.05 | 2.23 | 2.17 | 2.25 | 2.25 |
| csv-comparison-v3 | scan | mixed | kernel-native-small | 1.05 | 1.44 | 2.05 | 1.79 | 2.11 | 2.14 | 2.05 | 1.80 | 1.83 |
| csv-comparison-v3 | scan | mixed | sweep-native | 0.54 | 0.95 | 1.32 | 1.49 | 1.49 | 1.50 | 1.43 | 1.35 | 1.34 |
| csv-comparison-v3 | scan | mixed | sweep-swar | 0.55 | 0.77 | 0.97 | 1.02 | 1.01 | 1.02 | 0.94 | 0.87 | 0.92 |
| csv-comparison-v3 | scan | mixed | truffledata-scanner-bytes | 0.70 | 0.98 | 1.09 | 1.03 | 0.93 | 0.84 | 0.83 | 0.84 | 0.84 |
| csv-comparison-v3 | scan | numeric | kernel-native-small | 0.77 | 1.29 | 1.52 | 1.61 | 1.64 | 1.64 | 1.68 | 1.68 | 1.70 |
| csv-comparison-v3 | scan | numeric | sweep-native | 0.44 | 0.78 | 1.00 | 1.08 | 1.09 | 1.13 | 1.15 | 1.14 | 1.14 |
| csv-comparison-v3 | scan | numeric | sweep-swar | 0.46 | 0.68 | 0.78 | 0.79 | 0.82 | 0.83 | 0.83 | 0.83 | 0.84 |
| csv-comparison-v3 | scan | numeric | truffledata-scanner-bytes | 0.60 | 0.78 | 0.77 | 0.75 | 0.77 | 0.77 | 0.77 | 0.78 | 0.79 |
| csv-comparison-v3 | strings | mixed | sweep-batch-native | 0.08 | 0.16 | 0.25 | 0.29 | 0.31 | 0.30 | 0.29 | 0.21 | 0.21 |
| csv-comparison-v3 | strings | mixed | sweep-batch-swar | 0.08 | 0.15 | 0.22 | 0.24 | 0.25 | 0.21 | 0.24 | 0.19 | 0.19 |
| csv-comparison-v3 | strings | mixed | truffledata-batch-bytes | 0.09 | 0.17 | 0.23 | 0.25 | 0.25 | 0.24 | 0.22 | 0.19 | 0.18 |
| csv-comparison-v3 | strings | numeric | sweep-batch-native | 0.09 | 0.16 | 0.20 | 0.23 | 0.25 | 0.24 | 0.23 | 0.21 | 0.15 |
| csv-comparison-v3 | strings | numeric | sweep-batch-swar | 0.08 | 0.16 | 0.19 | 0.21 | 0.20 | 0.22 | 0.21 | 0.21 | 0.19 |
| csv-comparison-v3 | strings | numeric | truffledata-batch-bytes | 0.11 | 0.17 | 0.20 | 0.21 | 0.22 | 0.21 | 0.20 | 0.20 | 0.14 |
| csv_jvm | index | mixed | index-native | 2.28 | 3.86 | 7.64 | 8.71 | 8.53 | 8.79 | 7.40 | 5.15 | 5.04 |
| csv_jvm | index | mixed | index-native-small | 2.27 | 3.85 | 7.99 | 6.98 | 7.18 | 9.44 | 9.06 | 5.95 | 5.45 |
| csv_jvm | index | mixed | index-swar | 1.61 | 2.23 | 3.05 | 2.36 | 3.11 | 3.28 | 2.94 | 1.98 | 2.47 |
| csv_jvm | index | numeric | index-native | 1.85 | 4.75 | 6.72 | 9.35 | 9.24 | 7.70 | 8.01 | 7.76 | 7.18 |
| csv_jvm | index | numeric | index-native-small | 1.86 | 4.62 | 7.94 | 9.26 | 10.59 | 6.89 | 10.04 | 10.68 | 10.57 |
| csv_jvm | index | numeric | index-swar | 1.46 | 2.45 | 2.76 | 3.16 | 3.22 | 3.16 | 3.17 | 2.05 | 2.94 |
| csv_jvm | scan | mixed | kernel-native-small | 1.66 | 1.30 | 3.29 | 3.84 | 4.00 | 3.46 | 3.82 | 2.44 | 2.61 |
| csv_jvm | scan | mixed | sweep-native | 1.35 | 2.29 | 2.88 | 4.25 | 4.21 | 3.75 | 3.63 | 3.06 | 2.79 |
| csv_jvm | scan | mixed | sweep-swar | 1.13 | 1.55 | 2.13 | 2.06 | 2.04 | 2.21 | 1.85 | 1.52 | 1.59 |
| csv_jvm | scan | mixed | truffledata-scanner-bytes | 1.24 | 1.86 | 2.26 | 2.48 | 2.46 | 1.82 | 1.12 | 1.15 | 1.18 |
| csv_jvm | scan | numeric | kernel-native-small | 1.42 | 2.44 | 3.08 | 3.65 | 3.78 | 3.35 | 3.36 | 3.55 | 3.44 |
| csv_jvm | scan | numeric | sweep-native | 0.88 | 2.54 | 2.60 | 3.71 | 3.13 | 3.27 | 2.89 | 2.38 | 3.12 |
| csv_jvm | scan | numeric | sweep-swar | 0.55 | 1.46 | 1.64 | 1.78 | 1.46 | 1.93 | 1.83 | 1.73 | 1.62 |
| csv_jvm | scan | numeric | truffledata-scanner-bytes | 0.91 | 1.54 | 1.92 | 1.96 | 1.22 | 1.38 | 1.21 | 1.23 | 0.72 |
| csv_jvm | strings | mixed | sweep-batch-native | 0.00 | 0.34 | 0.63 | 0.84 | 0.85 | 0.81 | 0.84 | 0.78 | 0.29 |
| csv_jvm | strings | mixed | sweep-batch-swar | 0.10 | 0.29 | 0.51 | 0.64 | 0.65 | 0.63 | 0.62 | 0.60 | 0.38 |
| csv_jvm | strings | mixed | truffledata-batch-bytes | 0.15 | 0.29 | 0.60 | 0.77 | 0.77 | 0.54 | 0.49 | 0.36 | 0.42 |
| csv_jvm | strings | numeric | sweep-batch-native | 0.17 | 0.41 | 0.59 | 0.71 | 0.75 | 0.81 | 0.78 | 0.68 | 0.59 |
| csv_jvm | strings | numeric | sweep-batch-swar | 0.19 | 0.37 | 0.52 | 0.52 | 0.56 | 0.60 | 0.60 | 0.65 | 0.62 |
| csv_jvm | strings | numeric | truffledata-batch-bytes | 0.21 | 0.03 | 0.54 | 0.63 | 0.66 | 0.61 | 0.64 | 0.59 | 0.03 |

## Retained typed reads (32 numeric rows, 8 typed columns read per row)

| Binary | Engine | Stage | ns/row (range) | Java B/row |
|---|---|---|---:|---:|
| csv-comparison | batch-fastcsv | interpret | 70.26 (67.49–73.02) | 2.00 |
| csv-comparison | batch-fastcsv | peak | 10.75 (10.72–10.78) | 1.00 |
| csv-comparison | batch-fastcsv | read-aot | 67.43 (66.80–68.05) | 0.00 |
| csv-comparison | truffledata-plan | interpret | 72.28 (71.57–73.00) | 2.00 |
| csv-comparison | truffledata-plan | peak | 7.73 (7.00–8.45) | 1.00 |
| csv-comparison | truffledata-plan | read-aot | 68.47 (67.22–69.73) | 0.00 |
| csv-comparison-v3 | batch-fastcsv | interpret | 87.93 (72.49–103.36) | 2.00 |
| csv-comparison-v3 | batch-fastcsv | peak | 12.75 (11.19–14.30) | 1.00 |
| csv-comparison-v3 | batch-fastcsv | read-aot | 78.37 (69.31–87.43) | 0.00 |
| csv-comparison-v3 | truffledata-plan | interpret | 71.06 (66.61–75.51) | 2.00 |
| csv-comparison-v3 | truffledata-plan | peak | 10.29 (7.68–12.91) | 1.00 |
| csv-comparison-v3 | truffledata-plan | read-aot | 91.87 (86.11–97.62) | 0.00 |
| csv_jvm | batch-fastcsv | interpret | 30.51 (30.03–30.99) | 1.50 |
| csv_jvm | batch-fastcsv | peak | 13.28 (11.10–15.46) | 1.50 |
| csv_jvm | batch-fastcsv | read-aot | 28.37 (28.29–28.46) | 0.00 |
| csv_jvm | truffledata-plan | interpret | 37.94 (30.98–44.90) | 1.50 |
| csv_jvm | truffledata-plan | peak | 7.19 (6.95–7.43) | 1.50 |
| csv_jvm | truffledata-plan | read-aot | 28.61 (28.06–29.15) | 0.00 |
