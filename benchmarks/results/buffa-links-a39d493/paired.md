# Paired compiled profile-links experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1994.440 (1893.092–2002.898) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1813.033 (1736.157–1844.707) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1801.962 (1786.366–1917.165) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1662.643 (1614.915–1702.359) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2655.301 (2594.242–2777.205) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2554.118 (2442.265–2629.414) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 53.278 (51.961–54.793) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 57.633 (54.651–58.140) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 647.865 (644.972–656.165) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 671.888 (665.539–677.868) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 663.588 (630.460–680.477) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 659.445 (636.947–677.572) | 1776.750 |
