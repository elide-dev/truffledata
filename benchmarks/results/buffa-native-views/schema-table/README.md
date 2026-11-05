# Checked schema table lookup

Source: native schema lookup change on `0f38922`. Resolve the checked fixed-width field table and type flags once per binary message; scan that table without re-reading the type directory for every candidate field. Sparse and out-of-order numbers retain their schema ordinals. Truncated tables, including unused trailing records, are rejected.

Native, strict JVM view, and SVM view tests passed: https://app.buildbuddy.io/invocation/5791fb36-9f21-4508-ae2f-e2e1971c5155. Additional sparse-order and truncated-table regression tests passed: https://app.buildbuddy.io/invocation/837fabc5-1688-4dac-8006-7d13483d62b4.

Same five-fork randomized CPU-2 protocol as `../full/run.py`; remote `/tmp/truffledata-native-views.G2ATYf/schema-table`. Generated projected view ingestion improves 843.8 to 824.2 ns, full ingestion 1672.5 to 1628.2 ns. Stock controls are approximately unchanged, although fork ranges overlap and these are modest gains. Java allocation remains 501.5 projected and 1572.75 full bytes/row; native memory is excluded. Peak reads remain approximately 9.24 ns. This is still 1.28x stock projected and 2.38x stock full ingestion.

The compact scalar path regresses from roughly 54.2 to 56.7 ns because it also uses the shared lookup helper but does not yet hoist the table. A follow-up will address that path separately. This experiment does not establish overall parity.
