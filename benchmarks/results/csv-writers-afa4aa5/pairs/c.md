| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | fastcsv | ingest | 3.4749 [3.3992-3.7499] | 3.4099 [3.3886-3.4830] | 0.9813 | 0.2878 -> 0.2933 | 322.66 -> 322.66 |
| numeric 1 MiB | strings | kernel-native | ingest | 2.3350 [2.2789-2.4463] | 2.2850 [2.2679-2.2986] | 0.9786 | 0.4283 -> 0.4376 | 256.01 -> 256.01 |
| numeric 1 MiB | strings | truffledata-batch | ingest | 4.7546 [4.7359-4.7971] | 4.5084 [4.4600-4.5518] | 0.9482 | 0.2103 -> 0.2218 | 404.30 -> 404.30 |
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3612 [4.3282-4.3672] | 4.0299 [4.0215-4.0525] | 0.9240 | 0.2293 -> 0.2481 | 404.36 -> 404.36 |
| numeric 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.8432 [3.8367-3.8490] | 3.5550 [3.5348-3.5658] | 0.9250 | 0.2602 -> 0.2813 | 352.55 -> 352.55 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 2.6739 [2.6519-2.6773] | 2.6622 [2.6426-164.6650] | 0.9956 | 0.3740 -> 0.3756 | 88.57 -> 88.57 |
| mixed 1 MiB | strings | fastcsv | ingest | 3.1837 [3.1775-3.1967] | 3.3767 [3.3579-3.4380] | 1.0606 | 0.3141 -> 0.2961 | 995.90 -> 995.90 |
| mixed 1 MiB | strings | kernel-native | ingest | 2.2667 [2.2603-2.3158] | 2.2894 [2.2851-2.3050] | 1.0100 | 0.4412 -> 0.4368 | 898.57 -> 898.57 |
| mixed 1 MiB | strings | truffledata-batch | ingest | 4.1465 [4.1365-4.2090] | 3.9185 [3.9054-3.9549] | 0.9450 | 0.2412 -> 0.2552 | 1362.64 -> 1362.64 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.5743 [3.5279-3.5772] | 3.4271 [3.3976-3.4539] | 0.9588 | 0.2798 -> 0.2918 | 1362.90 -> 1362.90 |
| mixed 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.1619 [3.1338-3.1638] | 2.9890 [2.9265-3.4488] | 0.9453 | 0.3163 -> 0.3346 | 1133.21 -> 1133.21 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 1.8335 [1.8301-1.8642] | 1.8377 [1.8291-1.8609] | 1.0023 | 0.5454 -> 0.5442 | 448.43 -> 448.43 |
| quoted 1 MiB | strings | fastcsv | ingest | 1.4561 [1.4558-1.4703] | 1.4922 [1.4571-1.5130] | 1.0248 | 0.6868 -> 0.6702 | 850.86 -> 850.86 |
| quoted 1 MiB | strings | kernel-native | ingest | 0.6505 [0.6440-0.6618] | 0.6502 [0.6405-0.6528] | 0.9995 | 1.5373 -> 1.5381 | 768.52 -> 768.52 |
| quoted 1 MiB | strings | truffledata-batch | ingest | 1.5754 [1.5648-1.5964] | 1.5761 [1.5689-1.5824] | 1.0005 | 0.6348 -> 0.6345 | 1496.29 -> 1496.29 |
| quoted 1 MiB | strings | truffledata-batch-native | ingest | 1.1878 [1.1862-1.1886] | 1.1702 [1.1611-1.1718] | 0.9852 | 0.8419 -> 0.8546 | 1497.06 -> 1497.06 |
| quoted 1 MiB | strings | truffledata-batch-owned-native | ingest | 0.7152 [0.7072-0.7213] | 0.7138 [0.7114-0.7184] | 0.9981 | 1.3982 -> 1.4009 | 831.63 -> 831.63 |
| escaped 1 MiB | strings | fastcsv | ingest | 2.8364 [2.8312-2.9938] | 2.8612 [2.8189-2.9885] | 1.0088 | 0.3526 -> 0.3495 | 344.10 -> 344.10 |
| escaped 1 MiB | strings | kernel-native | ingest | 1.8797 [1.8717-1.9150] | 1.9296 [1.8702-1.9644] | 1.0265 | 0.5320 -> 0.5182 | 284.15 -> 284.15 |
| escaped 1 MiB | strings | truffledata-batch | ingest | 2.7055 [2.6876-2.7302] | 2.6899 [2.6711-2.7031] | 0.9942 | 0.3696 -> 0.3718 | 594.20 -> 594.20 |
| escaped 1 MiB | strings | truffledata-batch-native | ingest | 2.4190 [2.4055-2.5163] | 2.4115 [2.4010-2.4305] | 0.9969 | 0.4134 -> 0.4147 | 594.47 -> 594.47 |
| escaped 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.0206 [2.0116-2.0343] | 2.0378 [2.0086-2.0618] | 1.0085 | 0.4949 -> 0.4907 | 362.92 -> 362.92 |
