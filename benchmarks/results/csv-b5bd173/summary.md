
### scan (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Best kernel path | Best Java competitor | csv-core-scan | native-indexed-scan |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.47 | 1.35 | **3.65** | 3.79 (kernel-native) | 0.20 (jackson) | 0.48 | 4.22 |
| SVM (compat) | escaped | 128 MiB | 1.05 | 2.29 | **5.41** | 5.35 (kernel-native-small) | 0.39 (fastcsv) | 1.04 | 6.40 |
| SVM (compat) | mixed | 1 MiB | 0.82 | 1.12 | **1.95** | 1.70 (kernel-native-small) | 0.48 (fastcsv) | 1.03 | 2.75 |
| SVM (compat) | mixed | 128 MiB | 0.83 | 1.06 | **1.74** | 1.81 (kernel-native) | 0.46 (fastcsv) | 1.01 | 2.19 |
| SVM (compat) | numeric | 1 MiB | 0.77 | 0.97 | **1.47** | 1.68 (kernel-native) | 0.49 (fastcsv) | 1.08 | 2.46 |
| SVM (compat) | numeric | 128 MiB | 0.40 | 0.55 | **0.67** | 0.78 (kernel-native-small) | 0.28 (fastcsv) | 0.48 | 0.56 |
| SVM (compat) | quoted | 1 MiB | 1.58 | 1.68 | **8.13** | 9.00 (kernel-native-small) | 0.54 (fastcsv) | 0.85 | 9.91 |
| SVM (compat) | quoted | 128 MiB | 1.54 | 1.59 | **4.52** | 5.15 (kernel-native) | 0.47 (fastcsv) | 0.85 | 5.32 |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.88 | 1.88 | **6.14** | 6.81 (kernel-native-small) | 0.36 (fastcsv) | 0.80 | 7.64 |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.65 | 1.24 | **3.15** | 3.33 (kernel-native) | 0.25 (fastcsv) | 0.57 | 3.82 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.83 | 1.15 | **1.96** | 2.12 (kernel-native-small) | 0.41 (fastcsv) | 0.96 | 2.68 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.82 | 1.07 | **1.75** | 1.75 (kernel-native) | 0.40 (fastcsv) | 1.03 | 2.26 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.75 | 0.95 | **1.49** | 1.65 (kernel-native-small) | 0.58 (fastcsv) | 0.95 | 2.38 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.76 | 1.02 | **1.43** | 1.72 (kernel-native) | 0.58 (fastcsv) | 1.12 | 2.07 |
| SVM (x86-64-v3) | quoted | 1 MiB | 1.57 | 1.75 | **8.23** | 9.01 (kernel-native) | 0.93 (fastcsv) | 0.84 | 9.94 |
| SVM (x86-64-v3) | quoted | 128 MiB | 2.65 | 2.20 | **8.67** | 9.04 (kernel-native) | 1.19 (fastcsv) | 1.40 | 10.32 |
| JVM | escaped | 1 MiB | 1.09 | 2.53 | **7.65** | 7.17 (kernel-vector) | 0.42 (fastcsv) | 0.83 | 8.19 |
| JVM | escaped | 128 MiB | 0.66 | 1.58 | **3.15** | 3.29 (kernel-vector) | 0.32 (jackson) | 0.48 | 3.10 |
| JVM | mixed | 1 MiB | 0.59 | 1.02 | **1.66** | 1.69 (kernel-native-small) | 0.39 (fastcsv) | 0.57 | 1.48 |
| JVM | mixed | 128 MiB | 0.59 | 0.99 | **1.49** | 1.65 (kernel-native-small) | 0.34 (fastcsv) | 0.61 | 1.41 |
| JVM | numeric | 1 MiB | 1.02 | 1.52 | **2.88** | 3.00 (kernel-native-small) | 1.10 (fastcsv) | 1.12 | 2.50 |
| JVM | numeric | 128 MiB | 1.01 | 1.53 | **2.49** | 3.32 (kernel-native-small) | 0.87 (fastcsv) | 1.15 | 2.48 |
| JVM | quoted | 1 MiB | 4.38 | 2.74 | **13.85** | 13.19 (kernel-native-small) | 1.83 (fastcsv) | 1.49 | 14.46 |
| JVM | quoted | 128 MiB | 2.59 | 1.62 | **6.06** | 7.06 (kernel-native-small) | 1.27 (fastcsv) | 0.93 | 7.30 |

