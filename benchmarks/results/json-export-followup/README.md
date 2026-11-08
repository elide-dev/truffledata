# Compact export followup

A private sink key-cache hash now uses the decoded short-key words that exact equality already loads. The public key hash and native directory FNV contract are unchanged. The cursor reads one compact header, and text exports use the checked bulk-copy hooks introduced for snapshots. Long keys retain the existing byte-comparison path; cache capacity and key identity semantics are unchanged.

Local JVM diagnostic on the same 32 `JsonFixture` documents, alternating three fresh before/after processes, gives steady per-document medians 519.600/549.555/519.220 ns before and 460.990/461.275/436.850 ns after (1.127/1.191/1.189×). The before classes are from `7a0c27f`; both runs use the same ABI 10 native library and GraalVM .27. The probe source and every raw process log are retained. This diagnoses the export cost; it does not replace the required unclemax Native Image comparison or graph/allocation gates.

The full local JSON JVM suite passes 63 tests, including exact key identity across raw/escaped UTF-8 spellings, NUL suffixes, 8/16-byte boundaries and long keys; the existing generated-tree and cache-capacity tests remain. All 21 shared tape tests pass. Formal qualification is pending.
