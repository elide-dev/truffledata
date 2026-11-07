
### scan (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Best kernel path | Best Java competitor | csv-core-scan | native-indexed-scan |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.87 | 2.00 | **5.80** | 6.92 (kernel-native-small) | 0.31 (jackson) | 0.82 | 8.19 |
| SVM (compat) | escaped | 128 MiB | 1.06 | 2.26 | **5.01** | 5.34 (kernel-native) | 0.38 (jackson) | 1.03 | 6.30 |
| SVM (compat) | mixed | 1 MiB | 0.66 | 0.97 | **1.47** | 2.09 (kernel-native-small) | 0.35 (fastcsv) | 1.04 | 2.80 |
| SVM (compat) | mixed | 128 MiB | 0.84 | 0.91 | **1.36** | 1.84 (kernel-native-small) | 0.44 (fastcsv) | 1.08 | 2.29 |
| SVM (compat) | numeric | 1 MiB | 0.75 | 0.76 | **1.13** | 1.65 (kernel-native-small) | 0.49 (fastcsv) | 1.14 | 2.48 |
| SVM (compat) | numeric | 128 MiB | 0.41 | 0.49 | **0.51** | 0.74 (kernel-native) | 0.29 (fastcsv) | 0.47 | 0.65 |
| SVM (compat) | quoted | 1 MiB | 2.55 | 2.15 | **10.50** | 11.82 (kernel-native-small) | 0.89 (fastcsv) | 1.49 | 14.52 |
| SVM (compat) | quoted | 128 MiB | 3.37 | 2.63 | **8.90** | 11.64 (kernel-native-small) | 1.09 (fastcsv) | 1.84 | 12.79 |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.85 | 2.02 | **5.63** | 6.64 (kernel-native) | 0.34 (fastcsv) | 0.82 | 8.09 |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.66 | 1.19 | **2.91** | 3.41 (kernel-native-small) | 0.25 (fastcsv) | 0.53 | 3.55 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.82 | 0.95 | **1.44** | 1.94 (kernel-native) | 0.48 (fastcsv) | 1.06 | 2.82 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.84 | 0.92 | **1.29** | 1.82 (kernel-native) | 0.45 (fastcsv) | 1.05 | 2.27 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.67 | 0.72 | **0.92** | 1.58 (kernel-native-small) | 0.53 (fastcsv) | 1.08 | 2.23 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.81 | 0.78 | **1.11** | 1.72 (kernel-native) | 0.58 (fastcsv) | 1.17 | 2.59 |
| SVM (x86-64-v3) | quoted | 1 MiB | 2.69 | 2.24 | **10.04** | 12.59 (kernel-native) | 1.22 (fastcsv) | 1.49 | 14.49 |
| SVM (x86-64-v3) | quoted | 128 MiB | 2.70 | 2.06 | **7.93** | 9.21 (kernel-native) | 1.22 (fastcsv) | 1.47 | 10.23 |
| JVM | escaped | 1 MiB | 1.08 | 2.50 | **8.04** | 8.14 (kernel-native-small) | 0.41 (fastcsv) | 0.81 | 8.11 |
| JVM | escaped | 128 MiB | 0.67 | 1.58 | **3.28** | 3.32 (kernel-native-small) | 0.33 (jackson) | 0.46 | 3.00 |
| JVM | mixed | 1 MiB | 0.95 | 1.45 | **2.91** | 2.87 (kernel-native-small) | 0.54 (fastcsv) | 1.01 | 2.67 |
| JVM | mixed | 128 MiB | 0.59 | 1.00 | **1.55** | 1.65 (kernel-native-small) | 0.39 (fastcsv) | 0.61 | 1.42 |
| JVM | numeric | 1 MiB | 1.01 | 1.47 | **2.44** | 3.00 (kernel-native-small) | 1.09 (fastcsv) | 1.13 | 2.51 |
| JVM | numeric | 128 MiB | 1.04 | 1.52 | **2.30** | 3.31 (kernel-native-small) | 0.92 (fastcsv) | 1.13 | 2.57 |
| JVM | quoted | 1 MiB | 4.46 | 2.79 | **14.15** | 14.53 (kernel-native-small) | 1.61 (fastcsv) | 1.46 | 14.27 |
| JVM | quoted | 128 MiB | 2.82 | 1.62 | **6.92** | 6.60 (kernel-native-small) | 1.27 (fastcsv) | 0.90 | 7.24 |

