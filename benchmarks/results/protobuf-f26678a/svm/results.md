# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as retained index/header bytes. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 879.6 | 1030.4 (1.17×) | 945.0 (1.07×) | 2001.8 (2.28×) |
| sparse | resident | 935.6 | 970.2 (1.04×) | 1351.3 (1.44×) | 2951.0 (3.15×) |
| sparse | heap | 803.9 | 876.8 (1.09×) | 9413.0 (11.71×) | 1990.5 (2.48×) |
| sparse | detach | 1528.2 | 1791.0 (1.17×) | 11228.1 (7.35×) | 15173.7 (9.93×) |
| sparse | first | 15.3 | 13.0 (0.85×) | 158.2 (10.34×) | 25.5 (1.67×) |
| sparse | interpret | 9.7 | 41.0 (4.24×) | 64.0 (6.63×) | 58.6 (6.07×) |
| sparse | peak | 11.8 | 10.0 (0.85×) | 19.8 (1.68×) | 17.5 (1.48×) |
| full | decode | 781.4 | 855.9 (1.10×) | 1008.0 (1.29×) | 1772.4 (2.27×) |
| full | resident | 923.9 | 905.2 (0.98×) | 1670.7 (1.81×) | 2043.9 (2.21×) |
| full | heap | 856.1 | 878.0 (1.03×) | 1738.4 (2.03×) | 2152.7 (2.51×) |
| full | detach | 899.9 | 968.1 (1.08×) | 26487.7 (29.43×) | 7555.7 (8.40×) |
| full | first | 72.4 | 46.9 (0.65×) | 809.3 (11.18×) | 104.7 (1.45×) |
| full | interpret | 62.0 | 291.1 (4.70×) | 407.1 (6.57×) | 347.5 (5.61×) |
| full | peak | 110.1 | 45.2 (0.41×) | 212.5 (1.93×) | 127.1 (1.15×) |
| holdout | resident | 2146.6 | 2244.1 (1.05×) | 3655.0 (1.70×) | 4785.6 (2.23×) |
| holdout | peak | 138.2 | 52.7 (0.38×) | 187.0 (1.35×) | 145.0 (1.05×) |
| large | decode | 228560.3 | 270507.0 (1.18×) | 202403.3 (0.89×) | 553952.8 (2.42×) |
| large | resident | 269760.1 | 290281.1 (1.08×) | 443863.1 (1.65×) | 712064.0 (2.64×) |
| large | peak | 17258.6 | 6265.4 (0.36×) | 26855.3 (1.56×) | 19997.7 (1.16×) |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row |
|---|---|---|---|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 879.645 (853.065–925.230) | 1.00 | 170.4 | 1629.50 | 0.0 |
| candidate | sparse | decode | generated-owned | 1030.416 (998.562–2291.079) | 1.17 | 145.5 | 1607.50 | 0.0 |
| candidate | sparse | decode | native-view | 945.043 (939.436–1315.713) | 1.07 | 158.6 | 160.00 | 1512.0 |
| candidate | sparse | decode | native-eager | 2001.800 (1955.513–2089.144) | 2.28 | 74.9 | 1511.50 | 1488.0 |
| candidate | sparse | resident | stock | 935.624 (845.401–997.818) | 1.00 | 160.2 | 1630.50 | 0.0 |
| candidate | sparse | resident | generated-owned | 970.244 (916.551–1008.582) | 1.04 | 154.5 | 1608.50 | 0.0 |
| candidate | sparse | resident | native-view | 1351.340 (1111.650–1385.729) | 1.44 | 110.9 | 541.25 | 1512.0 |
| candidate | sparse | resident | native-eager | 2951.015 (2933.639–3308.236) | 3.15 | 50.8 | 1635.00 | 1488.0 |
| candidate | sparse | heap | stock | 803.946 (797.186–807.228) | 1.00 | 186.5 | 1630.50 | 0.0 |
| candidate | sparse | heap | generated-owned | 876.831 (875.356–883.792) | 1.09 | 171.0 | 1608.50 | 0.0 |
| candidate | sparse | heap | native-view | 9413.021 (1168.541–9584.805) | 11.71 | 15.9 | 749.25 | 1512.0 |
| candidate | sparse | heap | native-eager | 1990.517 (1990.347–1993.031) | 2.48 | 75.3 | 1843.00 | 1488.0 |
| candidate | sparse | detach | stock | 1528.173 (1507.535–1670.861) | 1.00 | 98.1 | 1630.50 | 0.0 |
| candidate | sparse | detach | generated-owned | 1791.011 (1738.305–1968.963) | 1.17 | 83.7 | 1608.50 | 0.0 |
| candidate | sparse | detach | native-view | 11228.085 (11058.499–12347.902) | 7.35 | 13.4 | 4035.75 | 1512.0 |
| candidate | sparse | detach | native-eager | 15173.652 (15162.771–15418.422) | 9.93 | 9.9 | 5387.25 | 1488.0 |
| candidate | sparse | first | stock | 15.297 (15.201–17.110) | 1.00 | 9800.0 | 1.00 | 0.0 |
| candidate | sparse | first | generated-owned | 12.951 (12.726–13.000) | 0.85 | 11575.0 | 1.00 | 0.0 |
| candidate | sparse | first | native-view | 158.172 (156.986–159.328) | 10.34 | 947.7 | 381.25 | 1512.0 |
| candidate | sparse | first | native-eager | 25.499 (24.010–26.773) | 1.67 | 5879.0 | 1.00 | 1488.0 |
| candidate | sparse | interpret | stock | 9.663 (9.648–10.203) | 1.00 | 15514.2 | 2.00 | 0.0 |
| candidate | sparse | interpret | generated-owned | 40.991 (40.200–41.339) | 4.24 | 3657.1 | 2.00 | 0.0 |
| candidate | sparse | interpret | native-view | 64.017 (62.845–64.155) | 6.63 | 2341.7 | 2.00 | 1512.0 |
| candidate | sparse | interpret | native-eager | 58.615 (58.327–93.260) | 6.07 | 2557.5 | 2.00 | 1488.0 |
| candidate | sparse | peak | stock | 11.816 (11.780–13.386) | 1.00 | 12687.2 | 1.00 | 0.0 |
| candidate | sparse | peak | generated-owned | 10.011 (9.579–10.113) | 0.85 | 14974.3 | 1.00 | 0.0 |
| candidate | sparse | peak | native-view | 19.832 (19.724–20.135) | 1.68 | 7558.7 | 1.00 | 1512.0 |
| candidate | sparse | peak | native-eager | 17.512 (17.230–19.586) | 1.48 | 8560.0 | 1.00 | 1488.0 |
| candidate | full | decode | stock | 781.385 (780.557–786.214) | 1.00 | 191.8 | 1629.50 | 0.0 |
| candidate | full | decode | generated-owned | 855.911 (837.358–949.650) | 1.10 | 175.1 | 1607.50 | 0.0 |
| candidate | full | decode | native-view | 1008.045 (920.902–1015.033) | 1.29 | 148.7 | 160.00 | 1512.0 |
| candidate | full | decode | native-eager | 1772.358 (1732.923–1885.647) | 2.27 | 84.6 | 1511.50 | 1488.0 |
| candidate | full | resident | stock | 923.852 (865.939–979.352) | 1.00 | 162.3 | 1630.50 | 0.0 |
| candidate | full | resident | generated-owned | 905.224 (893.545–925.529) | 0.98 | 165.6 | 1608.50 | 0.0 |
| candidate | full | resident | native-view | 1670.670 (1667.014–1866.576) | 1.81 | 89.7 | 1307.25 | 1512.0 |
| candidate | full | resident | native-eager | 2043.931 (2041.896–2141.460) | 2.21 | 73.3 | 1721.00 | 1488.0 |
| candidate | full | heap | stock | 856.107 (855.369–857.337) | 1.00 | 175.1 | 1630.50 | 0.0 |
| candidate | full | heap | generated-owned | 877.995 (870.495–892.830) | 1.03 | 170.7 | 1608.50 | 0.0 |
| candidate | full | heap | native-view | 1738.352 (1706.731–1844.824) | 2.03 | 86.2 | 1515.25 | 1512.0 |
| candidate | full | heap | native-eager | 2152.748 (2151.174–2889.845) | 2.51 | 69.6 | 1929.00 | 1488.0 |
| candidate | full | detach | stock | 899.919 (897.850–1011.119) | 1.00 | 166.6 | 1630.50 | 0.0 |
| candidate | full | detach | generated-owned | 968.097 (940.561–1670.267) | 1.08 | 154.8 | 1608.50 | 0.0 |
| candidate | full | detach | native-view | 26487.670 (5614.525–46884.062) | 29.43 | 5.7 | 4035.75 | 1512.0 |
| candidate | full | detach | native-eager | 7555.658 (7019.145–7980.861) | 8.40 | 19.8 | 5387.25 | 1488.0 |
| candidate | full | first | stock | 72.383 (69.676–146.851) | 1.00 | 2071.0 | 1.00 | 0.0 |
| candidate | full | first | generated-owned | 46.885 (34.248–54.388) | 0.65 | 3197.3 | 1.00 | 0.0 |
| candidate | full | first | native-view | 809.261 (753.749–817.363) | 11.18 | 185.2 | 1147.25 | 1512.0 |
| candidate | full | first | native-eager | 104.654 (89.295–106.897) | 1.45 | 1432.4 | 1.00 | 1488.0 |
| candidate | full | interpret | stock | 61.954 (61.669–62.895) | 1.00 | 2419.6 | 2.00 | 0.0 |
| candidate | full | interpret | generated-owned | 291.123 (291.028–301.329) | 4.70 | 514.9 | 2.00 | 0.0 |
| candidate | full | interpret | native-view | 407.109 (395.935–435.315) | 6.57 | 368.2 | 2.00 | 1512.0 |
| candidate | full | interpret | native-eager | 347.467 (342.937–348.765) | 5.61 | 431.4 | 2.00 | 1488.0 |
| candidate | full | peak | stock | 110.077 (108.081–115.765) | 1.00 | 1361.8 | 1.00 | 0.0 |
| candidate | full | peak | generated-owned | 45.161 (45.093–69.946) | 0.41 | 3319.3 | 1.00 | 0.0 |
| candidate | full | peak | native-view | 212.518 (169.551–327.555) | 1.93 | 705.4 | 1.00 | 1512.0 |
| candidate | full | peak | native-eager | 127.075 (125.321–132.753) | 1.15 | 1179.7 | 1.00 | 1488.0 |
| candidate | holdout | resident | stock | 2146.568 (2141.291–8752.022) | 1.00 | 237.6 | 5310.00 | 0.0 |
| candidate | holdout | resident | generated-owned | 2244.074 (2224.539–2303.356) | 1.05 | 227.3 | 5427.00 | 0.0 |
| candidate | holdout | resident | native-view | 3654.966 (3584.911–3848.273) | 1.70 | 139.5 | 4232.50 | 5208.0 |
| candidate | holdout | resident | native-eager | 4785.597 (4629.961–4807.942) | 2.23 | 106.6 | 5460.25 | 5184.0 |
| candidate | holdout | peak | stock | 138.160 (133.975–142.854) | 1.00 | 3691.6 | 1.00 | 0.0 |
| candidate | holdout | peak | generated-owned | 52.731 (51.711–53.921) | 0.38 | 9672.4 | 1.00 | 0.0 |
| candidate | holdout | peak | native-view | 187.032 (182.878–211.610) | 1.35 | 2727.0 | 1.00 | 5208.0 |
| candidate | holdout | peak | native-eager | 145.021 (144.713–146.675) | 1.05 | 3517.0 | 1.00 | 5184.0 |
| candidate | large | decode | stock | 228560.259 (215632.125–248964.375) | 1.00 | 226.7 | 433008.00 | 0.0 |
| candidate | large | decode | generated-owned | 270506.953 (269897.698–275927.545) | 1.18 | 191.6 | 460179.00 | 0.0 |
| candidate | large | decode | native-view | 202403.289 (200496.621–205221.383) | 0.89 | 256.0 | 160.00 | 393240.0 |
| candidate | large | decode | native-eager | 553952.833 (538758.000–562493.177) | 2.42 | 93.5 | 407556.00 | 393216.0 |
| candidate | large | resident | stock | 269760.089 (266130.302–271548.635) | 1.00 | 192.1 | 433009.00 | 0.0 |
| candidate | large | resident | generated-owned | 290281.104 (277858.797–292688.562) | 1.08 | 178.5 | 460180.00 | 0.0 |
| candidate | large | resident | native-view | 443863.062 (441254.164–464333.242) | 1.65 | 116.8 | 291357.25 | 393240.0 |
| candidate | large | resident | native-eager | 712063.969 (710262.802–841923.281) | 2.64 | 72.8 | 480223.50 | 393216.0 |
| candidate | large | peak | stock | 17258.573 (17083.093–17470.141) | 1.00 | 3002.7 | 1.00 | 0.0 |
| candidate | large | peak | generated-owned | 6265.432 (5480.524–6719.732) | 0.36 | 8271.1 | 1.00 | 0.0 |
| candidate | large | peak | native-view | 26855.315 (25964.190–27713.125) | 1.56 | 1929.7 | 1.00 | 393240.0 |
| candidate | large | peak | native-eager | 19997.745 (18562.280–20173.910) | 1.16 | 2591.4 | 1.00 | 393216.0 |
