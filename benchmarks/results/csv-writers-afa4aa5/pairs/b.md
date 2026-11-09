| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3149 [4.2705-4.3765] | 4.2905 [4.2719-4.3973] | 0.9944 | 0.2318 -> 0.2331 | 404.36 -> 404.36 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.2386 [2.2109-2.2423] | 2.2221 [2.2123-2.2236] | 0.9926 | 0.4467 -> 0.4500 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.5374 [3.5253-4.1092] | 3.2969 [3.2719-3.3027] | 0.9320 | 0.2827 -> 0.3033 | 88.52 -> 88.75 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 3.1137 [3.0801-3.1209] | 2.8758 [2.8678-2.8872] | 0.9236 | 0.3212 -> 0.3477 | 88.57 -> 88.81 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 3.0462 [3.0408-3.0667] | 2.7948 [2.7914-2.8223] | 0.9175 | 0.3283 -> 0.3578 | 64.52 -> 64.75 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.6239 [2.6222-2.6350] | 2.4052 [2.3994-4.4046] | 0.9167 | 0.3811 -> 0.4158 | 64.57 -> 64.81 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.5808 [72.4465-72.6050] | 72.3333 [72.0032-72.4030] | 0.9966 |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.6174 [6.6062-6.7159] | 6.5968 [6.5866-6.9231] | 0.9969 |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 70.7248 [70.3916-71.2840] | 70.4248 [70.1913-71.2832] | 0.9958 |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.6159 [3.6012-3.6772] | 3.5964 [3.5786-8.2135] | 0.9946 | 0.2766 -> 0.2781 | 1362.90 -> 1362.90 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.2795 [1.2702-4.4755] | 1.2298 [1.2171-1.2469] | 0.9611 | 0.7815 -> 0.8132 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.6984 [2.6887-2.8801] | 2.5195 [2.4967-2.6090] | 0.9337 | 0.3706 -> 0.3969 | 448.16 -> 451.04 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 2.0782 [2.0696-2.0848] | 1.9056 [1.9001-1.9523] | 0.9169 | 0.4812 -> 0.5248 | 448.43 -> 451.30 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.4845 [2.4472-2.4968] | 2.3052 [2.2819-2.3176] | 0.9278 | 0.4025 -> 0.4338 | 424.16 -> 427.04 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 1.9054 [1.8623-1.9421] | 1.7595 [1.7248-1.8719] | 0.9234 | 0.5248 -> 0.5683 | 424.43 -> 427.30 |
