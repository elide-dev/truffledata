# CSV comparison

GB/s is input bytes per nanosecond: the median of per-fork medians (range of fork medians).
Java B/row is Java heap allocation per data row. Shared-host timings are advisory.

## Hosts

| Binary | Group | CPU |
|---|---|---|
| csv-comparison | fixture escaped 1048576 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv-comparison | fixture escaped 134217728 | AMD EPYC |
| csv-comparison | fixture mixed 1048576 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv-comparison | fixture mixed 134217728 | AMD EPYC |
| csv-comparison | fixture numeric 1048576 | AMD EPYC |
| csv-comparison | fixture numeric 134217728 | Intel(R) Xeon(R) Processor @ 3.10GHz |
| csv-comparison | fixture quoted 1048576 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv-comparison | fixture quoted 134217728 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv-comparison | sweep | AMD EPYC |
| csv-comparison-v3 | fixture escaped 1048576 | AMD EPYC |
| csv-comparison-v3 | fixture escaped 134217728 | AMD EPYC |
| csv-comparison-v3 | fixture mixed 1048576 | AMD EPYC |
| csv-comparison-v3 | fixture mixed 134217728 | Intel(R) Xeon(R) Processor @ 2.70GHz |
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
| csv_jvm | fixture quoted 134217728 | Intel(R) Xeon(R) Processor @ 2.70GHz |
| csv_jvm | sweep | AMD EPYC |

## csv-comparison: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 7.802 | — |
| index | index-native-small | native-avx512-svm-leaf | 7.127 (7.108–7.147) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 6.491 (5.688–7.294) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 5.823 | — |
| index | index-native-avx2 | native-avx2-svm | 5.570 (4.857–6.283) | 0.0 |
| index | index-native | native-avx512-svm | 5.522 (5.484–5.559) | 0.0 |
| index | index-swar | java-swar | 1.577 (1.485–1.669) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.566 (1.547–1.585) | 0.0 |
| index | native-index-swar (native) | swar | 1.462 | — |
| index | native-index-scalar (native) | scalar | 0.708 | — |
| index | index-scalar | java-scalar | 0.562 (0.544–0.581) | 0.0 |
| index | index-table | java-table | 0.514 (0.339–0.689) | 0.0 |
| index | index-vector | java-vector64 | 0.047 (0.043–0.052) | 2279.7 |
| scan | native-indexed-scan (native) | avx512 | 2.019 | — |
| scan | kernel-native | native-avx512-svm | 1.338 (1.325–1.351) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.128 (1.085–1.172) | 0.0 |
| scan | csv-core-scan (native) |  | 1.021 | — |
| scan | kernel-swar | java-swar | 0.768 (0.743–0.793) | 0.0 |
| scan | truffledata-scanner |  | 0.698 (0.646–0.750) | 0.0 |
| scan | csv-byte-record (native) |  | 0.695 | — |
| scan | kernel-table | java-table | 0.486 (0.454–0.517) | 0.0 |
| scan | fastcsv |  | 0.379 (0.321–0.438) | 2.6 |
| scan | jackson |  | 0.128 (0.124–0.131) | 256.0 |
| scan | kernel-vector | java-vector64 | 0.049 (0.048–0.050) | 2279.7 |
| strings | csv-string-record (native) |  | 0.681 | — |
| strings | kernel-swar | java-swar | 0.285 (0.284–0.287) | 256.0 |
| strings | kernel-native | native-avx512-svm | 0.269 (0.245–0.294) | 256.0 |
| strings | fastcsv |  | 0.207 (0.146–0.268) | 322.7 |
| strings | univocity |  | 0.144 (0.106–0.182) | 505.8 |
| strings | truffledata-batch |  | 0.143 (0.141–0.146) | 603.2 |
| strings | jackson |  | 0.131 (0.108–0.154) | 256.0 |
| strings | commons-csv |  | 0.057 (0.056–0.058) | 345.6 |
| typed | csv-typed (native) |  | 0.415 | — |
| typed | kernel-native | native-avx512-svm | 0.355 (0.300–0.410) | 0.0 |
| typed | kernel-swar | java-swar | 0.328 (0.324–0.331) | 0.0 |
| typed | truffledata-plan |  | 0.136 (0.114–0.157) | 317.4 |
| typed | jackson |  | 0.070 (0.057–0.083) | 376.0 |
| typed | fastcsv |  | 0.055 (0.054–0.055) | 442.7 |
| typed | univocity |  | 0.052 (0.044–0.059) | 625.8 |
| typed | commons-csv |  | 0.041 (0.035–0.047) | 465.6 |
| utf8 | simdutf-validate (native) |  | 92.481 | — |

## csv-comparison: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 5.261 | — |
| index | index-native-small | native-avx512-svm-leaf | 4.834 (4.584–5.085) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 4.710 | — |
| index | index-native-avx512 | native-avx512-svm | 4.611 (4.130–5.091) | 0.0 |
| index | index-native | native-avx512-svm | 4.282 (4.114–4.451) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 4.185 (3.458–4.912) | 0.0 |
| index | index-swar | java-swar | 1.567 (1.488–1.647) | 0.0 |
| index | native-index-swar (native) | swar | 1.276 | — |
| index | index-native-swar | native-swar-svm | 1.077 (1.056–1.097) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.607 | — |
| index | index-table | java-table | 0.457 (0.447–0.467) | 0.0 |
| index | index-scalar | java-scalar | 0.283 (0.256–0.309) | 0.0 |
| index | index-vector | java-vector64 | 0.032 (0.028–0.037) | 10188.3 |
| scan | native-indexed-scan (native) | avx512 | 3.822 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 3.307 (2.777–3.837) | 0.0 |
| scan | kernel-native | native-avx512-svm | 2.939 (2.435–3.444) | 0.0 |
| scan | kernel-swar | java-swar | 1.172 (0.987–1.358) | 0.0 |
| scan | kernel-table | java-table | 0.471 (0.359–0.583) | 0.0 |
| scan | truffledata-scanner |  | 0.433 (0.397–0.468) | 0.1 |
| scan | csv-core-scan (native) |  | 0.380 | — |
| scan | csv-byte-record (native) |  | 0.373 | — |
| scan | jackson |  | 0.159 (0.143–0.175) | 284.3 |
| scan | fastcsv |  | 0.155 (0.151–0.158) | 11.9 |
| scan | kernel-vector | java-vector64 | 0.030 (0.028–0.033) | 10188.3 |
| strings | csv-string-record (native) |  | 0.356 | — |
| strings | kernel-native | native-avx512-svm | 0.256 (0.247–0.264) | 284.2 |
| strings | jackson |  | 0.180 (0.173–0.187) | 284.3 |
| strings | kernel-swar | java-swar | 0.175 (0.171–0.179) | 284.1 |
| strings | truffledata-batch |  | 0.161 (0.147–0.174) | 894.6 |
| strings | fastcsv |  | 0.110 (0.108–0.112) | 344.1 |
| strings | univocity |  | 0.105 (0.100–0.109) | 1245.5 |
| strings | commons-csv |  | 0.057 (0.054–0.059) | 363.4 |
| utf8 | simdutf-validate (native) |  | 48.537 | — |

## csv-comparison: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-svm-leaf | 12.169 (12.045–12.292) | 0.1 |
| index | index-native | native-avx512-svm | 11.643 (11.007–12.279) | 0.1 |
| index | index-native-avx512 | native-avx512-svm | 10.144 (8.096–12.193) | 0.1 |
| index | native-index-avx512 (native) | avx512 | 10.069 | — |
| index | index-native-avx2 | native-avx2-svm | 8.321 (6.118–10.524) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 7.416 | — |
| index | index-swar | java-swar | 1.499 (1.142–1.856) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.248 (1.160–1.337) | 0.1 |
| index | native-index-swar (native) | swar | 1.200 | — |
| index | native-index-scalar (native) | scalar | 1.171 | — |
| index | index-table | java-table | 0.645 (0.621–0.670) | 0.0 |
| index | index-scalar | java-scalar | 0.400 (0.346–0.455) | 0.0 |
| index | index-vector | java-vector64 | 0.042 (0.038–0.046) | 29278.5 |
| scan | native-indexed-scan (native) | avx512 | 9.097 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 7.186 (5.494–8.878) | 0.1 |
| scan | kernel-native | native-avx512-svm | 6.871 (5.213–8.528) | 0.1 |
| scan | kernel-swar | java-swar | 1.739 (1.720–1.757) | 0.0 |
| scan | csv-core-scan (native) |  | 0.824 | — |
| scan | truffledata-scanner |  | 0.641 (0.477–0.804) | 0.3 |
| scan | csv-byte-record (native) |  | 0.606 | — |
| scan | fastcsv |  | 0.418 (0.305–0.532) | 34.2 |
| scan | kernel-table | java-table | 0.411 (0.349–0.474) | 0.0 |
| scan | jackson |  | 0.201 (0.193–0.209) | 768.9 |
| scan | kernel-vector | java-vector64 | 0.040 (0.033–0.047) | 29278.5 |
| strings | kernel-native | native-avx512-svm | 0.841 (0.614–1.067) | 768.5 |
| strings | kernel-swar | java-swar | 0.645 (0.641–0.649) | 768.4 |
| strings | csv-string-record (native) |  | 0.575 | — |
| strings | fastcsv |  | 0.346 (0.326–0.365) | 850.9 |
| strings | truffledata-batch |  | 0.305 (0.228–0.382) | 1641.2 |
| strings | jackson |  | 0.170 (0.127–0.212) | 768.9 |
| strings | univocity |  | 0.161 (0.141–0.180) | 3486.1 |
| strings | commons-csv |  | 0.091 (0.071–0.112) | 863.1 |
| utf8 | simdutf-validate (native) |  | 58.261 | — |

