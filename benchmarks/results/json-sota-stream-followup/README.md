# Structural stream checkpoint

The stage-1 stream owns bounded batch indexes, and each document copies only its bytes into the session before the stage-2 walk. Partial batches use `streaming_partial`; the final buffered region uses `streaming_final` with the existing held-back-scalar rule. Buffered lookahead distinguishes a complete oversized root (skippable) from an incomplete one. The old session/stream implementation is frozen under `json/native/testing/ondemand_{session,stream}.h` for the paired manual `stream_probe` target.

Initial remote [dab2dbc5](https://app.buildbuddy.io/invocation/dab2dbc5-ae9d-4a8e-b29c-5d43be2cadc3) passes stream, API and structural correctness. The paired stream probe remains below 1.45x in its steady rounds (1.340/1.200/1.327); cold results are noisy and are not qualification.

The subsequent implementation remembers up to four previously verified root structural counts. It tries at most one index-derived candidate, only when existing session input/writer capacities cover it; the entire stage-2 grammar must succeed. A shorter root with trailing input or a longer root cut at an inner closer falls back to the exact structural boundary scan. Failed candidate state is recycled before retry; no candidate can grow allocator storage. Limit precedence and skip extents remain checked.

Local Apple clang ASan+UBSan passes API and complete stream tests including explicit shorter/longer/malformed candidate fallbacks, fault injection, retained ownership, 10,000 mixed roots and one-/seven-byte feeds. A paired local timing gives 1.412/1.490/1.682/1.473/1.513x (median1.490), but the host was noisy. Remote/native qualification and the final unclemax pair remain required.

Remote [a0c08060](https://app.buildbuddy.io/invocation/a0c08060-b429-4a00-829e-4d2b4c726df6) passes the 1.45× stream gate in every paired round: 1.516/1.518/1.502/1.511/1.504×. Structural elapsed time is 5.33–5.39ms for the unchanged 4,850,893-byte fixture. Raw paired output is `a0c08060-stream-probe.txt`; the final unclemax pair remains separate.
