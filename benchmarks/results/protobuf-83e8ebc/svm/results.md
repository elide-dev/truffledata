# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as retained index/header bytes. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 800.3 | 824.4 (1.03×) | 738.0 (0.92×) | 1721.6 (2.15×) |
| sparse | resident | 792.8 | 843.7 (1.06×) | 816.9 (1.03×) | 1854.9 (2.34×) |
| sparse | heap | 807.3 | 835.4 (1.03×) | 885.7 (1.10×) | 1929.0 (2.39×) |
| sparse | detach | 1628.0 | 1713.8 (1.05×) | 10722.8 (6.59×) | 14986.5 (9.21×) |
| sparse | first | 12.8 | 11.4 (0.89×) | 109.2 (8.56×) | 19.3 (1.51×) |
| sparse | interpret | 10.0 | 39.2 (3.92×) | 62.9 (6.29×) | 59.2 (5.92×) |
| sparse | peak | 11.8 | 9.7 (0.82×) | 20.5 (1.74×) | 18.0 (1.53×) |
| full | decode | 779.2 | 834.3 (1.07×) | 714.9 (0.92×) | 1707.2 (2.19×) |
| full | resident | 850.3 | 866.2 (1.02×) | 1203.7 (1.42×) | 1953.1 (2.30×) |
| full | heap | 849.5 | 878.1 (1.03×) | 1295.7 (1.53×) | 2106.1 (2.48×) |
| full | detach | 883.7 | 862.4 (0.98×) | 5516.0 (6.24×) | 6528.0 (7.39×) |
| full | first | 64.4 | 33.6 (0.52×) | 609.5 (9.47×) | 84.2 (1.31×) |
| full | interpret | 61.7 | 286.8 (4.65×) | 400.2 (6.49×) | 343.2 (5.57×) |
| full | peak | 105.0 | 58.4 (0.56×) | 171.2 (1.63×) | 125.7 (1.20×) |
| holdout | resident | 2138.9 | 2232.1 (1.04×) | 2889.4 (1.35×) | 4525.0 (2.12×) |
| holdout | peak | 134.1 | 51.7 (0.39×) | 192.9 (1.44×) | 132.4 (0.99×) |
| large | decode | 225594.6 | 267681.6 (1.19×) | 205771.1 (0.91×) | 545949.0 (2.42×) |
| large | resident | 269413.1 | 281442.5 (1.04×) | 372213.9 (1.38×) | 643211.8 (2.39×) |
| large | peak | 16052.5 | 5976.8 (0.37×) | 27717.8 (1.73×) | 18820.5 (1.17×) |

