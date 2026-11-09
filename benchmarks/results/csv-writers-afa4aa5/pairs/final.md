| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | fastcsv | ingest | 3.3448 [3.3447-3.5105] | 3.3522 [3.3328-3.4417] | 1.0022 | 0.2990 -> 0.2983 | 322.66 -> 322.66 |
| numeric 1 MiB | strings | jackson | ingest | 4.9716 [4.9543-4.9942] | 5.0506 [4.9359-5.0738] | 1.0159 | 0.2011 -> 0.1980 | 256.04 -> 256.04 |
| numeric 1 MiB | strings | kernel-native | ingest | 2.2910 [2.2536-2.3122] | 2.2644 [2.2623-2.3414] | 0.9884 | 0.4365 -> 0.4416 | 256.01 -> 256.01 |
| numeric 1 MiB | strings | truffledata-batch | ingest | 4.7522 [4.6805-4.7714] | 4.0146 [4.0137-4.0836] | 0.8448 | 0.2104 -> 0.2491 | 404.30 -> 404.30 |
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3187 [4.3068-4.3248] | 3.5982 [3.5917-3.6159] | 0.8332 | 0.2316 -> 0.2779 | 404.36 -> 404.36 |
| numeric 1 MiB | strings | truffledata-batch-owned | ingest | 4.2667 [4.2591-6.7024] | 3.5924 [3.5557-4.1929] | 0.8420 | 0.2344 -> 0.2784 | 352.49 -> 352.49 |
| numeric 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.8375 [3.8321-3.8815] | 3.1485 [3.1309-3.1541] | 0.8205 | 0.2606 -> 0.3176 | 352.55 -> 352.55 |
| numeric 1 MiB | typed | fastcsv | ingest | 9.6368 [9.5761-9.7479] | 9.3119 [9.2889-9.3626] | 0.9663 | 0.1038 -> 0.1074 | 442.67 -> 442.67 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.2071 [2.2070-2.2648] | 2.1462 [2.1441-2.1472] | 0.9724 | 0.4531 -> 0.4659 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.5229 [3.5155-3.5665] | 3.0659 [3.0651-3.0806] | 0.8703 | 0.2839 -> 0.3262 | 88.52 -> 88.51 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 3.0935 [3.0864-3.1044] | 2.6291 [2.6252-2.6677] | 0.8499 | 0.3233 -> 0.3804 | 88.57 -> 88.57 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 3.0457 [3.0447-3.0556] | 2.6166 [2.6039-2.6200] | 0.8591 | 0.3283 -> 0.3822 | 64.52 -> 64.51 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.6472 [2.6407-2.7850] | 2.1773 [2.1725-2.1856] | 0.8225 | 0.3778 -> 0.4593 | 64.57 -> 64.57 |
| numeric 1 MiB | typed-reads | batch-fastcsv | interpret | 42.5464 [42.1923-43.0579] | 44.7723 [42.4317-44.8214] | 1.0523 |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | batch-fastcsv | peak | 10.7438 [10.4763-11.9590] | 10.5704 [10.4151-10.6276] | 0.9839 |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | batch-fastcsv | read-aot | 41.1312 [41.0582-41.6231] | 41.3714 [41.2411-42.4205] | 1.0058 |  | 0.00 -> 0.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.3399 [72.1747-72.3998] | 71.7728 [71.7042-72.9894] | 0.9922 |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.5600 [6.5537-6.6228] | 6.5743 [6.5511-6.5869] | 1.0022 |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 71.1843 [70.6662-71.2613] | 70.8958 [70.8411-72.1563] | 0.9959 |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | fastcsv | ingest | 3.1385 [3.1366-3.1928] | 3.1703 [3.1446-3.3659] | 1.0101 | 0.3186 -> 0.3154 | 995.90 -> 995.90 |
| mixed 1 MiB | strings | jackson | ingest | 4.5073 [4.4305-4.5155] | 4.5102 [4.4749-4.5126] | 1.0007 | 0.2219 -> 0.2217 | 856.35 -> 856.35 |
| mixed 1 MiB | strings | kernel-native | ingest | 2.3273 [2.2865-2.3517] | 2.2723 [2.2722-2.3011] | 0.9764 | 0.4297 -> 0.4401 | 898.57 -> 898.57 |
| mixed 1 MiB | strings | truffledata-batch | ingest | 4.1933 [4.1639-4.4398] | 3.6736 [3.6627-3.6867] | 0.8761 | 0.2385 -> 0.2722 | 1362.63 -> 1362.60 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.6539 [3.5733-3.6596] | 3.0808 [3.0601-3.1124] | 0.8431 | 0.2737 -> 0.3246 | 1362.90 -> 1362.87 |
| mixed 1 MiB | strings | truffledata-batch-owned | ingest | 3.7677 [3.6639-3.8281] | 3.2192 [3.1588-3.2224] | 0.8544 | 0.2654 -> 0.3106 | 1132.94 -> 1132.91 |
| mixed 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.2111 [3.1572-3.2286] | 2.6546 [2.6409-2.6834] | 0.8267 | 0.3114 -> 0.3767 | 1133.21 -> 1133.18 |
| mixed 1 MiB | typed | fastcsv | ingest | 5.7780 [5.7311-5.8475] | 5.7580 [5.7213-5.7752] | 0.9965 | 0.1731 -> 0.1737 | 1239.09 -> 1239.09 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.2514 [1.2408-1.3033] | 1.1973 [1.1820-1.2467] | 0.9568 | 0.7991 -> 0.8352 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.7306 [2.6805-2.9458] | 2.4302 [2.4096-2.4476] | 0.8900 | 0.3662 -> 0.4115 | 448.16 -> 448.14 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 2.0658 [2.0602-2.0834] | 1.8288 [1.8243-1.8539] | 0.8853 | 0.4841 -> 0.5468 | 448.43 -> 448.40 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.4834 [2.4684-2.5041] | 2.2205 [2.1920-2.2352] | 0.8941 | 0.4027 -> 0.4504 | 424.16 -> 424.14 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 1.8970 [1.8825-1.9308] | 1.6491 [1.6463-1.6648] | 0.8693 | 0.5271 -> 0.6064 | 424.43 -> 424.40 |
| quoted 1 MiB | strings | fastcsv | ingest | 1.4593 [1.4566-1.4775] | 1.4664 [1.4617-1.4872] | 1.0049 | 0.6853 -> 0.6819 | 850.86 -> 850.86 |
| quoted 1 MiB | strings | jackson | ingest | 3.0008 [2.9787-3.0178] | 2.9969 [2.9773-2.9992] | 0.9987 | 0.3332 -> 0.3337 | 768.85 -> 768.85 |
| quoted 1 MiB | strings | kernel-native | ingest | 0.6470 [0.6400-0.6519] | 0.6461 [0.6441-0.6528] | 0.9987 | 1.5457 -> 1.5477 | 768.52 -> 768.52 |
| quoted 1 MiB | strings | truffledata-batch | ingest | 1.5955 [1.5822-1.5957] | 1.5544 [1.5258-1.5643] | 0.9743 | 0.6268 -> 0.6433 | 1496.28 -> 1496.26 |
| quoted 1 MiB | strings | truffledata-batch-native | ingest | 1.1781 [1.1731-1.1851] | 1.1363 [1.1331-1.1620] | 0.9646 | 0.8488 -> 0.8800 | 1497.05 -> 1497.03 |
| quoted 1 MiB | strings | truffledata-batch-owned | ingest | 1.1346 [1.1219-1.1364] | 1.1005 [1.0897-1.1096] | 0.9699 | 0.8814 -> 0.9087 | 830.86 -> 830.84 |
| quoted 1 MiB | strings | truffledata-batch-owned-native | ingest | 0.7353 [0.7188-0.7400] | 0.6953 [0.6880-0.6968] | 0.9456 | 1.3600 -> 1.4382 | 831.62 -> 831.60 |
| escaped 1 MiB | strings | fastcsv | ingest | 2.8317 [2.8272-2.8420] | 2.8515 [2.8424-2.8590] | 1.0070 | 0.3531 -> 0.3507 | 344.10 -> 344.10 |
| escaped 1 MiB | strings | jackson | ingest | 2.6497 [2.5889-2.7493] | 2.6087 [2.5877-2.6092] | 0.9845 | 0.3774 -> 0.3833 | 284.26 -> 284.26 |
| escaped 1 MiB | strings | kernel-native | ingest | 1.8526 [1.8505-1.8702] | 1.8297 [1.8270-1.8545] | 0.9876 | 0.5398 -> 0.5465 | 284.15 -> 284.15 |
| escaped 1 MiB | strings | truffledata-batch | ingest | 2.7351 [2.6682-2.7561] | 2.6595 [2.6401-2.6955] | 0.9724 | 0.3656 -> 0.3760 | 594.20 -> 594.19 |
| escaped 1 MiB | strings | truffledata-batch-native | ingest | 2.3680 [2.3604-2.4786] | 2.3665 [2.3452-2.3706] | 0.9994 | 0.4223 -> 0.4226 | 594.46 -> 594.46 |
| escaped 1 MiB | strings | truffledata-batch-owned | ingest | 2.3223 [2.3180-2.3720] | 2.2956 [2.2782-9.5770] | 0.9885 | 0.4306 -> 0.4356 | 362.65 -> 362.65 |
| escaped 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.0764 [2.0741-2.0783] | 1.9813 [1.9711-2.0095] | 0.9542 | 0.4816 -> 0.5047 | 362.92 -> 362.91 |
| numeric 128 MiB | strings | fastcsv | ingest | 3.4022 [3.3824-3.4383] | 3.3600 [3.3498-3.4515] | 0.9876 | 0.2939 -> 0.2976 | 320.61 -> 320.61 |
| numeric 128 MiB | strings | jackson | ingest | 5.0036 [4.9563-5.0411] | 5.0220 [4.9954-5.1043] | 1.0037 | 0.1999 -> 0.1991 | 256.00 -> 256.00 |
| numeric 128 MiB | strings | kernel-native | ingest | 2.2526 [2.2469-2.2542] | 2.2619 [2.2542-2.2749] | 1.0041 | 0.4439 -> 0.4421 | 256.00 -> 256.00 |
| numeric 128 MiB | strings | truffledata-batch | ingest | 5.1009 [5.0708-5.1740] | 4.4898 [4.4878-4.5187] | 0.8802 | 0.1960 -> 0.2227 | 473.02 -> 473.02 |
| numeric 128 MiB | strings | truffledata-batch-native | ingest | 4.8645 [4.8212-14.5485] | 4.1097 [4.0337-10.4316] | 0.8448 | 0.2056 -> 0.2433 | 473.08 -> 473.08 |
| numeric 128 MiB | strings | truffledata-batch-owned | ingest | 4.7067 [4.6785-4.7664] | 3.9812 [3.9146-4.0342] | 0.8459 | 0.2125 -> 0.2512 | 419.11 -> 419.11 |
| numeric 128 MiB | strings | truffledata-batch-owned-native | ingest | 4.3262 [4.3115-4.3335] | 3.5621 [3.5393-3.5624] | 0.8234 | 0.2311 -> 0.2807 | 419.17 -> 419.17 |
| numeric 128 MiB | typed | fastcsv | ingest | 9.6471 [9.5613-9.7565] | 9.3230 [9.2770-9.3320] | 0.9664 | 0.1037 -> 0.1073 | 440.61 -> 440.61 |
| numeric 128 MiB | typed | kernel-native | ingest | 2.1506 [2.1450-2.1729] | 2.0849 [2.0817-2.1272] | 0.9695 | 0.4650 -> 0.4796 | 0.00 -> 0.00 |
| numeric 128 MiB | typed | truffledata-plan | ingest | 3.6791 [3.6764-7.3262] | 3.2478 [3.2372-5.1393] | 0.8828 | 0.2718 -> 0.3079 | 147.89 -> 147.89 |
| numeric 128 MiB | typed | truffledata-plan-native | ingest | 3.3174 [3.3004-5.6064] | 2.8925 [2.8669-2.9180] | 0.8719 | 0.3014 -> 0.3457 | 147.95 -> 147.95 |
| numeric 128 MiB | typed-build | truffledata-plan | ingest | 3.2727 [3.1779-3.2785] | 2.7679 [2.7377-2.8056] | 0.8458 | 0.3056 -> 0.3613 | 123.89 -> 123.89 |
| numeric 128 MiB | typed-build | truffledata-plan-native | ingest | 2.8701 [2.8220-2.9493] | 2.3727 [2.3557-2.3821] | 0.8267 | 0.3484 -> 0.4215 | 123.95 -> 123.95 |
| mixed 128 MiB | strings | fastcsv | ingest | 3.2342 [3.2340-3.2889] | 3.2340 [3.2194-3.2833] | 0.9999 | 0.3092 -> 0.3092 | 986.48 -> 986.48 |
| mixed 128 MiB | strings | jackson | ingest | 4.5898 [4.5341-4.6183] | 4.5478 [4.5463-4.6904] | 0.9908 | 0.2179 -> 0.2199 | 855.85 -> 855.85 |
| mixed 128 MiB | strings | kernel-native | ingest | 2.3512 [2.3352-2.3707] | 2.3209 [2.3157-2.3392] | 0.9871 | 0.4253 -> 0.4309 | 898.08 -> 898.08 |
| mixed 128 MiB | strings | truffledata-batch | ingest | 4.7129 [4.5798-4.7359] | 4.1856 [4.1548-4.2035] | 0.8881 | 0.2122 -> 0.2389 | 1362.14 -> 1362.14 |
| mixed 128 MiB | strings | truffledata-batch-native | ingest | 4.2592 [4.2180-4.3296] | 3.7837 [3.5777-3.8339] | 0.8884 | 0.2348 -> 0.2643 | 1362.40 -> 1362.40 |
| mixed 128 MiB | strings | truffledata-batch-owned | ingest | 4.4783 [4.3397-4.5665] | 3.7554 [3.7405-3.8069] | 0.8386 | 0.2233 -> 0.2663 | 1130.68 -> 1130.68 |
| mixed 128 MiB | strings | truffledata-batch-owned-native | ingest | 3.7498 [3.6267-3.7594] | 3.1057 [3.0226-3.8639] | 0.8282 | 0.2667 -> 0.3220 | 1130.94 -> 1130.94 |
| mixed 128 MiB | typed | fastcsv | ingest | 5.9099 [5.8952-5.9216] | 5.7864 [5.7784-5.8132] | 0.9791 | 0.1692 -> 0.1728 | 1229.67 -> 1229.67 |
| mixed 128 MiB | typed | kernel-native | ingest | 1.3250 [1.3245-1.3483] | 1.2865 [1.2790-1.2981] | 0.9709 | 0.7547 -> 0.7773 | 0.01 -> 0.01 |
| mixed 128 MiB | typed | truffledata-plan | ingest | 2.8148 [2.8097-2.8219] | 2.5100 [2.5065-2.5525] | 0.8917 | 0.3553 -> 0.3984 | 448.85 -> 448.85 |
| mixed 128 MiB | typed | truffledata-plan-native | ingest | 2.3041 [2.3028-2.3081] | 1.9999 [1.9980-2.0087] | 0.8680 | 0.4340 -> 0.5000 | 449.11 -> 449.11 |
| mixed 128 MiB | typed-build | truffledata-plan | ingest | 2.6399 [2.6364-2.6892] | 2.3390 [2.3337-2.3557] | 0.8860 | 0.3788 -> 0.4275 | 424.85 -> 424.85 |
| mixed 128 MiB | typed-build | truffledata-plan-native | ingest | 2.1268 [2.1167-2.1475] | 1.8133 [1.8086-1.8245] | 0.8526 | 0.4702 -> 0.5515 | 425.11 -> 425.11 |
| quoted 128 MiB | strings | fastcsv | ingest | 1.5139 [1.4692-1.5845] | 1.4876 [1.4805-1.4910] | 0.9826 | 0.6605 -> 0.6722 | 823.95 -> 823.95 |
| quoted 128 MiB | strings | jackson | ingest | 2.9781 [2.9710-3.0142] | 2.9871 [2.9815-3.0312] | 1.0030 | 0.3358 -> 0.3348 | 767.95 -> 767.95 |
| quoted 128 MiB | strings | kernel-native | ingest | 0.6900 [0.6798-0.6965] | 0.6850 [0.6779-0.6856] | 0.9927 | 1.4493 -> 1.4599 | 767.98 -> 767.98 |
| quoted 128 MiB | strings | truffledata-batch | ingest | 1.5632 [1.5562-1.5822] | 1.5347 [1.5130-1.8159] | 0.9818 | 0.6397 -> 0.6516 | 1494.50 -> 1494.50 |
| quoted 128 MiB | strings | truffledata-batch-native | ingest | 1.3078 [1.3039-1.3429] | 1.1853 [1.1787-1.6095] | 0.9063 | 0.7647 -> 0.8437 | 1495.24 -> 1495.24 |
| quoted 128 MiB | strings | truffledata-batch-owned | ingest | 1.1290 [1.1186-1.1474] | 1.0904 [1.0831-1.0918] | 0.9658 | 0.8857 -> 0.9171 | 827.55 -> 827.55 |
| quoted 128 MiB | strings | truffledata-batch-owned-native | ingest | 0.7636 [0.7594-0.7806] | 0.7328 [0.7291-0.7368] | 0.9596 | 1.3096 -> 1.3647 | 828.29 -> 828.29 |
| escaped 128 MiB | strings | fastcsv | ingest | 3.4802 [3.4773-3.4869] | 3.5310 [3.4666-3.6727] | 1.0146 | 0.2873 -> 0.2832 | 333.85 -> 333.85 |
| escaped 128 MiB | strings | jackson | ingest | 3.2499 [3.2320-3.4866] | 3.1800 [3.1446-3.1915] | 0.9785 | 0.3077 -> 0.3145 | 283.08 -> 283.08 |
| escaped 128 MiB | strings | kernel-native | ingest | 2.2845 [2.2704-2.4433] | 2.3542 [2.2721-2.3693] | 1.0305 | 0.4377 -> 0.4248 | 283.09 -> 283.09 |
| escaped 128 MiB | strings | truffledata-batch | ingest | 3.3209 [3.3069-11.1816] | 3.2987 [3.2868-3.5000] | 0.9933 | 0.3011 -> 0.3032 | 593.19 -> 593.19 |
| escaped 128 MiB | strings | truffledata-batch-native | ingest | 2.9739 [2.9524-2.9739] | 2.9205 [2.9062-2.9291] | 0.9821 | 0.3363 -> 0.3424 | 593.44 -> 593.44 |
| escaped 128 MiB | strings | truffledata-batch-owned | ingest | 2.8736 [2.8699-2.8930] | 2.8238 [2.8222-2.8452] | 0.9827 | 0.3480 -> 0.3541 | 360.80 -> 360.80 |
| escaped 128 MiB | strings | truffledata-batch-owned-native | ingest | 2.5563 [2.5303-2.5648] | 2.4853 [2.4703-2.4875] | 0.9722 | 0.3912 -> 0.4024 | 361.05 -> 361.05 |
