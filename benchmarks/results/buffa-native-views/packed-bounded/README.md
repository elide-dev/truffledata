# Bounded packed-directory construction

Skip the speculative packing scan when the row spans more than fifteen native entries. The array fallback remains. Same five-fork randomized CPU-2 protocol as `../full/run.py`; remote `/tmp/truffledata-native-views.G2ATYf/packed-bounded`.

Build/JVM view check: https://app.buildbuddy.io/invocation/addf50b6-845a-4d2b-9b82-b8faf4278c09. Source: local span guard on `e5ee2ac`. The initial packed experiment also passed strict JVM and SVM view suites.

Full generated ingestion returns to 1865.0 ns (eager 1877.0 ns; stock 680.2 ns). Java allocation remains 1801.0 bytes/row (stock 1776.75); native storage is excluded. Projected ingestion is 1018.1 ns and 485.5 Java bytes/row. Retained generated peak reads are 10.65 ns versus eager 6.67 ns and stock-boundary 5.36 ns. Retain for allocation and peak-read improvements, without claiming ingestion parity.
