# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as retained index/header bytes. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 399.2 | 558.5 (1.40×) | 1101.6 (2.76×) | 5374.0 (13.46×) |
| sparse | resident | 265.4 | 356.2 (1.34×) | 876.0 (3.30×) | 1305.9 (4.92×) |
| sparse | heap | 256.4 | 355.6 (1.39×) | 930.1 (3.63×) | 1452.5 (5.67×) |
| sparse | detach | 705.1 | 1114.9 (1.58×) | 2645.4 (3.75×) | 2945.3 (4.18×) |
| sparse | first | 10.2 | 10.6 (1.03×) | 144.6 (14.13×) | 31.5 (3.08×) |
| sparse | interpret | 12.8 | 12.6 (0.98×) | 34.4 (2.69×) | 36.1 (2.83×) |
| sparse | peak | 10.0 | 7.7 (0.77×) | 27.2 (2.72×) | 19.3 (1.93×) |
| full | decode | 289.7 | 364.9 (1.26×) | 724.0 (2.50×) | 1340.0 (4.63×) |
| full | resident | 460.7 | 597.1 (1.30×) | 1710.2 (3.71×) | 2074.7 (4.50×) |
| full | heap | 305.9 | 378.6 (1.24×) | 1262.0 (4.13×) | 1622.4 (5.30×) |
| full | detach | 504.9 | 612.3 (1.21×) | 4343.0 (8.60×) | 7267.4 (14.39×) |
| full | first | 29.2 | 33.1 (1.13×) | 430.6 (14.73×) | 64.4 (2.20×) |
| full | interpret | 32.5 | 55.0 (1.69×) | 195.0 (6.01×) | 113.1 (3.48×) |
| full | peak | 22.8 | 19.0 (0.83×) | 101.8 (4.46×) | 44.7 (1.96×) |
| holdout | resident | 1012.5 | 1468.8 (1.45×) | 3383.9 (3.34×) | 4596.2 (4.54×) |
| holdout | peak | 52.8 | 50.1 (0.95×) | 271.3 (5.14×) | 132.6 (2.51×) |
| large | decode | 142808.2 | 142136.0 (1.00×) | 292612.3 (2.05×) | 364617.2 (2.55×) |
| large | resident | 84775.7 | 156881.3 (1.85×) | 334390.2 (3.94×) | 425094.8 (5.01×) |
| large | peak | 11363.8 | 11228.6 (0.99×) | 57393.5 (5.05×) | 29707.5 (2.61×) |

