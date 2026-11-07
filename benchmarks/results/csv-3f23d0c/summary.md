
### scan (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Best kernel path | Best Java competitor | csv-core-scan | native-indexed-scan |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.90 | 2.09 | **6.19** | 6.90 (kernel-native) | 0.33 (fastcsv) | 0.81 | 7.92 |
| SVM (compat) | escaped | 128 MiB | 1.10 | 2.32 | **5.50** | 5.88 (kernel-native-small) | 0.40 (fastcsv) | 1.04 | 6.67 |
| SVM (compat) | mixed | 1 MiB | 0.83 | 1.12 | **2.03** | 2.13 (kernel-native) | 0.48 (fastcsv) | 1.05 | 2.79 |
| SVM (compat) | mixed | 128 MiB | 0.82 | 1.05 | **1.76** | 1.82 (kernel-native-small) | 0.38 (fastcsv) | 1.00 | 2.28 |
| SVM (compat) | numeric | 1 MiB | 0.78 | 0.97 | **1.50** | 1.66 (kernel-native) | 0.46 (fastcsv) | 1.12 | 2.64 |
| SVM (compat) | numeric | 128 MiB | 0.43 | 0.59 | **0.68** | 0.79 (kernel-native) | 0.30 (fastcsv) | 0.53 | 0.89 |
| SVM (compat) | quoted | 1 MiB | 2.71 | 2.35 | **11.63** | 12.72 (kernel-native) | 0.91 (fastcsv) | 1.39 | 13.99 |
| SVM (compat) | quoted | 128 MiB | 2.77 | 2.27 | **8.91** | 9.46 (kernel-native-small) | 0.91 (fastcsv) | 1.49 | 10.42 |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.90 | 2.12 | **6.31** | 6.87 (kernel-native-small) | 0.37 (fastcsv) | 0.82 | 8.29 |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.68 | 1.24 | **3.20** | 3.42 (kernel-native-small) | 0.25 (fastcsv) | 0.57 | 3.87 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.84 | 1.16 | **2.03** | 2.12 (kernel-native-small) | 0.46 (fastcsv) | 1.03 | 2.79 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.84 | 1.09 | **1.78** | 1.82 (kernel-native-small) | 0.47 (fastcsv) | 1.01 | 2.10 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.78 | 1.01 | **1.51** | 1.67 (kernel-native-small) | 0.60 (fastcsv) | 1.13 | 2.63 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.80 | 1.04 | **1.55** | 1.72 (kernel-native) | 0.61 (fastcsv) | 1.12 | 2.70 |
| SVM (x86-64-v3) | quoted | 1 MiB | 2.70 | 2.34 | **11.62** | 12.72 (kernel-native) | 1.29 (fastcsv) | 1.50 | 14.51 |
| SVM (x86-64-v3) | quoted | 128 MiB | 2.78 | 2.30 | **8.89** | 9.51 (kernel-native-small) | 1.24 (fastcsv) | 1.49 | 10.59 |
| JVM | escaped | 1 MiB | 1.06 | 2.57 | **8.14** | 8.13 (kernel-native-small) | 0.42 (jackson) | 0.82 | 8.28 |
| JVM | escaped | 128 MiB | 1.08 | 2.26 | **5.14** | 5.44 (kernel-native-small) | 0.50 (jackson) | 0.84 | 5.38 |
| JVM | mixed | 1 MiB | 0.94 | 1.51 | **3.11** | 3.19 (kernel-native-small) | 0.58 (fastcsv) | 1.04 | 2.80 |
| JVM | mixed | 128 MiB | 0.97 | 1.44 | **2.51** | 2.68 (kernel-native-small) | 0.61 (fastcsv) | 0.19 | 2.33 |
| JVM | numeric | 1 MiB | 0.98 | 1.49 | **2.71** | 2.97 (kernel-native-small) | 1.12 (fastcsv) | 1.05 | 2.48 |
| JVM | numeric | 128 MiB | 1.03 | 1.52 | **2.53** | 3.16 (kernel-native-small) | 0.87 (fastcsv) | 1.16 | 2.66 |
| JVM | quoted | 1 MiB | 5.65 | 3.36 | **17.23** | 18.36 (kernel-native-small) | 2.34 (fastcsv) | 1.80 | 17.06 |
| JVM | quoted | 128 MiB | 2.81 | 1.61 | **7.15** | 7.19 (kernel-native-small) | 1.14 (fastcsv) | 0.92 | 7.00 |

