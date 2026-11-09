| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | Ranges | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---|---:|---:|
| numeric 1 MiB | typed | fastcsv | ingest | 9.7112 [9.5036-9.8038] | 9.3079 [9.2881-9.3114] | 0.9585 | separate | 0.1030 -> 0.1074 | 442.67 -> 442.67 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.1899 [2.1569-2.2361] | 2.1494 [2.0992-4.6276] | 0.9815 | overlap | 0.4566 -> 0.4652 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.0555 [3.0540-3.1123] | 3.0285 [3.0108-3.0818] | 0.9911 | overlap | 0.3273 -> 0.3302 | 88.51 -> 88.51 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 2.6494 [2.6008-2.6535] | 2.6669 [2.6124-2.9439] | 1.0066 | overlap | 0.3774 -> 0.3750 | 88.57 -> 88.57 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 2.6028 [2.5933-2.6082] | 2.5632 [2.5602-2.7078] | 0.9848 | overlap | 0.3842 -> 0.3901 | 64.51 -> 64.51 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.1734 [2.1503-4.6436] | 2.2283 [2.1197-2.2919] | 1.0252 | overlap | 0.4601 -> 0.4488 | 64.57 -> 64.57 |
| numeric 1 MiB | typed-reads | batch-fastcsv | interpret | 42.8570 [42.3652-43.2046] | 43.6334 [42.4501-48.5983] | 1.0181 | overlap |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | batch-fastcsv | peak | 10.7951 [10.7159-11.3661] | 10.6381 [10.6099-11.2266] | 0.9855 | overlap |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | batch-fastcsv | read-aot | 41.5533 [41.1931-41.6709] | 41.8873 [41.7968-41.9294] | 1.0080 | separate |  | 0.00 -> 0.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.0198 [71.8153-73.7455] | 72.3879 [71.9277-74.1341] | 1.0051 | overlap |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.6515 [6.5744-6.9442] | 6.6482 [6.5641-7.6393] | 0.9995 | overlap |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 72.1545 [71.4646-72.5387] | 71.6750 [71.4547-72.8572] | 0.9934 | overlap |  | 0.00 -> 0.00 |
