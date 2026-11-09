# Java slab producer and writer diagnosis

1 fresh-process forks. Ratios use medians of fork medians; every raw sample is retained. Reused probe storage and fresh owning production storage are labeled separately.

| Runtime | Mode | Variant | Case | ns/message | bytes/message | Within-fork max/min |
|---|---|---:|---|---:|---:|---:|
| jvm | holdout | 0 | generated-owning | 1311.44 | 6932.75 | 1.057 |
| jvm | holdout | 0 | probe-slab-reused | 990.05 | 0.00 | 1.011 |
| jvm | holdout | 0 | probe-spans-reused | 583.82 | 0.00 | 1.027 |
| jvm | holdout | 0 | stock | 1558.84 | 4769.25 | 1.031 |
| jvm | holdout | 0 | table-owning | 35325.02 | 8012.25 | 11.581 |
| jvm | holdout | 1 | probe-slab-reused | 953.62 | 0.00 | 1.031 |
| jvm | holdout | 2 | probe-slab-reused | 648.51 | 0.00 | 1.019 |
| jvm | holdout | 3 | probe-slab-reused | 1201.99 | 0.00 | 1.012 |
| jvm | holdout | 4 | probe-slab-reused | 963.53 | 0.00 | 1.016 |
| jvm | large | 0 | generated-owning | 124918.49 | 701041.25 | 1.020 |
| jvm | large | 0 | probe-slab-reused | 103140.19 | 0.00 | 1.004 |
| jvm | large | 0 | probe-spans-reused | 59407.67 | 0.00 | 1.017 |
| jvm | large | 0 | stock | 99062.68 | 306067.00 | 1.098 |
| jvm | large | 0 | table-owning | 315784.18 | 868180.25 | 1.426 |
| jvm | large | 1 | probe-slab-reused | 101554.47 | 0.00 | 1.057 |
| jvm | large | 2 | probe-slab-reused | 69602.76 | 0.00 | 1.047 |
| jvm | large | 3 | probe-slab-reused | 127013.06 | 0.00 | 1.083 |
| jvm | large | 4 | probe-slab-reused | 103465.78 | 0.00 | 1.006 |
| jvm | small | 0 | generated-owning | 389.45 | 2256.50 | 36.304 |
| jvm | small | 0 | probe-slab-reused | 335.44 | 0.00 | 1.006 |
| jvm | small | 0 | probe-spans-reused | 193.51 | 0.00 | 1.070 |
| jvm | small | 0 | stock | 418.15 | 1455.50 | 1.068 |
| jvm | small | 0 | table-owning | 41671.47 | 2931.50 | 55.196 |
| jvm | small | 1 | probe-slab-reused | 308.01 | 0.00 | 1.127 |
| jvm | small | 2 | probe-slab-reused | 224.36 | 0.00 | 1.076 |
| jvm | small | 3 | probe-slab-reused | 408.45 | 0.00 | 1.013 |
| jvm | small | 4 | probe-slab-reused | 320.15 | 0.00 | 1.069 |
| svm | holdout | 0 | generated-owning | 4484.35 | 6864.50 | 1.035 |
| svm | holdout | 0 | probe-slab-reused | 2219.22 | 0.00 | 1.006 |
| svm | holdout | 0 | probe-spans-reused | 1368.30 | 0.00 | 1.013 |
| svm | holdout | 0 | stock | 4506.95 | 5309.00 | 1.096 |
| svm | holdout | 0 | table-owning | 9393.54 | 8036.50 | 1.041 |
| svm | large | 0 | generated-owning | 617863.78 | 700914.25 | 1.057 |
| svm | large | 0 | probe-slab-reused | 245808.80 | 0.00 | 1.009 |
| svm | large | 0 | probe-spans-reused | 146032.41 | 0.00 | 1.062 |
| svm | large | 0 | stock | 371984.61 | 433008.00 | 1.551 |
| svm | large | 0 | table-owning | 1156067.57 | 886144.00 | 1.019 |
| svm | small | 0 | generated-owning | 1581.79 | 2227.50 | 1.039 |
| svm | small | 0 | probe-slab-reused | 824.06 | 0.00 | 1.078 |
| svm | small | 0 | probe-spans-reused | 478.87 | 0.00 | 1.138 |
| svm | small | 0 | stock | 1237.89 | 1629.50 | 1.020 |
| svm | small | 0 | table-owning | 3191.27 | 2887.50 | 1.016 |
