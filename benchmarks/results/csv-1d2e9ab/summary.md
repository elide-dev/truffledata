
### scan (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Best kernel path | Best Java competitor | csv-core-scan | native-indexed-scan |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.46 | 1.11 | **3.01** | 3.88 (kernel-native-small) | 0.19 (jackson) | 0.47 | 4.10 |
| SVM (compat) | escaped | 128 MiB | 1.09 | 2.02 | **5.46** | 5.78 (kernel-native) | 0.38 (fastcsv) | 1.00 | 6.41 |
| SVM (compat) | mixed | 1 MiB | 0.79 | 1.03 | **1.49** | 2.07 (kernel-native-small) | 0.44 (fastcsv) | 0.97 | 2.52 |
| SVM (compat) | mixed | 128 MiB | 0.82 | 1.04 | **1.44** | 1.83 (kernel-native-small) | 0.36 (fastcsv) | 1.07 | 2.28 |
| SVM (compat) | numeric | 1 MiB | 0.73 | 0.74 | **1.10** | 1.42 (kernel-native-small) | 0.43 (fastcsv) | 1.03 | 2.13 |
| SVM (compat) | numeric | 128 MiB | 0.42 | 0.48 | **0.58** | 0.78 (kernel-native) | 0.28 (fastcsv) | 0.51 | 0.83 |
| SVM (compat) | quoted | 1 MiB | 1.57 | 1.69 | **6.23** | 8.86 (kernel-native) | 0.55 (fastcsv) | 0.84 | 8.65 |
| SVM (compat) | quoted | 128 MiB | 1.56 | 1.66 | **4.85** | 5.19 (kernel-native) | 0.53 (fastcsv) | 0.84 | 5.29 |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.88 | 2.06 | **5.56** | 6.84 (kernel-native) | 0.36 (fastcsv) | 0.83 | 8.17 |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.67 | 1.22 | **3.12** | 3.40 (kernel-native-small) | 0.24 (fastcsv) | 0.55 | 3.83 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.80 | 1.08 | **1.60** | 2.09 (kernel-native) | 0.47 (fastcsv) | 1.05 | 2.75 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.84 | 1.02 | **1.55** | 1.76 (kernel-native) | 0.46 (fastcsv) | 1.05 | 2.20 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.76 | 0.83 | **0.75** | 1.60 (kernel-native-small) | 0.59 (fastcsv) | 1.13 | 2.45 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.78 | 0.97 | **1.46** | 1.67 (kernel-native) | 0.52 (fastcsv) | 1.16 | 2.40 |
| SVM (x86-64-v3) | quoted | 1 MiB | 1.51 | 1.67 | **6.95** | 8.89 (kernel-native-small) | 0.73 (fastcsv) | 0.80 | 9.58 |
| SVM (x86-64-v3) | quoted | 128 MiB | 2.72 | 2.25 | **8.56** | 9.24 (kernel-native-small) | 1.22 (fastcsv) | 1.46 | 10.39 |
| JVM | escaped | 1 MiB | 1.06 | 2.26 | **2.01** | 6.88 (kernel-native) | 0.40 (fastcsv) | 0.71 | 7.40 |
| JVM | escaped | 128 MiB | 0.69 | 1.47 | **3.02** | 3.25 (kernel-native-small) | 0.31 (jackson) | 0.47 | 3.19 |
| JVM | mixed | 1 MiB | 0.59 | 0.94 | **0.90** | 1.68 (kernel-native-small) | 0.40 (fastcsv) | 0.60 | 1.53 |
| JVM | mixed | 128 MiB | 0.55 | 0.85 | **1.51** | 1.62 (kernel-native-small) | 0.38 (fastcsv) | 0.61 | 1.41 |
| JVM | numeric | 1 MiB | 0.94 | 1.43 | **1.67** | 2.91 (kernel-native-small) | 1.03 (fastcsv) | 1.10 | 2.31 |
| JVM | numeric | 128 MiB | 0.92 | 1.53 | **1.93** | 3.30 (kernel-native-small) | 0.86 (fastcsv) | 1.13 | 2.44 |
| JVM | quoted | 1 MiB | 4.08 | 2.52 | **3.30** | 13.93 (kernel-native-small) | 1.88 (fastcsv) | 1.37 | 12.00 |
| JVM | quoted | 128 MiB | 2.47 | 1.47 | **6.38** | 6.91 (kernel-native-small) | 1.00 (fastcsv) | 0.92 | 7.25 |

