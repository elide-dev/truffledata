
### scan: `truffledata-scanner-native`

| Runtime | Dataset | Size | GB/s before | GB/s after | × kernel before | × kernel after | × best Java before | × best Java after | × csv-core-scan before | × csv-core-scan after | × native-indexed-scan before | × native-indexed-scan after |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 5.80 | **6.19** | 0.88 | **0.90** | 19.02 (jackson) | **18.88** (fastcsv) | 7.11 | **7.68** | 0.71 | **0.78** |
| SVM (compat) | escaped | 128 MiB | 5.01 | **5.50** | 0.94 | **0.95** | 13.15 (jackson) | **13.84** (fastcsv) | 4.88 | **5.30** | 0.79 | **0.83** |
| SVM (compat) | mixed | 1 MiB | 1.47 | **2.03** | 0.79 | **0.95** | 4.21 (fastcsv) | **4.23** (fastcsv) | 1.41 | **1.93** | 0.52 | **0.73** |
| SVM (compat) | mixed | 128 MiB | 1.36 | **1.76** | 0.75 | **0.97** | 3.11 (fastcsv) | **4.58** (fastcsv) | 1.27 | **1.77** | 0.60 | **0.77** |
| SVM (compat) | numeric | 1 MiB | 1.13 | **1.50** | 0.69 | **0.90** | 2.30 (fastcsv) | **3.25** (fastcsv) | 0.99 | **1.33** | 0.45 | **0.57** |
| SVM (compat) | numeric | 128 MiB | 0.51 | **0.68** | 0.69 | **0.87** | 1.77 (fastcsv) | **2.26** (fastcsv) | 1.07 | **1.30** | 0.78 | **0.77** |
| SVM (compat) | quoted | 1 MiB | 10.50 | **11.63** | 0.90 | **0.91** | 11.76 (fastcsv) | **12.79** (fastcsv) | 7.05 | **8.34** | 0.72 | **0.83** |
| SVM (compat) | quoted | 128 MiB | 8.90 | **8.91** | 0.85 | **0.96** | 8.15 (fastcsv) | **9.84** (fastcsv) | 4.83 | **5.99** | 0.70 | **0.85** |
| SVM (x86-64-v3) | escaped | 1 MiB | 5.63 | **6.31** | 0.85 | **0.92** | 16.45 (fastcsv) | **16.89** (fastcsv) | 6.86 | **7.68** | 0.70 | **0.76** |
| SVM (x86-64-v3) | escaped | 128 MiB | 2.91 | **3.20** | 0.88 | **0.94** | 11.46 (fastcsv) | **12.76** (fastcsv) | 5.51 | **5.66** | 0.82 | **0.83** |
| SVM (x86-64-v3) | mixed | 1 MiB | 1.44 | **2.03** | 0.74 | **0.97** | 2.99 (fastcsv) | **4.44** (fastcsv) | 1.36 | **1.97** | 0.51 | **0.73** |
| SVM (x86-64-v3) | mixed | 128 MiB | 1.29 | **1.78** | 0.71 | **0.98** | 2.83 (fastcsv) | **3.76** (fastcsv) | 1.23 | **1.75** | 0.57 | **0.84** |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.92 | **1.51** | 0.62 | **0.91** | 1.73 (fastcsv) | **2.52** (fastcsv) | 0.85 | **1.34** | 0.41 | **0.57** |
| SVM (x86-64-v3) | numeric | 128 MiB | 1.11 | **1.55** | 0.65 | **0.90** | 1.92 (fastcsv) | **2.57** (fastcsv) | 0.95 | **1.39** | 0.43 | **0.58** |
| SVM (x86-64-v3) | quoted | 1 MiB | 10.04 | **11.62** | 0.80 | **0.91** | 8.25 (fastcsv) | **9.03** (fastcsv) | 6.71 | **7.74** | 0.69 | **0.80** |
| SVM (x86-64-v3) | quoted | 128 MiB | 7.93 | **8.89** | 0.86 | **0.94** | 6.51 (fastcsv) | **7.15** (fastcsv) | 5.40 | **5.98** | 0.77 | **0.84** |
| JVM | escaped | 1 MiB | 8.04 | **8.14** | 1.16 | **1.15** | 19.42 (fastcsv) | **19.36** (jackson) | 9.89 | **9.88** | 0.99 | **0.98** |
| JVM | escaped | 128 MiB | 3.28 | **5.14** | 1.28 | **1.15** | 10.09 (jackson) | **10.23** (jackson) | 7.16 | **6.14** | 1.10 | **0.96** |
| JVM | mixed | 1 MiB | 2.91 | **3.11** | 1.02 | **1.08** | 5.39 (fastcsv) | **5.33** (fastcsv) | 2.87 | **2.99** | 1.09 | **1.11** |
| JVM | mixed | 128 MiB | 1.55 | **2.51** | 1.08 | **1.07** | 3.99 (fastcsv) | **4.09** (fastcsv) | 2.53 | **12.91** | 1.09 | **1.08** |
| JVM | numeric | 1 MiB | 2.44 | **2.71** | 0.84 | **0.96** | 2.24 (fastcsv) | **2.43** (fastcsv) | 2.15 | **2.58** | 0.97 | **1.10** |
| JVM | numeric | 128 MiB | 2.30 | **2.53** | 0.82 | **0.95** | 2.51 (fastcsv) | **2.91** (fastcsv) | 2.03 | **2.18** | 0.90 | **0.95** |
| JVM | quoted | 1 MiB | 14.15 | **17.23** | 1.32 | **1.24** | 8.77 (fastcsv) | **7.37** (fastcsv) | 9.69 | **9.60** | 0.99 | **1.01** |
| JVM | quoted | 128 MiB | 6.92 | **7.15** | 1.32 | **1.34** | 5.47 (fastcsv) | **6.26** (fastcsv) | 7.66 | **7.74** | 0.96 | **1.02** |

