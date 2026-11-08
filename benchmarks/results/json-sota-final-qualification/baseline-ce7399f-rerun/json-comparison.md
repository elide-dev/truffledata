# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 882.933 (878.938–903.472) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 185.170 (181.495–186.699) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 182.329 (181.783–183.958) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 1.030 (0.970–1.034) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 3.422 (3.357–3.595) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 5.156 (5.132–5.169) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.343 (1.329–1.345) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 3.992 (3.709–4.025) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 5.688 (5.677–5.783) | 2.000 (2.000–2.000) |
| peak | gson-stream | 1.032 (0.979–1.034) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 3.315 (3.066–3.331) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 2.143 (2.141–2.146) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 1940.468 (1908.940–1979.320) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 1781.990 (1702.912–1787.972) | 2528.000 (2528.000–2528.000) |
| ingest | simdjson-generic-interop | 791.002 (780.920–793.964) | 480.000 (480.000–480.000) |
| ingest | simdjson-snapshot | 1562.995 (1491.985–1582.854) | 2101.500 (2101.500–2101.500) |
| ingest | simdjson-session-java | 1610.685 (1600.010–1728.716) | 1240.000 (1240.000–1240.000) |
| ingest | simdjson-session-truffle | 1799.850 (1685.300–1802.168) | 1652.500 (1652.500–1652.500) |
| ingest | gson-stream-lines | 2113.421 (2083.132–2115.911) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-stream-java | 1608.660 (1522.447–1618.815) | 1218.500 (1218.500–1218.500) |
| ingest | simdjson-stream-interop | 666.690 (660.579–698.076) | 312.750 (312.750–312.750) |
| ingest | jackson-tree | 1235.010 (1187.689–1253.278) | 2245.000 (2245.000–2245.000) |
| ingest | jackson-stream | 711.858 (686.612–715.130) | 808.000 (808.000–808.000) |
| ingest | fastjson2-tree | 613.177 (581.865–615.219) | 1372.000 (1372.000–1372.000) |
| ingest | dsljson-map | 725.685 (723.081–754.226) | 1080.000 (1080.000–1080.000) |
| ingest | jsoniter-any | 1119.454 (1075.364–1133.823) | 1508.000 (1508.000–1508.000) |
| read-aot | gson-tree | 218.601 (215.342–233.447) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 958.289 (942.863–974.360) | 1168.000 (1168.000–1168.000) |
| read-aot | simdjson-generic-interop | 154.336 (150.224–162.509) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 160.894 (156.725–169.018) | 240.000 (240.000–240.000) |
| read-aot | jackson-tree | 59.798 (59.592–63.592) | 0.000 (0.000–0.000) |
| read-aot | fastjson2-tree | 82.167 (81.730–85.263) | 32.000 (32.000–32.000) |
| read-aot | dsljson-map | 84.959 (80.198–85.711) | 0.000 (0.000–0.000) |
| read-aot | jsoniter-any | 115.895 (106.152–116.887) | 80.000 (80.000–80.000) |
| interpret | gson-tree | 228.896 (213.867–229.411) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 922.127 (920.790–966.922) | 1170.000 (1170.000–1170.000) |
| interpret | simdjson-generic-interop | 152.654 (150.576–161.098) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 158.361 (158.344–164.757) | 242.000 (242.000–242.000) |
| interpret | jackson-tree | 63.766 (61.781–64.381) | 2.000 (2.000–2.000) |
| interpret | fastjson2-tree | 84.855 (81.960–85.595) | 34.000 (34.000–34.000) |
| interpret | dsljson-map | 84.449 (83.892–89.066) | 2.000 (2.000–2.000) |
| interpret | jsoniter-any | 118.241 (112.465–120.312) | 82.000 (82.000–82.000) |
| peak | gson-tree | 226.585 (215.822–228.070) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 982.931 (930.202–990.378) | 1169.000 (1169.000–1169.000) |
| peak | simdjson-generic-interop | 90.821 (87.360–90.866) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 99.244 (93.838–99.624) | 1.000 (1.000–1.000) |
| peak | jackson-tree | 64.298 (60.907–64.323) | 1.000 (1.000–1.000) |
| peak | fastjson2-tree | 76.833 (76.614–80.851) | 33.000 (33.000–33.000) |
| peak | dsljson-map | 87.454 (83.507–87.475) | 1.000 (1.000–1.000) |
| peak | jsoniter-any | 108.040 (107.069–108.323) | 81.000 (81.000–81.000) |
