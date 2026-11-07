# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as retained index/header bytes. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 767.5 | 832.2 (1.08×) | 696.2 (0.91×) | 1454.9 (1.90×) |
| sparse | resident | 785.5 | 815.4 (1.04×) | 803.9 (1.02×) | 1563.7 (1.99×) |
| sparse | heap | 816.6 | 825.1 (1.01×) | 900.3 (1.10×) | 1684.4 (2.06×) |
| sparse | detach | 1719.9 | 1973.9 (1.15×) | 12543.0 (7.29×) | 13286.3 (7.73×) |
| sparse | first | 14.5 | 11.7 (0.81×) | 119.5 (8.21×) | 22.1 (1.52×) |
| sparse | interpret | 9.9 | 40.1 (4.07×) | 62.7 (6.36×) | 58.3 (5.91×) |
| sparse | peak | 11.8 | 9.4 (0.80×) | 20.5 (1.75×) | 17.1 (1.46×) |
| full | decode | 772.5 | 816.1 (1.06×) | 689.6 (0.89×) | 1439.0 (1.86×) |
| full | resident | 847.8 | 880.5 (1.04×) | 1223.9 (1.44×) | 1721.7 (2.03×) |
| full | heap | 854.3 | 851.0 (1.00×) | 1301.8 (1.52×) | 1831.3 (2.14×) |
| full | detach | 841.5 | 851.3 (1.01×) | 5546.7 (6.59×) | 6321.0 (7.51×) |
| full | first | 64.6 | 33.9 (0.52×) | 551.7 (8.54×) | 82.1 (1.27×) |
| full | interpret | 62.7 | 298.6 (4.77×) | 399.1 (6.37×) | 341.0 (5.44×) |
| full | peak | 105.6 | 44.8 (0.42×) | 222.8 (2.11×) | 122.0 (1.15×) |
| holdout | resident | 2129.7 | 2136.2 (1.00×) | 2834.8 (1.33×) | 3989.9 (1.87×) |
| holdout | peak | 134.2 | 52.1 (0.39×) | 194.6 (1.45×) | 129.3 (0.96×) |
| large | decode | 225996.0 | 261080.1 (1.16×) | 282883.0 (1.25×) | 460382.1 (2.04×) |
| large | resident | 249459.5 | 269380.6 (1.08×) | 367449.6 (1.47×) | 561701.4 (2.25×) |
| large | peak | 17567.7 | 5428.7 (0.31×) | 27867.2 (1.59×) | 18161.3 (1.03×) |