### strings (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Mapped (native) | Best kernel path | Best Java competitor | csv-string-record |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.24 | 0.29 | **0.31** | 0.25 | 0.36 (kernel-native) | 0.30 (jackson) | 0.61 |
| SVM (compat) | escaped | 128 MiB | 0.29 | 0.31 | **0.36** | 0.34 | 0.45 (kernel-native) | 0.38 (jackson) | 0.81 |
| SVM (compat) | mixed | 1 MiB | 0.23 | 0.24 | **0.28** | 0.18 | 0.43 (kernel-native) | 0.30 (fastcsv) | 0.47 |
| SVM (compat) | mixed | 128 MiB | 0.20 | 0.20 | **0.24** | 0.15 | 0.42 (kernel-native) | 0.29 (fastcsv) | 0.48 |
| SVM (compat) | numeric | 1 MiB | 0.20 | 0.21 | **0.23** | 0.14 | 0.42 (kernel-native) | 0.24 (fastcsv) | 0.79 |
| SVM (compat) | numeric | 128 MiB | 0.11 | 0.12 | **0.13** | 0.07 | 0.23 (kernel-native) | 0.14 (fastcsv) | 0.40 |
| SVM (compat) | quoted | 1 MiB | 0.79 | 0.74 | **1.05** | 0.72 | 1.51 (kernel-native) | 0.65 (fastcsv) | 1.01 |
| SVM (compat) | quoted | 128 MiB | 0.74 | 0.64 | **0.21** | 0.80 | 1.45 (kernel-native) | 0.64 (fastcsv) | 1.04 |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.26 | 0.31 | **0.35** | 0.28 | 0.41 (kernel-native) | 0.34 (jackson) | 0.58 |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.16 | 0.17 | **0.24** | 0.20 | 0.29 (kernel-native) | 0.23 (fastcsv) | 0.48 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.23 | 0.25 | **0.29** | 0.16 | 0.44 (kernel-native) | 0.33 (fastcsv) | 0.44 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.19 | 0.21 | **0.24** | 0.14 | 0.44 (kernel-native) | 0.32 (fastcsv) | 0.49 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.20 | 0.21 | **0.23** | 0.13 | 0.42 (kernel-native) | 0.31 (fastcsv) | 0.78 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.17 | 0.19 | **0.19** | 0.12 | 0.44 (kernel-native) | 0.33 (fastcsv) | 0.79 |
| SVM (x86-64-v3) | quoted | 1 MiB | 1.06 | 0.98 | **1.63** | 0.92 | 3.13 (kernel-native) | 1.05 (fastcsv) | 1.04 |
| SVM (x86-64-v3) | quoted | 128 MiB | 0.99 | 0.90 | **1.38** | 1.07 | 2.92 (kernel-native) | 1.03 (fastcsv) | 1.02 |
| JVM | escaped | 1 MiB | 0.42 | 0.54 | **0.65** | 0.54 | 0.69 (kernel-native) | 0.41 (jackson) | 0.61 |
| JVM | escaped | 128 MiB | 0.44 | 0.55 | **0.63** | 0.57 | 0.66 (kernel-native) | 0.51 (jackson) | 0.66 |
| JVM | mixed | 1 MiB | 0.43 | 0.50 | **0.69** | 0.40 | 0.77 (kernel-native) | 0.39 (fastcsv) | 0.47 |
| JVM | mixed | 128 MiB | 0.42 | 0.48 | **0.61** | 0.37 | 0.74 (kernel-native) | 0.41 (fastcsv) | 0.49 |
| JVM | numeric | 1 MiB | 0.55 | 0.59 | **0.74** | 0.41 | 0.89 (kernel-native) | 0.40 (univocity) | 0.73 |
| JVM | numeric | 128 MiB | 0.52 | 0.55 | **0.67** | 0.39 | 0.88 (kernel-native) | 0.49 (fastcsv) | 0.81 |
| JVM | quoted | 1 MiB | 2.88 | 2.06 | **5.42** | 3.13 | 6.62 (kernel-native) | 1.69 (fastcsv) | 1.19 |
| JVM | quoted | 128 MiB | 1.36 | 1.05 | **2.30** | 1.97 | 2.48 (kernel-native) | 0.94 (fastcsv) | 0.76 |

### typed (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Best kernel path | Best Java competitor | csv-typed |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | mixed | 1 MiB | 0.34 | 0.39 | **0.51** | 0.80 (kernel-native) | 0.17 (fastcsv) | 0.38 |
| SVM (compat) | mixed | 128 MiB | 0.32 | 0.36 | **0.45** | 0.75 (kernel-native) | 0.17 (fastcsv) | 0.38 |
| SVM (compat) | numeric | 1 MiB | 0.23 | 0.28 | **0.31** | 0.45 (kernel-native) | 0.11 (fastcsv) | 0.47 |
| SVM (compat) | numeric | 128 MiB | 0.11 | 0.14 | **0.14** | 0.25 (kernel-native) | 0.05 (fastcsv) | 0.23 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.36 | 0.43 | **0.55** | 0.82 (kernel-native) | 0.18 (fastcsv) | 0.34 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.32 | 0.39 | **0.49** | 0.78 (kernel-native) | 0.18 (fastcsv) | 0.39 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.24 | 0.30 | **0.34** | 0.47 (kernel-native) | 0.12 (fastcsv) | 0.47 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.23 | 0.29 | **0.31** | 0.48 (kernel-native) | 0.11 (fastcsv) | 0.48 |
| JVM | mixed | 1 MiB | 0.50 | 0.59 | **0.80** | 1.13 (kernel-native) | 0.28 (fastcsv) | 0.39 |
| JVM | mixed | 128 MiB | 0.48 | 0.59 | **0.80** | 1.01 (kernel-native) | 0.28 (fastcsv) | 0.39 |
| JVM | numeric | 1 MiB | 0.44 | 0.51 | **0.60** | 0.70 (kernel-native) | 0.23 (fastcsv) | 0.45 |
| JVM | numeric | 128 MiB | 0.44 | 0.49 | **0.57** | 0.69 (kernel-native) | 0.25 (fastcsv) | 0.48 |
