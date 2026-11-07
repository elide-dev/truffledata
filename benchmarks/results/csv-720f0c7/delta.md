
### scan: `truffledata-scanner-native`

| Runtime | Dataset | Size | GB/s before | GB/s after | × kernel before | × kernel after | × best Java before | × best Java after | × csv-core-scan before | × csv-core-scan after | × native-indexed-scan before | × native-indexed-scan after |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 3.65 | **5.80** | 0.96 | **0.88** | 18.65 (jackson) | **19.02** (jackson) | 7.68 | **7.11** | 0.86 | **0.71** |
| SVM (compat) | escaped | 128 MiB | 5.41 | **5.01** | 1.25 | **0.94** | 13.79 (fastcsv) | **13.15** (jackson) | 5.21 | **4.88** | 0.85 | **0.79** |
| SVM (compat) | mixed | 1 MiB | 1.95 | **1.47** | 1.45 | **0.79** | 4.07 (fastcsv) | **4.21** (fastcsv) | 1.89 | **1.41** | 0.71 | **0.52** |
| SVM (compat) | mixed | 128 MiB | 1.74 | **1.36** | 0.96 | **0.75** | 3.74 (fastcsv) | **3.11** (fastcsv) | 1.72 | **1.27** | 0.79 | **0.60** |
| SVM (compat) | numeric | 1 MiB | 1.47 | **1.13** | 0.88 | **0.69** | 3.00 (fastcsv) | **2.30** (fastcsv) | 1.37 | **0.99** | 0.60 | **0.45** |
| SVM (compat) | numeric | 128 MiB | 0.67 | **0.51** | 0.94 | **0.69** | 2.40 (fastcsv) | **1.77** (fastcsv) | 1.39 | **1.07** | 1.20 | **0.78** |
| SVM (compat) | quoted | 1 MiB | 8.13 | **10.50** | 0.91 | **0.90** | 15.13 (fastcsv) | **11.76** (fastcsv) | 9.57 | **7.05** | 0.82 | **0.72** |
| SVM (compat) | quoted | 128 MiB | 4.52 | **8.90** | 0.88 | **0.85** | 9.61 (fastcsv) | **8.15** (fastcsv) | 5.34 | **4.83** | 0.85 | **0.70** |
| SVM (x86-64-v3) | escaped | 1 MiB | 6.14 | **5.63** | 0.90 | **0.85** | 16.84 (fastcsv) | **16.45** (fastcsv) | 7.67 | **6.86** | 0.80 | **0.70** |
| SVM (x86-64-v3) | escaped | 128 MiB | 3.15 | **2.91** | 0.94 | **0.88** | 12.54 (fastcsv) | **11.46** (fastcsv) | 5.55 | **5.51** | 0.83 | **0.82** |
| SVM (x86-64-v3) | mixed | 1 MiB | 1.96 | **1.44** | 0.92 | **0.74** | 4.77 (fastcsv) | **2.99** (fastcsv) | 2.03 | **1.36** | 0.73 | **0.51** |
| SVM (x86-64-v3) | mixed | 128 MiB | 1.75 | **1.29** | 0.99 | **0.71** | 4.33 (fastcsv) | **2.83** (fastcsv) | 1.70 | **1.23** | 0.77 | **0.57** |
| SVM (x86-64-v3) | numeric | 1 MiB | 1.49 | **0.92** | 0.92 | **0.62** | 2.56 (fastcsv) | **1.73** (fastcsv) | 1.56 | **0.85** | 0.62 | **0.41** |
| SVM (x86-64-v3) | numeric | 128 MiB | 1.43 | **1.11** | 0.83 | **0.65** | 2.47 (fastcsv) | **1.92** (fastcsv) | 1.28 | **0.95** | 0.69 | **0.43** |
| SVM (x86-64-v3) | quoted | 1 MiB | 8.23 | **10.04** | 0.91 | **0.80** | 8.80 (fastcsv) | **8.25** (fastcsv) | 9.76 | **6.71** | 0.83 | **0.69** |
| SVM (x86-64-v3) | quoted | 128 MiB | 8.67 | **7.93** | 0.96 | **0.86** | 7.27 (fastcsv) | **6.51** (fastcsv) | 6.19 | **5.40** | 0.84 | **0.77** |
| JVM | escaped | 1 MiB | 7.65 | **8.04** | 1.07 | **1.16** | 18.32 (fastcsv) | **19.42** (fastcsv) | 9.24 | **9.89** | 0.93 | **0.99** |
| JVM | escaped | 128 MiB | 3.15 | **3.28** | 1.22 | **1.28** | 9.73 (jackson) | **10.09** (jackson) | 6.63 | **7.16** | 1.02 | **1.10** |
| JVM | mixed | 1 MiB | 1.66 | **2.91** | 1.08 | **1.02** | 4.26 (fastcsv) | **5.39** (fastcsv) | 2.92 | **2.87** | 1.12 | **1.09** |
| JVM | mixed | 128 MiB | 1.49 | **1.55** | 1.05 | **1.08** | 4.37 (fastcsv) | **3.99** (fastcsv) | 2.44 | **2.53** | 1.05 | **1.09** |
| JVM | numeric | 1 MiB | 2.88 | **2.44** | 1.83 | **0.84** | 2.63 (fastcsv) | **2.24** (fastcsv) | 2.57 | **2.15** | 1.15 | **0.97** |
| JVM | numeric | 128 MiB | 2.49 | **2.30** | 0.91 | **0.82** | 2.85 (fastcsv) | **2.51** (fastcsv) | 2.17 | **2.03** | 1.01 | **0.90** |
| JVM | quoted | 1 MiB | 13.85 | **14.15** | 1.24 | **1.32** | 7.59 (fastcsv) | **8.77** (fastcsv) | 9.31 | **9.69** | 0.96 | **0.99** |
| JVM | quoted | 128 MiB | 6.06 | **6.92** | 2.17 | **1.32** | 4.78 (fastcsv) | **5.47** (fastcsv) | 6.51 | **7.66** | 0.83 | **0.96** |