### strings: `truffledata-batch-native`

| Runtime | Dataset | Size | GB/s before | GB/s after | × kernel before | × kernel after | × best Java before | × best Java after | × csv-string-record before | × csv-string-record after |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.43 | **0.31** | 0.81 | **0.88** | 1.40 (jackson) | **1.05** (jackson) | 0.70 | **0.52** |
| SVM (compat) | escaped | 128 MiB | 0.41 | **0.36** | 0.64 | **0.82** | 1.15 (jackson) | **0.96** (jackson) | 0.50 | **0.45** |
| SVM (compat) | mixed | 1 MiB | 0.26 | **0.28** | 0.71 | **0.65** | 1.06 (fastcsv) | **0.95** (fastcsv) | 0.62 | **0.61** |
| SVM (compat) | mixed | 128 MiB | 0.23 | **0.24** | 0.53 | **0.56** | 0.80 (fastcsv) | **0.81** (fastcsv) | 0.46 | **0.50** |
| SVM (compat) | numeric | 1 MiB | 0.21 | **0.23** | 0.59 | **0.56** | 0.76 (fastcsv) | **0.96** (fastcsv) | 0.27 | **0.29** |
| SVM (compat) | numeric | 128 MiB | 0.11 | **0.13** | 0.54 | **0.55** | 0.78 (fastcsv) | **0.89** (fastcsv) | 0.30 | **0.32** |
| SVM (compat) | quoted | 1 MiB | 0.85 | **1.05** | 0.58 | **0.70** | 1.36 (fastcsv) | **1.61** (fastcsv) | 0.82 | **1.04** |
| SVM (compat) | quoted | 128 MiB | 0.58 | **0.21** | 0.33 | **0.15** | 0.74 (fastcsv) | **0.33** (fastcsv) | 0.46 | **0.21** |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.50 | **0.35** | 0.77 | **0.84** | 1.57 (fastcsv) | **1.03** (jackson) | 0.85 | **0.60** |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.34 | **0.24** | 0.75 | **0.83** | 1.53 (jackson) | **1.05** (fastcsv) | 0.79 | **0.49** |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.29 | **0.29** | 0.67 | **0.67** | 0.93 (fastcsv) | **0.89** (fastcsv) | 0.62 | **0.67** |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.24 | **0.24** | 0.53 | **0.54** | 0.78 (fastcsv) | **0.74** (fastcsv) | 0.50 | **0.49** |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.21 | **0.23** | 0.52 | **0.55** | 0.69 (fastcsv) | **0.76** (fastcsv) | 0.27 | **0.30** |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.18 | **0.19** | 0.43 | **0.44** | 0.56 (fastcsv) | **0.58** (fastcsv) | 0.23 | **0.24** |
| SVM (x86-64-v3) | quoted | 1 MiB | 1.57 | **1.63** | 0.52 | **0.52** | 1.50 (fastcsv) | **1.55** (fastcsv) | 1.50 | **1.56** |
| SVM (x86-64-v3) | quoted | 128 MiB | 1.27 | **1.38** | 0.46 | **0.47** | 1.25 (fastcsv) | **1.35** (fastcsv) | 1.25 | **1.36** |
| JVM | escaped | 1 MiB | 0.42 | **0.65** | 0.93 | **0.94** | 1.02 (jackson) | **1.57** (jackson) | 0.69 | **1.07** |
| JVM | escaped | 128 MiB | 0.26 | **0.63** | 0.83 | **0.96** | 0.80 (jackson) | **1.24** (jackson) | 0.62 | **0.96** |
| JVM | mixed | 1 MiB | 0.56 | **0.69** | 0.80 | **0.89** | 2.29 (jackson) | **1.75** (fastcsv) | 1.20 | **1.46** |
| JVM | mixed | 128 MiB | 0.33 | **0.61** | 0.71 | **0.83** | 1.42 (fastcsv) | **1.50** (fastcsv) | 1.04 | **1.23** |
| JVM | numeric | 1 MiB | 0.63 | **0.74** | 0.73 | **0.84** | 1.55 (fastcsv) | **1.87** (univocity) | 0.81 | **1.02** |
| JVM | numeric | 128 MiB | 0.65 | **0.67** | 0.74 | **0.76** | 1.37 (fastcsv) | **1.36** (fastcsv) | 0.79 | **0.83** |
| JVM | quoted | 1 MiB | 0.51 | **5.42** | 0.19 | **0.82** | 0.38 (fastcsv) | **3.20** (fastcsv) | 0.51 | **4.54** |
| JVM | quoted | 128 MiB | 0.17 | **2.30** | 0.11 | **0.93** | 0.18 (fastcsv) | **2.44** (fastcsv) | 0.22 | **3.03** |