## baseline: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 776.2 | 828.5 (1.07×) | 927.4 (1.19×) | 1736.4 (2.24×) |
| sparse | resident | 815.2 | 859.9 (1.05×) | 1056.5 (1.30×) | 1852.8 (2.27×) |
| sparse | heap | 809.1 | 843.9 (1.04×) | 1143.4 (1.41×) | 1973.0 (2.44×) |
| sparse | detach | 1504.2 | 1713.8 (1.14×) | 10884.4 (7.24×) | 14992.0 (9.97×) |
| sparse | first | 13.5 | 12.1 (0.90×) | 149.5 (11.11×) | 20.8 (1.55×) |
| sparse | interpret | 9.9 | 39.6 (4.02×) | 62.2 (6.31×) | 58.1 (5.90×) |
| sparse | peak | 12.4 | 9.5 (0.77×) | 19.3 (1.56×) | 16.7 (1.35×) |
| full | decode | 791.8 | 838.7 (1.06×) | 902.3 (1.14×) | 1758.7 (2.22×) |
| full | resident | 850.1 | 867.3 (1.02×) | 1690.9 (1.99×) | 2003.7 (2.36×) |
| full | heap | 847.1 | 870.9 (1.03×) | 1690.2 (2.00×) | 2152.4 (2.54×) |
| full | detach | 839.0 | 862.9 (1.03×) | 5495.8 (6.55×) | 6606.9 (7.87×) |
| full | first | 64.2 | 34.4 (0.54×) | 739.5 (11.52×) | 86.3 (1.34×) |
| full | interpret | 61.1 | 300.8 (4.92×) | 417.6 (6.83×) | 348.5 (5.70×) |
| full | peak | 106.1 | 45.1 (0.43×) | 163.3 (1.54×) | 125.2 (1.18×) |
| holdout | resident | 2118.8 | 2203.5 (1.04×) | 3496.9 (1.65×) | 4562.5 (2.15×) |
| holdout | peak | 133.9 | 51.9 (0.39×) | 182.7 (1.37×) | 132.8 (0.99×) |
| large | decode | 224270.6 | 281321.1 (1.25×) | 189209.1 (0.84×) | 535613.8 (2.39×) |
| large | resident | 247110.4 | 288129.0 (1.17×) | 441907.4 (1.79×) | 684416.2 (2.77×) |
| large | peak | 16279.3 | 5571.5 (0.34×) | 25031.0 (1.54×) | 18365.1 (1.13×) |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row |
|---|---|---|---|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 800.349 (770.727–1022.144) | 1.00 | 187.3 | 1629.50 | 0.0 |
| candidate | sparse | decode | generated-owned | 824.369 (814.987–874.031) | 1.03 | 181.8 | 1607.50 | 0.0 |
| candidate | sparse | decode | native-view | 738.013 (737.496–739.943) | 0.92 | 203.1 | 144.00 | 1165.5 |
| candidate | sparse | decode | native-eager | 1721.593 (1706.401–1757.115) | 2.15 | 87.1 | 1527.50 | 1488.0 |
| candidate | sparse | resident | stock | 792.775 (787.634–818.509) | 1.00 | 189.1 | 1630.50 | 0.0 |
| candidate | sparse | resident | generated-owned | 843.657 (841.722–860.253) | 1.06 | 177.7 | 1608.50 | 0.0 |
| candidate | sparse | resident | native-view | 816.884 (791.577–1057.487) | 1.03 | 183.5 | 456.00 | 1165.5 |
| candidate | sparse | resident | native-eager | 1854.922 (1800.122–1873.715) | 2.34 | 80.8 | 1651.00 | 1488.0 |
| candidate | sparse | heap | stock | 807.270 (789.820–812.819) | 1.00 | 185.7 | 1630.50 | 0.0 |
| candidate | sparse | heap | generated-owned | 835.432 (833.794–837.182) | 1.03 | 179.4 | 1608.50 | 0.0 |
| candidate | sparse | heap | native-view | 885.687 (881.855–1323.894) | 1.10 | 169.3 | 664.00 | 1165.5 |
| candidate | sparse | heap | native-eager | 1928.956 (1928.118–1956.739) | 2.39 | 77.7 | 1859.00 | 1488.0 |
| candidate | sparse | detach | stock | 1628.033 (1487.847–1717.392) | 1.00 | 92.1 | 1630.50 | 0.0 |
| candidate | sparse | detach | generated-owned | 1713.774 (1708.009–1716.429) | 1.05 | 87.5 | 1608.50 | 0.0 |
| candidate | sparse | detach | native-view | 10722.843 (10565.211–12538.794) | 6.59 | 14.0 | 4019.75 | 1165.5 |
| candidate | sparse | detach | native-eager | 14986.473 (14982.626–15018.082) | 9.21 | 10.0 | 5403.25 | 1488.0 |
| candidate | sparse | first | stock | 12.751 (12.558–13.342) | 1.00 | 11756.8 | 1.00 | 0.0 |
| candidate | sparse | first | generated-owned | 11.394 (11.284–11.464) | 0.89 | 13157.1 | 1.00 | 0.0 |
| candidate | sparse | first | native-view | 109.160 (108.495–111.040) | 8.56 | 1373.3 | 312.00 | 1165.5 |
| candidate | sparse | first | native-eager | 19.269 (19.208–21.038) | 1.51 | 7779.8 | 1.00 | 1488.0 |
| candidate | sparse | interpret | stock | 9.999 (9.723–10.041) | 1.00 | 14992.6 | 2.00 | 0.0 |
| candidate | sparse | interpret | generated-owned | 39.166 (39.155–40.973) | 3.92 | 3827.4 | 2.00 | 0.0 |
| candidate | sparse | interpret | native-view | 62.932 (62.847–63.437) | 6.29 | 2382.0 | 2.00 | 1165.5 |
| candidate | sparse | interpret | native-eager | 59.204 (58.032–59.794) | 5.92 | 2532.0 | 2.00 | 1488.0 |
| candidate | sparse | peak | stock | 11.815 (11.787–11.926) | 1.00 | 12687.3 | 1.00 | 0.0 |
| candidate | sparse | peak | generated-owned | 9.672 (9.477–9.695) | 0.82 | 15499.6 | 1.00 | 0.0 |
| candidate | sparse | peak | native-view | 20.502 (20.383–21.005) | 1.74 | 7311.6 | 1.00 | 1165.5 |
| candidate | sparse | peak | native-eager | 18.031 (16.278–21.326) | 1.53 | 8313.6 | 1.00 | 1488.0 |
| candidate | full | decode | stock | 779.243 (771.881–833.695) | 1.00 | 192.4 | 1629.50 | 0.0 |
| candidate | full | decode | generated-owned | 834.300 (824.848–932.298) | 1.07 | 179.7 | 1607.50 | 0.0 |
| candidate | full | decode | native-view | 714.924 (713.808–806.034) | 0.92 | 209.7 | 144.00 | 1165.5 |
| candidate | full | decode | native-eager | 1707.230 (1695.310–1723.258) | 2.19 | 87.8 | 1527.50 | 1488.0 |
| candidate | full | resident | stock | 850.305 (842.441–4781.534) | 1.00 | 176.3 | 1630.50 | 0.0 |
| candidate | full | resident | generated-owned | 866.193 (865.732–872.903) | 1.02 | 173.1 | 1608.50 | 0.0 |
| candidate | full | resident | native-view | 1203.728 (1189.843–1307.703) | 1.42 | 124.5 | 1131.50 | 1165.5 |
| candidate | full | resident | native-eager | 1953.127 (1925.892–1991.982) | 2.30 | 76.8 | 1737.00 | 1488.0 |
| candidate | full | heap | stock | 849.483 (848.266–860.331) | 1.00 | 176.5 | 1630.50 | 0.0 |
| candidate | full | heap | generated-owned | 878.118 (868.690–899.526) | 1.03 | 170.7 | 1608.50 | 0.0 |
| candidate | full | heap | native-view | 1295.676 (1295.511–1309.503) | 1.53 | 115.7 | 1339.50 | 1165.5 |
| candidate | full | heap | native-eager | 2106.092 (2096.578–2108.470) | 2.48 | 71.2 | 1945.00 | 1488.0 |
| candidate | full | detach | stock | 883.718 (835.974–896.902) | 1.00 | 169.6 | 1630.50 | 0.0 |
| candidate | full | detach | generated-owned | 862.448 (862.132–878.604) | 0.98 | 173.8 | 1608.50 | 0.0 |
| candidate | full | detach | native-view | 5516.027 (5485.690–5653.145) | 6.24 | 27.2 | 4019.75 | 1165.5 |
| candidate | full | detach | native-eager | 6527.973 (6515.651–6687.376) | 7.39 | 23.0 | 5403.25 | 1488.0 |
| candidate | full | first | stock | 64.352 (63.995–64.590) | 1.00 | 2329.5 | 1.00 | 0.0 |
| candidate | full | first | generated-owned | 33.552 (33.380–34.924) | 0.52 | 4467.8 | 1.00 | 0.0 |
| candidate | full | first | native-view | 609.532 (527.000–636.753) | 9.47 | 245.9 | 987.50 | 1165.5 |
| candidate | full | first | native-eager | 84.236 (80.882–86.928) | 1.31 | 1779.6 | 1.00 | 1488.0 |
| candidate | full | interpret | stock | 61.671 (61.565–62.420) | 1.00 | 2430.8 | 2.00 | 0.0 |
| candidate | full | interpret | generated-owned | 286.788 (286.435–297.820) | 4.65 | 522.7 | 2.00 | 0.0 |
| candidate | full | interpret | native-view | 400.218 (398.943–400.428) | 6.49 | 374.6 | 2.00 | 1165.5 |
| candidate | full | interpret | native-eager | 343.201 (339.689–343.824) | 5.57 | 436.8 | 2.00 | 1488.0 |
| candidate | full | peak | stock | 105.047 (104.306–106.807) | 1.00 | 1427.0 | 1.00 | 0.0 |
| candidate | full | peak | generated-owned | 58.449 (45.014–63.207) | 0.56 | 2564.7 | 1.00 | 0.0 |
| candidate | full | peak | native-view | 171.215 (170.771–171.251) | 1.63 | 875.5 | 1.00 | 1165.5 |
| candidate | full | peak | native-eager | 125.677 (125.132–126.538) | 1.20 | 1192.8 | 1.00 | 1488.0 |
| candidate | holdout | resident | stock | 2138.925 (2108.459–2169.157) | 1.00 | 238.5 | 5310.00 | 0.0 |
| candidate | holdout | resident | generated-owned | 2232.130 (2178.629–2273.600) | 1.04 | 228.5 | 5427.00 | 0.0 |
| candidate | holdout | resident | native-view | 2889.449 (2862.240–2905.620) | 1.35 | 176.5 | 3913.25 | 3315.0 |
| candidate | holdout | resident | native-eager | 4525.010 (4501.922–4589.980) | 2.12 | 112.7 | 5476.25 | 5184.0 |
| candidate | holdout | peak | stock | 134.132 (133.443–141.060) | 1.00 | 3802.5 | 1.00 | 0.0 |
| candidate | holdout | peak | generated-owned | 51.653 (51.535–51.839) | 0.39 | 9874.2 | 1.00 | 0.0 |
| candidate | holdout | peak | native-view | 192.905 (192.022–201.609) | 1.44 | 2644.0 | 1.00 | 3315.0 |
| candidate | holdout | peak | native-eager | 132.395 (131.471–132.486) | 0.99 | 3852.3 | 1.00 | 5184.0 |
| candidate | large | decode | stock | 225594.616 (209198.199–226078.013) | 1.00 | 229.7 | 433008.00 | 0.0 |
| candidate | large | decode | generated-owned | 267681.604 (266976.646–267692.616) | 1.19 | 193.6 | 460179.00 | 0.0 |
| candidate | large | decode | native-view | 205771.055 (204021.410–208107.812) | 0.91 | 251.8 | 144.00 | 409153.5 |
| candidate | large | decode | native-eager | 545949.031 (541159.458–580950.333) | 2.42 | 94.9 | 407572.00 | 393216.0 |
| candidate | large | resident | stock | 269413.112 (268056.245–270230.734) | 1.00 | 192.4 | 433009.00 | 0.0 |
| candidate | large | resident | generated-owned | 281442.453 (272889.729–286984.635) | 1.04 | 184.1 | 460180.00 | 0.0 |
| candidate | large | resident | native-view | 372213.912 (369328.588–379434.088) | 1.38 | 139.2 | 263984.00 | 409153.5 |
| candidate | large | resident | native-eager | 643211.823 (641902.760–644992.573) | 2.39 | 80.6 | 480239.50 | 393216.0 |
| candidate | large | peak | stock | 16052.497 (15776.526–16295.013) | 1.00 | 3228.3 | 1.00 | 0.0 |
| candidate | large | peak | generated-owned | 5976.811 (5189.562–6524.214) | 0.37 | 8670.6 | 1.00 | 0.0 |
| candidate | large | peak | native-view | 27717.805 (27358.054–28542.342) | 1.73 | 1869.6 | 1.00 | 409153.5 |
| candidate | large | peak | native-eager | 18820.544 (18202.142–20001.050) | 1.17 | 2753.5 | 1.00 | 393216.0 |
| baseline | sparse | decode | stock | 776.150 (769.740–780.308) | 1.00 | 193.1 | 1629.50 | 0.0 |
| baseline | sparse | decode | generated-owned | 828.486 (826.332–851.554) | 1.07 | 180.9 | 1607.50 | 0.0 |
| baseline | sparse | decode | native-view | 927.369 (905.226–1030.244) | 1.19 | 161.6 | 160.00 | 1512.0 |
| baseline | sparse | decode | native-eager | 1736.389 (1724.237–1792.224) | 2.24 | 86.3 | 1511.50 | 1488.0 |
| baseline | sparse | resident | stock | 815.179 (795.787–820.544) | 1.00 | 183.9 | 1630.50 | 0.0 |
| baseline | sparse | resident | generated-owned | 859.886 (854.086–862.498) | 1.05 | 174.3 | 1608.50 | 0.0 |
| baseline | sparse | resident | native-view | 1056.537 (1044.062–1066.229) | 1.30 | 141.9 | 541.25 | 1512.0 |
| baseline | sparse | resident | native-eager | 1852.776 (1842.687–1854.913) | 2.27 | 80.9 | 1635.00 | 1488.0 |
| baseline | sparse | heap | stock | 809.055 (807.905–813.133) | 1.00 | 185.3 | 1630.50 | 0.0 |
| baseline | sparse | heap | generated-owned | 843.928 (840.224–844.576) | 1.04 | 177.6 | 1608.50 | 0.0 |
| baseline | sparse | heap | native-view | 1143.382 (1139.944–10224.648) | 1.41 | 131.1 | 749.25 | 1512.0 |
| baseline | sparse | heap | native-eager | 1972.962 (1965.392–2375.016) | 2.44 | 76.0 | 1843.00 | 1488.0 |
| baseline | sparse | detach | stock | 1504.248 (1497.553–1537.108) | 1.00 | 99.7 | 1630.50 | 0.0 |
| baseline | sparse | detach | generated-owned | 1713.822 (1711.044–1731.166) | 1.14 | 87.5 | 1608.50 | 0.0 |
| baseline | sparse | detach | native-view | 10884.388 (10626.031–10978.646) | 7.24 | 13.8 | 4035.75 | 1512.0 |
| baseline | sparse | detach | native-eager | 14992.009 (14920.493–15044.961) | 9.97 | 10.0 | 5387.25 | 1488.0 |
| baseline | sparse | first | stock | 13.454 (13.067–13.483) | 1.00 | 11142.3 | 1.00 | 0.0 |
| baseline | sparse | first | generated-owned | 12.098 (11.362–12.143) | 0.90 | 12391.3 | 1.00 | 0.0 |
| baseline | sparse | first | native-view | 149.478 (146.409–150.414) | 11.11 | 1002.9 | 381.25 | 1512.0 |
| baseline | sparse | first | native-eager | 20.821 (19.414–21.916) | 1.55 | 7199.9 | 1.00 | 1488.0 |
| baseline | sparse | interpret | stock | 9.853 (9.567–9.913) | 1.00 | 15215.0 | 2.00 | 0.0 |
| baseline | sparse | interpret | generated-owned | 39.630 (39.278–39.956) | 4.02 | 3782.7 | 2.00 | 0.0 |
| baseline | sparse | interpret | native-view | 62.169 (62.095–62.254) | 6.31 | 2411.3 | 2.00 | 1512.0 |
| baseline | sparse | interpret | native-eager | 58.105 (57.734–58.204) | 5.90 | 2579.9 | 2.00 | 1488.0 |
| baseline | sparse | peak | stock | 12.365 (11.768–12.684) | 1.00 | 12123.1 | 1.00 | 0.0 |
| baseline | sparse | peak | generated-owned | 9.479 (9.307–10.638) | 0.77 | 15813.9 | 1.00 | 0.0 |
| baseline | sparse | peak | native-view | 19.276 (19.174–19.774) | 1.56 | 7776.8 | 1.00 | 1512.0 |
| baseline | sparse | peak | native-eager | 16.706 (16.683–16.993) | 1.35 | 8973.4 | 1.00 | 1488.0 |
| baseline | full | decode | stock | 791.838 (782.237–880.946) | 1.00 | 189.3 | 1629.50 | 0.0 |
| baseline | full | decode | generated-owned | 838.671 (831.774–866.025) | 1.06 | 178.7 | 1607.50 | 0.0 |
| baseline | full | decode | native-view | 902.283 (901.083–916.383) | 1.14 | 166.1 | 160.00 | 1512.0 |
| baseline | full | decode | native-eager | 1758.669 (1737.792–1759.435) | 2.22 | 85.2 | 1511.50 | 1488.0 |
| baseline | full | resident | stock | 850.100 (834.433–851.131) | 1.00 | 176.3 | 1630.50 | 0.0 |
| baseline | full | resident | generated-owned | 867.258 (867.037–870.504) | 1.02 | 172.9 | 1608.50 | 0.0 |
| baseline | full | resident | native-view | 1690.944 (1690.716–1691.333) | 1.99 | 88.7 | 1307.25 | 1512.0 |
| baseline | full | resident | native-eager | 2003.692 (1991.991–2231.324) | 2.36 | 74.8 | 1721.00 | 1488.0 |
| baseline | full | heap | stock | 847.144 (846.729–859.517) | 1.00 | 177.0 | 1630.50 | 0.0 |
| baseline | full | heap | generated-owned | 870.939 (869.953–871.764) | 1.03 | 172.1 | 1608.50 | 0.0 |
| baseline | full | heap | native-view | 1690.234 (1689.030–1698.203) | 2.00 | 88.7 | 1515.25 | 1512.0 |
| baseline | full | heap | native-eager | 2152.448 (2134.979–2639.033) | 2.54 | 69.6 | 1929.00 | 1488.0 |
| baseline | full | detach | stock | 839.024 (836.247–841.404) | 1.00 | 178.7 | 1630.50 | 0.0 |
| baseline | full | detach | generated-owned | 862.940 (859.156–870.257) | 1.03 | 173.7 | 1608.50 | 0.0 |
| baseline | full | detach | native-view | 5495.755 (5457.120–5568.181) | 6.55 | 27.3 | 4035.75 | 1512.0 |
| baseline | full | detach | native-eager | 6606.865 (6600.042–6640.117) | 7.87 | 22.7 | 5387.25 | 1488.0 |
| baseline | full | first | stock | 64.194 (63.780–66.031) | 1.00 | 2335.2 | 1.00 | 0.0 |
| baseline | full | first | generated-owned | 34.433 (33.405–34.479) | 0.54 | 4353.5 | 1.00 | 0.0 |
| baseline | full | first | native-view | 739.498 (734.946–2076.998) | 11.52 | 202.7 | 1147.25 | 1512.0 |
| baseline | full | first | native-eager | 86.265 (85.192–88.618) | 1.34 | 1737.7 | 1.00 | 1488.0 |
| baseline | full | interpret | stock | 61.103 (60.971–61.396) | 1.00 | 2453.4 | 2.00 | 0.0 |
| baseline | full | interpret | generated-owned | 300.842 (300.069–302.438) | 4.92 | 498.3 | 2.00 | 0.0 |
| baseline | full | interpret | native-view | 417.559 (401.954–2475.086) | 6.83 | 359.0 | 2.00 | 1512.0 |
| baseline | full | interpret | native-eager | 348.543 (340.421–349.275) | 5.70 | 430.1 | 2.00 | 1488.0 |
| baseline | full | peak | stock | 106.068 (104.941–106.559) | 1.00 | 1413.3 | 1.00 | 0.0 |
| baseline | full | peak | generated-owned | 45.132 (44.751–45.224) | 0.43 | 3321.5 | 1.00 | 0.0 |
| baseline | full | peak | native-view | 163.307 (163.086–163.495) | 1.54 | 917.9 | 1.00 | 1512.0 |
| baseline | full | peak | native-eager | 125.202 (124.743–125.506) | 1.18 | 1197.3 | 1.00 | 1488.0 |
| baseline | holdout | resident | stock | 2118.789 (2101.346–2122.397) | 1.00 | 240.7 | 5310.00 | 0.0 |
| baseline | holdout | resident | generated-owned | 2203.502 (2196.843–2225.279) | 1.04 | 231.5 | 5427.00 | 0.0 |
| baseline | holdout | resident | native-view | 3496.857 (3483.269–4478.320) | 1.65 | 145.9 | 4232.50 | 5208.0 |
| baseline | holdout | resident | native-eager | 4562.490 (4559.040–4635.067) | 2.15 | 111.8 | 5460.25 | 5184.0 |
| baseline | holdout | peak | stock | 133.860 (132.698–134.209) | 1.00 | 3810.2 | 1.00 | 0.0 |
| baseline | holdout | peak | generated-owned | 51.859 (51.838–52.199) | 0.39 | 9835.0 | 1.00 | 0.0 |
| baseline | holdout | peak | native-view | 182.725 (181.072–187.918) | 1.37 | 2791.3 | 1.00 | 5208.0 |
| baseline | holdout | peak | native-eager | 132.756 (131.520–133.436) | 0.99 | 3841.9 | 1.00 | 5184.0 |
| baseline | large | decode | stock | 224270.562 (211006.840–2498918.094) | 1.00 | 231.1 | 433008.00 | 0.0 |
| baseline | large | decode | generated-owned | 281321.146 (272120.604–290884.922) | 1.25 | 184.2 | 460179.00 | 0.0 |
| baseline | large | decode | native-view | 189209.128 (186045.941–201445.617) | 0.84 | 273.9 | 160.00 | 393240.0 |
| baseline | large | decode | native-eager | 535613.844 (534302.927–600774.021) | 2.39 | 96.8 | 407556.00 | 393216.0 |
| baseline | large | resident | stock | 247110.362 (221224.977–262982.693) | 1.00 | 209.7 | 433009.00 | 0.0 |
| baseline | large | resident | generated-owned | 288128.958 (276128.177–340538.869) | 1.17 | 179.9 | 460180.00 | 0.0 |
| baseline | large | resident | native-view | 441907.414 (440286.477–452513.781) | 1.79 | 117.3 | 291357.25 | 393240.0 |
| baseline | large | resident | native-eager | 684416.240 (673545.083–710971.677) | 2.77 | 75.7 | 480223.50 | 393216.0 |
| baseline | large | peak | stock | 16279.304 (16217.888–16481.745) | 1.00 | 3183.3 | 1.00 | 0.0 |
| baseline | large | peak | generated-owned | 5571.528 (5406.307–6177.850) | 0.34 | 9301.3 | 1.00 | 0.0 |
| baseline | large | peak | native-view | 25030.990 (24928.022–25557.103) | 1.54 | 2070.3 | 1.00 | 393240.0 |
| baseline | large | peak | native-eager | 18365.107 (17494.209–18473.394) | 1.13 | 2821.8 | 1.00 | 393216.0 |