### strings (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Mapped (native) | Best kernel path | Best Java competitor | csv-string-record |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.17 | 0.18 | **0.23** | 0.18 | 0.33 (kernel-native) | 0.20 (jackson) | 0.39 |
| SVM (compat) | escaped | 128 MiB | 0.22 | 0.35 | **0.36** | 0.39 | 0.54 (kernel-native) | 0.34 (jackson) | 0.81 |
| SVM (compat) | mixed | 1 MiB | 0.20 | 0.19 | **0.21** | 0.16 | 0.40 (kernel-native) | 0.26 (fastcsv) | 0.42 |
| SVM (compat) | mixed | 128 MiB | 0.17 | 0.18 | **0.19** | 0.17 | 0.41 (kernel-native) | 0.29 (fastcsv) | 0.49 |
| SVM (compat) | numeric | 1 MiB | 0.16 | 0.15 | **0.15** | 0.13 | 0.33 (kernel-swar) | 0.27 (fastcsv) | 0.49 |
| SVM (compat) | numeric | 128 MiB | 0.10 | 0.09 | **0.10** | 0.06 | 0.23 (kernel-native) | 0.13 (univocity) | 0.39 |
| SVM (compat) | quoted | 1 MiB | 0.47 | 0.40 | **0.56** | 0.40 | 1.04 (kernel-native) | 0.37 (fastcsv) | 0.77 |
| SVM (compat) | quoted | 128 MiB | 0.43 | 0.47 | **0.44** | 0.62 | 0.95 (kernel-native) | 0.37 (fastcsv) | 0.74 |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.32 | 0.40 | **0.41** | 0.33 | 0.65 (kernel-native) | 0.33 (jackson) | 0.61 |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.18 | 0.27 | **0.31** | 0.27 | 0.44 (kernel-native) | 0.22 (jackson) | 0.46 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.21 | 0.22 | **0.23** | 0.17 | 0.44 (kernel-native) | 0.31 (fastcsv) | 0.45 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.16 | 0.18 | **0.21** | 0.16 | 0.44 (kernel-native) | 0.31 (fastcsv) | 0.49 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.17 | 0.17 | **0.18** | 0.14 | 0.42 (kernel-native) | 0.32 (fastcsv) | 0.78 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.16 | 0.17 | **0.18** | 0.14 | 0.43 (kernel-native) | 0.31 (fastcsv) | 0.73 |
| SVM (x86-64-v3) | quoted | 1 MiB | 0.52 | 0.49 | **0.79** | 0.39 | 1.80 (kernel-native) | 0.71 (fastcsv) | 0.77 |
| SVM (x86-64-v3) | quoted | 128 MiB | 0.89 | 0.86 | **1.22** | 0.96 | 2.79 (kernel-native) | 1.01 (fastcsv) | 1.03 |
| JVM | escaped | 1 MiB | 0.30 | 0.16 | **0.24** | 0.17 | 0.40 (kernel-native) | 0.37 (jackson) | 0.53 |
| JVM | escaped | 128 MiB | 0.21 | 0.23 | **0.18** | 0.25 | 0.29 (kernel-native) | 0.32 (jackson) | 0.41 |
| JVM | mixed | 1 MiB | 0.23 | 0.29 | **0.28** | 0.15 | 0.49 (kernel-native) | 0.20 (univocity) | 0.33 |
| JVM | mixed | 128 MiB | 0.23 | 0.26 | **0.28** | 0.18 | 0.46 (kernel-native) | 0.24 (fastcsv) | 0.32 |
| JVM | numeric | 1 MiB | 0.29 | 0.49 | **0.04** | 0.21 | 0.87 (kernel-native) | 0.29 (fastcsv) | 0.77 |
| JVM | numeric | 128 MiB | 0.05 | 0.51 | **0.57** | 0.37 | 0.89 (kernel-native) | 0.49 (fastcsv) | 0.78 |
| JVM | quoted | 1 MiB | 1.50 | 0.85 | **0.05** | 0.42 | 1.82 (kernel-native) | 1.15 (fastcsv) | 0.91 |
| JVM | quoted | 128 MiB | 1.03 | 0.81 | **0.67** | 1.19 | 1.35 (kernel-native) | 0.90 (fastcsv) | 0.79 |