## baseline: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 413.9 | 552.7 (1.34×) | 1227.1 (2.96×) | 1831.1 (4.42×) |
| sparse | resident | 321.0 | 378.2 (1.18×) | 1080.9 (3.37×) | 1302.3 (4.06×) |
| sparse | heap | 268.8 | 356.5 (1.33×) | 1169.7 (4.35×) | 1385.2 (5.15×) |
| sparse | detach | 363.0 | 970.5 (2.67×) | 2333.6 (6.43×) | 2888.2 (7.96×) |
| sparse | first | 10.1 | 11.0 (1.09×) | 165.3 (16.34×) | 30.3 (2.99×) |
| sparse | interpret | 7.3 | 14.7 (2.00×) | 32.4 (4.42×) | 37.4 (5.11×) |
| sparse | peak | 8.6 | 8.5 (0.98×) | 24.8 (2.87×) | 16.7 (1.93×) |
| full | decode | 304.7 | 351.4 (1.15×) | 900.7 (2.96×) | 1204.6 (3.95×) |
| full | resident | 442.4 | 601.2 (1.36×) | 2316.8 (5.24×) | 2381.3 (5.38×) |
| full | heap | 304.2 | 380.1 (1.25×) | 1762.7 (5.79×) | 1515.6 (4.98×) |
| full | detach | 677.5 | 604.8 (0.89×) | 12445.7 (18.37×) | 7358.6 (10.86×) |
| full | first | 31.7 | 33.2 (1.05×) | 569.4 (17.98×) | 63.7 (2.01×) |
| full | interpret | 29.3 | 46.9 (1.60×) | 192.0 (6.56×) | 112.4 (3.84×) |
| full | peak | 21.4 | 19.4 (0.90×) | 81.4 (3.80×) | 40.8 (1.90×) |
| holdout | resident | 1015.1 | 1489.0 (1.47×) | 4015.1 (3.96×) | 4306.2 (4.24×) |
| holdout | peak | 72.8 | 50.9 (0.70×) | 256.7 (3.53×) | 131.6 (1.81×) |
| large | decode | 90897.3 | 139407.6 (1.53×) | 227469.2 (2.50×) | 374820.5 (4.12×) |
| large | resident | 243357.1 | 156089.5 (0.64×) | 494859.8 (2.03×) | 428831.9 (1.76×) |
| large | peak | 10578.6 | 12796.3 (1.21×) | 58086.0 (5.49×) | 29573.2 (2.80×) |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row |
|---|---|---|---|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 399.241 (395.982–530.072) | 1.00 | 375.5 | 1391.50 | 0.0 |
| candidate | sparse | decode | generated-owned | 558.549 (548.913–4134.594) | 1.40 | 268.4 | 1837.25 | 0.0 |
| candidate | sparse | decode | native-view | 1101.623 (1019.880–1138.925) | 2.76 | 136.1 | 336.00 | 1165.5 |
| candidate | sparse | decode | native-eager | 5373.988 (1821.611–5906.096) | 13.46 | 27.9 | 1788.00 | 1488.0 |
| candidate | sparse | resident | stock | 265.379 (252.433–362.539) | 1.00 | 564.9 | 1393.00 | 0.0 |
| candidate | sparse | resident | generated-owned | 356.164 (349.314–391.586) | 1.34 | 420.9 | 1838.75 | 0.0 |
| candidate | sparse | resident | native-view | 875.998 (862.398–965.366) | 3.30 | 171.1 | 1017.25 | 1165.5 |
| candidate | sparse | resident | native-eager | 1305.888 (1266.467–1400.596) | 4.92 | 114.8 | 1967.75 | 1488.0 |
| candidate | sparse | heap | stock | 256.360 (253.739–317.149) | 1.00 | 584.7 | 1393.00 | 0.0 |
| candidate | sparse | heap | generated-owned | 355.572 (353.445–364.221) | 1.39 | 421.6 | 1838.75 | 0.0 |
| candidate | sparse | heap | native-view | 930.112 (919.396–945.013) | 3.63 | 161.2 | 1345.25 | 1165.5 |
| candidate | sparse | heap | native-eager | 1452.471 (1352.845–1637.207) | 5.67 | 103.2 | 2319.75 | 1488.0 |
| candidate | sparse | detach | stock | 705.103 (322.331–729.505) | 1.00 | 212.6 | 1393.00 | 0.0 |
| candidate | sparse | detach | generated-owned | 1114.947 (1083.104–452440.429) | 1.58 | 134.5 | 1838.75 | 0.0 |
| candidate | sparse | detach | native-view | 2645.394 (2438.382–2654.163) | 3.75 | 56.7 | 4494.25 | 1165.5 |
| candidate | sparse | detach | native-eager | 2945.325 (2833.529–3153.225) | 4.18 | 50.9 | 5946.25 | 1488.0 |
| candidate | sparse | first | stock | 10.229 (9.848–11.732) | 1.00 | 14654.7 | 1.50 | 0.0 |
| candidate | sparse | first | generated-owned | 10.554 (10.302–11.260) | 1.03 | 14203.5 | 1.50 | 0.0 |
| candidate | sparse | first | native-view | 144.574 (144.184–152.389) | 14.13 | 1036.9 | 681.25 | 1165.5 |
| candidate | sparse | first | native-eager | 31.525 (29.838–38.162) | 3.08 | 4755.1 | 1.50 | 1488.0 |
| candidate | sparse | interpret | stock | 12.764 (7.725–32.326) | 1.00 | 11744.3 | 1.50 | 0.0 |
| candidate | sparse | interpret | generated-owned | 12.551 (12.470–13.294) | 0.98 | 11943.4 | 1.50 | 0.0 |
| candidate | sparse | interpret | native-view | 34.393 (33.307–37.821) | 2.69 | 4358.6 | 1.50 | 1165.5 |
| candidate | sparse | interpret | native-eager | 36.075 (27.023–38.311) | 2.83 | 4155.4 | 1.50 | 1488.0 |
| candidate | sparse | peak | stock | 10.000 (9.116–10.479) | 1.00 | 14990.1 | 1.50 | 0.0 |
| candidate | sparse | peak | generated-owned | 7.651 (7.650–9.090) | 0.77 | 19591.9 | 1.50 | 0.0 |
| candidate | sparse | peak | native-view | 27.225 (26.459–28.057) | 2.72 | 5506.1 | 1.50 | 1165.5 |
| candidate | sparse | peak | native-eager | 19.257 (17.005–22.617) | 1.93 | 7784.3 | 1.50 | 1488.0 |
| candidate | full | decode | stock | 289.670 (239.715–803.539) | 1.00 | 517.5 | 1391.50 | 0.0 |
| candidate | full | decode | generated-owned | 364.875 (356.377–365.896) | 1.26 | 410.8 | 1837.25 | 0.0 |
| candidate | full | decode | native-view | 724.016 (714.651–1132.134) | 2.50 | 207.0 | 336.00 | 1165.5 |
| candidate | full | decode | native-eager | 1340.030 (1210.797–1540.692) | 4.63 | 111.9 | 1788.00 | 1488.0 |
| candidate | full | resident | stock | 460.661 (439.095–1766.617) | 1.00 | 325.4 | 1393.00 | 0.0 |
| candidate | full | resident | generated-owned | 597.118 (596.386–620.697) | 1.30 | 251.0 | 1838.75 | 0.0 |
| candidate | full | resident | native-view | 1710.199 (1674.367–1735.315) | 3.71 | 87.7 | 1924.75 | 1165.5 |
| candidate | full | resident | native-eager | 2074.738 (2046.479–2164.250) | 4.50 | 72.3 | 2182.75 | 1488.0 |
| candidate | full | heap | stock | 305.940 (276.180–311.941) | 1.00 | 490.0 | 1393.00 | 0.0 |
| candidate | full | heap | generated-owned | 378.650 (376.418–436.753) | 1.24 | 395.9 | 1838.75 | 0.0 |
| candidate | full | heap | native-view | 1262.023 (1225.188–1379.202) | 4.13 | 118.8 | 2252.75 | 1165.5 |
| candidate | full | heap | native-eager | 1622.445 (1531.329–1739.211) | 5.30 | 92.4 | 2534.75 | 1488.0 |
| candidate | full | detach | stock | 504.900 (446.469–1163.408) | 1.00 | 296.9 | 1393.00 | 0.0 |
| candidate | full | detach | generated-owned | 612.347 (603.261–635.687) | 1.21 | 244.8 | 1838.75 | 0.0 |
| candidate | full | detach | native-view | 4342.958 (4342.473–4924.538) | 8.60 | 34.5 | 4494.25 | 1165.5 |
| candidate | full | detach | native-eager | 7267.405 (6407.018–8111.247) | 14.39 | 20.6 | 5946.25 | 1488.0 |
| candidate | full | first | stock | 29.225 (28.573–29.817) | 1.00 | 5129.4 | 1.50 | 0.0 |
| candidate | full | first | generated-owned | 33.111 (31.850–36.188) | 1.13 | 4527.4 | 1.50 | 0.0 |
| candidate | full | first | native-view | 430.569 (427.788–468.952) | 14.73 | 348.2 | 1588.75 | 1165.5 |
| candidate | full | first | native-eager | 64.379 (63.985–65.792) | 2.20 | 2328.5 | 1.50 | 1488.0 |
| candidate | full | interpret | stock | 32.463 (30.612–37.372) | 1.00 | 4617.7 | 1.50 | 0.0 |
| candidate | full | interpret | generated-owned | 54.968 (43.055–86.503) | 1.69 | 2727.1 | 1.50 | 0.0 |
| candidate | full | interpret | native-view | 194.991 (184.491–221.356) | 6.01 | 768.8 | 1.50 | 1165.5 |
| candidate | full | interpret | native-eager | 113.117 (112.275–113.616) | 3.48 | 1325.2 | 1.50 | 1488.0 |
| candidate | full | peak | stock | 22.810 (21.893–27.120) | 1.00 | 6571.9 | 1.50 | 0.0 |
| candidate | full | peak | generated-owned | 18.978 (18.875–21.569) | 0.83 | 7899.1 | 1.50 | 0.0 |
| candidate | full | peak | native-view | 101.766 (82.010–117.106) | 4.46 | 1473.1 | 1.50 | 1165.5 |
| candidate | full | peak | native-eager | 44.652 (40.560–58.786) | 1.96 | 3357.2 | 1.50 | 1488.0 |
| candidate | holdout | resident | stock | 1012.520 (995.555–1014.623) | 1.00 | 503.7 | 4706.75 | 0.0 |
| candidate | holdout | resident | generated-owned | 1468.834 (1465.851–1695.830) | 1.45 | 347.2 | 6343.25 | 0.0 |
| candidate | holdout | resident | native-view | 3383.888 (3280.533–3713.418) | 3.34 | 150.7 | 5893.00 | 3315.0 |
| candidate | holdout | resident | native-eager | 4596.242 (4136.197–9421.913) | 4.54 | 111.0 | 6678.25 | 5184.0 |
| candidate | holdout | peak | stock | 52.771 (52.764–53.721) | 1.00 | 9665.0 | 1.50 | 0.0 |
| candidate | holdout | peak | generated-owned | 50.085 (47.782–51.748) | 0.95 | 10183.3 | 1.50 | 0.0 |
| candidate | holdout | peak | native-view | 271.257 (267.200–299.584) | 5.14 | 1880.2 | 1.50 | 3315.0 |
| candidate | holdout | peak | native-eager | 132.627 (132.035–134.708) | 2.51 | 3845.6 | 1.50 | 5184.0 |
| candidate | large | decode | stock | 142808.209 (121790.953–175050.142) | 1.00 | 362.9 | 306067.00 | 0.0 |
| candidate | large | decode | generated-owned | 142136.010 (138845.792–144441.185) | 1.00 | 364.6 | 533143.25 | 0.0 |
| candidate | large | decode | native-view | 292612.335 (289632.077–305151.216) | 2.05 | 177.1 | 336.00 | 409153.5 |
| candidate | large | decode | native-eager | 364617.205 (360195.316–417535.418) | 2.55 | 142.1 | 461258.50 | 393216.0 |
| candidate | large | resident | stock | 84775.675 (71908.871–126391.738) | 1.00 | 611.3 | 306060.50 | 0.0 |
| candidate | large | resident | generated-owned | 156881.344 (148844.137–167396.727) | 1.85 | 330.3 | 533144.75 | 0.0 |
| candidate | large | resident | native-view | 334390.188 (314752.656–335589.219) | 3.94 | 155.0 | 437040.25 | 409153.5 |
| candidate | large | resident | native-eager | 425094.805 (422021.445–425285.898) | 5.01 | 121.9 | 588510.25 | 393216.0 |
| candidate | large | peak | stock | 11363.784 (10649.212–11610.292) | 1.00 | 4560.3 | 1.50 | 0.0 |
| candidate | large | peak | generated-owned | 11228.599 (10271.498–11983.544) | 0.99 | 4615.2 | 1.50 | 0.0 |
| candidate | large | peak | native-view | 57393.505 (55003.297–62320.694) | 5.05 | 902.9 | 1.50 | 409153.5 |
| candidate | large | peak | native-eager | 29707.487 (28772.293–30213.178) | 2.61 | 1744.4 | 1.50 | 393216.0 |
| baseline | sparse | decode | stock | 413.948 (396.515–1711.686) | 1.00 | 362.1 | 1391.50 | 0.0 |
| baseline | sparse | decode | generated-owned | 552.746 (551.453–558.665) | 1.34 | 271.2 | 1837.25 | 0.0 |
| baseline | sparse | decode | native-view | 1227.068 (1224.106–1231.509) | 2.96 | 122.2 | 448.00 | 1512.0 |
| baseline | sparse | decode | native-eager | 1831.128 (1828.901–2039.478) | 4.42 | 81.9 | 1780.00 | 1488.0 |
| baseline | sparse | resident | stock | 320.979 (262.796–368.421) | 1.00 | 467.0 | 1393.00 | 0.0 |
| baseline | sparse | resident | generated-owned | 378.227 (356.849–382.666) | 1.18 | 396.3 | 1838.75 | 0.0 |
| baseline | sparse | resident | native-view | 1080.860 (1069.170–1130.845) | 3.37 | 138.7 | 906.75 | 1512.0 |
| baseline | sparse | resident | native-eager | 1302.333 (1244.223–1329.203) | 4.06 | 115.1 | 1959.75 | 1488.0 |
| baseline | sparse | heap | stock | 268.791 (258.842–275.619) | 1.00 | 557.7 | 1393.00 | 0.0 |
| baseline | sparse | heap | generated-owned | 356.492 (351.955–362.434) | 1.33 | 420.5 | 1838.75 | 0.0 |
| baseline | sparse | heap | native-view | 1169.692 (1113.465–1186.644) | 4.35 | 128.2 | 1234.75 | 1512.0 |
| baseline | sparse | heap | native-eager | 1385.249 (1350.391–1508.484) | 5.15 | 108.2 | 2311.75 | 1488.0 |
| baseline | sparse | detach | stock | 363.027 (314.216–790.897) | 1.00 | 412.9 | 1393.00 | 0.0 |
| baseline | sparse | detach | generated-owned | 970.550 (485.061–1009.990) | 2.67 | 154.5 | 1838.75 | 0.0 |
| baseline | sparse | detach | native-view | 2333.602 (2315.407–2346.255) | 6.43 | 64.2 | 4606.25 | 1512.0 |
| baseline | sparse | detach | native-eager | 2888.211 (2816.809–2947.462) | 7.96 | 51.9 | 5938.25 | 1488.0 |
| baseline | sparse | first | stock | 10.114 (10.033–12.499) | 1.00 | 14822.3 | 1.50 | 0.0 |
| baseline | sparse | first | generated-owned | 10.980 (10.549–13.161) | 1.09 | 13653.1 | 1.50 | 0.0 |
| baseline | sparse | first | native-view | 165.267 (153.886–215.617) | 16.34 | 907.1 | 458.75 | 1512.0 |
| baseline | sparse | first | native-eager | 30.289 (29.188–31.102) | 2.99 | 4949.2 | 1.50 | 1488.0 |
| baseline | sparse | interpret | stock | 7.319 (7.310–7.928) | 1.00 | 20482.7 | 1.50 | 0.0 |
| baseline | sparse | interpret | generated-owned | 14.652 (14.531–14.731) | 2.00 | 10230.9 | 1.50 | 0.0 |
| baseline | sparse | interpret | native-view | 32.361 (31.654–36.544) | 4.42 | 4632.2 | 1.50 | 1512.0 |
| baseline | sparse | interpret | native-eager | 37.400 (27.756–39.698) | 5.11 | 4008.1 | 1.50 | 1488.0 |
| baseline | sparse | peak | stock | 8.638 (8.439–8.889) | 1.00 | 17354.7 | 1.50 | 0.0 |
| baseline | sparse | peak | generated-owned | 8.454 (7.760–8.584) | 0.98 | 17731.9 | 1.50 | 0.0 |
| baseline | sparse | peak | native-view | 24.824 (21.965–26.810) | 2.87 | 6038.8 | 1.50 | 1512.0 |
| baseline | sparse | peak | native-eager | 16.658 (15.111–17.980) | 1.93 | 8999.2 | 1.50 | 1488.0 |
| baseline | full | decode | stock | 304.747 (253.470–398.403) | 1.00 | 491.9 | 1391.50 | 0.0 |
| baseline | full | decode | generated-owned | 351.391 (346.370–593.547) | 1.15 | 426.6 | 1837.25 | 0.0 |
| baseline | full | decode | native-view | 900.709 (865.546–919.954) | 2.96 | 166.4 | 448.00 | 1512.0 |
| baseline | full | decode | native-eager | 1204.595 (1182.977–1260.347) | 3.95 | 124.4 | 1780.00 | 1488.0 |
| baseline | full | resident | stock | 442.448 (435.357–448.302) | 1.00 | 338.8 | 1393.00 | 0.0 |
| baseline | full | resident | generated-owned | 601.186 (596.306–700.042) | 1.36 | 249.4 | 1838.75 | 0.0 |
| baseline | full | resident | native-view | 2316.805 (2225.294–2379.023) | 5.24 | 64.7 | 1920.75 | 1512.0 |
| baseline | full | resident | native-eager | 2381.293 (2171.189–3302.608) | 5.38 | 63.0 | 2174.75 | 1488.0 |
| baseline | full | heap | stock | 304.233 (277.268–305.517) | 1.00 | 492.7 | 1393.00 | 0.0 |
| baseline | full | heap | generated-owned | 380.106 (375.676–431.567) | 1.25 | 394.4 | 1838.75 | 0.0 |
| baseline | full | heap | native-view | 1762.731 (1753.689–2166.379) | 5.79 | 85.0 | 2248.75 | 1512.0 |
| baseline | full | heap | native-eager | 1515.609 (1431.877–1603.288) | 4.98 | 98.9 | 2526.75 | 1488.0 |
| baseline | full | detach | stock | 677.545 (444.672–960.943) | 1.00 | 221.2 | 1393.00 | 0.0 |
| baseline | full | detach | generated-owned | 604.839 (596.723–612.368) | 0.89 | 247.8 | 1838.75 | 0.0 |
| baseline | full | detach | native-view | 12445.685 (5939.873–31329.214) | 18.37 | 12.0 | 4606.25 | 1512.0 |
| baseline | full | detach | native-eager | 7358.557 (6630.024–7545.770) | 10.86 | 20.4 | 5938.25 | 1488.0 |
| baseline | full | first | stock | 31.665 (31.160–32.559) | 1.00 | 4734.2 | 1.50 | 0.0 |
| baseline | full | first | generated-owned | 33.242 (32.905–36.213) | 1.05 | 4509.6 | 1.50 | 0.0 |
| baseline | full | first | native-view | 569.418 (562.255–569.444) | 17.98 | 263.3 | 1472.75 | 1512.0 |
| baseline | full | first | native-eager | 63.723 (62.705–64.079) | 2.01 | 2352.5 | 1.50 | 1488.0 |
| baseline | full | interpret | stock | 29.285 (26.889–32.921) | 1.00 | 5118.9 | 1.50 | 0.0 |
| baseline | full | interpret | generated-owned | 46.905 (43.005–63.138) | 1.60 | 3195.9 | 1.50 | 0.0 |
| baseline | full | interpret | native-view | 192.031 (178.300–246.651) | 6.56 | 780.6 | 1.50 | 1512.0 |
| baseline | full | interpret | native-eager | 112.448 (112.014–114.598) | 3.84 | 1333.1 | 1.50 | 1488.0 |
| baseline | full | peak | stock | 21.435 (20.727–33.606) | 1.00 | 6993.4 | 1.50 | 0.0 |
| baseline | full | peak | generated-owned | 19.379 (18.558–29.008) | 0.90 | 7735.5 | 1.50 | 0.0 |
| baseline | full | peak | native-view | 81.372 (79.978–87.256) | 3.80 | 1842.2 | 1.50 | 1512.0 |
| baseline | full | peak | native-eager | 40.833 (40.805–40.855) | 1.90 | 3671.2 | 1.50 | 1488.0 |
| baseline | holdout | resident | stock | 1015.089 (999.623–1225.695) | 1.00 | 502.4 | 4706.75 | 0.0 |
| baseline | holdout | resident | generated-owned | 1489.008 (1458.296–1586.882) | 1.47 | 342.5 | 6343.25 | 0.0 |
| baseline | holdout | resident | native-view | 4015.120 (4003.835–4059.018) | 3.96 | 127.0 | 5506.50 | 5208.0 |
| baseline | holdout | resident | native-eager | 4306.200 (4270.620–5137.867) | 4.24 | 118.4 | 6670.25 | 5184.0 |
| baseline | holdout | peak | stock | 72.797 (60.857–121.841) | 1.00 | 7006.2 | 1.50 | 0.0 |
| baseline | holdout | peak | generated-owned | 50.920 (50.064–52.925) | 0.70 | 10016.3 | 1.50 | 0.0 |
| baseline | holdout | peak | native-view | 256.745 (255.057–306.091) | 3.53 | 1986.5 | 1.50 | 5208.0 |
| baseline | holdout | peak | native-eager | 131.573 (130.412–164.622) | 1.81 | 3876.4 | 1.50 | 5184.0 |
| baseline | large | decode | stock | 90897.349 (87145.383–172177.586) | 1.00 | 570.1 | 306051.62 | 0.0 |
| baseline | large | decode | generated-owned | 139407.594 (137159.659–139417.363) | 1.53 | 371.7 | 533143.25 | 0.0 |
| baseline | large | decode | native-view | 227469.167 (208548.273–251558.625) | 2.50 | 227.8 | 448.00 | 393240.0 |
| baseline | large | decode | native-eager | 374820.524 (367842.580–403058.637) | 4.12 | 138.3 | 461250.50 | 393216.0 |
| baseline | large | resident | stock | 243357.143 (88122.090–249774.014) | 1.00 | 212.9 | 306068.50 | 0.0 |
| baseline | large | resident | generated-owned | 156089.516 (152718.393–178195.781) | 0.64 | 332.0 | 533144.75 | 0.0 |
| baseline | large | resident | native-view | 494859.777 (363455.694–720396.938) | 2.03 | 104.7 | 364502.75 | 393240.0 |
| baseline | large | resident | native-eager | 428831.914 (424807.582–448678.170) | 1.76 | 120.8 | 588502.25 | 393216.0 |
| baseline | large | peak | stock | 10578.595 (10430.186–10658.950) | 1.00 | 4898.8 | 1.50 | 0.0 |
| baseline | large | peak | generated-owned | 12796.280 (11539.316–15497.428) | 1.21 | 4049.8 | 1.50 | 0.0 |
| baseline | large | peak | native-view | 58085.999 (53469.005–59122.532) | 5.49 | 892.2 | 1.50 | 393240.0 |
| baseline | large | peak | native-eager | 29573.181 (27263.563–30454.827) | 2.80 | 1752.3 | 1.50 | 393216.0 |