### strings: `truffledata-batch-native`

| Runtime | Dataset | Size | GB/s before | GB/s after | × kernel before | × kernel after | × best Java before | × best Java after | × csv-string-record before | × csv-string-record after |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.24 | **0.43** | 0.73 | **0.81** | 1.47 (jackson) | **1.40** (jackson) | 0.57 | **0.70** |
| SVM (compat) | escaped | 128 MiB | 0.44 | **0.41** | 0.70 | **0.64** | 1.17 (jackson) | **1.15** (jackson) | 0.55 | **0.50** |
| SVM (compat) | mixed | 1 MiB | 0.24 | **0.26** | 0.58 | **0.71** | 0.87 (fastcsv) | **1.06** (fastcsv) | 0.55 | **0.62** |
| SVM (compat) | mixed | 128 MiB | 0.21 | **0.23** | 0.74 | **0.53** | 0.71 (fastcsv) | **0.80** (fastcsv) | 0.46 | **0.46** |
| SVM (compat) | numeric | 1 MiB | 0.20 | **0.21** | 0.45 | **0.59** | 0.75 (fastcsv) | **0.76** (fastcsv) | 0.26 | **0.27** |
| SVM (compat) | numeric | 128 MiB | 0.09 | **0.11** | 0.42 | **0.54** | 0.68 (fastcsv) | **0.78** (fastcsv) | 0.35 | **0.30** |
| SVM (compat) | quoted | 1 MiB | 0.68 | **0.85** | 0.75 | **0.58** | 1.80 (fastcsv) | **1.36** (fastcsv) | 0.86 | **0.82** |
| SVM (compat) | quoted | 128 MiB | 0.56 | **0.58** | 0.69 | **0.33** | 1.55 (fastcsv) | **0.74** (fastcsv) | 0.76 | **0.46** |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.48 | **0.50** | 0.72 | **0.77** | 1.41 (jackson) | **1.57** (fastcsv) | 0.85 | **0.85** |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.33 | **0.34** | 0.75 | **0.75** | 1.47 (jackson) | **1.53** (jackson) | 0.69 | **0.79** |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.26 | **0.29** | 0.58 | **0.67** | 0.84 (fastcsv) | **0.93** (fastcsv) | 0.56 | **0.62** |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.21 | **0.24** | 0.49 | **0.53** | 0.71 (fastcsv) | **0.78** (fastcsv) | 0.45 | **0.50** |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.20 | **0.21** | 0.48 | **0.52** | 0.62 (fastcsv) | **0.69** (fastcsv) | 0.28 | **0.27** |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.17 | **0.18** | 0.42 | **0.43** | 0.56 (fastcsv) | **0.56** (fastcsv) | 0.25 | **0.23** |
| SVM (x86-64-v3) | quoted | 1 MiB | 0.93 | **1.57** | 0.50 | **0.52** | 1.27 (fastcsv) | **1.50** (fastcsv) | 1.18 | **1.50** |
| SVM (x86-64-v3) | quoted | 128 MiB | 0.72 | **1.27** | 0.26 | **0.46** | 0.74 (fastcsv) | **1.25** (fastcsv) | 0.71 | **1.25** |
| JVM | escaped | 1 MiB | 0.28 | **0.42** | 0.63 | **0.93** | 0.67 (jackson) | **1.02** (jackson) | 0.46 | **0.69** |
| JVM | escaped | 128 MiB | 0.27 | **0.26** | 0.87 | **0.83** | 0.84 (jackson) | **0.80** (jackson) | 0.65 | **0.62** |
| JVM | mixed | 1 MiB | 0.36 | **0.56** | 0.74 | **0.80** | 1.66 (fastcsv) | **2.29** (jackson) | 1.10 | **1.20** |
| JVM | mixed | 128 MiB | 0.25 | **0.33** | 0.68 | **0.71** | 1.01 (fastcsv) | **1.42** (fastcsv) | 0.76 | **1.04** |
| JVM | numeric | 1 MiB | 0.62 | **0.63** | 0.70 | **0.73** | 3.15 (fastcsv) | **1.55** (fastcsv) | 0.81 | **0.81** |
| JVM | numeric | 128 MiB | 0.57 | **0.65** | 0.65 | **0.74** | 1.23 (fastcsv) | **1.37** (fastcsv) | 0.70 | **0.79** |
| JVM | quoted | 1 MiB | 0.71 | **0.51** | 0.47 | **0.19** | 0.57 (fastcsv) | **0.38** (fastcsv) | 0.69 | **0.51** |
| JVM | quoted | 128 MiB | 0.86 | **0.17** | 0.55 | **0.11** | 0.95 (fastcsv) | **0.18** (fastcsv) | 1.11 | **0.22** |

