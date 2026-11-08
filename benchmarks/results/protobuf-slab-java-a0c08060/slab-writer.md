# Java slab producer and writer diagnosis

3 fresh-process forks per cell. Ratios use medians of fork medians; every raw sample is retained. Reused probe storage and fresh owning production storage are labeled separately.

| Runtime | Mode | Variant | Case | ns/message | bytes/message | Within-fork max/min |
|---|---|---:|---|---:|---:|---:|
| jvm | holdout | 0 | generated-owning | 961.86 | 2451.75 | 2.739 |
| jvm | holdout | 0 | probe-slab-reused | 996.56 | 0.00 | 1.037 |
| jvm | holdout | 0 | probe-spans-reused | 586.28 | 0.00 | 1.100 |
| jvm | holdout | 0 | stock | 1383.94 | 4708.86 | 1.056 |
| jvm | holdout | 0 | table-owning | 2781.05 | 7965.00 | 1.087 |
| jvm | holdout | 1 | probe-slab-reused | 890.95 | 0.00 | 1.044 |
| jvm | holdout | 2 | probe-slab-reused | 644.11 | 0.00 | 1.070 |
| jvm | holdout | 3 | probe-slab-reused | 1172.65 | 0.00 | 1.041 |
| jvm | holdout | 4 | probe-slab-reused | 980.80 | 0.00 | 3.561 |
| jvm | large | 0 | generated-owning | 104997.69 | 351103.00 | 1.020 |
| jvm | large | 0 | probe-slab-reused | 107747.82 | 0.00 | 1.020 |
| jvm | large | 0 | probe-spans-reused | 56670.28 | 0.00 | 1.021 |
| jvm | large | 0 | stock | 94008.73 | 306083.00 | 1.971 |
| jvm | large | 0 | table-owning | 314082.76 | 868180.25 | 1.014 |
| jvm | large | 1 | probe-slab-reused | 97409.31 | 0.00 | 1.061 |
| jvm | large | 2 | probe-slab-reused | 72305.74 | 0.00 | 1.015 |
| jvm | large | 3 | probe-slab-reused | 133052.18 | 0.00 | 2.083 |
| jvm | large | 4 | probe-slab-reused | 105722.93 | 0.00 | 1.026 |
| jvm | small | 0 | generated-owning | 316.09 | 773.25 | 1.291 |
| jvm | small | 0 | probe-slab-reused | 310.25 | 0.00 | 1.161 |
| jvm | small | 0 | probe-spans-reused | 192.23 | 0.00 | 1.081 |
| jvm | small | 0 | stock | 376.92 | 1392.45 | 1.110 |
| jvm | small | 0 | table-owning | 959.34 | 2873.30 | 1.049 |
| jvm | small | 1 | probe-slab-reused | 301.96 | 0.00 | 1.117 |
| jvm | small | 2 | probe-slab-reused | 219.33 | 0.00 | 1.074 |
| jvm | small | 3 | probe-slab-reused | 420.36 | 0.00 | 4.341 |
| jvm | small | 4 | probe-slab-reused | 305.77 | 0.00 | 1.038 |
| svm | holdout | 0 | generated-owning | 3024.17 | 2422.50 | 1.081 |
| svm | holdout | 0 | probe-slab-reused | 2255.30 | 0.00 | 1.020 |
| svm | holdout | 0 | probe-spans-reused | 1321.62 | 0.00 | 1.040 |
| svm | holdout | 0 | stock | 3775.95 | 5309.00 | 1.031 |
| svm | holdout | 0 | table-owning | 9034.42 | 8036.50 | 1.014 |
| svm | large | 0 | generated-owning | 434897.70 | 351012.00 | 1.016 |
| svm | large | 0 | probe-slab-reused | 245152.09 | 0.00 | 1.031 |
| svm | large | 0 | probe-spans-reused | 144479.85 | 0.00 | 1.040 |
| svm | large | 0 | stock | 325957.20 | 433008.00 | 23.852 |
| svm | large | 0 | table-owning | 1150184.47 | 886144.00 | 5.284 |
| svm | small | 0 | generated-owning | 1009.19 | 760.25 | 1.074 |
| svm | small | 0 | probe-slab-reused | 816.69 | 0.00 | 1.198 |
| svm | small | 0 | probe-spans-reused | 488.65 | 0.00 | 1.026 |
| svm | small | 0 | stock | 1193.92 | 1629.50 | 19.461 |
| svm | small | 0 | table-owning | 3178.74 | 2887.50 | 4.551 |