### typed (GB/s)

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Best kernel path | Best Java competitor | csv-typed |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | mixed | 1 MiB | 0.31 | 0.34 | **0.38** | 0.74 (kernel-native) | 0.16 (fastcsv) | 0.35 |
| SVM (compat) | mixed | 128 MiB | 0.29 | 0.25 | **0.35** | 0.57 (kernel-native) | 0.16 (fastcsv) | 0.40 |
| SVM (compat) | numeric | 1 MiB | 0.18 | 0.20 | **0.12** | 0.41 (kernel-native) | 0.09 (fastcsv) | 0.42 |
| SVM (compat) | numeric | 128 MiB | 0.10 | 0.10 | **0.10** | 0.23 (kernel-swar) | 0.04 (univocity) | 0.23 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.35 | 0.37 | **0.32** | 0.76 (kernel-native) | 0.18 (fastcsv) | 0.39 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.30 | 0.34 | **0.41** | 0.74 (kernel-native) | 0.17 (fastcsv) | 0.40 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.20 | 0.23 | **0.20** | 0.47 (kernel-native) | 0.11 (fastcsv) | 0.47 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.14 | 0.17 | **0.24** | 0.47 (kernel-native) | 0.11 (fastcsv) | 0.45 |
| JVM | mixed | 1 MiB | 0.28 | 0.33 | **0.32** | 0.65 (kernel-native) | 0.15 (jackson) | 0.26 |
| JVM | mixed | 128 MiB | 0.27 | 0.24 | **0.33** | 0.57 (kernel-native) | 0.17 (fastcsv) | 0.26 |
| JVM | numeric | 1 MiB | 0.20 | 0.18 | **0.08** | 0.67 (kernel-native) | 0.17 (jackson) | 0.46 |
| JVM | numeric | 128 MiB | 0.17 | 0.32 | **0.31** | 0.69 (kernel-native) | 0.24 (fastcsv) | 0.42 |

### Size sweep (GB/s, one fork)

