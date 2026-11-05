# Native views after single-value caching

Source `e11d7b5`; profiling build https://app.buildbuddy.io/invocation/95a8b759-eff3-4218-ac47-1914e029efe6. Serial CPU-2 captures on unclemax, three one-second measurement samples per cell after warmup. Native perf captures include startup; JFR starts after warmup. No lost perf samples. Raw perf/JFR files remain under `/tmp/truffledata-native-views.G2ATYf/single-cache/profiles` on unclemax. The JSON records binary and JFR hashes.

Projected generated native-view ingestion: binary decoding is 27.39% self, index push 8.77%. Full generated native-view ingestion: binary decoding 12.23%, native entry helper 5.98%, row index 4.60%, String constructor 4.09%, index push 3.91%, row getLong 3.90%, row get 3.85%, UTF-8 validation 3.81%, ordinal 3.34%, array materialization 3.14%, decode 3.01%, next 2.70%. These are diagnostic sample proportions, not an additive wall-time decomposition or a benchmark score.

Full-read allocation samples are now dominated by NativeRow (231), byte[] (116), and String (59); Object[] and int[] have 11 and 10 samples. Sample counts are neither allocated bytes nor total native memory. This supports moving beyond cache arrays to schema lookup, native index traversal, and child/string materialization.

The first follow-up experiment hoists the checked schema field-table range and type flags out of the binary field loop. It preserves arbitrary field-number ordering and performs a linear lookup over fixed-width checked entries; it does not assume dense field numbers or weaken input bounds checks.
