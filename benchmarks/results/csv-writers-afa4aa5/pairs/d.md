| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | fastcsv | ingest | 3.4858 [3.3612-3.4965] | 3.3602 [3.3508-3.4202] | 0.9640 | 0.2869 -> 0.2976 | 322.66 -> 322.66 |
| numeric 1 MiB | strings | kernel-native | ingest | 2.3518 [2.3409-2.5439] | 2.2726 [2.2678-2.2854] | 0.9663 | 0.4252 -> 0.4400 | 256.01 -> 256.01 |
| numeric 1 MiB | strings | truffledata-batch | ingest | 4.5369 [4.5291-4.5429] | 4.0525 [4.0275-4.0920] | 0.8932 | 0.2204 -> 0.2468 | 404.30 -> 404.30 |
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.0462 [3.9901-4.0482] | 3.5886 [3.5877-3.6228] | 0.8869 | 0.2471 -> 0.2787 | 404.36 -> 404.36 |
| numeric 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.6175 [3.6168-3.6254] | 3.3323 [3.1464-3.4043] | 0.9212 | 0.2764 -> 0.3001 | 352.55 -> 352.55 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 2.6904 [2.6029-2.7580] | 2.6440 [2.6387-2.6562] | 0.9828 | 0.3717 -> 0.3782 | 88.57 -> 88.57 |
| mixed 1 MiB | strings | fastcsv | ingest | 3.3781 [3.3588-3.5706] | 3.2256 [3.1852-65.9546] | 0.9549 | 0.2960 -> 0.3100 | 995.90 -> 995.90 |
| mixed 1 MiB | strings | kernel-native | ingest | 2.2466 [2.2208-2.2756] | 2.3242 [2.3135-2.3467] | 1.0345 | 0.4451 -> 0.4303 | 898.57 -> 898.57 |
| mixed 1 MiB | strings | truffledata-batch | ingest | 4.0118 [3.9193-4.0996] | 3.7914 [3.6832-3.8442] | 0.9451 | 0.2493 -> 0.2638 | 1362.64 -> 1362.64 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.4082 [3.4005-3.5351] | 3.0732 [3.0729-3.1446] | 0.9017 | 0.2934 -> 0.3254 | 1362.90 -> 1362.90 |
| mixed 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.9405 [2.9132-3.0681] | 2.5992 [2.5801-2.6926] | 0.8839 | 0.3401 -> 0.3847 | 1133.21 -> 1133.21 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 1.8873 [1.8208-2.2094] | 1.8696 [1.8069-1.9441] | 0.9906 | 0.5298 -> 0.5349 | 448.43 -> 448.43 |
| quoted 1 MiB | strings | fastcsv | ingest | 1.4805 [1.4801-1.4823] | 1.4667 [1.4652-1.4784] | 0.9907 | 0.6754 -> 0.6818 | 850.86 -> 850.86 |
| quoted 1 MiB | strings | kernel-native | ingest | 0.6516 [0.6383-0.6728] | 0.6439 [0.6436-0.6727] | 0.9882 | 1.5346 -> 1.5530 | 768.52 -> 768.52 |
| quoted 1 MiB | strings | truffledata-batch | ingest | 1.5649 [1.5565-1.5857] | 1.5256 [1.5256-1.5653] | 0.9749 | 0.6390 -> 0.6555 | 1496.29 -> 1496.29 |
| quoted 1 MiB | strings | truffledata-batch-native | ingest | 1.1720 [1.1647-1.1929] | 1.1619 [1.1406-1.1763] | 0.9914 | 0.8532 -> 0.8606 | 1497.06 -> 1497.06 |
| quoted 1 MiB | strings | truffledata-batch-owned-native | ingest | 0.7067 [0.7023-0.7102] | 0.6905 [0.6827-0.6952] | 0.9771 | 1.4151 -> 1.4483 | 831.63 -> 831.63 |
| escaped 1 MiB | strings | fastcsv | ingest | 2.8622 [2.8482-2.8683] | 2.8285 [2.8107-2.8383] | 0.9882 | 0.3494 -> 0.3535 | 344.10 -> 344.10 |
| escaped 1 MiB | strings | kernel-native | ingest | 1.8768 [1.8713-1.8947] | 1.8371 [1.8313-1.9030] | 0.9788 | 0.5328 -> 0.5443 | 284.15 -> 284.15 |
| escaped 1 MiB | strings | truffledata-batch | ingest | 2.6689 [2.6596-2.7358] | 2.6457 [2.6373-2.7140] | 0.9913 | 0.3747 -> 0.3780 | 594.20 -> 594.20 |
| escaped 1 MiB | strings | truffledata-batch-native | ingest | 2.4008 [2.3496-2.4477] | 2.3658 [2.3131-2.4290] | 0.9854 | 0.4165 -> 0.4227 | 594.47 -> 594.47 |
| escaped 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.0521 [2.0077-2.0554] | 1.9617 [1.9560-2.3267] | 0.9560 | 0.4873 -> 0.5098 | 362.92 -> 362.92 |