| Runtime | Workload | Dataset | Engine | 256 B | 1 KiB | 4 KiB | 16 KiB | 64 KiB | 256 KiB | 1024 KiB | 4096 KiB | 16384 KiB |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| JVM | index | mixed | index-native | 2.10 | 4.09 | 7.35 | 7.29 | 5.41 | 8.93 | 6.56 | 5.12 | 4.79 |
| JVM | index | mixed | index-native-small | 1.86 | 4.09 | 7.68 | 7.71 | 7.69 | 9.80 | 9.01 | 1.88 | 5.64 |
| JVM | index | mixed | index-swar | 1.60 | 2.40 | 3.01 | 2.96 | 1.61 | 3.26 | 2.93 | 2.08 | 2.33 |
| JVM | index | numeric | index-native | 1.55 | 4.22 | 6.91 | 8.71 | 9.24 | 6.99 | 8.18 | 7.70 | 7.21 |
| JVM | index | numeric | index-native-small | 1.60 | 4.68 | 7.40 | 10.10 | 10.71 | 9.35 | 10.33 | 9.99 | 9.58 |
| JVM | index | numeric | index-swar | 1.47 | 1.84 | 2.75 | 2.57 | 2.84 | 3.23 | 2.89 | 2.55 | 2.42 |
| JVM | scan | mixed | kernel-native-small | 1.36 | 2.20 | 3.04 | 3.80 | 3.87 | 4.07 | 3.86 | 2.88 | 2.91 |
| JVM | scan | mixed | sweep-native | 0.79 | 1.16 | 2.05 | 1.83 | 1.58 | 2.48 | 2.27 | 2.49 | 2.89 |
| JVM | scan | mixed | sweep-swar | 0.67 | 1.11 | 1.23 | 0.63 | 1.62 | 1.78 | 1.62 | 0.63 | 1.72 |
| JVM | scan | mixed | truffledata-scanner-bytes | 1.20 | 0.96 | 2.19 | 2.47 | 2.12 | 1.67 | 1.15 | 1.17 | 1.18 |
| JVM | scan | numeric | kernel-native-small | 1.38 | 2.52 | 3.13 | 2.38 | 3.27 | 3.56 | 3.24 | 3.16 | 3.22 |
| JVM | scan | numeric | sweep-native | 0.01 | 1.25 | 1.68 | 0.05 | 2.24 | 1.89 | 2.08 | 2.18 | 3.10 |
| JVM | scan | numeric | sweep-swar | 0.04 | 1.09 | 1.10 | 0.03 | 1.25 | 1.67 | 1.80 | 1.64 | 1.66 |
| JVM | scan | numeric | truffledata-scanner-bytes | 0.88 | 1.26 | 1.87 | 1.91 | 1.47 | 1.41 | 1.25 | 1.14 | 1.17 |
| JVM | strings | mixed | sweep-batch-native | 0.00 | 0.28 | 0.27 | 0.79 | 0.88 | 0.52 | 0.54 | 0.52 | 0.31 |
| JVM | strings | mixed | sweep-batch-swar | 0.15 | 0.23 | 0.23 | 0.59 | 0.60 | 0.53 | 0.55 | 0.39 | 0.37 |
| JVM | strings | mixed | truffledata-batch-bytes | 0.16 | 0.33 | 0.50 | 0.68 | 0.77 | 0.46 | 0.46 | 0.31 | 0.31 |
| JVM | strings | numeric | sweep-batch-native | 0.14 | 0.23 | 0.53 | 0.67 | 0.60 | 0.53 | 0.62 | 0.61 | 0.58 |
| JVM | strings | numeric | sweep-batch-swar | 0.14 | 0.27 | 0.41 | 0.49 | 0.53 | 0.58 | 0.43 | 0.49 | 0.57 |
| JVM | strings | numeric | truffledata-batch-bytes | 0.23 | 0.34 | 0.46 | 0.38 | 0.52 | 0.41 | 0.57 | 0.51 | 0.03 |
| SVM (compat) | index | mixed | index-native | 1.13 | 2.15 | 3.95 | 0.99 | 4.69 | 4.47 | 4.33 | 3.19 | 3.15 |
| SVM (compat) | index | mixed | index-native-small | 1.12 | 1.60 | 3.74 | 4.46 | 4.62 | 4.76 | 4.79 | 3.40 | 3.11 |
| SVM (compat) | index | mixed | index-swar | 0.68 | 0.91 | 1.18 | 1.31 | 1.21 | 1.32 | 1.22 | 1.13 | 1.15 |
| SVM (compat) | index | numeric | index-native | 0.93 | 2.19 | 3.50 | 3.89 | 4.31 | 4.00 | 4.24 | 3.96 | 4.25 |
| SVM (compat) | index | numeric | index-native-small | 0.93 | 2.19 | 3.50 | 3.27 | 4.32 | 4.11 | 4.13 | 4.28 | 4.09 |
| SVM (compat) | index | numeric | index-swar | 0.64 | 1.02 | 1.27 | 1.33 | 1.30 | 1.35 | 1.10 | 1.18 | 1.21 |
| SVM (compat) | scan | mixed | kernel-native-small | 0.60 | 0.90 | 1.12 | 1.19 | 1.30 | 1.18 | 1.17 | 1.07 | 1.06 |
| SVM (compat) | scan | mixed | sweep-native | 0.28 | 0.49 | 0.56 | 0.88 | 0.88 | 0.91 | 0.92 | 0.94 | 0.93 |
| SVM (compat) | scan | mixed | sweep-swar | 0.31 | 0.48 | 0.53 | 0.58 | 0.61 | 0.61 | 0.64 | 0.63 | 0.52 |
| SVM (compat) | scan | mixed | truffledata-scanner-bytes | 0.39 | 0.53 | 0.64 | 0.62 | 0.53 | 0.49 | 0.48 | 0.51 | 0.52 |
| SVM (compat) | scan | numeric | kernel-native-small | 0.50 | 0.71 | 0.83 | 0.87 | 0.90 | 0.87 | 0.89 | 0.79 | 0.91 |
| SVM (compat) | scan | numeric | sweep-native | 0.26 | 0.47 | 0.54 | 0.65 | 0.63 | 0.57 | 0.67 | 0.56 | 0.80 |
| SVM (compat) | scan | numeric | sweep-swar | 0.29 | 0.32 | 0.44 | 0.46 | 0.49 | 0.48 | 0.55 | 0.54 | 0.54 |
| SVM (compat) | scan | numeric | truffledata-scanner-bytes | 0.33 | 0.45 | 0.49 | 0.50 | 0.49 | 0.45 | 0.45 | 0.45 | 0.42 |
| SVM (compat) | strings | mixed | sweep-batch-native | 0.05 | 0.10 | 0.16 | 0.18 | 0.19 | 0.17 | 0.16 | 0.03 | 0.13 |
| SVM (compat) | strings | mixed | sweep-batch-swar | 0.05 | 0.10 | 0.13 | 0.15 | 0.14 | 0.14 | 0.15 | 0.12 | 0.09 |
| SVM (compat) | strings | mixed | truffledata-batch-bytes | 0.06 | 0.10 | 0.15 | 0.17 | 0.15 | 0.15 | 0.14 | 0.12 | 0.11 |
| SVM (compat) | strings | numeric | sweep-batch-native | 0.06 | 0.10 | 0.13 | 0.14 | 0.13 | 0.13 | 0.12 | 0.12 | 0.11 |
| SVM (compat) | strings | numeric | sweep-batch-swar | 0.06 | 0.10 | 0.11 | 0.11 | 0.12 | 0.13 | 0.09 | 0.12 | 0.11 |
| SVM (compat) | strings | numeric | truffledata-batch-bytes | 0.07 | 0.11 | 0.09 | 0.13 | 0.13 | 0.11 | 0.11 | 0.11 | 0.10 |
| SVM (x86-64-v3) | index | mixed | index-native | 1.61 | 3.37 | 6.18 | 7.77 | 7.90 | 8.05 | 7.68 | 5.06 | 4.85 |
| SVM (x86-64-v3) | index | mixed | index-native-small | 1.74 | 3.48 | 6.20 | 7.57 | 7.94 | 7.93 | 7.84 | 4.93 | 4.57 |
| SVM (x86-64-v3) | index | mixed | index-swar | 1.20 | 1.56 | 2.13 | 2.26 | 1.90 | 2.23 | 2.02 | 1.80 | 1.78 |
| SVM (x86-64-v3) | index | numeric | index-native | 1.43 | 3.59 | 6.54 | 7.99 | 8.06 | 8.37 | 7.77 | 8.24 | 8.00 |
| SVM (x86-64-v3) | index | numeric | index-native-small | 1.31 | 3.75 | 6.59 | 8.30 | 8.04 | 8.23 | 8.27 | 8.37 | 7.62 |
| SVM (x86-64-v3) | index | numeric | index-swar | 1.05 | 1.83 | 2.13 | 2.23 | 2.20 | 2.16 | 2.15 | 2.20 | 2.25 |
| SVM (x86-64-v3) | scan | mixed | kernel-native-small | 1.01 | 1.48 | 2.00 | 2.08 | 2.06 | 2.06 | 2.01 | 1.76 | 1.70 |
| SVM (x86-64-v3) | scan | mixed | sweep-native | 0.50 | 0.79 | 1.15 | 1.36 | 1.56 | 1.56 | 1.60 | 1.46 | 1.62 |
| SVM (x86-64-v3) | scan | mixed | sweep-swar | 0.51 | 0.60 | 0.92 | 0.97 | 0.98 | 0.99 | 0.97 | 1.03 | 1.06 |
| SVM (x86-64-v3) | scan | mixed | truffledata-scanner-bytes | 0.66 | 1.00 | 1.07 | 1.05 | 0.92 | 0.52 | 0.79 | 0.82 | 0.83 |
| SVM (x86-64-v3) | scan | numeric | kernel-native-small | 0.83 | 1.26 | 1.42 | 1.44 | 1.62 | 1.56 | 1.57 | 1.68 | 1.67 |
| SVM (x86-64-v3) | scan | numeric | sweep-native | 0.40 | 0.62 | 0.98 | 1.04 | 1.00 | 1.18 | 1.15 | 1.19 | 1.28 |
| SVM (x86-64-v3) | scan | numeric | sweep-swar | 0.41 | 0.60 | 0.77 | 0.77 | 0.85 | 0.91 | 0.92 | 0.94 | 0.97 |
| SVM (x86-64-v3) | scan | numeric | truffledata-scanner-bytes | 0.58 | 0.78 | 0.73 | 0.78 | 0.72 | 0.76 | 0.75 | 0.77 | 0.78 |
| SVM (x86-64-v3) | strings | mixed | sweep-batch-native | 0.08 | 0.12 | 0.22 | 0.24 | 0.25 | 0.24 | 0.20 | 0.19 | 0.19 |
| SVM (x86-64-v3) | strings | mixed | sweep-batch-swar | 0.08 | 0.12 | 0.18 | 0.20 | 0.21 | 0.16 | 0.21 | 0.18 | 0.17 |
| SVM (x86-64-v3) | strings | mixed | truffledata-batch-bytes | 0.08 | 0.16 | 0.20 | 0.23 | 0.18 | 0.20 | 0.21 | 0.17 | 0.17 |
| SVM (x86-64-v3) | strings | numeric | sweep-batch-native | 0.09 | 0.14 | 0.18 | 0.19 | 0.19 | 0.16 | 0.16 | 0.17 | 0.12 |
| SVM (x86-64-v3) | strings | numeric | sweep-batch-swar | 0.09 | 0.12 | 0.16 | 0.17 | 0.17 | 0.18 | 0.19 | 0.18 | 0.14 |
| SVM (x86-64-v3) | strings | numeric | truffledata-batch-bytes | 0.10 | 0.15 | 0.17 | 0.18 | 0.16 | 0.18 | 0.17 | 0.16 | 0.16 |