### typed: `truffledata-plan-native`

| Runtime | Dataset | Size | GB/s before | GB/s after | × kernel before | × kernel after | × best Java before | × best Java after | × csv-typed before | × csv-typed after |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | mixed | 1 MiB | 0.44 | **0.39** | 0.54 | **0.52** | 2.64 (fastcsv) | **2.47** (fastcsv) | 1.17 | **1.07** |
| SVM (compat) | mixed | 128 MiB | 0.41 | **0.45** | 0.55 | **0.62** | 2.40 (fastcsv) | **2.80** (fastcsv) | 1.10 | **1.13** |
| SVM (compat) | numeric | 1 MiB | 0.24 | **0.30** | 0.56 | **0.66** | 2.25 (fastcsv) | **3.04** (fastcsv) | 0.54 | **0.63** |
| SVM (compat) | numeric | 128 MiB | 0.09 | **0.14** | 0.38 | **0.59** | 1.99 (univocity) | **2.80** (fastcsv) | 0.63 | **0.74** |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.46 | **0.56** | 0.55 | **0.75** | 3.14 (univocity) | **3.18** (fastcsv) | 1.24 | **1.43** |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.42 | **0.47** | 0.53 | **0.62** | 2.55 (fastcsv) | **2.66** (fastcsv) | 1.05 | **1.17** |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.26 | **0.27** | 0.58 | **0.62** | 2.31 (fastcsv) | **2.76** (fastcsv) | 0.55 | **0.58** |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.22 | **0.31** | 0.46 | **0.65** | 2.09 (fastcsv) | **2.85** (fastcsv) | 0.51 | **0.67** |
| JVM | mixed | 1 MiB | 0.41 | **0.69** | 0.64 | **0.62** | 2.45 (fastcsv) | **2.71** (fastcsv) | 1.60 | **1.80** |
| JVM | mixed | 128 MiB | 0.32 | **0.43** | 0.54 | **0.72** | 1.79 (fastcsv) | **2.36** (fastcsv) | 1.26 | **1.67** |
| JVM | numeric | 1 MiB | 0.26 | **0.54** | 0.38 | **0.78** | 1.21 (univocity) | **2.71** (univocity) | 0.64 | **1.15** |
| JVM | numeric | 128 MiB | 0.30 | **0.30** | 0.42 | **0.43** | 1.23 (fastcsv) | **1.23** (fastcsv) | 0.62 | **0.62** |