## csv-comparison: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native | native-avx512-svm | 3.561 (3.538–3.583) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 3.536 | — |
| index | index-native-avx2 | native-avx2-svm | 3.428 (3.354–3.501) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 3.145 (2.806–3.485) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 3.082 | — |
| index | index-native-small | native-avx512-svm-leaf | 2.821 (2.683–2.959) | 0.0 |
| index | native-index-swar (native) | swar | 1.152 | — |
| index | index-native-swar | native-swar-svm | 1.142 (1.138–1.146) | 0.0 |
| index | index-swar | java-swar | 0.993 (0.748–1.239) | 0.0 |
| index | index-table | java-table | 0.443 (0.316–0.570) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.419 | — |
| index | index-scalar | java-scalar | 0.223 (0.214–0.232) | 0.0 |
| index | index-vector | java-vector64 | 0.035 (0.027–0.043) | 10106.4 |
| scan | native-indexed-scan (native) | avx512 | 1.531 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.212 (1.153–1.271) | 0.0 |
| scan | kernel-native | native-avx512-svm | 0.863 (0.845–0.881) | 0.0 |
| scan | kernel-swar | java-swar | 0.726 (0.660–0.793) | 0.0 |
| scan | csv-core-scan (native) |  | 0.602 | — |
| scan | csv-byte-record (native) |  | 0.550 | — |
| scan | truffledata-scanner |  | 0.447 (0.370–0.525) | 0.2 |
| scan | kernel-table | java-table | 0.305 (0.285–0.324) | 0.0 |
| scan | fastcsv |  | 0.237 (0.199–0.276) | 11.6 |
| scan | jackson |  | 0.125 (0.098–0.153) | 856.3 |
| scan | kernel-vector | java-vector64 | 0.033 (0.027–0.040) | 10106.4 |
| strings | csv-string-record (native) |  | 0.272 | — |
| strings | kernel-native | native-avx512-svm | 0.261 (0.220–0.302) | 898.6 |
| strings | kernel-swar | java-swar | 0.182 (0.143–0.222) | 898.5 |
| strings | jackson |  | 0.121 (0.085–0.158) | 856.3 |
| strings | fastcsv |  | 0.121 (0.118–0.124) | 995.9 |
| strings | truffledata-batch |  | 0.116 (0.105–0.128) | 2034.3 |
| strings | univocity |  | 0.104 (0.096–0.111) | 1890.2 |
| strings | commons-csv |  | 0.046 (0.031–0.060) | 1015.6 |
| typed | kernel-native | native-avx512-svm | 0.454 (0.382–0.525) | 0.0 |
| typed | kernel-swar | java-swar | 0.255 (0.248–0.262) | 0.0 |
| typed | csv-typed (native) |  | 0.205 | — |
| typed | truffledata-plan |  | 0.151 (0.143–0.158) | 1071.0 |
| typed | fastcsv |  | 0.080 (0.063–0.098) | 1239.1 |
| typed | univocity |  | 0.059 (0.054–0.064) | 2133.4 |
| typed | jackson |  | 0.059 (0.050–0.068) | 1099.5 |
| typed | commons-csv |  | 0.035 (0.032–0.038) | 1258.8 |
| utf8 | simdutf-validate (native) |  | 28.108 | — |

## csv-comparison: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx2 | native-avx2-svm | 2.662 (2.620–2.704) | 0.0 |
| index | index-native | native-avx512-svm | 2.613 (2.579–2.646) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 2.536 | — |
| index | index-native-small | native-avx512-svm-leaf | 2.507 (2.288–2.726) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 2.391 | — |
| index | index-native-avx512 | native-avx512-svm | 2.349 (2.294–2.405) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.194 (1.161–1.228) | 0.0 |
| index | index-swar | java-swar | 1.179 (1.037–1.322) | 0.0 |
| index | native-index-swar (native) | swar | 0.984 | — |
| index | index-table | java-table | 0.400 (0.313–0.486) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.335 | — |
| index | index-scalar | java-scalar | 0.293 (0.229–0.356) | 0.0 |
| index | index-vector | java-vector64 | 0.038 (0.038–0.039) | 2372.2 |
| scan | native-indexed-scan (native) | avx512 | 0.791 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 0.679 (0.598–0.761) | 0.0 |
| scan | kernel-native | native-avx512-svm | 0.665 (0.631–0.699) | 0.0 |
| scan | csv-core-scan (native) |  | 0.525 | — |
| scan | kernel-swar | java-swar | 0.515 (0.381–0.648) | 0.0 |
| scan | csv-byte-record (native) |  | 0.462 | — |
| scan | truffledata-scanner |  | 0.374 (0.349–0.399) | 0.0 |
| scan | kernel-table | java-table | 0.271 (0.189–0.352) | 0.0 |
| scan | fastcsv |  | 0.260 (0.218–0.302) | 0.6 |
| scan | jackson |  | 0.082 (0.072–0.091) | 256.0 |
| scan | kernel-vector | java-vector64 | 0.033 (0.029–0.038) | 2372.2 |
| strings | csv-string-record (native) |  | 0.393 | — |
| strings | kernel-swar | java-swar | 0.198 (0.184–0.212) | 256.0 |
| strings | kernel-native | native-avx512-svm | 0.181 (0.141–0.220) | 256.0 |
| strings | fastcsv |  | 0.144 (0.143–0.145) | 320.6 |
| strings | univocity |  | 0.110 (0.109–0.111) | 297.7 |
| strings | jackson |  | 0.094 (0.093–0.095) | 256.0 |
| strings | truffledata-batch |  | 0.092 (0.090–0.094) | 613.6 |
| strings | commons-csv |  | 0.034 (0.032–0.037) | 344.3 |
| typed | kernel-native | native-avx512-svm | 0.234 (0.212–0.255) | 0.0 |
| typed | kernel-swar | java-swar | 0.230 (0.228–0.232) | 0.0 |
| typed | csv-typed (native) |  | 0.228 | — |
| typed | truffledata-plan |  | 0.081 (0.074–0.087) | 327.0 |
| typed | fastcsv |  | 0.050 (0.050–0.050) | 440.6 |
| typed | univocity |  | 0.044 (0.042–0.047) | 417.7 |
| typed | jackson |  | 0.030 (0.026–0.034) | 376.0 |
| typed | commons-csv |  | 0.023 (0.020–0.025) | 464.3 |
| utf8 | simdutf-validate (native) |  | 12.065 | — |

## csv-comparison: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx512 | native-avx512-svm | 4.759 (4.674–4.845) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 4.653 (4.462–4.843) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.564 | — |
| index | native-index-avx512 (native) | avx512 | 4.528 | — |
| index | index-native | native-avx512-svm | 4.388 (4.244–4.531) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 3.482 (3.463–3.501) | 0.0 |
| index | native-index-swar (native) | swar | 1.605 | — |
| index | index-swar | java-swar | 1.546 (1.399–1.693) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.525 (1.505–1.545) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.739 | — |
| index | index-table | java-table | 0.714 (0.662–0.766) | 0.0 |
| index | index-scalar | java-scalar | 0.447 (0.415–0.480) | 0.0 |
| index | index-vector | java-vector64 | 0.060 (0.055–0.065) | 10184.3 |
| scan | native-indexed-scan (native) | avx512 | 2.204 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.664 (1.566–1.761) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.650 (1.646–1.653) | 0.0 |
| scan | kernel-swar | java-swar | 1.055 (1.051–1.058) | 0.0 |
| scan | csv-core-scan (native) |  | 1.047 | — |
| scan | csv-byte-record (native) |  | 0.816 | — |
| scan | truffledata-scanner |  | 0.660 (0.426–0.894) | 0.0 |
| scan | kernel-table | java-table | 0.588 (0.569–0.606) | 0.0 |
| scan | fastcsv |  | 0.319 (0.284–0.354) | 2.6 |
| scan | jackson |  | 0.191 (0.189–0.193) | 855.8 |
| scan | kernel-vector | java-vector64 | 0.055 (0.054–0.057) | 10184.3 |
| strings | csv-string-record (native) |  | 0.469 | — |
| strings | kernel-native | native-avx512-svm | 0.367 (0.315–0.420) | 898.1 |
| strings | kernel-swar | java-swar | 0.329 (0.322–0.337) | 898.1 |
| strings | fastcsv |  | 0.224 (0.179–0.270) | 986.5 |
| strings | jackson |  | 0.204 (0.201–0.207) | 855.8 |
| strings | univocity |  | 0.182 (0.170–0.194) | 967.2 |
| strings | truffledata-batch |  | 0.109 (0.068–0.150) | 2039.9 |
| strings | commons-csv |  | 0.070 (0.065–0.075) | 1009.2 |
| typed | kernel-native | native-avx512-svm | 0.680 (0.628–0.732) | 0.0 |
| typed | kernel-swar | java-swar | 0.523 (0.514–0.533) | 0.0 |
| typed | csv-typed (native) |  | 0.384 | — |
| typed | truffledata-plan |  | 0.227 (0.217–0.238) | 1077.2 |
| typed | univocity |  | 0.157 (0.153–0.160) | 1210.4 |
| typed | fastcsv |  | 0.156 (0.150–0.161) | 1229.7 |
| typed | jackson |  | 0.111 (0.101–0.121) | 1099.0 |
| typed | commons-csv |  | 0.053 (0.041–0.065) | 1252.4 |
| utf8 | simdutf-validate (native) |  | 17.661 | — |

