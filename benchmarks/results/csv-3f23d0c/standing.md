Each cell: rows ahead / rows, then the min–max ratio (shipped GB/s ÷ reference GB/s, same shard). Rows are dataset × size (1 MiB, 128 MiB).

### scan

| Runtime | Shipped path | vs fastcsv | vs jackson | vs univocity | vs commons-csv | vs kernel-native | vs csv-core-scan | vs native-indexed-scan |
|---|---|---|---|---|---|---|---|---|
| SVM (compat) | `truffledata-scanner-native` | 8/8 (2.26–18.88) | 8/8 (7.06–35.10) | — | — | 0/8 (0.87–0.97) | 8/8 (1.30–8.34) | 0/8 (0.57–0.85) |
| SVM (compat) | `truffledata-scanner` | 8/8 (1.95–6.39) | 8/8 (5.02–7.08) | — | — | 0/8 (0.18–0.75) | 7/8 (0.87–2.60) | 0/8 (0.17–0.66) |
| SVM (x86-64-v3) | `truffledata-scanner-native` | 8/8 (2.52–16.89) | 8/8 (7.76–33.65) | — | — | 0/8 (0.90–0.98) | 8/8 (1.34–7.74) | 0/8 (0.57–0.84) |
| SVM (x86-64-v3) | `truffledata-scanner` | 8/8 (1.69–5.69) | 8/8 (5.20–6.78) | — | — | 0/8 (0.18–0.61) | 6/8 (0.90–2.59) | 0/8 (0.16–0.52) |
| JVM | `truffledata-scanner-native` | 8/8 (2.43–19.98) | 8/8 (7.83–37.16) | — | — | 6/8 (0.95–1.34) | 8/8 (2.18–12.91) | 5/8 (0.95–1.11) |
| JVM | `truffledata-scanner` | 8/8 (1.34–6.31) | 8/8 (4.46–7.24) | — | — | 0/8 (0.24–0.61) | 8/8 (1.31–7.38) | 0/8 (0.20–0.62) |

### strings

| Runtime | Shipped path | vs fastcsv | vs jackson | vs univocity | vs commons-csv | vs kernel-native | vs csv-string-record |
|---|---|---|---|---|---|---|---|
| SVM (compat) | `truffledata-batch-native` | 3/8 (0.33–1.61) | 6/8 (0.67–3.19) | 5/8 (0.57–3.32) | 8/8 (1.44–7.02) | 0/8 (0.15–0.88) | 1/8 (0.21–1.04) |
| SVM (compat) | `truffledata-batch` | 3/8 (0.69–1.14) | 5/8 (0.82–2.26) | 5/8 (0.75–2.35) | 8/8 (2.28–4.98) | 0/8 (0.44–0.80) | 0/8 (0.26–0.73) |
| SVM (x86-64-v3) | `truffledata-batch-native` | 4/8 (0.58–1.55) | 8/8 (1.01–4.76) | 5/8 (0.69–4.21) | 8/8 (2.41–9.64) | 0/8 (0.44–0.84) | 2/8 (0.24–1.56) |
| SVM (x86-64-v3) | `truffledata-batch` | 0/8 (0.58–0.93) | 5/8 (0.75–2.86) | 5/8 (0.68–2.54) | 8/8 (2.19–5.80) | 0/8 (0.31–0.76) | 0/8 (0.24–0.94) |
| JVM | `truffledata-batch-native` | 8/8 (1.36–3.20) | 8/8 (1.24–11.88) | 8/8 (1.58–8.24) | 8/8 (2.19–12.41) | 0/8 (0.76–0.96) | 6/8 (0.83–4.54) |
| JVM | `truffledata-batch` | 8/8 (1.12–1.85) | 8/8 (1.08–4.50) | 8/8 (1.29–3.13) | 8/8 (1.91–4.84) | 0/8 (0.31–0.84) | 3/8 (0.68–1.72) |

### typed

| Runtime | Shipped path | vs fastcsv | vs jackson | vs univocity | vs commons-csv | vs kernel-native | vs csv-typed |
|---|---|---|---|---|---|---|---|
| SVM (compat) | `truffledata-plan-native` | 4/4 (2.62–2.96) | 4/4 (3.20–3.70) | 4/4 (2.77–3.42) | 4/4 (5.54–7.40) | 0/4 (0.57–0.70) | 2/4 (0.61–1.35) |
| SVM (compat) | `truffledata-plan` | 4/4 (2.12–2.72) | 4/4 (2.58–3.79) | 4/4 (2.24–2.90) | 4/4 (5.37–5.68) | 0/4 (0.48–0.62) | 1/4 (0.59–1.04) |
| SVM (x86-64-v3) | `truffledata-plan-native` | 4/4 (2.70–3.10) | 4/4 (3.22–3.66) | 4/4 (2.85–3.58) | 4/4 (6.18–7.83) | 0/4 (0.62–0.73) | 2/4 (0.65–1.61) |
| SVM (x86-64-v3) | `truffledata-plan` | 4/4 (2.20–2.60) | 4/4 (2.75–3.02) | 4/4 (2.37–2.90) | 4/4 (5.44–6.13) | 0/4 (0.50–0.63) | 2/4 (0.59–1.26) |
| JVM | `truffledata-plan-native` | 4/4 (2.28–2.87) | 4/4 (2.89–3.49) | 4/4 (2.56–3.06) | 4/4 (4.43–5.57) | 0/4 (0.71–0.86) | 4/4 (1.18–2.08) |
| JVM | `truffledata-plan` | 4/4 (1.95–2.21) | 4/4 (2.46–2.58) | 4/4 (2.18–2.33) | 4/4 (3.75–4.11) | 0/4 (0.53–0.72) | 4/4 (1.01–1.53) |
