| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | Ranges | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---|---:|---:|
| numeric 1 MiB | strings | fastcsv | ingest | 3.3995 [3.3926-3.7167] | 3.4086 [3.4004-4.1399] | 1.0027 | overlap | 0.2942 -> 0.2934 | 322.66 -> 322.66 |
| numeric 1 MiB | strings | jackson | ingest | 4.9849 [4.9280-5.1193] | 4.9861 [4.9805-5.0041] | 1.0002 | overlap | 0.2006 -> 0.2006 | 256.04 -> 256.04 |
| numeric 1 MiB | strings | kernel-native | ingest | 2.2915 [2.2821-2.3108] | 2.2823 [2.2726-2.2839] | 0.9960 | overlap | 0.4364 -> 0.4381 | 256.01 -> 256.01 |
| numeric 1 MiB | strings | truffledata-batch | ingest | 4.0565 [3.9096-4.2242] | 4.1387 [4.0843-4.4823] | 1.0203 | overlap | 0.2465 -> 0.2416 | 404.30 -> 404.30 |
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 3.6922 [3.6455-5.9495] | 3.6609 [3.5926-3.7596] | 0.9915 | overlap | 0.2708 -> 0.2732 | 404.36 -> 404.36 |
| numeric 1 MiB | strings | truffledata-batch-owned | ingest | 3.5641 [3.5373-3.5700] | 3.6032 [3.5613-3.6288] | 1.0110 | overlap | 0.2806 -> 0.2775 | 352.49 -> 352.49 |
| numeric 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.1573 [3.1272-3.3383] | 3.2045 [3.1823-3.2498] | 1.0149 | overlap | 0.3167 -> 0.3121 | 352.55 -> 352.55 |
| numeric 1 MiB | typed | fastcsv | ingest | 9.6169 [9.1464-9.8514] | 9.3209 [9.2398-9.6833] | 0.9692 | overlap | 0.1040 -> 0.1073 | 442.67 -> 442.67 |
| numeric 1 MiB | typed | kernel-native | ingest | 1.8710 [1.8147-1.8988] | 1.8186 [1.8139-1.8413] | 0.9719 | overlap | 0.5345 -> 0.5499 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 2.8526 [2.8429-2.9589] | 2.8370 [2.8202-2.8572] | 0.9945 | overlap | 0.3506 -> 0.3525 | 88.51 -> 88.51 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 2.4306 [2.4214-2.5274] | 2.4142 [2.4084-2.4637] | 0.9933 | overlap | 0.4114 -> 0.4142 | 88.57 -> 88.57 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 2.4115 [2.3938-2.5249] | 2.3983 [2.3908-2.5172] | 0.9945 | overlap | 0.4147 -> 0.4170 | 64.51 -> 64.51 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 1.9697 [1.9642-1.9811] | 1.9443 [1.9400-1.9557] | 0.9871 | separate | 0.5077 -> 0.5143 | 64.57 -> 64.57 |
| numeric 1 MiB | typed-reads | batch-fastcsv | interpret | 42.7815 [42.5339-43.7677] | 43.0053 [42.6229-43.7416] | 1.0052 | overlap |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | batch-fastcsv | peak | 10.5456 [10.4887-10.5589] | 10.6233 [10.5300-10.6299] | 1.0074 | overlap |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | batch-fastcsv | read-aot | 41.9780 [41.5734-42.0556] | 41.7234 [41.2535-41.8082] | 0.9939 | overlap |  | 0.00 -> 0.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.2996 [71.9739-73.4742] | 73.7323 [71.7289-73.8064] | 1.0198 | overlap |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.5749 [6.5579-7.6976] | 6.5764 [6.5548-7.6153] | 1.0002 | overlap |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 71.2135 [70.9798-74.2348] | 72.5588 [71.2280-73.1347] | 1.0189 | overlap |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | fastcsv | ingest | 3.1874 [3.1727-3.1916] | 3.2006 [3.1835-3.2518] | 1.0041 | overlap | 0.3137 -> 0.3124 | 995.90 -> 995.90 |
| mixed 1 MiB | strings | jackson | ingest | 4.4747 [4.4512-4.4974] | 4.4725 [4.4399-4.4985] | 0.9995 | overlap | 0.2235 -> 0.2236 | 856.35 -> 856.35 |
| mixed 1 MiB | strings | kernel-native | ingest | 2.3366 [2.3271-2.3461] | 2.3153 [2.3026-2.3289] | 0.9909 | overlap | 0.4280 -> 0.4319 | 898.57 -> 898.57 |
| mixed 1 MiB | strings | truffledata-batch | ingest | 3.7001 [3.6921-3.7106] | 3.6755 [3.6565-3.7177] | 0.9933 | overlap | 0.2703 -> 0.2721 | 1362.60 -> 1362.60 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.1657 [3.1040-6.0284] | 3.2149 [3.1222-5.0855] | 1.0156 | overlap | 0.3159 -> 0.3110 | 1362.87 -> 1362.87 |
| mixed 1 MiB | strings | truffledata-batch-owned | ingest | 3.2016 [3.1699-3.2102] | 3.2024 [3.1939-3.2277] | 1.0002 | overlap | 0.3123 -> 0.3123 | 1132.91 -> 1132.91 |
| mixed 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.6110 [2.6028-2.6346] | 2.6528 [2.6508-2.6637] | 1.0160 | separate | 0.3830 -> 0.3770 | 1133.18 -> 1133.18 |
| mixed 1 MiB | typed | fastcsv | ingest | 5.9440 [5.7292-6.4369] | 5.9615 [5.7124-6.2862] | 1.0029 | overlap | 0.1682 -> 0.1677 | 1239.09 -> 1239.09 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.0620 [1.0483-1.0837] | 1.0576 [1.0498-1.0605] | 0.9958 | overlap | 0.9416 -> 0.9456 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.3184 [2.3014-2.3648] | 2.2807 [2.2714-2.3609] | 0.9838 | overlap | 0.4313 -> 0.4385 | 448.14 -> 448.14 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 1.7612 [1.7572-1.8107] | 1.7636 [1.7385-1.7740] | 1.0014 | overlap | 0.5678 -> 0.5670 | 448.40 -> 448.40 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.1575 [2.1284-2.1724] | 2.1483 [2.1248-2.1491] | 0.9958 | overlap | 0.4635 -> 0.4655 | 424.14 -> 424.14 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 1.5690 [1.5604-1.6033] | 1.5872 [1.5612-1.5876] | 1.0116 | overlap | 0.6374 -> 0.6300 | 424.40 -> 424.40 |
| numeric 128 MiB | strings | fastcsv | ingest | 3.3543 [3.3444-3.3812] | 3.3866 [3.3630-3.5034] | 1.0096 | overlap | 0.2981 -> 0.2953 | 320.61 -> 320.61 |
| numeric 128 MiB | strings | jackson | ingest | 5.0206 [4.9655-5.1710] | 5.0368 [4.9995-5.0435] | 1.0032 | overlap | 0.1992 -> 0.1985 | 256.00 -> 256.00 |
| numeric 128 MiB | strings | kernel-native | ingest | 2.2798 [2.2366-2.3452] | 2.3102 [2.2651-2.3131] | 1.0134 | overlap | 0.4386 -> 0.4329 | 256.00 -> 256.00 |
| numeric 128 MiB | strings | truffledata-batch | ingest | 4.5429 [4.4121-4.5495] | 4.4541 [4.4311-4.6732] | 0.9805 | overlap | 0.2201 -> 0.2245 | 473.02 -> 473.02 |
| numeric 128 MiB | strings | truffledata-batch-native | ingest | 4.2789 [4.1282-4.2922] | 4.2123 [4.0553-11.3080] | 0.9844 | overlap | 0.2337 -> 0.2374 | 473.08 -> 473.08 |
| numeric 128 MiB | strings | truffledata-batch-owned | ingest | 3.9655 [3.9486-4.1498] | 4.0133 [3.9877-4.2237] | 1.0121 | overlap | 0.2522 -> 0.2492 | 419.11 -> 419.11 |
| numeric 128 MiB | strings | truffledata-batch-owned-native | ingest | 3.5911 [3.5824-3.8128] | 3.8263 [3.7307-3.9177] | 1.0655 | overlap | 0.2785 -> 0.2613 | 419.17 -> 419.17 |
| numeric 128 MiB | typed | fastcsv | ingest | 9.2146 [9.1466-9.5257] | 9.1713 [9.1712-9.3509] | 0.9953 | overlap | 0.1085 -> 0.1090 | 440.61 -> 440.61 |
| numeric 128 MiB | typed | kernel-native | ingest | 1.7664 [1.7620-1.7747] | 1.7596 [1.7578-1.7691] | 0.9961 | overlap | 0.5661 -> 0.5683 | 0.00 -> 0.00 |
| numeric 128 MiB | typed | truffledata-plan | ingest | 3.0833 [3.0672-3.1501] | 3.1179 [3.0393-3.3235] | 1.0112 | overlap | 0.3243 -> 0.3207 | 147.89 -> 147.89 |
| numeric 128 MiB | typed | truffledata-plan-native | ingest | 2.7731 [2.7219-4.7047] | 2.8699 [2.6957-2.9260] | 1.0349 | overlap | 0.3606 -> 0.3484 | 147.95 -> 147.95 |
| numeric 128 MiB | typed-build | truffledata-plan | ingest | 2.5591 [2.5523-3.1688] | 2.5529 [2.5508-2.6082] | 0.9976 | overlap | 0.3908 -> 0.3917 | 123.89 -> 123.89 |
| numeric 128 MiB | typed-build | truffledata-plan-native | ingest | 2.1926 [2.1832-2.2496] | 2.2214 [2.1817-2.2652] | 1.0131 | overlap | 0.4561 -> 0.4502 | 123.95 -> 123.95 |
| mixed 128 MiB | strings | fastcsv | ingest | 3.2172 [3.2140-3.3606] | 3.2263 [3.2261-3.3117] | 1.0028 | overlap | 0.3108 -> 0.3100 | 986.48 -> 986.48 |
| mixed 128 MiB | strings | jackson | ingest | 4.5705 [4.5134-6.1017] | 4.5091 [4.4987-4.6770] | 0.9866 | overlap | 0.2188 -> 0.2218 | 855.85 -> 855.85 |
| mixed 128 MiB | strings | kernel-native | ingest | 2.3331 [2.3272-3.0124] | 2.3454 [2.3398-2.5201] | 1.0053 | overlap | 0.4286 -> 0.4264 | 898.08 -> 898.08 |
| mixed 128 MiB | strings | truffledata-batch | ingest | 4.2959 [4.0696-4.4735] | 4.2329 [4.0618-4.4651] | 0.9853 | overlap | 0.2328 -> 0.2362 | 1362.14 -> 1362.14 |
| mixed 128 MiB | strings | truffledata-batch-native | ingest | 3.6027 [3.4338-6.2544] | 3.5340 [3.4630-3.6182] | 0.9809 | overlap | 0.2776 -> 0.2830 | 1362.40 -> 1362.40 |
| mixed 128 MiB | strings | truffledata-batch-owned | ingest | 3.8315 [3.7034-3.9329] | 3.9023 [3.7371-4.0291] | 1.0185 | overlap | 0.2610 -> 0.2563 | 1130.68 -> 1130.68 |
| mixed 128 MiB | strings | truffledata-batch-owned-native | ingest | 3.1647 [3.0816-3.5678] | 3.2414 [3.1654-12.0239] | 1.0242 | overlap | 0.3160 -> 0.3085 | 1130.94 -> 1130.94 |
| mixed 128 MiB | typed | fastcsv | ingest | 5.7694 [5.7091-6.3329] | 5.7514 [5.6911-6.2177] | 0.9969 | overlap | 0.1733 -> 0.1739 | 1229.67 -> 1229.67 |
| mixed 128 MiB | typed | kernel-native | ingest | 1.1748 [1.1631-1.2718] | 1.2196 [1.1600-1.2245] | 1.0381 | overlap | 0.8512 -> 0.8200 | 0.01 -> 0.01 |
| mixed 128 MiB | typed | truffledata-plan | ingest | 2.4551 [2.4258-2.4645] | 2.4167 [2.4084-2.4312] | 0.9844 | overlap | 0.4073 -> 0.4138 | 448.85 -> 448.85 |
| mixed 128 MiB | typed | truffledata-plan-native | ingest | 1.9374 [1.9307-1.9882] | 1.9984 [1.9115-2.3642] | 1.0315 | overlap | 0.5162 -> 0.5004 | 449.11 -> 449.11 |
| mixed 128 MiB | typed-build | truffledata-plan | ingest | 2.2514 [2.2389-2.2557] | 2.2652 [2.2306-2.2670] | 1.0061 | overlap | 0.4442 -> 0.4415 | 424.85 -> 424.85 |
| mixed 128 MiB | typed-build | truffledata-plan-native | ingest | 1.7160 [1.7098-1.7641] | 1.7115 [1.7098-1.7289] | 0.9974 | overlap | 0.5828 -> 0.5843 | 425.11 -> 425.11 |

| Fixture | Engine | Readback base (typed - typed-build) ns/B | Readback cand ns/B | Share of typed base -> cand |
|---|---|---:|---:|---:|
| mixed 1 MiB | truffledata-plan | 0.1609 | 0.1324 | 6.9% -> 5.8% |
| mixed 1 MiB | truffledata-plan-native | 0.1922 | 0.1764 | 10.9% -> 10.0% |
| mixed 128 MiB | truffledata-plan | 0.2037 | 0.1515 | 8.3% -> 6.3% |
| mixed 128 MiB | truffledata-plan-native | 0.2214 | 0.2868 | 11.4% -> 14.4% |
| numeric 1 MiB | truffledata-plan | 0.4411 | 0.4387 | 15.5% -> 15.5% |
| numeric 1 MiB | truffledata-plan-native | 0.4609 | 0.4699 | 19.0% -> 19.5% |
| numeric 128 MiB | truffledata-plan | 0.5241 | 0.5649 | 17.0% -> 18.1% |
| numeric 128 MiB | truffledata-plan-native | 0.5805 | 0.6486 | 20.9% -> 22.6% |