## csv-comparison: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 7.158 | — |
| index | native-index-avx2 (native) | avx2 | 7.157 | — |
| index | index-native-small | native-avx512-svm-leaf | 6.848 (6.360–7.337) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 5.759 (5.704–5.814) | 0.0 |
| index | index-native | native-avx512-svm | 5.696 (5.214–6.179) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 5.478 (5.445–5.511) | 0.0 |
| index | native-index-swar (native) | swar | 2.054 | — |
| index | index-swar | java-swar | 1.948 (1.891–2.006) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.770 (1.672–1.868) | 0.0 |
| index | native-index-scalar (native) | scalar | 1.042 | — |
| index | index-table | java-table | 0.678 (0.646–0.710) | 0.0 |
| index | index-scalar | java-scalar | 0.552 (0.539–0.565) | 0.0 |
| index | index-vector | java-vector64 | 0.061 (0.059–0.062) | 10225.2 |
| scan | native-indexed-scan (native) | avx512 | 6.088 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 4.765 (4.374–5.156) | 0.0 |
| scan | kernel-native | native-avx512-svm | 4.168 (3.790–4.547) | 0.0 |
| scan | kernel-swar | java-swar | 1.807 (1.552–2.061) | 0.0 |
| scan | csv-core-scan (native) |  | 0.982 | — |
| scan | csv-byte-record (native) |  | 0.845 | — |
| scan | truffledata-scanner |  | 0.778 (0.740–0.815) | 0.0 |
| scan | kernel-table | java-table | 0.695 (0.686–0.705) | 0.0 |
| scan | jackson |  | 0.307 (0.275–0.339) | 283.1 |
| scan | fastcsv |  | 0.275 (0.268–0.282) | 2.8 |
| scan | kernel-vector | java-vector64 | 0.053 (0.052–0.055) | 10225.2 |
| strings | csv-string-record (native) |  | 0.728 | — |
| strings | kernel-native | native-avx512-svm | 0.455 (0.402–0.508) | 283.1 |
| strings | kernel-swar | java-swar | 0.365 (0.348–0.382) | 283.1 |
| strings | fastcsv |  | 0.254 (0.208–0.301) | 333.9 |
| strings | truffledata-batch |  | 0.254 (0.251–0.256) | 893.2 |
| strings | jackson |  | 0.251 (0.247–0.255) | 283.1 |
| strings | univocity |  | 0.160 (0.143–0.178) | 314.4 |
| strings | commons-csv |  | 0.105 (0.092–0.117) | 356.5 |
| utf8 | simdutf-validate (native) |  | 29.368 | — |

## csv-comparison: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 6.119 | — |
| index | index-native-small | native-avx512-svm-leaf | 6.038 (6.035–6.040) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 5.948 (5.813–6.083) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 5.941 | — |
| index | index-native | native-avx512-svm | 5.849 (5.605–6.093) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 5.837 (5.701–5.973) | 0.0 |
| index | index-swar | java-swar | 1.780 (1.773–1.787) | 0.0 |
| index | native-index-swar (native) | swar | 1.479 | — |
| index | index-native-swar | native-swar-svm | 1.444 (1.412–1.475) | 0.0 |
| index | native-index-scalar (native) | scalar | 1.240 | — |
| index | index-table | java-table | 0.534 (0.395–0.673) | 0.0 |
| index | index-scalar | java-scalar | 0.441 (0.390–0.492) | 0.0 |
| index | index-vector | java-vector64 | 0.043 (0.042–0.045) | 29345.7 |
| scan | native-indexed-scan (native) | avx512 | 5.278 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 4.966 (4.844–5.088) | 0.0 |
| scan | kernel-native | native-avx512-svm | 4.906 (4.749–5.063) | 0.0 |
| scan | kernel-swar | java-swar | 1.685 (1.677–1.692) | 0.0 |
| scan | csv-core-scan (native) |  | 0.804 | — |
| scan | csv-byte-record (native) |  | 0.778 | — |
| scan | truffledata-scanner |  | 0.741 (0.692–0.790) | 0.0 |
| scan | kernel-table | java-table | 0.657 (0.655–0.659) | 0.0 |
| scan | fastcsv |  | 0.509 (0.493–0.525) | 8.0 |
| scan | jackson |  | 0.193 (0.184–0.201) | 768.0 |
| scan | kernel-vector | java-vector64 | 0.045 (0.043–0.046) | 29345.7 |
| strings | kernel-native | native-avx512-svm | 0.922 (0.908–0.936) | 768.0 |
| strings | csv-string-record (native) |  | 0.733 | — |
| strings | kernel-swar | java-swar | 0.631 (0.624–0.637) | 767.9 |
| strings | fastcsv |  | 0.355 (0.354–0.356) | 824.0 |
| strings | truffledata-batch |  | 0.337 (0.305–0.369) | 1640.8 |
| strings | jackson |  | 0.206 (0.199–0.212) | 768.0 |
| strings | univocity |  | 0.173 (0.173–0.174) | 813.1 |
| strings | commons-csv |  | 0.101 (0.099–0.104) | 844.0 |
| utf8 | simdutf-validate (native) |  | 12.894 | — |

## csv-comparison-v3: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.631 | — |
| index | index-native-small | native-avx512-svm-leaf | 7.842 (7.712–7.973) | 0.0 |
| index | index-native | native-avx512-svm | 7.410 (6.937–7.884) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 7.336 (6.101–8.572) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 6.532 (6.431–6.634) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 6.265 | — |
| index | index-vector | java-vector256 | 4.772 (4.360–5.185) | 0.0 |
| index | index-swar | java-swar | 2.079 (2.048–2.110) | 0.0 |
| index | native-index-swar (native) | swar | 1.682 | — |
| index | index-native-swar | native-swar-svm | 1.596 (1.548–1.644) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.823 | — |
| index | index-table | java-table | 0.694 (0.539–0.850) | 0.0 |
| index | index-scalar | java-scalar | 0.692 (0.690–0.694) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.222 | — |
| scan | kernel-native | native-avx512-svm | 1.389 (1.358–1.421) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.258 (1.149–1.367) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.142 (1.063–1.221) | 0.0 |
| scan | csv-core-scan (native) |  | 1.108 | — |
| scan | kernel-swar | java-swar | 0.959 (0.896–1.023) | 0.0 |
| scan | truffledata-scanner |  | 0.826 (0.805–0.848) | 0.0 |
| scan | csv-byte-record (native) |  | 0.826 | — |
| scan | kernel-table | java-table | 0.470 (0.341–0.599) | 0.0 |
| scan | fastcsv |  | 0.405 (0.284–0.526) | 2.6 |
| scan | jackson |  | 0.187 (0.185–0.189) | 256.0 |
| strings | csv-string-record (native) |  | 0.754 | — |
| strings | kernel-native | native-avx512-svm | 0.352 (0.322–0.382) | 256.0 |
| strings | kernel-swar | java-swar | 0.339 (0.331–0.347) | 256.0 |
| strings | fastcsv |  | 0.212 (0.101–0.324) | 322.7 |
| strings | truffledata-batch |  | 0.167 (0.164–0.170) | 603.2 |
| strings | univocity |  | 0.166 (0.154–0.179) | 505.8 |
| strings | jackson |  | 0.159 (0.150–0.168) | 256.0 |
| strings | commons-csv |  | 0.072 (0.070–0.073) | 345.6 |
| typed | csv-typed (native) |  | 0.443 | — |
| typed | kernel-native | native-avx512-svm | 0.418 (0.381–0.455) | 0.0 |
| typed | kernel-swar | java-swar | 0.386 (0.373–0.399) | 0.0 |
| typed | truffledata-plan |  | 0.179 (0.173–0.186) | 317.4 |
| typed | jackson |  | 0.078 (0.063–0.094) | 376.0 |
| typed | fastcsv |  | 0.076 (0.068–0.084) | 442.7 |
| typed | univocity |  | 0.064 (0.052–0.077) | 625.8 |
| typed | commons-csv |  | 0.038 (0.035–0.042) | 465.6 |
| utf8 | simdutf-validate (native) |  | 58.486 | — |

## csv-comparison-v3: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 10.555 | — |
| index | native-index-avx2 (native) | avx2 | 10.208 | — |
| index | index-native-small | native-avx512-svm-leaf | 10.135 (10.079–10.191) | 0.0 |
| index | index-native | native-avx512-svm | 9.906 (9.836–9.975) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 8.487 (8.337–8.637) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 7.794 (7.310–8.277) | 0.0 |
| index | index-vector | java-vector256 | 5.350 (4.858–5.842) | 0.0 |
| index | index-swar | java-swar | 2.050 (1.693–2.408) | 0.0 |
| index | native-index-swar (native) | swar | 2.001 | — |
| index | index-native-swar | native-swar-svm | 1.844 (1.800–1.888) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.923 | — |
| index | index-table | java-table | 0.739 (0.658–0.820) | 0.0 |
| index | index-scalar | java-scalar | 0.497 (0.427–0.567) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 8.200 | — |
| scan | kernel-native | native-avx512-svm | 6.327 (5.942–6.713) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 6.314 (5.962–6.667) | 0.0 |
| scan | kernel-vector | java-vector256 | 4.103 (3.848–4.357) | 0.0 |
| scan | kernel-swar | java-swar | 1.967 (1.827–2.107) | 0.0 |
| scan | csv-core-scan (native) |  | 0.789 | — |
| scan | kernel-table | java-table | 0.661 (0.557–0.765) | 0.0 |
| scan | csv-byte-record (native) |  | 0.652 | — |
| scan | truffledata-scanner |  | 0.634 (0.353–0.915) | 0.1 |
| scan | fastcsv |  | 0.345 (0.316–0.373) | 11.9 |
| scan | jackson |  | 0.169 (0.106–0.232) | 284.3 |
| strings | csv-string-record (native) |  | 0.604 | — |
| strings | kernel-native | native-avx512-svm | 0.588 (0.517–0.660) | 284.2 |
| strings | kernel-swar | java-swar | 0.451 (0.413–0.489) | 284.1 |
| strings | fastcsv |  | 0.313 (0.308–0.317) | 344.1 |
| strings | truffledata-batch |  | 0.303 (0.300–0.307) | 894.6 |
| strings | jackson |  | 0.302 (0.275–0.328) | 284.3 |
| strings | univocity |  | 0.171 (0.158–0.183) | 1245.5 |
| strings | commons-csv |  | 0.094 (0.076–0.113) | 363.4 |
| utf8 | simdutf-validate (native) |  | 106.952 | — |

