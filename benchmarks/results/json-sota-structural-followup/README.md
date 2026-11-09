# Structural JSON producer qualification

The paired native probe compares the frozen On-Demand decoder with the current structural producer in one process. Both rows preserve the compact tape, directory and numeric contracts; the structural row requires zero upstream allocations after warm-up. These measurements are progress evidence, not completion of the SOTA plan.

RBE invocation [c8037437](https://app.buildbuddy.io/invocation/c8037437-590f-4105-b0dc-564b25845ab5), baseline x86-64 build, 2026-11-07: retained ratios 1.349 / 1.460 / 1.447 / 1.394; validate ratios 1.491 / 1.738 / 1.719 / 1.646 (210 B / 4,330 B / 228,485 B / 4,850,894 B). Huge retained is below 1.4 and small validation is below 1.6. No target has been relaxed.

The source includes checked shape templates, cached clean-key recognition, structural reservations, bounded ordinary-number shortcuts, deferred validate-only key scratch, and carried string-scan/ASCII state. The shape cache occupies 43,144 bytes. The normal checked writer remains the YAML/On-Demand path; reserved appends are used only after a complete structural index bounds every write. Native acceptance and exact words/pool/directories pass alongside the probe. Local Apple clang ASan+UBSan also passes the differential corpus and million-key sequence test; hermetic sanitizer and full repository gates remain outstanding.

The harness is retained here for reproduction; its temporary manual Bazel target must be removed before the final release checkpoint. Baseline implementation: `json/native/testing/stage2_baseline_probe.h`.
