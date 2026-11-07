# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as retained index/header bytes. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 774.2 | 824.2 (1.06×) | 699.2 (0.90×) | 1408.8 (1.82×) |
| sparse | resident | 787.5 | 842.0 (1.07×) | 793.1 (1.01×) | 1516.4 (1.93×) |
| sparse | heap | 786.0 | 850.6 (1.08×) | 883.7 (1.12×) | 1636.3 (2.08×) |
| sparse | detach | 1514.7 | 1718.5 (1.13×) | 11001.4 (7.26×) | 13040.7 (8.61×) |
| sparse | first | 14.9 | 13.1 (0.88×) | 123.4 (8.29×) | 23.1 (1.55×) |
| sparse | interpret | 10.7 | 36.8 (3.44×) | 66.6 (6.24×) | 38.4 (3.60×) |
| sparse | peak | 11.9 | 10.4 (0.88×) | 22.1 (1.87×) | 16.7 (1.41×) |
| full | decode | 769.4 | 891.7 (1.16×) | 691.6 (0.90×) | 1399.0 (1.82×) |
| full | resident | 845.5 | 866.1 (1.02×) | 1225.0 (1.45×) | 1669.7 (1.97×) |
| full | heap | 864.1 | 887.1 (1.03×) | 1340.1 (1.55×) | 1793.9 (2.08×) |
| full | detach | 844.7 | 872.3 (1.03×) | 5528.3 (6.55×) | 6374.4 (7.55×) |
| full | first | 64.0 | 34.9 (0.55×) | 539.2 (8.43×) | 75.4 (1.18×) |
| full | interpret | 61.4 | 256.8 (4.18×) | 407.8 (6.64×) | 238.3 (3.88×) |
| full | peak | 108.2 | 48.9 (0.45×) | 170.8 (1.58×) | 116.9 (1.08×) |
| holdout | resident | 2136.2 | 2227.5 (1.04×) | 3012.2 (1.41×) | 4033.3 (1.89×) |
| holdout | peak | 142.2 | 57.4 (0.40×) | 196.6 (1.38×) | 126.1 (0.89×) |
| large | decode | 229715.1 | 267467.5 (1.16×) | 287478.1 (1.25×) | 468444.9 (2.04×) |
| large | resident | 269008.7 | 286604.8 (1.07×) | 367662.7 (1.37×) | 561172.4 (2.09×) |
| large | peak | 16179.0 | 6374.3 (0.39×) | 28858.1 (1.78×) | 16717.9 (1.03×) |