## csv-comparison-v3: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 12.504 | — |
| index | index-native-small | native-avx512-svm-leaf | 12.370 (12.280–12.459) | 0.1 |
| index | index-native | native-avx512-svm | 12.137 (11.891–12.382) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 10.902 | — |
| index | index-native-avx2 | native-avx2-svm | 10.628 (10.569–10.687) | 0.1 |
| index | index-native-avx512 | native-avx512-svm | 9.643 (6.831–12.454) | 0.1 |
| index | index-vector | java-vector256 | 4.559 (4.085–5.033) | 0.0 |
| index | index-swar | java-swar | 1.732 (1.585–1.879) | 0.0 |
| index | native-index-swar (native) | swar | 1.506 | — |
| index | index-native-swar | native-swar-svm | 1.501 (1.495–1.507) | 0.1 |
| index | native-index-scalar (native) | scalar | 1.257 | — |
| index | index-table | java-table | 0.814 (0.813–0.815) | 0.0 |
| index | index-scalar | java-scalar | 0.398 (0.289–0.506) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 9.763 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 8.988 (8.946–9.030) | 0.1 |
| scan | kernel-native | native-avx512-svm | 6.338 (4.987–7.689) | 0.1 |
| scan | kernel-vector | java-vector256 | 4.334 (4.285–4.384) | 0.0 |
| scan | kernel-swar | java-swar | 1.769 (1.760–1.778) | 0.0 |
| scan | truffledata-scanner |  | 0.940 (0.912–0.968) | 0.3 |
| scan | fastcsv |  | 0.910 (0.881–0.938) | 34.2 |
| scan | csv-core-scan (native) |  | 0.852 | — |
| scan | csv-byte-record (native) |  | 0.836 | — |
| scan | kernel-table | java-table | 0.781 (0.771–0.791) | 0.0 |
| scan | jackson |  | 0.190 (0.144–0.237) | 768.9 |
| strings | kernel-native | native-avx512-svm | 1.456 (1.186–1.726) | 768.5 |
| strings | kernel-swar | java-swar | 0.871 (0.869–0.874) | 768.4 |
| strings | csv-string-record (native) |  | 0.788 | — |
| strings | fastcsv |  | 0.722 (0.721–0.723) | 850.9 |
| strings | truffledata-batch |  | 0.322 (0.295–0.349) | 1641.2 |
| strings | jackson |  | 0.239 (0.239–0.239) | 768.9 |
| strings | univocity |  | 0.228 (0.224–0.232) | 3486.1 |
| strings | commons-csv |  | 0.126 (0.126–0.126) | 863.1 |
| utf8 | simdutf-validate (native) |  | 61.181 | — |

## csv-comparison-v3: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 7.880 | — |
| index | index-native-small | native-avx512-svm-leaf | 7.664 (7.537–7.791) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 7.135 | — |
| index | index-native-avx512 | native-avx512-svm | 6.982 (6.121–7.844) | 0.0 |
| index | index-native | native-avx512-svm | 6.716 (5.863–7.570) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 6.664 (6.254–7.075) | 0.0 |
| index | index-vector | java-vector256 | 3.527 (2.955–4.099) | 0.0 |
| index | native-index-swar (native) | swar | 1.854 | — |
| index | index-swar | java-swar | 1.816 (1.591–2.042) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.760 (1.658–1.863) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.764 | — |
| index | index-table | java-table | 0.680 (0.580–0.780) | 0.0 |
| index | index-scalar | java-scalar | 0.530 (0.462–0.597) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.727 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.963 (1.850–2.076) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.930 (1.810–2.050) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.562 (1.494–1.630) | 0.0 |
| scan | csv-core-scan (native) |  | 0.995 | — |
| scan | kernel-swar | java-swar | 0.884 (0.695–1.074) | 0.0 |
| scan | truffledata-scanner |  | 0.881 (0.856–0.905) | 0.2 |
| scan | csv-byte-record (native) |  | 0.763 | — |
| scan | kernel-table | java-table | 0.555 (0.496–0.615) | 0.0 |
| scan | fastcsv |  | 0.315 (0.230–0.401) | 11.6 |
| scan | jackson |  | 0.184 (0.181–0.187) | 856.3 |
| strings | kernel-native | native-avx512-svm | 0.442 (0.438–0.446) | 898.6 |
| strings | csv-string-record (native) |  | 0.412 | — |
| strings | kernel-swar | java-swar | 0.308 (0.269–0.346) | 898.5 |
| strings | fastcsv |  | 0.248 (0.246–0.250) | 995.9 |
| strings | jackson |  | 0.216 (0.215–0.217) | 856.3 |
| strings | univocity |  | 0.206 (0.205–0.207) | 1890.2 |
| strings | truffledata-batch |  | 0.196 (0.165–0.227) | 2034.3 |
| strings | commons-csv |  | 0.080 (0.079–0.082) | 1015.6 |
| typed | kernel-native | native-avx512-svm | 0.767 (0.706–0.829) | 0.0 |
| typed | kernel-swar | java-swar | 0.557 (0.545–0.569) | 0.0 |
| typed | csv-typed (native) |  | 0.364 | — |
| typed | truffledata-plan |  | 0.314 (0.283–0.345) | 1071.0 |
| typed | fastcsv |  | 0.169 (0.166–0.171) | 1239.1 |
| typed | univocity |  | 0.143 (0.143–0.143) | 2133.4 |
| typed | jackson |  | 0.117 (0.107–0.126) | 1099.5 |
| typed | commons-csv |  | 0.058 (0.051–0.064) | 1258.8 |
| utf8 | simdutf-validate (native) |  | 61.523 | — |

## csv-comparison-v3: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 4.287 | — |
| index | index-native | native-avx512-svm | 4.234 (4.206–4.263) | 0.0 |
| index | index-native-small | native-avx512-svm-leaf | 4.060 (3.858–4.262) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 3.886 | — |
| index | index-native-avx512 | native-avx512-svm | 3.509 (2.991–4.026) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 3.478 (3.030–3.926) | 0.0 |
| index | index-vector | java-vector256 | 3.115 (2.661–3.569) | 0.0 |
| index | native-index-swar (native) | swar | 1.305 | — |
| index | index-swar | java-swar | 1.240 (1.025–1.456) | 0.0 |
| index | index-native-swar | native-swar-svm | 0.587 (0.571–0.603) | 0.0 |
| index | index-table | java-table | 0.545 (0.519–0.571) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.393 | — |
| index | index-scalar | java-scalar | 0.383 (0.374–0.392) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 1.428 | — |
| scan | kernel-vector | java-vector256 | 0.865 (0.826–0.903) | 0.0 |
| scan | kernel-native-small | native-avx512-svm-leaf | 0.839 (0.755–0.923) | 0.0 |
| scan | kernel-native | native-avx512-svm | 0.767 (0.623–0.911) | 0.0 |
| scan | csv-core-scan (native) |  | 0.690 | — |
| scan | kernel-swar | java-swar | 0.655 (0.645–0.664) | 0.0 |
| scan | csv-byte-record (native) |  | 0.613 | — |
| scan | truffledata-scanner |  | 0.443 (0.348–0.538) | 0.0 |
| scan | kernel-table | java-table | 0.359 (0.337–0.381) | 0.0 |
| scan | fastcsv |  | 0.358 (0.329–0.387) | 0.6 |
| scan | jackson |  | 0.107 (0.096–0.117) | 256.0 |
| strings | csv-string-record (native) |  | 0.528 | — |
| strings | kernel-native | native-avx512-svm | 0.243 (0.240–0.247) | 256.0 |
| strings | kernel-swar | java-swar | 0.222 (0.222–0.223) | 256.0 |
| strings | fastcsv |  | 0.185 (0.177–0.193) | 320.6 |
| strings | univocity |  | 0.151 (0.144–0.158) | 297.7 |
| strings | jackson |  | 0.114 (0.107–0.121) | 256.0 |
| strings | truffledata-batch |  | 0.090 (0.077–0.103) | 613.6 |
| strings | commons-csv |  | 0.045 (0.040–0.049) | 344.3 |
| typed | csv-typed (native) |  | 0.318 | — |
| typed | kernel-native | native-avx512-svm | 0.293 (0.293–0.293) | 0.0 |
| typed | kernel-swar | java-swar | 0.247 (0.247–0.247) | 0.0 |
| typed | truffledata-plan |  | 0.068 (0.036–0.100) | 327.0 |
| typed | fastcsv |  | 0.067 (0.066–0.067) | 440.6 |
| typed | univocity |  | 0.055 (0.051–0.059) | 417.7 |
| typed | jackson |  | 0.047 (0.043–0.051) | 376.0 |
| typed | commons-csv |  | 0.032 (0.031–0.033) | 464.3 |
| utf8 | simdutf-validate (native) |  | 27.175 | — |

## csv-comparison-v3: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-avx2 | native-avx2-svm | 3.053 (3.021–3.084) | 0.0 |
| index | index-native | native-avx512-svm | 3.038 (3.036–3.041) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 3.012 | — |
| index | native-index-avx512 (native) | avx512 | 2.973 | — |
| index | index-native-small | native-avx512-svm-leaf | 2.943 (2.835–3.051) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 2.781 (2.534–3.029) | 0.0 |
| index | index-vector | java-vector256 | 1.944 (1.697–2.191) | 0.0 |
| index | index-swar | java-swar | 1.262 (1.234–1.289) | 0.0 |
| index | native-index-swar (native) | swar | 1.133 | — |
| index | index-native-swar | native-swar-svm | 1.116 (1.108–1.123) | 0.0 |
| index | index-table | java-table | 0.641 (0.603–0.678) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.423 | — |
| index | index-scalar | java-scalar | 0.325 (0.322–0.327) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 1.423 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 1.206 (1.187–1.224) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.072 (1.071–1.072) | 0.0 |
| scan | kernel-native | native-avx512-svm | 1.028 (0.834–1.221) | 0.0 |
| scan | kernel-swar | java-swar | 0.725 (0.664–0.786) | 0.0 |
| scan | csv-core-scan (native) |  | 0.614 | — |
| scan | csv-byte-record (native) |  | 0.569 | — |
| scan | truffledata-scanner |  | 0.534 (0.531–0.536) | 0.0 |
| scan | kernel-table | java-table | 0.386 (0.354–0.418) | 0.0 |
| scan | fastcsv |  | 0.187 (0.166–0.209) | 2.6 |
| scan | jackson |  | 0.151 (0.149–0.154) | 855.8 |
| strings | csv-string-record (native) |  | 0.325 | — |
| strings | kernel-native | native-avx512-svm | 0.297 (0.295–0.299) | 898.1 |
| strings | kernel-swar | java-swar | 0.238 (0.228–0.249) | 898.1 |
| strings | fastcsv |  | 0.181 (0.173–0.188) | 986.5 |
| strings | univocity |  | 0.153 (0.147–0.158) | 967.2 |
| strings | jackson |  | 0.132 (0.119–0.146) | 855.8 |
| strings | truffledata-batch |  | 0.102 (0.083–0.121) | 2039.9 |
| strings | commons-csv |  | 0.057 (0.057–0.057) | 1009.2 |
| typed | kernel-native | native-avx512-svm | 0.376 (0.336–0.416) | 0.0 |
| typed | kernel-swar | java-swar | 0.334 (0.292–0.375) | 0.0 |
| typed | csv-typed (native) |  | 0.258 | — |
| typed | truffledata-plan |  | 0.164 (0.160–0.169) | 1077.2 |
| typed | fastcsv |  | 0.097 (0.089–0.104) | 1229.7 |
| typed | univocity |  | 0.095 (0.092–0.098) | 1210.4 |
| typed | jackson |  | 0.090 (0.089–0.092) | 1099.0 |
| typed | commons-csv |  | 0.046 (0.046–0.046) | 1252.4 |
| utf8 | simdutf-validate (native) |  | 8.299 | — |

