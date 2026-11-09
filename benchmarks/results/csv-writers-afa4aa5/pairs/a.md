| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3013 [4.2834-4.3355] | 4.3350 [4.3339-4.3949] | 1.0078 | 0.2325 -> 0.2307 | 404.36 -> 404.36 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.2194 [2.2095-2.2248] | 2.2514 [2.2509-10.2416] | 1.0144 | 0.4506 -> 0.4442 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.5584 [3.5405-3.5802] | 3.6692 [3.6608-3.6811] | 1.0312 | 0.2810 -> 0.2725 | 88.52 -> 88.75 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 3.1141 [3.1075-3.1307] | 3.2409 [3.2195-3.2694] | 1.0407 | 0.3211 -> 0.3086 | 88.57 -> 88.81 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 3.0698 [3.0682-3.1087] | 3.1918 [3.1864-3.2372] | 1.0397 | 0.3258 -> 0.3133 | 64.52 -> 64.75 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.6444 [2.6309-2.6469] | 2.7735 [2.7698-2.7738] | 1.0488 | 0.3782 -> 0.3606 | 64.57 -> 64.81 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 75.6505 [72.3591-100.0906] | 72.5922 [72.3712-72.6695] | 0.9596 |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.6141 [6.5743-6.6223] | 6.6324 [6.5735-6.6336] | 1.0028 |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 71.5098 [70.7850-72.2779] | 70.9968 [70.4278-71.1451] | 0.9928 |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.8432 [3.6584-4.5435] | 3.8907 [3.5844-4.7769] | 1.0124 | 0.2602 -> 0.2570 | 1362.90 -> 1362.90 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.3569 [1.3337-1.7700] | 1.3922 [1.3421-1.7752] | 1.0261 | 0.7370 -> 0.7183 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.8969 [2.8633-2.9073] | 3.0397 [2.9056-3.7797] | 1.0493 | 0.3452 -> 0.3290 | 448.16 -> 451.04 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 2.2854 [2.1779-2.5396] | 2.3125 [2.2509-2.4090] | 1.0119 | 0.4376 -> 0.4324 | 448.43 -> 451.30 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.6128 [2.5129-2.6504] | 2.7184 [2.5735-2.8745] | 1.0404 | 0.3827 -> 0.3679 | 424.16 -> 427.04 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 2.0289 [2.0109-2.1501] | 2.1371 [2.1253-2.1729] | 1.0534 | 0.4929 -> 0.4679 | 424.43 -> 427.30 |
