# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as retained index/header bytes. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | generated-owned | native-view | native-eager |
|---|---|---:|---:|---:|---:|
| sparse | decode | 1216.9 | 849.3 (0.70×) | 1582.3 (1.30×) | 1916.7 (1.58×) |
| sparse | resident | 300.0 | 367.9 (1.23×) | 1038.3 (3.46×) | 1673.6 (5.58×) |
| sparse | heap | 253.3 | 386.5 (1.53×) | 1118.2 (4.41×) | 1409.2 (5.56×) |
| sparse | detach | 256.9 | 369.4 (1.44×) | 3011.6 (11.72×) | 3926.9 (15.28×) |
| sparse | first | 10.9 | 10.5 (0.97×) | 158.5 (14.59×) | 39.9 (3.67×) |
| sparse | interpret | 7.5 | 13.4 (1.77×) | 34.8 (4.60×) | 33.8 (4.47×) |
| sparse | peak | 8.0 | 9.0 (1.13×) | 22.2 (2.79×) | 16.2 (2.04×) |
| full | decode | 247.2 | 393.3 (1.59×) | 881.6 (3.57×) | 1220.4 (4.94×) |
| full | resident | 2087.9 | 601.7 (0.29×) | 2091.0 (1.00×) | 2134.7 (1.02×) |
| full | heap | 284.5 | 379.8 (1.33×) | 1537.6 (5.40×) | 1528.0 (5.37×) |
| full | detach | 557.2 | 622.1 (1.12×) | 9774.4 (17.54×) | 7226.3 (12.97×) |
| full | first | 29.6 | 33.2 (1.12×) | 587.8 (19.89×) | 63.9 (2.16×) |
| full | interpret | 31.0 | 43.4 (1.40×) | 192.6 (6.22×) | 115.8 (3.74×) |
| full | peak | 30.8 | 25.1 (0.81×) | 114.7 (3.72×) | 75.3 (2.44×) |
| holdout | resident | 1049.8 | 1551.6 (1.48×) | 4243.7 (4.04×) | 4174.8 (3.98×) |
| holdout | peak | 55.6 | 48.8 (0.88×) | 257.7 (4.63×) | 126.2 (2.27×) |
| large | decode | 85315.1 | 147015.2 (1.72×) | 202801.0 (2.38×) | 391318.5 (4.59×) |
| large | resident | 189483.0 | 151650.1 (0.80×) | 370308.2 (1.95×) | 447576.5 (2.36×) |
| large | peak | 12035.7 | 11097.5 (0.92×) | 52234.8 (4.34×) | 32409.9 (2.69×) |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row |
|---|---|---|---|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 1216.853 (393.647–1297.728) | 1.00 | 123.2 | 1391.50 | 0.0 |
| candidate | sparse | decode | generated-owned | 849.267 (559.778–2219.075) | 0.70 | 176.5 | 1837.25 | 0.0 |
| candidate | sparse | decode | native-view | 1582.314 (1207.726–13212.955) | 1.30 | 94.7 | 448.00 | 1512.0 |
| candidate | sparse | decode | native-eager | 1916.734 (1821.373–3382.671) | 1.58 | 78.2 | 1780.00 | 1488.0 |
| candidate | sparse | resident | stock | 300.014 (256.499–598.055) | 1.00 | 499.7 | 1393.00 | 0.0 |
| candidate | sparse | resident | generated-owned | 367.918 (365.250–478.218) | 1.23 | 407.4 | 1838.75 | 0.0 |
| candidate | sparse | resident | native-view | 1038.296 (1028.221–1039.778) | 3.46 | 144.4 | 906.75 | 1512.0 |
| candidate | sparse | resident | native-eager | 1673.628 (1311.271–3838.527) | 5.58 | 89.6 | 1959.75 | 1488.0 |
| candidate | sparse | heap | stock | 253.288 (252.474–309.525) | 1.00 | 591.8 | 1393.00 | 0.0 |
| candidate | sparse | heap | generated-owned | 386.484 (351.623–388.283) | 1.53 | 387.9 | 1838.75 | 0.0 |
| candidate | sparse | heap | native-view | 1118.185 (1107.286–1189.808) | 4.41 | 134.1 | 1234.75 | 1512.0 |
| candidate | sparse | heap | native-eager | 1409.164 (1387.054–1489.642) | 5.56 | 106.4 | 2311.75 | 1488.0 |
| candidate | sparse | detach | stock | 256.913 (252.888–282.111) | 1.00 | 583.5 | 1393.00 | 0.0 |
| candidate | sparse | detach | generated-owned | 369.417 (353.766–385.019) | 1.44 | 405.8 | 1838.75 | 0.0 |
| candidate | sparse | detach | native-view | 3011.605 (2837.544–6044.543) | 11.72 | 49.8 | 4606.25 | 1512.0 |
| candidate | sparse | detach | native-eager | 3926.857 (3539.788–5071.770) | 15.28 | 38.2 | 5938.25 | 1488.0 |
| candidate | sparse | first | stock | 10.865 (10.037–17.457) | 1.00 | 13797.1 | 1.50 | 0.0 |
| candidate | sparse | first | generated-owned | 10.514 (10.317–13.705) | 0.97 | 14257.8 | 1.50 | 0.0 |
| candidate | sparse | first | native-view | 158.499 (155.257–178.476) | 14.59 | 945.8 | 458.75 | 1512.0 |
| candidate | sparse | first | native-eager | 39.860 (34.369–42.108) | 3.67 | 3760.8 | 1.50 | 1488.0 |
| candidate | sparse | interpret | stock | 7.550 (7.342–8.745) | 1.00 | 19855.3 | 1.50 | 0.0 |
| candidate | sparse | interpret | generated-owned | 13.399 (11.535–13.733) | 1.77 | 11187.5 | 1.50 | 0.0 |
| candidate | sparse | interpret | native-view | 34.762 (33.178–43.817) | 4.60 | 4312.4 | 1.50 | 1512.0 |
| candidate | sparse | interpret | native-eager | 33.761 (29.436–35.128) | 4.47 | 4440.2 | 1.50 | 1488.0 |
| candidate | sparse | peak | stock | 7.957 (7.821–11.270) | 1.00 | 18838.7 | 1.50 | 0.0 |
| candidate | sparse | peak | generated-owned | 8.956 (7.938–9.081) | 1.13 | 16738.7 | 1.50 | 0.0 |
| candidate | sparse | peak | native-view | 22.185 (20.396–31.307) | 2.79 | 6757.0 | 1.50 | 1512.0 |
| candidate | sparse | peak | native-eager | 16.204 (15.147–17.170) | 2.04 | 9251.2 | 1.50 | 1488.0 |
| candidate | full | decode | stock | 247.164 (244.437–316.796) | 1.00 | 606.5 | 1391.50 | 0.0 |
| candidate | full | decode | generated-owned | 393.292 (353.261–398.921) | 1.59 | 381.2 | 1837.25 | 0.0 |
| candidate | full | decode | native-view | 881.629 (877.064–901.653) | 3.57 | 170.0 | 448.00 | 1512.0 |
| candidate | full | decode | native-eager | 1220.364 (1200.025–1228.517) | 4.94 | 122.8 | 1780.00 | 1488.0 |
| candidate | full | resident | stock | 2087.868 (427.601–3353.771) | 1.00 | 71.8 | 1393.00 | 0.0 |
| candidate | full | resident | generated-owned | 601.720 (595.547–620.818) | 0.29 | 249.1 | 1838.75 | 0.0 |
| candidate | full | resident | native-view | 2090.990 (2086.080–2108.095) | 1.00 | 71.7 | 1920.75 | 1512.0 |
| candidate | full | resident | native-eager | 2134.735 (2114.067–2208.647) | 1.02 | 70.2 | 2174.75 | 1488.0 |
| candidate | full | heap | stock | 284.531 (270.739–332.371) | 1.00 | 526.9 | 1393.00 | 0.0 |
| candidate | full | heap | generated-owned | 379.799 (373.882–392.355) | 1.33 | 394.7 | 1838.75 | 0.0 |
| candidate | full | heap | native-view | 1537.592 (1529.527–1580.899) | 5.40 | 97.5 | 2248.75 | 1512.0 |
| candidate | full | heap | native-eager | 1528.018 (1456.678–1576.205) | 5.37 | 98.1 | 2526.75 | 1488.0 |
| candidate | full | detach | stock | 557.200 (543.535–1402.557) | 1.00 | 269.0 | 1393.00 | 0.0 |
| candidate | full | detach | generated-owned | 622.146 (617.888–644.194) | 1.12 | 240.9 | 1838.75 | 0.0 |
| candidate | full | detach | native-view | 9774.413 (5135.770–40027.412) | 17.54 | 15.3 | 4606.25 | 1512.0 |
| candidate | full | detach | native-eager | 7226.256 (5451.531–434023.926) | 12.97 | 20.7 | 5938.25 | 1488.0 |
| candidate | full | first | stock | 29.557 (29.342–32.491) | 1.00 | 5071.7 | 1.50 | 0.0 |
| candidate | full | first | generated-owned | 33.208 (32.536–35.469) | 1.12 | 4514.2 | 1.50 | 0.0 |
| candidate | full | first | native-view | 587.757 (583.938–602.837) | 19.89 | 255.0 | 1472.75 | 1512.0 |
| candidate | full | first | native-eager | 63.863 (62.087–72.084) | 2.16 | 2347.3 | 1.50 | 1488.0 |
| candidate | full | interpret | stock | 30.965 (28.620–37.592) | 1.00 | 4841.1 | 1.50 | 0.0 |
| candidate | full | interpret | generated-owned | 43.361 (43.246–45.009) | 1.40 | 3457.2 | 1.50 | 0.0 |
| candidate | full | interpret | native-view | 192.646 (187.827–201.580) | 6.22 | 778.1 | 1.50 | 1512.0 |
| candidate | full | interpret | native-eager | 115.836 (112.041–138.695) | 3.74 | 1294.1 | 1.50 | 1488.0 |
| candidate | full | peak | stock | 30.824 (27.348–32.057) | 1.00 | 4863.4 | 1.50 | 0.0 |
| candidate | full | peak | generated-owned | 25.067 (24.238–26.028) | 0.81 | 5980.1 | 1.50 | 0.0 |
| candidate | full | peak | native-view | 114.668 (112.320–136.663) | 3.72 | 1307.3 | 1.50 | 1512.0 |
| candidate | full | peak | native-eager | 75.341 (53.894–82.047) | 2.44 | 1989.7 | 1.50 | 1488.0 |
| candidate | holdout | resident | stock | 1049.783 (1024.125–1135.274) | 1.00 | 485.8 | 4706.75 | 0.0 |
| candidate | holdout | resident | generated-owned | 1551.644 (1541.909–1806.026) | 1.48 | 328.7 | 6343.25 | 0.0 |
| candidate | holdout | resident | native-view | 4243.738 (3915.236–4322.242) | 4.04 | 120.2 | 5506.50 | 5208.0 |
| candidate | holdout | resident | native-eager | 4174.810 (4153.772–4312.358) | 3.98 | 122.2 | 6670.25 | 5184.0 |
| candidate | holdout | peak | stock | 55.618 (53.492–3003.354) | 1.00 | 9170.2 | 1.50 | 0.0 |
| candidate | holdout | peak | generated-owned | 48.798 (48.739–54.815) | 0.88 | 10452.0 | 1.50 | 0.0 |
| candidate | holdout | peak | native-view | 257.735 (256.762–263.070) | 4.63 | 1978.9 | 1.50 | 5208.0 |
| candidate | holdout | peak | native-eager | 126.170 (124.786–325.787) | 2.27 | 4042.4 | 1.50 | 5184.0 |
| candidate | large | decode | stock | 85315.124 (69849.660–85703.801) | 1.00 | 607.4 | 306060.39 | 0.0 |
| candidate | large | decode | generated-owned | 147015.189 (142506.395–171626.686) | 1.72 | 352.5 | 533143.25 | 0.0 |
| candidate | large | decode | native-view | 202800.998 (198802.309–244292.454) | 2.38 | 255.5 | 448.00 | 393240.0 |
| candidate | large | decode | native-eager | 391318.547 (390870.383–393198.469) | 4.59 | 132.4 | 461250.50 | 393216.0 |
| candidate | large | resident | stock | 189482.988 (82884.984–241292.542) | 1.00 | 273.5 | 306084.50 | 0.0 |
| candidate | large | resident | generated-owned | 151650.074 (150808.636–153769.182) | 0.80 | 341.7 | 533144.75 | 0.0 |
| candidate | large | resident | native-view | 370308.229 (364276.736–693397.750) | 1.95 | 139.9 | 364502.75 | 393240.0 |
| candidate | large | resident | native-eager | 447576.518 (427152.930–468942.009) | 2.36 | 115.8 | 588502.25 | 393216.0 |
| candidate | large | peak | stock | 12035.736 (10567.367–12347.314) | 1.00 | 4305.7 | 1.50 | 0.0 |
| candidate | large | peak | generated-owned | 11097.465 (9413.565–11696.665) | 0.92 | 4669.7 | 1.50 | 0.0 |
| candidate | large | peak | native-view | 52234.764 (52185.390–65823.025) | 4.34 | 992.1 | 1.50 | 393240.0 |
| candidate | large | peak | native-eager | 32409.898 (30730.036–157358.280) | 2.69 | 1599.0 | 1.50 | 393216.0 |