## csv-comparison-v3: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 4.593 | — |
| index | index-native-avx512 | native-avx512-svm | 4.379 (4.069–4.688) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.323 | — |
| index | index-native-small | native-avx512-svm-leaf | 4.269 (4.189–4.349) | 0.0 |
| index | index-native | native-avx512-svm | 4.259 (4.087–4.432) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 3.984 (3.671–4.296) | 0.0 |
| index | index-vector | java-vector256 | 3.100 (2.995–3.206) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.336 (1.254–1.417) | 0.0 |
| index | index-swar | java-swar | 1.315 (1.239–1.392) | 0.0 |
| index | native-index-swar (native) | swar | 1.263 | — |
| index | native-index-scalar (native) | scalar | 0.595 | — |
| index | index-table | java-table | 0.430 (0.402–0.457) | 0.0 |
| index | index-scalar | java-scalar | 0.315 (0.301–0.328) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 3.599 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 3.389 (3.380–3.397) | 0.0 |
| scan | kernel-native | native-avx512-svm | 2.403 (2.194–2.612) | 0.0 |
| scan | kernel-vector | java-vector256 | 1.739 (1.208–2.270) | 0.0 |
| scan | kernel-swar | java-swar | 1.007 (0.875–1.138) | 0.0 |
| scan | truffledata-scanner |  | 0.637 (0.580–0.693) | 0.0 |
| scan | csv-core-scan (native) |  | 0.552 | — |
| scan | csv-byte-record (native) |  | 0.496 | — |
| scan | kernel-table | java-table | 0.384 (0.367–0.402) | 0.0 |
| scan | fastcsv |  | 0.252 (0.250–0.253) | 2.8 |
| scan | jackson |  | 0.198 (0.179–0.217) | 283.1 |
| strings | csv-string-record (native) |  | 0.460 | — |
| strings | kernel-native | native-avx512-svm | 0.392 (0.358–0.427) | 283.1 |
| strings | kernel-swar | java-swar | 0.290 (0.278–0.303) | 283.1 |
| strings | jackson |  | 0.211 (0.209–0.214) | 283.1 |
| strings | fastcsv |  | 0.208 (0.199–0.216) | 333.9 |
| strings | truffledata-batch |  | 0.182 (0.168–0.196) | 893.2 |
| strings | univocity |  | 0.141 (0.137–0.145) | 314.4 |
| strings | commons-csv |  | 0.075 (0.075–0.075) | 356.5 |
| utf8 | simdutf-validate (native) |  | 23.554 | — |

## csv-comparison-v3: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-svm-leaf | 12.164 (11.908–12.419) | 0.0 |
| index | index-native-avx512 | native-avx512-svm | 12.135 (12.006–12.263) | 0.0 |
| index | index-native-avx2 | native-avx2-svm | 11.347 (11.182–11.512) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 10.526 | — |
| index | index-native | native-avx512-svm | 10.086 (7.858–12.314) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 9.865 | — |
| index | index-vector | java-vector256 | 6.596 (6.475–6.718) | 0.0 |
| index | native-index-swar (native) | swar | 2.105 | — |
| index | index-swar | java-swar | 1.957 (1.868–2.047) | 0.0 |
| index | index-native-swar | native-swar-svm | 1.886 (1.840–1.933) | 0.0 |
| index | native-index-scalar (native) | scalar | 1.840 | — |
| index | index-table | java-table | 0.861 (0.847–0.876) | 0.0 |
| index | index-scalar | java-scalar | 0.846 (0.640–1.052) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 9.189 | — |
| scan | kernel-native-small | native-avx512-svm-leaf | 9.126 (9.059–9.193) | 0.0 |
| scan | kernel-native | native-avx512-svm | 8.061 (6.901–9.221) | 0.0 |
| scan | kernel-vector | java-vector256 | 4.942 (4.322–5.562) | 0.0 |
| scan | kernel-swar | java-swar | 1.948 (1.569–2.327) | 0.0 |
| scan | truffledata-scanner |  | 1.658 (1.487–1.830) | 0.0 |
| scan | csv-core-scan (native) |  | 1.387 | — |
| scan | fastcsv |  | 1.145 (1.142–1.149) | 8.0 |
| scan | csv-byte-record (native) |  | 0.985 | — |
| scan | kernel-table | java-table | 0.831 (0.815–0.846) | 0.0 |
| scan | jackson |  | 0.327 (0.327–0.328) | 768.0 |
| strings | kernel-native | native-avx512-svm | 2.725 (2.648–2.803) | 768.0 |
| strings | kernel-swar | java-swar | 1.007 (0.979–1.035) | 767.9 |
| strings | fastcsv |  | 0.963 (0.916–1.011) | 824.0 |
| strings | csv-string-record (native) |  | 0.864 | — |
| strings | truffledata-batch |  | 0.491 (0.182–0.800) | 1640.8 |
| strings | univocity |  | 0.436 (0.433–0.438) | 813.1 |
| strings | jackson |  | 0.301 (0.255–0.347) | 768.0 |
| strings | commons-csv |  | 0.154 (0.154–0.155) | 844.0 |
| utf8 | simdutf-validate (native) |  | 31.715 | — |

## csv_jvm: numeric, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-ffm | 7.828 (7.750–7.906) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 6.528 | — |
| index | index-native | native-avx512-ffm | 5.653 (5.319–5.987) | 0.0 |
| index | index-vector | java-vector512 | 5.342 (5.304–5.379) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.979 | — |
| index | index-native-avx512 | native-avx512-ffm | 4.958 (4.776–5.141) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 4.400 (4.098–4.702) | 0.0 |
| index | index-swar | java-swar | 2.016 (1.831–2.200) | 0.0 |
| index | index-native-swar | native-swar-ffm | 1.495 (1.484–1.506) | 0.0 |
| index | native-index-swar (native) | swar | 1.372 | — |
| index | index-scalar | java-scalar | 1.181 (1.109–1.252) | 0.0 |
| index | index-table | java-table | 0.819 (0.766–0.872) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.622 | — |
| scan | kernel-native-small | native-avx512-ffm | 2.642 (2.484–2.801) | 0.0 |
| scan | kernel-vector | java-vector512 | 2.307 (2.169–2.446) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 2.274 (2.105–2.442) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.081 | — |
| scan | kernel-swar | java-swar | 1.483 (1.445–1.521) | 0.0 |
| scan | truffledata-scanner |  | 0.870 (0.837–0.903) | 0.0 |
| scan | csv-core-scan (native) |  | 0.868 | — |
| scan | kernel-table | java-table | 0.586 (0.550–0.622) | 0.0 |
| scan | csv-byte-record (native) |  | 0.560 | — |
| scan | fastcsv |  | 0.497 (0.419–0.575) | 2.0 |
| scan | jackson |  | 0.164 (0.149–0.179) | 384.1 |
| strings | kernel-native | native-avx512-ffm | 0.600 (0.531–0.669) | 0.0 |
| strings | kernel-swar | java-swar | 0.595 (0.512–0.679) | 0.0 |
| strings | csv-string-record (native) |  | 0.374 | — |
| strings | truffledata-batch |  | 0.309 (0.292–0.326) | 323.2 |
| strings | jackson |  | 0.216 (0.186–0.245) | 384.1 |
| strings | fastcsv |  | 0.167 (0.008–0.326) | 466.1 |
| strings | commons-csv |  | 0.112 (0.110–0.113) | 481.3 |
| strings | univocity |  | 0.105 (0.015–0.194) | 641.8 |
| typed | kernel-native | native-avx512-ffm | 0.520 (0.479–0.561) | 0.0 |
| typed | kernel-swar | java-swar | 0.454 (0.372–0.536) | 0.0 |
| typed | csv-typed (native) |  | 0.359 | — |
| typed | fastcsv |  | 0.170 (0.165–0.175) | 538.1 |
| typed | truffledata-plan |  | 0.164 (0.137–0.191) | 317.5 |
| typed | jackson |  | 0.139 (0.083–0.195) | 456.1 |
| typed | commons-csv |  | 0.105 (0.101–0.110) | 649.3 |
| typed | univocity |  | 0.062 (0.006–0.118) | 714.1 |
| utf8 | simdutf-validate (native) |  | 81.314 | — |

