| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3524 [4.3474-5.0248] | 4.3396 [4.2952-4.9650] | 0.9970 | 0.2298 -> 0.2304 | 404.36 -> 404.36 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.2219 [2.2178-2.2610] | 2.1444 [2.1429-2.1556] | 0.9651 | 0.4501 -> 0.4663 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.3057 [3.2878-3.3215] | 3.0743 [3.0374-3.0846] | 0.9300 | 0.3025 -> 0.3253 | 88.75 -> 88.52 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 2.8783 [2.8718-2.9057] | 2.6388 [2.6350-2.6827] | 0.9168 | 0.3474 -> 0.3790 | 88.81 -> 88.57 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 2.8238 [2.8220-2.8729] | 2.5921 [2.5921-2.5961] | 0.9179 | 0.3541 -> 0.3858 | 64.75 -> 64.52 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.4310 [2.4280-2.9195] | 2.1624 [2.1434-2.2866] | 0.8895 | 0.4113 -> 0.4625 | 64.81 -> 64.57 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.6319 [71.7445-72.9019] | 73.6632 [72.5567-74.7700] | 1.0142 |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.6284 [6.6112-7.6354] | 6.6199 [6.6110-7.7024] | 0.9987 |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 70.4442 [70.1696-71.3307] | 71.2800 [71.2410-72.1431] | 1.0119 |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.5724 [3.5705-3.5819] | 3.6023 [3.5794-3.6533] | 1.0083 | 0.2799 -> 0.2776 | 1362.90 -> 1362.90 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.2348 [1.2226-1.2552] | 1.2164 [1.1878-1.2245] | 0.9851 | 0.8098 -> 0.8221 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.5167 [2.4988-2.5228] | 2.4207 [2.4076-2.4488] | 0.9619 | 0.3973 -> 0.4131 | 451.04 -> 448.17 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 1.9556 [1.9472-1.9893] | 1.8569 [1.8452-1.8627] | 0.9495 | 0.5113 -> 0.5385 | 451.30 -> 448.43 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.3556 [2.3303-2.3947] | 2.2716 [2.2617-2.2916] | 0.9643 | 0.4245 -> 0.4402 | 427.04 -> 424.17 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 1.7805 [1.7610-1.8728] | 1.6768 [1.6458-1.7235] | 0.9418 | 0.5616 -> 0.5964 | 427.30 -> 424.43 |
