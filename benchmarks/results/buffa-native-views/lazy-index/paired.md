# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1636.823 (1625.926–1880.491) | 1743.750 |
| proto-mixed | buffa | ingest | view | 878.901 (876.476–881.167) | 421.500 |
| proto-mixed | buffa | ingest-generated | eager | 1507.522 (1500.316–1518.409) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 770.569 (765.280–782.741) | 421.500 |
| proto-mixed-full | buffa | ingest | eager | 2348.406 (2322.587–2375.109) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 2015.074 (1997.226–2023.492) | 1382.250 |
| proto-scalar | buffa | ingest-generated | eager | 53.159 (52.967–53.487) | 80.000 |
| proto-scalar | buffa | ingest-generated | view | 53.442 (52.911–54.076) | 80.000 |
| proto-mixed | generated-owned | ingest | eager | 655.917 (654.618–669.355) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 661.723 (654.100–669.006) | 1800.500 |
| proto-mixed | stock | ingest | eager | 632.374 (623.853–642.740) | 1776.750 |
| proto-mixed | stock | ingest | view | 635.310 (628.717–644.857) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 675.862 (669.906–685.069) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 679.635 (667.507–680.683) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 15.351 (15.310–15.513) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 17.420 (17.392–17.438) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.719 (6.618–6.784) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 9.967 (9.839–10.125) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.443 (5.346–5.519) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.379 (5.347–5.423) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.214 (5.160–5.297) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.225 (5.189–5.255) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.712 (2.711–2.763) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.715 (2.701–2.740) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1801.513 (1768.187–1822.293) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1406.768 (1399.629–1415.729) | 1382.250 |
| proto-mixed-full | owned | ingest-generated | eager | 3233.055 (3222.013–3249.526) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3231.867 (3224.984–3260.531) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 690.592 (683.686–703.699) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 685.725 (684.945–690.263) | 1800.500 |