### typed: `truffledata-plan-native`

| Runtime | Dataset | Size | GB/s before | GB/s after | × kernel before | × kernel after | × best Java before | × best Java after | × csv-typed before | × csv-typed after |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | mixed | 1 MiB | 0.39 | **0.51** | 0.52 | **0.64** | 2.47 (fastcsv) | **2.93** (fastcsv) | 1.07 | **1.35** |
| SVM (compat) | mixed | 128 MiB | 0.45 | **0.45** | 0.62 | **0.60** | 2.80 (fastcsv) | **2.62** (fastcsv) | 1.13 | **1.18** |
| SVM (compat) | numeric | 1 MiB | 0.30 | **0.31** | 0.66 | **0.70** | 3.04 (fastcsv) | **2.96** (fastcsv) | 0.63 | **0.66** |
| SVM (compat) | numeric | 128 MiB | 0.14 | **0.14** | 0.59 | **0.57** | 2.80 (fastcsv) | **2.65** (fastcsv) | 0.74 | **0.61** |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.56 | **0.55** | 0.75 | **0.67** | 3.18 (fastcsv) | **3.10** (fastcsv) | 1.43 | **1.61** |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.47 | **0.49** | 0.62 | **0.62** | 2.66 (fastcsv) | **2.70** (fastcsv) | 1.17 | **1.25** |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.27 | **0.34** | 0.62 | **0.73** | 2.76 (fastcsv) | **2.99** (fastcsv) | 0.58 | **0.73** |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.31 | **0.31** | 0.65 | **0.65** | 2.85 (fastcsv) | **2.85** (fastcsv) | 0.67 | **0.65** |
| JVM | mixed | 1 MiB | 0.69 | **0.80** | 0.62 | **0.71** | 2.71 (fastcsv) | **2.84** (fastcsv) | 1.80 | **2.08** |
| JVM | mixed | 128 MiB | 0.43 | **0.80** | 0.72 | **0.79** | 2.36 (fastcsv) | **2.87** (fastcsv) | 1.67 | **2.02** |
| JVM | numeric | 1 MiB | 0.54 | **0.60** | 0.78 | **0.86** | 2.71 (univocity) | **2.63** (fastcsv) | 1.15 | **1.33** |
| JVM | numeric | 128 MiB | 0.30 | **0.57** | 0.43 | **0.82** | 1.23 (fastcsv) | **2.28** (fastcsv) | 0.62 | **1.18** |
