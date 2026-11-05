# One plan check per generated getter

Source: generated getter change on `db62cfb`. Generated value/size getters delegate plan-identity checking to ProtoPlan; presence getters retain their direct check. Foreign-plan rejection tests cover integer, string, float, double, boolean, bytes/repeated sizes, and presence. Core tests, strict JVM views and SVM views passed: https://app.buildbuddy.io/invocation/14d57599-9a35-4029-a702-da9365a2363a.

Five randomized fresh forks per metric/cell on unclemax CPU 2, protocol `../full/run.py`. Remote `/tmp/truffledata-native-views.G2ATYf/single-check`. Full generated ingestion improves 1477.6 to 1395.5 ns (5.6%), retained generated interpreter reads 18.97 to 16.85 ns (11.2%), both with disjoint ranges. Projected generated ingestion remains 765 ns. Eager/generated-access full ingestion also improves, consistent with shared getter work being removed. Stock full control is approximately stable (673.2 to 679.0 ns). Peak native-view reads move slightly from 9.25 to 9.41 ns; no peak improvement is claimed.

Java allocation is unchanged (501.5 projected / 1572.75 full bytes/row) and excludes native memory. Full view ingestion remains approximately 2.06x stock; this is not parity. Stock guest mixed reads are explicit boundary projections.
