# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 0 | json-typed-nested | simdjson-typed-nested | ingest | 405.490 (388.840–407.322) | 516.996 (433.523–529.034) | 0.784x | yes | 160.000 (160.000–160.000) | 248.000 (248.000–248.000) |
| 0 | json-typed-nested | simdjson-typed-nested | read-aot | 23.824 (23.346–37.666) | 29.154 (27.707–47.723) | 0.817x | no | 48.000 (48.000–48.000) | 112.000 (112.000–112.000) |
| 0 | json-typed-nested | simdjson-typed-nested | interpret | 28.439 (23.721–28.859) | 29.578 (29.417–31.248) | 0.962x | yes | 50.000 (50.000–50.000) | 114.000 (114.000–114.000) |
| 0 | json-typed-nested | simdjson-typed-nested | peak | 24.527 (24.198–25.297) | 23.842 (23.444–28.772) | 1.029x | no | 49.000 (49.000–49.000) | 49.000 (49.000–49.000) |
| 1 | json-typed-repeated | simdjson-typed-repeated | ingest | 3696.585 (3630.480–3813.616) | 3688.775 (3637.244–3705.727) | 1.002x | no | 1568.000 (1568.000–1568.000) | 2040.000 (2040.000–2040.000) |
| 1 | json-typed-repeated | simdjson-typed-repeated | read-aot | 385.851 (383.413–387.616) | 437.266 (432.021–438.464) | 0.882x | yes | 1456.000 (1456.000–1456.000) | 1904.000 (1904.000–1904.000) |
| 1 | json-typed-repeated | simdjson-typed-repeated | interpret | 387.727 (384.402–438.702) | 458.007 (437.560–610.958) | 0.847x | no | 1458.000 (1458.000–1458.000) | 1906.000 (1906.000–1906.000) |
| 1 | json-typed-repeated | simdjson-typed-repeated | peak | 347.808 (346.929–349.666) | 353.351 (345.077–468.252) | 0.984x | no | 817.000 (817.000–817.000) | 817.000 (817.000–817.000) |
| 2 | json-typed-mixed | simdjson-typed-mixed | ingest | 1543.362 (1499.482–1547.312) | 1633.360 (1629.958–1670.729) | 0.945x | yes | 592.000 (592.000–592.000) | 776.000 (776.000–776.000) |
| 2 | json-typed-mixed | simdjson-typed-mixed | read-aot | 161.939 (160.691–162.672) | 180.355 (179.185–180.475) | 0.898x | yes | 480.000 (480.000–480.000) | 640.000 (640.000–640.000) |
| 2 | json-typed-mixed | simdjson-typed-mixed | interpret | 161.191 (160.639–163.688) | 182.525 (180.701–182.824) | 0.883x | yes | 482.000 (482.000–482.000) | 642.000 (642.000–642.000) |
| 2 | json-typed-mixed | simdjson-typed-mixed | peak | 150.181 (149.467–153.963) | 150.196 (148.804–153.479) | 1.000x | no | 321.000 (321.000–321.000) | 321.000 (321.000–321.000) |
| 3 | json-typed-churn | simdjson-typed-churn | ingest | 2278.488 (2266.206–2375.895) | 2609.004 (2541.920–5769.540) | 0.873x | yes | 558.500 (558.500–558.500) | 735.000 (735.000–735.000) |
| 3 | json-typed-churn | simdjson-typed-churn | read-aot | 235.017 (232.243–257.240) | 261.986 (256.879–272.181) | 0.897x | no | 446.500 (446.500–446.500) | 599.000 (599.000–599.000) |
| 3 | json-typed-churn | simdjson-typed-churn | interpret | 236.681 (233.303–249.784) | 273.933 (267.869–276.662) | 0.864x | yes | 448.500 (448.500–448.500) | 601.000 (601.000–601.000) |
| 3 | json-typed-churn | simdjson-typed-churn | peak | 236.625 (219.450–241.769) | 216.806 (212.672–217.620) | 1.091x | yes | 300.000 (300.000–300.000) | 300.000 (300.000–300.000) |
| 4 | json-scalar | simdjson-typed | ingest | 244.075 (240.887–246.192) | 228.745 (218.970–282.803) | 1.067x | no | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-typed | peak | 4.472 (4.378–16.211) | 4.310 (4.240–6.376) | 1.038x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | simdjson-specialized | ingest | 241.616 (240.668–262.107) | 250.788 (234.580–259.812) | 0.963x | no | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-specialized | peak | 2.871 (2.758–3.038) | 2.956 (2.833–3.141) | 0.971x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | gson-stream | ingest | 4057.497 (2019.668–10750.268) | 5277.337 (2527.773–10655.327) | 0.769x | no | 11288.000 (11288.000–11288.000) | 11288.000 (11288.000–11288.000) |
| 4 | json-scalar | gson-stream | peak | 1.451 (1.363–1.508) | 1.494 (1.360–1.621) | 0.971x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 7 | json-scalar | simdjson-typed | read-aot | 4.717 (4.667–4.830) | 4.842 (4.779–4.854) | 0.974x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-typed | interpret | 5.207 (5.191–5.522) | 5.310 (5.151–5.876) | 0.981x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | simdjson-specialized | read-aot | 7.100 (6.654–9.135) | 6.816 (6.658–6.880) | 1.042x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-specialized | interpret | 7.837 (7.625–7.886) | 7.963 (7.789–10.851) | 0.984x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | gson-stream | read-aot | 1.376 (1.363–1.388) | 1.418 (1.397–1.475) | 0.970x | yes | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | gson-stream | interpret | 1.885 (1.822–1.956) | 1.842 (1.774–1.854) | 1.024x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
