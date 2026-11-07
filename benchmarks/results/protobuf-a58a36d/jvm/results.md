# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as retained index/header bytes. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 565.6 | 557.2 (0.99×) | 1018.4 (1.80×) | 1758.3 (3.11×) |
| sparse | resident | 302.3 | 353.8 (1.17×) | 856.1 (2.83×) | 1252.0 (4.14×) |
| sparse | heap | 317.8 | 354.6 (1.12×) | 913.3 (2.87×) | 1322.9 (4.16×) |
| sparse | detach | 675.3 | 919.5 (1.36×) | 2461.5 (3.65×) | 2962.7 (4.39×) |
| sparse | first | 10.1 | 10.2 (1.01×) | 145.1 (14.36×) | 31.3 (3.10×) |
| sparse | interpret | 7.7 | 13.7 (1.78×) | 33.6 (4.36×) | 27.6 (3.58×) |
| sparse | peak | 9.8 | 8.5 (0.87×) | 26.0 (2.65×) | 15.3 (1.55×) |
| full | decode | 281.8 | 352.9 (1.25×) | 695.7 (2.47×) | 1143.9 (4.06×) |
| full | resident | 436.2 | 620.5 (1.42×) | 1699.7 (3.90×) | 2051.2 (4.70×) |
| full | heap | 267.6 | 385.2 (1.44×) | 1200.7 (4.49×) | 1457.6 (5.45×) |
| full | detach | 451.4 | 599.3 (1.33×) | 4417.0 (9.79×) | 5440.5 (12.05×) |
| full | first | 28.8 | 32.5 (1.13×) | 439.0 (15.26×) | 63.9 (2.22×) |
| full | interpret | 31.9 | 43.3 (1.36×) | 181.4 (5.69×) | 111.2 (3.48×) |
| full | peak | 23.0 | 22.2 (0.96×) | 97.0 (4.22×) | 40.7 (1.77×) |
| holdout | resident | 1022.1 | 1474.7 (1.44×) | 3504.0 (3.43×) | 3920.8 (3.84×) |
| holdout | peak | 52.2 | 48.7 (0.93×) | 265.8 (5.09×) | 129.4 (2.48×) |
| large | decode | 131558.0 | 140902.4 (1.07×) | 284198.9 (2.16×) | 338145.3 (2.57×) |
| large | resident | 225144.2 | 149764.4 (0.67×) | 312859.4 (1.39×) | 365576.2 (1.62×) |
| large | peak | 11784.3 | 13041.7 (1.11×) | 56067.1 (4.76×) | 24087.0 (2.04×) |