### strings (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Mapped (native) | Best kernel path | Best Java competitor | csv-string-record |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.29 | 0.36 | **0.43** | 0.32 | 0.53 (kernel-native) | 0.31 (jackson) | 0.61 |
| SVM (compat) | escaped | 128 MiB | 0.23 | 0.40 | **0.41** | 0.44 | 0.64 (kernel-native) | 0.35 (jackson) | 0.81 |
| SVM (compat) | mixed | 1 MiB | 0.20 | 0.21 | **0.26** | 0.13 | 0.37 (kernel-native) | 0.25 (fastcsv) | 0.43 |
| SVM (compat) | mixed | 128 MiB | 0.20 | 0.19 | **0.23** | 0.15 | 0.43 (kernel-native) | 0.28 (fastcsv) | 0.50 |
| SVM (compat) | numeric | 1 MiB | 0.20 | 0.20 | **0.21** | 0.14 | 0.36 (kernel-native) | 0.28 (fastcsv) | 0.79 |
| SVM (compat) | numeric | 128 MiB | 0.10 | 0.11 | **0.11** | 0.07 | 0.21 (kernel-native) | 0.14 (fastcsv) | 0.37 |
| SVM (compat) | quoted | 1 MiB | 0.68 | 0.67 | **0.85** | 0.67 | 1.46 (kernel-native) | 0.63 (fastcsv) | 1.04 |
| SVM (compat) | quoted | 128 MiB | 0.79 | 0.74 | **0.58** | 0.89 | 1.78 (kernel-native) | 0.79 (fastcsv) | 1.28 |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.34 | 0.41 | **0.50** | 0.36 | 0.65 (kernel-native) | 0.32 (fastcsv) | 0.59 |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.14 | 0.27 | **0.34** | 0.31 | 0.46 (kernel-native) | 0.23 (jackson) | 0.44 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.24 | 0.25 | **0.29** | 0.16 | 0.44 (kernel-native) | 0.32 (fastcsv) | 0.48 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.19 | 0.21 | **0.24** | 0.15 | 0.45 (kernel-native) | 0.31 (fastcsv) | 0.48 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.19 | 0.20 | **0.21** | 0.12 | 0.40 (kernel-native) | 0.30 (fastcsv) | 0.78 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.19 | 0.20 | **0.18** | 0.14 | 0.43 (kernel-native) | 0.33 (fastcsv) | 0.80 |
| SVM (x86-64-v3) | quoted | 1 MiB | 1.05 | 0.90 | **1.57** | 0.93 | 2.99 (kernel-native) | 1.05 (fastcsv) | 1.05 |
| SVM (x86-64-v3) | quoted | 128 MiB | 0.97 | 0.89 | **1.27** | 1.11 | 2.73 (kernel-native) | 1.01 (fastcsv) | 1.02 |
| JVM | escaped | 1 MiB | 0.32 | 0.38 | **0.42** | 0.28 | 0.45 (kernel-native) | 0.41 (jackson) | 0.60 |
| JVM | escaped | 128 MiB | 0.15 | 0.24 | **0.26** | 0.27 | 0.31 (kernel-native) | 0.33 (jackson) | 0.42 |
| JVM | mixed | 1 MiB | 0.43 | 0.49 | **0.56** | 0.33 | 0.70 (kernel-native) | 0.24 (jackson) | 0.47 |
| JVM | mixed | 128 MiB | 0.24 | 0.23 | **0.33** | 0.23 | 0.47 (kernel-native) | 0.23 (fastcsv) | 0.32 |
| JVM | numeric | 1 MiB | 0.48 | 0.29 | **0.63** | 0.37 | 0.87 (kernel-native) | 0.41 (fastcsv) | 0.78 |
| JVM | numeric | 128 MiB | 0.29 | 0.29 | **0.65** | 0.39 | 0.87 (kernel-native) | 0.47 (fastcsv) | 0.82 |
| JVM | quoted | 1 MiB | 1.57 | 0.64 | **0.51** | 1.47 | 2.61 (kernel-native) | 1.34 (fastcsv) | 1.00 |
| JVM | quoted | 128 MiB | 0.13 | 0.84 | **0.17** | 1.35 | 1.60 (kernel-native) | 0.94 (fastcsv) | 0.79 |

### typed (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Best kernel path | Best Java competitor | csv-typed |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | mixed | 1 MiB | 0.32 | 0.38 | **0.39** | 0.74 (kernel-native) | 0.16 (fastcsv) | 0.36 |
| SVM (compat) | mixed | 128 MiB | 0.32 | 0.37 | **0.45** | 0.72 (kernel-native) | 0.16 (fastcsv) | 0.40 |
| SVM (compat) | numeric | 1 MiB | 0.20 | 0.27 | **0.30** | 0.45 (kernel-native) | 0.10 (fastcsv) | 0.47 |
| SVM (compat) | numeric | 128 MiB | 0.10 | 0.12 | **0.14** | 0.24 (kernel-native) | 0.05 (fastcsv) | 0.19 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.35 | 0.42 | **0.56** | 0.75 (kernel-native) | 0.18 (fastcsv) | 0.39 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.31 | 0.37 | **0.47** | 0.75 (kernel-native) | 0.17 (fastcsv) | 0.40 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.22 | 0.26 | **0.27** | 0.44 (kernel-native) | 0.10 (fastcsv) | 0.47 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.17 | 0.26 | **0.31** | 0.48 (kernel-native) | 0.11 (fastcsv) | 0.47 |
| JVM | mixed | 1 MiB | 0.46 | 0.55 | **0.69** | 1.10 (kernel-native) | 0.25 (fastcsv) | 0.38 |
| JVM | mixed | 128 MiB | 0.27 | 0.35 | **0.43** | 0.59 (kernel-native) | 0.18 (fastcsv) | 0.26 |
| JVM | numeric | 1 MiB | 0.42 | 0.48 | **0.54** | 0.70 (kernel-native) | 0.20 (univocity) | 0.47 |
| JVM | numeric | 128 MiB | 0.31 | 0.32 | **0.30** | 0.70 (kernel-native) | 0.24 (fastcsv) | 0.48 |
