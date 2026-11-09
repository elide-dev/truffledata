# YAML compact ABI 4 qualification and ABI 5 candidates

Pinned RBE invocation `cf1883aa-98e0-40e6-b91b-62553bd92b72`, with YAML source at `4d9016f`, passes correctness and Native Image acceptance. The block ratio is **0.711**, below the unchanged **0.715** floor (per-pass 0.711–0.717). Config 0.551, flow 0.649, stream 0.481, and text-heavy 0.806 remain below their old baselines 0.597/0.724/0.497/0.895.

Retained SVM reads now pass: **1,943 first / 1,886 warm nodes**, one loop, no calls or allocation nodes. Export is 290/231 nodes with one loop. The smoke wrapper expected exactly two export loops; its expectation is tightened to one, with the node/call/allocation bounds unchanged. This wrapper-only failure does not imply a throughput pass.

The adjacent local logs compare frozen ABI 4 libraries in one process, seven alternating passes, three samples per pass, 200 decodes/sample (stream: 100). macOS arm64, Homebrew clang 23, `-O2 -fvisibility-inlines-hidden`, same checked-in fixtures and native flags0. They are exploratory, not pinned-toolchain results:

- Owned persistent key templates: stream 1.081×, block 1.000×. Incomplete templates are invalidated on errors; API failure/retain reuse and 9,093 fast/general/fused parity cases pass ASan/UBSan.
- Filtered input strings: block 1.005×, flow 1.108×, text-heavy 1.023×. Pool bytes fall respectively 21,445/112,780/321,880 to zero; word counts are unchanged. The flow log has visible timing variation; the best-pair ratio and all seven passes are preserved.

The filtered-string extension is explicitly YAML-only: WIDE/non-OWNED STRING addresses decoded bytes in filtered input and keeps an independent raw span in word1. JSON and DECIMAL continue rejecting that combination. Pristine-source retention still uses owned decoded text. Local qualification passes 15 compact native groups, 9,093 YAML parity cases (2,740 matching errors), C API14 injected failures, stream1,071 documents plus35 injected failures, YAML JVM31/31 and compact JVM13/13. Snapshot, Myna conversion, raw-source availability, export and retained-input lifetimes are exercised. Formal SVM/JSON graph and pinned throughput qualification remain required.

ABI 5 additionally gives stream_next per-document flags. Iterators request lookup directories, sinks omit them, both honor source retention, and reserved flags do not consume input. Fingerprint: `80867948e128d3f992340e28cd4448ba0e6dfb7396eb28fded1ef65bae3cc1ac` on all three supported targets.