## baseline: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 785.7 | 813.8 (1.04×) | 700.9 (0.89×) | 1465.8 (1.87×) |
| sparse | resident | 795.1 | 818.9 (1.03×) | 804.1 (1.01×) | 1560.2 (1.96×) |
| sparse | heap | 794.9 | 816.2 (1.03×) | 945.6 (1.19×) | 1681.2 (2.12×) |
| sparse | detach | 1494.8 | 1798.3 (1.20×) | 10744.8 (7.19×) | 13943.8 (9.33×) |
| sparse | first | 18.0 | 14.9 (0.83×) | 120.8 (6.70×) | 25.5 (1.42×) |
| sparse | interpret | 10.2 | 41.5 (4.07×) | 67.7 (6.64×) | 61.2 (5.99×) |
| sparse | peak | 12.8 | 9.8 (0.76×) | 20.7 (1.62×) | 17.0 (1.33×) |
| full | decode | 782.0 | 813.1 (1.04×) | 700.8 (0.90×) | 1469.7 (1.88×) |
| full | resident | 857.4 | 865.7 (1.01×) | 1234.8 (1.44×) | 1716.3 (2.00×) |
| full | heap | 863.0 | 888.0 (1.03×) | 1296.0 (1.50×) | 1873.2 (2.17×) |
| full | detach | 847.2 | 853.9 (1.01×) | 5450.9 (6.43×) | 6464.9 (7.63×) |
| full | first | 64.4 | 34.0 (0.53×) | 540.5 (8.39×) | 76.7 (1.19×) |
| full | interpret | 61.6 | 288.7 (4.69×) | 398.8 (6.47×) | 342.8 (5.57×) |
| full | peak | 105.1 | 44.8 (0.43×) | 171.0 (1.63×) | 122.2 (1.16×) |
| holdout | resident | 2139.2 | 2166.8 (1.01×) | 2863.4 (1.34×) | 4376.9 (2.05×) |
| holdout | peak | 138.4 | 53.5 (0.39×) | 203.0 (1.47×) | 132.8 (0.96×) |
| large | decode | 229036.1 | 279207.5 (1.22×) | 207896.1 (0.91×) | 455690.7 (1.99×) |
| large | resident | 240139.1 | 261100.1 (1.09×) | 385274.0 (1.60×) | 573581.7 (2.39×) |
| large | peak | 16482.4 | 5348.8 (0.32×) | 28738.8 (1.74×) | 18033.5 (1.09×) |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row |
|---|---|---|---|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 774.250 (768.319–786.667) | 1.00 | 193.6 | 1629.50 | 0.0 |
| candidate | sparse | decode | generated-owned | 824.161 (822.480–842.625) | 1.06 | 181.9 | 1607.50 | 0.0 |
| candidate | sparse | decode | native-view | 699.217 (696.160–717.771) | 0.90 | 214.4 | 144.00 | 1165.5 |
| candidate | sparse | decode | native-eager | 1408.838 (1402.723–1415.219) | 1.82 | 106.4 | 1214.25 | 1141.5 |
| candidate | sparse | resident | stock | 787.475 (786.482–796.907) | 1.00 | 190.4 | 1630.50 | 0.0 |
| candidate | sparse | resident | generated-owned | 842.044 (832.918–850.636) | 1.07 | 178.0 | 1608.50 | 0.0 |
| candidate | sparse | resident | native-view | 793.124 (779.717–963.652) | 1.01 | 189.0 | 456.00 | 1165.5 |
| candidate | sparse | resident | native-eager | 1516.436 (1512.480–1522.030) | 1.93 | 98.9 | 1337.75 | 1141.5 |
| candidate | sparse | heap | stock | 786.035 (785.267–793.005) | 1.00 | 190.7 | 1630.50 | 0.0 |
| candidate | sparse | heap | generated-owned | 850.649 (832.897–5340.785) | 1.08 | 176.2 | 1608.50 | 0.0 |
| candidate | sparse | heap | native-view | 883.659 (881.361–913.136) | 1.12 | 169.6 | 664.00 | 1165.5 |
| candidate | sparse | heap | native-eager | 1636.327 (1626.675–1637.736) | 2.08 | 91.6 | 1545.75 | 1141.5 |
| candidate | sparse | detach | stock | 1514.663 (1456.291–1533.105) | 1.00 | 99.0 | 1630.50 | 0.0 |
| candidate | sparse | detach | generated-owned | 1718.538 (1712.955–1722.389) | 1.13 | 87.2 | 1608.50 | 0.0 |
| candidate | sparse | detach | native-view | 11001.449 (10652.537–12181.162) | 7.26 | 13.6 | 4019.75 | 1165.5 |
| candidate | sparse | detach | native-eager | 13040.726 (12369.931–13555.063) | 8.61 | 11.5 | 5090.00 | 1141.5 |
| candidate | sparse | first | stock | 14.885 (14.204–15.035) | 1.00 | 10071.1 | 1.00 | 0.0 |
| candidate | sparse | first | generated-owned | 13.079 (12.988–13.117) | 0.88 | 11461.2 | 1.00 | 0.0 |
| candidate | sparse | first | native-view | 123.366 (113.672–126.280) | 8.29 | 1215.1 | 312.00 | 1165.5 |
| candidate | sparse | first | native-eager | 23.088 (21.224–23.803) | 1.55 | 6492.9 | 1.00 | 1141.5 |
| candidate | sparse | interpret | stock | 10.679 (9.900–10.991) | 1.00 | 14037.2 | 2.00 | 0.0 |
| candidate | sparse | interpret | generated-owned | 36.781 (36.477–38.374) | 3.44 | 4075.7 | 2.00 | 0.0 |
| candidate | sparse | interpret | native-view | 66.635 (63.256–67.007) | 6.24 | 2249.7 | 2.00 | 1165.5 |
| candidate | sparse | interpret | native-eager | 38.446 (36.137–39.893) | 3.60 | 3899.1 | 2.00 | 1141.5 |
| candidate | sparse | peak | stock | 11.860 (11.671–12.658) | 1.00 | 12639.6 | 1.00 | 0.0 |
| candidate | sparse | peak | generated-owned | 10.396 (9.977–10.489) | 0.88 | 14420.0 | 1.00 | 0.0 |
| candidate | sparse | peak | native-view | 22.141 (20.456–22.895) | 1.87 | 6770.5 | 1.00 | 1165.5 |
| candidate | sparse | peak | native-eager | 16.748 (16.609–18.718) | 1.41 | 8950.5 | 1.00 | 1141.5 |
| candidate | full | decode | stock | 769.376 (764.668–773.255) | 1.00 | 194.8 | 1629.50 | 0.0 |
| candidate | full | decode | generated-owned | 891.703 (829.313–896.637) | 1.16 | 168.1 | 1607.50 | 0.0 |
| candidate | full | decode | native-view | 691.562 (688.223–700.042) | 0.90 | 216.8 | 144.00 | 1165.5 |
| candidate | full | decode | native-eager | 1399.041 (1388.326–1412.910) | 1.82 | 107.1 | 1214.25 | 1141.5 |
| candidate | full | resident | stock | 845.494 (844.492–850.513) | 1.00 | 177.3 | 1630.50 | 0.0 |
| candidate | full | resident | generated-owned | 866.087 (862.639–876.505) | 1.02 | 173.1 | 1608.50 | 0.0 |
| candidate | full | resident | native-view | 1225.041 (1205.740–1227.618) | 1.45 | 122.4 | 1131.50 | 1165.5 |
| candidate | full | resident | native-eager | 1669.650 (1655.984–1674.914) | 1.97 | 89.8 | 1423.75 | 1141.5 |
| candidate | full | heap | stock | 864.093 (863.503–876.881) | 1.00 | 173.5 | 1630.50 | 0.0 |
| candidate | full | heap | generated-owned | 887.081 (863.434–907.704) | 1.03 | 169.0 | 1608.50 | 0.0 |
| candidate | full | heap | native-view | 1340.139 (1270.927–1429.828) | 1.55 | 111.9 | 1339.50 | 1165.5 |
| candidate | full | heap | native-eager | 1793.915 (1776.358–1871.649) | 2.08 | 83.6 | 1631.75 | 1141.5 |
| candidate | full | detach | stock | 844.658 (839.240–847.795) | 1.00 | 177.5 | 1630.50 | 0.0 |
| candidate | full | detach | generated-owned | 872.301 (871.158–877.523) | 1.03 | 171.9 | 1608.50 | 0.0 |
| candidate | full | detach | native-view | 5528.311 (5514.927–5582.972) | 6.55 | 27.1 | 4019.75 | 1165.5 |
| candidate | full | detach | native-eager | 6374.444 (6295.832–6433.264) | 7.55 | 23.5 | 5090.00 | 1141.5 |
| candidate | full | first | stock | 63.972 (63.947–65.849) | 1.00 | 2343.3 | 1.00 | 0.0 |
| candidate | full | first | generated-owned | 34.879 (34.867–36.020) | 0.55 | 4297.8 | 1.00 | 0.0 |
| candidate | full | first | native-view | 539.199 (531.253–549.317) | 8.43 | 278.0 | 987.50 | 1165.5 |
| candidate | full | first | native-eager | 75.413 (70.858–78.528) | 1.18 | 1987.8 | 1.00 | 1141.5 |
| candidate | full | interpret | stock | 61.419 (61.063–61.825) | 1.00 | 2440.7 | 2.00 | 0.0 |
| candidate | full | interpret | generated-owned | 256.832 (256.624–257.351) | 4.18 | 583.7 | 2.00 | 0.0 |
| candidate | full | interpret | native-view | 407.761 (405.179–410.959) | 6.64 | 367.6 | 2.00 | 1165.5 |
| candidate | full | interpret | native-eager | 238.295 (237.302–239.186) | 3.88 | 629.1 | 2.00 | 1141.5 |
| candidate | full | peak | stock | 108.248 (108.165–108.385) | 1.00 | 1384.8 | 1.00 | 0.0 |
| candidate | full | peak | generated-owned | 48.890 (48.845–49.016) | 0.45 | 3066.2 | 1.00 | 0.0 |
| candidate | full | peak | native-view | 170.830 (170.557–172.594) | 1.58 | 877.5 | 1.00 | 1165.5 |
| candidate | full | peak | native-eager | 116.890 (116.680–123.422) | 1.08 | 1282.5 | 1.00 | 1141.5 |
| candidate | holdout | resident | stock | 2136.179 (2124.019–2176.610) | 1.00 | 238.8 | 5310.00 | 0.0 |
| candidate | holdout | resident | generated-owned | 2227.498 (2212.889–2270.364) | 1.04 | 229.0 | 5427.00 | 0.0 |
| candidate | holdout | resident | native-view | 3012.189 (2879.215–3104.298) | 1.41 | 169.3 | 3913.25 | 3315.0 |
| candidate | holdout | resident | native-eager | 4033.326 (4023.802–4088.292) | 1.89 | 126.5 | 4684.25 | 3291.0 |
| candidate | holdout | peak | stock | 142.189 (140.456–148.360) | 1.00 | 3587.0 | 1.00 | 0.0 |
| candidate | holdout | peak | generated-owned | 57.382 (56.729–58.288) | 0.40 | 8888.3 | 1.00 | 0.0 |
| candidate | holdout | peak | native-view | 196.552 (196.450–205.685) | 1.38 | 2594.9 | 1.00 | 3315.0 |
| candidate | holdout | peak | native-eager | 126.056 (125.258–128.108) | 0.89 | 4046.1 | 1.00 | 3291.0 |
| candidate | large | decode | stock | 229715.080 (221330.277–231574.732) | 1.00 | 225.6 | 433008.00 | 0.0 |
| candidate | large | decode | generated-owned | 267467.500 (265351.130–273755.156) | 1.16 | 193.8 | 460179.00 | 0.0 |
| candidate | large | decode | native-view | 287478.135 (285000.823–292274.365) | 1.25 | 180.3 | 144.00 | 409153.5 |
| candidate | large | decode | native-eager | 468444.938 (450279.625–489097.711) | 2.04 | 110.6 | 316593.75 | 409129.5 |
| candidate | large | resident | stock | 269008.740 (245265.268–270718.745) | 1.00 | 192.6 | 433009.00 | 0.0 |
| candidate | large | resident | generated-owned | 286604.760 (276436.708–290528.286) | 1.07 | 180.8 | 460180.00 | 0.0 |
| candidate | large | resident | native-view | 367662.700 (367296.581–398915.773) | 1.37 | 141.0 | 263984.00 | 409153.5 |
| candidate | large | resident | native-eager | 561172.385 (558514.156–562562.490) | 2.09 | 92.3 | 389261.25 | 409129.5 |
| candidate | large | peak | stock | 16179.006 (15962.213–16523.737) | 1.00 | 3203.1 | 1.00 | 0.0 |
| candidate | large | peak | generated-owned | 6374.304 (6119.297–6399.836) | 0.39 | 8129.9 | 1.00 | 0.0 |
| candidate | large | peak | native-view | 28858.080 (27204.448–29808.383) | 1.78 | 1795.8 | 1.00 | 409153.5 |
| candidate | large | peak | native-eager | 16717.894 (16393.055–16778.900) | 1.03 | 3099.8 | 1.00 | 409129.5 |
| baseline | sparse | decode | stock | 785.743 (784.526–788.810) | 1.00 | 190.8 | 1629.50 | 0.0 |
| baseline | sparse | decode | generated-owned | 813.839 (807.716–814.667) | 1.04 | 184.2 | 1607.50 | 0.0 |
| baseline | sparse | decode | native-view | 700.901 (699.498–712.705) | 0.89 | 213.9 | 144.00 | 1165.5 |
| baseline | sparse | decode | native-eager | 1465.807 (1461.698–1469.616) | 1.87 | 102.3 | 1214.25 | 1141.5 |
| baseline | sparse | resident | stock | 795.068 (788.965–795.994) | 1.00 | 188.5 | 1630.50 | 0.0 |
| baseline | sparse | resident | generated-owned | 818.915 (813.624–826.078) | 1.03 | 183.1 | 1608.50 | 0.0 |
| baseline | sparse | resident | native-view | 804.119 (800.835–805.024) | 1.01 | 186.4 | 456.00 | 1165.5 |
| baseline | sparse | resident | native-eager | 1560.219 (1560.113–1570.132) | 1.96 | 96.1 | 1337.75 | 1141.5 |
| baseline | sparse | heap | stock | 794.890 (792.564–797.702) | 1.00 | 188.6 | 1630.50 | 0.0 |
| baseline | sparse | heap | generated-owned | 816.163 (811.517–820.102) | 1.03 | 183.7 | 1608.50 | 0.0 |
| baseline | sparse | heap | native-view | 945.554 (879.036–1695.259) | 1.19 | 158.5 | 664.00 | 1165.5 |
| baseline | sparse | heap | native-eager | 1681.206 (1674.796–1688.935) | 2.12 | 89.2 | 1545.75 | 1141.5 |
| baseline | sparse | detach | stock | 1494.816 (1484.257–1506.840) | 1.00 | 100.3 | 1630.50 | 0.0 |
| baseline | sparse | detach | generated-owned | 1798.270 (1696.112–1803.213) | 1.20 | 83.4 | 1608.50 | 0.0 |
| baseline | sparse | detach | native-view | 10744.793 (10684.313–10779.614) | 7.19 | 14.0 | 4019.75 | 1165.5 |
| baseline | sparse | detach | native-eager | 13943.849 (13386.199–14239.369) | 9.33 | 10.8 | 5090.00 | 1141.5 |
| baseline | sparse | first | stock | 18.021 (14.373–18.119) | 1.00 | 8318.3 | 1.00 | 0.0 |
| baseline | sparse | first | generated-owned | 14.898 (13.553–20.586) | 0.83 | 10062.3 | 1.00 | 0.0 |
| baseline | sparse | first | native-view | 120.820 (120.159–122.868) | 6.70 | 1240.7 | 312.00 | 1165.5 |
| baseline | sparse | first | native-eager | 25.519 (24.616–28.735) | 1.42 | 5874.3 | 1.00 | 1141.5 |
| baseline | sparse | interpret | stock | 10.206 (9.778–10.623) | 1.00 | 14688.3 | 2.00 | 0.0 |
| baseline | sparse | interpret | generated-owned | 41.547 (40.455–43.257) | 4.07 | 3608.1 | 2.00 | 0.0 |
| baseline | sparse | interpret | native-view | 67.736 (65.876–87.009) | 6.64 | 2213.1 | 2.00 | 1165.5 |
| baseline | sparse | interpret | native-eager | 61.166 (60.426–64.600) | 5.99 | 2450.8 | 2.00 | 1141.5 |
| baseline | sparse | peak | stock | 12.784 (11.749–13.639) | 1.00 | 11725.7 | 1.00 | 0.0 |
| baseline | sparse | peak | generated-owned | 9.767 (9.694–9.827) | 0.76 | 15348.9 | 1.00 | 0.0 |
| baseline | sparse | peak | native-view | 20.670 (20.587–24.093) | 1.62 | 7252.2 | 1.00 | 1165.5 |
| baseline | sparse | peak | native-eager | 17.023 (16.934–17.127) | 1.33 | 8806.3 | 1.00 | 1141.5 |
| baseline | full | decode | stock | 781.969 (776.036–796.492) | 1.00 | 191.7 | 1629.50 | 0.0 |
| baseline | full | decode | generated-owned | 813.065 (810.542–817.823) | 1.04 | 184.4 | 1607.50 | 0.0 |
| baseline | full | decode | native-view | 700.795 (697.779–704.527) | 0.90 | 213.9 | 144.00 | 1165.5 |
| baseline | full | decode | native-eager | 1469.681 (1453.874–1548.858) | 1.88 | 102.0 | 1214.25 | 1141.5 |
| baseline | full | resident | stock | 857.440 (856.213–864.806) | 1.00 | 174.8 | 1630.50 | 0.0 |
| baseline | full | resident | generated-owned | 865.678 (843.544–872.476) | 1.01 | 173.2 | 1608.50 | 0.0 |
| baseline | full | resident | native-view | 1234.755 (1215.385–1240.950) | 1.44 | 121.4 | 1131.50 | 1165.5 |
| baseline | full | resident | native-eager | 1716.332 (1705.802–1718.002) | 2.00 | 87.3 | 1423.75 | 1141.5 |
| baseline | full | heap | stock | 862.983 (851.973–877.811) | 1.00 | 173.7 | 1630.50 | 0.0 |
| baseline | full | heap | generated-owned | 888.020 (859.627–907.674) | 1.03 | 168.8 | 1608.50 | 0.0 |
| baseline | full | heap | native-view | 1295.951 (1293.623–1318.818) | 1.50 | 115.7 | 1339.50 | 1165.5 |
| baseline | full | heap | native-eager | 1873.221 (1872.148–18587.742) | 2.17 | 80.0 | 1631.75 | 1141.5 |
| baseline | full | detach | stock | 847.243 (844.728–882.341) | 1.00 | 176.9 | 1630.50 | 0.0 |
| baseline | full | detach | generated-owned | 853.894 (853.688–862.016) | 1.01 | 175.6 | 1608.50 | 0.0 |
| baseline | full | detach | native-view | 5450.948 (5443.986–5716.999) | 6.43 | 27.5 | 4019.75 | 1165.5 |
| baseline | full | detach | native-eager | 6464.879 (6394.994–6506.991) | 7.63 | 23.2 | 5090.00 | 1141.5 |
| baseline | full | first | stock | 64.448 (63.706–77.315) | 1.00 | 2326.0 | 1.00 | 0.0 |
| baseline | full | first | generated-owned | 33.983 (33.919–34.239) | 0.53 | 4411.2 | 1.00 | 0.0 |
| baseline | full | first | native-view | 540.516 (539.422–543.207) | 8.39 | 277.3 | 987.50 | 1165.5 |
| baseline | full | first | native-eager | 76.738 (75.685–77.644) | 1.19 | 1953.5 | 1.00 | 1141.5 |
| baseline | full | interpret | stock | 61.595 (61.108–61.856) | 1.00 | 2433.7 | 2.00 | 0.0 |
| baseline | full | interpret | generated-owned | 288.690 (288.067–289.153) | 4.69 | 519.3 | 2.00 | 0.0 |
| baseline | full | interpret | native-view | 398.773 (398.206–399.342) | 6.47 | 375.9 | 2.00 | 1165.5 |
| baseline | full | interpret | native-eager | 342.848 (339.265–348.039) | 5.57 | 437.2 | 2.00 | 1141.5 |
| baseline | full | peak | stock | 105.055 (104.456–105.473) | 1.00 | 1426.9 | 1.00 | 0.0 |
| baseline | full | peak | generated-owned | 44.836 (44.444–44.945) | 0.43 | 3343.4 | 1.00 | 0.0 |
| baseline | full | peak | native-view | 171.038 (171.034–171.598) | 1.63 | 876.4 | 1.00 | 1165.5 |
| baseline | full | peak | native-eager | 122.246 (121.915–122.925) | 1.16 | 1226.3 | 1.00 | 1141.5 |
| baseline | holdout | resident | stock | 2139.158 (2126.756–2415.218) | 1.00 | 238.4 | 5310.00 | 0.0 |
| baseline | holdout | resident | generated-owned | 2166.782 (2161.336–2167.728) | 1.01 | 235.4 | 5427.00 | 0.0 |
| baseline | holdout | resident | native-view | 2863.352 (2840.438–2888.545) | 1.34 | 178.1 | 3913.25 | 3315.0 |
| baseline | holdout | resident | native-eager | 4376.877 (4120.864–4702.476) | 2.05 | 116.5 | 4684.25 | 3291.0 |
| baseline | holdout | peak | stock | 138.409 (135.560–139.883) | 1.00 | 3685.0 | 1.00 | 0.0 |
| baseline | holdout | peak | generated-owned | 53.496 (52.706–69.910) | 0.39 | 9533.9 | 1.00 | 0.0 |
| baseline | holdout | peak | native-view | 203.033 (202.459–207.541) | 1.47 | 2512.1 | 1.00 | 3315.0 |
| baseline | holdout | peak | native-eager | 132.833 (130.859–135.220) | 0.96 | 3839.6 | 1.00 | 3291.0 |
| baseline | large | decode | stock | 229036.107 (225483.043–263546.818) | 1.00 | 226.3 | 433008.00 | 0.0 |
| baseline | large | decode | generated-owned | 279207.505 (251227.058–295419.490) | 1.22 | 185.6 | 460179.00 | 0.0 |
| baseline | large | decode | native-view | 207896.090 (199326.551–270613.271) | 0.91 | 249.3 | 144.00 | 409153.5 |
| baseline | large | decode | native-eager | 455690.688 (450925.742–469938.203) | 1.99 | 113.7 | 316593.75 | 409129.5 |
| baseline | large | resident | stock | 240139.062 (223472.174–246425.094) | 1.00 | 215.8 | 433009.00 | 0.0 |
| baseline | large | resident | generated-owned | 261100.146 (252033.888–263084.620) | 1.09 | 198.5 | 460180.00 | 0.0 |
| baseline | large | resident | native-view | 385273.975 (383298.675–409773.431) | 1.60 | 134.5 | 263984.00 | 409153.5 |
| baseline | large | resident | native-eager | 573581.667 (570488.135–592388.562) | 2.39 | 90.3 | 389261.25 | 409129.5 |
| baseline | large | peak | stock | 16482.384 (16445.676–16520.477) | 1.00 | 3144.1 | 1.00 | 0.0 |
| baseline | large | peak | generated-owned | 5348.835 (5342.233–6244.272) | 0.32 | 9688.5 | 1.00 | 0.0 |
| baseline | large | peak | native-view | 28738.751 (27068.282–28784.194) | 1.74 | 1803.2 | 1.00 | 409153.5 |
| baseline | large | peak | native-eager | 18033.464 (17937.031–18328.649) | 1.09 | 2873.7 | 1.00 | 409129.5 |
