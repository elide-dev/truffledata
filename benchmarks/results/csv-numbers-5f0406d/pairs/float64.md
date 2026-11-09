| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | Ranges | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---|---:|---:|
| numeric 1 MiB | strings | fastcsv | ingest | 3.3535 [3.3226-3.4240] | 3.4276 [3.3776-3.4673] | 1.0221 | overlap | 0.2982 -> 0.2917 | 322.66 -> 322.66 |
| numeric 1 MiB | strings | jackson | ingest | 4.9699 [4.9620-5.0783] | 5.0179 [4.9177-5.0472] | 1.0097 | overlap | 0.2012 -> 0.1993 | 256.04 -> 256.04 |
| numeric 1 MiB | strings | kernel-native | ingest | 2.2780 [2.2604-2.2927] | 2.2746 [2.2699-2.2862] | 0.9985 | overlap | 0.4390 -> 0.4396 | 256.01 -> 256.01 |
| numeric 1 MiB | strings | truffledata-batch | ingest | 4.0167 [4.0098-4.1247] | 4.0422 [4.0233-4.1145] | 1.0064 | overlap | 0.2490 -> 0.2474 | 404.30 -> 404.30 |
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 3.6049 [3.6005-3.6188] | 3.6127 [3.5803-3.6404] | 1.0022 | overlap | 0.2774 -> 0.2768 | 404.36 -> 404.36 |
| numeric 1 MiB | strings | truffledata-batch-owned | ingest | 3.5657 [3.5576-3.5795] | 3.5932 [3.5722-3.5976] | 1.0077 | overlap | 0.2805 -> 0.2783 | 352.49 -> 352.49 |
| numeric 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.1663 [3.1135-3.1892] | 3.1641 [3.1089-3.1797] | 0.9993 | overlap | 0.3158 -> 0.3160 | 352.55 -> 352.55 |
| numeric 1 MiB | typed | fastcsv | ingest | 9.3266 [9.3256-9.3324] | 9.2021 [9.1640-9.4763] | 0.9866 | overlap | 0.1072 -> 0.1087 | 442.67 -> 442.67 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.1530 [2.1455-2.1555] | 1.8191 [1.8181-1.8305] | 0.8449 | separate | 0.4645 -> 0.5497 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.0711 [3.0332-3.0764] | 2.8879 [2.8490-3.0434] | 0.9404 | overlap | 0.3256 -> 0.3463 | 88.51 -> 88.51 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 2.6337 [2.6248-2.6577] | 2.3935 [2.3809-2.4328] | 0.9088 | separate | 0.3797 -> 0.4178 | 88.57 -> 88.57 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 2.6319 [2.5993-2.6330] | 2.3860 [2.3779-2.3935] | 0.9066 | separate | 0.3799 -> 0.4191 | 64.51 -> 64.51 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.1586 [2.1400-2.1845] | 1.9499 [1.9312-1.9651] | 0.9033 | separate | 0.4633 -> 0.5128 | 64.57 -> 64.57 |
| numeric 1 MiB | typed-reads | batch-fastcsv | interpret | 42.4653 [42.2844-43.7793] | 43.3447 [42.5553-43.5490] | 1.0207 | overlap |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | batch-fastcsv | peak | 10.5298 [10.4990-10.6156] | 10.5363 [10.5211-10.5627] | 1.0006 | overlap |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | batch-fastcsv | read-aot | 41.4005 [41.3947-41.4142] | 41.3964 [41.1220-41.7685] | 0.9999 | overlap |  | 0.00 -> 0.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.7003 [72.4710-72.7888] | 72.5615 [72.5128-72.9773] | 0.9981 | overlap |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.5797 [6.5773-6.6401] | 6.6230 [6.6122-6.6302] | 1.0066 | overlap |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 71.8277 [71.2130-72.7757] | 72.0142 [71.4535-72.2053] | 1.0026 | overlap |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | fastcsv | ingest | 3.2065 [3.1896-47.8717] | 3.1765 [3.1719-3.1979] | 0.9906 | overlap | 0.3119 -> 0.3148 | 995.90 -> 995.90 |
| mixed 1 MiB | strings | jackson | ingest | 4.4725 [4.4422-4.4836] | 4.4657 [4.4641-4.5189] | 0.9985 | overlap | 0.2236 -> 0.2239 | 856.35 -> 856.35 |
| mixed 1 MiB | strings | kernel-native | ingest | 2.3325 [2.3253-2.3545] | 2.2793 [2.2703-2.2961] | 0.9772 | separate | 0.4287 -> 0.4387 | 898.57 -> 898.57 |
| mixed 1 MiB | strings | truffledata-batch | ingest | 3.6752 [3.6443-3.6891] | 3.6557 [3.6529-3.6602] | 0.9947 | overlap | 0.2721 -> 0.2735 | 1362.60 -> 1362.60 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.0735 [3.0653-3.1228] | 3.1116 [3.1031-3.1137] | 1.0124 | overlap | 0.3254 -> 0.3214 | 1362.87 -> 1362.87 |
| mixed 1 MiB | strings | truffledata-batch-owned | ingest | 3.2155 [3.1861-3.2249] | 3.2022 [3.1883-3.2467] | 0.9958 | overlap | 0.3110 -> 0.3123 | 1132.91 -> 1132.91 |
| mixed 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.6373 [2.5997-2.7138] | 2.6254 [2.5922-2.7533] | 0.9955 | overlap | 0.3792 -> 0.3809 | 1133.18 -> 1133.18 |
| mixed 1 MiB | typed | fastcsv | ingest | 5.7333 [5.6814-5.7862] | 5.6631 [5.6501-5.7805] | 0.9877 | overlap | 0.1744 -> 0.1766 | 1239.09 -> 1239.09 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.1855 [1.1810-1.2382] | 1.0735 [1.0600-1.0989] | 0.9056 | separate | 0.8436 -> 0.9315 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.4284 [2.3738-2.4403] | 2.3242 [2.3121-2.3313] | 0.9571 | separate | 0.4118 -> 0.4303 | 448.14 -> 448.14 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 1.8404 [1.8075-1.8658] | 1.7520 [1.7370-1.7694] | 0.9520 | separate | 0.5434 -> 0.5708 | 448.40 -> 448.40 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.2331 [2.2121-2.2626] | 2.1714 [2.1611-2.1861] | 0.9723 | separate | 0.4478 -> 0.4605 | 424.14 -> 424.14 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 1.6515 [1.6222-1.6731] | 1.5865 [1.5629-1.5918] | 0.9607 | separate | 0.6055 -> 0.6303 | 424.40 -> 424.40 |
| numeric 128 MiB | strings | fastcsv | ingest | 3.3869 [3.3740-3.4383] | 3.4563 [3.3785-3.4577] | 1.0205 | overlap | 0.2953 -> 0.2893 | 320.61 -> 320.61 |
| numeric 128 MiB | strings | jackson | ingest | 5.0480 [5.0088-5.1068] | 5.0859 [5.0125-5.1925] | 1.0075 | overlap | 0.1981 -> 0.1966 | 256.00 -> 256.00 |
| numeric 128 MiB | strings | kernel-native | ingest | 2.2677 [2.2463-2.3220] | 2.2892 [2.2766-2.3142] | 1.0095 | overlap | 0.4410 -> 0.4368 | 256.00 -> 256.00 |
| numeric 128 MiB | strings | truffledata-batch | ingest | 4.5519 [4.5486-4.6211] | 4.5463 [4.5097-4.5708] | 0.9988 | overlap | 0.2197 -> 0.2200 | 473.02 -> 473.02 |
| numeric 128 MiB | strings | truffledata-batch-native | ingest | 4.2078 [4.1358-4.5116] | 4.1918 [4.1788-4.3055] | 0.9962 | overlap | 0.2377 -> 0.2386 | 473.08 -> 473.08 |
| numeric 128 MiB | strings | truffledata-batch-owned | ingest | 4.0857 [4.0714-4.2540] | 4.0532 [4.0499-4.2366] | 0.9921 | overlap | 0.2448 -> 0.2467 | 419.11 -> 419.11 |
| numeric 128 MiB | strings | truffledata-batch-owned-native | ingest | 3.6982 [3.6864-3.7475] | 3.6966 [3.6625-3.7580] | 0.9996 | overlap | 0.2704 -> 0.2705 | 419.17 -> 419.17 |
| numeric 128 MiB | typed | fastcsv | ingest | 9.3851 [9.2726-9.4226] | 9.2401 [9.1779-9.3290] | 0.9845 | overlap | 0.1066 -> 0.1082 | 440.61 -> 440.61 |
| numeric 128 MiB | typed | kernel-native | ingest | 2.1328 [2.1307-2.1552] | 1.7902 [1.7606-1.8279] | 0.8393 | separate | 0.4689 -> 0.5586 | 0.00 -> 0.00 |
| numeric 128 MiB | typed | truffledata-plan | ingest | 3.3828 [3.3826-3.6612] | 3.1766 [3.1409-4.2926] | 0.9390 | overlap | 0.2956 -> 0.3148 | 147.89 -> 147.89 |
| numeric 128 MiB | typed | truffledata-plan-native | ingest | 2.9759 [2.9431-5.3247] | 2.7608 [2.7495-2.8122] | 0.9277 | separate | 0.3360 -> 0.3622 | 147.95 -> 147.95 |
| numeric 128 MiB | typed-build | truffledata-plan | ingest | 2.8350 [2.7894-2.8474] | 2.6028 [2.5854-2.6223] | 0.9181 | separate | 0.3527 -> 0.3842 | 123.89 -> 123.89 |
| numeric 128 MiB | typed-build | truffledata-plan-native | ingest | 2.4361 [2.4141-2.4443] | 2.2361 [2.2358-2.2483] | 0.9179 | separate | 0.4105 -> 0.4472 | 123.95 -> 123.95 |
| mixed 128 MiB | strings | fastcsv | ingest | 3.2675 [3.2349-3.2932] | 3.2426 [3.1996-3.2521] | 0.9924 | overlap | 0.3060 -> 0.3084 | 986.48 -> 986.48 |
| mixed 128 MiB | strings | jackson | ingest | 4.5216 [4.5136-4.5800] | 4.6038 [4.5933-4.6176] | 1.0182 | separate | 0.2212 -> 0.2172 | 855.85 -> 855.85 |
| mixed 128 MiB | strings | kernel-native | ingest | 2.3421 [2.3142-2.3479] | 2.2812 [2.2763-2.2888] | 0.9740 | separate | 0.4270 -> 0.4384 | 898.08 -> 898.08 |
| mixed 128 MiB | strings | truffledata-batch | ingest | 4.1883 [4.1238-4.3806] | 4.2194 [4.1106-4.4033] | 1.0074 | overlap | 0.2388 -> 0.2370 | 1362.14 -> 1362.14 |
| mixed 128 MiB | strings | truffledata-batch-native | ingest | 3.6531 [3.6293-3.7447] | 3.8083 [3.7627-3.9527] | 1.0425 | separate | 0.2737 -> 0.2626 | 1362.40 -> 1362.40 |
| mixed 128 MiB | strings | truffledata-batch-owned | ingest | 3.9761 [3.8155-4.1388] | 3.9393 [3.7871-4.0514] | 0.9907 | overlap | 0.2515 -> 0.2539 | 1130.68 -> 1130.68 |
| mixed 128 MiB | strings | truffledata-batch-owned-native | ingest | 3.3090 [3.2180-3.5312] | 3.2471 [3.1090-4.3043] | 0.9813 | overlap | 0.3022 -> 0.3080 | 1130.94 -> 1130.94 |
| mixed 128 MiB | typed | fastcsv | ingest | 5.8231 [5.7952-5.8597] | 5.8019 [5.7650-5.8169] | 0.9964 | overlap | 0.1717 -> 0.1724 | 1229.67 -> 1229.67 |
| mixed 128 MiB | typed | kernel-native | ingest | 1.2918 [1.2876-1.3280] | 1.1754 [1.1659-1.1756] | 0.9099 | separate | 0.7741 -> 0.8508 | 0.01 -> 0.01 |
| mixed 128 MiB | typed | truffledata-plan | ingest | 2.5306 [2.5299-2.5517] | 2.4444 [2.4404-2.4551] | 0.9659 | separate | 0.3952 -> 0.4091 | 448.85 -> 448.85 |
| mixed 128 MiB | typed | truffledata-plan-native | ingest | 2.0247 [2.0123-6.1038] | 1.9396 [1.9292-2.0109] | 0.9580 | separate | 0.4939 -> 0.5156 | 449.11 -> 449.11 |
| mixed 128 MiB | typed-build | truffledata-plan | ingest | 2.3564 [2.3502-2.3623] | 2.2665 [2.2522-2.2829] | 0.9618 | separate | 0.4244 -> 0.4412 | 424.85 -> 424.85 |
| mixed 128 MiB | typed-build | truffledata-plan-native | ingest | 1.8245 [1.8155-1.8263] | 1.7405 [1.7382-1.7937] | 0.9540 | separate | 0.5481 -> 0.5746 | 425.11 -> 425.11 |