## baseline: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 418.0 | 560.8 (1.34×) | 1031.6 (2.47×) | 1849.2 (4.42×) |
| sparse | resident | 402.1 | 360.0 (0.90×) | 851.1 (2.12×) | 1298.3 (3.23×) |
| sparse | heap | 296.1 | 351.4 (1.19×) | 906.8 (3.06×) | 1376.4 (4.65×) |
| sparse | detach | 687.9 | 933.7 (1.36×) | 2370.5 (3.45×) | 2914.1 (4.24×) |
| sparse | first | 9.9 | 10.5 (1.06×) | 144.3 (14.54×) | 28.5 (2.87×) |
| sparse | interpret | 8.0 | 11.6 (1.45×) | 35.3 (4.41×) | 27.9 (3.48×) |
| sparse | peak | 8.7 | 8.5 (0.99×) | 21.9 (2.52×) | 16.6 (1.91×) |
| full | decode | 528.0 | 342.6 (0.65×) | 702.7 (1.33×) | 1200.3 (2.27×) |
| full | resident | 444.3 | 604.9 (1.36×) | 1665.6 (3.75×) | 2114.7 (4.76×) |
| full | heap | 276.2 | 378.0 (1.37×) | 1215.2 (4.40×) | 1485.1 (5.38×) |
| full | detach | 438.7 | 609.2 (1.39×) | 5407.2 (12.32×) | 7203.0 (16.42×) |
| full | first | 29.1 | 32.7 (1.12×) | 423.9 (14.55×) | 64.1 (2.20×) |
| full | interpret | 30.3 | 44.3 (1.46×) | 183.1 (6.04×) | 112.7 (3.72×) |
| full | peak | 22.4 | 21.8 (0.97×) | 84.9 (3.79×) | 41.4 (1.85×) |
| holdout | resident | 988.8 | 1558.9 (1.58×) | 3361.5 (3.40×) | 4157.0 (4.20×) |
| holdout | peak | 55.2 | 48.3 (0.87×) | 268.0 (4.85×) | 136.7 (2.48×) |
| large | decode | 129602.8 | 133066.9 (1.03×) | 290414.5 (2.24×) | 362599.4 (2.80×) |
| large | resident | 125722.8 | 148742.6 (1.18×) | 317725.6 (2.53×) | 455709.6 (3.62×) |
| large | peak | 11260.1 | 13624.7 (1.21×) | 55961.4 (4.97×) | 29301.6 (2.60×) |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row |
|---|---|---|---|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 565.607 (400.148–1457.518) | 1.00 | 265.0 | 1391.50 | 0.0 |
| candidate | sparse | decode | generated-owned | 557.151 (555.038–561.186) | 0.99 | 269.1 | 1837.25 | 0.0 |
| candidate | sparse | decode | native-view | 1018.375 (1011.902–1027.263) | 1.80 | 147.2 | 336.00 | 1165.5 |
| candidate | sparse | decode | native-eager | 1758.275 (1714.069–1897.188) | 3.11 | 85.3 | 1782.25 | 1141.5 |
| candidate | sparse | resident | stock | 302.312 (285.716–401.839) | 1.00 | 495.9 | 1393.00 | 0.0 |
| candidate | sparse | resident | generated-owned | 353.825 (353.765–380.276) | 1.17 | 423.7 | 1838.75 | 0.0 |
| candidate | sparse | resident | native-view | 856.137 (837.163–863.576) | 2.83 | 175.1 | 1017.25 | 1165.5 |
| candidate | sparse | resident | native-eager | 1252.012 (1226.619–1280.917) | 4.14 | 119.7 | 1962.00 | 1141.5 |
| candidate | sparse | heap | stock | 317.781 (253.353–540.505) | 1.00 | 471.7 | 1393.00 | 0.0 |
| candidate | sparse | heap | generated-owned | 354.552 (351.199–583.595) | 1.12 | 422.8 | 1838.75 | 0.0 |
| candidate | sparse | heap | native-view | 913.304 (907.112–956.489) | 2.87 | 164.1 | 1345.25 | 1165.5 |
| candidate | sparse | heap | native-eager | 1322.921 (1290.407–1325.048) | 4.16 | 113.3 | 2290.00 | 1141.5 |
| candidate | sparse | detach | stock | 675.293 (654.171–703.472) | 1.00 | 222.0 | 1393.00 | 0.0 |
| candidate | sparse | detach | generated-owned | 919.503 (429.697–1053.977) | 1.36 | 163.0 | 1838.75 | 0.0 |
| candidate | sparse | detach | native-view | 2461.548 (2447.533–2592.225) | 3.65 | 60.9 | 4494.25 | 1165.5 |
| candidate | sparse | detach | native-eager | 2962.743 (2798.570–3165.947) | 4.39 | 50.6 | 5940.50 | 1141.5 |
| candidate | sparse | first | stock | 10.104 (9.981–12.967) | 1.00 | 14836.2 | 1.50 | 0.0 |
| candidate | sparse | first | generated-owned | 10.194 (10.169–11.128) | 1.01 | 14706.0 | 1.50 | 0.0 |
| candidate | sparse | first | native-view | 145.108 (142.605–146.097) | 14.36 | 1033.1 | 681.25 | 1165.5 |
| candidate | sparse | first | native-eager | 31.313 (25.145–32.989) | 3.10 | 4787.4 | 1.50 | 1141.5 |
| candidate | sparse | interpret | stock | 7.708 (7.695–7.770) | 1.00 | 19448.0 | 1.50 | 0.0 |
| candidate | sparse | interpret | generated-owned | 13.750 (13.556–15.467) | 1.78 | 10902.5 | 1.50 | 0.0 |
| candidate | sparse | interpret | native-view | 33.631 (32.814–33.662) | 4.36 | 4457.4 | 1.50 | 1165.5 |
| candidate | sparse | interpret | native-eager | 27.625 (26.324–33.724) | 3.58 | 5426.5 | 1.50 | 1141.5 |
| candidate | sparse | peak | stock | 9.822 (7.808–10.236) | 1.00 | 15262.8 | 1.50 | 0.0 |
| candidate | sparse | peak | generated-owned | 8.509 (8.319–8.805) | 0.87 | 17617.7 | 1.50 | 0.0 |
| candidate | sparse | peak | native-view | 25.986 (19.487–28.698) | 2.65 | 5768.8 | 1.50 | 1165.5 |
| candidate | sparse | peak | native-eager | 15.272 (14.833–15.958) | 1.55 | 9815.9 | 1.50 | 1141.5 |
| candidate | full | decode | stock | 281.781 (239.563–299.107) | 1.00 | 532.0 | 1391.50 | 0.0 |
| candidate | full | decode | generated-owned | 352.929 (348.372–355.791) | 1.25 | 424.7 | 1837.25 | 0.0 |
| candidate | full | decode | native-view | 695.722 (688.836–710.138) | 2.47 | 215.5 | 336.00 | 1165.5 |
| candidate | full | decode | native-eager | 1143.944 (1107.041–1145.056) | 4.06 | 131.0 | 1782.25 | 1141.5 |
| candidate | full | resident | stock | 436.231 (427.118–2222.234) | 1.00 | 343.6 | 1393.00 | 0.0 |
| candidate | full | resident | generated-owned | 620.483 (600.330–644.826) | 1.42 | 241.6 | 1838.75 | 0.0 |
| candidate | full | resident | native-view | 1699.650 (1664.480–1878.881) | 3.90 | 88.2 | 1924.75 | 1165.5 |
| candidate | full | resident | native-eager | 2051.213 (2007.286–2184.370) | 4.70 | 73.1 | 2177.00 | 1141.5 |
| candidate | full | heap | stock | 267.606 (267.307–271.383) | 1.00 | 560.2 | 1393.00 | 0.0 |
| candidate | full | heap | generated-owned | 385.232 (378.995–395.036) | 1.44 | 389.1 | 1838.75 | 0.0 |
| candidate | full | heap | native-view | 1200.703 (1184.706–1210.517) | 4.49 | 124.8 | 2252.75 | 1165.5 |
| candidate | full | heap | native-eager | 1457.649 (1437.629–1478.538) | 5.45 | 102.8 | 2505.00 | 1141.5 |
| candidate | full | detach | stock | 451.358 (449.665–1247.611) | 1.00 | 332.1 | 1393.00 | 0.0 |
| candidate | full | detach | generated-owned | 599.262 (596.710–604.556) | 1.33 | 250.2 | 1838.75 | 0.0 |
| candidate | full | detach | native-view | 4417.037 (4392.381–4839.188) | 9.79 | 33.9 | 4494.25 | 1165.5 |
| candidate | full | detach | native-eager | 5440.479 (5356.153–11759.466) | 12.05 | 27.6 | 5940.50 | 1141.5 |
| candidate | full | first | stock | 28.762 (28.748–29.052) | 1.00 | 5212.0 | 1.50 | 0.0 |
| candidate | full | first | generated-owned | 32.509 (31.247–33.135) | 1.13 | 4611.2 | 1.50 | 0.0 |
| candidate | full | first | native-view | 438.992 (434.932–458.037) | 15.26 | 341.5 | 1588.75 | 1165.5 |
| candidate | full | first | native-eager | 63.886 (62.690–64.374) | 2.22 | 2346.5 | 1.50 | 1141.5 |
| candidate | full | interpret | stock | 31.911 (29.939–33.022) | 1.00 | 4697.6 | 1.50 | 0.0 |
| candidate | full | interpret | generated-owned | 43.276 (43.132–44.248) | 1.36 | 3464.0 | 1.50 | 0.0 |
| candidate | full | interpret | native-view | 181.441 (179.254–181.681) | 5.69 | 826.2 | 1.50 | 1165.5 |
| candidate | full | interpret | native-eager | 111.192 (110.189–111.663) | 3.48 | 1348.2 | 1.50 | 1141.5 |
| candidate | full | peak | stock | 22.994 (21.167–24.026) | 1.00 | 6519.2 | 1.50 | 0.0 |
| candidate | full | peak | generated-owned | 22.166 (19.070–22.812) | 0.96 | 6763.0 | 1.50 | 0.0 |
| candidate | full | peak | native-view | 96.971 (82.835–108.829) | 4.22 | 1545.9 | 1.50 | 1165.5 |
| candidate | full | peak | native-eager | 40.738 (40.481–40.869) | 1.77 | 3679.7 | 1.50 | 1141.5 |
| candidate | holdout | resident | stock | 1022.117 (1005.149–1155.433) | 1.00 | 499.0 | 4706.75 | 0.0 |
| candidate | holdout | resident | generated-owned | 1474.652 (1457.128–1490.566) | 1.44 | 345.9 | 6343.25 | 0.0 |
| candidate | holdout | resident | native-view | 3503.977 (3329.078–6517.842) | 3.43 | 145.6 | 5893.00 | 3315.0 |
| candidate | holdout | resident | native-eager | 3920.842 (3896.605–3950.869) | 3.84 | 130.1 | 6624.00 | 3291.0 |
| candidate | holdout | peak | stock | 52.170 (52.122–52.700) | 1.00 | 9776.4 | 1.50 | 0.0 |
| candidate | holdout | peak | generated-owned | 48.693 (47.942–51.479) | 0.93 | 10474.3 | 1.50 | 0.0 |
| candidate | holdout | peak | native-view | 265.792 (264.779–287.230) | 5.09 | 1918.9 | 1.50 | 3315.0 |
| candidate | holdout | peak | native-eager | 129.440 (128.716–134.775) | 2.48 | 3940.3 | 1.50 | 3291.0 |
| candidate | large | decode | stock | 131558.033 (88175.799–187684.163) | 1.00 | 393.9 | 306051.25 | 0.0 |
| candidate | large | decode | generated-owned | 140902.440 (132598.491–146183.128) | 1.07 | 367.8 | 533143.25 | 0.0 |
| candidate | large | decode | native-view | 284198.915 (275328.370–291642.486) | 2.16 | 182.3 | 336.00 | 409153.5 |
| candidate | large | decode | native-eager | 338145.281 (326161.541–343267.162) | 2.57 | 153.3 | 452185.75 | 409129.5 |
| candidate | large | resident | stock | 225144.219 (75717.962–247928.387) | 1.00 | 230.2 | 306044.50 | 0.0 |
| candidate | large | resident | generated-owned | 149764.435 (147501.832–151669.673) | 0.67 | 346.0 | 533144.75 | 0.0 |
| candidate | large | resident | native-view | 312859.375 (308333.750–317770.156) | 1.39 | 165.6 | 437040.25 | 409153.5 |
| candidate | large | resident | native-eager | 365576.215 (365003.993–371962.045) | 1.62 | 141.8 | 579437.50 | 409129.5 |
| candidate | large | peak | stock | 11784.268 (11485.050–13104.032) | 1.00 | 4397.6 | 1.50 | 0.0 |
| candidate | large | peak | generated-owned | 13041.746 (9986.407–13804.228) | 1.11 | 3973.6 | 1.50 | 0.0 |
| candidate | large | peak | native-view | 56067.113 (54791.736–57119.468) | 4.76 | 924.3 | 1.50 | 409153.5 |
| candidate | large | peak | native-eager | 24086.980 (23830.851–24576.819) | 2.04 | 2151.5 | 1.50 | 409129.5 |
| baseline | sparse | decode | stock | 418.027 (405.345–772.009) | 1.00 | 358.6 | 1391.50 | 0.0 |
| baseline | sparse | decode | generated-owned | 560.758 (552.011–561.687) | 1.34 | 267.3 | 1837.25 | 0.0 |
| baseline | sparse | decode | native-view | 1031.644 (1026.531–1039.009) | 2.47 | 145.3 | 336.00 | 1165.5 |
| baseline | sparse | decode | native-eager | 1849.249 (1835.003–1855.080) | 4.42 | 81.1 | 1788.00 | 1488.0 |
| baseline | sparse | resident | stock | 402.098 (244.550–520.544) | 1.00 | 372.8 | 1393.00 | 0.0 |
| baseline | sparse | resident | generated-owned | 359.951 (352.826–374.448) | 0.90 | 416.5 | 1838.75 | 0.0 |
| baseline | sparse | resident | native-view | 851.063 (841.120–855.111) | 2.12 | 176.1 | 1017.25 | 1165.5 |
| baseline | sparse | resident | native-eager | 1298.271 (1285.128–1337.976) | 3.23 | 115.5 | 1967.75 | 1488.0 |
| baseline | sparse | heap | stock | 296.127 (247.673–772.657) | 1.00 | 506.2 | 1393.00 | 0.0 |
| baseline | sparse | heap | generated-owned | 351.414 (350.156–354.494) | 1.19 | 426.6 | 1838.75 | 0.0 |
| baseline | sparse | heap | native-view | 906.751 (900.576–922.287) | 3.06 | 165.3 | 1345.25 | 1165.5 |
| baseline | sparse | heap | native-eager | 1376.438 (1355.437–1378.886) | 4.65 | 108.9 | 2319.75 | 1488.0 |
| baseline | sparse | detach | stock | 687.906 (685.916–789.105) | 1.00 | 217.9 | 1393.00 | 0.0 |
| baseline | sparse | detach | generated-owned | 933.682 (333.476–943.638) | 1.36 | 160.6 | 1838.75 | 0.0 |
| baseline | sparse | detach | native-view | 2370.494 (2356.626–2380.049) | 3.45 | 63.2 | 4494.25 | 1165.5 |
| baseline | sparse | detach | native-eager | 2914.114 (2859.728–2949.441) | 4.24 | 51.4 | 5946.25 | 1488.0 |
| baseline | sparse | first | stock | 9.924 (9.865–13.203) | 1.00 | 15105.7 | 1.50 | 0.0 |
| baseline | sparse | first | generated-owned | 10.514 (10.253–14.436) | 1.06 | 14258.0 | 1.50 | 0.0 |
| baseline | sparse | first | native-view | 144.302 (143.943–145.700) | 14.54 | 1038.8 | 681.25 | 1165.5 |
| baseline | sparse | first | native-eager | 28.450 (28.041–30.856) | 2.87 | 5269.0 | 1.50 | 1488.0 |
| baseline | sparse | interpret | stock | 8.010 (7.846–9.603) | 1.00 | 18715.3 | 1.50 | 0.0 |
| baseline | sparse | interpret | generated-owned | 11.620 (11.499–15.255) | 1.45 | 12900.5 | 1.50 | 0.0 |
| baseline | sparse | interpret | native-view | 35.289 (33.826–35.686) | 4.41 | 4247.9 | 1.50 | 1165.5 |
| baseline | sparse | interpret | native-eager | 27.913 (27.164–34.176) | 3.48 | 5370.5 | 1.50 | 1488.0 |
| baseline | sparse | peak | stock | 8.664 (8.040–8.815) | 1.00 | 17301.7 | 1.50 | 0.0 |
| baseline | sparse | peak | generated-owned | 8.537 (8.282–9.416) | 0.99 | 17559.2 | 1.50 | 0.0 |
| baseline | sparse | peak | native-view | 21.860 (19.965–21.986) | 2.52 | 6857.7 | 1.50 | 1165.5 |
| baseline | sparse | peak | native-eager | 16.571 (15.703–19.125) | 1.91 | 9046.1 | 1.50 | 1488.0 |
| baseline | full | decode | stock | 528.002 (466.209–756.200) | 1.00 | 283.9 | 1391.50 | 0.0 |
| baseline | full | decode | generated-owned | 342.559 (342.046–345.926) | 0.65 | 437.6 | 1837.25 | 0.0 |
| baseline | full | decode | native-view | 702.727 (695.302–767.583) | 1.33 | 213.3 | 336.00 | 1165.5 |
| baseline | full | decode | native-eager | 1200.344 (1165.650–1230.470) | 2.27 | 124.9 | 1788.00 | 1488.0 |
| baseline | full | resident | stock | 444.277 (432.161–502.431) | 1.00 | 337.4 | 1393.00 | 0.0 |
| baseline | full | resident | generated-owned | 604.935 (601.593–607.678) | 1.36 | 247.8 | 1838.75 | 0.0 |
| baseline | full | resident | native-view | 1665.583 (1658.875–1669.541) | 3.75 | 90.0 | 1924.75 | 1165.5 |
| baseline | full | resident | native-eager | 2114.659 (2082.758–5250.875) | 4.76 | 70.9 | 2182.75 | 1488.0 |
| baseline | full | heap | stock | 276.239 (272.276–326.281) | 1.00 | 542.7 | 1393.00 | 0.0 |
| baseline | full | heap | generated-owned | 378.023 (377.369–379.947) | 1.37 | 396.6 | 1838.75 | 0.0 |
| baseline | full | heap | native-view | 1215.176 (1210.394–1221.801) | 4.40 | 123.4 | 2252.75 | 1165.5 |
| baseline | full | heap | native-eager | 1485.092 (1469.792–1516.547) | 5.38 | 100.9 | 2534.75 | 1488.0 |
| baseline | full | detach | stock | 438.725 (435.844–466.437) | 1.00 | 341.7 | 1393.00 | 0.0 |
| baseline | full | detach | generated-owned | 609.244 (603.656–616.689) | 1.39 | 246.1 | 1838.75 | 0.0 |
| baseline | full | detach | native-view | 5407.180 (4421.269–5476.856) | 12.32 | 27.7 | 4494.25 | 1165.5 |
| baseline | full | detach | native-eager | 7202.981 (6288.782–7266.845) | 16.42 | 20.8 | 5946.25 | 1488.0 |
| baseline | full | first | stock | 29.145 (28.698–30.959) | 1.00 | 5143.5 | 1.50 | 0.0 |
| baseline | full | first | generated-owned | 32.694 (31.290–32.876) | 1.12 | 4585.2 | 1.50 | 0.0 |
| baseline | full | first | native-view | 423.944 (418.630–451.291) | 14.55 | 353.6 | 1588.75 | 1165.5 |
| baseline | full | first | native-eager | 64.089 (62.226–65.062) | 2.20 | 2339.0 | 1.50 | 1488.0 |
| baseline | full | interpret | stock | 30.309 (24.170–32.344) | 1.00 | 4945.9 | 1.50 | 0.0 |
| baseline | full | interpret | generated-owned | 44.285 (42.584–45.056) | 1.46 | 3385.1 | 1.50 | 0.0 |
| baseline | full | interpret | native-view | 183.065 (182.929–185.027) | 6.04 | 818.9 | 1.50 | 1165.5 |
| baseline | full | interpret | native-eager | 112.723 (112.230–115.519) | 3.72 | 1329.9 | 1.50 | 1488.0 |
| baseline | full | peak | stock | 22.416 (19.863–26.452) | 1.00 | 6687.6 | 1.50 | 0.0 |
| baseline | full | peak | generated-owned | 21.821 (20.889–22.605) | 0.97 | 6869.7 | 1.50 | 0.0 |
| baseline | full | peak | native-view | 84.936 (83.428–88.003) | 3.79 | 1764.9 | 1.50 | 1165.5 |
| baseline | full | peak | native-eager | 41.429 (40.751–41.808) | 1.85 | 3618.4 | 1.50 | 1488.0 |
| baseline | holdout | resident | stock | 988.751 (988.703–1021.584) | 1.00 | 515.8 | 4706.75 | 0.0 |
| baseline | holdout | resident | generated-owned | 1558.943 (1478.743–1582.765) | 1.58 | 327.2 | 6343.25 | 0.0 |
| baseline | holdout | resident | native-view | 3361.497 (3301.329–3366.240) | 3.40 | 151.7 | 5893.00 | 3315.0 |
| baseline | holdout | resident | native-eager | 4156.994 (4155.093–4174.776) | 4.20 | 122.7 | 6678.25 | 5184.0 |
| baseline | holdout | peak | stock | 55.243 (54.036–56.640) | 1.00 | 9232.5 | 1.50 | 0.0 |
| baseline | holdout | peak | generated-owned | 48.333 (48.280–48.724) | 0.87 | 10552.3 | 1.50 | 0.0 |
| baseline | holdout | peak | native-view | 267.996 (266.140–270.021) | 4.85 | 1903.1 | 1.50 | 3315.0 |
| baseline | holdout | peak | native-eager | 136.726 (135.494–148.592) | 2.48 | 3730.3 | 1.50 | 5184.0 |
| baseline | large | decode | stock | 129602.835 (98773.180–149432.927) | 1.00 | 399.9 | 306043.00 | 0.0 |
| baseline | large | decode | generated-owned | 133066.904 (132683.676–137792.496) | 1.03 | 389.4 | 533143.25 | 0.0 |
| baseline | large | decode | native-view | 290414.472 (275137.781–350433.396) | 2.24 | 178.4 | 336.00 | 409153.5 |
| baseline | large | decode | native-eager | 362599.434 (361778.635–365491.278) | 2.80 | 142.9 | 461258.50 | 393216.0 |
| baseline | large | resident | stock | 125722.750 (96546.534–171278.750) | 1.00 | 412.2 | 306068.50 | 0.0 |
| baseline | large | resident | generated-owned | 148742.619 (144162.743–149250.268) | 1.18 | 348.4 | 533144.75 | 0.0 |
| baseline | large | resident | native-view | 317725.562 (316072.531–332868.844) | 2.53 | 163.1 | 437040.25 | 409153.5 |
| baseline | large | resident | native-eager | 455709.554 (427182.500–589495.990) | 3.62 | 113.7 | 588510.25 | 393216.0 |
| baseline | large | peak | stock | 11260.076 (10801.466–11519.575) | 1.00 | 4602.3 | 1.50 | 0.0 |
| baseline | large | peak | generated-owned | 13624.656 (13062.397–16871.684) | 1.21 | 3803.6 | 1.50 | 0.0 |
| baseline | large | peak | native-view | 55961.416 (55240.434–57412.009) | 4.97 | 926.0 | 1.50 | 409153.5 |
| baseline | large | peak | native-eager | 29301.628 (28613.901–34240.197) | 2.60 | 1768.6 | 1.50 | 393216.0 |