### strings (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Mapped (native) | Best kernel path | Best Java competitor | csv-string-record |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.18 | 0.22 | **0.24** | 0.17 | 0.34 (kernel-native) | 0.17 (jackson) | 0.43 |
| SVM (compat) | escaped | 128 MiB | 0.22 | 0.40 | **0.44** | 0.40 | 0.63 (kernel-native) | 0.38 (jackson) | 0.80 |
| SVM (compat) | mixed | 1 MiB | 0.20 | 0.22 | **0.24** | 0.18 | 0.41 (kernel-native) | 0.27 (fastcsv) | 0.44 |
| SVM (compat) | mixed | 128 MiB | 0.19 | 0.19 | **0.21** | 0.18 | 0.34 (kernel-swar) | 0.30 (fastcsv) | 0.46 |
| SVM (compat) | numeric | 1 MiB | 0.18 | 0.18 | **0.20** | 0.15 | 0.43 (kernel-native) | 0.26 (fastcsv) | 0.75 |
| SVM (compat) | numeric | 128 MiB | 0.09 | 0.10 | **0.09** | 0.07 | 0.21 (kernel-native) | 0.13 (fastcsv) | 0.25 |
| SVM (compat) | quoted | 1 MiB | 0.50 | 0.49 | **0.68** | 0.42 | 0.91 (kernel-native) | 0.38 (fastcsv) | 0.78 |
| SVM (compat) | quoted | 128 MiB | 0.43 | 0.43 | **0.56** | 0.62 | 0.82 (kernel-native) | 0.36 (fastcsv) | 0.74 |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.32 | 0.41 | **0.48** | 0.37 | 0.67 (kernel-native) | 0.34 (jackson) | 0.56 |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.15 | 0.21 | **0.33** | 0.29 | 0.44 (kernel-native) | 0.22 (jackson) | 0.48 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.22 | 0.20 | **0.26** | 0.18 | 0.45 (kernel-native) | 0.31 (fastcsv) | 0.46 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.19 | 0.19 | **0.21** | 0.18 | 0.43 (kernel-native) | 0.30 (fastcsv) | 0.47 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.17 | 0.19 | **0.20** | 0.14 | 0.42 (kernel-native) | 0.32 (fastcsv) | 0.72 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.16 | 0.18 | **0.17** | 0.14 | 0.41 (kernel-native) | 0.31 (fastcsv) | 0.68 |
| SVM (x86-64-v3) | quoted | 1 MiB | 0.61 | 0.61 | **0.93** | 0.53 | 1.85 (kernel-native) | 0.73 (fastcsv) | 0.79 |
| SVM (x86-64-v3) | quoted | 128 MiB | 0.77 | 0.85 | **0.72** | 0.91 | 2.73 (kernel-native) | 0.97 (fastcsv) | 1.01 |
| JVM | escaped | 1 MiB | 0.29 | 0.34 | **0.28** | 0.34 | 0.44 (kernel-native) | 0.41 (jackson) | 0.61 |
| JVM | escaped | 128 MiB | 0.20 | 0.25 | **0.27** | 0.27 | 0.31 (kernel-native) | 0.33 (jackson) | 0.42 |
| JVM | mixed | 1 MiB | 0.24 | 0.28 | **0.36** | 0.20 | 0.49 (kernel-native) | 0.22 (fastcsv) | 0.33 |
| JVM | mixed | 128 MiB | 0.22 | 0.23 | **0.25** | 0.20 | 0.39 (kernel-swar) | 0.24 (fastcsv) | 0.32 |
| JVM | numeric | 1 MiB | 0.33 | 0.49 | **0.62** | 0.37 | 0.89 (kernel-native) | 0.20 (fastcsv) | 0.77 |
| JVM | numeric | 128 MiB | 0.43 | 0.12 | **0.57** | 0.37 | 0.88 (kernel-native) | 0.46 (fastcsv) | 0.81 |
| JVM | quoted | 1 MiB | 0.88 | 0.84 | **0.71** | 0.04 | 1.56 (kernel-swar) | 1.26 (fastcsv) | 1.04 |
| JVM | quoted | 128 MiB | 0.67 | 0.53 | **0.86** | 0.76 | 1.56 (kernel-native) | 0.90 (fastcsv) | 0.77 |

### typed (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Best kernel path | Best Java competitor | csv-typed |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | mixed | 1 MiB | 0.33 | 0.35 | **0.44** | 0.81 (kernel-native) | 0.17 (fastcsv) | 0.37 |
| SVM (compat) | mixed | 128 MiB | 0.29 | 0.34 | **0.41** | 0.74 (kernel-native) | 0.17 (fastcsv) | 0.37 |
| SVM (compat) | numeric | 1 MiB | 0.21 | 0.23 | **0.24** | 0.44 (kernel-native) | 0.11 (fastcsv) | 0.46 |
| SVM (compat) | numeric | 128 MiB | 0.07 | 0.09 | **0.09** | 0.24 (kernel-native) | 0.05 (univocity) | 0.15 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.34 | 0.37 | **0.46** | 0.85 (kernel-native) | 0.15 (univocity) | 0.37 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.32 | 0.35 | **0.42** | 0.78 (kernel-native) | 0.16 (fastcsv) | 0.40 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.22 | 0.23 | **0.26** | 0.44 (kernel-native) | 0.11 (fastcsv) | 0.47 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.15 | 0.20 | **0.22** | 0.48 (kernel-native) | 0.11 (fastcsv) | 0.44 |
| JVM | mixed | 1 MiB | 0.27 | 0.34 | **0.41** | 0.65 (kernel-native) | 0.17 (fastcsv) | 0.26 |
| JVM | mixed | 128 MiB | 0.24 | 0.32 | **0.32** | 0.59 (kernel-native) | 0.18 (fastcsv) | 0.25 |
| JVM | numeric | 1 MiB | 0.22 | 0.21 | **0.26** | 0.69 (kernel-native) | 0.21 (univocity) | 0.40 |
| JVM | numeric | 128 MiB | 0.11 | 0.28 | **0.30** | 0.70 (kernel-native) | 0.24 (fastcsv) | 0.48 |