## csv_jvm: escaped, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | index-native-small | native-avx512-ffm | 9.648 (9.259–10.037) | 0.1 |
| index | native-index-avx2 (native) | avx2 | 9.540 | — |
| index | native-index-avx512 (native) | avx512 | 8.682 | — |
| index | index-native | native-avx512-ffm | 7.920 (7.426–8.413) | 0.0 |
| index | index-vector | java-vector512 | 6.617 (5.412–7.822) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 6.260 (6.246–6.274) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 5.083 (2.729–7.438) | 0.0 |
| index | index-swar | java-swar | 2.368 (2.330–2.405) | 0.0 |
| index | native-index-swar (native) | swar | 1.891 | — |
| index | index-native-swar | native-swar-ffm | 1.720 (1.720–1.720) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.859 | — |
| index | index-table | java-table | 0.821 (0.796–0.846) | 0.0 |
| index | index-scalar | java-scalar | 0.555 (0.536–0.574) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 6.968 (6.552–7.383) | 0.1 |
| scan | native-indexed-scan (native) | avx512 | 6.421 | — |
| scan | kernel-native | native-avx512-ffm | 5.699 (5.668–5.731) | 0.0 |
| scan | kernel-vector | java-vector512 | 5.481 (4.409–6.552) | 0.0 |
| scan | kernel-swar | java-swar | 2.106 (2.053–2.159) | 0.0 |
| scan | truffledata-scanner |  | 0.995 (0.979–1.010) | 0.1 |
| scan | kernel-table | java-table | 0.749 (0.705–0.793) | 0.0 |
| scan | csv-core-scan (native) |  | 0.748 | — |
| scan | csv-byte-record (native) |  | 0.552 | — |
| scan | jackson |  | 0.367 (0.364–0.369) | 332.3 |
| scan | fastcsv |  | 0.304 (0.230–0.377) | 9.1 |
| strings | csv-string-record (native) |  | 0.485 | — |
| strings | kernel-native | native-avx512-ffm | 0.386 (0.350–0.422) | 0.0 |
| strings | kernel-swar | java-swar | 0.353 (0.324–0.381) | 0.0 |
| strings | jackson |  | 0.334 (0.294–0.373) | 332.3 |
| strings | fastcsv |  | 0.294 (0.289–0.298) | 405.4 |
| strings | truffledata-batch |  | 0.264 (0.264–0.264) | 602.6 |
| strings | commons-csv |  | 0.218 (0.215–0.222) | 418.1 |
| strings | univocity |  | 0.045 (0.010–0.080) | 1301.5 |
| utf8 | simdutf-validate (native) |  | 78.623 | — |

## csv_jvm: quoted, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 15.434 | — |
| index | native-index-avx512 (native) | avx512 | 15.025 | — |
| index | index-native-small | native-avx512-ffm | 13.712 (13.349–14.075) | 0.2 |
| index | index-native | native-avx512-ffm | 10.686 (9.368–12.004) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 9.145 (8.992–9.299) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 8.593 (6.034–11.153) | 0.0 |
| index | index-vector | java-vector512 | 7.342 (6.224–8.461) | 0.0 |
| index | index-swar | java-swar | 2.350 (2.146–2.554) | 0.0 |
| index | native-index-swar (native) | swar | 1.801 | — |
| index | index-scalar | java-scalar | 1.716 (1.265–2.167) | 0.0 |
| index | native-index-scalar (native) | scalar | 1.698 | — |
| index | index-native-swar | native-swar-ffm | 1.657 (1.377–1.938) | 0.0 |
| index | index-table | java-table | 0.802 (0.658–0.946) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 13.627 (13.563–13.690) | 0.2 |
| scan | native-indexed-scan (native) | avx512 | 9.622 | — |
| scan | kernel-native | native-avx512-ffm | 8.461 (6.259–10.662) | 0.0 |
| scan | kernel-vector | java-vector512 | 7.494 (6.937–8.051) | 0.0 |
| scan | truffledata-scanner |  | 2.781 (2.491–3.071) | 0.3 |
| scan | kernel-swar | java-swar | 2.556 (2.389–2.723) | 0.0 |
| scan | csv-core-scan (native) |  | 1.242 | — |
| scan | fastcsv |  | 1.097 (1.083–1.110) | 26.2 |
| scan | kernel-table | java-table | 0.743 (0.683–0.804) | 0.0 |
| scan | csv-byte-record (native) |  | 0.720 | — |
| scan | jackson |  | 0.291 (0.261–0.321) | 833.0 |
| strings | kernel-native | native-avx512-ffm | 2.265 (2.030–2.501) | 0.0 |
| strings | kernel-swar | java-swar | 1.189 (1.177–1.201) | 0.0 |
| strings | fastcsv |  | 0.989 (0.950–1.027) | 923.0 |
| strings | csv-string-record (native) |  | 0.872 | — |
| strings | truffledata-batch |  | 0.570 (0.052–1.089) | 850.0 |
| strings | jackson |  | 0.267 (0.178–0.355) | 833.0 |
| strings | univocity |  | 0.255 (0.104–0.405) | 3558.3 |
| strings | commons-csv |  | 0.120 (0.015–0.225) | 936.8 |
| utf8 | simdutf-validate (native) |  | 94.536 | — |

## csv_jvm: mixed, 1.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 3.400 | — |
| index | native-index-avx512 (native) | avx512 | 3.317 | — |
| index | index-vector | java-vector512 | 3.130 (2.971–3.290) | 0.0 |
| index | index-native-small | native-avx512-ffm | 2.866 (2.679–3.053) | 0.1 |
| index | index-native | native-avx512-ffm | 2.802 (2.736–2.869) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 2.631 (2.343–2.919) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 2.451 (2.149–2.753) | 0.0 |
| index | index-swar | java-swar | 1.485 (1.454–1.516) | 0.0 |
| index | native-index-swar (native) | swar | 1.093 | — |
| index | index-native-swar | native-swar-ffm | 1.049 (1.035–1.062) | 0.0 |
| index | index-table | java-table | 0.497 (0.484–0.509) | 0.0 |
| index | index-scalar | java-scalar | 0.469 (0.409–0.530) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.294 | — |
| scan | native-indexed-scan (native) | avx512 | 1.466 | — |
| scan | kernel-native-small | native-avx512-ffm | 1.463 (1.239–1.687) | 0.1 |
| scan | kernel-vector | java-vector512 | 1.311 (1.280–1.342) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 1.309 (1.073–1.544) | 0.0 |
| scan | kernel-swar | java-swar | 1.031 (1.008–1.053) | 0.0 |
| scan | csv-core-scan (native) |  | 0.607 | — |
| scan | truffledata-scanner |  | 0.468 (0.429–0.507) | 0.2 |
| scan | kernel-table | java-table | 0.403 (0.384–0.422) | 0.0 |
| scan | csv-byte-record (native) |  | 0.370 | — |
| scan | fastcsv |  | 0.356 (0.320–0.392) | 9.1 |
| scan | jackson |  | 0.164 (0.132–0.197) | 1227.9 |
| strings | kernel-native | native-avx512-ffm | 0.475 (0.467–0.484) | 46.3 |
| strings | kernel-swar | java-swar | 0.316 (0.260–0.373) | 46.3 |
| strings | csv-string-record (native) |  | 0.300 | — |
| strings | truffledata-batch |  | 0.178 (0.153–0.204) | 1158.8 |
| strings | jackson |  | 0.154 (0.151–0.157) | 1227.9 |
| strings | fastcsv |  | 0.151 (0.148–0.154) | 1380.9 |
| strings | univocity |  | 0.101 (0.066–0.135) | 2269.9 |
| strings | commons-csv |  | 0.100 (0.083–0.117) | 1393.9 |
| typed | kernel-native | native-avx512-ffm | 0.663 (0.658–0.667) | 0.0 |
| typed | kernel-swar | java-swar | 0.375 (0.315–0.434) | 0.0 |
| typed | csv-typed (native) |  | 0.260 | — |
| typed | truffledata-plan |  | 0.176 (0.164–0.189) | 1049.1 |
| typed | jackson |  | 0.137 (0.111–0.163) | 1567.1 |
| typed | fastcsv |  | 0.116 (0.095–0.136) | 1720.1 |
| typed | univocity |  | 0.110 (0.090–0.129) | 2417.1 |
| typed | commons-csv |  | 0.065 (0.054–0.076) | 1733.9 |
| utf8 | simdutf-validate (native) |  | 21.279 | — |

## csv_jvm: numeric, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 8.548 | — |
| index | index-native-small | native-avx512-ffm | 6.154 (4.019–8.289) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 5.895 | — |
| index | index-vector | java-vector512 | 5.131 (4.746–5.516) | 0.0 |
| index | index-native | native-avx512-ffm | 4.706 (4.136–5.275) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 4.591 (4.441–4.742) | 0.0 |
| index | index-native-avx512 | native-avx512-ffm | 4.435 (4.413–4.457) | 0.0 |
| index | native-index-swar (native) | swar | 1.682 | — |
| index | index-swar | java-swar | 1.577 (0.754–2.401) | 0.0 |
| index | index-native-swar | native-swar-ffm | 1.233 (1.193–1.273) | 0.0 |
| index | index-scalar | java-scalar | 0.945 (0.840–1.050) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.766 | — |
| index | index-table | java-table | 0.654 (0.544–0.765) | 0.0 |
| scan | kernel-vector | java-vector512 | 2.598 (2.432–2.764) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.533 | — |
| scan | kernel-native-small | native-avx512-ffm | 2.394 (2.175–2.613) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 1.705 (1.580–1.830) | 0.0 |
| scan | kernel-swar | java-swar | 1.635 (1.557–1.713) | 0.0 |
| scan | csv-core-scan (native) |  | 1.102 | — |
| scan | truffledata-scanner |  | 0.852 (0.752–0.953) | 0.0 |
| scan | csv-byte-record (native) |  | 0.786 | — |
| scan | fastcsv |  | 0.747 (0.606–0.889) | 0.0 |
| scan | kernel-table | java-table | 0.731 (0.636–0.827) | 0.0 |
| scan | jackson |  | 0.182 (0.092–0.272) | 384.0 |
| strings | csv-string-record (native) |  | 0.723 | — |
| strings | kernel-swar | java-swar | 0.664 (0.587–0.741) | 0.0 |
| strings | kernel-native | native-avx512-ffm | 0.564 (0.556–0.572) | 0.0 |
| strings | fastcsv |  | 0.413 (0.370–0.457) | 464.0 |
| strings | univocity |  | 0.376 (0.339–0.413) | 433.7 |
| strings | truffledata-batch |  | 0.332 (0.325–0.339) | 333.6 |
| strings | jackson |  | 0.306 (0.300–0.313) | 384.0 |
| strings | commons-csv |  | 0.165 (0.160–0.169) | 480.0 |
| typed | kernel-native | native-avx512-ffm | 0.554 (0.525–0.583) | 0.0 |
| typed | kernel-swar | java-swar | 0.535 (0.507–0.563) | 0.0 |
| typed | csv-typed (native) |  | 0.453 | — |
| typed | fastcsv |  | 0.212 (0.187–0.237) | 536.0 |
| typed | univocity |  | 0.149 (0.137–0.161) | 601.7 |
| typed | truffledata-plan |  | 0.131 (0.045–0.218) | 327.0 |
| typed | jackson |  | 0.125 (0.120–0.129) | 504.0 |
| typed | commons-csv |  | 0.111 (0.099–0.123) | 648.0 |
| utf8 | simdutf-validate (native) |  | 34.414 | — |

