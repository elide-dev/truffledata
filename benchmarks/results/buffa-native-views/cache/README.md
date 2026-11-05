# Cached native-view reads

Return existing strings, arrays and child views before schema dispatch and native index lookup. Cache contents remain mutable; scoped owner checks are unchanged.

Build/JVM checks: https://app.buildbuddy.io/invocation/592f70a6-ef4c-465c-accf-b264abee2020. Same randomized five-fork CPU-2 protocol and executable mode toggle as the preceding experiments. Remote: `/tmp/truffledata-native-views.G2ATYf/cache`. Source: local cache-first change on `eeb30ef`.

Retained generated reads improve from 24.05 to 21.05 ns interpreter and 15.17 to 12.93 ns peak JIT. Eager controls remain near 18.7/9.3 ns, and stock boundary controls near 5.4/5.3 ns. Projected ingestion remains about 1.03 microseconds with 477.5 Java bytes/row. Full consumption remains about 2.33 microseconds. Java allocation excludes native memory; cross-build effects remain possible.