## baseline: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 771.1 | 828.2 (1.07×) | 703.8 (0.91×) | 1710.3 (2.22×) |
| sparse | resident | 773.3 | 845.3 (1.09×) | 799.6 (1.03×) | 1827.6 (2.36×) |
| sparse | heap | 795.0 | 845.3 (1.06×) | 894.5 (1.13×) | 1942.9 (2.44×) |
| sparse | detach | 1559.3 | 1788.6 (1.15×) | 10926.4 (7.01×) | 14961.3 (9.59×) |
| sparse | first | 14.5 | 12.8 (0.88×) | 114.5 (7.89×) | 22.2 (1.53×) |
| sparse | interpret | 9.7 | 40.2 (4.17×) | 62.8 (6.50×) | 58.2 (6.02×) |
| sparse | peak | 11.8 | 9.5 (0.81×) | 20.3 (1.73×) | 16.8 (1.42×) |
| full | decode | 773.8 | 823.8 (1.06×) | 693.3 (0.90×) | 1697.0 (2.19×) |
| full | resident | 855.1 | 869.9 (1.02×) | 1251.3 (1.46×) | 1986.1 (2.32×) |
| full | heap | 843.5 | 858.0 (1.02×) | 1282.3 (1.52×) | 2095.0 (2.48×) |
| full | detach | 846.2 | 877.5 (1.04×) | 5493.1 (6.49×) | 6589.1 (7.79×) |
| full | first | 69.9 | 33.6 (0.48×) | 538.1 (7.69×) | 80.1 (1.14×) |
| full | interpret | 61.9 | 291.2 (4.70×) | 404.6 (6.53×) | 343.5 (5.55×) |
| full | peak | 105.4 | 45.3 (0.43×) | 171.9 (1.63×) | 126.6 (1.20×) |
| holdout | resident | 2121.3 | 2214.1 (1.04×) | 2863.2 (1.35×) | 4534.1 (2.14×) |
| holdout | peak | 133.9 | 52.4 (0.39×) | 192.9 (1.44×) | 132.2 (0.99×) |
| large | decode | 223700.5 | 277054.2 (1.24×) | 198510.3 (0.89×) | 541430.8 (2.42×) |
| large | resident | 245739.5 | 272996.4 (1.11×) | 368393.1 (1.50×) | 698739.4 (2.84×) |
| large | peak | 17096.1 | 5304.8 (0.31×) | 27703.7 (1.62×) | 17350.7 (1.01×) |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row |
|---|---|---|---|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 767.518 (765.391–767.944) | 1.00 | 195.3 | 1629.50 | 0.0 |
| candidate | sparse | decode | generated-owned | 832.198 (809.203–836.632) | 1.08 | 180.1 | 1607.50 | 0.0 |
| candidate | sparse | decode | native-view | 696.205 (691.987–705.804) | 0.91 | 215.3 | 144.00 | 1165.5 |
| candidate | sparse | decode | native-eager | 1454.897 (1445.321–1460.268) | 1.90 | 103.0 | 1214.25 | 1141.5 |
| candidate | sparse | resident | stock | 785.487 (773.215–822.355) | 1.00 | 190.8 | 1630.50 | 0.0 |
| candidate | sparse | resident | generated-owned | 815.397 (813.640–818.672) | 1.04 | 183.8 | 1608.50 | 0.0 |
| candidate | sparse | resident | native-view | 803.939 (802.430–836.162) | 1.02 | 186.5 | 456.00 | 1165.5 |
| candidate | sparse | resident | native-eager | 1563.686 (1554.261–1602.180) | 1.99 | 95.9 | 1337.75 | 1141.5 |
| candidate | sparse | heap | stock | 816.633 (809.716–833.789) | 1.00 | 183.6 | 1630.50 | 0.0 |
| candidate | sparse | heap | generated-owned | 825.136 (823.748–833.140) | 1.01 | 181.7 | 1608.50 | 0.0 |
| candidate | sparse | heap | native-view | 900.297 (899.858–907.398) | 1.10 | 166.5 | 664.00 | 1165.5 |
| candidate | sparse | heap | native-eager | 1684.417 (1684.308–1699.143) | 2.06 | 89.0 | 1545.75 | 1141.5 |
| candidate | sparse | detach | stock | 1719.900 (1491.135–1723.948) | 1.00 | 87.2 | 1630.50 | 0.0 |
| candidate | sparse | detach | generated-owned | 1973.933 (1971.136–2660.516) | 1.15 | 75.9 | 1608.50 | 0.0 |
| candidate | sparse | detach | native-view | 12542.958 (10994.609–12633.774) | 7.29 | 12.0 | 4019.75 | 1165.5 |
| candidate | sparse | detach | native-eager | 13286.349 (13107.113–14240.172) | 7.73 | 11.3 | 5090.00 | 1141.5 |
| candidate | sparse | first | stock | 14.548 (13.963–14.699) | 1.00 | 10304.3 | 1.00 | 0.0 |
| candidate | sparse | first | generated-owned | 11.745 (11.728–12.099) | 0.81 | 12763.4 | 1.00 | 0.0 |
| candidate | sparse | first | native-view | 119.496 (118.667–122.467) | 8.21 | 1254.5 | 312.00 | 1165.5 |
| candidate | sparse | first | native-eager | 22.122 (21.558–22.837) | 1.52 | 6776.5 | 1.00 | 1141.5 |
| candidate | sparse | interpret | stock | 9.858 (9.759–9.900) | 1.00 | 15206.0 | 2.00 | 0.0 |
| candidate | sparse | interpret | generated-owned | 40.104 (40.029–41.648) | 4.07 | 3737.9 | 2.00 | 0.0 |
| candidate | sparse | interpret | native-view | 62.674 (62.377–63.987) | 6.36 | 2391.9 | 2.00 | 1165.5 |
| candidate | sparse | interpret | native-eager | 58.268 (58.239–58.718) | 5.91 | 2572.7 | 2.00 | 1141.5 |
| candidate | sparse | peak | stock | 11.751 (11.681–11.953) | 1.00 | 12757.4 | 1.00 | 0.0 |
| candidate | sparse | peak | generated-owned | 9.433 (9.395–9.444) | 0.80 | 15891.9 | 1.00 | 0.0 |
| candidate | sparse | peak | native-view | 20.543 (20.528–22.989) | 1.75 | 7297.2 | 1.00 | 1165.5 |
| candidate | sparse | peak | native-eager | 17.124 (16.570–17.642) | 1.46 | 8753.9 | 1.00 | 1141.5 |
| candidate | full | decode | stock | 772.527 (771.749–776.806) | 1.00 | 194.0 | 1629.50 | 0.0 |
| candidate | full | decode | generated-owned | 816.069 (808.404–854.456) | 1.06 | 183.7 | 1607.50 | 0.0 |
| candidate | full | decode | native-view | 689.560 (683.534–690.804) | 0.89 | 217.4 | 144.00 | 1165.5 |
| candidate | full | decode | native-eager | 1439.009 (1435.005–1453.608) | 1.86 | 104.2 | 1214.25 | 1141.5 |
| candidate | full | resident | stock | 847.783 (831.162–857.109) | 1.00 | 176.8 | 1630.50 | 0.0 |
| candidate | full | resident | generated-owned | 880.529 (870.470–1047.703) | 1.04 | 170.2 | 1608.50 | 0.0 |
| candidate | full | resident | native-view | 1223.932 (1213.054–1281.010) | 1.44 | 122.5 | 1131.50 | 1165.5 |
| candidate | full | resident | native-eager | 1721.738 (1712.329–1806.385) | 2.03 | 87.1 | 1423.75 | 1141.5 |
| candidate | full | heap | stock | 854.276 (853.330–860.736) | 1.00 | 175.5 | 1630.50 | 0.0 |
| candidate | full | heap | generated-owned | 851.035 (846.670–859.287) | 1.00 | 176.1 | 1608.50 | 0.0 |
| candidate | full | heap | native-view | 1301.837 (1288.071–1335.586) | 1.52 | 115.1 | 1339.50 | 1165.5 |
| candidate | full | heap | native-eager | 1831.271 (1825.089–1835.673) | 2.14 | 81.9 | 1631.75 | 1141.5 |
| candidate | full | detach | stock | 841.507 (828.909–841.622) | 1.00 | 178.1 | 1630.50 | 0.0 |
| candidate | full | detach | generated-owned | 851.269 (851.151–852.508) | 1.01 | 176.1 | 1608.50 | 0.0 |
| candidate | full | detach | native-view | 5546.686 (5523.937–6397.944) | 6.59 | 27.0 | 4019.75 | 1165.5 |
| candidate | full | detach | native-eager | 6320.995 (6310.596–6357.736) | 7.51 | 23.7 | 5090.00 | 1141.5 |
| candidate | full | first | stock | 64.632 (64.578–66.715) | 1.00 | 2319.4 | 1.00 | 0.0 |
| candidate | full | first | generated-owned | 33.908 (33.422–40.775) | 0.52 | 4421.0 | 1.00 | 0.0 |
| candidate | full | first | native-view | 551.706 (546.254–558.051) | 8.54 | 271.7 | 987.50 | 1165.5 |
| candidate | full | first | native-eager | 82.137 (80.962–83.511) | 1.27 | 1825.1 | 1.00 | 1141.5 |
| candidate | full | interpret | stock | 62.650 (61.349–69.187) | 1.00 | 2392.7 | 2.00 | 0.0 |
| candidate | full | interpret | generated-owned | 298.553 (292.085–304.400) | 4.77 | 502.1 | 2.00 | 0.0 |
| candidate | full | interpret | native-view | 399.113 (398.946–411.705) | 6.37 | 375.6 | 2.00 | 1165.5 |
| candidate | full | interpret | native-eager | 340.966 (339.380–342.278) | 5.44 | 439.7 | 2.00 | 1141.5 |
| candidate | full | peak | stock | 105.643 (105.208–111.058) | 1.00 | 1419.0 | 1.00 | 0.0 |
| candidate | full | peak | generated-owned | 44.767 (44.590–68.022) | 0.42 | 3348.6 | 1.00 | 0.0 |
| candidate | full | peak | native-view | 222.838 (187.198–336.300) | 2.11 | 672.7 | 1.00 | 1165.5 |
| candidate | full | peak | native-eager | 121.966 (121.789–122.253) | 1.15 | 1229.1 | 1.00 | 1141.5 |
| candidate | holdout | resident | stock | 2129.669 (2116.920–2162.906) | 1.00 | 239.5 | 5310.00 | 0.0 |
| candidate | holdout | resident | generated-owned | 2136.150 (2124.007–2141.353) | 1.00 | 238.8 | 5427.00 | 0.0 |
| candidate | holdout | resident | native-view | 2834.760 (2831.809–2847.044) | 1.33 | 179.9 | 3913.25 | 3315.0 |
| candidate | holdout | resident | native-eager | 3989.867 (3975.105–4092.235) | 1.87 | 127.8 | 4684.25 | 3291.0 |
| candidate | holdout | peak | stock | 134.178 (133.748–134.678) | 1.00 | 3801.2 | 1.00 | 0.0 |
| candidate | holdout | peak | generated-owned | 52.102 (52.017–52.179) | 0.39 | 9789.2 | 1.00 | 0.0 |
| candidate | holdout | peak | native-view | 194.570 (193.991–195.979) | 1.45 | 2621.3 | 1.00 | 3315.0 |
| candidate | holdout | peak | native-eager | 129.280 (128.606–130.626) | 0.96 | 3945.2 | 1.00 | 3291.0 |
| candidate | large | decode | stock | 225996.047 (211732.133–238221.652) | 1.00 | 229.3 | 433008.00 | 0.0 |
| candidate | large | decode | generated-owned | 261080.103 (247729.866–265321.880) | 1.16 | 198.5 | 460179.00 | 0.0 |
| candidate | large | decode | native-view | 282882.969 (276371.812–293202.906) | 1.25 | 183.2 | 144.00 | 409153.5 |
| candidate | large | decode | native-eager | 460382.062 (459539.953–488429.383) | 2.04 | 112.6 | 316593.75 | 409129.5 |
| candidate | large | resident | stock | 249459.473 (238574.683–297438.979) | 1.00 | 207.7 | 433009.00 | 0.0 |
| candidate | large | resident | generated-owned | 269380.620 (269058.946–281200.521) | 1.08 | 192.4 | 460180.00 | 0.0 |
| candidate | large | resident | native-view | 367449.588 (364856.019–400606.547) | 1.47 | 141.0 | 263984.00 | 409153.5 |
| candidate | large | resident | native-eager | 561701.354 (554256.969–567871.781) | 2.25 | 92.3 | 389261.25 | 409129.5 |
| candidate | large | peak | stock | 17567.682 (16259.484–40646.253) | 1.00 | 2949.9 | 1.00 | 0.0 |
| candidate | large | peak | generated-owned | 5428.744 (5371.742–5732.613) | 0.31 | 9545.9 | 1.00 | 0.0 |
| candidate | large | peak | native-view | 27867.168 (27556.834–30502.607) | 1.59 | 1859.6 | 1.00 | 409153.5 |
| candidate | large | peak | native-eager | 18161.293 (18142.344–22892.433) | 1.03 | 2853.4 | 1.00 | 409129.5 |
| baseline | sparse | decode | stock | 771.062 (770.924–777.532) | 1.00 | 194.4 | 1629.50 | 0.0 |
| baseline | sparse | decode | generated-owned | 828.237 (825.838–831.784) | 1.07 | 181.0 | 1607.50 | 0.0 |
| baseline | sparse | decode | native-view | 703.785 (701.111–711.419) | 0.91 | 213.0 | 144.00 | 1165.5 |
| baseline | sparse | decode | native-eager | 1710.288 (1702.613–1714.003) | 2.22 | 87.6 | 1527.50 | 1488.0 |
| baseline | sparse | resident | stock | 773.270 (772.014–778.389) | 1.00 | 193.9 | 1630.50 | 0.0 |
| baseline | sparse | resident | generated-owned | 845.340 (837.383–955.722) | 1.09 | 177.3 | 1608.50 | 0.0 |
| baseline | sparse | resident | native-view | 799.602 (798.190–803.244) | 1.03 | 187.5 | 456.00 | 1165.5 |
| baseline | sparse | resident | native-eager | 1827.575 (1777.019–1953.143) | 2.36 | 82.0 | 1651.00 | 1488.0 |
| baseline | sparse | heap | stock | 794.954 (787.201–797.026) | 1.00 | 188.6 | 1630.50 | 0.0 |
| baseline | sparse | heap | generated-owned | 845.311 (842.552–852.937) | 1.06 | 177.3 | 1608.50 | 0.0 |
| baseline | sparse | heap | native-view | 894.492 (885.355–4888.124) | 1.13 | 167.6 | 664.00 | 1165.5 |
| baseline | sparse | heap | native-eager | 1942.895 (1929.153–1968.668) | 2.44 | 77.2 | 1859.00 | 1488.0 |
| baseline | sparse | detach | stock | 1559.324 (1494.363–1654.574) | 1.00 | 96.1 | 1630.50 | 0.0 |
| baseline | sparse | detach | generated-owned | 1788.575 (1735.097–2240.162) | 1.15 | 83.8 | 1608.50 | 0.0 |
| baseline | sparse | detach | native-view | 10926.422 (10622.846–10946.486) | 7.01 | 13.7 | 4019.75 | 1165.5 |
| baseline | sparse | detach | native-eager | 14961.340 (14948.353–15044.452) | 9.59 | 10.0 | 5403.25 | 1488.0 |
| baseline | sparse | first | stock | 14.518 (14.113–14.538) | 1.00 | 10325.2 | 1.00 | 0.0 |
| baseline | sparse | first | generated-owned | 12.786 (11.808–12.959) | 0.88 | 11724.2 | 1.00 | 0.0 |
| baseline | sparse | first | native-view | 114.511 (109.824–121.716) | 7.89 | 1309.1 | 312.00 | 1165.5 |
| baseline | sparse | first | native-eager | 22.192 (21.887–24.263) | 1.53 | 6754.9 | 1.00 | 1488.0 |
| baseline | sparse | interpret | stock | 9.662 (9.625–9.740) | 1.00 | 15515.2 | 2.00 | 0.0 |
| baseline | sparse | interpret | generated-owned | 40.244 (39.512–40.397) | 4.17 | 3725.0 | 2.00 | 0.0 |
| baseline | sparse | interpret | native-view | 62.838 (62.723–63.221) | 6.50 | 2385.6 | 2.00 | 1165.5 |
| baseline | sparse | interpret | native-eager | 58.194 (58.168–59.517) | 6.02 | 2576.0 | 2.00 | 1488.0 |
| baseline | sparse | peak | stock | 11.781 (11.738–11.957) | 1.00 | 12724.5 | 1.00 | 0.0 |
| baseline | sparse | peak | generated-owned | 9.518 (9.476–9.537) | 0.81 | 15750.4 | 1.00 | 0.0 |
| baseline | sparse | peak | native-view | 20.343 (20.124–20.544) | 1.73 | 7368.9 | 1.00 | 1165.5 |
| baseline | sparse | peak | native-eager | 16.770 (16.667–16.771) | 1.42 | 8939.0 | 1.00 | 1488.0 |
| baseline | full | decode | stock | 773.775 (768.932–779.445) | 1.00 | 193.7 | 1629.50 | 0.0 |
| baseline | full | decode | generated-owned | 823.773 (814.238–834.176) | 1.06 | 182.0 | 1607.50 | 0.0 |
| baseline | full | decode | native-view | 693.348 (688.730–712.700) | 0.90 | 216.2 | 144.00 | 1165.5 |
| baseline | full | decode | native-eager | 1696.976 (1675.633–1723.321) | 2.19 | 88.3 | 1527.50 | 1488.0 |
| baseline | full | resident | stock | 855.074 (846.706–857.451) | 1.00 | 175.3 | 1630.50 | 0.0 |
| baseline | full | resident | generated-owned | 869.857 (862.756–883.050) | 1.02 | 172.3 | 1608.50 | 0.0 |
| baseline | full | resident | native-view | 1251.265 (1227.692–1690.563) | 1.46 | 119.8 | 1131.50 | 1165.5 |
| baseline | full | resident | native-eager | 1986.103 (1958.963–2399.767) | 2.32 | 75.5 | 1737.00 | 1488.0 |
| baseline | full | heap | stock | 843.509 (842.588–851.134) | 1.00 | 177.7 | 1630.50 | 0.0 |
| baseline | full | heap | generated-owned | 858.011 (855.688–865.723) | 1.02 | 174.7 | 1608.50 | 0.0 |
| baseline | full | heap | native-view | 1282.315 (1277.213–1284.698) | 1.52 | 116.9 | 1339.50 | 1165.5 |
| baseline | full | heap | native-eager | 2095.029 (2066.584–2114.400) | 2.48 | 71.6 | 1945.00 | 1488.0 |
| baseline | full | detach | stock | 846.168 (844.488–851.151) | 1.00 | 177.2 | 1630.50 | 0.0 |
| baseline | full | detach | generated-owned | 877.476 (859.833–894.520) | 1.04 | 170.8 | 1608.50 | 0.0 |
| baseline | full | detach | native-view | 5493.116 (5454.792–5535.717) | 6.49 | 27.3 | 4019.75 | 1165.5 |
| baseline | full | detach | native-eager | 6589.114 (6531.166–6695.579) | 7.79 | 22.8 | 5403.25 | 1488.0 |
| baseline | full | first | stock | 69.926 (64.550–70.537) | 1.00 | 2143.8 | 1.00 | 0.0 |
| baseline | full | first | generated-owned | 33.584 (33.556–34.532) | 0.48 | 4463.6 | 1.00 | 0.0 |
| baseline | full | first | native-view | 538.062 (530.875–540.072) | 7.69 | 278.6 | 987.50 | 1165.5 |
| baseline | full | first | native-eager | 80.059 (78.028–80.656) | 1.14 | 1872.4 | 1.00 | 1488.0 |
| baseline | full | interpret | stock | 61.921 (61.637–62.865) | 1.00 | 2420.9 | 2.00 | 0.0 |
| baseline | full | interpret | generated-owned | 291.211 (288.479–307.708) | 4.70 | 514.8 | 2.00 | 0.0 |
| baseline | full | interpret | native-view | 404.629 (399.471–408.490) | 6.53 | 370.5 | 2.00 | 1165.5 |
| baseline | full | interpret | native-eager | 343.506 (342.680–350.233) | 5.55 | 436.4 | 2.00 | 1488.0 |
| baseline | full | peak | stock | 105.380 (105.141–105.462) | 1.00 | 1422.5 | 1.00 | 0.0 |
| baseline | full | peak | generated-owned | 45.261 (44.975–45.623) | 0.43 | 3312.0 | 1.00 | 0.0 |
| baseline | full | peak | native-view | 171.940 (171.638–172.370) | 1.63 | 871.9 | 1.00 | 1165.5 |
| baseline | full | peak | native-eager | 126.562 (125.603–126.623) | 1.20 | 1184.4 | 1.00 | 1488.0 |
| baseline | holdout | resident | stock | 2121.338 (2106.133–2129.647) | 1.00 | 240.4 | 5310.00 | 0.0 |
| baseline | holdout | resident | generated-owned | 2214.118 (2213.181–2230.330) | 1.04 | 230.4 | 5427.00 | 0.0 |
| baseline | holdout | resident | native-view | 2863.243 (2834.431–2864.518) | 1.35 | 178.1 | 3913.25 | 3315.0 |
| baseline | holdout | resident | native-eager | 4534.072 (4517.905–4567.696) | 2.14 | 112.5 | 5476.25 | 5184.0 |
| baseline | holdout | peak | stock | 133.853 (133.458–135.363) | 1.00 | 3810.4 | 1.00 | 0.0 |
| baseline | holdout | peak | generated-owned | 52.424 (51.553–52.557) | 0.39 | 9729.0 | 1.00 | 0.0 |
| baseline | holdout | peak | native-view | 192.946 (192.845–196.066) | 1.44 | 2643.4 | 1.00 | 3315.0 |
| baseline | holdout | peak | native-eager | 132.189 (131.493–133.109) | 0.99 | 3858.3 | 1.00 | 5184.0 |
| baseline | large | decode | stock | 223700.522 (223458.589–244138.670) | 1.00 | 231.7 | 433008.00 | 0.0 |
| baseline | large | decode | generated-owned | 277054.228 (276444.578–288800.057) | 1.24 | 187.0 | 460179.00 | 0.0 |
| baseline | large | decode | native-view | 198510.305 (198363.484–199764.309) | 0.89 | 261.1 | 144.00 | 409153.5 |
| baseline | large | decode | native-eager | 541430.792 (535690.094–543726.031) | 2.42 | 95.7 | 407572.00 | 393216.0 |
| baseline | large | resident | stock | 245739.464 (243951.112–269907.888) | 1.00 | 210.9 | 433009.00 | 0.0 |
| baseline | large | resident | generated-owned | 272996.448 (260579.776–285498.292) | 1.11 | 189.8 | 460180.00 | 0.0 |
| baseline | large | resident | native-view | 368393.094 (364517.650–370496.331) | 1.50 | 140.7 | 263984.00 | 409153.5 |
| baseline | large | resident | native-eager | 698739.365 (686630.490–707822.823) | 2.84 | 74.2 | 480239.50 | 393216.0 |
| baseline | large | peak | stock | 17096.082 (16240.043–18000.086) | 1.00 | 3031.2 | 1.00 | 0.0 |
| baseline | large | peak | generated-owned | 5304.775 (5277.635–6773.110) | 0.31 | 9769.0 | 1.00 | 0.0 |
| baseline | large | peak | native-view | 27703.676 (27517.679–27909.906) | 1.62 | 1870.6 | 1.00 | 409153.5 |
| baseline | large | peak | native-eager | 17350.734 (17333.417–17589.753) | 1.01 | 2986.7 | 1.00 | 393216.0 |