## csv_jvm: mixed, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 3.000 | — |
| index | index-native-small | native-avx512-ffm | 2.894 (2.827–2.962) | 0.0 |
| index | index-vector | java-vector512 | 2.382 (2.280–2.485) | 0.0 |
| index | index-native | native-avx512-ffm | 2.371 (2.342–2.399) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 2.358 | — |
| index | index-native-avx512 | native-avx512-ffm | 2.316 (2.264–2.367) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 2.115 (1.905–2.324) | 0.0 |
| index | index-swar | java-swar | 1.434 (1.431–1.438) | 0.0 |
| index | native-index-swar (native) | swar | 1.124 | — |
| index | index-native-swar | native-swar-ffm | 0.993 (0.991–0.995) | 0.0 |
| index | index-table | java-table | 0.721 (0.711–0.732) | 0.0 |
| index | index-scalar | java-scalar | 0.483 (0.482–0.485) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.416 | — |
| scan | kernel-native-small | native-avx512-ffm | 1.612 (1.589–1.634) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 1.391 (1.375–1.407) | 0.0 |
| scan | kernel-vector | java-vector512 | 1.278 (1.229–1.327) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 1.007 | — |
| scan | kernel-swar | java-swar | 0.839 (0.647–1.031) | 0.0 |
| scan | csv-core-scan (native) |  | 0.612 | — |
| scan | truffledata-scanner |  | 0.519 (0.460–0.579) | 0.0 |
| scan | csv-byte-record (native) |  | 0.495 | — |
| scan | kernel-table | java-table | 0.487 (0.362–0.611) | 0.0 |
| scan | fastcsv |  | 0.370 (0.368–0.372) | 0.1 |
| scan | jackson |  | 0.148 (0.147–0.149) | 1227.4 |
| strings | kernel-native | native-avx512-ffm | 0.434 (0.413–0.454) | 46.0 |
| strings | kernel-swar | java-swar | 0.398 (0.397–0.400) | 46.0 |
| strings | csv-string-record (native) |  | 0.324 | — |
| strings | jackson |  | 0.201 (0.192–0.210) | 1227.4 |
| strings | fastcsv |  | 0.193 (0.190–0.195) | 1371.4 |
| strings | truffledata-batch |  | 0.184 (0.175–0.192) | 1163.8 |
| strings | univocity |  | 0.133 (0.122–0.143) | 1346.7 |
| strings | commons-csv |  | 0.104 (0.089–0.119) | 1387.4 |
| typed | kernel-swar | java-swar | 0.494 (0.490–0.498) | 0.0 |
| typed | kernel-native | native-avx512-ffm | 0.462 (0.428–0.497) | 0.0 |
| typed | csv-typed (native) |  | 0.248 | — |
| typed | truffledata-plan |  | 0.167 (0.159–0.176) | 1077.2 |
| typed | univocity |  | 0.147 (0.147–0.147) | 1493.9 |
| typed | jackson |  | 0.141 (0.129–0.153) | 1566.5 |
| typed | fastcsv |  | 0.140 (0.108–0.172) | 1710.6 |
| typed | commons-csv |  | 0.078 (0.062–0.095) | 1726.6 |
| utf8 | simdutf-validate (native) |  | 7.870 | — |

## csv_jvm: escaped, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx2 (native) | avx2 | 3.753 | — |
| index | index-native-small | native-avx512-ffm | 3.575 (3.255–3.895) | 0.0 |
| index | index-vector | java-vector512 | 3.399 (3.346–3.452) | 0.0 |
| index | native-index-avx512 (native) | avx512 | 3.379 | — |
| index | index-native-avx512 | native-avx512-ffm | 3.316 (3.212–3.420) | 0.0 |
| index | index-native | native-avx512-ffm | 3.184 (2.998–3.370) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 3.152 (3.146–3.157) | 0.0 |
| index | index-swar | java-swar | 1.658 (1.649–1.666) | 0.0 |
| index | native-index-swar (native) | swar | 1.112 | — |
| index | index-native-swar | native-swar-ffm | 0.988 (0.845–1.131) | 0.0 |
| index | index-table | java-table | 0.858 (0.856–0.860) | 0.0 |
| index | native-index-scalar (native) | scalar | 0.594 | — |
| index | index-scalar | java-scalar | 0.348 (0.281–0.416) | 0.0 |
| scan | kernel-native-small | native-avx512-ffm | 2.839 (2.432–3.247) | 0.0 |
| scan | kernel-vector | java-vector512 | 2.838 (2.438–3.239) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 2.799 | — |
| scan | kernel-native | native-avx512-ffm | 2.525 (2.480–2.570) | 0.0 |
| scan | kernel-swar | java-swar | 1.611 (1.603–1.619) | 0.0 |
| scan | truffledata-scanner |  | 0.659 (0.658–0.661) | 0.0 |
| scan | kernel-table | java-table | 0.584 (0.470–0.698) | 0.0 |
| scan | csv-byte-record (native) |  | 0.452 | — |
| scan | csv-core-scan (native) |  | 0.420 | — |
| scan | jackson |  | 0.324 (0.320–0.327) | 331.1 |
| scan | fastcsv |  | 0.264 (0.259–0.269) | 0.1 |
| strings | csv-string-record (native) |  | 0.414 | — |
| strings | jackson |  | 0.323 (0.321–0.324) | 331.1 |
| strings | kernel-native | native-avx512-ffm | 0.310 (0.309–0.311) | 0.0 |
| strings | kernel-swar | java-swar | 0.245 (0.241–0.248) | 0.0 |
| strings | fastcsv |  | 0.216 (0.198–0.234) | 395.1 |
| strings | commons-csv |  | 0.189 (0.187–0.191) | 411.1 |
| strings | univocity |  | 0.188 (0.180–0.196) | 370.4 |
| strings | truffledata-batch |  | 0.153 (0.116–0.189) | 602.1 |
| utf8 | simdutf-validate (native) |  | 11.069 | — |

## csv_jvm: quoted, 128.0 MiB

| Workload | Engine | Kernel | GB/s (range) | Java B/row |
|---|---|---|---:|---:|
| index | native-index-avx512 (native) | avx512 | 5.846 | — |
| index | index-native-small | native-avx512-ffm | 5.583 (5.175–5.990) | 0.1 |
| index | index-native | native-avx512-ffm | 4.900 (4.859–4.940) | 0.0 |
| index | index-vector | java-vector512 | 4.649 (4.121–5.178) | 0.0 |
| index | native-index-avx2 (native) | avx2 | 4.552 | — |
| index | index-native-avx512 | native-avx512-ffm | 3.898 (3.789–4.007) | 0.0 |
| index | index-native-avx2 | native-avx2-ffm | 3.722 (3.668–3.776) | 0.0 |
| index | index-swar | java-swar | 1.633 (1.520–1.746) | 0.0 |
| index | index-native-swar | native-swar-ffm | 1.146 (1.012–1.279) | 0.0 |
| index | index-scalar | java-scalar | 1.138 (1.136–1.140) | 0.0 |
| index | native-index-swar (native) | swar | 1.092 | — |
| index | native-index-scalar (native) | scalar | 0.941 | — |
| index | index-table | java-table | 0.853 (0.850–0.856) | 0.0 |
| scan | native-indexed-scan (native) | avx512 | 5.166 | — |
| scan | kernel-native-small | native-avx512-ffm | 4.800 (4.570–5.029) | 0.1 |
| scan | kernel-vector | java-vector512 | 4.784 (4.745–4.823) | 0.0 |
| scan | kernel-native | native-avx512-ffm | 3.533 (3.322–3.744) | 0.0 |
| scan | kernel-swar | java-swar | 1.604 (1.380–1.829) | 0.0 |
| scan | truffledata-scanner |  | 1.389 (1.338–1.441) | 0.0 |
| scan | fastcsv |  | 1.130 (0.967–1.293) | 0.2 |
| scan | csv-core-scan (native) |  | 0.831 | — |
| scan | csv-byte-record (native) |  | 0.754 | — |
| scan | kernel-table | java-table | 0.641 (0.640–0.642) | 0.0 |
| scan | jackson |  | 0.228 (0.202–0.253) | 832.0 |
| strings | kernel-native | native-avx512-ffm | 1.093 (1.002–1.184) | 0.0 |
| strings | kernel-swar | java-swar | 0.838 (0.774–0.902) | 0.0 |
| strings | fastcsv |  | 0.758 (0.745–0.771) | 896.2 |
| strings | csv-string-record (native) |  | 0.715 | — |
| strings | truffledata-batch |  | 0.545 (0.505–0.584) | 849.6 |
| strings | univocity |  | 0.271 (0.249–0.294) | 885.0 |
| strings | jackson |  | 0.270 (0.251–0.288) | 832.0 |
| strings | commons-csv |  | 0.220 (0.216–0.223) | 912.2 |
| utf8 | simdutf-validate (native) |  | 10.953 | — |

## Size sweep (kernel crossovers; GB/s, one fork)

