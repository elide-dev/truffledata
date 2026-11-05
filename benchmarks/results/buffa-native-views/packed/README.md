# Packed child offset experiment

Up to sixteen field offsets use four-bit relative positions; wider fields or spans fall back to the integer directory. Duplicate singular scalar fields overwrite their offset, and zero represents absence.

Build/JVM/SVM view checks: https://app.buildbuddy.io/invocation/515378c7-4dba-455f-b2ed-3cd1b225bd40. All passed, including schema-width and duplicate-index-span boundaries. Same five-fork randomized CPU-2 protocol as `../full/run.py`. Remote: `/tmp/truffledata-native-views.G2ATYf/packed`. Source: local packed-offset change on `da82ee6`.

Full-view Java allocation falls from 2037.25 to 1801.00 bytes/row; projected allocation grows from 477.50 to 485.50 because each row carries the packed word. Native memory is excluded. Retained peak reads improve from 11.60 to 10.72 ns, with eager controls near 6.64 ns.

Full generated ingestion rises from 1889.9 to 2017.6 ns, while the eager control rises from 1879.2 to 2014.6 ns. Generic full ingestion also regresses (2417.8 to 2667.2 ns; eager 2338.5 to 2469.0 ns). Stock controls remain near 0.63/0.68 microseconds. These results do not establish a throughput improvement; layout changes and the speculative packing scan require follow-up. The implementation is experimental and unshipped.
