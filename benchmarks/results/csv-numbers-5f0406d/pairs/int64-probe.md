| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | Ranges | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---|---:|---:|
| numeric 1 MiB | strings | fastcsv | ingest | 3.4140 [3.3482-3.4712] | 3.5468 [3.3679-15.0618] | 1.0389 | overlap | 0.2929 -> 0.2819 | 322.66 -> 322.66 |
| numeric 1 MiB | strings | jackson | ingest | 5.0374 [5.0257-5.5322] | 5.1592 [4.9940-5.3807] | 1.0242 | overlap | 0.1985 -> 0.1938 | 256.04 -> 256.04 |
| numeric 1 MiB | strings | kernel-native | ingest | 2.2918 [2.2894-2.4192] | 2.3646 [2.3330-2.4248] | 1.0318 | overlap | 0.4363 -> 0.4229 | 256.01 -> 256.01 |
| numeric 1 MiB | strings | truffledata-batch | ingest | 4.1310 [4.0129-4.2109] | 4.1521 [4.0240-4.2325] | 1.0051 | overlap | 0.2421 -> 0.2408 | 404.30 -> 404.30 |
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 3.6349 [3.6325-3.8743] | 3.6714 [3.6312-4.1140] | 1.0100 | overlap | 0.2751 -> 0.2724 | 404.36 -> 404.36 |
| numeric 1 MiB | strings | truffledata-batch-owned | ingest | 3.6132 [3.5632-3.6518] | 3.5804 [3.5749-4.0692] | 0.9909 | overlap | 0.2768 -> 0.2793 | 352.49 -> 352.49 |
| numeric 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.1792 [3.1547-3.3544] | 3.1864 [3.1287-3.4531] | 1.0023 | overlap | 0.3145 -> 0.3138 | 352.55 -> 352.55 |
| numeric 1 MiB | typed | fastcsv | ingest | 9.5868 [9.4042-9.8521] | 9.1760 [9.0963-9.7712] | 0.9571 | overlap | 0.1043 -> 0.1090 | 442.67 -> 442.67 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.1688 [2.1437-2.2688] | 2.0326 [2.0202-2.0401] | 0.9372 | separate | 0.4611 -> 0.4920 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.1381 [3.1064-3.1387] | 3.0447 [3.0387-3.0634] | 0.9702 | separate | 0.3187 -> 0.3284 | 88.51 -> 88.51 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 2.8117 [2.6543-2.8126] | 2.6249 [2.6187-2.8220] | 0.9336 | overlap | 0.3557 -> 0.3810 | 88.57 -> 88.57 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 2.6059 [2.6023-2.6480] | 2.5644 [2.5629-2.6222] | 0.9841 | overlap | 0.3837 -> 0.3900 | 64.51 -> 64.51 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.1936 [2.1834-5.7286] | 2.1742 [2.1366-2.1771] | 0.9911 | separate | 0.4559 -> 0.4599 | 64.57 -> 64.57 |
| numeric 1 MiB | typed-reads | batch-fastcsv | interpret | 43.8510 [42.9376-45.7323] | 42.6678 [42.6503-45.5681] | 0.9730 | overlap |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | batch-fastcsv | peak | 10.7879 [10.7113-12.6425] | 10.6986 [10.6566-11.3789] | 0.9917 | overlap |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | batch-fastcsv | read-aot | 41.4558 [41.2549-42.0360] | 41.2043 [41.1895-41.9219] | 0.9939 | overlap |  | 0.00 -> 0.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.1212 [72.1133-72.8683] | 72.7599 [72.7083-73.6689] | 1.0089 | overlap |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.5970 [6.5696-6.6027] | 6.6282 [6.6131-6.7123] | 1.0047 | separate |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 75.4405 [72.3503-79.2854] | 75.3693 [71.5042-80.8436] | 0.9991 | overlap |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | fastcsv | ingest | 3.2728 [3.2184-3.5359] | 3.6028 [3.4058-4.4092] | 1.1008 | overlap | 0.3055 -> 0.2776 | 995.90 -> 995.90 |
| mixed 1 MiB | strings | jackson | ingest | 5.0560 [4.5715-5.2108] | 4.5331 [4.4897-4.9402] | 0.8966 | overlap | 0.1978 -> 0.2206 | 856.35 -> 856.35 |
| mixed 1 MiB | strings | kernel-native | ingest | 2.3794 [2.2919-2.4839] | 2.2981 [2.2501-2.6639] | 0.9658 | overlap | 0.4203 -> 0.4352 | 898.57 -> 898.57 |
| mixed 1 MiB | strings | truffledata-batch | ingest | 4.4231 [3.7579-4.4234] | 3.9633 [3.7365-4.0877] | 0.8960 | overlap | 0.2261 -> 0.2523 | 1362.60 -> 1362.60 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.1124 [3.0938-3.4974] | 3.1454 [3.1240-4.5982] | 1.0106 | overlap | 0.3213 -> 0.3179 | 1362.87 -> 1362.87 |
| mixed 1 MiB | strings | truffledata-batch-owned | ingest | 3.3681 [3.2096-3.6605] | 3.2221 [3.2096-3.5615] | 0.9567 | overlap | 0.2969 -> 0.3104 | 1132.91 -> 1132.91 |
| mixed 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.6444 [2.6270-2.6485] | 2.6419 [2.6225-2.6554] | 0.9991 | overlap | 0.3782 -> 0.3785 | 1133.18 -> 1133.18 |
| mixed 1 MiB | typed | fastcsv | ingest | 5.8451 [5.7697-6.1581] | 6.0166 [5.8381-6.4429] | 1.0293 | overlap | 0.1711 -> 0.1662 | 1239.09 -> 1239.09 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.3702 [1.2235-1.4023] | 1.2980 [1.1432-1.4611] | 0.9473 | overlap | 0.7298 -> 0.7704 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.4991 [2.4909-2.6885] | 2.4711 [2.4476-2.7537] | 0.9888 | overlap | 0.4002 -> 0.4047 | 448.14 -> 448.14 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 2.0075 [1.8663-2.0663] | 1.9096 [1.8528-2.0119] | 0.9513 | overlap | 0.4981 -> 0.5237 | 448.40 -> 448.40 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.3193 [2.3085-2.3371] | 2.3035 [2.2723-2.5106] | 0.9932 | overlap | 0.4312 -> 0.4341 | 424.14 -> 424.14 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 1.6776 [1.6737-1.6913] | 1.6810 [1.6374-1.7186] | 1.0020 | overlap | 0.5961 -> 0.5949 | 424.40 -> 424.40 |
| numeric 128 MiB | strings | fastcsv | ingest | 3.4475 [3.3586-3.5034] | 3.8372 [3.7794-3.8640] | 1.1130 | separate | 0.2901 -> 0.2606 | 320.61 -> 320.61 |
| numeric 128 MiB | strings | jackson | ingest | 5.1581 [5.1280-5.2043] | 5.1786 [5.0698-5.1986] | 1.0040 | overlap | 0.1939 -> 0.1931 | 256.00 -> 256.00 |
| numeric 128 MiB | strings | kernel-native | ingest | 2.2748 [2.2660-2.2846] | 2.3282 [2.2630-2.4055] | 1.0235 | overlap | 0.4396 -> 0.4295 | 256.00 -> 256.00 |
| numeric 128 MiB | strings | truffledata-batch | ingest | 4.7601 [4.6686-10.4916] | 4.5441 [4.4971-4.5447] | 0.9546 | separate | 0.2101 -> 0.2201 | 473.02 -> 473.02 |
| numeric 128 MiB | strings | truffledata-batch-native | ingest | 4.2372 [4.1728-4.3489] | 4.4411 [4.0944-4.4590] | 1.0481 | overlap | 0.2360 -> 0.2252 | 473.08 -> 473.08 |
| numeric 128 MiB | strings | truffledata-batch-owned | ingest | 4.1738 [4.1337-4.2046] | 4.2396 [4.0570-4.3460] | 1.0158 | overlap | 0.2396 -> 0.2359 | 419.11 -> 419.11 |
| numeric 128 MiB | strings | truffledata-batch-owned-native | ingest | 3.6396 [3.6069-3.7037] | 3.6666 [3.6408-3.6694] | 1.0074 | overlap | 0.2748 -> 0.2727 | 419.17 -> 419.17 |
| numeric 128 MiB | typed | fastcsv | ingest | 9.4624 [9.4336-10.0449] | 9.5660 [9.4838-9.5941] | 1.0110 | overlap | 0.1057 -> 0.1045 | 440.61 -> 440.61 |
| numeric 128 MiB | typed | kernel-native | ingest | 2.1152 [2.0991-2.1171] | 1.9730 [1.9692-1.9806] | 0.9328 | separate | 0.4728 -> 0.5068 | 0.00 -> 0.00 |
| numeric 128 MiB | typed | truffledata-plan | ingest | 3.4204 [3.3765-3.6500] | 3.3671 [3.3235-6.4904] | 0.9844 | overlap | 0.2924 -> 0.2970 | 147.89 -> 147.89 |
| numeric 128 MiB | typed | truffledata-plan-native | ingest | 3.0230 [2.9750-5.9365] | 2.9959 [2.9533-3.0218] | 0.9910 | overlap | 0.3308 -> 0.3338 | 147.95 -> 147.95 |
| numeric 128 MiB | typed-build | truffledata-plan | ingest | 2.8060 [2.7824-2.8406] | 2.7944 [2.7585-2.8274] | 0.9959 | overlap | 0.3564 -> 0.3579 | 123.89 -> 123.89 |
| numeric 128 MiB | typed-build | truffledata-plan-native | ingest | 2.4689 [2.4604-2.5484] | 2.3925 [2.3828-2.4145] | 0.9690 | separate | 0.4050 -> 0.4180 | 123.95 -> 123.95 |
| mixed 128 MiB | strings | fastcsv | ingest | 3.4911 [3.2133-3.5214] | 3.5190 [3.3844-3.7037] | 1.0080 | overlap | 0.2864 -> 0.2842 | 986.48 -> 986.48 |
| mixed 128 MiB | strings | jackson | ingest | 4.6381 [4.5547-4.8468] | 4.6068 [4.5728-5.1377] | 0.9932 | overlap | 0.2156 -> 0.2171 | 855.85 -> 855.85 |
| mixed 128 MiB | strings | kernel-native | ingest | 2.3343 [2.3328-2.5859] | 2.4291 [2.2962-2.4474] | 1.0406 | overlap | 0.4284 -> 0.4117 | 898.08 -> 898.08 |
| mixed 128 MiB | strings | truffledata-batch | ingest | 4.5629 [4.3850-4.9936] | 4.1286 [4.0869-4.6410] | 0.9048 | overlap | 0.2192 -> 0.2422 | 1362.14 -> 1362.14 |
| mixed 128 MiB | strings | truffledata-batch-native | ingest | 3.7529 [3.6822-4.4632] | 3.8536 [3.6465-4.2136] | 1.0268 | overlap | 0.2665 -> 0.2595 | 1362.40 -> 1362.40 |
| mixed 128 MiB | strings | truffledata-batch-owned | ingest | 3.9194 [3.8467-4.0026] | 3.9215 [3.8659-4.0199] | 1.0006 | overlap | 0.2551 -> 0.2550 | 1130.68 -> 1130.68 |
| mixed 128 MiB | strings | truffledata-batch-owned-native | ingest | 3.4773 [3.1939-3.5876] | 3.3549 [3.1220-3.8928] | 0.9648 | overlap | 0.2876 -> 0.2981 | 1130.94 -> 1130.94 |
| mixed 128 MiB | typed | fastcsv | ingest | 5.8801 [5.8714-6.3488] | 6.2178 [5.9553-6.5073] | 1.0574 | overlap | 0.1701 -> 0.1608 | 1229.67 -> 1229.67 |
| mixed 128 MiB | typed | kernel-native | ingest | 1.3117 [1.2916-1.3738] | 1.2698 [1.2552-1.3309] | 0.9681 | overlap | 0.7624 -> 0.7875 | 0.01 -> 0.01 |
| mixed 128 MiB | typed | truffledata-plan | ingest | 2.5458 [2.5407-2.7913] | 2.5219 [2.5157-2.5520] | 0.9906 | overlap | 0.3928 -> 0.3965 | 448.85 -> 448.85 |
| mixed 128 MiB | typed | truffledata-plan-native | ingest | 2.0660 [2.0102-2.1367] | 2.0665 [2.0024-6.2587] | 1.0003 | overlap | 0.4840 -> 0.4839 | 449.11 -> 449.11 |
| mixed 128 MiB | typed-build | truffledata-plan | ingest | 2.3470 [2.3296-2.4761] | 2.4574 [2.3251-2.4628] | 1.0471 | overlap | 0.4261 -> 0.4069 | 424.85 -> 424.85 |
| mixed 128 MiB | typed-build | truffledata-plan-native | ingest | 1.9282 [1.8492-1.9285] | 1.8186 [1.8077-1.9830] | 0.9432 | overlap | 0.5186 -> 0.5499 | 425.11 -> 425.11 |