| Binary | Workload | Dataset | Engine | 4 KiB | 16 KiB | 64 KiB | 256 KiB | 1024 KiB | 4096 KiB | 16384 KiB |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| csv-comparison | index | mixed | index-native | 2.94 | 3.00 | 2.50 | 3.61 | 3.32 | 2.03 | 2.71 |
| csv-comparison | index | mixed | index-native-small | 2.99 | 3.31 | 4.04 | 4.29 | 3.42 | 2.63 | 2.47 |
| csv-comparison | index | mixed | index-swar | 1.10 | 1.08 | 0.92 | 0.89 | 0.50 | 0.91 | 1.03 |
| csv-comparison | index | mixed | index-vector | 0.02 | 0.04 | 0.04 | 0.03 | 0.04 | 0.04 | 0.03 |
| csv-comparison | index | numeric | index-native | 2.66 | 3.13 | 4.17 | 2.93 | 3.36 | 3.03 | 3.34 |
| csv-comparison | index | numeric | index-native-small | 2.56 | 3.02 | 3.79 | 3.63 | 3.25 | 3.52 | 3.30 |
| csv-comparison | index | numeric | index-swar | 0.85 | 0.83 | 1.27 | 1.17 | 0.96 | 0.96 | 0.99 |
| csv-comparison | index | numeric | index-vector | 0.03 | 0.03 | 0.03 | 0.04 | 0.04 | 0.03 | 0.03 |
| csv-comparison | scan | mixed | kernel-native | 0.76 | 1.28 | 1.20 | 0.98 | 1.14 | 0.93 | 0.99 |
| csv-comparison | scan | mixed | kernel-native-small | 1.11 | 0.82 | 1.22 | 1.22 | 1.00 | 0.89 | 0.94 |
| csv-comparison | scan | mixed | kernel-swar | 0.66 | 0.72 | 0.61 | 0.51 | 0.64 | 0.43 | 0.61 |
| csv-comparison | scan | mixed | truffledata-scanner | 0.49 | 0.34 | 0.43 | 0.40 | 0.35 | 0.54 | 0.42 |
| csv-comparison | scan | numeric | kernel-native | 0.66 | 0.74 | 0.77 | 0.89 | 0.45 | 0.62 | 0.63 |
| csv-comparison | scan | numeric | kernel-native-small | 0.60 | 0.73 | 0.72 | 0.60 | 0.61 | 0.78 | 0.92 |
| csv-comparison | scan | numeric | kernel-swar | 0.26 | 0.44 | 0.46 | 0.45 | 0.47 | 0.47 | 0.61 |
| csv-comparison | scan | numeric | truffledata-scanner | 0.40 | 0.41 | 0.33 | 0.37 | 0.40 | 0.44 | 0.40 |
| csv-comparison-v3 | index | mixed | index-native | 6.14 | 6.88 | 7.26 | 7.91 | 6.02 | 3.55 | 4.75 |
| csv-comparison-v3 | index | mixed | index-native-small | 6.34 | 7.37 | 7.60 | 7.98 | 7.84 | 4.58 | 4.71 |
| csv-comparison-v3 | index | mixed | index-swar | 1.96 | 2.15 | 2.11 | 2.20 | 1.63 | 1.72 | 1.80 |
| csv-comparison-v3 | index | mixed | index-vector | 3.21 | 5.50 | 4.92 | 5.11 | 3.73 | 3.19 | 3.22 |
| csv-comparison-v3 | index | numeric | index-native | 5.57 | 6.95 | 8.44 | 7.30 | 6.75 | 6.95 | 7.80 |
| csv-comparison-v3 | index | numeric | index-native-small | 6.39 | 7.54 | 8.07 | 8.18 | 8.48 | 7.50 | 8.35 |
| csv-comparison-v3 | index | numeric | index-swar | 1.88 | 1.85 | 2.19 | 1.83 | 2.15 | 2.06 | 2.17 |
| csv-comparison-v3 | index | numeric | index-vector | 4.39 | 4.93 | 4.52 | 4.89 | 5.01 | 5.24 | 5.13 |
| csv-comparison-v3 | scan | mixed | kernel-native | 1.81 | 2.02 | 2.05 | 2.12 | 1.90 | 1.59 | 1.77 |
| csv-comparison-v3 | scan | mixed | kernel-native-small | 1.82 | 2.07 | 2.10 | 2.05 | 2.08 | 1.82 | 1.77 |
| csv-comparison-v3 | scan | mixed | kernel-swar | 1.21 | 1.20 | 1.26 | 1.26 | 1.11 | 1.08 | 1.09 |
| csv-comparison-v3 | scan | mixed | truffledata-scanner | 1.21 | 0.84 | 0.70 | 0.68 | 0.84 | 0.83 | 0.74 |
| csv-comparison-v3 | scan | numeric | kernel-native | 1.30 | 1.43 | 1.62 | 1.59 | 1.23 | 1.47 | 1.43 |
| csv-comparison-v3 | scan | numeric | kernel-native-small | 0.97 | 1.52 | 1.50 | 1.41 | 1.12 | 1.58 | 1.57 |
| csv-comparison-v3 | scan | numeric | kernel-swar | 0.77 | 0.74 | 0.97 | 0.92 | 0.99 | 0.98 | 0.91 |
| csv-comparison-v3 | scan | numeric | truffledata-scanner | 0.81 | 0.72 | 0.71 | 0.82 | 0.84 | 0.65 | 0.88 |
| csv_jvm | index | mixed | index-native | 4.89 | 6.05 | 6.24 | 7.66 | 5.33 | 3.54 | 4.61 |
| csv_jvm | index | mixed | index-native-small | 6.16 | 7.63 | 8.50 | 7.39 | 7.39 | 4.82 | 4.41 |
| csv_jvm | index | mixed | index-swar | 2.66 | 2.67 | 2.56 | 2.49 | 2.26 | 1.89 | 2.17 |
| csv_jvm | index | mixed | index-vector | 5.04 | 7.51 | 7.67 | 7.78 | 4.36 | 3.90 | 3.96 |
| csv_jvm | index | numeric | index-native | 4.39 | 5.69 | 7.54 | 6.02 | 6.79 | 5.47 | 5.44 |
| csv_jvm | index | numeric | index-native-small | 6.46 | 9.54 | 8.53 | 8.60 | 8.57 | 9.77 | 7.53 |
| csv_jvm | index | numeric | index-swar | 2.22 | 3.14 | 2.76 | 1.94 | 2.88 | 2.81 | 2.48 |
| csv_jvm | index | numeric | index-vector | 6.48 | 6.87 | 6.49 | 6.62 | 7.85 | 6.64 | 6.41 |
| csv_jvm | scan | mixed | kernel-native | 2.55 | 3.50 | 2.93 | 2.95 | 2.49 | 2.50 | 1.99 |
| csv_jvm | scan | mixed | kernel-native-small | 2.94 | 2.59 | 3.11 | 3.54 | 2.61 | 2.60 | 2.95 |
| csv_jvm | scan | mixed | kernel-swar | 1.98 | 1.74 | 1.72 | 1.62 | 1.79 | 1.54 | 1.53 |
| csv_jvm | scan | mixed | truffledata-scanner | 1.60 | 1.80 | 1.19 | 0.90 | 0.85 | 1.06 | 0.80 |
| csv_jvm | scan | numeric | kernel-native | 2.14 | 3.26 | 2.95 | 3.45 | 1.95 | 2.36 | 1.89 |
| csv_jvm | scan | numeric | kernel-native-small | 1.56 | 3.04 | 2.45 | 2.11 | 2.28 | 2.30 | 2.89 |
| csv_jvm | scan | numeric | kernel-swar | 1.27 | 0.92 | 1.98 | 1.40 | 1.55 | 1.48 | 1.63 |
| csv_jvm | scan | numeric | truffledata-scanner | 1.41 | 1.27 | 1.20 | 1.06 | 1.10 | 0.85 | 1.08 |

## Retained typed reads (32 numeric rows, 8 typed columns read per row)

| Binary | Engine | Stage | ns/row (range) | Java B/row |
|---|---|---|---:|---:|
| csv-comparison | batch-fastcsv | interpret | 79.12 (74.28–83.95) | 2.00 |
| csv-comparison | batch-fastcsv | peak | 17.44 (15.39–19.49) | 1.00 |
| csv-comparison | batch-fastcsv | read-aot | 79.99 (78.95–81.03) | 0.00 |
| csv-comparison | truffledata-plan | interpret | 124.58 (118.54–130.62) | 2.00 |
| csv-comparison | truffledata-plan | peak | 19.02 (13.60–24.44) | 1.00 |
| csv-comparison | truffledata-plan | read-aot | 96.20 (93.45–98.96) | 0.00 |
| csv-comparison-v3 | batch-fastcsv | interpret | 69.69 (68.25–71.13) | 2.00 |
| csv-comparison-v3 | batch-fastcsv | peak | 15.29 (12.84–17.74) | 1.00 |
| csv-comparison-v3 | batch-fastcsv | read-aot | 87.70 (84.80–90.59) | 0.00 |
| csv-comparison-v3 | truffledata-plan | interpret | 87.29 (77.55–97.03) | 2.00 |
| csv-comparison-v3 | truffledata-plan | peak | 14.32 (8.11–20.53) | 1.00 |
| csv-comparison-v3 | truffledata-plan | read-aot | 69.10 (65.34–72.85) | 0.00 |
| csv_jvm | batch-fastcsv | interpret | 50.19 (33.61–66.78) | 2.12 |
| csv_jvm | batch-fastcsv | peak | 14.02 (12.38–15.65) | 1.50 |
| csv_jvm | batch-fastcsv | read-aot | 47.08 (35.70–58.45) | 0.00 |
| csv_jvm | truffledata-plan | interpret | 39.97 (37.89–42.05) | 2.12 |
| csv_jvm | truffledata-plan | peak | 29.03 (7.06–50.99) | 1.50 |
| csv_jvm | truffledata-plan | read-aot | 34.53 (32.82–36.24) | 0.00 |
