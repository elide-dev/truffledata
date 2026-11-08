# Java slab retirement qualification

Local macOS arm64, GraalVM 25.5.5-elide.27, seam ABI 11 after view and scalar export removal.
Java fallback and FFM acceptance pass; the compiled read suite includes all four raw unknown-byte query implementations, allocation thresholds and expired-session rejection.

The consumer log qualifies correct checksums and tiers for stock host interop (sparse interpreted/full compiled), projected holdout sparse, fully consumed lazy holdout, and projected native sessions. Scalar/string stock reads invoke protobuf-java host getters through cached interop; immutable child wrappers and counts are cached. Graph budget remains 10,000. Short single-process timing samples are diagnostic only, not performance-gate evidence.

Formal Linux JVM/SVM acceptance, paired E1 sparse-resident measurements and the final historical baseline comparison remain pending.
