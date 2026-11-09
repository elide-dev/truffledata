# Projection resident gates — 4065dd6f

Isolated candidate `5002235`, invocation `4065dd6f-2b23-40f9-bb50-bfec5e1a448f`, x86-64 RBE. Both runtimes used unsharded cross-mode comparisons. Complete raw measurements, worker/load metadata, summaries and test log/XML are in `svm/` and `jvm/`.

| Runtime | Comparison | Ratio | Ceiling | Required | Result |
|---|---|---:|---:|---|---|
| SVM | selected-small | 0.664427 | 0.85 | yes | pass |
| SVM | selected-holdout | 0.373690 | 0.5 | yes | pass |
| SVM | lazy-holdout | 0.497736 | 0.5 | yes | pass |
| SVM | session-lazy-holdout | 0.550438 | 0.5 | no | miss |
| JVM | selected-small | 0.863839 | 0.85 | yes | miss |
| JVM | selected-holdout | 0.489243 | 0.5 | yes | pass |
| JVM | lazy-holdout | 0.346017 | 0.5 | yes | pass |
| JVM | session-lazy-holdout | 0.589008 | 0.5 | no | miss |

All three required SVM comparisons pass. JVM selected-small remains above 0.85; selected-holdout and lazy-holdout pass. Session-lazy is reported but is not a required E1 gate. The later generated eager-projection change (`844aafe`) was qualified locally with 29,440 exact byte/status pairs, including input windows, exact/one-below budgets and mutations, but its resident performance is not measured here. It needs a fresh formal E1 run after the requested pause.

These ratios compare projected and ordinary ingestion within each runtime, not against stock protobuf. [The full paired consumer archive](../protobuf-slab-4065dd6f/README.md) retains stock/historical comparisons and their limits. No E1 completion claim is made while the required JVM selected-small result misses.
